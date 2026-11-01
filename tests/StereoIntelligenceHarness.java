package com.dennis.aimasteringlab;

import java.io.File;
import java.io.FileInputStream;
import java.util.EnumSet;
import java.util.Locale;

import org.json.JSONObject;

/** End-to-end regression for the three v0.5 stereo modules and combined pass. */
public final class StereoIntelligenceHarness {
    private StereoIntelligenceHarness() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "Usage: StereoIntelligenceHarness fixture.wav output-directory");
        }
        File input = new File(args[0]);
        File output = new File(args[1]);
        if (!output.exists() && !output.mkdirs()) {
            throw new IllegalStateException("Could not create harness output directory");
        }
        AudioAnalyzer.Progress progress = (stage, percent) -> {};
        AnalysisResult analysis = AudioAnalyzer.analyze(input.getName(),
                () -> new FileInputStream(input), progress);
        analysis.sections = LearnedSectionMapper.analyze(
                () -> new FileInputStream(input), analysis, progress);
        analysis.aiDiagnostics = AdaptiveDiagnosticModel.analyze(
                () -> new FileInputStream(input), analysis, progress);
        analysis.stereoIntelligence = StereoIntelligenceModel.analyze(
                () -> new FileInputStream(input), analysis, progress);

        StereoIntelligenceModel.Result model = analysis.stereoIntelligence;
        if (!model.hasEventRepair() || !model.hasLowEnd() || !model.hasVocalFocus()) {
            throw new AssertionError("Fixture did not activate all three modules: "
                    + model.compactSummary());
        }
        if (model.kickLikeWindowsProtected < 1) {
            throw new AssertionError("Low-End Intelligence failed to protect the kick-like control");
        }
        for (StereoIntelligenceModel.Event event : model.events) {
            if (!event.automatic) continue;
            if (event.startSeconds < 2.0
                    || event.endSeconds > analysis.wav.durationSeconds - 8.0) {
                throw new AssertionError("An automatic event entered a protected edge");
            }
        }

        AutonomousMasteringBrain.Result brain = AutonomousMasteringBrain.analyze(
                () -> new FileInputStream(input), analysis, progress);
        FullAiMasteringEngine.Result engine = FullAiMasteringEngine.run(
                () -> new FileInputStream(input), analysis, brain, output, progress);
        for (FullAiMasteringEngine.Candidate candidate : engine.candidates) {
            if (candidate.intelligenceAware) {
                System.out.println("CANDIDATE " + candidate.strategy + " "
                        + candidate.status + " " + candidate.rejectionReasons);
            }
        }
        if (engine.intelligenceCandidatesAttempted != 4
                || engine.intelligenceCandidatesEligible != 4) {
            throw new AssertionError("Expected four eligible stereo-intelligence candidates, found "
                    + engine.intelligenceCandidatesEligible + "/"
                    + engine.intelligenceCandidatesAttempted);
        }

        EnumSet<StereoIntelligenceRenderer.Mode> modes = EnumSet.noneOf(
                StereoIntelligenceRenderer.Mode.class);
        for (FullAiMasteringEngine.Candidate candidate : engine.candidates) {
            if (!candidate.intelligenceAware) continue;
            if (!candidate.rendered || !candidate.eligible
                    || candidate.stereoIntelligence == null) {
                throw new AssertionError(candidate.strategy
                        + " did not survive render verification");
            }
            StereoIntelligenceRenderer.Result rendered = candidate.stereoIntelligence;
            modes.add(rendered.mode);
            if (!rendered.authoritativeStereoOnly || rendered.stemWaveformUsed) {
                throw new AssertionError(candidate.strategy
                        + " violated the authoritative-stereo-only contract");
            }
            if (rendered.delta == null || !rendered.delta.protectedRegionsExact
                    || rendered.delta.sourceCorrelation < 0.995) {
                throw new AssertionError(candidate.strategy
                        + " failed protected-edge or correlation verification");
            }
            AnalysisResult verified = rendered.render.master.verification;
            if (verified.wav.frames != analysis.wav.frames
                    || verified.truePeakEstimateDbtp > -1.0
                    || verified.clippedSamples != 0
                    || Math.abs(verified.integratedLufs + 14.0) > 0.006) {
                throw new AssertionError(candidate.strategy
                        + " failed final master measurements");
            }
            System.out.printf(Locale.US,
                    "MODULE %s events=%d max=%.3f benefit=%.3f corr=%.8f intro=%.2g tail=%.2g LUFS=%.4f TP=%.4f%n",
                    candidate.strategy, rendered.correctionEvents,
                    rendered.maximumNominalEditDb,
                    rendered.measuredTargetBenefit,
                    rendered.delta.sourceCorrelation,
                    rendered.delta.protectedIntroMaxDelta,
                    rendered.delta.protectedEndingMaxDelta,
                    verified.integratedLufs, verified.truePeakEstimateDbtp);
        }
        if (modes.size() != StereoIntelligenceRenderer.Mode.values().length) {
            throw new AssertionError("Not every module/combined mode was exercised: " + modes);
        }
        if (engine.selected == null || engine.selected.promotionBlocked) {
            throw new AssertionError("Autonomous selection returned no promotable winner");
        }
        int codecFallbackEligible = 0;
        int coreFallbacks = 0;
        int intelligenceFallbacks = 0;
        int advancedFallbacks = 0;
        int stemFallbacks = 0;
        for (FullAiMasteringEngine.Candidate candidate : engine.rankedEligible) {
            if (candidate.promotionBlocked) continue;
            codecFallbackEligible++;
            if (candidate.stemAware) stemFallbacks++;
            else if (candidate.advancedAware) advancedFallbacks++;
            else if (candidate.intelligenceAware) intelligenceFallbacks++;
            else coreFallbacks++;
        }
        // Every independently cleared stage remains a valid lower-processing
        // codec fallback. All siblings that failed promotion remain blocked.
        if (codecFallbackEligible != 3 || coreFallbacks != 1
                || intelligenceFallbacks != 1 || advancedFallbacks != 1
                || stemFallbacks != 0 || engine.transparent.promotionBlocked) {
            throw new AssertionError("Promotion-blocked candidates could re-enter codec fallback");
        }
        FullAiReleaseQc.Result qc = new FullAiReleaseQc.Result();
        qc.initialPcmWinner = engine.selected;
        qc.finalCandidate = engine.selected;
        qc.finalMaster = engine.selected.file;
        qc.finalMasterVerification = engine.selected.render.master.verification;
        qc.finalMasterSha256 = qc.finalMasterVerification.sourceSha256;
        qc.technicalVerdict = "PASS";
        String report = FullAiReportWriter.create(analysis, brain, engine, qc);
        JSONObject json = new JSONObject(report);
        if (!"0.9.1".equals(json.getString("version"))) {
            throw new AssertionError("QC report did not identify v0.9.1");
        }
        JSONObject stereo = json.getJSONObject("authoritative_stereo_intelligence");
        if (stereo.getInt("available_modules") != 3
                || stereo.getBoolean("stem_waveform_used")) {
            throw new AssertionError("QC report omitted or mislabelled stereo intelligence");
        }
        JSONObject advanced = json.getJSONObject("v07_advanced_intelligence");
        if (!advanced.getBoolean("section_aware_available")
                || !advanced.getBoolean(
                        "ai_generation_artifact_repair_available")
                || advanced.getBoolean("stem_waveform_used")) {
            throw new AssertionError("QC report omitted or mislabelled v0.7 intelligence");
        }
        JSONObject competition = json.getJSONObject("candidate_competition");
        if (competition.getInt("stereo_intelligence_candidates_attempted") != 4
                || competition.getInt("v07_advanced_candidates_attempted") < 1
                || competition.getJSONArray("candidates").length() < 9) {
            throw new AssertionError("QC report did not include the expanded competition");
        }
        if (!json.has("dennis_preference_memory")
                || !json.has("album_consistency")
                || !json.has("master_confidence_map")) {
            throw new AssertionError("QC report omitted v0.6 learning/confidence evidence");
        }
        System.out.println("MODEL " + model.compactSummary());
        System.out.println("WINNER " + engine.selected.strategy);
        System.out.println("DECISION " + engine.intelligenceDecision);
        System.out.println("STEREO_INTELLIGENCE_PASS");
    }
}
