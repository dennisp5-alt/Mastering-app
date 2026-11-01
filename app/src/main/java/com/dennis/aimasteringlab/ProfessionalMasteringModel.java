package com.dennis.aimasteringlab;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;

/**
 * Song-learned, authoritative-stereo evidence maps for four professional
 * mastering controls introduced in v0.8.  The labels describe an inference,
 * never a claim that a vocal or instrument was separated from the mix.
 */
final class ProfessionalMasteringModel {
    enum Kind {
        DEPTH_SIDE_REVERB,
        DEPTH_HIGH_WIDTH,
        DEPTH_FLAT_DIAGNOSTIC,
        LF_PHASE_25_60,
        LF_PHASE_60_100,
        LF_PHASE_100_160,
        VOCAL_SIBILANCE,
        VOCAL_PLOSIVE,
        VOCAL_HARSH_VOWEL
    }

    static final class Event {
        Kind kind;
        double startSeconds;
        double endSeconds;
        double confidence;
        double primaryDb;
        boolean automatic;
        String evidence;
        boolean contextChecked,contextPassed;
        String contextReason;
        double trackedFrequencyHz,trackedQ;
        double[] trackingFrequency,trackingStrength;

        double durationSeconds() {
            return Math.max(0.0, endSeconds - startSeconds);
        }
    }

    static final class Result {
        String model = "DAML v0.12 musical-context and frequency-tracking intelligence";
        String method = "offline per-song 100 ms proposals; independent 25 ms musical-context vetoes and moving-frequency evidence; stereo inference, not isolated vocals";
        int windows;
        int activeWindows;
        double activeFloorDbfs;
        boolean limiterCrestAvailable;
        boolean flatDepthDiagnostic;
        final List<Event> events = new ArrayList<>();
        final List<String> safetyNotes = new ArrayList<>();

        int automaticCount(Kind... kinds) {
            int count = 0;
            for (Event event : events) {
                if (event.automatic && contains(event.kind, kinds)) count++;
            }
            return count;
        }

        boolean hasDepthReverb() {
            return automaticCount(Kind.DEPTH_SIDE_REVERB,
                    Kind.DEPTH_HIGH_WIDTH) > 0;
        }

        boolean hasLowPhase() {
            return automaticCount(Kind.LF_PHASE_25_60,
                    Kind.LF_PHASE_60_100, Kind.LF_PHASE_100_160) > 0;
        }

        boolean hasVocalArtifacts() {
            return automaticCount(Kind.VOCAL_SIBILANCE,
                    Kind.VOCAL_PLOSIVE, Kind.VOCAL_HARSH_VOWEL) > 0;
        }

        int availableModules() {
            return (hasDepthReverb() ? 1 : 0)
                    + (limiterCrestAvailable ? 1 : 0)
                    + (hasLowPhase() ? 1 : 0)
                    + (hasVocalArtifacts() ? 1 : 0);
        }

        String compactSummary() {
            return String.format(Locale.US,
                    "%d/%d active windows • Depth/Reverb %d%s • Limiter/Crest %s • LF Phase %d • Vocal Artifacts %d",
                    activeWindows, windows,
                    automaticCount(Kind.DEPTH_SIDE_REVERB,
                            Kind.DEPTH_HIGH_WIDTH),
                    flatDepthDiagnostic ? " + flat-depth diagnostic" : "",
                    limiterCrestAvailable ? "eligible" : "bypassed",
                    automaticCount(Kind.LF_PHASE_25_60,
                            Kind.LF_PHASE_60_100, Kind.LF_PHASE_100_160),
                    automaticCount(Kind.VOCAL_SIBILANCE,
                            Kind.VOCAL_PLOSIVE, Kind.VOCAL_HARSH_VOWEL));
        }
    }

    private static final int BUFFER_FRAMES = 4096;
    private static final double WINDOW_SECONDS = 0.100;
    private static final double MAX_DEPTH_COVERAGE = 0.18;
    private static final double MAX_PHASE_COVERAGE = 0.15;
    private static final double MAX_VOCAL_COVERAGE = 0.14;

    private ProfessionalMasteringModel() {}

    static Result unavailable(String reason, AnalysisResult base) {
        Result out = new Result();
        out.activeFloorDbfs = base == null ? -300.0 : base.rmsDbfs;
        out.safetyNotes.add(reason == null
                ? "v0.8 professional analysis was unavailable; earlier verified candidates remain active."
                : reason);
        return out;
    }

    static Result analyze(AudioAnalyzer.StreamProvider source,
                          AnalysisResult base,
                          AudioAnalyzer.Progress progress) throws IOException {
        if (base.wav.channels != 2 || base.wav.sampleRate != 48000) {
            throw new IOException("v0.8 professional intelligence requires a 48 kHz stereo authoritative mix");
        }
        progress.update("Professional intelligence: learning depth, phase and vocal-artifact evidence", 0);
        List<Window> windows = extract(source, base, progress);
        Result out = new Result();
        out.windows = windows.size();
        out.limiterCrestAvailable = LimiterCrestOptimizer.available(base);
        if (windows.size() < 30) {
            out.safetyNotes.add("The track was too short for reliable v0.8 event automation; limiter eligibility remains independently measured.");
            return out;
        }
        double[] levels = values(windows, Feature.LEVEL, null);
        out.activeFloorDbfs = Math.max(percentile(levels, 0.15),
                percentile(levels, 0.90) - 32.0);
        ArrayList<Integer> active = new ArrayList<>();
        for (int i = 0; i < windows.size(); i++) {
            if (windows.get(i).f[Feature.LEVEL] >= out.activeFloorDbfs) {
                active.add(i);
            }
        }
        out.activeWindows = active.size();
        if (active.size() < 24) {
            out.safetyNotes.add("Too few active windows cleared the learned reliability floor; event corrections remained diagnostic-only.");
            return out;
        }

        addDepthEvents(out, windows, active, base.wav.durationSeconds);
        addPhaseEvents(out, windows, active, base.wav.durationSeconds);
        addVocalArtifactEvents(out, windows, active,
                base.wav.durationSeconds);
        MusicalEventGuard.refine(source,base,out,progress);
        if(base.sourceGuard!=null && base.sourceGuard.secondPassGuard){
            out.limiterCrestAvailable=false;
            for(Event e:out.events)if(e.automatic && e.confidence<.97){
                e.automatic=false;e.evidence+="; finished-source gate requires confidence at least 0.97";
            }
        }
        out.events.sort(Comparator.comparingDouble(event -> event.startSeconds));
        if (out.availableModules() == 0) {
            out.safetyNotes.add("No v0.8 professional module found a correction that cleared confidence, duration and coverage gates.");
        }
        out.safetyNotes.add("Depth processing can only reduce event-specific masking or unsafe side information; this engine never widens the mix.");
        out.safetyNotes.add("Low-frequency phase rescue attenuates only the affected side-band component and never synthesizes bass.");
        out.safetyNotes.add("Vocal-artifact labels are cautious centre-channel inferences, not separated-vocal provenance claims.");
        out.safetyNotes.add("The first 2 seconds and final max(8 seconds, 4% of duration) are excluded from every automatic event.");
        progress.update("Professional intelligence: bounded correction maps ready", 100);
        return out;
    }

    private static List<Window> extract(AudioAnalyzer.StreamProvider source,
                                        AnalysisResult base,
                                        AudioAnalyzer.Progress progress) throws IOException {
        ArrayList<Window> out = new ArrayList<>();
        try (WavFile.Reader reader = WavFile.open(source.open())) {
            int rate = reader.info.sampleRate;
            int target = Math.max(1024,
                    (int) Math.round(rate * WINDOW_SECONDS));
            FilterBank bank = new FilterBank(rate);
            Accumulator accumulator = new Accumulator();
            float[][] samples = new float[2][BUFFER_FRAMES];
            long absolute = 0L;
            int read;
            while ((read = reader.readFrames(samples, BUFFER_FRAMES)) > 0) {
                for (int i = 0; i < read; i++, absolute++) {
                    accumulator.add(finite(samples[0][i]),
                            finite(samples[1][i]), bank);
                    if (accumulator.count == target) {
                        out.add(accumulator.finish(
                                (absolute + 1L - target) / (double) rate,
                                target / (double) rate));
                        accumulator = new Accumulator();
                    }
                }
                int percent = (int) Math.min(94, Math.round(94.0 * absolute
                        / Math.max(1L, reader.info.frames)));
                progress.update("Professional intelligence: extracting robust stereo evidence", percent);
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("Professional-intelligence analysis cancelled");
                }
            }
            if (accumulator.count >= Math.max(256, target / 3)) {
                out.add(accumulator.finish(
                        (absolute - accumulator.count) / (double) rate,
                        accumulator.count / (double) rate));
            }
        }
        return out;
    }

    private static void addDepthEvents(Result out, List<Window> windows,
                                       List<Integer> active,
                                       double duration) {
        double[] sideVocal = values(windows, Feature.SIDE_VOCAL_RATIO, active);
        double[] highSide = values(windows, Feature.HIGH_SIDE_RATIO, active);
        double[] correlation = values(windows, Feature.CORRELATION, active);
        double sideMedian = percentile(sideVocal, 0.50);
        double sideScale = robustScale(sideVocal, 0.65);
        double highMedian = percentile(highSide, 0.50);
        double highScale = robustScale(highSide, 0.65);
        double corrMedian = percentile(correlation, 0.50);
        double ending = protectedEndingStart(duration);
        ArrayList<Event> raw = new ArrayList<>();

        if (percentile(sideVocal, 0.90) - percentile(sideVocal, 0.10)
                < 0.90 && sideMedian > -17.0) {
            out.flatDepthDiagnostic = true;
            Event flat = new Event();
            flat.kind = Kind.DEPTH_FLAT_DIAGNOSTIC;
            flat.startSeconds = 2.0;
            flat.endSeconds = ending;
            flat.confidence = 0.70;
            flat.primaryDb = 0.0;
            flat.automatic = false;
            flat.evidence = String.format(Locale.US,
                    "side-depth spread was only %.3f dB; retained as diagnostic because correction would require automatic widening",
                    percentile(sideVocal, 0.90)
                            - percentile(sideVocal, 0.10));
            out.events.add(flat);
        }

        for (int index : active) {
            Window w = windows.get(index);
            if (!editable(w, ending)) continue;
            double sideZ = (w.f[Feature.SIDE_VOCAL_RATIO] - sideMedian)
                    / sideScale;
            double highZ = (w.f[Feature.HIGH_SIDE_RATIO] - highMedian)
                    / highScale;
            boolean vocalShape = w.f[Feature.VOCAL_SHARE] > -17.0
                    && w.f[Feature.CENTRE_VOCAL_DB] > -1.5
                    && w.f[Feature.DERIVATIVE_SHARE] < -4.5;
            if (sideZ >= 2.05 && vocalShape
                    && w.f[Feature.SIDE_VOCAL_RATIO] > -11.0) {
                double confidence = logistic((sideZ - 1.80) * 1.50);
                if (confidence >= 0.84) {
                    Event event = event(Kind.DEPTH_SIDE_REVERB, w,
                            confidence, -(0.20 + 0.36
                                    * strength(confidence, 0.84)),
                            String.format(Locale.US,
                                    "centre-compatible vocal-band energy carried a side excess %.2f robust units; local correlation %.3f",
                                    sideZ, w.f[Feature.CORRELATION]));
                    raw.add(event);
                }
            }
            if (highZ >= 2.20
                    && w.f[Feature.CORRELATION] < Math.min(0.72,
                    corrMedian - 0.03)
                    && w.f[Feature.HIGH_SIDE_RATIO] > -8.0) {
                double confidence = logistic((highZ - 1.95) * 1.55);
                if (confidence >= 0.85) {
                    Event event = event(Kind.DEPTH_HIGH_WIDTH, w,
                            confidence, -(0.18 + 0.37
                                    * strength(confidence, 0.85)),
                            String.format(Locale.US,
                                    "upper-band side energy was %.2f robust units above the song baseline with local correlation %.3f",
                                    highZ, w.f[Feature.CORRELATION]));
                    raw.add(event);
                }
            }
        }
        List<Event> merged = merge(raw, WINDOW_SECONDS * 1.10);
        for (Event event : merged) {
            event.automatic = event.durationSeconds() >= 0.19;
            if (!event.automatic) {
                event.evidence += "; diagnostic-only because evidence was too brief";
            }
        }
        applyCoverageGate(out, merged, duration, MAX_DEPTH_COVERAGE,
                "Depth/reverb evidence was too widespread for repair-style automation and remained diagnostic-only.");
    }

    private static void addPhaseEvents(Result out, List<Window> windows,
                                       List<Integer> active,
                                       double duration) {
        int[] correlations = {Feature.CORR_25_60, Feature.CORR_60_100,
                Feature.CORR_100_160};
        int[] shares = {Feature.SHARE_25_60, Feature.SHARE_60_100,
                Feature.SHARE_100_160};
        Kind[] kinds = {Kind.LF_PHASE_25_60, Kind.LF_PHASE_60_100,
                Kind.LF_PHASE_100_160};
        double ending = protectedEndingStart(duration);
        ArrayList<Event> raw = new ArrayList<>();
        for (int band = 0; band < kinds.length; band++) {
            double[] corr = values(windows, correlations[band], active);
            double[] share = values(windows, shares[band], active);
            double median = percentile(corr, 0.50);
            double scale = robustScale(corr, 0.045);
            double shareMedian = percentile(share, 0.50);
            double gate = Math.min(0.18, median - 1.80 * scale);
            for (int index : active) {
                Window w = windows.get(index);
                if (!editable(w, ending)
                        || w.f[shares[band]] < Math.max(-26.0,
                        shareMedian - 1.0)) continue;
                double local = w.f[correlations[band]];
                if (local >= gate) continue;
                double robustDeficit = (gate - local) / scale;
                double confidence = clamp(0.84 + 0.065
                        * robustDeficit + 0.08 * Math.max(0.0, -local),
                        0.0, 0.995);
                if (confidence < 0.86) continue;
                raw.add(event(kinds[band], w, confidence,
                        -(0.24 + 0.40 * strength(confidence, 0.86)),
                        String.format(Locale.US,
                                "%s Hz local L/R correlation %.3f fell %.2f robust units below the learned gate %.3f with usable band energy",
                                band == 0 ? "25–60" : band == 1
                                        ? "60–100" : "100–160",
                                local, robustDeficit, gate)));
            }
        }
        List<Event> merged = merge(raw, WINDOW_SECONDS * 1.10);
        for (Event event : merged) {
            event.automatic = event.durationSeconds() >= 0.19;
            if (!event.automatic) {
                event.evidence += "; diagnostic-only because phase evidence did not persist for two analysis windows";
            }
        }
        applyCoverageGate(out, merged, duration, MAX_PHASE_COVERAGE,
                "Low-frequency phase risk was too broad for local rescue; the mix was flagged for review instead.");
    }

    private static void addVocalArtifactEvents(Result out,
                                                List<Window> windows,
                                                List<Integer> active,
                                                double duration) {
        double[] sib = values(windows, Feature.SIBILANCE_RATIO, active);
        double[] plosive = values(windows, Feature.PLOSIVE_RATIO, active);
        double[] harsh = values(windows, Feature.HARSH_VOWEL_RATIO, active);
        double sibMedian = percentile(sib, 0.50);
        double sibScale = robustScale(sib, 0.55);
        double plosiveMedian = percentile(plosive, 0.50);
        double plosiveScale = robustScale(plosive, 0.65);
        double harshMedian = percentile(harsh, 0.50);
        double harshScale = robustScale(harsh, 0.55);
        double ending = protectedEndingStart(duration);
        ArrayList<Event> raw = new ArrayList<>();

        for (int index : active) {
            Window w = windows.get(index);
            if (!editable(w, ending)) continue;
            boolean centreEvidence = w.f[Feature.CENTRE_VOCAL_DB] > 2.0
                    && w.f[Feature.VOCAL_SHARE] > -15.5;
            if (!centreEvidence) continue;
            double sibZ = (w.f[Feature.SIBILANCE_RATIO] - sibMedian)
                    / sibScale;
            if (sibZ >= 2.15 && w.f[Feature.SIBILANCE_RATIO] > -8.5
                    && w.f[Feature.CREST] < 17.5) {
                double confidence = logistic((sibZ - 1.90) * 1.55);
                if (confidence >= 0.84) {
                    raw.add(event(Kind.VOCAL_SIBILANCE, w, confidence,
                            -(0.20 + 0.38 * strength(confidence, 0.84)),
                            String.format(Locale.US,
                                    "centred 6.5–10 kHz energy was %.2f robust units above the song baseline; broad HF attenuation was rejected",
                                    sibZ)));
                }
            }
            double plosiveZ = (w.f[Feature.PLOSIVE_RATIO] - plosiveMedian)
                    / plosiveScale;
            boolean ongoingVocalBand = w.f[Feature.VOCAL_SHARE] > -12.5;
            if (plosiveZ >= 2.25 && ongoingVocalBand
                    && w.f[Feature.CREST] < 16.0) {
                double confidence = logistic((plosiveZ - 2.00) * 1.45);
                if (confidence >= 0.84) {
                    raw.add(event(Kind.VOCAL_PLOSIVE, w, confidence,
                            -(0.18 + 0.37 * strength(confidence, 0.84)),
                            String.format(Locale.US,
                                    "centred 25–160 Hz burst was %.2f robust units above its vocal-band baseline; continuing mid-band evidence and a %.2f dB local crest rejected a high-crest isolated-kick inference",
                                    plosiveZ, w.f[Feature.CREST])));
                }
            }
            double harshZ = (w.f[Feature.HARSH_VOWEL_RATIO] - harshMedian)
                    / harshScale;
            if (harshZ >= 2.05
                    && w.f[Feature.HARSH_VOWEL_RATIO] > -2.5
                    && w.f[Feature.CREST] < 15.5) {
                double confidence = logistic((harshZ - 1.82) * 1.45);
                if (confidence >= 0.84) {
                    raw.add(event(Kind.VOCAL_HARSH_VOWEL, w,
                            confidence,
                            -(0.18 + 0.40 * strength(confidence, 0.84)),
                            String.format(Locale.US,
                                    "centred 1.6–3.2 kHz vowel-band energy was %.2f robust units above the learned body balance",
                                    harshZ)));
                }
            }
        }
        List<Event> merged = merge(raw, WINDOW_SECONDS * 1.08);
        for (Event event : merged) {
            double minimum = event.kind == Kind.VOCAL_PLOSIVE
                    ? 0.095 : 0.19;
            boolean plosiveDurationSafe = event.kind != Kind.VOCAL_PLOSIVE
                    || event.durationSeconds() <= 0.36;
            event.automatic = event.durationSeconds() >= minimum
                    && plosiveDurationSafe;
            if (!event.automatic) {
                event.evidence += plosiveDurationSafe
                        ? "; diagnostic-only because the centre evidence was too brief"
                        : "; diagnostic-only because the low-frequency event persisted too long for a cautious plosive inference";
            }
        }
        // Quality-first cleanup remains a local high-confidence inference.
        // A finished/limited source needs stronger evidence, not a blanket ban.
        for(Event e:merged){
            double required=e.kind==Kind.VOCAL_PLOSIVE?.96:.92;
            if(e.confidence<required || e.durationSeconds()>.85){
                e.automatic=false;e.evidence+="; quality-first confidence/duration gate";
            }
            if(e.automatic)e.primaryDb=Math.max(-1.20,e.primaryDb*1.9);
        }
        applyCoverageGate(out, merged, duration, MAX_VOCAL_COVERAGE,
                "Centre-channel artifact evidence was too widespread for event repair and remained diagnostic-only.");
    }

    private static boolean editable(Window window, double ending) {
        return window.start >= 2.0
                && window.start + window.duration <= ending;
    }

    private static Event event(Kind kind, Window window, double confidence,
                               double editDb, String evidence) {
        Event event = new Event();
        event.kind = kind;
        event.startSeconds = window.start;
        event.endSeconds = window.start + window.duration;
        event.confidence = confidence;
        event.primaryDb = Math.max(-0.65, Math.min(0.0, editDb));
        event.evidence = evidence;
        return event;
    }

    private static void applyCoverageGate(Result out, List<Event> events,
                                          double duration,
                                          double maximumFraction,
                                          String note) {
        EnumMap<Kind, Double> coverage = new EnumMap<>(Kind.class);
        for (Event event : events) {
            if (!event.automatic) continue;
            coverage.put(event.kind,
                    coverage.getOrDefault(event.kind, 0.0)
                            + event.durationSeconds());
        }
        boolean gated = false;
        for (Kind kind : coverage.keySet()) {
            if (coverage.get(kind) <= Math.max(1.0,
                    duration * maximumFraction)) continue;
            gated = true;
            for (Event event : events) {
                if (event.kind == kind) {
                    event.automatic = false;
                    event.evidence += "; coverage safety gate rejected broad automation";
                }
            }
        }
        if (gated) out.safetyNotes.add(note);
        out.events.addAll(events);
    }

    private static List<Event> merge(List<Event> input,
                                     double allowedGap) {
        input.sort((left, right) -> {
            int kind = left.kind.compareTo(right.kind);
            return kind != 0 ? kind
                    : Double.compare(left.startSeconds, right.startSeconds);
        });
        ArrayList<Event> out = new ArrayList<>();
        for (Event event : input) {
            Event prior = out.isEmpty() ? null : out.get(out.size() - 1);
            if (prior != null && prior.kind == event.kind
                    && event.startSeconds <= prior.endSeconds + allowedGap) {
                prior.endSeconds = Math.max(prior.endSeconds,
                        event.endSeconds);
                if (event.confidence > prior.confidence) {
                    prior.confidence = event.confidence;
                    prior.primaryDb = event.primaryDb;
                    prior.evidence = event.evidence;
                }
            } else {
                out.add(event);
            }
        }
        return out;
    }

    private static double[] values(List<Window> windows, int feature,
                                   List<Integer> selected) {
        double[] out = new double[selected == null
                ? windows.size() : selected.size()];
        for (int i = 0; i < out.length; i++) {
            int index = selected == null ? i : selected.get(i);
            out[i] = windows.get(index).f[feature];
        }
        return out;
    }

    private static double percentile(double[] input, double fraction) {
        if (input.length == 0) return 0.0;
        double[] copy = input.clone();
        java.util.Arrays.sort(copy);
        double at = clamp(fraction, 0.0, 1.0) * (copy.length - 1);
        int low = (int) Math.floor(at);
        int high = Math.min(copy.length - 1, low + 1);
        double mix = at - low;
        return copy[low] * (1.0 - mix) + copy[high] * mix;
    }

    private static double robustScale(double[] values, double minimum) {
        double scale = (percentile(values, 0.75)
                - percentile(values, 0.25)) / 1.349;
        return Math.max(minimum, scale);
    }

    private static double protectedEndingStart(double duration) {
        return Math.max(2.0,
                duration - Math.max(8.0, duration * 0.04));
    }

    private static double strength(double confidence, double floor) {
        return clamp((confidence - floor) / Math.max(0.001, 1.0 - floor),
                0.0, 1.0);
    }

    private static double logistic(double value) {
        return 1.0 / (1.0 + Math.exp(-value));
    }

    private static boolean contains(Kind value, Kind... choices) {
        for (Kind choice : choices) if (value == choice) return true;
        return false;
    }

    private static double clamp(double value, double minimum,
                                double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private static double db(double numerator, double denominator) {
        return 10.0 * Math.log10((numerator + 1.0e-20)
                / (denominator + 1.0e-20));
    }

    private static double correlation(double xy, double xx, double yy) {
        double denominator = Math.sqrt(Math.max(0.0, xx * yy));
        return denominator <= 1.0e-20 ? 1.0
                : clamp(xy / denominator, -1.0, 1.0);
    }

    private static final class Feature {
        static final int LEVEL = 0;
        static final int SIDE_VOCAL_RATIO = 1;
        static final int HIGH_SIDE_RATIO = 2;
        static final int CENTRE_VOCAL_DB = 3;
        static final int VOCAL_SHARE = 4;
        static final int SIBILANCE_RATIO = 5;
        static final int PLOSIVE_RATIO = 6;
        static final int HARSH_VOWEL_RATIO = 7;
        static final int DERIVATIVE_SHARE = 8;
        static final int CREST = 9;
        static final int CORRELATION = 10;
        static final int CORR_25_60 = 11;
        static final int CORR_60_100 = 12;
        static final int CORR_100_160 = 13;
        static final int SHARE_25_60 = 14;
        static final int SHARE_60_100 = 15;
        static final int SHARE_100_160 = 16;
        static final int COUNT = 17;
    }

    private static final class Window {
        double start;
        double duration;
        final double[] f = new double[Feature.COUNT];
    }

    private static final class FilterBank {
        final Biquad mid160, mid700, mid1600, mid3200, mid6500, mid10000;
        final Biquad side700, side6500, side10000;
        final Biquad left25, left60, left100, left160;
        final Biquad right25, right60, right100, right160;

        FilterBank(double rate) {
            mid160 = Biquad.lowPass(rate, 160.0);
            mid700 = Biquad.lowPass(rate, 700.0);
            mid1600 = Biquad.lowPass(rate, 1600.0);
            mid3200 = Biquad.lowPass(rate, 3200.0);
            mid6500 = Biquad.lowPass(rate, 6500.0);
            mid10000 = Biquad.lowPass(rate, 10000.0);
            side700 = Biquad.lowPass(rate, 700.0);
            side6500 = Biquad.lowPass(rate, 6500.0);
            side10000 = Biquad.lowPass(rate, 10000.0);
            left25 = Biquad.lowPass(rate, 25.0);
            left60 = Biquad.lowPass(rate, 60.0);
            left100 = Biquad.lowPass(rate, 100.0);
            left160 = Biquad.lowPass(rate, 160.0);
            right25 = Biquad.lowPass(rate, 25.0);
            right60 = Biquad.lowPass(rate, 60.0);
            right100 = Biquad.lowPass(rate, 100.0);
            right160 = Biquad.lowPass(rate, 160.0);
        }
    }

    private static final class Accumulator {
        long count;
        double total, midTotal, sideTotal;
        double vocal, sideVocal, highMid, highSide;
        double sib, plosive, lowerVocal, upperVocal;
        double derivative;
        double peak;
        double priorMid;
        boolean havePrior;
        double lr, ll, rr;
        final double[] lowCross = new double[3];
        final double[] lowLeft = new double[3];
        final double[] lowRight = new double[3];
        final double[] lowEnergy = new double[3];

        void add(double left, double right, FilterBank bank) {
            double mid = 0.5 * (left + right);
            double side = 0.5 * (left - right);
            double m160 = bank.mid160.process(mid);
            double m700 = bank.mid700.process(mid);
            double m1600 = bank.mid1600.process(mid);
            double m3200 = bank.mid3200.process(mid);
            double m6500 = bank.mid6500.process(mid);
            double m10000 = bank.mid10000.process(mid);
            double s700 = bank.side700.process(side);
            double s6500 = bank.side6500.process(side);
            double s10000 = bank.side10000.process(side);
            double body = m700 - m160;
            double lower = m1600 - m700;
            double upper = m3200 - m1600;
            double clarity = m6500 - m3200;
            double sVocal = s6500 - s700;
            double mHigh = m10000 - m6500;
            double sHigh = s10000 - s6500;
            double[] l = lowBands(left, bank.left25.process(left),
                    bank.left60.process(left), bank.left100.process(left),
                    bank.left160.process(left));
            double[] r = lowBands(right, bank.right25.process(right),
                    bank.right60.process(right), bank.right100.process(right),
                    bank.right160.process(right));

            count++;
            double power = left * left + right * right;
            total += power;
            midTotal += mid * mid;
            sideTotal += side * side;
            vocal += body * body + lower * lower + upper * upper
                    + clarity * clarity;
            sideVocal += sVocal * sVocal;
            highMid += mHigh * mHigh;
            highSide += sHigh * sHigh;
            sib += mHigh * mHigh;
            plosive += m160 * m160;
            lowerVocal += body * body + lower * lower;
            upperVocal += upper * upper;
            if (havePrior) {
                double change = mid - priorMid;
                derivative += change * change;
            }
            priorMid = mid;
            havePrior = true;
            peak = Math.max(peak, Math.max(Math.abs(left), Math.abs(right)));
            lr += left * right;
            ll += left * left;
            rr += right * right;
            for (int i = 0; i < 3; i++) {
                lowCross[i] += l[i] * r[i];
                lowLeft[i] += l[i] * l[i];
                lowRight[i] += r[i] * r[i];
                lowEnergy[i] += l[i] * l[i] + r[i] * r[i];
            }
        }

        Window finish(double start, double duration) {
            Window out = new Window();
            out.start = start;
            out.duration = duration;
            out.f[Feature.LEVEL] = 10.0 * Math.log10(
                    total / Math.max(1L, count * 2L) + 1.0e-20);
            out.f[Feature.SIDE_VOCAL_RATIO] = db(sideVocal, vocal);
            out.f[Feature.HIGH_SIDE_RATIO] = db(highSide, highMid);
            out.f[Feature.CENTRE_VOCAL_DB] = db(vocal, sideVocal);
            out.f[Feature.VOCAL_SHARE] = db(vocal, midTotal);
            out.f[Feature.SIBILANCE_RATIO] = db(sib,
                    lowerVocal + upperVocal);
            out.f[Feature.PLOSIVE_RATIO] = db(plosive,
                    lowerVocal + upperVocal);
            out.f[Feature.HARSH_VOWEL_RATIO] = db(upperVocal,
                    lowerVocal);
            out.f[Feature.DERIVATIVE_SHARE] = db(derivative, midTotal);
            double rms = Math.sqrt(total / Math.max(1L, count * 2L));
            out.f[Feature.CREST] = 20.0 * Math.log10(
                    (peak + 1.0e-20) / (rms + 1.0e-20));
            out.f[Feature.CORRELATION] = correlation(lr, ll, rr);
            out.f[Feature.CORR_25_60] = correlation(lowCross[0],
                    lowLeft[0], lowRight[0]);
            out.f[Feature.CORR_60_100] = correlation(lowCross[1],
                    lowLeft[1], lowRight[1]);
            out.f[Feature.CORR_100_160] = correlation(lowCross[2],
                    lowLeft[2], lowRight[2]);
            out.f[Feature.SHARE_25_60] = db(lowEnergy[0], total);
            out.f[Feature.SHARE_60_100] = db(lowEnergy[1], total);
            out.f[Feature.SHARE_100_160] = db(lowEnergy[2], total);
            return out;
        }

        private static double[] lowBands(double input, double at25,
                                         double at60, double at100,
                                         double at160) {
            return new double[]{at60 - at25, at100 - at60,
                    at160 - at100};
        }
    }
}
