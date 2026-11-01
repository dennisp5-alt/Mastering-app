package com.dennis.aimasteringlab;
import java.io.*;
public final class V0143RealRawNeedHarness {
 public static void main(String[] args)throws Exception{
  File f=new File(args[0]);AudioAnalyzer.StreamProvider p=()->new FileInputStream(f);
  AnalysisResult a=AudioAnalyzer.analyze(f.getName(),p,(s,n)->{});
  a.sourceGuard=SourceGuardModel.analyze(p,a,(s,n)->{});
  AutonomousMasteringBrain.Result brain=AutonomousMasteringBrain.analyze(p,a,(s,n)->{});
  BroadMasteringNeed.Result need=BroadMasteringNeed.assess(a,brain);
  if(!need.required)throw new AssertionError("expected real raw excerpt broad demand: "+need.summary());
  System.out.println("V0143_REAL_RAW_NEED_PASS "+need.summary());
 }
}
