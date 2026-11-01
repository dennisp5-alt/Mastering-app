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
 * Learns three conservative correction maps directly from the authoritative
 * stereo mix. No separated waveform is required or used.
 */
final class StereoIntelligenceModel {
    enum Kind {
        SPECTRAL_HF,
        SPECTRAL_HARSH,
        SPECTRAL_MUD,
        LOW_SUB_BLOOM,
        LOW_BASS_BLOOM,
        LOW_PHASE_RISK,
        VOCAL_FOCUS
    }

    static final class Event {
        Kind kind;
        double startSeconds;
        double endSeconds;
        double confidence;
        double primaryDb;
        double secondaryDb;
        boolean automatic;
        String evidence;

        double durationSeconds() {
            return Math.max(0.0, endSeconds - startSeconds);
        }
    }

    static final class Result {
        String model = "DAML v0.5 authoritative-stereo micro-repair intelligence";
        String method = "offline per-song robust 125 ms feature learning; spectral event, transient-preserving low-end and centre-evidence vocal-focus inference";
        int windows;
        int activeWindows;
        double activeFloorDbfs;
        int kickLikeWindowsProtected;
        final List<Event> events = new ArrayList<>();
        final List<String> safetyNotes = new ArrayList<>();

        int automaticCount(Kind... kinds) {
            int count = 0;
            for (Event event : events) {
                if (event.automatic && contains(event.kind, kinds)) count++;
            }
            return count;
        }

        boolean hasEventRepair() {
            return automaticCount(Kind.SPECTRAL_HF, Kind.SPECTRAL_HARSH,
                    Kind.SPECTRAL_MUD) > 0;
        }

        boolean hasLowEnd() {
            return automaticCount(Kind.LOW_SUB_BLOOM, Kind.LOW_BASS_BLOOM,
                    Kind.LOW_PHASE_RISK) > 0;
        }

        boolean hasVocalFocus() {
            return automaticCount(Kind.VOCAL_FOCUS) > 0;
        }

        int availableModules() {
            return (hasEventRepair() ? 1 : 0) + (hasLowEnd() ? 1 : 0)
                    + (hasVocalFocus() ? 1 : 0);
        }

        double maximumEditDb(Kind... kinds) {
            double maximum = 0.0;
            for (Event event : events) {
                if (!event.automatic || !contains(event.kind, kinds)) continue;
                maximum = Math.max(maximum, Math.abs(event.primaryDb));
                maximum = Math.max(maximum, Math.abs(event.secondaryDb));
            }
            return maximum;
        }

        String compactSummary() {
            return String.format(Locale.US,
                    "%d/%d active windows • Event Repair %d • Low-End %d • Vocal Focus %d • %d kick-like windows protected",
                    activeWindows, windows,
                    automaticCount(Kind.SPECTRAL_HF, Kind.SPECTRAL_HARSH,
                            Kind.SPECTRAL_MUD),
                    automaticCount(Kind.LOW_SUB_BLOOM, Kind.LOW_BASS_BLOOM,
                            Kind.LOW_PHASE_RISK),
                    automaticCount(Kind.VOCAL_FOCUS), kickLikeWindowsProtected);
        }
    }

    private static final int BUFFER_FRAMES = 4096;
    private static final double WINDOW_SECONDS = 0.125;
    private static final double MIN_LOW_CONFIDENCE = 0.86;
    private static final double MIN_VOCAL_CONFIDENCE = 0.84;
    private static final double MAX_LOW_COVERAGE = 0.16;
    private static final double MAX_VOCAL_COVERAGE = 0.38;

    private StereoIntelligenceModel() {}

    static Result unavailable(String reason, AnalysisResult base) {
        Result out = new Result();
        out.activeFloorDbfs = base == null ? 0.0 : base.rmsDbfs;
        out.safetyNotes.add(reason == null
                ? "Stereo-intelligence analysis was unavailable; core stereo mastering remains active."
                : reason);
        out.safetyNotes.add("No stereo-intelligence candidate was rendered; this does not disable the verified core stereo fallback.");
        return out;
    }

    static Result analyze(AudioAnalyzer.StreamProvider source,
                          AnalysisResult base,
                          AudioAnalyzer.Progress progress) throws IOException {
        if (base.wav.channels != 2 || base.wav.sampleRate != 48000) {
            throw new IOException("Stereo intelligence requires a 48 kHz stereo authoritative mix");
        }
        progress.update("Stereo intelligence: learning event, low-end and vocal evidence", 0);
        List<Window> windows = extract(source, base, progress);
        Result out = new Result();
        out.windows = windows.size();
        if (windows.size() < 24) {
            out.safetyNotes.add("The track is too short for reliable stereo-intelligence correction; diagnostics only.");
            return out;
        }

        double[] levels = values(windows, Feature.LEVEL, null);
        double p15 = percentile(levels, 0.15);
        double p90 = percentile(levels, 0.90);
        out.activeFloorDbfs = Math.max(p15, p90 - 32.0);
        ArrayList<Integer> active = new ArrayList<>();
        for (int i = 0; i < windows.size(); i++) {
            if (windows.get(i).feature[Feature.LEVEL] >= out.activeFloorDbfs) {
                active.add(i);
            }
        }
        out.activeWindows = active.size();
        if (active.size() < 20) {
            out.safetyNotes.add("Too few active windows cleared the reliability floor; diagnostics only.");
            return out;
        }

        addSpectralEvents(out, base);
        addLowEndEvents(out, windows, active, base.wav.durationSeconds);
        addVocalEvents(out, windows, active, base.wav.durationSeconds);
        out.events.sort(Comparator.comparingDouble(event -> event.startSeconds));
        if (out.availableModules() == 0) {
            out.safetyNotes.add("No stereo-only module found a correction that cleared every confidence and coverage gate.");
        }
        out.safetyNotes.add("All inference is derived from the authoritative stereo WAV; no stem or generated waveform is used.");
        out.safetyNotes.add("Kick-like low transients are explicitly protected; broad low-end or vocal conditions remain diagnostic-only.");
        out.safetyNotes.add("Automatic processing is bounded, section-relative and disabled in the protected intro and ending.");
        progress.update("Stereo intelligence: candidate maps ready", 100);
        return out;
    }

    private static List<Window> extract(AudioAnalyzer.StreamProvider source,
                                        AnalysisResult base,
                                        AudioAnalyzer.Progress progress) throws IOException {
        ArrayList<Window> out = new ArrayList<>();
        try (WavFile.Reader reader = WavFile.open(source.open())) {
            int rate = reader.info.sampleRate;
            int target = Math.max(1024, (int) Math.round(rate * WINDOW_SECONDS));
            FilterBank bank = new FilterBank(rate);
            Accumulator accumulator = new Accumulator();
            float[][] samples = new float[2][BUFFER_FRAMES];
            long absolute = 0L;
            int read;
            while ((read = reader.readFrames(samples, BUFFER_FRAMES)) > 0) {
                for (int i = 0; i < read; i++, absolute++) {
                    accumulator.add(finite(samples[0][i]), finite(samples[1][i]), bank);
                    if (accumulator.count == target) {
                        out.add(accumulator.finish(
                                (absolute + 1L - target) / (double) rate,
                                target / (double) rate));
                        accumulator = new Accumulator();
                    }
                }
                int percent = (int) Math.min(94,
                        Math.round(94.0 * absolute / Math.max(1L, reader.info.frames)));
                progress.update("Stereo intelligence: learning authoritative stereo features", percent);
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("Stereo-intelligence analysis cancelled");
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

    private static void addSpectralEvents(Result out, AnalysisResult base) {
        AdaptiveDiagnosticModel.Result diagnostic = base.aiDiagnostics;
        if (diagnostic == null) return;
        for (AdaptiveDiagnosticModel.Event source : diagnostic.events) {
            if (!source.autoCorrection) continue;
            Kind kind;
            if (source.type == AdaptiveDiagnosticModel.Type.HIGH_FREQUENCY_EMPHASIS) {
                kind = Kind.SPECTRAL_HF;
            } else if (source.type == AdaptiveDiagnosticModel.Type.UPPER_MID_HARSHNESS) {
                kind = Kind.SPECTRAL_HARSH;
            } else if (source.type == AdaptiveDiagnosticModel.Type.LOW_MID_MASKING) {
                kind = Kind.SPECTRAL_MUD;
            } else {
                continue;
            }
            Event event = new Event();
            event.kind = kind;
            event.startSeconds = source.startSeconds;
            event.endSeconds = source.endSeconds;
            event.confidence = source.confidence;
            event.primaryDb = Math.max(-0.60, Math.min(0.0, source.correctionDb));
            event.automatic = true;
            event.evidence = source.evidence + "; v0.5 event-repair cap applied";
            out.events.add(event);
        }
    }

    private static void addLowEndEvents(Result out, List<Window> windows,
                                        List<Integer> active,
                                        double duration) {
        double[] low = values(windows, Feature.LOW_SHARE, active);
        double[] sub = values(windows, Feature.SUB_WITHIN_LOW, active);
        double[] crest = values(windows, Feature.LOW_CREST, active);
        double lowMedian = percentile(low, 0.50);
        double lowScale = robustScale(low, 0.55);
        double subMedian = percentile(sub, 0.50);
        double subScale = robustScale(sub, 0.45);
        double crestP75 = percentile(crest, 0.75);
        ArrayList<Event> raw = new ArrayList<>();
        double endingStart = protectedEndingStart(duration);

        for (int index : active) {
            Window window = windows.get(index);
            if (window.start < 2.0 || window.start + window.duration > endingStart) continue;
            double lowZ = (window.feature[Feature.LOW_SHARE] - lowMedian) / lowScale;
            double subZ = (window.feature[Feature.SUB_WITHIN_LOW] - subMedian) / subScale;
            boolean kickLike = window.feature[Feature.LOW_CREST]
                    >= Math.max(9.5, crestP75 + 0.8)
                    || window.feature[Feature.DERIVATIVE_SHARE] > -8.0;
            if (lowZ >= 2.15 && window.feature[Feature.LOW_SHARE] > -7.0) {
                if (kickLike) {
                    out.kickLikeWindowsProtected++;
                } else {
                    double confidence = logistic((lowZ - 2.0) * 1.25);
                    if (confidence >= MIN_LOW_CONFIDENCE) {
                        Event event = new Event();
                        event.kind = subZ > 0.85
                                ? Kind.LOW_SUB_BLOOM : Kind.LOW_BASS_BLOOM;
                        event.startSeconds = window.start;
                        event.endSeconds = window.start + window.duration;
                        event.confidence = confidence;
                        double strength = clamp((confidence - MIN_LOW_CONFIDENCE)
                                / (1.0 - MIN_LOW_CONFIDENCE), 0.0, 1.0);
                        event.primaryDb = event.kind == Kind.LOW_SUB_BLOOM
                                ? -(0.18 + 0.32 * strength)
                                : -(0.16 + 0.29 * strength);
                        event.evidence = String.format(Locale.US,
                                "sustained low-band outlier %.2f robust units; crest %.2f dB did not indicate a kick transient",
                                lowZ, window.feature[Feature.LOW_CREST]);
                        raw.add(event);
                    }
                }
            }

            double correlation = window.feature[Feature.LOW_CORRELATION];
            if (correlation < 0.06
                    && window.feature[Feature.LOW_SHARE] > lowMedian - lowScale) {
                double confidence = clamp(0.84 + (0.06 - correlation) * 0.30,
                        0.0, 0.995);
                if (confidence >= 0.88) {
                    Event phase = new Event();
                    phase.kind = Kind.LOW_PHASE_RISK;
                    phase.startSeconds = window.start;
                    phase.endSeconds = window.start + window.duration;
                    phase.confidence = confidence;
                    phase.primaryDb = -0.60;
                    phase.evidence = String.format(Locale.US,
                            "below-160 Hz local correlation %.3f with material low-band energy",
                            correlation);
                    raw.add(phase);
                }
            }
        }

        List<Event> merged = merge(raw, WINDOW_SECONDS * 1.05);
        for (Event event : merged) {
            if ((event.kind == Kind.LOW_SUB_BLOOM
                    || event.kind == Kind.LOW_BASS_BLOOM)
                    && event.durationSeconds() < 0.245) {
                event.automatic = false;
                event.evidence += "; kept diagnostic-only because the event was too brief to distinguish safely from kick impact";
            } else {
                event.automatic = true;
            }
        }
        applyCoverageGate(out, merged, duration, MAX_LOW_COVERAGE,
                "Low-end evidence was too widespread for safe event automation; retained for diagnostics only.");
    }

    private static void addVocalEvents(Result out, List<Window> windows,
                                       List<Integer> active,
                                       double duration) {
        double[] dominance = values(windows, Feature.VOCAL_DOMINANCE, active);
        double[] share = values(windows, Feature.VOCAL_SHARE, active);
        double[] balance = values(windows, Feature.PRESENCE_BODY, active);
        double dominanceMedian = percentile(dominance, 0.50);
        double dominanceScale = robustScale(dominance, 0.80);
        double shareMedian = percentile(share, 0.50);
        double shareScale = robustScale(share, 0.75);
        double balanceMedian = percentile(balance, 0.50);
        double balanceScale = robustScale(balance, 0.65);
        double endingStart = protectedEndingStart(duration);
        ArrayList<Event> raw = new ArrayList<>();

        for (int index : active) {
            Window window = windows.get(index);
            if (window.start < 2.0 || window.start + window.duration > endingStart) continue;
            double centre = window.feature[Feature.VOCAL_DOMINANCE];
            double vocalShare = window.feature[Feature.VOCAL_SHARE];
            double presenceBody = window.feature[Feature.PRESENCE_BODY];
            double centreZ = (centre - dominanceMedian) / dominanceScale;
            double shareZ = (vocalShare - shareMedian) / shareScale;
            double deficitZ = (balanceMedian - presenceBody) / balanceScale;
            boolean spectralShape = presenceBody > -12.5 && presenceBody < 7.5
                    && window.feature[Feature.HIGH_SHARE] < -5.0;
            boolean nonTransient = window.feature[Feature.MID_CREST] < 14.5
                    && window.feature[Feature.DERIVATIVE_SHARE] < -6.5;
            boolean centreEvidence = centre >= Math.max(2.0,
                    dominanceMedian + 0.20 * dominanceScale)
                    && centre <= 11.5;
            boolean enoughVocalBand = vocalShare > Math.max(-13.0,
                    shareMedian - 1.10 * shareScale) && shareZ > -1.35;
            if (!spectralShape || !nonTransient || !centreEvidence || !enoughVocalBand) {
                continue;
            }
            double need = Math.max(0.0, (7.5 - centre) / 5.5)
                    + 0.30 * Math.max(0.0, deficitZ)
                    + 0.12 * Math.max(0.0, centreZ);
            double confidence = logistic((need - 0.25) * 2.4 + 1.55);
            if (confidence < MIN_VOCAL_CONFIDENCE) continue;
            Event event = new Event();
            event.kind = Kind.VOCAL_FOCUS;
            event.startSeconds = window.start;
            event.endSeconds = window.start + window.duration;
            event.confidence = confidence;
            double strength = clamp((confidence - MIN_VOCAL_CONFIDENCE)
                    / (1.0 - MIN_VOCAL_CONFIDENCE), 0.0, 1.0);
            event.primaryDb = 0.14 + 0.16 * strength;
            event.secondaryDb = -(0.08 + 0.12 * strength);
            event.evidence = String.format(Locale.US,
                    "centre vocal-band evidence %.2f dB; vocal share %.2f dB; transient rejection passed",
                    centre, vocalShare);
            raw.add(event);
        }

        List<Event> merged = merge(raw, WINDOW_SECONDS * 1.05);
        ArrayList<Event> bounded = new ArrayList<>();
        for (Event event : merged) {
            if (event.durationSeconds() < 0.34) {
                event.automatic = false;
                event.evidence += "; diagnostic-only because centre evidence was too brief";
                bounded.add(event);
                continue;
            }
            splitAt(bounded, event, 2.25);
        }
        for (Event event : bounded) {
            if (event.durationSeconds() >= 0.34) event.automatic = true;
        }
        applyCoverageGate(out, bounded, duration, MAX_VOCAL_COVERAGE,
                "Centre-channel vocal evidence was too widespread for safe automatic focus; retained for diagnostics only.");
    }

    private static void applyCoverageGate(Result out, List<Event> events,
                                          double duration, double maximumFraction,
                                          String note) {
        EnumMap<Kind, Double> coverage = new EnumMap<>(Kind.class);
        for (Event event : events) {
            if (!event.automatic) continue;
            coverage.put(event.kind, coverage.getOrDefault(event.kind, 0.0)
                    + event.durationSeconds());
        }
        boolean gated = false;
        for (Kind kind : coverage.keySet()) {
            if (coverage.get(kind) <= Math.max(1.0, duration * maximumFraction)) continue;
            gated = true;
            for (Event event : events) {
                if (event.kind == kind) event.automatic = false;
            }
        }
        if (gated) out.safetyNotes.add(note);
        out.events.addAll(events);
    }

    private static List<Event> merge(List<Event> input, double allowedGap) {
        input.sort((a, b) -> {
            int kind = a.kind.compareTo(b.kind);
            return kind != 0 ? kind : Double.compare(a.startSeconds, b.startSeconds);
        });
        ArrayList<Event> merged = new ArrayList<>();
        for (Event event : input) {
            Event prior = merged.isEmpty() ? null : merged.get(merged.size() - 1);
            if (prior != null && prior.kind == event.kind
                    && event.startSeconds <= prior.endSeconds + allowedGap) {
                prior.endSeconds = Math.max(prior.endSeconds, event.endSeconds);
                if (event.confidence > prior.confidence) {
                    prior.confidence = event.confidence;
                    prior.primaryDb = event.primaryDb;
                    prior.secondaryDb = event.secondaryDb;
                    prior.evidence = event.evidence;
                }
            } else {
                merged.add(event);
            }
        }
        merged.sort(Comparator.comparingDouble(event -> event.startSeconds));
        return merged;
    }

    private static void splitAt(List<Event> output, Event source, double maximum) {
        double cursor = source.startSeconds;
        while (cursor < source.endSeconds - 1.0e-6) {
            Event event = new Event();
            event.kind = source.kind;
            event.startSeconds = cursor;
            event.endSeconds = Math.min(source.endSeconds, cursor + maximum);
            event.confidence = source.confidence;
            event.primaryDb = source.primaryDb;
            event.secondaryDb = source.secondaryDb;
            event.evidence = source.evidence;
            output.add(event);
            cursor = event.endSeconds;
        }
    }

    static boolean kindInMode(Kind kind, StereoIntelligenceRenderer.Mode mode) {
        if (mode == StereoIntelligenceRenderer.Mode.COMBINED) return true;
        if (mode == StereoIntelligenceRenderer.Mode.EVENT_REPAIR) {
            return kind == Kind.SPECTRAL_HF || kind == Kind.SPECTRAL_HARSH
                    || kind == Kind.SPECTRAL_MUD;
        }
        if (mode == StereoIntelligenceRenderer.Mode.LOW_END) {
            return kind == Kind.LOW_SUB_BLOOM || kind == Kind.LOW_BASS_BLOOM
                    || kind == Kind.LOW_PHASE_RISK;
        }
        return kind == Kind.VOCAL_FOCUS;
    }

    private static double protectedEndingStart(double duration) {
        return Math.max(2.0, duration - Math.max(8.0, duration * 0.04));
    }

    private static boolean contains(Kind value, Kind[] values) {
        for (Kind candidate : values) if (candidate == value) return true;
        return false;
    }

    private static double[] values(List<Window> windows, int feature,
                                   List<Integer> indices) {
        double[] out = new double[indices == null ? windows.size() : indices.size()];
        if (indices == null) {
            for (int i = 0; i < windows.size(); i++) out[i] = windows.get(i).feature[feature];
        } else {
            for (int i = 0; i < indices.size(); i++) {
                out[i] = windows.get(indices.get(i)).feature[feature];
            }
        }
        return out;
    }

    private static double percentile(double[] input, double fraction) {
        if (input.length == 0) return 0.0;
        double[] sorted = input.clone();
        Arrays.sort(sorted);
        double position = clamp(fraction, 0.0, 1.0) * (sorted.length - 1);
        int lower = (int) Math.floor(position);
        int upper = Math.min(sorted.length - 1, lower + 1);
        double blend = position - lower;
        return sorted[lower] * (1.0 - blend) + sorted[upper] * blend;
    }

    private static double robustScale(double[] input, double minimum) {
        if (input.length == 0) return minimum;
        double median = percentile(input, 0.50);
        double[] deviations = new double[input.length];
        for (int i = 0; i < input.length; i++) {
            deviations[i] = Math.abs(input[i] - median);
        }
        return Math.max(minimum, 1.4826 * percentile(deviations, 0.50));
    }

    private static double logistic(double value) {
        return 1.0 / (1.0 + Math.exp(-value));
    }

    private static double db(double numerator, double denominator) {
        return 10.0 * Math.log10((numerator + 1.0e-20)
                / (denominator + 1.0e-20));
    }

    private static double rmsDb(double squares, long count) {
        return count <= 0 ? -300.0
                : 10.0 * Math.log10(squares / count + 1.0e-20);
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static final class Feature {
        static final int LEVEL = 0;
        static final int LOW_SHARE = 1;
        static final int SUB_WITHIN_LOW = 2;
        static final int LOW_CREST = 3;
        static final int LOW_CORRELATION = 4;
        static final int VOCAL_SHARE = 5;
        static final int VOCAL_DOMINANCE = 6;
        static final int PRESENCE_BODY = 7;
        static final int HIGH_SHARE = 8;
        static final int MID_CREST = 9;
        static final int DERIVATIVE_SHARE = 10;
        static final int COUNT = 11;
    }

    private static final class Window {
        double start;
        double duration;
        final double[] feature = new double[Feature.COUNT];
    }

    private static final class FilterBank {
        final Biquad mid80;
        final Biquad mid160;
        final Biquad mid250;
        final Biquad mid700;
        final Biquad mid3200;
        final Biquad mid6500;
        final Biquad side250;
        final Biquad side700;
        final Biquad side3200;
        final Biquad side6500;
        final Biquad left160;
        final Biquad right160;

        FilterBank(double rate) {
            mid80 = Biquad.lowPass(rate, 80.0);
            mid160 = Biquad.lowPass(rate, 160.0);
            mid250 = Biquad.lowPass(rate, 250.0);
            mid700 = Biquad.lowPass(rate, 700.0);
            mid3200 = Biquad.lowPass(rate, 3200.0);
            mid6500 = Biquad.lowPass(rate, 6500.0);
            side250 = Biquad.lowPass(rate, 250.0);
            side700 = Biquad.lowPass(rate, 700.0);
            side3200 = Biquad.lowPass(rate, 3200.0);
            side6500 = Biquad.lowPass(rate, 6500.0);
            left160 = Biquad.lowPass(rate, 160.0);
            right160 = Biquad.lowPass(rate, 160.0);
        }
    }

    private static final class Accumulator {
        long count;
        double totalSquares;
        double midSquares;
        double lowSquares;
        double subSquares;
        double bodySquares;
        double presenceSquares;
        double claritySquares;
        double sideBodySquares;
        double sidePresenceSquares;
        double sideClaritySquares;
        double lowLeftSum;
        double lowRightSum;
        double lowLeftSquares;
        double lowRightSquares;
        double lowCross;
        double derivativeSquares;
        double priorMid;
        boolean havePrior;
        double lowPeak;
        double midPeak;

        void add(double left, double right, FilterBank bank) {
            double mid = 0.5 * (left + right);
            double side = 0.5 * (left - right);
            double sub = bank.mid80.process(mid);
            double low = bank.mid160.process(mid);
            double m250 = bank.mid250.process(mid);
            double m700 = bank.mid700.process(mid);
            double m3200 = bank.mid3200.process(mid);
            double m6500 = bank.mid6500.process(mid);
            double s250 = bank.side250.process(side);
            double s700 = bank.side700.process(side);
            double s3200 = bank.side3200.process(side);
            double s6500 = bank.side6500.process(side);
            double body = m700 - m250;
            double presence = m3200 - m700;
            double clarity = m6500 - m3200;
            double sideBody = s700 - s250;
            double sidePresence = s3200 - s700;
            double sideClarity = s6500 - s3200;
            double lowLeft = bank.left160.process(left);
            double lowRight = bank.right160.process(right);
            double derivative = havePrior ? mid - priorMid : 0.0;
            priorMid = mid;
            havePrior = true;

            count++;
            totalSquares += 0.5 * (left * left + right * right);
            midSquares += mid * mid;
            lowSquares += low * low;
            subSquares += sub * sub;
            bodySquares += body * body;
            presenceSquares += presence * presence;
            claritySquares += clarity * clarity;
            sideBodySquares += sideBody * sideBody;
            sidePresenceSquares += sidePresence * sidePresence;
            sideClaritySquares += sideClarity * sideClarity;
            lowLeftSum += lowLeft;
            lowRightSum += lowRight;
            lowLeftSquares += lowLeft * lowLeft;
            lowRightSquares += lowRight * lowRight;
            lowCross += lowLeft * lowRight;
            derivativeSquares += derivative * derivative;
            lowPeak = Math.max(lowPeak, Math.abs(low));
            midPeak = Math.max(midPeak, Math.abs(mid));
        }

        Window finish(double start, double duration) {
            Window out = new Window();
            out.start = start;
            out.duration = duration;
            double vocal = bodySquares + presenceSquares + 0.50 * claritySquares;
            double sideVocal = sideBodySquares + sidePresenceSquares
                    + 0.50 * sideClaritySquares;
            out.feature[Feature.LEVEL] = rmsDb(totalSquares, count);
            out.feature[Feature.LOW_SHARE] = db(lowSquares, totalSquares);
            out.feature[Feature.SUB_WITHIN_LOW] = db(subSquares, lowSquares);
            out.feature[Feature.LOW_CREST] = 20.0 * Math.log10((lowPeak + 1.0e-12)
                    / (Math.sqrt(lowSquares / Math.max(1L, count)) + 1.0e-12));
            double numerator = count * lowCross - lowLeftSum * lowRightSum;
            double denominator = Math.sqrt(Math.max(0.0,
                    count * lowLeftSquares - lowLeftSum * lowLeftSum)
                    * Math.max(0.0,
                    count * lowRightSquares - lowRightSum * lowRightSum));
            out.feature[Feature.LOW_CORRELATION] = denominator <= 1.0e-20
                    ? 1.0 : clamp(numerator / denominator, -1.0, 1.0);
            out.feature[Feature.VOCAL_SHARE] = db(vocal, totalSquares);
            out.feature[Feature.VOCAL_DOMINANCE] = db(vocal, sideVocal);
            out.feature[Feature.PRESENCE_BODY] = db(presenceSquares, bodySquares);
            out.feature[Feature.HIGH_SHARE] = db(claritySquares + sideClaritySquares,
                    totalSquares);
            out.feature[Feature.MID_CREST] = 20.0 * Math.log10((midPeak + 1.0e-12)
                    / (Math.sqrt(midSquares / Math.max(1L, count)) + 1.0e-12));
            out.feature[Feature.DERIVATIVE_SHARE] = db(derivativeSquares, midSquares);
            return out;
        }
    }
}
