package com.dennis.aimasteringlab;

import java.io.*;
import java.util.ArrayList;

public final class RadioPreparationHarness {
    static final AudioAnalyzer.Progress QUIET=(s,p)->{};
    static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static AnalysisResult measure(File f)throws Exception{return AudioAnalyzer.analyze(f.getName(),()->new FileInputStream(f),QUIET);}
    static RadioPreparation.Result check(File before,File after)throws Exception{
        return RadioPreparation.analyze(()->new FileInputStream(before),measure(before),after,measure(after),QUIET);
    }
    public static void main(String[] args)throws Exception{
        File dir=new File(args[0]);dir.mkdirs();
        File clean=fixture(dir,"clean",0),wide=fixture(dir,"uncorrelated",1),phase=fixture(dir,"phase",2),tail=fixture(dir,"tail",3);
        require(check(clean,clean).issues.isEmpty(),"Clean faded source falsely warned");
        require(check(wide,wide).issues.isEmpty(),"Uncorrelated stereo width falsely treated as phase damage");
        RadioPreparation.Result introduced=check(clean,phase),inherited=check(phase,phase);
        require(introduced.issues.size()==1 && inherited.issues.size()==1,"Local phase window detection");
        RadioPreparation.Issue a=introduced.issues.get(0),b=inherited.issues.get(0);
        require(a.kind.equals("MONO_SECTION")&&a.startSeconds==2&&a.endSeconds==3&&!a.presentInSource&&b.presentInSource,"Phase provenance or timestamp");
        RadioPreparation.Result ending=check(clean,tail);
        require(ending.issues.stream().anyMatch(i->i.kind.equals("ENDING_TRANSIENT")&&!i.presentInSource),"Late transient warning");
        String sha=Hashing.sha256(tail);check(tail,tail);require(sha.equals(Hashing.sha256(tail)),"Diagnostics edited audio");
        Thread.currentThread().interrupt();
        try {check(clean,clean);throw new AssertionError("Cancellation ignored");}catch(IOException expected){}finally{Thread.interrupted();}
        for(MasterRenderer.LimiterProfile profile:MasterRenderer.LimiterProfile.values())if(profile!=MasterRenderer.LimiterProfile.OVERSAMPLED_CLEAN)for(int frames:new int[]{31,2000}){
            double[][] x=new double[2][frames];x[0][0]=.2;x[1][frames-1]=-.3;x[0][frames-2]=.1;
            LookaheadLimiter l=new LookaheadLimiter(48000,-1.2,profile.lookaheadMs,profile.releaseMs,profile==MasterRenderer.LimiterProfile.RAMPED_CLEAN);
            ArrayList<double[]> y=new ArrayList<>();LookaheadLimiter.Output out=(left,right)->y.add(new double[]{left,right});
            for(int f=0;f<frames;f++)l.add(x[0][f],x[1][f],out);l.finish(out);
            require(y.size()==frames,"Limiter changed length "+profile.label);
            for(int f=0;f<frames;f++)for(int c=0;c<2;c++)require(y.get(f)[c]==x[c][f],"Limiter shifted or discarded boundary samples "+profile.label);
        }
        System.out.println("RADIO_PREPARATION_PASS clean/uncorrelated/inherited/introduced/tail/cancellation");
        System.out.println("LIMITER_BOUNDARY_ALIGNMENT_PASS all-four-profiles short-and-long-streams");
    }
    static File fixture(File dir,String name,int mode)throws Exception{
        File f=new File(dir,name+".wav");int rate=48000,frames=rate*5;
        try(OutputStream out=new BufferedOutputStream(new FileOutputStream(f),262144)){
            MasterRenderer.writeHeader(out,rate,2,frames*6L,24);
            for(int i=0;i<frames;i++){
                double t=i/(double)rate,edge=Math.min(1,Math.min(t/.1,(frames-1-i)/(rate*.5)));
                double left=.12*Math.sin(2*Math.PI*440*t)*edge;
                double right=mode==1?.12*Math.sin(2*Math.PI*733*t)*edge:left;
                if(mode==2&&t>=2&&t<3)right=-left;
                if(mode==3&&t>4.96&&t<4.99)left=right=.4*Math.sin(2*Math.PI*1000*t);
                for(double v:new double[]{left,right}){int q=(int)Math.round(v*8388608);out.write(q);out.write(q>>8);out.write(q>>16);}
            }
        }return f;
    }
}
