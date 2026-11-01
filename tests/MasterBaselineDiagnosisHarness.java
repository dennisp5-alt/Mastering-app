package com.dennis.aimasteringlab;
import java.io.*;
import java.util.Locale;
public final class MasterBaselineDiagnosisHarness {
 public static void main(String[] args)throws Exception {
  File input=new File(args[0]),work=new File(args[1]);work.mkdirs();
  AudioAnalyzer.Progress quiet=(s,p)->{};
  AnalysisResult a=AudioAnalyzer.analyze(input.getName(),()->new FileInputStream(input),quiet);
  a.sourceGuard=SourceGuardModel.analyze(()->new FileInputStream(input),a,quiet);
  System.out.printf(Locale.US,"SOURCE lufs=%.8f tp=%.8f gain=%.8f predicted=%.8f limiter=%s allowed=%s noRender=%s previous=%s%n",a.integratedLufs,a.truePeakEstimateDbtp,a.appliedSafeGainDb,a.predictedOutputTruePeakDbtp,a.limiterRequired,a.limiterAllowed,a.noRenderNeeded,a.sourceGuard.secondPassGuard);
  AutonomousMasteringBrain.Plan transparent=new AutonomousMasteringBrain.Plan();
  transparent.mode=AutonomousMasteringBrain.Mode.CONSERVATIVE;transparent.strategy="transparent_flat_gain";
  try {
   AutonomousMasteringRenderer.Result r=AutonomousMasteringRenderer.render(()->new FileInputStream(input),a,transparent,new File(work,"probe.wav"),work,quiet);
   System.out.printf(Locale.US,"BASELINE_PASS %.8f %.8f%n",r.master.verification.integratedLufs,r.master.verification.truePeakEstimateDbtp);
  } catch(IOException e) {System.out.println("BASELINE_REJECTED "+e.getMessage());}
 }
}
