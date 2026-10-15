package com.dennis.aimasteringlab;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import org.json.JSONObject;

/** Source-level contract: 2-stem ZIP -> verified source -> untouched studio mastering input. */
public final class V050StemOnlyIntegrationHarness {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: safe.zip unsafe.zip");
        File root = Files.createTempDirectory("stem-only-v050").toFile();
        File projects = new File(root, "projects");
        if (!projects.mkdirs()) throw new AssertionError("No project root");
        try {
            StudioProject project = StudioProject.create(projects, "Vocals + Instrumental");
            try (FileInputStream selected = new FileInputStream(args[0])) {
                StemOnlyImport.source(project, selected, (stage, percent) -> {});
            }
            project = StudioProject.load(project.dir);
            require("STEMS_ONLY".equals(project.data.getString("stem_authority")), "stem authority missing");
            require("QUEUED".equals(project.state()), "not queued after premixing");
            require(project.validFile("source", "source_hash"), "source hash invalid");
            File premix = StudioImports.requireSource(project, (stage, percent) -> {});
            AnalysisResult measured = StudioAudio.measure(premix, (stage, percent) -> {});
            require(measured.wav.sampleRate == 48000 && measured.wav.channels == 2
                    && measured.wav.floatingPoint && measured.wav.frames == 48_000L * 18L,
                    "verified full-resolution float premix required");
            JSONObject qc = project.data.getJSONObject("stem_import_qc");
            require(qc.getInt("stem_count") == 2, "vocals/instrumental count");
            require(!qc.getBoolean("original_stereo_required") && !qc.getBoolean("original_stereo_used"),
                    "no original WAV permitted as requirement");
            require(qc.getJSONArray("stems").length() == 2, "both stem roles retained");
            require(qc.getString("premix_sha256").equals(Hashing.sha256(premix)),
                    "premix source hash mismatch");
            require(qc.getDouble("premix_gain_db") <= 0.0, "shared premix gain must not boost stems");
            require(project.file("stem_report").isFile(), "missing persistent import QC");
            StudioProject rejected = StudioProject.create(projects, "Unsafe stems");
            boolean failed = false;
            try (FileInputStream selected = new FileInputStream(args[1])) {
                StemOnlyImport.source(rejected, selected, (stage, percent) -> {});
            } catch (Exception expected) { failed = true; }
            require(failed, "unsafe archive incorrectly accepted");
            require(!rejected.validFile("source", "source_hash"), "unsafe project acquired a processing source");
            System.out.println("V050_STEM_ONLY_INTEGRATION_PASS " + qc.getString("premix_sha256")
                    + " preflight=" + qc.getString("preflight_status"));
        } finally {
            // Test fixtures are ephemeral. The app's production project remains persistent.
            remove(root);
        }
    }
    private static void require(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }
    private static void remove(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) remove(child);
        }
        file.delete();
    }
}
