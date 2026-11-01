package com.dennis.aimasteringlab;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;

/**
 * Learns the v0.7 section, artifact-signature and transient-control maps from
 * the authoritative stereo mix. "Artifact" describes measured signal
 * behaviour only; it never claims to identify how the source was created.
 */
final class AdvancedStereoModel {
    enum Kind {
        SECTION_QUIET_CONTOUR,
        SECTION_ENERGY_CONTOUR,
        ARTIFACT_HF_FIZZ,
        ARTIFACT_METALLIC_RESONANCE,
        ARTIFACT_PHASEY_HF,
        TRANSIENT_PEAK_CONTROL,
        TRANSIENT_PROTECTED
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
        String model = "DAML advanced section/artifact/transient intelligence";
        String method = "offline per-song robust 100 ms learning + learned arrangement-state evidence + provenance-neutral artifact signatures + limiter-pressure transient control";
        int windows;
        int activeWindows;
        double activeFloorDbfs;
        double sectionMapConfidence = Double.NaN;
        double sourceSectionLiftLu = Double.NaN;
        double predictedSectionLiftChangeLu;
        boolean limiterPressure;
        double predictedFlatGainTruePeakDbtp = Double.NaN;
        int transientsProtected;
        final List<Event> events = new ArrayList<>();
        final List<String> safetyNotes = new ArrayList<>();

        int automaticCount(Kind... kinds) {
            int count = 0;
            for (Event event : events) {
                if (event.automatic && contains(event.kind, kinds)) count++;
            }
            return count;
        }

        boolean hasSectionAware() {
            return automaticCount(Kind.SECTION_QUIET_CONTOUR,
                    Kind.SECTION_ENERGY_CONTOUR) > 0;
        }

        boolean hasArtifactRepair() {
            return automaticCount(Kind.ARTIFACT_HF_FIZZ,
                    Kind.ARTIFACT_METALLIC_RESONANCE,
                    Kind.ARTIFACT_PHASEY_HF) > 0;
        }

        boolean hasTransientControl() {
            return automaticCount(Kind.TRANSIENT_PEAK_CONTROL) > 0;
        }

        int availableModules() {
            return (hasSectionAware() ? 1 : 0)
                    + (hasArtifactRepair() ? 1 : 0)
                    + (hasTransientControl() ? 1 : 0);
        }

        String compactSummary() {
            return String.format(Locale.US,
                    "%d/%d active windows • Section-Aware %d • Artifact Repair %d • Transient Control %d • %d natural transients protected",
                    activeWindows, windows,
                    automaticCount(Kind.SECTION_QUIET_CONTOUR,
                            Kind.SECTION_ENERGY_CONTOUR),
                    automaticCount(Kind.ARTIFACT_HF_FIZZ,
                            Kind.ARTIFACT_METALLIC_RESONANCE,
                            Kind.ARTIFACT_PHASEY_HF),
                    automaticCount(Kind.TRANSIENT_PEAK_CONTROL),
                    transientsProtected);
        }
    }

    private static final int BUFFER_FRAMES = 4096;
    private static final double WINDOW_SECONDS = 0.100;
    private static final double MIN_ARTIFACT_CONFIDENCE = 0.875;
    private static final double MIN_TRANSIENT_CONFIDENCE = 0.900;
    private static final double MAX_ARTIFACT_COVERAGE = 0.12;
    private static final double MAX_SECTION_COVERAGE = 0.65;
    private static final int MAX_PROTECTED_TRANSIENT_DIAGNOSTICS = 24;

    private AdvancedStereoModel() {}

    static Result unavailable(String reason, AnalysisResult base) {
        Result out = new Result();
        if (base != null) {
            out.activeFloorDbfs = base.rmsDbfs;
            out.predictedFlatGainTruePeakDbtp = base.predictedOutputTruePeakDbtp;
        }
        out.safetyNotes.add(reason == null
                ? "Advanced stereo analysis was unavailable; the verified v0.6 competition remains active."
                : reason);
        out.safetyNotes.add("No advanced mastering candidate was rendered when the advanced map was unavailable.");
        return out;
    }

    static Result analyze(AudioAnalyzer.StreamProvider source,
                          AnalysisResult base,
                          AudioAnalyzer.Progress progress) throws IOException {
        if (base.wav.channels != 2 || base.wav.sampleRate != 48000) {
            throw new IOException("Advanced mastering intelligence requires a 48 kHz stereo authoritative mix");
        }
        progress.update("Advanced mastering: learning sections, artifacts and transients", 0);
        List<Window> windows = extract(source, base, progress);
        Result out = new Result();
        out.windows = windows.size();
        out.predictedFlatGainTruePeakDbtp = base.truePeakEstimateDbtp
                + (-14.0 - base.integratedLufs);
        out.limiterPressure = base.limiterRequired
                || out.predictedFlatGainTruePeakDbtp > -1.10;
        if (windows.size() < 30) {
            out.safetyNotes.add("The track was too short for reliable advanced correction learning; diagnostics only.");
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
        if (active.size() < 24) {
            out.safetyNotes.add("Too few active windows cleared the advanced reliability floor; diagnostics only.");
            return out;
        }

        addSectionEvents(out, base, base.wav.durationSeconds);
        addArtifactEvents(out, windows, active, base.wav.durationSeconds);
        addTransientEvents(out, windows, active, base,
                base.wav.durationSeconds);
        out.events.sort(Comparator.comparingDouble(event -> event.startSeconds));
        if (out.availableModules() == 0) {
            out.safetyNotes.add("No advanced module found a correction that cleared every confidence, coverage and necessity gate.");
        }
        out.safetyNotes.add("Section labels are learned arrangement candidates, never unquestioned composer intent.");
        out.safetyNotes.add("Artifact signatures are provenance-neutral measurements and do not prove that a recording was AI-generated.");
        out.safetyNotes.add("Transient control activates only under measured target-gain peak pressure; natural kick/snare-like attacks are preferentially protected.");
        out.safetyNotes.add("All audio edits use the authoritative stereo mix, remain stereo-linked where broadband, and are disabled in the protected intro and ending.");
        progress.update("Advanced mastering: candidate maps ready", 100);
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
                    accumulator.add(finite(samples[0][i]), finite(samples[1][i]),
                            bank, accumulator.count / (double) rate);
                    if (accumulator.count == target) {
                        out.add(accumulator.finish(
                                (absolute + 1L - target) / (double) rate,
                                target / (double) rate));
                        accumulator = new Accumulator();
                    }
                }
                int percent = (int) Math.min(94,
                        Math.round(94.0 * absolute / Math.max(1L, reader.info.frames)));
                progress.update("Advanced mastering: learning authoritative stereo features", percent);
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("Advanced mastering analysis cancelled");
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

    private static void addSectionEvents(Result out, AnalysisResult base,
                                         double duration) {
        TrackSections sections = base.sections;
        if (sections == null || !sections.learnedMapping
                || sections.learnedStructure.isEmpty()
                || !Double.isFinite(sections.mappingConfidence)
                || sections.mappingConfidence < 0.70) {
            out.safetyNotes.add("Section-Aware Mastering remained diagnostic-only because the learned arrangement map was not reliable enough.");
            return;
        }
        out.sectionMapConfidence = sections.mappingConfidence;
        out.sourceSectionLiftLu = sections.quietToChorusLiftLu;
        double ending = protectedEndingStart(duration);
        ArrayList<Event> proposed = new ArrayList<>();

        if (Double.isFinite(sections.quietToChorusLiftLu)
                && sections.quietToChorusLiftLu < 1.75) {
            double need = 1.75 - sections.quietToChorusLiftLu;
            double quietDb = -clamp(0.10 + 0.07 * need, 0.10, 0.24);
            for (TrackSections.Segment segment : sections.learnedStructure) {
                String role = segment.role == null ? "" : segment.role;
                Kind kind = role.contains("quieter")
                        ? Kind.SECTION_QUIET_CONTOUR : null;
                if (kind == null || segment.durationSeconds < 1.25) continue;
                Event event = clippedSectionEvent(segment, kind, 2.0, ending,
                        sections.mappingConfidence,
                        quietDb,
                        String.format(Locale.US,
                                "learned section lift %.3f LU was below the 1.750 LU contour gate; role %s",
                                sections.quietToChorusLiftLu, role));
                if (event != null) proposed.add(event);
            }
            out.predictedSectionLiftChangeLu = -quietDb;
        } else {
            ArrayList<TrackSections.Segment> energy = new ArrayList<>();
            for (TrackSections.Segment segment : sections.learnedStructure) {
                String role = segment.role == null ? "" : segment.role;
                if (role.contains("high_energy")
                        && segment.durationSeconds >= 1.25
                        && Double.isFinite(segment.averageLufs)) {
                    energy.add(segment);
                }
            }
            if (energy.size() >= 2) {
                double[] levels = new double[energy.size()];
                for (int i = 0; i < levels.length; i++) {
                    levels[i] = energy.get(i).averageLufs;
                }
                double median = percentile(levels, 0.50);
                double spread = percentile(levels, 0.90) - percentile(levels, 0.10);
                if (spread > 1.60) {
                    for (TrackSections.Segment segment : energy) {
                        double delta = median - segment.averageLufs;
                        if (Math.abs(delta) < 0.80) continue;
                        double edit = clamp(delta * 0.10, -0.12, 0.12);
                        Event event = clippedSectionEvent(segment,
                                Kind.SECTION_ENERGY_CONTOUR, 2.0, ending,
                                sections.mappingConfidence, edit,
                                String.format(Locale.US,
                                        "recurring high-energy sections varied %.3f LU; this section differed %+.3f LU from their median",
                                        spread, -delta));
                        if (event != null) proposed.add(event);
                    }
                }
            }
        }

        double coverage = 0.0;
        for (Event event : proposed) coverage += event.durationSeconds();
        if (coverage <= 0.0) {
            out.safetyNotes.add("Section-Aware Mastering found no demonstrated contour inconsistency requiring an edit.");
            return;
        }
        if (coverage > Math.max(2.0, duration * MAX_SECTION_COVERAGE)) {
            for (Event event : proposed) event.automatic = false;
            out.safetyNotes.add("Section contour evidence covered too much of the song; it was retained as diagnostic-only rather than becoming broad automation.");
        }
        out.events.addAll(proposed);
    }

    private static Event clippedSectionEvent(TrackSections.Segment segment,
                                             Kind kind,
                                             double minimum,
                                             double maximum,
                                             double confidence,
                                             double editDb,
                                             String evidence) {
        double start = Math.max(minimum, segment.startSeconds);
        double end = Math.min(maximum,
                segment.startSeconds + segment.durationSeconds);
        if (end - start < 0.75 || Math.abs(editDb) < 0.025) return null;
        Event event = new Event();
        event.kind = kind;
        event.startSeconds = start;
        event.endSeconds = end;
        event.confidence = clamp(confidence, 0.0, 0.95);
        event.primaryDb = clamp(editDb, -0.24, 0.12);
        event.automatic = true;
        event.evidence = evidence;
        return event;
    }

    private static void addArtifactEvents(Result out, List<Window> windows,
                                          List<Integer> active,
                                          double duration) {
        double[] fizz = values(windows, Feature.FIZZ_SHARE, active);
        double[] metallic = values(windows, Feature.METALLIC_SHARE, active);
        double[] highSide = values(windows, Feature.HIGH_SIDE_SHARE, active);
        double fizzMedian = percentile(fizz, 0.50);
        double metallicMedian = percentile(metallic, 0.50);
        double sideMedian = percentile(highSide, 0.50);
        double fizzScale = robustScale(fizz, 0.55);
        double metallicScale = robustScale(metallic, 0.55);
        double sideScale = robustScale(highSide, 0.65);
        double ending = protectedEndingStart(duration);
        ArrayList<Event> raw = new ArrayList<>();

        for (int index : active) {
            Window window = windows.get(index);
            if (window.start < 2.0 || window.start + window.duration > ending) continue;
            double crest = window.feature[Feature.CREST];
            double fizzZ = (window.feature[Feature.FIZZ_SHARE] - fizzMedian)
                    / fizzScale;
            double metallicZ = (window.feature[Feature.METALLIC_SHARE]
                    - metallicMedian) / metallicScale;
            double sideZ = (window.feature[Feature.HIGH_SIDE_SHARE] - sideMedian)
                    / sideScale;

            if (fizzZ >= 2.55 && window.feature[Feature.FIZZ_SHARE] > -24.0
                    && crest < 15.0) {
                double confidence = logistic((fizzZ - 2.05) * 2.0 + 1.0);
                if (confidence >= MIN_ARTIFACT_CONFIDENCE) {
                    Event event = event(window, Kind.ARTIFACT_HF_FIZZ,
                            confidence, -(0.18 + 0.27 * strength(confidence,
                                    MIN_ARTIFACT_CONFIDENCE)),
                            String.format(Locale.US,
                                    "provenance-neutral 9–15 kHz fizz signature %.2f robust units above the song median; crest %.2f dB",
                                    fizzZ, crest));
                    raw.add(event);
                }
            }
            if (metallicZ >= 2.70
                    && window.feature[Feature.METALLIC_SHARE] > -18.0
                    && crest < 14.5) {
                double confidence = logistic((metallicZ - 2.15) * 2.0 + 0.95);
                if (confidence >= MIN_ARTIFACT_CONFIDENCE) {
                    Event event = event(window,
                            Kind.ARTIFACT_METALLIC_RESONANCE, confidence,
                            -(0.16 + 0.26 * strength(confidence,
                                    MIN_ARTIFACT_CONFIDENCE)),
                            String.format(Locale.US,
                                    "provenance-neutral 3.2–7 kHz metallic-band signature %.2f robust units above the song median; crest %.2f dB",
                                    metallicZ, crest));
                    raw.add(event);
                }
            }
            if (sideZ >= 2.55
                    && window.feature[Feature.HIGH_CORRELATION] < 0.12
                    && window.feature[Feature.HIGH_SIDE_SHARE] > -23.0) {
                double confidence = clamp(0.86 + 0.035 * (sideZ - 2.4)
                        + 0.07 * (0.12 - window.feature[Feature.HIGH_CORRELATION]),
                        0.0, 0.985);
                if (confidence >= MIN_ARTIFACT_CONFIDENCE) {
                    Event event = event(window, Kind.ARTIFACT_PHASEY_HF,
                            confidence, -(0.16 + 0.22 * strength(confidence,
                                    MIN_ARTIFACT_CONFIDENCE)),
                            String.format(Locale.US,
                                    "high-frequency side energy %.2f robust units above median with local L/R correlation %.3f",
                                    sideZ, window.feature[Feature.HIGH_CORRELATION]));
                    raw.add(event);
                }
            }
        }

        List<Event> merged = merge(raw, WINDOW_SECONDS * 1.10);
        for (Event event : merged) {
            if (event.durationSeconds() < 0.18) {
                event.automatic = false;
                event.evidence += "; diagnostic-only because the signature was too brief";
            }
        }
        applyCoverageByKind(out, merged, duration, MAX_ARTIFACT_COVERAGE,
                "An artifact signature recurred too broadly for safe local repair and was retained for review only.");
    }

    private static void addTransientEvents(Result out, List<Window> windows,
                                           List<Integer> active,
                                           AnalysisResult base,
                                           double duration) {
        double[] peak = values(windows, Feature.PEAK_DBFS, active);
        double[] crest = values(windows, Feature.CREST, active);
        double[] derivative = values(windows, Feature.DERIVATIVE_SHARE, active);
        double[] low = values(windows, Feature.LOW_SHARE, active);
        double[] fizz = values(windows, Feature.FIZZ_SHARE, active);
        double[] metallic = values(windows, Feature.METALLIC_SHARE, active);
        double peakMedian = percentile(peak, 0.50);
        double crestMedian = percentile(crest, 0.50);
        double derivativeMedian = percentile(derivative, 0.50);
        double lowMedian = percentile(low, 0.50);
        double fizzMedian = percentile(fizz, 0.50);
        double metallicMedian = percentile(metallic, 0.50);
        double peakScale = robustScale(peak, 0.70);
        double crestScale = robustScale(crest, 0.75);
        double derivativeScale = robustScale(derivative, 0.65);
        double lowScale = robustScale(low, 0.65);
        double fizzScale = robustScale(fizz, 0.55);
        double metallicScale = robustScale(metallic, 0.55);
        double ending = protectedEndingStart(duration);
        ArrayList<Event> controls = new ArrayList<>();
        ArrayList<Event> protectedDiagnostics = new ArrayList<>();

        for (int index : active) {
            Window window = windows.get(index);
            double peakZ = (window.feature[Feature.PEAK_DBFS] - peakMedian)
                    / peakScale;
            double crestZ = (window.feature[Feature.CREST] - crestMedian)
                    / crestScale;
            double derivativeZ = (window.feature[Feature.DERIVATIVE_SHARE]
                    - derivativeMedian) / derivativeScale;
            boolean transientLike = (crestZ >= 1.40
                    && derivativeZ >= 0.80 && peakZ >= 1.70)
                    || (crestZ >= 2.00 && peakZ >= 2.70);
            if (!transientLike) continue;
            double peakTime = window.start + window.peakOffsetSeconds;
            if (peakTime < 2.0 || peakTime >= ending) continue;
            boolean naturalLowImpact = window.feature[Feature.LOW_SHARE]
                    > lowMedian + 0.55 * lowScale
                    && crestZ < 4.50
                    && window.feature[Feature.FIZZ_SHARE]
                    < fizzMedian + 1.20 * fizzScale
                    && window.feature[Feature.METALLIC_SHARE]
                    < metallicMedian + 1.20 * metallicScale;
            double localPredicted = window.feature[Feature.PEAK_DBFS]
                    + (-14.0 - base.integratedLufs);
            if (!out.limiterPressure || naturalLowImpact
                    || localPredicted <= -0.85) {
                Event protectedEvent = new Event();
                protectedEvent.kind = Kind.TRANSIENT_PROTECTED;
                protectedEvent.startSeconds = Math.max(2.0, peakTime - 0.002);
                protectedEvent.endSeconds = Math.min(ending, peakTime + 0.006);
                protectedEvent.confidence = clamp(0.82 + 0.025 * crestZ,
                        0.0, 0.97);
                protectedEvent.automatic = false;
                protectedEvent.evidence = naturalLowImpact
                        ? "high-crest low-frequency impact was protected as kick/snare-like evidence"
                        : "natural transient was protected because target-gain peak pressure did not require control";
                protectedDiagnostics.add(protectedEvent);
                out.transientsProtected++;
                continue;
            }
            double confidence = logistic((peakZ - 1.45) * 1.30
                    + (crestZ - 1.10) * 0.85
                    + Math.max(0.0, derivativeZ - 0.55) * 0.35);
            if (confidence < MIN_TRANSIENT_CONFIDENCE) continue;
            Event event = new Event();
            event.kind = Kind.TRANSIENT_PEAK_CONTROL;
            event.startSeconds = Math.max(2.0, peakTime - 0.0015);
            event.endSeconds = Math.min(ending, peakTime + 0.0045);
            event.confidence = confidence;
            event.primaryDb = -(0.10 + 0.20 * strength(confidence,
                    MIN_TRANSIENT_CONFIDENCE));
            event.automatic = true;
            event.evidence = String.format(Locale.US,
                    "isolated stereo-linked peak predicted %.3f dBTP-equivalent after target gain; peak/crest/derivative robust scores %.2f/%.2f/%.2f",
                    localPredicted, peakZ, crestZ, derivativeZ);
            controls.add(event);
        }

        if (protectedDiagnostics.size() > MAX_PROTECTED_TRANSIENT_DIAGNOSTICS) {
            protectedDiagnostics.sort((a, b) ->
                    Double.compare(b.confidence, a.confidence));
            protectedDiagnostics = new ArrayList<>(protectedDiagnostics.subList(
                    0, MAX_PROTECTED_TRANSIENT_DIAGNOSTICS));
            out.safetyNotes.add(String.format(Locale.US,
                    "The confidence report retained the %d highest-confidence protected-transient examples; all %d detected natural transients remained untouched.",
                    MAX_PROTECTED_TRANSIENT_DIAGNOSTICS,
                    out.transientsProtected));
        }
        protectedDiagnostics.sort(Comparator.comparingDouble(
                event -> event.startSeconds));
        out.events.addAll(protectedDiagnostics);

        controls.sort(Comparator.comparingDouble(event -> event.startSeconds));
        ArrayList<Event> separated = new ArrayList<>();
        for (Event event : controls) {
            Event prior = separated.isEmpty() ? null
                    : separated.get(separated.size() - 1);
            if (prior != null && event.startSeconds < prior.endSeconds + 0.060) {
                if (event.confidence > prior.confidence) {
                    separated.set(separated.size() - 1, event);
                }
            } else {
                separated.add(event);
            }
        }
        int maximum = Math.max(4, Math.min(32,
                (int) Math.ceil(duration / 2.5)));
        if (separated.size() > maximum) {
            separated.sort((a, b) -> Double.compare(b.confidence, a.confidence));
            separated = new ArrayList<>(separated.subList(0, maximum));
            separated.sort(Comparator.comparingDouble(event -> event.startSeconds));
            out.safetyNotes.add("Transient control was limited to the highest-confidence isolated peaks.");
        }
        out.events.addAll(separated);
        if (out.limiterPressure && separated.isEmpty()) {
            out.safetyNotes.add("Peak pressure existed, but no isolated transient safely distinguished itself from musical attacks; Transient Intelligence bypassed processing.");
        }
    }

    private static Event event(Window window, Kind kind, double confidence,
                               double editDb, String evidence) {
        Event event = new Event();
        event.kind = kind;
        event.startSeconds = window.start;
        event.endSeconds = window.start + window.duration;
        event.confidence = confidence;
        event.primaryDb = editDb;
        event.automatic = true;
        event.evidence = evidence;
        return event;
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

    private static void applyCoverageByKind(Result out, List<Event> events,
                                            double duration,
                                            double maximumFraction,
                                            String note) {
        EnumMap<Kind, Double> coverage = new EnumMap<>(Kind.class);
        for (Event event : events) {
            if (!event.automatic) continue;
            coverage.put(event.kind, coverage.getOrDefault(event.kind, 0.0)
                    + event.durationSeconds());
        }
        boolean gated = false;
        for (Kind kind : coverage.keySet()) {
            if (coverage.get(kind) <= Math.max(0.75,
                    duration * maximumFraction)) continue;
            gated = true;
            for (Event event : events) {
                if (event.kind == kind) event.automatic = false;
            }
        }
        if (gated) out.safetyNotes.add(note);
        out.events.addAll(events);
    }

    static boolean kindInMode(Kind kind, AdvancedStereoRenderer.Mode mode) {
        if (mode == AdvancedStereoRenderer.Mode.ADVANCED_COMBINED) {
            return kind != Kind.TRANSIENT_PROTECTED;
        }
        if (mode == AdvancedStereoRenderer.Mode.SECTION_AWARE) {
            return kind == Kind.SECTION_QUIET_CONTOUR
                    || kind == Kind.SECTION_ENERGY_CONTOUR;
        }
        if (mode == AdvancedStereoRenderer.Mode.ARTIFACT_REPAIR) {
            return kind == Kind.ARTIFACT_HF_FIZZ
                    || kind == Kind.ARTIFACT_METALLIC_RESONANCE
                    || kind == Kind.ARTIFACT_PHASEY_HF;
        }
        return mode == AdvancedStereoRenderer.Mode.TRANSIENT_INTELLIGENCE
                && kind == Kind.TRANSIENT_PEAK_CONTROL;
    }

    private static double protectedEndingStart(double duration) {
        return Math.max(2.0,
                duration - Math.max(8.0, duration * 0.04));
    }

    private static boolean contains(Kind value, Kind[] values) {
        for (Kind candidate : values) if (candidate == value) return true;
        return false;
    }

    private static double[] values(List<Window> windows, int feature,
                                   List<Integer> indices) {
        double[] out = new double[indices == null ? windows.size() : indices.size()];
        if (indices == null) {
            for (int i = 0; i < windows.size(); i++) {
                out[i] = windows.get(i).feature[feature];
            }
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
        return Math.max(minimum,
                1.4826 * percentile(deviations, 0.50));
    }

    private static double strength(double confidence, double minimum) {
        return clamp((confidence - minimum) / Math.max(1.0e-9, 1.0 - minimum),
                0.0, 1.0);
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
        static final int METALLIC_SHARE = 2;
        static final int FIZZ_SHARE = 3;
        static final int HIGH_SIDE_SHARE = 4;
        static final int HIGH_CORRELATION = 5;
        static final int CREST = 6;
        static final int DERIVATIVE_SHARE = 7;
        static final int PEAK_DBFS = 8;
        static final int COUNT = 9;
    }

    private static final class Window {
        double start;
        double duration;
        double peakOffsetSeconds;
        final double[] feature = new double[Feature.COUNT];
    }

    private static final class FilterBank {
        final Biquad mid160;
        final Biquad mid3200;
        final Biquad mid7000;
        final Biquad mid9000;
        final Biquad mid15000;
        final Biquad side7000;
        final Biquad left7000;
        final Biquad right7000;

        FilterBank(double rate) {
            mid160 = Biquad.lowPass(rate, 160.0);
            mid3200 = Biquad.lowPass(rate, 3200.0);
            mid7000 = Biquad.lowPass(rate, 7000.0);
            mid9000 = Biquad.lowPass(rate, 9000.0);
            mid15000 = Biquad.lowPass(rate, 15000.0);
            side7000 = Biquad.lowPass(rate, 7000.0);
            left7000 = Biquad.lowPass(rate, 7000.0);
            right7000 = Biquad.lowPass(rate, 7000.0);
        }
    }

    private static final class Accumulator {
        long count;
        double totalSquares;
        double lowSquares;
        double metallicSquares;
        double fizzSquares;
        double airSquares;
        double highSideSquares;
        double highLeftSum;
        double highRightSum;
        double highLeftSquares;
        double highRightSquares;
        double highCross;
        double derivativeSquares;
        double priorMid;
        boolean havePrior;
        double peak;
        double peakOffsetSeconds;

        void add(double left, double right, FilterBank bank,
                 double offsetSeconds) {
            double mid = 0.5 * (left + right);
            double side = 0.5 * (left - right);
            double low = bank.mid160.process(mid);
            double m3200 = bank.mid3200.process(mid);
            double m7000 = bank.mid7000.process(mid);
            double m9000 = bank.mid9000.process(mid);
            double m15000 = bank.mid15000.process(mid);
            double metallic = m7000 - m3200;
            double fizz = m15000 - m9000;
            double air = mid - m15000;
            double highSide = side - bank.side7000.process(side);
            double highLeft = left - bank.left7000.process(left);
            double highRight = right - bank.right7000.process(right);
            double derivative = havePrior ? mid - priorMid : 0.0;
            priorMid = mid;
            havePrior = true;
            double framePeak = Math.max(Math.abs(left), Math.abs(right));
            if (framePeak > peak) {
                peak = framePeak;
                peakOffsetSeconds = offsetSeconds;
            }

            count++;
            totalSquares += 0.5 * (left * left + right * right);
            lowSquares += low * low;
            metallicSquares += metallic * metallic;
            fizzSquares += fizz * fizz;
            airSquares += air * air;
            highSideSquares += highSide * highSide;
            highLeftSum += highLeft;
            highRightSum += highRight;
            highLeftSquares += highLeft * highLeft;
            highRightSquares += highRight * highRight;
            highCross += highLeft * highRight;
            derivativeSquares += derivative * derivative;
        }

        Window finish(double start, double duration) {
            Window out = new Window();
            out.start = start;
            out.duration = duration;
            out.peakOffsetSeconds = peakOffsetSeconds;
            out.feature[Feature.LEVEL] = rmsDb(totalSquares, count);
            out.feature[Feature.LOW_SHARE] = db(lowSquares, totalSquares);
            out.feature[Feature.METALLIC_SHARE] = db(metallicSquares,
                    totalSquares);
            out.feature[Feature.FIZZ_SHARE] = db(fizzSquares
                    + 0.35 * airSquares, totalSquares);
            out.feature[Feature.HIGH_SIDE_SHARE] = db(highSideSquares,
                    totalSquares);
            double numerator = count * highCross
                    - highLeftSum * highRightSum;
            double denominator = Math.sqrt(Math.max(0.0,
                    count * highLeftSquares - highLeftSum * highLeftSum)
                    * Math.max(0.0,
                    count * highRightSquares - highRightSum * highRightSum));
            out.feature[Feature.HIGH_CORRELATION] = denominator <= 1.0e-20
                    ? 1.0 : clamp(numerator / denominator, -1.0, 1.0);
            out.feature[Feature.CREST] = 20.0 * Math.log10((peak + 1.0e-12)
                    / (Math.sqrt(totalSquares / Math.max(1L, count))
                    + 1.0e-12));
            out.feature[Feature.DERIVATIVE_SHARE] = db(derivativeSquares,
                    totalSquares);
            out.feature[Feature.PEAK_DBFS] = 20.0
                    * Math.log10(peak + 1.0e-12);
            return out;
        }
    }
}
