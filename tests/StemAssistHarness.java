package com.dennis.aimasteringlab;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/** End-to-end JVM regression for safe promotion, timing rejection and ZIP safety. */
public final class StemAssistHarness {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "Usage: StemAssistHarness fixture-directory work-directory");
        }
        File fixtures = new File(args[0]);
        File work = new File(args[1]);
        if (!work.exists() && !work.mkdirs()) {
            throw new IOException("Could not create stem regression directory");
        }
        File mix = new File(fixtures, "StemFixtureMix.wav");
        File safeZip = new File(fixtures, "StemFixtureSafe.zip");
        File delayedZip = new File(fixtures, "StemFixtureDelayed.zip");
        File unsafeZip = new File(fixtures, "StemFixtureUnsafePath.zip");
        AudioAnalyzer.Progress progress = (stage, percent) -> {};

        AnalysisResult safe = prepare(mix, safeZip, progress);
        require(safe.stemReconstruction.rawStemSumAllowed,
                "controlled production stems did not reconstruct the mix");
        for (StemAlignmentValidator.StemResult stem : safe.stemAlignment.stems) {
            System.out.println("ALIGN " + stem.path + " secure=" + stem.secure
                    + " informative=" + stem.informativeProbes
                    + " polarity=" + stem.positivePolarityProbes
                    + " lag=" + stem.minimumLagMs + "/" + stem.maximumLagMs
                    + " drift=" + stem.driftMs + " reasons=" + stem.rejectionReasons);
        }
        require(safe.stemAlignment.secure("Lead Vocal.wav"),
                "lead stem did not pass five-position alignment");
        require(safe.stemAlignment.secure("Electric Guitar.wav"),
                "guitar stem did not pass five-position alignment");
        safe.stemTrust = StemTrustClassifier.classify(safe);
        safe.stemTrustTier = safe.stemTrust.code();
        require(safe.stemTrust.tier == StemTrustClassifier.Tier.TIER_1_AUDIO_DELTA,
                "exact production stems did not enter Tier 1");
        safe.stemArchiveAccepted = true;
        safe.vocalHumanity = VocalHumanityModel.analyze(safeZip, safe, progress);
        require(safe.vocalHumanity.available,
                "controlled lead stem was not available to the vocal model");
        require(safe.vocalHumanity.hasCorrections(),
                "controlled vocal fixture produced no targeted correction");
        safe.maskingPockets = MaskingPocketModel.analyze(safeZip, safe, progress);
        System.out.println("MASKING " + safe.maskingPockets.compactSummary()
                + " status=" + safe.maskingPockets.status
                + " notes=" + safe.maskingPockets.safetyNotes);
        require(safe.maskingPockets.available,
                "controlled guitar was not available to the masking model");
        require(safe.maskingPockets.hasCorrections(),
                "controlled masking burst produced no targeted pocket");

        AutonomousMasteringBrain.Result brain = AutonomousMasteringBrain.analyze(
                () -> new FileInputStream(mix), safe, progress);
        FullAiMasteringEngine.Result engine = FullAiMasteringEngine.run(
                () -> new FileInputStream(mix), safe, brain, safeZip, work, progress);
        require(engine.stemCandidatesAttempted == 3,
                "all three guarded stem strategies were not attempted");
        require(engine.stemCandidatesEligible >= 1,
                "no stem-aware render survived measured verification");
        int verifiedStemCandidates = 0;
        for (FullAiMasteringEngine.Candidate candidate : engine.candidates) {
            System.out.println(candidate.compactSummary());
            if (candidate.stemAware) {
                System.out.println("STEM_SCORE " + candidate.strategy
                        + " evidenceBonus=" + candidate.stemEvidenceBonus
                        + " processingCost=" + candidate.processingCost
                        + " benefit=" + (candidate.stemAssist == null ? Double.NaN
                        : candidate.stemAssist.evidenceBenefit)
                        + " confidence=" + (candidate.stemAssist == null ? Double.NaN
                        : candidate.stemAssist.modelConfidence));
            }
            if (!candidate.stemAware || !candidate.rendered) continue;
            verifiedStemCandidates++;
            require(candidate.stemAssist != null && candidate.stemAssist.delta != null,
                    "stem candidate has no delta audit");
            require(candidate.stemAssist.delta.protectedRegionsExact,
                    "stem candidate leaked into protected intro/ending");
            require(candidate.stemAssist.delta.sourceCorrelation >= 0.995,
                    "stem candidate exceeded mix-correlation guardrail");
            require(candidate.render.master.verification.truePeakEstimateDbtp <= -1.0,
                    "stem candidate exceeded true-peak ceiling");
            require(Math.abs(candidate.render.master.verification.integratedLufs + 14.0) <= 0.02
                            || candidate.render.master.dynamicsProtected,
                    "stem candidate missed loudness without a dynamics-protection decision");
        }
        require(verifiedStemCandidates >= 1,
                "no rendered stem candidate was available for boundary verification");

        AnalysisResult delayed = prepare(mix, delayedZip, progress);
        StemAlignmentValidator.StemResult delayedGuitar =
                delayed.stemAlignment.find("Electric Guitar.wav");
        require(delayedGuitar != null && !delayedGuitar.secure,
                "20 ms delayed guitar incorrectly passed local timing validation");

        boolean unsafeRejected = false;
        try {
            StemInventory.inspect(unsafeZip, progress);
        } catch (IOException expected) {
            unsafeRejected = true;
        }
        require(unsafeRejected, "archive traversal path was not rejected");

        System.out.println("STEM_ASSIST_PASS winner=" + engine.selected.strategy
                + " stemWinner=" + engine.selected.stemAware
                + " eligible=" + engine.stemCandidatesEligible
                + " vocalRides=" + safe.vocalHumanity.rideCount()
                + " deEss=" + safe.vocalHumanity.deEssEvents.size()
                + " maskingEvents=" + safe.maskingPockets.events.size()
                + " decision=" + engine.stemDecision);
    }

    private static AnalysisResult prepare(File mix, File archive,
                                          AudioAnalyzer.Progress progress)
            throws Exception {
        AnalysisResult analysis = AudioAnalyzer.analyze(mix.getName(),
                () -> new FileInputStream(mix), progress);
        try {
            analysis.sections = LearnedSectionMapper.analyze(
                    () -> new FileInputStream(mix), analysis, progress);
        } catch (IOException ignored) {
            // Short deterministic fixtures may use the base energy map.
        }
        analysis.aiDiagnostics = AdaptiveDiagnosticModel.analyze(
                () -> new FileInputStream(mix), analysis, progress);
        analysis.stemArchiveName = archive.getName();
        analysis.stemArchiveSha256 = Hashing.sha256(archive);
        analysis.stems.addAll(StemInventory.inspect(archive, progress));
        StemInventory.compareAgainstMix(analysis);
        analysis.stemReconstruction = StemReconstruction.analyze(
                () -> new FileInputStream(mix), analysis, archive, progress);
        analysis.stemAlignment = StemAlignmentValidator.analyze(
                () -> new FileInputStream(mix), analysis, archive, progress);
        return analysis;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
