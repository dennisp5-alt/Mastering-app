package com.dennis.aimasteringlab;

import java.io.File;
import java.io.FileInputStream;
import java.util.EnumSet;
import java.util.Locale;

/** End-to-end host regression for the three v0.7 additions. */
public final class AdvancedIntelligenceHarness {
    private AdvancedIntelligenceHarness() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "Usage: AdvancedIntelligenceHarness fixture.wav output-directory");
        }
        File input = new File(args[0]);
        File output = new File(args[1]);
        if (!output.exists() && !output.mkdirs()) {
            throw new IllegalStateException("Could not create output directory");
        }
        AudioAnalyzer.Progress progress = (stage, percent) -> {};
        AnalysisResult analysis = AudioAnalyzer.analyze(input.getName(),
                () -> new FileInputStream(input), progress);
        analysis.sections = controlledSections(analysis.wav.durationSeconds);
        analysis.aiDiagnostics = AdaptiveDiagnosticModel.analyze(
                () -> new FileInputStream(input), analysis, progress);
        analysis.advancedIntelligence = AdvancedStereoModel.analyze(
                () -> new FileInputStream(input), analysis, progress);

        AdvancedStereoModel.Result model = analysis.advancedIntelligence;
        for (AdvancedStereoModel.Event event : model.events) {
            System.out.printf(Locale.US,
                    "V07_EVIDENCE %s %.6f-%.6f auto=%s confidence=%.4f edit=%+.3f %s%n",
                    event.kind, event.startSeconds, event.endSeconds,
                    event.automatic, event.confidence, event.primaryDb,
                    event.evidence);
        }
        if (!model.hasSectionAware() || !model.hasArtifactRepair()
                || !model.hasTransientControl()) {
            throw new AssertionError("Fixture did not activate all three v0.7 modules: "
                    + model.compactSummary() + " " + model.safetyNotes);
        }
        if (model.transientsProtected < 1 || !model.limiterPressure) {
            throw new AssertionError("Transient necessity/protection controls did not activate");
        }
        for (AdvancedStereoModel.Event event : model.events) {
            if (!event.automatic) continue;
            if (event.startSeconds < 2.0
                    || event.endSeconds > analysis.wav.durationSeconds - 8.0) {
                throw new AssertionError("An automatic v0.7 event entered a protected edge");
            }
        }

        AutonomousMasteringBrain.Result brain = AutonomousMasteringBrain.analyze(
                () -> new FileInputStream(input), analysis, progress);
        FullAiMasteringEngine.Result engine = FullAiMasteringEngine.run(
                () -> new FileInputStream(input), analysis, brain, output,
                progress);
        if (engine.advancedCandidatesAttempted != 4
                || engine.advancedCandidatesEligible != 4) {
            for (FullAiMasteringEngine.Candidate candidate : engine.candidates) {
                if (candidate.advancedAware) System.out.println("V07_REJECTION "
                        + candidate.strategy + " " + candidate.rejectionReasons);
            }
            throw new AssertionError("Expected four eligible v0.7 candidates, found "
                    + engine.advancedCandidatesEligible + "/"
                    + engine.advancedCandidatesAttempted);
        }

        EnumSet<AdvancedStereoRenderer.Mode> modes = EnumSet.noneOf(
                AdvancedStereoRenderer.Mode.class);
        for (FullAiMasteringEngine.Candidate candidate : engine.candidates) {
            if (!candidate.advancedAware) continue;
            if (!candidate.rendered || !candidate.eligible
                    || candidate.advancedIntelligence == null) {
                throw new AssertionError(candidate.strategy
                        + " did not survive render verification: "
                        + candidate.rejectionReasons);
            }
            AdvancedStereoRenderer.Result rendered =
                    candidate.advancedIntelligence;
            modes.add(rendered.mode);
            if (!rendered.authoritativeStereoOnly || rendered.stemWaveformUsed
                    || rendered.delta == null
                    || !rendered.delta.protectedRegionsExact
                    || rendered.delta.sourceCorrelation < 0.9955) {
                throw new AssertionError(candidate.strategy
                        + " violated source/protected-edge guardrails");
            }
            AnalysisResult verified = rendered.render.master.verification;
            if (verified.wav.frames != analysis.wav.frames
                    || verified.truePeakEstimateDbtp > -1.0
                    || verified.clippedSamples != 0
                    || (!rendered.render.master.exactTargetReached
                    && !rendered.render.master.dynamicsProtected)) {
                throw new AssertionError(candidate.strategy
                        + " failed final-master verification");
            }
            System.out.printf(Locale.US,
                    "V07_MODULE %s events=%d max=%.3f target=%.3f section=%.3f artifact=%.3f transient=%.3f corr=%.8f LUFS=%.4f TP=%.4f%n",
                    candidate.strategy, rendered.correctionEvents,
                    rendered.maximumNominalEditDb,
                    rendered.measuredTargetBenefit,
                    rendered.sectionLiftImprovementLu,
                    rendered.artifactReductionDb,
                    rendered.transientPeakReductionDb,
                    rendered.delta.sourceCorrelation,
                    verified.integratedLufs, verified.truePeakEstimateDbtp);
        }
        if (modes.size() != AdvancedStereoRenderer.Mode.values().length) {
            throw new AssertionError("Not every v0.7 mode was exercised: "
                    + modes);
        }
        if (engine.selected == null || engine.selected.promotionBlocked) {
            throw new AssertionError("No final promotable winner remained");
        }
        System.out.println("ADVANCED_MODEL " + model.compactSummary());
        System.out.println("ADVANCED_DECISION " + engine.advancedDecision);
        System.out.println("ADVANCED_INTELLIGENCE_PASS");
    }

    private static TrackSections controlledSections(double duration) {
        TrackSections out = new TrackSections();
        out.learnedMapping = true;
        out.learnedStates = 2;
        out.mappingConfidence = 0.91;
        out.quietLufs = -20.1;
        out.chorusProxyLufs = -19.2;
        out.quietToChorusLiftLu = 0.9;
        add(out, "intro_recurring_quieter_state_candidate", 0.0, 10.0,
                -20.1);
        add(out, "recurring_high_energy_state_candidate", 10.0, 8.0,
                -19.2);
        add(out, "recurring_quieter_state_candidate", 18.0, 9.0,
                -20.0);
        add(out, "outro_recurring_high_energy_state_candidate", 27.0,
                Math.max(0.0, duration - 27.0), -19.1);
        return out;
    }

    private static void add(TrackSections out, String role, double start,
                            double duration, double lufs) {
        TrackSections.Segment segment = new TrackSections.Segment();
        segment.role = role;
        segment.startSeconds = start;
        segment.durationSeconds = duration;
        segment.averageLufs = lufs;
        segment.confidence = 0.91;
        out.learnedStructure.add(segment);
    }
}
