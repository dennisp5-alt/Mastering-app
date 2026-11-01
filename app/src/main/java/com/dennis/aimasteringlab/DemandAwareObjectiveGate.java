package com.dennis.aimasteringlab;

import java.util.Locale;

/**
 * v0.14.6: bounded final acceptance for a dynamics-led adaptive fallback ONLY.
 * Rendering, hard safety, passage preservation and DSP remain unchanged.
 * This is not a global mastering-score or generic natural-preservation override.
 */
final class DemandAwareObjectiveGate {
    static final double MAX_OBJECTIVE_LOSS = 0.050;
    static final double MAX_TECHNICAL_LOSS = 0.300;

    static final class Decision {
        boolean passed;
        boolean scopePassed;
        boolean preservationPassed;
        boolean objectivePassed;
        boolean technicalPassed;
        double objectiveDelta;
        double technicalDelta;
        String diagnostic;
    }

    private DemandAwareObjectiveGate() {}

    static Decision assess(FullAiMasteringEngine.Candidate candidate,
                           FullAiMasteringEngine.Candidate transparent,
                           BroadMasteringNeed.Result need,
                           AdaptiveDynamicsPreservation.Result preservation,
                           double sourceObjective,
                           boolean unresolvedNormalBroad,
                           boolean meaningfulBroadDelta) {
        Decision d = new Decision();
        d.scopePassed = unresolvedNormalBroad && need != null && need.required
                && AdaptiveDynamicsPreservation.envelope(need).active
                && candidate != null && transparent != null
                && candidate.strategy != null
                && candidate.strategy.startsWith("adaptive_broad_")
                && candidate.eligible && !candidate.promotionBlocked
                && candidate.passageCheck != null && candidate.passageCheck.passed()
                && meaningfulBroadDelta;
        d.preservationPassed = preservation != null && preservation.envelopePassed;
        d.objectiveDelta = candidate == null ? Double.NaN
                : candidate.measuredObjective - sourceObjective;
        d.technicalDelta = candidate == null || transparent == null ? Double.NaN
                : candidate.technicalScore - transparent.technicalScore;
        d.objectivePassed = Double.isFinite(d.objectiveDelta)
                && d.objectiveDelta >= -MAX_OBJECTIVE_LOSS - 1e-9;
        d.technicalPassed = Double.isFinite(d.technicalDelta)
                && d.technicalDelta >= -MAX_TECHNICAL_LOSS - 1e-9;
        d.passed = d.scopePassed && d.preservationPassed
                && d.objectivePassed && d.technicalPassed;
        d.diagnostic = String.format(Locale.US,
                "v0.14.6 adaptive dynamics final gate: %s • scope=%s • measured broad=%s"
                        + " • hard/passage=%s • dynamics envelope=%s • dynamic benefit=%s"
                        + " • objective delta %+.4f (floor -%.3f): %s"
                        + " • raw technical delta %+.4f (floor -%.3f): %s",
                d.passed ? "PASS" : "BLOCKED", d.scopePassed, meaningfulBroadDelta,
                candidate != null && candidate.eligible && candidate.passageCheck != null
                        && candidate.passageCheck.passed(),
                d.preservationPassed,
                preservation != null && preservation.dynamicBenefit,
                d.objectiveDelta, MAX_OBJECTIVE_LOSS,
                d.objectivePassed ? "PASS" : "FAIL",
                d.technicalDelta, MAX_TECHNICAL_LOSS,
                d.technicalPassed ? "PASS" : "FAIL");
        return d;
    }
}
