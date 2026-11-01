package com.dennis.aimasteringlab;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/** Deterministic stereo fixture for the three v0.7 candidate families. */
public final class AdvancedIntelligenceFixtureGenerator {
    private static final int RATE = 48000;
    private static final int DURATION_SECONDS = 38;
    private static final long FRAMES = (long) RATE * DURATION_SECONDS;
    private static final double SCALE = 8388608.0;

    private AdvancedIntelligenceFixtureGenerator() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Usage: AdvancedIntelligenceFixtureGenerator output.wav");
        }
        File output = new File(args[0]);
        File parent = output.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create fixture directory");
        }
        try (OutputStream stream = new BufferedOutputStream(
                new FileOutputStream(output), 256 * 1024)) {
            writeHeader(stream);
            for (long frame = 0; frame < FRAMES; frame++) {
                double seconds = frame / (double) RATE;
                double[] sample = signal(seconds);
                write24(stream, quantize(sample[0]));
                write24(stream, quantize(sample[1]));
            }
        }
        System.out.println("ADVANCED_INTELLIGENCE_FIXTURE "
                + Hashing.sha256(output));
    }

    private static double[] signal(double seconds) {
        double edge = edgeFade(seconds);
        double section = seconds < 10.0 ? 0.88
                : seconds < 18.0 ? 1.0
                : seconds < 27.0 ? 0.90 : 1.0;
        double mid = section * (
                0.050 * Math.sin(twoPi(98.0, seconds, 0.10))
                + 0.038 * Math.sin(twoPi(196.0, seconds, 0.30))
                + 0.030 * Math.sin(twoPi(392.0, seconds, 0.70))
                + 0.023 * Math.sin(twoPi(784.0, seconds, 1.10))
                + 0.017 * Math.sin(twoPi(1568.0, seconds, 0.20))
                + 0.011 * Math.sin(twoPi(3136.0, seconds, 0.90))
                + 0.006 * Math.sin(twoPi(6272.0, seconds, 0.50)));
        double side = section * (
                0.020 * Math.sin(twoPi(245.0, seconds, 0.40))
                + 0.015 * Math.sin(twoPi(735.0, seconds, 1.00))
                + 0.010 * Math.sin(twoPi(2205.0, seconds, 0.20)));

        // Isolated high-frequency fizz and metallic signatures.
        double fizz = plateau(seconds, 5.00, 5.55, 0.04);
        mid += fizz * (0.080 * Math.sin(twoPi(11200.0, seconds, 0.30))
                + 0.052 * Math.sin(twoPi(13700.0, seconds, 0.70)));
        double metallic = plateau(seconds, 15.00, 15.55, 0.04);
        mid += metallic * (0.090 * Math.sin(twoPi(4820.0, seconds, 0.15))
                + 0.058 * Math.sin(twoPi(6120.0, seconds, 0.55)));
        double phasey = plateau(seconds, 23.00, 23.55, 0.04);
        side += phasey * (0.090 * Math.sin(twoPi(9100.0, seconds, 0.10))
                + 0.060 * Math.sin(twoPi(12600.0, seconds, 0.80)));

        // One short full-band peak produces target-gain limiter pressure.
        double spike = gaussian(seconds, 12.345, 0.00055);
        mid += 0.62 * spike;

        // A low-frequency impact is a negative control and must be protected.
        double kick = gaussian(seconds, 20.250, 0.022)
                * Math.sin(twoPi(58.0, seconds, 0.20));
        mid += 0.42 * kick;
        return new double[]{edge * (mid + side), edge * (mid - side)};
    }

    private static double twoPi(double frequency, double seconds,
                                double phase) {
        return 2.0 * Math.PI * frequency * seconds + phase;
    }

    private static double edgeFade(double seconds) {
        return smooth(Math.min(1.0, seconds / 0.70))
                * smooth(Math.min(1.0,
                (DURATION_SECONDS - seconds) / 3.0));
    }

    private static double plateau(double seconds, double start, double end,
                                  double ramp) {
        if (seconds <= start || seconds >= end) return 0.0;
        double in = Math.min(1.0, (seconds - start) / ramp);
        double out = Math.min(1.0, (end - seconds) / ramp);
        return smooth(Math.min(in, out));
    }

    private static double gaussian(double seconds, double centre,
                                   double sigma) {
        double x = (seconds - centre) / sigma;
        return Math.exp(-0.5 * x * x);
    }

    private static double smooth(double value) {
        double x = Math.max(0.0, Math.min(1.0, value));
        return x * x * (3.0 - 2.0 * x);
    }

    private static int quantize(double value) {
        return (int) Math.max(-8388608, Math.min(8388607,
                Math.round(value * SCALE)));
    }

    private static void writeHeader(OutputStream out) throws IOException {
        long dataBytes = FRAMES * 2L * 3L;
        LittleEndian.writeAscii(out, "RIFF");
        LittleEndian.writeU32(out, 36L + dataBytes);
        LittleEndian.writeAscii(out, "WAVE");
        LittleEndian.writeAscii(out, "fmt ");
        LittleEndian.writeU32(out, 16);
        LittleEndian.writeU16(out, 1);
        LittleEndian.writeU16(out, 2);
        LittleEndian.writeU32(out, RATE);
        LittleEndian.writeU32(out, RATE * 6L);
        LittleEndian.writeU16(out, 6);
        LittleEndian.writeU16(out, 24);
        LittleEndian.writeAscii(out, "data");
        LittleEndian.writeU32(out, dataBytes);
    }

    private static void write24(OutputStream out, int value)
            throws IOException {
        out.write(value & 0xff);
        out.write((value >>> 8) & 0xff);
        out.write((value >>> 16) & 0xff);
    }
}
