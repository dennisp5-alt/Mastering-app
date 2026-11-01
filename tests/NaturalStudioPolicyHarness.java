package com.dennis.aimasteringlab;

import java.io.File;
import java.io.FileInputStream;

/** Focused regression for v0.3 release-loudness, codec and translation policy. */
public final class NaturalStudioPolicyHarness {
    public static void main(String[] args) throws Exception {
        require(AudioAnalyzer.STANDARD_LIMITER_MAXIMUM_REDUCTION_DB == 3.00,
                "standard limiter ceiling");
        require(AudioAnalyzer.SPARSE_LIMITER_MAXIMUM_REDUCTION_DB == 5.50,
                "sparse limiter ceiling");
        require(FullAiMasteringEngine.loudnessShortfallPenalty(-19.25) > 3.40,
                "large release loudness shortfall is materially penalized");
        require("PASS".equals(FullAiReleaseQc.technicalVerdict(true, true, true)),
                "exact safe result passes");
        require("CONDITIONAL".equals(
                FullAiReleaseQc.technicalVerdict(true, true, false)),
                "dynamic shortfall is honestly conditional");
        require("CONDITIONAL".equals(
                FullAiReleaseQc.technicalVerdict(true, false, true)),
                "codec failure is conditional");
        require("PASS".equals(
                FullAiReleaseQc.technicalVerdict(true, true, false, true)),
                "small deliberate codec-safe margin passes honestly");

        AnalysisResult limiterAnalysis = new AnalysisResult();
        limiterAnalysis.limiterReductionAllowanceDb = 3.0;
        MasterRenderer.Result sparse = new MasterRenderer.Result();
        sparse.maximumLimiterReductionDb = 5.20;
        sparse.averageLimiterReductionDb = 0.42;
        sparse.limiterFramesAboveOneDbPercent = 7.0;
        sparse.limiterFramesAboveThreeDbPercent = 1.2;
        require(MasterRenderer.limiterActivitySafe(sparse, limiterAnalysis)
                        && sparse.sparsePeakReleaseMode,
                "brief extended peak control is eligible");
        MasterRenderer.Result dense = new MasterRenderer.Result();
        dense.maximumLimiterReductionDb = 5.20;
        dense.averageLimiterReductionDb = 1.20;
        dense.limiterFramesAboveOneDbPercent = 28.0;
        dense.limiterFramesAboveThreeDbPercent = 8.0;
        require(!MasterRenderer.limiterActivitySafe(dense, limiterAnalysis)
                        && !dense.sparsePeakReleaseMode,
                "dense extended limiting is rejected");

        FullAiReleaseQc.CodecAttempt codec = new FullAiReleaseQc.CodecAttempt();
        codec.aac = new AndroidCodecEncoder.Result();
        codec.aac.available = true;
        codec.aacDecoded = new AndroidCodecDecoder.Result();
        codec.aacDecoded.verification = new AnalysisResult();
        codec.aacDecoded.verification.truePeakEstimateDbtp = -0.9069;
        double attenuation = FullAiReleaseQc.requiredCodecSafetyAttenuationDb(codec);
        require(Math.abs(attenuation - 0.2131) < 0.00001,
                "flat codec attenuation margin");

        TranslationAnalyzer.Result source = new TranslationAnalyzer.Result();
        TranslationAnalyzer.Result mastered = new TranslationAnalyzer.Result();
        addPair(source, mastered, "inherited-low-lift", 0.10, 0.05,
                0.50, 0.50, false, false);
        addPair(source, mastered, "mastering-lost-lift", 3.00, 0.20,
                0.50, 0.50, false, false);
        addPair(source, mastered, "inherited-phase", 1.00, 1.00,
                -0.15, -0.14, true, true);
        source.passCount = 1;
        source.reviewCount = 2;
        TranslationAnalyzer.applySourceBaseline(mastered, source);
        require(mastered.passCount == 2 && mastered.reviewCount == 1,
                "source-relative translation counts");
        require("PASS".equals(mastered.devices.get(0).verdict)
                        && !mastered.devices.get(0).diagnosticNotes.isEmpty(),
                "inherited low lift remains diagnostic");
        require("REVIEW".equals(mastered.devices.get(1).verdict)
                        && mastered.devices.get(1).masteringIntroducedLiftRisk,
                "mastering lift loss is reviewed");
        require("PASS".equals(mastered.devices.get(2).verdict)
                        && !mastered.devices.get(2).masteringIntroducedMonoPhaseRisk,
                "inherited phase risk is not blamed on mastering");

        if (args.length == 2) {
            File input = new File(args[0]);
            File output = new File(args[1]);
            AnalysisResult analysis = AudioAnalyzer.analyze(input.getName(),
                    () -> new FileInputStream(input), (stage, percent) -> {});
            MasterRenderer.Result prior = new MasterRenderer.Result();
            prior.gainDb = 3.0;
            prior.limiterUsed = true;
            prior.maximumLimiterReductionDb = 1.20;
            prior.renderPasses = 2;
            MasterRenderer.Result rendered =
                    MasterRenderer.renderCodecSafetyAttenuation(
                            () -> new FileInputStream(input), analysis, prior,
                            0.22, output, (stage, percent) -> {});
            require(rendered.verification.wav.frames == analysis.wav.frames,
                    "codec attenuation duration");
            require(rendered.verification.truePeakEstimateDbtp
                            <= analysis.truePeakEstimateDbtp - 0.20,
                    "codec attenuation creates headroom");
            require(rendered.limiterUsed
                            && Math.abs(rendered.maximumLimiterReductionDb - 1.20) < 1.0e-9,
                    "prior limiter envelope metadata retained");
            require(rendered.dynamicsProtected && !rendered.exactTargetReached,
                    "codec headroom shortfall is explicit");
        }

        System.out.printf(java.util.Locale.US,
                "NATURAL_STUDIO_V010_POLICY_PASS standard=%.2f sparse=%.2f codecAttenuation=%.4f translation=%d/%d%n",
                AudioAnalyzer.STANDARD_LIMITER_MAXIMUM_REDUCTION_DB,
                AudioAnalyzer.SPARSE_LIMITER_MAXIMUM_REDUCTION_DB,
                attenuation, mastered.passCount, mastered.reviewCount);
    }

    private static void addPair(TranslationAnalyzer.Result source,
                                TranslationAnalyzer.Result mastered,
                                String name,
                                double sourceLift,
                                double masteredLift,
                                double sourceCorrelation,
                                double masteredCorrelation,
                                boolean sourcePhaseRisk,
                                boolean masteredPhaseRisk) {
        TranslationAnalyzer.DeviceResult baseline =
                new TranslationAnalyzer.DeviceResult();
        baseline.name = name;
        baseline.chorusLiftDb = sourceLift;
        baseline.busyLiftDb = sourceLift;
        baseline.stereoCorrelation = sourceCorrelation;
        baseline.monoPhaseRisk = sourcePhaseRisk;
        source.devices.add(baseline);

        TranslationAnalyzer.DeviceResult out =
                new TranslationAnalyzer.DeviceResult();
        out.name = name;
        out.chorusLiftDb = masteredLift;
        out.busyLiftDb = masteredLift;
        out.stereoCorrelation = masteredCorrelation;
        out.monoPhaseRisk = masteredPhaseRisk;
        if (masteredLift < 0.5) {
            out.reviewReasons.add("quiet-to-chorus lift was below 0.5 dB");
        }
        if (masteredPhaseRisk) {
            out.reviewReasons.add("stereo correlation fell below -0.10");
        }
        mastered.devices.add(out);
    }

    private static void require(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
}
