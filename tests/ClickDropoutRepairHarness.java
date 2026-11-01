package com.dennis.aimasteringlab;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;

/** End-to-end proof for bounded continuity repair and report audit. */
public final class ClickDropoutRepairHarness {
    private ClickDropoutRepairHarness() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException(
                "Usage: ClickDropoutRepairHarness input.wav work-directory");
        File input = new File(args[0]);
        File work = new File(args[1]);
        if (!work.exists() && !work.mkdirs()) throw new IllegalStateException("No work directory");
        AudioAnalyzer.Progress progress = (stage, percent) -> {};
        AnalysisResult source = AudioAnalyzer.analyze(input.getName(),
                () -> new FileInputStream(input), progress);
        try {
            source.sections = LearnedSectionMapper.analyze(
                    () -> new FileInputStream(input), source, progress);
        } catch (Exception ignored) {
            // The base meter's whole-track fallback remains sufficient here.
        }
        source.aiDiagnostics = AdaptiveDiagnosticModel.analyze(
                () -> new FileInputStream(input), source, progress);
        source.clickDropoutRepair = ClickDropoutRepair.analyze(
                () -> new FileInputStream(input), source, progress);
        ClickDropoutRepair.Result model = source.clickDropoutRepair;
        if (model.isolatedClicks != 1 || model.exactZeroDropouts != 1) {
            throw new AssertionError("Expected exactly one repairable click and dropout, found "
                    + model.isolatedClicks + "/" + model.exactZeroDropouts
                    + " events=" + model.events.size());
        }
        for (ClickDropoutRepair.Event event : model.events) {
            double start = event.startFrame / 48000.0;
            if (start < 2.0 || start >= 12.0) {
                throw new AssertionError("Continuity repair entered a protected edge: " + start);
            }
        }
        File repaired = new File(work, "repaired-source.wav");
        ClickDropoutRepair.render(() -> new FileInputStream(input), source,
                model, repaired, progress);
        if (!model.repairApplied || !model.protectedRegionsExact
                || model.sourceCorrelation < 0.997) {
            throw new AssertionError("Rendered continuity repair failed audit guards");
        }
        AnalysisResult repairedAnalysis = AudioAnalyzer.analyze(repaired.getName(),
                () -> new FileInputStream(repaired), progress);
        long originalZeros = countStereoZeros(input, 48000L * 10L,
                48000L * 10L + 96L);
        long repairedZeros = countStereoZeros(repaired, 48000L * 10L,
                48000L * 10L + 96L);
        if (repairedAnalysis.wav.frames != source.wav.frames
                || originalZeros != 96L || repairedZeros != 0L) {
            throw new AssertionError("Repair did not retain duration or replace the proven zero hole: frames "
                    + source.wav.frames + "/" + repairedAnalysis.wav.frames
                    + " zero frames " + originalZeros + "/" + repairedZeros);
        }

        source = TechnicalSourcePreparation.reanalyze(
                () -> new FileInputStream(repaired), source, progress);
        AutonomousMasteringBrain.Result brain = AutonomousMasteringBrain.analyze(
                () -> new FileInputStream(repaired), source, progress);
        FullAiMasteringEngine.Result engine = FullAiMasteringEngine.run(
                () -> new FileInputStream(repaired), source, brain, work, progress);
        FullAiReleaseQc.Result qc = new FullAiReleaseQc.Result();
        qc.initialPcmWinner = engine.selected;
        qc.finalCandidate = engine.selected;
        qc.finalMaster = engine.selected.file;
        qc.finalMasterVerification = engine.selected.render.master.verification;
        qc.finalMasterSha256 = Hashing.sha256(qc.finalMaster);
        qc.technicalVerdict = "PASS";
        JSONObject report = new JSONObject(FullAiReportWriter.create(
                source, brain, engine, qc));
        JSONObject continuity = report.getJSONObject("click_dropout_repair");
        if (!"0.12.0".equals(report.getString("version"))
                || !continuity.getBoolean("repair_applied")
                || continuity.getJSONArray("events").length() != 2
                || report.getJSONObject("master_confidence_map")
                .getJSONArray("sections").length() < 1) {
            throw new AssertionError("v0.6 report omitted continuity or confidence evidence");
        }
        System.out.println("CLICK_DROPOUT_REPAIR_PASS " + model.compactSummary());
    }

    private static long countStereoZeros(File file, long start, long end)
            throws Exception {
        long count = 0L;
        long absolute = 0L;
        float[][] samples = new float[2][4096];
        try (WavFile.Reader reader = WavFile.open(new FileInputStream(file))) {
            int read;
            while ((read = reader.readFrames(samples, samples[0].length)) > 0) {
                for (int i = 0; i < read; i++, absolute++) {
                    if (absolute >= end) return count;
                    if (absolute >= start && samples[0][i] == 0.0f
                            && samples[1][i] == 0.0f) count++;
                }
            }
        }
        return count;
    }
}
