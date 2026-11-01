package com.dennis.aimasteringlab;
import java.io.IOException;
import java.util.*;

/** Independent 25 ms context veto and frequency tracking for 100 ms event
 * proposals. These remain conservative stereo inferences, never vocal isolation. */
final class MusicalEventGuard {
    static final double STEP=.025;
    static final double[] HZ={6600,7300,8100,9000,9900,40,55,75,105,145,1700,2000,2350,2750,3200};
    private static final class Window {
        final double[] band=new double[15];double body,sideBody,power,peak;int n;
        double crest(){return 20*Math.log10((peak+1e-20)/Math.sqrt(power/Math.max(1,n)+1e-20));}
    }
    static void refine(AudioAnalyzer.StreamProvider source,AnalysisResult a,ProfessionalMasteringModel.Result model,AudioAnalyzer.Progress progress)throws IOException{
        boolean needed=false;for(ProfessionalMasteringModel.Event e:model.events)if(e.automatic&&offset(e.kind)>=0)needed=true;
        if(!needed)return;
        ArrayList<Window> windows=new ArrayList<>();Biquad[] bands=new Biquad[15];
        for(int i=0;i<15;i++)bands[i]=Biquad.bandPass(48000,HZ[i],2.8);
        Biquad body=Biquad.bandPass(48000,1000,.8),sideBody=Biquad.bandPass(48000,1000,.8);
        Window w=new Window();long done=0;
        try(WavFile.Reader reader=WavFile.open(source.open())){
            float[][] x=new float[2][4096];int n;
            while((n=reader.readFrames(x,4096))>0){
                for(int i=0;i<n;i++){
                    double l=x[0][i],r=x[1][i],m=(l+r)*.5,s=(l-r)*.5;
                    if(!Double.isFinite(l+r))throw new IOException("Non-finite musical context");
                    for(int k=0;k<15;k++){double v=bands[k].process(m);w.band[k]+=v*v;}
                    double v=body.process(m),z=sideBody.process(s);w.body+=v*v;w.sideBody+=z*z;
                    w.power+=(l*l+r*r)*.5;w.peak=Math.max(w.peak,Math.max(Math.abs(l),Math.abs(r)));
                    if(++w.n==1200){windows.add(w);w=new Window();}
                }
                done+=n;progress.update("Checking musical context and tracking resonances",(int)(done*100/a.wav.frames));
                if(Thread.currentThread().isInterrupted())throw new IOException("Musical context cancelled");
            }
        }
        if(w.n>0)windows.add(w);
        int rejected=0,accepted=0;
        for(ProfessionalMasteringModel.Event e:model.events){
            int offset=offset(e.kind);if(!e.automatic||offset<0)continue;e.contextChecked=true;
            int first=Math.max(1,(int)Math.floor(e.startSeconds/STEP)),last=Math.min(windows.size()-1,(int)Math.ceil(e.endSeconds/STEP));
            if(last-first<3){reject(e,"insufficient fine-window support");rejected++;continue;}
            double bodySum=0,sideSum=0,early=0,late=0,crest=0,preBody=0,prePower=0,maxPower=0;
            for(int i=first;i<last;i++){
                Window q=windows.get(i);bodySum+=q.body;sideSum+=q.sideBody;crest=Math.max(crest,q.crest());maxPower=Math.max(maxPower,q.power);
                double target=target(q,offset);if(i<first+(last-first)/3)early+=target;if(i>=last-(last-first)/3)late+=target;
            }
            for(int i=Math.max(0,first-4);i<first;i++){preBody+=windows.get(i).body;prePower+=windows.get(i).power;}
            preBody/=Math.min(4,first);prePower/=Math.min(4,first);
            double centreDb=db(bodySum,sideSum),bodyRise=db(bodySum/(last-first),preBody),onset=db(maxPower,prePower);
            boolean fastDecay=late<early*.35,percussive=fastDecay&&onset>5&&(crest>11||bodyRise>4);
            // Repeated sharp bursts are ambiguous drum/instrument evidence.
            int repeats=0,lastHit=-100;double eventPeak=0;for(int i=first;i<last;i++)eventPeak=Math.max(eventPeak,target(windows.get(i),offset));
            if(onset>4){for(int i=2;i+3<windows.size();i++){
                if(i>=first-12&&i<=last+12)continue;Window q=windows.get(i);
                if(i-lastHit>8&&target(q,offset)>.55*eventPeak&&db(q.power,windows.get(i-2).power)>4
                        &&target(windows.get(i+3),offset)<target(q,offset)*.5){repeats++;lastHit=i;}
            }}
            if(centreDb<3||bodySum/(last-first)<1e-7){reject(e,"weak or side-dominated continuing centre-body evidence");rejected++;continue;}
            if(percussive||repeats>=3){reject(e,percussive?"fast-decaying broadband/percussive attack":"repeated sharp instrument-like bursts");rejected++;continue;}
            double[] baseline=new double[5];
            for(int k=0;k<5;k++){
                ArrayList<Double> values=new ArrayList<>();
                for(int i=Math.max(0,first-32);i<Math.min(windows.size(),last+32);i++){
                    if(i>=first-4&&i<last+4)continue;Window q=windows.get(i);
                    if(q.body>1e-7&&q.power/q.n>1e-6)values.add(db(q.band[offset+k],q.body));
                }
                if(values.size()<6){reject(e,"insufficient neighboring musical context");break;}
                Collections.sort(values);baseline[k]=values.get(values.size()/2);
            }
            if(!e.automatic){rejected++;continue;}
            e.trackingFrequency=new double[last-first];e.trackingStrength=new double[last-first];double sumHz=0,sumWeight=0;
            for(int i=first;i<last;i++){
                Window q=windows.get(i);int best=0;double bestExcess=-300;
                for(int k=0;k<5;k++){double excess=db(q.band[offset+k],q.body)-baseline[k];if(excess>bestExcess){best=k;bestExcess=excess;}}
                double hz=HZ[offset+best],strength=Math.max(0,Math.min(1,(bestExcess-1.5)/6));
                if(i>first){double previous=e.trackingFrequency[i-first-1];hz=Math.max(previous/1.12246,Math.min(previous*1.12246,hz));}
                e.trackingFrequency[i-first]=hz;e.trackingStrength[i-first]=strength;sumHz+=hz*strength;sumWeight+=strength;
            }
            if(sumWeight<2){reject(e,"no sustained narrow excess against adjacent musical context");rejected++;continue;}
            e.trackedFrequencyHz=sumHz/sumWeight;e.trackedQ=offset==5?1.15:offset==10?2.2:3.0;
            e.contextPassed=true;e.contextReason=String.format(Locale.US,"25 ms context PASS; centre/body %.1f dB; onset %.1f dB; tracked %.0f Hz Q %.2f",centreDb,onset,e.trackedFrequencyHz,e.trackedQ);
            e.evidence+="; "+e.contextReason;accepted++;
        }
        model.safetyNotes.add("Fine musical context: "+accepted+" event(s) retained, "+rejected+" vetoed. Frequency tracking is signal-based; no source separation or calibrated vocal-identification probability.");
    }
    private static double target(Window w,int offset){double s=0;for(int k=0;k<5;k++)s+=w.band[offset+k];return s;}
    private static double db(double a,double b){return 10*Math.log10((a+1e-20)/(b+1e-20));}
    private static void reject(ProfessionalMasteringModel.Event e,String reason){e.automatic=false;e.contextPassed=false;e.contextReason=reason;e.evidence+="; context veto: "+reason;}
    static int offset(ProfessionalMasteringModel.Kind k){return k==ProfessionalMasteringModel.Kind.VOCAL_SIBILANCE?0:k==ProfessionalMasteringModel.Kind.VOCAL_PLOSIVE?5:k==ProfessionalMasteringModel.Kind.VOCAL_HARSH_VOWEL?10:-1;}
}
