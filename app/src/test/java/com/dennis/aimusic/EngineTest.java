package com.dennis.aimusic;

import org.junit.Test;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

public class EngineTest {
    @Test public void brainLearnsAndSamplesValidPitchClasses(){
        TinyMusicBrain model=new TinyMusicBrain();
        int[] seq=new int[800];for(int i=0;i<seq.length;i++)seq[i]=new int[]{0,4,7,9}[i%4];
        model.fit(Collections.singletonList(seq));assertTrue(model.isTrained());
        assertEquals(798,model.getExamples());
        for(int i=0;i<1000;i++){int n=model.sample(0,4,new Random(i));assertTrue(n>=0&&n<12);}
    }
    @Test public void renderProducesCorrectWaveFileLengthAndHeader() throws Exception {
        TinyMusicBrain model=new TinyMusicBrain();
        SongComposer.Plan plan=SongComposer.compose(model,100,10,0,0,42L);
        File f=File.createTempFile("dennis-render", ".wav");
        try {
            SongRenderer.render(plan,f,null);
            assertEquals(44+10L*SongRenderer.RATE*4,f.length());
            try(RandomAccessFile r=new RandomAccessFile(f,"r")){
                byte[] b=new byte[4];r.readFully(b);assertEquals("RIFF",new String(b,"US-ASCII"));
                r.seek(8);r.readFully(b);assertEquals("WAVE",new String(b,"US-ASCII"));
            }
        } finally {f.delete();}
    }
    @Test public void pitchAnalyzerRecognisesSimpleC4Pcm()throws Exception {
        int rate=16000,frames=rate*4;
        ByteArrayOutputStream b=new ByteArrayOutputStream();
        // Build the full valid little-endian PCM test WAV.
        b.write(new byte[]{'R','I','F','F'});write32(b,36+frames*2);
        b.write(new byte[]{'W','A','V','E','f','m','t',' '});write32(b,16);write16(b,1);write16(b,1);
        write32(b,rate);write32(b,rate*2);write16(b,2);write16(b,16);
        b.write(new byte[]{'d','a','t','a'});write32(b,frames*2);
        for(int i=0;i<frames;i++)write16(b,(int)(16000*Math.sin(2*Math.PI*261.625565*i/rate)));
        WavAnalyzer.SongProfile p=WavAnalyzer.analyse(new ByteArrayInputStream(b.toByteArray()),"synthetic C",null);
        assertTrue(p.notes.length>=10);
        int dominant=0;for(int i=1;i<12;i++)if(p.chroma[i]>p.chroma[dominant])dominant=i;
        assertEquals("C4 should peak in C pitch class",0,dominant);
    }
    private static void write16(OutputStream out,int x)throws IOException{out.write(x&255);out.write((x>>>8)&255);}
    private static void write32(OutputStream out,int x)throws IOException{for(int i=0;i<4;i++)out.write((x>>>(i*8))&255);}
}