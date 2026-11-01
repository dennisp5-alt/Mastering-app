package com.dennis.aimasteringlab;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;

/** Regression for inherited clips, source-zero provenance and stereo repair. */
public final class SourceIntegrityHarness {
    private SourceIntegrityHarness() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "Usage: SourceIntegrityHarness fixture.wav output-directory");
        }
        File input = new File(args[0]);
        File output = new File(args[1]);
        if (!output.exists() && !output.mkdirs()) {
            throw new IllegalStateException("Could not create output directory");
        }
        AudioAnalyzer.Progress progress = (stage, percent) -> {};
        AnalysisResult analysis = AudioAnalyzer.analyze(input.getName(),
                () -> new FileInputStream(input), progress);
        if (analysis.wav.sampleRate != 48000
                || analysis.wav.bitsPerSample != 16) {
            throw new AssertionError("Fixture did not preserve independent sample-rate/bit-depth identity");
        }
        if (analysis.clippedSamples != 1) {
            throw new AssertionError("Expected exactly one inherited full-scale sample, found "
                    + analysis.clippedSamples);
        }
        if (analysis.internalZeroRegions.size() != 1) {
            throw new AssertionError("Expected one source long-zero region, found "
                    + analysis.internalZeroRegions.size());
        }
        AnalysisResult.ZeroRegion zero = analysis.internalZeroRegions.get(0);
        if (zero.startFrame != 6L * 48000L
                || zero.endFrameExclusive != 6L * 48000L + 6000L) {
            throw new AssertionError("Source zero-region frame provenance changed: "
                    + zero.startFrame + "-" + zero.endFrameExclusive);
        }

        AutonomousMasteringBrain.Result brain =
                AutonomousMasteringBrain.analyze(
                        () -> new FileInputStream(input), analysis,
                        progress);
        AutonomousMasteringBrain.Plan transparent = null;
        for (AutonomousMasteringBrain.Plan plan : brain.internalPlans) {
            if ("transparent_flat_gain".equals(plan.strategy)) {
                transparent = plan;
                break;
            }
        }
        if (transparent == null || transparent.hasAudioChanges()) {
            throw new AssertionError("Transparent baseline was unavailable");
        }
        File flat = new File(output, "integrity-flat.wav");
        AutonomousMasteringRenderer.Result flatRender =
                AutonomousMasteringRenderer.render(
                        () -> new FileInputStream(input), analysis,
                        transparent, flat, output, progress);
        if (flatRender.master.verification.clippedSamples != 0
                || flatRender.master.verification.nonFiniteSamples != 0) {
            throw new AssertionError("Final transparent master did not remove inherited full-scale risk safely");
        }

        StereoIntelligenceModel.Result model =
                new StereoIntelligenceModel.Result();
        StereoIntelligenceModel.Event event =
                new StereoIntelligenceModel.Event();
        event.kind = StereoIntelligenceModel.Kind.SPECTRAL_MUD;
        event.startSeconds = 8.0;
        event.endSeconds = 8.6;
        event.confidence = 0.95;
        event.primaryDb = -0.28;
        event.automatic = true;
        event.evidence = "controlled isolated low-mid event";
        model.events.add(event);
        analysis.stereoIntelligence = model;
        File repaired = new File(output, "integrity-event-repair.wav");
        StereoIntelligenceRenderer.Result eventRender =
                StereoIntelligenceRenderer.render(
                        () -> new FileInputStream(input), analysis,
                        transparent,
                        StereoIntelligenceRenderer.Mode.EVENT_REPAIR,
                        repaired, output, progress);
        if (eventRender.render.master.verification.clippedSamples != 0
                || eventRender.render.master.verification.nonFiniteSamples != 0
                || eventRender.delta == null
                || !eventRender.delta.protectedRegionsExact) {
            throw new AssertionError("Stereo Event Repair did not survive inherited-sample and edge guardrails");
        }
        String warning = FullAiReleaseQc.unresolvedContinuityWarning(analysis);
        if (!warning.contains("6.000000–6.125000 s")
                || !warning.contains("dither")
                || !warning.contains("not evidence of continuity repair")) {
            throw new AssertionError("Source continuity provenance warning was incomplete: "
                    + warning);
        }
        JSONObject continuity = (JSONObject) FullAiReportWriter.continuity(
                new ClickDropoutRepair.Result(), analysis);
        if (continuity.getInt("unresolved_source_zero_regions") != 1
                || continuity.getJSONArray(
                "source_zero_regions_100ms_or_longer").length() != 1
                || !continuity.getString(
                "post_dither_zero_count_semantics").contains(
                "does not prove repair")) {
            throw new AssertionError("QC JSON did not retain source-zero/dither semantics");
        }
        System.out.println("SOURCE_FORMAT 48000_HZ 16_BIT");
        System.out.println("INHERITED_FULL_SCALE_ALLOWED_PREMASTER 1 FINAL_CLIPS 0");
        System.out.println("SOURCE_ZERO_REGION_PRESERVED 6.000000-6.125000");
        System.out.println("STEREO_EVENT_REPAIR_PROVENANCE_PASS");
        System.out.println("SOURCE_INTEGRITY_PASS");
    }
}
