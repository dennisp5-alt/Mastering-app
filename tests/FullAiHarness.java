package com.dennis.aimasteringlab;

import java.io.File;
import java.io.FileInputStream;

public final class FullAiHarness {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: FullAiHarness input.wav output-directory");
        }
        File input = new File(args[0]);
        File output = new File(args[1]);
        if (!output.exists() && !output.mkdirs()) {
            throw new IllegalStateException("Could not create output directory");
        }
        AudioAnalyzer.Progress progress = (stage, percent) -> {};
        AnalysisResult analysis = AudioAnalyzer.analyze(input.getName(),
                () -> new FileInputStream(input), progress);
        try {
            analysis.sections = LearnedSectionMapper.analyze(
                    () -> new FileInputStream(input), analysis, progress);
        } catch (Exception ignored) {}
        analysis.aiDiagnostics = AdaptiveDiagnosticModel.analyze(
                () -> new FileInputStream(input), analysis, progress);
        analysis.clickDropoutRepair = ClickDropoutRepair.analyze(
                () -> new FileInputStream(input), analysis, progress);
        File repaired = null;
        if (analysis.clickDropoutRepair.hasRepairs()) {
            repaired = new File(output, "continuity-repaired-source.wav");
            ClickDropoutRepair.render(() -> new FileInputStream(input), analysis,
                    analysis.clickDropoutRepair, repaired, progress);
        }
        final File processing = repaired == null ? input : repaired;
        AutonomousMasteringBrain.Result brain = AutonomousMasteringBrain.analyze(
                () -> new FileInputStream(processing), analysis, progress);
        FullAiMasteringEngine.Result result = FullAiMasteringEngine.run(
                () -> new FileInputStream(processing), analysis, brain, output, progress);
        for (FullAiMasteringEngine.Candidate candidate : result.candidates) {
            System.out.println(candidate.compactSummary());
        }
        System.out.println("WINNER " + result.selected.strategy);
        System.out.println("REASON " + result.selectionReason);
        System.out.println("CONTINUITY " + analysis.clickDropoutRepair.compactSummary());
        System.out.println("HASH " + result.selected.render.master.verification.sourceSha256);
    }
}
