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
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Renders an optional vocal-humanity A/B master using a stem delta around the mix. */
final class VocalGuidedRenderer {
    private static final int BUFFER_FRAMES = 4096;

    private VocalGuidedRenderer() {}

    static MasterRenderer.Result render(AudioAnalyzer.StreamProvider source,
                                        AnalysisResult sourceAnalysis,
                                        File stemArchive,
                                        File output,
                                        File workingDirectory,
                                        AudioAnalyzer.Progress progress) throws IOException {
        VocalHumanityModel.Result vocal = sourceAnalysis.vocalHumanity;
        if (vocal == null || !vocal.available || !vocal.hasCorrections()) {
            throw new IOException("No lead-vocal event passed the vocal-humanity correction gate");
        }
        if (sourceAnalysis.wav.channels != 2) {
            throw new IOException("Vocal delta rendering requires a stereo authoritative mix");
        }
        if (!workingDirectory.exists() && !workingDirectory.mkdirs()) {
            throw new IOException("Could not create the private vocal render cache");
        }
        File correctedFloat = new File(workingDirectory,
                "vocal-delta-premaster-" + System.currentTimeMillis() + ".wav");
        try {
            renderCorrectedFloat(source, sourceAnalysis, stemArchive, vocal,
                    correctedFloat, progress);
            AnalysisResult corrected = AudioAnalyzer.analyze(correctedFloat.getName(),
                    () -> new FileInputStream(correctedFloat), progress);
            if (corrected.wav.frames != sourceAnalysis.wav.frames
                    || corrected.wav.sampleRate != sourceAnalysis.wav.sampleRate
                    || corrected.wav.channels != 2
                    || !corrected.wav.floatingPoint) {
                throw new IOException("Vocal delta pre-master failed format or duration verification");
            }
            progress.update("Vocal humanity: loudness-matching A/B test master", 0);
            MasterRenderer.Result result = MasterRenderer.render(
                    () -> new FileInputStream(correctedFloat), corrected,
                    corrected.appliedSafeGainDb, output, progress);
            result.vocalHumanityGuided = true;
            result.vocalStemPath = vocal.leadStemPath;
            result.vocalRideCount = vocal.rideCount();
            result.vocalDeEssEvents = vocal.deEssEvents.size();
            result.vocalRideDownDb = vocal.maximumRideDownDb;
            result.vocalRideUpDb = vocal.maximumRideUpDb;
            result.vocalMaximumDeEssDb = vocal.maximumDeEssDb;
            result.vocalDeltaScale = vocal.deltaScale;
            result.aiCorrectedPremasterLufs = corrected.integratedLufs;
            result.aiCorrectedPremasterTruePeakDbtp = corrected.truePeakEstimateDbtp;
            String deEssSummary = vocal.deEssEvents.isEmpty()
                    ? "no event de-essing applied"
                    : String.format(Locale.US, "%d event de-ess envelopes to %.2f dB",
                    vocal.deEssEvents.size(), -vocal.maximumDeEssDb);
            result.vocalCorrectionSummary = String.format(Locale.US,
                    "stem processing: %d phrase rides from %+.2f to %+.2f dB; %s; processed-minus-original delta scale %.2f; centered delta only",
                    vocal.rideCount(), vocal.maximumRideDownDb, vocal.maximumRideUpDb,
                    deEssSummary, vocal.deltaScale);
            return result;
        } finally {
            if (correctedFloat.exists() && !correctedFloat.delete()) correctedFloat.deleteOnExit();
        }
    }

    static void renderCorrectedFloat(AudioAnalyzer.StreamProvider source,
                                     AnalysisResult analysis,
                                     File stemArchive,
                                     VocalHumanityModel.Result vocal,
                                     File output,
                                     AudioAnalyzer.Progress progress) throws IOException {
        long dataBytes = analysis.wav.frames * 2L * 4L;
        if (dataBytes > 0xffffffffL - 36L) {
            throw new IOException("The vocal correction intermediate would exceed the RIFF/WAV 4 GB limit");
        }
        StemInventory.Item expected = findStem(analysis, vocal.leadStemPath);
        if (expected == null || expected.wavInfo == null) {
            throw new IOException("The analysed lead stem is no longer available in the stem inventory");
        }

        progress.update("Vocal humanity: rendering protected 32-bit-float stem delta", 0);
        try (ZipFile zip = new ZipFile(stemArchive)) {
            ZipEntry entry = zip.getEntry(vocal.leadStemPath);
            if (entry == null || entry.isDirectory()) {
                throw new IOException("The selected archive no longer contains the analysed lead stem");
            }
            if (expected.crc32 >= 0 && entry.getCrc() >= 0 && expected.crc32 != entry.getCrc()) {
                throw new IOException("Lead-stem CRC changed after analysis; run analysis again");
            }
            try (WavFile.Reader mix = WavFile.open(source.open());
                 InputStream stemInput = zip.getInputStream(entry);
                 WavFile.Reader stem = WavFile.open(stemInput);
                 OutputStream raw = new BufferedOutputStream(new FileOutputStream(output), 256 * 1024)) {
                if (stem.info.sampleRate != mix.info.sampleRate
                        || stem.info.channels < 1 || stem.info.channels > 2
                        || stem.info.frames != expected.wavInfo.frames) {
                    throw new IOException("Lead-stem format changed after analysis; run analysis again");
                }
                writeFloatHeader(raw, mix.info.sampleRate, 2, dataBytes);
                FloatWriter writer = new FloatWriter(raw);
                float[][] mixSamples = new float[2][BUFFER_FRAMES];
                float[][] stemSamples = new float[stem.info.channels][BUFFER_FRAMES];
                RideControl rides = new RideControl(vocal.phrases);
                DeEssControl deEss = new DeEssControl(vocal.deEssEvents);
                double upper = Math.min(11000.0, mix.info.sampleRate * 0.22);
                double lower = Math.min(4800.0, upper * 0.62);
                Biquad belowLower = Biquad.lowPass(mix.info.sampleRate, lower);
                Biquad belowUpper = Biquad.lowPass(mix.info.sampleRate, upper);
                double protectedEndingStart = Math.max(2.0,
                        analysis.wav.durationSeconds
                                - Math.max(8.0, analysis.wav.durationSeconds * 0.04));
                long framesDone = 0;
                long stemFramesRead = 0;
                int mixRead;
                while ((mixRead = mix.readFrames(mixSamples, BUFFER_FRAMES)) > 0) {
                    int stemRead = stem.readFrames(stemSamples, mixRead);
                    if (stemRead < 0) stemRead = 0;
                    stemFramesRead += stemRead;
                    for (int frame = 0; frame < mixRead; frame++) {
                        double left = finite(mixSamples[0][frame]);
                        double right = finite(mixSamples[1][frame]);
                        double delta = 0.0;
                        if (frame < stemRead) {
                            double stemLeft = finite(stemSamples[0][frame]);
                            double stemRight = stem.info.channels == 2
                                    ? finite(stemSamples[1][frame]) : stemLeft;
                            double stemMid = 0.5 * (stemLeft + stemRight);
                            double lowValue = belowLower.process(stemMid);
                            double highValue = belowUpper.process(stemMid);
                            double sibilanceBand = highValue - lowValue;
                            double seconds = framesDone / (double) mix.info.sampleRate;
                            if (seconds >= 2.0 && seconds < protectedEndingStart) {
                                double rideDb = rides.gainDbAt(seconds);
                                double deEssDb = deEss.gainDbAt(seconds);
                                double rideDelta = gainDelta(rideDb) * stemMid;
                                double deEssDelta = gainDelta(deEssDb) * sibilanceBand;
                                delta = vocal.deltaScale * (rideDelta + deEssDelta);
                            }
                        }
                        writer.frame(left + delta, right + delta);
                        framesDone++;
                    }
                    int percent = (int) Math.min(92,
                            Math.round(92.0 * framesDone / Math.max(1L, mix.info.frames)));
                    progress.update("Vocal humanity: rendering protected 32-bit-float stem delta", percent);
                    if (Thread.currentThread().isInterrupted()) {
                        throw new IOException("Vocal delta render cancelled");
                    }
                }
                writer.finish();
                if (framesDone != analysis.wav.frames) {
                    throw new IOException("Vocal delta frame accounting failed");
                }
                if (stemFramesRead != expected.wavInfo.frames) {
                    throw new IOException("Lead stem ended unexpectedly during delta rendering");
                }
            }
        } catch (IOException e) {
            if (output.exists() && !output.delete()) output.deleteOnExit();
            throw e;
        }
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

    private static final class RideControl {
        private static final double ATTACK_SECONDS = 0.100;
        private static final double RELEASE_SECONDS = 0.160;
        private final List<VocalHumanityModel.Phrase> phrases = new ArrayList<>();
        private int cursor;

        RideControl(List<VocalHumanityModel.Phrase> source) {
            for (VocalHumanityModel.Phrase phrase : source) {
                if (Math.abs(phrase.rideDb) >= 0.001) phrases.add(phrase);
            }
            phrases.sort(Comparator.comparingDouble(phrase -> phrase.startSeconds));
        }

        double gainDbAt(double seconds) {
            while (cursor < phrases.size()
                    && seconds > phrases.get(cursor).endSeconds + RELEASE_SECONDS) cursor++;
            double gain = 0.0;
            for (int i = Math.max(0, cursor - 1); i < Math.min(phrases.size(), cursor + 3); i++) {
                VocalHumanityModel.Phrase phrase = phrases.get(i);
                if (seconds < phrase.startSeconds - ATTACK_SECONDS
                        || seconds > phrase.endSeconds + RELEASE_SECONDS) continue;
                double envelope;
                if (seconds < phrase.startSeconds) {
                    envelope = smooth((seconds - phrase.startSeconds + ATTACK_SECONDS)
                            / ATTACK_SECONDS);
                } else if (seconds <= phrase.endSeconds) {
                    envelope = 1.0;
                } else {
                    envelope = smooth(1.0 - (seconds - phrase.endSeconds) / RELEASE_SECONDS);
                }
                gain += phrase.rideDb * envelope;
            }
            return clamp(gain, -0.45, 0.60);
        }
    }

    private static final class DeEssControl {
        private static final double ATTACK_SECONDS = 0.015;
        private static final double RELEASE_SECONDS = 0.080;
        private final List<VocalHumanityModel.DeEssEvent> events = new ArrayList<>();
        private int cursor;

        DeEssControl(List<VocalHumanityModel.DeEssEvent> source) {
            events.addAll(source);
            events.sort(Comparator.comparingDouble(event -> event.startSeconds));
        }

        double gainDbAt(double seconds) {
            while (cursor < events.size()
                    && seconds > events.get(cursor).endSeconds + RELEASE_SECONDS) cursor++;
            if (cursor >= events.size()) return 0.0;
            VocalHumanityModel.DeEssEvent event = events.get(cursor);
            if (seconds < event.startSeconds - ATTACK_SECONDS) return 0.0;
            if (seconds < event.startSeconds) {
                return event.attenuationDb * smooth(
                        (seconds - event.startSeconds + ATTACK_SECONDS) / ATTACK_SECONDS);
            }
            if (seconds <= event.endSeconds) return event.attenuationDb;
            return event.attenuationDb * smooth(
                    1.0 - (seconds - event.endSeconds) / RELEASE_SECONDS);
        }
    }

    private static double smooth(double value) {
        double x = clamp(value, 0.0, 1.0);
        return x * x * (3.0 - 2.0 * x);
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
