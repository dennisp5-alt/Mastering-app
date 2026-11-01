package com.dennis.aimasteringlab;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

final class Hashing {
    private Hashing() {}

    static String sha256(File file) throws IOException {
        try (InputStream input = new FileInputStream(file)) {
            return sha256(input);
        }
    }

    static String sha256(InputStream source) throws IOException {
        try (InputStream input = source) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[256 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) digest.update(buffer, 0, read);
                if (Thread.currentThread().isInterrupted()) throw new IOException("Checksum cancelled");
            }
            StringBuilder out = new StringBuilder(64);
            for (byte value : digest.digest()) {
                out.append(String.format(Locale.US, "%02x", value & 0xff));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException(impossible);
        }
    }
}
