package com.dennis.aimasteringlab;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;

/** Focused regression coverage for the seven v0.6.1 maintenance corrections. */
public final class MaintenanceRegressionHarness {
    private MaintenanceRegressionHarness() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException(
                    "Usage: MaintenanceRegressionHarness song.wav work-directory");
        }
        File input = new File(args[0]);
        File work = new File(args[1]);
        if (!work.exists() && !work.mkdirs()) {
            throw new IllegalStateException("Could not create work directory");
        }
        AudioAnalyzer.Progress progress = (stage, percent) -> {};
        AnalysisResult source = AudioAnalyzer.analyze(input.getName(),
                () -> new FileInputStream(input), progress);
        source.sections = LearnedSectionMapper.analyze(
                () -> new FileInputStream(input), source, progress);

        File compositeFile = new File(work, "maintenance-translation.wav");
        TranslationComposite.Result composite = TranslationComposite.build(
                input, source, source.sections, compositeFile, progress);
        TranslationAnalyzer.Result translation = TranslationAnalyzer.analyze(
                compositeFile, composite, progress);
        int rawStressProfiles = 0;
        for (TranslationAnalyzer.DeviceResult device : translation.devices) {
            if (device.peakDbfs > translation.referencePeakDbfs + 0.0001) {
                throw new AssertionError("Level-match peak cap failed for " + device.name);
            }
            if (device.rawHeadroomStressDb > 0.0) {
                rawStressProfiles++;
                if (device.clippingRisk) {
                    throw new AssertionError("Raw response boost was misreported as actual clipping");
                }
            }
        }
        JSONObject translationJson = (JSONObject) FullAiReportWriter.translation(translation);
        JSONObject firstDevice = translationJson.getJSONArray("devices").getJSONObject(0);
        if (!firstDevice.has("raw_response_peak_dbfs")
                || !firstDevice.has("level_matched_peak_dbfs")
                || !firstDevice.has("raw_headroom_stress_db")) {
            throw new AssertionError("Translation audit fields are incomplete");
        }

        FullAiMasteringEngine.Candidate selected = selectedCandidate(input, source);
        FullAiMasteringEngine.Result engine = new FullAiMasteringEngine.Result();
        engine.selected = selected;
        engine.transparent = selected;
        engine.selectionReason = "Controlled maintenance fixture";
        engine.candidates.add(selected);
        engine.rankedEligible.add(selected);
        FullAiReleaseQc.Result qc = new FullAiReleaseQc.Result();
        qc.finalCandidate = selected;
        qc.initialPcmWinner = selected;
        qc.technicalVerdict = "PASS";
        qc.translation = translation;
        qc.translationVerdict = translation.reviewCount == 0 ? "PASS" : "REVIEW";

        MasterConfidenceMap.Result map = MasterConfidenceMap.build(source, engine, qc);
        if (source.wav.durationSeconds >= 60.0
                && (map.sections.size() < 8 || map.sections.size() > 15)) {
            throw new AssertionError("Confidence Map did not produce 8-15 macro sections");
        }
        if (map.rawLearnedSectionCount <= map.sections.size()) {
            throw new AssertionError("Learned microstates were not consolidated");
        }
        for (MasterConfidenceMap.Section section : map.sections) {
            if (section.role.contains("silence") && section.role.contains("candidate")) {
                throw new AssertionError("Active song macro section was mislabeled silence: "
                        + section.role);
            }
            if (!section.globalProcessingApplies || section.changed.isEmpty()) {
                throw new AssertionError("Global selected processing was omitted from a section");
            }
            if (section.sectionSpecificChange) {
                throw new AssertionError("Fixture unexpectedly reported a local repair");
            }
        }

        ClickDropoutRepair.Result noRepair = new ClickDropoutRepair.Result();
        JSONObject continuity = (JSONObject) FullAiReportWriter.continuity(noRepair, 48000);
        if (!continuity.isNull("protected_regions_exact")
                || !"NOT_APPLICABLE_NO_REPAIR".equals(
                    continuity.getString("protected_regions_verification"))) {
            throw new AssertionError("No-repair protected-region state was not N/A");
        }

        FullAiMasteringEngine.Candidate rawLeader = new FullAiMasteringEngine.Candidate();
        rawLeader.strategy = "stereo_event_repair";
        rawLeader.rendered = true;
        rawLeader.eligible = true;
        rawLeader.rank = 1;
        rawLeader.score = 91.100;
        rawLeader.promotionBlocked = true;
        FullAiMasteringEngine.Candidate gatedWinner = new FullAiMasteringEngine.Candidate();
        gatedWinner.strategy = "transparent_flat_gain";
        gatedWinner.rendered = true;
        gatedWinner.eligible = true;
        gatedWinner.rank = 2;
        gatedWinner.score = 91.080;
        gatedWinner.selectedAfterPromotionGates = true;
        String selection = FullAiMasteringEngine.defaultSelectionReason(
                rawLeader, gatedWinner);
        if (!selection.contains("led the raw score table")
                || !selection.contains("did not clear")
                || !selection.contains("raw score rank 2")) {
            throw new AssertionError("Rank/promotion selection explanation is ambiguous");
        }
        if (!"PROMOTION_GATE_BLOCKED".equals(rawLeader.decisionOutcome())
                || !"SELECTED_AFTER_PROMOTION_GATES".equals(
                    gatedWinner.decisionOutcome())) {
            throw new AssertionError("Candidate decision outcomes are ambiguous");
        }

        JSONObject preference = FullAiReportWriter.preference(
                new DennisPreferenceMemory.Profile());
        JSONObject album = FullAiReportWriter.album(
                new AlbumConsistencyModel.Profile());
        if (!"USED_FOR_THIS_RUN_AT_RUN_START".equals(
                preference.getString("snapshot_scope"))
                || !"USED_FOR_THIS_RUN_AT_RUN_START".equals(
                    album.getString("snapshot_scope"))) {
            throw new AssertionError("Run-start profile snapshot labels are missing");
        }

        FullAiReleaseQc.Result propagated = new FullAiReleaseQc.Result();
        propagated.technicalVerdict = "PASS";
        propagated.translationVerdict = "REVIEW";
        if (!"PASS • TRANSLATION REVIEW".equals(propagated.combinedVerdict())) {
            throw new AssertionError("Translation review did not propagate to display verdict");
        }
        TranslationAnalyzer.Result syntheticReview = new TranslationAnalyzer.Result();
        TranslationAnalyzer.DeviceResult reviewDevice =
                new TranslationAnalyzer.DeviceResult();
        reviewDevice.name = "Controlled review profile";
        reviewDevice.verdict = "REVIEW";
        reviewDevice.reviewReasons.add("controlled lift review");
        syntheticReview.devices.add(reviewDevice);
        syntheticReview.reviewCount = 1;
        String propagatedWarning = FullAiReleaseQc.translationReviewWarning(
                syntheticReview);
        if (!propagatedWarning.contains("Controlled review profile")
                || !propagatedWarning.contains("controlled lift review")) {
            throw new AssertionError("Translation review warning lacks device/reason evidence");
        }

        JSONObject mapJson = MasterConfidenceMap.json(map);
        JSONArray sections = mapJson.getJSONArray("sections");
        if (mapJson.getInt("sections_with_any_selected_change") != sections.length()
                || mapJson.getInt("sections_with_global_processing") != sections.length()
                || mapJson.getInt("sections_with_section_specific_change") != 0) {
            throw new AssertionError("Confidence Map change counters are inaccurate");
        }
        System.out.println("MAINTENANCE_REGRESSION_PASS translation="
                + translation.passCount + "/" + translation.reviewCount
                + " raw_stress_profiles=" + rawStressProfiles
                + " sections=" + map.rawLearnedSectionCount + "->" + sections.length());
    }

    private static FullAiMasteringEngine.Candidate selectedCandidate(
            File input, AnalysisResult source) {
        AutonomousMasteringBrain.Plan plan = new AutonomousMasteringBrain.Plan();
        plan.mode = AutonomousMasteringBrain.Mode.CONSERVATIVE;
        plan.strategy = "tonal_corrective";
        plan.lowShelfDb = -0.469;
        plan.lowMidDb = -0.130;
        plan.sideScale = 1.0;

        MasterRenderer.Result master = new MasterRenderer.Result();
        master.file = input;
        master.gainDb = 1.2394;
        master.verification = source;

        AutonomousMasteringRenderer.Result render = new AutonomousMasteringRenderer.Result();
        render.plan = plan;
        render.master = master;
        render.correctedPremaster = source;
        render.maximumGlobalEqDb = 0.469;

        FullAiMasteringEngine.Candidate selected = new FullAiMasteringEngine.Candidate();
        selected.strategy = plan.strategy;
        selected.plan = plan;
        selected.file = input;
        selected.render = render;
        selected.rendered = true;
        selected.eligible = true;
        selected.rank = 1;
        selected.score = 90.0;
        selected.selectedAfterPromotionGates = true;
        return selected;
    }
}
