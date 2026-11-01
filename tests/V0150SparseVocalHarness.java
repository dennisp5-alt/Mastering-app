package com.dennis.aimasteringlab;

/** Fail-open conditions for the optional professional-vocal sparse limiter gate. */
public final class V0150SparseVocalHarness {
    private static void check(boolean passed,String message) {
        if(!passed)throw new AssertionError(message);
    }
    private static AnalysisResult analysis(){
        AnalysisResult a=new AnalysisResult();
        a.wav=new WavFile.Info();
        a.wav.frames=48000*4L;
        a.integratedLufs=-14.0;
        a.truePeakEstimateDbtp=-1.15;
        return a;
    }
    public static void main(String[] args){
        AnalysisResult source=analysis();
        LimiterCrestOptimizer.Trial balanced=new LimiterCrestOptimizer.Trial();
        balanced.profile=MasterRenderer.LimiterProfile.BALANCED;
        balanced.eligible=true;
        balanced.master=new MasterRenderer.Result();
        balanced.master.verification=analysis();
        balanced.master.limiterUsed=true;
        balanced.master.maximumLimiterReductionDb=0.23;
        balanced.master.averageLimiterReductionDb=0.0007;
        balanced.master.maximumLimiterGainStepDb=0.01;
        balanced.crestLossDb=0.05;
        balanced.lraLossLu=0.04;
        check(LimiterCrestOptimizer.sparseVocalSufficient(balanced,source,-1.1),"sparse verified master eligible");
        balanced.master.maximumLimiterReductionDb=0.251;
        check(!LimiterCrestOptimizer.sparseVocalSufficient(balanced,source,-1.1),"significant peak limiting requires full audition");
        balanced.master.maximumLimiterReductionDb=0.23;
        balanced.master.averageLimiterReductionDb=0.002;
        check(!LimiterCrestOptimizer.sparseVocalSufficient(balanced,source,-1.1),"average reduction requires full audition");
        balanced.master.averageLimiterReductionDb=0.0007;
        balanced.lraLossLu=0.10;
        check(!LimiterCrestOptimizer.sparseVocalSufficient(balanced,source,-1.1),"dynamic damage requires full audition");
        balanced.lraLossLu=0.04;
        balanced.master.verification.truePeakEstimateDbtp=-1.095;
        check(!LimiterCrestOptimizer.sparseVocalSufficient(balanced,source,-1.1),"over ceiling requires full audition");
        balanced.master.verification.truePeakEstimateDbtp=-1.15;
        balanced.master.verification.clippedSamples=1;
        check(!LimiterCrestOptimizer.sparseVocalSufficient(balanced,source,-1.1),"clipping requires full audition");
        balanced.master.verification.clippedSamples=0;
        balanced.profile=MasterRenderer.LimiterProfile.CLEAN_LONG;
        check(!LimiterCrestOptimizer.sparseVocalSufficient(balanced,source,-1.1),"only BALANCED eligible");
        System.out.println("V0150_SPARSE_VOCAL_PASS bounded shortcut and full-audition fallback conditions");
    }
}
