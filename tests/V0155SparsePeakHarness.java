package com.dennis.aimasteringlab;

final class V0155SparsePeakHarness {
    private static void check(boolean yes,String reason){if(!yes)throw new AssertionError(reason);}
    private static PassagePreservation.Scan scan(int windows,double crestLoss,int losses) {
        PassagePreservation.Scan s=new PassagePreservation.Scan();
        s.frames=270L*48000;s.rate=48000;s.duration=270;
        s.lufs=-16.0;
        for(int i=0;i<windows;i++){
            PassagePreservation.Window w=new PassagePreservation.Window();
            w.frames=48000;w.start=i;w.end=i+1;
            w.energy=48000*1.e-4;w.midEnergy=w.energy*0.8;
            w.sideEnergy=w.energy*.2;w.midPresence=w.energy*.25;
            w.peak=1.e-2*Math.pow(10.0,(16.0 + (i < losses?crestLoss:0.0))/20.0);
            s.windows.add(w);
        }
        return s;
    }
    public static void main(String[] args) {
        PassagePreservation.Scan original=scan(270,0,0);
        PassagePreservation.Scan gentle=scan(270,-2.2,36);
        check(!PassagePreservation.compare(original,gentle).passed(),"ordinary protection must reject");
        PassagePreservation.Result r=PassagePreservation.compareSparsePeak(original,gentle);
        check(r.passed() && r.sparsePeakWindowsReviewed==36,"36 altered windows permitted only under sparse limiter envelope");
        check(!PassagePreservation.compareSparsePeak(original,scan(270,-2.2,41)).passed(),"41 windows exceed sparse budget");
        check(!PassagePreservation.compareSparsePeak(original,scan(270,-3.3,2)).passed(),"3.3 dB crest loss must fail");
        MasterRenderer.Result m=new MasterRenderer.Result();m.limiterUsed=true;
        m.maximumLimiterReductionDb=2.3;m.averageLimiterReductionDb=.029;
        m.limiterFramesAboveOneDbPercent=.42;m.limiterFramesAboveThreeDbPercent=0;
        check(SparsePeakRecovery.sparseActivitySafe(m),"sparse limiter accepted");
        m.limiterFramesAboveOneDbPercent=1.1;
        check(!SparsePeakRecovery.sparseActivitySafe(m),"non-sparse limiter rejected");
        System.out.println("v0.15.5 sparse-peak harness PASS (normal crest gate unchanged, bounded windows, hard limit and density)");
    }
}
