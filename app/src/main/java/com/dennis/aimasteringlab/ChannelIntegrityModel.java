package com.dennis.aimasteringlab;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Stable technical stereo-fault analysis; normal panning stays untouched. */
final class ChannelIntegrityModel {
    enum Kind { LEVEL_BALANCE, POLARITY_REVERSAL, FIXED_DELAY, CHANNEL_SWAP_DIAGNOSTIC, CENTRE_DRIFT_DIAGNOSTIC }

    static final class Event {
        Kind kind;
        double startSeconds;
        double endSeconds;
        double correctionDb;
        int lagSamples;
        double confidence;
        boolean automatic;
        String evidence;
    }

    static final class Result {
        String model = "DAML v0.9 channel integrity and stereo-balance repair";
        String method = "seven distributed stationary probes; stable level, polarity and sub-millisecond lag faults only";
        int probes;
        double balanceDb;
        double balanceMadDb;
        double spectralBalanceSpreadDb;
        double directionAgreement;
        int medianLagSamples;
        double lagAgreement;
        double medianCorrelation;
        double alignedCorrelation;
        final List<Event> events = new ArrayList<>();
        final List<String> safetyNotes = new ArrayList<>();

        int automaticCount() { int n=0; for(Event e:events) if(e.automatic)n++; return n; }
        String compactSummary() {
            return String.format(Locale.US,
                    "%d probes • balance %+.3f dB (MAD %.3f) • lag %d samples (agreement %.1f%%) • %d stable technical correction(s)",
                    probes, balanceDb, balanceMadDb, medianLagSamples,
                    lagAgreement * 100.0, automaticCount());
        }
    }

    private static final int WINDOW = 8192;
    private static final int PROBES = 7;
    private static final int MAX_LAG = 24;

    private ChannelIntegrityModel() {}

    static Result analyze(AudioAnalyzer.StreamProvider source,
                          AnalysisResult base,
                          AudioAnalyzer.Progress progress) throws IOException {
        Result out = new Result();
        List<Probe> probes = collect(source, base);
        out.probes = probes.size();
        if (probes.size() < 5) return out;
        double[] balances = new double[probes.size()];
        double[] corrs = new double[probes.size()];
        int[] lags = new int[probes.size()];
        double[] aligned = new double[probes.size()];
        double[] spectralSpread = new double[probes.size()];
        for (int i = 0; i < probes.size(); i++) {
            Probe p = probes.get(i); balances[i] = p.balanceDb;
            corrs[i] = p.correlation; lags[i] = p.bestLag;
            aligned[i] = p.bestCorrelation;
            spectralSpread[i] = p.spectralBalanceSpreadDb;
        }
        out.balanceDb = median(balances);
        out.balanceMadDb = mad(balances, out.balanceDb);
        out.spectralBalanceSpreadDb = median(spectralSpread);
        int sameDirection = 0;
        for (double v : balances) if (Math.signum(v) == Math.signum(out.balanceDb)) sameDirection++;
        out.directionAgreement = sameDirection / (double) balances.length;
        out.medianLagSamples = median(lags);
        int sameLag = 0; for (int lag : lags) if (lag == out.medianLagSamples) sameLag++;
        out.lagAgreement = sameLag / (double) lags.length;
        out.medianCorrelation = median(corrs);
        out.alignedCorrelation = median(aligned);
        double start = 2.0;
        double end = Math.max(start, base.wav.durationSeconds
                - Math.max(8.0, base.wav.durationSeconds * 0.04));

        if (out.medianCorrelation < -0.78
                && negativeAgreement(corrs) >= 0.86) {
            Event e = event(Kind.POLARITY_REVERSAL, start, end, true);
            e.confidence = Math.min(0.98, Math.abs(out.medianCorrelation));
            e.evidence = String.format(Locale.US,
                    "one-channel polarity reversal remained stable across %.1f%% of probes; median correlation %.3f",
                    negativeAgreement(corrs) * 100.0, out.medianCorrelation);
            out.events.add(e);
        } else if (Math.abs(out.balanceDb) >= 0.65
                && Math.abs(out.balanceDb) <= 3.0
                && out.balanceMadDb <= 0.12
                && out.spectralBalanceSpreadDb <= 0.22
                && out.directionAgreement >= 0.86
                && out.medianCorrelation >= 0.88) {
            Event e = event(Kind.LEVEL_BALANCE, start, end, false);
            e.correctionDb = -Math.signum(out.balanceDb)
                    * Math.min(0.75, Math.abs(out.balanceDb));
            e.confidence = Math.min(0.95, 0.72
                    + out.directionAgreement * 0.16
                    + Math.max(0.0, 0.12 - out.balanceMadDb));
            e.evidence = String.format(Locale.US,
                    "%s channel remained %.3f dB louder with %.3f dB MAD, %.3f dB cross-band spread and %.1f%% direction agreement; diagnostic only: level balance can reflect intentional panning",
                    out.balanceDb > 0 ? "left" : "right",
                    Math.abs(out.balanceDb), out.balanceMadDb,
                    out.spectralBalanceSpreadDb,
                    out.directionAgreement * 100.0);
            out.events.add(e);
        } else if (Math.abs(out.balanceDb) >= 0.65) {
            Event e = event(Kind.CENTRE_DRIFT_DIAGNOSTIC, start, end, false);
            e.evidence = "left/right level difference was not sufficiently correlated and stationary to distinguish a technical fault from arrangement panning";
            out.events.add(e);
        }

        if (out.medianCorrelation > 0.20 && out.medianLagSamples != 0
                && Math.abs(out.medianLagSamples) <= MAX_LAG
                && out.lagAgreement >= 0.86
                && out.alignedCorrelation - out.medianCorrelation >= 0.045) {
            Event e = event(Kind.FIXED_DELAY, start, end, true);
            e.lagSamples = out.medianLagSamples;
            e.confidence = Math.min(0.96, 0.72 + out.lagAgreement * 0.18
                    + Math.min(0.06, out.alignedCorrelation - out.medianCorrelation));
            e.evidence = String.format(Locale.US,
                    "fixed inter-channel lag %d samples repeated in %.1f%% of probes and improved correlation %.3f -> %.3f",
                    e.lagSamples, out.lagAgreement * 100.0,
                    out.medianCorrelation, out.alignedCorrelation);
            out.events.add(e);
        }
        Event swap = event(Kind.CHANNEL_SWAP_DIAGNOSTIC, start, end, false);
        swap.evidence = "Content-only stereo analysis cannot prove intended left/right orientation; channel swap remains diagnostic-only.";
        out.events.add(swap);
        out.safetyNotes.add("Stable faults only: normal panning, changing balance and arrangement movement are retained.");
        out.safetyNotes.add("Channel swap is never automatic without external orientation metadata.");
        if (progress != null) progress.update("v0.9: channel integrity ready", 100);
        return out;
    }

    private static Event event(Kind kind, double start, double end, boolean auto) {
        Event e = new Event(); e.kind=kind; e.startSeconds=start;
        e.endSeconds=end; e.automatic=auto; return e;
    }

    private static List<Probe> collect(AudioAnalyzer.StreamProvider source,
                                       AnalysisResult base) throws IOException {
        long start = base.wav.sampleRate * 2L;
        long end = base.wav.frames - Math.round(Math.max(8.0,
                base.wav.durationSeconds * 0.04) * base.wav.sampleRate);
        if (end - start < WINDOW) return new ArrayList<>();
        long[] starts = new long[PROBES];
        for (int i=0;i<PROBES;i++) starts[i]=start+Math.round(
                (end-start-WINDOW)*(i/(double)(PROBES-1)));
        double[][] left = new double[PROBES][WINDOW];
        double[][] right = new double[PROBES][WINDOW];
        int[] fill = new int[PROBES];
        try(WavFile.Reader reader=WavFile.open(source.open())) {
            float[][] b=new float[2][4096]; long frame=0; int read;
            while((read=reader.readFrames(b,4096))>0) {
                for(int n=0;n<read;n++,frame++) for(int p=0;p<PROBES;p++) {
                    if(frame>=starts[p]&&frame<starts[p]+WINDOW) {
                        int at=fill[p]++; left[p][at]=b[0][n]; right[p][at]=b[1][n];
                    }
                }
            }
        }
        ArrayList<Probe> out=new ArrayList<>();
        for(int p=0;p<PROBES;p++) if(fill[p]==WINDOW) out.add(measure(
                left[p],right[p],base.wav.sampleRate));
        return out;
    }

    private static Probe measure(double[] l,double[] r,int rate) {
        Probe p=new Probe(); double le=0,re=0;
        for(int i=0;i<l.length;i++){le+=l[i]*l[i];re+=r[i]*r[i];}
        p.balanceDb=10*Math.log10(Math.max(1e-30,le)/Math.max(1e-30,re));
        double[] bandBalance=new double[3];
        double[][] edges={{35,250},{250,4000},{4000,16000}};
        for(int band=0;band<edges.length;band++) {
            Biquad lh=Biquad.highPass(rate,edges[band][0]);
            Biquad ll=Biquad.lowPass(rate,edges[band][1]);
            Biquad rh=Biquad.highPass(rate,edges[band][0]);
            Biquad rl=Biquad.lowPass(rate,edges[band][1]);
            double leBand=0,reBand=0;
            for(int i=0;i<l.length;i++) {
                double lv=ll.process(lh.process(l[i]));
                double rv=rl.process(rh.process(r[i]));
                leBand+=lv*lv;reBand+=rv*rv;
            }
            bandBalance[band]=10*Math.log10(Math.max(1e-30,leBand)
                    /Math.max(1e-30,reBand));
        }
        double lo=bandBalance[0],hi=bandBalance[0];
        for(double v:bandBalance){lo=Math.min(lo,v);hi=Math.max(hi,v);}
        p.spectralBalanceSpreadDb=hi-lo;
        p.correlation=corr(l,r,0); p.bestCorrelation=p.correlation; p.bestLag=0;
        for(int lag=-MAX_LAG;lag<=MAX_LAG;lag++) {
            double c=corr(l,r,lag);
            if(c>p.bestCorrelation){p.bestCorrelation=c;p.bestLag=lag;}
        }
        return p;
    }

    private static double corr(double[] l,double[] r,int lag) {
        int ls=Math.max(0,-lag), rs=Math.max(0,lag);
        int n=Math.min(l.length-ls,r.length-rs); if(n<32)return 0;
        double xy=0,xx=0,yy=0;
        for(int i=0;i<n;i++){double x=l[ls+i],y=r[rs+i];xy+=x*y;xx+=x*x;yy+=y*y;}
        return xy/Math.sqrt(Math.max(1e-30,xx*yy));
    }

    private static double negativeAgreement(double[] values){int n=0;for(double v:values)if(v<0)n++;return n/(double)values.length;}
    private static double median(double[] v){double[]c=v.clone();Arrays.sort(c);return c[c.length/2];}
    private static int median(int[] v){int[]c=v.clone();Arrays.sort(c);return c[c.length/2];}
    private static double mad(double[]v,double m){double[]d=new double[v.length];for(int i=0;i<v.length;i++)d[i]=Math.abs(v[i]-m);return median(d);}
    private static final class Probe {double balanceDb,spectralBalanceSpreadDb,correlation,bestCorrelation;int bestLag;}
}
