package com.dennis.aimasteringlab;
import java.io.*;
import java.util.Locale;
public final class NaturalStudioToneHarness {
 public static void main(String[] args)throws Exception {
  File input=new File(args[0]),work=new File(args[1]);work.mkdirs();
  AudioAnalyzer.Progress quiet=(s,p)->{};
  AnalysisResult a=AudioAnalyzer.analyze(input.getName(),()->new FileInputStream(input),quiet);
  a.nextGenerationIntelligence=new NextGenerationMasteringModel.Result();
  a.nextGenerationIntelligence.tones=PersistentToneModel.analyze(()->new FileInputStream(input),a,quiet);
  if(a.nextGenerationIntelligence.tones.automaticCount()<3)throw new AssertionError("hum not detected");
  AutonomousMasteringBrain.Plan plan=new AutonomousMasteringBrain.Plan();plan.strategy="transparent_flat_gain";
  NextGenerationMasteringRenderer.Result r=NextGenerationMasteringRenderer.render(()->new FileInputStream(input),a,plan,NextGenerationMasteringRenderer.Mode.PERSISTENT_TONE_REPAIR,new File(work,"tone.wav"),work,quiet);
  if(r.toneReductionDb<.1||!r.delta.protectedRegionsExact||r.delta.sourceCorrelation<.9985||r.render.master.verification.truePeakEstimateDbtp> -1)throw new AssertionError("tone benefit/protection not measured");
  for(NextGenerationMasteringRenderer.Mode m:new NextGenerationMasteringRenderer.Mode[]{NextGenerationMasteringRenderer.Mode.REFERENCE_AWARE,NextGenerationMasteringRenderer.Mode.TRANSLATION_CONSENSUS,NextGenerationMasteringRenderer.Mode.V09_COMBINED}){
   try{NextGenerationMasteringRenderer.render(()->new FileInputStream(input),a,plan,m,new File(work,"forbidden.wav"),work,quiet);throw new AssertionError("diagnostic mode rendered");}catch(IOException e){if(!e.getMessage().contains("diagnostic-only"))throw e;}
  }
  System.out.printf(Locale.US,"MEASURED_TONE_REPAIR_PASS tones=%d reduction=%.5f corr=%.8f protected=%s%n",a.nextGenerationIntelligence.tones.automaticCount(),r.toneReductionDb,r.delta.sourceCorrelation,r.delta.protectedRegionsExact);
  System.out.println("DIAGNOSTIC_ONLY_RENDER_GUARDS_PASS");
 }
}
