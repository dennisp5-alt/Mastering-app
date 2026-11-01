package com.dennis.aimasteringlab;

import java.io.*;
import java.util.Locale;

/** Exercises the one-shot challenger using actual PCM renders and the production competition. */
public final class GentlerRenderHarness {
    static final AudioAnalyzer.Progress QUIET=(s,p)->{};
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        File work=new File(args[0]);work.mkdirs();
        File input=new File(work,"core-correction-fixture.wav");
        try(OutputStream out=new BufferedOutputStream(new FileOutputStream(input),262144)){
            int rate=48000,frames=rate*20;
            MasterRenderer.writeHeader(out,rate,2,frames*6L,24);
            for(int i=0;i<frames;i++){
                double t=i/(double)rate,edge=Math.min(1,Math.min(t/.3,(20-t)/.7));
                double common=.09*Math.sin(2*Math.PI*70*t)+.02*Math.sin(2*Math.PI*350*t)
                        +.058*Math.sin(2*Math.PI*1100*t)+.016*Math.sin(2*Math.PI*2800*t)
                        +.005*Math.sin(2*Math.PI*6000*t);
                double side=.009*Math.sin(2*Math.PI*790*t);
                for(double v:new double[]{common+side,common-side}){
                    int q=(int)Math.round(v*edge*8388608);out.write(q);out.write(q>>8);out.write(q>>16);
                }
            }
        }
        AnalysisResult a=AudioAnalyzer.analyze(input.getName(),()->new FileInputStream(input),QUIET);
        a.stereoIntelligence=new StereoIntelligenceModel.Result();
        a.advancedIntelligence=new AdvancedStereoModel.Result();
        a.professionalIntelligence=new ProfessionalMasteringModel.Result();
        a.nextGenerationIntelligence=new NextGenerationMasteringModel.Result();
        AutonomousMasteringBrain.Result brain=AutonomousMasteringBrain.analyze(()->new FileInputStream(input),a,QUIET);
        brain.internalPlans.removeIf(p->!"transparent_flat_gain".equals(p.strategy));
        AutonomousMasteringBrain.Plan p=new AutonomousMasteringBrain.Plan();
        p.strategy="verified_core_eq";p.mode=AutonomousMasteringBrain.Mode.PREFERRED;
        p.lowShelfDb=-1.2;p.confidence=.85;
        brain.internalPlans.add(p);
        FullAiMasteringEngine.Result r=FullAiMasteringEngine.run(()->new FileInputStream(input),a,brain,new File(work,"competition"),QUIET);
        System.out.println("SOURCE_FEATURES "+brain.features.compactSummary());
        for(FullAiMasteringEngine.Candidate c:r.candidates)System.out.println(c.compactSummary()+" "+c.rejectionReasons);
        System.out.println("GENTLER_DECISION "+r.gentlerDecision);
        require(r.gentlerCandidate!=null&&r.gentlerCandidate.rendered,"Challenger was not rendered");
        require(r.candidates.stream().filter(c->c.gentlerChallenger).count()==1&&r.gentlerCandidate.renderAttempts==1,"Challenger must be bounded to one render");
        require(r.gentlerCandidate.eligible&&r.gentlerCandidate.passageCheck.passed(),"Real gentler render failed verification");
        require(r.selected.file.isFile()&&r.selected.render.master.verification.wav.frames==a.wav.frames,"Winner lost or truncated");
        require(FullAiReleaseQc.codecOrder(r).get(0)==r.selected,"Release did not keep selected winner first");
        require(r.selected==r.gentlerParent||r.selected==r.gentlerCandidate,"Unexpected winner");
        require((r.selected==r.gentlerCandidate)==FullAiMasteringEngine.gentlerEquivalent(r.gentlerParent,r.gentlerCandidate),"Promotion disagrees with measured gate");
        System.out.printf(Locale.US,"GENTLER_REAL_RENDER_PASS selected=%s parentObjective=%.6f gentlerObjective=%.6f parentCost=%.6f gentlerCost=%.6f LUFS=%.6f TP=%.6f%n",r.selected.strategy,r.gentlerParent.measuredObjective,r.gentlerCandidate.measuredObjective,r.gentlerParent.processingCost,r.gentlerCandidate.processingCost,r.selected.render.master.verification.integratedLufs,r.selected.render.master.verification.truePeakEstimateDbtp);
    }
}
