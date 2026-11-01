package com.dennis.aimasteringlab;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Broad optional reference learning; never curve cloning or loudness matching. */
final class ReferenceCalibrationModel {
    enum Kind { LOW_SHELF, LOW_MID, PRESENCE, HIGH_SHELF, SIDE_REDUCTION, VOCAL_POCKET, DYNAMICS_DIAGNOSTIC }

    static final class Event {
        Kind kind;
        double adjustmentDb;
        double measuredDifferenceDb;
        double confidence;
        boolean automatic;
        String evidence;
    }

    static final class Result {
        String model = "DAML v0.9 optional reference-aware calibration";
        String method = "level-matched broad distribution comparison; 1.5 dB deadband, 18% learning, 0.35 dB per band and 0.80 dB combined cap";
        boolean supplied;
        boolean usable;
        String referenceName;
        String referenceSha256;
        double analysisLevelMatchDb;
        double broadDistance;
        final List<Event> events = new ArrayList<>();
        final List<String> safetyNotes = new ArrayList<>();

        int automaticCount(){int n=0;for(Event e:events)if(e.automatic)n++;return n;}
        String compactSummary(){return supplied?(usable?"usable • level-match analysis "+String.format(Locale.US,"%+.3f dB • broad distance %.3f • %d bounded adjustment(s)",analysisLevelMatchDb,broadDistance,automaticCount()):"supplied but rejected"):"no optional reference supplied";}
    }

    private ReferenceCalibrationModel() {}

    static Result none() { return new Result(); }

    static Result analyze(AudioAnalyzer.StreamProvider source,
                          AnalysisResult sourceAnalysis,
                          AudioAnalyzer.StreamProvider reference,
                          String referenceName,
                          String referenceSha256,
                          AudioAnalyzer.Progress progress) throws IOException {
        Result out=new Result(); out.supplied=true; out.referenceName=referenceName;
        out.referenceSha256=referenceSha256;
        BroadAudioProfile.Result src=BroadAudioProfile.analyze(source,progress);
        BroadAudioProfile.Result ref=BroadAudioProfile.analyze(reference,progress);
        if(src.sampleRate!=ref.sampleRate || src.sampleRate!=48000) {
            out.safetyNotes.add("Reference rejected: source and reference must both be 48 kHz stereo WAV files.");
            return out;
        }
        out.analysisLevelMatchDb=src.rmsDbfs-ref.rmsDbfs;
        double[] diff=new double[src.midBandDb.length]; double distance=0;
        for(int i=0;i<diff.length;i++) {
            diff[i]=(ref.midBandDb[i]-ref.rmsDbfs)-(src.midBandDb[i]-src.rmsDbfs);
            distance+=Math.abs(diff[i]);
        }
        out.broadDistance=distance/diff.length; out.usable=true;
        addBand(out,Kind.LOW_SHELF,average(diff,0,2));
        addBand(out,Kind.LOW_MID,average(diff,2,4));
        addBand(out,Kind.PRESENCE,diff[4]);
        addBand(out,Kind.HIGH_SHELF,average(diff,5,7));
        double sideDifference=ref.sideShareDb-src.sideShareDb;
        if(sideDifference<-1.5) add(out,Kind.SIDE_REDUCTION,
                Math.max(-0.35,sideDifference*0.18),sideDifference,true,
                "source side share exceeded the level-matched reference; attenuation only");
        double vocalDifference=ref.vocalCentreProxyDb-src.vocalCentreProxyDb;
        if(vocalDifference>2.0) add(out,Kind.VOCAL_POCKET,-0.25,
                vocalDifference,true,"centre-vocal proxy was less prominent than the level-matched reference; only competing side energy may be reduced");
        add(out,Kind.DYNAMICS_DIAGNOSTIC,0.0,
                sourceAnalysis.lraLu, false,
                "reference dynamics are learned for comparison only; reference loudness, LRA and crest are never imposed");
        capCombined(out,0.80);
        out.safetyNotes.add("Reference loudness is ignored after measurement-only level matching.");
        out.safetyNotes.add("No exact EQ curve is copied and no automatic widening is allowed.");
        return out;
    }

    private static void addBand(Result out,Kind kind,double difference) {
        if(Math.abs(difference)<=1.5)return;
        double learned=Math.max(-0.35,Math.min(0.35,difference*0.18));
        add(out,kind,learned,difference,true,String.format(Locale.US,
                "broad level-matched reference difference %+.3f dB; only 18%% (%+.3f dB) may be learned",difference,learned));
    }
    private static void add(Result out,Kind kind,double adjustment,double difference,boolean automatic,String evidence){Event e=new Event();e.kind=kind;e.adjustmentDb=adjustment;e.measuredDifferenceDb=difference;e.automatic=automatic;e.confidence=automatic?Math.min(0.90,0.68+Math.abs(difference)*0.04):0;e.evidence=evidence;out.events.add(e);}
    private static void capCombined(Result out,double cap){double sum=0;for(Event e:out.events)if(e.automatic&&e.kind!=Kind.SIDE_REDUCTION&&e.kind!=Kind.VOCAL_POCKET)sum+=Math.abs(e.adjustmentDb);if(sum<=cap)return;double scale=cap/sum;for(Event e:out.events)if(e.automatic&&e.kind!=Kind.SIDE_REDUCTION&&e.kind!=Kind.VOCAL_POCKET){e.adjustmentDb*=scale;e.evidence+="; scaled to respect the 0.80 dB combined tonal cap";}}
    private static double average(double[]v,int a,int b){double s=0;int n=0;for(int i=a;i<Math.min(b,v.length);i++){s+=v[i];n++;}return n==0?0:s/n;}
}
