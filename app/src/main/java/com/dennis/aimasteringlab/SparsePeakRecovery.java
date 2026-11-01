package com.dennis.aimasteringlab;

/** Optional, measured peak-constrained loudness recovery; no global policy changes. */
final class SparsePeakRecovery {
    private SparsePeakRecovery() {}

    static boolean mayAudition(AnalysisResult source, BroadMasteringNeed.Result need,
                               int transientRejections, int qualifiedRungs) {
        return source != null && source.sourceGuard != null
                && !source.sourceGuard.secondPassGuard
                && need != null && need.required && need.reliability >= 0.45
                && need.eqDemandDb >= 0.35 && transientRejections >= 2
                && qualifiedRungs == 0
                && Double.isFinite(source.truePeakEstimateDbtp)
                && Double.isFinite(source.integratedLufs)
                && source.truePeakEstimateDbtp + (-14.0 - source.integratedLufs) > -1.10
                && source.truePeakEstimateDbtp > -1.10
                && source.integratedLufs >= -22.0
                && source.integratedLufs <= -12.0;
    }

    /**
     * v0.15.6: candidate-order fast preflight for unusually peak-constrained,
     * reliably measured stereo sources. It does not override any output gate;
     * failures fall back to the unchanged full competition.
     */
    static boolean earlyAuditionSafe(AnalysisResult source, BroadMasteringNeed.Result need,
                                     boolean hasStems, boolean hasBroadCoreAnchor) {
        if (source == null || source.sourceGuard == null || hasStems || hasBroadCoreAnchor
                || !source.sourceGuard.allowsBroadProcessing()
                || source.nextGenerationIntelligence == null
                || source.nextGenerationIntelligence.availableModules() > 0
                || need == null || !need.required
                || need.reliability < 0.80 || need.eqDemandDb < 1.0
                || !Double.isFinite(source.integratedLufs)
                || !Double.isFinite(source.truePeakEstimateDbtp)) return false;
        final double requiredGainDb = -14.0 - source.integratedLufs;
        return source.integratedLufs >= -18.0 && source.integratedLufs <= -14.6
                && source.truePeakEstimateDbtp >= -0.10
                && requiredGainDb >= 0.60
                && source.truePeakEstimateDbtp + requiredGainDb > 0.65;
    }

    static boolean sparseActivitySafe(MasterRenderer.Result r) {
        return r != null && r.limiterUsed
                && r.maximumLimiterReductionDb > 0.15
                && r.maximumLimiterReductionDb <= 3.50
                && r.averageLimiterReductionDb <= 0.10
                && r.limiterFramesAboveOneDbPercent <= 1.0
                && r.limiterFramesAboveThreeDbPercent <= 0.15;
    }

    static boolean stableOutput(AnalysisResult source, AnalysisResult baseline,
                                MasterRenderer.Result r) {
        if (source == null || baseline == null || r == null || r.verification == null
                || !sparseActivitySafe(r)) return false;
        AnalysisResult out = r.verification;
        return Double.isFinite(out.integratedLufs)
                && Math.abs(out.integratedLufs + 14.0) <= 0.08
                && Double.isFinite(out.truePeakEstimateDbtp)
                && out.truePeakEstimateDbtp <= -1.10
                && baseline.lraLu - out.lraLu <= 0.65
                && baseline.crestFactorDb - out.crestFactorDb <= 3.20
                && out.clippedSamples == 0 && out.nonFiniteSamples == 0
                && source.wav.frames == out.wav.frames;
    }

    static boolean promotionSafe(FullAiMasteringEngine.Candidate c,
                                 FullAiMasteringEngine.Candidate transparent,
                                 double originalObjective) {
        return c != null && c.sparsePeakAware && transparent != null
                && c.eligible && !c.promotionBlocked
                && c.passageCheck != null && c.passageCheck.passed()
                && stableOutput(transparent.render.master.verification,
                        transparent.render.master.verification, c.render.master)
                && c.masteringDelta != null && c.masteringDelta.broadMeaningful
                && c.technicalScore >= transparent.technicalScore - 0.75
                && c.measuredObjective >= originalObjective - 0.05;
    }
}
