package com.dennis.aimasteringlab;

import java.io.File;
import java.io.FileInputStream;
import java.util.Locale;

public final class AiHarness {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: AiHarness input.wav ai-output.wav [stems.zip]");
        }
        File input = new File(args[0]);
        File output = new File(args[1]);
        File work = new File(output.getParentFile(), "ai-harness-work");
        AudioAnalyzer.Progress progress = (stage, percent) -> {
            if (percent < 0 || percent == 0 || percent == 25 || percent == 50
                    || percent == 75 || percent >= 95) {
                System.err.println(stage + (percent >= 0 ? " " + percent + "%" : ""));
            }
        };
        AnalysisResult analysis = AudioAnalyzer.analyze(input.getName(),
                () -> new FileInputStream(input), progress);
        if (args.length >= 3) {
            File stems = new File(args[2]);
            analysis.stems.addAll(StemInventory.inspect(stems, progress));
            StemInventory.compareAgainstMix(analysis);
            analysis.stemReconstruction = StemReconstruction.analyze(
                    () -> new FileInputStream(input), analysis, stems, progress);
        }
        analysis.aiDiagnostics = AdaptiveDiagnosticModel.analyze(
                () -> new FileInputStream(input), analysis, progress);
        System.out.println("AI " + analysis.aiDiagnostics.compactSummary());
        System.out.println("STEM_TRUST " + analysis.aiDiagnostics.stemTrustAssessment);
        for (AdaptiveDiagnosticModel.Event event : analysis.aiDiagnostics.events) {
            System.out.printf(Locale.US,
                    "EVENT %.3f-%.3f %s confidence=%.3f auto=%s cut=%.3f%n",
                    event.startSeconds, event.endSeconds,
                    AdaptiveDiagnosticModel.label(event.type), event.confidence,
                    event.autoCorrection, event.correctionDb);
        }
        if (!analysis.aiDiagnostics.hasAutoCorrections()) {
            System.out.println("DIAGNOSTIC_ONLY no correction event cleared the safety gate");
            return;
        }
        MasterRenderer.Result rendered = AiGuidedRenderer.render(
                () -> new FileInputStream(input), analysis, output, work, progress);
        System.out.printf(Locale.US,
                "AI_OUTPUT events=%d max_cut=%.3f LUFS=%.4f TP=%.4f LRA=%.4f clips=%d dur=%.6f sha=%s%n",
                rendered.aiCorrectionEvents, rendered.maximumAiCorrectionDb,
                rendered.verification.integratedLufs,
                rendered.verification.truePeakEstimateDbtp,
                rendered.verification.lraLu, rendered.quantizerClips,
                rendered.verification.wav.durationSeconds,
                rendered.verification.sourceSha256);
    }
}
