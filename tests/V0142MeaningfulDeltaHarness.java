package com.dennis.aimasteringlab;

import java.io.File;

public final class V0142MeaningfulDeltaHarness {
    static void req(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static File synth(File dir,String name,boolean tilt)throws Exception{
        File f=new File(dir,name);long frames=48000L*12L;
        try(PcmExportWriter out=new PcmExportWriter(f,48000,32,frames)){
            for(long i=0;i<frames;i++){
                double t=i/48000.0;
                double env=Math.min(1.0,Math.min(t/.08,(12.0-t)/.10));if(env<0)env=0;
                double low=.16*Math.sin(2*Math.PI*110*t);
                double high=.07*Math.sin(2*Math.PI*3200*t);
                double side=.018*Math.sin(2*Math.PI*890*t);
                if(tilt){low*=.965;high*=1.045;}
                double m=env*(low+high);double s=env*side;
                out.frame(m+s,m-s);
            }
        }
        return f;
    }
    public static void main(String[] args)throws Exception{
        File dir=new File(args[0]);dir.mkdirs();
        File reference=synth(dir,"reference.wav",false);
        File broad=synth(dir,"broad.wav",true);
        MasteringDeltaEvidence.Result broadDelta=MasteringDeltaEvidence.compare(reference,broad);
        req(broadDelta.broadMeaningful,"spectral tilt should clear broad gate: "+broadDelta.summary());
        if(args.length>2){
            MasteringDeltaEvidence.Result real=MasteringDeltaEvidence.compare(new File(args[1]),new File(args[2]));
            req(!real.broadMeaningful,"The Old Girl near-copy regression unexpectedly cleared broad gate: "+real.summary());
            req(real.levelMatchedResidualRelativeDb < -46.0,"expected near-copy residual below -46 dB: "+real.summary());
            System.out.println("REAL_NEAR_COPY "+real.summary());
        }
        System.out.println("V0142_MEANINGFUL_DELTA_PASS broad="+broadDelta.summary());
    }
}
