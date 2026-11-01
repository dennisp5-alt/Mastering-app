package com.dennis.aimasteringlab;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.json.JSONObject;
import org.json.JSONArray;

/** Optional and reversible mastering trials. Never mutates an existing project or the DSP engine. */
final class StudioBulkExperiment {
    interface Progress { void update(String stage, int percent); }
    private static final int MAX_SOURCES = 10;
    // Exact recovered v0.5.1 Trial B; the presence trial is not generated.
    private static final String[][] VARIANTS = {
        {"LOW_MID_CONTROL", "Trial B", "Original v0.5.1 low-mid control at 260 Hz and 3.5 kHz"}
    };

    private StudioBulkExperiment() {}

    static boolean eligible(StudioProject p) {
        try {
            return p.data.optString("trial_parent_id", "").isEmpty()
                    && "COMPLETE".equals(p.state())
                    && p.file("source") != null && p.file("source").isFile()
                    && p.file("base") != null && p.file("base").isFile()
                    && p.file("master") != null && p.file("master").isFile()
                    && !p.data.optString("source_hash", "").isEmpty()
                    && !p.data.optString("base_hash", "").isEmpty()
                    && !p.data.optString("master_hash", "").isEmpty()
                    && p.data.optDouble("duration", 0) >= 1;
        } catch (IOException e) { return false; }
    }

    static int create(File root, List<String> selectedIds, Progress progress) throws Exception {
        if (selectedIds == null || selectedIds.isEmpty() || selectedIds.size() > MAX_SOURCES)
            throw new IOException("Choose between one and ten verified completed masters for Trial B");
        Set<String> unique = new HashSet<>();
        List<StudioProject> originals = new ArrayList<>();
        for (String id : selectedIds) {
            if (id == null || !id.matches("[a-fA-F0-9-]{36}") || !unique.add(id))
                throw new IOException("Invalid or duplicate project selection");
            StudioProject p = StudioProject.load(new File(root, id));
            if (!eligible(p) || !p.validFile("source", "source_hash")
                    || !p.validFile("base", "base_hash") || !p.validFile("master", "master_hash"))
                throw new IOException("Project fails original SHA-256 verification: " + p.name());
            if (p.recipe().edits.size() > 62)
                throw new IOException("Too many passage edits for two additional trial filters: " + p.name());
            originals.add(p);
        }
        // Reserve storage for isolated source and base copies, with additional headroom for masters.
        long required = 256L * 1024 * 1024;
        for (StudioProject p : originals) {
            File source = p.file("source"), base = p.file("base");
            required = Math.addExact(required, Math.addExact(source.length(), base.length()));
            required = Math.addExact(required, Math.multiplyExact(2L, Math.max(source.length(), base.length())));
        }
        if (root.getUsableSpace() < required)
            throw new IOException("Not enough free storage for independent Trial B masters: approximately "
                    + (required / 1048576) + " MB required");
        int total = originals.size() * VARIANTS.length, made = 0;
        for (StudioProject original : originals) {
            for (String[] variant : VARIANTS) {
                progress.update("Creating " + original.name() + " / " + variant[1], made * 100 / total);
                createOne(root, original, variant);
                made++;
            }
        }
        progress.update("Trial B projects are queued", 100);
        return made;
    }

    private static StudioProject createOne(File root, StudioProject original, String[] variant) throws Exception {
        StudioRecipe recipe = StudioRecipe.read(original.recipe().json());
        final String suffix = " [Trial B]";
        String title = recipe.title;
        recipe.title = title.substring(0, Math.min(title.length(), 120 - suffix.length())) + suffix;
        double duration = original.data.optDouble("duration", 0);
        // Original v0.5.1 Trial B parameters, not an approximation.
        recipe.edits.add(edit("DYNAMIC_EQ", 260, 0.95, -0.55, -24, duration, variant[1]));
        recipe.edits.add(edit("DYNAMIC_EQ", 3500, 1.2, -0.45, -30, duration, variant[1]));
        recipe.validate(duration);
        StudioProject trial = StudioProject.create(root, recipe.title);
        try {
            File source = trial.child("source.wav"), base = trial.child("base-master.wav");
            StudioProject.copy(new FileInputStream(original.file("source")), source);
            StudioProject.copy(new FileInputStream(original.file("base")), base);
            if (!Hashing.sha256(source).equals(original.data.getString("source_hash"))
                    || !Hashing.sha256(base).equals(original.data.getString("base_hash")))
                throw new IOException("Trial file copy checksum does not match its source");
            trial.file("source", source);
            trial.file("base", base);
            // Inherit verified, measured source/base provenance. Never copy completed masters.
            JSONArray keys = original.data.names();
            for (int index = 0; keys != null && index < keys.length(); index++) {
                String key = keys.getString(index);
                if (key.startsWith("base_") || key.startsWith("transparent_")
                        || key.equals("source_hash") || key.equals("source_bytes")
                        || key.equals("source_meters") || key.equals("source_verified")
                        || key.equals("duration")) {
                    Object value = original.data.get(key);
                    trial.data.put(key, value);
                }
            }
            trial.data.put("recipe", recipe.json()).put("name", recipe.title)
                    .put("revision", 1).put("trial_parent_id", original.dir.getName())
                    .put("trial_parent_source_sha256", original.data.getString("source_hash"))
                    .put("trial_variant", variant[0]).put("trial_description", variant[2])
                    .put("trial_original_title", title)
                    .put("trial_source_kind", kind(original))
                    .put("trial_dsp_scope", "post-master dynamic EQ only; verified v0.15.7 base unchanged")
                    .put("trial_recipe_revision", original.data.optInt("revision", 0))
                    .put("trial_base_reused", true);
            trial.state("QUEUED", "Isolated " + variant[1] + " audition from the verified original base; original untouched");
            return trial;
        } catch (Exception e) {
            removeTree(trial.dir);
            throw e;
        }
    }

    private static StudioRecipe.Edit edit(String kind, double hz, double q, double db,
                                          double threshold, double duration, String family) {
        StudioRecipe.Edit e = new StudioRecipe.Edit();
        e.kind = kind; e.channel = "STEREO"; e.start = 0; e.end = duration;
        e.hz = hz; e.q = q; e.db = db; e.threshold = threshold;
        e.reason = "v0.5.1 optional " + family + " listening trial (not automatic AI)";
        e.automatic = false; e.enabled = true;
        return e;
    }

    static String kind(StudioProject p) {
        if ("STEMS_ONLY".equals(p.data.optString("stem_authority", ""))) return "STEM_PREMIX";
        String trial = p.data.optString("trial_source_kind", "");
        return trial.isEmpty() ? "FULL_WAV" : trial;
    }

    static File report(File root, File output, Progress progress) throws Exception {
        List<StudioProject> projects = StudioProject.list(root, null);
        StringBuilder s = new StringBuilder("project_id,title,input_type,trial,parent_id,status,source_sha256,master_sha256,"
                + "target_lufs,actual_lufs,lufs_shortfall,true_peak_dbtp,ceiling_dbtp,lra_lu,crest_db,"
                + "stereo_correlation,baseline_delta_lufs,baseline_delta_tp,master_hash_check,peak_ceiling_check,"
                + "base_strategy,sonic_change,detail\n");
        int i = 0;
        for (StudioProject p : projects) {
            JSONObject meters = p.data.optJSONObject("master_meters");
            StudioRecipe recipe = p.recipe();
            String parentId = p.data.optString("trial_parent_id", "");
            double baselineLufs = Double.NaN, baselineTp = Double.NaN;
            if (!parentId.isEmpty()) try {
                StudioProject parent = StudioProject.load(new File(root, parentId));
                JSONObject pm = parent.data.optJSONObject("master_meters");
                if (pm != null) { baselineLufs = pm.optDouble("lufs", Double.NaN);
                    baselineTp = pm.optDouble("true_peak", Double.NaN); }
            } catch (Exception ignored) { }
            double lufs = meters == null ? Double.NaN : meters.optDouble("lufs", Double.NaN);
            double tp = meters == null ? Double.NaN : meters.optDouble("true_peak", Double.NaN);
            String hash = "NOT_RENDERED", peak = "NOT_MEASURED";
            if (meters != null) {
                hash = p.validFile("master", "master_hash") ? "PASS" : "FAIL";
                peak = Double.isFinite(tp) ? (tp <= recipe.ceiling + .005 ? "PASS" : "REVIEW") : "NOT_MEASURED";
            }
            String[] cells = {
                p.dir.getName(), p.name(), kind(p), p.data.optString("trial_variant", "BASELINE"),
                parentId, p.state(), p.data.optString("source_hash", ""), p.data.optString("master_hash", ""),
                fmt(recipe.target), fmt(lufs), fmt(Double.isFinite(lufs) ? recipe.target - lufs : Double.NaN),
                fmt(tp), fmt(recipe.ceiling), fmt(meters == null ? Double.NaN : meters.optDouble("lra", Double.NaN)),
                fmt(meters == null ? Double.NaN : meters.optDouble("crest", Double.NaN)),
                fmt(meters == null ? Double.NaN : meters.optDouble("correlation", Double.NaN)),
                fmt(Double.isFinite(lufs) && Double.isFinite(baselineLufs) ? lufs - baselineLufs : Double.NaN),
                fmt(Double.isFinite(tp) && Double.isFinite(baselineTp) ? tp - baselineTp : Double.NaN),
                hash, peak, p.data.optString("base_winner", ""),
                Boolean.toString(p.data.optBoolean("base_meaningful_change", false)),
                p.data.optString("trial_description", p.data.optString("message", ""))
            };
            for (int k = 0; k < cells.length; k++) {
                if (k > 0) s.append(',');
                s.append('"').append(cells[k].replace("\"", "\"\"").replace('\r', ' ').replace('\n', ' ')).append('"');
            }
            s.append('\n');
            progress.update("Checking bulk results", ++i * 100 / Math.max(1, projects.size()));
        }
        StudioProject.atomic(output, s.toString().getBytes(StandardCharsets.UTF_8));
        return output;
    }

    private static void removeTree(File file) {
        if (file.isDirectory()) { File[] children = file.listFiles();
            if (children != null) for (File child : children) removeTree(child); }
        file.delete();
    }

    private static String fmt(double x) { return Double.isFinite(x) ? String.format(Locale.US, "%.4f", x) : ""; }
}
