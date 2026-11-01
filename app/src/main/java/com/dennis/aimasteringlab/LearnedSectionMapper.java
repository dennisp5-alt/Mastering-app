package com.dennis.aimasteringlab;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Learns repeated arrangement states from the selected song. Labels remain
 * candidates: no unsupervised model can know the composer's section names.
 */
final class LearnedSectionMapper {
    private static final int BUFFER_FRAMES = 4096;
    private static final int FEATURE_COUNT = 7;

    private LearnedSectionMapper() {}

    static TrackSections analyze(AudioAnalyzer.StreamProvider provider,
                                 AnalysisResult base,
                                 AudioAnalyzer.Progress progress) throws IOException {
        progress.update("AI section map: learning arrangement states", 0);
        Extraction extraction = extract(provider, base, progress);
        if (extraction.windows.size() < 24) {
            throw new IOException("Track is too short for learned section mapping");
        }
        TrackSections mapped = infer(extraction, base.wav.durationSeconds);
        progress.update("AI section map complete", 100);
        return mapped;
    }

    private static Extraction extract(AudioAnalyzer.StreamProvider provider,
                                      AnalysisResult base,
                                      AudioAnalyzer.Progress progress) throws IOException {
        Extraction extraction = new Extraction();
        try (WavFile.Reader reader = WavFile.open(provider.open())) {
            int rate = reader.info.sampleRate;
            int target = Math.max(1024, rate); // one-second learned blocks
            LoudnessMeter loudness = new LoudnessMeter(reader.info.channels, rate);
            Biquad low = Biquad.lowPass(rate, 180.0);
            Biquad belowOne = Biquad.lowPass(rate, 1000.0);
            Biquad belowFive = Biquad.lowPass(rate, Math.min(5000.0, rate * 0.20));
            float[][] samples = new float[reader.info.channels][BUFFER_FRAMES];
            Accumulator accumulator = new Accumulator();
            long absoluteFrame = 0;
            double previousMono = 0.0;
            boolean havePrevious = false;
            int read;
            while ((read = reader.readFrames(samples, BUFFER_FRAMES)) > 0) {
                for (int frame = 0; frame < read; frame++) {
                    loudness.addFrame(samples, frame);
                    double left = finite(samples[0][frame]);
                    double right = reader.info.channels == 2
                            ? finite(samples[1][frame]) : left;
                    double mono = 0.5 * (left + right);
                    double side = 0.5 * (left - right);
                    double lowValue = low.process(mono);
                    double one = belowOne.process(mono);
                    double five = belowFive.process(mono);
                    double lowMid = one - lowValue;
                    double presence = five - one;
                    double air = mono - five;
                    double delta = havePrevious ? mono - previousMono : 0.0;
                    accumulator.add(mono, side, lowValue, lowMid, presence, air, delta);
                    previousMono = mono;
                    havePrevious = true;
                    absoluteFrame++;
                    if (accumulator.frames == target) {
                        extraction.windows.add(accumulator.finish(
                                (absoluteFrame - accumulator.frames) / (double) rate,
                                accumulator.frames / (double) rate));
                        accumulator = new Accumulator();
                    }
                }
                int percent = (int) Math.min(92,
                        Math.round(92.0 * absoluteFrame / Math.max(1L, reader.info.frames)));
                progress.update("AI section map: learning arrangement states", percent);
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("Section mapping cancelled");
                }
            }
            if (accumulator.frames >= Math.max(512, target / 3)) {
                extraction.windows.add(accumulator.finish(
                        (absoluteFrame - accumulator.frames) / (double) rate,
                        accumulator.frames / (double) rate));
            }
            extraction.loudness = loudness.finish();
        }
        return extraction;
    }

    private static TrackSections infer(Extraction extraction, double duration) {
        List<Window> windows = extraction.windows;
        double[] levels = new double[windows.size()];
        for (int i = 0; i < levels.length; i++) levels[i] = windows.get(i).feature[0];
        double p10 = percentile(levels, 0.10);
        double p90 = percentile(levels, 0.90);
        double activeFloor = twoStateFloor(levels, p10, p90);
        ArrayList<Integer> active = new ArrayList<>();
        for (int i = 0; i < windows.size(); i++) {
            if (windows.get(i).feature[0] >= activeFloor) active.add(i);
        }
        if (active.size() < 18) {
            active.clear();
            for (int i = 0; i < windows.size(); i++) {
                if (windows.get(i).feature[0] >= p10) active.add(i);
            }
        }
        int k = active.size() >= 210 ? 5 : active.size() >= 110 ? 4 : 3;
        k = Math.max(2, Math.min(k, active.size()));

        double[] medians = new double[FEATURE_COUNT];
        double[] scales = new double[FEATURE_COUNT];
        for (int feature = 0; feature < FEATURE_COUNT; feature++) {
            double[] values = values(windows, active, feature);
            medians[feature] = percentile(values, 0.50);
            scales[feature] = robustScale(values);
        }
        double[][] vectors = new double[active.size()][FEATURE_COUNT];
        for (int i = 0; i < active.size(); i++) {
            Window window = windows.get(active.get(i));
            for (int feature = 0; feature < FEATURE_COUNT; feature++) {
                vectors[i][feature] = clamp((window.feature[feature] - medians[feature])
                        / scales[feature], -6.0, 6.0);
            }
        }
        int[] assignment = kMeans(vectors, k);
        for (int i = 0; i < active.size(); i++) windows.get(active.get(i)).state = assignment[i];
        smoothStates(windows);

        int[] runs = new int[k];
        double[] stateLevel = new double[k];
        int[] stateCount = new int[k];
        int prior = -2;
        for (Window window : windows) {
            if (window.state >= 0) {
                stateLevel[window.state] += window.feature[0];
                stateCount[window.state]++;
                if (window.state != prior) runs[window.state]++;
            }
            prior = window.state;
        }
        for (int state = 0; state < k; state++) {
            stateLevel[state] /= Math.max(1, stateCount[state]);
        }
        int chorusState = recurringExtreme(stateLevel, runs, true);
        int verseState = recurringExtreme(stateLevel, runs, false);

        TrackSections out = new TrackSections();
        out.learnedMapping = true;
        out.learnedStates = k;
        out.method = "DMS-AI v0.4 track-trained arrangement clustering; roles are confidence-ranked candidates requiring listening confirmation";
        buildStructure(out, windows, extraction.loudness, chorusState, verseState, duration);
        out.boundaryCandidates = Math.max(0, out.learnedStructure.size() - 1);

        Candidate quiet = chooseQuiet(windows, extraction.loudness, duration, activeFloor);
        Candidate chorus = chooseChorus(windows, extraction.loudness, duration,
                chorusState, runs);
        Candidate busy = chooseBusy(windows, extraction.loudness, duration, chorus,
                medians, scales);
        Candidate ending = candidateAt(extraction.loudness,
                Math.max(0.0, duration - Math.min(12.0, duration)),
                Math.min(12.0, duration), 1.0, -1);

        add(out, "quiet_vocal_or_verse_learned_candidate", quiet);
        add(out, "full_chorus_learned_candidate", chorus);
        add(out, "busiest_alternate_learned_candidate", busy);
        add(out, "ending", ending);
        out.quietLufs = quiet == null ? Double.NaN : quiet.lufs;
        out.chorusProxyLufs = chorus == null ? Double.NaN : chorus.lufs;
        out.busyLufs = busy == null ? Double.NaN : busy.lufs;
        out.endingLufs = ending == null ? Double.NaN : ending.lufs;
        if (quiet != null && chorus != null) {
            out.quietToChorusLiftLu = chorus.lufs - quiet.lufs;
        }
        double confidence = 0.45;
        if (chorus != null) confidence += 0.20 * chorus.confidence;
        if (quiet != null) confidence += 0.15 * quiet.confidence;
        if (busy != null) confidence += 0.10 * busy.confidence;
        confidence += Math.min(0.10, out.boundaryCandidates * 0.01);
        out.mappingConfidence = clamp(confidence, 0.0, 0.95);
        return out;
    }

    private static void buildStructure(TrackSections out, List<Window> windows,
                                       LoudnessMeter.Result loudness,
                                       int chorusState, int verseState,
                                       double duration) {
        int start = 0;
        while (start < windows.size()) {
            int state = windows.get(start).state;
            int end = start + 1;
            while (end < windows.size() && windows.get(end).state == state) end++;
            double segmentStart = windows.get(start).start;
            double segmentEnd = Math.min(duration,
                    windows.get(end - 1).start + windows.get(end - 1).duration);
            TrackSections.Segment segment = new TrackSections.Segment();
            segment.startSeconds = segmentStart;
            segment.durationSeconds = Math.max(0.0, segmentEnd - segmentStart);
            segment.learnedState = state;
            segment.averageLufs = windowLufs(loudness, segmentStart, segment.durationSeconds);
            if (state < 0) segment.role = "silence_or_tail_candidate";
            else if (state == chorusState) segment.role = "recurring_high_energy_state_candidate";
            else if (state == verseState) segment.role = "recurring_quieter_state_candidate";
            else segment.role = "alternate_arrangement_state_candidate";
            if (start == 0) segment.role = "intro_" + segment.role;
            if (end == windows.size()) segment.role = "outro_" + segment.role;
            segment.confidence = state < 0 ? 0.55 : 0.72;
            out.learnedStructure.add(segment);
            start = end;
        }
    }

    private static Candidate chooseQuiet(List<Window> windows, LoudnessMeter.Result loudness,
                                         double duration, double activeFloor) {
        double length = Math.min(12.0, Math.max(7.0, duration * 0.045));
        int count = Math.max(1, (int) Math.round(length));
        Candidate best = null;
        for (int start = Math.max(1, (int) Math.floor(duration * 0.04));
             start + count < windows.size() && start < duration * 0.75; start++) {
            double level = averageFeature(windows, start, count, 0);
            if (level < activeFloor) continue;
            double activeFraction = activeFraction(windows, start, count, activeFloor);
            if (activeFraction < 0.65) continue;
            double lufs = windowLufs(loudness, windows.get(start).start, length);
            if (!Double.isFinite(lufs)) continue;
            if (best == null || lufs < best.lufs) {
                int state = dominantState(windows, start, count);
                best = new Candidate(windows.get(start).start, length, lufs,
                        clamp(0.58 + 0.25 * activeFraction, 0.0, 0.90), state);
            }
        }
        return best;
    }

    private static Candidate chooseChorus(List<Window> windows,
                                          LoudnessMeter.Result loudness,
                                          double duration, int chorusState,
                                          int[] runs) {
        double length = Math.min(15.0, Math.max(9.0, duration * 0.055));
        int count = Math.max(1, (int) Math.round(length));
        Candidate best = null;
        for (int start = Math.max(1, (int) Math.floor(duration * 0.10));
             start + count < windows.size() && start + count < duration * 0.93; start++) {
            int state = dominantState(windows, start, count);
            if (state < 0) continue;
            double stateFraction = stateFraction(windows, start, count, state);
            double lufs = windowLufs(loudness, windows.get(start).start, length);
            if (!Double.isFinite(lufs)) continue;
            double recurrence = Math.min(1.0, Math.max(0, runs[state] - 1) / 3.0);
            double score = lufs + (state == chorusState ? 1.2 : 0.0)
                    + 0.8 * recurrence + 0.35 * stateFraction;
            if (best == null || score > best.score) {
                double confidence = clamp(0.52 + 0.22 * recurrence
                        + 0.18 * stateFraction + (state == chorusState ? 0.06 : 0.0),
                        0.0, 0.95);
                best = new Candidate(windows.get(start).start, length, lufs,
                        confidence, state, score);
            }
        }
        return best;
    }

    private static Candidate chooseBusy(List<Window> windows,
                                        LoudnessMeter.Result loudness,
                                        double duration, Candidate exclude,
                                        double[] medians, double[] scales) {
        double length = Math.min(12.0, Math.max(7.0, duration * 0.045));
        int count = Math.max(1, (int) Math.round(length));
        Candidate best = null;
        for (int start = Math.max(1, (int) Math.floor(duration * 0.08));
             start + count < windows.size() && start + count < duration * 0.93; start++) {
            double seconds = windows.get(start).start;
            if (exclude != null && overlaps(seconds, length, exclude.start, exclude.duration, 3.0)) {
                continue;
            }
            double level = z(averageFeature(windows, start, count, 0), medians[0], scales[0]);
            double presence = z(averageFeature(windows, start, count, 3), medians[3], scales[3]);
            double air = z(averageFeature(windows, start, count, 4), medians[4], scales[4]);
            double width = z(averageFeature(windows, start, count, 5), medians[5], scales[5]);
            double flux = z(averageFeature(windows, start, count, 6), medians[6], scales[6]);
            double score = 0.44 * level + 0.20 * presence + 0.10 * air
                    + 0.08 * width + 0.18 * flux;
            double lufs = windowLufs(loudness, seconds, length);
            if (!Double.isFinite(lufs)) continue;
            if (best == null || score > best.score) {
                best = new Candidate(seconds, length, lufs,
                        clamp(0.62 + 0.08 * Math.max(0.0, score), 0.0, 0.91),
                        dominantState(windows, start, count), score);
            }
        }
        return best;
    }

    private static void add(TrackSections out, String role, Candidate candidate) {
        if (candidate == null) return;
        TrackSections.Segment segment = new TrackSections.Segment();
        segment.role = role;
        segment.startSeconds = candidate.start;
        segment.durationSeconds = candidate.duration;
        segment.averageLufs = candidate.lufs;
        segment.confidence = candidate.confidence;
        segment.learnedState = candidate.state;
        out.segments.add(segment);
    }

    private static Candidate candidateAt(LoudnessMeter.Result loudness, double start,
                                         double duration, double confidence, int state) {
        return new Candidate(start, duration, windowLufs(loudness, start, duration),
                confidence, state);
    }

    private static double windowLufs(LoudnessMeter.Result loudness,
                                     double start, double duration) {
        double[] values = loudness.momentaryLufs;
        if (values == null || values.length == 0) return Double.NaN;
        int first = Math.max(0, Math.min(values.length - 1,
                (int) Math.floor(start / loudness.hopSeconds)));
        int frames = Math.max(1, (int) Math.round(duration / loudness.hopSeconds));
        int end = Math.min(values.length, first + frames);
        double energy = 0.0;
        int count = 0;
        for (int i = first; i < end; i++) {
            if (!Double.isFinite(values[i])) continue;
            energy += Math.pow(10.0, (values[i] + 0.691) / 10.0);
            count++;
        }
        return count == 0 ? Double.NaN
                : -0.691 + 10.0 * Math.log10(energy / count);
    }

    private static void smoothStates(List<Window> windows) {
        for (int pass = 0; pass < 2; pass++) {
            int[] original = new int[windows.size()];
            int maximumState = -1;
            for (int i = 0; i < original.length; i++) {
                original[i] = windows.get(i).state;
                maximumState = Math.max(maximumState, original[i]);
            }
            for (int i = 0; i < original.length; i++) {
                if (original[i] < 0) continue;
                int[] counts = new int[maximumState + 1];
                for (int j = Math.max(0, i - 2); j <= Math.min(original.length - 1, i + 2); j++) {
                    if (original[j] >= 0) counts[original[j]]++;
                }
                int best = original[i];
                for (int state = 0; state < counts.length; state++) {
                    if (counts[state] > counts[best]) best = state;
                }
                if (counts[best] >= 3) windows.get(i).state = best;
            }
        }
        // Eliminate one- and two-second state flicker. Longer boundaries remain evidence.
        for (int pass = 0; pass < 3; pass++) {
            int start = 0;
            boolean changed = false;
            while (start < windows.size()) {
                int state = windows.get(start).state;
                int end = start + 1;
                while (end < windows.size() && windows.get(end).state == state) end++;
                if (state >= 0 && end - start < 3) {
                    int left = start > 0 ? windows.get(start - 1).state : -1;
                    int right = end < windows.size() ? windows.get(end).state : -1;
                    int replacement = left >= 0 ? left : right;
                    if (left >= 0 && right >= 0 && left != right) {
                        replacement = neighbouringRunLength(windows, start - 1, left, -1)
                                >= neighbouringRunLength(windows, end, right, 1) ? left : right;
                    }
                    if (replacement >= 0) {
                        for (int i = start; i < end; i++) windows.get(i).state = replacement;
                        changed = true;
                    }
                }
                start = end;
            }
            if (!changed) break;
        }
    }

    private static int neighbouringRunLength(List<Window> windows, int index,
                                             int state, int direction) {
        int count = 0;
        while (index >= 0 && index < windows.size() && windows.get(index).state == state) {
            count++;
            index += direction;
        }
        return count;
    }

    private static int recurringExtreme(double[] stateLevel, int[] runs, boolean highest) {
        int best = 0;
        double score = highest ? -Double.MAX_VALUE : Double.MAX_VALUE;
        boolean foundRepeated = false;
        for (int state = 0; state < stateLevel.length; state++) {
            boolean repeated = runs[state] >= 2;
            if (foundRepeated && !repeated) continue;
            if (!foundRepeated && repeated) {
                foundRepeated = true;
                score = highest ? -Double.MAX_VALUE : Double.MAX_VALUE;
            }
            if ((highest && stateLevel[state] > score)
                    || (!highest && stateLevel[state] < score)) {
                score = stateLevel[state];
                best = state;
            }
        }
        return best;
    }

    private static int dominantState(List<Window> windows, int start, int count) {
        int maxState = -1;
        for (int i = start; i < Math.min(windows.size(), start + count); i++) {
            maxState = Math.max(maxState, windows.get(i).state);
        }
        if (maxState < 0) return -1;
        int[] counts = new int[maxState + 1];
        for (int i = start; i < Math.min(windows.size(), start + count); i++) {
            int state = windows.get(i).state;
            if (state >= 0) counts[state]++;
        }
        int best = 0;
        for (int state = 1; state < counts.length; state++) {
            if (counts[state] > counts[best]) best = state;
        }
        return best;
    }

    private static double stateFraction(List<Window> windows, int start, int count, int state) {
        int hits = 0;
        int actual = 0;
        for (int i = start; i < Math.min(windows.size(), start + count); i++) {
            actual++;
            if (windows.get(i).state == state) hits++;
        }
        return hits / (double) Math.max(1, actual);
    }

    private static double activeFraction(List<Window> windows, int start, int count,
                                         double floor) {
        int hits = 0;
        int actual = 0;
        for (int i = start; i < Math.min(windows.size(), start + count); i++) {
            actual++;
            if (windows.get(i).feature[0] >= floor) hits++;
        }
        return hits / (double) Math.max(1, actual);
    }

    private static double averageFeature(List<Window> windows, int start, int count,
                                         int feature) {
        double sum = 0.0;
        int actual = 0;
        for (int i = start; i < Math.min(windows.size(), start + count); i++) {
            sum += windows.get(i).feature[feature];
            actual++;
        }
        return sum / Math.max(1, actual);
    }

    private static double[] values(List<Window> windows, List<Integer> indices, int feature) {
        double[] out = new double[indices.size()];
        for (int i = 0; i < indices.size(); i++) {
            out[i] = windows.get(indices.get(i)).feature[feature];
        }
        return out;
    }

    private static double twoStateFloor(double[] levels, double low, double high) {
        double a = low;
        double b = high;
        for (int iteration = 0; iteration < 24; iteration++) {
            double sumA = 0.0, sumB = 0.0;
            int countA = 0, countB = 0;
            for (double value : levels) {
                if (Math.abs(value - a) <= Math.abs(value - b)) {
                    sumA += value;
                    countA++;
                } else {
                    sumB += value;
                    countB++;
                }
            }
            if (countA > 0) a = sumA / countA;
            if (countB > 0) b = sumB / countB;
        }
        return Math.min(Math.max((a + b) * 0.5, low + 2.0), high - 1.0);
    }

    private static int[] kMeans(double[][] vectors, int k) {
        int dimensions = vectors[0].length;
        double[][] centers = new double[k][dimensions];
        centers[0] = vectors[vectors.length / 2].clone();
        for (int center = 1; center < k; center++) {
            int best = 0;
            double bestDistance = -1.0;
            for (int i = 0; i < vectors.length; i++) {
                double nearest = Double.POSITIVE_INFINITY;
                for (int prior = 0; prior < center; prior++) {
                    nearest = Math.min(nearest, distance(vectors[i], centers[prior]));
                }
                if (nearest > bestDistance) {
                    bestDistance = nearest;
                    best = i;
                }
            }
            centers[center] = vectors[best].clone();
        }
        int[] assignments = new int[vectors.length];
        Arrays.fill(assignments, -1);
        for (int iteration = 0; iteration < 24; iteration++) {
            boolean changed = false;
            for (int i = 0; i < vectors.length; i++) {
                int nearest = nearest(vectors[i], centers);
                if (assignments[i] != nearest) {
                    assignments[i] = nearest;
                    changed = true;
                }
            }
            double[][] sums = new double[k][dimensions];
            int[] counts = new int[k];
            for (int i = 0; i < vectors.length; i++) {
                int state = assignments[i];
                counts[state]++;
                for (int feature = 0; feature < dimensions; feature++) {
                    sums[state][feature] += vectors[i][feature];
                }
            }
            for (int state = 0; state < k; state++) {
                if (counts[state] == 0) continue;
                for (int feature = 0; feature < dimensions; feature++) {
                    centers[state][feature] = sums[state][feature] / counts[state];
                }
            }
            if (!changed) break;
        }
        return assignments;
    }

    private static int nearest(double[] vector, double[][] centers) {
        int best = 0;
        double bestDistance = distance(vector, centers[0]);
        for (int i = 1; i < centers.length; i++) {
            double value = distance(vector, centers[i]);
            if (value < bestDistance) {
                bestDistance = value;
                best = i;
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

    private static double robustScale(double[] values) {
        double median = percentile(values, 0.50);
        double[] deviations = new double[values.length];
        for (int i = 0; i < values.length; i++) deviations[i] = Math.abs(values[i] - median);
        return Math.max(0.20, 1.4826 * percentile(deviations, 0.50));
    }

    private static double percentile(double[] input, double fraction) {
        if (input.length == 0) return 0.0;
        double[] values = input.clone();
        Arrays.sort(values);
        double position = clamp(fraction, 0.0, 1.0) * (values.length - 1);
        int low = (int) Math.floor(position);
        int high = Math.min(values.length - 1, low + 1);
        double mix = position - low;
        return values[low] * (1.0 - mix) + values[high] * mix;
    }

    private static boolean overlaps(double aStart, double aDuration,
                                    double bStart, double bDuration, double margin) {
        return aStart < bStart + bDuration + margin
                && bStart < aStart + aDuration + margin;
    }

    private static double z(double value, double median, double scale) {
        return (value - median) / Math.max(0.20, scale);
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static final class Extraction {
        final List<Window> windows = new ArrayList<>();
        LoudnessMeter.Result loudness;
    }

    private static final class Window {
        double start;
        double duration;
        final double[] feature = new double[FEATURE_COUNT];
        int state = -1;
    }

    private static final class Accumulator {
        int frames;
        double total;
        double low;
        double lowMid;
        double presence;
        double air;
        double side;
        double delta;

        void add(double mono, double sideValue, double lowValue, double lowMidValue,
                 double presenceValue, double airValue, double deltaValue) {
            frames++;
            total += mono * mono;
            side += sideValue * sideValue;
            low += lowValue * lowValue;
            lowMid += lowMidValue * lowMidValue;
            presence += presenceValue * presenceValue;
            air += airValue * airValue;
            delta += deltaValue * deltaValue;
        }

        Window finish(double start, double duration) {
            Window out = new Window();
            out.start = start;
            out.duration = duration;
            double energy = total / Math.max(1, frames);
            out.feature[0] = AudioAnalyzer.toDb(Math.sqrt(energy));
            out.feature[1] = ratioDb(low, total);
            out.feature[2] = ratioDb(lowMid, total);
            out.feature[3] = ratioDb(presence, total);
            out.feature[4] = ratioDb(air, total);
            out.feature[5] = ratioDb(side, total);
            out.feature[6] = ratioDb(delta, total);
            return out;
        }

        private static double ratioDb(double numerator, double denominator) {
            return 10.0 * Math.log10((numerator + 1.0e-18) / (denominator + 1.0e-18));
        }
    }

    private static final class Candidate {
        final double start;
        final double duration;
        final double lufs;
        final double confidence;
        final int state;
        final double score;

        Candidate(double start, double duration, double lufs,
                  double confidence, int state) {
            this(start, duration, lufs, confidence, state, 0.0);
        }

        Candidate(double start, double duration, double lufs,
                  double confidence, int state, double score) {
            this.start = start;
            this.duration = duration;
            this.lufs = lufs;
            this.confidence = confidence;
            this.state = state;
            this.score = score;
        }
    }
}
