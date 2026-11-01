package com.dennis.aimasteringlab;

import java.io.File;

public final class V0145AdaptiveAnchorHarness {
    static void req(boolean ok,String m){if(!ok)throw new AssertionError(m);}
    static File synth(File dir,String name,double lowScale,double highScale)throws Exception{
        File f=new File(dir,name);long frames=48000L*6L;
        try(PcmExportWriter out=new PcmExportWriter(f,48000,32,frames)){
            for(long i=0;i<frames;i++){
                double t=i/48000.0;double e=Math.min(1.0,Math.min(t/.04,(6.0-t)/.06));if(e<0)e=0;
                double low=.15*lowScale*Math.sin(2*Math.PI*105*t);
                double high=.06*highScale*Math.sin(2*Math.PI*3600*t);
                double m=e*(low+high),side=e*.012*Math.sin(2*Math.PI*880*t);
                out.frame(m+side,m-side);
            }
        }
        return f;
    }
    static FullAiMasteringEngine.Candidate c(String name,File f,double tech,double lra,double crest,double comp){
        FullAiMasteringEngine.Candidate c=new FullAiMasteringEngine.Candidate();
        c.strategy=name;c.file=f;c.score=tech;c.technicalScore=tech;c.eligible=true;c.rendered=true;c.measuredObjective=99.0;
        c.plan=new AutonomousMasteringBrain.Plan();c.plan.strategy=name;
        c.render=new AutonomousMasteringRenderer.Result();c.render.master=new MasterRenderer.Result();
        c.render.master.verification=new AnalysisResult();
        c.render.master.verification.lraLu=lra;c.render.master.verification.crestFactorDb=crest;
        c.render.master.maximumLimiterReductionDb=.24;c.render.maximumCompressionReductionDb=comp;
        return c;
    }
    public static void main(String[] args)throws Exception{
        File dir=new File(args[0]);dir.mkdirs();
        FullAiMasteringEngine.Candidate transparent=c("transparent_flat_gain",synth(dir,"transparent.wav",1,1),10.0,6.97,14.30,0);
        transparent.render.master.maximumLimiterReductionDb=.02;
        FullAiMasteringEngine.Candidate adaptive=c("adaptive_broad_balanced",synth(dir,"adaptive.wav",.965,1.045),9.80,6.22,13.42,.52);
        FullAiMasteringEngine.Result r=new FullAiMasteringEngine.Result();
        r.transparent=transparent;r.sourceObjective=99.0;
        r.broadMasteringNeed=new BroadMasteringNeed.Result();r.broadMasteringNeed.required=true;
        r.broadMasteringNeed.eqDemandDb=.21;r.broadMasteringNeed.dynamicsDemand=1.05;r.broadMasteringNeed.stereoDemand=0;
        // v0.14.6 correctly requires proof this is the verified unresolved
        // adaptive fallback rather than a generic candidate bypassing score gates.
        r.adaptiveBroadAnchor=adaptive;
        adaptive.passageCheck=new PassagePreservation.Result();
        r.rankedEligible.add(transparent);r.rankedEligible.add(adaptive);
        FullAiMasteringEngine.Candidate picked=FullAiMasteringEngine.bestBroadCoreAnchor(r,transparent);
        req(picked==adaptive,"demand-aware adaptive candidate should survive final broad-anchor selection");
        req(picked.masteringDelta!=null&&picked.masteringDelta.broadMeaningful,"adaptive anchor delta missing");
        System.out.println("V0145_ADAPTIVE_ANCHOR_PASS "+picked.masteringDelta.summary());
    }
}
