package com.dennis.aimasteringlab;
import org.json.JSONObject;
public final class V0148TimingHarness {
    static void check(boolean b,String e){if(!b)throw new AssertionError(e);}
    public static void main(String[] ignored)throws Exception{
        StudioPerformance p=new StudioPerformance();p.checkpoint("analysis_ms");p.checkpoint("render_ms");
        JSONObject j=p.json();
        check(j.getLong("analysis_ms")>=0,"negative stage");
        check(j.getLong("render_ms")>=0,"negative stage");
        check(j.getLong("elapsed_ms")>=j.getLong("analysis_ms")+j.getLong("render_ms"),"elapsed shorter than stages");
        System.out.println("V0148_TIMING_PASS monotonic stage totals");
    }
}
