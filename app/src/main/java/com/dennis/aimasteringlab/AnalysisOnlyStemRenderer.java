package com.dennis.aimasteringlab;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Uses aligned low-trust stems only as event-control evidence. Every rendered
 * sample comes from the authoritative stereo mix; no stem sample, stem sum or
 * processed-minus-original stem delta is read by this renderer.
 */
final class AnalysisOnlyStemRenderer {
    static final class Stats {
        int phraseRegions;
        int deEssEvents;
        int maskingEvents;
        int stemsUsed;
        double meanConfidence;
        double maximumDeEssCutDb;
        double maximumMaskingCutDb;
        double maximumCombinedNominalEditDb;
    }

    private static final int BUFFER_FRAMES = 4096;
    private static final double DEESS_SCALE = 0.40;
    private static final double MASKING_SCALE = 0.55;
    private static final double MAX_DEESS_CUT_DB = 0.35;
    private static final double MAX_MASKING_EVENT_CUT_DB = 0.35;
    private static final double MAX_MASKING_COMBINED_CUT_DB = 0.45;
    private static final double MAX_TOTAL_NOMINAL_EDIT_DB = 0.55;

    private AnalysisOnlyStemRenderer() {}

    static Stats renderCorrectedFloat(AudioAnalyzer.StreamProvider source,
                                      AnalysisResult analysis,
                                      File output,
                                      AudioAnalyzer.Progress progress) throws IOException {
        if (!analysis.stemArchiveAnalysisOnlyAccepted) {
            throw new IOException("Analysis-only stem trust was not established");
        }
        if (analysis.wav.channels != 2 || analysis.wav.sampleRate != 48000) {
            throw new IOException("Analysis-only guidance requires a 48 kHz stereo authoritative mix");
        }
        VocalHumanityModel.Result vocal = analysis.vocalHumanity;
        MaskingPocketModel.Result masking = analysis.maskingPockets;
        boolean deEssAvailable = vocal != null && vocal.analysisOnly
                && !vocal.deEssEvents.isEmpty();
        boolean maskingAvailable = masking != null && masking.analysisOnly
                && !masking.events.isEmpty();
        if (!deEssAvailable && !maskingAvailable) {
            throw new IOException("Analysis-only stems produced no eligible stereo-control event");
        }

        long dataBytes = analysis.wav.frames * 2L * 4L;
        if (dataBytes > 0xffffffffL - 36L) {
            throw new IOException("Analysis-only intermediate would exceed the RIFF/WAV 4 GB limit");
        }
        Stats stats = describe(vocal, masking);
        DeEssControl deEss = new DeEssControl(vocal);
        MaskingControl[] maskingControls = new MaskingControl[4];
        for (int band = 0; band < maskingControls.length; band++) {
            maskingControls[band] = new MaskingControl(masking, band);
        }

        int rate = analysis.wav.sampleRate;
        BandPass deEssBand = new BandPass(rate, 4800.0, 11000.0);
        MaskingPocketModel.BandBank sideBands = new MaskingPocketModel.BandBank(rate);
        double[] sideBandValues = new double[4];
        double[] maskingDb = new double[4];
        long introEnd = Math.round(2.0 * rate);
        long protectedFrames = Math.round(Math.max(8.0,
                analysis.wav.durationSeconds * 0.04) * rate);
        long endingStart = Math.max(0L, analysis.wav.frames - protectedFrames);

        progress.update("Analysis-only stems: rendering authoritative-mix control candidate", 0);
        try (WavFile.Reader reader = WavFile.open(source.open());
             OutputStream raw = new BufferedOutputStream(
                     new FileOutputStream(output), 256 * 1024)) {
            writeFloatHeader(raw, rate, 2, dataBytes);
            FloatWriter writer = new FloatWriter(raw);
            float[][] samples = new float[2][BUFFER_FRAMES];
            long absolute = 0L;
            int read;
            while ((read = reader.readFrames(samples, BUFFER_FRAMES)) > 0) {
                for (int frame = 0; frame < read; frame++, absolute++) {
                    double left = finite(samples[0][frame]);
                    double right = finite(samples[1][frame]);
                    double mid = 0.5 * (left + right);
                    double side = 0.5 * (left - right);
                    double deEssValue = deEssBand.process(mid);
                    sideBands.process(side, sideBandValues);

                    double deEssDb = 0.0;
                    for (int band = 0; band < maskingDb.length; band++) maskingDb[band] = 0.0;
                    if (absolute >= introEnd && absolute < endingStart) {
                        double seconds = absolute / (double) rate;
                        deEssDb = deEss.gainDbAt(seconds);
                        double maskingTotal = 0.0;
                        for (int band = 0; band < maskingDb.length; band++) {
                            maskingDb[band] = maskingControls[band].gainDbAt(seconds);
                            maskingTotal += -maskingDb[band];
                        }
                        if (maskingTotal > MAX_MASKING_COMBINED_CUT_DB) {
                            double scale = MAX_MASKING_COMBINED_CUT_DB / maskingTotal;
                            for (int band = 0; band < maskingDb.length; band++) {
                                maskingDb[band] *= scale;
                            }
                            maskingTotal = MAX_MASKING_COMBINED_CUT_DB;
                        }
                        double total = -deEssDb + maskingTotal;
                        if (total > MAX_TOTAL_NOMINAL_EDIT_DB) {
                            double scale = MAX_TOTAL_NOMINAL_EDIT_DB / total;
                            deEssDb *= scale;
                            for (int band = 0; band < maskingDb.length; band++) {
                                maskingDb[band] *= scale;
                            }
                            maskingTotal *= scale;
                            total = MAX_TOTAL_NOMINAL_EDIT_DB;
                        }
                        stats.maximumDeEssCutDb = Math.max(
                                stats.maximumDeEssCutDb, -deEssDb);
                        stats.maximumMaskingCutDb = Math.max(
                                stats.maximumMaskingCutDb, maskingTotal);
                        stats.maximumCombinedNominalEditDb = Math.max(
                                stats.maximumCombinedNominalEditDb, total);
                    }

                    double correctedMid = mid + gainDelta(deEssDb) * deEssValue;
                    double correctedSide = side;
                    for (int band = 0; band < maskingDb.length; band++) {
                        correctedSide += gainDelta(maskingDb[band]) * sideBandValues[band];
                    }
                    writer.frame(correctedMid + correctedSide,
                            correctedMid - correctedSide);
                }
                int percent = (int) Math.min(94,
                        Math.round(94.0 * absolute / Math.max(1L, reader.info.frames)));
                progress.update("Analysis-only stems: rendering authoritative-mix control candidate",
                        percent);
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("Analysis-only stem rendering cancelled");
                }
            }
            writer.finish();
            if (absolute != analysis.wav.frames) {
                throw new IOException("Analysis-only render frame accounting failed");
            }
        } catch (IOException error) {
            if (output.exists() && !output.delete()) output.deleteOnExit();
            throw error;
        }
        progress.update("Analysis-only stem control candidate complete", 100);
        return stats;
    }

    private static Stats describe(VocalHumanityModel.Result vocal,
                                  MaskingPocketModel.Result masking) {
        Stats out = new Stats();
        double confidence = 0.0;
        int count = 0;
        if (vocal != null) {
            out.phraseRegions = vocal.phrases.size();
            out.deEssEvents = vocal.deEssEvents.size();
            for (VocalHumanityModel.DeEssEvent event : vocal.deEssEvents) {
                confidence += event.confidence;
                count++;
            }
        }
        Set<String> paths = new HashSet<>();
        if (masking != null) {
            out.maskingEvents = masking.events.size();
            for (MaskingPocketModel.Event event : masking.events) {
                paths.add(event.stemPath);
                confidence += event.confidence;
                count++;
            }
        }
        if (vocal != null && vocal.leadStemPath != null) paths.add(vocal.leadStemPath);
        out.stemsUsed = paths.size();
        out.meanConfidence = count == 0 ? 0.0 : confidence / count;
        return out;
    }

    private static double gainDelta(double gainDb) {
        return gainDb == 0.0 ? 0.0 : Math.pow(10.0, gainDb / 20.0) - 1.0;
    }

    private static double envelope(double seconds, double start, double end,
                                   double attack, double release) {
        if (seconds < start || seconds > end + release) return 0.0;
        if (seconds <= end) {
            return smooth(Math.min(1.0, (seconds - start) / attack));
        }
        return smooth(1.0 - Math.min(1.0, (seconds - end) / release));
    }

    private static double smooth(double value) {
        double x = Math.max(0.0, Math.min(1.0, value));
        return x * x * (3.0 - 2.0 * x);
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0;
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

    private static final class DeEssControl {
        private final List<VocalHumanityModel.DeEssEvent> events = new ArrayList<>();
        private int cursor;

        DeEssControl(VocalHumanityModel.Result vocal) {
            if (vocal != null) events.addAll(vocal.deEssEvents);
            events.sort(Comparator.comparingDouble(event -> event.startSeconds));
        }

        double gainDbAt(double seconds) {
            while (cursor < events.size()
                    && seconds > events.get(cursor).endSeconds + 0.080) cursor++;
            double gain = 0.0;
            for (int index = cursor; index < events.size(); index++) {
                VocalHumanityModel.DeEssEvent event = events.get(index);
                if (event.startSeconds > seconds) break;
                gain += Math.max(-MAX_DEESS_CUT_DB,
                        event.attenuationDb * DEESS_SCALE)
                        * envelope(seconds, event.startSeconds, event.endSeconds,
                        0.015, 0.080);
            }
            return Math.max(-MAX_DEESS_CUT_DB, gain);
        }
    }

    private static final class MaskingControl {
        private final List<MaskingPocketModel.Event> events = new ArrayList<>();
        private int cursor;

        MaskingControl(MaskingPocketModel.Result masking, int band) {
            if (masking != null) {
                for (MaskingPocketModel.Event event : masking.events) {
                    if (event.bandIndex == band) events.add(event);
                }
            }
            events.sort(Comparator.comparingDouble(event -> event.startSeconds));
        }

        double gainDbAt(double seconds) {
            while (cursor < events.size()
                    && seconds > events.get(cursor).endSeconds + 0.140) cursor++;
            double gain = 0.0;
            for (int index = cursor; index < events.size(); index++) {
                MaskingPocketModel.Event event = events.get(index);
                if (event.startSeconds > seconds) break;
                gain += Math.max(-MAX_MASKING_EVENT_CUT_DB,
                        event.attenuationDb * MASKING_SCALE)
                        * envelope(seconds, event.startSeconds, event.endSeconds,
                        0.080, 0.140);
            }
            return Math.max(-MAX_MASKING_COMBINED_CUT_DB, gain);
        }
    }

    private static final class BandPass {
        private final Biquad highA;
        private final Biquad highB;
        private final Biquad lowA;
        private final Biquad lowB;

        BandPass(double rate, double low, double high) {
            highA = Biquad.highPass(rate, low);
            highB = Biquad.highPass(rate, low);
            lowA = Biquad.lowPass(rate, Math.min(high, rate * 0.44));
            lowB = Biquad.lowPass(rate, Math.min(high, rate * 0.44));
        }

        double process(double sample) {
            return lowB.process(lowA.process(highB.process(highA.process(sample))));
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
