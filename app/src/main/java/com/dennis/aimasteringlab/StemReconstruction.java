package com.dennis.aimasteringlab;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class StemReconstruction {
    static final class Result {
        boolean attempted;
        boolean compatible;
        int stemsCompared;
        long framesCompared;
        double waveformCorrelation = Double.NaN;
        double leastSquaresGain = Double.NaN;
        double leastSquaresGainDb = Double.NaN;
        double residualSnrDb = Double.NaN;
        double startLagMs = Double.NaN;
        double middleLagMs = Double.NaN;
        double endLagMs = Double.NaN;
        double measuredDriftMs = Double.NaN;
        boolean globalPolarityRisk;
        boolean rawStemSumAllowed;
        boolean likelySeparationDerived;
        String verdict = "NOT ATTEMPTED";
        String mixPolicy = "AUTHORITATIVE STEREO MIX REMAINS PRIMARY";
        String error;
    }

    private static final int BUFFER_FRAMES = 4096;

    private StemReconstruction() {}

    static Result analyze(AudioAnalyzer.StreamProvider mixSource,
                          AnalysisResult analysis,
                          File stemArchive,
                          AudioAnalyzer.Progress progress) {
        Result out = new Result();
        out.attempted = true;
        ArrayList<StemInventory.Item> wavItems = new ArrayList<>();
        for (StemInventory.Item item : analysis.stems) {
            if (item.wav && item.wavInfo != null && item.error == null) wavItems.add(item);
        }
        if (wavItems.isEmpty()) {
            out.error = "No intact WAV stems were available for reconstruction";
            out.verdict = "UNAVAILABLE";
            return out;
        }
        if (wavItems.size() > 64) {
            out.error = "More than 64 WAV stems is outside the guarded mobile reconstruction limit";
            out.verdict = "UNAVAILABLE";
            return out;
        }

        try (WavFile.Reader mix = WavFile.open(mixSource.open());
             ZipFile zip = new ZipFile(stemArchive)) {
            for (StemInventory.Item item : wavItems) {
                WavFile.Info info = item.wavInfo;
                if (info.sampleRate != mix.info.sampleRate
                        || (info.channels != 1 && info.channels != mix.info.channels)) {
                    out.error = "Stem format mismatch prevents sample-domain reconstruction: " + item.path;
                    out.verdict = "INCOMPATIBLE";
                    return out;
                }
            }

            WavFile.Reader[] readers = new WavFile.Reader[wavItems.size()];
            try {
                for (int i = 0; i < readers.length; i++) {
                    ZipEntry entry = zip.getEntry(wavItems.get(i).path);
                    if (entry == null) throw new IOException("Stem entry disappeared: " + wavItems.get(i).path);
                    InputStream stream = zip.getInputStream(entry);
                    readers[i] = WavFile.open(stream);
                }
                out.compatible = true;
                out.stemsCompared = readers.length;
                process(mix, readers, wavItems, out, progress);
            } finally {
                for (WavFile.Reader reader : readers) {
                    if (reader != null) try { reader.close(); } catch (IOException ignored) {}
                }
            }
        } catch (Exception e) {
            out.error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            out.verdict = "FAILED";
            out.compatible = false;
        }
        return out;
    }

    private static void process(WavFile.Reader mix,
                                WavFile.Reader[] readers,
                                List<StemInventory.Item> items,
                                Result out,
                                AudioAnalyzer.Progress progress) throws IOException {
        int channels = mix.info.channels;
        float[][] mixBuffer = new float[channels][BUFFER_FRAMES];
        float[][][] stemBuffers = new float[readers.length][][];
        for (int i = 0; i < readers.length; i++) {
            stemBuffers[i] = new float[readers[i].info.channels][BUFFER_FRAMES];
        }
        double[] stemSum = new double[readers.length];
        double[] stemSquares = new double[readers.length];
        double[] stemPeaks = new double[readers.length];
        long[] stemSamples = new long[readers.length];
        long[] stemClips = new long[readers.length];
        double[] clipThresholds = new double[readers.length];
        for (int i = 0; i < readers.length; i++) {
            clipThresholds[i] = readers[i].info.floatingPoint ? 1.0
                    : 1.0 - 1.0 / Math.pow(2.0, readers[i].info.validBitsPerSample - 1);
        }

        long n = 0;
        double sx = 0.0, sy = 0.0, sxx = 0.0, syy = 0.0, sxy = 0.0;
        ArrayList<Double> mixEnvelope = new ArrayList<>();
        ArrayList<Double> sumEnvelope = new ArrayList<>();
        int envelopeHop = Math.max(1, mix.info.sampleRate / 100); // 10 ms
        int envelopeCount = 0;
        double envelopeMixEnergy = 0.0;
        double envelopeSumEnergy = 0.0;
        long framesDone = 0;
        int mixRead;
        while ((mixRead = mix.readFrames(mixBuffer, BUFFER_FRAMES)) > 0) {
            int[] stemRead = new int[readers.length];
            for (int i = 0; i < readers.length; i++) {
                for (float[] channel : stemBuffers[i]) Arrays.fill(channel, 0, mixRead, 0.0f);
                int count = readers[i].readFrames(stemBuffers[i], mixRead);
                stemRead[i] = Math.max(0, count);
            }

            for (int frame = 0; frame < mixRead; frame++) {
                double frameMixMid = 0.0;
                double frameSumMid = 0.0;
                for (int channel = 0; channel < channels; channel++) {
                    double mixValue = finite(mixBuffer[channel][frame]);
                    double sumValue = 0.0;
                    for (int stem = 0; stem < readers.length; stem++) {
                        int sourceChannel = readers[stem].info.channels == 1 ? 0 : channel;
                        double value = frame < stemRead[stem]
                                ? finite(stemBuffers[stem][sourceChannel][frame]) : 0.0;
                        sumValue += value;
                        stemSum[stem] += value;
                        stemSquares[stem] += value * value;
                        stemPeaks[stem] = Math.max(stemPeaks[stem], Math.abs(value));
                        stemSamples[stem]++;
                        if (Math.abs(value) >= clipThresholds[stem]) stemClips[stem]++;
                    }
                    sx += mixValue;
                    sy += sumValue;
                    sxx += mixValue * mixValue;
                    syy += sumValue * sumValue;
                    sxy += mixValue * sumValue;
                    n++;
                    frameMixMid += mixValue / channels;
                    frameSumMid += sumValue / channels;
                }
                envelopeMixEnergy += frameMixMid * frameMixMid;
                envelopeSumEnergy += frameSumMid * frameSumMid;
                envelopeCount++;
                if (envelopeCount == envelopeHop) {
                    mixEnvelope.add(Math.sqrt(envelopeMixEnergy / envelopeCount));
                    sumEnvelope.add(Math.sqrt(envelopeSumEnergy / envelopeCount));
                    envelopeCount = 0;
                    envelopeMixEnergy = 0.0;
                    envelopeSumEnergy = 0.0;
                }
            }
            framesDone += mixRead;
            int percent = 1 + (int) Math.min(98,
                    Math.round(98.0 * framesDone / mix.info.frames));
            progress.update("Reconstructing and comparing stem sum", percent);
            if (Thread.currentThread().isInterrupted()) throw new IOException("Stem reconstruction cancelled");
        }
        if (envelopeCount > 0) {
            mixEnvelope.add(Math.sqrt(envelopeMixEnergy / envelopeCount));
            sumEnvelope.add(Math.sqrt(envelopeSumEnergy / envelopeCount));
        }

        out.framesCompared = framesDone;
        double gain = syy > 1.0e-30 ? sxy / syy : 0.0;
        double residual = Math.max(0.0, sxx - 2.0 * gain * sxy + gain * gain * syy);
        out.leastSquaresGain = gain;
        out.leastSquaresGainDb = gain > 0.0 ? 20.0 * Math.log10(gain) : Double.NaN;
        out.residualSnrDb = residual > 1.0e-30 && sxx > 1.0e-30
                ? 10.0 * Math.log10(sxx / residual) : Double.POSITIVE_INFINITY;
        out.waveformCorrelation = correlation(n, sx, sy, sxx, syy, sxy);
        out.globalPolarityRisk = out.waveformCorrelation < -0.5 || gain < 0.0;

        double hopMs = 10.0;
        int envelopeSize = Math.min(mixEnvelope.size(), sumEnvelope.size());
        int window = Math.max(50, Math.min(1000, envelopeSize / 4));
        int maxLag = Math.min(10, Math.max(1, window / 10));
        out.startLagMs = bestLagMs(mixEnvelope, sumEnvelope,
                Math.min(envelopeSize / 10, Math.max(0, envelopeSize - window)), window, maxLag, hopMs);
        out.middleLagMs = bestLagMs(mixEnvelope, sumEnvelope,
                Math.max(0, envelopeSize / 2 - window / 2), window, maxLag, hopMs);
        out.endLagMs = bestLagMs(mixEnvelope, sumEnvelope,
                Math.max(0, envelopeSize - window), window, maxLag, hopMs);
        out.measuredDriftMs = out.endLagMs - out.startLagMs;

        for (int i = 0; i < items.size(); i++) {
            StemInventory.Item item = items.get(i);
            long count = Math.max(1L, stemSamples[i]);
            if (!Double.isFinite(item.rmsDbfs)) {
                item.rmsDbfs = AudioAnalyzer.toDb(Math.sqrt(stemSquares[i] / count));
                item.peakDbfs = AudioAnalyzer.toDb(stemPeaks[i]);
                item.dcOffset = stemSum[i] / count;
                item.crestFactorDb = item.peakDbfs - item.rmsDbfs;
                item.clippedSamples = stemClips[i];
            }
        }

        double absoluteCorrelation = Math.abs(out.waveformCorrelation);
        out.rawStemSumAllowed = !out.globalPolarityRisk
                && absoluteCorrelation >= 0.995
                && out.residualSnrDb >= 25.0
                && Math.abs(out.measuredDriftMs) <= 10.0;
        out.likelySeparationDerived = absoluteCorrelation < 0.98
                || out.residualSnrDb < 20.0;
        if (out.rawStemSumAllowed) {
            out.verdict = "HIGH-FIDELITY RECONSTRUCTION";
            out.mixPolicy = "AUTHORITATIVE MIX REMAINS PRIMARY; STEM SUM MAY BE USED AS A VALIDATION REFERENCE";
        } else if (out.likelySeparationDerived) {
            out.verdict = "POOR OR SEPARATION-DERIVED RECONSTRUCTION";
            out.mixPolicy = "NEVER REPLACE THE ORIGINAL; STEM WAVEFORM AND DELTA AUDIO ARE PROHIBITED; ALIGNED STEMS MAY QUALIFY AS ANALYSIS-ONLY CONTROL EVIDENCE";
        } else {
            out.verdict = "PARTIAL RECONSTRUCTION";
            out.mixPolicy = "AUTHORITATIVE MIX ONLY; STEMS MAY INFORM CONSERVATIVE DELTA PROCESSING";
        }
        progress.update("Stem reconstruction complete", 100);
    }

    private static double bestLagMs(List<Double> reference, List<Double> candidate,
                                    int start, int window, int maxLag, double hopMs) {
        if (reference.isEmpty() || candidate.isEmpty()) return Double.NaN;
        double best = -Double.MAX_VALUE;
        int bestLag = 0;
        for (int lag = -maxLag; lag <= maxLag; lag++) {
            double x = 0.0, y = 0.0, xx = 0.0, yy = 0.0, xy = 0.0;
            int count = 0;
            for (int i = 0; i < window; i++) {
                int a = start + i;
                int b = a + lag;
                if (a < 0 || b < 0 || a >= reference.size() || b >= candidate.size()) continue;
                double av = reference.get(a);
                double bv = candidate.get(b);
                x += av;
                y += bv;
                xx += av * av;
                yy += bv * bv;
                xy += av * bv;
                count++;
            }
            double corr = correlation(count, x, y, xx, yy, xy);
            if (corr > best + 1.0e-12
                    || (Math.abs(corr - best) <= 1.0e-12
                    && Math.abs(lag) < Math.abs(bestLag))) {
                best = corr;
                bestLag = lag;
            }
        }
        // Positive means the stem-sum envelope occurs later than the mix.
        return bestLag * hopMs;
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private static double correlation(long n, double sx, double sy,
                                      double sxx, double syy, double sxy) {
        if (n <= 1) return 0.0;
        double numerator = n * sxy - sx * sy;
        double denominator = Math.sqrt(Math.max(0.0, n * sxx - sx * sx)
                * Math.max(0.0, n * syy - sy * sy));
        if (denominator <= 1.0e-30) return 0.0;
        return Math.max(-1.0, Math.min(1.0, numerator / denominator));
    }

    static String compact(Result result) {
        if (result == null) return "not performed";
        if (result.error != null) return result.verdict + ": " + result.error;
        return String.format(Locale.US,
                "%s • corr %.5f • gain %+.3f dB • residual SNR %.2f dB • drift %+.1f ms",
                result.verdict, result.waveformCorrelation, result.leastSquaresGainDb,
                result.residualSnrDb, result.measuredDriftMs);
    }
}
