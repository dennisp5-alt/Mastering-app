package com.dennis.sunoprompts;

import org.json.JSONArray;
import org.json.JSONObject;
import android.net.Uri;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

final class ResearchClient {
    static final class Research {
        String metadata="", artistFacts="", songFacts="", sources="", warnings="";
        String context() {
            return "VERIFIED DATABASE MATCH:\n"+metadata+"\n\nARTIST ENCYCLOPEDIA:\n"+artistFacts+
                    "\n\nSONG ENCYCLOPEDIA:\n"+songFacts+"\n\nREFERENCES:\n"+sources+
                    "\n\nLIMITATIONS:\n"+warnings;
        }
    }
    private static long lastMusicBrainz=0;

    static Research lookup(String artist,String song) {
        Research result=new Research();
        try { musicBrainz(result,artist,song); }
        catch(Exception e) {result.warnings+="MusicBrainz lookup unavailable. "; }
        try {
            String title=wikiFind(artist,true);
            if(!title.isEmpty()) {
                result.artistFacts=limit(wikiExtract(title),1600);
                result.sources+="Wikipedia ("+title+"): https://en.wikipedia.org/wiki/"+wikiSegment(title)+"\n";
            }
        } catch(Exception e) {result.warnings+="Artist encyclopedia unavailable. ";}
        try {
            String title=wikiFind(song+" "+artist,false);
            if(!title.isEmpty() && matchesSong(title,song)) {
                result.songFacts=limit(wikiExtract(title),3200);
                result.sources+="Wikipedia ("+title+"): https://en.wikipedia.org/wiki/"+wikiSegment(title)+"\n";
            } else {result.warnings+="No confidently matched song article. ";}
        }catch(Exception e) {result.warnings+="Song encyclopedia unavailable. ";}
        if(result.metadata.isEmpty()) result.warnings+="No verified recording metadata; do not infer exact key, BPM or instruments. ";
        if(result.sources.trim().isEmpty()) result.warnings+="No web sources accessible; output is a starter template only.";
        return result;
    }
    private static boolean matchesSong(String title,String song) {
        String a=title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");
        String b=song.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");
        return !b.isEmpty() && a.contains(b);
    }
    private static synchronized void musicBrainz(Research r,String artist,String song) throws Exception {
        long delay=1100-(System.currentTimeMillis()-lastMusicBrainz);
        if(delay>0) Thread.sleep(delay);
        lastMusicBrainz=System.currentTimeMillis();
        String query="recording:\""+song.replace("\"","")+"\" AND artist:\""+artist.replace("\"","")+"\"";
        String url="https://musicbrainz.org/ws/2/recording/?fmt=json&limit=8&query="+encode(query);
        JSONObject json=new JSONObject(read(url));
        JSONArray records=json.optJSONArray("recordings");
        if(records==null) return;
        for(int i=0;i<records.length();i++) {
            JSONObject record=records.optJSONObject(i);if(record==null)continue;
            if(!record.optString("title","").equalsIgnoreCase(song))continue;
            JSONArray credits=record.optJSONArray("artist-credit");
            boolean match=false;
            if(credits!=null) for(int j=0;j<credits.length();j++){
                JSONObject a=credits.optJSONObject(j);
                if(a!=null && (a.optString("name","").equalsIgnoreCase(artist) ||
                  a.optJSONObject("artist")!=null && a.optJSONObject("artist").optString("name","").equalsIgnoreCase(artist))) match=true;
            }
            if(!match)continue;
            String id=record.optString("id","");
            r.metadata="Recording: "+record.optString("title")+", credited to "+artist;
            int duration=record.optInt("length",0);
            if(duration>0)r.metadata+="\nDuration: "+duration/60000+":"+String.format(Locale.US,"%02d",(duration/1000)%60);
            String date=record.optString("first-release-date","");
            if(!date.isEmpty())r.metadata+="\nEarliest catalogued release: "+date;
            if(!id.isEmpty())r.sources+="MusicBrainz recording: https://musicbrainz.org/recording/"+id+"\n";
            return;
        }
    }
    private static String wikiFind(String phrase,boolean artist) throws Exception {
        String url="https://en.wikipedia.org/w/api.php?action=query&list=search&srnamespace=0&srlimit=5&format=json&srsearch="+encode(phrase);
        JSONArray hits=new JSONObject(read(url)).getJSONObject("query").optJSONArray("search");
        if(hits==null)return "";
        for(int i=0;i<hits.length();i++) {
            String title=hits.getJSONObject(i).optString("title","");
            if(artist && title.equalsIgnoreCase(phrase)) return title;
            if(!artist && matchesSong(title,phrase.split(" ")[0])) { /* inspect complete input below */ }
            if(!artist)return title;
        }
        return "";
    }
    private static String wikiExtract(String title) throws Exception {
        String url="https://en.wikipedia.org/w/api.php?action=query&prop=extracts&explaintext=1&exintro=1&redirects=1&format=json&titles="+encode(title);
        JSONObject pages=new JSONObject(read(url)).getJSONObject("query").getJSONObject("pages");
        java.util.Iterator<String> it=pages.keys();
        if(!it.hasNext())return "";
        return pages.getJSONObject(it.next()).optString("extract","");
    }
    private static String wikiSegment(String name) {
        return Uri.encode(name.replace(' ','_'));
    }
    private static String encode(String s) throws Exception {
        return URLEncoder.encode(s,StandardCharsets.UTF_8.name());
    }
    private static String limit(String text,int max) {
        if(text==null)return "";return text.length()>max?text.substring(0,max)+"…":text;
    }
    private static String read(String url) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setRequestMethod("GET");c.setConnectTimeout(12000);c.setReadTimeout(12000);
        c.setRequestProperty("User-Agent","SunoPrompts/0.1 (https://github.com/dennisp5-alt/Mastering-app)");
        c.setRequestProperty("Accept","application/json");
        try {
            int code=c.getResponseCode();if(code<200||code>=300)throw new Exception("HTTP "+code);
            try(InputStream stream=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[4096];int count;
                while((count=stream.read(buffer))!=-1) {
                    out.write(buffer,0,count);
                    if(out.size()>1500000)throw new Exception("Response too large");
                }
                return out.toString(StandardCharsets.UTF_8.name());
            }
        }finally{c.disconnect();}
    }
}
