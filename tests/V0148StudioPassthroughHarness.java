package com.dennis.aimasteringlab;
import java.io.*;
import org.json.*;

/** Exact editor fast-path conditions, including hidden boundary fades. */
public final class V0148StudioPassthroughHarness {
    static void require(boolean ok,String error){if(!ok)throw new AssertionError(error);}
    public static void main(String[] args)throws Exception {
        File dir=new File(args.length>0?args[0]:".");dir.mkdirs();
        File clean=new File(dir,"v0148-silent-edges.wav"),sharp=new File(dir,"v0148-sharp-edges.wav");
        for(File f:new File[]{clean,sharp})try(PcmExportWriter out=new PcmExportWriter(f,48000,32,96000)){
            for(int i=0;i<96000;i++){
                double x=f==sharp?.12:(i==0||i==95999?0:Math.sin(i*.05)*.12);
                out.frame(x,-x);
            }
        }
        AnalysisResult a=StudioAudio.measure(clean,StudioAudio.QUIET);
        StudioRecipe r=new StudioRecipe();
        require(StudioAudio.studioPassthroughSafe(clean,a,r),"clean no-edit should bypass");
        r.fadeIn=.01;require(!StudioAudio.studioPassthroughSafe(clean,a,r),"fade cannot bypass");r.fadeIn=0;
        r.trimStart=.01;require(!StudioAudio.studioPassthroughSafe(clean,a,r),"trim cannot bypass");r.trimStart=0;
        StudioRecipe.Edit e=new StudioRecipe.Edit();e.start=0;e.end=.08;e.db=.1;r.edits.add(e);
        require(!StudioAudio.studioPassthroughSafe(clean,a,r),"enabled EQ/gain cannot bypass");
        e.enabled=false;require(StudioAudio.studioPassthroughSafe(clean,a,r),"disabled edit should bypass");
        e.enabled=true;e.db=0;require(!StudioAudio.studioPassthroughSafe(clean,a,r),"automatic/zero-depth edit should follow normal path");
        r.edits.clear();require(!StudioAudio.studioPassthroughSafe(sharp,StudioAudio.measure(sharp,StudioAudio.QUIET),r),"boundary fades cannot bypass");
        r.boundaryRepair=false;require(StudioAudio.studioPassthroughSafe(sharp,StudioAudio.measure(sharp,StudioAudio.QUIET),r),"explicitly disabled boundary repair should bypass");
        new File(dir,"v0148-silent-edges.wav").delete();new File(dir,"v0148-sharp-edges.wav").delete();
        System.out.println("V0148_STUDIO_NOOP_GUARD_PASS seven cases");
    }
}
