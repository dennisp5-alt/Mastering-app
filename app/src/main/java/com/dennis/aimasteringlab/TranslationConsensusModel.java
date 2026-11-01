package com.dennis.aimasteringlab;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Measured agreement across the same 12 translation proxies used by final QC. */
final class TranslationConsensusModel {
    enum Kind { BASS_BLOOM, VOCAL_MASKING, HARSHNESS, PHONE_ONLY_DIAGNOSTIC }

    static final class Event {
        Kind kind; double startSeconds,endSeconds,lowHz,highHz,attenuationDb;
        int familyCount,deviceCount; double confidence; boolean automatic;
        String familyEvidence,deviceEvidence,evidence;
    }

    static final class Result {
        String model="DAML v0.9.1 diagnostic translation consensus";
        String method="the same 12 response simulations used by translation QC are measured in 100 ms windows and grouped into home, Bluetooth, phone, headphone and car families; findings inform QC only and never generate mastering audio";
        int windows;
        int deviceProfiles=TranslationAnalyzer.PROFILES.length;
        int independentFamilies=Family.values().length;
        final List<Event> events=new ArrayList<>();
        final List<String> safetyNotes=new ArrayList<>();
        int automaticCount(){int n=0;for(Event e:events)if(e.automatic)n++;return n;}
        String compactSummary(){return events.size()+" translation finding(s), all diagnostic-only • 12 measured profiles";}
    }

    private enum Family { HOME("home"),BLUETOOTH("Bluetooth"),PHONE("phone"),HEADPHONE("headphone"),CAR("car");final String label;Family(String s){label=s;} }
    private static final double WINDOW=.100,Z_GATE=2.65,MAX_COVERAGE=.16;
    private TranslationConsensusModel(){}

    static Result analyze(AudioAnalyzer.StreamProvider source,AnalysisResult base,AudioAnalyzer.Progress progress)throws IOException{
        Result out=new Result();ArrayList<Window> windows=extract(source,base,progress);out.windows=windows.size();
        if(windows.size()<30){out.safetyNotes.add("Track too short for robust cross-device consensus; diagnostics only.");return out;}
        double[] levels=levels(windows);double floor=Math.max(percentile(levels,.15),percentile(levels,.90)-32);ArrayList<Integer> active=new ArrayList<>();
        for(int i=0;i<windows.size();i++)if(windows.get(i).level>=floor)active.add(i);if(active.size()<24)return out;
        add(out,windows,active,Kind.BASS_BLOOM,base.wav.durationSeconds);add(out,windows,active,Kind.VOCAL_MASKING,base.wav.durationSeconds);add(out,windows,active,Kind.HARSHNESS,base.wav.durationSeconds);
        out.events.sort(Comparator.comparingDouble(e->e.startSeconds));
        out.safetyNotes.add("Bass and harshness require agreement across at least three independent response families; several phones alone remain diagnostic.");
        out.safetyNotes.add("Vocal masking may qualify from at least three independent phone profiles because the action is a tiny masking cut, never bass or presence boosting.");
        out.safetyNotes.add("v0.9.1 does not render translation-driven correction candidates; these findings are post-master QC evidence only.");
        out.safetyNotes.add("The first 2 seconds and final max(8 seconds, 4% of duration) are protected.");return out;
    }

    private static void add(Result out,List<Window>windows,List<Integer>active,Kind kind,double duration){
        int devices=TranslationAnalyzer.PROFILES.length;double[]med=new double[devices],scale=new double[devices];
        for(int d=0;d<devices;d++){double[]p=new double[active.size()];for(int i=0;i<p.length;i++)p[i]=windows.get(active.get(i)).features[d][index(kind)];med[d]=percentile(p,.5);scale[d]=robustScale(p);}
        double ending=Math.max(2,duration-Math.max(8,duration*.04));double floor=percentile(activeLevels(windows,active),.12);ArrayList<Event>raw=new ArrayList<>();
        for(Window w:windows){if(w.start<2||w.start+WINDOW>ending||w.level<floor)continue;Set<Family>families=new LinkedHashSet<>();ArrayList<String>names=new ArrayList<>();double maxZ=0;int phone=0;
            for(int d=0;d<devices;d++){double z=(w.features[d][index(kind)]-med[d])/scale[d];if(z<Z_GATE)continue;maxZ=Math.max(maxZ,z);Family f=family(d);families.add(f);if(f==Family.PHONE)phone++;names.add(TranslationAnalyzer.PROFILES[d].name);}
            boolean automatic=kind==Kind.VOCAL_MASKING?((families.size()>=2&&names.size()>=3)||phone>=3):(families.size()>=3&&names.size()>=3);
            if(automatic)raw.add(event(w,kind,kind,families,names,maxZ,true));else if(names.size()>=3&&families.size()==1&&families.contains(Family.PHONE))raw.add(event(w,Kind.PHONE_ONLY_DIAGNOSTIC,kind,families,names,maxZ,false));
        }
        List<Event>merged=merge(raw);double coverage=0;for(Event e:merged)if(e.automatic)coverage+=e.endSeconds-e.startSeconds;
        if(coverage/Math.max(1,duration)>MAX_COVERAGE){Event e=new Event();e.kind=Kind.PHONE_ONLY_DIAGNOSTIC;e.startSeconds=2;e.endSeconds=ending;e.evidence=kind.name().toLowerCase(Locale.US)+" recurred too broadly for safe translation-driven mastering; diagnostic only";out.events.add(e);}else out.events.addAll(merged);
    }

    private static Event event(Window w,Kind stored,Kind measured,Set<Family>families,List<String>devices,double z,boolean automatic){
        Event e=new Event();e.kind=stored;e.startSeconds=w.start;e.endSeconds=w.start+WINDOW;
        if(measured==Kind.BASS_BLOOM){e.lowHz=35;e.highHz=180;}else if(measured==Kind.VOCAL_MASKING){e.lowHz=700;e.highHz=4200;}else{e.lowHz=3200;e.highHz=8500;}
        e.attenuationDb=automatic?-(.18+.32*clamp((z-Z_GATE)/3)):0;e.familyCount=families.size();e.deviceCount=devices.size();e.familyEvidence=joinFamilies(families);e.deviceEvidence=join(devices);e.confidence=automatic?Math.min(.935,.66+.055*families.size()+.018*devices.size()+.025*clamp(z-Z_GATE)):.62;e.automatic=automatic;
        e.evidence=automatic?String.format(Locale.US,"%d measured device profiles across %d response families (%s) agreed on %s; maximum robust-threshold excess %.3f; attenuation-only candidate",e.deviceCount,e.familyCount,e.familyEvidence,measured.name().toLowerCase(Locale.US),z):String.format(Locale.US,"%d bandwidth-limited phone profiles identified %s, but no independent non-phone family agreed; diagnostic only",e.deviceCount,measured.name().toLowerCase(Locale.US));return e;
    }

    private static List<Event>merge(List<Event>raw){if(raw.isEmpty())return raw;raw.sort(Comparator.comparingDouble(e->e.startSeconds));ArrayList<Event>out=new ArrayList<>();Event current=raw.get(0);
        for(int i=1;i<raw.size();i++){Event next=raw.get(i);if(next.startSeconds<=current.endSeconds+.051&&next.kind==current.kind&&Math.abs(next.lowHz-current.lowHz)<.001&&Math.abs(next.highHz-current.highHz)<.001){current.endSeconds=next.endSeconds;current.attenuationDb=Math.min(current.attenuationDb,next.attenuationDb);current.familyCount=Math.max(current.familyCount,next.familyCount);current.deviceCount=Math.max(current.deviceCount,next.deviceCount);current.confidence=Math.max(current.confidence,next.confidence);if(next.familyEvidence!=null&&(current.familyEvidence==null||next.familyEvidence.length()>current.familyEvidence.length())){current.familyEvidence=next.familyEvidence;current.deviceEvidence=next.deviceEvidence;current.evidence=next.evidence;}}else{if(!current.automatic||current.endSeconds-current.startSeconds>=.20)out.add(current);current=next;}}
        if(!current.automatic||current.endSeconds-current.startSeconds>=.20)out.add(current);return out;}

    private static ArrayList<Window>extract(AudioAnalyzer.StreamProvider source,AnalysisResult base,AudioAnalyzer.Progress progress)throws IOException{
        ArrayList<Window>out=new ArrayList<>();try(WavFile.Reader reader=WavFile.open(source.open())){int target=Math.max(1024,(int)Math.round(reader.info.sampleRate*WINDOW));FeatureBank rawBank=new FeatureBank(reader.info.sampleRate);DevicePath[]paths=new DevicePath[TranslationAnalyzer.PROFILES.length];for(int i=0;i<paths.length;i++)paths[i]=new DevicePath(reader.info.sampleRate,TranslationAnalyzer.PROFILES[i]);Acc raw=new Acc();Acc[]dev=accs(paths.length);float[][]b=new float[2][4096];long frame=0;int read;
            while((read=reader.readFrames(b,4096))>0){for(int n=0;n<read;n++,frame++){double l=finite(b[0][n]),r=finite(b[1][n]);raw.add(l,r,rawBank);for(int d=0;d<paths.length;d++){double[]v=paths[d].process(l,r);dev[d].add(v[0],v[1],paths[d].features);}if(raw.count==target){Window w=raw.finish((frame+1-target)/(double)reader.info.sampleRate,paths.length);for(int d=0;d<paths.length;d++)dev[d].finishFeatures(w.features[d]);out.add(w);raw=new Acc();dev=accs(paths.length);}}
                if(progress!=null)progress.update("v0.9: measuring all 12 translation response profiles",(int)Math.min(98,Math.round(98.0*frame/Math.max(1L,reader.info.frames))));if(Thread.currentThread().isInterrupted())throw new IOException("Translation consensus cancelled");}}
        return out;}

    private static Acc[]accs(int n){Acc[]a=new Acc[n];for(int i=0;i<n;i++)a[i]=new Acc();return a;}
    private static int index(Kind k){return k==Kind.BASS_BLOOM?0:k==Kind.VOCAL_MASKING?1:2;}
    private static Family family(int i){if(i==0||i==10)return Family.HOME;if(i==1)return Family.BLUETOOTH;if(i>=2&&i<=5)return Family.PHONE;if(i>=6&&i<=9)return Family.HEADPHONE;return Family.CAR;}
    private static String joinFamilies(Set<Family>s){ArrayList<String>n=new ArrayList<>();for(Family f:s)n.add(f.label);return join(n);}private static String join(List<String>n){StringBuilder b=new StringBuilder();for(String s:n){if(b.length()>0)b.append(", ");b.append(s);}return b.toString();}
    private static double[]levels(List<Window>w){double[]v=new double[w.size()];for(int i=0;i<v.length;i++)v[i]=w.get(i).level;return v;}private static double[]activeLevels(List<Window>w,List<Integer>ix){double[]v=new double[ix.size()];for(int i=0;i<v.length;i++)v[i]=w.get(ix.get(i)).level;return v;}
    private static double percentile(double[]v,double q){if(v.length==0)return-300;double[]c=v.clone();Arrays.sort(c);double p=clamp(q)*(c.length-1);int lo=(int)Math.floor(p),hi=Math.min(c.length-1,lo+1);return c[lo]+(c[hi]-c[lo])*(p-lo);}private static double robustScale(double[]v){double m=percentile(v,.5);double[]d=new double[v.length];for(int i=0;i<v.length;i++)d[i]=Math.abs(v[i]-m);return Math.max(.35,percentile(d,.65)*1.4826);}private static double clamp(double v){return Math.max(0,Math.min(1,v));}private static double finite(double v){return Double.isFinite(v)?v:0;}private static double db(double e,long n){return 10*Math.log10(Math.max(1e-30,e/Math.max(1,n)));}

    private static final class Window{double start,level;final double[][]features;Window(int n){features=new double[n][3];}}
    private static final class Acc{long count;double total,bass,body,midVocal,sideVocal,harsh;void add(double l,double r,FeatureBank b){double m=.5*(l+r),s=.5*(l-r),v;total+=l*l+r*r;v=b.bass.process(m);bass+=v*v;v=b.body.process(m);body+=v*v;v=b.midVocal.process(m);midVocal+=v*v;v=b.sideVocal.process(s);sideVocal+=v*v;v=b.harsh.process(m);harsh+=v*v;count++;}Window finish(double start,int n){Window w=new Window(n);w.start=start;w.level=db(total,count*2);return w;}void finishFeatures(double[]o){double bd=db(bass,count),bodyDb=db(body,count),mid=db(midVocal,count),side=db(sideVocal,count),h=db(harsh,count);o[0]=bd-bodyDb;o[1]=side-mid;o[2]=h-bodyDb;}}
    private static final class DevicePath{final TranslationAnalyzer.Profile profile;final TranslationAnalyzer.FilterChain left,right;final FeatureBank features;final double[]pair=new double[2];DevicePath(int rate,TranslationAnalyzer.Profile p){profile=p;left=new TranslationAnalyzer.FilterChain(rate,p);right=new TranslationAnalyzer.FilterChain(rate,p);features=new FeatureBank(rate);}double[]process(double il,double ir){double l=left.process(il),r=right.process(ir),ml=l*(1-profile.crossfeed)+r*profile.crossfeed,mr=r*(1-profile.crossfeed)+l*profile.crossfeed;if(profile.mono){double m=.5*(ml+mr);ml=m;mr=m;}pair[0]=ml;pair[1]=mr;return pair;}}
    private static final class FeatureBank{final Band bass,body,midVocal,sideVocal,harsh;FeatureBank(int r){bass=new Band(r,30,180);body=new Band(r,180,700);midVocal=new Band(r,700,4200);sideVocal=new Band(r,700,4200);harsh=new Band(r,3200,8500);}}
    private static final class Band{final Biquad high,low;Band(int r,double a,double b){high=Biquad.highPass(r,a);low=Biquad.lowPass(r,b);}double process(double v){return low.process(high.process(v));}}
}
