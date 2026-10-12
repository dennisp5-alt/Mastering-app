package com.dennis.sunoprompts;

import org.junit.Test;
import static org.junit.Assert.*;

public final class LyricRhythmCoachTest {
    @Test public void newWaveNeedsChantPlusMelodicRelease(){
        String advice=LyricRhythmCoach.focus("1984 new-wave dance-rock, pounding toms");
        assertTrue(advice.contains("DANCE-ROCK"));
        assertTrue(advice.contains("shouted calls"));
        assertTrue(advice.contains("melodic"));
    }
    @Test public void catchesCrowdedChorus(){
        String lyrics="[Verse 1]\nWe passed the guard outside the gate\nWe did not stop\n"+
            "[Chorus]\nWe leave the gate wide open and everybody has to follow us all the way until the morning comes\n"+
            "Let the last one through\n[Bridge]\nDon't leave him there\n";
        LyricRhythmCoach.Report r=LyricRhythmCoach.inspect(lyrics,"new-wave dance-rock");
        assertTrue(r.verseLines>0);
        assertTrue(r.chorusLines>0);
        assertTrue(r.summary.contains("chorus line(s) are dense"));
    }
    @Test public void countUsThroughFlatChorusGetsWarning(){
        String lyrics="[Verse 1]\nWe clocked out under camera eyes\nOur names in chalk beside the door\n"+
            "[Chorus]\nCount us through.\nOne by one.\nHold that gate.\nTill the last one's through.\n"+
            "Take my hand.\nI've got you.\nCount us through.\n"+
            "[Bridge]\nNow I count the heads behind me.\n";
        LyricRhythmCoach.Report r=LyricRhythmCoach.inspect(lyrics,"1984 new-wave dance-rock, tribal toms");
        assertTrue("flat chant-only chorus must be flagged",r.chantOnlyWarning);
        assertTrue(r.summary.contains("CHANT-HEAVY DANCE-ROCK CHORUS"));
    }
    @Test public void melodicAnswersDoNotTriggerFlatChorusWarning(){
        String lyrics="[Verse 1]\nJack hid cutters in his coat\nThe factory lights were fading\n"+
            "[Chorus]\nCount us through!\nLeave the gate wide open tonight!\n"+
            "Bring him back!\nNo one gets left on the other side!\n";
        LyricRhythmCoach.Report r=LyricRhythmCoach.inspect(lyrics,"dance-rock tribal toms");
        assertFalse(r.chantOnlyWarning);
        assertTrue(r.mediumChorusLines+r.longChorusLines>0);
    }
    @Test public void importedLyricsWarnWithoutGenre(){
        String chorus="[Chorus]\nCount us through.\nOne by one.\nHold that gate\nTill the last one's through.\nTake my hand.\nI've got you.\nCount us through.\n";
        String source="[Verse 1]\nWe clocked out under camera eyes\nOur names in chalk beside the door\n"+
            chorus+chorus.replace("[Chorus]","[Final Chorus]")+
            chorus+"[Bridge]\nNow I count the heads behind me\n";
        LyricRhythmCoach.Report report=LyricRhythmCoach.inspect(source,"Imported Lyrics");
        assertTrue(report.chantOnlyWarning);
        assertTrue(report.summary.contains("POSSIBLE CHANT-ONLY CHORUS"));
        assertTrue(report.summary.contains("No specific production style detected"));
    }
    @Test public void newRepairedChorusWithAlternatingSyllableLengthPasses(){
        String lyrics="[Verse 1]\nClocked out cameras watched us go\nOur names in chalk beside the door\n"+
            "[Chorus]\nCount us through.\nHold that gate till the last one's through.\n"+
            "Take my hand.\nIf you fall back I'll come back for you.\n"+
            "Count us through.\nOne by one till we're all outside.\n";
        LyricRhythmCoach.Report report=LyricRhythmCoach.inspect(lyrics,"");
        assertFalse(report.chantOnlyWarning);
        assertTrue(report.mediumChorusLines>=2);
    }
    @Test public void countryKeepsItsOwnVocalRhythm(){
        assertTrue(LyricRhythmCoach.suggest("Australian bush-country ballad").contains("Narrative"));
        assertFalse(LyricRhythmCoach.focus("Australian bush-country ballad").contains("DANCE-ROCK"));
    }
}
