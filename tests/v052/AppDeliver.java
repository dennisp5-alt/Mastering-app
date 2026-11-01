package com.dennis.aimasteringlab;
import java.io.*;
/** Makes a finished master the way the app's Studio delivery does: -14 LUFS, -1.1 dBTP. */
public final class AppDeliver {
  public static void main(String[] a) throws Exception {
    File src=new File(a[0]); AnalysisResult an=StudioAudio.measure(src,(s,p)->{});
    StudioRecipe r=new StudioRecipe(); r.target=Double.parseDouble(a[2]); r.ceiling=-1.1;
    StudioAudio.Delivery d=StudioAudio.deliver(src,an,r,new File(a[1]),(s,p)->{});
    System.out.printf("APPMASTER %s %.2f LUFS %.2f dBTP limiter=%b %s%n",a[1],d.analysis.integratedLufs,d.analysis.truePeakEstimateDbtp,d.render!=null&&d.render.limiterUsed,d.note);
  }
}
