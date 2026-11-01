package com.dennis.aimasteringlab;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

final class AudioAnalyzer {
    interface StreamProvider {
        InputStream open() throws IOException;
    }

    interface Progress {
        void update(String stage, int percent);
    }

    private static final int BUFFER_FRAMES = 4096;
    private static final double TARGET_LUFS = -14.0;
    private static final double NO_OP_GAIN_TOLERANCE_DB = 0.01;
    static final double COMPLIANT_MASTER_TOLERANCE_LU = 0.10;
    private static final double TRUE_PEAK_CEILING_DBTP = -1.0;
    private static final double LIMITER_WORKING_CEILING_DBTP = -1.10;

    /** Normal full-mix limiter range before sparse-peak evidence is required. */
    static final double STANDARD_LIMITER_MAXIMUM_REDUCTION_DB = 3.00;
    /** Absolute ceiling for brief, measured peak-only release work. */
    static final double SPARSE_LIMITER_MAXIMUM_REDUCTION_DB = 5.50;
    static final double SPARSE_LIMITER_MAXIMUM_AVERAGE_DB = 0.90;
    static final double SPARSE_LIMITER_MAXIMUM_ABOVE_1DB_PERCENT = 20.0;
    static final double SPARSE_LIMITER_MAXIMUM_ABOVE_3DB_PERCENT = 5.0;

    static AnalysisResult analyze(String sourceName, StreamProvider provider, Progress progress)
            throws IOException {
        AnalysisResult result = new AnalysisResult();
        result.sourceName = sourceName;
        progress.update("Checksumming source", 0);
        result.sourceSha256 = sha256(provider.open());

        progress.update("Reading WAV structure", 2);
        try (WavFile.Reader reader = WavFile.open(provider.open())) {
            result.wav = reader.info;
            float[][] samples = new float[reader.info.channels][BUFFER_FRAMES];
            LoudnessMeter loudness = new LoudnessMeter(reader.info.channels, reader.info.sampleRate);
            Biquad lowL = Biquad.lowPass(reader.info.sampleRate, 160.0);
            Biquad lowR = reader.info.channels == 2
                    ? Biquad.lowPass(reader.info.sampleRate, 160.0) : null;
            TruePeakMeter[] tp = new TruePeakMeter[reader.info.channels];
            for (int c = 0; c < tp.length; c++) tp[c] = new TruePeakMeter();

            double[] sums = new double[reader.info.channels];
            double sumSquares = 0.0;
            double peak = 0.0;
            double sumL = 0, sumR = 0, sumLL = 0, sumRR = 0, sumLR = 0;
            double lowSumL = 0, lowSumR = 0, lowSumLL = 0, lowSumRR = 0, lowSumLR = 0;
            double monoSumSquares = 0.0;
            double monoPeak = 0.0;
            long stereoFrames = 0;
            long lowFrames = 0;
            long framesDone = 0;
            long zeroRun = 0;
            long zeroRunStart = -1L;
            long longestZeroRun = 0;
            long zeroRuns = 0;
            boolean seenAudio = false;
            double clipThreshold = reader.info.floatingPoint ? 1.0
                    : 1.0 - 1.0 / Math.pow(2.0, reader.info.validBitsPerSample - 1);

            int read;
            while ((read = reader.readFrames(samples, BUFFER_FRAMES)) > 0) {
                boolean firstBuffer = framesDone == 0;
                for (int f = 0; f < read; f++) {
                    boolean allZero = true;
                    double framePeak = 0.0;
                    for (int c = 0; c < reader.info.channels; c++) {
                        double v = samples[c][f];
                        if (!Double.isFinite(v)) {
                            result.nonFiniteSamples++;
                            v = 0.0;
                            samples[c][f] = 0.0f;
                        }
                        if (v != 0.0) allZero = false;
                        double av = Math.abs(v);
                        if (av >= clipThreshold) result.clippedSamples++;
                        if (av > peak) peak = av;
                        if (av > framePeak) framePeak = av;
                        sums[c] += v;
                        sumSquares += v * v;
                        tp[c].add(v);
                    }
                    loudness.addFrame(samples, f);
                    if (firstBuffer && f == 0) result.firstFramePeak = framePeak;
                    result.lastFramePeak = framePeak;

                    if (allZero) {
                        if (seenAudio) {
                            if (zeroRun == 0L) zeroRunStart = framesDone + f;
                            zeroRun++;
                        }
                    } else {
                        if (zeroRun >= Math.round(reader.info.sampleRate * 0.100)) {
                            zeroRuns++;
                            if (zeroRun > longestZeroRun) longestZeroRun = zeroRun;
                            AnalysisResult.ZeroRegion region =
                                    new AnalysisResult.ZeroRegion();
                            region.startFrame = Math.max(0L, zeroRunStart);
                            region.endFrameExclusive = framesDone + f;
                            result.internalZeroRegions.add(region);
                        }
                        zeroRun = 0;
                        zeroRunStart = -1L;
                        seenAudio = true;
                    }

                    if (reader.info.channels == 2) {
                        double l = samples[0][f];
                        double r = samples[1][f];
                        sumL += l;
                        sumR += r;
                        sumLL += l * l;
                        sumRR += r * r;
                        sumLR += l * r;
                        stereoFrames++;
                        double mono = 0.5 * (l + r);
                        monoSumSquares += mono * mono;
                        monoPeak = Math.max(monoPeak, Math.abs(mono));
                        double ll = lowL.process(l);
                        double lr = lowR.process(r);
                        lowSumL += ll;
                        lowSumR += lr;
                        lowSumLL += ll * ll;
                        lowSumRR += lr * lr;
                        lowSumLR += ll * lr;
                        lowFrames++;
                    }
                }
                framesDone += read;
                if (Thread.currentThread().isInterrupted()) throw new IOException("Analysis cancelled");
                int percent = 2 + (int) Math.min(96,
                        Math.round(96.0 * framesDone / reader.info.frames));
                progress.update("Measuring loudness, peaks and phase", percent);
            }

            LoudnessMeter.Result lr = loudness.finish();
            result.integratedLufs = lr.integratedLufs;
            result.lraLu = lr.lraLu;
            result.totalSamples = framesDone * reader.info.channels;
            result.internalZeroRuns = zeroRuns;
            result.longestInternalZeroSeconds = longestZeroRun / (double) reader.info.sampleRate;
            result.dcOffset = new double[reader.info.channels];
            for (int c = 0; c < reader.info.channels; c++) {
                result.dcOffset[c] = sums[c] / Math.max(1L, framesDone);
            }
            result.rmsDbfs = toDb(Math.sqrt(sumSquares / Math.max(1L, result.totalSamples)));
            result.samplePeakDbfs = toDb(peak);
            double truePeak = peak;
            for (TruePeakMeter estimator : tp) {
                estimator.finish();
                truePeak = Math.max(truePeak, estimator.peak());
            }
            result.truePeakEstimateDbtp = toDb(truePeak);
            result.crestFactorDb = result.samplePeakDbfs - result.rmsDbfs;
            if (reader.info.channels == 2) {
                result.stereoCorrelation = correlation(stereoFrames, sumL, sumR, sumLL, sumRR, sumLR);
                result.lowBandCorrelation = correlation(lowFrames, lowSumL, lowSumR,
                        lowSumLL, lowSumRR, lowSumLR);
                result.monoFoldRmsDbfs = toDb(Math.sqrt(
                        monoSumSquares / Math.max(1L, stereoFrames)));
                result.monoFoldPeakDbfs = toDb(monoPeak);
                result.monoFoldLossDb = result.monoFoldRmsDbfs - result.rmsDbfs;
            } else {
                result.stereoCorrelation = 1.0;
                result.lowBandCorrelation = 1.0;
                result.monoFoldRmsDbfs = result.rmsDbfs;
                result.monoFoldPeakDbfs = result.samplePeakDbfs;
                result.monoFoldLossDb = 0.0;
            }
            result.sections = TrackSections.detect(lr, reader.info.durationSeconds);

            if (!Double.isFinite(result.integratedLufs)) {
                throw new IOException("The WAV does not contain enough active audio for an integrated loudness result");
            }
            result.targetGainDb = TARGET_LUFS - result.integratedLufs;
            double flatTargetPeak = result.truePeakEstimateDbtp + result.targetGainDb;
            result.limiterRequired = flatTargetPeak > TRUE_PEAK_CEILING_DBTP;
            result.estimatedLimiterReductionDb = result.limiterRequired
                    ? Math.max(0.0, flatTargetPeak - LIMITER_WORKING_CEILING_DBTP) : 0.0;
            result.limiterReductionAllowanceDb = Math.max(1.50,
                    Math.min(STANDARD_LIMITER_MAXIMUM_REDUCTION_DB,
                            Math.max(0.0, result.lraLu) * 0.50));
            result.limiterAllowed = result.limiterRequired
                    && result.estimatedLimiterReductionDb
                    <= SPARSE_LIMITER_MAXIMUM_REDUCTION_DB;
            double peakSafeGain = LIMITER_WORKING_CEILING_DBTP - result.truePeakEstimateDbtp;
            result.appliedSafeGainDb = result.limiterRequired && !result.limiterAllowed
                    ? Math.min(result.targetGainDb, peakSafeGain) : result.targetGainDb;
            result.ceilingLimited = result.limiterRequired && !result.limiterAllowed;
            boolean releaseFormatReady = !reader.info.floatingPoint
                    && reader.info.bitsPerSample == 24
                    && reader.info.sampleRate == 48000
                    && reader.info.channels == 2
                    && result.truePeakEstimateDbtp <= TRUE_PEAK_CEILING_DBTP
                    && result.clippedSamples == 0
                    && result.nonFiniteSamples == 0;
            result.alreadyAtTargetNoRender = releaseFormatReady
                    && Math.abs(result.targetGainDb) <= NO_OP_GAIN_TOLERANCE_DB
                    && result.truePeakEstimateDbtp <= TRUE_PEAK_CEILING_DBTP;
            result.alreadyCompliantNoRender = isCompliantReleaseMaster(result);
            boolean dynamicsProtectedNoChange = releaseFormatReady
                    && result.ceilingLimited
                    && Math.abs(result.appliedSafeGainDb) <= NO_OP_GAIN_TOLERANCE_DB;
            result.noRenderNeeded = result.alreadyCompliantNoRender || dynamicsProtectedNoChange;
            result.noRenderReason = result.alreadyAtTargetNoRender
                    ? "Already-compliant 24-bit/48 kHz master is at target"
                    : result.alreadyCompliantNoRender
                    ? "Peak-safe 24-bit/48 kHz master is within 0.10 LU of target; preserve original audio"
                    : dynamicsProtectedNoChange
                    ? "No transparent gain is available without excessive limiting; preserve the dynamics-protected source"
                    : "A verified render is available";
            if (result.alreadyCompliantNoRender) {
                result.appliedSafeGainDb = 0.0;
                result.ceilingLimited = false;
                result.limiterRequired = false;
                result.limiterAllowed = false;
                result.estimatedLimiterReductionDb = 0.0;
            }
            result.predictedOutputLufs = result.integratedLufs + result.appliedSafeGainDb;
            result.predictedOutputTruePeakDbtp = result.limiterAllowed
                    ? LIMITER_WORKING_CEILING_DBTP
                    : result.truePeakEstimateDbtp + result.appliedSafeGainDb;
        }
        progress.update("Analysis complete", 100);
        return result;
    }

    static boolean isCompliantReleaseMaster(AnalysisResult result) {
        return result != null && result.wav != null
                && !result.wav.floatingPoint && result.wav.bitsPerSample == 24
                && result.wav.sampleRate == 48000 && result.wav.channels == 2
                && result.wav.frames > 0
                && Double.isFinite(result.integratedLufs)
                && Double.isFinite(result.truePeakEstimateDbtp)
                && Math.abs(result.integratedLufs - TARGET_LUFS)
                <= COMPLIANT_MASTER_TOLERANCE_LU
                && result.truePeakEstimateDbtp <= TRUE_PEAK_CEILING_DBTP
                && result.clippedSamples == 0 && result.nonFiniteSamples == 0;
    }

    private static String sha256(InputStream raw) throws IOException {
        try (InputStream in = raw) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[128 * 1024];
            int n;
            while ((n = in.read(buffer)) >= 0) {
                if (n > 0) digest.update(buffer, 0, n);
                if (Thread.currentThread().isInterrupted()) throw new IOException("Checksum cancelled");
            }
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest.digest()) hex.append(String.format(Locale.US, "%02x", b & 0xff));
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException(impossible);
        }
    }

    static double toDb(double linear) {
        return linear > 0.0 ? 20.0 * Math.log10(linear) : Double.NEGATIVE_INFINITY;
    }

    private static double correlation(long n, double sx, double sy,
                                      double sxx, double syy, double sxy) {
        if (n <= 1) return 0.0;
        double numerator = n * sxy - sx * sy;
        double denom = Math.sqrt(Math.max(0.0, n * sxx - sx * sx)
                * Math.max(0.0, n * syy - sy * sy));
        if (denom <= 1e-20) return 0.0;
        return Math.max(-1.0, Math.min(1.0, numerator / denom));
    }

}
