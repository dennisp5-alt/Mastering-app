package com.dennis.sunoprompts;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.LinkedHashMap;

/** Existing production-prompt data is preserved; lyrics are separate draft records. */
final class PromptStore extends SQLiteOpenHelper {
    static final class Prompt {
        long id;
        String artist, song, style, exclude, analysis, sources, mode;
    }
    static final class Lyrics {
        long id;
        String artist="", song="", body="", brief="", mode="";
        long updatedAt;
    }

    PromptStore(Context context) {
        super(context,"suno_prompts.db",null,2);
    }
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE prompts (id INTEGER PRIMARY KEY AUTOINCREMENT, artist TEXT NOT NULL COLLATE NOCASE, song TEXT NOT NULL COLLATE NOCASE, style TEXT NOT NULL, exclude_text TEXT NOT NULL, analysis TEXT NOT NULL, sources TEXT NOT NULL, mode TEXT NOT NULL, updated_at INTEGER NOT NULL, UNIQUE(artist,song))");
        db.execSQL("CREATE INDEX ix_artist ON prompts(artist)");
        createLyrics(db);
    }
    private void createLyrics(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS lyrics (id INTEGER PRIMARY KEY AUTOINCREMENT, artist TEXT NOT NULL COLLATE NOCASE, song TEXT NOT NULL COLLATE NOCASE, lyric_body TEXT NOT NULL, brief TEXT NOT NULL, mode TEXT NOT NULL, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS ix_lyrics_artist ON lyrics(artist)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){
        if(oldVersion<2)createLyrics(db); // No DROP; keep every v0.1-v0.3 prompt.
    }
    synchronized long save(Prompt p){
        SQLiteDatabase db=getWritableDatabase();
        ContentValues v=new ContentValues();
        v.put("artist",p.artist.trim());v.put("song",p.song.trim());
        v.put("style",p.style);v.put("exclude_text",p.exclude);
        v.put("analysis",p.analysis);v.put("sources",p.sources);
        v.put("mode",p.mode);v.put("updated_at",System.currentTimeMillis());
        long id=p.id;
        if(id==0){
            try(Cursor c=db.rawQuery("SELECT id FROM prompts WHERE artist=? COLLATE NOCASE AND song=? COLLATE NOCASE",new String[]{p.artist.trim(),p.song.trim()})){
                if(c.moveToFirst())id=c.getLong(0);
            }
        }
        if(id>0)db.update("prompts",v,"id=?",new String[]{String.valueOf(id)});
        else id=db.insertOrThrow("prompts",null,v);
        return id;
    }
    synchronized LinkedHashMap<String,Integer> artists(){
        LinkedHashMap<String,Integer> result=new LinkedHashMap<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT artist,COUNT(*) FROM (SELECT artist FROM prompts UNION ALL SELECT artist FROM lyrics) GROUP BY artist COLLATE NOCASE ORDER BY artist COLLATE NOCASE",null)){
            while(c.moveToNext())result.put(c.getString(0),c.getInt(1));
        }
        return result;
    }
    synchronized ArrayList<Prompt> byArtist(String artist){
        ArrayList<Prompt> result=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,artist,song,style,exclude_text,analysis,sources,mode FROM prompts WHERE artist=? COLLATE NOCASE ORDER BY song COLLATE NOCASE",new String[]{artist})){
            while(c.moveToNext())result.add(from(c));
        }
        return result;
    }
    synchronized Prompt findPrompt(String artist,String song){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,artist,song,style,exclude_text,analysis,sources,mode FROM prompts WHERE artist=? COLLATE NOCASE AND song=? COLLATE NOCASE LIMIT 1",new String[]{artist,song})){
            return c.moveToFirst()?from(c):null;
        }
    }
    synchronized Prompt get(long id){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,artist,song,style,exclude_text,analysis,sources,mode FROM prompts WHERE id=?",new String[]{String.valueOf(id)})){
            return c.moveToFirst()?from(c):null;
        }
    }
    synchronized void remove(long id){
        getWritableDatabase().delete("prompts","id=?",new String[]{String.valueOf(id)});
    }
    private Prompt from(Cursor c){
        Prompt p=new Prompt();p.id=c.getLong(0);p.artist=c.getString(1);p.song=c.getString(2);
        p.style=c.getString(3);p.exclude=c.getString(4);p.analysis=c.getString(5);
        p.sources=c.getString(6);p.mode=c.getString(7);return p;
    }

    /** Create a NEW draft by default, retaining previous generated versions. */
    synchronized long saveLyrics(Lyrics draft){
        ContentValues v=new ContentValues();
        v.put("artist",draft.artist.trim());
        v.put("song",draft.song.trim());
        v.put("lyric_body",draft.body);
        v.put("brief",draft.brief);
        v.put("mode",draft.mode);
        v.put("updated_at",System.currentTimeMillis());
        if(draft.id>0){
            int count=getWritableDatabase().update("lyrics",v,"id=?",new String[]{String.valueOf(draft.id)});
            if(count<1)throw new IllegalStateException("The lyric draft no longer exists");
            return draft.id;
        }
        return getWritableDatabase().insertOrThrow("lyrics",null,v);
    }
    synchronized Lyrics getLyrics(long id){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,artist,song,lyric_body,brief,mode,updated_at FROM lyrics WHERE id=?",new String[]{String.valueOf(id)})){
            return c.moveToFirst()?lyricsFrom(c):null;
        }
    }
    synchronized ArrayList<Lyrics> lyricsByArtist(String artist){
        ArrayList<Lyrics> result=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,artist,song,lyric_body,brief,mode,updated_at FROM lyrics WHERE artist=? COLLATE NOCASE ORDER BY updated_at DESC,id DESC",new String[]{artist})){
            while(c.moveToNext())result.add(lyricsFrom(c));
        }
        return result;
    }
    synchronized boolean hasExactLyrics(String artist,String song,String text){
        try(Cursor c=getReadableDatabase().rawQuery(
            "SELECT 1 FROM lyrics WHERE artist=? COLLATE NOCASE AND song=? COLLATE NOCASE AND lyric_body=? LIMIT 1",
            new String[]{artist,song,text})){
            return c.moveToFirst();
        }
    }
    synchronized int lyricCount(){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM lyrics",null)){
            return c.moveToFirst()?c.getInt(0):0;
        }
    }
    synchronized void deleteLyrics(long id){
        getWritableDatabase().delete("lyrics","id=?",new String[]{String.valueOf(id)});
    }
    private Lyrics lyricsFrom(Cursor c){
        Lyrics l=new Lyrics();
        l.id=c.getLong(0);l.artist=c.getString(1);l.song=c.getString(2);
        l.body=c.getString(3);l.brief=c.getString(4);l.mode=c.getString(5);
        l.updatedAt=c.getLong(6);return l;
    }
}
