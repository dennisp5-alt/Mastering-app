package com.dennis.aimasteringlab;

/** Host-only regression for the bounded, fail-closed v0.15.6 source preflight. */
final class V0156EarlySparseHarness {
    private static void check(boolean yes,String name){if(!yes)throw new AssertionError(name);}
    public static void main(String[] args) {
        AnalysisResult source=new AnalysisResult();
        source.sourceGuard=new SourceGuardModel.Result();
        source.nextGenerationIntelligence=new NextGenerationMasteringModel.Result();
        source.integratedLufs=-15.24;
        source.truePeakEstimateDbtp=0.13;
        BroadMasteringNeed.Result need=new BroadMasteringNeed.Result();
        need.required=true;need.reliability=1.0;need.eqDemandDb=1.378;
        check(SparsePeakRecovery.earlyAuditionSafe(source,need,false,false), "the verified peak-constrained 4:29-style source may audition early");
        check(!SparsePeakRecovery.earlyAuditionSafe(source,need,true,false), "stem-supplied source must not bypass competition");
        check(!SparsePeakRecovery.earlyAuditionSafe(source,need,false,true), "existing core anchor must prevent early attempt");
        source.sourceGuard.secondPassGuard=true;
        check(!SparsePeakRecovery.earlyAuditionSafe(source,need,false,false), "already-mastered source must use normal processing");
        source.sourceGuard.secondPassGuard=false;
        source.nextGenerationIntelligence=null;
        check(!SparsePeakRecovery.earlyAuditionSafe(source,need,false,false), "without a full technical screen use original competition");
        source.nextGenerationIntelligence=new NextGenerationMasteringModel.Result();
        source.sourceGuard.likelyLossyInWav=true;
        check(!SparsePeakRecovery.earlyAuditionSafe(source,need,false,false), "lossy sources must not short-circuit");
        source.sourceGuard.likelyLossyInWav=false;
        source.truePeakEstimateDbtp=-1.3;
        check(!SparsePeakRecovery.earlyAuditionSafe(source,need,false,false), "non-peak-constrained song must retain full competition");
        source.truePeakEstimateDbtp=0.13;source.integratedLufs=-13.9;
        check(!SparsePeakRecovery.earlyAuditionSafe(source,need,false,false), "already-at-target input must not enter fast lane");
        source.integratedLufs=-15.24;need.reliability=0.4;
        check(!SparsePeakRecovery.earlyAuditionSafe(source,need,false,false), "weak source evidence must fall back");
        System.out.println("v0.15.6 early sparse-peak preflight PASS: guarded source, stems, prior master, lossy, peak headroom, reliable demand");
    }
}
