package com.dennis.aimasteringlab;

import org.json.JSONObject;
import org.json.JSONException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Monotonic on-device elapsed timings; zero audio-path decisions depend on these. */
final class StudioPerformance {
    private final long begin=System.nanoTime();
    private long checkpoint=begin;
    private final LinkedHashMap<String,Long> stages=new LinkedHashMap<>();

    void checkpoint(String stage){
        long now=System.nanoTime();
        long elapsed=Math.max(0L,(now-checkpoint)/1_000_000L);
        stages.put(stage,elapsed);
        checkpoint=now;
    }
    JSONObject json() throws JSONException{
        JSONObject obj=new JSONObject();
        for(Map.Entry<String,Long> stage:stages.entrySet())obj.put(stage.getKey(),stage.getValue());
        obj.put("elapsed_ms",Math.max(0L,(System.nanoTime()-begin)/1_000_000L));
        return obj;
    }
}
