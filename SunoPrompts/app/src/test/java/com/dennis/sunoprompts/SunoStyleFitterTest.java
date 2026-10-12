package com.dennis.sunoprompts;

import org.junit.Test;
import org.json.JSONObject;
import static org.junit.Assert.*;

public final class SunoStyleFitterTest {
    private ProducerPipeline.Context track(){
        return ProducerPipeline.context("Slim Dusty","Lights on the Hill","","");
    }
    private JSONObject dna()throws Exception {
        return new JSONObject()
            .put("groove","A gently rolling Australian road-country two-beat with soft brushed drum and snare accents.")
            .put("musicians","Acoustic guitar strums an even down-up pulse; bass walks the road beat while clean electric twang guitar and pedal steel answer the singer.")
            .put("vocal","Natural weathered male baritone with conversational storytelling and held vowels at phrase endings.")
            .put("arrangement","Opening sparse acoustic, verses leave gaps after lines, refrain lifts softly, spare steel-guitar break then an unhurried outro.")
            .put("mix","Small live room, dry centred lead vocal, bass and kick with steel right and acoustic left, restrained tape-era dynamic contrast.")
            .put("distinctive_details","Trucking road groove, no anthemic choir or modern pop-country gloss.")
            .put("evidence_limits","Curated musical interpretation, not source audio measurement");
    }
    private String originalGoodStyle(){
        return "Classic Australian truck-driving country ballad, gently rolling road rhythm. "+
            "Opening acoustic guitar down-up strum and plain bass two-beat establish the journey. "+
            "Light brushed drums and soft snare keep steady behind the singer. "+
            "In the verses, clean electric twang guitar and pedal steel answer the vocal line endings "+
            "without playing over the words. Weathered Australian baritone with relaxed, "+
            "conversational storyteller phrasing and natural breaths, never forced into a huge chorus. "+
            "The refrain lifts with a slightly fuller bass pulse and sympathetic steel swells. "+
            "A brief instrumental break gives the guitar and steel room to trade short fills. "+
            "Small live studio sound: centred voice, bass and kick, balanced left/right acoustic and steel, "+
            "clear soft transients, organic tape-era warmth and uncompressed dynamic contrast.";
    }
    @Test public void overlongValidOutputIsCondensedLocallyWithoutLosingKeyMusic()throws Exception{
        String source=originalGoodStyle()+
            " This recording should be absolutely immersive, radio-ready, polished and perfect. "+
            " Very modern spectacular production makes the sound wide, huge and completely professional. "+
            " The song is expressive, impressive and emotional in every possible way.";
        assertTrue("test must represent real >1000 error",source.length()>1000);
        SunoStyleFitter.Result result=SunoStyleFitter.fit(source,dna(),track());
        assertTrue(result.condensed());
        assertTrue(result.style.length()<=1000);
        assertTrue(result.style.length()>=430);
        assertTrue(ProducerPipeline.quality(result.style,track()).detail,
            ProducerPipeline.quality(result.style,track()).pass);
        assertTrue(result.style.toLowerCase().contains("rolling"));
        assertTrue(result.style.toLowerCase().contains("acoustic"));
        assertTrue(result.style.toLowerCase().contains("steel"));
        assertTrue(result.style.toLowerCase().contains("vocal") ||
            result.style.toLowerCase().contains("baritone"));
    }
    @Test public void validWithinLimitStaysUnchanged()throws Exception{
        String original=originalGoodStyle();
        assertTrue(original.length()<=1000);
        SunoStyleFitter.Result result=SunoStyleFitter.fit(original,dna(),track());
        assertEquals("unchanged",result.method);
        assertEquals(original,result.style);
    }
    @Test public void hugeUnbrokenParagraphUsesFirstPassDnaWithoutExtraCall()throws Exception{
        String invalid="Generic professional studio perfect ".repeat(55);
        assertTrue(invalid.length()>1000);
        SunoStyleFitter.Result result=SunoStyleFitter.fit(invalid,dna(),track());
        assertTrue(result.style.length()<=1000);
        assertEquals("reconstructed from first-pass musician DNA",result.method);
        assertTrue(ProducerPipeline.quality(result.style,track()).detail,
            ProducerPipeline.quality(result.style,track()).pass);
        assertTrue(result.style.toLowerCase().contains("country"));
        assertTrue(result.style.toLowerCase().contains("rolling"));
        assertTrue(result.style.toLowerCase().contains("steel"));
    }
    @Test public void cannotSilentlyRepairGenrelessEmptyMusicDna()throws Exception{
        ProducerPipeline.Context other=ProducerPipeline.context("Unknown Musician","Unknown Song","","");
        String huge="Professional studio audio with polished quality. ".repeat(32);
        try {
            SunoStyleFitter.Result fitted=SunoStyleFitter.fit(huge,new JSONObject(),other);
            assertFalse("Do not treat a generic paragraph as passing song QC",
                ProducerPipeline.quality(fitted.style,other).pass);
        }catch(Exception validRejection){
            assertNotNull(validRejection.getMessage());
        }
    }
}