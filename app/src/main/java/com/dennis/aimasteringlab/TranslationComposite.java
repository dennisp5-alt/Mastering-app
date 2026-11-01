package com.dennis.aimasteringlab;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

final class TranslationComposite {
    static final class SegmentMap {
        String role;
        double sourceStartSeconds;
        double sourceDurationSeconds;
        double compositeStartSeconds;
    }

    static final class Result {
        File file;
        AnalysisResult verification;
        final List<SegmentMap> segments = new ArrayList<>();
    }

    private TranslationComposite() {}

    static Result build(File master, AnalysisResult masterAnalysis,
                        TrackSections sections, File output,
                        AudioAnalyzer.Progress progress) throws IOException {
        return build(() -> new FileInputStream(master), masterAnalysis,
                sections, output, progress);
    }

    static Result build(AudioAnalyzer.StreamProvider master,
                        AnalysisResult masterAnalysis,
                        TrackSections sections, File output,
                        AudioAnalyzer.Progress progress) throws IOException {
        if (sections == null || sections.segments.isEmpty()) {
            throw new IOException("No section candidates were available for the translation composite");
        }
        Result result = new Result();
        result.file = output;
        int sampleRate = masterAnalysis.wav.sampleRate;
        int gapFrames = (int) Math.round(sampleRate * 0.25);
        long totalFrames = 0;
        ArrayList<Long> segmentFrames = new ArrayList<>();
        for (TrackSections.Segment segment : sections.segments) {
            long start = Math.max(0L, Math.round(segment.startSeconds * sampleRate));
            long available = Math.max(0L, masterAnalysis.wav.frames - start);
            long frames = Math.min(available, Math.round(segment.durationSeconds * sampleRate));
            segmentFrames.add(frames);
            totalFrames += frames;
        }
        totalFrames += (long) gapFrames * Math.max(0, sections.segments.size() - 1);
        long dataBytes = totalFrames * 6L;
        if (dataBytes > 0xffffffffL - 36L) throw new IOException("Translation composite is too large");

        try (OutputStream raw = new BufferedOutputStream(new FileOutputStream(output), 256 * 1024)) {
            MasterRenderer.writeHeader(raw, sampleRate, 2, dataBytes, 24);
            Writer writer = new Writer(raw, seed(masterAnalysis.sourceSha256));
            long compositeFrame = 0;
            for (int index = 0; index < sections.segments.size(); index++) {
                TrackSections.Segment segment = sections.segments.get(index);
                long frames = segmentFrames.get(index);
                SegmentMap map = new SegmentMap();
                map.role = segment.role;
                map.sourceStartSeconds = segment.startSeconds;
                map.sourceDurationSeconds = frames / (double) sampleRate;
                map.compositeStartSeconds = compositeFrame / (double) sampleRate;
                result.segments.add(map);
                writeSegment(master, segment.startSeconds, frames, sampleRate, writer,
                        index, sections.segments.size(), progress);
                compositeFrame += frames;
                if (index + 1 < sections.segments.size()) {
                    for (int frame = 0; frame < gapFrames; frame++) writer.frame(0.0, 0.0);
                    compositeFrame += gapFrames;
                }
            }
            writer.finish();
        } catch (IOException e) {
            if (output.exists() && !output.delete()) output.deleteOnExit();
            throw e;
        }
        result.verification = AudioAnalyzer.analyze(output.getName(),
                () -> new FileInputStream(output), progress);
        progress.update("Translation composite verified", 100);
        return result;
    }

    private static void writeSegment(AudioAnalyzer.StreamProvider master,
                                     double startSeconds, long frames,
                                     int sampleRate, Writer writer, int segmentIndex,
                                     int segmentCount, AudioAnalyzer.Progress progress)
            throws IOException {
        try (WavFile.Reader reader = WavFile.open(master.open())) {
            long startFrame = Math.max(0L, Math.round(startSeconds * sampleRate));
            float[][] buffer = new float[2][4096];
            long skipped = 0;
            while (skipped < startFrame) {
                int request = (int) Math.min(buffer[0].length, startFrame - skipped);
                int read = reader.readFrames(buffer, request);
                if (read < 0) return;
                skipped += read;
            }
            long fadeFrames = Math.min(Math.round(sampleRate * 0.050), frames / 4);
            long done = 0;
            while (done < frames) {
                int request = (int) Math.min(buffer[0].length, frames - done);
                int read = reader.readFrames(buffer, request);
                if (read < 0) break;
                for (int frame = 0; frame < read; frame++) {
                    long position = done + frame;
                    double fadeIn = fadeFrames > 0 ? Math.min(1.0, (position + 1.0) / fadeFrames) : 1.0;
                    double fadeOut = fadeFrames > 0
                            ? Math.min(1.0, (frames - position) / (double) fadeFrames) : 1.0;
                    double gain = Math.min(fadeIn, fadeOut);
                    writer.frame(buffer[0][frame] * gain, buffer[1][frame] * gain);
                }
                done += read;
                int percent = (int) Math.round(90.0
                        * (segmentIndex + done / (double) Math.max(1L, frames)) / segmentCount);
                progress.update("Building translation reference: "
                        + (segmentIndex + 1) + "/" + segmentCount, percent);
            }
        }
    }

    private static long seed(String sha256) {
        long value = 0x444d5354524cL;
        if (sha256 != null && sha256.length() >= 16) {
            try { value ^= Long.parseUnsignedLong(sha256.substring(0, 16), 16); }
            catch (NumberFormatException ignored) {}
        }
        return value;
    }

    private static final class Writer {
        private final OutputStream output;
        private final Random random;
        private final byte[] bytes = new byte[256 * 1024 - 2];
        private int position;

        Writer(OutputStream output, long seed) {
            this.output = output;
            this.random = new Random(seed);
        }

        void frame(double left, double right) throws IOException {
            sample(left);
            sample(right);
        }

        void finish() throws IOException {
            flush();
        }

        private void sample(double value) throws IOException {
            if (position + 3 > bytes.length) flush();
            double tpdf = random.nextDouble() - random.nextDouble();
            long q = Math.round(value * 8388608.0 + tpdf);
            q = Math.max(-8388608L, Math.min(8388607L, q));
            int sample = (int) q;
            bytes[position++] = (byte) sample;
            bytes[position++] = (byte) (sample >>> 8);
            bytes[position++] = (byte) (sample >>> 16);
        }

        private void flush() throws IOException {
            if (position > 0) output.write(bytes, 0, position);
            position = 0;
        }
    }
}
