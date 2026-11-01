package com.dennis.aimasteringlab;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;

/**
 * Small offline, track-trained diagnostic model. It uses robust feature
 * normalisation and deterministic k-means clustering rather than a generic
 * genre preset or a cloud model. Findings are advisory; only three local,
 * attenuation-only event types can pass the separate auto-correction gate.
 */
final class AdaptiveDiagnosticModel {
    enum Type {
        HIGH_FREQUENCY_EMPHASIS,
        UPPER_MID_HARSHNESS,
        LOW_MID_MASKING,
        LOW_END_DOMINANCE,
        STEREO_PHASE_RISK,
        LOW_FREQUENCY_PHASE_RISK,
        TRANSIENT_DISCONTINUITY,
        POSSIBLE_DROPOUT
    }

    static final class Event {
        Type type;
        double startSeconds;
        double endSeconds;
        double confidence;
        double correctionDb;
        boolean autoCorrection;
        String evidence;

        double durationSeconds() {
            return Math.max(0.0, endSeconds - startSeconds);
        }
    }

    static final class Result {
        String model = "DAML v0.5 robust clustering / per-track anomaly inference";
        String method = "offline unsupervised model trained on this track; 125 ms feature windows; deterministic robust k-means";
        int windows;
        int activeWindows;
        int clusters;
        double activeFloorDbfs;
        double windowSeconds;
        boolean stemTrustRisk;
        String stemTrustAssessment;
        final List<Event> events = new ArrayList<>();
        final List<String> safetyNotes = new ArrayList<>();

        boolean hasAutoCorrections() {
            for (Event event : events) if (event.autoCorrection) return true;
            return false;
        }

        int autoCorrectionCount() {
            int count = 0;
            for (Event event : events) if (event.autoCorrection) count++;
            return count;
        }

        double maximumCorrectionDb() {
            double maximum = 0.0;
            for (Event event : events) {
                if (event.autoCorrection) maximum = Math.max(maximum, -event.correctionDb);
            }
            return maximum;
        }

        String compactSummary() {
            int review = events.size() - autoCorrectionCount();
            return String.format(Locale.US,
                    "%d windows • %d learned states • %d local findings • %d safe correction events • max cut %.2f dB%s",
                    windows, clusters, events.size(), autoCorrectionCount(), maximumCorrectionDb(),
                    review > 0 ? " • " + review + " review-only" : "");
        }
    }

    private static final int BUFFER_FRAMES = 4096;
    private static final int FEATURE_COUNT = 13;
    private static final int[] CLUSTER_FEATURES = {0, 1, 2, 3, 4, 5, 6, 9, 11};
    private static final double REPORT_CONFIDENCE = 0.72;
    private static final double CORRECTION_CONFIDENCE = 0.82;
    private static final double MAX_CORRECTED_COVERAGE = 0.18;
    private static final int MAX_CORRECTION_EVENTS_PER_TYPE = 24;
    private static final int MAX_REVIEW_EVENTS_PER_TYPE = 12;

    private AdaptiveDiagnosticModel() {}

    static Result unavailable(String reason, AnalysisResult base) {
        Result result = new Result();
        result.method = "AI diagnostic unavailable; deterministic technical analysis remains valid";
        result.safetyNotes.add(reason == null ? "Unknown AI diagnostic error" : reason);
        addStemTrust(result, base);
        return result;
    }

    static Result analyze(AudioAnalyzer.StreamProvider provider, AnalysisResult base,
                          AudioAnalyzer.Progress progress) throws IOException {
        if (base.wav.channels != 2) {
            Result unsupported = new Result();
            unsupported.safetyNotes.add("Stereo AI diagnostics require a two-channel authoritative mix.");
            return unsupported;
        }
        progress.update("AI diagnostic: extracting track features", 0);
        List<Window> frames = extract(provider, base, progress);
        if (frames.size() < 16) {
            Result shortTrack = new Result();
            shortTrack.windows = frames.size();
            shortTrack.windowSeconds = frames.isEmpty() ? 0.125 : frames.get(0).duration;
            shortTrack.safetyNotes.add("Track is too short for reliable per-track clustering; no automatic correction allowed.");
            addStemTrust(shortTrack, base);
            return shortTrack;
        }

        Result result = infer(frames, base, progress);
        addStemTrust(result, base);
        progress.update("AI diagnostic complete", 100);
        return result;
    }

    private static List<Window> extract(AudioAnalyzer.StreamProvider provider,
                                        AnalysisResult base,
                                        AudioAnalyzer.Progress progress) throws IOException {
        ArrayList<Window> out = new ArrayList<>();
        try (WavFile.Reader reader = WavFile.open(provider.open())) {
            int rate = reader.info.sampleRate;
            int targetFrames = Math.max(1024, (int) Math.round(rate * 0.125));
            Biquad mono160 = Biquad.lowPass(rate, 160.0);
            Biquad mono500 = Biquad.lowPass(rate, 500.0);
            Biquad mono4000 = Biquad.lowPass(rate, 4000.0);
            Biquad mono8000 = Biquad.lowPass(rate, Math.min(8000.0, rate * 0.20));
            Biquad left160 = Biquad.lowPass(rate, 160.0);
            Biquad right160 = Biquad.lowPass(rate, 160.0);
            Accumulator accumulator = new Accumulator();
            float[][] samples = new float[2][BUFFER_FRAMES];
            long absoluteFrame = 0;
            double previousMono = 0.0;
            boolean havePrevious = false;
            int read;
            while ((read = reader.readFrames(samples, BUFFER_FRAMES)) > 0) {
                for (int i = 0; i < read; i++) {
                    double left = finite(samples[0][i]);
                    double right = finite(samples[1][i]);
                    double mono = 0.5 * (left + right);
                    double side = 0.5 * (left - right);
                    double low = mono160.process(mono);
                    double below500 = mono500.process(mono);
                    double below4000 = mono4000.process(mono);
                    double below8000 = mono8000.process(mono);
                    double lowMid = below500 - low;
                    double presence = below4000 - below500;
                    double harsh = below8000 - below4000;
                    double air = mono - below8000;
                    double lowLeft = left160.process(left);
                    double lowRight = right160.process(right);
                    double delta = havePrevious ? mono - previousMono : 0.0;
                    accumulator.add(left, right, mono, side, low, lowMid,
                            presence, harsh, air, lowLeft, lowRight, delta);
                    previousMono = mono;
                    havePrevious = true;
                    absoluteFrame++;
                    if (accumulator.count == targetFrames) {
                        out.add(accumulator.finish(
                                (absoluteFrame - accumulator.count) / (double) rate,
                                accumulator.count / (double) rate));
                        accumulator = new Accumulator();
                    }
                }
                int percent = (int) Math.min(92,
                        Math.round(92.0 * absoluteFrame / Math.max(1L, reader.info.frames)));
                progress.update("AI diagnostic: extracting track features", percent);
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("AI diagnostic cancelled");
                }
            }
            if (accumulator.count >= Math.max(128, targetFrames / 4)) {
                out.add(accumulator.finish(
                        (absoluteFrame - accumulator.count) / (double) rate,
                        accumulator.count / (double) rate));
            }
        }
        return out;
    }

    private static Result infer(List<Window> frames, AnalysisResult base,
                                AudioAnalyzer.Progress progress) {
        Result result = new Result();
        result.windows = frames.size();
        result.windowSeconds = medianDuration(frames);

        double[] levels = values(frames, 0, null);
        double p10 = percentile(levels, 0.10);
        double p90 = percentile(levels, 0.90);
        double activeFloor = twoStateFloor(levels, p10, p90);
        result.activeFloorDbfs = activeFloor;
        ArrayList<Integer> active = new ArrayList<>();
        for (int i = 0; i < frames.size(); i++) {
            if (frames.get(i).feature[0] >= activeFloor) active.add(i);
        }
        if (active.size() < 12) {
            active.clear();
            for (int i = 0; i < frames.size(); i++) {
                if (frames.get(i).feature[0] >= p10) active.add(i);
            }
        }
        result.activeWindows = active.size();
        int k = active.size() >= 96 ? 4 : active.size() >= 48 ? 3 : 2;
        k = Math.max(1, Math.min(k, active.size()));
        result.clusters = k;

        double[] globalMedian = new double[FEATURE_COUNT];
        double[] globalScale = new double[FEATURE_COUNT];
        for (int f = 0; f < FEATURE_COUNT; f++) {
            double[] data = values(frames, f, active);
            globalMedian[f] = percentile(data, 0.50);
            globalScale[f] = robustScale(data, f);
        }
        double[][] vectors = new double[active.size()][CLUSTER_FEATURES.length];
        for (int i = 0; i < active.size(); i++) {
            Window window = frames.get(active.get(i));
            for (int d = 0; d < CLUSTER_FEATURES.length; d++) {
                int f = CLUSTER_FEATURES[d];
                vectors[i][d] = clamp((window.feature[f] - globalMedian[f])
                        / globalScale[f], -7.0, 7.0);
            }
        }
        int[] assignments = kMeans(vectors, k);
        for (int i = 0; i < active.size(); i++) frames.get(active.get(i)).cluster = assignments[i];
        assignInactive(frames, active);

        double[][] medians = new double[k][FEATURE_COUNT];
        double[][] scales = new double[k][FEATURE_COUNT];
        for (int cluster = 0; cluster < k; cluster++) {
            for (int f = 0; f < FEATURE_COUNT; f++) {
                double[] data = valuesForCluster(frames, active, cluster, f);
                if (data.length < 6) {
                    medians[cluster][f] = globalMedian[f];
                    scales[cluster][f] = globalScale[f];
                } else {
                    medians[cluster][f] = percentile(data, 0.50);
                    scales[cluster][f] = robustScale(data, f);
                }
            }
        }

        progress.update("AI diagnostic: inferring local issues", 95);
        ArrayList<Event> candidates = new ArrayList<>();
        for (int i = 0; i < frames.size(); i++) {
            Window w = frames.get(i);
            if (w.feature[0] < activeFloor) continue;
            int cluster = Math.max(0, Math.min(k - 1, w.cluster));
            double[] z = new double[FEATURE_COUNT];
            double[] globalZ = new double[FEATURE_COUNT];
            for (int f = 0; f < FEATURE_COUNT; f++) {
                z[f] = (w.feature[f] - medians[cluster][f]) / scales[cluster][f];
                globalZ[f] = (w.feature[f] - globalMedian[f]) / globalScale[f];
            }

            addCandidate(candidates, w, Type.HIGH_FREQUENCY_EMPHASIS,
                    confidence(0.60 * strongestPositive(z[5], globalZ[5])
                            + 0.40 * strongestPositive(z[4], globalZ[4])),
                    "air and upper-band energy are locally high for the learned track state");
            addCandidate(candidates, w, Type.UPPER_MID_HARSHNESS,
                    confidence(0.68 * strongestPositive(z[4], globalZ[4])
                            + 0.32 * strongestPositive(z[3], globalZ[3])),
                    "upper-mid energy is locally high relative to the learned section state");
            addCandidate(candidates, w, Type.LOW_MID_MASKING,
                    confidence(0.72 * strongestPositive(z[2], globalZ[2])
                            + 0.28 * strongestPositive(-z[3], -globalZ[3])),
                    "low-mid share is high while presence is locally reduced");
            addCandidate(candidates, w, Type.LOW_END_DOMINANCE,
                    confidence(strongestPositive(z[1], globalZ[1])),
                    "sub/low-band share is locally high for this track state");

            double phaseAdaptive = confidence(0.55 * positive(z[6])
                    + 0.45 * positive(-z[7]));
            double phasePhysical = clamp((0.05 - w.feature[7]) / 0.55, 0.0, 1.0);
            addCandidate(candidates, w, Type.STEREO_PHASE_RISK,
                    Math.max(phaseAdaptive, 0.50 + 0.48 * phasePhysical),
                    "side energy and/or stereo correlation indicate a local mono-fold risk");

            double lowPhaseAdaptive = confidence(positive(-z[8]));
            double lowEnergyWeight = clamp((z[1] + 1.0) / 3.0, 0.0, 1.0);
            double lowPhasePhysical = clamp((0.10 - w.feature[8]) / 0.55, 0.0, 1.0)
                    * lowEnergyWeight;
            addCandidate(candidates, w, Type.LOW_FREQUENCY_PHASE_RISK,
                    Math.max(lowPhaseAdaptive, 0.50 + 0.48 * lowPhasePhysical),
                    "below-160 Hz correlation is locally weak");

            addCandidate(candidates, w, Type.TRANSIENT_DISCONTINUITY,
                    confidence(0.62 * positive(z[10]) + 0.38 * positive(z[11])),
                    "sample-jump and derivative energy are anomalous for the learned state");

            if (i > 0 && i + 1 < frames.size()) {
                double neighbour = 0.5 * (frames.get(i - 1).feature[0]
                        + frames.get(i + 1).feature[0]);
                double gapZ = (neighbour - w.feature[0]) / Math.max(1.0, globalScale[0]);
                double dropout = confidence(0.75 * positive(gapZ)
                        + 0.25 * positive(z[12]));
                addCandidate(candidates, w, Type.POSSIBLE_DROPOUT, dropout,
                        "a short level collapse or exact-zero concentration differs from adjacent windows");
            }
        }

        result.events.addAll(merge(candidates, result.windowSeconds));
        applyCorrectionGate(result, base.wav.durationSeconds);
        pruneReviewEvents(result);
        if (!result.hasAutoCorrections()) {
            result.safetyNotes.add("No local finding passed every correction gate; diagnostic-only result retained.");
        }
        result.safetyNotes.add("AI findings are confidence-ranked perceptual inferences, not proof of an audible defect.");
        result.safetyNotes.add("Automatic mode never boosts, widens, denoises, regenerates or changes pitch/timing.");
        return result;
    }

    private static void addCandidate(List<Event> out, Window window, Type type,
                                     double confidence, String evidence) {
        if (!Double.isFinite(confidence) || confidence < reportThreshold(type)) return;
        Event event = new Event();
        event.type = type;
        event.startSeconds = window.start;
        event.endSeconds = window.start + window.duration;
        event.confidence = clamp(confidence, 0.0, 0.999);
        event.evidence = evidence;
        out.add(event);
    }

    private static double reportThreshold(Type type) {
        if (type == Type.STEREO_PHASE_RISK) return 0.88;
        if (type == Type.LOW_FREQUENCY_PHASE_RISK) return 0.90;
        if (type == Type.TRANSIENT_DISCONTINUITY) return 0.85;
        if (type == Type.POSSIBLE_DROPOUT) return 0.88;
        if (type == Type.LOW_END_DOMINANCE) return 0.80;
        return REPORT_CONFIDENCE;
    }

    private static List<Event> merge(List<Event> input, double windowSeconds) {
        Collections.sort(input, (a, b) -> {
            int type = a.type.compareTo(b.type);
            return type != 0 ? type : Double.compare(a.startSeconds, b.startSeconds);
        });
        ArrayList<Event> merged = new ArrayList<>();
        for (Event event : input) {
            Event previous = merged.isEmpty() ? null : merged.get(merged.size() - 1);
            if (previous != null && previous.type == event.type
                    && event.startSeconds <= previous.endSeconds + windowSeconds * 1.05) {
                previous.endSeconds = Math.max(previous.endSeconds, event.endSeconds);
                if (event.confidence > previous.confidence) {
                    previous.confidence = event.confidence;
                    previous.evidence = event.evidence;
                }
            } else {
                merged.add(event);
            }
        }
        Collections.sort(merged, Comparator.comparingDouble(a -> a.startSeconds));
        return merged;
    }

    private static void applyCorrectionGate(Result result, double trackDuration) {
        EnumMap<Type, ArrayList<Event>> byType = new EnumMap<>(Type.class);
        double protectedEndingStart = Math.max(0.0,
                trackDuration - Math.max(8.0, trackDuration * 0.04));
        int edgeProtected = 0;
        for (Event event : result.events) {
            if (!isCorrectable(event.type) || event.confidence < CORRECTION_CONFIDENCE) continue;
            if (event.startSeconds < 2.0 || event.endSeconds > protectedEndingStart) {
                edgeProtected++;
                continue;
            }
            byType.computeIfAbsent(event.type, ignored -> new ArrayList<>()).add(event);
        }
        if (edgeProtected > 0) {
            result.safetyNotes.add(edgeProtected
                    + " otherwise-eligible event(s) were kept review-only to protect the intro or intended ending/reverb tail.");
        }
        for (Type type : byType.keySet()) {
            ArrayList<Event> events = byType.get(type);
            double coverage = 0.0;
            for (Event event : events) coverage += event.durationSeconds();
            if (coverage > Math.max(1.0, trackDuration * MAX_CORRECTED_COVERAGE)) {
                result.safetyNotes.add(label(type)
                        + " appears too widespread for safe local automation; retained for review only.");
                continue;
            }
            events.sort((a, b) -> Double.compare(b.confidence, a.confidence));
            int enabled = 0;
            for (Event event : events) {
                if (enabled >= MAX_CORRECTION_EVENTS_PER_TYPE) break;
                event.autoCorrection = true;
                event.correctionDb = correctionDb(event.type, event.confidence);
                enabled++;
            }
        }
    }

    private static void pruneReviewEvents(Result result) {
        EnumMap<Type, ArrayList<Event>> byType = new EnumMap<>(Type.class);
        for (Event event : result.events) {
            byType.computeIfAbsent(event.type, ignored -> new ArrayList<>()).add(event);
        }
        ArrayList<Event> retained = new ArrayList<>();
        for (Type type : byType.keySet()) {
            ArrayList<Event> events = byType.get(type);
            for (Event event : events) if (event.autoCorrection) retained.add(event);
            events.sort((a, b) -> Double.compare(b.confidence, a.confidence));
            int reviews = 0;
            for (Event event : events) {
                if (event.autoCorrection) continue;
                if (reviews++ >= MAX_REVIEW_EVENTS_PER_TYPE) break;
                retained.add(event);
            }
        }
        retained.sort(Comparator.comparingDouble(event -> event.startSeconds));
        result.events.clear();
        result.events.addAll(retained);
    }

    private static boolean isCorrectable(Type type) {
        return type == Type.HIGH_FREQUENCY_EMPHASIS
                || type == Type.UPPER_MID_HARSHNESS
                || type == Type.LOW_MID_MASKING;
    }

    private static double correctionDb(Type type, double confidence) {
        double strength = clamp((confidence - CORRECTION_CONFIDENCE)
                / (1.0 - CORRECTION_CONFIDENCE), 0.0, 1.0);
        if (type == Type.HIGH_FREQUENCY_EMPHASIS) return -(0.25 + 0.65 * strength);
        if (type == Type.UPPER_MID_HARSHNESS) return -(0.20 + 0.55 * strength);
        return -(0.15 + 0.40 * strength);
    }

    static String label(Type type) {
        switch (type) {
            case HIGH_FREQUENCY_EMPHASIS: return "HF emphasis / possible sibilance or fizz";
            case UPPER_MID_HARSHNESS: return "upper-mid harshness";
            case LOW_MID_MASKING: return "low-mid masking";
            case LOW_END_DOMINANCE: return "low-end dominance";
            case STEREO_PHASE_RISK: return "stereo phase risk";
            case LOW_FREQUENCY_PHASE_RISK: return "below-160 Hz phase risk";
            case TRANSIENT_DISCONTINUITY: return "transient/click discontinuity candidate";
            case POSSIBLE_DROPOUT: return "possible short dropout";
            default: return type.name().toLowerCase(Locale.US);
        }
    }

    private static void addStemTrust(Result result, AnalysisResult base) {
        if (base.stemTrust != null) {
            result.stemTrustRisk = base.stemTrust.tier
                    != StemTrustClassifier.Tier.TIER_1_AUDIO_DELTA;
            result.stemTrustAssessment = base.stemTrust.status;
            return;
        }
        StemReconstruction.Result reconstruction = base.stemReconstruction;
        if (reconstruction == null) {
            result.stemTrustAssessment = "No reconstruction evidence available; authoritative stereo mix remains the only render source.";
            return;
        }
        result.stemTrustRisk = !reconstruction.rawStemSumAllowed
                || reconstruction.likelySeparationDerived;
        result.stemTrustAssessment = result.stemTrustRisk
                ? "Stem reconstruction is not trusted for raw-sum replacement; AI correction remains on the authoritative stereo mix only."
                : "Stem reconstruction passed the existing trust gate; this test build still leaves the raw stem sum unused.";
    }

    private static double confidence(double combinedZ) {
        return 1.0 / (1.0 + Math.exp(-(combinedZ - 2.20) * 1.15));
    }

    private static double positive(double value) {
        return Math.max(0.0, value);
    }

    private static double strongestPositive(double local, double global) {
        return Math.max(positive(local), positive(global));
    }

    private static int[] kMeans(double[][] vectors, int k) {
        int n = vectors.length;
        int dimensions = vectors[0].length;
        double[][] centers = new double[k][dimensions];
        int first = n / 2;
        centers[0] = vectors[first].clone();
        for (int c = 1; c < k; c++) {
            int best = 0;
            double bestDistance = -1.0;
            for (int i = 0; i < n; i++) {
                double nearest = Double.POSITIVE_INFINITY;
                for (int existing = 0; existing < c; existing++) {
                    nearest = Math.min(nearest, distance(vectors[i], centers[existing]));
                }
                if (nearest > bestDistance) {
                    bestDistance = nearest;
                    best = i;
                }
            }
            centers[c] = vectors[best].clone();
        }
        int[] assignment = new int[n];
        Arrays.fill(assignment, -1);
        for (int iteration = 0; iteration < 24; iteration++) {
            boolean changed = false;
            for (int i = 0; i < n; i++) {
                int nearest = nearest(vectors[i], centers);
                if (assignment[i] != nearest) {
                    assignment[i] = nearest;
                    changed = true;
                }
            }
            double[][] sums = new double[k][dimensions];
            int[] counts = new int[k];
            for (int i = 0; i < n; i++) {
                int cluster = assignment[i];
                counts[cluster]++;
                for (int d = 0; d < dimensions; d++) sums[cluster][d] += vectors[i][d];
            }
            for (int c = 0; c < k; c++) {
                if (counts[c] == 0) continue;
                for (int d = 0; d < dimensions; d++) centers[c][d] = sums[c][d] / counts[c];
            }
            if (!changed) break;
        }
        return assignment;
    }

    private static int nearest(double[] vector, double[][] centers) {
        int best = 0;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int c = 0; c < centers.length; c++) {
            double candidate = distance(vector, centers[c]);
            if (candidate < bestDistance) {
                bestDistance = candidate;
                best = c;
            }
        }
        return best;
    }

    private static double distance(double[] a, double[] b) {
        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            double delta = a[i] - b[i];
            sum += delta * delta;
        }
        return sum;
    }

    private static void assignInactive(List<Window> frames, List<Integer> active) {
        int previousCluster = active.isEmpty() ? 0 : frames.get(active.get(0)).cluster;
        int cursor = 0;
        for (int i = 0; i < frames.size(); i++) {
            if (cursor < active.size() && active.get(cursor) == i) {
                previousCluster = frames.get(i).cluster;
                cursor++;
            } else {
                frames.get(i).cluster = previousCluster;
            }
        }
    }

    private static double[] values(List<Window> frames, int feature, List<Integer> indices) {
        double[] out = new double[indices == null ? frames.size() : indices.size()];
        if (indices == null) {
            for (int i = 0; i < frames.size(); i++) out[i] = frames.get(i).feature[feature];
        } else {
            for (int i = 0; i < indices.size(); i++) out[i] = frames.get(indices.get(i)).feature[feature];
        }
        return out;
    }

    private static double[] valuesForCluster(List<Window> frames, List<Integer> active,
                                             int cluster, int feature) {
        int count = 0;
        for (int index : active) if (frames.get(index).cluster == cluster) count++;
        double[] out = new double[count];
        int position = 0;
        for (int index : active) {
            Window window = frames.get(index);
            if (window.cluster == cluster) out[position++] = window.feature[feature];
        }
        return out;
    }

    private static double twoStateFloor(double[] input, double p10, double p90) {
        double quiet = p10;
        double active = p90;
        for (int iteration = 0; iteration < 24; iteration++) {
            double quietSum = 0.0, activeSum = 0.0;
            int quietCount = 0, activeCount = 0;
            double boundary = 0.5 * (quiet + active);
            for (double value : input) {
                if (value < boundary) {
                    quietSum += value;
                    quietCount++;
                } else {
                    activeSum += value;
                    activeCount++;
                }
            }
            if (quietCount > 0) quiet = quietSum / quietCount;
            if (activeCount > 0) active = activeSum / activeCount;
        }
        double learned = 0.5 * (quiet + active);
        return clamp(learned, p90 - 55.0, p90 - 26.0);
    }

    private static double robustScale(double[] values, int feature) {
        if (values.length == 0) return minimumScale(feature);
        double median = percentile(values, 0.50);
        double[] deviations = new double[values.length];
        for (int i = 0; i < values.length; i++) deviations[i] = Math.abs(values[i] - median);
        return Math.max(minimumScale(feature), 1.4826 * percentile(deviations, 0.50));
    }

    private static double minimumScale(int feature) {
        if (feature == 7 || feature == 8) return 0.035;
        if (feature == 12) return 0.005;
        return feature == 0 ? 1.0 : 0.65;
    }

    private static double percentile(double[] values, double fraction) {
        if (values.length == 0) return 0.0;
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        double position = clamp(fraction, 0.0, 1.0) * (sorted.length - 1);
        int lower = (int) Math.floor(position);
        int upper = Math.min(sorted.length - 1, lower + 1);
        double blend = position - lower;
        return sorted[lower] * (1.0 - blend) + sorted[upper] * blend;
    }

    private static double medianDuration(List<Window> frames) {
        double[] values = new double[frames.size()];
        for (int i = 0; i < frames.size(); i++) values[i] = frames.get(i).duration;
        return percentile(values, 0.50);
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

    private static double dbAmplitude(double amplitude) {
        return 20.0 * Math.log10(Math.max(1e-15, amplitude));
    }

    private static final class Window {
        final double start;
        final double duration;
        final double[] feature;
        int cluster;

        Window(double start, double duration, double[] feature) {
            this.start = start;
            this.duration = duration;
            this.feature = feature;
        }
    }

    private static final class Accumulator {
        long count;
        double totalEnergy;
        double lowEnergy;
        double lowMidEnergy;
        double presenceEnergy;
        double harshEnergy;
        double airEnergy;
        double sideEnergy;
        double derivativeEnergy;
        double maximumMono;
        double maximumJump;
        long exactZeroFrames;
        double sumL, sumR, sumLL, sumRR, sumLR;
        double sumLowL, sumLowR, sumLowLL, sumLowRR, sumLowLR;

        void add(double left, double right, double mono, double side,
                 double low, double lowMid, double presence, double harsh,
                 double air, double lowLeft, double lowRight, double delta) {
            count++;
            totalEnergy += mono * mono;
            lowEnergy += low * low;
            lowMidEnergy += lowMid * lowMid;
            presenceEnergy += presence * presence;
            harshEnergy += harsh * harsh;
            airEnergy += air * air;
            sideEnergy += side * side;
            derivativeEnergy += delta * delta;
            maximumMono = Math.max(maximumMono, Math.abs(mono));
            maximumJump = Math.max(maximumJump, Math.abs(delta));
            if (left == 0.0 && right == 0.0) exactZeroFrames++;
            sumL += left;
            sumR += right;
            sumLL += left * left;
            sumRR += right * right;
            sumLR += left * right;
            sumLowL += lowLeft;
            sumLowR += lowRight;
            sumLowLL += lowLeft * lowLeft;
            sumLowRR += lowRight * lowRight;
            sumLowLR += lowLeft * lowRight;
        }

        Window finish(double start, double duration) {
            double rms = Math.sqrt(totalEnergy / Math.max(1L, count));
            double[] feature = new double[FEATURE_COUNT];
            feature[0] = dbAmplitude(rms);
            feature[1] = dbEnergy(lowEnergy, totalEnergy);
            feature[2] = dbEnergy(lowMidEnergy, totalEnergy);
            feature[3] = dbEnergy(presenceEnergy, totalEnergy);
            feature[4] = dbEnergy(harshEnergy, totalEnergy);
            feature[5] = dbEnergy(airEnergy, totalEnergy);
            feature[6] = dbEnergy(sideEnergy, totalEnergy);
            feature[7] = correlation(count, sumL, sumR, sumLL, sumRR, sumLR);
            feature[8] = correlation(count, sumLowL, sumLowR,
                    sumLowLL, sumLowRR, sumLowLR);
            feature[9] = dbAmplitude(maximumMono / Math.max(1e-15, rms));
            feature[10] = dbAmplitude(maximumJump / Math.max(1e-15, rms));
            feature[11] = dbEnergy(derivativeEnergy, totalEnergy);
            feature[12] = exactZeroFrames / (double) Math.max(1L, count);
            return new Window(start, duration, feature);
        }

        private static double correlation(long n, double sx, double sy,
                                          double sxx, double syy, double sxy) {
            if (n <= 1) return 0.0;
            double numerator = n * sxy - sx * sy;
            double denominator = Math.sqrt(Math.max(0.0, n * sxx - sx * sx)
                    * Math.max(0.0, n * syy - sy * sy));
            return denominator <= 1e-20 ? 0.0
                    : clamp(numerator / denominator, -1.0, 1.0);
        }
    }
}
