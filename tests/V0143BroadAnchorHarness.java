package com.dennis.aimasteringlab;

import java.io.File;

public final class V0143BroadAnchorHarness {
    static void req(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static File synth(File dir,String name,double lowScale,double highScale)throws Exception{
        File f=new File(dir,name);long frames=48000L*8L;
        try(PcmExportWriter out=new PcmExportWriter(f,48000,32,frames)){
            for(long i=0;i<frames;i++){
                double t=i/48000.0;
                double e=Math.min(1.0,Math.min(t/.05,(8.0-t)/.08));if(e<0)e=0;
                double low=.15*lowScale*Math.sin(2*Math.PI*100*t);
                double high=.065*highScale*Math.sin(2*Math.PI*3400*t);
                double side=.014*Math.sin(2*Math.PI*900*t);
                double m=e*(low+high),s=e*side;out.frame(m+s,m-s);
            }
        }
        return f;
    }
    static FullAiMasteringEngine.Candidate candidate(String strategy,File f,double score,double tech,double lra,double crest){
        FullAiMasteringEngine.Candidate c=new FullAiMasteringEngine.Candidate();
        c.strategy=strategy;c.file=f;c.score=score;c.technicalScore=tech;c.eligible=true;c.rendered=true;
        c.plan=new AutonomousMasteringBrain.Plan();c.plan.strategy=strategy;
        c.render=new AutonomousMasteringRenderer.Result();
        c.render.master=new MasterRenderer.Result();
        c.render.master.verification=new AnalysisResult();
        c.render.master.verification.lraLu=lra;c.render.master.verification.crestFactorDb=crest;
        c.render.master.maximumLimiterReductionDb=.10;c.render.master.averageLimiterReductionDb=.001;
        return c;
    }
    public static void main(String[] args)throws Exception{
        File dir=new File(args[0]);dir.mkdirs();
        File ref=synth(dir,"transparent.wav",1.0,1.0);
        File near=synth(dir,"near.wav",.9998,1.0002);
        File broad=synth(dir,"broad.wav",.965,1.045);
        FullAiMasteringEngine.Candidate transparent=candidate("transparent_flat_gain",ref,100.0,100.0,7.0,13.0);
        FullAiMasteringEngine.Candidate nearC=candidate("conservative_corrective",near,100.1,100.05,7.0,13.0);
        FullAiMasteringEngine.Candidate broadC=candidate("sonic_finish",broad,99.96,99.94,6.8,12.8);
        FullAiMasteringEngine.Result result=new FullAiMasteringEngine.Result();
        result.transparent=transparent;result.rankedEligible.add(nearC);result.rankedEligible.add(transparent);result.rankedEligible.add(broadC);
        FullAiMasteringEngine.Candidate picked=FullAiMasteringEngine.bestBroadCoreAnchor(result,transparent);
        req(picked==broadC,"broad anchor should select meaningful broad candidate rather than higher-score near-copy");
        req(picked.masteringDelta!=null&&picked.masteringDelta.broadMeaningful,"broad anchor delta not recorded");
        System.out.println("V0143_BROAD_ANCHOR_PASS "+picked.strategy+" "+picked.masteringDelta.summary());
    }
}
