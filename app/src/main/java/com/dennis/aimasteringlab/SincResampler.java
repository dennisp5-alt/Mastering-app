package com.dennis.aimasteringlab;
import java.io.IOException;

/** Streaming rational Blackman-windowed sinc, with latency removed and a
 * single rounded duration. Memory is independent of song length. */
final class SincResampler {
    interface Sink { void frame(double l,double r)throws IOException; }
    private final int inRate,outRate,half,capacity,phases;
    private final double[][] kernel,ring;
    private final Sink sink;
    private long index=-1,sourceFrames,outputFrames;
    private boolean finished;
    SincResampler(int inRate,int outRate,int half,Sink sink){
        if(inRate<8000||outRate<8000||half<16)throw new IllegalArgumentException("Invalid resampling parameters");
        this.inRate=inRate;this.outRate=outRate;this.half=half;this.sink=sink;
        phases=outRate/gcd(inRate,outRate);capacity=2*half+8+(inRate/outRate);
        ring=new double[2][capacity];kernel=new double[phases][2*half+1];
        double cutoff=.475*Math.min(1.0,outRate/(double)inRate);
        for(int p=0;p<phases;p++){
            double fraction=p/(double)phases,sum=0;
            for(int k=-half;k<=half;k++){
                double d=k-fraction;
                double window=Math.abs(d)>half?0:.42+.5*Math.cos(Math.PI*d/half)+.08*Math.cos(2*Math.PI*d/half);
                double h=(Math.abs(d)<1e-14?2*cutoff:Math.sin(2*Math.PI*cutoff*d)/(Math.PI*d))*window;
                kernel[p][k+half]=h;sum+=h;
            }
            for(int k=0;k<kernel[p].length;k++)kernel[p][k]/=sum;
        }
    }
    void add(double l,double r)throws IOException{
        if(finished)throw new IOException("Resampler already finished");
        if(!Double.isFinite(l)||!Double.isFinite(r))throw new IOException("Non-finite resampler input");
        sourceFrames++;push(l,r,Long.MAX_VALUE);
    }
    private void push(double l,double r,long limit)throws IOException{
        index++;int slot=(int)(index%capacity);ring[0][slot]=l;ring[1][slot]=r;
        while(outputFrames<limit){
            long position=outputFrames*inRate,centre=position/outRate;
            if(centre+half>index)break;
            int phase=(int)((position%outRate)*phases/outRate);
            double a=0,b=0;double[] h=kernel[phase];
            // One ring-index modulus per output frame, instead of one per
            // coefficient. Retains the same tap order, kernel and arithmetic,
            // so this is a strict DSP-equivalent execution optimisation.
            int start=(int)Math.max(0L,half-centre);
            int j=(int)((centre-half+start)%capacity);
            for(int k=start;k<h.length;k++){
                double c=h[k];a+=ring[0][j]*c;b+=ring[1][j]*c;
                if(++j==capacity)j=0;
            }
            sink.frame(a,b);outputFrames++;
        }
    }
    void finish()throws IOException{
        if(finished)throw new IOException("Resampler finished twice");finished=true;
        long target=Math.round(sourceFrames*(double)outRate/inRate);
        while(outputFrames<target)push(0,0,target);
    }
    long frames(){return outputFrames;}
    private static int gcd(int a,int b){while(b!=0){int c=a%b;a=b;b=c;}return a;}
}
