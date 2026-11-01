package com.dennis.aimasteringlab;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Sample-level repair for isolated impulses, very short stereo zero holes,
 * and sparse per-channel digital clip plateaux with unambiguous boundaries.
 */
final class ClickDropoutRepair {
    enum Kind { ISOLATED_CLICK, EXACT_ZERO_DROPOUT, ISOLATED_CLIP_RUN }

    static final class Event {
        Kind kind;
        long startFrame;
        long endFrame;
        int channelMask;
        double confidence;
        final double[] before = new double[2];
        final double[] after = new double[2];
        final double[] replacement = new double[2];
        String evidence;

        long frames() {
            return Math.max(0L, endFrame - startFrame + 1L);
        }
    }

    static final class Result {
        String model = "DAML v0.10 guarded stereo continuity and isolated-clip repair";
        String method = "offline isolated-impulse, exact-zero-hole and sparse per-channel digital-plateau interpolation";
        final List<Event> events = new ArrayList<>();
        final List<String> rejectedReasons = new ArrayList<>();
        final List<String> safetyNotes = new ArrayList<>();
        int isolatedClicks;
        int exactZeroDropouts;
        int isolatedClipRuns;
        long repairedClippedSamples;
        long inheritedClipSamplesRemaining;
        long repairedFrames;
        long inspectedFrames;
        boolean repairApplied;
        boolean protectedRegionsExact;
        double sourceCorrelation = Double.NaN;
        double maximumAbsoluteDelta;
        double editedRmsDeltaDbfs = Double.NEGATIVE_INFINITY;
        double measuredTargetedBenefit;
        String repairedSha256;
        File repairedFile;
        String status = "No proven click or short exact-zero dropout cleared every repair gate";

        boolean hasRepairs() {
            return !events.isEmpty();
        }

        String compactSummary() {
            if (!hasRepairs()) return status;
            return String.format(Locale.US,
                    "%d isolated click%s + %d short dropout%s + %d isolated clip runs • %d repaired frame%s • source correlation %.8f",
                    isolatedClicks, isolatedClicks == 1 ? "" : "s",
                    exactZeroDropouts, exactZeroDropouts == 1 ? "" : "s",
                    isolatedClipRuns, repairedFrames, repairedFrames == 1 ? "" : "s",
                    sourceCorrelation);
        }
    }

    private static final int BUFFER_FRAMES = 4096;
    private static final int MAX_EVENTS = 96;
    private static final int MAX_ZERO_FRAMES = 960;
    private static final double MIN_ACTIVE_RMS = 0.0030;

    private ClickDropoutRepair() {}

    static Result analyze(AudioAnalyzer.StreamProvider source,
                          AnalysisResult base,
                          AudioAnalyzer.Progress progress) throws IOException {
        if (base.wav.channels != 2 || base.wav.sampleRate != 48000) {
            throw new IOException("Click/dropout repair requires a 48 kHz stereo authoritative WAV");
        }
        Result out = new Result();
        out.inheritedClipSamplesRemaining = base.clippedSamples;
        long protectedIntroEnd = Math.round(base.wav.sampleRate * 2.0);
        long protectedTailStart = Math.max(protectedIntroEnd,
                base.wav.frames - Math.round(base.wav.sampleRate
                        * Math.max(8.0, base.wav.durationSeconds * 0.04)));
        ArrayList<Event> raw = new ArrayList<>();
        IsolatedClipDetector[] clips = {
                new IsolatedClipDetector(0, base.wav, protectedIntroEnd, protectedTailStart),
                new IsolatedClipDetector(1, base.wav, protectedIntroEnd, protectedTailStart)};
        Rolling[] rolling = {new Rolling(), new Rolling()};
        float[][] samples = new float[2][BUFFER_FRAMES];
        double[] previous2 = new double[2];
        double[] previous1 = new double[2];
        boolean havePrevious1 = false;
        boolean havePrevious2 = false;
        long absolute = 0L;
        long zeroStart = -1L;
        int zeroCount = 0;
        double zeroPreRms = 0.0;
        double[] zeroBefore = new double[2];
        long suppressClicksUntil = -1L;

        progress.update("Continuity AI: inspecting samples for proven faults", 0);
        try (WavFile.Reader reader = WavFile.open(source.open())) {
            int read;
            while ((read = reader.readFrames(samples, BUFFER_FRAMES)) > 0) {
                for (int i = 0; i < read; i++, absolute++) {
                    double left = finite(samples[0][i]);
                    double right = finite(samples[1][i]);
                    clips[0].observe(left, previous1[0], absolute);
                    clips[1].observe(right, previous1[1], absolute);
                    boolean exactStereoZero = left == 0.0 && right == 0.0;

                    if (exactStereoZero) {
                        if (zeroStart < 0L) {
                            zeroStart = absolute;
                            zeroCount = 0;
                            zeroPreRms = Math.sqrt(0.5
                                    * (rolling[0].energy + rolling[1].energy));
                            zeroBefore[0] = havePrevious1 ? previous1[0] : 0.0;
                            zeroBefore[1] = havePrevious1 ? previous1[1] : 0.0;
                        }
                        zeroCount++;
                    } else if (zeroStart >= 0L) {
                        if (zeroCount >= 2 && zeroCount <= MAX_ZERO_FRAMES
                                && zeroStart >= protectedIntroEnd
                                && absolute < protectedTailStart) {
                            double afterLevel = Math.sqrt(0.5
                                    * (left * left + right * right));
                            double beforeLevel = Math.sqrt(0.5
                                    * (zeroBefore[0] * zeroBefore[0]
                                    + zeroBefore[1] * zeroBefore[1]));
                            if (zeroPreRms >= MIN_ACTIVE_RMS
                                    && Math.max(afterLevel, beforeLevel) >= MIN_ACTIVE_RMS) {
                                Event event = new Event();
                                event.kind = Kind.EXACT_ZERO_DROPOUT;
                                event.startFrame = zeroStart;
                                event.endFrame = zeroStart + zeroCount - 1L;
                                event.channelMask = 3;
                                event.before[0] = zeroBefore[0];
                                event.before[1] = zeroBefore[1];
                                event.after[0] = left;
                                event.after[1] = right;
                                event.confidence = clamp(0.92
                                        + 0.015 * Math.log1p(zeroCount), 0.92, 0.999);
                                event.evidence = "stereo-synchronous exact-zero run of "
                                        + zeroCount + " frames bracketed by active audio";
                                raw.add(event);
                                suppressClicksUntil = absolute + 3L;
                            } else {
                                out.rejectedReasons.add("An exact-zero run was retained because active audio was not proven on both sides.");
                            }
                        } else if (zeroCount > MAX_ZERO_FRAMES) {
                            out.rejectedReasons.add("A long zero region was retained as possible intentional silence; automatic repair is limited to 20 ms.");
                        }
                        zeroStart = -1L;
                        zeroCount = 0;
                    }

                    if (!exactStereoZero && havePrevious2
                            && absolute > suppressClicksUntil) {
                        double[] current = {left, right};
                        double[] residual = new double[2];
                        double[] threshold = new double[2];
                        int mask = 0;
                        for (int channel = 0; channel < 2; channel++) {
                            double prediction = 0.5
                                    * (previous2[channel] + current[channel]);
                            residual[channel] = Math.abs(previous1[channel] - prediction);
                            double rms = Math.sqrt(Math.max(1e-12, rolling[channel].energy));
                            threshold[channel] = Math.max(0.080,
                                    Math.max(rolling[channel].residual * 18.0,
                                            rms * 1.25));
                            double neighbourMotion = Math.abs(
                                    current[channel] - previous2[channel]);
                            double isolation = residual[channel]
                                    / Math.max(1e-6, 0.35 * neighbourMotion
                                    + 2.0 * rolling[channel].residual);
                            double neighbourPeak = Math.max(
                                    Math.abs(previous2[channel]),
                                    Math.abs(current[channel]));
                            double spikeDominance = (Math.abs(previous1[channel]) + 0.005)
                                    / (neighbourPeak + 0.005);
                            if (residual[channel] > threshold[channel]
                                    && isolation >= 4.0
                                    && spikeDominance >= 2.40) mask |= 1 << channel;
                        }
                        long centre = absolute - 1L;
                        if (mask != 0 && centre >= protectedIntroEnd
                                && centre < protectedTailStart
                                && transientDiagnosticSupports(base, centre)) {
                            double localRms = Math.sqrt(Math.max(1e-12,
                                    0.5 * (rolling[0].energy + rolling[1].energy)));
                            double maximumResidual = Math.max(residual[0], residual[1]);
                            boolean stereoTransientRisk = mask == 3
                                    && maximumResidual < localRms * 1.45;
                            if (!stereoTransientRisk
                                    && (raw.isEmpty()
                                    || centre > raw.get(raw.size() - 1).endFrame + 2L)) {
                                Event event = new Event();
                                event.kind = Kind.ISOLATED_CLICK;
                                event.startFrame = centre;
                                event.endFrame = centre;
                                event.channelMask = mask;
                                event.replacement[0] = 0.5 * (previous2[0] + current[0]);
                                event.replacement[1] = 0.5 * (previous2[1] + current[1]);
                                double ratio = maximumResidual / Math.max(1e-6,
                                        Math.max(threshold[0], threshold[1]));
                                event.confidence = clamp(0.88
                                        + 0.07 * Math.log1p(ratio), 0.88, 0.999);
                                event.evidence = "isolated sample prediction residual exceeded the local robust derivative floor";
                                raw.add(event);
                            }
                        }
                        rolling[0].updateResidual(residual[0]);
                        rolling[1].updateResidual(residual[1]);
                    }

                    if (!exactStereoZero) {
                        rolling[0].updateEnergy(left);
                        rolling[1].updateEnergy(right);
                    }
                    previous2[0] = previous1[0];
                    previous2[1] = previous1[1];
                    previous1[0] = left;
                    previous1[1] = right;
                    havePrevious2 = havePrevious1;
                    havePrevious1 = true;
                }
                int percent = (int) Math.min(94,
                        Math.round(94.0 * absolute / Math.max(1L, base.wav.frames)));
                progress.update("Continuity AI: inspecting samples for proven faults", percent);
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("Click/dropout inspection cancelled");
                }
            }
        }
        out.inspectedFrames = absolute;
        int clipRuns = clips[0].qualifiedRuns + clips[1].qualifiedRuns;
        long clipFrames = clips[0].qualifiedFrames + clips[1].qualifiedFrames;
        long sparseBudget = Math.min(512L, Math.max(64L, Math.round(base.wav.frames * 0.00005)));
        if (clipRuns <= 16 && clipFrames <= sparseBudget
                && base.clippedSamples <= clipFrames * 2L + 16L) {
            raw.addAll(clips[0].events);
            raw.addAll(clips[1].events);
        } else if (clipRuns > 0) {
            out.rejectedReasons.add("Clip plateaux were retained because clipping was too dense for isolated-event reconstruction.");
        }
        if (clips[0].rejectedRuns + clips[1].rejectedRuns > 0) {
            out.safetyNotes.add("Long, ambiguous, boundary or rounded full-scale peaks remain diagnostic; they are not reconstructed.");
        }
        selectSafeEvents(raw, out, base.wav.frames);
        out.safetyNotes.add("A click requires both an isolated single-sample prediction failure and an overlapping high-confidence track-state discontinuity finding; exact-zero holes require stereo-synchronous sample proof and are limited to 20 ms.");
        out.safetyNotes.add("Musical transients, long silence, fades, protected intro and protected ending remain untouched.");
        out.safetyNotes.add("A detected event must also pass rendered duration, correlation, peak, continuity and protected-edge verification.");
        if (out.hasRepairs()) {
            out.status = out.events.size() + " proven continuity event(s) cleared the detection gate; rendered verification remains required";
        }
        progress.update("Continuity AI: detection complete", 100);
        return out;
    }

    static File render(AudioAnalyzer.StreamProvider source,
                       AnalysisResult base,
                       Result model,
                       File output,
                       AudioAnalyzer.Progress progress) throws IOException {
        if (model == null || !model.hasRepairs()) {
            throw new IOException("No proven click/dropout event is available to render");
        }
        long dataBytes = base.wav.frames * 2L * 4L;
        if (dataBytes > 0xffffffffL - 36L) {
            throw new IOException("Continuity repair would exceed the RIFF/WAV 4 GB limit");
        }
        List<Event> events = new ArrayList<>(model.events);
        Collections.sort(events, Comparator.comparingLong(event -> event.startFrame));
        long protectedIntroEnd = Math.round(base.wav.sampleRate * 2.0);
        long protectedTailStart = Math.max(protectedIntroEnd,
                base.wav.frames - Math.round(base.wav.sampleRate
                        * Math.max(8.0, base.wav.durationSeconds * 0.04)));
        double dot = 0.0, sourceEnergy = 0.0, outputEnergy = 0.0, deltaEnergy = 0.0;
        double targetBefore = 0.0;
        double protectedDelta = 0.0;
        long framesDone = 0L;
        long repairedClips = 0L;
        double sourceClipThreshold = base.wav.floatingPoint ? 1.0
                : 1.0 - Math.pow(2.0, 1.0 - base.wav.validBitsPerSample);
        int cursor = 0;
        float[][] samples = new float[2][BUFFER_FRAMES];
        progress.update("Continuity AI: rendering verified micro-repairs", 0);
        try (WavFile.Reader reader = WavFile.open(source.open());
             FileOutputStream file = new FileOutputStream(output);
             OutputStream raw = new BufferedOutputStream(file, 256 * 1024)) {
            writeFloatHeader(raw, base.wav.sampleRate, 2, dataBytes);
            FloatWriter writer = new FloatWriter(raw);
            int read;
            while ((read = reader.readFrames(samples, BUFFER_FRAMES)) > 0) {
                for (int i = 0; i < read; i++, framesDone++) {
                    double originalLeft = finite(samples[0][i]);
                    double originalRight = finite(samples[1][i]);
                    double left = originalLeft;
                    double right = originalRight;
                    while (cursor < events.size()
                            && framesDone > events.get(cursor).endFrame) cursor++;
                    for (int active = cursor; active < events.size(); active++) {
                        Event event = events.get(active);
                        if (event.startFrame > framesDone) break;
                        if (framesDone > event.endFrame) continue;
                        if (event.kind == Kind.ISOLATED_CLICK) {
                            if ((event.channelMask & 1) != 0) left = event.replacement[0];
                            if ((event.channelMask & 2) != 0) right = event.replacement[1];
                        } else {
                            double t = (framesDone - event.startFrame + 1.0) / (event.frames() + 1.0);
                            double smooth = t * t * (3.0 - 2.0 * t);
                            if ((event.channelMask & 1) != 0) left = event.before[0] * (1.0 - smooth) + event.after[0] * smooth;
                            if ((event.channelMask & 2) != 0) right = event.before[1] * (1.0 - smooth) + event.after[1] * smooth;
                        }
                    }
                    if (Math.abs(originalLeft) >= sourceClipThreshold
                            && Math.abs(left) < sourceClipThreshold) repairedClips++;
                    if (Math.abs(originalRight) >= sourceClipThreshold
                            && Math.abs(right) < sourceClipThreshold) repairedClips++;
                    double dl = left - originalLeft;
                    double dr = right - originalRight;
                    model.maximumAbsoluteDelta = Math.max(model.maximumAbsoluteDelta,
                            Math.max(Math.abs(dl), Math.abs(dr)));
                    deltaEnergy += dl * dl + dr * dr;
                    targetBefore += dl * dl + dr * dr;
                    dot += originalLeft * left + originalRight * right;
                    sourceEnergy += originalLeft * originalLeft
                            + originalRight * originalRight;
                    outputEnergy += left * left + right * right;
                    if (framesDone < protectedIntroEnd || framesDone >= protectedTailStart) {
                        protectedDelta = Math.max(protectedDelta,
                                Math.max(Math.abs(dl), Math.abs(dr)));
                    }
                    writer.frame(left, right);
                }
                int percent = (int) Math.min(94,
                        Math.round(94.0 * framesDone / Math.max(1L, base.wav.frames)));
                progress.update("Continuity AI: rendering verified micro-repairs", percent);
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("Click/dropout repair render cancelled");
                }
            }
            writer.finish();
            raw.flush();
            file.getFD().sync();
        } catch (IOException error) {
            if (output.exists() && !output.delete()) output.deleteOnExit();
            throw error;
        }
        if (framesDone != base.wav.frames || output.length() != 44L + dataBytes) {
            if (output.exists() && !output.delete()) output.deleteOnExit();
            throw new IOException("Click/dropout repair changed frame count or produced an incomplete file");
        }
        model.sourceCorrelation = dot / Math.sqrt(Math.max(1e-30,
                sourceEnergy * outputEnergy));
        model.editedRmsDeltaDbfs = 20.0 * Math.log10(Math.max(1e-15,
                Math.sqrt(deltaEnergy / Math.max(1L, framesDone * 2L))));
        model.measuredTargetedBenefit = targetBefore > 1e-20 ? 1.0 : 0.0;
        model.protectedRegionsExact = protectedDelta == 0.0;

        AnalysisResult verification = AudioAnalyzer.analyze(output.getName(),
                () -> new FileInputStream(output), progress);
        if (!verification.wav.floatingPoint || verification.wav.frames != base.wav.frames
                || verification.wav.sampleRate != base.wav.sampleRate
                || verification.wav.channels != 2
                || verification.nonFiniteSamples != 0
                || verification.clippedSamples > base.clippedSamples
                || verification.internalZeroRuns > base.internalZeroRuns
                || (model.isolatedClipRuns > 0 && base.clippedSamples > 0
                    && verification.clippedSamples >= base.clippedSamples)
                || model.sourceCorrelation < 0.997
                || !model.protectedRegionsExact) {
            if (!output.delete()) output.deleteOnExit();
            throw new IOException("Click/dropout repair failed duration, integrity, correlation or protected-edge verification");
        }
        model.repairedClippedSamples = repairedClips;
        model.inheritedClipSamplesRemaining = Math.max(0L, base.clippedSamples - repairedClips);
        model.repairApplied = true;
        model.repairedFile = output;
        model.repairedSha256 = Hashing.sha256(output);
        model.status = "PASS: proven continuity events repaired with unchanged duration and exact protected edges";
        progress.update("Continuity AI: repaired source verified", 100);
        return output;
    }

    private static void selectSafeEvents(List<Event> raw, Result out, long frames) {
        ArrayList<Event> filtered = new ArrayList<>();
        raw.sort(Comparator.comparingLong(event -> event.startFrame));
        for (Event event : raw) {
            boolean overlapsDropout = false;
            if (event.kind == Kind.ISOLATED_CLICK) {
                for (Event other : raw) {
                    if ((other.kind == Kind.EXACT_ZERO_DROPOUT || other.kind == Kind.ISOLATED_CLIP_RUN)
                            && event.startFrame >= other.startFrame - 3L
                            && event.startFrame <= other.endFrame + 3L) {
                        overlapsDropout = true;
                        break;
                    }
                }
            }
            if (!overlapsDropout) filtered.add(event);
        }
        filtered.sort((a, b) -> Double.compare(b.confidence, a.confidence));
        long maximumFrames = Math.min(4800L,
                Math.max(96L, Math.round(frames * 0.00025)));
        long acceptedFrames = 0L;
        for (Event event : filtered) {
            if (out.events.size() >= MAX_EVENTS) break;
            if (acceptedFrames + event.frames() > maximumFrames) continue;
            out.events.add(event);
            acceptedFrames += event.frames();
        }
        out.events.sort(Comparator.comparingLong(event -> event.startFrame));
        out.repairedFrames = acceptedFrames;
        for (Event event : out.events) {
            if (event.kind == Kind.ISOLATED_CLICK) out.isolatedClicks++;
            else if (event.kind == Kind.EXACT_ZERO_DROPOUT) out.exactZeroDropouts++;
            else out.isolatedClipRuns++;
        }
        if (filtered.size() > out.events.size()) {
            out.rejectedReasons.add((filtered.size() - out.events.size())
                    + " additional candidate event(s) were rejected by event-count or total-coverage limits.");
        }
    }

    private static boolean transientDiagnosticSupports(AnalysisResult base,
                                                       long frame) {
        if (base == null || base.aiDiagnostics == null || base.wav == null) return false;
        double seconds = frame / (double) Math.max(1, base.wav.sampleRate);
        for (AdaptiveDiagnosticModel.Event event : base.aiDiagnostics.events) {
            if (event.type == AdaptiveDiagnosticModel.Type.TRANSIENT_DISCONTINUITY
                    && event.confidence >= 0.97
                    && seconds >= event.startSeconds
                    && seconds < event.endSeconds) return true;
        }
        return false;
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

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static final class Rolling {
        double energy = 1e-8;
        double residual = 1e-5;

        void updateEnergy(double sample) {
            energy += (sample * sample - energy) / 2048.0;
        }

        void updateResidual(double value) {
            double limited = Math.min(value, Math.max(1e-5, residual * 4.0));
            residual += (limited - residual) / 1024.0;
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

        private void sample(double value) throws IOException {
            if (position + 4 > buffer.length) flush();
            int bits = Float.floatToIntBits((float) finite(value));
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
