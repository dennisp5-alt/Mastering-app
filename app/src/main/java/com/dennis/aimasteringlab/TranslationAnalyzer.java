package com.dennis.aimasteringlab;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class TranslationAnalyzer {
    static final class DeviceResult {
        String name;
        boolean mono;
        double rawResponsePeakDbfs;
        double rawResponseRmsDbfs;
        double requestedLevelMatchGainDb;
        double appliedLevelMatchGainDb;
        boolean levelMatchPeakLimited;
        double rawHeadroomStressDb;
        double peakDbfs;
        double rmsDbfs;
        double vocalBandShareDb;
        double stereoCorrelation;
        double chorusLiftDb;
        double busyLiftDb;
        double sourceChorusLiftDb = Double.NaN;
        double sourceBusyLiftDb = Double.NaN;
        double chorusLiftDeltaDb = Double.NaN;
        double busyLiftDeltaDb = Double.NaN;
        double sourceStereoCorrelation = Double.NaN;
        boolean clippingRisk;
        boolean monoPhaseRisk;
        boolean sourceMonoPhaseRisk;
        boolean masteringIntroducedMonoPhaseRisk;
        boolean absoluteLiftAboveFloor;
        boolean sourceRelativeLiftSafe;
        boolean masteringIntroducedLiftRisk;
        boolean liftPreserved;
        String verdict;
        final List<String> reviewReasons = new ArrayList<>();
        final List<String> diagnosticNotes = new ArrayList<>();
    }

    static final class Result {
        String method = "offline response-profile simulation with RMS level matching capped to the unfiltered composite peak; raw profile gain is reported separately and this is not a physical loudspeaker listening test";
        String levelMatchPolicy = "request the unfiltered composite RMS; cap gain so the simulated response cannot exceed the unfiltered composite sample peak";
        double referencePeakDbfs;
        double referenceRmsDbfs;
        final List<DeviceResult> devices = new ArrayList<>();
        int passCount;
        int reviewCount;
        boolean sourceRelative;
        int sourceAbsolutePassCount;
        int sourceAbsoluteReviewCount;
        double permittedLiftReductionDb = 0.25;
    }

    static final Profile[] PROFILES = new Profile[]{
            new Profile("Philips NX4-style home system", 45, 18000, 3.0, -1.0, 1.0, false, 0.03),
            new Profile("JBL Charge 3-style Bluetooth speaker", 55, 16000, 2.0, -1.5, -1.0, false, 0.12),
            new Profile("Samsung Galaxy S23 Ultra stereo speakers", 180, 17000, -2.0, 2.0, 1.0, false, 0.16),
            new Profile("Samsung Galaxy A54 stereo speakers", 220, 15000, -2.5, 2.5, 0.0, false, 0.20),
            new Profile("iPhone 16e-style stereo speakers", 170, 17000, -1.5, 2.0, 0.5, false, 0.18),
            new Profile("Small mono phone speaker", 350, 7500, -4.0, 3.0, -2.0, true, 0.50),
            new Profile("Galaxy Buds2 Pro", 25, 20000, 2.0, 0.0, 1.0, false, 0.02),
            new Profile("Galaxy Buds2", 30, 19000, 3.0, -0.5, -1.0, false, 0.03),
            new Profile("Sennheiser HD 650-style neutral reference", 20, 20000, 0.0, -1.0, -1.0, false, 0.01),
            new Profile("Sony MDR-7506-style bright reference", 30, 20000, -1.0, 2.5, 2.0, false, 0.01),
            new Profile("Small bookshelf home stereo", 50, 19000, 1.0, 0.0, 0.5, false, 0.05),
            new Profile("Car-style cabin response", 35, 16000, 4.0, -2.0, -1.0, false, 0.10)
    };

    private TranslationAnalyzer() {}

    static Result analyze(File composite, TranslationComposite.Result map,
                          AudioAnalyzer.Progress progress) throws IOException {
        Result result = new Result();
        ReferenceLevel reference = measureReference(composite);
        result.referencePeakDbfs = reference.peakDbfs;
        result.referenceRmsDbfs = reference.rmsDbfs;
        for (int i = 0; i < PROFILES.length; i++) {
            DeviceResult device = analyzeProfile(composite, map, PROFILES[i], reference);
            result.devices.add(device);
            if ("PASS".equals(device.verdict)) result.passCount++;
            else result.reviewCount++;
            progress.update("Testing translation profile " + (i + 1) + "/" + PROFILES.length,
                    (int) Math.round(100.0 * (i + 1) / PROFILES.length));
            if (Thread.currentThread().isInterrupted()) throw new IOException("Translation analysis cancelled");
        }
        return result;
    }

    /**
     * Re-judges the final simulations against the same profiles applied to the
     * processing source. An inherited arrangement weakness remains visible as
     * a diagnostic, but only a material mastering regression causes REVIEW.
     */
    static void applySourceBaseline(Result mastered, Result source) throws IOException {
        if (mastered == null || source == null
                || mastered.devices.size() != source.devices.size()) {
            throw new IOException("Source-relative translation comparison was incomplete");
        }
        mastered.sourceRelative = true;
        mastered.sourceAbsolutePassCount = source.passCount;
        mastered.sourceAbsoluteReviewCount = source.reviewCount;
        mastered.method = "source-relative before/after use of the same 12 offline response profiles; absolute source weaknesses remain diagnostic and only mastering-introduced deterioration changes the verdict";
        mastered.passCount = 0;
        mastered.reviewCount = 0;
        for (int index = 0; index < mastered.devices.size(); index++) {
            DeviceResult out = mastered.devices.get(index);
            DeviceResult baseline = source.devices.get(index);
            if (!out.name.equals(baseline.name)) {
                throw new IOException("Translation profile ordering changed during source comparison");
            }
            out.sourceChorusLiftDb = baseline.chorusLiftDb;
            out.sourceBusyLiftDb = baseline.busyLiftDb;
            out.sourceStereoCorrelation = baseline.stereoCorrelation;
            out.sourceMonoPhaseRisk = baseline.monoPhaseRisk;
            out.chorusLiftDeltaDb = finiteDifference(
                    out.chorusLiftDb, baseline.chorusLiftDb);
            out.busyLiftDeltaDb = finiteDifference(
                    out.busyLiftDb, baseline.busyLiftDb);

            removeReason(out.reviewReasons,
                    "quiet-to-chorus lift was below 0.5 dB");
            removeReason(out.reviewReasons,
                    "stereo correlation fell below -0.10");

            out.absoluteLiftAboveFloor = Double.isFinite(out.chorusLiftDb)
                    && out.chorusLiftDb >= 0.5;
            out.sourceRelativeLiftSafe = Double.isFinite(out.chorusLiftDeltaDb)
                    && out.chorusLiftDeltaDb >= -mastered.permittedLiftReductionDb;
            out.masteringIntroducedLiftRisk = !out.sourceRelativeLiftSafe;
            out.liftPreserved = out.sourceRelativeLiftSafe;
            if (out.masteringIntroducedLiftRisk) {
                out.reviewReasons.add(String.format(java.util.Locale.US,
                        "mastering reduced source quiet-to-chorus lift by %.3f dB",
                        -out.chorusLiftDeltaDb));
            } else if (!out.absoluteLiftAboveFloor) {
                out.diagnosticNotes.add(String.format(java.util.Locale.US,
                        "quiet-to-chorus lift remained below 0.5 dB but changed only %+.3f dB from the source",
                        out.chorusLiftDeltaDb));
            }

            out.masteringIntroducedMonoPhaseRisk = out.monoPhaseRisk
                    && (!baseline.monoPhaseRisk
                    || out.stereoCorrelation < baseline.stereoCorrelation - 0.02);
            if (out.masteringIntroducedMonoPhaseRisk) {
                out.reviewReasons.add("mastering introduced or materially worsened the simulated stereo phase risk");
            } else if (out.monoPhaseRisk) {
                out.diagnosticNotes.add("the simulated phase risk was already present in the processing source");
            }

            out.verdict = !out.clippingRisk
                    && !out.masteringIntroducedMonoPhaseRisk
                    && !out.masteringIntroducedLiftRisk
                    ? "PASS" : "REVIEW";
            if ("PASS".equals(out.verdict)) mastered.passCount++;
            else mastered.reviewCount++;
        }
    }

    private static DeviceResult analyzeProfile(File composite,
                                               TranslationComposite.Result map,
                                               Profile profile,
                                               ReferenceLevel reference) throws IOException {
        DeviceResult out = new DeviceResult();
        out.name = profile.name;
        out.mono = profile.mono;
        try (WavFile.Reader reader = WavFile.open(new FileInputStream(composite))) {
            FilterChain left = new FilterChain(reader.info.sampleRate, profile);
            FilterChain right = new FilterChain(reader.info.sampleRate, profile);
            Biquad vocalHigh = Biquad.highPass(reader.info.sampleRate, 250.0);
            Biquad vocalLow = Biquad.lowPass(reader.info.sampleRate, 5000.0);
            float[][] samples = new float[2][4096];
            long n = 0;
            double sumSquares = 0.0;
            double vocalSquares = 0.0;
            double peak = 0.0;
            double sx = 0.0, sy = 0.0, sxx = 0.0, syy = 0.0, sxy = 0.0;
            double[] segmentEnergy = new double[map.segments.size()];
            long[] segmentSamples = new long[map.segments.size()];
            long frameIndex = 0;
            int read;
            while ((read = reader.readFrames(samples, 4096)) > 0) {
                for (int frame = 0; frame < read; frame++, frameIndex++) {
                    double l = left.process(samples[0][frame]);
                    double r = right.process(samples[1][frame]);
                    double mixedL = l * (1.0 - profile.crossfeed) + r * profile.crossfeed;
                    double mixedR = r * (1.0 - profile.crossfeed) + l * profile.crossfeed;
                    if (profile.mono) {
                        double mono = 0.5 * (mixedL + mixedR);
                        mixedL = mono;
                        mixedR = mono;
                    }
                    peak = Math.max(peak, Math.max(Math.abs(mixedL), Math.abs(mixedR)));
                    sumSquares += mixedL * mixedL + mixedR * mixedR;
                    double mono = 0.5 * (mixedL + mixedR);
                    double vocal = vocalLow.process(vocalHigh.process(mono));
                    vocalSquares += vocal * vocal * 2.0;
                    sx += mixedL;
                    sy += mixedR;
                    sxx += mixedL * mixedL;
                    syy += mixedR * mixedR;
                    sxy += mixedL * mixedR;
                    n++;
                    for (int segment = 0; segment < map.segments.size(); segment++) {
                        TranslationComposite.SegmentMap item = map.segments.get(segment);
                        long start = Math.round(item.compositeStartSeconds * reader.info.sampleRate);
                        long end = start + Math.round(item.sourceDurationSeconds * reader.info.sampleRate);
                        if (frameIndex >= start && frameIndex < end) {
                            segmentEnergy[segment] += mixedL * mixedL + mixedR * mixedR;
                            segmentSamples[segment] += 2;
                            break;
                        }
                    }
                }
            }
            out.rawResponsePeakDbfs = AudioAnalyzer.toDb(peak);
            out.rawResponseRmsDbfs = AudioAnalyzer.toDb(
                    Math.sqrt(sumSquares / Math.max(1L, n * 2L)));
            out.requestedLevelMatchGainDb = reference.rmsDbfs
                    - out.rawResponseRmsDbfs;
            double peakLimitedGain = reference.peakDbfs
                    - out.rawResponsePeakDbfs;
            out.appliedLevelMatchGainDb = Math.min(
                    out.requestedLevelMatchGainDb, peakLimitedGain);
            if (!Double.isFinite(out.appliedLevelMatchGainDb)) {
                out.appliedLevelMatchGainDb = 0.0;
            }
            out.levelMatchPeakLimited = out.appliedLevelMatchGainDb
                    < out.requestedLevelMatchGainDb - 0.001;
            out.rawHeadroomStressDb = Math.max(0.0, out.rawResponsePeakDbfs);
            out.peakDbfs = out.rawResponsePeakDbfs + out.appliedLevelMatchGainDb;
            out.rmsDbfs = out.rawResponseRmsDbfs + out.appliedLevelMatchGainDb;
            out.vocalBandShareDb = sumSquares > 0.0
                    ? 10.0 * Math.log10(Math.max(1.0e-30, vocalSquares / sumSquares))
                    : Double.NEGATIVE_INFINITY;
            out.stereoCorrelation = correlation(n, sx, sy, sxx, syy, sxy);
            double quiet = segmentDb(map, segmentEnergy, segmentSamples,
                    "quiet_vocal_or_verse_proxy");
            double chorus = segmentDb(map, segmentEnergy, segmentSamples,
                    "full_chorus_proxy");
            double busy = segmentDb(map, segmentEnergy, segmentSamples,
                    "busiest_alternate_proxy");
            out.chorusLiftDb = chorus - quiet;
            out.busyLiftDb = busy - quiet;
            out.clippingRisk = out.peakDbfs > 0.0;
            out.monoPhaseRisk = !profile.mono && out.stereoCorrelation < -0.10;
            out.absoluteLiftAboveFloor = Double.isFinite(out.chorusLiftDb)
                    && out.chorusLiftDb >= 0.5;
            out.sourceRelativeLiftSafe = out.absoluteLiftAboveFloor;
            out.liftPreserved = out.absoluteLiftAboveFloor;
            if (out.clippingRisk) {
                out.reviewReasons.add("level-matched response exceeded 0 dBFS");
            }
            if (out.monoPhaseRisk) {
                out.reviewReasons.add("stereo correlation fell below -0.10");
            }
            if (!out.liftPreserved) {
                out.reviewReasons.add("quiet-to-chorus lift was below 0.5 dB");
            }
            out.verdict = !out.clippingRisk && !out.monoPhaseRisk && out.liftPreserved
                    ? "PASS" : "REVIEW";
        }
        return out;
    }

    private static ReferenceLevel measureReference(File composite) throws IOException {
        ReferenceLevel out = new ReferenceLevel();
        try (WavFile.Reader reader = WavFile.open(new FileInputStream(composite))) {
            float[][] samples = new float[2][4096];
            long sampleCount = 0L;
            double sumSquares = 0.0;
            double peak = 0.0;
            int read;
            while ((read = reader.readFrames(samples, 4096)) > 0) {
                for (int frame = 0; frame < read; frame++) {
                    double left = samples[0][frame];
                    double right = samples[1][frame];
                    peak = Math.max(peak, Math.max(Math.abs(left), Math.abs(right)));
                    sumSquares += left * left + right * right;
                    sampleCount += 2L;
                }
            }
            out.peakDbfs = AudioAnalyzer.toDb(peak);
            out.rmsDbfs = AudioAnalyzer.toDb(Math.sqrt(
                    sumSquares / Math.max(1L, sampleCount)));
        }
        return out;
    }

    private static final class ReferenceLevel {
        double peakDbfs;
        double rmsDbfs;
    }

    private static double segmentDb(TranslationComposite.Result map, double[] energy,
                                    long[] samples, String role) {
        for (int i = 0; i < map.segments.size(); i++) {
            if (roleMatches(role, map.segments.get(i).role)) {
                return samples[i] > 0
                        ? 10.0 * Math.log10(Math.max(1.0e-30, energy[i] / samples[i]))
                        : Double.NaN;
            }
        }
        return Double.NaN;
    }

    private static boolean roleMatches(String requested, String actual) {
        if (requested.equals(actual)) return true;
        if (actual == null) return false;
        if ("quiet_vocal_or_verse_proxy".equals(requested)) {
            return actual.contains("quiet") || actual.contains("verse");
        }
        if ("full_chorus_proxy".equals(requested)) {
            return actual.contains("chorus");
        }
        if ("busiest_alternate_proxy".equals(requested)) {
            return actual.contains("busiest") || actual.contains("busy");
        }
        return false;
    }

    private static double finiteDifference(double value, double baseline) {
        return Double.isFinite(value) && Double.isFinite(baseline)
                ? value - baseline : Double.NaN;
    }

    private static void removeReason(List<String> reasons, String reason) {
        for (int index = reasons.size() - 1; index >= 0; index--) {
            if (reason.equals(reasons.get(index))) reasons.remove(index);
        }
    }

    private static double correlation(long n, double sx, double sy,
                                      double sxx, double syy, double sxy) {
        if (n <= 1) return 0.0;
        double numerator = n * sxy - sx * sy;
        double denominator = Math.sqrt(Math.max(0.0, n * sxx - sx * sx)
                * Math.max(0.0, n * syy - sy * sy));
        return denominator > 1.0e-30 ? Math.max(-1.0, Math.min(1.0, numerator / denominator)) : 0.0;
    }

    static final class Profile {
        final String name;
        final double highPass;
        final double lowPass;
        final double bassDb;
        final double presenceDb;
        final double trebleDb;
        final boolean mono;
        final double crossfeed;

        Profile(String name, double highPass, double lowPass, double bassDb,
                double presenceDb, double trebleDb, boolean mono, double crossfeed) {
            this.name = name;
            this.highPass = highPass;
            this.lowPass = lowPass;
            this.bassDb = bassDb;
            this.presenceDb = presenceDb;
            this.trebleDb = trebleDb;
            this.mono = mono;
            this.crossfeed = crossfeed;
        }
    }

    static final class FilterChain {
        final Biquad highPass;
        final Biquad lowShelf;
        final Biquad presence;
        final Biquad highShelf;
        final Biquad lowPass;

        FilterChain(int sampleRate, Profile profile) {
            highPass = Biquad.highPass(sampleRate, profile.highPass);
            lowShelf = Biquad.lowShelf(sampleRate, 110.0, profile.bassDb);
            presence = Biquad.peaking(sampleRate, 2800.0, 0.8, profile.presenceDb);
            highShelf = Biquad.highShelf(sampleRate, 7000.0, profile.trebleDb);
            lowPass = Biquad.lowPass(sampleRate, profile.lowPass);
        }

        double process(double value) {
            return lowPass.process(highShelf.process(presence.process(
                    lowShelf.process(highPass.process(value)))));
        }
    }
}
