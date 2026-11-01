package com.dennis.aimasteringlab;

import java.io.File;
import java.io.FileInputStream;

public final class MeasureHarness {
    public static void main(String[] args) throws Exception {
        for (String value : args) {
            File file = new File(value);
            AnalysisResult result = AudioAnalyzer.analyze(file.getName(),
                    () -> new FileInputStream(file), (stage, percent) -> {});
            System.out.printf(java.util.Locale.US,
                    "%s LUFS=%.4f TP=%.4f LRA=%.4f clips=%d nonfinite=%d corr=%.6f lowcorr=%.6f frames=%d sha=%s%n",
                    file.getName(), result.integratedLufs,
                    result.truePeakEstimateDbtp, result.lraLu,
                    result.clippedSamples, result.nonFiniteSamples,
                    result.stereoCorrelation, result.lowBandCorrelation,
                    result.wav.frames, result.sourceSha256);
        }
    }
}
