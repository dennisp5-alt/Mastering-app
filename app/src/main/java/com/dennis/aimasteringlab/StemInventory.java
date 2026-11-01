package com.dennis.aimasteringlab;

import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.CheckedInputStream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class StemInventory {
    private static final int MAX_ENTRIES = 512;
    private static final int MAX_WAV_STEMS = 64;
    private static final long MAX_ENTRY_BYTES = 4L * 1024L * 1024L * 1024L;
    private static final long MAX_TOTAL_BYTES = 8L * 1024L * 1024L * 1024L;
    private static final long RATIO_CHECK_BYTES = 64L * 1024L * 1024L;
    private static final double MAX_COMPRESSION_RATIO = 500.0;
    static final class Item {
        String path;
        long uncompressedBytes;
        long compressedBytes;
        long crc32;
        boolean directory;
        boolean wav;
        WavFile.Info wavInfo;
        boolean crcVerified;
        String error;
        double rmsDbfs = Double.NaN;
        double activeRmsDbfs = Double.NaN;
        double activeFraction = Double.NaN;
        double peakDbfs = Double.NaN;
        double dcOffset = Double.NaN;
        double crestFactorDb = Double.NaN;
        long clippedSamples;
    }

    static List<Item> inspect(File archive, AudioAnalyzer.Progress progress) throws IOException {
        ArrayList<Item> items = new ArrayList<>();
        try (ZipFile zip = new ZipFile(archive)) {
            if (zip.size() > MAX_ENTRIES) {
                throw new IOException("Stem archive contains more than 512 entries");
            }
            Enumeration<? extends ZipEntry> entries = zip.entries();
            int count = 0;
            int wavCount = 0;
            long totalUncompressed = 0L;
            Set<String> paths = new HashSet<>();
            int total = zip.size();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (++count > MAX_ENTRIES) throw new IOException("Stem archive contains more than 512 entries");
                Item item = new Item();
                item.path = entry.getName();
                validatePath(item.path, paths);
                item.uncompressedBytes = entry.getSize();
                item.compressedBytes = entry.getCompressedSize();
                item.crc32 = entry.getCrc();
                item.directory = entry.isDirectory();
                item.wav = !item.directory && item.path.toLowerCase(Locale.US).endsWith(".wav");
                if (!item.directory) {
                    if (item.uncompressedBytes < 0 || item.compressedBytes < 0) {
                        throw new IOException("Archive entry has unknown declared size: " + item.path);
                    }
                    if (item.uncompressedBytes > MAX_ENTRY_BYTES) {
                        throw new IOException("Archive entry exceeds the 4 GB guarded limit: " + item.path);
                    }
                    if (Long.MAX_VALUE - totalUncompressed < item.uncompressedBytes
                            || (totalUncompressed += item.uncompressedBytes) > MAX_TOTAL_BYTES) {
                        throw new IOException("Stem archive exceeds the 8 GB guarded expansion limit");
                    }
                    if (item.uncompressedBytes > RATIO_CHECK_BYTES
                            && (item.compressedBytes == 0
                            || item.uncompressedBytes / (double) item.compressedBytes
                            > MAX_COMPRESSION_RATIO)) {
                        throw new IOException("Suspicious archive compression ratio: " + item.path);
                    }
                    if (item.wav && ++wavCount > MAX_WAV_STEMS) {
                        throw new IOException("Stem archive contains more than 64 WAV stems");
                    }
                    try (InputStream raw = zip.getInputStream(entry);
                         CheckedInputStream checked = new CheckedInputStream(raw, new CRC32())) {
                        if (item.wav) {
                            try (WavFile.Reader reader = WavFile.open(new NonClosingInputStream(checked))) {
                                item.wavInfo = reader.info;
                                float[][] samples = new float[reader.info.channels][4096];
                                double sum = 0.0;
                                double sumSquares = 0.0;
                                double peak = 0.0;
                                long sampleCount = 0;
                                long clips = 0;
                                int blockHop = Math.max(1, reader.info.sampleRate / 10);
                                int blockFrames = 0;
                                double blockSquares = 0.0;
                                long blockSamples = 0;
                                ArrayList<Double> blockMeanSquares = new ArrayList<>();
                                double clipThreshold = reader.info.floatingPoint ? 1.0
                                        : 1.0 - 1.0 / Math.pow(2.0,
                                        reader.info.validBitsPerSample - 1);
                                int frames;
                                while ((frames = reader.readFrames(samples, 4096)) > 0) {
                                    for (int frame = 0; frame < frames; frame++) {
                                        for (int channel = 0; channel < reader.info.channels; channel++) {
                                            double value = samples[channel][frame];
                                            if (!Double.isFinite(value)) value = 0.0;
                                            sum += value;
                                            sumSquares += value * value;
                                            blockSquares += value * value;
                                            sampleCount++;
                                            blockSamples++;
                                            peak = Math.max(peak, Math.abs(value));
                                            if (Math.abs(value) >= clipThreshold) clips++;
                                        }
                                        blockFrames++;
                                        if (blockFrames == blockHop) {
                                            blockMeanSquares.add(blockSquares
                                                    / Math.max(1L, blockSamples));
                                            blockFrames = 0;
                                            blockSquares = 0.0;
                                            blockSamples = 0;
                                        }
                                    }
                                    if (Thread.currentThread().isInterrupted()) {
                                        throw new IOException("Stem inventory cancelled");
                                    }
                                }
                                if (blockSamples > 0) {
                                    blockMeanSquares.add(blockSquares / blockSamples);
                                }
                                item.rmsDbfs = AudioAnalyzer.toDb(Math.sqrt(
                                        sumSquares / Math.max(1L, sampleCount)));
                                item.peakDbfs = AudioAnalyzer.toDb(peak);
                                item.dcOffset = sum / Math.max(1L, sampleCount);
                                item.crestFactorDb = item.peakDbfs - item.rmsDbfs;
                                item.clippedSamples = clips;
                                setTrackRelativeActiveMetrics(item, blockMeanSquares);
                            }
                        }
                        byte[] discard = new byte[64 * 1024];
                        while (checked.read(discard) >= 0) {
                            if (Thread.currentThread().isInterrupted()) {
                                throw new IOException("Stem inventory cancelled");
                            }
                        }
                        item.crcVerified = entry.getCrc() < 0
                                || checked.getChecksum().getValue() == entry.getCrc();
                        if (!item.crcVerified) item.error = "CRC-32 mismatch";
                    } catch (Exception e) {
                        item.error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    }
                }
                items.add(item);
                progress.update("Validating stems: " + count + "/" + total, -1);
                if (Thread.currentThread().isInterrupted()) throw new IOException("Stem inventory cancelled");
            }
        }
        return items;
    }

    private static void validatePath(String path, Set<String> paths) throws IOException {
        if (path == null || path.isEmpty() || path.indexOf('\\') >= 0
                || path.startsWith("/") || path.startsWith("~")
                || path.matches("^[A-Za-z]:.*")) {
            throw new IOException("Unsafe archive path was rejected");
        }
        String[] components = path.split("/", -1);
        for (int i = 0; i < components.length; i++) {
            String component = components[i];
            boolean finalDirectoryMarker = component.isEmpty()
                    && i == components.length - 1 && path.endsWith("/");
            if (finalDirectoryMarker) continue;
            if (component.isEmpty() || ".".equals(component) || "..".equals(component)) {
                throw new IOException("Unsafe archive traversal path was rejected: " + path);
            }
        }
        String key = path.toLowerCase(Locale.US);
        if (!paths.add(key)) {
            throw new IOException("Duplicate archive path was rejected: " + path);
        }
    }

    private static void setTrackRelativeActiveMetrics(Item item,
                                                       List<Double> blockMeanSquares) {
        double preliminary = 0.0;
        int nonSilent = 0;
        for (double energy : blockMeanSquares) {
            if (energy > 1.0e-12) {
                preliminary += energy;
                nonSilent++;
            }
        }
        if (nonSilent == 0) return;
        preliminary /= nonSilent;
        double relativeGate = preliminary * 0.01;
        double active = 0.0;
        int activeBlocks = 0;
        for (double energy : blockMeanSquares) {
            if (energy >= relativeGate) {
                active += energy;
                activeBlocks++;
            }
        }
        if (activeBlocks > 0) {
            item.activeRmsDbfs = AudioAnalyzer.toDb(Math.sqrt(active / activeBlocks));
            item.activeFraction = activeBlocks / (double) Math.max(1, blockMeanSquares.size());
        }
    }

    static void compareAgainstMix(AnalysisResult result) {
        result.wavStemCount = 0;
        result.crcVerifiedStemCount = 0;
        result.stemDurationMismatchCount = 0;
        result.shortestStemDurationSeconds = Double.NaN;
        result.longestStemDurationSeconds = Double.NaN;
        result.largestStemDurationDeltaSeconds = 0.0;
        result.commonStemTailShortfallSeconds = 0.0;
        double mixDuration = result.wav.durationSeconds;
        double tolerance = 1.01 / result.wav.sampleRate;
        for (Item item : result.stems) {
            if (!item.wav || item.wavInfo == null) continue;
            result.wavStemCount++;
            if (item.crcVerified) result.crcVerifiedStemCount++;
            double duration = item.wavInfo.durationSeconds;
            if (!Double.isFinite(result.shortestStemDurationSeconds)
                    || duration < result.shortestStemDurationSeconds) {
                result.shortestStemDurationSeconds = duration;
            }
            if (!Double.isFinite(result.longestStemDurationSeconds)
                    || duration > result.longestStemDurationSeconds) {
                result.longestStemDurationSeconds = duration;
            }
            double delta = mixDuration - duration;
            if (Math.abs(delta) > tolerance) result.stemDurationMismatchCount++;
            result.largestStemDurationDeltaSeconds = Math.max(
                    result.largestStemDurationDeltaSeconds, Math.abs(delta));
        }
        if (result.wavStemCount > 0
                && result.stemDurationMismatchCount == result.wavStemCount
                && result.longestStemDurationSeconds < mixDuration - tolerance
                && result.longestStemDurationSeconds - result.shortestStemDurationSeconds <= tolerance) {
            result.commonStemTailShortfallSeconds = mixDuration - result.longestStemDurationSeconds;
        }
    }

    private static final class NonClosingInputStream extends FilterInputStream {
        NonClosingInputStream(InputStream in) {
            super(in);
        }

        @Override public void close() {
            // ZipFile and CheckedInputStream retain ownership for CRC validation.
        }
    }
}
