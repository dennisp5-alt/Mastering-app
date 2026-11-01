package com.dennis.aimasteringlab;
import java.io.*;

/** Steady brightness, side-only HF bursts, and misleading gain changes. */
public final class QualityCounterexampleHarness {
    static final AudioAnalyzer.Progress QUIET=(s,p)->{};
    static void require(boolean v,String s){if(!v)throw new AssertionError(s);}
    public static void main(String[] args)throws Exception{
        File work=new File(args[0]);work.mkdirs();
        File base=fixture(work,"steady-bright",0),wide=fixture(work,"side-bursts",1),gain=fixture(work,"uniform-gain",2),boost=fixture(work,"bad-target-boost",3);
        for(File f:new File[]{base,wide}){
            AnalysisResult a=measure(f);
            ProfessionalMasteringModel.Result m=ProfessionalMasteringModel.analyze(()->new FileInputStream(f),a,QUIET);
            require(!m.hasVocalArtifacts(),"Steady brightness or side-only event mistaken for a centred artifact: "+f.getName()+" "+m.compactSummary());
        }
        // Deliberately supply a source event map: the finished-audio comparison must
        // still reject no-op gain and a change that actually increases its target.
        ProfessionalMasteringModel.Result map=new ProfessionalMasteringModel.Result();
        ProfessionalMasteringModel.Event e=new ProfessionalMasteringModel.Event();
        e.kind=ProfessionalMasteringModel.Kind.VOCAL_SIBILANCE;e.startSeconds=6;e.endSeconds=6.5;e.automatic=true;e.confidence=.99;map.events.add(e);
        TargetedQualityEvidence.Result g=TargetedQualityEvidence.compare(base,measure(base),gain,measure(gain),map);
        require(!g.passed()&&Math.abs(g.meanTargetReductionDb)<.001,"Flat gain falsely credited as cleanup");
        TargetedQualityEvidence.Result b=TargetedQualityEvidence.compare(base,measure(base),boost,measure(boost),map);
        require(!b.passed()&&b.worsenedEvents>0,"Actual target damage passed despite requested correction");
        Thread.currentThread().interrupt();
        try{TargetedQualityEvidence.compare(base,measure(base),base,measure(base),map);throw new AssertionError("Cancelled verification continued");}
        catch(IOException expected){}finally{Thread.interrupted();}
        System.out.println("QUALITY_COUNTEREXAMPLES_PASS steady-bright/side-only/flat-gain/worsened-target/cancellation");
    }
    static AnalysisResult measure(File f)throws Exception{return AudioAnalyzer.analyze(f.getName(),()->new FileInputStream(f),QUIET);}
    static File fixture(File work,String name,int mode)throws Exception{
        File f=new File(work,name+".wav");int frames=48000*20;
        try(OutputStream out=new BufferedOutputStream(new FileOutputStream(f),262144)){
            MasterRenderer.writeHeader(out,48000,2,frames*6L,24);
            for(int i=0;i<frames;i++){
                double t=i/48000.0,edge=Math.min(1,Math.min(t/.2,(20-t)/.5));
                double body=.045*Math.sin(2*Math.PI*930*t)+.055*Math.sin(2*Math.PI*210*t)+.025*Math.sin(2*Math.PI*2300*t);
                double high=.030*Math.sin(2*Math.PI*8000*t);
                if(mode==3&&t>=6&&t<6.5)high*=1.4;
                double side=.01*Math.sin(2*Math.PI*730*t);
                if(mode==1&&t>=6&&t<6.5)side+=.09*Math.sin(2*Math.PI*8000*t);
                double scale=edge*(mode==2?.8:1);
                for(double v:new double[]{(body+high+side)*scale,(body+high-side)*scale}){
                    int q=(int)Math.round(v*8388608);out.write(q);out.write(q>>8);out.write(q>>16);
                }
            }
        }return f;
    }
}
