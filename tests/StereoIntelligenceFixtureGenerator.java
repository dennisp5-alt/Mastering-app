package com.dennis.aimasteringlab;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/** Creates a deterministic stereo-only fixture with isolated module evidence. */
public final class StereoIntelligenceFixtureGenerator {
    private static final int RATE = 48000;
    private static final int DURATION_SECONDS = 32;
    private static final long FRAMES = (long) RATE * DURATION_SECONDS;
    private static final double SCALE = 8388608.0;

    private StereoIntelligenceFixtureGenerator() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Usage: StereoIntelligenceFixtureGenerator output.wav");
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
        System.out.println("STEREO_INTELLIGENCE_FIXTURE " + Hashing.sha256(output));
    }

    private static double[] signal(double seconds) {
        double edge = edgeFade(seconds);
        double section = seconds < 7.0 ? 0.78
                : seconds < 12.0 ? 0.94
                : seconds < 19.0 ? 0.86 : 1.0;
        double mid = 1.90 * section * (
                0.038 * Math.sin(twoPi(110.0, seconds, 0.10))
                + 0.030 * Math.sin(twoPi(220.0, seconds, 0.30))
                + 0.022 * Math.sin(twoPi(440.0, seconds, 0.70))
                + 0.014 * Math.sin(twoPi(880.0, seconds, 1.10))
                + 0.010 * Math.sin(twoPi(1760.0, seconds, 0.20))
                + 0.006 * Math.sin(twoPi(3520.0, seconds, 0.90))
                + 0.003 * Math.sin(twoPi(7040.0, seconds, 0.50)));
        double side = 1.90 * section * (
                0.016 * Math.sin(twoPi(275.0, seconds, 0.40))
                + 0.013 * Math.sin(twoPi(550.0, seconds, 1.00))
                + 0.010 * Math.sin(twoPi(1320.0, seconds, 0.20))
                + 0.007 * Math.sin(twoPi(2640.0, seconds, 0.80)));

        // Brief, isolated spectral defect for Event Repair AI.
        double spectral = plateau(seconds, 4.00, 4.85, 0.06)
                * (0.065 * Math.sin(twoPi(5100.0, seconds, 0.20))
                + 0.050 * Math.sin(twoPi(10300.0, seconds, 0.60)));
        mid += spectral;

        // Sustained low bloom; its smooth sine crest deliberately differs
        // from the short kick-like control pulses below.
        double bloom = plateau(seconds, 8.00, 9.10, 0.10);
        mid += bloom * (0.115 * Math.sin(twoPi(54.0, seconds, 0.30))
                + 0.060 * Math.sin(twoPi(92.0, seconds, 0.80)));

        // A low-band anti-phase interval exercises phase-safe side reduction.
        double phase = plateau(seconds, 10.00, 10.85, 0.09);
        side += phase * (0.080 * Math.sin(twoPi(72.0, seconds, 0.10))
                + 0.040 * Math.sin(twoPi(128.0, seconds, 0.50)));

        // Short high-crest low pulses must be detected but protected as kick-like.
        double kick = pulse(seconds, 6.20, 0.055)
                + 0.88 * pulse(seconds, 11.45, 0.050);
        mid += kick * 0.18 * Math.sin(twoPi(58.0, seconds, 0.20));

        // Centre-dominant, non-transient vocal-shaped phrases with controlled
        // side-band competition. The centre/side ratio remains below the
        // hard 11.5 dB vocal-focus ceiling.
        double vocal = plateau(seconds, 13.10, 15.20, 0.12)
                * (0.90 + 0.10 * Math.sin(twoPi(2.1, seconds, 0.20)));
        double vocalBody = 0.075 * Math.sin(twoPi(196.0, seconds, 0.10))
                + 0.048 * Math.sin(twoPi(392.0, seconds, 0.40))
                + 0.034 * Math.sin(twoPi(784.0, seconds, 0.70))
                + 0.026 * Math.sin(twoPi(1568.0, seconds, 0.30))
                + 0.018 * Math.sin(twoPi(3136.0, seconds, 1.10));
        double vocalMask = 0.028 * Math.sin(twoPi(760.0, seconds, 0.80))
                + 0.021 * Math.sin(twoPi(1520.0, seconds, 0.15))
                + 0.017 * Math.sin(twoPi(3040.0, seconds, 0.55));
        mid += vocal * vocalBody;
        side += vocal * vocalMask;

        return new double[]{edge * (mid + side), edge * (mid - side)};
    }

    private static double twoPi(double frequency, double seconds, double phase) {
        return 2.0 * Math.PI * frequency * seconds + phase;
    }

    private static double edgeFade(double seconds) {
        return smooth(Math.min(1.0, seconds / 0.75))
                * smooth(Math.min(1.0, (DURATION_SECONDS - seconds) / 3.0));
    }

    private static double plateau(double seconds, double start, double end,
                                  double ramp) {
        if (seconds <= start || seconds >= end) return 0.0;
        double in = Math.min(1.0, (seconds - start) / ramp);
        double out = Math.min(1.0, (end - seconds) / ramp);
        return smooth(Math.min(in, out));
    }

    private static double pulse(double seconds, double centre, double width) {
        double distance = Math.abs(seconds - centre) / width;
        return distance >= 1.0 ? 0.0 : smooth(1.0 - distance);
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

    private static void write24(OutputStream out, int value) throws IOException {
        out.write(value & 0xff);
        out.write((value >>> 8) & 0xff);
        out.write((value >>> 16) & 0xff);
    }
}
