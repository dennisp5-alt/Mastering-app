package com.dennis.aimasteringlab;

import java.io.File;

public final class V0143BroadRetentionHarness {
    static void req(boolean ok,String m){if(!ok)throw new AssertionError(m);}
    static File synth(File dir,String name,double lowScale,double highScale)throws Exception{
        File f=new File(dir,name);long frames=48000L*6L;
        try(PcmExportWriter out=new PcmExportWriter(f,48000,32,frames)){
            for(long i=0;i<frames;i++){
                double t=i/48000.0;double e=Math.min(1.0,Math.min(t/.04,(6.0-t)/.06));if(e<0)e=0;
                double low=.15*lowScale*Math.sin(2*Math.PI*105*t);
                double high=.06*highScale*Math.sin(2*Math.PI*3600*t);
                double m=e*(low+high), side=e*.012*Math.sin(2*Math.PI*880*t);
                out.frame(m+side,m-side);
            }
        }
        return f;
    }
    static FullAiMasteringEngine.Candidate c(String name,File f,double score,double tech){
        FullAiMasteringEngine.Candidate c=new FullAiMasteringEngine.Candidate();
        c.strategy=name;c.file=f;c.score=score;c.technicalScore=tech;c.eligible=true;c.rendered=true;
        c.plan=new AutonomousMasteringBrain.Plan();c.plan.strategy=name;
        c.render=new AutonomousMasteringRenderer.Result();c.render.master=new MasterRenderer.Result();
        c.render.master.verification=new AnalysisResult();
        c.render.master.verification.lraLu=7;c.render.master.verification.crestFactorDb=13;
        c.render.master.maximumLimiterReductionDb=.1;c.render.master.averageLimiterReductionDb=.001;
        return c;
    }
    public static void main(String[] args)throws Exception{
        File dir=new File(args[0]);dir.mkdirs();
        FullAiMasteringEngine.Candidate transparent=c("transparent_flat_gain",synth(dir,"transparent.wav",1,1),100,100);
        FullAiMasteringEngine.Candidate near=c("conservative_corrective",synth(dir,"near.wav",.9998,1.0002),100.15,100.05);
        FullAiMasteringEngine.Candidate broad=c("sonic_finish",synth(dir,"broad.wav",.965,1.045),99.96,99.94);
        FullAiMasteringEngine.Result r=new FullAiMasteringEngine.Result();r.transparent=transparent;
        r.broadMasteringNeed=new BroadMasteringNeed.Result();r.broadMasteringNeed.required=true;
        r.candidates.add(transparent);r.candidates.add(near);r.candidates.add(broad);
        FullAiMasteringEngine.retainCoreCompetitionAudio(r);
        req(transparent.file.isFile(),"transparent was discarded");
        req(near.file.isFile(),"raw-score family finalist should remain");
        req(broad.file.isFile(),"broad anchor was incorrectly discarded before final selection");
        req(r.broadMasteringAnchor==broad,"broad anchor was not retained in result");
        req(r.broadMasteringAnchorDelta!=null&&r.broadMasteringAnchorDelta.broadMeaningful,"retained broad anchor lacks broad delta evidence");
        System.out.println("V0143_BROAD_RETENTION_PASS "+r.broadMasteringAnchor.strategy+" "+r.broadMasteringAnchorDelta.summary());
    }
}
