package com.dennis.aimasteringlab;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/** Deterministic fixture with two repairable faults and protected controls. */
public final class ClickDropoutFixtureGenerator {
    private static final int RATE = 48000;
    private static final int DURATION = 20;
    private static final long FRAMES = (long) RATE * DURATION;

    private ClickDropoutFixtureGenerator() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Usage: ClickDropoutFixtureGenerator output.wav");
        File output = new File(args[0]);
        File parent = output.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create fixture directory");
        }
        long dropoutStart = RATE * 10L;
        try (OutputStream out = new BufferedOutputStream(
                new FileOutputStream(output), 256 * 1024)) {
            writeHeader(out);
            for (long frame = 0; frame < FRAMES; frame++) {
                double t = frame / (double) RATE;
                double fade = Math.min(1.0, Math.min(t / 0.5, (DURATION - t) / 1.0));
                double transientControl = pulse(t, 7.0, 0.020) * 0.22;
                double left = fade * (0.11 * Math.sin(2.0 * Math.PI * 220.0 * t)
                        + 0.055 * Math.sin(2.0 * Math.PI * 440.0 * t + 0.3)
                        + transientControl * Math.sin(2.0 * Math.PI * 95.0 * t));
                double right = fade * (0.10 * Math.sin(2.0 * Math.PI * 220.0 * t + 0.2)
                        + 0.050 * Math.sin(2.0 * Math.PI * 330.0 * t + 0.7)
                        + transientControl * Math.sin(2.0 * Math.PI * 95.0 * t + 0.1));

                // Protected-intro impulse: must remain untouched.
                if (frame == RATE) left = 0.82;
                // Repairable isolated one-channel digital click.
                if (frame == RATE * 5L) left = -0.88;
                // Repairable 2 ms stereo-synchronous exact-zero hole.
                if (frame >= dropoutStart && frame < dropoutStart + 96L) {
                    left = 0.0;
                    right = 0.0;
                }
                // Protected-tail exact-zero control: must remain untouched.
                if (frame >= RATE * 15L && frame < RATE * 15L + 48L) {
                    left = 0.0;
                    right = 0.0;
                }
                write24(out, quantize(left));
                write24(out, quantize(right));
            }
        }
        System.out.println("CLICK_DROPOUT_FIXTURE " + Hashing.sha256(output));
    }

    private static double pulse(double seconds, double centre, double halfWidth) {
        double d = Math.abs(seconds - centre) / halfWidth;
        if (d >= 1.0) return 0.0;
        double x = 1.0 - d;
        return x * x * (3.0 - 2.0 * x);
    }

    private static int quantize(double value) {
        return (int) Math.max(-8388608, Math.min(8388607,
                Math.round(value * 8388608.0)));
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

    private static void write24(OutputStream out, int value) throws IOException {
        out.write(value & 0xff);
        out.write((value >>> 8) & 0xff);
        out.write((value >>> 16) & 0xff);
    }
}
