package com.dennis.aimasteringlab;
import java.io.*;
import org.json.*;

/** Runs imported-master Trial B on a finished WAV and prints input/output loudness and peak. */
public final class ImportedTrialBHarness {
    public static void main(String[] args) throws Exception {
        File input = new File(args[0]), root = new File(args[1]);
        root.mkdirs();
        StudioProject p = ImportedMasterTrialB.render(root, input.getName(), new FileInputStream(input), (s, x) -> {});
        p = StudioProject.load(p.dir);
        JSONObject in = p.data.getJSONObject("source_meters"), out = p.data.getJSONObject("master_meters");
        double dl = out.getDouble("lufs") - in.getDouble("lufs");
        System.out.println(String.format(java.util.Locale.US,
            "TRIALB %s state=%s in=%.2f LUFS/%.2f dBTP out=%.2f LUFS/%.2f dBTP change=%+.2f LU gain=%.2f dB note=%s",
            input.getName(), p.state(), in.getDouble("lufs"), in.getDouble("true_peak"),
            out.getDouble("lufs"), out.getDouble("true_peak"), dl,
            p.data.optDouble("trial_b_delivery_gain_db", Double.NaN),
            p.data.optString("trial_b_loudness_summary", "")));
        System.out.println("TRIALB_HASH " + input.getName() + " " + p.data.getString("master_hash"));
    }
}
