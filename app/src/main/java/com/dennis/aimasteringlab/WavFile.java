package com.dennis.aimasteringlab;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class WavFile {
    static final class Info {
        int formatTag;
        int channels;
        int sampleRate;
        int bitsPerSample;
        int validBitsPerSample;
        long channelMask;
        int blockAlign;
        long dataBytes;
        long frames;
        double durationSeconds;
        boolean floatingPoint;

        String formatName() {
            return floatingPoint ? "IEEE float" : "PCM";
        }
    }

    static final class Reader implements Closeable {
        final Info info;
        private final InputStream in;
        private long dataRemaining;
        private byte[] byteBuffer = new byte[0];

        private Reader(InputStream in, Info info) {
            this.in = in;
            this.info = info;
            this.dataRemaining = info.dataBytes;
        }

        int readFrames(float[][] channels, int maxFrames) throws IOException {
            if (channels.length < info.channels) throw new IllegalArgumentException("Channel buffer too small");
            if (dataRemaining <= 0) return -1;
            int frames = (int) Math.min(maxFrames, dataRemaining / info.blockAlign);
            int bytes = frames * info.blockAlign;
            if (byteBuffer.length < bytes) byteBuffer = new byte[bytes];
            LittleEndian.readFully(in, byteBuffer, 0, bytes);
            dataRemaining -= bytes;

            // v0.15.0: decode the dominant stereo/24-bit candidate format without
            // branching on format or channel in every single sample. Identical
            // s24 conversion and division preserve all float sample bits.
            if (info.channels == 2 && !info.floatingPoint && info.bitsPerSample == 24) {
                float[] left = channels[0];
                float[] right = channels[1];
                int p = 0;
                for (int f = 0; f < frames; f++, p += 6) {
                    left[f] = LittleEndian.s24(byteBuffer, p) / 8388608.0f;
                    right[f] = LittleEndian.s24(byteBuffer, p + 3) / 8388608.0f;
                }
                return frames;
            }
            int bytesPerSample = info.bitsPerSample / 8;
            int p = 0;
            for (int f = 0; f < frames; f++) {
                for (int c = 0; c < info.channels; c++) {
                    float sample;
                    if (info.floatingPoint) {
                        int bits = LittleEndian.s32(byteBuffer, p);
                        sample = Float.intBitsToFloat(bits);
                    } else if (info.bitsPerSample == 16) {
                        int v = (short) LittleEndian.u16(byteBuffer, p);
                        sample = v / 32768.0f;
                    } else if (info.bitsPerSample == 24) {
                        sample = LittleEndian.s24(byteBuffer, p) / 8388608.0f;
                    } else {
                        sample = LittleEndian.s32(byteBuffer, p) / 2147483648.0f;
                    }
                    channels[c][f] = sample;
                    p += bytesPerSample;
                }
            }
            return frames;
        }

        @Override public void close() throws IOException {
            in.close();
        }

        void skipFrames(long frames) throws IOException {
            if (frames < 0 || frames > dataRemaining / info.blockAlign)
                throw new IOException("WAV seek is outside the remaining audio");
            long bytes = frames * info.blockAlign;
            LittleEndian.skipFully(in, bytes);
            dataRemaining -= bytes;
        }
    }

    private WavFile() {}

    static Reader open(InputStream raw) throws IOException {
        BufferedInputStream in = raw instanceof BufferedInputStream
                ? (BufferedInputStream) raw : new BufferedInputStream(raw, 64 * 1024);
        byte[] riff = new byte[12];
        LittleEndian.readFully(in, riff, 0, riff.length);
        String riffId = new String(riff, 0, 4, StandardCharsets.US_ASCII);
        String waveId = new String(riff, 8, 4, StandardCharsets.US_ASCII);
        if (!"RIFF".equals(riffId) || !"WAVE".equals(waveId)) {
            throw new IOException("Only standard RIFF/WAVE files are supported");
        }

        Info info = null;
        byte[] chunkHeader = new byte[8];
        while (true) {
            LittleEndian.readFully(in, chunkHeader, 0, 8);
            String id = new String(chunkHeader, 0, 4, StandardCharsets.US_ASCII);
            long size = LittleEndian.u32(chunkHeader, 4);
            if ("fmt ".equals(id)) {
                if (size < 16 || size > 65536) throw new IOException("Invalid WAV format chunk");
                byte[] fmt = new byte[(int) size];
                LittleEndian.readFully(in, fmt, 0, fmt.length);
                info = parseFormat(fmt);
            } else if ("data".equals(id)) {
                if (info == null) throw new IOException("WAV data appeared before its format chunk");
                if (size <= 0 || size % info.blockAlign != 0) {
                    throw new IOException("Invalid or incomplete WAV audio data");
                }
                info.dataBytes = size;
                info.frames = size / info.blockAlign;
                info.durationSeconds = info.frames / (double) info.sampleRate;
                return new Reader(in, info);
            } else {
                LittleEndian.skipFully(in, size);
            }
            if ((size & 1L) != 0) LittleEndian.skipFully(in, 1);
        }
    }

    private static Info parseFormat(byte[] fmt) throws IOException {
        Info out = new Info();
        int originalTag = LittleEndian.u16(fmt, 0);
        out.formatTag = originalTag;
        out.channels = LittleEndian.u16(fmt, 2);
        out.sampleRate = (int) LittleEndian.u32(fmt, 4);
        out.blockAlign = LittleEndian.u16(fmt, 12);
        out.bitsPerSample = LittleEndian.u16(fmt, 14);
        out.validBitsPerSample = out.bitsPerSample;

        int resolvedTag = originalTag;
        if (originalTag == 0xfffe) {
            if (fmt.length < 40) throw new IOException("Incomplete WAVE_FORMAT_EXTENSIBLE header");
            out.validBitsPerSample = LittleEndian.u16(fmt, 18);
            out.channelMask = LittleEndian.u32(fmt, 20);
            resolvedTag = LittleEndian.u16(fmt, 24);
        }
        out.floatingPoint = resolvedTag == 3;
        if (resolvedTag != 1 && resolvedTag != 3) {
            throw new IOException("Unsupported WAV encoding: format tag " + resolvedTag);
        }
        if (out.channels < 1 || out.channels > 2) {
            throw new IOException("This build accepts mono or stereo WAV files; found " + out.channels + " channels");
        }
        if (out.sampleRate < 8000 || out.sampleRate > 384000) {
            throw new IOException("Unsupported sample rate: " + out.sampleRate);
        }
        if (out.floatingPoint && out.bitsPerSample != 32) {
            throw new IOException("Only 32-bit floating-point WAV is supported");
        }
        if (!out.floatingPoint && out.bitsPerSample != 16
                && out.bitsPerSample != 24 && out.bitsPerSample != 32) {
            throw new IOException("Only 16, 24 or 32-bit PCM WAV is supported");
        }
        int expectedAlign = out.channels * (out.bitsPerSample / 8);
        if (out.blockAlign != expectedAlign) throw new IOException("Packed WAV layout is not supported");
        return out;
    }
}
