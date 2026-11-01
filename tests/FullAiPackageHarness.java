package com.dennis.aimasteringlab;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.util.zip.ZipFile;

public final class FullAiPackageHarness {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: FullAiPackageHarness input.wav work-directory");
        }
        File input = new File(args[0]);
        File work = new File(args[1]);
        if (!work.exists() && !work.mkdirs()) throw new IllegalStateException("No work directory");
        AudioAnalyzer.Progress progress = (stage, percent) -> {};
        AnalysisResult source = AudioAnalyzer.analyze(input.getName(),
                () -> new FileInputStream(input), progress);
        try {
            source.sections = LearnedSectionMapper.analyze(
                    () -> new FileInputStream(input), source, progress);
        } catch (Exception ignored) {}
        source.aiDiagnostics = AdaptiveDiagnosticModel.analyze(
                () -> new FileInputStream(input), source, progress);
        source.clickDropoutRepair = ClickDropoutRepair.analyze(
                () -> new FileInputStream(input), source, progress);
        AutonomousMasteringBrain.Result brain = AutonomousMasteringBrain.analyze(
                () -> new FileInputStream(input), source, progress);
        FullAiMasteringEngine.Result engine = FullAiMasteringEngine.run(
                () -> new FileInputStream(input), source, brain, work, progress);
        FullAiReleaseQc.Result qc = FullAiReleaseQc.run(
                () -> new FileInputStream(input), source, engine, work, "Fixture", progress);
        if (qc.aac != null && qc.aac.available) throw new AssertionError("Host must not claim Android codec success");
        if (!"CONDITIONAL".equals(qc.technicalVerdict)) throw new AssertionError("Missing host codec must be reported honestly");
        FullAiPackageBuilder.Result result = FullAiPackageBuilder.build(
                source, brain, engine, qc, work, "Fixture", progress);
        if(!(qc.releaseFormats != null && qc.releaseFormats.complete())) throw new AssertionError("Release formats incomplete");
        JSONObject report = new JSONObject(read(result.report));
        if (!"0.12.0".equals(report.getString("version"))
                || !report.has("radio_preparation")
                || !report.has("v08_professional_intelligence")
                || !report.getJSONObject("candidate_competition")
                .has("v08_professional_candidates_attempted")
                || !report.getJSONObject("click_dropout_repair")
                .has("post_dither_zero_count_semantics")) {
            throw new AssertionError("v0.12.0 package QC schema is incomplete");
        }
        if (!report.getJSONObject("natural_studio_policy").getBoolean("quality_first_selection")
                || !report.getJSONObject("candidate_competition").getBoolean("quality_first_selection")
                || !read(result.preparationReport).contains("independently verified cleanup or limiter improvements may replace it")) {
            throw new AssertionError("Release report still describes a frozen v0.7 policy");
        }
        int entries;
        try (ZipFile zip = new ZipFile(result.zip)) {
            entries = zip.size();
            if (zip.getEntry(result.report.getName()) == null
                    || zip.getEntry(result.confidenceMap.getName()) == null
                    || zip.getEntry(result.preparationReport.getName()) == null
                    || zip.getEntry(result.checksums.getName()) == null
                    || zip.getEntry(qc.finalMaster.getName()) == null) {
                throw new AssertionError("Package listing incomplete");
            }
        }
        System.out.println("FULL_AI_PACKAGE_PASS entries=" + entries
                + " translation=" + qc.translation.passCount + "/"
                + qc.translation.reviewCount + " sha=" + result.zipSha256);
    }

    private static String read(File file) throws Exception {
        byte[] data = new byte[(int) file.length()];
        try (FileInputStream input = new FileInputStream(file)) {
            int offset = 0;
            while (offset < data.length) {
                int count = input.read(data, offset, data.length - offset);
                if (count < 0) break;
                offset += count;
            }
            if (offset != data.length) {
                throw new IllegalStateException("Short QC report read");
            }
        }
        return new String(data, "UTF-8");
    }
}
