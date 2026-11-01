package com.dennis.aimasteringlab;
import java.util.*;
public final class V0144AdaptiveLadderHarness {
    static void req(boolean ok,String m){if(!ok)throw new AssertionError(m);}
    static AutonomousMasteringBrain.Result brain(){
        AutonomousMasteringBrain.Result r=new AutonomousMasteringBrain.Result();
        AutonomousMasteringBrain.Plan p=new AutonomousMasteringBrain.Plan();
        p.strategy="sonic_finish";p.lowShelfDb=.18;p.lowMidDb=-.08;p.presenceDb=.03;p.highShelfDb=.02;
        p.sideScale=1.0;p.eventScale=.30;p.compressorEnabled=true;p.compressorThresholdDbfs=-18;
        p.compressorRatio=1.30;p.compressorAttackMs=24;p.compressorReleaseMs=150;p.compressorMaximumReductionDb=1.20;
        p.confidence=.9;r.preferred=p;r.internalPlans.add(p);return r;
    }
    public static void main(String[] args){
        BroadMasteringNeed.Result need=new BroadMasteringNeed.Result();need.required=true;need.eqDemandDb=.21;need.dynamicsDemand=1.05;need.reliability=1.0;
        List<AutonomousMasteringBrain.Plan> x=AdaptiveBroadMastering.ladder(need,brain());
        req(x.size()==3,"expected 3 rungs");
        req("adaptive_broad_gentle".equals(x.get(0).strategy),"gentle name");
        req(x.get(0).compressorRatio<x.get(1).compressorRatio&&x.get(1).compressorRatio<x.get(2).compressorRatio,"dynamics ladder not increasing");
        req(Math.abs(x.get(0).lowShelfDb)<Math.abs(x.get(2).lowShelfDb),"EQ ladder not increasing");
        req(x.get(0).compressorAttackMs>x.get(2).compressorAttackMs,"gentle rung should preserve transients with slower attack");
        req(x.get(2).compressorMaximumReductionDb<=2.20,"max GR cap exceeded");
        System.out.println("V0144_ADAPTIVE_LADDER_PASS "+x.get(0).compactSummary()+" | "+x.get(2).compactSummary());
    }
}
