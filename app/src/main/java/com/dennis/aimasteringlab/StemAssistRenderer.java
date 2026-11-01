package com.dennis.aimasteringlab;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Builds and verifies optional stem-aware candidates around the stereo mix. */
final class StemAssistRenderer {
    enum Mode {
        VOCAL_HUMANITY("stem_vocal_humanity"),
        MASKING_POCKETS("stem_masking_pockets"),
        COMBINED("stem_combined"),
        ANALYSIS_ONLY_GUIDED("analysis_only_stem_guided");

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
        double protectedIntroRmsDeltaDbfs;
        double protectedEndingRmsDeltaDbfs;
        double sideDeltaRmsDbfs;
        boolean protectedRegionsExact;
    }

    static final class Result {
        Mode mode;
        AutonomousMasteringRenderer.Result render;
        DeltaVerification delta;
        int correctionEvents;
        int stemsUsed;
        double maximumNominalEditDb;
        double deltaScale;
        double modelConfidence;
        double evidenceBenefit;
        String evidenceSummary;
        boolean analysisOnlyControl;
        boolean stemWaveformUsed;
    }

    private static final int BUFFER_FRAMES = 4096;

    private StemAssistRenderer() {}

    static Result render(AudioAnalyzer.StreamProvider source,
                         AnalysisResult sourceAnalysis,
                         File stemArchive,
                         AutonomousMasteringBrain.Plan transparentPlan,
                         Mode mode,
                         File output,
                         File workDirectory,
                         AudioAnalyzer.Progress progress) throws IOException {
        return render(source, sourceAnalysis, stemArchive, transparentPlan, mode,
                output, workDirectory, -1.0, false, progress);
    }

    static Result render(AudioAnalyzer.StreamProvider source,
                         AnalysisResult sourceAnalysis,
                         File stemArchive,
                         AutonomousMasteringBrain.Plan transparentPlan,
                         Mode mode,
                         File output,
                         File workDirectory,
                         double finalTruePeakCeilingDbtp,
                         boolean forceLimiter,
                         AudioAnalyzer.Progress progress) throws IOException {
        validateRequest(sourceAnalysis, stemArchive, transparentPlan, mode);
        File stageOne = new File(workDirectory,
                "stem-assist-stage-one-" + mode.strategy + "-" + System.nanoTime() + ".wav");
        File corrected = new File(workDirectory,
                "stem-assist-premaster-" + mode.strategy + "-" + System.nanoTime() + ".wav");
        try {
            AnalysisOnlyStemRenderer.Stats analysisOnlyStats = null;
            if (mode == Mode.ANALYSIS_ONLY_GUIDED) {
                analysisOnlyStats = AnalysisOnlyStemRenderer.renderCorrectedFloat(
                        source, sourceAnalysis, corrected, progress);
            } else if (mode == Mode.VOCAL_HUMANITY) {
                VocalGuidedRenderer.renderCorrectedFloat(source, sourceAnalysis,
                        stemArchive, sourceAnalysis.vocalHumanity, corrected, progress);
            } else if (mode == Mode.MASKING_POCKETS) {
                MaskingPocketRenderer.renderCorrectedFloat(source, sourceAnalysis,
                        stemArchive, sourceAnalysis.maskingPockets, corrected, progress);
            } else {
                VocalGuidedRenderer.renderCorrectedFloat(source, sourceAnalysis,
                        stemArchive, sourceAnalysis.vocalHumanity, stageOne, progress);
                MaskingPocketRenderer.renderCorrectedFloat(
                        () -> new FileInputStream(stageOne), sourceAnalysis,
                        stemArchive, sourceAnalysis.maskingPockets, corrected, progress);
            }

            AnalysisResult correctedAnalysis = AudioAnalyzer.analyze(corrected.getName(),
                    () -> new FileInputStream(corrected), progress);
            DeltaVerification delta = verifyDelta(source,
                    () -> new FileInputStream(corrected), sourceAnalysis);
            String guardrail = validatePremaster(sourceAnalysis, correctedAnalysis, delta);
            MasterRenderer.Result master = MasterRenderer.renderWithCeiling(
                    () -> new FileInputStream(corrected), correctedAnalysis,
                    correctedAnalysis.appliedSafeGainDb, output,
                    finalTruePeakCeilingDbtp, forceLimiter, progress);

            Result result = mode == Mode.ANALYSIS_ONLY_GUIDED
                    ? describeAnalysisOnly(sourceAnalysis, analysisOnlyStats)
                    : describe(sourceAnalysis, mode);
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
            annotateMaster(master, sourceAnalysis, result);
            return result;
        } finally {
            delete(stageOne);
            delete(corrected);
        }
    }

    private static void validateRequest(AnalysisResult source, File archive,
                                        AutonomousMasteringBrain.Plan plan,
                                        Mode mode) throws IOException {
        boolean accepted = mode == Mode.ANALYSIS_ONLY_GUIDED
                ? source.stemArchiveAnalysisOnlyAccepted
                : source.stemArchiveAccepted;
        if (archive == null || !archive.isFile() || !accepted) {
            throw new IOException("No validated stem archive is available");
        }
        if (source.wav.channels != 2 || source.wav.sampleRate != 48000) {
            throw new IOException("Stem assist requires a 48 kHz stereo authoritative mix");
        }
        if (plan == null || plan.hasAudioChanges()) {
            throw new IOException("Stem assist requires the transparent stereo baseline plan");
        }
        boolean vocal = source.vocalHumanity != null
                && source.vocalHumanity.available
                && source.vocalHumanity.hasCorrections();
        boolean masking = source.maskingPockets != null
                && source.maskingPockets.available
                && source.maskingPockets.hasCorrections();
        boolean analysisOnly = source.stemArchiveAnalysisOnlyAccepted
                && ((source.vocalHumanity != null
                    && source.vocalHumanity.analysisOnly
                    && !source.vocalHumanity.deEssEvents.isEmpty())
                || (source.maskingPockets != null
                    && source.maskingPockets.analysisOnly
                    && source.maskingPockets.hasCorrections()));
        if ((mode == Mode.VOCAL_HUMANITY && !vocal)
                || (mode == Mode.MASKING_POCKETS && !masking)
                || (mode == Mode.COMBINED && (!vocal || !masking))
                || (mode == Mode.ANALYSIS_ONLY_GUIDED && !analysisOnly)) {
            throw new IOException("The requested stem-assist model has no eligible corrections");
        }
    }

    private static String validatePremaster(AnalysisResult source,
                                            AnalysisResult corrected,
                                            DeltaVerification delta)
            throws IOException {
        if (!corrected.wav.floatingPoint || corrected.wav.channels != 2
                || corrected.wav.sampleRate != source.wav.sampleRate
                || corrected.wav.frames != source.wav.frames) {
            throw new IOException("Stem-guided pre-master changed format or duration");
        }
        if (corrected.nonFiniteSamples != 0) {
            throw new IOException("Stem-guided pre-master introduced non-finite samples");
        }
        if (corrected.clippedSamples > source.clippedSamples) {
            throw new IOException(String.format(Locale.US,
                    "Stem-guided pre-master introduced %d additional full-scale sample(s); source already contained %d",
                    corrected.clippedSamples - source.clippedSamples,
                    source.clippedSamples));
        }
        if (corrected.internalZeroRuns > source.internalZeroRuns) {
            throw new IOException("Stem-guided pre-master introduced an exact-zero interruption");
        }
        if (!delta.protectedRegionsExact) {
            throw new IOException("Stem-guided edit leaked into the protected intro or ending");
        }
        if (delta.maximumAbsoluteDelta <= 1.0e-8
                || delta.editedRmsDeltaDbfs < -145.0) {
            throw new IOException("Stem-guided candidate produced no measurable edit");
        }
        if (delta.sourceCorrelation < 0.995) {
            throw new IOException("Stem-guided edit changed the authoritative mix beyond the correlation guardrail");
        }
        if (corrected.stereoCorrelation < source.stereoCorrelation - 0.025
                || corrected.lowBandCorrelation < source.lowBandCorrelation - 0.015) {
            throw new IOException("Stem-guided edit worsened stereo or low-band correlation");
        }
        if (source.lraLu - corrected.lraLu > 0.80) {
            throw new IOException("Stem-guided edit reduced LRA beyond the candidate guardrail");
        }
        return String.format(Locale.US,
                "STEM-GUIDED PREMASTER PASS: mix correlation %.7f; protected intro/tail delta %.2g/%.2g; duration, continuity, phase and LRA verified",
                delta.sourceCorrelation, delta.protectedIntroMaxDelta,
                delta.protectedEndingMaxDelta);
    }

    private static Result describe(AnalysisResult source, Mode mode) {
        Result out = new Result();
        out.mode = mode;
        out.stemWaveformUsed = true;
        VocalHumanityModel.Result vocal = source.vocalHumanity;
        MaskingPocketModel.Result masking = source.maskingPockets;
        if (mode == Mode.VOCAL_HUMANITY || mode == Mode.COMBINED) {
            out.correctionEvents += vocal.rideCount() + vocal.deEssEvents.size();
            out.stemsUsed++;
            out.maximumNominalEditDb = Math.max(out.maximumNominalEditDb,
                    Math.max(Math.max(Math.abs(vocal.maximumRideDownDb),
                            vocal.maximumRideUpDb), vocal.maximumDeEssDb));
            out.deltaScale = Math.max(out.deltaScale, vocal.deltaScale);
        }
        if (mode == Mode.MASKING_POCKETS || mode == Mode.COMBINED) {
            out.correctionEvents += masking.events.size();
            Set<String> paths = new HashSet<>();
            for (MaskingPocketModel.Event event : masking.events) paths.add(event.stemPath);
            out.stemsUsed += paths.size();
            out.maximumNominalEditDb = Math.max(out.maximumNominalEditDb,
                    masking.maximumCombinedNominalCutDb);
            out.deltaScale = Math.max(out.deltaScale, masking.deltaScale);
        }
        double confidenceSum = 0.0;
        double weightedBenefit = 0.0;
        int confidenceCount = 0;
        if (mode == Mode.VOCAL_HUMANITY || mode == Mode.COMBINED) {
            for (VocalHumanityModel.Phrase phrase : vocal.phrases) {
                if (Math.abs(phrase.rideDb) < 0.001) continue;
                confidenceSum += phrase.confidence;
                weightedBenefit += phrase.confidence * Math.abs(phrase.rideDb)
                        * Math.max(0.05, phrase.endSeconds - phrase.startSeconds);
                confidenceCount++;
            }
            for (VocalHumanityModel.DeEssEvent event : vocal.deEssEvents) {
                confidenceSum += event.confidence;
                weightedBenefit += event.confidence * -event.attenuationDb
                        * Math.max(0.02, event.endSeconds - event.startSeconds);
                confidenceCount++;
            }
        }
        if (mode == Mode.MASKING_POCKETS || mode == Mode.COMBINED) {
            for (MaskingPocketModel.Event event : masking.events) {
                confidenceSum += event.confidence;
                weightedBenefit += event.confidence * -event.attenuationDb
                        * Math.max(0.02, event.endSeconds - event.startSeconds);
                confidenceCount++;
            }
        }
        out.modelConfidence = confidenceCount == 0 ? 0.0
                : confidenceSum / confidenceCount;
        out.evidenceBenefit = clamp(weightedBenefit
                / Math.max(1.0, source.wav.durationSeconds * 0.10), 0.0, 1.0);
        out.evidenceSummary = String.format(Locale.US,
                "%d track-trained events on %d secure stems; mean confidence %.3f; normalized targeted benefit %.3f",
                out.correctionEvents, out.stemsUsed, out.modelConfidence,
                out.evidenceBenefit);
        return out;
    }

    private static Result describeAnalysisOnly(
            AnalysisResult source, AnalysisOnlyStemRenderer.Stats stats) {
        Result out = new Result();
        out.mode = Mode.ANALYSIS_ONLY_GUIDED;
        out.analysisOnlyControl = true;
        out.stemWaveformUsed = false;
        out.correctionEvents = stats.deEssEvents + stats.maskingEvents;
        out.stemsUsed = stats.stemsUsed;
        out.maximumNominalEditDb = stats.maximumCombinedNominalEditDb;
        out.deltaScale = 0.0;
        out.modelConfidence = stats.meanConfidence;
        double targetedSeconds = 0.0;
        if (source.vocalHumanity != null) {
            for (VocalHumanityModel.DeEssEvent event : source.vocalHumanity.deEssEvents) {
                targetedSeconds += Math.max(0.0, event.endSeconds - event.startSeconds);
            }
        }
        if (source.maskingPockets != null) {
            targetedSeconds += source.maskingPockets.totalPocketSeconds;
        }
        out.evidenceBenefit = clamp(out.modelConfidence
                * targetedSeconds / Math.max(1.0, source.wav.durationSeconds * 0.08),
                0.0, 1.0);
        out.evidenceSummary = String.format(Locale.US,
                "%d vocal phrase regions guided %d de-ess and %d masking control event(s) across %d aligned stems; mean confidence %.3f; max authoritative-mix edit %.3f dB; stem waveform used: no",
                stats.phraseRegions, stats.deEssEvents, stats.maskingEvents,
                stats.stemsUsed, stats.meanConfidence,
                stats.maximumCombinedNominalEditDb);
        return out;
    }

    private static void annotateMaster(MasterRenderer.Result master,
                                       AnalysisResult source, Result result) {
        if (result.mode == Mode.VOCAL_HUMANITY || result.mode == Mode.COMBINED) {
            VocalHumanityModel.Result vocal = source.vocalHumanity;
            master.vocalHumanityGuided = true;
            master.vocalStemPath = vocal.leadStemPath;
            master.vocalRideCount = vocal.rideCount();
            master.vocalDeEssEvents = vocal.deEssEvents.size();
            master.vocalRideDownDb = vocal.maximumRideDownDb;
            master.vocalRideUpDb = vocal.maximumRideUpDb;
            master.vocalMaximumDeEssDb = vocal.maximumDeEssDb;
            master.vocalDeltaScale = vocal.deltaScale;
            master.vocalCorrectionSummary = vocal.compactSummary();
        }
        if (result.mode == Mode.MASKING_POCKETS || result.mode == Mode.COMBINED) {
            MaskingPocketModel.Result masking = source.maskingPockets;
            master.maskingPocketGuided = true;
            master.maskingPocketEvents = masking.events.size();
            master.maskingPocketStems = uniqueMaskingStems(masking);
            master.maskingMaximumCutDb = masking.maximumCutDb;
            master.maskingMaximumCombinedCutDb = masking.maximumCombinedNominalCutDb;
            master.maskingDeltaScale = masking.deltaScale;
            master.maskingCorrectionSummary = masking.compactSummary();
        }
        master.aiGuided = true;
        master.aiCorrectionEvents = result.correctionEvents;
        master.maximumAiCorrectionDb = result.maximumNominalEditDb;
        master.aiCorrectionSummary = result.mode.strategy + "; " + result.evidenceSummary;
    }

    private static int uniqueMaskingStems(MaskingPocketModel.Result masking) {
        Set<String> paths = new HashSet<>();
        for (MaskingPocketModel.Event event : masking.events) paths.add(event.stemPath);
        return paths.size();
    }

    private static DeltaVerification verifyDelta(
            AudioAnalyzer.StreamProvider source,
            AudioAnalyzer.StreamProvider corrected,
            AnalysisResult analysis) throws IOException {
        DeltaVerification out = new DeltaVerification();
        long introEnd = Math.round(2.0 * analysis.wav.sampleRate);
        double protectedSeconds = Math.max(8.0, analysis.wav.durationSeconds * 0.04);
        long endingStart = Math.max(0L, analysis.wav.frames
                - Math.round(protectedSeconds * analysis.wav.sampleRate));
        double sx = 0.0, sy = 0.0, sxx = 0.0, syy = 0.0, sxy = 0.0;
        double editedSquares = 0.0;
        long editedSamples = 0L;
        double introSquares = 0.0;
        long introSamples = 0L;
        double endingSquares = 0.0;
        long endingSamples = 0L;
        double sideSquares = 0.0;
        long sideSamples = 0L;
        try (WavFile.Reader reference = WavFile.open(source.open());
             WavFile.Reader test = WavFile.open(corrected.open())) {
            float[][] a = new float[2][BUFFER_FRAMES];
            float[][] b = new float[2][BUFFER_FRAMES];
            long frame = 0L;
            int read;
            while ((read = reference.readFrames(a, BUFFER_FRAMES)) > 0) {
                int testRead = test.readFrames(b, read);
                if (testRead != read) throw new IOException("Stem delta comparison ended early");
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
                        introSquares += dl * dl + dr * dr;
                        introSamples += 2;
                    } else if (frame >= endingStart) {
                        out.protectedEndingMaxDelta = Math.max(
                                out.protectedEndingMaxDelta,
                                Math.max(Math.abs(dl), Math.abs(dr)));
                        endingSquares += dl * dl + dr * dr;
                        endingSamples += 2;
                    } else {
                        editedSquares += dl * dl + dr * dr;
                        editedSamples += 2;
                    }
                    double sideDelta = 0.5 * (dl - dr);
                    sideSquares += sideDelta * sideDelta;
                    sideSamples++;
                }
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("Stem delta verification cancelled");
                }
            }
            if (frame != analysis.wav.frames || test.readFrames(b, 1) > 0) {
                throw new IOException("Stem delta comparison changed the sample count");
            }
            out.frames = frame;
        }
        double numerator = out.frames * sxy - sx * sy;
        double denominator = Math.sqrt(Math.max(0.0, out.frames * sxx - sx * sx)
                * Math.max(0.0, out.frames * syy - sy * sy));
        out.sourceCorrelation = denominator <= 1.0e-30 ? 0.0
                : clamp(numerator / denominator, -1.0, 1.0);
        out.editedRmsDeltaDbfs = dbfs(editedSquares, editedSamples);
        out.protectedIntroRmsDeltaDbfs = dbfs(introSquares, introSamples);
        out.protectedEndingRmsDeltaDbfs = dbfs(endingSquares, endingSamples);
        out.sideDeltaRmsDbfs = dbfs(sideSquares, sideSamples);
        out.protectedRegionsExact = out.protectedIntroMaxDelta <= 1.0e-7
                && out.protectedEndingMaxDelta <= 1.0e-7;
        return out;
    }

    private static double dbfs(double squares, long count) {
        if (count <= 0 || squares <= 0.0) return -300.0;
        return 10.0 * Math.log10(squares / count);
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
}
