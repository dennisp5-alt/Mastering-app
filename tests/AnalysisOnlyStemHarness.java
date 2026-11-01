package com.dennis.aimasteringlab;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.json.JSONArray;
import org.json.JSONObject;

/** End-to-end test of Tier 2 stems that guide but never enter the waveform. */
public final class AnalysisOnlyStemHarness {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "Usage: AnalysisOnlyStemHarness fixture-directory work-directory");
        }
        File fixtures = new File(args[0]);
        File mix = new File(fixtures, "AnalysisOnlyMix.wav");
        File stems = new File(fixtures, "AnalysisOnlyStems.zip");
        File work = new File(args[1]);
        if (!work.exists() && !work.mkdirs()) throw new IOException("No work directory");
        AudioAnalyzer.Progress progress = (stage, percent) -> {};

        AnalysisResult source = AudioAnalyzer.analyze(mix.getName(),
                () -> new FileInputStream(mix), progress);
        source.stemArchiveName = stems.getName();
        source.stemArchiveSha256 = Hashing.sha256(stems);
        source.stems.addAll(StemInventory.inspect(stems, progress));
        StemInventory.compareAgainstMix(source);
        source.stemReconstruction = StemReconstruction.analyze(
                () -> new FileInputStream(mix), source, stems, progress);
        require(source.stemReconstruction.likelySeparationDerived,
                "fixture unexpectedly passed the production reconstruction gate");
        require(!source.stemReconstruction.globalPolarityRisk,
                "fixture introduced global polarity risk");
        source.stemAlignment = StemAlignmentValidator.analyze(
                () -> new FileInputStream(mix), source, stems, progress);
        for (StemAlignmentValidator.StemResult stem : source.stemAlignment.stems) {
            System.out.println("ANALYSIS_ALIGN " + stem.path
                    + " secure=" + stem.secure
                    + " informative=" + stem.informativeProbes
                    + " polarity=" + stem.positivePolarityProbes
                    + " lag=" + stem.minimumLagMs + "/" + stem.maximumLagMs
                    + " drift=" + stem.driftMs
                    + " reasons=" + stem.rejectionReasons);
        }
        source.stemTrust = StemTrustClassifier.classify(source);
        source.stemTrustTier = source.stemTrust.code();
        require(source.stemTrust.tier == StemTrustClassifier.Tier.TIER_2_ANALYSIS_ONLY,
                "aligned poor reconstruction did not enter Tier 2: " + source.stemTrust.status);
        source.stemArchiveAnalysisOnlyAccepted = true;
        source.vocalHumanity = VocalHumanityModel.analyzeForGuidance(
                stems, source, progress);
        require(source.vocalHumanity.available && source.vocalHumanity.analysisOnly,
                "analysis-only lead vocal was unavailable");
        require(source.vocalHumanity.rideCount() == 0,
                "analysis-only lead unexpectedly retained audio phrase rides");
        source.maskingPockets = MaskingPocketModel.analyzeForGuidance(
                stems, source, progress);
        require(source.maskingPockets.analysisOnly,
                "masking model did not retain analysis-only identity");
        require(!source.vocalHumanity.deEssEvents.isEmpty()
                        || !source.maskingPockets.events.isEmpty(),
                "fixture produced no eligible analysis-only control event");

        try {
            source.sections = LearnedSectionMapper.analyze(
                    () -> new FileInputStream(mix), source, progress);
        } catch (IOException ignored) {}
        source.aiDiagnostics = AdaptiveDiagnosticModel.analyze(
                () -> new FileInputStream(mix), source, progress);
        AutonomousMasteringBrain.Result brain = AutonomousMasteringBrain.analyze(
                () -> new FileInputStream(mix), source, progress);
        FullAiMasteringEngine.Result engine = FullAiMasteringEngine.run(
                () -> new FileInputStream(mix), source, brain, stems, work, progress);
        require(engine.stemCandidatesAttempted == 1,
                "Tier 2 must attempt exactly one authoritative-mix control candidate");

        FullAiMasteringEngine.Candidate guided = null;
        for (FullAiMasteringEngine.Candidate candidate : engine.candidates) {
            if ("analysis_only_stem_guided".equals(candidate.strategy)) guided = candidate;
        }
        require(guided != null && guided.rendered && guided.eligible,
                "analysis-only candidate did not survive render verification");
        require(guided.stemAssist.analysisOnlyControl,
                "candidate lost analysis-only identity");
        require(!guided.stemAssist.stemWaveformUsed,
                "analysis-only candidate incorrectly reported stem waveform use");
        require(guided.stemAssist.delta.protectedRegionsExact,
                "analysis-only edit reached the protected intro or ending");
        require(guided.stemAssist.delta.sourceCorrelation >= 0.995,
                "analysis-only edit exceeded the source-correlation guardrail");
        require(guided.render.master.verification.clippedSamples == 0
                        && guided.render.master.verification.nonFiniteSamples == 0,
                "analysis-only candidate introduced sample-integrity faults");
        require(guided.render.master.verification.truePeakEstimateDbtp <= -1.0,
                "analysis-only candidate exceeded the true-peak ceiling");

        FullAiReleaseQc.Result qc = new FullAiReleaseQc.Result();
        qc.initialPcmWinner = engine.selected;
        qc.finalCandidate = engine.selected;
        qc.finalMaster = engine.selected.file;
        qc.finalMasterVerification = engine.selected.render.master.verification;
        qc.finalMasterSha256 = Hashing.sha256(qc.finalMaster);
        qc.technicalVerdict = "PASS";
        JSONObject report = new JSONObject(FullAiReportWriter.create(
                source, brain, engine, qc));
        JSONObject stemReport = report.getJSONObject("stem_assist");
        require("TIER_2_ANALYSIS_ONLY".equals(stemReport.getString("trust_tier"))
                        && stemReport.getBoolean("accepted_for_analysis_only")
                        && !stemReport.getBoolean("accepted_for_audio_delta"),
                "QC JSON did not preserve the Tier 2 trust decision");
        JSONArray candidates = report.getJSONObject("candidate_competition")
                .getJSONArray("candidates");
        boolean reported = false;
        for (int index = 0; index < candidates.length(); index++) {
            JSONObject candidate = candidates.getJSONObject(index);
            if (!"analysis_only_stem_guided".equals(
                    candidate.getString("strategy"))) continue;
            JSONObject processing = candidate.getJSONObject("stem_guided_processing");
            reported = "analysis_only".equals(candidate.getString("stem_control_mode"))
                    && processing.getBoolean("analysis_only_control")
                    && !processing.getBoolean("stem_waveform_used");
        }
        require(reported,
                "QC JSON did not prove that the Tier 2 render used no stem waveform");

        System.out.println("ANALYSIS_ONLY_STEM_PASS tier=" + source.stemTrustTier
                + " corr=" + source.stemReconstruction.waveformCorrelation
                + " residualSnr=" + source.stemReconstruction.residualSnrDb
                + " secure=" + source.stemAlignment.secureStems
                + " deEss=" + source.vocalHumanity.deEssEvents.size()
                + " masking=" + source.maskingPockets.events.size()
                + " maxEdit=" + guided.stemAssist.maximumNominalEditDb
                + " waveformUsed=" + guided.stemAssist.stemWaveformUsed
                + " winner=" + engine.selected.strategy
                + " decision=" + engine.stemDecision);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
