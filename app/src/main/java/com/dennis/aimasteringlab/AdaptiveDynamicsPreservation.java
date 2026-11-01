package com.dennis.aimasteringlab;

import java.util.Locale;

/**
 * v0.14.5 demand-aware preservation envelope used only by adaptive broad
 * candidates after normal broad mastering has failed and the measured source
 * need is clearly dynamics-led. It does not weaken hard safety, stereo/phase,
 * continuity, clipping or true-peak checks.
 */
final class AdaptiveDynamicsPreservation {
    static final class Envelope {
        boolean active;
        double strength;
        double allowedLraLossLu;
        double allowedCrestLossDb;
        double allowedExtraLimiterReductionDb;
        double passageTransientFloorDb;

        String summary() {
            if (!active) return "standard preservation envelope";
            return String.format(Locale.US,
                    "dynamics-aware envelope strength %.3f • LRA loss <= %.3f LU • crest loss <= %.3f dB • extra limiter <= %.3f dB • one-second transient floor %.3f dB",
                    strength, allowedLraLossLu, allowedCrestLossDb,
                    allowedExtraLimiterReductionDb, passageTransientFloorDb);
        }
    }

    static final class Result {
        boolean passed;
        boolean envelopePassed;
        boolean dynamicBenefit;
        boolean lraSafe;
        boolean crestSafe;
        boolean limiterSafe;
        boolean objectiveSafe;
        boolean compressionActive;
        double lraLossLu;
        double crestLossDb;
        double extraLimiterReductionDb;
        double compressionReductionDb;
        double objectiveDelta;
        double demandAdjustedTechnicalScore;
        String decision;
    }

    private AdaptiveDynamicsPreservation() {}

    static Envelope envelope(BroadMasteringNeed.Result need) {
        Envelope e = new Envelope();
        if (need == null || !need.required) return e;
        boolean dynamicsLed = need.dynamicsDemand >= 0.85
                && need.dynamicsDemand >= need.eqDemandDb * 1.35
                && need.stereoDemand < 0.20;
        if (!dynamicsLed) return e;
        e.active = true;
        e.strength = clamp((need.dynamicsDemand - 0.80) / 0.60, 0.0, 1.0);
        // These remain well inside the engine's absolute 1.80 LU hard gate.
        e.allowedLraLossLu = 0.60 + 0.50 * e.strength;
        e.allowedCrestLossDb = 0.85 + 0.30 * e.strength;
        e.allowedExtraLimiterReductionDb = 0.25 + 0.12 * e.strength;
        e.passageTransientFloorDb = -(0.70 + 0.30 * e.strength);
        return e;
    }

    static Result assess(FullAiMasteringEngine.Candidate candidate,
                         FullAiMasteringEngine.Candidate reference,
                         BroadMasteringNeed.Result need,
                         double sourceObjective) {
        Result r = new Result();
        Envelope e = envelope(need);
        if (!e.active || candidate == null || reference == null
                || candidate.render == null || reference.render == null) {
            r.decision = "Demand-aware dynamics preservation not active";
            return r;
        }
        AnalysisResult out = candidate.render.master.verification;
        AnalysisResult baseline = reference.render.master.verification;
        r.lraLossLu = Math.max(0.0, baseline.lraLu - out.lraLu);
        r.crestLossDb = Math.max(0.0,
                baseline.crestFactorDb - out.crestFactorDb);
        r.extraLimiterReductionDb = Math.max(0.0,
                candidate.render.master.maximumLimiterReductionDb
                        - reference.render.master.maximumLimiterReductionDb);
        r.compressionReductionDb = Math.max(0.0,
                candidate.render.maximumCompressionReductionDb);
        r.objectiveDelta = candidate.measuredObjective - sourceObjective;
        r.lraSafe = r.lraLossLu <= e.allowedLraLossLu + 1e-9;
        r.crestSafe = r.crestLossDb <= e.allowedCrestLossDb + 1e-9;
        r.limiterSafe = r.extraLimiterReductionDb
                <= e.allowedExtraLimiterReductionDb + 1e-9;
        // Tonal objective does not reward dynamics directly; it may stay flat,
        // but it still may not materially regress.
        r.objectiveSafe = r.objectiveDelta >= -0.030;
        r.compressionActive = r.compressionReductionDb >= 0.10;
        r.dynamicBenefit = r.compressionActive
                && (r.crestLossDb >= 0.060 || r.lraLossLu >= 0.080);

        // The generic technical score contains an LRA-loss penalty intended to
        // protect normal candidates. Recover most of that specific penalty here
        // so a measured dynamics need is not rejected twice for doing the job.
        double lraPenalty = 0.80 * Math.max(0.0, r.lraLossLu - 0.45);
        double recoveryFraction = 0.70 + 0.20 * e.strength;
        r.demandAdjustedTechnicalScore = candidate.technicalScore
                + lraPenalty * recoveryFraction;

        r.envelopePassed = r.lraSafe && r.crestSafe && r.limiterSafe
                && r.dynamicBenefit;
        // Keep the v0.14.5 combined diagnostic for legacy callers.
        // The v0.14.6 acceptance gate assesses objective and technical deltas
        // independently; a tonal objective penalty is not a physical damage signal.
        r.passed = r.envelopePassed && r.objectiveSafe;
        r.decision = String.format(Locale.US,
                "%s • LRA loss %.3f/%.3f LU %s • crest loss %.3f/%.3f dB %s • extra limiter %.3f/%.3f dB %s • compression %.3f dB • legacy objective diagnostic %+.4f %s (not an adaptive-fallback veto; see v0.14.6 final gate) • dynamic benefit %s • demand-adjusted technical %.4f",
                e.summary(), r.lraLossLu, e.allowedLraLossLu,
                r.lraSafe ? "PASS" : "FAIL", r.crestLossDb,
                e.allowedCrestLossDb, r.crestSafe ? "PASS" : "FAIL",
                r.extraLimiterReductionDb, e.allowedExtraLimiterReductionDb,
                r.limiterSafe ? "PASS" : "FAIL", r.compressionReductionDb,
                r.objectiveDelta, r.objectiveSafe ? "WITHIN_LEGACY_THRESHOLD" : "BELOW_LEGACY_THRESHOLD",
                r.dynamicBenefit ? "PROVED" : "NOT_PROVED",
                r.demandAdjustedTechnicalScore);
        return r;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
