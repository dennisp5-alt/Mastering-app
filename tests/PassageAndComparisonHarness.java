package com.dennis.aimasteringlab;

import java.io.*;
import java.util.Locale;

/** Local regressions deliberately small in whole-track metrics, plus the actual playback mixer. */
public final class PassageAndComparisonHarness {
    static final AudioAnalyzer.Progress QUIET=(s,p)->{};
    static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static AnalysisResult measure(File f)throws Exception{return AudioAnalyzer.analyze(f.getName(),()->new FileInputStream(f),QUIET);}
    static PassagePreservation.Result compare(File a,File b)throws Exception{
        return PassagePreservation.compare(PassagePreservation.scan(a,measure(a)),PassagePreservation.scan(b,measure(b)));
    }
    public static void main(String[] args)throws Exception{
        File work=new File(args[0]);work.mkdirs();
        File clean=fixture(work,"clean",0),gain=fixture(work,"flat-gain",1);
        require(compare(clean,clean).passed(),"Unchanged master rejected");
        require(compare(clean,gain).passed(),"Flat level change mistaken for local damage");
        for(int kind=2;kind<=5;kind++){
            File changed=fixture(work,"damage-"+kind,kind);
            PassagePreservation.Result r=compare(clean,changed);
            String expected=kind==2?"CENTRE_PRESENCE_LOSS":kind==3?"STEREO_WIDTH_LOSS":kind==4?"TRANSIENT_CREST_LOSS":"TAIL_LEVEL_LOSS";
            require(!r.passed()&&r.findings.stream().anyMatch(f->f.kind.equals(expected)),"Missed local change "+expected+" "+r.summary());
            for(PassagePreservation.Finding f:r.findings)require(f.start>=0&&f.end<=20,"Invalid timestamp");
            System.out.println("LOCAL_DAMAGE_CAUGHT "+expected+" "+r.summary());
        }
        AnalysisResult a=measure(clean),b=measure(gain);String hash=Hashing.sha256(clean);
        double maximumError=0;
        try(ComparisonAudioStream stream=new ComparisonAudioStream(clean,a,gain,b);
            WavFile.Reader reference=WavFile.open(new FileInputStream(clean))){
            require(stream.firstGainDb==0&&stream.secondGainDb< -2.99,"Comparison matching must attenuate only the louder version");
            float[] stereo=new float[2048];float[][] expected=new float[2][1024];int n,blocks=0;
            while((n=stream.read(stereo,1024))>0){
                require(reference.readFrames(expected,1024)==n,"Playback length changed");
                if(++blocks==30)stream.choose(true);
                for(int i=0;i<n;i++)for(int c=0;c<2;c++)maximumError=Math.max(maximumError,Math.abs(stereo[i*2+c]-expected[c][i]));
            }
            require(stream.position()==a.wav.frames&&maximumError<1e-6,"A/B switch moved time or was not loudness matched: "+maximumError);
            stream.seek(48000*7+123);stream.mono(true);stream.read(stereo,1024);
            try(WavFile.Reader r=WavFile.open(new FileInputStream(clean))){
                r.skipFrames(48000*7+123);require(r.readFrames(expected,1024)==1024,"Reference seek");
                for(int i=240;i<1024;i++){
                    double mono=(expected[0][i]+expected[1][i])*.5;
                    require(Math.abs(stereo[i*2]-mono)<1e-6&&Math.abs(stereo[i*2+1]-mono)<1e-6,"Mono or seek alignment error");
                }
            }
            stream.seek(a.wav.frames-7);require(stream.read(stereo,1024)==7&&stream.read(stereo,1024)==-1,"Tail frames lost");
            try{stream.seek(a.wav.frames+1);throw new AssertionError("Out-of-range seek accepted");}catch(IOException expectedError){}
        }
        require(hash.equals(Hashing.sha256(clean)),"Playback altered source audio");
        for(boolean blind:new boolean[]{false,true})for(boolean swapped:new boolean[]{false,true})
            require(ComparisonAudioStream.alternativeForSlot(0,blind,swapped)!=ComparisonAudioStream.alternativeForSlot(1,blind,swapped),"Blind slots target same version");
        AnalysisResult wrong=measure(gain);wrong.wav.frames--;
        try{new ComparisonAudioStream(clean,a,gain,wrong);throw new AssertionError("Misaligned pair admitted");}catch(IOException expected){}
        Thread.currentThread().interrupt();
        try{PassagePreservation.scan(clean,a);throw new AssertionError("Passage cancellation ignored");}catch(IOException expected){}finally{Thread.interrupted();}
        System.out.printf(Locale.US,"ALIGNED_AB_PASS maxSampleError=%.9f gainA=0 gainB=%.5f stereo/mono/seek/tail/blind/no-write%n",maximumError,a.integratedLufs-b.integratedLufs);
        System.out.println("PASSAGE_PRESERVATION_PASS gain-invariant/local-centre/width/transient/tail/cancellation");
        challenger(work);
    }
    static File fixture(File work,String name,int mode)throws Exception{
        File f=new File(work,name+".wav");int rate=48000,frames=rate*20;
        try(OutputStream out=new BufferedOutputStream(new FileOutputStream(f),262144)){
            MasterRenderer.writeHeader(out,rate,2,frames*6L,24);
            for(int i=0;i<frames;i++){
                double t=i/(double)rate,edge=Math.min(1,Math.min(t/.1,(frames-1-i)/(rate*.1)));
                double tail=t<15?1:Math.pow(10,-4*(t-15)/20);
                double vocal=.055*Math.sin(2*Math.PI*1200*t)+.035*Math.sin(2*Math.PI*2300*t);
                double bass=.075*Math.sin(2*Math.PI*90*t),side=.028*Math.sin(2*Math.PI*630*t);
                double spike=.48*Math.exp(-Math.pow((t-10.4)/.0003,2));
                if(mode==2&&t>=5&&t<8)vocal*=.50;
                if(mode==3&&t>=5&&t<8)side*=.50;
                if(mode==4)spike*=.20;
                if(mode==5&&t>=17)tail*=.40;
                double g=edge*tail*(mode==1?Math.pow(10,3.0/20):1);
                double left=(vocal+bass+side+spike)*g,right=(vocal+bass-side+spike)*g;
                for(double v:new double[]{left,right}){int q=(int)Math.round(v*8388608);out.write(q);out.write(q>>8);out.write(q>>16);}
            }
        }return f;
    }
    static void challenger(File work)throws Exception{
        AutonomousMasteringBrain.Plan p=new AutonomousMasteringBrain.Plan();
        p.lowShelfDb=-.8;p.presenceDb=.2;p.sideScale=.99;p.eventScale=.4;
        p.compressorEnabled=true;p.compressorRatio=1.2;p.compressorMaximumReductionDb=.6;
        AutonomousMasteringBrain.Plan softer=FullAiMasteringEngine.gentlerPlan(p,"test");
        require(p.lowShelfDb==-.8&&Math.abs(softer.lowShelfDb+.6)<1e-12&&Math.abs(softer.compressorRatio-1.15)<1e-12,"Challenger changed parent or did not soften nonlinear parameters");
        FullAiMasteringEngine.Candidate parent=new FullAiMasteringEngine.Candidate(),c=new FullAiMasteringEngine.Candidate();
        parent.render=new AutonomousMasteringRenderer.Result();c.render=new AutonomousMasteringRenderer.Result();
        parent.render.master=new MasterRenderer.Result();c.render.master=new MasterRenderer.Result();
        parent.render.master.verification=new AnalysisResult();c.render.master.verification=new AnalysisResult();
        parent.render.master.verification.integratedLufs=c.render.master.verification.integratedLufs=-14;
        parent.measuredObjective=c.measuredObjective=90;parent.technicalScore=c.technicalScore=89.9;
        parent.processingCost=.1;c.processingCost=.075;c.eligible=true;c.passageCheck=new PassagePreservation.Result();
        require(FullAiMasteringEngine.gentlerEquivalent(parent,c),"Equivalent lower-cost challenger rejected");
        parent.render.master.verification.lraLu=.3;require(!FullAiMasteringEngine.gentlerEquivalent(parent,c),"Dynamics loss promoted");
        parent.render.master.verification.lraLu=0;
        c.measuredObjective=89.8;require(!FullAiMasteringEngine.gentlerEquivalent(parent,c),"Lost benefit promoted");
        c.measuredObjective=Double.NaN;require(!FullAiMasteringEngine.gentlerEquivalent(parent,c),"Unknown benefit promoted");
        c.measuredObjective=90;c.render.master.maximumLimiterReductionDb=.5;require(!FullAiMasteringEngine.gentlerEquivalent(parent,c),"Extra limiter pressure promoted");
        c.render.master.maximumLimiterReductionDb=0;c.passageCheck.error="failed";require(!FullAiMasteringEngine.gentlerEquivalent(parent,c),"Failed passage promoted");
        c.file=new File(work,"clean.wav");parent.file=c.file;parent.eligible=true;
        FullAiMasteringEngine.Result r=new FullAiMasteringEngine.Result();r.selected=c;r.rankedEligible.add(parent);r.rankedEligible.add(c);
        require(FullAiReleaseQc.codecOrder(r).get(0)==c,"Codec check silently replaced selected winner with raw score leader");
        System.out.println("GENTLER_POLICY_PASS parent-immutable/benefit/cost/finite/limiter/passage/codec-order");
    }
}
