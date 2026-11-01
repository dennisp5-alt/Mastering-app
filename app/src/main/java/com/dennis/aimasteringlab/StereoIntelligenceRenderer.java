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

/** Renders the v0.5 authoritative-stereo intelligence candidates. */
final class StereoIntelligenceRenderer {
    enum Mode {
        EVENT_REPAIR("stereo_event_repair"),
        LOW_END("low_end_intelligence"),
        VOCAL_FOCUS("stereo_vocal_focus"),
        COMBINED("stereo_intelligence_combined");

        final String strategy;

        Mode(String strategy) {
            this.strategy = strategy;
        }
    }

    static final class DeltaVerification {
        long frames;
        double sourceCorrelation;
        double maximumAbsoluteDelta;
        double editedRmsDeltaDbfs;
        double protectedIntroMaxDelta;
        double protectedEndingMaxDelta;
        boolean protectedRegionsExact;
    }

    static final class Result {
        Mode mode;
        AutonomousMasteringRenderer.Result render;
        DeltaVerification delta;
        int correctionEvents;
        double maximumNominalEditDb;
        double modelConfidence;
        double evidenceBenefit;
        double measuredTargetBenefit;
        double spectralReductionDb;
        double lowEndReductionDb;
        double vocalFocusImprovementDb;
        boolean authoritativeStereoOnly = true;
        boolean stemWaveformUsed;
        String evidenceSummary;
    }

    private static final int BUFFER_FRAMES = 4096;
    private static final double MAX_SPECTRAL_CUT_DB = 0.80;
    private static final double MAX_LOW_CUT_DB = 0.65;
    private static final double MAX_TOTAL_NOMINAL_EDIT_DB = 0.90;

    private StereoIntelligenceRenderer() {}

    static Result render(AudioAnalyzer.StreamProvider source,
                         AnalysisResult sourceAnalysis,
                         AutonomousMasteringBrain.Plan transparentPlan,
                         Mode mode,
                         File output,
                         File workDirectory,
                         AudioAnalyzer.Progress progress) throws IOException {
        return render(source, sourceAnalysis, transparentPlan, mode, output,
                workDirectory, -1.0, false, progress);
    }

    static Result render(AudioAnalyzer.StreamProvider source,
                         AnalysisResult sourceAnalysis,
                         AutonomousMasteringBrain.Plan transparentPlan,
                         Mode mode,
                         File output,
                         File workDirectory,
                         double finalTruePeakCeilingDbtp,
                         boolean forceLimiter,
                         AudioAnalyzer.Progress progress) throws IOException {
        validate(sourceAnalysis, transparentPlan, mode);
        File corrected = new File(workDirectory,
                "stereo-intelligence-premaster-" + mode.strategy + "-"
                        + System.nanoTime() + ".wav");
        try {
            Stats stats = renderFloat(source, sourceAnalysis, mode, corrected, progress);
            AnalysisResult correctedAnalysis = AudioAnalyzer.analyze(corrected.getName(),
                    () -> new FileInputStream(corrected), progress);
            DeltaVerification delta;
            try {
                delta = verifyDelta(source,
                        () -> new FileInputStream(corrected), sourceAnalysis);
            } catch (IOException error) {
                long expectedBytes = 44L + sourceAnalysis.wav.frames * 2L * 4L;
                throw new IOException("Stereo-intelligence delta verification failed; "
                        + "premaster length " + corrected.length() + "/"
                        + expectedBytes + " bytes: " + error.getMessage(), error);
            }
            String guardrail = validatePremaster(sourceAnalysis, correctedAnalysis, delta);
            MasterRenderer.Result master = MasterRenderer.renderWithCeiling(
                    () -> new FileInputStream(corrected), correctedAnalysis,
                    correctedAnalysis.appliedSafeGainDb, output,
                    finalTruePeakCeilingDbtp, forceLimiter, progress);

            Result result = describe(sourceAnalysis.stereoIntelligence, mode, stats,
                    sourceAnalysis.wav.durationSeconds);
            result.delta = delta;
            AutonomousMasteringRenderer.Result wrapper =
                    new AutonomousMasteringRenderer.Result();
            wrapper.plan = transparentPlan;
            wrapper.master = master;
            wrapper.correctedPremaster = correctedAnalysis;
            wrapper.maximumLocalCutDb = result.maximumNominalEditDb;
            wrapper.maximumCompressionReductionDb = 0.0;
            wrapper.maximumGlobalEqDb = 0.0;
            wrapper.guardrailVerdict = guardrail;
            result.render = wrapper;
            master.aiGuided = true;
            master.aiCorrectionEvents = result.correctionEvents;
            master.maximumAiCorrectionDb = result.maximumNominalEditDb;
            master.aiCorrectionSummary = mode.strategy + "; " + result.evidenceSummary;
            return result;
        } finally {
            delete(corrected);
        }
    }

    private static void validate(AnalysisResult source,
                                 AutonomousMasteringBrain.Plan plan,
                                 Mode mode) throws IOException {
        if (source.wav.channels != 2 || source.wav.sampleRate != 48000) {
            throw new IOException("Stereo intelligence requires a 48 kHz stereo authoritative mix");
        }
        if (plan == null || plan.hasAudioChanges()) {
            throw new IOException("Stereo intelligence requires the transparent baseline plan");
        }
        StereoIntelligenceModel.Result model = source.stereoIntelligence;
        if (model == null) throw new IOException("Run stereo-intelligence analysis first");
        boolean available = mode == Mode.EVENT_REPAIR ? model.hasEventRepair()
                : mode == Mode.LOW_END ? model.hasLowEnd()
                : mode == Mode.VOCAL_FOCUS ? model.hasVocalFocus()
                : model.availableModules() >= 2;
        if (!available) {
            throw new IOException("The requested stereo-intelligence module has no eligible correction map");
        }
    }

    private static Stats renderFloat(AudioAnalyzer.StreamProvider source,
                                     AnalysisResult analysis,
                                     Mode mode,
                                     File output,
                                     AudioAnalyzer.Progress progress) throws IOException {
        long dataBytes = analysis.wav.frames * 2L * 4L;
        if (dataBytes > 0xffffffffL - 36L) {
            throw new IOException("Stereo-intelligence intermediate exceeds the RIFF/WAV 4 GB limit");
        }
        int rate = analysis.wav.sampleRate;
        StereoIntelligenceModel.Result model = analysis.stereoIntelligence;
        EventControl hf = control(model, mode, StereoIntelligenceModel.Kind.SPECTRAL_HF,
                analysis.wav.durationSeconds, 0.060, 0.180);
        EventControl harsh = control(model, mode, StereoIntelligenceModel.Kind.SPECTRAL_HARSH,
                analysis.wav.durationSeconds, 0.060, 0.180);
        EventControl mud = control(model, mode, StereoIntelligenceModel.Kind.SPECTRAL_MUD,
                analysis.wav.durationSeconds, 0.080, 0.220);
        EventControl sub = control(model, mode, StereoIntelligenceModel.Kind.LOW_SUB_BLOOM,
                analysis.wav.durationSeconds, 0.090, 0.260);
        EventControl bass = control(model, mode, StereoIntelligenceModel.Kind.LOW_BASS_BLOOM,
                analysis.wav.durationSeconds, 0.090, 0.240);
        EventControl lowPhase = control(model, mode, StereoIntelligenceModel.Kind.LOW_PHASE_RISK,
                analysis.wav.durationSeconds, 0.100, 0.280);
        EventControl vocal = control(model, mode, StereoIntelligenceModel.Kind.VOCAL_FOCUS,
                analysis.wav.durationSeconds, 0.100, 0.180);

        Biquad hfLeft = Biquad.highPass(rate, 7000.0);
        Biquad hfRight = Biquad.highPass(rate, 7000.0);
        BandPass harshLeft = new BandPass(rate, 2500.0, 7500.0);
        BandPass harshRight = new BandPass(rate, 2500.0, 7500.0);
        BandPass mudLeft = new BandPass(rate, 140.0, 520.0);
        BandPass mudRight = new BandPass(rate, 140.0, 520.0);
        Biquad subLeft = Biquad.lowPass(rate, 80.0);
        Biquad subRight = Biquad.lowPass(rate, 80.0);
        Biquad lowLeft = Biquad.lowPass(rate, 180.0);
        Biquad lowRight = Biquad.lowPass(rate, 180.0);
        Biquad lowSide = Biquad.lowPass(rate, 160.0);
        BandPass vocalMid = new BandPass(rate, 700.0, 4200.0);
        BandPass vocalSide = new BandPass(rate, 700.0, 4200.0);
        Stats stats = new Stats();
        stats.events = hf.count() + harsh.count() + mud.count() + sub.count()
                + bass.count() + lowPhase.count() + vocal.count();

        progress.update("Stereo intelligence: rendering protected float candidate", 0);
        try (WavFile.Reader reader = WavFile.open(source.open());
             FileOutputStream fileOutput = new FileOutputStream(output);
             OutputStream raw = new BufferedOutputStream(fileOutput, 256 * 1024)) {
            writeFloatHeader(raw, rate, 2, dataBytes);
            FloatWriter writer = new FloatWriter(raw);
            float[][] samples = new float[2][BUFFER_FRAMES];
            long absolute = 0L;
            int read;
            while ((read = reader.readFrames(samples, BUFFER_FRAMES)) > 0) {
                for (int frame = 0; frame < read; frame++, absolute++) {
                    double left = finite(samples[0][frame]);
                    double right = finite(samples[1][frame]);
                    double mid = 0.5 * (left + right);
                    double side = 0.5 * (left - right);
                    double seconds = absolute / (double) rate;

                    double hfBandL = hfLeft.process(left);
                    double hfBandR = hfRight.process(right);
                    double harshBandL = harshLeft.process(left);
                    double harshBandR = harshRight.process(right);
                    double mudBandL = mudLeft.process(left);
                    double mudBandR = mudRight.process(right);
                    double subBandL = subLeft.process(left);
                    double subBandR = subRight.process(right);
                    double lowBandL = lowLeft.process(left) - subBandL;
                    double lowBandR = lowRight.process(right) - subBandR;
                    double lowSideBand = lowSide.process(side);
                    double vocalMidBand = vocalMid.process(mid);
                    double vocalSideBand = vocalSide.process(side);

                    double hfDb = hf.primaryDbAt(seconds);
                    double harshDb = harsh.primaryDbAt(seconds);
                    double mudDb = mud.primaryDbAt(seconds);
                    double spectralTotal = -hfDb - harshDb - mudDb;
                    if (spectralTotal > MAX_SPECTRAL_CUT_DB) {
                        double scale = MAX_SPECTRAL_CUT_DB / spectralTotal;
                        hfDb *= scale;
                        harshDb *= scale;
                        mudDb *= scale;
                        spectralTotal = MAX_SPECTRAL_CUT_DB;
                    }

                    double subDb = sub.primaryDbAt(seconds);
                    double bassDb = bass.primaryDbAt(seconds);
                    double phaseDb = lowPhase.primaryDbAt(seconds);
                    double lowTotal = -subDb - bassDb - phaseDb;
                    if (lowTotal > MAX_LOW_CUT_DB) {
                        double scale = MAX_LOW_CUT_DB / lowTotal;
                        subDb *= scale;
                        bassDb *= scale;
                        phaseDb *= scale;
                        lowTotal = MAX_LOW_CUT_DB;
                    }

                    double vocalMidDb = vocal.primaryDbAt(seconds);
                    double vocalSideDb = vocal.secondaryDbAt(seconds);
                    double totalNominal = spectralTotal + lowTotal
                            + Math.abs(vocalMidDb) + Math.abs(vocalSideDb);
                    if (totalNominal > MAX_TOTAL_NOMINAL_EDIT_DB) {
                        double scale = MAX_TOTAL_NOMINAL_EDIT_DB / totalNominal;
                        hfDb *= scale;
                        harshDb *= scale;
                        mudDb *= scale;
                        subDb *= scale;
                        bassDb *= scale;
                        phaseDb *= scale;
                        vocalMidDb *= scale;
                        vocalSideDb *= scale;
                        totalNominal = MAX_TOTAL_NOMINAL_EDIT_DB;
                    }
                    stats.maximumNominalEditDb = Math.max(
                            stats.maximumNominalEditDb, totalNominal);

                    double correctedLeft = left
                            + gainDelta(hfDb) * hfBandL
                            + gainDelta(harshDb) * harshBandL
                            + gainDelta(mudDb) * mudBandL
                            + gainDelta(subDb) * subBandL
                            + gainDelta(bassDb) * lowBandL;
                    double correctedRight = right
                            + gainDelta(hfDb) * hfBandR
                            + gainDelta(harshDb) * harshBandR
                            + gainDelta(mudDb) * mudBandR
                            + gainDelta(subDb) * subBandR
                            + gainDelta(bassDb) * lowBandR;

                    double phaseDelta = gainDelta(phaseDb) * lowSideBand;
                    double vocalMidDelta = gainDelta(vocalMidDb) * vocalMidBand;
                    double vocalSideDelta = gainDelta(vocalSideDb) * vocalSideBand;
                    correctedLeft += phaseDelta + vocalMidDelta + vocalSideDelta;
                    correctedRight += -phaseDelta + vocalMidDelta - vocalSideDelta;

                    accumulateCut(stats, hfDb, hfBandL, hfBandR, true);
                    accumulateCut(stats, harshDb, harshBandL, harshBandR, true);
                    accumulateCut(stats, mudDb, mudBandL, mudBandR, true);
                    accumulateCut(stats, subDb, subBandL, subBandR, false);
                    accumulateCut(stats, bassDb, lowBandL, lowBandR, false);
                    if (phaseDb < -1.0e-9) {
                        stats.lowBefore += lowSideBand * lowSideBand;
                        double after = lowSideBand * dbGain(phaseDb);
                        stats.lowAfter += after * after;
                    }
                    if (Math.abs(vocalMidDb) > 1.0e-9
                            || Math.abs(vocalSideDb) > 1.0e-9) {
                        stats.vocalMidBefore += vocalMidBand * vocalMidBand;
                        stats.vocalSideBefore += vocalSideBand * vocalSideBand;
                        double afterMid = vocalMidBand * dbGain(vocalMidDb);
                        double afterSide = vocalSideBand * dbGain(vocalSideDb);
                        stats.vocalMidAfter += afterMid * afterMid;
                        stats.vocalSideAfter += afterSide * afterSide;
                    }
                    writer.frame(correctedLeft, correctedRight);
                }
                int percent = (int) Math.min(94,
                        Math.round(94.0 * absolute / Math.max(1L, reader.info.frames)));
                progress.update("Stereo intelligence: rendering protected float candidate", percent);
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("Stereo-intelligence rendering cancelled");
                }
            }
            writer.finish();
            raw.flush();
            fileOutput.getFD().sync();
            if (absolute != analysis.wav.frames) {
                throw new IOException("Stereo-intelligence frame accounting failed");
            }
        } catch (IOException error) {
            delete(output);
            throw error;
        }
        long expected = 44L + dataBytes;
        if (output.length() != expected) {
            long actual = output.length();
            delete(output);
            throw new IOException("Stereo-intelligence durable-length verification failed: expected "
                    + expected + " bytes, found " + actual);
        }
        stats.finishBenefits();
        return stats;
    }

    private static EventControl control(StereoIntelligenceModel.Result model,
                                        Mode mode,
                                        StereoIntelligenceModel.Kind kind,
                                        double duration,
                                        double attack,
                                        double release) {
        return new EventControl(model, mode, kind, duration, attack, release);
    }

    private static void accumulateCut(Stats stats, double gainDb,
                                      double left, double right,
                                      boolean spectral) {
        if (gainDb >= -1.0e-9) return;
        double before = left * left + right * right;
        double gain = dbGain(gainDb);
        double after = (left * gain) * (left * gain)
                + (right * gain) * (right * gain);
        if (spectral) {
            stats.spectralBefore += before;
            stats.spectralAfter += after;
        } else {
            stats.lowBefore += before;
            stats.lowAfter += after;
        }
    }

    private static Result describe(StereoIntelligenceModel.Result model,
                                   Mode mode, Stats stats, double duration) {
        Result out = new Result();
        out.mode = mode;
        out.correctionEvents = stats.events;
        out.maximumNominalEditDb = stats.maximumNominalEditDb;
        out.spectralReductionDb = stats.spectralReductionDb;
        out.lowEndReductionDb = stats.lowReductionDb;
        out.vocalFocusImprovementDb = stats.vocalFocusImprovementDb;
        double confidence = 0.0;
        double weighted = 0.0;
        int count = 0;
        for (StereoIntelligenceModel.Event event : model.events) {
            if (!event.automatic || !StereoIntelligenceModel.kindInMode(event.kind, mode)) {
                continue;
            }
            confidence += event.confidence;
            weighted += event.confidence
                    * (Math.abs(event.primaryDb) + Math.abs(event.secondaryDb))
                    * event.durationSeconds();
            count++;
        }
        out.modelConfidence = count == 0 ? 0.0 : confidence / count;
        out.evidenceBenefit = clamp(weighted
                / Math.max(1.0, duration * 0.08), 0.0, 1.0);
        out.measuredTargetBenefit = stats.measuredTargetBenefit;
        out.evidenceSummary = String.format(Locale.US,
                "%d authoritative-stereo event(s); confidence %.3f; evidence %.3f; measured targeted benefit %.3f; spectral reduction %.3f dB; low reduction %.3f dB; vocal focus +%.3f dB; no stem waveform used",
                out.correctionEvents, out.modelConfidence, out.evidenceBenefit,
                out.measuredTargetBenefit, out.spectralReductionDb,
                out.lowEndReductionDb, out.vocalFocusImprovementDb);
        return out;
    }

    private static String validatePremaster(AnalysisResult source,
                                            AnalysisResult corrected,
                                            DeltaVerification delta) throws IOException {
        if (!corrected.wav.floatingPoint || corrected.wav.channels != 2
                || corrected.wav.sampleRate != source.wav.sampleRate
                || corrected.wav.frames != source.wav.frames) {
            throw new IOException("Stereo-intelligence pre-master changed format or duration");
        }
        if (corrected.nonFiniteSamples != 0) {
            throw new IOException("Stereo-intelligence pre-master introduced non-finite samples");
        }
        if (corrected.clippedSamples > source.clippedSamples) {
            throw new IOException(String.format(Locale.US,
                    "Stereo-intelligence pre-master introduced %d additional full-scale sample(s); source already contained %d",
                    corrected.clippedSamples - source.clippedSamples,
                    source.clippedSamples));
        }
        if (corrected.internalZeroRuns > source.internalZeroRuns) {
            throw new IOException("Stereo-intelligence pre-master introduced an exact-zero interruption");
        }
        if (!delta.protectedRegionsExact) {
            throw new IOException("Stereo-intelligence edit leaked into the protected intro or ending");
        }
        if (delta.maximumAbsoluteDelta <= 1.0e-8
                || delta.editedRmsDeltaDbfs < -145.0) {
            throw new IOException("Stereo-intelligence candidate produced no measurable edit");
        }
        if (delta.sourceCorrelation < 0.995) {
            throw new IOException("Stereo-intelligence edit exceeded the source-correlation guardrail");
        }
        if (corrected.stereoCorrelation < source.stereoCorrelation - 0.020
                || corrected.lowBandCorrelation < source.lowBandCorrelation - 0.010) {
            throw new IOException("Stereo-intelligence edit worsened stereo or low-band correlation");
        }
        if (source.lraLu - corrected.lraLu > 0.65) {
            throw new IOException("Stereo-intelligence edit reduced LRA beyond 0.65 LU");
        }
        return String.format(Locale.US,
                "STEREO-INTELLIGENCE PREMASTER PASS: source correlation %.7f; inherited full-scale samples %d→%d (no increase); protected intro/tail %.2g/%.2g; duration, continuity, phase and LRA verified",
                delta.sourceCorrelation, source.clippedSamples,
                corrected.clippedSamples, delta.protectedIntroMaxDelta,
                delta.protectedEndingMaxDelta);
    }

    private static DeltaVerification verifyDelta(
            AudioAnalyzer.StreamProvider source,
            AudioAnalyzer.StreamProvider corrected,
            AnalysisResult analysis) throws IOException {
        DeltaVerification out = new DeltaVerification();
        long introEnd = Math.round(2.0 * analysis.wav.sampleRate);
        long endingStart = Math.max(0L, analysis.wav.frames - Math.round(
                Math.max(8.0, analysis.wav.durationSeconds * 0.04)
                        * analysis.wav.sampleRate));
        double sx = 0.0, sy = 0.0, sxx = 0.0, syy = 0.0, sxy = 0.0;
        double editedSquares = 0.0;
        long editedSamples = 0L;
        try (WavFile.Reader reference = WavFile.open(source.open());
             WavFile.Reader test = WavFile.open(corrected.open())) {
            float[][] a = new float[2][BUFFER_FRAMES];
            float[][] b = new float[2][BUFFER_FRAMES];
            long frame = 0L;
            int read;
            while ((read = reference.readFrames(a, BUFFER_FRAMES)) > 0) {
                int testRead = test.readFrames(b, read);
                if (testRead != read) throw new IOException("Stereo-intelligence delta ended early");
                for (int i = 0; i < read; i++, frame++) {
                    double dl = finite(b[0][i]) - finite(a[0][i]);
                    double dr = finite(b[1][i]) - finite(a[1][i]);
                    out.maximumAbsoluteDelta = Math.max(out.maximumAbsoluteDelta,
                            Math.max(Math.abs(dl), Math.abs(dr)));
                    double av = 0.5 * (finite(a[0][i]) + finite(a[1][i]));
                    double bv = 0.5 * (finite(b[0][i]) + finite(b[1][i]));
                    sx += av;
                    sy += bv;
                    sxx += av * av;
                    syy += bv * bv;
                    sxy += av * bv;
                    if (frame < introEnd) {
                        out.protectedIntroMaxDelta = Math.max(
                                out.protectedIntroMaxDelta,
                                Math.max(Math.abs(dl), Math.abs(dr)));
                    } else if (frame >= endingStart) {
                        out.protectedEndingMaxDelta = Math.max(
                                out.protectedEndingMaxDelta,
                                Math.max(Math.abs(dl), Math.abs(dr)));
                    } else {
                        editedSquares += dl * dl + dr * dr;
                        editedSamples += 2;
                    }
                }
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("Stereo-intelligence delta verification cancelled");
                }
            }
            if (frame != analysis.wav.frames || test.readFrames(b, 1) > 0) {
                throw new IOException("Stereo-intelligence delta changed the sample count");
            }
            out.frames = frame;
        }
        double numerator = out.frames * sxy - sx * sy;
        double denominator = Math.sqrt(Math.max(0.0, out.frames * sxx - sx * sx)
                * Math.max(0.0, out.frames * syy - sy * sy));
        out.sourceCorrelation = denominator <= 1.0e-30 ? 0.0
                : clamp(numerator / denominator, -1.0, 1.0);
        out.editedRmsDeltaDbfs = editedSamples <= 0 || editedSquares <= 0.0
                ? -300.0 : 10.0 * Math.log10(editedSquares / editedSamples);
        out.protectedRegionsExact = out.protectedIntroMaxDelta <= 1.0e-7
                && out.protectedEndingMaxDelta <= 1.0e-7;
        return out;
    }

    private static double gainDelta(double db) {
        return Math.abs(db) < 1.0e-12 ? 0.0 : dbGain(db) - 1.0;
    }

    private static double dbGain(double db) {
        return Math.pow(10.0, db / 20.0);
    }

    private static double reductionDb(double before, double after) {
        return before <= 1.0e-20 || after <= 1.0e-20 ? 0.0
                : Math.max(0.0, 10.0 * Math.log10(before / after));
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

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static void delete(File file) {
        if (file != null && file.exists() && !file.delete()) file.deleteOnExit();
    }

    private static final class Stats {
        int events;
        double maximumNominalEditDb;
        double spectralBefore;
        double spectralAfter;
        double lowBefore;
        double lowAfter;
        double vocalMidBefore;
        double vocalSideBefore;
        double vocalMidAfter;
        double vocalSideAfter;
        double spectralReductionDb;
        double lowReductionDb;
        double vocalFocusImprovementDb;
        double measuredTargetBenefit;

        void finishBenefits() {
            spectralReductionDb = reductionDb(spectralBefore, spectralAfter);
            lowReductionDb = reductionDb(lowBefore, lowAfter);
            if (vocalMidBefore > 1.0e-20 && vocalSideBefore > 1.0e-20
                    && vocalMidAfter > 1.0e-20 && vocalSideAfter > 1.0e-20) {
                double before = 10.0 * Math.log10(vocalMidBefore / vocalSideBefore);
                double after = 10.0 * Math.log10(vocalMidAfter / vocalSideAfter);
                vocalFocusImprovementDb = Math.max(0.0, after - before);
            }
            double total = 0.0;
            int count = 0;
            if (spectralBefore > 1.0e-20) {
                total += clamp(spectralReductionDb / 0.20, 0.0, 1.0);
                count++;
            }
            if (lowBefore > 1.0e-20) {
                total += clamp(lowReductionDb / 0.18, 0.0, 1.0);
                count++;
            }
            if (vocalMidBefore > 1.0e-20) {
                total += clamp(vocalFocusImprovementDb / 0.20, 0.0, 1.0);
                count++;
            }
            measuredTargetBenefit = count == 0 ? 0.0 : total / count;
        }
    }

    private static final class EventControl {
        private final List<StereoIntelligenceModel.Event> events = new ArrayList<>();
        private final double protectedEndingStart;
        private final double attack;
        private final double release;
        private int cursor;

        EventControl(StereoIntelligenceModel.Result model, Mode mode,
                     StereoIntelligenceModel.Kind kind, double duration,
                     double attack, double release) {
            this.protectedEndingStart = Math.max(2.0,
                    duration - Math.max(8.0, duration * 0.04));
            this.attack = attack;
            this.release = release;
            for (StereoIntelligenceModel.Event event : model.events) {
                if (event.automatic && event.kind == kind
                        && StereoIntelligenceModel.kindInMode(kind, mode)) {
                    events.add(event);
                }
            }
            events.sort(Comparator.comparingDouble(event -> event.startSeconds));
        }

        int count() {
            return events.size();
        }

        double primaryDbAt(double seconds) {
            StereoIntelligenceModel.Event event = eventAt(seconds);
            return event == null ? 0.0 : event.primaryDb * envelope(event, seconds);
        }

        double secondaryDbAt(double seconds) {
            StereoIntelligenceModel.Event event = eventAt(seconds);
            return event == null ? 0.0 : event.secondaryDb * envelope(event, seconds);
        }

        private StereoIntelligenceModel.Event eventAt(double seconds) {
            if (seconds < 2.0 || seconds >= protectedEndingStart) return null;
            while (cursor < events.size()
                    && seconds > events.get(cursor).endSeconds + release) cursor++;
            if (cursor >= events.size()) return null;
            StereoIntelligenceModel.Event event = events.get(cursor);
            if (seconds < event.startSeconds || seconds > event.endSeconds + release) {
                return null;
            }
            return event;
        }

        private double envelope(StereoIntelligenceModel.Event event, double seconds) {
            if (seconds <= event.endSeconds) {
                return smooth(Math.min(1.0,
                        (seconds - event.startSeconds) / Math.max(0.001, attack)));
            }
            return smooth(1.0 - Math.min(1.0,
                    (seconds - event.endSeconds) / Math.max(0.001, release)));
        }

        private static double smooth(double value) {
            double x = clamp(value, 0.0, 1.0);
            return x * x * (3.0 - 2.0 * x);
        }
    }

    private static final class BandPass {
        private final Biquad highA;
        private final Biquad highB;
        private final Biquad lowA;
        private final Biquad lowB;

        BandPass(double rate, double low, double high) {
            highA = Biquad.highPass(rate, low);
            highB = Biquad.highPass(rate, low);
            lowA = Biquad.lowPass(rate, high);
            lowB = Biquad.lowPass(rate, high);
        }

        double process(double sample) {
            return lowB.process(lowA.process(highB.process(highA.process(sample))));
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
