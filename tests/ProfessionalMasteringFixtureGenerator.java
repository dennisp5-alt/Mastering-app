package com.dennis.aimasteringlab;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/** Deterministic fixture for all four v0.8 professional modules. */
public final class ProfessionalMasteringFixtureGenerator {
    private static final int RATE = 48000;
    private static final int SECONDS = 42;
    private static final long FRAMES = (long) RATE * SECONDS;
    private static final double SCALE = 8388608.0;

    private ProfessionalMasteringFixtureGenerator() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Usage: ProfessionalMasteringFixtureGenerator output.wav");
        }
        File output = new File(args[0]);
        File parent = output.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create professional fixture directory");
        }
        try (OutputStream stream = new BufferedOutputStream(
                new FileOutputStream(output), 256 * 1024)) {
            writeHeader(stream);
            for (long frame = 0; frame < FRAMES; frame++) {
                double t = frame / (double) RATE;
                double[] value = signal(t);
                write24(stream, quantize(value[0]));
                write24(stream, quantize(value[1]));
            }
        }
        System.out.println("PROFESSIONAL_MASTERING_FIXTURE "
                + Hashing.sha256(output));
    }

    private static double[] signal(double t) {
        double edge = smooth(Math.min(1.0, t / 0.65))
                * smooth(Math.min(1.0, (SECONDS - t) / 2.0));
        double mid = 0.090 * Math.sin(twoPi(95.0, t, 0.1))
                + 0.074 * Math.sin(twoPi(220.0, t, 0.4))
                + 0.060 * Math.sin(twoPi(740.0, t, 0.8))
                + 0.052 * Math.sin(twoPi(1450.0, t, 0.3))
                + 0.041 * Math.sin(twoPi(2750.0, t, 1.0))
                + 0.020 * Math.sin(twoPi(5100.0, t, 0.6))
                + 0.008 * Math.sin(twoPi(7900.0, t, 0.2));
        double side = 0.019 * Math.sin(twoPi(360.0, t, 0.7))
                + 0.015 * Math.sin(twoPi(1050.0, t, 0.2))
                + 0.010 * Math.sin(twoPi(4200.0, t, 1.1))
                + 0.004 * Math.sin(twoPi(8200.0, t, 0.5));

        double washed = plateau(t, 6.00, 6.70, 0.05);
        side += washed * (0.105 * Math.sin(twoPi(1100.0, t, 0.9))
                + 0.080 * Math.sin(twoPi(3600.0, t, 0.2)));
        double highWidth = plateau(t, 10.00, 10.70, 0.04);
        side += highWidth * (0.110 * Math.sin(twoPi(7800.0, t, 0.3))
                + 0.080 * Math.sin(twoPi(9200.0, t, 0.8)));

        double phase45 = plateau(t, 14.00, 14.70, 0.06);
        side += phase45 * 0.120 * Math.sin(twoPi(45.0, t, 0.1));
        double phase82 = plateau(t, 16.00, 16.70, 0.06);
        side += phase82 * 0.115 * Math.sin(twoPi(82.0, t, 0.7));
        double phase132 = plateau(t, 18.00, 18.70, 0.06);
        side += phase132 * 0.105 * Math.sin(twoPi(132.0, t, 0.4));

        double sibilance = plateau(t, 23.00, 23.50, 0.025);
        mid += sibilance * (0.125 * Math.sin(twoPi(7600.0, t, 0.3))
                + 0.085 * Math.sin(twoPi(9100.0, t, 0.9)));
        double plosive = plateau(t, 26.00, 26.22, 0.015);
        mid += plosive * (0.300 * Math.sin(twoPi(72.0, t, 0.5))
                + 0.180 * Math.sin(twoPi(118.0, t, 0.2)));
        double harsh = plateau(t, 30.00, 30.60, 0.035);
        mid += harsh * (0.135 * Math.sin(twoPi(2200.0, t, 0.7))
                + 0.100 * Math.sin(twoPi(2850.0, t, 0.1)));

        // Short controlled pressure: limiting is needed, but remains within
        // the song-derived reduction allowance.
        mid += 0.74 * gaussian(t, 20.345, 0.00065);
        return new double[]{edge * (mid + side), edge * (mid - side)};
    }

    private static double twoPi(double frequency, double seconds,
                                double phase) {
        return 2.0 * Math.PI * frequency * seconds + phase;
    }

    private static double plateau(double seconds, double start,
                                  double end, double ramp) {
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
        long dataBytes = FRAMES * 6L;
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
