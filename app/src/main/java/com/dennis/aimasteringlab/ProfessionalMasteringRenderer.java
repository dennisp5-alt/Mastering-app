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

/** Renders the four guarded v0.8 professional mastering modules. */
final class ProfessionalMasteringRenderer {
    enum Mode {
        STEREO_DEPTH_REVERB("stereo_depth_reverb_intelligence"),
        LIMITER_CREST("limiter_crest_optimisation"),
        LOW_FREQUENCY_PHASE_RESCUE("low_frequency_phase_rescue"),
        VOCAL_ARTIFACT("vocal_artifact_intelligence"),
        VOCAL_ARTIFACT_GENTLE("vocal_artifact_gentle"),
        PROFESSIONAL_COMBINED("v08_professional_combined");

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
        LimiterCrestOptimizer.Result limiterOptimisation;
        int correctionEvents;
        double maximumNominalEditDb;
        double modelConfidence;
        double evidenceBenefit;
        double measuredTargetBenefit;
        double depthSideReductionDb;
        double lowPhaseImprovement;
        double vocalArtifactReductionDb;
        boolean authoritativeStereoOnly = true;
        boolean stemWaveformUsed;
        boolean wideningUsed;
        String evidenceSummary;
    }

    private static final int BUFFER_FRAMES = 4096;
    private static final double MAX_EVENT_CUT_DB = 0.65;
    private static final double MAX_TOTAL_NOMINAL_EDIT_DB = 0.85;

    private ProfessionalMasteringRenderer() {}

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
        if (mode == Mode.LIMITER_CREST) {
            return renderLimiterOnly(source, sourceAnalysis,
                    transparentPlan, output, workDirectory,
                    finalTruePeakCeilingDbtp, forceLimiter, progress);
        }

        File corrected = new File(workDirectory,
                "v08-professional-premaster-" + mode.strategy + "-"
                        + System.nanoTime() + ".wav");
        try {
            Stats stats = renderFloat(source, sourceAnalysis, mode,
                    corrected, progress);
            AnalysisResult correctedAnalysis = AudioAnalyzer.analyze(
                    corrected.getName(), () -> new FileInputStream(corrected),
                    progress);
            DeltaVerification delta = verifyDelta(source,
                    () -> new FileInputStream(corrected), sourceAnalysis);
            String guardrail = validatePremaster(sourceAnalysis,
                    correctedAnalysis, delta, stats);

            MasterRenderer.Result master;
            LimiterCrestOptimizer.Result limiter = null;
            boolean optimiseLimiter = (mode == Mode.PROFESSIONAL_COMBINED || isVocal(mode))
                    && sourceAnalysis.professionalIntelligence
                    .limiterCrestAvailable
                    && (LimiterCrestOptimizer.available(correctedAnalysis)
                    || forceLimiter);
            if (optimiseLimiter) {
                limiter = LimiterCrestOptimizer.render(
                        () -> new FileInputStream(corrected),
                        correctedAnalysis, output, workDirectory,
                        finalTruePeakCeilingDbtp, forceLimiter,
                        isVocal(mode), progress);
                master = limiter.master;
            } else {
                master = MasterRenderer.renderWithCeiling(
                        () -> new FileInputStream(corrected),
                        correctedAnalysis,
                        correctedAnalysis.appliedSafeGainDb, output,
                        finalTruePeakCeilingDbtp, forceLimiter, progress);
            }
            Result result = describe(sourceAnalysis.professionalIntelligence,
                    mode, stats, correctedAnalysis, sourceAnalysis,
                    limiter, sourceAnalysis.wav.durationSeconds);
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
            result.limiterOptimisation = limiter;
            markMaster(master, result);
            return result;
        } finally {
            delete(corrected);
        }
    }

    private static Result renderLimiterOnly(
            AudioAnalyzer.StreamProvider source,
            AnalysisResult analysis,
            AutonomousMasteringBrain.Plan transparentPlan,
            File output,
            File workDirectory,
            double finalTruePeakCeilingDbtp,
            boolean forceLimiter,
            AudioAnalyzer.Progress progress) throws IOException {
        LimiterCrestOptimizer.Result limiter = LimiterCrestOptimizer.render(
                source, analysis, output, workDirectory,
                finalTruePeakCeilingDbtp, forceLimiter, progress);
        Result result = new Result();
        result.mode = Mode.LIMITER_CREST;
        result.limiterOptimisation = limiter;
        result.modelConfidence = 0.94;
        result.evidenceBenefit = limiter.alternativeProfileSelected
                ? Math.max(0.10, limiter.measuredTargetBenefit) : 0.04;
        result.measuredTargetBenefit = limiter.measuredTargetBenefit;
        result.evidenceSummary = limiter.decision
                + "; limiter profiles compared only where the limiter engages; no pre-master EQ, width or event correction was used";
        AutonomousMasteringRenderer.Result wrapper =
                new AutonomousMasteringRenderer.Result();
        wrapper.plan = transparentPlan;
        wrapper.master = limiter.master;
        wrapper.correctedPremaster = analysis;
        wrapper.guardrailVerdict = "V0.8 LIMITER PASS: applicable profile renders were verified and the lowest-cost safe crest/dynamics result was retained";
        result.render = wrapper;
        markMaster(limiter.master, result);
        return result;
    }

    private static void markMaster(MasterRenderer.Result master,
                                   Result result) {
        master.aiGuided = true;
        master.aiCorrectionEvents = result.correctionEvents;
        master.maximumAiCorrectionDb = result.maximumNominalEditDb;
        master.aiCorrectionSummary = result.mode.strategy + "; "
                + result.evidenceSummary;
    }

    private static void validate(AnalysisResult source,
                                 AutonomousMasteringBrain.Plan plan,
                                 Mode mode) throws IOException {
        if (source.wav.channels != 2 || source.wav.sampleRate != 48000) {
            throw new IOException("v0.8 professional intelligence requires a 48 kHz stereo authoritative mix");
        }
        if (plan == null || plan.hasAudioChanges()) {
            throw new IOException("v0.8 professional intelligence requires the transparent baseline plan");
        }
        ProfessionalMasteringModel.Result model =
                source.professionalIntelligence;
        if (model == null) {
            throw new IOException("Run v0.8 professional analysis first");
        }
        boolean available = mode == Mode.STEREO_DEPTH_REVERB
                ? model.hasDepthReverb()
                : mode == Mode.LIMITER_CREST
                ? model.limiterCrestAvailable
                : mode == Mode.LOW_FREQUENCY_PHASE_RESCUE
                ? model.hasLowPhase()
                : isVocal(mode)
                ? model.hasVocalArtifacts()
                : model.availableModules() >= 2;
        if (!available) {
            throw new IOException("The requested v0.8 professional module has no eligible correction map");
        }
    }

    private static Stats renderFloat(AudioAnalyzer.StreamProvider source,
                                     AnalysisResult analysis,
                                     Mode mode,
                                     File output,
                                     AudioAnalyzer.Progress progress) throws IOException {
        long dataBytes = analysis.wav.frames * 2L * 4L;
        if (dataBytes > 0xffffffffL - 36L) {
            throw new IOException("v0.8 professional intermediate exceeds the RIFF/WAV 4 GB limit");
        }
        ProfessionalMasteringModel.Result model =
                analysis.professionalIntelligence;
        double duration = analysis.wav.durationSeconds;
        int rate = analysis.wav.sampleRate;

        EventControl depthVocal = control(model, mode,
                ProfessionalMasteringModel.Kind.DEPTH_SIDE_REVERB,
                duration, 0.050, 0.180);
        EventControl depthHigh = control(model, mode,
                ProfessionalMasteringModel.Kind.DEPTH_HIGH_WIDTH,
                duration, 0.035, 0.150);
        EventControl phase25 = control(model, mode,
                ProfessionalMasteringModel.Kind.LF_PHASE_25_60,
                duration, 0.070, 0.220);
        EventControl phase60 = control(model, mode,
                ProfessionalMasteringModel.Kind.LF_PHASE_60_100,
                duration, 0.055, 0.180);
        EventControl phase100 = control(model, mode,
                ProfessionalMasteringModel.Kind.LF_PHASE_100_160,
                duration, 0.045, 0.150);
        EventControl sibilance = control(model, mode,
                ProfessionalMasteringModel.Kind.VOCAL_SIBILANCE,
                duration, 0.010, 0.090);
        EventControl plosive = control(model, mode,
                ProfessionalMasteringModel.Kind.VOCAL_PLOSIVE,
                duration, 0.004, 0.065);
        EventControl harsh = control(model, mode,
                ProfessionalMasteringModel.Kind.VOCAL_HARSH_VOWEL,
                duration, 0.020, 0.120);

        BandPass depthVocalBand = new BandPass(rate, 700.0, 6500.0);
        BandPass depthHighBand = new BandPass(rate, 6500.0, 10000.0);
        BandPass phase25Band = new BandPass(rate, 25.0, 60.0);
        BandPass phase60Band = new BandPass(rate, 60.0, 100.0);
        BandPass phase100Band = new BandPass(rate, 100.0, 160.0);
        BandPass sibilanceBand = new BandPass(rate, 6500.0, 10000.0);
        BandPass plosiveBand = new BandPass(rate, 25.0, 160.0);
        BandPass harshBand = new BandPass(rate, 1600.0, 3200.0);
        TrackingDynamicEq tracking = isVocal(mode) ? new TrackingDynamicEq(model, duration,
                mode == Mode.VOCAL_ARTIFACT_GENTLE ? .60 : 1.0) : null;
        Stats stats = new Stats();
        stats.events = depthVocal.count() + depthHigh.count()
                + phase25.count() + phase60.count() + phase100.count()
                + sibilance.count() + plosive.count() + harsh.count();

        progress.update("Professional intelligence: rendering protected float candidate", 0);
        try (WavFile.Reader reader = WavFile.open(source.open());
             FileOutputStream fileOutput = new FileOutputStream(output);
             OutputStream raw = new BufferedOutputStream(fileOutput,
                     256 * 1024)) {
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

                    double dV = Math.max(-MAX_EVENT_CUT_DB,
                            depthVocal.dbAt(seconds));
                    double dH = Math.max(-MAX_EVENT_CUT_DB,
                            depthHigh.dbAt(seconds));
                    double p25 = Math.max(-MAX_EVENT_CUT_DB,
                            phase25.dbAt(seconds));
                    double p60 = Math.max(-MAX_EVENT_CUT_DB,
                            phase60.dbAt(seconds));
                    double p100 = Math.max(-MAX_EVENT_CUT_DB,
                            phase100.dbAt(seconds));
                    double amount = mode == Mode.VOCAL_ARTIFACT_GENTLE ? .60 : 1.0;
                    double vocalCap = isVocal(mode) ? 1.20 : MAX_EVENT_CUT_DB;
                    double sib = Math.max(-vocalCap,
                            sibilance.dbAt(seconds) * amount);
                    double plo = Math.max(-vocalCap,
                            plosive.dbAt(seconds) * amount);
                    double har = Math.max(-vocalCap,
                            harsh.dbAt(seconds) * amount);
                    double total = -dV - dH - p25 - p60 - p100
                            - sib - plo - har;
                    double totalCap = isVocal(mode) ? 1.50 : MAX_TOTAL_NOMINAL_EDIT_DB;
                    if (total > totalCap) {
                        double scale = totalCap / total;
                        dV *= scale;
                        dH *= scale;
                        p25 *= scale;
                        p60 *= scale;
                        p100 *= scale;
                        sib *= scale;
                        plo *= scale;
                        har *= scale;
                        total = totalCap;
                    }
                    stats.maximumNominalEditDb = Math.max(
                            stats.maximumNominalEditDb, total);

                    double sideVocal = depthVocalBand.process(side);
                    double sideHigh = depthHighBand.process(side);
                    double side25 = phase25Band.process(side);
                    double side60 = phase60Band.process(side);
                    double side100 = phase100Band.process(side);
                    double midSib = sibilanceBand.process(mid);
                    double midPlosive = plosiveBand.process(mid);
                    double midHarsh = harshBand.process(mid);
                    double correctedSide = side
                            + gainDelta(dV) * sideVocal
                            + gainDelta(dH) * sideHigh
                            + gainDelta(p25) * side25
                            + gainDelta(p60) * side60
                            + gainDelta(p100) * side100;
                    double correctedMid = tracking != null ? tracking.process(mid, seconds) : mid
                            + gainDelta(sib) * midSib
                            + gainDelta(plo) * midPlosive
                            + gainDelta(har) * midHarsh;
                    double correctedLeft = correctedMid + correctedSide;
                    double correctedRight = correctedMid - correctedSide;

                    accumulate(stats, dV, sideVocal, Target.DEPTH);
                    accumulate(stats, dH, sideHigh, Target.DEPTH);
                    accumulate(stats, p25, side25, Target.PHASE);
                    accumulate(stats, p60, side60, Target.PHASE);
                    accumulate(stats, p100, side100, Target.PHASE);
                    accumulate(stats, sib, midSib, Target.VOCAL);
                    accumulate(stats, plo, midPlosive, Target.VOCAL);
                    accumulate(stats, har, midHarsh, Target.VOCAL);
                    if (dV < 0.0 || dH < 0.0 || p25 < 0.0
                            || p60 < 0.0 || p100 < 0.0) {
                        stats.sideBefore += side * side;
                        stats.sideAfter += correctedSide * correctedSide;
                    }
                    writer.frame(correctedLeft, correctedRight);
                }
                int percent = (int) Math.min(94, Math.round(94.0 * absolute
                        / Math.max(1L, reader.info.frames)));
                progress.update("Professional intelligence: rendering protected float candidate", percent);
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("Professional-intelligence rendering cancelled");
                }
            }
            writer.finish();
            raw.flush();
            fileOutput.getFD().sync();
            if (absolute != analysis.wav.frames) {
                throw new IOException("v0.8 professional frame accounting failed");
            }
        } catch (IOException error) {
            delete(output);
            throw error;
        }
        long expected = 44L + dataBytes;
        if (output.length() != expected) {
            long actual = output.length();
            delete(output);
            throw new IOException("v0.8 professional durable-length verification failed: expected "
                    + expected + " bytes, found " + actual);
        }
        stats.finish();
        return stats;
    }

    private static EventControl control(
            ProfessionalMasteringModel.Result model, Mode mode,
            ProfessionalMasteringModel.Kind kind, double duration,
            double attack, double release) {
        return new EventControl(model, mode, kind, duration, attack,
                release);
    }

    private static void accumulate(Stats stats, double gainDb,
                                   double value, Target target) {
        if (gainDb >= -1.0e-9) return;
        double before = value * value;
        double afterValue = value * dbGain(gainDb);
        double after = afterValue * afterValue;
        if (target == Target.DEPTH) {
            stats.depthBefore += before;
            stats.depthAfter += after;
        } else if (target == Target.PHASE) {
            stats.phaseBefore += before;
            stats.phaseAfter += after;
        } else {
            stats.vocalBefore += before;
            stats.vocalAfter += after;
        }
    }

    private static Result describe(
            ProfessionalMasteringModel.Result model, Mode mode, Stats stats,
            AnalysisResult corrected, AnalysisResult source,
            LimiterCrestOptimizer.Result limiter, double duration) {
        Result out = new Result();
        out.mode = mode;
        out.correctionEvents = stats.events;
        out.maximumNominalEditDb = stats.maximumNominalEditDb;
        out.depthSideReductionDb = stats.depthReductionDb;
        out.lowPhaseImprovement = Math.max(0.0,
                corrected.lowBandCorrelation - source.lowBandCorrelation)
                + 0.10 * stats.phaseReductionDb;
        out.vocalArtifactReductionDb = stats.vocalReductionDb;
        double confidence = 0.0;
        double weighted = 0.0;
        int count = 0;
        for (ProfessionalMasteringModel.Event event : model.events) {
            if (!event.automatic || !kindInMode(event.kind, mode)) continue;
            confidence += event.confidence;
            weighted += event.confidence * Math.abs(event.primaryDb)
                    * Math.max(0.006, event.durationSeconds());
            count++;
        }
        out.modelConfidence = count == 0
                ? (limiter == null ? 0.0 : 0.94) : confidence / count;
        out.evidenceBenefit = clamp(weighted
                / Math.max(0.40, duration * 0.035), 0.0, 1.0);
        double total = 0.0;
        int modules = 0;
        if (stats.depthBefore > 1.0e-20) {
            total += clamp(stats.depthReductionDb / 0.08, 0.0, 1.0);
            modules++;
        }
        if (stats.phaseBefore > 1.0e-20) {
            total += clamp(out.lowPhaseImprovement / 0.025, 0.0, 1.0);
            modules++;
        }
        if (stats.vocalBefore > 1.0e-20) {
            total += clamp(stats.vocalReductionDb / 0.08, 0.0, 1.0);
            modules++;
        }
        if (limiter != null) {
            total += limiter.measuredTargetBenefit;
            modules++;
        }
        out.measuredTargetBenefit = modules == 0 ? 0.0
                : total / modules;
        out.evidenceSummary = String.format(Locale.US,
                "%d event(s); confidence %.3f; evidence %.3f; measured target benefit %.3f; depth side reduction %.3f dB; LF phase benefit %.4f; vocal-artifact reduction %.3f dB; limiter profile %s; no widening, no stem waveform and no source replacement",
                out.correctionEvents, out.modelConfidence,
                out.evidenceBenefit, out.measuredTargetBenefit,
                out.depthSideReductionDb, out.lowPhaseImprovement,
                out.vocalArtifactReductionDb,
                limiter == null || limiter.selected == null
                        ? "not auditioned" : limiter.selected.profile.label);
        return out;
    }

    private static String validatePremaster(AnalysisResult source,
                                            AnalysisResult corrected,
                                            DeltaVerification delta,
                                            Stats stats) throws IOException {
        if (!corrected.wav.floatingPoint || corrected.wav.channels != 2
                || corrected.wav.sampleRate != source.wav.sampleRate
                || corrected.wav.frames != source.wav.frames) {
            throw new IOException("v0.8 professional pre-master changed format or duration");
        }
        if (corrected.nonFiniteSamples != 0) {
            throw new IOException("v0.8 professional pre-master introduced non-finite samples");
        }
        if (corrected.clippedSamples > source.clippedSamples) {
            throw new IOException(String.format(Locale.US,
                    "v0.8 professional pre-master introduced %d additional full-scale sample(s); source already contained %d",
                    corrected.clippedSamples - source.clippedSamples,
                    source.clippedSamples));
        }
        if (corrected.internalZeroRuns > source.internalZeroRuns) {
            throw new IOException("v0.8 professional pre-master introduced an exact-zero interruption");
        }
        if (!delta.protectedRegionsExact) {
            throw new IOException("v0.8 professional edit leaked into the protected intro or ending");
        }
        if (delta.maximumAbsoluteDelta <= 1.0e-8
                || delta.editedRmsDeltaDbfs < -145.0) {
            throw new IOException("v0.8 professional candidate produced no measurable edit");
        }
        if (delta.sourceCorrelation < 0.9950) {
            throw new IOException("v0.8 professional edit exceeded the source-correlation guardrail");
        }
        if (corrected.stereoCorrelation < source.stereoCorrelation - 0.010
                || corrected.lowBandCorrelation
                < source.lowBandCorrelation - 0.002) {
            throw new IOException("v0.8 professional edit worsened stereo or low-frequency phase correlation");
        }
        if (stats.sideBefore > 1.0e-20
                && stats.sideAfter > stats.sideBefore * 1.000001) {
            throw new IOException("v0.8 depth/phase processing increased side energy; widening is prohibited");
        }
        if (source.lraLu - corrected.lraLu > 0.45) {
            throw new IOException("v0.8 professional edit reduced LRA beyond 0.45 LU");
        }
        return String.format(Locale.US,
                "V0.8 PROFESSIONAL PREMASTER PASS: source correlation %.7f; inherited full-scale samples %d→%d (no increase); protected intro/tail %.2g/%.2g; no widening; duration, continuity, phase and LRA verified",
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
                if (testRead != read) {
                    throw new IOException("v0.8 professional delta ended early");
                }
                for (int i = 0; i < read; i++, frame++) {
                    double al = finite(a[0][i]);
                    double ar = finite(a[1][i]);
                    double bl = finite(b[0][i]);
                    double br = finite(b[1][i]);
                    double dl = bl - al;
                    double dr = br - ar;
                    out.maximumAbsoluteDelta = Math.max(
                            out.maximumAbsoluteDelta,
                            Math.max(Math.abs(dl), Math.abs(dr)));
                    double av = 0.5 * (al + ar);
                    double bv = 0.5 * (bl + br);
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
                        editedSamples += 2L;
                    }
                }
            }
            if (frame != analysis.wav.frames || test.readFrames(b, 1) > 0) {
                throw new IOException("v0.8 professional delta changed the sample count");
            }
            out.frames = frame;
        }
        double numerator = out.frames * sxy - sx * sy;
        double denominator = Math.sqrt(Math.max(0.0,
                out.frames * sxx - sx * sx)
                * Math.max(0.0, out.frames * syy - sy * sy));
        out.sourceCorrelation = denominator <= 1.0e-30 ? 0.0
                : clamp(numerator / denominator, -1.0, 1.0);
        out.editedRmsDeltaDbfs = editedSamples <= 0
                || editedSquares <= 0.0 ? -300.0
                : 10.0 * Math.log10(editedSquares / editedSamples);
        out.protectedRegionsExact = out.protectedIntroMaxDelta <= 1.0e-7
                && out.protectedEndingMaxDelta <= 1.0e-7;
        return out;
    }

    static boolean isVocal(Mode mode) {
        return mode==Mode.VOCAL_ARTIFACT || mode==Mode.VOCAL_ARTIFACT_GENTLE;
    }

    static boolean kindInMode(ProfessionalMasteringModel.Kind kind,
                              Mode mode) {
        if (mode == Mode.PROFESSIONAL_COMBINED) {
            return kind != ProfessionalMasteringModel.Kind
                    .DEPTH_FLAT_DIAGNOSTIC;
        }
        if (mode == Mode.STEREO_DEPTH_REVERB) {
            return kind == ProfessionalMasteringModel.Kind.DEPTH_SIDE_REVERB
                    || kind == ProfessionalMasteringModel.Kind
                    .DEPTH_HIGH_WIDTH;
        }
        if (mode == Mode.LOW_FREQUENCY_PHASE_RESCUE) {
            return kind == ProfessionalMasteringModel.Kind.LF_PHASE_25_60
                    || kind == ProfessionalMasteringModel.Kind.LF_PHASE_60_100
                    || kind == ProfessionalMasteringModel.Kind
                    .LF_PHASE_100_160;
        }
        if (isVocal(mode)) {
            return kind == ProfessionalMasteringModel.Kind.VOCAL_SIBILANCE
                    || kind == ProfessionalMasteringModel.Kind.VOCAL_PLOSIVE
                    || kind == ProfessionalMasteringModel.Kind
                    .VOCAL_HARSH_VOWEL;
        }
        return false;
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
                                         int channels,
                                         long dataBytes) throws IOException {
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

    private static double clamp(double value, double minimum,
                                double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static void delete(File file) {
        if (file != null && file.exists() && !file.delete()) {
            file.deleteOnExit();
        }
    }

    private enum Target { DEPTH, PHASE, VOCAL }

    private static final class Stats {
        int events;
        double maximumNominalEditDb;
        double depthBefore, depthAfter;
        double phaseBefore, phaseAfter;
        double vocalBefore, vocalAfter;
        double sideBefore, sideAfter;
        double depthReductionDb;
        double phaseReductionDb;
        double vocalReductionDb;

        void finish() {
            depthReductionDb = reductionDb(depthBefore, depthAfter);
            phaseReductionDb = reductionDb(phaseBefore, phaseAfter);
            vocalReductionDb = reductionDb(vocalBefore, vocalAfter);
        }
    }

    private static final class EventControl {
        private final List<ProfessionalMasteringModel.Event> events =
                new ArrayList<>();
        private final double protectedEndingStart;
        private final double attack;
        private final double release;
        private int cursor;

        EventControl(ProfessionalMasteringModel.Result model, Mode mode,
                     ProfessionalMasteringModel.Kind kind, double duration,
                     double attack, double release) {
            this.protectedEndingStart = Math.max(2.0,
                    duration - Math.max(8.0, duration * 0.04));
            this.attack = attack;
            this.release = release;
            for (ProfessionalMasteringModel.Event event : model.events) {
                if (event.automatic && event.kind == kind
                        && kindInMode(kind, mode)) {
                    events.add(event);
                }
            }
            events.sort(Comparator.comparingDouble(
                    event -> event.startSeconds));
        }

        int count() {
            return events.size();
        }

        double dbAt(double seconds) {
            ProfessionalMasteringModel.Event event = eventAt(seconds);
            return event == null ? 0.0
                    : event.primaryDb * envelope(event, seconds);
        }

        private ProfessionalMasteringModel.Event eventAt(double seconds) {
            if (seconds < 2.0 || seconds >= protectedEndingStart) return null;
            while (cursor < events.size()
                    && seconds > events.get(cursor).endSeconds + release) {
                cursor++;
            }
            if (cursor >= events.size()) return null;
            ProfessionalMasteringModel.Event event = events.get(cursor);
            if (seconds < event.startSeconds
                    || seconds > event.endSeconds + release) return null;
            return event;
        }

        private double envelope(ProfessionalMasteringModel.Event event,
                                double seconds) {
            if (seconds <= event.endSeconds) {
                return smooth(Math.min(1.0,
                        (seconds - event.startSeconds)
                                / Math.max(0.0002, attack)));
            }
            return smooth(1.0 - Math.min(1.0,
                    (seconds - event.endSeconds)
                            / Math.max(0.0002, release)));
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
            return lowB.process(lowA.process(
                    highB.process(highA.process(sample))));
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
