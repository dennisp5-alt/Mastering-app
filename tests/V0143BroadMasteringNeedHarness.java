package com.dennis.aimasteringlab;

public final class V0143BroadMasteringNeedHarness {
    static void req(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static AnalysisResult source(boolean priorMaster){
        AnalysisResult a=new AnalysisResult();
        a.sourceGuard=new SourceGuardModel.Result();
        a.sourceGuard.secondPassGuard=priorMaster;
        a.sourceGuard.likelyLossyInWav=false;
        return a;
    }
    static AutonomousMasteringBrain.Result brain(double eq,boolean compressor,double reliability){
        AutonomousMasteringBrain.Result r=new AutonomousMasteringBrain.Result();
        r.features=new AutonomousMasteringBrain.Features();
        r.features.reliability=reliability;
        AutonomousMasteringBrain.Plan p=new AutonomousMasteringBrain.Plan();
        p.strategy="sonic_finish";
        p.lowShelfDb=eq;
        p.presenceDb=-eq*.7;
        p.compressorEnabled=compressor;
        p.compressorMaximumReductionDb=compressor?1.0:0.0;
        p.objectiveBefore=95.0;
        p.predictedObjectiveAfter=97.0;
        r.preferred=p;
        r.internalPlans.add(p);
        return r;
    }
    public static void main(String[] args){
        BroadMasteringNeed.Result raw=BroadMasteringNeed.assess(source(false),brain(.35,true,.80));
        req(raw.required,"credible raw broad demand should require broad mastering: "+raw.summary());
        BroadMasteringNeed.Result clean=BroadMasteringNeed.assess(source(false),brain(.02,false,.80));
        req(!clean.required,"tiny whole-song plan should remain transparent-capable: "+clean.summary());
        BroadMasteringNeed.Result prior=BroadMasteringNeed.assess(source(true),brain(.55,true,.90));
        req(!prior.required,"previous-master guard must block broad remastering: "+prior.summary());
        BroadMasteringNeed.Result weak=BroadMasteringNeed.assess(source(false),brain(.40,true,.30));
        req(!weak.required,"low-reliability analysis must not force broad mastering: "+weak.summary());
        System.out.println("V0143_BROAD_MASTERING_NEED_PASS "+raw.summary());
    }
}
