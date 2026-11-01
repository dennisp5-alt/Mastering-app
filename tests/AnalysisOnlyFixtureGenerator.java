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

/** Builds aligned but deliberately incomplete/separation-like stem evidence. */
public final class AnalysisOnlyFixtureGenerator {
    private static final double SCALE = 8388608.0;

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Usage: AnalysisOnlyFixtureGenerator stem-fixture-directory");
        }
        File directory = new File(args[0]);
        File source = new File(directory, "StemFixtureMix.wav");
        File mix = new File(directory, "AnalysisOnlyMix.wav");
        File zip = new File(directory, "AnalysisOnlyStems.zip");
        renderIncompleteMix(source, mix);
        zip(zip,
                new String[]{"Lead Vocal.wav", "Electric Guitar.wav"},
                new File[]{new File(directory, "Lead Vocal.wav"),
                        new File(directory, "Electric Guitar.wav")});
        System.out.println("ANALYSIS_ONLY_MIX " + Hashing.sha256(mix));
        System.out.println("ANALYSIS_ONLY_ZIP " + Hashing.sha256(zip));
    }

    private static void renderIncompleteMix(File source, File output)
            throws IOException {
        try (WavFile.Reader reader = WavFile.open(new FileInputStream(source));
             OutputStream raw = new BufferedOutputStream(
                     new FileOutputStream(output), 256 * 1024)) {
            if (reader.info.sampleRate != 48000 || reader.info.channels != 2) {
                throw new IOException("Expected the controlled 48 kHz stereo fixture");
            }
            writeHeader(raw, reader.info.sampleRate, reader.info.frames);
            float[][] samples = new float[2][4096];
            long absolute = 0L;
            int read;
            while ((read = reader.readFrames(samples, samples[0].length)) > 0) {
                for (int frame = 0; frame < read; frame++, absolute++) {
                    double seconds = absolute / (double) reader.info.sampleRate;
                    double fade = edgeFade(seconds, reader.info.durationSeconds);
                    double unstemmed = fade * (0.060 * Math.sin(2.0 * Math.PI * 123.47 * seconds)
                            + 0.052 * Math.sin(2.0 * Math.PI * 246.94 * seconds + 0.4)
                            + 0.044 * Math.sin(2.0 * Math.PI * 493.88 * seconds + 0.9)
                            + 0.030 * Math.sin(2.0 * Math.PI * 1800.0 * seconds + 0.2));
                    double leftBed = unstemmed
                            + 0.025 * fade * Math.sin(2.0 * Math.PI * 73.42 * seconds + 0.7);
                    double rightBed = 0.72 * unstemmed
                            + 0.021 * fade * Math.sin(2.0 * Math.PI * 92.50 * seconds + 1.1);
                    write24(raw, quantize(samples[0][frame] + leftBed));
                    write24(raw, quantize(samples[1][frame] + rightBed));
                }
            }
            if (absolute != reader.info.frames) {
                throw new IOException("Analysis-only fixture frame accounting failed");
            }
        }
    }

    private static double edgeFade(double seconds, double duration) {
        double in = Math.min(1.0, seconds / 0.45);
        double out = Math.min(1.0, (duration - seconds) / 2.0);
        return smooth(Math.max(0.0, in)) * smooth(Math.max(0.0, out));
    }

    private static double smooth(double value) {
        double x = Math.max(0.0, Math.min(1.0, value));
        return x * x * (3.0 - 2.0 * x);
    }

    private static int quantize(double value) {
        return (int) Math.max(-8388608, Math.min(8388607,
                Math.round(value * SCALE)));
    }

    private static void writeHeader(OutputStream out, int rate, long frames)
            throws IOException {
        long dataBytes = frames * 2L * 3L;
        LittleEndian.writeAscii(out, "RIFF");
        LittleEndian.writeU32(out, 36L + dataBytes);
        LittleEndian.writeAscii(out, "WAVE");
        LittleEndian.writeAscii(out, "fmt ");
        LittleEndian.writeU32(out, 16);
        LittleEndian.writeU16(out, 1);
        LittleEndian.writeU16(out, 2);
        LittleEndian.writeU32(out, rate);
        LittleEndian.writeU32(out, rate * 6L);
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
