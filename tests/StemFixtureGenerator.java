package com.dennis.aimasteringlab;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Creates deterministic production-stem, delayed-stem and unsafe-path fixtures. */
public final class StemFixtureGenerator {
    private static final int RATE = 48000;
    private static final int DURATION_SECONDS = 18;
    private static final long FRAMES = (long) RATE * DURATION_SECONDS;
    private static final int DELAY_FRAMES = 960; // 20 ms: intentionally unsafe
    private static final double SCALE = 8388608.0;

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: StemFixtureGenerator output-directory");
        }
        File directory = new File(args[0]);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Could not create fixture directory");
        }
        File mix = new File(directory, "StemFixtureMix.wav");
        File lead = new File(directory, "Lead Vocal.wav");
        File guitar = new File(directory, "Electric Guitar.wav");
        File delayed = new File(directory, "Electric Guitar Delayed.wav");
        render(mix, lead, guitar, delayed);

        File safeZip = new File(directory, "StemFixtureSafe.zip");
        zip(safeZip, new String[]{"Lead Vocal.wav", "Electric Guitar.wav"},
                new File[]{lead, guitar});
        File delayedZip = new File(directory, "StemFixtureDelayed.zip");
        zip(delayedZip, new String[]{"Lead Vocal.wav", "Electric Guitar.wav"},
                new File[]{lead, delayed});
        File unsafeZip = new File(directory, "StemFixtureUnsafePath.zip");
        zip(unsafeZip, new String[]{"../Lead Vocal.wav"}, new File[]{lead});

        System.out.println("FIXTURE_MIX " + Hashing.sha256(mix));
        System.out.println("SAFE_ZIP " + Hashing.sha256(safeZip));
        System.out.println("DELAYED_ZIP " + Hashing.sha256(delayedZip));
        System.out.println("UNSAFE_ZIP " + Hashing.sha256(unsafeZip));
    }

    private static void render(File mixFile, File leadFile, File guitarFile,
                               File delayedFile) throws IOException {
        try (OutputStream mix = output(mixFile);
             OutputStream lead = output(leadFile);
             OutputStream guitar = output(guitarFile);
             OutputStream delayed = output(delayedFile)) {
            writeHeader(mix);
            writeHeader(lead);
            writeHeader(guitar);
            writeHeader(delayed);
            for (long frame = 0; frame < FRAMES; frame++) {
                double seconds = frame / (double) RATE;
                int leadSample = quantize(lead(seconds));
                int guitarLeft = quantize(guitar(seconds, true));
                int guitarRight = quantize(guitar(seconds, false));
                write24(lead, leadSample);
                write24(lead, leadSample);
                write24(guitar, guitarLeft);
                write24(guitar, guitarRight);
                write24(mix, leadSample + guitarLeft);
                write24(mix, leadSample + guitarRight);

                if (frame < DELAY_FRAMES) {
                    write24(delayed, 0);
                    write24(delayed, 0);
                } else {
                    double delayedSeconds = (frame - DELAY_FRAMES) / (double) RATE;
                    write24(delayed, quantize(guitar(delayedSeconds, true)));
                    write24(delayed, quantize(guitar(delayedSeconds, false)));
                }
            }
        }
    }

    private static double lead(double seconds) {
        double fade = edgeFade(seconds);
        double phrasePosition = seconds % 4.0;
        if (phrasePosition > 3.45) return 0.0;
        double[] levels = {0.044, 0.028, 0.052, 0.034, 0.046, 0.025, 0.049, 0.036};
        int phrase = Math.min(levels.length - 1, (int) (seconds / 4.0));
        double amplitude = levels[phrase] * fade;
        double body = Math.sin(2.0 * Math.PI * 196.0 * seconds)
                + 0.44 * Math.sin(2.0 * Math.PI * 392.0 * seconds + 0.3)
                + 0.20 * Math.sin(2.0 * Math.PI * 784.0 * seconds + 0.8)
                + 0.09 * Math.sin(2.0 * Math.PI * 1568.0 * seconds + 0.1);
        double syllable = 0.78 + 0.22 * Math.sin(2.0 * Math.PI * 2.1 * seconds);
        double sibilanceEnvelope = burst(seconds, 3.15, 0.18)
                + burst(seconds, 6.25, 0.16)
                + burst(seconds, 8.05, 0.14);
        double sibilance = 0.020 * sibilanceEnvelope * fade
                * (Math.sin(2.0 * Math.PI * 6200.0 * seconds)
                + 0.55 * Math.sin(2.0 * Math.PI * 7600.0 * seconds + 0.4));
        return amplitude * syllable * body + sibilance;
    }

    private static double guitar(double seconds, boolean left) {
        double fade = edgeFade(seconds);
        double position = left ? 1.0 : 0.62;
        double base = fade * position
                * (0.020 * Math.sin(2.0 * Math.PI * 90.0 * seconds + 0.9)
                + 0.025 * Math.sin(2.0 * Math.PI * 329.63 * seconds + 0.2)
                + 0.014 * Math.sin(2.0 * Math.PI * 659.26 * seconds + 0.7)
                + 0.006 * Math.sin(2.0 * Math.PI * 1318.5 * seconds + 1.1)
                + 0.030 * Math.sin(2.0 * Math.PI * 2500.0 * seconds + 0.35)
                + 0.024 * Math.sin(2.0 * Math.PI * 5800.0 * seconds + 0.65)
                + 0.018 * Math.sin(2.0 * Math.PI * 11000.0 * seconds + 1.25));
        double local = burst(seconds, 5.45, 0.52)
                + 0.72 * burst(seconds, 8.25, 0.34);
        double presence = 0.180 * local * fade * position
                * (Math.sin(2.0 * Math.PI * 2350.0 * seconds + 0.4)
                + 0.65 * Math.sin(2.0 * Math.PI * 3150.0 * seconds + 1.0)
                + 0.25 * Math.sin(2.0 * Math.PI * 4700.0 * seconds + 0.1));
        return base + presence;
    }

    private static double edgeFade(double seconds) {
        double in = Math.min(1.0, seconds / 0.45);
        double out = Math.min(1.0, (DURATION_SECONDS - seconds) / 2.0);
        return smooth(Math.max(0.0, in)) * smooth(Math.max(0.0, out));
    }

    private static double burst(double seconds, double centre, double halfWidth) {
        double distance = Math.abs(seconds - centre) / halfWidth;
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

    private static OutputStream output(File file) throws IOException {
        return new BufferedOutputStream(new FileOutputStream(file), 256 * 1024);
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

    private static void zip(File output, String[] names, File[] files)
            throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(
                new FileOutputStream(output), 256 * 1024))) {
            zip.setLevel(1);
            byte[] buffer = new byte[256 * 1024];
            for (int index = 0; index < files.length; index++) {
                ZipEntry entry = new ZipEntry(names[index]);
                entry.setTime(0L);
                zip.putNextEntry(entry);
                try (InputStream input = new BufferedInputStream(
                        new FileInputStream(files[index]), 256 * 1024)) {
                    int read;
                    while ((read = input.read(buffer)) >= 0) {
                        if (read > 0) zip.write(buffer, 0, read);
                    }
                }
                zip.closeEntry();
            }
        }
    }
}
