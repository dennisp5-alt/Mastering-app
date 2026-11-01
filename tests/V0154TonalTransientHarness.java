package com.dennis.aimasteringlab;
import java.util.List;
public final class V0154TonalTransientHarness {
    static void check(boolean pass, String explanation) {
        if (!pass) throw new AssertionError(explanation);
    }
    static AutonomousMasteringBrain.Result brain() {
        AutonomousMasteringBrain.Result result = new AutonomousMasteringBrain.Result();
        AutonomousMasteringBrain.Plan p = new AutonomousMasteringBrain.Plan();
        p.strategy = "sonic_finish";
        p.lowShelfDb = .48; p.lowMidDb = -.32; p.presenceDb = .37;
        p.highShelfDb = .208;
        p.compressorEnabled = true; p.compressorRatio = 1.35;
        p.compressorThresholdDbfs = -17; p.compressorMaximumReductionDb = 1.8;
        p.compressorAttackMs = 22; p.compressorReleaseMs = 175;
        p.eventScale = .24; p.sideScale = 1.0;
        result.preferred = p; result.internalPlans.add(p);
        return result;
    }
    static BroadMasteringNeed.Result need(double eq, double dynamics) {
        BroadMasteringNeed.Result n = new BroadMasteringNeed.Result();
        n.required = true; n.reliability = 1.0;
        n.eqDemandDb = eq; n.dynamicsDemand = dynamics;
        return n;
    }
    public static void main(String[] args) {
        BroadMasteringNeed.Result n = need(1.378, 1.080);
        List<AutonomousMasteringBrain.Plan> ordinary = AdaptiveBroadMastering.ladder(n, brain());
        check(ordinary.size() == 3, "usual candidate ladder must stay intact");
        check(!AdaptiveBroadMastering.transientSafeTonalEligible(n,3,1,0),"single crest rejection must not trigger");
        check(!AdaptiveBroadMastering.transientSafeTonalEligible(n,3,3,1),"existing qualified candidate must win unchanged");
        check(AdaptiveBroadMastering.transientSafeTonalEligible(n,3,3,0),"tonal-led song with 3 crest failures needs audition");
        check(!AdaptiveBroadMastering.transientSafeTonalEligible(need(.45,1.1),3,3,0),"dynamics-led song must not trigger");
        check(!AdaptiveBroadMastering.transientSafeTonalEligible(need(1.1,1.1),3,3,0),"mixed-demand song must not trigger");
        AutonomousMasteringBrain.Plan p = AdaptiveBroadMastering.transientSafeTonalPlan(n,brain());
        check(p != null && p.hasAudioChanges(),"broad tonal signal must remain present");
        check(!p.compressorEnabled && p.compressorMaximumReductionDb == 0.0,"compressor must be bypassed");
        check(p.eventScale == 0.0 && p.sideScale == 1.0,"local shaping and width must remain untouched");
        check(Math.abs(p.lowShelfDb - .48*.82) < 1e-9,"same source-informed EQ anchor");
        check(p.strategy.equals("adaptive_broad_tonal_transient_safe"),"audition identity");
        System.out.println("V0154_TONAL_TRANSIENT_RECOVERY_PASS unchanged 3-rung ladder; rare crest failures only; compressor OFF, bounded EQ ON");
    }
}
