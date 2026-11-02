package com.dennis.aimasteringlab;

import java.io.*;
import java.util.Locale;
import org.json.*;

/**
 * ISOLATED EXPERIMENT — original v0.5.1 Trial B EQ applied to an imported,
 * already-mastered stereo WAV. Does NOT call StudioPipeline.master/finish.
 * Never pretends this is identical to verified-base project Trial B.
 */
final class ImportedMasterTrialB {
    /** v0.5.2: -1.0 dBTP (was -1.1 with a further 0.06 dB margin and no limiter). */
    static final double CEILING = -1.0;
    /** Within this, the listener should hear no level difference. */
    static final double LOUDNESS_KEPT_LU = .10;
    private ImportedMasterTrialB() {}

    private static String fmt(double v) { return String.format(Locale.US, "%.2f", v); }

    /** One plain sentence: what happened to loudness and peak, and why. */
    static String loudnessSummary(AnalysisResult in, AnalysisResult eq, AnalysisResult out,
                                  double ceiling, boolean limiter, double limiterMax) {
        double change = out.integratedLufs - in.integratedLufs;
        String levels = "Loudness " + fmt(in.integratedLufs) + " → " + fmt(out.integratedLufs)
            + " LUFS (" + (change >= 0 ? "+" : "") + fmt(change) + " LU), true peak "
            + fmt(out.truePeakEstimateDbtp) + " dBTP";
        if (Math.abs(change) <= LOUDNESS_KEPT_LU) {
            return levels + ". Loudness kept" + (limiter
                ? " using the transparent peak limiter (max " + fmt(limiterMax) + " dB)."
                : " with flat gain only; no limiter needed.");
        }
        String why = in.truePeakEstimateDbtp > ceiling
            ? "the imported master already peaks at " + fmt(in.truePeakEstimateDbtp)
                + " dBTP, above the " + fmt(ceiling) + " dBTP ceiling, and keeping its loudness would need more than the transparent limiter allows"
            : "keeping the loudness would need more than the transparent limiter allows";
        return levels + (change < 0 ? ". Quieter than the original because " : ". Louder than the original because ") + why + ".";
    }

    static String cleanTitle(String fileName) {
        String s = fileName == null ? "Song" : fileName.trim()
            .replaceFirst("(?i)\\.wav$", "")
            .replaceFirst("(?i)[ _-]+(master|final)[ _-]+24bit[ _-]*48khz$", "")
            .replaceFirst("(?i)[ _-]+24bit[ _-]*48khz$", "")
            .replaceFirst("(?i)[ _-]+(master|final)$", "")
            .replaceFirst("(?i)[ _-]+trial[ _-]*b$", "");
        s = s.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (s.isEmpty()) s = "Song";
        // Keep room for the displayed test suffix and recipe's 120-character limit.
        return s.substring(0, Math.min(87, s.length()));
    }

    static StudioRecipe originalB(String title, double duration, double sourceLufs)
            throws IOException {
        StudioRecipe r = new StudioRecipe();
        r.title = title + " [Imported Trial B TEST]";
        r.target = sourceLufs; // Preserve the imported master's actual loudness.
        r.ceiling = CEILING;
        r.boundaryRepair = false; // No added fades or trims on already finished songs.
        r.lowEnd = false; // No extra low-end algorithm; exactly the two Trial B EQ bands.
        r.edits.add(band(260, .95, -.55, -24, duration));
        r.edits.add(band(3500, 1.2, -.45, -30, duration));
        r.validate(duration);
        return r;
    }

    private static StudioRecipe.Edit band(double hz, double q, double depth,
                                           double threshold, double duration) {
        StudioRecipe.Edit e = new StudioRecipe.Edit();
        e.kind = "DYNAMIC_EQ";
        e.channel = "STEREO";
        e.start = 0;
        e.end = duration;
        e.db = depth;
        e.hz = hz;
        e.q = q;
        e.threshold = threshold;
        e.enabled = true;
        e.automatic = false;
        e.reason = "Exact v0.5.1 Trial B recipe; experimental processing of an imported final WAV";
        return e;
    }

    /** Import to an app-private, hash-verified project before queue processing. */
    static StudioProject stage(File projects, String importedName, InputStream input) throws Exception {
        if (input == null || importedName == null || !importedName.toLowerCase(Locale.ROOT).endsWith(".wav"))
            throw new IOException("Import a 48 kHz stereo mastered WAV.");
        String title = cleanTitle(importedName);
        StudioProject p = StudioProject.create(projects, title + " [Imported Trial B TEST]");
        try {
            // Persist experiment identity BEFORE copying so a failed import can NEVER
            // be mistaken for a normal full-mastering project on Retry Failed.
            p.data.put("imported_master_trial_b", true)
                .put("imported_filename", importedName)
                .put("trial_variant", "LOW_MID_CONTROL")
                .put("trial_original_title", title).put("trial_base_reused", false)
                .put("no_second_mastering", true)
                .put("trial_dsp_scope", "Trial B on imported finished master; no base mastering");
            p.save();
            File source = p.child("source.wav");
            String copyHash = StudioImports.copyVerified(input, source);
            p.file("source", source);
            p.data.put("source_hash", copyHash).put("source_bytes", source.length())
                .put("source_verified", false);
            p.state("QUEUED", "Verified copied master is queued for imported Trial B; no remaster");
            return p;
        } catch (Exception ex) {
            p.data.put("import_error", ex.getClass().getSimpleName() + ": " + ex.getMessage());
            p.state(Thread.currentThread().isInterrupted() ? "PAUSED" : "FAILED",
                    "Mastered WAV staging failed: " + ex.getMessage());
            throw ex;
        }
    }

    /** Existing one-at-a-time picker also uses the exact queued Trial B DSP. */
    static StudioProject render(File projects, String importedName, InputStream input,
                                AudioAnalyzer.Progress progress) throws Exception {
        return renderStaged(stage(projects, importedName, input), progress);
    }

    /** Resume at a project boundary from the saved original WAV, NEVER via full mastering. */
    static StudioProject renderStaged(StudioProject p, AudioAnalyzer.Progress progress) throws Exception {
        if (!p.data.optBoolean("imported_master_trial_b", false))
            throw new IOException("This is not an imported-master Trial B project.");
        if (p.state().equals("COMPLETE") && p.validFile("master", "master_hash")) return p;
        try {
            String importedName = p.data.optString("imported_filename", p.name() + ".wav");
            String title = p.data.optString("trial_original_title", cleanTitle(importedName));
            File source = p.file("source");
            String copyHash = p.data.optString("source_hash", "");
            if (source == null || !copyHash.matches("[0-9a-f]{64}") || !p.validFile("source", "source_hash"))
                throw new IOException("The retained imported master failed SHA-256 validation. No processing attempted.");
            p.state("IMPORTING", "Verifying retained finished master before Trial B");
            progress.update("Analysing imported finished master", 5);
            AnalysisResult a = StudioAudio.measure(source, progress);
            StudioAudio.valid(a, a.wav.frames);
            if (!copyHash.equals(a.sourceSha256))
                throw new IOException("Imported master changed while being analysed.");
            if (a.wav.durationSeconds < .05)
                throw new IOException("Imported master is too short for Trial B.");
            if (a.integratedLufs < -30 || a.integratedLufs > -10)
                throw new IOException("This imported WAV measures "
                    + String.format(Locale.US, "%.2f", a.integratedLufs)
                    + " LUFS; experimental Trial B requires a finished master between -30 and -10 LUFS. No remaster was attempted.");
            long free = p.dir.getUsableSpace();
            long extra = Math.max(96L * 1048576L, source.length() * 4L);
            if (free < extra)
                throw new IOException("Not enough free storage for a safe separate Trial B WAV. Need about "
                    + (extra / 1048576L) + " MB of free space.");
            p.data.put("source_meters", StudioAudio.meters(a))
                .put("duration", a.wav.durationSeconds).put("source_verified", true)
                .put("imported_master_lufs", a.integratedLufs)
                .put("imported_master_true_peak", a.truePeakEstimateDbtp);
            StudioRecipe r = originalB(title, a.wav.durationSeconds, a.integratedLufs);
            p.data.put("recipe", r.json()).put("name", r.title).put("revision", 1);
            p.state("RENDERING", "Original v0.5.1 Trial B on imported mastered WAV; no base mastering");
            File edited = p.child("trial-b-edited-float.wav");
            progress.update("Applying original v0.5.1 Trial B dynamic EQ", 15);
            StudioAudio.EditResult e = StudioAudio.edit(source, a, r, edited, progress);
            if (e.analysis.wav.frames != a.wav.frames)
                throw new IOException("Trial B changed the song duration.");
            // v0.5.2: the SAME delivery stage as every Studio master and project
            // Trial B (StudioAudio.deliver). Target = the imported master's own
            // loudness; ceiling -1.0 dBTP. When flat gain alone would leave the song
            // quieter, delivery may use the verified transparent peak limiter
            // (<=3.1 dB max, <=0.2 dB mean, <=4% of frames above 1 dB, LRA within
            // 0.7 LU, crest loss <=1.5 dB); otherwise it falls back to flat gain and
            // the loudness change is reported, never hidden.
            File output = p.child("final.wav");
            progress.update("Trial B delivery: keeping loudness under " + fmt(r.ceiling) + " dBTP", 70);
            StudioAudio.Delivery delivered = StudioAudio.deliverStandard(edited, e.analysis, r, output, progress);
            AnalysisResult done = StudioAudio.measure(output, progress);
            StudioAudio.valid(done, a.wav.frames);
            if (done.truePeakEstimateDbtp > r.ceiling + .005)
                throw new IOException("Trial B could not meet true-peak safety; original WAV retained.");
            if (Hashing.sha256(source).equals(Hashing.sha256(output)))
                throw new IOException("Trial B produced identical audio; check whether dynamic EQ activated.");
            boolean limiter = delivered.render != null && delivered.render.limiterUsed;
            double gain = delivered.render == null ? 0 : delivered.render.gainDb;
            double change = done.integratedLufs - a.integratedLufs;
            double limiterMax = limiter ? delivered.render.maximumLimiterReductionDb : 0;
            double limiterMean = limiter ? delivered.render.averageLimiterReductionDb : 0;
            String summary = loudnessSummary(a, e.analysis, done, r.ceiling, limiter, limiterMax);
            p.file("master", output);
            p.data.put("master_hash", Hashing.sha256(output))
                .put("master_meters", StudioAudio.meters(done))
                .put("rendered_revision", 1)
                .put("trial_b_delivery_gain_db", gain)
                .put("trial_b_eq_loudness_change_lu", e.analysis.integratedLufs - a.integratedLufs)
                .put("trial_b_loudness_delta_lu", change)
                .put("trial_b_loudness_kept", Math.abs(change) <= LOUDNESS_KEPT_LU)
                .put("trial_b_true_peak_delta_db", done.truePeakEstimateDbtp - a.truePeakEstimateDbtp)
                .put("trial_b_ceiling_dbtp", r.ceiling)
                .put("trial_b_no_limiter", !limiter)
                .put("trial_b_limiter_max_db", limiterMax)
                .put("trial_b_limiter_mean_db", limiterMean)
                .put("trial_b_delivery_note", delivered.note)
                .put("trial_b_loudness_summary", summary)
                .put("trial_b_is_identical_to_project_trial_b", false);
            JSONObject qc = new JSONObject()
                .put("mode", "IMPORTED_MASTER_TRIAL_B_TEST")
                .put("app_version", StudioQcReporting.APP_VERSION)
                .put("verified_project_trial_b_equivalence", false)
                .put("original_v051_eq_recipe", true)
                .put("additional_mastering_pass", false)
                .put("delivery_stage", "StudioAudio.deliver (shared with all Studio masters)")
                .put("true_peak_ceiling_dbtp", r.ceiling)
                .put("limiter_used", limiter)
                .put("limiter_max_reduction_db", limiterMax)
                .put("limiter_mean_reduction_db", limiterMean)
                .put("limiter_peak_corrections", delivered.limiterPeakCorrections)
                .put("loudness_change_lu", change)
                .put("loudness_kept", Math.abs(change) <= LOUDNESS_KEPT_LU)
                .put("eq_loudness_change_lu", e.analysis.integratedLufs - a.integratedLufs)
                .put("loudness_summary", summary)
                .put("delivery_note", delivered.note)
                .put("imported_filename", importedName)
                .put("song_title", title)
                .put("input", StudioAudio.meters(a))
                .put("output", StudioAudio.meters(done))
                .put("recipe", r.json())
                .put("delivery_gain_db", gain)
                .put("input_sha256", copyHash)
                .put("output_sha256", Hashing.sha256(output))
                .put("note", "Original v0.5.1 Trial B dynamic EQ on an exported master, delivered through the shared Studio delivery stage; the exact original project-base workflow requires the retained base master.");
            File report = p.child("Imported_TrialB_QC.json");
            StudioProject.atomic(report, qc.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            p.file("imported_trial_b_qc", report);
            // The intermediate 32-bit render is no longer needed after final verification.
            edited.delete();
            p.state("COMPLETE", summary);
            progress.update("Trial B finished; original source retained and output verified", 100);
            return p;
        } catch (Exception ex) {
            try {
                p.data.put("import_error", ex.getClass().getSimpleName() + ": " + ex.getMessage());
                p.state(Thread.currentThread().isInterrupted() ? "PAUSED" : "FAILED",
                    "Imported-master Trial B did not complete: " + ex.getMessage());
            } catch (Exception ignored) { }
            throw ex;
        }
    }
}
