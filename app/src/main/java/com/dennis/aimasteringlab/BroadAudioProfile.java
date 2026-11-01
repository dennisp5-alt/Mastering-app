package com.dennis.aimasteringlab;

import java.io.IOException;
import java.util.Arrays;

/** Broad, level-independent tonal/width evidence shared by v0.9 models. */
final class BroadAudioProfile {
    static final double[] LOW = {25, 80, 180, 500, 1600, 4200, 9000};
    static final double[] HIGH = {80, 180, 500, 1600, 4200, 9000, 18000};

    static final class Result {
        int sampleRate;
        long frames;
        double rmsDbfs;
        double peakDbfs;
        final double[] midBandDb = new double[LOW.length];
        final double[] sideBandDb = new double[LOW.length];
        double sideShareDb;
        double vocalCentreProxyDb;
    }

    private BroadAudioProfile() {}

    static Result analyze(AudioAnalyzer.StreamProvider source,
                          AudioAnalyzer.Progress progress) throws IOException {
        Result out = new Result();
        try (WavFile.Reader reader = WavFile.open(source.open())) {
            if (reader.info.channels != 2) {
                throw new IOException("Broad profile requires stereo audio");
            }
            out.sampleRate = reader.info.sampleRate;
            out.frames = reader.info.frames;
            Band[] mid = new Band[LOW.length];
            Band[] side = new Band[LOW.length];
            for (int i = 0; i < LOW.length; i++) {
                mid[i] = new Band(out.sampleRate, LOW[i],
                        Math.min(HIGH[i], out.sampleRate * 0.47));
                side[i] = new Band(out.sampleRate, LOW[i],
                        Math.min(HIGH[i], out.sampleRate * 0.47));
            }
            double[] midEnergy = new double[LOW.length];
            double[] sideEnergy = new double[LOW.length];
            double total = 0.0;
            double totalMid = 0.0;
            double totalSide = 0.0;
            double peak = 0.0;
            long samples = 0L;
            long frame = 0L;
            float[][] buffer = new float[2][4096];
            int read;
            while ((read = reader.readFrames(buffer, 4096)) > 0) {
                for (int n = 0; n < read; n++, frame++) {
                    double l = finite(buffer[0][n]);
                    double r = finite(buffer[1][n]);
                    double m = 0.5 * (l + r);
                    double s = 0.5 * (l - r);
                    total += l * l + r * r;
                    totalMid += m * m;
                    totalSide += s * s;
                    peak = Math.max(peak, Math.max(Math.abs(l), Math.abs(r)));
                    samples += 2;
                    for (int i = 0; i < LOW.length; i++) {
                        double mv = mid[i].process(m);
                        double sv = side[i].process(s);
                        midEnergy[i] += mv * mv;
                        sideEnergy[i] += sv * sv;
                    }
                }
                if (progress != null) progress.update(
                        "v0.9: measuring broad tonal and width evidence",
                        (int) Math.min(98, Math.round(98.0 * frame
                                / Math.max(1L, reader.info.frames))));
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("Broad profile analysis cancelled");
                }
            }
            out.rmsDbfs = db(Math.sqrt(total / Math.max(1L, samples)));
            out.peakDbfs = db(peak);
            for (int i = 0; i < LOW.length; i++) {
                out.midBandDb[i] = db(Math.sqrt(midEnergy[i]
                        / Math.max(1L, frame)));
                out.sideBandDb[i] = db(Math.sqrt(sideEnergy[i]
                        / Math.max(1L, frame)));
            }
            out.sideShareDb = 10.0 * Math.log10(Math.max(1.0e-30,
                    totalSide / Math.max(1.0e-30, totalMid)));
            double vocalMid = sum(midEnergy, 3, 5);
            double vocalSide = sum(sideEnergy, 3, 5);
            out.vocalCentreProxyDb = 10.0 * Math.log10(Math.max(1.0e-30,
                    vocalMid / Math.max(1.0e-30, vocalSide)));
        }
        if (progress != null) progress.update("v0.9: broad profile ready", 100);
        return out;
    }

    private static double sum(double[] values, int start, int end) {
        double out = 0.0;
        for (int i = start; i < Math.min(end, values.length); i++) out += values[i];
        return out;
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private static double db(double value) {
        return 20.0 * Math.log10(Math.max(1.0e-15, value));
    }

    private static final class Band {
        final Biquad high;
        final Biquad low;

        Band(int rate, double lowHz, double highHz) {
            high = Biquad.highPass(rate, Math.max(10.0, lowHz));
            low = Biquad.lowPass(rate, Math.max(lowHz + 10.0, highHz));
        }

        double process(double value) {
            return low.process(high.process(value));
        }
    }
}
