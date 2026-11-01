package com.dennis.aimasteringlab;
import java.util.*;
public final class V0144AdaptiveSelectionHarness {
    static void req(boolean ok,String m){if(!ok)throw new AssertionError(m);}
    static FullAiMasteringEngine.Candidate c(String n,double tech,double cost){
        FullAiMasteringEngine.Candidate c=new FullAiMasteringEngine.Candidate();c.strategy=n;c.eligible=true;c.technicalScore=tech;c.processingCost=cost;return c;
    }
    public static void main(String[] args){
        FullAiMasteringEngine.Candidate gentle=c("adaptive_broad_gentle",10.00,.12);
        FullAiMasteringEngine.Candidate balanced=c("adaptive_broad_balanced",10.02,.18);
        FullAiMasteringEngine.Candidate firm=c("adaptive_broad_firm",10.06,.31);
        FullAiMasteringEngine.Candidate a=FullAiMasteringEngine.safestAdaptiveBroad(Arrays.asList(gentle,balanced,firm));
        req(a==firm,"firm should win when technical benefit is >0.03 above lower-cost alternatives");
        firm.technicalScore=10.045;
        a=FullAiMasteringEngine.safestAdaptiveBroad(Arrays.asList(gentle,balanced,firm));
        req(a==balanced,"balanced should win as safest candidate within 0.03 of best technical score");
        balanced.technicalScore=10.025;firm.technicalScore=10.025;
        a=FullAiMasteringEngine.safestAdaptiveBroad(Arrays.asList(gentle,balanced,firm));
        req(a==gentle,"gentle should win when all qualifying rungs are within 0.03 technical window");
        System.out.println("V0144_ADAPTIVE_SELECTION_PASS "+a.strategy);
    }
}
