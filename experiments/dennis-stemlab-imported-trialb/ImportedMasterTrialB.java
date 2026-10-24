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
    private static final double CEILING = -1.1;
    private static final double SAFETY_MARGIN = .06;
    private ImportedMasterTrialB() {}

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

    static StudioProject render(File projects, String importedName, InputStream input,
                                AudioAnalyzer.Progress progress) throws Exception {
        if (input == null) throw new IOException("Select an existing mastered WAV.");
        String title = cleanTitle(importedName);
        StudioProject p = StudioProject.create(projects, title + " [Imported Trial B TEST]");
        try {
            File source = p.child("source.wav");
            String copyHash = StudioImports.copyVerified(input, source);
            p.file("source", source);
            p.data.put("source_hash", copyHash).put("source_bytes", source.length())
                .put("source_verified", false)
                .put("imported_master_trial_b", true)
                .put("trial_variant", "LOW_MID_CONTROL")
                .put("trial_original_title", title)
                .put("trial_base_reused", false)
                .put("no_second_mastering", true)
                .put("trial_dsp_scope", "Trial B on imported finished master; no base mastering");
            p.state("IMPORTING", "Verified copy retained; analysing finished source");
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
            double restore = a.integratedLufs - e.analysis.integratedLufs;
            double gain = Math.min(restore, r.ceiling - SAFETY_MARGIN - e.analysis.truePeakEstimateDbtp);
            if (!Double.isFinite(gain))
                throw new IOException("Nonfinite delivery gain from the imported master.");
            // No second master competition, and NO extra limiting; gain is only for
            // maintaining existing loudness subject to the true-peak safety ceiling.
            File output = p.child("final.wav");
            AnalysisResult done = null;
            MasterRenderer.Result rendered = null;
            for (int attempt = 0; attempt < 3; attempt++) {
                progress.update("Exporting 24-bit Trial B WAV without another mastering pass", 70);
                rendered = MasterRenderer.renderAttempt(
                    () -> new FileInputStream(edited), e.analysis, gain, false,
                    r.ceiling, MasterRenderer.LimiterProfile.BALANCED, output, progress);
                done = StudioAudio.measure(output, progress);
                StudioAudio.valid(done, a.wav.frames);
                if (rendered.quantizerClips != 0)
                    throw new IOException("Output clipped during 24-bit conversion; original imported master retained.");
                if (done.truePeakEstimateDbtp <= r.ceiling + .005) break;
                gain -= Math.max(.05, done.truePeakEstimateDbtp - r.ceiling + .06);
            }
            if (done == null || done.truePeakEstimateDbtp > r.ceiling + .005)
                throw new IOException("Trial B could not meet true-peak safety; original WAV retained.");
            if (Hashing.sha256(source).equals(Hashing.sha256(output)))
                throw new IOException("Trial B produced identical audio; check whether dynamic EQ activated.");
            p.file("master", output);
            p.data.put("master_hash", Hashing.sha256(output))
                .put("master_meters", StudioAudio.meters(done))
                .put("rendered_revision", 1)
                .put("trial_b_delivery_gain_db", gain)
                .put("trial_b_loudness_delta_lu", done.integratedLufs - a.integratedLufs)
                .put("trial_b_true_peak_delta_db", done.truePeakEstimateDbtp - a.truePeakEstimateDbtp)
                .put("trial_b_no_limiter", true)
                .put("trial_b_is_identical_to_project_trial_b", false);
            JSONObject qc = new JSONObject()
                .put("mode", "IMPORTED_MASTER_TRIAL_B_TEST")
                .put("verified_project_trial_b_equivalence", false)
                .put("original_v051_eq_recipe", true)
                .put("additional_mastering_pass", false)
                .put("limiter_used", false)
                .put("imported_filename", importedName)
                .put("song_title", title)
                .put("input", StudioAudio.meters(a))
                .put("output", StudioAudio.meters(done))
                .put("recipe", r.json())
                .put("delivery_gain_db", gain)
                .put("input_sha256", copyHash)
                .put("output_sha256", Hashing.sha256(output))
                .put("note", "This is original v0.5.1 Trial B DSP on an exported master; the exact original project-base workflow requires the retained base master.");
            File report = p.child("Imported_TrialB_QC.json");
            StudioProject.atomic(report, qc.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            p.file("imported_trial_b_qc", report);
            // The intermediate 32-bit render is no longer needed after final verification.
            edited.delete();
            p.state("COMPLETE", "Imported-master Trial B test finished without additional base mastering");
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
