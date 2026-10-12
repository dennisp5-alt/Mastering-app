package com.dennis.sunoprompts;

import java.util.Locale;

/**
 * Parses Android ACTION_SEND text from the original Suno Prompts app.
 * Never silently imports, never overwrites without a confirmation.
 */
final class ShareImport {
    enum Type { STYLE, LYRICS, UNKNOWN }
    final Type type;
    final String artist, title, body, exclude;
    private ShareImport(Type type,String artist,String title,String body,String exclude) {
        this.type=type;this.artist=artist;this.title=title;this.body=body;this.exclude=exclude;
    }
    static ShareImport parse(CharSequence raw) {
        if(raw==null || raw.length()==0 || raw.length()>100_000)
            return new ShareImport(Type.UNKNOWN,"","","","");
        String shared=raw.toString().replace("\r\n","\n").trim();
        if(shared.startsWith("\uFEFF"))shared=shared.substring(1).trim();
        int firstBreak=shared.indexOf('\n');
        if(firstBreak<0 || firstBreak>350)
            return new ShareImport(Type.UNKNOWN,"","","","");
        String titleLine=shared.substring(0,firstBreak).trim();
        if(titleLine.isEmpty())return new ShareImport(Type.UNKNOWN,"","","","");
        String content=shared.substring(firstBreak).trim();
        int styleStart=content.indexOf("STYLE\n");
        int excludeStart=content.lastIndexOf("\nEXCLUDE\n");
        if(styleStart>=0 && excludeStart>styleStart) {
            int separator=titleLine.indexOf(" \u2014 ");
            if(separator<1 || separator>=titleLine.length()-3)
                return new ShareImport(Type.UNKNOWN,"","","","");
            String artist=titleLine.substring(0,separator).trim();
            String song=titleLine.substring(separator+3).trim();
            String style=content.substring(styleStart+6,excludeStart).trim();
            String exclude=content.substring(excludeStart+9).trim();
            if(artist.isEmpty()||song.isEmpty()||style.isEmpty()||style.length()>1000)
                return new ShareImport(Type.UNKNOWN,"","","","");
            return new ShareImport(Type.STYLE,artist,song,style,exclude);
        }
        // Earlier Suno Prompts lyric share is SongTitle\n\n[Verse 1]...
        if(content.contains("[Verse") && content.contains("[Chorus") && content.length()<26000) {
            return new ShareImport(Type.LYRICS,"Imported Lyrics",titleLine,content,"");
        }
        return new ShareImport(Type.UNKNOWN,"","","","");
    }
}
