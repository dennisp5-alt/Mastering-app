package com.dennis.aimasteringlab;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Random;

/** Candidate-fast-path invariants; independent of subjective sound scoring. */
public final class V0149CandidateEfficiencyHarness {
    private static void check(boolean b, String why) {
        if (!b) throw new AssertionError(why);
    }

    private static byte[] fixture(int bits, boolean floating, int channels, int frames) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int sampleBytes=bits/8;
        int bytes=sampleBytes*channels*frames;
        LittleEndian.writeAscii(out,"RIFF");LittleEndian.writeU32(out,36+bytes);
        LittleEndian.writeAscii(out,"WAVE");LittleEndian.writeAscii(out,"fmt ");
        LittleEndian.writeU32(out,16);LittleEndian.writeU16(out,floating?3:1);
        LittleEndian.writeU16(out,channels);LittleEndian.writeU32(out,48000);
        LittleEndian.writeU32(out,48000*channels*sampleBytes);
        LittleEndian.writeU16(out,channels*sampleBytes);LittleEndian.writeU16(out,bits);
        LittleEndian.writeAscii(out,"data");LittleEndian.writeU32(out,bytes);
        Random random=new Random(0x149AEDEEL + bits + channels);
        byte[] audio = new byte[bytes];random.nextBytes(audio);
        if (floating) {
            // Ensure finite, real-valued 32-bit IEEE samples for independent reference checks.
            for (int p=0;p<audio.length;p+=4) {
                int b=Float.floatToRawIntBits((random.nextFloat()-0.5f)*2f);
                audio[p]=(byte)b;audio[p+1]=(byte)(b>>>8);
                audio[p+2]=(byte)(b>>>16);audio[p+3]=(byte)(b>>>24);
            }
        }
        out.write(audio);return out.toByteArray();
    }

    private static void verify(int bits, boolean floating, int channels) throws Exception {
        byte[] wav=fixture(bits,floating,channels,65537);
        int stride=bits/8;
        int p=44;
        try (WavFile.Reader reader=WavFile.open(new ByteArrayInputStream(wav))) {
            check(reader.info.frames==65537,"frame count");
            float[][] frame=new float[channels][4096];
            int n;int sampleNo=0;
            while ((n=reader.readFrames(frame,4096))>0) {
                for (int f=0;f<n;f++)for(int c=0;c<channels;c++){
                    float expect;
                    if(floating)expect=Float.intBitsToFloat(LittleEndian.s32(wav,p));
                    else if(bits==16)expect=(short)LittleEndian.u16(wav,p)/32768.0f;
                    else if(bits==24)expect=LittleEndian.s24(wav,p)/8388608.0f;
                    else expect=LittleEndian.s32(wav,p)/2147483648.0f;
                    check(Float.floatToRawIntBits(expect)==Float.floatToRawIntBits(frame[c][f]),
                        "sample altered at "+sampleNo+", bits "+bits+", channel "+c);
                    p+=stride;sampleNo++;
                }
            }
            check(sampleNo==65537*channels,"all samples read");
        }
    }

    public static void main(String[] ignored) throws Exception {
        verify(16,false,2);verify(24,false,2);verify(32,false,2);
        verify(32,true,2);verify(24,false,1);
        AnalysisResult a=new AnalysisResult();
        a.limiterRequired=true;a.limiterAllowed=true;a.truePeakEstimateDbtp=-3.0;
        check(!MasterRenderer.limiterWillEngage(a,0.0,false),"flat gain must bypass limiter");
        check(MasterRenderer.limiterWillEngage(a,2.0,false),"peak pressure must engage limiter");
        check(MasterRenderer.limiterWillEngage(a,0.0,true),"force limiter cannot bypass");
        a.limiterAllowed=false;
        check(!MasterRenderer.limiterWillEngage(a,2.0,false),"limiter not allowed");
        System.out.println("V0149_CANDIDATE_EFFICIENCY_PASS exact PCM samples 16/24/32/float/mono and limiter predicates");
    }
}
