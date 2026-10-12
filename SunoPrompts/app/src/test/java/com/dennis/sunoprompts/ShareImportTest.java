package com.dennis.sunoprompts;

import org.junit.Test;
import static org.junit.Assert.*;

public class ShareImportTest {
    @Test public void importPromptFromOldAndroidShare(){
        String data="AC/DC — Thunderstruck\n\nSTYLE\n1980s hard rock with tight guitar and live drums.\n\nEXCLUDE\nSynth pads, metal guitars";
        ShareImport parsed=ShareImport.parse(data);
        assertEquals(ShareImport.Type.STYLE,parsed.type);
        assertEquals("AC/DC",parsed.artist);
        assertEquals("Thunderstruck",parsed.title);
        assertTrue(parsed.body.contains("live drums"));
        assertEquals("Synth pads, metal guitars",parsed.exclude);
    }
    @Test public void importLyricsFromOldAndroidShare(){
        String data="Leave the Gate Wide Open\n\n[Verse 1]\nWe crossed the line\n[Chorus]\nLeave the gate wide open";
        ShareImport parsed=ShareImport.parse(data);
        assertEquals(ShareImport.Type.LYRICS,parsed.type);
        assertEquals("Imported Lyrics",parsed.artist);
        assertEquals("Leave the Gate Wide Open",parsed.title);
        assertTrue(parsed.body.contains("[Chorus]"));
    }
    @Test public void rejectRandomOrOversizedText(){
        assertEquals(ShareImport.Type.UNKNOWN,ShareImport.parse("http://example.com").type);
        assertEquals(ShareImport.Type.UNKNOWN,ShareImport.parse("a".repeat(100001)).type);
    }
}