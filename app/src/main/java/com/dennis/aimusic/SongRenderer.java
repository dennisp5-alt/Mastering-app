package com.dennis.aimusic;

import java.io.*;

/** Tiny deterministic synthesiser. Not a generative-audio neural network or a professional mix engine. */
public final class SongRenderer {
    private SongRenderer(){}
    public interface Progress {void report(int percent);}
    public static final int RATE=22050;
    private static final int TABLE_BITS=14, TABLE_LEN=1<<TABLE_BITS;
    private static final double[] SINE=new double[TABLE_LEN];
    private static final double[] FREQ=new double[128];
    static {
        for(int i=0;i<TABLE_LEN;i++)SINE[i]=Math.sin(i*2*Math.PI/TABLE_LEN);
        for(int i=0;i<FREQ.length;i++)FREQ[i]=440*Math.pow(2,(i-69)/12.);
    }
    private static double sine(double cycles) {
        double wrapped=cycles-Math.floor(cycles);
        return SINE[(int)(wrapped*TABLE_LEN)&(TABLE_LEN-1)];
    }
    private static double triangle(double cycles) {
        double f=cycles-Math.floor(cycles);
        return 4*Math.abs(f-.5)-1;
    }
    private static double smooth(double x){return x*x*(3-2*x);}
    private static double clip(double x){return x/(1+Math.abs(x)*.65);}
    private static int noise(long frame,long seed){
        long x=frame*0x9e3779b97f4a7c15L+seed;
        x^=(x>>>30);x*=0xbf58476d1ce4e5b9L;x^=(x>>>27);x*=0x94d049bb133111ebL;x^=(x>>>31);
        return (int)(x>>>33);
    }
    private static void u16(OutputStream out,int v)throws IOException{out.write(v&255);out.write((v>>>8)&255);}
    private static void u32(OutputStream out,long v)throws IOException{for(int i=0;i<4;i++)out.write((int)(v>>>(i*8))&255);}
    private static void header(OutputStream out,int seconds)throws IOException{
        long dataSize=(long)seconds*RATE*4;
        out.write(new byte[]{'R','I','F','F'});u32(out,dataSize+36);
        out.write(new byte[]{'W','A','V','E','f','m','t',' '});u32(out,16);u16(out,1);u16(out,2);
        u32(out,RATE);u32(out,RATE*4);u16(out,4);u16(out,16);
        out.write(new byte[]{'d','a','t','a'});u32(out,dataSize);
    }
    public static void render(SongComposer.Plan plan,File file,Progress callback)throws IOException {
        File parent=file.getParentFile();if(parent!=null&&!parent.exists()&&!parent.mkdirs())throw new IOException("No output folder");
        long total=(long)plan.seconds*RATE;
        double beatSeconds=60.0/plan.bpm;
        double framesPerBeat=RATE*beatSeconds;
        int delayLength=(int)(0.19*RATE);
        float[] delayLeft=new float[delayLength],delayRight=new float[delayLength];
        int delayIndex=0;
        byte[] output=new byte[32768];int used=0;
        try(BufferedOutputStream out=new BufferedOutputStream(new FileOutputStream(file),32768)) {
            header(out,plan.seconds);
            for(long frame=0;frame<total;frame++) {
                double t=frame/(double)RATE;
                long beatIndex=(long)(frame/framesPerBeat);
                double beatPhase=frame/framesPerBeat-beatIndex;
                int bar=(int)Math.min(plan.chordRoots.length-1,beatIndex/4);
                int section=plan.sections[bar];
                boolean introOrOutro=section==0||section==4;
                double barPhase=((beatIndex%4)+beatPhase)/4;
                int base=48+plan.chordRoots[bar];
                // Sustained simple harmony; basic triads deliberately do not copy the input audio.
                double padFade=Math.min(1,barPhase*9)*Math.min(1,(1-barPhase)*13);
                double pad=.19*padFade*(sine(FREQ[base]*t)+.72*sine(FREQ[base+4]*t)+.64*sine(FREQ[base+7]*t))/2.36;
                // Simple electric bass playing roots, with a fixed beat-length decay.
                double bassAmp=.30*Math.pow(1-beatPhase,1.6);
                double bass=bassAmp*(.75*triangle(FREQ[base-12]*t)+.25*sine(FREQ[base-12]*2*t));
                if(introOrOutro)bass*=.65;
                // Composition model supplies successive pitch classes; audible short plucked lead.
                int step=(int)Math.min(plan.noteClasses.length-1,Math.floor(frame/(framesPerBeat/2)));
                double stepPhase=frame/(framesPerBeat/2)-step;
                int melodyMidi=60+plan.noteClasses[step];
                double pluckAmp=Math.pow(1-stepPhase,2.0)*Math.min(1,stepPhase*26);
                double lead=.21*pluckAmp*(.85*sine(FREQ[melodyMidi]*t)+.12*sine(FREQ[melodyMidi]*2*t)+.08*sine(FREQ[melodyMidi]*3*t));
                if(introOrOutro)lead*=.48;
                // Rhythm patterns selected per style. Deterministic noise-based hi-hat/snare.
                int beatsInBar=(int)(beatIndex%4);
                double kick=0,snare=0,hat=0;
                if(!introOrOutro || section==4) {
                    boolean fourOnFloor=plan.style==2;
                    boolean kickEvent=(fourOnFloor|| beatsInBar==0||beatsInBar==2);
                    if(kickEvent&&beatPhase<.28) {
                        double k=beatPhase/.28;
                        kick=.34*(1-k)*(1-k)*sine((75-30*k)*t);
                    }
                    if((beatsInBar==1||beatsInBar==3)&&beatPhase<.20){
                        double s=beatPhase/.20;
                        double n=noise(frame,plan.seed)/1073741824.0-1;
                        snare=.18*(1-s)*(1-s)*n;
                    }
                    double hphase=(beatPhase*2)%1;
                    if(hphase<.20) {
                        double n=noise(frame,plan.seed^0x289af3L)/1073741824.0-1;
                        hat=.065*(1-hphase/.20)*n;
                    }
                    if(plan.style==1){kick*=1.10;lead*=1.1;pad*=.7;}
                    if(plan.style==3){kick*=.78;snare*=.75;pad*=1.18;}
                    if(plan.style==0){kick*=.72;snare*=.72;hat*=.55;}
                }
                double dry=pad+bass+lead+kick+snare+hat;
                // Low-cost cross-feedback echo for stereo dimension; bounded feedback.
                float pastL=delayLeft[delayIndex],pastR=delayRight[delayIndex];
                double l=clip(dry+.15*pastL+.06*pastR);
                double r=clip(pad*.93+bass+lead*.90+kick+snare+hat+.16*pastR+.05*pastL);
                delayLeft[delayIndex]=(float)(lead*.55+pad*.22+pastR*.21);
                delayRight[delayIndex]=(float)(lead*.47+pad*.27+pastL*.21);
                if(++delayIndex==delayLength)delayIndex=0;
                // Fade in/out to eliminate hard boundaries.
                double fade=Math.min(1,Math.min(frame/(double)(RATE*2), (total-1-frame)/(double)(RATE*3)));
                fade=smooth(Math.max(0,fade));
                int a=(int)Math.round(Math.max(-1,Math.min(1,l*fade)) * 32760);
                int b=(int)Math.round(Math.max(-1,Math.min(1,r*fade)) * 32760);
                output[used++]=(byte)a;output[used++]=(byte)(a>>>8);
                output[used++]=(byte)b;output[used++]=(byte)(b>>>8);
                if(used==output.length){out.write(output);used=0;}
                if(callback!=null && frame>0 && frame%(RATE*3)==0)callback.report((int)(100*frame/total));
            }
            if(used>0)out.write(output,0,used);
        } catch(IOException|RuntimeException ex) {
            if(file.exists())file.delete();throw ex;
        }
        if(callback!=null)callback.report(100);
    }
}