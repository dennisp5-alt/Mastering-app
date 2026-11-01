package com.dennis.aimasteringlab;

import java.io.File;
import java.io.FileInputStream;
import java.util.Locale;

public final class VocalHarness {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException("Usage: VocalHarness input.wav stems.zip vocal-output.wav");
        }
        File input = new File(args[0]);
        File stems = new File(args[1]);
        File output = new File(args[2]);
        File work = new File(output.getParentFile(), "vocal-harness-work");
        AudioAnalyzer.Progress progress = (stage, percent) -> {
            if (percent < 0 || percent == 0 || percent == 25 || percent == 50
                    || percent == 75 || percent >= 95) {
                System.err.println(stage + (percent >= 0 ? " " + percent + "%" : ""));
            }
        };
        AnalysisResult analysis = AudioAnalyzer.analyze(input.getName(),
                () -> new FileInputStream(input), progress);
        analysis.sections = LearnedSectionMapper.analyze(
                () -> new FileInputStream(input), analysis, progress);
        analysis.stems.addAll(StemInventory.inspect(stems, progress));
        StemInventory.compareAgainstMix(analysis);
        analysis.stemReconstruction = StemReconstruction.analyze(
                () -> new FileInputStream(input), analysis, stems, progress);
        analysis.vocalHumanity = VocalHumanityModel.analyze(stems, analysis, progress);
        System.out.println("SECTION " + analysis.sections.method);
        System.out.printf(Locale.US,
                "SECTION_RESULT states=%d boundaries=%d confidence=%.4f lift=%.3f%n",
                analysis.sections.learnedStates, analysis.sections.boundaryCandidates,
                analysis.sections.mappingConfidence, analysis.sections.quietToChorusLiftLu);
        System.out.println("VOCAL " + analysis.vocalHumanity.compactSummary());
        System.out.println("VOCAL_STATUS " + analysis.vocalHumanity.status);
        for (VocalHumanityModel.Phrase phrase : analysis.vocalHumanity.phrases) {
            if (Math.abs(phrase.rideDb) >= 0.001) {
                System.out.printf(Locale.US,
                        "RIDE %.3f-%.3f level=%.3f gain=%+.3f confidence=%.3f%n",
                        phrase.startSeconds, phrase.endSeconds, phrase.levelDbfs,
                        phrase.rideDb, phrase.confidence);
            }
        }
        for (VocalHumanityModel.DeEssEvent event : analysis.vocalHumanity.deEssEvents) {
            System.out.printf(Locale.US,
                    "DEESS %.3f-%.3f gain=%+.3f z=%.3f confidence=%.3f%n",
                    event.startSeconds, event.endSeconds, event.attenuationDb,
                    event.sibilanceZ, event.confidence);
        }
        if (!analysis.vocalHumanity.hasCorrections()) {
            System.out.println("DIAGNOSTIC_ONLY no vocal event cleared the safety gate");
            return;
        }
        MasterRenderer.Result rendered = VocalGuidedRenderer.render(
                () -> new FileInputStream(input), analysis, stems, output, work, progress);
        System.out.printf(Locale.US,
                "VOCAL_OUTPUT rides=%d deess=%d ride_range=%+.3f..%+.3f deess_max=%.3f delta=%.3f LUFS=%.4f TP=%.4f LRA=%.4f clips=%d dur=%.6f sha=%s%n",
                rendered.vocalRideCount, rendered.vocalDeEssEvents,
                rendered.vocalRideDownDb, rendered.vocalRideUpDb,
                rendered.vocalMaximumDeEssDb, rendered.vocalDeltaScale,
                rendered.verification.integratedLufs,
                rendered.verification.truePeakEstimateDbtp,
                rendered.verification.lraLu, rendered.quantizerClips,
                rendered.verification.wav.durationSeconds,
                rendered.verification.sourceSha256);
    }
}
