package com.dennis.aimasteringlab;

import java.io.File;
import java.io.FileInputStream;

public final class CodecSafetyHarness {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: CodecSafetyHarness input.wav output.wav");
        }
        File input = new File(args[0]);
        File output = new File(args[1]);
        File work = new File(output.getParentFile(), "codec-safety-work");
        AudioAnalyzer.Progress progress = (stage, percent) -> {};
        AnalysisResult analysis = AudioAnalyzer.analyze(input.getName(),
                () -> new FileInputStream(input), progress);
        analysis.aiDiagnostics = AdaptiveDiagnosticModel.analyze(
                () -> new FileInputStream(input), analysis, progress);
        AutonomousMasteringBrain.Result brain = AutonomousMasteringBrain.analyze(
                () -> new FileInputStream(input), analysis, progress);
        AutonomousMasteringRenderer.Result result = AutonomousMasteringRenderer.render(
                () -> new FileInputStream(input), analysis, brain.preferred,
                output, work, -1.50, true, progress);
        if (result.master.verification.truePeakEstimateDbtp > -1.499
                || Math.abs(result.master.verification.integratedLufs + 14.0) > 0.02
                || result.master.verification.clippedSamples != 0) {
            throw new AssertionError("Forced codec-safety render failed target/ceiling/integrity");
        }
        System.out.printf(java.util.Locale.US,
                "CODEC_SAFETY_PASS LUFS=%.4f TP=%.4f LRA=%.4f LIMITER_GR=%.4f SHA=%s%n",
                result.master.verification.integratedLufs,
                result.master.verification.truePeakEstimateDbtp,
                result.master.verification.lraLu,
                result.master.maximumLimiterReductionDb,
                result.master.verification.sourceSha256);
    }
}
