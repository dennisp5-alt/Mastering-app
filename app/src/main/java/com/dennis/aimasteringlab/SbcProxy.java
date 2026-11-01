package com.dennis.aimasteringlab;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Random;

final class SbcProxy {
    static final class Result {
        File decodedProxyWav;
        AnalysisResult verification;
        String method = "328 kbps Bluetooth/SBC proxy: 48 kHz joint-stereo bandwidth and 16-bit subband-quantization stress approximation";
    }

    private SbcProxy() {}

    static Result render(File master, File output, AudioAnalyzer.Progress progress)
            throws IOException {
        Result result = new Result();
        result.decodedProxyWav = output;
        try (WavFile.Reader reader = WavFile.open(new FileInputStream(master));
             OutputStream raw = new BufferedOutputStream(new FileOutputStream(output), 256 * 1024)) {
            if (reader.info.sampleRate != 48000 || reader.info.channels != 2) {
                throw new IOException("SBC proxy expects a 48 kHz stereo master");
            }
            long dataBytes = reader.info.frames * 4L;
            MasterRenderer.writeHeader(raw, 48000, 2, dataBytes, 16);
            Biquad lowL = Biquad.lowPass(48000, 17800.0);
            Biquad lowR = Biquad.lowPass(48000, 17800.0);
            Random random = new Random(0x534243333238L ^ reader.info.frames);
            float[][] samples = new float[2][4096];
            byte[] bytes = new byte[4096 * 4];
            long framesDone = 0;
            int read;
            while ((read = reader.readFrames(samples, 4096)) > 0) {
                int position = 0;
                for (int frame = 0; frame < read; frame++) {
                    double l = lowL.process(samples[0][frame]);
                    double r = lowR.process(samples[1][frame]);
                    // A small, signal-dependent mid/side precision reduction
                    // approximates the most translation-relevant SBC stress
                    // without pretending to be a bit-exact vendor codec.
                    double mid = 0.5 * (l + r);
                    double side = 0.5 * (l - r);
                    double sideStep = Math.max(1.0 / 32768.0,
                            Math.abs(mid) * Math.pow(10.0, -72.0 / 20.0));
                    side = Math.rint(side / sideStep) * sideStep;
                    double[] outputFrame = {mid + side, mid - side};
                    for (double value : outputFrame) {
                        double tpdf = random.nextDouble() - random.nextDouble();
                        long q = Math.round(value * 32768.0 + tpdf);
                        q = Math.max(-32768L, Math.min(32767L, q));
                        int sample = (int) q;
                        bytes[position++] = (byte) sample;
                        bytes[position++] = (byte) (sample >>> 8);
                    }
                }
                raw.write(bytes, 0, position);
                framesDone += read;
                progress.update("Rendering 328 kbps Bluetooth/SBC proxy",
                        (int) Math.min(95, Math.round(95.0 * framesDone / reader.info.frames)));
            }
        } catch (IOException e) {
            if (output.exists() && !output.delete()) output.deleteOnExit();
            throw e;
        }
        result.verification = AudioAnalyzer.analyze(output.getName(),
                () -> new FileInputStream(output), progress);
        return result;
    }
}
