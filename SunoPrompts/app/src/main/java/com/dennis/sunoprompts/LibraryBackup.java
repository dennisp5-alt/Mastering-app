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
        backup.put("schema",2);
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
                JSONArray arrangements=new JSONArray();
                for(PromptStore.LyricProduction item:store.productionsForLyrics(l.id)){
                    JSONObject prod=new JSONObject();
                    prod.put("style",item.style);prod.put("exclude",item.exclude);
                    prod.put("notes",item.notes);prod.put("source_hash",item.sourceHash);
                    prod.put("mode",item.mode);
                    arrangements.put(prod);
                }
                entry.put("productions",arrangements);
                lyrics.put(entry);
            }
        }
        backup.put("prompts",prompts);backup.put("lyrics",lyrics);
        return backup;
    }

    static final class Preview {
        JSONObject document;
        int productionPrompts,lyricDrafts,lyricProductions;
        String text(){
            return productionPrompts+" production prompts, "+lyricDrafts+
                " lyric drafts and "+lyricProductions+" lyrics-first arrangements.\n\nExisting production prompts won't be overwritten. Identical lyrics will be skipped. New versions are added as separate drafts.\n\nNo passwords, API keys or ChatGPT tokens are imported.";
        }
    }
    static Preview inspect(String raw)throws Exception {
        if(raw==null || raw.length()<30 || raw.length()>MAX_BYTES)
            throw new Exception("Backup file missing, invalid or larger than the 12 MB import limit.");
        JSONObject source=new JSONObject(raw);
        if(!"com.dennis.sunoprompts.library".equals(source.optString("format")))
            throw new Exception("This isn't a Suno Prompts Studio library backup.");
        if(source.optInt("schema",-1)!=1 && source.optInt("schema",-1)!=2)
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
        int productionCount=0;
        for(int i=0;i<lyrics.length();i++){
            JSONObject l=lyrics.optJSONObject(i);
            if(l==null||!safe(l.optString("artist"),200)||!safe(l.optString("song"),250)
               ||!safe(l.optString("body"),26000))
                throw new Exception("Invalid lyric draft at position "+(i+1));
            JSONArray parts=l.optJSONArray("productions");
            if(parts==null)continue; // v0.4.2 backups have no arrangements.
            if(parts.length()>100)throw new Exception("Too many arrangements for one draft");
            for(int j=0;j<parts.length();j++){
                JSONObject part=parts.optJSONObject(j);
                if(part==null || !safe(part.optString("style"),1000) ||
                    part.optString("exclude","").length()>350 ||
                    part.optString("notes","").length()>30000)
                    throw new Exception("Invalid production arrangement in lyric "+(i+1));
                productionCount++;
            }
        }
        Preview v=new Preview();
        v.document=source;v.productionPrompts=prompts.length();v.lyricDrafts=lyrics.length();v.lyricProductions=productionCount;
        return v;
    }
    private static boolean safe(String s,int limit){
        return s!=null && !s.trim().isEmpty() && s.length()<=limit;
    }
    static String restore(PromptStore store,Preview preview)throws Exception {
        JSONArray prompts=preview.document.getJSONArray("prompts");
        JSONArray lyrics=preview.document.getJSONArray("lyrics");
        int addedPrompt=0,addedLyric=0,addedProductions=0,skipped=0;
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
                long lyricId=store.findExactLyricsId(artist,song,body);
                if(lyricId==0){
                    PromptStore.Lyrics l=new PromptStore.Lyrics();
                    l.artist=artist;l.song=song;l.body=body;
                    l.brief=j.optString("brief","");l.mode=j.optString("mode","RESTORED LYRICS");
                    lyricId=store.saveLyrics(l);addedLyric++;
                }else skipped++;
                JSONArray productions=j.optJSONArray("productions");
                if(productions!=null){
                    for(int k=0;k<productions.length();k++){
                        JSONObject source=productions.getJSONObject(k);
                        String style=source.getString("style");
                        String hash=source.optString("source_hash","");
                        if(store.hasExactLyricProduction(lyricId,style,hash)){skipped++;continue;}
                        PromptStore.LyricProduction p=new PromptStore.LyricProduction();
                        p.lyricId=lyricId;p.style=style;p.sourceHash=hash;
                        p.exclude=source.optString("exclude","");p.notes=source.optString("notes","");
                        p.mode=source.optString("mode","RESTORED LYRICS ARRANGEMENT");
                        store.saveLyricProduction(p);addedProductions++;
                    }
                }
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        return "Imported "+addedPrompt+" production prompts, "+addedLyric+
            " lyric drafts and "+addedProductions+" lyrics-first arrangements. Skipped "+
            skipped+" already-saved items.";
    }
}
