package com.dennis.aimasteringlab;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Separate-app Stems Only import adapter; hands the verified stereo premix to
 * the unmodified v0.15.7 StudioPipeline. No original stereo WAV is required.
 * Stem reconstruction/trust gates are deliberately NOT involved.
 */
final class StemOnlyImport {
    private StemOnlyImport() {}

    static void source(StudioProject project, InputStream selectedZip,
                       AudioAnalyzer.Progress progress) throws Exception {
        File zip = project.child("stems-original.zip");
        String originalHash = StudioImports.copyVerified(selectedZip, zip);
        project.file("stem_archive", zip);
        project.data.put("stem_archive_sha256", originalHash)
                .put("stem_authority", "STEMS_ONLY")
                .put("stem_import_version", "0.5.0-dev");
        project.state("IMPORTING", "Stem ZIP saved and SHA-256 verified; checking stems");

        // Reject malformed and oversized archives before preparing multiple
        // intermediate files. This preserves the legacy inspector and 1–32
        // 48-kHz mono/stereo WAV constraints unchanged.
        List<StemInventory.Item> inventory = StemInventory.inspect(zip, progress);
        int wavs = 0;
        long totalBytes = 0;
        for (StemInventory.Item item : inventory) {
            if (item.directory || !item.wav) continue;
            if (item.error != null || !item.crcVerified || item.wavInfo == null)
                throw new IOException("Invalid WAV stem: " + item.path);
            wavs++;
            totalBytes += item.uncompressedBytes;
        }
        if (wavs < 1 || wavs > 32)
            throw new IOException("Stems Only needs 1–32 intact WAVs; vocals + instrumental is supported");
        if (project.dir.getUsableSpace() < totalBytes * 3L + 256L * 1024L * 1024L)
            throw new IOException("Insufficient free storage for guarded stem preparation and premixing");

        File work = project.child("stem-preparation");
        if (!work.isDirectory() && !work.mkdirs())
            throw new IOException("Cannot create stem preparation workspace");
        StemStudioPreflight.Result preflight;
        File effective = zip;
        try {
            preflight = StemStudioPreflight.run(zip, work, true, progress);
            effective = preflight.effectiveArchive;
        } catch (Exception error) {
            if (Thread.currentThread().isInterrupted()) throw error;
            preflight = new StemStudioPreflight.Result();
            preflight.repairEnabled = true;
            preflight.effectiveArchive = zip;
            preflight.effectiveArchiveSha256 = originalHash;
            preflight.status = "Preflight unavailable; verified original stems used unchanged: " + error.getMessage();
            preflight.safetyNotes.add("No automatic repair was accepted after preflight failure");
        }
        if (effective == null || !effective.isFile())
            throw new IOException("Prepared stem archive is unavailable");
        if (!Hashing.sha256(effective).equals(preflight.effectiveArchiveSha256))
            throw new IOException("Stem preflight archive checksum mismatch");

        StemPremixBuilder.Result premix = StemPremixBuilder.build(effective, work, progress);
        // Use the existing source importer to retain modern SHA verification,
        // source meters, project persistence and downstream QC unchanged.
        try (FileInputStream audio = new FileInputStream(premix.premix)) {
            StudioImports.source(project, audio, progress);
        }
        JSONObject report = new JSONObject()
                .put("authority", "STEMS_ONLY")
                .put("original_stereo_required", false)
                .put("original_stereo_used", false)
                .put("source_zip_sha256", originalHash)
                .put("effective_zip_sha256", preflight.effectiveArchiveSha256)
                .put("premix_sha256", project.data.optString("source_hash", ""))
                .put("stem_count", premix.stemCount)
                .put("duration_seconds", premix.durationSeconds)
                .put("premix_gain_db", premix.premixAttenuationDb)
                .put("premix_peak_dbfs_before_gain", premix.rawPeakDbfs)
                .put("preflight_repair_used", preflight.preparedArchiveUsed)
                .put("preflight_stems_repaired", preflight.stemsRepaired)
                .put("preflight_status", preflight.status)
                .put("preflight_safety_notes", new JSONArray(preflight.safetyNotes));
        JSONArray stems = new JSONArray();
        for (StemInventory.Item item : premix.stems) {
            if (!item.wav || item.directory) continue;
            stems.put(new JSONObject()
                    .put("filename", item.path)
                    .put("role", StemPremixBuilder.role(item.path))
                    .put("channels", item.wavInfo.channels)
                    .put("sample_rate", item.wavInfo.sampleRate)
                    .put("frames", item.wavInfo.frames)
                    .put("crc_verified", item.crcVerified));
        }
        report.put("stems", stems);
        File reportFile = project.child("Stem_Import_QC.json");
        StudioProject.atomic(reportFile, report.toString(2).getBytes(StandardCharsets.UTF_8));
        project.file("stem_report", reportFile);
        project.data.put("stem_import_qc", report);
        project.state("QUEUED", "Verified " + premix.stemCount
                + " stems and prepared a float stereo premix. Ready for v0.15.7 mastering.");
    }
}
