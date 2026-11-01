package com.dennis.aimasteringlab;

public final class V0145DynamicsPreservationHarness {
    static void req(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    static FullAiMasteringEngine.Candidate candidate(double lra, double crest,
            double limiter, double compression, double technical,
            double objective) {
        FullAiMasteringEngine.Candidate c = new FullAiMasteringEngine.Candidate();
        c.strategy = "adaptive_broad_balanced";
        c.eligible = true;
        c.technicalScore = technical;
        c.measuredObjective = objective;
        c.render = new AutonomousMasteringRenderer.Result();
        c.render.master = new MasterRenderer.Result();
        c.render.master.verification = new AnalysisResult();
        c.render.master.verification.lraLu = lra;
        c.render.master.verification.crestFactorDb = crest;
        c.render.master.maximumLimiterReductionDb = limiter;
        c.render.maximumCompressionReductionDb = compression;
        return c;
    }

    public static void main(String[] args) {
        BroadMasteringNeed.Result need = new BroadMasteringNeed.Result();
        need.required = true;
        need.eqDemandDb = 0.21;
        need.dynamicsDemand = 1.05;
        need.stereoDemand = 0.0;
        AdaptiveDynamicsPreservation.Envelope e =
                AdaptiveDynamicsPreservation.envelope(need);
        req(e.active, "dynamics-led envelope should activate");
        req(e.allowedLraLossLu > 0.60 && e.allowedLraLossLu <= 1.10,
                "LRA envelope out of bounds");
        req(e.allowedCrestLossDb > 0.85 && e.allowedCrestLossDb <= 1.15,
                "crest envelope out of bounds");
        req(e.allowedExtraLimiterReductionDb > 0.25
                        && e.allowedExtraLimiterReductionDb <= 0.37,
                "limiter envelope out of bounds");

        FullAiMasteringEngine.Candidate reference = candidate(
                6.97, 14.30, 0.05, 0.0, 10.00, 99.0);
        FullAiMasteringEngine.Candidate safe = candidate(
                6.22, 13.42, 0.29, 0.52, 9.80, 99.0);
        AdaptiveDynamicsPreservation.Result safeResult =
                AdaptiveDynamicsPreservation.assess(safe, reference, need, 99.0);
        req(safeResult.passed, "bounded dynamics-led move should pass: "
                + safeResult.decision);
        req(safeResult.demandAdjustedTechnicalScore > safe.technicalScore,
                "intended LRA penalty recovery was not applied");

        FullAiMasteringEngine.Candidate excessive = candidate(
                5.70, 12.95, 0.60, 1.30, 9.90, 99.0);
        AdaptiveDynamicsPreservation.Result excessiveResult =
                AdaptiveDynamicsPreservation.assess(excessive, reference, need, 99.0);
        req(!excessiveResult.passed,
                "excessive dynamics move must remain rejected");

        BroadMasteringNeed.Result tonal = new BroadMasteringNeed.Result();
        tonal.required = true;
        tonal.eqDemandDb = 0.90;
        tonal.dynamicsDemand = 0.60;
        req(!AdaptiveDynamicsPreservation.envelope(tonal).active,
                "tonal-led tracks must retain standard gates");
        System.out.println("V0145_DYNAMICS_PRESERVATION_PASS " + safeResult.decision);
    }
}
