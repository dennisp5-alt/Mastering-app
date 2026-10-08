package com.dennis.aimusic;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;

/** Lightweight offline WAV feature extractor. Spectral peaks on mastered mixes are proxies,
 * not ground-truth melodies. No recording audio is stored in the learned library.
 */
public final class WavAnalyzer {
    private WavAnalyzer() {}
    public interface Progress { void report(int percentage); }
    public static final class SongProfile {
        public final String name;
        public final int[] notes;
        public final float[] chroma;
        public SongProfile(String name, int[] notes, float[] chroma) {
            this.name = name; this.notes = notes; this.chroma = chroma;
        }
    }
    private static int read16(byte[] a,int p) { return (a[p]&255) | ((a[p+1]&255)<<8); }
    private static long read32(byte[] a,int p) {
        return ((long)a[p]&255) | (((long)a[p+1]&255)<<8) | (((long)a[p+2]&255)<<16) | (((long)a[p+3]&255)<<24);
    }
    private static void fully(InputStream in,byte[] b,int count) throws IOException {
        int n=0;while(n<count){int r=in.read(b,n,count-n);if(r<0)throw new EOFException("Unexpected end of WAV");if(r==0)continue;n+=r;}
    }
    private static void skip(InputStream in,long count) throws IOException {
        while(count>0) {long n=in.skip(count);if(n<=0){if(in.read()<0)throw new EOFException("Truncated WAV chunk");n=1;}count-=n;}
    }
    public static SongProfile analyse(InputStream raw,String name,Progress progress) throws IOException {
        BufferedInputStream in = new BufferedInputStream(raw,128*1024);
        byte[] head = new byte[12]; fully(in,head,12);
        if(!textIs(head,0,"RIFF") || !textIs(head,8,"WAVE"))
            throw new IOException("Requires standard RIFF WAV (convert other formats to PCM WAV)");
        int format=0,channels=0,rate=0,bits=0,blockAlign=0;
        long dataLength=-1;
        byte[] chunk=new byte[8];
        while(dataLength<0) {
            try { fully(in,chunk,8); } catch (EOFException ex) {throw new IOException("No audio data in WAV",ex);}
            long size=read32(chunk,4);if(size>1024L*1024*1024)throw new IOException("WAV chunk exceeds 1 GB limit");
            if(textIs(chunk,0,"fmt ")) {
                if(size<16||size>8192)throw new IOException("Unsupported WAV fmt chunk");
                byte[] fmt=new byte[(int)size];fully(in,fmt,(int)size);
                format=read16(fmt,0);channels=read16(fmt,2);rate=(int)read32(fmt,4);
                blockAlign=read16(fmt,12);bits=read16(fmt,14);
                if(format==65534 && size>=40) format=read16(fmt,24); // WAVE_FORMAT_EXTENSIBLE PCM/float GUID low word
            } else if(textIs(chunk,0,"data")) {
                if(rate==0)throw new IOException("WAV has data before fmt chunk");
                dataLength=size;
            } else skip(in,size);
            if(dataLength<0 && (size&1)==1)skip(in,1);
        }
        if((format!=1&&format!=3)|| (format==3&&bits!=32) ||
           (format==1&&bits!=16&&bits!=24&&bits!=32) ||
           channels<1||channels>8||rate<8000||rate>192000 ||
           blockAlign<channels*(bits/8)) {
            throw new IOException("Unsupported WAV encoding (use 16/24-bit PCM WAV)");
        }
        final int framesPerBlock=Math.max(1024,rate/5); // 0.2-second feature windows
        byte[] audio=new byte[framesPerBlock*blockAlign];
        final int totalBlocks=(int)Math.min(20000,dataLength/Math.max(1,audio.length));
        ArrayList<Integer> notes=new ArrayList<>();
        double[] totals=new double[12];
        int previous=-1;
        long remaining=dataLength;
        int blocks=0;
        while(remaining>=1024L*blockAlign && blocks<20000) {
            int frames=(int)Math.min(framesPerBlock,remaining/blockAlign);
            int bytes=frames*blockAlign;
            fully(in,audio,bytes);remaining-=bytes;
            // Evenly sample the frame; no retained song data.
            double[] mono=new double[1024];double energy=0;
            for(int i=0;i<1024;i++) {
                int off=(int)((long)i*frames/1024)*blockAlign;
                double v=0;
                for(int ch=0;ch<channels;ch++) {
                    int at=off+ch*(bits/8);
                    if(format==3) {
                        v+=Float.intBitsToFloat((int)read32(audio,at));
                    } else if(bits==16) {
                        int x=read16(audio,at);v+=(short)x/32768.;
                    } else if(bits==24) {
                        int x=(audio[at]&255)|((audio[at+1]&255)<<8)|(audio[at+2]<<16);
                        v+=x/8388608.;
                    } else v+=(int)read32(audio,at)/2147483648.;
                }
                v/=channels;
                mono[i]=Math.max(-1,Math.min(1,v));energy+=v*v;
            }
            if(energy>0.0000001) {
                double[] chroma=new double[12];
                // Goertzel analysis over 2 octaves: approx C3 (130.81 Hz) -> B4.
                double sampleRate=1024.0*rate/frames;
                for(int midi=48;midi<72;midi++) {
                    double freq=440*Math.pow(2,(midi-69)/12.);
                    double w=2*Math.PI*freq/sampleRate;
                    double coeff=2*Math.cos(w),s0=0,s1=0,s2=0;
                    for(int i=0;i<1024;i++) {
                        double taper=0.5-0.5*Math.cos(2*Math.PI*i/1023);
                        s0=mono[i]*taper+coeff*s1-s2;s2=s1;s1=s0;
                    }
                    double power=s1*s1+s2*s2-coeff*s1*s2;
                    chroma[midi%12]+=Math.max(0,power);
                }
                int best=0;for(int k=1;k<12;k++)if(chroma[k]>chroma[best])best=k;
                double sum=0;for(double p:chroma)sum+=p;
                if(sum>0)for(int k=0;k<12;k++)totals[k]+=chroma[k]/sum;
                // Suppress repeated identical spectral evidence while preserving sequential flow.
                if(best!=previous || blocks%4==0) {notes.add(best);previous=best;}
            }
            blocks++;
            if(progress!=null && blocks%16==0) progress.report(Math.min(99,(int)(100.0*blocks/Math.max(1,totalBlocks))));
        }
        if(notes.size()<10)throw new IOException("Insufficient musical content in this recording");
        int[] seq=new int[notes.size()];for(int i=0;i<seq.length;i++)seq[i]=notes.get(i);
        double sum=0;for(double v:totals)sum+=v;
        float[] dist=new float[12]; for(int i=0;i<12;i++)dist[i]=(float)(sum>0?totals[i]/sum:1./12.);
        if(progress!=null)progress.report(100);
        return new SongProfile(name,seq,dist);
    }
    private static boolean textIs(byte[] b,int i,String s) {
        for(int x=0;x<s.length();x++)if(b[i+x]!=(byte)s.charAt(x))return false;
        return true;
    }
    /** Only approximate tonal centre: chord tones frequently dominate commercial stereo recordings. */
    public static int estimatedRoot(java.util.List<SongProfile> profiles) {
        if(profiles.isEmpty())return 0;
        double[] histogram=new double[12];
        for(SongProfile p:profiles) for(int i=0;i<12;i++)histogram[i]+=p.chroma[i];
        int key=0;double best=-Double.MAX_VALUE;
        for(int k=0;k<12;k++) {
            double score=1.4*histogram[k]+1.0*histogram[(k+7)%12]
                +0.9*histogram[(k+4)%12]+0.65*histogram[(k+9)%12];
            if(score>best){best=score;key=k;}
        }
        return key;
    }
}