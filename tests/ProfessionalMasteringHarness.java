package com.dennis.aimasteringlab;

import java.io.File;
import java.io.FileInputStream;
import java.util.EnumSet;
import java.util.Locale;

/** End-to-end host regression for all four v0.8 professional modules. */
public final class ProfessionalMasteringHarness {
    private ProfessionalMasteringHarness() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "Usage: ProfessionalMasteringHarness fixture.wav output-directory");
        }
        File input = new File(args[0]);
        File output = new File(args[1]);
        if (!output.exists() && !output.mkdirs()) {
            throw new IllegalStateException("Could not create output directory");
        }
        AudioAnalyzer.Progress progress = (stage, percent) -> {};
        AnalysisResult analysis = AudioAnalyzer.analyze(input.getName(),
                () -> new FileInputStream(input), progress);
        analysis.professionalIntelligence =
                ProfessionalMasteringModel.analyze(
                        () -> new FileInputStream(input), analysis,
                        progress);
        ProfessionalMasteringModel.Result model =
                analysis.professionalIntelligence;
        for (ProfessionalMasteringModel.Event event : model.events) {
            System.out.printf(Locale.US,
                    "V08_EVIDENCE %s %.6f-%.6f auto=%s confidence=%.4f edit=%+.3f %s%n",
                    event.kind, event.startSeconds, event.endSeconds,
                    event.automatic, event.confidence, event.primaryDb,
                    event.evidence);
        }
        if (!model.hasDepthReverb() || !model.hasLowPhase()
                || !model.hasVocalArtifacts()
                || !model.limiterCrestAvailable) {
            throw new AssertionError("Fixture did not activate all four v0.8 modules: "
                    + model.compactSummary() + " " + model.safetyNotes
                    + " limiterRequired=" + analysis.limiterRequired
                    + " allowance=" + analysis.limiterReductionAllowanceDb);
        }
        for (ProfessionalMasteringModel.Event event : model.events) {
            if (!event.automatic) continue;
            if (event.startSeconds < 2.0
                    || event.endSeconds > analysis.wav.durationSeconds - 8.0) {
                throw new AssertionError("An automatic v0.8 event entered a protected edge");
            }
            if (event.primaryDb > 0.0 || event.primaryDb < -1.20) {
                throw new AssertionError("An event violated the attenuation-only cap");
            }
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
        EnumSet<ProfessionalMasteringRenderer.Mode> modes =
                EnumSet.noneOf(ProfessionalMasteringRenderer.Mode.class);
        for (ProfessionalMasteringRenderer.Mode mode
                : ProfessionalMasteringRenderer.Mode.values()) {
            File renderedFile = new File(output,
                    mode.strategy + ".wav");
            ProfessionalMasteringRenderer.Result rendered =
                    ProfessionalMasteringRenderer.render(
                            () -> new FileInputStream(input), analysis,
                            transparent, mode, renderedFile, output,
                            progress);
            modes.add(mode);
            AnalysisResult verified = rendered.render.master.verification;
            if (verified.wav.frames != analysis.wav.frames
                    || verified.wav.bitsPerSample != 24
                    || verified.truePeakEstimateDbtp > -1.0
                    || verified.clippedSamples != 0
                    || verified.nonFiniteSamples != 0
                    || (!rendered.render.master.exactTargetReached
                    && !rendered.render.master.dynamicsProtected)) {
                throw new AssertionError(mode
                        + " failed final-master verification");
            }
            if (rendered.wideningUsed) {
                throw new AssertionError(mode + " used prohibited widening");
            }
            if (mode != ProfessionalMasteringRenderer.Mode.LIMITER_CREST) {
                if (rendered.delta == null
                        || !rendered.delta.protectedRegionsExact
                        || rendered.delta.sourceCorrelation < 0.9950) {
                    throw new AssertionError(mode
                            + " violated source/protected-edge guardrails");
                }
            } else if (rendered.limiterOptimisation == null
                    || rendered.limiterOptimisation.trials.size() != MasterRenderer.LimiterProfile.values().length
                    || rendered.limiterOptimisation.selected == null) {
                throw new AssertionError("Limiter/Crest did not compare four rendered profiles");
            }
            System.out.printf(Locale.US,
                    "V08_MODULE %s events=%d max=%.3f target=%.3f depth=%.3f phase=%.4f vocal=%.3f limiter=%s LUFS=%.4f TP=%.4f%n",
                    mode.strategy, rendered.correctionEvents,
                    rendered.maximumNominalEditDb,
                    rendered.measuredTargetBenefit,
                    rendered.depthSideReductionDb,
                    rendered.lowPhaseImprovement,
                    rendered.vocalArtifactReductionDb,
                    verified == null ? "none"
                            : rendered.render.master.limiterProfile,
                    verified.integratedLufs,
                    verified.truePeakEstimateDbtp);
        }
        if (modes.size()
                != ProfessionalMasteringRenderer.Mode.values().length) {
            throw new AssertionError("Not every v0.8 mode was exercised");
        }
        System.out.println("PROFESSIONAL_MODEL " + model.compactSummary());
        System.out.println("PROFESSIONAL_MASTERING_PASS");
    }
}
