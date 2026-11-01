package com.dennis.aimasteringlab;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Learns short, frequency-specific vocal-masking events from aligned stems.
 * The model is intentionally conservative: findings are attenuation-only,
 * instrument-side, protected at the file boundaries and never release-promoted.
 */
final class MaskingPocketModel {
    static final class Event {
        String stemPath;
        String stemRole;
        int bandIndex;
        String bandLabel;
        double lowHz;
        double highHz;
        double startSeconds;
        double endSeconds;
        double attenuationDb;
        double confidence;
        double maskingRatioDb;
        double learnedTriggerDb;
        String evidence;
        int startWindow;
        int endWindow;
    }

    static final class Result {
        boolean available;
        boolean analysisOnly;
        String status = "No reliable stem-aware masking analysis is available";
        String method = "DAML v0.4 tier-aware track-trained stem masking-pocket model";
        String leadStemPath;
        double deltaScale;
        int windowsAnalyzed;
        int activeVocalWindows;
        int competingStemsAnalyzed;
        double maximumCutDb;
        double maximumCombinedNominalCutDb;
        double totalPocketSeconds;
        final List<String> competingStemPaths = new ArrayList<>();
        final List<Event> events = new ArrayList<>();
        final List<String> safetyNotes = new ArrayList<>();

        boolean hasCorrections() {
            return !events.isEmpty();
        }

        String compactSummary() {
            if (!available) return status;
            if (events.isEmpty()) {
                return String.format(Locale.US,
                        "%d competing stems analysed • no local masking event cleared every gate",
                        competingStemsAnalyzed);
            }
            if (analysisOnly) {
                return String.format(Locale.US,
                        "%d analysis-only pockets across %d competing stems • max guide %.2f dB • no stem waveform used",
                        events.size(), competingStemsAnalyzed, maximumCutDb);
            }
            return String.format(Locale.US,
                    "%d pockets across %d competing stems • max cut %.2f dB • delta scale %.2f",
                    events.size(), competingStemsAnalyzed, maximumCutDb, deltaScale);
        }
    }

    private static final int BUFFER_FRAMES = 4096;
    private static final int MAX_COMPETING_STEMS = 6;
    private static final int MAX_EVENTS = 48;
    private static final double WINDOW_SECONDS = 0.100;
    private static final double MAX_EVENT_SECONDS = 2.50;
    private static final double MIN_CUT_DB = 0.12;
    private static final double MAX_CUT_DB = 0.60;
    private static final double MAX_COMBINED_CUT_DB = 0.75;
    private static final double ROBUST_TRIGGER_Z = 2.40;
    private static final double MAX_BAND_COVERAGE_FRACTION = 0.18;
    private static final double MAX_TOTAL_COVERAGE_FRACTION = 0.18;
    private static final double RENDER_ATTACK_SECONDS = 0.080;
    private static final double RENDER_RELEASE_SECONDS = 0.140;
    private static final double[] BAND_LOW_HZ = {250.0, 700.0, 1600.0, 3200.0};
    private static final double[] BAND_HIGH_HZ = {700.0, 1600.0, 3200.0, 6500.0};
    private static final String[] BAND_LABELS = {
            "vocal body", "vocal articulation", "vocal presence", "vocal clarity"
    };

    private MaskingPocketModel() {}

    static Result analyze(File stemArchive, AnalysisResult base,
                          AudioAnalyzer.Progress progress) throws IOException {
        return analyze(stemArchive, base, false, progress);
    }

    static Result analyzeForGuidance(File stemArchive, AnalysisResult base,
                                     AudioAnalyzer.Progress progress) throws IOException {
        return analyze(stemArchive, base, true, progress);
    }

    private static Result analyze(File stemArchive, AnalysisResult base,
                                  boolean analysisOnly,
                                  AudioAnalyzer.Progress progress) throws IOException {
        Result result = new Result();
        result.analysisOnly = analysisOnly;
        if (base.vocalHumanity == null || !base.vocalHumanity.available
                || base.vocalHumanity.leadStemPath == null) {
            result.status = "A validated lead-vocal stem is required before masking-pocket analysis";
            result.safetyNotes.add("No instrument-side edit was enabled without lead-stem identity and alignment evidence.");
            return result;
        }
        if (!alignmentAllows(base, analysisOnly)) {
            result.status = analysisOnly
                    ? "Stem alignment is not safe enough for analysis-only masking guidance"
                    : "Stem alignment is not safe enough for processed-minus-original masking deltas";
            result.safetyNotes.add("Masking guidance was disabled because local lag or drift evidence was incomplete.");
            return result;
        }
        StemInventory.Item lead = findStem(base, base.vocalHumanity.leadStemPath);
        if (!validStem(lead, base)) {
            result.status = "The validated lead stem is no longer available in the stem inventory";
            return result;
        }
        List<StemInventory.Item> competitors = chooseCompetitors(base, lead,
                analysisOnly);
        if (competitors.isEmpty()) {
            result.status = "No securely identified competing instrument stem is available";
            result.safetyNotes.add("Drums, bass, vocals and ambiguous stem roles are excluded from automatic masking pockets.");
            return result;
        }

        result.leadStemPath = lead.path;
        result.deltaScale = analysisOnly ? 0.0 : base.vocalHumanity.deltaScale;
        progress.update("AI masking pass: learning vocal and instrument overlap", 0);

        List<BandWindow> leadWindows;
        ArrayList<Event> candidates = new ArrayList<>();
        try (ZipFile zip = new ZipFile(stemArchive)) {
            leadWindows = readWindows(zip, lead, "lead vocal", progress);
            if (leadWindows.size() < 20) {
                result.status = "The lead stem is too short for robust masking analysis";
                return result;
            }
            result.windowsAnalyzed = leadWindows.size();
            boolean[] active = activeWindows(leadWindows, base);
            for (boolean value : active) if (value) result.activeVocalWindows++;
            if (result.activeVocalWindows < 20) {
                result.status = "Too little protected lead-vocal activity was available for masking analysis";
                return result;
            }

            int index = 0;
            for (StemInventory.Item competitor : competitors) {
                index++;
                progress.update("AI masking pass: analysing " + role(competitor.path)
                        + " " + index + "/" + competitors.size(), -1);
                List<BandWindow> competingWindows = readWindows(
                        zip, competitor, role(competitor.path), progress);
                if (Math.abs(competingWindows.size() - leadWindows.size()) > 20) {
                    result.safetyNotes.add(competitor.path
                            + ": window count did not remain aligned; no automatic pocket was allowed.");
                    continue;
                }
                result.competingStemsAnalyzed++;
                result.competingStemPaths.add(competitor.path);
                inferStemEvents(leadWindows, competingWindows, active,
                        competitor, candidates, result);
            }
            selectSafeEvents(candidates, result, result.activeVocalWindows,
                    leadWindows.size());
        }

        result.available = result.competingStemsAnalyzed > 0;
        result.status = result.hasCorrections()
                ? (analysisOnly
                    ? "Analysis-only masking guidance is available for guarded stereo processing"
                    : "Validated stem-aware masking-pocket candidate evidence is available")
                : "Aligned stems were analysed; no local masking pocket cleared every safety gate";
        result.safetyNotes.add(analysisOnly
                ? "Only short frequency-specific mid/side attenuation on the authoritative stereo mix may be considered; no competing-stem waveform is rendered."
                : "Only short frequency-specific attenuation on securely identified competing instrument stems is eligible.");
        result.safetyNotes.add("The authoritative stereo mix remains the anchor; raw stems are never summed or substituted.");
        result.safetyNotes.add("The candidate must beat the stereo-only winner through rendered-file safety and score gates or it is discarded.");
        progress.update("AI masking pass complete", 100);
        return result;
    }

    private static List<StemInventory.Item> chooseCompetitors(AnalysisResult base,
                                                               StemInventory.Item lead,
                                                               boolean analysisOnly) {
        ArrayList<StemInventory.Item> candidates = new ArrayList<>();
        Map<StemInventory.Item, Integer> scores = new HashMap<>();
        for (StemInventory.Item item : base.stems) {
            if (item == lead || !validStem(item, base)) continue;
            if (base.stemAlignment == null
                    || (analysisOnly
                        ? !base.stemAlignment.analysisSecure(item.path)
                        : !base.stemAlignment.secure(item.path))) continue;
            double excess = item.wavInfo.durationSeconds - base.wav.durationSeconds;
            double shortfall = base.wav.durationSeconds - item.wavInfo.durationSeconds;
            if (excess > 1.01 / base.wav.sampleRate || shortfall > 2.0) continue;
            int score = roleScore(item.path);
            if (score < 40) continue;
            candidates.add(item);
            scores.put(item, score);
        }
        candidates.sort((a, b) -> {
            int score = Integer.compare(scores.get(b), scores.get(a));
            return score != 0 ? score : a.path.compareToIgnoreCase(b.path);
        });
        if (candidates.size() > MAX_COMPETING_STEMS) {
            candidates.subList(MAX_COMPETING_STEMS, candidates.size()).clear();
        }
        return candidates;
    }

    private static int roleScore(String path) {
        String name = path.toLowerCase(Locale.US);
        if (containsAny(name, "vocal", "vox", "voice", "harmony", "choir", "bgv", "bvox")) {
            return Integer.MIN_VALUE;
        }
        if (containsAny(name, "drum", "kick", "snare", "tom", "hihat", "hi-hat",
                "percussion", "cymbal", "bass", "sub", "click", "metronome")) {
            return Integer.MIN_VALUE;
        }
        int score = 0;
        if (containsAny(name, "guitar", "gtr")) score = Math.max(score, 100);
        if (containsAny(name, "piano")) score = Math.max(score, 96);
        if (containsAny(name, "keyboard", "keys", "organ")) score = Math.max(score, 92);
        if (containsAny(name, "synth", "pad")) score = Math.max(score, 88);
        if (containsAny(name, "string", "fiddle", "violin", "cello")) score = Math.max(score, 82);
        if (containsAny(name, "brass", "horn", "sax")) score = Math.max(score, 76);
        if (containsAny(name, "fx", "effect", "ambience", "crowd")) score -= 35;
        return score;
    }

    private static String role(String path) {
        String name = path.toLowerCase(Locale.US);
        if (containsAny(name, "guitar", "gtr")) return "guitar";
        if (name.contains("piano")) return "piano";
        if (containsAny(name, "keyboard", "keys", "organ")) return "keys";
        if (containsAny(name, "synth", "pad")) return "synth/pad";
        if (containsAny(name, "string", "fiddle", "violin", "cello")) return "strings";
        if (containsAny(name, "brass", "horn", "sax")) return "brass";
        return "securely named instrument";
    }

    private static void inferStemEvents(List<BandWindow> lead,
                                        List<BandWindow> competing,
                                        boolean[] active,
                                        StemInventory.Item stem,
                                        List<Event> output,
                                        Result result) {
        int count = Math.min(Math.min(lead.size(), competing.size()), active.length);
        BandStats[] stats = new BandStats[BAND_LABELS.length];
        for (int band = 0; band < stats.length; band++) {
            stats[band] = learnBand(lead, competing, active, count, band);
            if (stats[band].broadCoverage && result.safetyNotes.size() < 12) {
                result.safetyNotes.add(stem.path + " / " + BAND_LABELS[band]
                        + ": overlap was too widespread for a local automatic pocket.");
            }
        }

        int[] selectedBand = new int[count];
        double[] selectedZ = new double[count];
        double[] selectedRatio = new double[count];
        Arrays.fill(selectedBand, -1);
        for (int i = 0; i < count; i++) {
            if (!active[i]) continue;
            for (int band = 0; band < stats.length; band++) {
                BandStats stat = stats[band];
                if (!stat.enabled) continue;
                double leadLevel = lead.get(i).bandDb[band];
                double competingLevel = competing.get(i).bandDb[band];
                if (leadLevel < stat.leadFloorDb || competingLevel < stat.competingFloorDb
                        || leadLevel < -72.0 || competingLevel < -72.0) continue;
                double ratio = competingLevel - leadLevel;
                double z = (ratio - stat.medianRatioDb) / stat.robustScaleDb;
                if (ratio >= stat.triggerRatioDb && z > selectedZ[i]) {
                    selectedBand[i] = band;
                    selectedZ[i] = z;
                    selectedRatio[i] = ratio;
                }
            }
        }

        int start = 0;
        while (start < count) {
            while (start < count && selectedBand[start] < 0) start++;
            if (start >= count) break;
            int band = selectedBand[start];
            int end = start + 1;
            while (end < count && selectedBand[end] == band) end++;
            int cursor = start;
            int maximumWindows = Math.max(2,
                    (int) Math.round(MAX_EVENT_SECONDS / WINDOW_SECONDS));
            while (end - cursor >= 2) {
                int chunkEnd = Math.min(end, cursor + maximumWindows);
                addEvent(lead, selectedZ, selectedRatio, cursor, chunkEnd,
                        band, stats[band], stem, output);
                cursor = chunkEnd;
            }
            start = end;
        }
    }

    private static BandStats learnBand(List<BandWindow> lead,
                                       List<BandWindow> competing,
                                       boolean[] active, int count, int band) {
        BandStats out = new BandStats();
        ArrayList<Double> ratios = new ArrayList<>();
        ArrayList<Double> leadLevels = new ArrayList<>();
        ArrayList<Double> competingLevels = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (!active[i]) continue;
            double leadLevel = lead.get(i).bandDb[band];
            double competingLevel = competing.get(i).bandDb[band];
            if (leadLevel < -90.0 || competingLevel < -90.0) continue;
            ratios.add(competingLevel - leadLevel);
            leadLevels.add(leadLevel);
            competingLevels.add(competingLevel);
        }
        if (ratios.size() < 20) return out;
        out.leadFloorDb = percentile(leadLevels, 0.15);
        out.competingFloorDb = percentile(competingLevels, 0.15);
        out.medianRatioDb = percentile(ratios, 0.50);
        ArrayList<Double> deviations = new ArrayList<>();
        for (double value : ratios) deviations.add(Math.abs(value - out.medianRatioDb));
        out.robustScaleDb = Math.max(0.75, 1.4826 * percentile(deviations, 0.50));
        out.triggerRatioDb = out.medianRatioDb + ROBUST_TRIGGER_Z * out.robustScaleDb;
        int above = 0;
        int eligible = 0;
        for (int i = 0; i < count; i++) {
            if (!active[i]) continue;
            double leadLevel = lead.get(i).bandDb[band];
            double competingLevel = competing.get(i).bandDb[band];
            if (leadLevel < out.leadFloorDb || competingLevel < out.competingFloorDb
                    || leadLevel < -72.0 || competingLevel < -72.0) continue;
            eligible++;
            if (competingLevel - leadLevel >= out.triggerRatioDb) above++;
        }
        out.broadCoverage = above > Math.max(3,
                Math.round(eligible * MAX_BAND_COVERAGE_FRACTION));
        out.enabled = eligible >= 12 && above >= 2 && !out.broadCoverage;
        return out;
    }

    private static void addEvent(List<BandWindow> windows,
                                 double[] z, double[] ratio,
                                 int start, int end, int band,
                                 BandStats stat, StemInventory.Item stem,
                                 List<Event> output) {
        double maximumZ = 0.0;
        double maximumRatio = Double.NEGATIVE_INFINITY;
        for (int i = start; i < end; i++) {
            maximumZ = Math.max(maximumZ, z[i]);
            maximumRatio = Math.max(maximumRatio, ratio[i]);
        }
        double strength = clamp((maximumZ - ROBUST_TRIGGER_Z) / 4.0, 0.0, 1.0);
        double cut = MIN_CUT_DB + (MAX_CUT_DB - MIN_CUT_DB) * strength;
        Event event = new Event();
        event.stemPath = stem.path;
        event.stemRole = role(stem.path);
        event.bandIndex = band;
        event.bandLabel = BAND_LABELS[band];
        event.lowHz = BAND_LOW_HZ[band];
        event.highHz = BAND_HIGH_HZ[band];
        event.startSeconds = windows.get(start).startSeconds;
        event.endSeconds = windows.get(end - 1).startSeconds
                + windows.get(end - 1).durationSeconds;
        event.attenuationDb = -cut;
        event.confidence = clamp(0.68 + 0.07 * (maximumZ - ROBUST_TRIGGER_Z), 0.0, 0.97);
        event.maskingRatioDb = maximumRatio;
        event.learnedTriggerDb = stat.triggerRatioDb;
        event.evidence = String.format(Locale.US,
                "%s energy exceeded the learned local vocal/competitor ratio by %.2f robust units",
                BAND_LABELS[band], maximumZ - ROBUST_TRIGGER_Z);
        event.startWindow = start;
        event.endWindow = end;
        output.add(event);
    }

    private static void selectSafeEvents(List<Event> candidates, Result result,
                                         int activeWindows, int totalWindows) {
        candidates.sort((a, b) -> {
            int confidence = Double.compare(b.confidence, a.confidence);
            if (confidence != 0) return confidence;
            return Double.compare(a.startSeconds, b.startSeconds);
        });
        int[] simultaneous = new int[Math.max(1, totalWindows)];
        double[] combined = new double[Math.max(1, totalWindows)];
        int coverageBudget = Math.max(4,
                (int) Math.floor(activeWindows * MAX_TOTAL_COVERAGE_FRACTION));
        int usedCoverage = 0;
        for (Event event : candidates) {
            if (result.events.size() >= MAX_EVENTS) break;
            int eventWindows = event.endWindow - event.startWindow;
            if (eventWindows < 2 || usedCoverage + eventWindows > coverageBudget) continue;
            boolean safe = true;
            for (int i = event.startWindow; i < event.endWindow && i < simultaneous.length; i++) {
                if (simultaneous[i] >= 2
                        || combined[i] - event.attenuationDb > MAX_COMBINED_CUT_DB + 1.0e-9) {
                    safe = false;
                    break;
                }
            }
            if (!safe) continue;
            result.events.add(event);
            usedCoverage += eventWindows;
            for (int i = event.startWindow; i < event.endWindow && i < simultaneous.length; i++) {
                simultaneous[i]++;
                combined[i] -= event.attenuationDb;
                result.maximumCombinedNominalCutDb = Math.max(
                        result.maximumCombinedNominalCutDb, combined[i]);
            }
            result.maximumCutDb = Math.max(result.maximumCutDb, -event.attenuationDb);
            result.totalPocketSeconds += event.endSeconds - event.startSeconds;
        }
        result.events.sort(Comparator.comparingDouble(event -> event.startSeconds));
    }

    private static List<BandWindow> readWindows(ZipFile zip, StemInventory.Item item,
                                                 String role,
                                                 AudioAnalyzer.Progress progress) throws IOException {
        ZipEntry entry = zip.getEntry(item.path);
        if (entry == null || entry.isDirectory()) {
            throw new IOException("Stem entry disappeared during masking analysis: " + item.path);
        }
        if (item.crc32 >= 0 && entry.getCrc() >= 0 && item.crc32 != entry.getCrc()) {
            throw new IOException("Stem CRC changed after inventory: " + item.path);
        }
        try (InputStream input = zip.getInputStream(entry);
             WavFile.Reader reader = WavFile.open(input)) {
            int targetFrames = Math.max(256,
                    (int) Math.round(reader.info.sampleRate * WINDOW_SECONDS));
            BandBank bank = new BandBank(reader.info.sampleRate);
            float[][] samples = new float[reader.info.channels][BUFFER_FRAMES];
            ArrayList<BandWindow> windows = new ArrayList<>();
            WindowAccumulator accumulator = new WindowAccumulator();
            double[] bands = new double[BAND_LABELS.length];
            long absolute = 0;
            int read;
            while ((read = reader.readFrames(samples, BUFFER_FRAMES)) > 0) {
                for (int frame = 0; frame < read; frame++) {
                    double left = finite(samples[0][frame]);
                    double right = reader.info.channels == 2
                            ? finite(samples[1][frame]) : left;
                    double mid = 0.5 * (left + right);
                    bank.process(mid, bands);
                    accumulator.add(mid, bands);
                    absolute++;
                    if (accumulator.frames == targetFrames) {
                        windows.add(accumulator.finish(
                                (absolute - accumulator.frames) / (double) reader.info.sampleRate,
                                accumulator.frames / (double) reader.info.sampleRate));
                        accumulator = new WindowAccumulator();
                    }
                }
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("Masking analysis cancelled");
                }
            }
            if (accumulator.frames >= Math.max(128, targetFrames / 3)) {
                windows.add(accumulator.finish(
                        (absolute - accumulator.frames) / (double) reader.info.sampleRate,
                        accumulator.frames / (double) reader.info.sampleRate));
            }
            progress.update("AI masking pass: measured " + role, -1);
            return windows;
        }
    }

    private static boolean[] activeWindows(List<BandWindow> windows, AnalysisResult base) {
        boolean[] active = new boolean[windows.size()];
        double protectedEnd = Math.max(0.0, base.wav.durationSeconds
                - Math.max(8.0, base.wav.durationSeconds * 0.04));
        List<VocalHumanityModel.Phrase> phrases = base.vocalHumanity.phrases;
        for (int i = 0; i < windows.size(); i++) {
            BandWindow window = windows.get(i);
            double middle = window.startSeconds + 0.5 * window.durationSeconds;
            if (window.startSeconds - RENDER_ATTACK_SECONDS < 2.0
                    || window.startSeconds + window.durationSeconds
                    + RENDER_RELEASE_SECONDS > protectedEnd) continue;
            for (VocalHumanityModel.Phrase phrase : phrases) {
                if (middle >= phrase.startSeconds && middle <= phrase.endSeconds) {
                    active[i] = true;
                    break;
                }
            }
        }
        return active;
    }

    private static StemInventory.Item findStem(AnalysisResult base, String path) {
        for (StemInventory.Item item : base.stems) {
            if (item.path != null && item.path.equals(path)) return item;
        }
        return null;
    }

    private static boolean validStem(StemInventory.Item item, AnalysisResult base) {
        return item != null && item.wav && item.wavInfo != null && item.error == null
                && item.crcVerified && item.wavInfo.sampleRate == base.wav.sampleRate
                && (item.wavInfo.channels == 1 || item.wavInfo.channels == 2);
    }

    private static boolean alignmentAllows(AnalysisResult base, boolean analysisOnly) {
        StemReconstruction.Result reconstruction = base.stemReconstruction;
        return reconstruction != null && reconstruction.attempted && reconstruction.compatible
                && !reconstruction.globalPolarityRisk
                && (analysisOnly || !reconstruction.likelySeparationDerived)
                && (analysisOnly
                    || (Double.isFinite(reconstruction.startLagMs)
                        && Double.isFinite(reconstruction.middleLagMs)
                        && Double.isFinite(reconstruction.endLagMs)
                        && Double.isFinite(reconstruction.measuredDriftMs)
                        && Math.abs(reconstruction.startLagMs) <= 1.0
                        && Math.abs(reconstruction.middleLagMs) <= 1.0
                        && Math.abs(reconstruction.endLagMs) <= 1.0
                        && Math.abs(reconstruction.measuredDriftMs) <= 1.0))
                && base.stemAlignment != null
                && base.vocalHumanity != null
                && (analysisOnly
                    ? base.stemAlignment.analysisSecure(base.vocalHumanity.leadStemPath)
                    : base.stemAlignment.secure(base.vocalHumanity.leadStemPath));
    }

    private static boolean containsAny(String value, String... terms) {
        for (String term : terms) if (value.contains(term)) return true;
        return false;
    }

    private static double percentile(List<Double> input, double fraction) {
        if (input.isEmpty()) return Double.NaN;
        double[] values = new double[input.size()];
        for (int i = 0; i < values.length; i++) values[i] = input.get(i);
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

    private static final class BandStats {
        boolean enabled;
        boolean broadCoverage;
        double leadFloorDb;
        double competingFloorDb;
        double medianRatioDb;
        double robustScaleDb;
        double triggerRatioDb;
    }

    private static final class BandWindow {
        double startSeconds;
        double durationSeconds;
        double totalDb;
        final double[] bandDb = new double[BAND_LABELS.length];
    }

    private static final class WindowAccumulator {
        int frames;
        double totalEnergy;
        final double[] bandEnergy = new double[BAND_LABELS.length];

        void add(double total, double[] bands) {
            frames++;
            totalEnergy += total * total;
            for (int i = 0; i < bandEnergy.length; i++) {
                bandEnergy[i] += bands[i] * bands[i];
            }
        }

        BandWindow finish(double start, double duration) {
            BandWindow out = new BandWindow();
            out.startSeconds = start;
            out.durationSeconds = duration;
            out.totalDb = AudioAnalyzer.toDb(Math.sqrt(totalEnergy / Math.max(1, frames)));
            for (int i = 0; i < bandEnergy.length; i++) {
                out.bandDb[i] = AudioAnalyzer.toDb(
                        Math.sqrt(bandEnergy[i] / Math.max(1, frames)));
            }
            return out;
        }
    }

    static final class BandBank {
        private final Biquad[] highPassA = new Biquad[BAND_LABELS.length];
        private final Biquad[] highPassB = new Biquad[BAND_LABELS.length];
        private final Biquad[] lowPassA = new Biquad[BAND_LABELS.length];
        private final Biquad[] lowPassB = new Biquad[BAND_LABELS.length];

        BandBank(double sampleRate) {
            for (int i = 0; i < BAND_LABELS.length; i++) {
                double upper = Math.min(BAND_HIGH_HZ[i], sampleRate * 0.44);
                double lower = Math.min(BAND_LOW_HZ[i], upper * 0.70);
                highPassA[i] = Biquad.highPass(sampleRate, lower);
                highPassB[i] = Biquad.highPass(sampleRate, lower);
                lowPassA[i] = Biquad.lowPass(sampleRate, upper);
                lowPassB[i] = Biquad.lowPass(sampleRate, upper);
            }
        }

        void process(double sample, double[] output) {
            for (int i = 0; i < output.length; i++) {
                double filtered = highPassA[i].process(sample);
                filtered = highPassB[i].process(filtered);
                filtered = lowPassA[i].process(filtered);
                output[i] = lowPassB[i].process(filtered);
            }
        }
    }
}
