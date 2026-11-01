package com.dennis.aimasteringlab;
import java.io.IOException;

/** Four-times processing with anti-imaging/anti-alias filters and aligned PCM. */
final class OversampledLimiter {
    final LookaheadLimiter limiter;
    private final SincResampler up,down;
    private final LookaheadLimiter.Output limited;
    private long frames;
    OversampledLimiter(int rate,double ceiling,double lookahead,double release,LookaheadLimiter.Output output){
        limiter=new LookaheadLimiter(rate*4,ceiling,lookahead,release,true);
        down=new SincResampler(rate*4,rate,192,output::frame);limited=down::add;
        up=new SincResampler(rate,rate*4,48,(l,r)->limiter.add(l,r,limited));
    }
    void add(double l,double r)throws IOException{frames++;up.add(l,r);}
    void finish()throws IOException{
        up.finish();limiter.finish(limited);down.finish();
        if(up.frames()!=frames*4||down.frames()!=frames)throw new IOException("Oversampled limiter frame count changed");
    }
    long sourceFrames(){return frames;}
}
