package com.dennis.aimasteringlab;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public final class NoOpHarness {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: NoOpHarness compliant.wav output.wav");
        }
        File input = new File(args[0]);
        File output = new File(args[1]);
        AudioAnalyzer.Progress progress = (stage, percent) -> {};
        AnalysisResult analysis = AudioAnalyzer.analyze(input.getName(),
                () -> new FileInputStream(input), progress);
        if (!analysis.alreadyAtTargetNoRender) {
            throw new AssertionError("Expected already-at-target no-render state; LUFS="
                    + analysis.integratedLufs + " bits=" + analysis.wav.bitsPerSample
                    + " sampleRate=" + analysis.wav.sampleRate);
        }
        if (analysis.appliedSafeGainDb != 0.0) {
            throw new AssertionError("No-op source must have exactly zero recommended gain");
        }
        try {
            MasterRenderer.render(() -> new FileInputStream(input), analysis,
                    0.0, output, progress);
            throw new AssertionError("Renderer accepted a redundant zero-gain dither pass");
        } catch (IOException expected) {
            if (!expected.getMessage().contains("No render needed")) throw expected;
        }
        if (output.exists() && output.length() > 0) {
            throw new AssertionError("No-op rejection created output bytes");
        }
        System.out.printf(java.util.Locale.US,
                "NO_OP_PASS bits=%d sr=%d LUFS=%.4f TP_EST=%.4f sha=%s%n",
                analysis.wav.bitsPerSample, analysis.wav.sampleRate,
                analysis.integratedLufs, analysis.truePeakEstimateDbtp,
                analysis.sourceSha256);
    }
}
