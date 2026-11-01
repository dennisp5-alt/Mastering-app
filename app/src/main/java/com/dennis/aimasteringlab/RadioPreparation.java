package com.dennis.aimasteringlab;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Final, source-relative delivery checks. These diagnostics never generate audio edits. */
final class RadioPreparation {
    static final class Issue {
        String kind;
        double startSeconds, endSeconds, monoLossDb, correlation;
        boolean presentInSource;
        String detail;
    }

    static final class Result {
        String verdict = "REVIEW";
        String error;
        int windowsChecked;
        final List<Issue> issues = new ArrayList<>();

        String compactSummary() {
            if (error != null) return "Radio preparation: INCOMPLETE — " + error;
            if (issues.isEmpty()) return "Radio preparation: " + verdict
                    + " — whole-song mono and opening/ending checks completed";
            StringBuilder s = new StringBuilder("Radio preparation: REVIEW");
            for (int i = 0; i < Math.min(5, issues.size()); i++) {
                Issue issue = issues.get(i);
                s.append(String.format(Locale.US, "\n%s–%s: %s (%s)",
                        time(issue.startSeconds), time(issue.endSeconds), issue.detail,
                        issue.presentInSource ? "also present in input" : "new or worsened; review"));
            }
            if (issues.size() > 5) s.append("\nMore findings are in the preparation report.");
            return s.toString();
        }
    }

    private static final class Stats {
        long n;
        double l, r, ll, rr, lr, peak;
        long peakFrame;
        void add(double a, double b, long frame) {
            n++; l += a; r += b; ll += a * a; rr += b * b; lr += a * b;
            double p = Math.max(Math.abs(a), Math.abs(b));
            if (p > peak) { peak = p; peakFrame = frame; }
        }
        double rmsDb() { return db(Math.sqrt((ll + rr) / Math.max(1L, 2 * n))); }
        double monoLossDb() {
            return 10.0 * Math.log10(Math.max(1e-15,
                    (ll + rr + 2.0 * lr) / Math.max(1e-30, 2.0 * (ll + rr))));
        }
        double correlation() {
            double a = ll - l * l / Math.max(1L, n);
            double b = rr - r * r / Math.max(1L, n);
            double denominator = Math.sqrt(Math.max(0.0, a * b));
            return denominator < 1e-20 ? 1.0
                    : Math.max(-1.0, Math.min(1.0, (lr - l * r / n) / denominator));
        }
    }

    private static final class Scan {
        final List<Stats> seconds = new ArrayList<>();
        final List<Issue> issues = new ArrayList<>();
    }

    static Result analyze(AudioAnalyzer.StreamProvider source, AnalysisResult before,
                          File output, AnalysisResult after,
                          AudioAnalyzer.Progress progress) throws IOException {
        Scan original = scan(source, before, progress);
        Scan finalScan = before.sourceSha256.equalsIgnoreCase(after.sourceSha256)
                ? original : scan(() -> new FileInputStream(output), after, progress);
        Result result = new Result();
        result.windowsChecked = finalScan.seconds.size();
        for (Issue issue : finalScan.issues) {
            issue.presentInSource = inherited(issue, original);
            result.issues.add(issue);
        }
        result.verdict = result.issues.isEmpty() ? "CHECKS_PASSED" : "REVIEW";
        return result;
    }

    private static Scan scan(AudioAnalyzer.StreamProvider source, AnalysisResult analysis,
                             AudioAnalyzer.Progress progress) throws IOException {
        Scan out = new Scan();
        ArrayList<Stats> tail = new ArrayList<>();
        int rate = analysis.wav.sampleRate;
        int shortFrames = Math.max(1, rate / 100);
        long frame = 0;
        Stats second = new Stats(), shortBin = new Stats();
        try (WavFile.Reader reader = WavFile.open(source.open())) {
            if (reader.info.channels != 2) throw new IOException("Radio preparation requires stereo audio");
            float[][] x = new float[2][4096];
            int n;
            while ((n = reader.readFrames(x, 4096)) > 0) {
                for (int i = 0; i < n; i++, frame++) {
                    if (!Float.isFinite(x[0][i]) || !Float.isFinite(x[1][i])) {
                        throw new IOException("Non-finite audio in radio preparation scan");
                    }
                    second.add(x[0][i], x[1][i], frame);
                    shortBin.add(x[0][i], x[1][i], frame);
                    if (second.n == rate) {
                        addSecond(out, second, frame + 1, rate);
                        second = new Stats();
                    }
                    if (shortBin.n == shortFrames) {
                        tail.add(shortBin);
                        if (tail.size() > 100) tail.remove(0);
                        shortBin = new Stats();
                    }
                }
                if (Thread.currentThread().isInterrupted()) throw new IOException("Mastering cancelled");
                progress.update("Radio preparation: checking full-song mono and boundaries",
                        (int) Math.min(99L, 100L * frame / Math.max(1L, analysis.wav.frames)));
            }
        }
        if (frame != analysis.wav.frames) throw new IOException("Radio preparation sample count mismatch");
        if (second.n > 0) addSecond(out, second, frame, rate);
        if (shortBin.n > 0) tail.add(shortBin);
        double duration = frame / (double) rate;
        if (analysis.firstFramePeak > 0.005) {
            add(out, "OPENING_BOUNDARY", 0, Math.min(0.02, duration),
                    "Opening starts away from zero; audition for a boundary click");
        }
        if (analysis.lastFramePeak > 0.005) {
            add(out, "ENDING_BOUNDARY", Math.max(0, duration - 0.02), duration,
                    "Ending stops away from zero; audition the final boundary");
        }
        if (tail.size() >= 40) {
            ArrayList<Double> earlier = new ArrayList<>();
            int recentStart = tail.size() - 10;
            for (int i = Math.max(0, recentStart - 30); i < recentStart; i++) earlier.add(tail.get(i).peak);
            Collections.sort(earlier);
            double background = earlier.get(earlier.size() / 2);
            Stats spike = tail.get(recentStart);
            for (int i = recentStart + 1; i < tail.size(); i++) if (tail.get(i).peak > spike.peak) spike = tail.get(i);
            if (db(spike.peak) > -30.0 && db(spike.peak) - db(background) > 15.0) {
                double at = spike.peakFrame / (double) rate;
                add(out, "ENDING_TRANSIENT", Math.max(0, at - 0.015), duration,
                        "Brief late transient rises sharply above the preceding tail; confirm it is intentional");
            }
        }
        return out;
    }

    private static void addSecond(Scan out, Stats s, long end, int rate) {
        out.seconds.add(s);
        // Uncorrelated width alone is not a failure. Require active audio and
        // anti-correlation; a warning remains an audition request, never an EQ trigger.
        if (s.n >= rate / 2 && s.rmsDb() > -40.0
                && s.correlation() < -0.05 && s.monoLossDb() < -3.2) {
            Issue issue = add(out, "MONO_SECTION", (end - s.n) / (double) rate,
                    end / (double) rate, "Section loses energy in mono; check important instruments and vocals");
            issue.monoLossDb = s.monoLossDb(); issue.correlation = s.correlation();
        }
    }

    private static Issue add(Scan out, String kind, double start, double end, String detail) {
        Issue issue = new Issue(); issue.kind = kind; issue.startSeconds = start;
        issue.endSeconds = end; issue.detail = detail; out.issues.add(issue); return issue;
    }

    private static boolean inherited(Issue issue, Scan source) {
        if ("MONO_SECTION".equals(issue.kind)) {
            int index = (int) Math.floor(issue.startSeconds + 1e-6);
            if (index < source.seconds.size()) {
                Stats s = source.seconds.get(index);
                return s.rmsDb() > -45.0
                        && issue.monoLossDb >= s.monoLossDb() - 0.75
                        && issue.correlation >= s.correlation() - 0.08;
            }
        }
        for (Issue prior : source.issues) if (issue.kind.equals(prior.kind)
                && Math.abs(issue.startSeconds - prior.startSeconds) <= 0.025) return true;
        return false;
    }

    static JSONObject json(Result r) throws JSONException {
        JSONObject j = new JSONObject();
        j.put("method", "full-length one-second stereo/mono windows and sample-boundary/late-transient checks; diagnostic only");
        j.put("target_lufs_i", -14.0); j.put("true_peak_ceiling_dbtp", -1.0);
        j.put("station_specific_delivery_certified", false);
        j.put("listening_performed", false);
        j.put("verdict", r == null ? "INCOMPLETE" : r.verdict);
        if (r == null) return j;
        j.put("windows_checked", r.windowsChecked);
        if (r.error != null) j.put("error", r.error);
        JSONArray issues = new JSONArray();
        for (Issue i : r.issues) {
            JSONObject item = new JSONObject(); item.put("kind", i.kind);
            item.put("start_seconds", i.startSeconds); item.put("end_seconds", i.endSeconds);
            item.put("present_in_mastering_input", i.presentInSource); item.put("detail", i.detail);
            if ("MONO_SECTION".equals(i.kind)) {
                item.put("mono_rms_change_db", i.monoLossDb); item.put("stereo_correlation", i.correlation);
            }
            issues.put(item);
        }
        j.put("listening_checks", issues); return j;
    }

    static String report(FullAiReleaseQc.Result qc) {
        MasterRenderer.Result m = qc.finalCandidate.render.master;
        AutonomousMasteringBrain.Plan p = qc.finalCandidate.plan;
        StringBuilder out = new StringBuilder("DENNIS AI MASTERING LAB — RADIO / RELEASE PREPARATION\n\n");
        out.append("Delivery target: approximately -14 LUFS-I, at or below -1 dBTP; 24-bit/48 kHz stereo.\n")
                .append("A station may request different levels or formats. This is not station-specific certification.\n\n");
        out.append(qc.radioPreparation == null ? "Radio preparation incomplete" : qc.radioPreparation.compactSummary()).append("\n\n");
        if (qc.radioPreparation != null && !qc.radioPreparation.issues.isEmpty()) {
            out.append("ALL TIMESTAMPED LISTENING CHECKS\n");
            for (Issue issue : qc.radioPreparation.issues) {
                out.append(time(issue.startSeconds)).append("–").append(time(issue.endSeconds))
                        .append(": ").append(issue.detail).append("; ")
                        .append(issue.presentInSource ? "also present in input" : "new or worsened; review");
                if ("MONO_SECTION".equals(issue.kind)) out.append(String.format(Locale.US,
                        "; mono RMS change %.3f dB, correlation %.3f", issue.monoLossDb, issue.correlation));
                out.append('\n');
            }
            out.append('\n');
        }
        out.append(qc.compactSummary()).append("\n");
        out.append("Loudness outcome: ").append(qc.loudnessVerdict).append("\n");
        if (qc.passageCheck != null) out.append(qc.passageCheck.summary()).append("\n");
        out.append(String.format(Locale.US,
                "Selected gain %+.4f dB; limiter maximum %.3f dB, average %.4f dB; frames above 1 dB %.3f%%, above 3 dB %.3f%%.\n",
                m.gainDb, m.maximumLimiterReductionDb, m.averageLimiterReductionDb,
                m.limiterFramesAboveOneDbPercent, m.limiterFramesAboveThreeDbPercent));
        if (p != null) {
            out.append("Selected plan: ").append(p.compactSummary()).append("\n");
            for (String why : p.decisions) out.append("- ").append(why).append('\n');
            for (String why : p.bypassed) out.append("- ").append(why).append('\n');
        }
        out.append("\nWARNINGS / SOURCE LIMITATIONS\n");
        if (qc.warnings.isEmpty()) out.append("None recorded.\n");
        for (String why : qc.warnings) out.append("- ").append(why).append('\n');
        out.append("\nNo blanket radio EQ, automatic LRA-only compression, stereo widening or device-profile correction was added.\n")
                .append("The advanced mastering result is retained for comparison; independently verified cleanup or limiter improvements may replace it. Detailed evidence is in the QC JSON.\n")
                .append("Human listening remains necessary for vocal realism, musical intent and final artistic approval.\n");
        return out.toString();
    }

    private static double db(double v) { return 20.0 * Math.log10(Math.max(1e-15, v)); }
    private static String time(double t) {
        return String.format(Locale.US, "%d:%06.3f", (int) (t / 60), t % 60.0);
    }
}
