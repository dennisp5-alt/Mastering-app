package com.dennis.aimasteringlab;

public final class CompliantMasterPolicyHarness {
    static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static AnalysisResult good(){
        AnalysisResult a=new AnalysisResult();a.wav=new WavFile.Info();a.wav.channels=2;a.wav.sampleRate=48000;a.wav.bitsPerSample=24;a.wav.frames=480000;
        a.integratedLufs=-14.04;a.truePeakEstimateDbtp=-1.09;a.sourceSha256="0123456789abcdef";return a;
    }
    public static void main(String[] args){
        for(double lufs:new double[]{-14.09999,-14.04,-14,-13.90001}){AnalysisResult a=good();a.integratedLufs=lufs;require(AudioAnalyzer.isCompliantReleaseMaster(a),"Within-tolerance master rejected");}
        for(double lufs:new double[]{-14.10001,-13.89999,Double.NaN,Double.NEGATIVE_INFINITY}){AnalysisResult a=good();a.integratedLufs=lufs;require(!AudioAnalyzer.isCompliantReleaseMaster(a),"Out-of-policy loudness accepted");}
        AnalysisResult a=good();a.truePeakEstimateDbtp=-.999;require(!AudioAnalyzer.isCompliantReleaseMaster(a),"Unsafe peak accepted");
        a=good();a.truePeakEstimateDbtp=Double.NaN;require(!AudioAnalyzer.isCompliantReleaseMaster(a),"Unknown peak accepted");
        a=good();a.wav.bitsPerSample=16;require(!AudioAnalyzer.isCompliantReleaseMaster(a),"16-bit bypass");
        a=good();a.wav.floatingPoint=true;require(!AudioAnalyzer.isCompliantReleaseMaster(a),"Float bypass");
        a=good();a.clippedSamples=1;require(!AudioAnalyzer.isCompliantReleaseMaster(a),"Clipped source bypass");
        a=good();a.nonFiniteSamples=1;require(!AudioAnalyzer.isCompliantReleaseMaster(a),"Nonfinite bypass");
        MasterRenderer.Result m=new MasterRenderer.Result();m.compliantSourcePreserved=true;
        a=good();AnalysisResult b=good();require(MasterRenderer.isCompliantSourcePreserved(a,b,m),"Valid measured copy rejected");
        b.sourceSha256="different";require(!MasterRenderer.isCompliantSourcePreserved(a,b,m),"Flag bypassed changed hash");
        b=good();b.wav.frames--;require(!MasterRenderer.isCompliantSourcePreserved(a,b,m),"Flag bypassed changed length");
        b=good();m.gainDb=.0001;require(!MasterRenderer.isCompliantSourcePreserved(a,b,m),"Gain claimed unchanged");
        m.gainDb=0;m.renderPasses=1;require(!MasterRenderer.isCompliantSourcePreserved(a,b,m),"Rerender claimed unchanged");
        m.renderPasses=0;m.limiterUsed=true;require(!MasterRenderer.isCompliantSourcePreserved(a,b,m),"Limiter claimed unchanged");
        System.out.println("COMPLIANT_MASTER_POLICY_PASS bounds/format/finite/peak/hash/frame/gain/render/limiter");
    }
}
