package com.dennis.aimasteringlab;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

final class ReleasePackageBuilder {
    private ReleasePackageBuilder() {}

    static ReleaseArtifacts build(File sourceWav, AnalysisResult source,
                                  File workDirectory, String requestedBase,
                                  AudioAnalyzer.Progress progress) throws Exception {
        if (source.wav.sampleRate != 48000) {
            throw new IOException("v0.4.0 complete release packaging currently requires a 48 kHz authoritative source");
        }
        if (!workDirectory.exists() && !workDirectory.mkdirs()) {
            throw new IOException("Could not create the release work directory");
        }
        String base = safeBase(requestedBase);
        ReleaseArtifacts a = new ReleaseArtifacts();
        a.baseName = base;
        a.source = source;
        a.primaryWav = new File(workDirectory, base + "_M24.wav");
        a.mp3File = new File(workDirectory, base + "_320.mp3");
        a.aacFile = new File(workDirectory, base + "_256.m4a");
        a.translationWav = new File(workDirectory, base + "_TR.wav");
        a.translationMp3File = new File(workDirectory, base + "_TR320.mp3");
        a.masteringReport = new File(workDirectory, base + "_Mastering.md");
        a.translationReport = new File(workDirectory, base + "_Translation.md");
        a.qcJson = new File(workDirectory, base + "_QC.json");
        a.verificationReport = new File(workDirectory, base + "_Verify.txt");
        a.checksums = new File(workDirectory, base + "_SHA256.txt");
        a.coreZip = new File(workDirectory, base + "_Core.zip");
        a.completeZip = new File(workDirectory, base + "_Complete.zip");

        progress.update("Building verified 24-bit primary master", 1);
        if (source.noRenderNeeded) {
            copy(sourceWav, a.primaryWav);
            a.master = AudioAnalyzer.analyze(a.primaryWav.getName(),
                    () -> new FileInputStream(a.primaryWav), progress);
            a.masterRender = new MasterRenderer.Result();
            a.masterRender.file = a.primaryWav;
            a.masterRender.savedFileName = a.primaryWav.getName();
            a.masterRender.verification = a.master;
            a.masterRender.gainDb = 0.0;
            if (!source.sourceSha256.equalsIgnoreCase(a.master.sourceSha256)) {
                throw new IOException("Transparent release copy failed source hash verification");
            }
            a.masterRender.compliantSourcePreserved = source.alreadyCompliantNoRender
                    && AudioAnalyzer.isCompliantReleaseMaster(a.master);
            a.masterRender.exactTargetReached = Math.abs(a.master.integratedLufs + 14.0) <= 0.02;
            a.masterRender.dynamicsProtected = !a.masterRender.exactTargetReached
                    && source.ceilingLimited;
            a.masterRender.targetShortfallLu = -14.0 - a.master.integratedLufs;
        } else {
            a.masterRender = MasterRenderer.render(() -> new FileInputStream(sourceWav),
                    source, source.appliedSafeGainDb, a.primaryWav, progress);
            a.master = a.masterRender.verification;
        }

        a.mp3 = AndroidCodecEncoder.encodeMp3(a.primaryWav, a.mp3File,
                320000, progress);
        if (a.mp3.available) {
            a.mp3Decoded = decodeTemporary(a.mp3File,
                    new File(workDirectory, "decode-mp3.wav"), progress);
        }
        a.aac = AndroidCodecEncoder.encodeAac(a.primaryWav, a.aacFile,
                256000, progress);
        if (a.aac.available) {
            a.aacDecoded = decodeTemporary(a.aacFile,
                    new File(workDirectory, "decode-aac.wav"), progress);
        }

        a.translationComposite = TranslationComposite.build(a.primaryWav, a.master,
                source.sections, a.translationWav, progress);
        a.translation = TranslationAnalyzer.analyze(a.translationWav,
                a.translationComposite, progress);
        a.translationMp3 = AndroidCodecEncoder.encodeMp3(a.translationWav,
                a.translationMp3File, 320000, progress);
        if (a.translationMp3.available) {
            a.translationMp3Decoded = decodeTemporary(a.translationMp3File,
                    new File(workDirectory, "decode-translation-mp3.wav"), progress);
        }

        File sbcFile = new File(workDirectory, "decode-sbc328-proxy.wav");
        a.sbc = SbcProxy.render(a.primaryWav, sbcFile, progress);
        if (!sbcFile.delete()) sbcFile.deleteOnExit();

        a.assessment = FormalAssessment.evaluate(source, a.masterRender, a.master,
                a.mp3, a.mp3Decoded, a.aac, a.aacDecoded, a.sbc, a.translation);

        addMediaHashes(a, progress);
        ReleaseReportWriter.writeAll(a);
        addHash(a, a.masteringReport);
        addHash(a, a.translationReport);
        addHash(a, a.qcJson);
        addHash(a, a.verificationReport);
        writeChecksums(a);

        ArrayList<File> core = new ArrayList<>();
        core.add(a.primaryWav);
        if (a.mp3.available) core.add(a.mp3File);
        if (a.aac.available) core.add(a.aacFile);
        core.add(a.masteringReport);
        core.add(a.translationReport);
        core.add(a.qcJson);
        core.add(a.verificationReport);
        core.add(a.checksums);
        zip(a.coreZip, core, progress, "Building core release ZIP");
        verifyZip(a.coreZip);
        addHash(a, a.coreZip);

        ArrayList<File> complete = new ArrayList<>();
        complete.add(a.coreZip);
        complete.add(a.translationWav);
        if (a.translationMp3.available) complete.add(a.translationMp3File);
        complete.add(a.masteringReport);
        complete.add(a.translationReport);
        complete.add(a.qcJson);
        complete.add(a.verificationReport);
        complete.add(a.checksums);
        zip(a.completeZip, complete, progress, "Building complete release ZIP");
        verifyZip(a.completeZip);
        addHash(a, a.completeZip);
        progress.update("Complete release package verified", 100);
        return a;
    }

    private static AndroidCodecDecoder.Result decodeTemporary(File encoded, File decoded,
                                                               AudioAnalyzer.Progress progress) {
        AndroidCodecDecoder.Result result = AndroidCodecDecoder.decodeAndMeasure(
                encoded, decoded, progress);
        if (decoded.exists() && !decoded.delete()) decoded.deleteOnExit();
        return result;
    }

    private static void addMediaHashes(ReleaseArtifacts a,
                                       AudioAnalyzer.Progress progress) throws IOException {
        ArrayList<File> media = new ArrayList<>();
        media.add(a.primaryWav);
        if (a.mp3.available) media.add(a.mp3File);
        if (a.aac.available) media.add(a.aacFile);
        media.add(a.translationWav);
        if (a.translationMp3.available) media.add(a.translationMp3File);
        for (int i = 0; i < media.size(); i++) {
            addHash(a, media.get(i));
            progress.update("Checksumming release files " + (i + 1) + "/" + media.size(),
                    (int) Math.round(100.0 * (i + 1) / media.size()));
        }
    }

    private static void addHash(ReleaseArtifacts a, File file) throws IOException {
        if (file != null && file.exists()) a.sha256.put(file.getName(), Hashing.sha256(file));
    }

    private static void writeChecksums(ReleaseArtifacts a) throws IOException {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> entry : a.sha256.entrySet()) {
            out.append(entry.getValue()).append("  ").append(entry.getKey()).append('\n');
        }
        try (OutputStream stream = new FileOutputStream(a.checksums)) {
            stream.write(out.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void zip(File destination, List<File> files,
                            AudioAnalyzer.Progress progress, String stage) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(
                new FileOutputStream(destination), 256 * 1024))) {
            zip.setLevel(1);
            byte[] buffer = new byte[256 * 1024];
            for (int i = 0; i < files.size(); i++) {
                File file = files.get(i);
                ZipEntry entry = new ZipEntry(file.getName());
                entry.setTime(file.lastModified());
                zip.putNextEntry(entry);
                try (InputStream input = new BufferedInputStream(new FileInputStream(file), 256 * 1024)) {
                    int read;
                    while ((read = input.read(buffer)) >= 0) {
                        if (read > 0) zip.write(buffer, 0, read);
                    }
                }
                zip.closeEntry();
                progress.update(stage + " " + (i + 1) + "/" + files.size(),
                        (int) Math.round(100.0 * (i + 1) / files.size()));
            }
        }
    }

    private static void verifyZip(File archive) throws IOException {
        byte[] buffer = new byte[256 * 1024];
        try (ZipFile zip = new ZipFile(archive)) {
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                try (InputStream input = zip.getInputStream(entry)) {
                    while (input.read(buffer) >= 0) {}
                }
            }
        }
    }

    private static void copy(File source, File destination) throws IOException {
        try (InputStream input = new BufferedInputStream(new FileInputStream(source), 256 * 1024);
             OutputStream output = new BufferedOutputStream(new FileOutputStream(destination), 256 * 1024)) {
            byte[] buffer = new byte[256 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) if (read > 0) output.write(buffer, 0, read);
        }
    }

    private static String safeBase(String value) {
        String base = value == null ? "Song" : value.replaceAll("[^A-Za-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (base.isEmpty()) base = "Song";
        if (base.length() > 24) base = base.substring(0, 24).replaceAll("_+$", "");
        return base;
    }
}
