package com.dennis.aimasteringlab;
import java.io.*;
import java.util.*;

/** Musical false-positive vetoes, narrow moving EQ and real oversampled render. */
public final class V012AudioHarness {
    static final AudioAnalyzer.Progress QUIET=(s,p)->{};
    static void require(boolean b,String why){if(!b)throw new AssertionError(why);}
    static AnalysisResult measure(File f)throws Exception{return AudioAnalyzer.analyze(f.getName(),()->new FileInputStream(f),QUIET);}
    public static void main(String[] args)throws Exception{
        File work=new File(args[0]);work.mkdirs();
        for(int mode=0;mode<3;mode++){
            File f=fixture(work,mode);AnalysisResult a=measure(f);
            ProfessionalMasteringModel.Result m=new ProfessionalMasteringModel.Result();
            ProfessionalMasteringModel.Event e=new ProfessionalMasteringModel.Event();
            e.automatic=true;e.confidence=.99;e.startSeconds=6;e.endSeconds=6.5;e.primaryDb=-1.2;e.evidence="injected proposal";
            e.kind=mode==2?ProfessionalMasteringModel.Kind.VOCAL_PLOSIVE:ProfessionalMasteringModel.Kind.VOCAL_SIBILANCE;m.events.add(e);
            MusicalEventGuard.refine(()->new FileInputStream(f),a,m,QUIET);
            if(mode>0)require(!e.automatic&&!e.contextPassed,"Repeated cymbal/kick accepted: "+mode+" "+e.contextReason);
            else{
                require(e.contextPassed&&e.automatic,"Moving resonance rejected: "+e.contextReason);
                double min=20000,max=0;for(double hz:e.trackingFrequency){min=Math.min(min,hz);max=Math.max(max,hz);}
                require(max-min>300,"Frequency tracker stayed fixed: "+min+".."+max);
                TrackingDynamicEq eq=new TrackingDynamicEq(m,20,1);double before=0,after=0,protectedDelta=0;long frame=0;
                Biquad targetIn=Biquad.bandPass(48000,8100,1.3),targetOut=Biquad.bandPass(48000,8100,1.3);
                Biquad bodyIn=Biquad.bandPass(48000,1000,4),bodyOut=Biquad.bandPass(48000,1000,4);double bi=0,bo=0;
                try(WavFile.Reader r=WavFile.open(new FileInputStream(f))){float[][] x=new float[2][4096];int n;
                    while((n=r.readFrames(x,4096))>0)for(int i=0;i<n;i++,frame++){
                        double t=frame/48000.0,mid=(x[0][i]+x[1][i])*.5,out=eq.process(mid,t);
                        double u=targetIn.process(mid),v=targetOut.process(out),b=bodyIn.process(mid),c=bodyOut.process(out);
                        if(t>=6.1&&t<6.45){before+=u*u;after+=v*v;bi+=b*b;bo+=c*c;}
                        if(t<2||t>=12)protectedDelta=Math.max(protectedDelta,Math.abs(out-mid));
                    }}
                double reduction=10*Math.log10(before/after),bodyDelta=10*Math.log10(bo/bi);
                require(reduction>.15&&Math.abs(bodyDelta)<.1,"Narrow EQ failed target/body protection: "+reduction+" / "+bodyDelta);
                require(protectedDelta==0,"Tracking EQ changed protected boundaries");
                System.out.printf(Locale.US,"TRACKING_EQ_PASS range=%.0f..%.0f Hz reduction=%.4f dB bodyChange=%.5f dB protectedExact=true%n",min,max,reduction,bodyDelta);
            }
            System.out.println("MUSICAL_CONTEXT_PASS mode="+mode+" "+e.contextReason);
        }
        oversampled(work);
    }
    static File fixture(File work,int mode)throws Exception{
        File f=new File(work,"context-"+mode+".wav");int frames=48000*20;
        try(PcmExportWriter w=new PcmExportWriter(f,48000,32,frames)){
            for(int i=0;i<frames;i++){
                double t=i/48000.0,mid=.07*Math.sin(2*Math.PI*1000*t)+.055*Math.sin(2*Math.PI*210*t)+.002*Math.sin(2*Math.PI*8000*t);
                if(mode==0&&t>=6&&t<6.5){double d=t-6,env=Math.min(1,Math.min(d/.015,(.5-d)/.015));mid+=.16*env*Math.sin(2*Math.PI*(7000*d+2000*d*d));}
                if(mode>0&&t>=3&&t<16){double d=t-Math.floor(t),env=Math.exp(-d*55);mid+=env*(mode==1?.65*(Math.sin(2*Math.PI*8200*t)+.3*Math.sin(2*Math.PI*11000*t)):.8*Math.sin(2*Math.PI*75*t));}
                double s=.005*Math.sin(2*Math.PI*730*t);w.frame(mid+s,mid-s);
            }
        }return f;
    }
    static void oversampled(File work)throws Exception{
        for(int n:new int[]{1,31,2000}){
            ArrayList<Double> y=new ArrayList<>();OversampledLimiter limiter=new OversampledLimiter(48000,-1.2,6,110,(l,r)->{require(Math.abs(l+r)<1e-12,"Oversampled stereo linkage");y.add(l);});
            int at=n/2;for(int i=0;i<n;i++)limiter.add(i==at?.2:0,i==at?-.2:0);limiter.finish();
            require(y.size()==n,"Oversampled duration");int max=0;for(int i=0;i<n;i++)if(Math.abs(y.get(i))>Math.abs(y.get(max)))max=i;
            require(max==at&&Math.abs(y.get(max))>(n==1?.10:.15),"Oversampled alignment or boundary loss n="+n+" peak="+max+" value="+y.get(max));
        }
        File src=new File(work,"limiter-source.wav");int frames=48000*6;
        try(PcmExportWriter w=new PcmExportWriter(src,48000,32,frames)){
            for(int i=0;i<frames;i++){double t=i/48000.0,v=.15*Math.sin(2*Math.PI*511*t)+.075*Math.sin(2*Math.PI*19001*t);
                if(i%48000>=24000&&i%48000<24020)v+=.8*Math.sin(2*Math.PI*12000*t+Math.PI/4);w.frame(v,-v);}}
        AnalysisResult a=measure(src);MasterRenderer.Result r=MasterRenderer.renderWithCeiling(()->new FileInputStream(src),a,a.appliedSafeGainDb,new File(work,"oversampled-master.wav"),-1,true,MasterRenderer.LimiterProfile.OVERSAMPLED_CLEAN,QUIET);
        require(r.limiterOversampling==4&&r.limiterUsed&&r.verification.wav.frames==frames&&r.verification.truePeakEstimateDbtp<=-1&&r.verification.clippedSamples==0,"Final oversampled render verification");
        System.out.printf(Locale.US,"OVERSAMPLED_LIMITER_PASS frames=%d LUFS=%.6f TP=%.6f maxGR=%.4f%n",r.verification.wav.frames,r.verification.integratedLufs,r.verification.truePeakEstimateDbtp,r.maximumLimiterReductionDb);
    }
}
