package com.dennis.aimasteringlab;

import java.io.*;
import java.util.*;

/** Positive and counterexample regressions for finished-audio decisions. */
public final class QualityFirstHarness {
    static final AudioAnalyzer.Progress QUIET=(s,p)->{};
    static void require(boolean condition,String why){if(!condition)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception{
        File input=new File(args[0]),work=new File(args[1]);work.mkdirs();
        limiterEnvelope();
        AnalysisResult a=AudioAnalyzer.analyze(input.getName(),()->new FileInputStream(input),QUIET);
        a.sourceGuard=SourceGuardModel.analyze(()->new FileInputStream(input),a,QUIET);
        a.professionalIntelligence=ProfessionalMasteringModel.analyze(()->new FileInputStream(input),a,QUIET);
        require(a.professionalIntelligence.hasVocalArtifacts(),"Positive fixture lacks supported cleanup events");
        TargetedQualityEvidence.Result same=TargetedQualityEvidence.compare(input,a,input,a,a.professionalIntelligence);
        require(!same.passed()&&Math.abs(same.meanTargetReductionDb)<1e-12,"Unchanged audio falsely measured as an improvement");
        AutonomousMasteringBrain.Result brain=AutonomousMasteringBrain.analyze(()->new FileInputStream(input),a,QUIET);
        FullAiMasteringEngine.Result r=FullAiMasteringEngine.run(()->new FileInputStream(input),a,brain,work,QUIET);
        int targets=0,passes=0;
        for(FullAiMasteringEngine.Candidate c:r.candidates){
            System.out.println(c.compactSummary()+" "+c.rejectionReasons);
            if(c.qualityDecision!=null)System.out.println("QUALITY_COMPARE "+c.strategy+" "+c.qualityDecision);
            if(ProfessionalMasteringRenderer.isVocal(c.professionalMode)){
                targets++;if(c.qualityEvidence!=null&&c.qualityEvidence.passed())passes++;
                if(c.professionalIntelligence!=null)require(c.professionalIntelligence.delta.protectedRegionsExact,"Cleanup changed protected boundaries");
            }
        }
        require(targets==2,"Must compare exactly two cleanup strengths");
        require(passes>0,"No independently measured cleanup benefit on injected artifact fixture");
        require(r.selected.professionalAware,"Quality-first candidate did not beat comparison reference on positive fixture");
        require(r.naturalReference!=r.selected&&r.naturalReference.file.isFile(),"Legacy reference was not retained for A/B");
        require(FullAiReleaseQc.codecOrder(r).get(0)==r.selected,"Codec stage lost quality selection");
        AnalysisResult v=r.selected.render.master.verification;
        require(v.wav.frames==a.wav.frames&&v.truePeakEstimateDbtp<=-1&&v.clippedSamples==0,"Quality winner failed technical delivery");
        // A low-score or unknown numerical result must never bypass preservation.
        r.selected.passageCheck.error="injected failure";
        require(!FullAiMasteringEngine.qualityPreservationPassed(r.selected,r.naturalReference),"Failed passage check bypassed");
        r.selected.passageCheck.error=null;
        System.out.printf(Locale.US,"QUALITY_FIRST_RENDER_PASS winner=%s reference=%s cleanupComparisons=%d measuredPasses=%d LUFS=%.6f TP=%.6f%n",r.selected.strategy,r.naturalReference.strategy,targets,passes,v.integratedLufs,v.truePeakEstimateDbtp);
    }
    static void limiterEnvelope()throws Exception{
        int n=48000;double[] x=new double[n];Arrays.fill(x,.08);
        // A later large peak must not conceal an earlier smaller peak.
        x[12000]=1.10;x[12100]=1.40;x[12300]=.95;x[n-1]=1.2;
        List<Double> old=new ArrayList<>(),smooth=new ArrayList<>();
        LookaheadLimiter a=new LookaheadLimiter(48000,-1.2,6,110,false);
        LookaheadLimiter b=new LookaheadLimiter(48000,-1.2,6,110,true);
        for(double v:x){a.add(v,-v,(l,r)->old.add(l));b.add(v,-v,(l,r)->{require(Math.abs(l+r)<1e-12,"Unlinked stereo gain");smooth.add(l);});}
        a.finish((l,r)->old.add(l));b.finish((l,r)->smooth.add(l));
        require(smooth.size()==n&&old.size()==n,"Limiter changed frame count");
        double ceiling=Math.pow(10,-1.2/20);
        for(double v:smooth)require(Math.abs(v)<=ceiling+1e-10,"Ramped limiter missed a current peak");
        require(b.maximumGainStepDb()<=6.0/288+1e-8&&a.maximumGainStepDb()>1,"Ramped attack did not remove the abrupt gain step");
        require(smooth.get(0)==x[0]&&Math.abs(smooth.get(n-1))>0,"Ramped limiter changed boundary alignment");
        System.out.printf(Locale.US,"RAMPED_ENVELOPE_PASS frames=%d maximumStepOld=%.6f maximumStepNew=%.6f earlier-peak-safe/stereo-linked/tail-aligned%n",n,a.maximumGainStepDb(),b.maximumGainStepDb());
    }
}
