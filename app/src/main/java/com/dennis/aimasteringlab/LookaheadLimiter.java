package com.dennis.aimasteringlab;

import java.io.IOException;

/** Stereo-linked offline look-ahead peak limiter. */
final class LookaheadLimiter {
    interface Output {
        void frame(double left, double right) throws IOException;
    }

    private final int lookaheadFrames;
    private final int capacity;
    private final double ceiling;
    private final double releaseStep;
    private final boolean rampedAttack;
    private final double attackSlopeDb;
    private final double[][] ring = new double[2][];
    private final long[] peakIndices;
    private final double[] peakValues;
    private int nextRingSlot;
    private int nextOutputSlot;
    private int dequeHead;
    private int dequeTail;
    private long inputIndex = -1;
    private long sourceFrames;
    private double gain = 1.0;
    private double minimumGain = 1.0;
    private long measuredFrames;
    private double reductionSumDb;
    private long framesAboveOneDb;
    private long framesAboveThreeDb;
    private double maximumGainStepDb;
    private double lastReductionDb;

    LookaheadLimiter(int sampleRate, double ceilingDbfs) {
        this(sampleRate, ceilingDbfs, 5.0, 150.0);
    }

    LookaheadLimiter(int sampleRate, double ceilingDbfs,
                     double lookaheadMs, double releaseMs) {
        this(sampleRate, ceilingDbfs, lookaheadMs, releaseMs, false);
    }

    LookaheadLimiter(int sampleRate, double ceilingDbfs,
                     double lookaheadMs, double releaseMs, boolean rampedAttack) {
        lookaheadFrames = Math.max(1, (int) Math.round(sampleRate
                * Math.max(1.0, Math.min(12.0, lookaheadMs)) / 1000.0));
        capacity = lookaheadFrames + 2;
        ceiling = Math.pow(10.0, ceilingDbfs / 20.0);
        this.rampedAttack = rampedAttack;
        attackSlopeDb = 6.0 / lookaheadFrames;
        double boundedRelease = Math.max(60.0, Math.min(320.0, releaseMs));
        releaseStep = 1.0 - Math.exp(-1.0
                / (sampleRate * boundedRelease / 1000.0));
        ring[0] = new double[capacity];
        ring[1] = new double[capacity];
        peakIndices = new long[capacity * 2];
        peakValues = new double[capacity * 2];
    }

    void add(double left, double right, Output output) throws IOException {
        addInternal(left, right, true, output);
    }

    void finish(Output output) throws IOException {
        for (int i = 0; i < lookaheadFrames; i++) addInternal(0.0, 0.0, false, output);
    }

    double maximumReductionDb() {
        return minimumGain > 0.0 ? -20.0 * Math.log10(minimumGain) : Double.POSITIVE_INFINITY;
    }

    long sourceFrames() {
        return sourceFrames;
    }

    double averageReductionDb() {
        return measuredFrames > 0 ? reductionSumDb / measuredFrames : 0.0;
    }

    double maximumGainStepDb() { return maximumGainStepDb; }

    double percentAboveOneDb() {
        return measuredFrames > 0 ? 100.0 * framesAboveOneDb / measuredFrames : 0.0;
    }

    double percentAboveThreeDb() {
        return measuredFrames > 0 ? 100.0 * framesAboveThreeDb / measuredFrames : 0.0;
    }

    private void addInternal(double left, double right, boolean source, Output output)
            throws IOException {
        inputIndex++;
        if (source) sourceFrames++;
        int slot = nextRingSlot;
        if (++nextRingSlot == capacity) nextRingSlot = 0;
        ring[0][slot] = finite(left);
        ring[1][slot] = finite(right);
        double framePeak = Math.max(Math.abs(ring[0][slot]), Math.abs(ring[1][slot]));
        // Each future sample contributes the attenuation required now to arrive
        // at its safe gain with a 6 dB/lookahead linear-dB attack. The deque holds
        // the maximum constraint, including nearer peaks, not just the loudest peak.
        double constraint = rampedAttack
                ? (framePeak <= ceiling ? 0.0
                    : Math.max(0.0, 20.0 * Math.log10(Math.max(1e-30, framePeak / ceiling))))
                    - attackSlopeDb * inputIndex
                : framePeak;
        while (dequeTail > dequeHead
                && peakValues[(dequeTail - 1) % peakValues.length] <= constraint) {
            dequeTail--;
        }
        int tailSlot = dequeTail % peakValues.length;
        peakIndices[tailSlot] = inputIndex;
        peakValues[tailSlot] = constraint;
        dequeTail++;

        if (inputIndex < lookaheadFrames) return;
        long outputIndex = inputIndex - lookaheadFrames;
        while (dequeTail > dequeHead
                && peakIndices[dequeHead % peakIndices.length] < outputIndex) {
            dequeHead++;
        }
        double futurePeak = dequeTail > dequeHead
                ? peakValues[dequeHead % peakValues.length] : 0.0;
        double target = rampedAttack
                ? Math.pow(10.0, -Math.max(0.0, futurePeak + attackSlopeDb * outputIndex) / 20.0)
                : futurePeak > ceiling ? ceiling / futurePeak : 1.0;
        if (target < gain) {
            gain = target;
        } else {
            gain += (target - gain) * releaseStep;
            if (gain > target) gain = target;
        }
        minimumGain = Math.min(minimumGain, gain);
        if (outputIndex >= 0 && outputIndex < sourceFrames) {
            double reductionDb = gain == 1.0 ? 0.0 : gain > 0.0
                    ? Math.max(0.0, -20.0 * Math.log10(gain))
                    : Double.POSITIVE_INFINITY;
            measuredFrames++;
            maximumGainStepDb = Math.max(maximumGainStepDb, Math.abs(reductionDb-lastReductionDb));
            lastReductionDb = reductionDb;
            reductionSumDb += reductionDb;
            if (reductionDb > 1.0) framesAboveOneDb++;
            if (reductionDb > 3.0) framesAboveThreeDb++;
        }
        int outputSlot = nextOutputSlot;
        if (++nextOutputSlot == capacity) nextOutputSlot = 0;
        output.frame(ring[0][outputSlot] * gain, ring[1][outputSlot] * gain);
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }
}
