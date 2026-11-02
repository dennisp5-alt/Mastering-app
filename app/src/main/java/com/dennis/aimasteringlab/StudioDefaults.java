package com.dennis.aimasteringlab;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import org.json.JSONObject;

/**
 * v0.5.3 — the loudness every newly imported song is delivered at (each project can
 * still be changed in Finishing and delivery). Stored beside the projects.
 */
final class StudioDefaults {
    static final double[] TARGETS = {-14, -12, -11, -10, -9};
    static final String[] NAMES = {
        "Streaming −14 LUFS (Spotify / Apple / YouTube level)",
        "Balanced −12 LUFS (−2 dBTP)",
        "Loud −11 LUFS (country / pop, −2 dBTP)",
        "Louder −10 LUFS (rock / pop, −2 dBTP)",
        "Very loud −9 LUFS (−2 dBTP, only if it stays clean)",
    };

    private StudioDefaults() {}

    private static File file(File root) { return new File(root, "studio_defaults.json"); }

    static double target(File root) {
        try {
            JSONObject j = new JSONObject(new String(Files.readAllBytes(file(root).toPath()), StandardCharsets.UTF_8));
            double t = j.optDouble("target", -14);
            for (double v : TARGETS) if (Math.abs(v - t) < 1e-9) return v;
        } catch (Exception ignored) { }
        return -14;
    }

    static void target(File root, double t) throws Exception {
        StudioProject.atomic(file(root), new JSONObject().put("target", t).toString().getBytes(StandardCharsets.UTF_8));
    }

    static String name(double t) {
        for (int i = 0; i < TARGETS.length; i++) if (Math.abs(TARGETS[i] - t) < 1e-9) return NAMES[i];
        return String.format(Locale.US, "%.1f LUFS", t);
    }

    /** Spotify: a master louder than −14 LUFS should keep its true peak below −2 dB. */
    static final double LOUD_CEILING_DBTP = -2.0;

    /** A new project's delivery: the default target; louder than −14 uses a −2.0 dBTP ceiling. */
    static void apply(File root, StudioRecipe r) {
        r.target = target(root);
        if (r.target > -14.0 + 1e-9) r.ceiling = LOUD_CEILING_DBTP;
    }
}
