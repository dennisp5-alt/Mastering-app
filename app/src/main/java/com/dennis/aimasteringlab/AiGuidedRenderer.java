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

/** Renders the optional A/B test master from confidence-gated AI findings. */
final class AiGuidedRenderer {
    private static final int BUFFER_FRAMES = 4096;
    private static final double MAX_COMBINED_CUT_DB = 1.00;

    private AiGuidedRenderer() {}

    static MasterRenderer.Result render(AudioAnalyzer.StreamProvider source,
                                        AnalysisResult sourceAnalysis,
                                        File output,
                                        File workingDirectory,
                                        AudioAnalyzer.Progress progress) throws IOException {
        AdaptiveDiagnosticModel.Result diagnostics = sourceAnalysis.aiDiagnostics;
        if (diagnostics == null) throw new IOException("Run the AI diagnostic pass first");
        if (!diagnostics.hasAutoCorrections()) {
            throw new IOException("No local event passed the strict AI correction gate; keep the diagnostic-only result");
        }
        if (sourceAnalysis.wav.channels != 2) {
            throw new IOException("AI-guided test rendering requires a stereo authoritative mix");
        }
        if (!workingDirectory.exists() && !workingDirectory.mkdirs()) {
            throw new IOException("Could not create the private AI render cache");
        }
        File correctedFloat = new File(workingDirectory,
                "ai-corrected-premaster-" + System.currentTimeMillis() + ".wav");
        try {
            CorrectionStats correctionStats = renderCorrectedFloat(source, sourceAnalysis, diagnostics,
                    correctedFloat, progress);
            AnalysisResult corrected = AudioAnalyzer.analyze(correctedFloat.getName(),
                    () -> new FileInputStream(correctedFloat), progress);
            if (corrected.wav.frames != sourceAnalysis.wav.frames
                    || corrected.wav.sampleRate != sourceAnalysis.wav.sampleRate
                    || corrected.wav.channels != 2
                    || !corrected.wav.floatingPoint) {
                throw new IOException("AI correction pre-master failed format or duration verification");
            }
            progress.update("AI correction: loudness-matching A/B test master", 0);
            MasterRenderer.Result result = MasterRenderer.render(
                    () -> new FileInputStream(correctedFloat), corrected,
                    corrected.appliedSafeGainDb, output, progress);
            result.aiGuided = true;
            result.aiCorrectionEvents = diagnostics.autoCorrectionCount();
            result.maximumAiCorrectionDb = correctionStats.maximumCombinedCutDb;
            result.aiCorrectedPremasterLufs = corrected.integratedLufs;
            result.aiCorrectedPremasterTruePeakDbtp = corrected.truePeakEstimateDbtp;
            result.aiCorrectionSummary = correctionSummary(diagnostics, correctionStats);
            return result;
        } finally {
            if (correctedFloat.exists() && !correctedFloat.delete()) correctedFloat.deleteOnExit();
        }
    }

    private static CorrectionStats renderCorrectedFloat(AudioAnalyzer.StreamProvider source,
                                                        AnalysisResult analysis,
                                                        AdaptiveDiagnosticModel.Result diagnostics,
                                                        File output,
                                                        AudioAnalyzer.Progress progress) throws IOException {
        long dataBytes = analysis.wav.frames * 2L * 4L;
        if (dataBytes > 0xffffffffL - 36L) {
            throw new IOException("The AI correction intermediate would exceed the RIFF/WAV 4 GB limit");
        }
        EventControl high = new EventControl(diagnostics,
                AdaptiveDiagnosticModel.Type.HIGH_FREQUENCY_EMPHASIS);
        EventControl harsh = new EventControl(diagnostics,
                AdaptiveDiagnosticModel.Type.UPPER_MID_HARSHNESS);
        EventControl mud = new EventControl(diagnostics,
                AdaptiveDiagnosticModel.Type.LOW_MID_MASKING);

        int rate = analysis.wav.sampleRate;
        Biquad highLeft = Biquad.highPass(rate, Math.min(7000.0, rate * 0.18));
        Biquad highRight = Biquad.highPass(rate, Math.min(7000.0, rate * 0.18));
        Biquad harshHighLeft = Biquad.highPass(rate, 2500.0);
        Biquad harshHighRight = Biquad.highPass(rate, 2500.0);
        Biquad harshLowLeft = Biquad.lowPass(rate, Math.min(7500.0, rate * 0.19));
        Biquad harshLowRight = Biquad.lowPass(rate, Math.min(7500.0, rate * 0.19));
        Biquad mudHighLeft = Biquad.highPass(rate, 140.0);
        Biquad mudHighRight = Biquad.highPass(rate, 140.0);
        Biquad mudLowLeft = Biquad.lowPass(rate, 520.0);
        Biquad mudLowRight = Biquad.lowPass(rate, 520.0);
        CorrectionStats stats = new CorrectionStats();

        progress.update("AI correction: rendering protected 32-bit-float pre-master", 0);
        try (WavFile.Reader reader = WavFile.open(source.open());
             OutputStream raw = new BufferedOutputStream(new FileOutputStream(output), 256 * 1024)) {
            writeFloatHeader(raw, rate, 2, dataBytes);
            FloatWriter writer = new FloatWriter(raw);
            float[][] samples = new float[2][BUFFER_FRAMES];
            long framesDone = 0;
            int read;
            while ((read = reader.readFrames(samples, BUFFER_FRAMES)) > 0) {
                for (int frame = 0; frame < read; frame++) {
                    double left = finite(samples[0][frame]);
                    double right = finite(samples[1][frame]);
                    double highBandLeft = highLeft.process(left);
                    double highBandRight = highRight.process(right);
                    double harshBandLeft = harshLowLeft.process(harshHighLeft.process(left));
                    double harshBandRight = harshLowRight.process(harshHighRight.process(right));
                    double mudBandLeft = mudLowLeft.process(mudHighLeft.process(left));
                    double mudBandRight = mudLowRight.process(mudHighRight.process(right));
                    double seconds = framesDone / (double) rate;
                    double highDb = high.gainDbAt(seconds);
                    double harshDb = harsh.gainDbAt(seconds);
                    double mudDb = mud.gainDbAt(seconds);
                    double combined = -highDb - harshDb - mudDb;
                    if (combined > MAX_COMBINED_CUT_DB) {
                        double scale = MAX_COMBINED_CUT_DB / combined;
                        highDb *= scale;
                        harshDb *= scale;
                        mudDb *= scale;
                        combined = MAX_COMBINED_CUT_DB;
                    }
                    stats.maximumHighCutDb = Math.max(stats.maximumHighCutDb, -highDb);
                    stats.maximumHarshCutDb = Math.max(stats.maximumHarshCutDb, -harshDb);
                    stats.maximumMudCutDb = Math.max(stats.maximumMudCutDb, -mudDb);
                    stats.maximumCombinedCutDb = Math.max(stats.maximumCombinedCutDb, combined);
                    double highMix = gainDelta(highDb);
                    double harshMix = gainDelta(harshDb);
                    double mudMix = gainDelta(mudDb);
                    writer.frame(left + highMix * highBandLeft
                                    + harshMix * harshBandLeft + mudMix * mudBandLeft,
                            right + highMix * highBandRight
                                    + harshMix * harshBandRight + mudMix * mudBandRight);
                    framesDone++;
                }
                int percent = (int) Math.min(92,
                        Math.round(92.0 * framesDone / Math.max(1L, reader.info.frames)));
                progress.update("AI correction: rendering protected 32-bit-float pre-master", percent);
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("AI-guided render cancelled");
                }
            }
            writer.finish();
            if (framesDone != analysis.wav.frames) {
                throw new IOException("AI correction frame accounting failed");
            }
        } catch (IOException e) {
            if (output.exists() && !output.delete()) output.deleteOnExit();
            throw e;
        }
        return stats;
    }

    private static String correctionSummary(AdaptiveDiagnosticModel.Result diagnostics,
                                            CorrectionStats stats) {
        int high = 0, harsh = 0, mud = 0;
        for (AdaptiveDiagnosticModel.Event event : diagnostics.events) {
            if (!event.autoCorrection) continue;
            if (event.type == AdaptiveDiagnosticModel.Type.HIGH_FREQUENCY_EMPHASIS) {
                high++;
            } else if (event.type == AdaptiveDiagnosticModel.Type.UPPER_MID_HARSHNESS) {
                harsh++;
            } else if (event.type == AdaptiveDiagnosticModel.Type.LOW_MID_MASKING) {
                mud++;
            }
        }
        return String.format(Locale.US,
                "actual event envelopes: HF %d/%.2f dB max; upper-mid %d/%.2f dB max; low-mid %d/%.2f dB max; combined cap %.2f dB",
                high, stats.maximumHighCutDb, harsh, stats.maximumHarshCutDb,
                mud, stats.maximumMudCutDb, stats.maximumCombinedCutDb);
    }

    private static double gainDelta(double gainDb) {
        return gainDb == 0.0 ? 0.0 : Math.pow(10.0, gainDb / 20.0) - 1.0;
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private static void writeFloatHeader(OutputStream out, int sampleRate,
                                         int channels, long dataBytes) throws IOException {
        int bits = 32;
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
        LittleEndian.writeU16(out, bits);
        LittleEndian.writeAscii(out, "data");
        LittleEndian.writeU32(out, dataBytes);
    }

    private static final class EventControl {
        private static final double ATTACK_SECONDS = 0.060;
        private static final double RELEASE_SECONDS = 0.180;
        private final List<AdaptiveDiagnosticModel.Event> events = new ArrayList<>();
        private int cursor;

        EventControl(AdaptiveDiagnosticModel.Result diagnostics,
                     AdaptiveDiagnosticModel.Type type) {
            for (AdaptiveDiagnosticModel.Event event : diagnostics.events) {
                if (event.autoCorrection && event.type == type) events.add(event);
            }
            events.sort(Comparator.comparingDouble(e -> e.startSeconds));
        }

        double gainDbAt(double seconds) {
            while (cursor < events.size()
                    && seconds > events.get(cursor).endSeconds + RELEASE_SECONDS) cursor++;
            if (cursor >= events.size()) return 0.0;
            AdaptiveDiagnosticModel.Event event = events.get(cursor);
            if (seconds < event.startSeconds) return 0.0;
            if (seconds <= event.endSeconds) {
                double attack = Math.min(1.0,
                        (seconds - event.startSeconds) / ATTACK_SECONDS);
                return event.correctionDb * smooth(attack);
            }
            double release = 1.0 - Math.min(1.0,
                    (seconds - event.endSeconds) / RELEASE_SECONDS);
            return event.correctionDb * smooth(release);
        }

        private static double smooth(double value) {
            double x = Math.max(0.0, Math.min(1.0, value));
            return x * x * (3.0 - 2.0 * x);
        }
    }

    private static final class CorrectionStats {
        double maximumHighCutDb;
        double maximumHarshCutDb;
        double maximumMudCutDb;
        double maximumCombinedCutDb;
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

        void sample(double sample) throws IOException {
            if (position + 4 > buffer.length) flush();
            int bits = Float.floatToIntBits((float) finite(sample));
            buffer[position++] = (byte) (bits & 0xff);
            buffer[position++] = (byte) ((bits >>> 8) & 0xff);
            buffer[position++] = (byte) ((bits >>> 16) & 0xff);
            buffer[position++] = (byte) ((bits >>> 24) & 0xff);
        }

        void finish() throws IOException {
            flush();
        }

        void flush() throws IOException {
            if (position > 0) output.write(buffer, 0, position);
            position = 0;
        }
    }
}
