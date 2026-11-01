package com.dennis.aimasteringlab;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;

public final class CoreHarness {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            throw new IllegalArgumentException("Usage: CoreHarness input.wav stems.zip output.wav");
        }
        File input = new File(args[0]);
        File stems = new File(args[1]);
        File output = new File(args[2]);
        final int[] last = {-1};
        AudioAnalyzer.Progress progress = (stage, percent) -> {
            if (percent >= 0 && percent / 10 != last[0] / 10) {
                System.err.println(stage + " " + percent + "%");
                last[0] = percent;
            }
        };
        AnalysisResult analysis = AudioAnalyzer.analyze(input.getName(),
                () -> new FileInputStream(input), progress);
        List<StemInventory.Item> items = StemInventory.inspect(stems, progress);
        analysis.stems.addAll(items);
        StemInventory.compareAgainstMix(analysis);
        analysis.stemReconstruction = StemReconstruction.analyze(
                () -> new FileInputStream(input), analysis, stems, progress);
        int stemErrors = 0;
        int crcVerified = 0;
        for (StemInventory.Item item : items) {
            if (item.error != null) stemErrors++;
            if (item.crcVerified) crcVerified++;
        }
        System.out.printf(java.util.Locale.US,
                "SOURCE sr=%d bits=%d ch=%d dur=%.6f LUFS=%.4f LRA=%.4f "
                        + "SP=%.4f TP_EST=%.4f corr=%.6f lowcorr=%.6f gain=%.4f limited=%s "
                        + "stems=%d crc_ok=%d stem_errors=%d duration_mismatches=%d "
                        + "mix_only_tail=%.6f%n",
                analysis.wav.sampleRate, analysis.wav.bitsPerSample, analysis.wav.channels,
                analysis.wav.durationSeconds, analysis.integratedLufs, analysis.lraLu,
                analysis.samplePeakDbfs, analysis.truePeakEstimateDbtp,
                analysis.stereoCorrelation, analysis.lowBandCorrelation,
                analysis.appliedSafeGainDb, analysis.ceilingLimited, items.size(),
                crcVerified, stemErrors, analysis.stemDurationMismatchCount,
                analysis.commonStemTailShortfallSeconds);
        System.out.println("RECONSTRUCTION " + StemReconstruction.compact(
                analysis.stemReconstruction));
        MasterRenderer.Result rendered = MasterRenderer.render(
                () -> new FileInputStream(input), analysis, analysis.appliedSafeGainDb,
                output, progress);
        System.out.printf(java.util.Locale.US,
                "OUTPUT sr=%d bits=%d dur=%.6f LUFS=%.4f LRA=%.4f SP=%.4f "
                        + "TP_EST=%.4f clips=%d sha=%s%n",
                rendered.verification.wav.sampleRate, rendered.verification.wav.bitsPerSample,
                rendered.verification.wav.durationSeconds, rendered.verification.integratedLufs,
                rendered.verification.lraLu, rendered.verification.samplePeakDbfs,
                rendered.verification.truePeakEstimateDbtp, rendered.quantizerClips,
                rendered.verification.sourceSha256);
    }
}
