package com.dennis.aimasteringlab;

import java.io.File;
import java.io.FileInputStream;
import java.util.Locale;

public final class AiLabHarness {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            throw new IllegalArgumentException(
                    "Usage: AiLabHarness input.wav conservative.wav preferred.wav");
        }
        File input = new File(args[0]);
        File conservativeOutput = new File(args[1]);
        File preferredOutput = new File(args[2]);
        File work = new File(conservativeOutput.getParentFile(), "ai-lab-harness-work");
        final int[] lastBucket = {-1};
        final String[] lastStage = {""};
        AudioAnalyzer.Progress progress = (stage, percent) -> {
            int bucket = percent < 0 ? -1 : percent / 25;
            if (!stage.equals(lastStage[0]) || bucket != lastBucket[0]) {
                System.err.println(stage + (percent >= 0 ? " " + percent + "%" : ""));
                lastStage[0] = stage;
                lastBucket[0] = bucket;
            }
        };
        AnalysisResult analysis = AudioAnalyzer.analyze(input.getName(),
                () -> new FileInputStream(input), progress);
        try {
            analysis.sections = LearnedSectionMapper.analyze(
                    () -> new FileInputStream(input), analysis, progress);
        } catch (Exception ignored) {}
        analysis.aiDiagnostics = AdaptiveDiagnosticModel.analyze(
                () -> new FileInputStream(input), analysis, progress);
        AutonomousMasteringBrain.Result brain = AutonomousMasteringBrain.analyze(
                () -> new FileInputStream(input), analysis, progress);
        System.out.println("FEATURES " + brain.features.compactSummary());
        System.out.println("PLAN_A " + brain.conservative.compactSummary());
        System.out.println("PLAN_B " + brain.preferred.compactSummary());

        AutonomousMasteringRenderer.Result conservative = AutonomousMasteringRenderer.render(
                () -> new FileInputStream(input), analysis, brain.conservative,
                conservativeOutput, work, progress);
        long conservativeLengthAfterRender = conservativeOutput.length();
        System.out.println("CONSERVATIVE_BYTES_AFTER_FIRST " + conservativeLengthAfterRender);
        AutonomousMasteringRenderer.Result preferred = AutonomousMasteringRenderer.render(
                () -> new FileInputStream(input), analysis, brain.preferred,
                preferredOutput, work, progress);
        System.out.println("CONSERVATIVE_BYTES_AFTER_SECOND " + conservativeOutput.length());
        System.out.println("PREFERRED_BYTES_AFTER_SECOND " + preferredOutput.length());
        AnalysisResult conservativeReopen = AudioAnalyzer.analyze(
                conservativeOutput.getName(), () -> new FileInputStream(conservativeOutput), progress);
        AnalysisResult preferredReopen = AudioAnalyzer.analyze(
                preferredOutput.getName(), () -> new FileInputStream(preferredOutput), progress);
        if (!conservative.master.verification.sourceSha256.equals(
                conservativeReopen.sourceSha256)
                || conservativeReopen.wav.frames != analysis.wav.frames) {
            throw new AssertionError("Conservative output failed delayed reopen verification");
        }
        if (!preferred.master.verification.sourceSha256.equals(preferredReopen.sourceSha256)
                || preferredReopen.wav.frames != analysis.wav.frames) {
            throw new AssertionError("Preferred output failed delayed reopen verification");
        }
        print("CONSERVATIVE", conservative);
        print("PREFERRED", preferred);
    }

    private static void print(String label, AutonomousMasteringRenderer.Result result) {
        System.out.printf(Locale.US,
                "%s LUFS=%.4f TP=%.4f LRA=%.4f corr=%.6f lowcorr=%.6f "
                        + "limiter=%s limiter_gr=%.4f comp_gr=%.4f clips=%d sha=%s%n",
                label, result.master.verification.integratedLufs,
                result.master.verification.truePeakEstimateDbtp,
                result.master.verification.lraLu,
                result.master.verification.stereoCorrelation,
                result.master.verification.lowBandCorrelation,
                result.master.limiterUsed, result.master.maximumLimiterReductionDb,
                result.maximumCompressionReductionDb, result.master.quantizerClips,
                result.master.verification.sourceSha256);
    }
}
