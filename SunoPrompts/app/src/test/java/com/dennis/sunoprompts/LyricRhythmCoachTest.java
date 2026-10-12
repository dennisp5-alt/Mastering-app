package com.dennis.sunoprompts;

import org.junit.Test;
import static org.junit.Assert.*;

/** Safeguard against generic/story-only output regressions. */
public final class LyricRhythmCoachTest {
    @Test public void newWaveNeedsChantLikePerformance(){
        String production="1984 dark British new-wave dance-rock with pounding tribal toms, aggressive synths, guitar stabs";
        String advice=LyricRhythmCoach.focus(production);
        assertTrue(advice.contains("DANCE-ROCK"));
        assertTrue(advice.contains("shouted calls"));
        assertTrue(advice.contains("melodic"));
    }
    @Test public void catchesOvercrowdedChorus(){
        String lyrics="[Verse 1]\nWe passed the guard outside the gate\nWe did not stop\n"+
            "[Chorus]\nWild boys we leave the gate wide open and everybody has to follow us all the way until the morning comes\n"+
            "Let the last one through\n"+
            "[Bridge]\nDon't leave him there\n";
        LyricRhythmCoach.Report report=LyricRhythmCoach.inspect(
            lyrics,"New-wave dance-rock, pounding tom patterns");
        assertTrue(report.verseLines>0);
        assertTrue(report.chorusLines>0);
        assertTrue(report.summary.contains("chorus line(s) are dense"));
        assertTrue(report.estimatedChorusSyllables>=report.estimatedVerseSyllables);
    }
    @Test public void shortChantsDoNotFlagDenseChorus(){
        String lyrics="[Verse 1]\nI saw the morning coming round the corner\n"+
            "Jack grabbed the handle and started running\n"+
            "[Chorus]\nLeave it open!\nLet them through!\n"+
            "Bring him with us!\nI'm with you!\n"+
            "[Bridge]\nWe turn around\n";
        LyricRhythmCoach.Report report=LyricRhythmCoach.inspect(
            lyrics,"1980s new-wave dance-rock, tribal drums and synths");
        assertFalse(report.summary.contains("chorus line(s) are dense"));
        assertTrue(report.estimatedChorusSyllables<report.estimatedVerseSyllables);
    }
    @Test public void wildBoysCaseWarnsAboutFlatCountUsThroughChorus(){
        String raw="[Verse 1]\\nWe clocked out under camera eyes,\\nOur names in chalk beside the door.\\n"+
            "[Chorus]\\nCount us through.\\nOne by one.\\nHold that gate\\nTill the last one's through.\\n"+
            "Take my hand.\\nI've got you.\\nCount us through.\\n"+
            "[Bridge]\\nI used to want the front seat,\\nNow I count the heads behind me.";
        LyricRhythmCoach.Report report=LyricRhythmCoach.inspect(raw.replace("\\\\n","\\n"),
            "1984 new-wave dance-rock, pounding tribal toms, synth stabs");
        assertTrue("short-only hook should be flagged",report.chantOnlyWarning);
        assertTrue(report.summary.contains("CHORUS TOO CHANT-ONLY"));
    }
    @Test public void melodicResponseBalancesDanceRockChorus(){
        String raw="[Verse 1]\\nJack hid cutters in his coat\\nThe factory lights were fading\\n"+
            "[Chorus]\\nCount us through!\\nLeave the gate wide open tonight\\n"+
            "Bring him back!\\nNo one gets left on the other side\\n";
        LyricRhythmCoach.Report report=LyricRhythmCoach.inspect(raw.replace("\\\\n","\\n"),
            "dance-rock with tribal toms");
        assertFalse(report.chantOnlyWarning);
        assertTrue(report.mediumChorusLines+report.longChorusLines>0);
    }
    @Test public void countryDoesNotDemandShoutedDanceHook(){
        assertTrue(LyricRhythmCoach.suggest("1970s Australian bush-country ballad").contains("Narrative"));
        assertFalse(LyricRhythmCoach.focus("1970s Australian bush-country ballad").contains("DANCE-ROCK"));
    }
}