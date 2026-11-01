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
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Builds one compact, self-contained experimental Full AI handoff package. */
final class FullAiPackageBuilder {
    static final class Result {
        File report;
        File confidenceMap;
        File preparationReport;
        File checksums;
        File zip;
        String zipSha256;
    }

    private FullAiPackageBuilder() {}

    static Result build(AnalysisResult source,
                        AutonomousMasteringBrain.Result brain,
                        FullAiMasteringEngine.Result engine,
                        FullAiReleaseQc.Result qc,
                        File workDirectory,
                        String base,
                        AudioAnalyzer.Progress progress) throws Exception {
        Result result = new Result();
        String safe = safeBase(base);
        result.report = new File(workDirectory, safe + "_FullAI_QC.json");
        result.confidenceMap = new File(workDirectory,
                safe + "_FullAI_Confidence_Map.txt");
        result.preparationReport = new File(workDirectory,
                safe + "_Radio_Preparation.txt");
        result.checksums = new File(workDirectory, safe + "_FullAI_SHA256.txt");
        result.zip = new File(workDirectory, safe + "_FullAI_Package.zip");
        qc.releaseFormats = ReleaseFormats.create(qc.finalMaster, qc.finalMasterVerification,
                workDirectory, base, progress);
        write(result.report, FullAiReportWriter.create(source, brain, engine, qc));
        write(result.confidenceMap, MasterConfidenceMap.text(
                MasterConfidenceMap.build(source, engine, qc)));
        write(result.preparationReport, RadioPreparation.report(qc));

        ArrayList<File> payload = new ArrayList<>();
        payload.add(qc.finalMaster);
        if (qc.releaseFormats.complete()) {
            payload.add(qc.releaseFormats.wav16);
            payload.add(qc.releaseFormats.mp3);
        }
        if (qc.finalAac != null && qc.finalAac.exists()) payload.add(qc.finalAac);
        if (qc.translationComposite != null && qc.translationComposite.file != null
                && qc.translationComposite.file.exists()) {
            payload.add(qc.translationComposite.file);
        }
        payload.add(result.report);
        payload.add(result.confidenceMap);
        payload.add(result.preparationReport);

        StringBuilder manifest = new StringBuilder();
        for (File file : payload) {
            manifest.append(Hashing.sha256(file)).append("  ")
                    .append(file.getName()).append('\n');
        }
        write(result.checksums, manifest.toString());
        payload.add(result.checksums);
        zip(result.zip, payload, progress);
        verify(result.zip, payload);
        result.zipSha256 = Hashing.sha256(result.zip);
        return result;
    }

    private static void zip(File output, List<File> files,
                            AudioAnalyzer.Progress progress) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(
                new FileOutputStream(output), 256 * 1024))) {
            zip.setLevel(1);
            byte[] buffer = new byte[256 * 1024];
            for (int index = 0; index < files.size(); index++) {
                File file = files.get(index);
                ZipEntry entry = new ZipEntry(file.getName());
                entry.setTime(file.lastModified());
                zip.putNextEntry(entry);
                try (InputStream input = new BufferedInputStream(
                        new FileInputStream(file), 256 * 1024)) {
                    int read;
                    while ((read = input.read(buffer)) >= 0) {
                        if (read > 0) zip.write(buffer, 0, read);
                        if (Thread.currentThread().isInterrupted()) {
                            throw new IOException("Full AI package creation cancelled");
                        }
                    }
                }
                zip.closeEntry();
                progress.update("Building verified Full AI package "
                                + (index + 1) + "/" + files.size(),
                        (int) Math.round(100.0 * (index + 1) / files.size()));
            }
        }
    }

    private static void verify(File archive, List<File> expected) throws IOException {
        byte[] buffer = new byte[256 * 1024];
        try (ZipFile zip = new ZipFile(archive)) {
            if (zip.size() != expected.size()) {
                throw new IOException("Full AI ZIP listing did not match its payload");
            }
            for (File file : expected) {
                ZipEntry entry = zip.getEntry(file.getName());
                if (entry == null || entry.isDirectory()) {
                    throw new IOException("Full AI ZIP is missing " + file.getName());
                }
                try (InputStream input = zip.getInputStream(entry)) {
                    while (input.read(buffer) >= 0) {}
                }
            }
        }
    }

    private static void write(File file, String value) throws IOException {
        try (OutputStream output = new FileOutputStream(file)) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String safeBase(String value) {
        String out = value == null ? "Song" : value.replaceAll("[^A-Za-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (out.isEmpty()) out = "Song";
        if (out.length() > 28) out = out.substring(0, 28).replaceAll("_+$", "");
        return out;
    }
}
