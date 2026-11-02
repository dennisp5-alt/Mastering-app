package com.dennis.aimasteringlab;
import java.io.*;
import org.json.*;

/**
 * v0.5.3 host checks.
 *   unchained <wav> <dir>  : prints the base hash with the chain switched off (must equal v0.5.2)
 *   chained   <wav> <dir>  : tone + repairs, then a loud (−10 LUFS) delivery
 */
public final class V053Checks {
    static StudioProject project(File input, File root) throws Exception {
        root.mkdirs();
        AudioAnalyzer.Progress q = (s, x) -> {};
        StudioProject p = StudioProject.create(root, input.getName());
        File c = p.child("source.wav");
        StudioProject.copy(new FileInputStream(input), c);
        AnalysisResult a = StudioAudio.measure(c, q);
        p.file("source", c);
        p.data.put("source_hash", a.sourceSha256).put("duration", a.wav.durationSeconds).put("source_meters", StudioAudio.meters(a));
        p.state("QUEUED", "ready");
        return p;
    }

    public static void main(String[] args) throws Exception {
        File input = new File(args[1]), root = new File(args[2]);
        AudioAnalyzer.Progress q = (s, x) -> {};
        if (args[0].equals("unchained")) {
            MasteringCore.CHAINED_BY_DEFAULT = false;
            StudioProject p = project(input, root);
            StudioPipeline.master(p, q);
            System.out.println("UNCHAINED " + input.getName() + " base=" + StudioProject.load(p.dir).data.getString("base_hash"));
            return;
        }
        root.mkdirs();
        StudioDefaults.target(root, -10);
        StudioProject p = project(input, root);
        if (Math.abs(p.recipe().target + 10) > 1e-9 || Math.abs(p.recipe().ceiling + 2.0) > 1e-9)
            throw new AssertionError("new projects must take the default loudness: " + p.recipe().json());
        long t = System.nanoTime();
        StudioPipeline.master(p, q);
        StudioPipeline.finish(p, q);
        long ms = (System.nanoTime() - t) / 1000000;
        p = StudioProject.load(p.dir);
        JSONArray chain = p.data.getJSONArray("base_chain");
        JSONObject report = p.data.getJSONObject("final_qc");
        if (!report.has("base_chain") || !report.has("base_stage_ms") || !report.has("base_tone_winner"))
            throw new AssertionError("the Studio QC report must carry the chain and stage timings");
        if (chain.length() < 2 || !chain.getString(0).startsWith("tone: "))
            throw new AssertionError("chain not recorded: " + chain);
        JSONObject m = p.data.getJSONObject("master_meters"), qc = p.data.getJSONObject("final_qc");
        double lufs = m.getDouble("lufs"), tp = m.getDouble("true_peak");
        String note = qc.optString("delivery_note");
        if (tp > -1.995) throw new AssertionError("true peak over −2.0: " + tp);
        boolean reached = lufs > -10.2 && lufs < -9.85;
        boolean explained = note.contains("asked") || note.contains("not possible cleanly");
        if (!reached && !explained) throw new AssertionError("loud target neither reached nor explained: " + lufs + " / " + note);
        System.out.println("CHAINED " + input.getName() + " ms=" + ms + " chain=" + chain + " master=" + String.format(java.util.Locale.US, "%.2f LUFS %.2f dBTP", lufs, tp) + " note=" + note);
    }
}
