package com.dennis.aimasteringlab;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Executes one bounded AI-console plan and hands the result to the verified master renderer. */
final class AutonomousMasteringRenderer {
    static final class Result {
        AutonomousMasteringBrain.Plan plan;
        MasterRenderer.Result master;
        AnalysisResult correctedPremaster;
        double maximumLocalCutDb;
        double maximumCompressionReductionDb;
        double maximumGlobalEqDb;
        String guardrailVerdict;
        boolean bypassedInactiveEventFilters;

        String compactSummary() {
            return String.format(Locale.US,
                    "%s\nVerified %.2f LUFS-I / %.2f dBTP / LRA %.2f LU • limiter %s %.2f dB max • AI comp %.2f dB max • %s",
                    plan.compactSummary(), master.verification.integratedLufs,
                    master.verification.truePeakEstimateDbtp, master.verification.lraLu,
                    master.limiterUsed ? "used" : "bypassed",
                    master.maximumLimiterReductionDb, maximumCompressionReductionDb,
                    guardrailVerdict);
        }
    }

    private static final int BUFFER_FRAMES = 4096;
    private static final double MAX_COMBINED_LOCAL_CUT_DB = 1.65;

    private AutonomousMasteringRenderer() {}

    static Result render(AudioAnalyzer.StreamProvider source,
                         AnalysisResult sourceAnalysis,
                         AutonomousMasteringBrain.Plan plan,
                         File output,
                         File workingDirectory,
                         AudioAnalyzer.Progress progress) throws IOException {
        return render(source, sourceAnalysis, plan, output, workingDirectory,
                -1.0, false, progress);
    }

    static Result render(AudioAnalyzer.StreamProvider source,
                         AnalysisResult sourceAnalysis,
                         AutonomousMasteringBrain.Plan plan,
                         File output,
                         File workingDirectory,
                         double finalTruePeakCeilingDbtp,
                         boolean forceLimiter,
                         AudioAnalyzer.Progress progress) throws IOException {
        return render(source, sourceAnalysis, plan, output, workingDirectory,
                finalTruePeakCeilingDbtp, forceLimiter,
                MasterRenderer.LimiterProfile.BALANCED, progress);
    }

    static Result render(AudioAnalyzer.StreamProvider source,
                         AnalysisResult sourceAnalysis,
                         AutonomousMasteringBrain.Plan plan,
                         File output,
                         File workingDirectory,
                         double finalTruePeakCeilingDbtp,
                         boolean forceLimiter,
                         MasterRenderer.LimiterProfile limiterProfile,
                         AudioAnalyzer.Progress progress) throws IOException {
        if (sourceAnalysis.wav.channels != 2) {
            throw new IOException("AI mastering candidate rendering requires stereo audio");
        }
        if (plan == null) throw new IOException("AI mastering plan is unavailable");
        if (!workingDirectory.exists() && !workingDirectory.mkdirs()) {
            throw new IOException("Could not create the private AI mastering cache");
        }
        File corrected = new File(workingDirectory,
                "ai-console-premaster-" + safeStrategy(plan)
                        + "-" + System.currentTimeMillis() + ".wav");
        try {
            RenderStats stats = renderFloat(source, sourceAnalysis, plan, corrected, progress);
            AnalysisResult correctedAnalysis = AudioAnalyzer.analyze(corrected.getName(),
                    () -> new FileInputStream(corrected), progress);
            String guardrail = validatePremaster(sourceAnalysis, correctedAnalysis, plan, stats);
            progress.update("AI mastering: loudness matching verified candidate", 0);
            MasterRenderer.Result master = MasterRenderer.renderWithCeiling(
                    () -> new FileInputStream(corrected), correctedAnalysis,
                    correctedAnalysis.appliedSafeGainDb, output,
                    finalTruePeakCeilingDbtp, forceLimiter, limiterProfile,
                    progress);
            master.aiGuided = true;
            master.aiCorrectionEvents = stats.localEvents;
            master.maximumAiCorrectionDb = Math.max(stats.maximumGlobalEqDb,
                    Math.max(stats.maximumLocalCutDb, stats.maximumCompressionReductionDb));
            master.aiCorrectedPremasterLufs = correctedAnalysis.integratedLufs;
            master.aiCorrectedPremasterTruePeakDbtp = correctedAnalysis.truePeakEstimateDbtp;
            master.aiCorrectionSummary = String.format(Locale.US,
                    "%s plan; global EQ max %.2f dB; local event cut max %.2f dB; stereo side scale %.3f; compression max %.2f dB; %s",
                    plan.mode.name().toLowerCase(Locale.US), stats.maximumGlobalEqDb,
                    stats.maximumLocalCutDb, plan.sideScale,
                    stats.maximumCompressionReductionDb, guardrail);
            if (master.peakSafetyRecoveryPasses > 0) {
                master.aiCorrectionSummary += String.format(Locale.US,
                        "; measured peak-safety recovery: %d verified flat-gain retries, %.3f dB less gain than originally requested; exact -14 LUFS=%s",
                        master.peakSafetyRecoveryPasses,
                        master.peakSafetyRecoveryAttenuationDb,
                        Math.abs(master.verification.integratedLufs + 14.0) <= 0.02);
            }

            Result result = new Result();
            result.plan = plan;
            result.master = master;
            result.correctedPremaster = correctedAnalysis;
            result.maximumLocalCutDb = stats.maximumLocalCutDb;
            result.maximumCompressionReductionDb = stats.maximumCompressionReductionDb;
            result.maximumGlobalEqDb = stats.maximumGlobalEqDb;
            result.guardrailVerdict = guardrail;
            result.bypassedInactiveEventFilters = stats.localEvents == 0;
            return result;
        } finally {
            if (corrected.exists() && !corrected.delete()) corrected.deleteOnExit();
        }
    }

    private static String safeStrategy(AutonomousMasteringBrain.Plan plan) {
        String value = plan.strategy == null
                ? plan.mode.name().toLowerCase(Locale.US) : plan.strategy;
        value = value.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return value.isEmpty() ? "candidate" : value;
    }

    private static RenderStats renderFloat(AudioAnalyzer.StreamProvider source,
                                           AnalysisResult analysis,
                                           AutonomousMasteringBrain.Plan plan,
                                           File output,
                                           AudioAnalyzer.Progress progress) throws IOException {
        long dataBytes = analysis.wav.frames * 2L * 4L;
        if (dataBytes > 0xffffffffL - 36L) {
            throw new IOException("The AI pre-master would exceed the RIFF/WAV 4 GB limit");
        }
        int rate = analysis.wav.sampleRate;
        Biquad lowLeft = Biquad.lowShelf(rate, 110.0, plan.lowShelfDb);
        Biquad lowRight = Biquad.lowShelf(rate, 110.0, plan.lowShelfDb);
        Biquad lowMidLeft = Biquad.peaking(rate, 320.0, 0.82, plan.lowMidDb);
        Biquad lowMidRight = Biquad.peaking(rate, 320.0, 0.82, plan.lowMidDb);
        Biquad presenceLeft = Biquad.peaking(rate, 3200.0, 0.88, plan.presenceDb);
        Biquad presenceRight = Biquad.peaking(rate, 3200.0, 0.88, plan.presenceDb);
        Biquad highLeft = Biquad.highShelf(rate, Math.min(9000.0, rate * 0.21),
                plan.highShelfDb);
        Biquad highRight = Biquad.highShelf(rate, Math.min(9000.0, rate * 0.21),
                plan.highShelfDb);

        EventControl hf = new EventControl(analysis.aiDiagnostics,
                AdaptiveDiagnosticModel.Type.HIGH_FREQUENCY_EMPHASIS, plan.eventScale,
                analysis.wav.durationSeconds);
        EventControl harsh = new EventControl(analysis.aiDiagnostics,
                AdaptiveDiagnosticModel.Type.UPPER_MID_HARSHNESS, plan.eventScale,
                analysis.wav.durationSeconds);
        EventControl mud = new EventControl(analysis.aiDiagnostics,
                AdaptiveDiagnosticModel.Type.LOW_MID_MASKING, plan.eventScale,
                analysis.wav.durationSeconds);
        Biquad eventHighLeft = Biquad.highPass(rate, Math.min(7000.0, rate * 0.18));
        Biquad eventHighRight = Biquad.highPass(rate, Math.min(7000.0, rate * 0.18));
        Biquad eventHarshHighLeft = Biquad.highPass(rate, 2500.0);
        Biquad eventHarshHighRight = Biquad.highPass(rate, 2500.0);
        Biquad eventHarshLowLeft = Biquad.lowPass(rate, Math.min(7500.0, rate * 0.19));
        Biquad eventHarshLowRight = Biquad.lowPass(rate, Math.min(7500.0, rate * 0.19));
        Biquad eventMudHighLeft = Biquad.highPass(rate, 140.0);
        Biquad eventMudHighRight = Biquad.highPass(rate, 140.0);
        Biquad eventMudLowLeft = Biquad.lowPass(rate, 520.0);
        Biquad eventMudLowRight = Biquad.lowPass(rate, 520.0);

        LinkedCompressor compressor = new LinkedCompressor(rate, plan);
        RenderStats stats = new RenderStats();
        stats.localEvents = hf.count() + harsh.count() + mud.count();
        // No local-correction event can ever engage in this render: skip the
        // ten otherwise wasted filter passes, while keeping global EQ,
        // compressor, mastering plan and the downstream QC unchanged.
        final boolean localCorrectionActive = stats.localEvents > 0;
        stats.maximumGlobalEqDb = Math.max(Math.abs(plan.lowShelfDb),
                Math.max(Math.abs(plan.lowMidDb),
                        Math.max(Math.abs(plan.presenceDb), Math.abs(plan.highShelfDb))));

        progress.update("AI mastering: rendering protected float pre-master", 0);
        try (WavFile.Reader reader = WavFile.open(source.open());
             FileOutputStream fileOutput = new FileOutputStream(output);
             OutputStream raw = new BufferedOutputStream(fileOutput, 256 * 1024)) {
            writeFloatHeader(raw, rate, 2, dataBytes);
            FloatWriter writer = new FloatWriter(raw);
            float[][] samples = new float[2][BUFFER_FRAMES];
            long framesDone = 0;
            int read;
            while ((read = reader.readFrames(samples, BUFFER_FRAMES)) > 0) {
                for (int i = 0; i < read; i++) {
                    double left = finite(samples[0][i]);
                    double right = finite(samples[1][i]);

                    double middle = 0.5 * (left + right);
                    double side = 0.5 * (left - right) * plan.sideScale;
                    left = middle + side;
                    right = middle - side;

                    left = highLeft.process(presenceLeft.process(
                            lowMidLeft.process(lowLeft.process(left))));
                    right = highRight.process(presenceRight.process(
                            lowMidRight.process(lowRight.process(right))));

                    if (localCorrectionActive) {
                        double highBandLeft = eventHighLeft.process(left);
                        double highBandRight = eventHighRight.process(right);
                        double harshBandLeft = eventHarshLowLeft.process(
                                eventHarshHighLeft.process(left));
                        double harshBandRight = eventHarshLowRight.process(
                                eventHarshHighRight.process(right));
                        double mudBandLeft = eventMudLowLeft.process(eventMudHighLeft.process(left));
                        double mudBandRight = eventMudLowRight.process(eventMudHighRight.process(right));
                        double seconds = framesDone / (double) rate;
                        double highDb = hf.gainDbAt(seconds);
                        double harshDb = harsh.gainDbAt(seconds);
                        double mudDb = mud.gainDbAt(seconds);
                        double combined = -highDb - harshDb - mudDb;
                        double localCutLimit = plan.mode == AutonomousMasteringBrain.Mode.PREFERRED
                                ? MAX_COMBINED_LOCAL_CUT_DB : 1.00;
                        if (combined > localCutLimit) {
                            double scale = localCutLimit / combined;
                            highDb *= scale;
                            harshDb *= scale;
                            mudDb *= scale;
                            combined = localCutLimit;
                        }
                        stats.maximumLocalCutDb = Math.max(stats.maximumLocalCutDb, combined);
                        left += gainDelta(highDb) * highBandLeft
                                + gainDelta(harshDb) * harshBandLeft
                                + gainDelta(mudDb) * mudBandLeft;
                        right += gainDelta(highDb) * highBandRight
                                + gainDelta(harshDb) * harshBandRight
                                + gainDelta(mudDb) * mudBandRight;

                    }

                    double compressionGain = compressor.gain(left, right);
                    left *= compressionGain;
                    right *= compressionGain;
                    stats.maximumCompressionReductionDb = Math.max(
                            stats.maximumCompressionReductionDb,
                            compressor.currentReductionDb);
                    writer.frame(left, right);
                    framesDone++;
                }
                int percent = (int) Math.min(92,
                        Math.round(92.0 * framesDone / Math.max(1L, reader.info.frames)));
                progress.update("AI mastering: rendering protected float pre-master", percent);
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("AI mastering candidate render cancelled");
                }
            }
            writer.finish();
            raw.flush();
            fileOutput.getFD().sync();
            if (framesDone != analysis.wav.frames) {
                throw new IOException("AI mastering frame accounting failed");
            }
        } catch (IOException error) {
            if (output.exists() && !output.delete()) output.deleteOnExit();
            throw error;
        }
        long expectedBytes = 44L + dataBytes;
        if (output.length() != expectedBytes) {
            long actualBytes = output.length();
            if (!output.delete()) output.deleteOnExit();
            throw new IOException("AI pre-master durable-length verification failed: expected "
                    + expectedBytes + " bytes, found " + actualBytes);
        }
        return stats;
    }

    private static String validatePremaster(AnalysisResult source,
                                            AnalysisResult corrected,
                                            AutonomousMasteringBrain.Plan plan,
                                            RenderStats stats) throws IOException {
        if (!corrected.wav.floatingPoint || corrected.wav.channels != 2
                || corrected.wav.frames != source.wav.frames
                || corrected.wav.sampleRate != source.wav.sampleRate) {
            throw new IOException("AI pre-master failed format or duration verification");
        }
        if (corrected.nonFiniteSamples != 0) {
            throw new IOException("AI pre-master introduced non-finite samples");
        }
        if (corrected.clippedSamples > source.clippedSamples) {
            throw new IOException(String.format(Locale.US,
                    "AI pre-master introduced %d additional full-scale sample(s); source already contained %d",
                    corrected.clippedSamples - source.clippedSamples,
                    source.clippedSamples));
        }
        if (corrected.internalZeroRuns > source.internalZeroRuns) {
            throw new IOException("AI pre-master introduced an exact-zero interruption");
        }
        if (corrected.stereoCorrelation < source.stereoCorrelation - 0.035) {
            throw new IOException("AI pre-master worsened global stereo correlation");
        }
        if (corrected.lowBandCorrelation < source.lowBandCorrelation - 0.025) {
            throw new IOException("AI pre-master worsened below-160 Hz correlation");
        }
        double allowedLraChange = plan.mode == AutonomousMasteringBrain.Mode.CONSERVATIVE
                ? 0.80 : 1.75;
        if (source.lraLu - corrected.lraLu > allowedLraChange + 0.05) {
            throw new IOException("AI pre-master reduced LRA beyond the candidate guardrail");
        }
        if (stats.maximumCompressionReductionDb
                > plan.compressorMaximumReductionDb + 0.03) {
            throw new IOException("AI compressor exceeded its plan-derived reduction cap");
        }
        return String.format(Locale.US,
                "PREMASTER PASS: duration/continuity/phase/LRA caps verified; inherited full-scale samples %d→%d (no increase); corr %.3f→%.3f; low corr %.3f→%.3f",
                source.clippedSamples, corrected.clippedSamples,
                source.stereoCorrelation, corrected.stereoCorrelation,
                source.lowBandCorrelation, corrected.lowBandCorrelation);
    }

    private static double gainDelta(double gainDb) {
        return Math.abs(gainDb) < 1e-12 ? 0.0
                : Math.pow(10.0, gainDb / 20.0) - 1.0;
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private static void writeFloatHeader(OutputStream out, int sampleRate,
                                         int channels, long dataBytes) throws IOException {
        int blockAlign = channels * 4;
        LittleEndian.writeAscii(out, "RIFF");
        LittleEndian.writeU32(out, 36L + dataBytes);
        LittleEndian.writeAscii(out, "WAVE");
        LittleEndian.writeAscii(out, "fmt ");
        LittleEndian.writeU32(out, 16);
        LittleEndian.writeU16(out, 3);
        LittleEndian.writeU16(out, channels);
        LittleEndian.writeU32(out, sampleRate);
        LittleEndian.writeU32(out, (long) sampleRate * blockAlign);
        LittleEndian.writeU16(out, blockAlign);
        LittleEndian.writeU16(out, 32);
        LittleEndian.writeAscii(out, "data");
        LittleEndian.writeU32(out, dataBytes);
    }

    private static final class RenderStats {
        int localEvents;
        double maximumLocalCutDb;
        double maximumCompressionReductionDb;
        double maximumGlobalEqDb;
    }

    private static final class EventControl {
        private static final double ATTACK = 0.060;
        private static final double RELEASE = 0.180;
        private final List<AdaptiveDiagnosticModel.Event> events = new ArrayList<>();
        private final double scale;
        private final double protectedEndingStart;
        private int cursor;

        EventControl(AdaptiveDiagnosticModel.Result diagnostics,
                     AdaptiveDiagnosticModel.Type type,
                     double scale,
                     double trackDuration) {
            this.scale = scale;
            this.protectedEndingStart = Math.max(2.0,
                    trackDuration - Math.max(8.0, trackDuration * 0.04));
            if (diagnostics != null && scale > 0.0) {
                for (AdaptiveDiagnosticModel.Event event : diagnostics.events) {
                    if (event.autoCorrection && event.type == type) events.add(event);
                }
                events.sort(Comparator.comparingDouble(event -> event.startSeconds));
            }
        }

        int count() {
            return events.size();
        }

        double gainDbAt(double seconds) {
            if (seconds < 2.0 || seconds >= protectedEndingStart) return 0.0;
            while (cursor < events.size()
                    && seconds > events.get(cursor).endSeconds + RELEASE) cursor++;
            if (cursor >= events.size()) return 0.0;
            AdaptiveDiagnosticModel.Event event = events.get(cursor);
            if (seconds < event.startSeconds) return 0.0;
            if (seconds <= event.endSeconds) {
                double attack = Math.min(1.0, (seconds - event.startSeconds) / ATTACK);
                return event.correctionDb * scale * smooth(attack);
            }
            double release = 1.0 - Math.min(1.0,
                    (seconds - event.endSeconds) / RELEASE);
            return event.correctionDb * scale * smooth(release);
        }

        private static double smooth(double value) {
            double x = Math.max(0.0, Math.min(1.0, value));
            return x * x * (3.0 - 2.0 * x);
        }
    }

    private static final class LinkedCompressor {
        final boolean enabled;
        final double thresholdDb;
        final double ratio;
        final double maximumReductionDb;
        final double detectorAttack;
        final double detectorRelease;
        final double gainAttack;
        final double gainRelease;
        double envelope;
        double currentReductionDb;

        LinkedCompressor(double rate, AutonomousMasteringBrain.Plan plan) {
            enabled = plan.compressorEnabled;
            thresholdDb = plan.compressorThresholdDbfs;
            ratio = Math.max(1.0, plan.compressorRatio);
            maximumReductionDb = Math.max(0.0, plan.compressorMaximumReductionDb);
            detectorAttack = coefficient(rate, plan.compressorAttackMs);
            detectorRelease = coefficient(rate, plan.compressorReleaseMs);
            gainAttack = coefficient(rate, 8.0);
            gainRelease = coefficient(rate, Math.max(120.0, plan.compressorReleaseMs));
        }

        double gain(double left, double right) {
            if (!enabled) return 1.0;
            double detector = Math.max(Math.abs(left), Math.abs(right));
            double coefficient = detector > envelope ? detectorAttack : detectorRelease;
            envelope = coefficient * envelope + (1.0 - coefficient) * detector;
            double levelDb = 20.0 * Math.log10(Math.max(1e-12, envelope));
            double knee = 3.0;
            double over;
            if (levelDb <= thresholdDb - knee * 0.5) {
                over = 0.0;
            } else if (levelDb >= thresholdDb + knee * 0.5) {
                over = levelDb - thresholdDb;
            } else {
                double x = levelDb - (thresholdDb - knee * 0.5);
                over = x * x / (2.0 * knee);
            }
            double target = Math.min(maximumReductionDb,
                    over * (1.0 - 1.0 / ratio));
            double coefficientGain = target > currentReductionDb ? gainAttack : gainRelease;
            currentReductionDb = coefficientGain * currentReductionDb
                    + (1.0 - coefficientGain) * target;
            return Math.pow(10.0, -currentReductionDb / 20.0);
        }

        private static double coefficient(double rate, double milliseconds) {
            if (milliseconds <= 0.0) return 0.0;
            return Math.exp(-1.0 / (rate * milliseconds * 0.001));
        }
    }

    private static final class FloatWriter {
        private final OutputStream output;
        private final byte[] buffer = new byte[256 * 1024];
        private int position;

        FloatWriter(OutputStream output) {
            this.output = output;
        }

        void frame(double left, double right) throws IOException {
            sample(left);
            sample(right);
        }

        private void sample(double value) throws IOException {
            if (position + 4 > buffer.length) flush();
            int bits = Float.floatToIntBits((float) finite(value));
            buffer[position++] = (byte) (bits & 0xff);
            buffer[position++] = (byte) ((bits >>> 8) & 0xff);
            buffer[position++] = (byte) ((bits >>> 16) & 0xff);
            buffer[position++] = (byte) ((bits >>> 24) & 0xff);
        }

        void finish() throws IOException {
            flush();
        }

        private void flush() throws IOException {
            if (position > 0) output.write(buffer, 0, position);
            position = 0;
        }
    }
}
