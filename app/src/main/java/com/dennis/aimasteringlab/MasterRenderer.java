package com.dennis.aimasteringlab;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Random;

final class MasterRenderer {
    enum LimiterProfile {
        CLEAN_LONG("clean_long_release", 8.0, 220.0),
        BALANCED("balanced", 5.0, 150.0),
        TRANSIENT_RECOVERY("transient_recovery", 3.0, 90.0),
        RAMPED_CLEAN("ramped_clean", 6.0, 110.0),
        OVERSAMPLED_CLEAN("oversampled_4x_clean", 6.0, 110.0),
        SPARSE_MUSICAL("sparse_peak_recovery", 4.0, 70.0);

        final String label;
        final double lookaheadMs;
        final double releaseMs;

        LimiterProfile(String label, double lookaheadMs, double releaseMs) {
            this.label = label;
            this.lookaheadMs = lookaheadMs;
            this.releaseMs = releaseMs;
        }
    }

    static final class Result {
        File file;
        String savedFileName;
        double gainDb;
        long quantizerClips;
        AnalysisResult verification;
        boolean limiterUsed;
        boolean dynamicsProtected;
        boolean exactTargetReached;
        boolean compliantSourcePreserved;
        double maximumLimiterReductionDb;
        double averageLimiterReductionDb;
        double maximumLimiterGainStepDb;
        int limiterOversampling = 1;
        double limiterFramesAboveOneDbPercent;
        double limiterFramesAboveThreeDbPercent;
        boolean sparsePeakReleaseMode;
        String limiterProfile = "bypassed";
        double limiterLookaheadMs;
        double limiterReleaseMs;
        double targetShortfallLu;
        int renderPasses;
        // Explicit, verified fallback if rendered true peak disagrees with the
        // predicted ceiling. This is never an exemption from final PCM QC.
        int peakSafetyRecoveryPasses;
        double peakSafetyRecoveryAttenuationDb;
        boolean aiGuided;
        int aiCorrectionEvents;
        double maximumAiCorrectionDb;
        double aiCorrectedPremasterLufs;
        double aiCorrectedPremasterTruePeakDbtp;
        String aiCorrectionSummary;
        boolean vocalHumanityGuided;
        String vocalStemPath;
        int vocalRideCount;
        int vocalDeEssEvents;
        double vocalRideDownDb;
        double vocalRideUpDb;
        double vocalMaximumDeEssDb;
        double vocalDeltaScale;
        String vocalCorrectionSummary;
        boolean maskingPocketGuided;
        int maskingPocketEvents;
        int maskingPocketStems;
        double maskingMaximumCutDb;
        double maskingMaximumCombinedCutDb;
        double maskingDeltaScale;
        String maskingCorrectionSummary;
    }

    private static final int BUFFER_FRAMES = 4096;
    private static final double TARGET_LUFS = -14.0;
    private static final double TRUE_PEAK_CEILING = -1.0;
    private static final double INITIAL_LIMITER_CEILING = -1.20;

    static Result render(AudioAnalyzer.StreamProvider source,
                         AnalysisResult analysis,
                         double requestedGainDb,
                         File output,
                         AudioAnalyzer.Progress progress) throws IOException {
        return renderWithCeiling(source, analysis, requestedGainDb, output,
                TRUE_PEAK_CEILING, false, progress);
    }

    static Result renderWithCeiling(AudioAnalyzer.StreamProvider source,
                                    AnalysisResult analysis,
                                    double requestedGainDb,
                                    File output,
                                    double finalTruePeakCeilingDbtp,
                                    boolean forceLimiter,
                                    AudioAnalyzer.Progress progress) throws IOException {
        return renderWithCeiling(source, analysis, requestedGainDb, output,
                finalTruePeakCeilingDbtp, forceLimiter,
                LimiterProfile.BALANCED, progress);
    }

    // The same predicate is used by the renderer and the profiler fast path.
    // If it is false, limiter profiles never enter the audio signal path.
    static boolean limiterWillEngage(AnalysisResult analysis, double gainDb,
                                     boolean forceLimiter) {
        return forceLimiter || (analysis.limiterRequired && analysis.limiterAllowed
                && gainDb > -1.10 - analysis.truePeakEstimateDbtp + 0.001);
    }

    static Result renderWithCeiling(AudioAnalyzer.StreamProvider source,
                                    AnalysisResult analysis,
                                    double requestedGainDb,
                                    File output,
                                    double finalTruePeakCeilingDbtp,
                                    boolean forceLimiter,
                                    LimiterProfile limiterProfile,
                                    AudioAnalyzer.Progress progress) throws IOException {
        if (analysis.wav.channels != 2) throw new IOException("The release master must be stereo");
        if (!Double.isFinite(finalTruePeakCeilingDbtp)
                || finalTruePeakCeilingDbtp > TRUE_PEAK_CEILING) {
            throw new IOException("The requested final true-peak ceiling is unsafe");
        }
        if (analysis.noRenderNeeded && Math.abs(requestedGainDb) <= 0.0005) {
            throw new IOException("No render needed: " + analysis.noRenderReason);
        }
        LimiterProfile selectedProfile = limiterProfile == null
                ? LimiterProfile.BALANCED : limiterProfile;
        boolean useLimiter = limiterWillEngage(analysis, requestedGainDb, forceLimiter);
        // Flat gain has no limiter overshoot to budget. Use the same final ceiling
        // as the analyzer, then independently measure the actual rendered file.
        double unLimitedSafetyCeiling = finalTruePeakCeilingDbtp;
        if (!useLimiter && analysis.truePeakEstimateDbtp + requestedGainDb
                > unLimitedSafetyCeiling + 0.001) {
            throw new IOException(String.format(java.util.Locale.US,
                    "Requested flat gain predicts %.4f dBTP, above the %.4f dBTP ceiling; guarded limiting is unavailable",
                    analysis.truePeakEstimateDbtp + requestedGainDb,
                    finalTruePeakCeilingDbtp));
        }
        long dataBytes = analysis.wav.frames * analysis.wav.channels * 3L;
        if (dataBytes > 0xffffffffL - 36L) {
            throw new IOException("The 24-bit output would exceed the RIFF/WAV 4 GB limit");
        }

        double workingGain = requestedGainDb;
        double limiterCeiling = forceLimiter
                ? finalTruePeakCeilingDbtp - 0.12 : INITIAL_LIMITER_CEILING;
        Result result = null;
        int maximumPasses = useLimiter ? 4 : 1;
        try {
            for (int pass = 1; pass <= maximumPasses; pass++) {
                result = renderAttempt(source, analysis, workingGain, useLimiter,
                        limiterCeiling, selectedProfile, output, progress);
                result.renderPasses = pass;
                result.verification = verify(output, analysis, progress);

                if (useLimiter && !limiterActivitySafe(result, analysis)) {
                    if (forceLimiter) {
                        throw new IOException("Codec-safety limiting exceeded the song-derived reduction allowance");
                    }
                    useLimiter = false;
                    workingGain = Math.min(requestedGainDb,
                            -1.10 - analysis.truePeakEstimateDbtp);
                    result = renderAttempt(source, analysis, workingGain, false,
                            limiterCeiling, selectedProfile, output, progress);
                    result.renderPasses = pass + 1;
                    result.dynamicsProtected = true;
                    result.verification = verify(output, analysis, progress);
                    if (result.verification.truePeakEstimateDbtp > finalTruePeakCeilingDbtp + 0.001) {
                        result = recoverVerifiedFlatPeak(source, analysis, output,
                                finalTruePeakCeilingDbtp, selectedProfile, progress,
                                workingGain, requestedGainDb, result.renderPasses,
                                result.verification.truePeakEstimateDbtp);
                        workingGain = result.gainDb;
                    }
                    break;
                }

                double peakOvershoot = result.verification.truePeakEstimateDbtp
                        - finalTruePeakCeilingDbtp;
                if (peakOvershoot > 0.001) {
                    if (!useLimiter || pass == maximumPasses) {
                        if (forceLimiter) {
                            throw new IOException("Post-render true peak exceeded the requested ceiling");
                        }
                        result = recoverVerifiedFlatPeak(source, analysis, output,
                                finalTruePeakCeilingDbtp, selectedProfile, progress,
                                workingGain, requestedGainDb, pass,
                                result.verification.truePeakEstimateDbtp);
                        workingGain = result.gainDb;
                        break;
                    }
                    limiterCeiling -= peakOvershoot + 0.05;
                    continue;
                }

                double loudnessError = TARGET_LUFS - result.verification.integratedLufs;
                if (!useLimiter || Math.abs(loudnessError) <= 0.015) break;
                if (pass == maximumPasses) break;
                workingGain += loudnessError;
            }
        } catch (IOException e) {
            if (output.exists() && !output.delete()) output.deleteOnExit();
            throw e;
        }
        if (result == null) throw new IOException("Master render produced no result");
        result.file = output;
        result.savedFileName = output.getName();
        result.gainDb = workingGain;
        if (result.quantizerClips != 0 || result.verification.nonFiniteSamples != 0) {
            if (output.exists() && !output.delete()) output.deleteOnExit();
            throw new IOException("Post-render sample-integrity verification failed");
        }
        if (!Double.isFinite(result.verification.truePeakEstimateDbtp)
                || result.verification.truePeakEstimateDbtp > finalTruePeakCeilingDbtp + 0.001) {
            if (output.exists() && !output.delete()) output.deleteOnExit();
            throw new IOException("Final verified true peak exceeded the requested ceiling");
        }
        result.exactTargetReached = Math.abs(result.verification.integratedLufs - TARGET_LUFS) <= 0.02;
        result.targetShortfallLu = TARGET_LUFS - result.verification.integratedLufs;
        if (analysis.ceilingLimited && !result.exactTargetReached) {
            result.dynamicsProtected = true;
        }
        if (!result.dynamicsProtected && !result.exactTargetReached) {
            if (output.exists() && !output.delete()) output.deleteOnExit();
            throw new IOException("Verified loudness did not converge to -14.00 LUFS-I");
        }
        progress.update("Render verified", 100);
        return result;
    }

    // A predicted-safe render may very occasionally have a higher measured true
    // peak because of resampling, quantisation, or floating-point rounding.
    // Rather than rejecting the entire Studio job, retry with less *flat gain*.
    // No DSP/limiter settings are relaxed, and each result is independently
    // reanalysed. A shortfall from -14 LUFS is explicitly marked as protected.
    static Result recoverVerifiedFlatPeak(AudioAnalyzer.StreamProvider source,
                                         AnalysisResult analysis, File output,
                                         double finalCeilingDbtp,
                                         LimiterProfile profile,
                                         AudioAnalyzer.Progress progress,
                                         double previousGainDb,
                                         double originalRequestedGainDb,
                                         int previousPasses,
                                         double measuredPeakDbtp) throws IOException {
        if (!Double.isFinite(measuredPeakDbtp) || !Double.isFinite(previousGainDb)) {
            throw new IOException("Cannot recover a non-finite true-peak measurement");
        }
        double gain = Math.min(previousGainDb,
                finalCeilingDbtp - 0.12 - analysis.truePeakEstimateDbtp);
        // If a flat render exceeded its estimate, use the *measured* overshoot
        // as additional evidence. For a failed limiter render the predictive
        // flat-gain bound above is the controlling safety limit.
        if (measuredPeakDbtp > finalCeilingDbtp &&
                previousGainDb <= finalCeilingDbtp - analysis.truePeakEstimateDbtp + 0.001) {
            gain = Math.min(gain,
                    previousGainDb - (measuredPeakDbtp - finalCeilingDbtp) - 0.12);
        }
        for (int retry = 1; retry <= 3; retry++) {
            if (!Double.isFinite(gain)) {
                throw new IOException("Peak-safety fallback computed a non-finite gain");
            }
            progress.update("Verifying conservative transparent peak-safe fallback", 0);
            Result recovered = renderAttempt(source, analysis, gain, false,
                    INITIAL_LIMITER_CEILING, profile, output, progress);
            recovered.renderPasses = previousPasses + retry;
            recovered.peakSafetyRecoveryPasses = retry;
            recovered.peakSafetyRecoveryAttenuationDb =
                    Math.max(0.0, originalRequestedGainDb - gain);
            recovered.dynamicsProtected = true;
            recovered.verification = verify(output, analysis, progress);
            if (recovered.quantizerClips != 0 ||
                    recovered.verification.nonFiniteSamples != 0) {
                throw new IOException("Peak-safety fallback failed sample-integrity verification");
            }
            double overshoot = recovered.verification.truePeakEstimateDbtp - finalCeilingDbtp;
            if (Double.isFinite(overshoot) && overshoot <= 0.001) return recovered;
            if (!Double.isFinite(overshoot)) {
                throw new IOException("Peak-safety fallback true peak was non-finite");
            }
            gain -= Math.max(0.06, overshoot + 0.12);
        }
        throw new IOException("Peak-safe fallback exhausted verification retries; refusing an unsafe master");
    }

    static Result renderCodecSafetyAttenuation(
            AudioAnalyzer.StreamProvider verifiedMaster,
            AnalysisResult masterAnalysis,
            Result prior,
            double attenuationDb,
            File output,
            AudioAnalyzer.Progress progress) throws IOException {
        if (masterAnalysis.wav.channels != 2 || masterAnalysis.wav.sampleRate != 48000
                || masterAnalysis.wav.bitsPerSample != 24) {
            throw new IOException("Codec-safety attenuation requires a verified 24-bit/48 kHz stereo master");
        }
        if (!Double.isFinite(attenuationDb) || attenuationDb < 0.05
                || attenuationDb > 1.00) {
            throw new IOException("Codec-safety attenuation request was outside the 0.05–1.00 dB safety range");
        }
        Result out = null;
        try {
            out = renderAttempt(verifiedMaster, masterAnalysis, -attenuationDb,
                    false, INITIAL_LIMITER_CEILING, LimiterProfile.BALANCED, output, progress);
            out.renderPasses = prior.renderPasses + 1;
            out.verification = verify(output, masterAnalysis, progress);
            if (out.quantizerClips != 0 || out.verification.nonFiniteSamples != 0
                    || out.verification.truePeakEstimateDbtp > -1.10) {
                throw new IOException("Flat codec-safety attenuation failed PCM integrity or headroom verification");
            }
        } catch (IOException error) {
            if (output.exists() && !output.delete()) output.deleteOnExit();
            throw error;
        }
        out.file = output;
        out.savedFileName = output.getName();
        out.gainDb = prior.gainDb - attenuationDb;
        out.limiterUsed = prior.limiterUsed;
        out.limiterProfile = prior.limiterProfile;
        out.limiterLookaheadMs = prior.limiterLookaheadMs;
        out.limiterReleaseMs = prior.limiterReleaseMs;
        out.maximumLimiterReductionDb = prior.maximumLimiterReductionDb;
        out.averageLimiterReductionDb = prior.averageLimiterReductionDb;
        out.maximumLimiterGainStepDb = prior.maximumLimiterGainStepDb;
        out.limiterOversampling = prior.limiterOversampling;
        out.limiterFramesAboveOneDbPercent = prior.limiterFramesAboveOneDbPercent;
        out.limiterFramesAboveThreeDbPercent = prior.limiterFramesAboveThreeDbPercent;
        out.sparsePeakReleaseMode = prior.sparsePeakReleaseMode;
        out.dynamicsProtected = true;
        out.exactTargetReached = Math.abs(out.verification.integratedLufs - TARGET_LUFS) <= 0.02;
        out.targetShortfallLu = TARGET_LUFS - out.verification.integratedLufs;
        out.aiGuided = prior.aiGuided;
        out.aiCorrectionEvents = prior.aiCorrectionEvents;
        out.maximumAiCorrectionDb = prior.maximumAiCorrectionDb;
        out.aiCorrectedPremasterLufs = prior.aiCorrectedPremasterLufs;
        out.aiCorrectedPremasterTruePeakDbtp = prior.aiCorrectedPremasterTruePeakDbtp;
        out.aiCorrectionSummary = prior.aiCorrectionSummary;
        out.vocalHumanityGuided = prior.vocalHumanityGuided;
        out.vocalStemPath = prior.vocalStemPath;
        out.vocalRideCount = prior.vocalRideCount;
        out.vocalDeEssEvents = prior.vocalDeEssEvents;
        out.vocalRideDownDb = prior.vocalRideDownDb;
        out.vocalRideUpDb = prior.vocalRideUpDb;
        out.vocalMaximumDeEssDb = prior.vocalMaximumDeEssDb;
        out.vocalDeltaScale = prior.vocalDeltaScale;
        out.vocalCorrectionSummary = prior.vocalCorrectionSummary;
        out.maskingPocketGuided = prior.maskingPocketGuided;
        out.maskingPocketEvents = prior.maskingPocketEvents;
        out.maskingPocketStems = prior.maskingPocketStems;
        out.maskingMaximumCutDb = prior.maskingMaximumCutDb;
        out.maskingMaximumCombinedCutDb = prior.maskingMaximumCombinedCutDb;
        out.maskingDeltaScale = prior.maskingDeltaScale;
        out.maskingCorrectionSummary = prior.maskingCorrectionSummary;
        progress.update("Flat codec-safety attenuation verified", 100);
        return out;
    }

    static boolean isCompliantSourcePreserved(AnalysisResult source,
                                               AnalysisResult output, Result render) {
        return render != null && render.compliantSourcePreserved
                && render.renderPasses == 0 && !render.limiterUsed
                && render.gainDb == 0.0 && render.quantizerClips == 0
                && AudioAnalyzer.isCompliantReleaseMaster(source)
                && AudioAnalyzer.isCompliantReleaseMaster(output)
                && source.wav.frames == output.wav.frames
                && source.sourceSha256 != null
                && source.sourceSha256.equalsIgnoreCase(output.sourceSha256);
    }

    static boolean limiterActivitySafe(Result result, AnalysisResult analysis) {
        double normalLimit = Math.min(
                AudioAnalyzer.STANDARD_LIMITER_MAXIMUM_REDUCTION_DB,
                analysis.limiterReductionAllowanceDb + 0.10);
        if (result.maximumLimiterReductionDb <= normalLimit) return true;
        boolean sparse = result.maximumLimiterReductionDb
                <= AudioAnalyzer.SPARSE_LIMITER_MAXIMUM_REDUCTION_DB
                && result.averageLimiterReductionDb
                <= AudioAnalyzer.SPARSE_LIMITER_MAXIMUM_AVERAGE_DB
                && result.limiterFramesAboveOneDbPercent
                <= AudioAnalyzer.SPARSE_LIMITER_MAXIMUM_ABOVE_1DB_PERCENT
                && result.limiterFramesAboveThreeDbPercent
                <= AudioAnalyzer.SPARSE_LIMITER_MAXIMUM_ABOVE_3DB_PERCENT;
        result.sparsePeakReleaseMode = sparse;
        return sparse;
    }

    static Result renderAttempt(AudioAnalyzer.StreamProvider source,
                                        AnalysisResult analysis,
                                        double gainDb,
                                        boolean useLimiter,
                                        double limiterCeilingDb,
                                        LimiterProfile limiterProfile,
                                        File output,
                                        AudioAnalyzer.Progress progress) throws IOException {
        Result result = new Result();
        result.file = output;
        result.savedFileName = output.getName();
        result.gainDb = gainDb;
        result.limiterUsed = useLimiter;
        result.limiterProfile = useLimiter ? limiterProfile.label : "bypassed";
        result.limiterOversampling = useLimiter && limiterProfile == LimiterProfile.OVERSAMPLED_CLEAN ? 4 : 1;
        result.limiterLookaheadMs = useLimiter ? limiterProfile.lookaheadMs : 0.0;
        result.limiterReleaseMs = useLimiter ? limiterProfile.releaseMs : 0.0;
        double gain = Math.pow(10.0, gainDb / 20.0);
        long dataBytes = analysis.wav.frames * 6L;
        long seed = ditherSeed(analysis.sourceSha256);
        File staging = WavRenderIntegrity.stagingFile(output, "master");

        progress.update(useLimiter ? "Rendering guarded look-ahead master"
                : "Rendering transparent flat-gain master", 0);
        try (WavFile.Reader reader = WavFile.open(source.open());
             FileOutputStream fileOutput = new FileOutputStream(staging);
             OutputStream raw = new BufferedOutputStream(fileOutput, 256 * 1024)) {
            writeHeader(raw, reader.info.sampleRate, 2, dataBytes, 24);
            Quantized24Writer writer = new Quantized24Writer(raw, seed);
            OversampledLimiter oversampled = useLimiter && limiterProfile == LimiterProfile.OVERSAMPLED_CLEAN
                    ? new OversampledLimiter(reader.info.sampleRate,limiterCeilingDb,
                    limiterProfile.lookaheadMs,limiterProfile.releaseMs,writer::frame) : null;
            LookaheadLimiter limiter = oversampled != null ? oversampled.limiter : useLimiter
                    ? new LookaheadLimiter(reader.info.sampleRate,
                    limiterCeilingDb, limiterProfile.lookaheadMs,
                    limiterProfile.releaseMs,
                    limiterProfile == LimiterProfile.RAMPED_CLEAN) : null;
            float[][] samples = new float[2][BUFFER_FRAMES];
            long framesDone = 0;
            int read;
            while ((read = reader.readFrames(samples, BUFFER_FRAMES)) > 0) {
                for (int frame = 0; frame < read; frame++) {
                    double left = finite(samples[0][frame]) * gain;
                    double right = finite(samples[1][frame]) * gain;
                    if (oversampled != null) oversampled.add(left,right);
                    else if (limiter != null) limiter.add(left, right, writer::frame);
                    else writer.frame(left, right);
                }
                framesDone += read;
                int percent = (int) Math.min(94,
                        Math.round(94.0 * framesDone / reader.info.frames));
                progress.update(useLimiter ? "Rendering guarded look-ahead master"
                        : "Rendering transparent flat-gain master", percent);
                if (Thread.currentThread().isInterrupted()) throw new IOException("Render cancelled");
            }
            if (limiter != null) {
                if(oversampled != null) oversampled.finish(); else limiter.finish(writer::frame);
                result.maximumLimiterReductionDb = limiter.maximumReductionDb();
                result.averageLimiterReductionDb = limiter.averageReductionDb();
                result.maximumLimiterGainStepDb = limiter.maximumGainStepDb()*result.limiterOversampling;
                result.limiterFramesAboveOneDbPercent = limiter.percentAboveOneDb();
                result.limiterFramesAboveThreeDbPercent = limiter.percentAboveThreeDb();
                if (limiter.sourceFrames() != analysis.wav.frames*result.limiterOversampling) {
                    throw new IOException("Limiter frame accounting failed");
                }
            }
            writer.finish();
            result.quantizerClips = writer.clips;
            raw.flush();
            fileOutput.getFD().sync();
            if (framesDone != analysis.wav.frames
                    || writer.frames != analysis.wav.frames) {
                throw new IOException("Master render frame accounting failed");
            }
        } catch (IOException error) {
            WavRenderIntegrity.discard(staging);
            throw error;
        }
        try {
            WavRenderIntegrity.commit(staging, output, dataBytes,
                    analysis.wav.sampleRate, 2, 24, false);
        } catch (IOException error) {
            WavRenderIntegrity.discard(staging);
            throw error;
        }
        return result;
    }

    private static AnalysisResult verify(File output, AnalysisResult source,
                                         AudioAnalyzer.Progress progress) throws IOException {
        progress.update("Verifying rendered WAV", 96);
        AnalysisResult verification = AudioAnalyzer.analyze(output.getName(),
                () -> new FileInputStream(output), progress);
        if (verification.wav.frames != source.wav.frames
                || verification.wav.sampleRate != source.wav.sampleRate
                || verification.wav.bitsPerSample != 24
                || verification.wav.channels != 2) {
            throw new IOException("Post-render format or duration verification failed");
        }
        if (verification.nonFiniteSamples != 0) {
            throw new IOException("Post-render sample-integrity verification failed");
        }
        return verification;
    }

    static void writeHeader(OutputStream out, int sampleRate, int channels,
                            long dataBytes, int bitsPerSample) throws IOException {
        int blockAlign = channels * (bitsPerSample / 8);
        long byteRate = (long) sampleRate * blockAlign;
        LittleEndian.writeAscii(out, "RIFF");
        LittleEndian.writeU32(out, 36L + dataBytes);
        LittleEndian.writeAscii(out, "WAVE");
        LittleEndian.writeAscii(out, "fmt ");
        LittleEndian.writeU32(out, 16);
        LittleEndian.writeU16(out, 1);
        LittleEndian.writeU16(out, channels);
        LittleEndian.writeU32(out, sampleRate);
        LittleEndian.writeU32(out, byteRate);
        LittleEndian.writeU16(out, blockAlign);
        LittleEndian.writeU16(out, bitsPerSample);
        LittleEndian.writeAscii(out, "data");
        LittleEndian.writeU32(out, dataBytes);
    }

    private static long ditherSeed(String sha256) {
        long seed = 0x444d535632L;
        if (sha256 != null && sha256.length() >= 16) {
            try { seed ^= Long.parseUnsignedLong(sha256.substring(0, 16), 16); }
            catch (NumberFormatException ignored) {}
        }
        return seed;
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private static final class Quantized24Writer {
        private final OutputStream output;
        private final Random dither;
        private final byte[] buffer = new byte[256 * 1024 - 2];
        private int position;
        long clips;
        long frames;

        Quantized24Writer(OutputStream output, long seed) {
            this.output = output;
            this.dither = new Random(seed);
        }

        void frame(double left, double right) throws IOException {
            sample(left);
            sample(right);
            frames++;
        }

        void finish() throws IOException {
            flush();
        }

        private void sample(double sample) throws IOException {
            if (position + 3 > buffer.length) flush();
            double tpdf = dither.nextDouble() - dither.nextDouble();
            long quantized = Math.round(finite(sample) * 8388608.0 + tpdf);
            if (quantized > 8388607) {
                quantized = 8388607;
                clips++;
            } else if (quantized < -8388608) {
                quantized = -8388608;
                clips++;
            }
            int value = (int) quantized;
            buffer[position++] = (byte) (value & 0xff);
            buffer[position++] = (byte) ((value >>> 8) & 0xff);
            buffer[position++] = (byte) ((value >>> 16) & 0xff);
        }

        private void flush() throws IOException {
            if (position > 0) output.write(buffer, 0, position);
            position = 0;
        }
    }
}
