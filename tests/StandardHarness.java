package com.dennis.aimasteringlab;

import java.io.File;
import java.io.FileInputStream;
import java.util.Locale;

public final class StandardHarness {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: StandardHarness input.wav output.wav");
        }
        File input = new File(args[0]);
        File output = new File(args[1]);
        AudioAnalyzer.Progress progress = (stage, percent) -> {};
        AnalysisResult analysis = AudioAnalyzer.analyze(input.getName(),
                () -> new FileInputStream(input), progress);
        MasterRenderer.Result rendered = MasterRenderer.render(
                () -> new FileInputStream(input), analysis, analysis.appliedSafeGainDb,
                output, progress);
        System.out.printf(Locale.US,
                "STANDARD LUFS=%.4f TP=%.4f LRA=%.4f clips=%d dur=%.6f sha=%s%n",
                rendered.verification.integratedLufs,
                rendered.verification.truePeakEstimateDbtp,
                rendered.verification.lraLu, rendered.quantizerClips,
                rendered.verification.wav.durationSeconds,
                rendered.verification.sourceSha256);
    }
}
