package com.dennis.aimasteringlab;

import java.util.Locale;

/**
 * Determines whether a source has enough measured whole-song evidence that a
 * broad mastering pass should be established before narrow repair candidates
 * are allowed to become the final master.
 *
 * This is deliberately independent of loudness compliance. A raw mix can
 * already sit near -14 LUFS and still need tonal/dynamic mastering, while a
 * genuinely finished source can remain transparent when no broad demand is
 * measured.
 */
final class BroadMasteringNeed {
    static final class Result {
        boolean required;
        boolean broadProcessingAllowed;
        double reliability;
        double eqDemandDb;
        double dynamicsDemand;
        double stereoDemand;
        double objectiveGainPrediction;
        double demandScore;
        String strategy;
        String verdict;

        String summary() {
            return String.format(Locale.US,
                    "%s • demand %.3f • reliability %.3f • EQ %.3f dB • dynamics %.3f • stereo %.3f • predicted objective gain %.3f • plan %s",
                    verdict, demandScore, reliability, eqDemandDb,
                    dynamicsDemand, stereoDemand, objectiveGainPrediction,
                    strategy == null ? "unavailable" : strategy);
        }
    }

    private BroadMasteringNeed() {}

    static Result assess(AnalysisResult source,
                         AutonomousMasteringBrain.Result brain) {
        Result out = new Result();
        out.broadProcessingAllowed = source != null
                && (source.sourceGuard == null
                || source.sourceGuard.allowsBroadProcessing());
        if (source == null || brain == null || brain.features == null) {
            out.verdict = "BROAD_NEED_UNAVAILABLE";
            return out;
        }
        out.reliability = brain.features.reliability;
        if (!out.broadProcessingAllowed) {
            out.verdict = "BROAD_MASTERING_BLOCKED_BY_SOURCE_GUARD";
            return out;
        }

        AutonomousMasteringBrain.Plan plan = preferredBroadPlan(brain);
        if (plan == null) {
            out.verdict = "NO_BROAD_PLAN_AVAILABLE";
            return out;
        }
        out.strategy = plan.strategy;
        out.eqDemandDb = Math.abs(plan.lowShelfDb)
                + Math.abs(plan.lowMidDb)
                + Math.abs(plan.presenceDb)
                + Math.abs(plan.highShelfDb);
        out.dynamicsDemand = plan.compressorEnabled
                ? 0.45 + 0.35 * Math.min(1.8,
                Math.max(0.0, plan.compressorMaximumReductionDb)) : 0.0;
        out.stereoDemand = 4.0 * Math.abs(1.0 - plan.sideScale);
        out.objectiveGainPrediction = Math.max(0.0,
                plan.predictedObjectiveAfter - plan.objectiveBefore);
        out.demandScore = out.eqDemandDb + out.dynamicsDemand
                + out.stereoDemand;

        // Local eventScale alone is intentionally not broad-mastering demand.
        // Require reliable, whole-song evidence from EQ, bus dynamics or width.
        boolean wholeSongMove = out.eqDemandDb >= 0.20
                || out.dynamicsDemand >= 0.50
                || out.stereoDemand >= 0.10;
        boolean enoughEvidence = out.reliability >= 0.45
                && (out.demandScore >= 0.32
                || out.objectiveGainPrediction >= 0.70);
        out.required = wholeSongMove && enoughEvidence;
        out.verdict = out.required
                ? "BROAD_MASTERING_REQUIRED"
                : "BROAD_MASTERING_NOT_REQUIRED";
        return out;
    }

    private static AutonomousMasteringBrain.Plan preferredBroadPlan(
            AutonomousMasteringBrain.Result brain) {
        AutonomousMasteringBrain.Plan sonic = null;
        AutonomousMasteringBrain.Plan combined = null;
        for (AutonomousMasteringBrain.Plan plan : brain.internalPlans) {
            if (plan == null) continue;
            if ("sonic_finish".equals(plan.strategy)) sonic = plan;
            else if ("full_combined".equals(plan.strategy)) combined = plan;
        }
        if (sonic != null) return sonic;
        if (combined != null) return combined;
        return brain.preferred;
    }
}
