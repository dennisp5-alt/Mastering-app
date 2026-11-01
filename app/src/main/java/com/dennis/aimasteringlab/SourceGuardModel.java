package com.dennis.aimasteringlab;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Detects previous-master and source-resolution conditions before new DSP. */
final class SourceGuardModel {
    static final class Result {
        String model = "DAML v0.14.5 source-history and lossy-source guard";
        int declaredBits;
        int effectiveBits;
        boolean artificial24From16;
        boolean likelyPriorDennisMaster;
        boolean alreadyCompliantReleaseMaster;
        boolean strongLimiterSignature;
        boolean likelyLossyInWav;
        boolean bandwidthCutoffDetected;
        boolean codecPreEchoDetected;
        boolean highBoostBlocked;
        boolean limiterEscalationBlocked;
        boolean secondPassGuard;
        double nearPeakShare;
        double highBandRelativeDb;
        int strongTransientEvents;
        int codecPreEchoCandidateEvents;
        double codecPreEchoShare;
        double estimatedCutoffHz = Double.NaN;
        String verdict;
        final List<String> evidence = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();

        boolean allowsBroadProcessing() {
            return !secondPassGuard && !likelyLossyInWav;
        }

        String compactSummary() {
            return verdict + " • declared/effective " + declaredBits + "/"
                    + effectiveBits + "-bit • cutoff "
                    + (Double.isFinite(estimatedCutoffHz)
                    ? String.format(Locale.US, "%.0f Hz", estimatedCutoffHz)
                    : "not detected") + " • limiter signature "
                    + (strongLimiterSignature ? "detected" : "not detected")
                    + " • compliant release container "
                    + (alreadyCompliantReleaseMaster ? "yes" : "no")
                    + " • second-pass guard "
                    + (secondPassGuard ? "active" : "inactive");
        }
    }

    private SourceGuardModel() {}

    static Result analyze(AudioAnalyzer.StreamProvider source,
                          AnalysisResult base,
                          AudioAnalyzer.Progress progress) throws IOException {
        Result out = new Result();
        out.declaredBits = base.wav.validBitsPerSample;
        int commonTrailing = 31;
        long quantized = 0L;
        long nearPeak = 0L;
        double peak = Math.pow(10.0, base.samplePeakDbfs / 20.0);
        double nearThreshold = Math.max(0.20, peak * Math.pow(10.0, -0.20 / 20.0));
        double midEnergy = 0.0;
        double highEnergy = 0.0;
        ArrayList<Double> blockWideEnergy = new ArrayList<>();
        ArrayList<Double> blockHighEnergy = new ArrayList<>();
        long frames = 0L;
        try (WavFile.Reader reader = WavFile.open(source.open())) {
            Biquad midHigh = Biquad.highPass(reader.info.sampleRate, 5000.0);
            Biquad midLow = Biquad.lowPass(reader.info.sampleRate, 14000.0);
            Biquad high1 = Biquad.highPass(reader.info.sampleRate, 16000.0);
            Biquad high2 = Biquad.highPass(reader.info.sampleRate, 16000.0);
            Biquad high3 = Biquad.highPass(reader.info.sampleRate, 16000.0);
            Biquad high4 = Biquad.highPass(reader.info.sampleRate, 16000.0);
            Biquad preEchoHigh = Biquad.highPass(reader.info.sampleRate, 5000.0);
            int preEchoBlock = Math.max(64, reader.info.sampleRate / 200);
            int preEchoFrames = 0;
            double preEchoWideSum = 0.0;
            double preEchoHighSum = 0.0;
            float[][] buffer = new float[Math.max(2, reader.info.channels)][4096];
            int read;
            while ((read = reader.readFrames(buffer, 4096)) > 0) {
                for (int n = 0; n < read; n++, frames++) {
                    double l = finite(buffer[0][n]);
                    double r = reader.info.channels > 1
                            ? finite(buffer[1][n]) : l;
                    double mono = 0.5 * (l + r);
                    double m = midLow.process(midHigh.process(mono));
                    double h = high4.process(high3.process(
                            high2.process(high1.process(mono))));
                    double preHigh = preEchoHigh.process(mono);
                    midEnergy += m * m;
                    highEnergy += h * h;
                    preEchoWideSum += 0.5 * (l * l + r * r);
                    preEchoHighSum += preHigh * preHigh;
                    if (++preEchoFrames == preEchoBlock) {
                        blockWideEnergy.add(preEchoWideSum / preEchoFrames);
                        blockHighEnergy.add(preEchoHighSum / preEchoFrames);
                        preEchoFrames = 0;
                        preEchoWideSum = 0.0;
                        preEchoHighSum = 0.0;
                    }
                    if (Math.max(Math.abs(l), Math.abs(r)) >= nearThreshold) nearPeak++;
                    commonTrailing = Math.min(commonTrailing,
                            trailingBits(l, reader.info.bitsPerSample));
                    commonTrailing = Math.min(commonTrailing,
                            trailingBits(r, reader.info.bitsPerSample));
                    quantized += 2;
                }
                if (progress != null) progress.update(
                        "Sonic Finish: screening previous-master and lossy-source evidence",
                        (int) Math.min(98, Math.round(98.0 * frames
                                / Math.max(1L, reader.info.frames))));
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("Source guard analysis cancelled");
                }
            }
            if (preEchoFrames > 0) {
                blockWideEnergy.add(preEchoWideSum / preEchoFrames);
                blockHighEnergy.add(preEchoHighSum / preEchoFrames);
            }
        }
        int containerBits = Math.min(24, Math.max(16, base.wav.bitsPerSample));
        out.effectiveBits = Math.max(1, containerBits - Math.max(0, commonTrailing));
        if (base.wav.bitsPerSample == 16) out.effectiveBits = 16;
        out.artificial24From16 = base.wav.bitsPerSample >= 24
                && out.effectiveBits <= 16;
        out.nearPeakShare = nearPeak / (double) Math.max(1L, frames);
        out.highBandRelativeDb = 10.0 * Math.log10(Math.max(1.0e-30,
                highEnergy / Math.max(1.0e-30, midEnergy)));
        out.bandwidthCutoffDetected = out.highBandRelativeDb < -34.0
                && base.rmsDbfs > -38.0;
        PreEchoStats preEcho = detectPreEcho(blockWideEnergy,
                blockHighEnergy);
        out.strongTransientEvents = preEcho.transients;
        out.codecPreEchoCandidateEvents = preEcho.candidates;
        out.codecPreEchoShare = preEcho.share;
        out.codecPreEchoDetected = preEcho.candidates >= 3
                && preEcho.share >= 0.18;
        if (out.bandwidthCutoffDetected) {
            out.estimatedCutoffHz = 16000.0;
            out.evidence.add("A steep high-frequency bandwidth limit was measured; it may indicate a codec cutoff or deliberate production filtering.");
        }
        if (out.codecPreEchoDetected) {
            out.evidence.add(String.format(Locale.US,
                    "Short high-frequency pre-onset energy preceded %d of %d measured strong transients (%.1f%%); compatible with transform-codec pre-echo but not conclusive alone.",
                    out.codecPreEchoCandidateEvents,
                    out.strongTransientEvents,
                    out.codecPreEchoShare * 100.0));
        }
        out.likelyLossyInWav = out.bandwidthCutoffDetected
                && (out.codecPreEchoDetected
                || out.highBandRelativeDb < -42.0);
        if (out.likelyLossyInWav) {
            out.warnings.add("Combined bandwidth and codec-artifact evidence is compatible with lossy material saved inside WAV; high-frequency boosting is blocked.");
        } else if (out.bandwidthCutoffDetected) {
            out.warnings.add("A bandwidth cutoff was measured but could be intentional; it remains source-aware evidence and high-frequency boosting is blocked.");
        }
        String name = base.sourceName == null ? ""
                : base.sourceName.toLowerCase(Locale.US);
        out.likelyPriorDennisMaster = name.contains("fullai")
                || name.contains("dms24") || name.contains("release_master")
                || name.contains("release master");
        out.alreadyCompliantReleaseMaster = base.alreadyCompliantNoRender;
        out.strongLimiterSignature = out.nearPeakShare > 0.010
                && base.crestFactorDb < 8.5 && base.lraLu < 5.0;
        // Sonic Finish does not equate loudness/limiting with a prior Dennis master.
        // Only positive source-history evidence activates the second-pass block.
        out.secondPassGuard = out.likelyPriorDennisMaster;
        out.highBoostBlocked = out.bandwidthCutoffDetected
                || out.likelyLossyInWav || out.secondPassGuard;
        out.limiterEscalationBlocked = out.secondPassGuard;
        if (out.artificial24From16) {
            out.warnings.add("The 24-bit container carries no more than 16-bit effective sample resolution; exporting at 24-bit does not recover lost detail.");
        }
        if (out.likelyPriorDennisMaster) {
            out.evidence.add("The filename and release-level measurements identify a likely previous Full AI or DMS master.");
        }
        if (out.alreadyCompliantReleaseMaster) {
            out.evidence.add("The input is a peak-safe 24-bit/48 kHz file within 0.10 LU of target. This is delivery-compliant, but Sonic Finish does not treat loudness compliance alone as proof that mastering is complete.");
        }
        if (out.strongLimiterSignature) {
            out.evidence.add(String.format(Locale.US,
                    "Stable near-peak occupancy %.4f with crest %.2f dB and LRA %.2f LU is compatible with previous limiting. Sonic Finish treats this as processing evidence, not proof of a previous Dennis master.",
                    out.nearPeakShare, base.crestFactorDb, base.lraLu));
        }
        if (out.secondPassGuard) {
            out.verdict = "High-confidence previous-master signature: broad re-mastering and limiter escalation blocked";
            out.warnings.add("Only a demonstrated narrow technical repair may compete because this source has high-confidence previous-master evidence; loudness compliance alone does not trigger this guard.");
            // Prevent the downstream transparent renderer from adding new peak pressure.
            double ceilingGain = -1.10 - base.truePeakEstimateDbtp;
            base.appliedSafeGainDb = Math.min(base.appliedSafeGainDb,
                    Math.max(0.0, ceilingGain));
            base.limiterRequired = false;
            base.limiterAllowed = false;
            base.estimatedLimiterReductionDb = 0.0;
        } else if (out.likelyLossyInWav || out.bandwidthCutoffDetected
                || out.artificial24From16) {
            out.verdict = "Source-aware processing active: resolution or codec limitation detected";
        } else {
            out.verdict = "No high-confidence previous-master or lossy-in-WAV condition detected";
        }
        if (progress != null) progress.update("Sonic Finish: source guard ready", 100);
        return out;
    }

    private static int trailingBits(double sample, int bits) {
        if (!Double.isFinite(sample) || sample == 0.0) return 31;
        int use = Math.min(24, Math.max(16, bits));
        long scale = 1L << (use - 1);
        long value = Math.round(sample * scale);
        long abs = Math.abs(value);
        if (abs == 0L) return 31;
        return Long.numberOfTrailingZeros(abs);
    }

    private static PreEchoStats detectPreEcho(List<Double> wide,
                                               List<Double> high) {
        PreEchoStats out = new PreEchoStats();
        int count = Math.min(wide.size(), high.size());
        for (int i = 6; i + 1 < count; i++) {
            double baselineWide = average(wide, i - 6, i - 2);
            double currentWide = wide.get(i);
            if (dbRatio(currentWide, baselineWide) < 11.0
                    || 10.0 * Math.log10(Math.max(1.0e-30,
                    currentWide)) < -38.0) continue;
            out.transients++;
            double baselineHigh = average(high, i - 6, i - 2);
            double preHigh = average(high, i - 2, i);
            double onsetHigh = average(high, i, i + 2);
            double preRise = dbRatio(preHigh, baselineHigh);
            double preToOnset = dbRatio(preHigh, onsetHigh);
            if (preRise >= 4.5 && preToOnset <= -1.5
                    && preToOnset >= -22.0) out.candidates++;
        }
        out.share = out.candidates / (double) Math.max(1, out.transients);
        return out;
    }

    private static double average(List<Double> values, int start, int end) {
        double sum = 0.0; int count = 0;
        for (int i = Math.max(0, start);
             i < Math.min(end, values.size()); i++) {
            sum += values.get(i); count++;
        }
        return sum / Math.max(1, count);
    }

    private static double dbRatio(double numerator, double denominator) {
        return 10.0 * Math.log10(Math.max(1.0e-30, numerator)
                / Math.max(1.0e-30, denominator));
    }

    private static final class PreEchoStats {
        int transients; int candidates; double share;
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }
}
