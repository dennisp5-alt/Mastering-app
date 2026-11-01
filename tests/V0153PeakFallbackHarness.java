package com.dennis.aimasteringlab;
import java.io.*;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;

/** No unsafe ceiling override, truthful shortfall and unchanged compliant audio. */
public final class V0153PeakFallbackHarness {
    static final AudioAnalyzer.Progress QUIET=(stage,percent)->{};
    static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    static AnalysisResult analyze(File f)throws IOException{return AudioAnalyzer.analyze(f.getName(),()->new FileInputStream(f),QUIET);}
    static byte[] sha(File f)throws Exception{MessageDigest md=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(f)){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)md.update(b,0,n);}return md.digest();}
    static void write(File f, boolean transientPeak)throws Exception {
        int n=48000*2;
        try(PcmExportWriter w=new PcmExportWriter(f,48000,32,n)){
            for(int i=0;i<n;i++){
                double x=transientPeak?0.24*Math.sin(.067*i):0.12*Math.sin(.067*i);
                double y=transientPeak?0.23*Math.cos(.079*i):0.11*Math.cos(.079*i);
                if(transientPeak&&i==n/2){x=.96;y=-.94;}
                w.frame(x,y);
            }
        }
    }
    public static void main(String[] args)throws Exception {
        File d=new File(args.length>0?args[0]:"host-test-work/v0153");d.mkdirs();
        File source=new File(d,"peak-source.wav");write(source,true);
        AnalysisResult a=analyze(source);
        check(a.truePeakEstimateDbtp>-1.1,"peak fixture needs measured overshoot");
        a.truePeakEstimateDbtp-=1.6; // injected prediction error exercises the old fail-closed gap
        a.limiterRequired=false;a.limiterAllowed=false;a.noRenderNeeded=false;a.ceilingLimited=false;
        check(a.truePeakEstimateDbtp < -1.2,"injected predicted peak must pass upfront gate");
        byte[] inputHash=sha(source);
        File out=new File(d,"recovered.wav");
        MasterRenderer.Result r=MasterRenderer.renderWithCeiling(()->new FileInputStream(source),a,0.0,out,-1.1,false,QUIET);
        check(r.peakSafetyRecoveryPasses>=1 && r.renderPasses>=2,"no verified recovery run");
        check(r.verification.truePeakEstimateDbtp<=-1.1+.001,"unsafe recovery passed");
        check(r.dynamicsProtected && !r.limiterUsed,"recovery must be flat gain with dynamics warning");
        check(r.quantizerClips==0 && r.verification.nonFiniteSamples==0,"sample integrity lost");
        check(r.peakSafetyRecoveryAttenuationDb>0,"recovery attenuation not reported");
        check(Arrays.equals(inputHash,sha(source)),"source WAV modified");
        File blocked=new File(d,"blocked.wav");
        try{MasterRenderer.renderWithCeiling(()->new FileInputStream(source),analyze(source),2.0,blocked,-1.1,false,QUIET);throw new AssertionError("unsafe predicted flat gain accepted");}
        catch(IOException expected){check(!blocked.exists(),"unsafe output published");}
        File normal=new File(d,"normal-source.wav");write(normal,false);
        AnalysisResult n=analyze(normal);
        check(!n.limiterRequired,"normal fixture should not require limiter");
        double gain=n.appliedSafeGainDb;
        MasterRenderer.Result ordinary=MasterRenderer.renderWithCeiling(()->new FileInputStream(normal),n,gain,new File(d,"ordinary.wav"),-1.1,false,QUIET);
        MasterRenderer.Result reference=MasterRenderer.renderAttempt(()->new FileInputStream(normal),n,gain,false,-1.2,MasterRenderer.LimiterProfile.BALANCED,new File(d,"reference.wav"),QUIET);
        check(ordinary.peakSafetyRecoveryPasses==0 && ordinary.renderPasses==1,"compliant case changed passes");
        check(Arrays.equals(sha(ordinary.file),sha(reference.file)),"compliant PCM render changed bits");
        System.out.printf(Locale.US,"V0153_PEAK_FALLBACK_PASS recovery=%d attenuation=%.3f dB truePeak=%.3f targetShortfall=%.3f normal=BIT_IDENTICAL%n",r.peakSafetyRecoveryPasses,r.peakSafetyRecoveryAttenuationDb,r.verification.truePeakEstimateDbtp,r.targetShortfallLu);
    }
}
