package com.dennis.aimasteringlab;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Conservative, track-trained phrase rides and event de-essing for a named lead stem. */
final class VocalHumanityModel {
    static final class Phrase {
        double startSeconds;
        double endSeconds;
        double levelDbfs;
        double rideDb;
        double confidence;
        int learnedState = -1;
    }

    static final class DeEssEvent {
        double startSeconds;
        double endSeconds;
        double attenuationDb;
        double confidence;
        double sibilanceZ;
    }

    static final class Result {
        boolean available;
        boolean analysisOnly;
        String status = "No reliable lead-vocal stem was identified";
        String method = "DAML v0.4 tier-aware track-trained vocal-humanity model";
        String leadStemPath;
        String leadSelectionEvidence;
        double deltaScale;
        double activeFloorDbfs = Double.NaN;
        double phraseFloorDbfs = Double.NaN;
        double learnedSibilanceMedianDb = Double.NaN;
        double learnedSibilanceScaleDb = Double.NaN;
        double learnedSibilanceTriggerDb = Double.NaN;
        double maximumRideUpDb;
        double maximumRideDownDb;
        double maximumDeEssDb;
        int phrasesAnalyzed;
        final List<Phrase> phrases = new ArrayList<>();
        final List<DeEssEvent> deEssEvents = new ArrayList<>();
        final List<String> safetyNotes = new ArrayList<>();

        boolean hasCorrections() {
            for (Phrase phrase : phrases) if (Math.abs(phrase.rideDb) >= 0.001) return true;
            return !deEssEvents.isEmpty();
        }

        int rideCount() {
            int count = 0;
            for (Phrase phrase : phrases) if (Math.abs(phrase.rideDb) >= 0.001) count++;
            return count;
        }

        String compactSummary() {
            if (!available) return status;
            String deEss = deEssEvents.isEmpty() ? "no de-ess events"
                    : String.format(Locale.US, "%d de-ess events (to %.2f dB)",
                    deEssEvents.size(), -maximumDeEssDb);
            if (analysisOnly) {
                return String.format(Locale.US,
                        "%s • %d vocal phrase regions • %s • analysis control only; no stem audio",
                        leadStemPath, phrases.size(), deEss);
            }
            return String.format(Locale.US,
                    "%s • %d phrase rides (%+.2f to %+.2f dB) • %s • delta scale %.2f",
                    leadStemPath, rideCount(), maximumRideDownDb, maximumRideUpDb,
                    deEss, deltaScale);
        }
    }

    private static final int BUFFER_FRAMES = 4096;
    private static final double MAX_RIDE_UP_DB = 0.60;
    private static final double MAX_RIDE_DOWN_DB = -0.45;
    private static final double MAX_DEESS_DB = 0.90;
    private static final double RIDE_ATTACK_SECONDS = 0.100;
    private static final double RIDE_RELEASE_SECONDS = 0.160;
    private static final double DEESS_ATTACK_SECONDS = 0.015;
    private static final double DEESS_RELEASE_SECONDS = 0.080;

    private VocalHumanityModel() {}

    static Result analyze(java.io.File stemArchive, AnalysisResult base,
                          AudioAnalyzer.Progress progress) throws IOException {
        return analyze(stemArchive, base, false, progress);
    }

    static Result analyzeForGuidance(java.io.File stemArchive, AnalysisResult base,
                                     AudioAnalyzer.Progress progress) throws IOException {
        return analyze(stemArchive, base, true, progress);
    }

    private static Result analyze(java.io.File stemArchive, AnalysisResult base,
                                  boolean analysisOnly,
                                  AudioAnalyzer.Progress progress) throws IOException {
        Result result = new Result();
        result.analysisOnly = analysisOnly;
        StemInventory.Item lead = chooseLead(base, result);
        if (lead == null) return result;
        if (!alignmentAllows(base, lead, analysisOnly, result)) return result;
        result.leadStemPath = lead.path;
        result.deltaScale = analysisOnly ? 0.0 : deltaScale(base.stemReconstruction);

        progress.update("AI vocal pass: learning phrases and sibilance", 0);
        List<Window> windows;
        try (ZipFile zip = new ZipFile(stemArchive)) {
            ZipEntry entry = zip.getEntry(lead.path);
            if (entry == null) throw new IOException("Lead-vocal stem entry disappeared");
            try (InputStream input = zip.getInputStream(entry);
                 WavFile.Reader reader = WavFile.open(input)) {
                windows = extract(reader, progress);
            }
        }
        if (windows.size() < 20) {
            result.status = "Lead-vocal stem is too short for reliable phrase learning";
            return result;
        }
        infer(windows, base, result);
        if (analysisOnly) {
            for (Phrase phrase : result.phrases) phrase.rideDb = 0.0;
            result.maximumRideDownDb = 0.0;
            result.maximumRideUpDb = 0.0;
        }
        result.available = true;
        if (analysisOnly) {
            result.status = result.hasCorrections()
                    ? "Analysis-only vocal timing and sibilance evidence is available"
                    : "Analysis-only lead stem validated; no sibilance event cleared the correction gate";
        } else {
            result.status = result.hasCorrections()
                    ? "Validated vocal-humanity candidate evidence is available"
                    : "Lead stem validated; no phrase or sibilance event cleared the correction gate";
        }
        result.safetyNotes.add("Original pitch, timing, breaths, vibrato and grit are not regenerated or altered.");
        result.safetyNotes.add(analysisOnly
                ? "The separated lead is used only to locate vocal phrases and sibilance; none of its waveform or a processed stem delta enters the master."
                : "Only the processed-minus-original lead delta is added around the authoritative mix; the raw stem sum is never substituted.");
        result.safetyNotes.add("The stem-aware render must beat the verified stereo-only winner; otherwise the stereo fallback remains selected.");
        progress.update("AI vocal pass complete", 100);
        return result;
    }

    private static StemInventory.Item chooseLead(AnalysisResult base, Result result) {
        StemInventory.Item best = null;
        int bestScore = Integer.MIN_VALUE;
        int secondScore = -1000000;
        for (StemInventory.Item item : base.stems) {
            if (!item.wav || item.wavInfo == null || item.error != null || !item.crcVerified) continue;
            if (item.wavInfo.sampleRate != base.wav.sampleRate) continue;
            if (item.wavInfo.channels != 1 && item.wavInfo.channels != 2) continue;
            String name = item.path.toLowerCase(Locale.US);
            int score = 0;
            if (name.contains("lead vocal")) score += 120;
            else if (name.contains("lead vox")) score += 110;
            else if (name.contains("lead") && (name.contains("vocal") || name.contains("vox"))) score += 100;
            else if (name.contains("vocal")) score += 35;
            else if (name.contains("vox")) score += 25;
            if (name.contains("main")) score += 15;
            if (name.contains("backing") || name.contains("background")
                    || name.contains("bgv") || name.contains("bvox")) score -= 120;
            if (name.contains("harmony") || name.contains("choir")) score -= 70;
            if (name.contains("instrumental")) score -= 150;
            if (item.clippedSamples > 0) score -= 35;
            if (score > bestScore) {
                secondScore = bestScore;
                bestScore = score;
                best = item;
            } else if (score > secondScore) {
                secondScore = score;
            }
        }
        if (best == null || bestScore < 80 || bestScore - secondScore <= 10) {
            result.safetyNotes.add("Filename evidence did not identify one unambiguous lead-vocal stem with a safe role margin; automatic vocal editing is disabled.");
            return null;
        }
        result.leadSelectionEvidence = "filename/role score " + bestScore
                + "; runner-up " + secondScore
                + "; intact CRC-verified WAV; matching sample rate";
        return best;
    }

    private static boolean alignmentAllows(AnalysisResult base, StemInventory.Item lead,
                                           boolean analysisOnly, Result result) {
        StemReconstruction.Result reconstruction = base.stemReconstruction;
        if (reconstruction == null || !reconstruction.attempted || !reconstruction.compatible) {
            result.status = "Lead stem exists, but sample-domain reconstruction did not establish alignment";
            result.safetyNotes.add("Vocal delta disabled because alignment evidence is incomplete.");
            return false;
        }
        if (reconstruction.globalPolarityRisk
                || (!analysisOnly && reconstruction.likelySeparationDerived)) {
            result.status = "Stem reconstruction indicates polarity risk or separation-derived material";
            result.safetyNotes.add(analysisOnly
                    ? "Analysis-only vocal guidance was disabled because global polarity evidence is unsafe."
                    : "Vocal delta disabled because only secure production-stem evidence may enter Full AI competition.");
            return false;
        }
        if (base.stemAlignment == null
                || (analysisOnly
                    ? !base.stemAlignment.analysisSecure(lead.path)
                    : !base.stemAlignment.secure(lead.path))) {
            result.status = "Lead stem failed five-position local lag/drift validation";
            result.safetyNotes.add((analysisOnly ? "Analysis-only guidance" : "Vocal delta")
                    + " disabled because the selected lead did not establish effectively zero local lag and drift.");
            return false;
        }
        if (!analysisOnly && (!Double.isFinite(reconstruction.startLagMs)
                || !Double.isFinite(reconstruction.middleLagMs)
                || !Double.isFinite(reconstruction.endLagMs)
                || Math.abs(reconstruction.startLagMs) > 1.0
                || Math.abs(reconstruction.middleLagMs) > 1.0
                || Math.abs(reconstruction.endLagMs) > 1.0
                || Math.abs(reconstruction.measuredDriftMs) > 1.0)) {
            result.status = "Lead stem exists, but measured alignment is not effectively zero";
            result.safetyNotes.add((analysisOnly ? "Analysis-only guidance" : "Vocal delta")
                    + " disabled; local lag/drift must be effectively zero at the reconstruction meter's resolution.");
            return false;
        }
        double excess = lead.wavInfo.durationSeconds - base.wav.durationSeconds;
        if (excess > 1.01 / base.wav.sampleRate) {
            result.status = "Lead stem extends beyond the authoritative mix";
            result.safetyNotes.add("Vocal delta disabled to protect the intended ending.");
            return false;
        }
        if (base.wav.durationSeconds - lead.wavInfo.durationSeconds > 2.0) {
            result.status = "Lead stem is more than two seconds shorter than the authoritative mix";
            result.safetyNotes.add("Vocal delta disabled because the mismatch is not a small original-only tail.");
            return false;
        }
        return true;
    }

    private static double deltaScale(StemReconstruction.Result reconstruction) {
        if (reconstruction.rawStemSumAllowed) return 0.70;
        return 0.50;
    }

    private static List<Window> extract(WavFile.Reader reader,
                                        AudioAnalyzer.Progress progress) throws IOException {
        ArrayList<Window> windows = new ArrayList<>();
        int rate = reader.info.sampleRate;
        int target = Math.max(512, rate / 10); // 100 ms
        double upper = Math.min(11000.0, rate * 0.22);
        double lower = Math.min(4800.0, upper * 0.62);
        Biquad belowLower = Biquad.lowPass(rate, lower);
        Biquad belowUpper = Biquad.lowPass(rate, upper);
        float[][] samples = new float[reader.info.channels][BUFFER_FRAMES];
        Accumulator accumulator = new Accumulator();
        long absolute = 0;
        int read;
        while ((read = reader.readFrames(samples, BUFFER_FRAMES)) > 0) {
            for (int frame = 0; frame < read; frame++) {
                double left = finite(samples[0][frame]);
                double right = reader.info.channels == 2
                        ? finite(samples[1][frame]) : left;
                double mid = 0.5 * (left + right);
                double lowerValue = belowLower.process(mid);
                double upperValue = belowUpper.process(mid);
                double sibilance = upperValue - lowerValue;
                accumulator.add(mid, sibilance);
                absolute++;
                if (accumulator.frames == target) {
                    windows.add(accumulator.finish(
                            (absolute - accumulator.frames) / (double) rate,
                            accumulator.frames / (double) rate));
                    accumulator = new Accumulator();
                }
            }
            int percent = (int) Math.min(94,
                    Math.round(94.0 * absolute / Math.max(1L, reader.info.frames)));
            progress.update("AI vocal pass: learning phrases and sibilance", percent);
            if (Thread.currentThread().isInterrupted()) {
                throw new IOException("Vocal analysis cancelled");
            }
        }
        if (accumulator.frames >= Math.max(128, target / 3)) {
            windows.add(accumulator.finish(
                    (absolute - accumulator.frames) / (double) rate,
                    accumulator.frames / (double) rate));
        }
        return windows;
    }

    private static void infer(List<Window> windows, AnalysisResult base, Result result) {
        double[] levels = new double[windows.size()];
        for (int i = 0; i < windows.size(); i++) levels[i] = windows.get(i).levelDbfs;
        double p10 = percentile(levels, 0.10);
        double p90 = percentile(levels, 0.90);
        result.activeFloorDbfs = twoStateFloor(levels, p10, p90);
        ArrayList<Double> broadlyActive = new ArrayList<>();
        for (double level : levels) if (level >= result.activeFloorDbfs) broadlyActive.add(level);
        double phraseFloor = result.activeFloorDbfs;
        if (broadlyActive.size() >= 20) {
            double[] values = new double[broadlyActive.size()];
            for (int i = 0; i < values.length; i++) values[i] = broadlyActive.get(i);
            phraseFloor = Math.max(phraseFloor, percentile(values, 0.15));
        }
        result.phraseFloorDbfs = phraseFloor;
        boolean[] active = new boolean[windows.size()];
        for (int i = 0; i < active.length; i++) {
            active[i] = windows.get(i).levelDbfs >= phraseFloor;
        }
        bridgeShortGaps(active, 3);
        buildPhrases(windows, active, base, result);
        inferDeEss(windows, active, base.wav.durationSeconds, result);
    }

    private static void buildPhrases(List<Window> windows, boolean[] active,
                                     AnalysisResult base, Result result) {
        ArrayList<Phrase> phrases = new ArrayList<>();
        int start = 0;
        while (start < active.length) {
            while (start < active.length && !active[start]) start++;
            if (start >= active.length) break;
            int end = start + 1;
            while (end < active.length && active[end]) end++;
            if (end - start >= 4) splitAndAddPhrases(
                    windows, start, end, base.wav.durationSeconds, base.sections, phrases);
            start = end;
        }
        result.phrasesAnalyzed = phrases.size();
        if (phrases.isEmpty()) return;
        double[] allLevels = new double[phrases.size()];
        for (int i = 0; i < phrases.size(); i++) allLevels[i] = phrases.get(i).levelDbfs;
        double globalMedian = percentile(allLevels, 0.50);
        double protectedEnding = Math.max(0.0,
                base.wav.durationSeconds - Math.max(8.0, base.wav.durationSeconds * 0.04));
        for (Phrase phrase : phrases) {
            double localMedian = localMedian(phrases, phrase, globalMedian, true);
            if (!Double.isFinite(localMedian)) {
                localMedian = localMedian(phrases, phrase, globalMedian, false);
            }
            double difference = localMedian - phrase.levelDbfs;
            double ride = clamp(difference * 0.32, MAX_RIDE_DOWN_DB, MAX_RIDE_UP_DB);
            if (Math.abs(ride) < 0.12
                    || phrase.startSeconds - RIDE_ATTACK_SECONDS < 2.0
                    || phrase.endSeconds + RIDE_RELEASE_SECONDS > protectedEnding) {
                ride = 0.0;
            }
            phrase.rideDb = ride;
            phrase.confidence = clamp(0.52 + Math.abs(difference) / 12.0, 0.0, 0.94);
            if (ride > 0.0) result.maximumRideUpDb = Math.max(result.maximumRideUpDb, ride);
            if (ride < 0.0) result.maximumRideDownDb = Math.min(result.maximumRideDownDb, ride);
            result.phrases.add(phrase);
        }
    }

    private static void splitAndAddPhrases(List<Window> windows, int runStart, int runEnd,
                                           double duration, TrackSections sections,
                                           List<Phrase> output) {
        double windowDuration = windows.get(runStart).duration;
        int maximumWindows = Math.max(8, (int) Math.round(
                clamp(duration * 0.04, 8.0, 14.0) / Math.max(0.05, windowDuration)));
        int start = runStart;
        while (runEnd - start > maximumWindows) {
            int earliest = start + Math.max(4, maximumWindows / 2);
            int latest = Math.min(runEnd - 4, start + maximumWindows);
            int split = earliest;
            double quietest = windows.get(split).levelDbfs;
            for (int i = earliest + 1; i <= latest; i++) {
                if (windows.get(i).levelDbfs < quietest) {
                    quietest = windows.get(i).levelDbfs;
                    split = i;
                }
            }
            addPhrase(windows, start, split, sections, output);
            start = split;
        }
        if (runEnd - start >= 4) addPhrase(windows, start, runEnd, sections, output);
    }

    private static void addPhrase(List<Window> windows, int start, int end,
                                  TrackSections sections, List<Phrase> output) {
        Phrase phrase = new Phrase();
        phrase.startSeconds = windows.get(start).start;
        phrase.endSeconds = windows.get(end - 1).start + windows.get(end - 1).duration;
        phrase.levelDbfs = energyAverageLevel(windows, start, end);
        phrase.learnedState = learnedStateAt(sections,
                0.5 * (phrase.startSeconds + phrase.endSeconds));
        output.add(phrase);
    }

    private static int learnedStateAt(TrackSections sections, double seconds) {
        if (sections == null || !sections.learnedMapping) return -1;
        for (TrackSections.Segment segment : sections.learnedStructure) {
            if (seconds >= segment.startSeconds
                    && seconds < segment.startSeconds + segment.durationSeconds) {
                return segment.learnedState;
            }
        }
        return -1;
    }

    private static void inferDeEss(List<Window> windows, boolean[] active,
                                   double mixDuration, Result result) {
        ArrayList<Double> activeShares = new ArrayList<>();
        for (int i = 0; i < windows.size(); i++) {
            if (active[i] && Double.isFinite(windows.get(i).sibilanceShareDb)) {
                activeShares.add(windows.get(i).sibilanceShareDb);
            }
        }
        if (activeShares.size() < 20) return;
        double[] shares = new double[activeShares.size()];
        for (int i = 0; i < shares.length; i++) shares[i] = activeShares.get(i);
        double median = percentile(shares, 0.50);
        double[] deviations = new double[shares.length];
        for (int i = 0; i < shares.length; i++) deviations[i] = Math.abs(shares[i] - median);
        double scale = Math.max(0.60, 1.4826 * percentile(deviations, 0.50));
        double trigger = median + 2.80 * scale;
        result.learnedSibilanceMedianDb = median;
        result.learnedSibilanceScaleDb = scale;
        result.learnedSibilanceTriggerDb = trigger;
        double protectedEnding = Math.max(0.0,
                mixDuration - Math.max(8.0, mixDuration * 0.04));

        ArrayList<DeEssEvent> candidates = new ArrayList<>();
        int start = 0;
        while (start < windows.size()) {
            boolean candidate = active[start]
                    && windows.get(start).sibilanceShareDb >= trigger;
            if (!candidate) {
                start++;
                continue;
            }
            int end = start + 1;
            double maximumZ = (windows.get(start).sibilanceShareDb - median) / scale;
            while (end < windows.size() && active[end]
                    && windows.get(end).sibilanceShareDb >= trigger) {
                maximumZ = Math.max(maximumZ,
                        (windows.get(end).sibilanceShareDb - median) / scale);
                end++;
            }
            double eventStart = windows.get(start).start;
            double eventEnd = windows.get(end - 1).start + windows.get(end - 1).duration;
            double eventDuration = eventEnd - eventStart;
            if (eventDuration <= 0.60
                    && eventStart - DEESS_ATTACK_SECONDS >= 2.0
                    && eventEnd + DEESS_RELEASE_SECONDS <= protectedEnding) {
                DeEssEvent event = new DeEssEvent();
                event.startSeconds = eventStart;
                event.endSeconds = eventEnd;
                event.sibilanceZ = maximumZ;
                double strength = clamp((maximumZ - 2.80) / 4.0, 0.0, 1.0);
                event.attenuationDb = -(0.20 + (MAX_DEESS_DB - 0.20) * strength);
                event.confidence = clamp(0.70 + 0.06 * (maximumZ - 2.80), 0.0, 0.97);
                candidates.add(event);
            }
            start = end;
        }
        double coverage = 0.0;
        for (DeEssEvent event : candidates) coverage += event.endSeconds - event.startSeconds;
        if (coverage > Math.max(1.2, mixDuration * 0.04)) {
            result.safetyNotes.add("Sibilance outliers were too widespread for event-only de-essing; no automatic de-essing was enabled.");
            return;
        }
        if (candidates.size() > 64) {
            candidates.sort((a, b) -> Double.compare(b.confidence, a.confidence));
            candidates.subList(64, candidates.size()).clear();
            candidates.sort(Comparator.comparingDouble(event -> event.startSeconds));
        }
        result.deEssEvents.addAll(candidates);
        for (DeEssEvent event : candidates) {
            result.maximumDeEssDb = Math.max(result.maximumDeEssDb, -event.attenuationDb);
        }
    }

    private static void bridgeShortGaps(boolean[] active, int maximumGap) {
        int i = 0;
        while (i < active.length) {
            while (i < active.length && active[i]) i++;
            int start = i;
            while (i < active.length && !active[i]) i++;
            int length = i - start;
            if (start > 0 && i < active.length && length <= maximumGap) {
                for (int j = start; j < i; j++) active[j] = true;
            }
        }
    }

    private static double localMedian(List<Phrase> phrases, Phrase target,
                                      double fallback, boolean sameStateOnly) {
        ArrayList<Double> local = new ArrayList<>();
        double targetMiddle = 0.5 * (target.startSeconds + target.endSeconds);
        for (Phrase phrase : phrases) {
            double middle = 0.5 * (phrase.startSeconds + phrase.endSeconds);
            if (Math.abs(middle - targetMiddle) <= 45.0
                    && (!sameStateOnly || target.learnedState < 0
                    || phrase.learnedState == target.learnedState)) {
                local.add(phrase.levelDbfs);
            }
        }
        if (local.size() < 3) return sameStateOnly ? Double.NaN : fallback;
        double[] values = new double[local.size()];
        for (int i = 0; i < values.length; i++) values[i] = local.get(i);
        return percentile(values, 0.50);
    }

    private static double energyAverageLevel(List<Window> windows, int start, int end) {
        double sum = 0.0;
        int count = 0;
        for (int i = start; i < end; i++) {
            sum += Math.pow(10.0, windows.get(i).levelDbfs / 10.0);
            count++;
        }
        return count == 0 ? Double.NEGATIVE_INFINITY
                : 10.0 * Math.log10(sum / count);
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
        double minimum = low + 2.0;
        double maximum = high - 1.0;
        if (maximum < minimum) return 0.5 * (low + high);
        return clamp(0.5 * (a + b), minimum, maximum);
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

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static final class Window {
        double start;
        double duration;
        double levelDbfs;
        double sibilanceShareDb;
    }

    private static final class Accumulator {
        int frames;
        double total;
        double sibilance;

        void add(double value, double sibilanceValue) {
            frames++;
            total += value * value;
            sibilance += sibilanceValue * sibilanceValue;
        }

        Window finish(double start, double duration) {
            Window out = new Window();
            out.start = start;
            out.duration = duration;
            out.levelDbfs = AudioAnalyzer.toDb(Math.sqrt(total / Math.max(1, frames)));
            out.sibilanceShareDb = 10.0 * Math.log10(
                    (sibilance + 1.0e-18) / (total + 1.0e-18));
            return out;
        }
    }
}
