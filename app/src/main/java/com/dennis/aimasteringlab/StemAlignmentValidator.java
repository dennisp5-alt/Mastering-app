package com.dennis.aimasteringlab;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Measures local per-stem timing against the authoritative mix at five song
 * positions. Automatic stem deltas are enabled only when timing is zero at the
 * meter's 5 ms resolution and polarity evidence is not contradictory.
 */
final class StemAlignmentValidator {
    static final class Probe {
        String label;
        double positionSeconds;
        boolean informative;
        double lagMs = Double.NaN;
        double envelopeCorrelation = Double.NaN;
        double signedCorrelation = Double.NaN;
    }

    static final class StemResult {
        String path;
        boolean evaluated;
        boolean secure;
        boolean analysisSecure;
        boolean polarityRisk;
        int informativeProbes;
        int positivePolarityProbes;
        double minimumEnvelopeCorrelation = Double.NaN;
        double minimumLagMs = Double.NaN;
        double maximumLagMs = Double.NaN;
        double driftMs = Double.NaN;
        String verdict = "NOT EVALUATED";
        final List<Probe> probes = new ArrayList<>();
        final List<String> rejectionReasons = new ArrayList<>();
    }

    static final class Result {
        String method = "DAML v0.4 five-position per-stem envelope/waveform alignment; 5 ms resolution";
        String status = "No stems were available for local alignment validation";
        int mixWindows;
        int stemsEvaluated;
        int secureStems;
        int analysisSecureStems;
        final List<StemResult> stems = new ArrayList<>();

        StemResult find(String path) {
            if (path == null) return null;
            for (StemResult stem : stems) {
                if (path.equals(stem.path)) return stem;
            }
            return null;
        }

        boolean secure(String path) {
            StemResult result = find(path);
            return result != null && result.secure;
        }

        boolean analysisSecure(String path) {
            StemResult result = find(path);
            return result != null && result.analysisSecure;
        }
    }

    private static final int BUFFER_FRAMES = 4096;
    private static final double HOP_SECONDS = 0.005;
    private static final double PROBE_SECONDS = 6.0;
    private static final int MAX_LAG_WINDOWS = 2;
    private static final int MAX_STEMS = 64;
    private static final double MIN_ENVELOPE_CORRELATION = 0.10;
    private static final double MIN_SIGNED_CORRELATION = 0.07;
    private static final double[] POSITIONS = {0.06, 0.25, 0.50, 0.75, 0.92};
    private static final String[] LABELS = {"early", "25%", "midpoint", "75%", "near ending"};

    private StemAlignmentValidator() {}

    static Result analyze(AudioAnalyzer.StreamProvider mixSource,
                          AnalysisResult base,
                          File stemArchive,
                          AudioAnalyzer.Progress progress) throws IOException {
        Result result = new Result();
        FeatureSeries mix;
        progress.update("Stem assist: measuring authoritative timing reference", 0);
        try (WavFile.Reader reader = WavFile.open(mixSource.open())) {
            mix = readFeatures(reader);
        }
        result.mixWindows = mix.envelope.length;
        if (mix.envelope.length < 200) {
            result.status = "The authoritative mix is too short for local stem alignment";
            return result;
        }

        int eligible = 0;
        for (StemInventory.Item item : base.stems) {
            if (eligibleStem(item, base)) eligible++;
        }
        if (eligible > MAX_STEMS) {
            result.status = "More than 64 intact WAV stems is outside the guarded local-alignment limit";
            return result;
        }

        try (ZipFile zip = new ZipFile(stemArchive)) {
            int index = 0;
            for (StemInventory.Item item : base.stems) {
                if (!eligibleStem(item, base)) continue;
                index++;
                progress.update("Stem assist: local alignment " + index + "/" + eligible, -1);
                StemResult stem = inspectStem(zip, item, base, mix);
                result.stems.add(stem);
                result.stemsEvaluated++;
                if (stem.secure) result.secureStems++;
                if (stem.analysisSecure) result.analysisSecureStems++;
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("Stem alignment validation cancelled");
                }
            }
        }
        result.status = String.format(Locale.US,
                "%d/%d stems established exact zero local lag/drift; %d/%d are within the analysis-only ±5 ms control tolerance",
                result.secureStems, result.stemsEvaluated,
                result.analysisSecureStems, result.stemsEvaluated);
        progress.update("Stem assist: local alignment complete", 100);
        return result;
    }

    private static StemResult inspectStem(ZipFile zip, StemInventory.Item item,
                                          AnalysisResult base, FeatureSeries mix)
            throws IOException {
        StemResult result = new StemResult();
        result.path = item.path;
        ZipEntry entry = zip.getEntry(item.path);
        if (entry == null || entry.isDirectory()) {
            result.rejectionReasons.add("archive entry disappeared after inventory");
            result.verdict = "REJECTED";
            return result;
        }
        if (item.crc32 >= 0 && entry.getCrc() >= 0 && item.crc32 != entry.getCrc()) {
            result.rejectionReasons.add("CRC changed after inventory");
            result.verdict = "REJECTED";
            return result;
        }
        FeatureSeries stem;
        try (InputStream input = zip.getInputStream(entry);
             WavFile.Reader reader = WavFile.open(input)) {
            stem = readFeatures(reader);
        }
        result.evaluated = true;
        double excess = item.wavInfo.durationSeconds - base.wav.durationSeconds;
        double shortfall = base.wav.durationSeconds - item.wavInfo.durationSeconds;
        if (excess > 1.01 / base.wav.sampleRate) {
            result.rejectionReasons.add("stem extends beyond the authoritative mix");
        }
        if (shortfall > 2.0) {
            result.rejectionReasons.add("stem is more than two seconds shorter than the authoritative mix");
        }

        int common = Math.min(mix.envelope.length, stem.envelope.length);
        int probeLength = Math.max(120, (int) Math.round(PROBE_SECONDS / HOP_SECONDS));
        double firstLag = Double.NaN;
        double lastLag = Double.NaN;
        double minimumCorrelation = Double.POSITIVE_INFINITY;
        for (int probeIndex = 0; probeIndex < POSITIONS.length; probeIndex++) {
            Probe probe = measureProbe(mix, stem, common, probeLength,
                    POSITIONS[probeIndex], LABELS[probeIndex]);
            result.probes.add(probe);
            if (!probe.informative) continue;
            result.informativeProbes++;
            minimumCorrelation = Math.min(minimumCorrelation, probe.envelopeCorrelation);
            if (!Double.isFinite(firstLag)) firstLag = probe.lagMs;
            lastLag = probe.lagMs;
            if (!Double.isFinite(result.minimumLagMs)
                    || probe.lagMs < result.minimumLagMs) result.minimumLagMs = probe.lagMs;
            if (!Double.isFinite(result.maximumLagMs)
                    || probe.lagMs > result.maximumLagMs) result.maximumLagMs = probe.lagMs;
            if (probe.signedCorrelation >= MIN_SIGNED_CORRELATION) {
                result.positivePolarityProbes++;
            } else if (probe.signedCorrelation <= -0.20) {
                result.polarityRisk = true;
            }
        }
        if (minimumCorrelation < Double.POSITIVE_INFINITY) {
            result.minimumEnvelopeCorrelation = minimumCorrelation;
        }
        if (Double.isFinite(firstLag) && Double.isFinite(lastLag)) {
            result.driftMs = lastLag - firstLag;
        }
        if (result.informativeProbes < 3) {
            result.rejectionReasons.add("fewer than three local timing probes were informative");
        }
        if (result.positivePolarityProbes < 2) {
            result.rejectionReasons.add("individual polarity evidence was insufficient");
        }
        if (result.polarityRisk) {
            result.rejectionReasons.add("a local signed-correlation probe indicated polarity risk");
        }
        boolean analysisTimingSafe = Double.isFinite(result.minimumLagMs)
                && Math.abs(result.minimumLagMs) <= 5.001
                && Math.abs(result.maximumLagMs) <= 5.001
                && Double.isFinite(result.driftMs)
                && Math.abs(result.driftMs) <= 5.001;
        result.analysisSecure = result.rejectionReasons.isEmpty()
                && analysisTimingSafe;
        if (!Double.isFinite(result.minimumLagMs)
                || Math.abs(result.minimumLagMs) > 0.001
                || Math.abs(result.maximumLagMs) > 0.001
                || !Double.isFinite(result.driftMs)
                || Math.abs(result.driftMs) > 0.001) {
            result.rejectionReasons.add("local lag or drift was not zero at 5 ms resolution");
        }
        result.secure = result.rejectionReasons.isEmpty();
        result.verdict = result.secure
                ? "PASS: five-position local timing and polarity evidence are secure"
                : (result.analysisSecure
                    ? "ANALYSIS-ONLY PASS: local timing is within ±5 ms; stem waveform audio remains prohibited"
                    : "REJECTED: " + result.rejectionReasons.get(0));
        return result;
    }

    private static Probe measureProbe(FeatureSeries mix, FeatureSeries stem,
                                      int common, int requestedLength,
                                      double fraction, String label) {
        Probe probe = new Probe();
        probe.label = label;
        if (common < 80) return probe;
        int length = Math.min(requestedLength, Math.max(80, common / 5));
        int centre = (int) Math.round(fraction * (common - 1));
        int start = Math.max(MAX_LAG_WINDOWS,
                Math.min(common - length - MAX_LAG_WINDOWS, centre - length / 2));
        if (start < MAX_LAG_WINDOWS || start + length + MAX_LAG_WINDOWS > common) return probe;

        double bestScore = -Double.MAX_VALUE;
        double bestEnvelope = Double.NaN;
        double bestSigned = Double.NaN;
        int bestLag = 0;
        for (int lag = -MAX_LAG_WINDOWS; lag <= MAX_LAG_WINDOWS; lag++) {
            double envelopeCorrelation = correlation(mix.envelope, stem.envelope,
                    start, start + lag, length);
            double signedCorrelation = correlation(mix.signed, stem.signed,
                    start, start + lag, length);
            if (!Double.isFinite(envelopeCorrelation)
                    || !Double.isFinite(signedCorrelation)) continue;
            double score = 0.35 * envelopeCorrelation + 0.65 * signedCorrelation;
            if (score > bestScore + 1.0e-12
                    || (Math.abs(score - bestScore) <= 1.0e-12
                    && Math.abs(lag) < Math.abs(bestLag))) {
                bestScore = score;
                bestEnvelope = envelopeCorrelation;
                bestSigned = signedCorrelation;
                bestLag = lag;
            }
        }
        if (!Double.isFinite(bestScore)
                || bestEnvelope < MIN_ENVELOPE_CORRELATION
                || bestSigned < MIN_SIGNED_CORRELATION) return probe;
        probe.informative = true;
        probe.positionSeconds = (start + length * 0.5) * HOP_SECONDS;
        probe.lagMs = bestLag * HOP_SECONDS * 1000.0;
        probe.envelopeCorrelation = bestEnvelope;
        probe.signedCorrelation = bestSigned;
        return probe;
    }

    private static FeatureSeries readFeatures(WavFile.Reader reader) throws IOException {
        int hop = Math.max(1, (int) Math.round(reader.info.sampleRate * HOP_SECONDS));
        ArrayList<Double> envelope = new ArrayList<>();
        ArrayList<Double> signed = new ArrayList<>();
        float[][] samples = new float[reader.info.channels][BUFFER_FRAMES];
        int inHop = 0;
        double energy = 0.0;
        double signedSample = 0.0;
        int read;
        while ((read = reader.readFrames(samples, BUFFER_FRAMES)) > 0) {
            for (int frame = 0; frame < read; frame++) {
                double left = finite(samples[0][frame]);
                double right = reader.info.channels == 2
                        ? finite(samples[1][frame]) : left;
                double mid = 0.5 * (left + right);
                if (inHop == 0) signedSample = mid;
                energy += 0.5 * (left * left + right * right);
                inHop++;
                if (inHop == hop) {
                    envelope.add(10.0 * Math.log10(energy / inHop + 1.0e-18));
                    signed.add(signedSample);
                    inHop = 0;
                    energy = 0.0;
                }
            }
            if (Thread.currentThread().isInterrupted()) {
                throw new IOException("Stem alignment validation cancelled");
            }
        }
        if (inHop >= Math.max(16, hop / 3)) {
            envelope.add(10.0 * Math.log10(energy / inHop + 1.0e-18));
            signed.add(signedSample);
        }
        FeatureSeries result = new FeatureSeries(envelope.size());
        for (int i = 0; i < result.envelope.length; i++) {
            result.envelope[i] = envelope.get(i);
            result.signed[i] = signed.get(i);
        }
        return result;
    }

    private static boolean eligibleStem(StemInventory.Item item, AnalysisResult base) {
        return item.wav && item.wavInfo != null && item.error == null && item.crcVerified
                && item.wavInfo.sampleRate == base.wav.sampleRate
                && (item.wavInfo.channels == 1 || item.wavInfo.channels == 2);
    }

    private static double correlation(double[] a, double[] b,
                                      int startA, int startB, int count) {
        double x = 0.0, y = 0.0, xx = 0.0, yy = 0.0, xy = 0.0;
        for (int i = 0; i < count; i++) {
            double av = a[startA + i];
            double bv = b[startB + i];
            x += av;
            y += bv;
            xx += av * av;
            yy += bv * bv;
            xy += av * bv;
        }
        double numerator = count * xy - x * y;
        double denominator = Math.sqrt(Math.max(0.0, count * xx - x * x)
                * Math.max(0.0, count * yy - y * y));
        if (denominator <= 1.0e-20) return Double.NaN;
        return Math.max(-1.0, Math.min(1.0, numerator / denominator));
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private static final class FeatureSeries {
        final double[] envelope;
        final double[] signed;

        FeatureSeries(int size) {
            envelope = new double[size];
            signed = new double[size];
        }
    }
}
