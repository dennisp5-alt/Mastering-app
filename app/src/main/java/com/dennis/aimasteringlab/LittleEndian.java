package com.dennis.aimasteringlab;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

final class LittleEndian {
    private LittleEndian() {}

    static int u16(byte[] b, int o) {
        return (b[o] & 0xff) | ((b[o + 1] & 0xff) << 8);
    }

    static int s24(byte[] b, int o) {
        int v = (b[o] & 0xff) | ((b[o + 1] & 0xff) << 8) | ((b[o + 2] & 0xff) << 16);
        if ((v & 0x00800000) != 0) v |= 0xff000000;
        return v;
    }

    static int s32(byte[] b, int o) {
        return (b[o] & 0xff)
                | ((b[o + 1] & 0xff) << 8)
                | ((b[o + 2] & 0xff) << 16)
                | (b[o + 3] << 24);
    }

    static long u32(byte[] b, int o) {
        return ((long) b[o] & 0xff)
                | (((long) b[o + 1] & 0xff) << 8)
                | (((long) b[o + 2] & 0xff) << 16)
                | (((long) b[o + 3] & 0xff) << 24);
    }

    static void readFully(InputStream in, byte[] b, int off, int len) throws IOException {
        int n = 0;
        while (n < len) {
            int r = in.read(b, off + n, len - n);
            if (r < 0) throw new EOFException("Unexpected end of file");
            n += r;
        }
    }

    static void skipFully(InputStream in, long len) throws IOException {
        long left = len;
        byte[] scratch = new byte[8192];
        while (left > 0) {
            long skipped = in.skip(left);
            if (skipped > 0) {
                left -= skipped;
                continue;
            }
            int r = in.read(scratch, 0, (int) Math.min(scratch.length, left));
            if (r < 0) throw new EOFException("Unexpected end while skipping chunk");
            left -= r;
        }
    }

    static void writeU16(OutputStream out, int value) throws IOException {
        out.write(value & 0xff);
        out.write((value >>> 8) & 0xff);
    }

    static void writeU32(OutputStream out, long value) throws IOException {
        out.write((int) (value & 0xff));
        out.write((int) ((value >>> 8) & 0xff));
        out.write((int) ((value >>> 16) & 0xff));
        out.write((int) ((value >>> 24) & 0xff));
    }

    static void writeAscii(OutputStream out, String text) throws IOException {
        for (int i = 0; i < text.length(); i++) out.write((byte) text.charAt(i));
    }
}
