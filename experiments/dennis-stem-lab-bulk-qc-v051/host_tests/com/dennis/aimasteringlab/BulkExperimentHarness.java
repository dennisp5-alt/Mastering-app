package com.dennis.aimasteringlab;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import org.json.JSONObject;

/** Host regression for both regular WAV projects and verified stem-premix projects. */
public class BulkExperimentHarness {
    private static void ensure(boolean ok, String why) { if (!ok) throw new AssertionError(why); }

    private static StudioProject baseline(File root, String name, boolean stems) throws Exception {
        StudioProject p = StudioProject.create(root, name);
        File source = p.child("source.wav"), base = p.child("base-master.wav"), master = p.child("final.wav");
        Files.write(source.toPath(), (name + ":source").getBytes(StandardCharsets.UTF_8));
        Files.write(base.toPath(), (name + ":base").getBytes(StandardCharsets.UTF_8));
        Files.write(master.toPath(), (name + ":master").getBytes(StandardCharsets.UTF_8));
        p.file("source", source); p.file("base", base); p.file("master", master);
        p.data.put("source_hash", Hashing.sha256(source)).put("base_hash", Hashing.sha256(base))
              .put("master_hash", Hashing.sha256(master)).put("duration", 180d)
              .put("source_meters", new JSONObject().put("lufs", -16))
              .put("master_meters", new JSONObject().put("lufs", -14.2).put("true_peak", -1.2)
                     .put("lra", 8.5).put("crest", 12.3).put("correlation", 0.9))
              .put("base_winner", "original").put("base_meaningful_change", true);
        if (stems) p.data.put("stem_authority", "STEMS_ONLY");
        p.state("COMPLETE", "Ready");
        return p;
    }

    public static void main(String[] args) throws Exception {
        File root = Files.createTempDirectory("stemlab-bulk-v051-").toFile();
        StudioProject wav = baseline(root, "Complete WAV", false);
        StudioProject stems = baseline(root, "Imported Stem Mix", true);
        ensure(StudioBulkExperiment.eligible(wav) && StudioBulkExperiment.eligible(stems), "both inputs eligible");
        List<String> ids = Arrays.asList(wav.dir.getName(), stems.dir.getName());
        int created = StudioBulkExperiment.create(root, ids, (stage, pct) -> {});
        ensure(created == 4, "two trial variants per each source format");
        ensure("COMPLETE".equals(StudioProject.load(wav.dir).state()), "original WAV intact");
        ensure("COMPLETE".equals(StudioProject.load(stems.dir).state()), "original stems intact");
        int wavTrials = 0, stemTrials = 0, controlA = 0, controlB = 0;
        for (StudioProject p : StudioProject.list(root, null)) {
            if (!p.data.has("trial_variant")) continue;
            ensure(p.validFile("source", "source_hash"), "source checksum");
            ensure(p.validFile("base", "base_hash"), "base checksum");
            ensure(p.file("master") == null, "trial has no premature final master");
            ensure("QUEUED".equals(p.state()), "trial queue status");
            ensure(p.recipe().edits.size() == 2, "controlled pair of corrective edits");
            ensure(p.data.optBoolean("trial_base_reused", false), "reused proven v15.7 base");
            String kind = StudioBulkExperiment.kind(p);
            if ("FULL_WAV".equals(kind)) wavTrials++;
            else if ("STEM_PREMIX".equals(kind)) stemTrials++;
            else throw new AssertionError("wrong input format " + kind);
            if ("PRESENCE_CONTROL".equals(p.data.getString("trial_variant"))) controlA++;
            if ("LOW_MID_CONTROL".equals(p.data.getString("trial_variant"))) controlB++;
        }
        ensure(wavTrials == 2 && stemTrials == 2 && controlA == 2 && controlB == 2,
               "four distinct trials across both input formats");
        File csv = new File(root.getParentFile(), "bulk-qc-v051-host.csv");
        StudioBulkExperiment.report(root, csv, (stage, pct) -> {});
        String report = new String(Files.readAllBytes(csv.toPath()), StandardCharsets.UTF_8);
        ensure(report.contains("FULL_WAV") && report.contains("STEM_PREMIX")
                && report.contains("PRESENCE_CONTROL") && report.contains("LOW_MID_CONTROL"), "CSV coverage");
        ensure(wav.validFile("master", "master_hash") && stems.validFile("master", "master_hash"),
               "original mastered WAV checksums still match");
        System.out.println("PASS: WAV + stem originals preserved; 4 independent verified trials; QC CSV");
    }
}
