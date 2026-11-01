package com.dennis.aimasteringlab;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Renders an optional instrument-side masking-pocket A/B master. */
final class MaskingPocketRenderer {
    private static final int BUFFER_FRAMES = 4096;
    private static final double MAX_PER_STEM_NOMINAL_CUT_DB = 0.60;
    private static final double MAX_COMBINED_NOMINAL_CUT_DB = 0.75;

    private MaskingPocketRenderer() {}

    static MasterRenderer.Result render(AudioAnalyzer.StreamProvider source,
                                        AnalysisResult sourceAnalysis,
                                        File stemArchive,
                                        File output,
                                        File workingDirectory,
                                        AudioAnalyzer.Progress progress) throws IOException {
        MaskingPocketModel.Result masking = sourceAnalysis.maskingPockets;
        if (masking == null || !masking.available || !masking.hasCorrections()) {
            throw new IOException("No stem masking event passed the complete correction gate");
        }
        if (sourceAnalysis.wav.channels != 2) {
            throw new IOException("Masking-pocket delta rendering requires a stereo authoritative mix");
        }
        if (!workingDirectory.exists() && !workingDirectory.mkdirs()) {
            throw new IOException("Could not create the private masking render cache");
        }
        File correctedFloat = new File(workingDirectory,
                "masking-delta-premaster-" + System.currentTimeMillis() + ".wav");
        try {
            renderCorrectedFloat(source, sourceAnalysis, stemArchive, masking,
                    correctedFloat, progress);
            AnalysisResult corrected = AudioAnalyzer.analyze(correctedFloat.getName(),
                    () -> new FileInputStream(correctedFloat), progress);
            if (corrected.wav.frames != sourceAnalysis.wav.frames
                    || corrected.wav.sampleRate != sourceAnalysis.wav.sampleRate
                    || corrected.wav.channels != 2
                    || !corrected.wav.floatingPoint) {
                throw new IOException("Masking-pocket pre-master failed format or duration verification");
            }
            progress.update("Masking pockets: loudness-matching A/B test master", 0);
            MasterRenderer.Result result = MasterRenderer.render(
                    () -> new FileInputStream(correctedFloat), corrected,
                    corrected.appliedSafeGainDb, output, progress);
            result.maskingPocketGuided = true;
            result.maskingPocketEvents = masking.events.size();
            result.maskingPocketStems = uniqueStemCount(masking.events);
            result.maskingMaximumCutDb = masking.maximumCutDb;
            result.maskingMaximumCombinedCutDb = masking.maximumCombinedNominalCutDb;
            result.maskingDeltaScale = masking.deltaScale;
            result.aiCorrectedPremasterLufs = corrected.integratedLufs;
            result.aiCorrectedPremasterTruePeakDbtp = corrected.truePeakEstimateDbtp;
            result.maskingCorrectionSummary = String.format(Locale.US,
                    "%d local frequency pockets across %d instrument stems; maximum per-event cut %.2f dB; maximum combined nominal cut %.2f dB; processed-minus-original delta scale %.2f; stereo position retained",
                    result.maskingPocketEvents, result.maskingPocketStems,
                    result.maskingMaximumCutDb, result.maskingMaximumCombinedCutDb,
                    result.maskingDeltaScale);
            return result;
        } finally {
            if (correctedFloat.exists() && !correctedFloat.delete()) {
                correctedFloat.deleteOnExit();
            }
        }
    }

    static void renderCorrectedFloat(AudioAnalyzer.StreamProvider source,
                                     AnalysisResult analysis,
                                     File stemArchive,
                                     MaskingPocketModel.Result masking,
                                     File output,
                                     AudioAnalyzer.Progress progress) throws IOException {
        long dataBytes = analysis.wav.frames * 2L * 4L;
        if (dataBytes > 0xffffffffL - 36L) {
            throw new IOException("The masking correction intermediate would exceed the RIFF/WAV 4 GB limit");
        }
        Map<String, List<MaskingPocketModel.Event>> grouped = group(masking.events);
        if (grouped.isEmpty()) throw new IOException("Masking event grouping produced no stem paths");

        progress.update("Masking pockets: rendering protected 32-bit-float stem deltas", 0);
        ArrayList<StemStream> stemStreams = new ArrayList<>();
        try (ZipFile zip = new ZipFile(stemArchive);
             WavFile.Reader mix = WavFile.open(source.open());
             OutputStream raw = new BufferedOutputStream(
                     new FileOutputStream(output), 256 * 1024)) {
            for (Map.Entry<String, List<MaskingPocketModel.Event>> entry : grouped.entrySet()) {
                StemInventory.Item expected = findStem(analysis, entry.getKey());
                if (expected == null || expected.wavInfo == null) {
                    throw new IOException("The analysed competing stem is no longer in the inventory: "
                            + entry.getKey());
                }
                ZipEntry zipEntry = zip.getEntry(entry.getKey());
                if (zipEntry == null || zipEntry.isDirectory()) {
                    throw new IOException("The selected archive no longer contains " + entry.getKey());
                }
                if (expected.crc32 >= 0 && zipEntry.getCrc() >= 0
                        && expected.crc32 != zipEntry.getCrc()) {
                    throw new IOException("Competing-stem CRC changed after analysis: " + entry.getKey());
                }
                InputStream input = zip.getInputStream(zipEntry);
                WavFile.Reader reader;
                try {
                    reader = WavFile.open(input);
                } catch (IOException error) {
                    input.close();
                    throw error;
                }
                if (reader.info.sampleRate != mix.info.sampleRate
                        || reader.info.channels < 1 || reader.info.channels > 2
                        || reader.info.frames != expected.wavInfo.frames) {
                    reader.close();
                    throw new IOException("Competing-stem format changed after analysis: "
                            + entry.getKey());
                }
                stemStreams.add(new StemStream(entry.getKey(), expected,
                        reader, entry.getValue(), mix.info.sampleRate,
                        analysis.wav.durationSeconds));
            }

            writeFloatHeader(raw, mix.info.sampleRate, 2, dataBytes);
            FloatWriter writer = new FloatWriter(raw);
            float[][] mixSamples = new float[2][BUFFER_FRAMES];
            long framesDone = 0;
            int mixRead;
            while ((mixRead = mix.readFrames(mixSamples, BUFFER_FRAMES)) > 0) {
                for (StemStream stream : stemStreams) stream.read(mixRead);
                for (int frame = 0; frame < mixRead; frame++) {
                    double seconds = framesDone / (double) mix.info.sampleRate;
                    double totalNominalCut = 0.0;
                    for (StemStream stream : stemStreams) {
                        stream.prepare(frame, seconds);
                        totalNominalCut += stream.nominalCutDb;
                    }
                    double globalScale = totalNominalCut <= MAX_COMBINED_NOMINAL_CUT_DB
                            ? 1.0 : MAX_COMBINED_NOMINAL_CUT_DB / totalNominalCut;
                    double deltaLeft = 0.0;
                    double deltaRight = 0.0;
                    for (StemStream stream : stemStreams) {
                        deltaLeft += stream.leftDelta(globalScale);
                        deltaRight += stream.rightDelta(globalScale);
                    }
                    double left = finite(mixSamples[0][frame])
                            + masking.deltaScale * deltaLeft;
                    double right = finite(mixSamples[1][frame])
                            + masking.deltaScale * deltaRight;
                    writer.frame(left, right);
                    framesDone++;
                }
                int percent = (int) Math.min(92,
                        Math.round(92.0 * framesDone / Math.max(1L, mix.info.frames)));
                progress.update("Masking pockets: rendering protected 32-bit-float stem deltas",
                        percent);
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("Masking-pocket render cancelled");
                }
            }
            writer.finish();
            if (framesDone != analysis.wav.frames) {
                throw new IOException("Masking-pocket frame accounting failed");
            }
            for (StemStream stream : stemStreams) stream.verifyComplete();
        } catch (IOException error) {
            if (output.exists() && !output.delete()) output.deleteOnExit();
            throw error;
        } finally {
            IOException closeError = null;
            for (StemStream stream : stemStreams) {
                try {
                    stream.close();
                } catch (IOException error) {
                    closeError = error;
                }
            }
            if (closeError != null && output.exists() && !output.delete()) output.deleteOnExit();
        }
    }

    private static Map<String, List<MaskingPocketModel.Event>> group(
            List<MaskingPocketModel.Event> events) {
        LinkedHashMap<String, List<MaskingPocketModel.Event>> grouped = new LinkedHashMap<>();
        for (MaskingPocketModel.Event event : events) {
            List<MaskingPocketModel.Event> values = grouped.get(event.stemPath);
            if (values == null) {
                values = new ArrayList<>();
                grouped.put(event.stemPath, values);
            }
            values.add(event);
        }
        for (List<MaskingPocketModel.Event> values : grouped.values()) {
            values.sort(Comparator.comparingDouble(event -> event.startSeconds));
        }
        return grouped;
    }

    private static int uniqueStemCount(List<MaskingPocketModel.Event> events) {
        return group(events).size();
    }

    private static StemInventory.Item findStem(AnalysisResult analysis, String path) {
        for (StemInventory.Item item : analysis.stems) {
            if (item.path != null && item.path.equals(path)) return item;
        }
        return null;
    }

    private static double gainDelta(double gainDb) {
        return Math.abs(gainDb) < 1.0e-12 ? 0.0
                : Math.pow(10.0, gainDb / 20.0) - 1.0;
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double smooth(double value) {
        double x = clamp(value, 0.0, 1.0);
        return x * x * (3.0 - 2.0 * x);
    }

    private static void writeFloatHeader(OutputStream out, int sampleRate,
                                         int channels, long dataBytes) throws IOException {
        int blockAlign = channels * 4;
        LittleEndian.writeAscii(out, "RIFF");
        LittleEndian.writeU32(out, 36L + dataBytes);
        LittleEndian.writeAscii(out, "WAVE");
        LittleEndian.writeAscii(out, "fmt ");
        LittleEndian.writeU32(out, 16);
        LittleEndian.writeU16(out, 3);
        LittleEndian.writeU16(out, channels);
        LittleEndian.writeU32(out, sampleRate);
        LittleEndian.writeU32(out, (long) sampleRate * blockAlign);
        LittleEndian.writeU16(out, blockAlign);
        LittleEndian.writeU16(out, 32);
        LittleEndian.writeAscii(out, "data");
        LittleEndian.writeU32(out, dataBytes);
    }

    private static final class StemStream implements java.io.Closeable {
        final String path;
        final StemInventory.Item expected;
        final WavFile.Reader reader;
        final float[][] samples;
        final MaskControl control;
        final MaskingPocketModel.BandBank leftBank;
        final MaskingPocketModel.BandBank rightBank;
        final double[] leftBands = new double[4];
        final double[] rightBands = new double[4];
        final double[] gainDb = new double[4];
        int framesInBlock;
        long framesRead;
        double nominalCutDb;

        StemStream(String path, StemInventory.Item expected, WavFile.Reader reader,
                   List<MaskingPocketModel.Event> events, int sampleRate,
                   double durationSeconds) {
            this.path = path;
            this.expected = expected;
            this.reader = reader;
            this.samples = new float[reader.info.channels][BUFFER_FRAMES];
            this.control = new MaskControl(events, durationSeconds);
            this.leftBank = new MaskingPocketModel.BandBank(sampleRate);
            this.rightBank = reader.info.channels == 2
                    ? new MaskingPocketModel.BandBank(sampleRate) : null;
        }

        void read(int requested) throws IOException {
            int value = reader.readFrames(samples, requested);
            framesInBlock = Math.max(0, value);
            framesRead += framesInBlock;
        }

        void prepare(int frame, double seconds) {
            ArraysFill.zero(leftBands);
            ArraysFill.zero(rightBands);
            ArraysFill.zero(gainDb);
            nominalCutDb = 0.0;
            if (frame >= framesInBlock) return;
            double left = finite(samples[0][frame]);
            double right = reader.info.channels == 2
                    ? finite(samples[1][frame]) : left;
            leftBank.process(left, leftBands);
            if (rightBank != null) rightBank.process(right, rightBands);
            else System.arraycopy(leftBands, 0, rightBands, 0, leftBands.length);
            control.gainDbAt(seconds, gainDb);
            for (double value : gainDb) nominalCutDb += -Math.min(0.0, value);
            if (nominalCutDb > MAX_PER_STEM_NOMINAL_CUT_DB) {
                double scale = MAX_PER_STEM_NOMINAL_CUT_DB / nominalCutDb;
                for (int i = 0; i < gainDb.length; i++) gainDb[i] *= scale;
                nominalCutDb = MAX_PER_STEM_NOMINAL_CUT_DB;
            }
        }

        double leftDelta(double globalScale) {
            return delta(leftBands, globalScale);
        }

        double rightDelta(double globalScale) {
            return delta(rightBands, globalScale);
        }

        private double delta(double[] bands, double globalScale) {
            double value = 0.0;
            for (int band = 0; band < bands.length; band++) {
                value += gainDelta(gainDb[band] * globalScale) * bands[band];
            }
            return value;
        }

        void verifyComplete() throws IOException {
            if (framesRead != expected.wavInfo.frames) {
                throw new IOException("Competing stem ended unexpectedly during rendering: " + path);
            }
        }

        @Override public void close() throws IOException {
            reader.close();
        }
    }

    private static final class MaskControl {
        private static final double ATTACK_SECONDS = 0.080;
        private static final double RELEASE_SECONDS = 0.140;
        private final List<MaskingPocketModel.Event> events = new ArrayList<>();
        private final double protectedEndingStart;
        private int cursor;

        MaskControl(List<MaskingPocketModel.Event> source, double durationSeconds) {
            protectedEndingStart = Math.max(2.0,
                    durationSeconds - Math.max(8.0, durationSeconds * 0.04));
            events.addAll(source);
            events.sort(Comparator.comparingDouble(event -> event.startSeconds));
        }

        void gainDbAt(double seconds, double[] output) {
            if (seconds < 2.0 || seconds >= protectedEndingStart) return;
            while (cursor < events.size()
                    && seconds > events.get(cursor).endSeconds + RELEASE_SECONDS) cursor++;
            for (int i = Math.max(0, cursor - 1); i < Math.min(events.size(), cursor + 4); i++) {
                MaskingPocketModel.Event event = events.get(i);
                if (seconds < event.startSeconds - ATTACK_SECONDS
                        || seconds > event.endSeconds + RELEASE_SECONDS) continue;
                double envelope;
                if (seconds < event.startSeconds) {
                    envelope = smooth((seconds - event.startSeconds + ATTACK_SECONDS)
                            / ATTACK_SECONDS);
                } else if (seconds <= event.endSeconds) {
                    envelope = 1.0;
                } else {
                    envelope = smooth(1.0 - (seconds - event.endSeconds)
                            / RELEASE_SECONDS);
                }
                output[event.bandIndex] += event.attenuationDb * envelope;
            }
            for (int band = 0; band < output.length; band++) {
                output[band] = clamp(output[band], -MAX_PER_STEM_NOMINAL_CUT_DB, 0.0);
            }
        }
    }

    private static final class ArraysFill {
        static void zero(double[] values) {
            for (int i = 0; i < values.length; i++) values[i] = 0.0;
        }
    }

    private static final class FloatWriter {
        private final OutputStream output;
        private final byte[] buffer = new byte[256 * 1024];
        private int position;

        FloatWriter(OutputStream output) {
            this.output = output;
        }

        void frame(double left, double right) throws IOException {
            sample(left);
            sample(right);
        }

        private void sample(double sample) throws IOException {
            if (position + 4 > buffer.length) flush();
            int bits = Float.floatToIntBits((float) finite(sample));
            buffer[position++] = (byte) (bits & 0xff);
            buffer[position++] = (byte) ((bits >>> 8) & 0xff);
            buffer[position++] = (byte) ((bits >>> 16) & 0xff);
            buffer[position++] = (byte) ((bits >>> 24) & 0xff);
        }

        void finish() throws IOException {
            flush();
        }

        private void flush() throws IOException {
            if (position > 0) output.write(buffer, 0, position);
            position = 0;
        }
    }
}
