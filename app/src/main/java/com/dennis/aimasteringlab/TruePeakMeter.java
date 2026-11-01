package com.dennis.aimasteringlab;

/**
 * Four-times band-limited interpolation meter. At a 48 kHz source rate this
 * evaluates the waveform on a 192 kHz grid, matching the minimum oversampled
 * domain normally used for delivery true-peak checks. The FIR is deliberately
 * compact enough for long-form offline work on a phone.
 */
final class TruePeakMeter {
    private static final int PHASES = 4;
    private static final int TAPS = 12;
    private static final double[][] COEFFICIENTS = coefficients();

    private final double[] history = new double[TAPS];
    private int writeIndex;
    private long samplesSeen;
    private double peak;

    void add(double sample) {
        if (!Double.isFinite(sample)) sample = 0.0;
        peak = Math.max(peak, Math.abs(sample));
        history[writeIndex] = sample;
        if (++writeIndex == TAPS) writeIndex = 0;
        samplesSeen++;
        if (samplesSeen >= TAPS) evaluateFractionalPhases();
    }

    void finish() {
        // Flush the FIR look-ahead without allowing the padding itself to
        // influence the sample peak measurement.
        for (int i = 0; i < TAPS / 2; i++) {
            history[writeIndex] = 0.0;
            if (++writeIndex == TAPS) writeIndex = 0;
            samplesSeen++;
            if (samplesSeen >= TAPS) evaluateFractionalPhases();
        }
    }

    double peak() {
        return peak;
    }

    private void evaluateFractionalPhases() {
        for (int phase = 1; phase < PHASES; phase++) {
            double y = 0.0;
            int index = writeIndex;
            for (int tap = 0; tap < TAPS; tap++) {
                y += history[index] * COEFFICIENTS[phase][tap];
                if (++index == TAPS) index = 0;
            }
            peak = Math.max(peak, Math.abs(y));
        }
    }

    private static double[][] coefficients() {
        double[][] out = new double[PHASES][TAPS];
        double centre = TAPS / 2.0 - 1.0;
        for (int phase = 0; phase < PHASES; phase++) {
            double fractional = phase / (double) PHASES;
            double sum = 0.0;
            for (int tap = 0; tap < TAPS; tap++) {
                double distance = tap - centre - fractional;
                double sinc = Math.abs(distance) < 1.0e-12
                        ? 1.0 : Math.sin(Math.PI * distance) / (Math.PI * distance);
                // Blackman window suppresses interpolation ringing around
                // clipped or highly compressed source samples.
                double position = tap / (double) (TAPS - 1);
                double window = 0.42 - 0.5 * Math.cos(2.0 * Math.PI * position)
                        + 0.08 * Math.cos(4.0 * Math.PI * position);
                out[phase][tap] = sinc * window;
                sum += out[phase][tap];
            }
            if (Math.abs(sum) > 1.0e-12) {
                for (int tap = 0; tap < TAPS; tap++) out[phase][tap] /= sum;
            }
        }
        return out;
    }
}
