package com.dennis.musiccloud;

import org.junit.Test;
import org.json.JSONArray;
import org.json.JSONObject;
import static org.junit.Assert.*;

public class AceStepClientTest {
    @Test public void generationRequestMatchesOfficialAceStepSchema()throws Exception{
        JSONObject obj=AceStepClient.makeRequest("Country rock","[Verse 1]\nHello","acestep-v15-xl-turbo",104,180);
        assertEquals("Country rock",obj.getString("prompt"));
        assertEquals("acestep-v15-xl-turbo",obj.getString("model"));
        assertEquals("wav",obj.getString("audio_format"));
        assertEquals(180,obj.getInt("audio_duration"));
        assertEquals(104,obj.getInt("bpm"));
        assertEquals(8,obj.getInt("inference_steps"));
        assertTrue(obj.getBoolean("thinking"));
        assertEquals(1,obj.getInt("batch_size"));
    }
    @Test public void decodeNestedResultAndRejectUntrustedHost() throws Exception{
        JSONObject task=new JSONObject().put("status",1).put("result",
            new JSONArray().put(new JSONObject().put("file","/v1/audio?path=%2Ftmp%2Fnew.wav")).toString());
        assertEquals("/v1/audio?path=%2Ftmp%2Fnew.wav",AceStepClient.successFile(task));
        task.put("result",new JSONArray().put(new JSONObject().put("file","https://example.com/leak")).toString());
        try{AceStepClient.successFile(task);fail("Unexpected remote address accepted");}
        catch(java.io.IOException expected){}
    }
    @Test public void rejectUnencryptedApi()throws Exception {
        try{new AceStepClient("http://example.com","key");fail("Unencrypted API accepted");}
        catch(IllegalArgumentException expected){}
        new AceStepClient("https://gpu.example.com","key");
    }
    @Test public void rejectMissingLyrics()throws Exception{
        try{AceStepClient.makeRequest("Country","", "acestep-v15-xl-turbo",104,180);fail("Missing lyrics");}
        catch(IllegalArgumentException expected){}
    }
}
