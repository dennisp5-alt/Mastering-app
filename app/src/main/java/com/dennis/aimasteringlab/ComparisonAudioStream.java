package com.dennis.aimasteringlab;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/** Both versions advance through one sample timeline; playback only, never an export renderer. */
final class ComparisonAudioStream implements Closeable {
    private final File first, second;
    final double firstGainDb, secondGainDb;
    final long frames;
    final int rate;
    private final double firstGain, secondGain;
    private WavFile.Reader a, b;
    private float[][] x=new float[2][0], y=new float[2][0];
    private long position;
    private volatile boolean alternative, mono,delta;
    private double blend, monoBlend,deltaBlend;
    private long loopFirst,loopLast=-1;

    ComparisonAudioStream(File first,AnalysisResult firstAnalysis,
                          File second,AnalysisResult secondAnalysis) throws IOException {
        if (!Double.isFinite(firstAnalysis.integratedLufs) || !Double.isFinite(secondAnalysis.integratedLufs))
            throw new IOException("A/B needs valid loudness measurements for both files");
        if (firstAnalysis.wav.frames!=secondAnalysis.wav.frames
                || firstAnalysis.wav.channels!=2 || secondAnalysis.wav.channels!=2
                || firstAnalysis.wav.sampleRate!=48000 || secondAnalysis.wav.sampleRate!=48000)
            throw new IOException("A/B files must have matching stereo sample timelines");
        this.first=first;this.second=second;frames=firstAnalysis.wav.frames;rate=48000;
        double target=Math.min(firstAnalysis.integratedLufs,secondAnalysis.integratedLufs);
        firstGainDb=target-firstAnalysis.integratedLufs;secondGainDb=target-secondAnalysis.integratedLufs;
        firstGain=Math.pow(10,firstGainDb/20);secondGain=Math.pow(10,secondGainDb/20);
        seek(0);
    }
    void choose(boolean alternative){this.alternative=alternative;}
    void mono(boolean mono){this.mono=mono;}
    void delta(boolean enabled){delta=enabled;}
    void loopBoundaries(long first,long last){loopFirst=first;loopLast=last;}
    static boolean alternativeForSlot(int slot,boolean blind,boolean swapped){return (slot==1) ^ (blind&&swapped);}
    long position(){return position;}
    void seek(long frame)throws IOException{
        if(frame<0||frame>frames)throw new IOException("A/B seek is outside the song");
        close();
        try{
            a=WavFile.open(new FileInputStream(first));b=WavFile.open(new FileInputStream(second));
            for(WavFile.Reader r:new WavFile.Reader[]{a,b})
                if(r.info.frames!=frames||r.info.channels!=2||r.info.sampleRate!=rate)
                    throw new IOException("A/B cache format or length changed");
            a.skipFrames(frame);b.skipFrames(frame);position=frame;
            blend=alternative?1:0;monoBlend=mono?1:0;
        }catch(IOException e){close();throw e;}
    }
    int read(float[] interleaved,int wanted)throws IOException{
        if(interleaved.length<wanted*2)throw new IllegalArgumentException("Playback buffer too short");
        if(x[0].length<wanted){x=new float[2][wanted];y=new float[2][wanted];}
        int n=a.readFrames(x,wanted),m=b.readFrames(y,wanted);
        if(n!=m)throw new IOException("A/B streams lost sample alignment");
        if(n<0)return n;
        if(position==0){blend=alternative?1:0;monoBlend=mono?1:0;}
        for(int i=0;i<n;i++){
            blend=approach(blend,alternative?1:0,1.0/240);monoBlend=approach(monoBlend,mono?1:0,1.0/240);
            double l=x[0][i]*firstGain*(1-blend)+y[0][i]*secondGain*blend;
            double r=x[1][i]*firstGain*(1-blend)+y[1][i]*secondGain*blend;
            deltaBlend=approach(deltaBlend,delta?1:0,1.0/240);
            // Half-scale signed difference prevents the subtraction itself overloading playback.
            l=l*(1-deltaBlend)+(y[0][i]*secondGain-x[0][i]*firstGain)*.5*deltaBlend;
            r=r*(1-deltaBlend)+(y[1][i]*secondGain-x[1][i]*firstGain)*.5*deltaBlend;
            if(!Double.isFinite(l)||!Double.isFinite(r))throw new IOException("Non-finite A/B playback audio");
            double mid=(l+r)*.5;
            double edge=loopLast>loopFirst?StudioAudio.smooth((position+i-loopFirst)/240.0)*StudioAudio.smooth((loopLast-1-position-i)/240.0):1;
            interleaved[i*2]=(float)((l*(1-monoBlend)+mid*monoBlend)*edge);
            interleaved[i*2+1]=(float)((r*(1-monoBlend)+mid*monoBlend)*edge);
        }
        position+=n;return n;
    }
    private static double approach(double value,double target,double step){return value<target?Math.min(target,value+step):Math.max(target,value-step);}
    @Override public void close()throws IOException{
        WavFile.Reader left=a,right=b;a=null;b=null;
        try{if(left!=null)left.close();}finally{if(right!=null)right.close();}
    }
}
