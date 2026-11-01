package com.dennis.aimasteringlab;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Locale;

/**
 * Level-matched whole-song delta evidence used to distinguish a real sonic
 * mastering move from a delivery-only or sparse peak-control near-copy.
 *
 * This is an engineering proxy, not a listening verdict. Narrow proven repairs
 * are allowed to use their own event-specific promotion gates instead.
 */
final class MasteringDeltaEvidence {
    static final double BROAD_CHANGE_FLOOR_DB = -46.0;
    static final double NARROW_CHANGE_FLOOR_DB = -56.0;
    private static final int BUFFER_FRAMES = 8192;

    static final class Result {
        long frames;
        long samples;
        double gainMatchDb;
        double correlation;
        double referenceRmsDbfs;
        double candidateRmsDbfs;
        double levelMatchedResidualDbfs;
        double levelMatchedResidualRelativeDb;
        double dcDifference;
        boolean broadMeaningful;
        boolean narrowDetectable;
        String verdict;

        String summary() {
            return String.format(Locale.US,
                    "level-matched residual %.2f dB relative / %.2f dBFS; correlation %.8f; gain match %+.4f dB; %s",
                    levelMatchedResidualRelativeDb, levelMatchedResidualDbfs,
                    correlation, gainMatchDb, verdict);
        }
    }

    private MasteringDeltaEvidence() {}

    static Result compare(File reference, File candidate) throws IOException {
        if (reference == null || candidate == null
                || !reference.isFile() || !candidate.isFile()) {
            throw new IOException("Mastering delta comparison file is missing");
        }
        Result out = new Result();
        double sx = 0.0, sy = 0.0, sxx = 0.0, syy = 0.0, sxy = 0.0;
        long n = 0L;
        try (WavFile.Reader a = WavFile.open(new FileInputStream(reference));
             WavFile.Reader b = WavFile.open(new FileInputStream(candidate))) {
            if (a.info.sampleRate != b.info.sampleRate
                    || a.info.channels != b.info.channels
                    || a.info.frames != b.info.frames) {
                throw new IOException("Mastering delta comparison requires aligned WAV files");
            }
            float[][] x = new float[a.info.channels][BUFFER_FRAMES];
            float[][] y = new float[b.info.channels][BUFFER_FRAMES];
            long frames = 0L;
            int read;
            while ((read = a.readFrames(x, BUFFER_FRAMES)) > 0) {
                int other = b.readFrames(y, read);
                if (other != read) throw new IOException("Mastering delta candidate ended early");
                for (int f = 0; f < read; f++) {
                    for (int c = 0; c < a.info.channels; c++) {
                        double xv = finite(x[c][f]);
                        double yv = finite(y[c][f]);
                        sx += xv; sy += yv;
                        sxx += xv * xv; syy += yv * yv; sxy += xv * yv;
                        n++;
                    }
                }
                frames += read;
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("Mastering delta comparison cancelled");
                }
            }
            if (frames != a.info.frames || b.readFrames(y, 1) > 0) {
                throw new IOException("Mastering delta comparison changed sample count");
            }
            out.frames = frames;
        }
        out.samples = n;
        if (n < 2) throw new IOException("Mastering delta comparison has no usable samples");
        double count = n;
        double meanX = sx / count;
        double meanY = sy / count;
        double varX = Math.max(0.0, sxx - sx * sx / count);
        double varY = Math.max(0.0, syy - sy * sy / count);
        double cov = sxy - sx * sy / count;
        if (varX <= 1.0e-24 || varY <= 1.0e-24) {
            throw new IOException("Mastering delta comparison signal is effectively silent");
        }
        double alpha = cov / varX;
        double residualEnergy = Math.max(0.0,
                varY - 2.0 * alpha * cov + alpha * alpha * varX);
        double matchedReferenceEnergy = Math.max(1.0e-30,
                alpha * alpha * varX);
        out.gainMatchDb = 20.0 * Math.log10(Math.max(1.0e-15, Math.abs(alpha)));
        out.correlation = clamp(cov / Math.sqrt(varX * varY), -1.0, 1.0);
        out.referenceRmsDbfs = 10.0 * Math.log10(Math.max(1.0e-30, varX / count));
        out.candidateRmsDbfs = 10.0 * Math.log10(Math.max(1.0e-30, varY / count));
        out.levelMatchedResidualDbfs = residualEnergy <= 1.0e-30 ? -300.0
                : 10.0 * Math.log10(residualEnergy / count);
        out.levelMatchedResidualRelativeDb = residualEnergy <= 1.0e-30 ? -300.0
                : 10.0 * Math.log10(residualEnergy / matchedReferenceEnergy);
        out.dcDifference = meanY - alpha * meanX;
        out.broadMeaningful = out.levelMatchedResidualRelativeDb >= BROAD_CHANGE_FLOOR_DB;
        out.narrowDetectable = out.levelMatchedResidualRelativeDb >= NARROW_CHANGE_FLOOR_DB;
        if (out.broadMeaningful) {
            out.verdict = "MEANINGFUL_BROAD_SONIC_DELTA";
        } else if (out.narrowDetectable) {
            out.verdict = "NARROW_OR_SPARSE_DELTA_ONLY";
        } else {
            out.verdict = "NEAR_COPY_AFTER_LEVEL_MATCH";
        }
        return out;
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
