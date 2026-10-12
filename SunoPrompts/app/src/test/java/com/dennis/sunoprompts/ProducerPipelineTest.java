package com.dennis.sunoprompts;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public final class ProducerPipelineTest {
    @Test public void lightsOnTheHillPassesReviewedSongReferenceToMusicalDna(){
        ProducerPipeline.Context c=ProducerPipeline.context(
            "Slim Dusty","Lights on the Hill","Natural one-room 1970s ensemble","No metadata");
        assertNotNull(c.reviewed);
        String input=ProducerPipeline.firstRequest(c);
        assertTrue(input.contains("CURATED SONG-SPECIFIC INTERPRETATION"));
        assertTrue(input.toLowerCase().contains("rolling"));
        assertTrue(input.toLowerCase().contains("pedal steel"));
        assertTrue(input.toLowerCase().contains("conversational"));
        assertTrue(input.contains("USER DIRECTION: Natural one-room 1970s ensemble"));
        assertTrue(input.contains("not verified audio"));
        assertTrue(ProducerPipeline.SUNO_RULES.contains("SILENT QUALITY REVIEW"));
    }
    @Test public void unrelatedSongMustNotPretendToHaveTrackProfile(){
        ProducerPipeline.Context c=ProducerPipeline.context(
            "Slim Dusty","A Song Not In The Library","","");
        assertNull(c.reviewed);
        assertNotNull(c.guide);
        assertTrue(c.reference().contains("ARTIST-LEVEL GUIDE ONLY"));
        assertFalse(c.reference().contains("CURATED SONG-SPECIFIC INTERPRETATION"));
    }
    @Test public void goodStudioBandSoundPassesSongSpecificQc(){
        ProducerPipeline.Context c=ProducerPipeline.context("Slim Dusty","Lights on the Hill","","");
        String style="Intimate 1970s Australian trucking country with a gently rolling road rhythm. "+
            "Begin with an acoustic guitar playing a clipped down-up strum and a plain two-beat electric bass. "+
            "Light brushed drums tick through the verses and a clean electric twang guitar answers "+
            "the last words of the vocal lines, with short pedal steel bends in the spaces. "+
            "Keep the singer in a weathered conversational baritone, letting the words fall naturally "+
            "behind the beat rather than belting every line. In the refrain the bass walks a little "+
            "more and the kit gives a soft snare lift, while steel and acoustic continue separate "+
            "rhythmic roles. Let a spare instrumental break carry the same road pulse. "+
            "Small live-room depth, clear dry centred voice, bass and kick, distinct supporting "+
            "instruments left and right, modest plate on steel, natural tape-era dynamics.";
        assertTrue(ProducerPipeline.quality(style,c).detail,
            ProducerPipeline.quality(style,c).pass);
    }
    @Test public void rejectsGenericCountryDespiteGoodLength(){
        ProducerPipeline.Context c=ProducerPipeline.context("Slim Dusty","Lights on the Hill","","");
        String generic="Classic Nashville country with professional live drums and a versatile "+
            "electric guitar and piano. The bass grooves along while the singer sounds warm. "+
            "In the opening, the drummer adds a fill. The verse stays fairly soft, then the chorus "+
            "becomes energetic and big, followed by a break where the musicians all improvise. "+
            "The vocal is powerful and very polished, with modern stereo mixing and a huge vocal "+
            "choir across the wide soundstage. Everything should sound modern, bright, shiny "+
            "and impressive, with lots of radio compression and a soaring anthemic finish.";
        assertFalse(ProducerPipeline.quality(generic,c).pass);
    }
    @Test public void missingMusicalDnaFieldsPreventBadSave()throws Exception{
        JSONObject partial=new JSONObject();
        partial.put("groove","rolling two-beat with natural timing");
        try{
            ProducerPipeline.inspectDna(partial);
            fail("should reject incomplete DNA");
        }catch(Exception expected){
            assertTrue(expected.getMessage().contains("musical analysis"));
        }
    }
}