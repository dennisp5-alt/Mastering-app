package com.dennis.aimasteringlab;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Conservative distributed-probe detector for stable non-musical tones. */
final class PersistentToneModel {
    enum Kind { HUM_50, HUM_60, ELECTRICAL_BUZZ, DIGITAL_WHINE, MUSICAL_DIAGNOSTIC }

    static final class Event {
        Kind kind;
        double frequencyHz;
        double startSeconds;
        double endSeconds;
        double attenuationDb;
        double q;
        double persistence;
        double prominenceDb;
        double levelMadDb;
        double confidence;
        boolean automatic;
        String evidence;
    }

    static final class Result {
        String model = "DAML v0.9 hum, whine and persistent-tone repair";
        String method = "distributed Hann/Goertzel probes; stable harmonic families only; musical-note ambiguity remains diagnostic";
        int probeWindows;
        int frequenciesTested;
        final List<Event> events = new ArrayList<>();
        final List<String> safetyNotes = new ArrayList<>();

        int automaticCount() {
            int n = 0; for (Event e : events) if (e.automatic) n++; return n;
        }

        String compactSummary() {
            return probeWindows + " probes / " + frequenciesTested
                    + " frequencies • " + automaticCount()
                    + " automatic narrow notch(es) • "
                    + (events.size() - automaticCount()) + " diagnostic tone(s)";
        }
    }

    private static final int WINDOW = 4096;
    private static final int PROBES = 9;

    private PersistentToneModel() {}

    static Result analyze(AudioAnalyzer.StreamProvider source,
                          AnalysisResult base,
                          AudioAnalyzer.Progress progress) throws IOException {
        Result out = new Result();
        double[][] probes = collect(source, base, progress);
        out.probeWindows = probes.length;
        if (probes.length < 5) return out;
        ArrayList<Double> frequencies = new ArrayList<>();
        for (double f : new double[]{50,100,150,200,60,120,180,240}) frequencies.add(f);
        for (double f = 750.0; f <= 16500.0; f += 62.5) frequencies.add(f);
        out.frequenciesTested = frequencies.size();
        ArrayList<Tone> stable = new ArrayList<>();
        for (int i = 0; i < frequencies.size(); i++) {
            Tone tone = measure(probes, base.wav.sampleRate, frequencies.get(i));
            if (tone.persistence >= 0.75 && tone.prominenceDb >= 14.0
                    && tone.levelMadDb <= 3.0 && tone.medianLevelDb > -78.0) {
                stable.add(tone);
            }
            if (progress != null && i % 16 == 0) progress.update(
                    "v0.9: testing persistent tonal components",
                    (int) Math.round(100.0 * (i + 1) / frequencies.size()));
        }
        Set<Double> used = new LinkedHashSet<>();
        addMains(out, stable, used, 50.0, Kind.HUM_50, base.wav.durationSeconds);
        addMains(out, stable, used, 60.0, Kind.HUM_60, base.wav.durationSeconds);
        addBuzz(out, stable, used, base.wav.durationSeconds);
        addWhine(out, stable, used, base.wav.durationSeconds);
        for (Tone tone : stable) {
            if (used.contains(tone.frequency) || tone.frequency < 700.0) continue;
            if (tone.prominenceDb >= 22.0) {
                Event e = event(Kind.MUSICAL_DIAGNOSTIC, tone,
                        base.wav.durationSeconds, false);
                e.evidence = "single persistent pitch could be a musical note or synth partial; no automatic notch";
                out.events.add(e);
            }
        }
        out.events.sort(Comparator.comparingDouble(e -> e.frequencyHz));
        out.safetyNotes.add("Automatic notches require a stable harmonic family or paired non-note-grid carrier; isolated pitches are never removed.");
        out.safetyNotes.add("Notches are stereo-linked, extremely narrow and capped at 1.8 dB each / 2.5 dB combined.");
        return out;
    }

    private static void addMains(Result out, List<Tone> stable,
                                 Set<Double> used, double fundamental,
                                 Kind kind, double duration) {
        ArrayList<Tone> family = new ArrayList<>();
        for (int h = 1; h <= 4; h++) {
            Tone t = nearest(stable, fundamental * h, 1.0);
            if (t != null) family.add(t);
        }
        if (family.size() < 3) return;
        for (Tone tone : family) {
            Event e = event(kind, tone, duration, true);
            e.evidence = String.format(Locale.US,
                    "stable %.0f Hz mains-family harmonic series; %.1f Hz persisted in %.1f%% of probes, prominence %.2f dB",
                    fundamental, tone.frequency, tone.persistence * 100.0,
                    tone.prominenceDb);
            out.events.add(e); used.add(tone.frequency);
        }
    }

    private static void addBuzz(Result out, List<Tone> stable,
                                Set<Double> used, double duration) {
        for (Tone root : stable) {
            if (root.frequency < 750.0 || root.frequency > 4500.0
                    || musical(root.frequency)) continue;
            ArrayList<Tone> family = new ArrayList<>(); family.add(root);
            for (int h = 2; h <= 4; h++) {
                Tone t = nearest(stable, root.frequency * h,
                        Math.max(18.0, root.frequency * h * 0.012));
                if (t != null) family.add(t);
            }
            if (family.size() < 3) continue;
            for (Tone tone : family) {
                if (used.add(tone.frequency)) {
                    Event e = event(Kind.ELECTRICAL_BUZZ, tone, duration, true);
                    e.evidence = String.format(Locale.US,
                            "three-or-more stable non-note-grid harmonic components around %.1f Hz", root.frequency);
                    out.events.add(e);
                }
            }
            return;
        }
    }

    private static void addWhine(Result out, List<Tone> stable,
                                 Set<Double> used, double duration) {
        for (Tone root : stable) {
            if (root.frequency < 5500.0 || root.frequency > 9000.0
                    || musical(root.frequency)) continue;
            Tone harmonic = nearest(stable, root.frequency * 2.0,
                    Math.max(30.0, root.frequency * 0.02));
            if (harmonic == null) continue;
            for (Tone tone : new Tone[]{root, harmonic}) {
                if (used.add(tone.frequency)) {
                    Event e = event(Kind.DIGITAL_WHINE, tone, duration, true);
                    e.evidence = "two stable harmonically related high-frequency carrier components away from the equal-tempered note grid";
                    out.events.add(e);
                }
            }
            return;
        }
    }

    private static Event event(Kind kind, Tone tone, double duration,
                               boolean automatic) {
        Event e = new Event(); e.kind = kind; e.frequencyHz = tone.frequency;
        e.startSeconds = 2.0; e.endSeconds = Math.max(2.0,
                duration - Math.max(8.0, duration * 0.04));
        e.attenuationDb = automatic ? -Math.min(1.8,
                0.75 + Math.max(0.0, tone.prominenceDb - 14.0) * 0.055) : 0.0;
        e.q = Math.min(45.0, Math.max(18.0, tone.frequency / 55.0));
        e.persistence = tone.persistence; e.prominenceDb = tone.prominenceDb;
        e.levelMadDb = tone.levelMadDb;
        e.confidence = Math.min(0.90, 0.60 + tone.persistence * 0.22
                + Math.min(0.08, tone.prominenceDb / 300.0));
        e.automatic = automatic; return e;
    }

    private static double[][] collect(AudioAnalyzer.StreamProvider source,
                                      AnalysisResult base,
                                      AudioAnalyzer.Progress progress) throws IOException {
        long usableStart = Math.min(base.wav.frames, base.wav.sampleRate * 2L);
        long usableEnd = Math.max(usableStart, base.wav.frames
                - Math.round(Math.max(8.0, base.wav.durationSeconds * 0.04)
                * base.wav.sampleRate));
        int count = usableEnd - usableStart >= WINDOW ? PROBES : 0;
        if (count == 0) return new double[0][];
        long[] starts = new long[count];
        for (int i = 0; i < count; i++) starts[i] = usableStart
                + Math.round((usableEnd - usableStart - WINDOW)
                * (i / (double) Math.max(1, count - 1)));
        double[][] probes = new double[count][WINDOW];
        int[] filled = new int[count];
        try (WavFile.Reader reader = WavFile.open(source.open())) {
            float[][] buffer = new float[2][4096]; long frame = 0L; int read;
            while ((read = reader.readFrames(buffer, 4096)) > 0) {
                for (int n = 0; n < read; n++, frame++) {
                    for (int p = 0; p < count; p++) {
                        if (frame >= starts[p] && frame < starts[p] + WINDOW) {
                            int at = filled[p]++;
                            probes[p][at] = 0.5 * (buffer[0][n] + buffer[1][n]);
                        }
                    }
                }
            }
        }
        ArrayList<double[]> complete = new ArrayList<>();
        for (int i = 0; i < count; i++) if (filled[i] == WINDOW) complete.add(probes[i]);
        return complete.toArray(new double[complete.size()][]);
    }

    private static Tone measure(double[][] probes, int rate, double frequency) {
        double[] levels = new double[probes.length];
        double[] prominence = new double[probes.length];
        int present = 0;
        for (int i = 0; i < probes.length; i++) {
            double centre = goertzel(probes[i], rate, frequency);
            // At mains frequencies a 4096-sample Hann probe has a wide main
            // lobe. Compare farther away so a 50/60 Hz tone is not used as
            // its own local noise floor. The three-harmonic gate still
            // prevents isolated musical bass notes from being removed.
            double neighbourOffset = frequency <= 300.0
                    ? 25.0 : Math.max(12.5, frequency * 0.012);
            double left = goertzel(probes[i], rate,
                    Math.max(20.0, frequency - neighbourOffset));
            double right = goertzel(probes[i], rate,
                    frequency + neighbourOffset);
            levels[i] = 10.0 * Math.log10(Math.max(1.0e-30, centre));
            prominence[i] = 10.0 * Math.log10(Math.max(1.0e-30,
                    centre / Math.max(1.0e-30, 0.5 * (left + right))));
            if (prominence[i] >= 10.0 && levels[i] > -82.0) present++;
        }
        Tone t = new Tone(); t.frequency = frequency;
        t.persistence = present / (double) probes.length;
        t.medianLevelDb = percentile(levels, 0.5);
        t.prominenceDb = percentile(prominence, 0.5);
        double[] dev = new double[levels.length];
        for (int i = 0; i < levels.length; i++) dev[i] = Math.abs(levels[i] - t.medianLevelDb);
        t.levelMadDb = percentile(dev, 0.5); return t;
    }

    private static double goertzel(double[] x, int rate, double frequency) {
        double omega = 2.0 * Math.PI * frequency / rate;
        double coeff = 2.0 * Math.cos(omega); double s0, s1 = 0.0, s2 = 0.0;
        for (int n = 0; n < x.length; n++) {
            double window = 0.5 - 0.5 * Math.cos(2.0 * Math.PI * n / (x.length - 1));
            s0 = x[n] * window + coeff * s1 - s2; s2 = s1; s1 = s0;
        }
        return Math.max(0.0, s1*s1 + s2*s2 - coeff*s1*s2) / (x.length*x.length);
    }

    private static Tone nearest(List<Tone> tones, double target, double tolerance) {
        Tone best = null; double distance = tolerance;
        for (Tone t : tones) {
            double d = Math.abs(t.frequency - target);
            if (d <= distance) { best = t; distance = d; }
        }
        return best;
    }

    private static boolean musical(double frequency) {
        double midi = 69.0 + 12.0 * Math.log(frequency / 440.0) / Math.log(2.0);
        double cents = Math.abs(midi - Math.rint(midi)) * 100.0;
        return cents < 28.0;
    }

    private static double percentile(double[] values, double q) {
        double[] copy = values.clone(); Arrays.sort(copy);
        if (copy.length == 0) return -300.0;
        double p = Math.max(0.0, Math.min(1.0, q)) * (copy.length - 1);
        int lo = (int) Math.floor(p), hi = Math.min(copy.length - 1, lo + 1);
        return copy[lo] + (copy[hi] - copy[lo]) * (p - lo);
    }

    private static final class Tone {
        double frequency, persistence, medianLevelDb, prominenceDb, levelMadDb;
    }
}
