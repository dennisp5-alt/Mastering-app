package com.dennis.aimasteringlab;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Builds the creative-authority stereo source used by Stems Only mode. */
final class StemPremixBuilder {
    static final class Result {
        File premix;
        final List<StemInventory.Item> stems = new ArrayList<>();
        final List<String> roles = new ArrayList<>();
        int stemCount;
        long frames;
        double durationSeconds;
        double rawPeak;
        double rawPeakDbfs;
        double premixAttenuationDb;
        double linearScale = 1.0;

        String compactSummary() {
            return String.format(Locale.US,
                    "%d CRC-verified stems • %.3f s • raw peak %.3f dBFS • shared premix gain %+.3f dB",
                    stemCount, durationSeconds, rawPeakDbfs, premixAttenuationDb);
        }
    }

    private static final int MAX_STEMS = 32;
    private static final int BUFFER_FRAMES = 4096;
    private static final double PREMIX_PEAK = 0.5011872336272722; // -6 dBFS

    private StemPremixBuilder() {}

    static Result build(File archive, File workDirectory,
                        AudioAnalyzer.Progress progress) throws IOException {
        if (archive == null || !archive.isFile()) {
            throw new IOException("Select a stem ZIP first");
        }
        if (!workDirectory.exists() && !workDirectory.mkdirs()) {
            throw new IOException("Could not create the Stem Lab cache");
        }
        Result out = new Result();
        out.stems.addAll(StemInventory.inspect(archive, progress));
        ArrayList<StemInventory.Item> wavs = new ArrayList<>();
        for (StemInventory.Item item : out.stems) {
            if (item.directory) continue;
            if (item.error != null || !item.crcVerified) {
                throw new IOException("Stem archive entry failed decode/CRC validation: "
                        + item.path);
            }
            if (!item.wav) continue;
            if (item.wavInfo == null || item.wavInfo.sampleRate != 48000
                    || (item.wavInfo.channels != 1 && item.wavInfo.channels != 2)) {
                throw new IOException("Every audio stem must be a 48 kHz mono/stereo WAV: "
                        + item.path);
            }
            wavs.add(item);
            out.frames = Math.max(out.frames, item.wavInfo.frames);
            out.roles.add(role(item.path));
        }
        out.stemCount = wavs.size();
        if (out.stemCount < 1 || out.stemCount > MAX_STEMS) {
            throw new IOException("Stems Only mode accepts 1–32 intact WAV stems");
        }
        if (out.frames < 48000) throw new IOException("Stem set is too short to master");
        long dataBytes = out.frames * 8L;
        if (dataBytes <= 0L || dataBytes > 0xfffffff0L) {
            throw new IOException("Stem set exceeds the standard RIFF/WAVE size limit");
        }
        out.durationSeconds = out.frames / 48000.0;
        File rawPremix = new File(workDirectory, "stem-authority-raw-float.wav");
        File premix = new File(workDirectory, "stem-authority-premix-float.wav");
        try {
            out.rawPeak = writeRawPremix(archive, wavs, rawPremix, out.frames,
                    progress);
            if (!Double.isFinite(out.rawPeak) || out.rawPeak <= 1.0e-12) {
                throw new IOException("The selected stems contain no usable audio");
            }
            out.rawPeakDbfs = AudioAnalyzer.toDb(out.rawPeak);
            out.linearScale = Math.min(1.0, PREMIX_PEAK / out.rawPeak);
            out.premixAttenuationDb = 20.0 * Math.log10(out.linearScale);
            scale(rawPremix, premix, out.linearScale, out.frames, progress);
            out.premix = premix;
            return out;
        } catch (IOException error) {
            delete(premix);
            throw error;
        } finally {
            delete(rawPremix);
        }
    }

    private static double writeRawPremix(File archive,
                                          List<StemInventory.Item> stems,
                                          File output, long frames,
                                          AudioAnalyzer.Progress progress)
            throws IOException {
        try (ZipFile zip = new ZipFile(archive);
             OutputStream fileOut = new BufferedOutputStream(
                     new FileOutputStream(output), 256 * 1024)) {
            WavFile.Reader[] readers = new WavFile.Reader[stems.size()];
            float[][][] buffers = new float[stems.size()][][];
            try {
                for (int i = 0; i < stems.size(); i++) {
                    ZipEntry entry = zip.getEntry(stems.get(i).path);
                    if (entry == null || entry.isDirectory()) {
                        throw new IOException("Stem entry disappeared: " + stems.get(i).path);
                    }
                    InputStream input = zip.getInputStream(entry);
                    readers[i] = WavFile.open(input);
                    buffers[i] = new float[readers[i].info.channels][BUFFER_FRAMES];
                }
                writeFloatHeader(fileOut, 48000, 2, frames * 8L);
                byte[] bytes = new byte[BUFFER_FRAMES * 8];
                long position = 0L;
                double peak = 0.0;
                while (position < frames) {
                    int count = (int) Math.min(BUFFER_FRAMES, frames - position);
                    int[] read = new int[readers.length];
                    for (int stem = 0; stem < readers.length; stem++) {
                        for (float[] channel : buffers[stem]) {
                            Arrays.fill(channel, 0, count, 0.0f);
                        }
                        if (position < readers[stem].info.frames) {
                            read[stem] = Math.max(0,
                                    readers[stem].readFrames(buffers[stem], count));
                        }
                    }
                    int p = 0;
                    for (int frame = 0; frame < count; frame++) {
                        double left = 0.0;
                        double right = 0.0;
                        for (int stem = 0; stem < readers.length; stem++) {
                            if (frame >= read[stem]) continue;
                            double l = buffers[stem][0][frame];
                            double r = readers[stem].info.channels == 2
                                    ? buffers[stem][1][frame] : l;
                            if (!Double.isFinite(l) || !Double.isFinite(r)) {
                                throw new IOException("Non-finite stem sample was rejected: "
                                        + stems.get(stem).path);
                            }
                            left += l;
                            right += r;
                        }
                        peak = Math.max(peak, Math.max(Math.abs(left), Math.abs(right)));
                        p = putFloat(bytes, p, (float) left);
                        p = putFloat(bytes, p, (float) right);
                    }
                    fileOut.write(bytes, 0, p);
                    position += count;
                    progress.update("Stems Only: building lossless 32-bit-float premix",
                            (int) Math.min(94, Math.round(94.0 * position / frames)));
                    if (Thread.currentThread().isInterrupted()) {
                        throw new IOException("Stem premix cancelled");
                    }
                }
                return peak;
            } finally {
                for (WavFile.Reader reader : readers) {
                    if (reader != null) try { reader.close(); } catch (IOException ignored) {}
                }
            }
        }
    }

    private static void scale(File source, File output, double scale, long expectedFrames,
                              AudioAnalyzer.Progress progress) throws IOException {
        try (WavFile.Reader reader = WavFile.open(new FileInputStream(source));
             OutputStream out = new BufferedOutputStream(
                     new FileOutputStream(output), 256 * 1024)) {
            writeFloatHeader(out, 48000, 2, expectedFrames * 8L);
            float[][] samples = new float[2][BUFFER_FRAMES];
            byte[] bytes = new byte[BUFFER_FRAMES * 8];
            long done = 0L;
            int count;
            while ((count = reader.readFrames(samples, BUFFER_FRAMES)) > 0) {
                int p = 0;
                for (int frame = 0; frame < count; frame++) {
                    p = putFloat(bytes, p, (float) (samples[0][frame] * scale));
                    p = putFloat(bytes, p, (float) (samples[1][frame] * scale));
                }
                out.write(bytes, 0, p);
                done += count;
                progress.update("Stems Only: setting safe premix headroom",
                        (int) Math.min(98, Math.round(98.0 * done / expectedFrames)));
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("Stem premix cancelled");
                }
            }
            if (done != expectedFrames) throw new IOException("Stem premix frame count changed");
        }
    }

    private static int putFloat(byte[] output, int offset, float value) {
        int bits = Float.floatToIntBits(value);
        output[offset++] = (byte) bits;
        output[offset++] = (byte) (bits >>> 8);
        output[offset++] = (byte) (bits >>> 16);
        output[offset++] = (byte) (bits >>> 24);
        return offset;
    }

    private static void writeFloatHeader(OutputStream out, int sampleRate,
                                         int channels, long dataBytes) throws IOException {
        int blockAlign = channels * 4;
        LittleEndian.writeAscii(out, "RIFF");
        LittleEndian.writeU32(out, 36L + dataBytes);
        LittleEndian.writeAscii(out, "WAVE");
        LittleEndian.writeAscii(out, "fmt ");
        LittleEndian.writeU32(out, 16);
        LittleEndian.writeU16(out, 3);
        LittleEndian.writeU16(out, channels);
        LittleEndian.writeU32(out, sampleRate);
        LittleEndian.writeU32(out, (long) sampleRate * blockAlign);
        LittleEndian.writeU16(out, blockAlign);
        LittleEndian.writeU16(out, 32);
        LittleEndian.writeAscii(out, "data");
        LittleEndian.writeU32(out, dataBytes);
    }

    static String role(String path) {
        String name = path == null ? "" : path.toLowerCase(Locale.US);
        if (contains(name, "lead vocal", "lead vox")) return "lead_vocal";
        if (contains(name, "vocal", "vox", "choir", "harmony")) return "vocal";
        if (contains(name, "drum", "kick", "snare", "percussion")) return "drums";
        if (contains(name, "bass", "sub")) return "bass";
        if (contains(name, "guitar", "gtr")) return "guitar";
        if (contains(name, "piano", "keys", "keyboard", "organ")) return "keys";
        if (contains(name, "synth", "pad")) return "synth";
        if (contains(name, "string", "violin", "fiddle", "cello")) return "strings";
        if (contains(name, "brass", "horn", "sax")) return "brass";
        if (contains(name, "fx", "effect", "ambience")) return "effects";
        return "other";
    }

    private static boolean contains(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }

    private static void delete(File file) {
        if (file != null && file.exists() && !file.delete()) file.deleteOnExit();
    }
}
