package com.dennis.aimasteringlab;

import java.io.File;
import java.util.Arrays;

public final class V0146DemandAwareObjectiveHarness {
    static void req(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }
    static BroadMasteringNeed.Result need(double dynamics, double eq, boolean required) {
        BroadMasteringNeed.Result n = new BroadMasteringNeed.Result();
        n.required=required; n.dynamicsDemand=dynamics; n.eqDemandDb=eq; n.stereoDemand=0;
        return n;
    }
    static FullAiMasteringEngine.Candidate c(String name, double objective, double technical,
                                             double lra, double crest, double compression) {
        FullAiMasteringEngine.Candidate c = V0145DynamicsPreservationHarness.candidate(
                lra, crest, .20, compression, technical, objective);
        c.strategy=name; c.score=technical;c.processingCost=0.1;
        c.passageCheck = new PassagePreservation.Result();
        return c;
    }
    static DemandAwareObjectiveGate.Decision judge(FullAiMasteringEngine.Candidate candidate,
                                                    FullAiMasteringEngine.Candidate ref,
                                                    BroadMasteringNeed.Result need,
                                                    boolean unresolved,
                                                    boolean broad) {
        AdaptiveDynamicsPreservation.Result envelope =
                AdaptiveDynamicsPreservation.assess(candidate,ref,need,100.0);
        return DemandAwareObjectiveGate.assess(candidate,ref,need,envelope,
                100.0,unresolved,broad);
    }
    public static void main(String[] args) throws Exception {
        BroadMasteringNeed.Result n=need(1.057,.212,true);
        FullAiMasteringEngine.Candidate ref=c("transparent_flat_gain",100.,10.,6.97,14.3,0.);
        ref.render.master.maximumLimiterReductionDb=.02;
        FullAiMasteringEngine.Candidate gentle=c("adaptive_broad_gentle",99.9563,9.750,6.45,13.94,.614);
        FullAiMasteringEngine.Candidate balanced=c("adaptive_broad_balanced",99.9592,9.755,6.25,13.76,.946);
        FullAiMasteringEngine.Candidate firm=c("adaptive_broad_firm",99.9622,9.77,6.22,13.50,1.185);
        AdaptiveDynamicsPreservation.Result old = AdaptiveDynamicsPreservation.assess(gentle,ref,n,100.0);
        req(old.envelopePassed && !old.objectiveSafe && !old.passed,
                "v0.14.5 objective veto must be reproducible for dynamics-preserved How I Am scenario");
        req(judge(gentle,ref,n,true,true).passed,"gentle must clear v0.14.6 narrowed gate");
        req(judge(balanced,ref,n,true,true).passed,"balanced must clear v0.14.6 narrowed gate");
        req(judge(firm,ref,n,true,true).passed,"firm must clear v0.14.6 narrowed gate");
        req(FullAiMasteringEngine.leastAggressiveAdaptiveBroad(Arrays.asList(firm,balanced,gentle))==gentle,
                "least-aggressive qualifying candidate should win independently of list order and score");
        gentle.measuredObjective=99.95;gentle.technicalScore=9.70;
        req(judge(gentle,ref,n,true,true).passed,"exact objective and technical tolerance boundaries must pass");
        gentle.measuredObjective=99.949;
        req(!judge(gentle,ref,n,true,true).passed,"objective loss > 0.050 must be rejected");
        gentle.measuredObjective=99.96;gentle.technicalScore=9.699;
        req(!judge(gentle,ref,n,true,true).passed,"raw technical loss > 0.300 must be rejected");
        gentle.technicalScore=9.8;
        req(!judge(gentle,ref,n,false,true).passed,"cannot apply gate when normal broad mastering has succeeded");
        req(!judge(gentle,ref,n,true,false).passed,"near-copy or narrow-only mastering cannot qualify");
        req(!judge(gentle,ref,need(.20,1.0,true),true,true).passed,
                "tonal-led source cannot use dynamics-only tolerance");
        req(!judge(gentle,ref,need(1.05,.2,false),true,true).passed,
                "source without measured broad need cannot use tolerance");
        gentle.passageCheck.error="injected passage damage";
        req(!judge(gentle,ref,n,true,true).passed,"passage preservation failures must remain a veto");
        gentle.passageCheck.error=null;
        gentle.render.master.verification.lraLu=4.70;
        req(!judge(gentle,ref,n,true,true).passed,"LRA damage must remain a veto");
        gentle.render.master.verification.lraLu=6.45;
        gentle.strategy="conservative_corrective";
        req(!judge(gentle,ref,n,true,true).passed,"non-adaptive candidates cannot use tolerance");
        gentle.strategy="adaptive_broad_gentle";
        gentle.promotionBlocked=true;
        req(!judge(gentle,ref,n,true,true).passed,"previously blocked candidate cannot revive");
        gentle.promotionBlocked=false;
        gentle.measuredObjective=Double.NaN;
        req(!judge(gentle,ref,n,true,true).passed,"nonfinite scores must fail closed");
        // Verify no later generic broad-core anchor veto can discard an already verified fallback.
        File dir = new File(args[0]);dir.mkdirs();
        FullAiMasteringEngine.Candidate t=V0145AdaptiveAnchorHarness.c(
                "transparent_flat_gain",V0145AdaptiveAnchorHarness.synth(dir,"control.wav",1,1),10.,6.97,14.3,0);
        t.measuredObjective=100.;t.passageCheck=new PassagePreservation.Result();
        FullAiMasteringEngine.Candidate a=V0145AdaptiveAnchorHarness.c(
                "adaptive_broad_gentle",V0145AdaptiveAnchorHarness.synth(dir,"candidate.wav",.965,1.045),9.75,6.45,13.94,.614);
        a.measuredObjective=99.9563;a.passageCheck=new PassagePreservation.Result();
        FullAiMasteringEngine.Result r=new FullAiMasteringEngine.Result();
        r.broadMasteringNeed=n;r.sourceObjective=100.;r.transparent=t;r.adaptiveBroadAnchor=a;
        a.masteringDelta=MasteringDeltaEvidence.compare(t.file,a.file);
        req(a.masteringDelta.broadMeaningful,"synthesized whole-song difference must be meaningful");
        r.rankedEligible.add(t);r.rankedEligible.add(a);
        req(FullAiMasteringEngine.bestBroadCoreAnchor(r,t)==a,
                "v0.14.6 final selection must retain passing dynamics-led anchor despite old objective veto");
        System.out.println("V0146_OBJECTIVE_GATE_PASS 17 checks; final anchor verified; no DSP change");
    }
}
