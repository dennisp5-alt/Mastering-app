package com.dennis.aimasteringlab;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public final class DynamicsNoChangeHarness {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: DynamicsNoChangeHarness protected.wav output.wav");
        }
        File input = new File(args[0]);
        File output = new File(args[1]);
        AnalysisResult analysis = AudioAnalyzer.analyze(input.getName(),
                () -> new FileInputStream(input), (stage, percent) -> {});
        if (!analysis.noRenderNeeded || !analysis.ceilingLimited
                || analysis.alreadyAtTargetNoRender) {
            throw new AssertionError("Expected dynamics-protected no-change state");
        }
        try {
            MasterRenderer.render(() -> new FileInputStream(input), analysis,
                    analysis.appliedSafeGainDb, output, (stage, percent) -> {});
            throw new AssertionError("Renderer accepted a redundant dynamics-protected pass");
        } catch (IOException expected) {
            if (!expected.getMessage().contains("No render needed")) throw expected;
        }
        if (output.exists() && output.length() > 0) {
            throw new AssertionError("No-change rejection created output bytes");
        }
        System.out.printf(java.util.Locale.US,
                "DYNAMICS_NO_CHANGE_PASS LUFS=%.4f TP=%.4f gain=%.4f reason=%s%n",
                analysis.integratedLufs, analysis.truePeakEstimateDbtp,
                analysis.appliedSafeGainDb, analysis.noRenderReason);
    }
}
