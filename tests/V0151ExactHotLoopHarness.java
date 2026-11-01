package com.dennis.aimasteringlab;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

/** Exact bit-level tests against v0.15.0 core DSP reference implementations. */
public final class V0151ExactHotLoopHarness {
    static void eq(double x, double y, String where) {
        if(Double.doubleToRawLongBits(x)!=Double.doubleToRawLongBits(y))
            throw new AssertionError(where + ": " + x + " != " + y);
    }
    static void require(boolean b,String s) {if(!b)throw new AssertionError(s);}
    static double sample(Random r,int i,int pattern) {
        if (pattern==0) return 0.0;
        if (pattern==1) return i%13073==0 ? 1.15 : (r.nextDouble()-0.5)*0.10;
        if (pattern==2) return i%1024<5 ? -0.98 : (r.nextDouble()-0.5)*0.95;
        return Math.sin(i*0.127)*0.21 + (r.nextDouble()-0.5)*0.71;
    }
    static void meter() {
        for(int pattern=0;pattern<4;pattern++) {
            TruePeakMeter fast=new TruePeakMeter();
            LegacyTruePeakMeter old=new LegacyTruePeakMeter();
            Random r=new Random(714151+pattern);
            for(int i=0;i<310019;i++) {
                double x=sample(r,i,pattern);
                fast.add(x);old.add(x);
                if(i%1009==0)eq(fast.peak(),old.peak(),"meter prefix/"+pattern+"/"+i);
            }
            fast.finish();old.finish();eq(fast.peak(),old.peak(),"meter final/"+pattern);
        }
    }
    static class Collector {
        final ArrayList<Long> left=new ArrayList<>(),right=new ArrayList<>();
        void frame(double l,double r) {left.add(Double.doubleToRawLongBits(l));right.add(Double.doubleToRawLongBits(r));}
        void compare(Collector other,String label){
            require(left.size()==other.left.size(),label+" output frame count");
            for(int i=0;i<left.size();i++) {
                require(left.get(i).equals(other.left.get(i)),label+" L frame "+i);
                require(right.get(i).equals(other.right.get(i)),label+" R frame "+i);
            }
        }
    }
    static void limiter(int sampleRate,double lookahead,double release,boolean ramped,int pattern) throws IOException {
        LookaheadLimiter fast=new LookaheadLimiter(sampleRate,-1.2,lookahead,release,ramped);
        LegacyLookaheadLimiter old=new LegacyLookaheadLimiter(sampleRate,-1.2,lookahead,release,ramped);
        Collector f=new Collector(),o=new Collector();
        LookaheadLimiter.Output fOut=f::frame;
        LegacyLookaheadLimiter.Output oOut=o::frame;
        Random rnd=new Random(714515+pattern);
        int n=180019;
        for(int i=0;i<n;i++) {
            double l=sample(rnd,i,pattern);
            double r=sample(rnd,i+17,pattern);
            fast.add(l,r,fOut);old.add(l,r,oOut);
        }
        fast.finish(fOut);old.finish(oOut);
        f.compare(o,pattern+"/"+sampleRate+"/"+ramped);
        require(fast.sourceFrames()==old.sourceFrames(),"source frames");
        eq(fast.maximumReductionDb(),old.maximumReductionDb(),"max reduction");
        eq(fast.averageReductionDb(),old.averageReductionDb(),"average reduction");
        eq(fast.maximumGainStepDb(),old.maximumGainStepDb(),"gain step");
        eq(fast.percentAboveOneDb(),old.percentAboveOneDb(),"frames >1 dB");
        eq(fast.percentAboveThreeDb(),old.percentAboveThreeDb(),"frames >3 dB");
    }
    public static void main(String[] args) throws Exception {
        meter();
        for(int pattern=0;pattern<4;pattern++) {
            limiter(48000,3.0,90,false,pattern);
            limiter(48000,5.0,150,false,pattern);
            limiter(48000,6.0,110,true,pattern);
            limiter(192000,6.0,110,true,pattern);
        }
        System.out.println("V0151_EXACT_HOT_LOOP_PASS: peak-meter sample-bit equality; limiter PCM and all metrics bit-equal at 48/192 kHz across silence, sparse impulse, transients, dense waveform");
    }
}
