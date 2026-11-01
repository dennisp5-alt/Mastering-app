package com.dennis.aimasteringlab;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Source-relative signal checks, not voice separation or a perceptual listening model. */
final class PassagePreservation {
    static final class Window {
        long frames;
        double energy, midPresence, sideEnergy, midEnergy, peak;
        double start, end;
        double level() { return db(energy / Math.max(1, frames)); }
        double crest() { return 20 * Math.log10(Math.max(1e-15, peak)) - level(); }
        double presence() { return db(midPresence / Math.max(1, frames)); }
        double width() { return db(sideEnergy / Math.max(1e-30, midEnergy)); }
    }
    static final class Scan {
        String hash;
        long frames;
        int rate;
        double lufs, duration;
        final List<Window> windows = new ArrayList<>();
    }
    static final class Finding {
        String kind, role;
        double start, end, changeDb;
    }
    static final class Result {
        String referenceHash;
        String error;
        int windowsChecked;
        int sparsePeakWindowsReviewed;
        int sparsePeakWindowBudget;
        double matchingGainDb;
        final List<Finding> findings = new ArrayList<>();
        boolean passed() { return error == null && findings.isEmpty(); }
        String summary() {
            if (error != null) return "Passage check unavailable: " + error;
            if (passed()) return "Passage preservation: measured PASS across " + windowsChecked + " aligned windows";
            Finding f = findings.get(0);
            return String.format(Locale.US, "Passage preservation rejected: %s at %.2f–%.2f s (%+.3f dB)",
                    f.kind, f.start, f.end, f.changeDb);
        }
    }

    static Scan scan(File file, AnalysisResult a) throws IOException {
        Scan scan = new Scan(); scan.hash = a.sourceSha256; scan.lufs = a.integratedLufs;
        scan.rate = a.wav.sampleRate; scan.frames = a.wav.frames; scan.duration = a.wav.durationSeconds;
        if (a.wav.channels != 2 || scan.rate != 48000 || !Double.isFinite(scan.lufs))
            throw new IOException("Passage comparison requires measured 48 kHz stereo audio");
        Biquad high = Biquad.highPass(scan.rate, 700), low = Biquad.lowPass(scan.rate, 3200);
        Window w = new Window(); long frame = 0;
        try (WavFile.Reader r = WavFile.open(new FileInputStream(file))) {
            if (r.info.frames != scan.frames || r.info.sampleRate != scan.rate || r.info.channels != 2)
                throw new IOException("Passage scan format or length changed");
            float[][] x = new float[2][4096]; int n;
            while ((n = r.readFrames(x, 4096)) > 0) {
                for (int i = 0; i < n; i++, frame++) {
                    double l=x[0][i], right=x[1][i];
                    if (!Double.isFinite(l) || !Double.isFinite(right)) throw new IOException("Non-finite passage audio");
                    double m=(l+right)*.5, s=(l-right)*.5, p=low.process(high.process(m));
                    w.frames++; w.energy+=(l*l+right*right)*.5;
                    w.midPresence+=p*p; w.midEnergy+=m*m; w.sideEnergy+=s*s;
                    w.peak=Math.max(w.peak,Math.max(Math.abs(l),Math.abs(right)));
                    if (w.frames == scan.rate) {
                        w.start=(frame+1-w.frames)/(double)scan.rate; w.end=(frame+1)/(double)scan.rate;
                        scan.windows.add(w); w=new Window();
                    }
                }
                if (Thread.currentThread().isInterrupted()) throw new IOException("Mastering cancelled");
            }
        }
        if (frame != scan.frames) throw new IOException("Passage scan was truncated");
        if (w.frames > 0) {
            w.start=(frame-w.frames)/(double)scan.rate; w.end=frame/(double)scan.rate; scan.windows.add(w);
        }
        return scan;
    }

    static Result compare(Scan baseline, Scan candidate) {
        return compare(baseline, candidate, -0.70);
    }

    static Result compareDynamicsLed(Scan baseline, Scan candidate,
                                     double transientCrestFloorDb) {
        return compare(baseline, candidate,
                Math.max(-1.05, Math.min(-0.70, transientCrestFloorDb)));
    }

    /** A bounded peak-event allowance, never used by ordinary mastering.
     * All centre/stereo/level/tail safety checks are unchanged. The greater
     * per-window crest allowance is only usable with independently measured
     * sparse limiter activity, checked by SparsePeakRecovery. */
    static Result compareSparsePeak(Scan baseline, Scan candidate) {
        Result r = compare(baseline, candidate, -3.00);
        if (r.error != null) return r;
        int altered = 0;
        for (int i = 0; i < baseline.windows.size(); i++) {
            Window a = baseline.windows.get(i);
            Window b = candidate.windows.get(i);
            if (a.frames < baseline.rate/2 || a.level() < -55) continue;
            if (a.level() > -45 && a.crest() > 9
                    && b.crest() - a.crest() < -0.70) altered++;
        }
        // One-second windows overcount brief isolated peaks. When <1% of
        // samples experience >=1 dB limiting, permit at most 15% of windows
        // with limited crests and a hard cap of 40 such seconds.
        int budget = Math.min(40, Math.max(1,
                (int) Math.floor(r.windowsChecked * 0.15)));
        r.sparsePeakWindowsReviewed = altered;
        r.sparsePeakWindowBudget = budget;
        if (altered > budget) {
            add(r, "SPARSE_TRANSIENT_BUDGET_EXCEEDED", "active passage",
                    0.0, baseline.duration, altered - budget);
        }
        return r;
    }

    private static Result compare(Scan baseline, Scan candidate,
                                  double transientCrestFloorDb) {
        Result r = new Result();
        if (baseline == null || candidate == null || baseline.frames != candidate.frames
                || baseline.rate != candidate.rate || baseline.windows.size() != candidate.windows.size()) {
            r.error="Aligned reference and candidate scans are required"; return r;
        }
        r.referenceHash=baseline.hash; r.matchingGainDb=baseline.lufs-candidate.lufs;
        if (!Double.isFinite(r.matchingGainDb)) { r.error="Invalid loudness match"; return r; }
        int presenceRun=0, widthRun=0, levelRun=0;
        for (int i=0;i<baseline.windows.size();i++) {
            Window a=baseline.windows.get(i), b=candidate.windows.get(i); r.windowsChecked++;
            if (a.frames < baseline.rate/2 || a.level() < -55) { presenceRun=widthRun=levelRun=0; continue; }
            String role=a.end>baseline.duration-8 ? "ending" : a.level()<baseline.lufs-8 ? "quiet passage" : "active passage";
            double level=b.level()+r.matchingGainDb-a.level();
            double presence=b.presence()+r.matchingGainDb-a.presence();
            double crest=b.crest()-a.crest(), width=b.width()-a.width();
            // Require sustained centre/image changes. A single musical note is insufficient evidence.
            presenceRun=a.presence()>-55 && presence < -0.85 ? presenceRun+1 : 0;
            widthRun=a.width()>-30 && a.width()<0 && width < -1.25 ? widthRun+1 : 0;
            levelRun=level < -0.85 ? levelRun+1 : 0;
            if (presenceRun==2) add(r,"CENTRE_PRESENCE_LOSS",role,baseline.windows.get(i-1).start,a.end,presence);
            if (widthRun==2) add(r,"STEREO_WIDTH_LOSS",role,baseline.windows.get(i-1).start,a.end,width);
            if (levelRun==2) add(r,"PASSAGE_LEVEL_LOSS",role,baseline.windows.get(i-1).start,a.end,level);
            if (a.level()>-45 && a.crest()>9 && crest < transientCrestFloorDb)
                add(r,"TRANSIENT_CREST_LOSS",role,a.start,a.end,crest);
            // A decaying ending is protected even when it is too quiet for other gates.
            if ("ending".equals(role) && i>=2 && a.level()<baseline.windows.get(i-2).level()-6 && level < -1.0)
                add(r,"TAIL_LEVEL_LOSS",role,a.start,a.end,level);
        }
        return r;
    }
    private static void add(Result r,String kind,String role,double start,double end,double db) {
        if (r.findings.size()>=24) return;
        Finding f=new Finding();f.kind=kind;f.role=role;f.start=start;f.end=end;f.changeDb=db;r.findings.add(f);
    }
    static JSONObject json(Result r) throws JSONException {
        JSONObject j=new JSONObject();
        j.put("method","aligned one-second windows with one fixed integrated-loudness gain match; signal proxies, not vocal isolation or listening");
        j.put("verdict",r==null ? "NOT_CHECKED" : r.error!=null ? "INCOMPLETE" : r.passed() ? "MEASURED_PASS" : "REJECTED");
        if (r==null) return j;
        j.put("reference_sha256",r.referenceHash);j.put("windows_checked",r.windowsChecked);j.put("comparison_gain_db",r.matchingGainDb);
        j.put("sparse_peak_windows_reviewed",r.sparsePeakWindowsReviewed);
        j.put("sparse_peak_window_budget",r.sparsePeakWindowBudget);
        if(r.error!=null)j.put("error",r.error);
        JSONArray findings=new JSONArray();
        for(Finding f:r.findings){JSONObject x=new JSONObject();x.put("kind",f.kind);x.put("role",f.role);x.put("start_seconds",f.start);x.put("end_seconds",f.end);x.put("change_db",f.changeDb);findings.put(x);}
        j.put("findings",findings);return j;
    }
    private static double db(double energy){return 10*Math.log10(Math.max(1e-30,energy));}
}
