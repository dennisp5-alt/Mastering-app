package com.dennis.sunoprompts;

import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Map;

/**
 * User-controlled offline JSON backup. Auth tokens never leave the credential
 * vault and are never included in exports.
 */
final class LibraryBackup {
    private LibraryBackup(){}
    static final int MAX_BYTES=12_000_000;

    static JSONObject export(PromptStore store)throws Exception {
        JSONObject backup=new JSONObject();
        backup.put("format","com.dennis.sunoprompts.library");
        backup.put("schema",1);
        backup.put("created_at",System.currentTimeMillis());
        JSONArray prompts=new JSONArray(),lyrics=new JSONArray();
        for(Map.Entry<String,Integer> folder:store.artists().entrySet()){
            String artist=folder.getKey();
            for(PromptStore.Prompt p:store.byArtist(artist)){
                JSONObject entry=new JSONObject();
                entry.put("artist",p.artist);entry.put("song",p.song);
                entry.put("style",p.style);entry.put("exclude",p.exclude);
                entry.put("analysis",p.analysis);entry.put("sources",p.sources);
                entry.put("mode",p.mode);
                prompts.put(entry);
            }
            for(PromptStore.Lyrics l:store.lyricsByArtist(artist)){
                JSONObject entry=new JSONObject();
                entry.put("artist",l.artist);entry.put("song",l.song);
                entry.put("body",l.body);entry.put("brief",l.brief);
                entry.put("mode",l.mode);
                lyrics.put(entry);
            }
        }
        backup.put("prompts",prompts);backup.put("lyrics",lyrics);
        return backup;
    }

    static final class Preview {
        JSONObject document;
        int productionPrompts,lyricDrafts;
        String text(){
            return productionPrompts+" production prompts and "+lyricDrafts+
                " lyric drafts.\n\nExisting production prompts won't be overwritten. Identical lyrics will be skipped. New versions are added as separate drafts.\n\nNo passwords, API keys or ChatGPT tokens are imported.";
        }
    }
    static Preview inspect(String raw)throws Exception {
        if(raw==null || raw.length()<30 || raw.length()>MAX_BYTES)
            throw new Exception("Backup file missing, invalid or larger than the 12 MB import limit.");
        JSONObject source=new JSONObject(raw);
        if(!"com.dennis.sunoprompts.library".equals(source.optString("format")))
            throw new Exception("This isn't a Suno Prompts Studio library backup.");
        if(source.optInt("schema",-1)!=1)
            throw new Exception("Unsupported backup schema.");
        JSONArray prompts=source.optJSONArray("prompts"),lyrics=source.optJSONArray("lyrics");
        if(prompts==null || lyrics==null)
            throw new Exception("Backup is missing its production or lyrics entries.");
        if(prompts.length()>5000 || lyrics.length()>5000)
            throw new Exception("Backup contains more than 5,000 items of one type.");
        for(int i=0;i<prompts.length();i++){
            JSONObject p=prompts.optJSONObject(i);
            if(p==null || !safe(p.optString("artist"),200)||!safe(p.optString("song"),250)
               ||!safe(p.optString("style"),1000))
                throw new Exception("Invalid production prompt at position "+(i+1));
        }
        for(int i=0;i<lyrics.length();i++){
            JSONObject l=lyrics.optJSONObject(i);
            if(l==null||!safe(l.optString("artist"),200)||!safe(l.optString("song"),250)
               ||!safe(l.optString("body"),26000))
                throw new Exception("Invalid lyric draft at position "+(i+1));
        }
        Preview v=new Preview();
        v.document=source;v.productionPrompts=prompts.length();v.lyricDrafts=lyrics.length();
        return v;
    }
    private static boolean safe(String s,int limit){
        return s!=null && !s.trim().isEmpty() && s.length()<=limit;
    }
    static String restore(PromptStore store,Preview preview)throws Exception {
        JSONArray prompts=preview.document.getJSONArray("prompts");
        JSONArray lyrics=preview.document.getJSONArray("lyrics");
        int addedPrompt=0,addedLyric=0,skipped=0;
        SQLiteDatabase db=store.getWritableDatabase();
        db.beginTransaction();
        try {
            for(int i=0;i<prompts.length();i++){
                JSONObject j=prompts.getJSONObject(i);
                String artist=j.getString("artist"),song=j.getString("song");
                if(store.findPrompt(artist,song)!=null){skipped++;continue;}
                PromptStore.Prompt p=new PromptStore.Prompt();
                p.artist=artist;p.song=song;p.style=j.getString("style");
                p.exclude=j.optString("exclude","");p.analysis=j.optString("analysis","");
                p.sources=j.optString("sources","");
                p.mode=j.optString("mode","RESTORED PROMPT");
                store.save(p);addedPrompt++;
            }
            for(int i=0;i<lyrics.length();i++){
                JSONObject j=lyrics.getJSONObject(i);
                String artist=j.getString("artist"),song=j.getString("song"),body=j.getString("body");
                if(store.hasExactLyrics(artist,song,body)){skipped++;continue;}
                PromptStore.Lyrics l=new PromptStore.Lyrics();
                l.artist=artist;l.song=song;l.body=body;
                l.brief=j.optString("brief","");l.mode=j.optString("mode","RESTORED LYRICS");
                store.saveLyrics(l);addedLyric++;
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        return "Imported "+addedPrompt+" production prompts and "+addedLyric+
            " lyric drafts. Skipped "+skipped+" already-saved items.";
    }
}
