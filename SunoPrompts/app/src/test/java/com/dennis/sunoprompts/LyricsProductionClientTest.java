package com.dennis.sunoprompts;

import org.junit.Test;
import static org.junit.Assert.*;

public final class LyricsProductionClientTest {
    @Test public void fingerprintTracksLyricEdits(){
        String a=LyricsProductionClient.fingerprint("[Verse 1]\nCount us through\n[Chorus]\nHold that gate");
        String b=LyricsProductionClient.fingerprint("[Verse 1]\nCount us through!\n[Chorus]\nHold that gate");
        assertEquals(64,a.length());
        assertNotEquals(a,b);
        assertEquals(a,LyricsProductionClient.fingerprint("[Verse 1]\nCount us through\n[Chorus]\nHold that gate"));
    }
    @Test public void promptIsLyricsFirstWithPlayingMechanics(){
        String rules=LyricsProductionClient.instructions();
        assertTrue(rules.contains("supplied ORIGINAL lyrics"));
        assertTrue(rules.contains("HOW each musician plays") || rules.contains("Prescribe HOW each musician plays"));
        assertTrue(rules.contains("MAXIMUM 1000 characters"));
        assertTrue(rules.contains("DO NOT output new lyrics"));
        assertTrue(rules.contains("chorus"));
    }
}