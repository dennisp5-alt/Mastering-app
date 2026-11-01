package com.dennis.aimasteringlab;

import java.io.File;
import java.io.FileInputStream;

public final class TranslationHarness {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: TranslationHarness master.wav translation.wav");
        }
        File master = new File(args[0]);
        File translation = new File(args[1]);
        AudioAnalyzer.Progress progress = (stage, percent) -> {};
        AnalysisResult analysis = AudioAnalyzer.analyze(master.getName(),
                () -> new FileInputStream(master), progress);
        TranslationComposite.Result composite = TranslationComposite.build(
                master, analysis, analysis.sections, translation, progress);
        TranslationAnalyzer.Result result = TranslationAnalyzer.analyze(
                translation, composite, progress);
        System.out.printf(java.util.Locale.US,
                "COMPOSITE dur=%.3f segments=%d LUFS=%.3f TP=%.3f devices=%d pass=%d review=%d lift=%.3f%n",
                composite.verification.wav.durationSeconds, composite.segments.size(),
                composite.verification.integratedLufs,
                composite.verification.truePeakEstimateDbtp,
                result.devices.size(), result.passCount, result.reviewCount,
                analysis.sections.quietToChorusLiftLu);
        for (TranslationAnalyzer.DeviceResult device : result.devices) {
            System.out.printf(java.util.Locale.US,
                    "%s | %s | raw peak %.2f | matched peak %.2f | gain %+.2f | stress %.2f | vocal %.2f | corr %.3f | chorus lift %.2f%n",
                    device.verdict, device.name, device.rawResponsePeakDbfs,
                    device.peakDbfs, device.appliedLevelMatchGainDb,
                    device.rawHeadroomStressDb,
                    device.vocalBandShareDb, device.stereoCorrelation,
                    device.chorusLiftDb);
            if (device.peakDbfs > result.referencePeakDbfs + 0.0001) {
                throw new AssertionError("Translation level-match peak cap failed");
            }
        }
    }
}
