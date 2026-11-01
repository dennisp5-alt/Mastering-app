package com.dennis.aimasteringlab;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.json.*;

/** All edits are expressed on the original song timeline, never on a prior edit. */
final class StudioRecipe {
    static final class Edit {
        String id=java.util.UUID.randomUUID().toString(), kind="GAIN", channel="STEREO", reason="Manual edit";
        double start, end, db, hz=160, q=1.2, threshold=-28;
        boolean enabled=true, automatic;
        JSONObject json() throws JSONException {
            return new JSONObject().put("id",id).put("kind",kind).put("channel",channel)
                    .put("start",start).put("end",end).put("db",db).put("hz",hz).put("q",q)
                    .put("threshold",threshold).put("enabled",enabled).put("automatic",automatic).put("reason",reason);
        }
        static Edit read(JSONObject j){
            Edit e=new Edit();e.id=j.optString("id",e.id);e.kind=j.optString("kind","GAIN");e.channel=j.optString("channel","STEREO");
            e.start=j.optDouble("start",0);e.end=j.optDouble("end",0);e.db=j.optDouble("db",0);
            e.hz=j.optDouble("hz",160);e.q=j.optDouble("q",1.2);e.threshold=j.optDouble("threshold",-28);
            e.enabled=j.optBoolean("enabled",true);e.automatic=j.optBoolean("automatic",false);e.reason=j.optString("reason","Manual edit");return e;
        }
    }
    final List<Edit> edits=new ArrayList<>();
    double trimStart, trimEnd, fadeIn, fadeOut, target=-14, ceiling=-1.1;
    boolean boundaryRepair=true, lowEnd=true;
    String title="Song",artist="",album="",isrc="";
    int track=1;
    JSONObject json() throws JSONException {
        JSONArray a=new JSONArray();for(Edit e:edits)a.put(e.json());
        return new JSONObject().put("edits",a).put("trim_start",trimStart).put("trim_end",trimEnd)
                .put("fade_in",fadeIn).put("fade_out",fadeOut).put("target",target).put("ceiling",ceiling)
                .put("boundary_repair",boundaryRepair).put("low_end",lowEnd).put("title",title)
                .put("artist",artist).put("album",album).put("isrc",isrc).put("track",track);
    }
    static StudioRecipe read(JSONObject j){
        StudioRecipe r=new StudioRecipe();if(j==null)return r;
        r.trimStart=j.optDouble("trim_start",0);r.trimEnd=j.optDouble("trim_end",0);
        r.fadeIn=j.optDouble("fade_in",0);r.fadeOut=j.optDouble("fade_out",0);r.target=j.optDouble("target",-14);
        r.ceiling=j.optDouble("ceiling",-1.1);r.boundaryRepair=j.optBoolean("boundary_repair",true);r.lowEnd=j.optBoolean("low_end",true);
        r.title=j.optString("title","Song");r.artist=j.optString("artist","");r.album=j.optString("album","");r.isrc=j.optString("isrc","");r.track=j.optInt("track",1);
        JSONArray a=j.optJSONArray("edits");if(a!=null)for(int i=0;i<a.length();i++){JSONObject e=a.optJSONObject(i);if(e!=null)r.edits.add(Edit.read(e));}return r;
    }
    double end(double duration){return trimEnd==0?duration:trimEnd;}
    void validate(double duration) throws IOException {
        range(trimStart,0,duration,"Trim start");range(end(duration),trimStart+.05,duration,"Trim end");
        double length=end(duration)-trimStart;
        range(fadeIn,0,Math.min(30,length/2),"Fade in");range(fadeOut,0,Math.min(30,length/2),"Fade out");
        range(target,-30,-10,"Loudness target");range(ceiling,-6,-1,"True-peak ceiling");
        if(edits.size()>64)throw new IOException("Use at most 64 passage edits per song");
        if(title.trim().isEmpty()||title.length()>120||artist.length()>120||album.length()>120)throw new IOException("Title, artist and album must be at most 120 characters; title is required");
        if(!isrc.isEmpty()&&!isrc.matches("[A-Za-z]{2}[A-Za-z0-9]{3}[0-9]{7}"))throw new IOException("ISRC must be 12 characters without spaces or hyphens, or blank");
        for(Edit e:edits){
            if(!e.kind.equals("GAIN")&&!e.kind.equals("EQ")&&!e.kind.equals("DYNAMIC_EQ"))throw new IOException("Unknown passage edit type");
            if(!e.channel.equals("STEREO")&&!e.channel.equals("MID")&&!e.channel.equals("SIDE"))throw new IOException("Unknown edit channel");
            range(e.start,0,duration,"Edit start");range(e.end,e.start+.05,duration,"Edit end");
            range(e.db,e.kind.equals("DYNAMIC_EQ")?-1.5:-3,e.kind.equals("DYNAMIC_EQ")?0:3,"Edit amount");
            range(e.hz,35,16000,"EQ frequency");range(e.q,.3,4,"EQ Q");range(e.threshold,-80,-3,"Dynamic threshold");
        }
    }
    static void range(double value,double min,double max,String name)throws IOException{
        if(!Double.isFinite(value)||value<min-1e-6||value>max+1e-6)throw new IOException(name+" must be between "+min+" and "+max);
    }
}
