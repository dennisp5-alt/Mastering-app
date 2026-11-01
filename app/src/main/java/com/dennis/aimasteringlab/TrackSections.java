package com.dennis.aimasteringlab;

import java.util.ArrayList;
import java.util.List;

final class TrackSections {
    static final class Segment {
        String role;
        double startSeconds;
        double durationSeconds;
        double averageLufs;
        double confidence = Double.NaN;
        int learnedState = -1;
    }

    final List<Segment> segments = new ArrayList<>();
    final List<Segment> learnedStructure = new ArrayList<>();
    double quietLufs = Double.NaN;
    double chorusProxyLufs = Double.NaN;
    double busyLufs = Double.NaN;
    double endingLufs = Double.NaN;
    double quietToChorusLiftLu = Double.NaN;
    boolean learnedMapping;
    int learnedStates;
    int boundaryCandidates;
    double mappingConfidence = Double.NaN;
    String method = "energy-derived candidates; musical roles require perceptual confirmation";

    static TrackSections detect(LoudnessMeter.Result loudness, double durationSeconds) {
        TrackSections out = new TrackSections();
        double[] values = loudness.momentaryLufs;
        if (values == null || values.length < 20) return out;
        double hop = loudness.hopSeconds;
        double quietDuration = Math.min(12.0, Math.max(6.0, durationSeconds * 0.05));
        double chorusDuration = Math.min(15.0, Math.max(8.0, durationSeconds * 0.065));
        double busyDuration = Math.min(12.0, Math.max(6.0, durationSeconds * 0.05));

        Candidate quiet = choose(values, hop, quietDuration,
                durationSeconds * 0.05, durationSeconds * 0.72,
                false, null, loudness.integratedLufs - 24.0);
        Candidate chorus = choose(values, hop, chorusDuration,
                durationSeconds * 0.15, durationSeconds * 0.92,
                true, null, loudness.integratedLufs - 24.0);
        Candidate busy = choose(values, hop, busyDuration,
                durationSeconds * 0.12, durationSeconds * 0.92,
                true, chorus, loudness.integratedLufs - 24.0);

        double endingDuration = Math.min(12.0, durationSeconds);
        Candidate ending = candidateAt(values, hop,
                Math.max(0.0, durationSeconds - endingDuration), endingDuration);

        add(out, "quiet_vocal_or_verse_proxy", quiet);
        add(out, "full_chorus_proxy", chorus);
        add(out, "busiest_alternate_proxy", busy);
        add(out, "ending", ending);
        out.quietLufs = quiet == null ? Double.NaN : quiet.lufs;
        out.chorusProxyLufs = chorus == null ? Double.NaN : chorus.lufs;
        out.busyLufs = busy == null ? Double.NaN : busy.lufs;
        out.endingLufs = ending == null ? Double.NaN : ending.lufs;
        if (quiet != null && chorus != null) {
            out.quietToChorusLiftLu = chorus.lufs - quiet.lufs;
        }
        return out;
    }

    private static void add(TrackSections out, String role, Candidate candidate) {
        if (candidate == null) return;
        Segment segment = new Segment();
        segment.role = role;
        segment.startSeconds = candidate.start;
        segment.durationSeconds = candidate.duration;
        segment.averageLufs = candidate.lufs;
        out.segments.add(segment);
    }

    private static Candidate choose(double[] values, double hop, double duration,
                                    double minimumStart, double maximumEnd,
                                    boolean highest, Candidate exclude,
                                    double activeFloor) {
        int frames = Math.max(1, (int) Math.round(duration / hop));
        int first = Math.max(0, (int) Math.floor(minimumStart / hop));
        int last = Math.min(values.length - frames,
                (int) Math.floor(maximumEnd / hop) - frames);
        Candidate best = null;
        for (int i = first; i <= last; i++) {
            double start = i * hop;
            if (exclude != null && overlaps(start, duration, exclude.start, exclude.duration, 3.0)) {
                continue;
            }
            double lufs = windowLufs(values, i, frames);
            if (!Double.isFinite(lufs) || lufs < activeFloor) continue;
            if (best == null || (highest ? lufs > best.lufs : lufs < best.lufs)) {
                best = new Candidate(start, duration, lufs);
            }
        }
        if (best == null && exclude != null) {
            return choose(values, hop, duration, minimumStart, maximumEnd,
                    highest, null, activeFloor);
        }
        return best;
    }

    private static Candidate candidateAt(double[] values, double hop,
                                         double start, double duration) {
        int index = Math.max(0, Math.min(values.length - 1,
                (int) Math.floor(start / hop)));
        int frames = Math.max(1, Math.min(values.length - index,
                (int) Math.round(duration / hop)));
        return new Candidate(start, duration, windowLufs(values, index, frames));
    }

    private static boolean overlaps(double aStart, double aDuration,
                                    double bStart, double bDuration, double margin) {
        return aStart < bStart + bDuration + margin
                && bStart < aStart + aDuration + margin;
    }

    private static double windowLufs(double[] values, int start, int length) {
        double energy = 0.0;
        int count = 0;
        int end = Math.min(values.length, start + length);
        for (int i = start; i < end; i++) {
            double value = values[i];
            if (!Double.isFinite(value)) continue;
            energy += Math.pow(10.0, (value + 0.691) / 10.0);
            count++;
        }
        return count == 0 ? Double.NaN
                : -0.691 + 10.0 * Math.log10(energy / count);
    }

    private static final class Candidate {
        final double start;
        final double duration;
        final double lufs;

        Candidate(double start, double duration, double lufs) {
            this.start = start;
            this.duration = duration;
            this.lufs = lufs;
        }
    }
}
