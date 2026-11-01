package com.dennis.aimasteringlab;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

final class AndroidCodecDecoder {
    static final class Result {
        File decodedWav;
        String decoderName;
        String sourceMime;
        AnalysisResult verification;
        long decodedPcmBytes;
        String error;
    }

    private AndroidCodecDecoder() {}

    static Result decodeAndMeasure(File encoded, File decodedWav,
                                   AudioAnalyzer.Progress progress) {
        Result result = new Result();
        result.decodedWav = decodedWav;
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        try (RandomAccessFile output = new RandomAccessFile(decodedWav, "rw")) {
            extractor.setDataSource(encoded.getAbsolutePath());
            int track = findAudioTrack(extractor);
            if (track < 0) throw new IOException("No decodable audio track was found");
            extractor.selectTrack(track);
            MediaFormat inputFormat = extractor.getTrackFormat(track);
            String mime = inputFormat.getString(MediaFormat.KEY_MIME);
            if (mime == null) throw new IOException("Encoded track did not identify its codec");
            result.sourceMime = mime;
            inputFormat.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT);
            int sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            long durationUs = inputFormat.containsKey(MediaFormat.KEY_DURATION)
                    ? inputFormat.getLong(MediaFormat.KEY_DURATION) : -1L;
            decoder = MediaCodec.createDecoderByType(mime);
            result.decoderName = decoder.getName();
            decoder.configure(inputFormat, null, null, 0);
            decoder.start();

            output.setLength(0);
            output.seek(44);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputEnded = false;
            boolean outputEnded = false;
            byte[] copy = new byte[128 * 1024];
            long pcmBytes = 0;
            while (!outputEnded) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("Codec processing cancelled");
                if (!inputEnded) {
                    int inputIndex = decoder.dequeueInputBuffer(10000);
                    if (inputIndex >= 0) {
                        ByteBuffer buffer = decoder.getInputBuffer(inputIndex);
                        if (buffer == null) throw new IOException("Decoder returned no input buffer");
                        buffer.clear();
                        int size = extractor.readSampleData(buffer, 0);
                        if (size < 0) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputEnded = true;
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, size,
                                    extractor.getSampleTime(), extractor.getSampleFlags());
                            extractor.advance();
                        }
                    }
                }
                int outputIndex = decoder.dequeueOutputBuffer(info, 10000);
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat format = decoder.getOutputFormat();
                    sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    int encoding = format.containsKey(MediaFormat.KEY_PCM_ENCODING)
                            ? format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            : AudioFormat.ENCODING_PCM_16BIT;
                    if (encoding != AudioFormat.ENCODING_PCM_16BIT) {
                        throw new IOException("Decoder did not provide 16-bit PCM output");
                    }
                } else if (outputIndex >= 0) {
                    ByteBuffer buffer = decoder.getOutputBuffer(outputIndex);
                    if (buffer != null && info.size > 0
                            && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        buffer.position(info.offset);
                        buffer.limit(info.offset + info.size);
                        while (buffer.hasRemaining()) {
                            int count = Math.min(buffer.remaining(), copy.length);
                            buffer.get(copy, 0, count);
                            output.write(copy, 0, count);
                            pcmBytes += count;
                        }
                    }
                    outputEnded = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    decoder.releaseOutputBuffer(outputIndex, false);
                    if (durationUs > 0 && info.presentationTimeUs >= 0) {
                        progress.update("Decoding and measuring " + mime,
                                (int) Math.min(99, Math.round(100.0 * info.presentationTimeUs / durationUs)));
                    }
                }
            }
            if (channels < 1 || channels > 2 || pcmBytes % (channels * 2L) != 0) {
                throw new IOException("Decoded PCM framing was invalid");
            }
            ByteArrayOutputStream header = new ByteArrayOutputStream(44);
            MasterRenderer.writeHeader(header, sampleRate, channels, pcmBytes, 16);
            output.seek(0);
            output.write(header.toByteArray());
            result.decodedPcmBytes = pcmBytes;
        } catch (Exception e) {
            result.error = message(e);
            if (decodedWav.exists() && !decodedWav.delete()) decodedWav.deleteOnExit();
        } finally {
            if (decoder != null) {
                try { decoder.stop(); } catch (Exception ignored) {}
                decoder.release();
            }
            extractor.release();
        }
        if (result.error == null) {
            try {
                result.verification = AudioAnalyzer.analyze(encoded.getName() + " decoded",
                        () -> new FileInputStream(decodedWav), progress);
            } catch (Exception e) {
                result.error = message(e);
            }
        }
        return result;
    }

    private static int findAudioTrack(MediaExtractor extractor) {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            String mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) return i;
        }
        return -1;
    }

    private static String message(Exception e) {
        String value = e.getMessage();
        return value == null || value.trim().isEmpty() ? e.getClass().getSimpleName() : value;
    }
}
