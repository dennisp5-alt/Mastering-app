package com.dennis.aimasteringlab;

import java.io.*;
import java.util.Arrays;
import java.util.Locale;

/**
 * v0.5.3 — loud masters (rock, pop, country): targets above −12.5 LUFS.
 *
 * The streaming delivery only allows ~3 dB of peak limiting with almost none on
 * average, so a −10 LUFS request came back quieter. Loud delivery works the way a
 * mastering engineer gets level: a gentle stereo-linked glue compressor (1.6:1, soft
 * knee, 30 ms attack, 250 ms release) holds the loudest passages a little, then the
 * 4× oversampled limiter takes the peaks to the true-peak ceiling.
 *
 * Every attempt is measured. It must keep the song musical — limiter at most 6 dB on
 * its loudest peak and 1.2 dB on average, under 35% of the song limited by more than
 * 1 dB and under 6% by more than 3 dB, loudness range within 2.5 LU and crest within
 * 4.5 dB of the original, no clipped samples, true peak under the ceiling. When the
 * requested loudness cannot pass, the target is lowered 0.5 LU at a time (at most
 * 2 LU) and the result says exactly how loud it got and why.
 */
final class LoudDelivery {
    static final double LOUD_FROM_LUFS = -12.5;
    private static final double RATIO = 1.6, KNEE_DB = 6.0, ATTACK_MS = 30.0, RELEASE_MS = 250.0;

    private LoudDelivery() {}

    static boolean applies(StudioRecipe r) { return r.target > LOUD_FROM_LUFS; }

    static final class Glue {
        double thresholdDb, meanReductionDb, maxReductionDb;
    }

    /** RMS level (dBFS) of 50 ms blocks after [gainDb], loud-part percentile [q]. */
    static double blockLevelPercentile(File source, double gainDb, double q) throws IOException {
        double g = Math.pow(10, gainDb / 20);
        double[] levels = new double[1 << 16];
        int n = 0;
        try (WavFile.Reader r = WavFile.open(new FileInputStream(source))) {
            int block = Math.max(1, r.info.sampleRate / 20);
            float[][] b = new float[2][block];
            int got;
            while ((got = r.readFrames(b, block)) > 0) {
                double sum = 0;
                for (int i = 0; i < got; i++) {
                    double l = b[0][i] * g, rr = b[1][i] * g;
                    sum += (l * l + rr * rr) * 0.5;
                }
                double db = 10 * Math.log10(Math.max(1e-12, sum / got));
                if (db > -50) {
                    if (n == levels.length) levels = Arrays.copyOf(levels, n * 2);
                    levels[n++] = db;
                }
                StudioAudio.cancel();
            }
        }
        if (n == 0) return -20;
        Arrays.sort(levels, 0, n);
        return levels[Math.min(n - 1, (int) (q * (n - 1)))];
    }

    /** Gain then glue compression, written as 32-bit float. */
    static Glue glue(File source, File output, double gainDb, double thresholdDb, long frames) throws IOException {
        Glue out = new Glue();
        out.thresholdDb = thresholdDb;
        double g = Math.pow(10, gainDb / 20);
        double detectorTc, attack, release;
        double sumGr = 0, maxGr = 0;
        long counted = 0;
        try (WavFile.Reader r = WavFile.open(new FileInputStream(source));
             PcmExportWriter w = new PcmExportWriter(output, r.info.sampleRate, 32, frames)) {
            double rate = r.info.sampleRate;
            detectorTc = Math.exp(-1.0 / (0.010 * rate));
            attack = Math.exp(-1.0 / (ATTACK_MS / 1000.0 * rate));
            release = Math.exp(-1.0 / (RELEASE_MS / 1000.0 * rate));
            double power = 1e-9, gr = 0;
            float[][] b = new float[2][8192];
            int got;
            while ((got = r.readFrames(b, 8192)) > 0) {
                for (int i = 0; i < got; i++) {
                    double l = b[0][i] * g, rr = b[1][i] * g;
                    power = detectorTc * power + (1 - detectorTc) * (l * l + rr * rr) * 0.5;
                    double level = 10 * Math.log10(Math.max(1e-12, power));
                    double over = level - thresholdDb, target;
                    if (over <= -KNEE_DB / 2) target = 0;
                    else if (over >= KNEE_DB / 2) target = over * (1 - 1 / RATIO);
                    else target = (1 - 1 / RATIO) * (over + KNEE_DB / 2) * (over + KNEE_DB / 2) / (2 * KNEE_DB);
                    gr = target > gr ? attack * gr + (1 - attack) * target : release * gr + (1 - release) * target;
                    double k = Math.pow(10, -gr / 20);
                    w.frame(l * k, rr * k);
                    sumGr += gr;
                    counted++;
                    if (gr > maxGr) maxGr = gr;
                }
                StudioAudio.cancel();
            }
        }
        out.meanReductionDb = counted == 0 ? 0 : sumGr / counted;
        out.maxReductionDb = maxGr;
        return out;
    }

    /** Null when [d] is musical enough at loud levels, else the reason. */
    static String unsafe(AnalysisResult before, MasterRenderer.Result m, AnalysisResult after, double ceiling) {
        if (m.quantizerClips != 0) return "clipped samples";
        if (after.truePeakEstimateDbtp > ceiling + .005) return "true peak over the ceiling";
        if (m.maximumLimiterReductionDb > 6.0) return String.format(Locale.US, "limiter peaks %.1f dB", m.maximumLimiterReductionDb);
        if (m.averageLimiterReductionDb > 1.2) return String.format(Locale.US, "limiter average %.1f dB", m.averageLimiterReductionDb);
        if (m.limiterFramesAboveOneDbPercent > 35) return String.format(Locale.US, "%.0f%% of the song limited over 1 dB", m.limiterFramesAboveOneDbPercent);
        if (m.limiterFramesAboveThreeDbPercent > 6) return String.format(Locale.US, "%.0f%% of the song limited over 3 dB", m.limiterFramesAboveThreeDbPercent);
        if (before.lraLu - after.lraLu > 2.5) return String.format(Locale.US, "dynamics squashed by %.1f LU", before.lraLu - after.lraLu);
        if (before.crestFactorDb - after.crestFactorDb > 4.5) return String.format(Locale.US, "punch reduced by %.1f dB", before.crestFactorDb - after.crestFactorDb);
        return null;
    }

    static StudioAudio.Delivery deliver(File source, AnalysisResult a, StudioRecipe r, File output,
                                        AudioAnalyzer.Progress progress) throws IOException {
        File glued = new File(output.getParentFile(), output.getName() + ".glue.wav");
        String firstFailure = null;
        try {
            for (int step = 0; step <= 4; step++) {
                double target = r.target - 0.5 * step;
                if (target <= LOUD_FROM_LUFS - 0.5) break;
                progress.update(String.format(Locale.US, "Loud master • aiming for %.1f LUFS", target), Math.min(90, step * 20));
                // Glue: about 2 dB under target on the incoming level, so the compressor
                // holds the loudest fifth of the song gently.
                double pre = target - a.integratedLufs - 2.0;
                double threshold = blockLevelPercentile(source, pre, 0.80);
                Glue glue = glue(source, glued, pre, threshold, a.wav.frames);
                AnalysisResult ga = StudioAudio.measure(glued, (s, x) -> {});
                double gain = target - ga.integratedLufs;
                double limiterCeiling = r.ceiling - .18;
                StudioAudio.Delivery d = new StudioAudio.Delivery();
                d.file = output;
                String why = null;
                int loudnessPasses = 0, peakPasses = 0;
                while (true) {
                    d.render = MasterRenderer.renderAttempt(() -> new FileInputStream(glued), ga, gain, true,
                            limiterCeiling, MasterRenderer.LimiterProfile.OVERSAMPLED_CLEAN, output, progress);
                    d.analysis = StudioAudio.measure(output, (s, x) -> {});
                    StudioAudio.valid(d.analysis, a.wav.frames);
                    double overshoot = d.analysis.truePeakEstimateDbtp - r.ceiling;
                    if (overshoot > .005) {
                        if (++peakPasses > 3) { why = "true peak over the ceiling"; break; }
                        limiterCeiling -= overshoot + .03;
                        d.limiterPeakCorrections = peakPasses;
                        continue;
                    }
                    why = unsafe(a, d.render, d.analysis, r.ceiling);
                    if (why != null) break;
                    double deficit = target - d.analysis.integratedLufs;
                    if (Math.abs(deficit) <= .10) break;
                    if (++loudnessPasses > 2) { why = String.format(Locale.US, "settled at %.1f LUFS", d.analysis.integratedLufs); break; }
                    gain += deficit;
                }
                if (why == null) {
                    d.limiterCeilingDbtp = limiterCeiling;
                    d.loud = true;
                    d.glueMeanReductionDb = glue.meanReductionDb;
                    d.glueMaxReductionDb = glue.maxReductionDb;
                    d.note = String.format(Locale.US,
                            "Loud master %.1f LUFS%s • glue %.1f dB avg • limiter %.1f dB peak, %.1f dB avg",
                            d.analysis.integratedLufs,
                            step == 0 ? "" : String.format(Locale.US, " (asked %.1f: %s there)", r.target, firstFailure),
                            glue.meanReductionDb, d.render.maximumLimiterReductionDb, d.render.averageLimiterReductionDb);
                    return d;
                }
                if (firstFailure == null) firstFailure = why;
            }
        } finally {
            WavRenderIntegrity.discard(glued);
        }
        // Could not get loud cleanly: the streaming delivery at the requested target,
        // which delivers quieter and says so.
        StudioAudio.Delivery d = StudioAudio.deliverStandard(source, a, r, output, progress);
        d.note = "Loud master not possible cleanly (" + firstFailure + "); " + d.note;
        return d;
    }
}
