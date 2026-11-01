package com.dennis.aimasteringlab;
import java.io.*;
import org.json.*;

/** Uses complete stereo energy, including sides, for diagnostic reference comparison. */
final class StudioReferences {
    static JSONObject profile(File f,AudioAnalyzer.Progress progress)throws Exception{
        AnalysisResult a=StudioAudio.measure(f,progress);StudioAudio.valid(a,a.wav.frames);
        BroadAudioProfile.Result b=BroadAudioProfile.analyze(()->new FileInputStream(f),progress);
        JSONArray tone=new JSONArray();for(int i=0;i<7;i++){
            double energy=Math.pow(10,b.midBandDb[i]/10)+Math.pow(10,b.sideBandDb[i]/10);
            tone.put(10*Math.log10(Math.max(1e-20,energy))-b.rmsDbfs);
        }
        return new JSONObject().put("meters",StudioAudio.meters(a)).put("tone",tone);
    }
    static JSONArray suggestions(JSONObject source,JSONObject reference,double start,double end)throws Exception{
        JSONArray s=source.getJSONArray("tone"),r=reference.getJSONArray("tone"),out=new JSONArray();
        double[] hz={55,120,300,900,2600,6500,12000};double budget=0;
        for(int k=0;k<7;k++){
            double diff=((Number)r.get(k)).doubleValue()-((Number)s.get(k)).doubleValue();if(Math.abs(diff)<2)continue;
            StudioRecipe.Edit e=new StudioRecipe.Edit();e.kind="EQ";e.hz=hz[k];e.q=.7;e.start=start;e.end=end;
            e.db=Math.max(-.5,Math.min(.5,diff*.12));budget+=Math.abs(e.db);e.enabled=false;
            e.reason=String.format(java.util.Locale.US,"Reference difference %+.2f dB in broad band; optional suggestion, not a measured defect",diff);
            out.put(e.json());
        }
        if(budget>1.5)for(int i=0;i<out.length();i++){JSONObject e=out.getJSONObject(i);e.put("db",e.getDouble("db")*1.5/budget);}
        return out;
    }
}
