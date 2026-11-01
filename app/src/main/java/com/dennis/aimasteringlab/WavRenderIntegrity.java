package com.dennis.aimasteringlab;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/** Transaction boundary for generated WAV files. */
final class WavRenderIntegrity {
    private WavRenderIntegrity() {}

    static File stagingFile(File destination, String label) throws IOException {
        File parent = destination.getAbsoluteFile().getParentFile();
        if (parent == null) throw new IOException("Rendered WAV has no parent directory");
        if (!parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create the rendered WAV directory");
        }
        String safe = label == null ? "render" : label.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (safe.length() < 3) safe = "render";
        return File.createTempFile("daml-" + safe + "-", ".part", parent);
    }

    static void commit(File staging, File destination, long expectedDataBytes,
                       int expectedSampleRate, int expectedChannels,
                       int expectedBits, boolean expectedFloat) throws IOException {
        verify(staging, expectedDataBytes, expectedSampleRate,
                expectedChannels, expectedBits, expectedFloat);
        if (destination.exists() && !destination.delete()) {
            throw new IOException("Could not replace the prior candidate WAV");
        }
        if (!staging.renameTo(destination)) {
            throw new IOException("Could not atomically publish the verified candidate WAV");
        }
        verify(destination, expectedDataBytes, expectedSampleRate,
                expectedChannels, expectedBits, expectedFloat);
    }

    static void verify(File file, long expectedDataBytes,
                       int expectedSampleRate, int expectedChannels,
                       int expectedBits, boolean expectedFloat) throws IOException {
        long expectedLength = 44L + expectedDataBytes;
        long actualLength = file.length();
        if (!file.isFile() || actualLength != expectedLength) {
            throw new IOException("Rendered WAV length mismatch: expected "
                    + expectedLength + " bytes, found " + actualLength);
        }
        try (WavFile.Reader reader = WavFile.open(new FileInputStream(file))) {
            if (reader.info.dataBytes != expectedDataBytes
                    || reader.info.sampleRate != expectedSampleRate
                    || reader.info.channels != expectedChannels
                    || reader.info.bitsPerSample != expectedBits
                    || reader.info.floatingPoint != expectedFloat) {
                throw new IOException("Rendered WAV header or format verification failed");
            }
        }
    }

    static void discard(File file) {
        if (file != null && file.exists() && !file.delete()) file.deleteOnExit();
    }
}
