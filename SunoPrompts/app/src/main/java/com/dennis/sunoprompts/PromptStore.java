package com.dennis.sunoprompts;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.LinkedHashMap;

final class PromptStore extends SQLiteOpenHelper {
    static final class Prompt {
        long id;
        String artist, song, style, exclude, analysis, sources, mode;
    }

    PromptStore(Context context) {
        super(context, "suno_prompts.db", null, 1);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE prompts (id INTEGER PRIMARY KEY AUTOINCREMENT, artist TEXT NOT NULL COLLATE NOCASE, song TEXT NOT NULL COLLATE NOCASE, style TEXT NOT NULL, exclude_text TEXT NOT NULL, analysis TEXT NOT NULL, sources TEXT NOT NULL, mode TEXT NOT NULL, updated_at INTEGER NOT NULL, UNIQUE(artist,song))");
        db.execSQL("CREATE INDEX ix_artist ON prompts(artist)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) {}

    synchronized long save(Prompt p) {
        SQLiteDatabase db=getWritableDatabase();
        ContentValues v=new ContentValues();
        v.put("artist",p.artist.trim());v.put("song",p.song.trim());
        v.put("style",p.style);v.put("exclude_text",p.exclude);
        v.put("analysis",p.analysis);v.put("sources",p.sources);
        v.put("mode",p.mode);v.put("updated_at",System.currentTimeMillis());
        long id=p.id;
        if(id==0) {
            try (Cursor c=db.rawQuery("SELECT id FROM prompts WHERE artist=? COLLATE NOCASE AND song=? COLLATE NOCASE",new String[]{p.artist.trim(),p.song.trim()})) {
                if(c.moveToFirst()) id=c.getLong(0);
            }
        }
        if(id>0) db.update("prompts",v,"id=?",new String[]{String.valueOf(id)});
        else id=db.insertOrThrow("prompts",null,v);
        return id;
    }
    synchronized LinkedHashMap<String,Integer> artists() {
        LinkedHashMap<String,Integer> result=new LinkedHashMap<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT artist,COUNT(*) FROM prompts GROUP BY artist COLLATE NOCASE ORDER BY artist COLLATE NOCASE",null)) {
            while(c.moveToNext()) result.put(c.getString(0),c.getInt(1));
        }
        return result;
    }
    synchronized ArrayList<Prompt> byArtist(String artist) {
        ArrayList<Prompt> result=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,artist,song,style,exclude_text,analysis,sources,mode FROM prompts WHERE artist=? COLLATE NOCASE ORDER BY song COLLATE NOCASE",new String[]{artist})) {
            while(c.moveToNext()) result.add(from(c));
        }
        return result;
    }
    synchronized Prompt get(long id) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,artist,song,style,exclude_text,analysis,sources,mode FROM prompts WHERE id=?",new String[]{String.valueOf(id)})) {
            return c.moveToFirst()?from(c):null;
        }
    }
    synchronized void remove(long id) {
        getWritableDatabase().delete("prompts","id=?",new String[]{String.valueOf(id)});
    }
    private Prompt from(Cursor c) {
        Prompt p=new Prompt();p.id=c.getLong(0);p.artist=c.getString(1);p.song=c.getString(2);
        p.style=c.getString(3);p.exclude=c.getString(4);p.analysis=c.getString(5);
        p.sources=c.getString(6);p.mode=c.getString(7);return p;
    }
}
