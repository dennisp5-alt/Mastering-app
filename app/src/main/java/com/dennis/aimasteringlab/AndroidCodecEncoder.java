package com.dennis.aimasteringlab;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

final class AndroidCodecEncoder {
    static final class Result {
        File file;
        String codec;
        int bitrate;
        boolean available;
        String encoderName;
        String error;
    }

    private AndroidCodecEncoder() {}

    static Result encodeAac(File wav, File output, int bitrate,
                            AudioAnalyzer.Progress progress) {
        Result result = new Result();
        result.file = output;
        result.codec = "AAC-LC in M4A";
        result.bitrate = bitrate;
        MediaCodec codec = null;
        MediaMuxer muxer = null;
        boolean muxerStarted = false;
        try (Pcm16Source source = new Pcm16Source(wav, 0x414143444d53L)) {
            MediaFormat format = MediaFormat.createAudioFormat(
                    MediaFormat.MIMETYPE_AUDIO_AAC, source.sampleRate, source.channels);
            format.setInteger(MediaFormat.KEY_AAC_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024);
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            result.encoderName = codec.getName();
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            codec.start();
            muxer = new MediaMuxer(output.getAbsolutePath(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputEnded = false;
            boolean outputEnded = false;
            int track = -1;
            while (!outputEnded) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("Codec processing cancelled");
                if (!inputEnded) {
                    int index = codec.dequeueInputBuffer(10000);
                    if (index >= 0) {
                        ByteBuffer buffer = codec.getInputBuffer(index);
                        if (buffer == null) throw new IOException("AAC encoder returned no input buffer");
                        buffer.clear();
                        long presentation = source.presentationTimeUs();
                        int bytes = source.fill(buffer);
                        if (bytes <= 0) {
                            codec.queueInputBuffer(index, 0, 0, presentation,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputEnded = true;
                        } else {
                            codec.queueInputBuffer(index, 0, bytes, presentation, 0);
                        }
                    }
                }
                int outputIndex = codec.dequeueOutputBuffer(info, 10000);
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (muxerStarted) throw new IOException("AAC output format changed twice");
                    track = muxer.addTrack(codec.getOutputFormat());
                    muxer.start();
                    muxerStarted = true;
                } else if (outputIndex >= 0) {
                    ByteBuffer buffer = codec.getOutputBuffer(outputIndex);
                    if (buffer != null && info.size > 0
                            && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        if (!muxerStarted) throw new IOException("AAC samples arrived before muxer format");
                        buffer.position(info.offset);
                        buffer.limit(info.offset + info.size);
                        muxer.writeSampleData(track, buffer, info);
                    }
                    outputEnded = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    codec.releaseOutputBuffer(outputIndex, false);
                }
                progress.update("Encoding 256 kbps AAC", source.percent());
            }
            result.available = true;
        } catch (Exception e) {
            result.error = message(e);
            if (output.exists() && !output.delete()) output.deleteOnExit();
        } finally {
            if (codec != null) {
                try { codec.stop(); } catch (Exception ignored) {}
                codec.release();
            }
            if (muxer != null) {
                if (muxerStarted) try { muxer.stop(); } catch (Exception ignored) {}
                muxer.release();
            }
        }
        return result;
    }

    static Result encodeMp3(File wav, File output, int bitrate,
                            AudioAnalyzer.Progress progress) {
        Result result = new Result();
        result.file = output;
        result.codec = "MP3";
        result.bitrate = bitrate;
        MediaCodec codec = null;
        try (Pcm16Source source = new Pcm16Source(wav, 0x4d5033444d53L);
             OutputStream encoded = new BufferedOutputStream(new FileOutputStream(output), 128 * 1024)) {
            MediaFormat format = MediaFormat.createAudioFormat(
                    MediaFormat.MIMETYPE_AUDIO_MPEG, source.sampleRate, source.channels);
            format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024);
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_MPEG);
            result.encoderName = codec.getName();
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            codec.start();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputEnded = false;
            boolean outputEnded = false;
            byte[] copy = new byte[128 * 1024];
            while (!outputEnded) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("Codec processing cancelled");
                if (!inputEnded) {
                    int index = codec.dequeueInputBuffer(10000);
                    if (index >= 0) {
                        ByteBuffer buffer = codec.getInputBuffer(index);
                        if (buffer == null) throw new IOException("MP3 encoder returned no input buffer");
                        buffer.clear();
                        long presentation = source.presentationTimeUs();
                        int bytes = source.fill(buffer);
                        if (bytes <= 0) {
                            codec.queueInputBuffer(index, 0, 0, presentation,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputEnded = true;
                        } else {
                            codec.queueInputBuffer(index, 0, bytes, presentation, 0);
                        }
                    }
                }
                int outputIndex = codec.dequeueOutputBuffer(info, 10000);
                if (outputIndex >= 0) {
                    ByteBuffer buffer = codec.getOutputBuffer(outputIndex);
                    if (buffer != null && info.size > 0
                            && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        buffer.position(info.offset);
                        buffer.limit(info.offset + info.size);
                        while (buffer.hasRemaining()) {
                            int count = Math.min(buffer.remaining(), copy.length);
                            buffer.get(copy, 0, count);
                            encoded.write(copy, 0, count);
                        }
                    }
                    outputEnded = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    codec.releaseOutputBuffer(outputIndex, false);
                }
                progress.update("Encoding 320 kbps MP3", source.percent());
            }
            encoded.flush();
            result.available = output.length() > 0;
            if (!result.available) result.error = "The device MP3 encoder returned no audio frames";
        } catch (Exception e) {
            result.error = message(e);
            if (output.exists() && !output.delete()) output.deleteOnExit();
        } finally {
            if (codec != null) {
                try { codec.stop(); } catch (Exception ignored) {}
                codec.release();
            }
        }
        return result;
    }

    private static String message(Exception e) {
        String value = e.getMessage();
        return value == null || value.trim().isEmpty() ? e.getClass().getSimpleName() : value;
    }

    private static final class Pcm16Source implements AutoCloseable {
        final WavFile.Reader reader;
        final int sampleRate;
        final int channels;
        final long totalFrames;
        final float[][] samples;
        final Random dither;
        long submittedFrames;
        int samplePosition;
        int sampleCount;

        Pcm16Source(File wav, long seed) throws IOException {
            reader = WavFile.open(new FileInputStream(wav));
            sampleRate = reader.info.sampleRate;
            channels = reader.info.channels;
            totalFrames = reader.info.frames;
            samples = new float[channels][4096];
            dither = new Random(seed ^ totalFrames ^ sampleRate);
        }

        long presentationTimeUs() {
            return submittedFrames * 1_000_000L / sampleRate;
        }

        int percent() {
            return (int) Math.min(100, Math.round(100.0 * submittedFrames / totalFrames));
        }

        int fill(ByteBuffer buffer) throws IOException {
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            int start = buffer.position();
            while (buffer.remaining() >= channels * 2) {
                if (samplePosition >= sampleCount) {
                    sampleCount = reader.readFrames(samples, samples[0].length);
                    samplePosition = 0;
                    if (sampleCount < 0) break;
                }
                for (int channel = 0; channel < channels; channel++) {
                    double tpdf = dither.nextDouble() - dither.nextDouble();
                    long q = Math.round(samples[channel][samplePosition] * 32768.0 + tpdf);
                    q = Math.max(-32768L, Math.min(32767L, q));
                    buffer.putShort((short) q);
                }
                samplePosition++;
                submittedFrames++;
            }
            return buffer.position() - start;
        }

        @Override public void close() throws IOException {
            reader.close();
        }
    }
}
