package com.dennis.aimasteringlab;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/** 48 kHz/16-bit fixture with inherited full scale and a long source gap. */
public final class SourceIntegrityFixtureGenerator {
    private static final int RATE = 48000;
    private static final int SECONDS = 22;
    private static final long FRAMES = (long) RATE * SECONDS;

    private SourceIntegrityFixtureGenerator() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Usage: SourceIntegrityFixtureGenerator output.wav");
        }
        File output = new File(args[0]);
        File parent = output.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create integrity fixture directory");
        }
        try (OutputStream stream = new BufferedOutputStream(
                new FileOutputStream(output), 256 * 1024)) {
            writeHeader(stream);
            long zeroStart = Math.round(6.000 * RATE);
            long zeroEnd = Math.round(6.125 * RATE);
            long inheritedPeak = Math.round(5.600 * RATE);
            for (long frame = 0; frame < FRAMES; frame++) {
                int left;
                int right;
                if (frame >= zeroStart && frame < zeroEnd) {
                    left = 0;
                    right = 0;
                } else {
                    double t = frame / (double) RATE;
                    double edge = Math.min(1.0, t / 0.40)
                            * Math.min(1.0, (SECONDS - t) / 1.0);
                    double mid = edge * (0.060 * Math.sin(2.0 * Math.PI
                            * 110.0 * t)
                            + 0.045 * Math.sin(2.0 * Math.PI * 330.0 * t)
                            + 0.028 * Math.sin(2.0 * Math.PI * 1320.0 * t));
                    double event = plateau(t, 8.0, 8.6, 0.04)
                            * 0.095 * Math.sin(2.0 * Math.PI * 360.0 * t);
                    double side = edge * 0.018 * Math.sin(2.0 * Math.PI
                            * 770.0 * t + 0.3);
                    left = quantize(mid + event + side);
                    right = quantize(mid + event - side);
                }
                if (frame == inheritedPeak) left = 32767;
                write16(stream, left);
                write16(stream, right);
            }
        }
        System.out.println("SOURCE_INTEGRITY_FIXTURE "
                + Hashing.sha256(output));
    }

    private static double plateau(double seconds, double start,
                                  double end, double ramp) {
        if (seconds <= start || seconds >= end) return 0.0;
        double in = Math.min(1.0, (seconds - start) / ramp);
        double out = Math.min(1.0, (end - seconds) / ramp);
        return Math.min(in, out);
    }

    private static int quantize(double value) {
        return (int) Math.max(-32768, Math.min(32767,
                Math.round(value * 32768.0)));
    }

    private static void writeHeader(OutputStream out) throws IOException {
        long dataBytes = FRAMES * 4L;
        LittleEndian.writeAscii(out, "RIFF");
        LittleEndian.writeU32(out, 36L + dataBytes);
        LittleEndian.writeAscii(out, "WAVE");
        LittleEndian.writeAscii(out, "fmt ");
        LittleEndian.writeU32(out, 16);
        LittleEndian.writeU16(out, 1);
        LittleEndian.writeU16(out, 2);
        LittleEndian.writeU32(out, RATE);
        LittleEndian.writeU32(out, RATE * 4L);
        LittleEndian.writeU16(out, 4);
        LittleEndian.writeU16(out, 16);
        LittleEndian.writeAscii(out, "data");
        LittleEndian.writeU32(out, dataBytes);
    }

    private static void write16(OutputStream out, int value)
            throws IOException {
        out.write(value & 0xff);
        out.write((value >>> 8) & 0xff);
    }
}
