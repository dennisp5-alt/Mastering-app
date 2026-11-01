package com.dennis.aimasteringlab;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * v0.14.5 fallback ladder for sources that clearly need whole-song mastering
 * but whose normal core candidates cannot simultaneously clear broad-delta and
 * preservation gates. The ladder is intentionally small and source-shaped:
 * dynamics-led material receives progressively stronger, transient-safe bus
 * control with restrained EQ; tonal-led material receives progressively
 * stronger broad EQ with conservative dynamics; mixed material scales both.
 */
final class AdaptiveBroadMastering {
    private AdaptiveBroadMastering() {}

    static List<AutonomousMasteringBrain.Plan> ladder(
            BroadMasteringNeed.Result need,
            AutonomousMasteringBrain.Result brain) {
        ArrayList<AutonomousMasteringBrain.Plan> out = new ArrayList<>();
        if (need == null || !need.required || brain == null) return out;
        AutonomousMasteringBrain.Plan base = preferredBroadPlan(brain);
        if (base == null || !base.hasAudioChanges()) return out;

        boolean dynamicsLed = need.dynamicsDemand >= 0.50
                && need.dynamicsDemand >= need.eqDemandDb * 1.35;
        boolean tonalLed = need.eqDemandDb >= 0.35
                && need.eqDemandDb >= need.dynamicsDemand * 1.25;

        // The ladder is not three generic "more processing" presets. Each rung
        // changes the dominant measured need while keeping secondary processing
        // bounded so a difficult raw source gets another safe route to a broad
        // master rather than simply relaxing the preservation gates.
        if (dynamicsLed) {
            out.add(adapt(base, "adaptive_broad_gentle", 0.40, 0.86,
                    0.00, 10.0, 30.0, 0.55, need, "dynamics-led gentle"));
            out.add(adapt(base, "adaptive_broad_balanced", 0.55, 1.08,
                    -0.55, 7.0, 18.0, 0.70, need, "dynamics-led balanced"));
            out.add(adapt(base, "adaptive_broad_firm", 0.70, 1.28,
                    -1.00, 5.0, 8.0, 0.85, need, "dynamics-led firm"));
        } else if (tonalLed) {
            out.add(adapt(base, "adaptive_broad_gentle", 0.78, 0.68,
                    0.20, 10.0, 30.0, 0.55, need, "tonal-led gentle"));
            out.add(adapt(base, "adaptive_broad_balanced", 0.98, 0.82,
                    0.00, 8.0, 20.0, 0.70, need, "tonal-led balanced"));
            out.add(adapt(base, "adaptive_broad_firm", 1.16, 0.96,
                    -0.25, 6.0, 12.0, 0.85, need, "tonal-led firm"));
        } else {
            out.add(adapt(base, "adaptive_broad_gentle", 0.70, 0.78,
                    0.10, 10.0, 28.0, 0.55, need, "mixed-demand gentle"));
            out.add(adapt(base, "adaptive_broad_balanced", 0.92, 0.98,
                    -0.30, 8.0, 18.0, 0.70, need, "mixed-demand balanced"));
            out.add(adapt(base, "adaptive_broad_firm", 1.08, 1.16,
                    -0.65, 6.0, 10.0, 0.85, need, "mixed-demand firm"));
        }
        return out;
    }

    /**
     * Extra tonal-first audition only after the ordinary three rungs demonstrably
     * fail the unchanged one-second transient gate. This does not bypass any
     * limiter, passage, true-peak, dynamic or objective gate. When tonal evidence
     * dominates, global EQ may have a safe path even though bus compression did not.
     */
    static boolean transientSafeTonalEligible(BroadMasteringNeed.Result need,
                                              int ordinaryRungs,
                                              int transientCrestRejections,
                                              int acceptedRungs) {
        return need != null && need.required && need.reliability >= 0.45
                && need.eqDemandDb >= 0.60
                && need.eqDemandDb >= need.dynamicsDemand * 1.25
                && ordinaryRungs == 3 && acceptedRungs == 0
                && transientCrestRejections >= 2;
    }

    static AutonomousMasteringBrain.Plan transientSafeTonalPlan(
            BroadMasteringNeed.Result need, AutonomousMasteringBrain.Result brain) {
        if (need == null || brain == null || !need.required
                || need.eqDemandDb < 0.60
                || need.eqDemandDb < need.dynamicsDemand * 1.25) return null;
        AutonomousMasteringBrain.Plan base = preferredBroadPlan(brain);
        if (base == null) return null;
        AutonomousMasteringBrain.Plan p = adapt(base,
                "adaptive_broad_tonal_transient_safe", 0.82, 0.0,
                0.0, 0.0, 0.0, 0.0, need,
                "tonal-first transient-safe extra audition");
        // Not a relaxed transient limit. No bus envelope or event shaping at all.
        p.compressorEnabled = false;
        p.compressorRatio = 1.0;
        p.compressorMaximumReductionDb = 0.0;
        p.eventScale = 0.0;
        p.sideScale = 1.0;
        p.decisions.add("v0.15.4 measured crest-loss recovery: compression and event shaping bypassed; tone-only candidate must still clear every original independent gate");
        return p.hasAudioChanges() ? p : null;
    }

    /** Tonal correction with no bus compression; a separately measured sparse
     * stereo-linked limiter handles only the target-constrained peaks. */
    static AutonomousMasteringBrain.Plan sparsePeakPlan(
            BroadMasteringNeed.Result need, AutonomousMasteringBrain.Result brain) {
        if (need == null || !need.required || brain == null || need.eqDemandDb < 0.35) return null;
        AutonomousMasteringBrain.Plan base = preferredBroadPlan(brain);
        if (base == null) return null;
        AutonomousMasteringBrain.Plan p = adapt(base, "adaptive_broad_sparse_peak_recovery",
                0.60, 0.0, 0.0, 0.0, 0.0, 0.0, need,
                "sparse peak and tonal recovery");
        p.compressorEnabled = false;
        p.compressorRatio = 1.0;
        p.compressorMaximumReductionDb = 0.0;
        p.eventScale = 0.0;
        p.sideScale = 1.0;
        p.decisions.add("v0.15.5 guarded stereo-linked sparse limiter: mandatory rendered peak/loudness, limiter activity and isolated passage checks");
        return p;
    }

    private static AutonomousMasteringBrain.Plan adapt(
            AutonomousMasteringBrain.Plan base,
            String strategy,
            double eqScale,
            double dynamicsScale,
            double thresholdOffsetDb,
            double attackExtraMs,
            double releaseExtraMs,
            double secondaryScale,
            BroadMasteringNeed.Result need,
            String label) {
        AutonomousMasteringBrain.Plan p = base.copy(strategy);
        p.lowShelfDb = clamp(base.lowShelfDb * eqScale, -2.20, 2.20);
        p.lowMidDb = clamp(base.lowMidDb * eqScale, -2.20, 2.20);
        p.presenceDb = clamp(base.presenceDb * eqScale, -2.10, 2.10);
        p.highShelfDb = clamp(base.highShelfDb * eqScale, -1.90, 1.90);
        p.sideScale = 1.0 + (base.sideScale - 1.0) * secondaryScale;
        p.eventScale = Math.min(1.15, Math.max(0.0, base.eventScale * secondaryScale));

        if (need.dynamicsDemand >= 0.50 && base.compressorEnabled) {
            p.compressorEnabled = true;
            p.compressorRatio = clamp(1.0 + (base.compressorRatio - 1.0) * dynamicsScale,
                    1.03, 1.58);
            p.compressorMaximumReductionDb = clamp(
                    base.compressorMaximumReductionDb * dynamicsScale, 0.25, 2.20);
            p.compressorThresholdDbfs = clamp(base.compressorThresholdDbfs
                    + thresholdOffsetDb, -30.0, -4.0);
            // Slower attack is deliberate: the fallback may increase broad
            // control, but it must not buy that control by shaving transients.
            p.compressorAttackMs = clamp(base.compressorAttackMs + attackExtraMs,
                    18.0, 70.0);
            p.compressorReleaseMs = clamp(base.compressorReleaseMs + releaseExtraMs,
                    120.0, 360.0);
        } else if (base.compressorEnabled) {
            p.compressorRatio = clamp(1.0 + (base.compressorRatio - 1.0)
                    * Math.min(1.0, dynamicsScale), 1.02, 1.45);
            p.compressorMaximumReductionDb = clamp(
                    base.compressorMaximumReductionDb * Math.min(1.0, dynamicsScale),
                    0.15, 1.60);
            p.compressorAttackMs = clamp(base.compressorAttackMs + attackExtraMs,
                    18.0, 70.0);
            p.compressorReleaseMs = clamp(base.compressorReleaseMs + releaseExtraMs,
                    120.0, 360.0);
        }
        p.decisions.add(String.format(Locale.US,
                "v0.14.5 Adaptive Broad Mastering: %s rung; measured demand EQ %.3f dB / dynamics %.3f / stereo %.3f. Normal preservation gates remain unchanged; unresolved dynamics-led fallback may use the bounded v0.14.5 demand-aware envelope.",
                label, need.eqDemandDb, need.dynamicsDemand, need.stereoDemand));
        return p;
    }

    private static AutonomousMasteringBrain.Plan preferredBroadPlan(
            AutonomousMasteringBrain.Result brain) {
        AutonomousMasteringBrain.Plan sonic = null;
        AutonomousMasteringBrain.Plan combined = null;
        for (AutonomousMasteringBrain.Plan p : brain.internalPlans) {
            if (p == null) continue;
            if ("sonic_finish".equals(p.strategy)) sonic = p;
            else if ("full_combined".equals(p.strategy)) combined = p;
        }
        if (sonic != null) return sonic;
        if (combined != null) return combined;
        return brain.preferred;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
