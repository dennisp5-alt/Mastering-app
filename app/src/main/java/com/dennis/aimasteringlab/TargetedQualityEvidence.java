package com.dennis.aimasteringlab;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONObject;
import org.json.JSONException;

/** Independent finished-PCM measurements at source-identified events. These
 * centre-band proxies cannot establish vocal identity or artistic intent. */
final class TargetedQualityEvidence {
    static final class Result {
        int events, improvedEvents, worsenedEvents, controlWindows;
        double meanTargetReductionDb, controlDifferenceDb=Double.POSITIVE_INFINITY;
        double minimumConfidence=1;
        String error;
        boolean passed() {
            return error==null && events>0 && improvedEvents>=Math.ceil(events*.75)
                    && worsenedEvents==0 && controlWindows>=10
                    && Double.isFinite(meanTargetReductionDb) && meanTargetReductionDb>=.08
                    && Double.isFinite(controlDifferenceDb) && controlDifferenceDb<=-26;
        }
        String summary() {
            if(error!=null)return "Targeted comparison unavailable: "+error;
            return String.format(Locale.US,"Finished-audio target comparison %s: %d/%d events improved, %d worsened; mean reduction %.3f dB; control difference %.1f dB relative to signal",
                    passed()?"PASS":"REJECT",improvedEvents,events,worsenedEvents,meanTargetReductionDb,controlDifferenceDb);
        }
    }
    private static final class Window {
        final double[] before=new double[4],after=new double[4];
        double referenceEnergy,differenceEnergy;
        long frames;
    }
    private static final class Bank {
        final Biquad[] high=new Biquad[4],low=new Biquad[4];
        Bank(int rate){
            double[] lows={6500,25,1600,700},highs={10000,160,3200,1400};
            for(int i=0;i<4;i++){high[i]=Biquad.highPass(rate,lows[i]);low[i]=Biquad.lowPass(rate,highs[i]);}
        }
        void add(double mid,double[] energy){for(int i=0;i<4;i++){double v=low[i].process(high[i].process(mid));energy[i]+=v*v;}}
    }
    static Result compare(File baseline,AnalysisResult before,File candidate,AnalysisResult after,
                          ProfessionalMasteringModel.Result model)throws IOException {
        Result result=new Result();
        if(model==null){result.error="no source event map";return result;}
        if(before.wav.frames!=after.wav.frames||before.wav.sampleRate!=48000||after.wav.sampleRate!=48000
                ||before.wav.channels!=2||after.wav.channels!=2)throw new IOException("Target comparison requires aligned stereo 48 kHz files");
        double gain=Math.pow(10,(before.integratedLufs-after.integratedLufs)/20);
        if(!Double.isFinite(gain))throw new IOException("Invalid target comparison level match");
        List<Window> windows=new ArrayList<>();Window w=new Window();Bank a=new Bank(48000),b=new Bank(48000);
        long frames=0;
        try(WavFile.Reader left=WavFile.open(new FileInputStream(baseline));WavFile.Reader right=WavFile.open(new FileInputStream(candidate))){
            if(left.info.frames!=before.wav.frames||right.info.frames!=after.wav.frames)throw new IOException("Target comparison source length changed");
            float[][] x=new float[2][4096],y=new float[2][4096];int n;
            while((n=left.readFrames(x,4096))>0){
                if(right.readFrames(y,n)!=n)throw new IOException("Truncated target comparison");
                for(int i=0;i<n;i++,frames++){
                    double l=x[0][i],r=x[1][i],cl=y[0][i]*gain,cr=y[1][i]*gain;
                    if(!Double.isFinite(l+r+cl+cr))throw new IOException("Non-finite target comparison audio");
                    a.add((l+r)*.5,w.before);b.add((cl+cr)*.5,w.after);
                    w.referenceEnergy+=(l*l+r*r)*.5;
                    w.differenceEnergy+=((l-cl)*(l-cl)+(r-cr)*(r-cr))*.5;
                    if(++w.frames==4800){windows.add(w);w=new Window();}
                }
                if(Thread.currentThread().isInterrupted())throw new IOException("Targeted comparison cancelled");
            }
        }
        if(frames!=before.wav.frames)throw new IOException("Target comparison frame count changed");
        if(w.frames>0)windows.add(w);
        boolean[] edited=new boolean[windows.size()];double sum=0,weight=0;
        for(ProfessionalMasteringModel.Event event:model.events){
            int band=band(event.kind);
            if(!event.automatic||band<0)continue;
            int start=Math.max(0,(int)Math.floor(event.startSeconds*10));
            int end=Math.min(windows.size(),(int)Math.ceil(event.endSeconds*10));
            for(int i=Math.max(0,start-2);i<Math.min(edited.length,end+2);i++)edited[i]=true;
            double[] x=new double[4],y=new double[4];
            for(int i=start;i<end;i++)for(int k=0;k<4;k++){x[k]+=windows.get(i).before[k];y[k]+=windows.get(i).after[k];}
            if(x[3]<1e-12||y[3]<1e-12||x[band]<1e-12)continue;
            double reduction=10*Math.log10(x[band]/x[3])-10*Math.log10(Math.max(1e-30,y[band]/y[3]));
            if(!Double.isFinite(reduction))throw new IOException("Invalid target-band measurement");
            result.events++;if(reduction>=.04)result.improvedEvents++;if(reduction<-.08)result.worsenedEvents++;
            result.minimumConfidence=Math.min(result.minimumConfidence,event.confidence);
            double ew=Math.max(.05,event.durationSeconds())*event.confidence;sum+=ew*reduction;weight+=ew;
        }
        double controlSignal=0,controlDelta=0;
        for(int i=0;i<windows.size();i++){
            Window q=windows.get(i);
            if(!edited[i]&&q.referenceEnergy/q.frames>1e-6){result.controlWindows++;controlSignal+=q.referenceEnergy;controlDelta+=q.differenceEnergy;}
        }
        if(weight>0)result.meanTargetReductionDb=sum/weight;
        if(controlSignal>0)result.controlDifferenceDb=10*Math.log10(Math.max(1e-30,controlDelta/controlSignal));
        return result;
    }
    private static int band(ProfessionalMasteringModel.Kind kind){
        return kind==ProfessionalMasteringModel.Kind.VOCAL_SIBILANCE?0:
            kind==ProfessionalMasteringModel.Kind.VOCAL_PLOSIVE?1:
            kind==ProfessionalMasteringModel.Kind.VOCAL_HARSH_VOWEL?2:-1;
    }
    static JSONObject json(Result r)throws JSONException{
        JSONObject j=new JSONObject();if(r==null)return j;
        j.put("verdict",r.passed()?"MEASURED_PASS":"REJECTED");j.put("events",r.events);
        j.put("improved_events",r.improvedEvents);j.put("worsened_events",r.worsenedEvents);
        j.put("mean_target_reduction_db",r.meanTargetReductionDb);
        j.put("control_difference_db",Double.isFinite(r.controlDifferenceDb)?r.controlDifferenceDb:JSONObject.NULL);
        j.put("minimum_source_confidence",r.minimumConfidence);j.put("control_windows",r.controlWindows);
        j.put("method","source-defined event bands relative to a centre-body anchor, independently measured from finished PCM; fixed LUFS matching for control differences; no vocal isolation or perceptual-quality certification");
        if(r.error!=null)j.put("error",r.error);return j;
    }
}
