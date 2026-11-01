package com.dennis.aimasteringlab;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Offline mastering controller for the separate AI laboratory app.
 *
 * This is deliberately a hybrid controller rather than a waveform generator:
 * it learns robust, song-specific feature distributions, proposes bounded
 * console settings, and leaves rendering and final safety verification to the
 * deterministic DSP engine.
 */
final class AutonomousMasteringBrain {
    enum Mode { CONSERVATIVE, PREFERRED }

    static final class Features {
        int windows;
        int activeWindows;
        double activeFloorDbfs;
        double lowShareDb;
        double lowMidShareDb;
        double presenceShareDb;
        double harshShareDb;
        double airShareDb;
        double sideShareDb;
        double medianWindowCrestDb;
        double highWindowCrestDb;
        double reliability;

        String compactSummary() {
            return String.format(Locale.US,
                    "%d/%d active windows • low %.1f • low-mid %.1f • presence %.1f • harsh %.1f • air %.1f dB share • side %.1f dB",
                    activeWindows, windows, lowShareDb, lowMidShareDb,
                    presenceShareDb, harshShareDb, airShareDb, sideShareDb);
        }
    }

    static final class Plan {
        Mode mode;
        String strategy;
        double lowShelfDb;
        double lowMidDb;
        double presenceDb;
        double highShelfDb;
        double sideScale = 1.0;
        double eventScale;
        boolean compressorEnabled;
        double compressorThresholdDbfs;
        double compressorRatio = 1.0;
        double compressorAttackMs;
        double compressorReleaseMs;
        double compressorMaximumReductionDb;
        double confidence;
        double objectiveBefore;
        double predictedObjectiveAfter;
        final List<String> decisions = new ArrayList<>();
        final List<String> bypassed = new ArrayList<>();

        Plan copy(String nextStrategy) {
            Plan out = new Plan();
            out.mode = mode;
            out.strategy = nextStrategy;
            out.lowShelfDb = lowShelfDb;
            out.lowMidDb = lowMidDb;
            out.presenceDb = presenceDb;
            out.highShelfDb = highShelfDb;
            out.sideScale = sideScale;
            out.eventScale = eventScale;
            out.compressorEnabled = compressorEnabled;
            out.compressorThresholdDbfs = compressorThresholdDbfs;
            out.compressorRatio = compressorRatio;
            out.compressorAttackMs = compressorAttackMs;
            out.compressorReleaseMs = compressorReleaseMs;
            out.compressorMaximumReductionDb = compressorMaximumReductionDb;
            out.confidence = confidence;
            out.objectiveBefore = objectiveBefore;
            out.predictedObjectiveAfter = predictedObjectiveAfter;
            out.decisions.addAll(decisions);
            out.bypassed.addAll(bypassed);
            return out;
        }

        boolean hasAudioChanges() {
            return Math.abs(lowShelfDb) > 0.005
                    || Math.abs(lowMidDb) > 0.005
                    || Math.abs(presenceDb) > 0.005
                    || Math.abs(highShelfDb) > 0.005
                    || Math.abs(sideScale - 1.0) > 0.0005
                    || eventScale > 0.0
                    || compressorEnabled;
        }

        String compactSummary() {
            return String.format(Locale.US,
                    "%s • confidence %.2f • EQ [%+.2f, %+.2f, %+.2f, %+.2f] dB • side %.3f • events %.2f • compression %s",
                    strategy == null
                            ? (mode == Mode.CONSERVATIVE ? "Conservative" : "AI preferred")
                            : strategy,
                    confidence, lowShelfDb, lowMidDb, presenceDb, highShelfDb,
                    sideScale, eventScale,
                    compressorEnabled
                            ? String.format(Locale.US, "%.2f:1 / %.2f dB max",
                            compressorRatio, compressorMaximumReductionDb)
                            : "bypassed");
        }
    }

    static final class Result {
        String model = "DAML v0.14.5 Broad Mastering Requirement controller";
        String method = "offline robust feature learning + Sonic Finish candidate competition + corrective EQ + dynamic EQ + stereo-linked mastering compression + independently verified cleanup/limiter competition + technical tone/channel candidates + diagnostic reference/translation simulations + tiered stems + deterministic DSP; no generative waveform model";
        Features features;
        Plan conservative;
        Plan preferred;
        AlbumConsistencyModel.Profile albumProfile;
        final List<Plan> internalPlans = new ArrayList<>();
        final List<String> limitations = new ArrayList<>();

        String compactSummary() {
            return features.compactSummary() + "\n" + conservative.compactSummary()
                    + "\n" + preferred.compactSummary();
        }
    }

    private static final int BUFFER_FRAMES = 4096;
    private static final double WINDOW_SECONDS = 0.500;

    private AutonomousMasteringBrain() {}

    static Result analyze(AudioAnalyzer.StreamProvider provider,
                          AnalysisResult base,
                          AudioAnalyzer.Progress progress) throws IOException {
        return analyze(provider, base, null, progress);
    }

    static Result analyze(AudioAnalyzer.StreamProvider provider,
                          AnalysisResult base,
                          AlbumConsistencyModel.Profile albumProfile,
                          AudioAnalyzer.Progress progress) throws IOException {
        if (base.wav.channels != 2) {
            throw new IOException("AI mastering requires a stereo authoritative WAV");
        }
        progress.update("AI mastering brain: learning whole-song features", 0);
        Features features = extract(provider, base, progress);
        Result result = new Result();
        result.features = features;
        result.albumProfile = albumProfile;
        result.conservative = makePlan(Mode.CONSERVATIVE, features, base);
        result.preferred = makePlan(Mode.PREFERRED, features, base);
        buildInternalPlans(result, base);
        AutonomousMasteringBrain.Plan album = AlbumConsistencyModel.makePlan(
                albumProfile, features, base);
        if (album != null) result.internalPlans.add(album);
        result.limitations.add("The Sonic Finish controller is track-trained analytical DSP/feature inference, not a pretrained neural waveform model.");
        result.limitations.add("Tonal corridors are broad safety priors; autonomous selection is an engineering inference rather than human artistic approval.");
        result.limitations.add("Stereo Event Repair, Low-End Intelligence and Stereo Vocal Focus are bounded inferences from the authoritative stereo mix; each requires a separate measured promotion gate.");
        result.limitations.add("Section-Aware Mastering, AI-Generation Artifact Repair and Transient Intelligence are additional bounded authoritative-stereo candidates; artifact labels describe signal signatures and do not classify source provenance.");
        result.limitations.add("Tier 2 stems are alignment-checked analytical control evidence only; no separated-stem waveform or processed stem delta enters that candidate.");
        result.limitations.add("Preference Memory and Album Consistency can rank only candidates that already passed hard technical safety gates.");
        result.limitations.add("Click/dropout repair is limited to sample-proven isolated impulses and short exact-zero holes; ambiguous discontinuities remain review-only.");
        result.limitations.add("Device translation remains response simulation unless a human performs physical playback checks.");
        progress.update("AI mastering brain: plans ready", 100);
        return result;
    }

    private static void buildInternalPlans(Result result, AnalysisResult base) {
        Plan transparent = new Plan();
        transparent.mode = Mode.CONSERVATIVE;
        transparent.strategy = "transparent_flat_gain";
        transparent.confidence = result.features.reliability;
        transparent.objectiveBefore = objective(result.features, base);
        transparent.predictedObjectiveAfter = transparent.objectiveBefore;
        transparent.bypassed.add("Global EQ bypassed by transparent strategy.");
        transparent.bypassed.add("Local dynamic EQ bypassed by transparent strategy.");
        transparent.bypassed.add("Stereo processing bypassed by transparent strategy.");
        transparent.bypassed.add("Bus compression bypassed by transparent strategy.");

        Plan conservative = result.conservative.copy("conservative_corrective");

        Plan tonal = result.preferred.copy("tonal_corrective");
        tonal.sideScale = 1.0;
        tonal.compressorEnabled = false;
        tonal.compressorMaximumReductionDb = 0.0;
        tonal.bypassed.add("Stereo and bus compression disabled for isolated tonal candidate measurement.");

        Plan dynamic = result.preferred.copy("dynamic_transient");
        dynamic.lowShelfDb = 0.0;
        dynamic.lowMidDb = 0.0;
        dynamic.presenceDb = 0.0;
        dynamic.highShelfDb = 0.0;
        dynamic.eventScale = 0.0;
        dynamic.bypassed.add("Global and local EQ disabled for isolated dynamics/phase candidate measurement.");

        Plan combined = result.preferred.copy("full_combined");

        Plan sonicFinish = result.preferred.copy("sonic_finish");
        sonicFinish.lowShelfDb = bounded(sonicFinish.lowShelfDb * 1.12, 2.10);
        sonicFinish.lowMidDb = bounded(sonicFinish.lowMidDb * 1.12, 2.10);
        sonicFinish.presenceDb = bounded(sonicFinish.presenceDb * 1.10, 2.00);
        sonicFinish.highShelfDb = bounded(sonicFinish.highShelfDb * 1.10, 1.90);
        sonicFinish.eventScale = Math.min(1.25, sonicFinish.eventScale * 1.15);
        if (sonicFinish.compressorEnabled) {
            sonicFinish.compressorRatio = Math.min(1.50, sonicFinish.compressorRatio * 1.08);
            sonicFinish.compressorMaximumReductionDb = Math.min(2.00,
                    sonicFinish.compressorMaximumReductionDb * 1.25);
            sonicFinish.compressorAttackMs = Math.max(18.0, sonicFinish.compressorAttackMs - 4.0);
            sonicFinish.compressorReleaseMs = Math.max(130.0, sonicFinish.compressorReleaseMs - 20.0);
        }
        sonicFinish.decisions.add("Sonic Finish challenger allowed to make a stronger measured mastering move than the transparent/control candidates.");
        result.internalPlans.add(transparent);
        result.internalPlans.add(conservative);
        result.internalPlans.add(tonal);
        result.internalPlans.add(dynamic);
        result.internalPlans.add(combined);
        result.internalPlans.add(sonicFinish);
    }

    static Features measureFeatures(AudioAnalyzer.StreamProvider provider,
                                    AnalysisResult base,
                                    AudioAnalyzer.Progress progress) throws IOException {
        return extract(provider, base, progress);
    }

    private static Features extract(AudioAnalyzer.StreamProvider provider,
                                    AnalysisResult base,
                                    AudioAnalyzer.Progress progress) throws IOException {
        ArrayList<Window> windows = new ArrayList<>();
        try (WavFile.Reader reader = WavFile.open(provider.open())) {
            int rate = reader.info.sampleRate;
            int windowFrames = Math.max(2048, (int) Math.round(rate * WINDOW_SECONDS));
            BandBank bands = new BandBank(rate);
            Accumulator accumulator = new Accumulator();
            float[][] samples = new float[2][BUFFER_FRAMES];
            long framesDone = 0;
            int read;
            while ((read = reader.readFrames(samples, BUFFER_FRAMES)) > 0) {
                for (int i = 0; i < read; i++) {
                    double left = finite(samples[0][i]);
                    double right = finite(samples[1][i]);
                    accumulator.add(left, right, bands);
                    framesDone++;
                    if (accumulator.frames == windowFrames) {
                        windows.add(accumulator.finish());
                        accumulator = new Accumulator();
                    }
                }
                int percent = (int) Math.min(94,
                        Math.round(94.0 * framesDone / Math.max(1L, reader.info.frames)));
                progress.update("AI mastering brain: learning whole-song features", percent);
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("AI mastering analysis cancelled");
                }
            }
            if (accumulator.frames >= Math.max(256, windowFrames / 4)) {
                windows.add(accumulator.finish());
            }
        }
        if (windows.size() < 8) {
            throw new IOException("The song is too short for reliable AI mastering feature learning");
        }

        double[] levels = values(windows, 0, null);
        double p20 = percentile(levels, 0.20);
        double p90 = percentile(levels, 0.90);
        double activeFloor = Math.max(p20, p90 - 34.0);
        ArrayList<Integer> active = new ArrayList<>();
        for (int i = 0; i < windows.size(); i++) {
            if (windows.get(i).values[0] >= activeFloor) active.add(i);
        }
        if (active.size() < Math.min(8, windows.size())) {
            active.clear();
            for (int i = 0; i < windows.size(); i++) {
                if (windows.get(i).values[0] >= p20) active.add(i);
            }
        }

        Features out = new Features();
        out.windows = windows.size();
        out.activeWindows = active.size();
        out.activeFloorDbfs = activeFloor;
        out.lowShareDb = percentile(values(windows, 1, active), 0.50);
        out.lowMidShareDb = percentile(values(windows, 2, active), 0.50);
        out.presenceShareDb = percentile(values(windows, 3, active), 0.50);
        out.harshShareDb = percentile(values(windows, 4, active), 0.50);
        out.airShareDb = percentile(values(windows, 5, active), 0.50);
        out.sideShareDb = percentile(values(windows, 6, active), 0.50);
        out.medianWindowCrestDb = percentile(values(windows, 7, active), 0.50);
        out.highWindowCrestDb = percentile(values(windows, 7, active), 0.90);
        out.reliability = clamp(active.size() / 48.0, 0.35, 1.0);
        return out;
    }

    private static Plan makePlan(Mode mode, Features f, AnalysisResult base) {
        Plan plan = new Plan();
        plan.mode = mode;
        plan.strategy = mode == Mode.CONSERVATIVE
                ? "conservative_corrective" : "full_combined";
        double strength = mode == Mode.CONSERVATIVE ? 0.58 : 1.05;
        double maximumEq = mode == Mode.CONSERVATIVE ? 0.85 : 1.85;

        double low = corridor(f.lowShareDb, -12.0, -4.2, 0.16, 0.26);
        double lowMid = 0.0;
        if (f.lowMidShareDb > -6.0 && f.presenceShareDb < -4.0) {
            lowMid = -(f.lowMidShareDb + 6.0) * 0.30;
        } else if (f.lowMidShareDb > -4.8) {
            lowMid = -(f.lowMidShareDb + 4.8) * 0.20;
        }
        double presence = corridor(f.presenceShareDb, -10.5, -3.0, 0.18, 0.20);
        double high = 0.0;
        if (f.harshShareDb > -8.0) high -= (f.harshShareDb + 8.0) * 0.20;
        if (f.airShareDb > -11.0) high -= (f.airShareDb + 11.0) * 0.13;
        if (f.harshShareDb < -18.0 && f.airShareDb < -21.0) {
            high += Math.min((-18.0 - f.harshShareDb) * 0.08,
                    (-21.0 - f.airShareDb) * 0.10);
        }

        plan.lowShelfDb = bounded(low * strength, maximumEq);
        plan.lowMidDb = bounded(lowMid * strength, maximumEq);
        plan.presenceDb = bounded(presence * strength, maximumEq);
        plan.highShelfDb = bounded(high * strength, maximumEq);

        if (base.stereoCorrelation < 0.05 || base.lowBandCorrelation < 0.05) {
            plan.sideScale = mode == Mode.CONSERVATIVE ? 0.96 : 0.90;
            plan.decisions.add("Side level reduced because measured stereo or low-band correlation is near the phase-risk boundary.");
        } else if (base.stereoCorrelation < 0.22 || base.lowBandCorrelation < 0.18) {
            plan.sideScale = mode == Mode.CONSERVATIVE ? 0.985 : 0.95;
            plan.decisions.add("Small side reduction selected for measured mono-compatibility risk.");
        } else {
            plan.bypassed.add("Stereo processing bypassed: measured correlation does not justify changing width.");
        }

        int safeEvents = base.aiDiagnostics == null ? 0
                : base.aiDiagnostics.autoCorrectionCount();
        plan.eventScale = safeEvents == 0 ? 0.0
                : mode == Mode.CONSERVATIVE ? 0.62 : 1.0;
        if (safeEvents > 0) {
            plan.decisions.add(safeEvents + " confidence-gated local correction event(s) retained at mode-scaled depth.");
        } else {
            plan.bypassed.add("Local dynamic EQ bypassed: no event passed the strict correction gate.");
        }

        double flatTargetPeak = base.truePeakEstimateDbtp + (-14.0 - base.integratedLufs);
        double peakControlNeed = Math.max(0.0, flatTargetPeak + 1.10);
        boolean dynamicEvidence = base.crestFactorDb > 8.6
                && base.lraLu > 3.6
                && (peakControlNeed > 0.20 || base.integratedLufs > -15.5);
        boolean priorMasterBlocked = base.sourceGuard != null
                && base.sourceGuard.secondPassGuard;
        if (mode == Mode.PREFERRED && dynamicEvidence && !priorMasterBlocked) {
            plan.compressorEnabled = true;
            plan.compressorThresholdDbfs = clamp(
                    base.rmsDbfs + 0.56 * base.crestFactorDb, -16.0, -4.5);
            double movementNeed = Math.max(0.0, (base.crestFactorDb - 8.6) * 0.08)
                    + Math.max(0.0, (base.lraLu - 3.6) * 0.04);
            plan.compressorRatio = Math.min(1.45,
                    1.16 + peakControlNeed * 0.045 + movementNeed);
            plan.compressorAttackMs = 24.0;
            plan.compressorReleaseMs = 165.0;
            plan.compressorMaximumReductionDb = Math.min(1.65,
                    Math.max(0.50, 0.55 + peakControlNeed * 0.45
                            + Math.max(0.0, base.crestFactorDb - 10.0) * 0.09));
            plan.decisions.add(String.format(Locale.US,
                    "Stereo-linked mastering compression selected from crest/LRA/peak evidence: predicted flat-gain peak pressure %.2f dB.",
                    peakControlNeed));
        } else {
            plan.bypassed.add(priorMasterBlocked
                    ? "Bus compression bypassed: high-confidence prior Dennis master guard is active."
                    : "Bus compression bypassed: current crest/LRA/peak evidence does not clear the mode gate.");
        }

        if (Math.abs(plan.lowShelfDb) < 0.05) plan.lowShelfDb = 0.0;
        if (Math.abs(plan.lowMidDb) < 0.05) plan.lowMidDb = 0.0;
        if (Math.abs(plan.presenceDb) < 0.05) plan.presenceDb = 0.0;
        if (Math.abs(plan.highShelfDb) < 0.05) plan.highShelfDb = 0.0;
        if (plan.lowShelfDb == 0.0 && plan.lowMidDb == 0.0
                && plan.presenceDb == 0.0 && plan.highShelfDb == 0.0) {
            plan.bypassed.add("Global EQ bypassed: learned tonal features remain inside the broad safety corridors.");
        } else {
            plan.decisions.add("Minimum-phase global EQ selected from learned full-song band distributions.");
        }

        plan.objectiveBefore = objective(f, base);
        double expectedImprovement = 4.0 * (Math.abs(plan.lowShelfDb)
                + Math.abs(plan.lowMidDb) + Math.abs(plan.presenceDb)
                + Math.abs(plan.highShelfDb))
                + 5.0 * Math.max(0.0, 1.0 - plan.sideScale)
                + (plan.compressorEnabled ? 2.0 : 0.0)
                + Math.min(3.0, safeEvents * plan.eventScale * 0.15);
        plan.predictedObjectiveAfter = clamp(plan.objectiveBefore + expectedImprovement,
                0.0, 100.0);
        double evidence = Math.min(1.0,
                0.35 + 0.12 * expectedImprovement + 0.15 * (safeEvents > 0 ? 1.0 : 0.0));
        plan.confidence = clamp(f.reliability * evidence, 0.20, 0.97);
        return plan;
    }

    static double objective(Features f, AnalysisResult base) {
        double penalty = distanceOutside(f.lowShareDb, -12.0, -4.2) * 1.3
                + distanceOutside(f.lowMidShareDb, -13.0, -4.8) * 1.2
                + distanceOutside(f.presenceShareDb, -10.5, -3.0)
                + distanceOutside(f.harshShareDb, -18.0, -8.0) * 0.9
                + distanceOutside(f.airShareDb, -24.0, -11.0) * 0.7;
        if (base != null && base.stereoCorrelation < 0.1) {
            penalty += (0.1 - base.stereoCorrelation) * 20.0;
        }
        if (base != null && base.lowBandCorrelation < 0.1) {
            penalty += (0.1 - base.lowBandCorrelation) * 24.0;
        }
        return clamp(100.0 - penalty, 0.0, 100.0);
    }

    private static double corridor(double value, double lower, double upper,
                                   double upwardStrength, double downwardStrength) {
        if (value < lower) return (lower - value) * upwardStrength;
        if (value > upper) return -(value - upper) * downwardStrength;
        return 0.0;
    }

    private static double distanceOutside(double value, double lower, double upper) {
        if (value < lower) return lower - value;
        if (value > upper) return value - upper;
        return 0.0;
    }

    private static double bounded(double value, double maximumMagnitude) {
        return clamp(value, -maximumMagnitude, maximumMagnitude);
    }

    private static double[] values(List<Window> windows, int feature,
                                   List<Integer> indices) {
        double[] out = new double[indices == null ? windows.size() : indices.size()];
        if (indices == null) {
            for (int i = 0; i < windows.size(); i++) out[i] = windows.get(i).values[feature];
        } else {
            for (int i = 0; i < indices.size(); i++) {
                out[i] = windows.get(indices.get(i)).values[feature];
            }
        }
        return out;
    }

    private static double percentile(double[] input, double fraction) {
        if (input.length == 0) return 0.0;
        double[] values = input.clone();
        Arrays.sort(values);
        double position = clamp(fraction, 0.0, 1.0) * (values.length - 1);
        int lower = (int) Math.floor(position);
        int upper = Math.min(values.length - 1, lower + 1);
        double blend = position - lower;
        return values[lower] * (1.0 - blend) + values[upper] * blend;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private static double dbEnergy(double energy, double reference) {
        return 10.0 * Math.log10((energy + 1e-30) / (reference + 1e-30));
    }

    private static double dbAmplitude(double value) {
        return 20.0 * Math.log10(Math.max(1e-15, value));
    }

    private static final class Window {
        final double[] values;

        Window(double[] values) {
            this.values = values;
        }
    }

    private static final class BandBank {
        final Biquad lowL, lowR, below500L, below500R;
        final Biquad below4000L, below4000R, below8000L, below8000R;

        BandBank(double rate) {
            lowL = Biquad.lowPass(rate, 160.0);
            lowR = Biquad.lowPass(rate, 160.0);
            below500L = Biquad.lowPass(rate, 500.0);
            below500R = Biquad.lowPass(rate, 500.0);
            below4000L = Biquad.lowPass(rate, Math.min(4000.0, rate * 0.16));
            below4000R = Biquad.lowPass(rate, Math.min(4000.0, rate * 0.16));
            below8000L = Biquad.lowPass(rate, Math.min(8000.0, rate * 0.23));
            below8000R = Biquad.lowPass(rate, Math.min(8000.0, rate * 0.23));
        }
    }

    private static final class Accumulator {
        long frames;
        double total;
        double low;
        double lowMid;
        double presence;
        double harsh;
        double air;
        double mid;
        double side;
        double peak;

        void add(double left, double right, BandBank b) {
            double lowLeft = b.lowL.process(left);
            double lowRight = b.lowR.process(right);
            double below500Left = b.below500L.process(left);
            double below500Right = b.below500R.process(right);
            double below4000Left = b.below4000L.process(left);
            double below4000Right = b.below4000R.process(right);
            double below8000Left = b.below8000L.process(left);
            double below8000Right = b.below8000R.process(right);
            double lowMidLeft = below500Left - lowLeft;
            double lowMidRight = below500Right - lowRight;
            double presenceLeft = below4000Left - below500Left;
            double presenceRight = below4000Right - below500Right;
            double harshLeft = below8000Left - below4000Left;
            double harshRight = below8000Right - below4000Right;
            double airLeft = left - below8000Left;
            double airRight = right - below8000Right;
            double middle = 0.5 * (left + right);
            double sideValue = 0.5 * (left - right);
            frames++;
            total += 0.5 * (left * left + right * right);
            low += 0.5 * (lowLeft * lowLeft + lowRight * lowRight);
            lowMid += 0.5 * (lowMidLeft * lowMidLeft + lowMidRight * lowMidRight);
            presence += 0.5 * (presenceLeft * presenceLeft + presenceRight * presenceRight);
            harsh += 0.5 * (harshLeft * harshLeft + harshRight * harshRight);
            air += 0.5 * (airLeft * airLeft + airRight * airRight);
            mid += middle * middle;
            side += sideValue * sideValue;
            peak = Math.max(peak, Math.max(Math.abs(left), Math.abs(right)));
        }

        Window finish() {
            double rms = Math.sqrt(total / Math.max(1L, frames));
            double[] values = new double[8];
            values[0] = dbAmplitude(rms);
            values[1] = dbEnergy(low, total);
            values[2] = dbEnergy(lowMid, total);
            values[3] = dbEnergy(presence, total);
            values[4] = dbEnergy(harsh, total);
            values[5] = dbEnergy(air, total);
            values[6] = dbEnergy(side, mid + side);
            values[7] = dbAmplitude(peak / Math.max(1e-15, rms));
            return new Window(values);
        }
    }
}
