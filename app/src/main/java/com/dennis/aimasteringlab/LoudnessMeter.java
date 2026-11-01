package com.dennis.aimasteringlab;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class LoudnessMeter {
    static final class Result {
        double integratedLufs;
        double lraLu;
        int gatedBlocks;
        int totalBlocks;
        double[] momentaryLufs;
        double[] shortTermLufs;
        double hopSeconds = 0.1;
    }

    private final int channels;
    private final int hopSamples;
    private final Biquad[] shelves;
    private final Biquad[] highPasses;
    private final ArrayList<Double> hopSums = new ArrayList<>();
    private int inHop;
    private double currentHopSum;

    LoudnessMeter(int channels, int sampleRate) {
        this.channels = channels;
        this.hopSamples = Math.max(1, (int) Math.round(sampleRate / 10.0));
        this.shelves = new Biquad[channels];
        this.highPasses = new Biquad[channels];
        for (int c = 0; c < channels; c++) {
            shelves[c] = Biquad.kWeightShelf(sampleRate);
            highPasses[c] = Biquad.kWeightHighPass(sampleRate);
        }
    }

    void addFrame(float[][] samples, int frame) {
        double frameEnergy = 0.0;
        for (int c = 0; c < channels; c++) {
            double y = highPasses[c].process(shelves[c].process(samples[c][frame]));
            frameEnergy += y * y;
        }
        currentHopSum += frameEnergy;
        inHop++;
        if (inHop == hopSamples) {
            hopSums.add(currentHopSum);
            currentHopSum = 0.0;
            inHop = 0;
        }
    }

    Result finish() {
        Result out = new Result();
        ArrayList<Double> blocks = new ArrayList<>();
        for (int i = 3; i < hopSums.size(); i++) {
            double sum = hopSums.get(i) + hopSums.get(i - 1)
                    + hopSums.get(i - 2) + hopSums.get(i - 3);
            blocks.add(sum / (4.0 * hopSamples));
        }
        out.momentaryLufs = new double[blocks.size()];
        for (int i = 0; i < blocks.size(); i++) {
            out.momentaryLufs[i] = energyToLufs(blocks.get(i));
        }
        out.shortTermLufs = new double[hopSums.size()];
        java.util.Arrays.fill(out.shortTermLufs, Double.NaN);
        for (int i = 29; i < hopSums.size(); i++) {
            double sum = 0.0;
            for (int j = i - 29; j <= i; j++) sum += hopSums.get(j);
            out.shortTermLufs[i] = energyToLufs(sum / (30.0 * hopSamples));
        }
        out.totalBlocks = blocks.size();
        if (blocks.isEmpty()) {
            out.integratedLufs = Double.NaN;
            out.lraLu = Double.NaN;
            return out;
        }

        ArrayList<Double> absolute = new ArrayList<>();
        for (double energy : blocks) {
            if (energyToLufs(energy) >= -70.0) absolute.add(energy);
        }
        if (absolute.isEmpty()) {
            out.integratedLufs = Double.NEGATIVE_INFINITY;
            out.lraLu = 0.0;
            return out;
        }
        double ungatedEnergy = mean(absolute);
        double relativeGate = energyToLufs(ungatedEnergy) - 10.0;
        double gate = Math.max(-70.0, relativeGate);
        ArrayList<Double> gated = new ArrayList<>();
        for (double energy : absolute) {
            if (energyToLufs(energy) >= gate) gated.add(energy);
        }
        out.gatedBlocks = gated.size();
        out.integratedLufs = energyToLufs(mean(gated));
        out.lraLu = calculateLra(out.integratedLufs);
        return out;
    }

    private double calculateLra(double integrated) {
        ArrayList<Double> shortTerms = new ArrayList<>();
        for (int i = 29; i < hopSums.size(); i += 10) {
            double sum = 0.0;
            for (int j = i - 29; j <= i; j++) sum += hopSums.get(j);
            double lufs = energyToLufs(sum / (30.0 * hopSamples));
            if (lufs >= -70.0 && lufs >= integrated - 20.0) shortTerms.add(lufs);
        }
        if (shortTerms.size() < 2) return 0.0;
        Collections.sort(shortTerms);
        return percentile(shortTerms, 0.95) - percentile(shortTerms, 0.10);
    }

    private static double mean(List<Double> values) {
        double sum = 0.0;
        for (double v : values) sum += v;
        return sum / values.size();
    }

    private static double energyToLufs(double energy) {
        if (!(energy > 0.0)) return Double.NEGATIVE_INFINITY;
        return -0.691 + 10.0 * Math.log10(energy);
    }

    private static double percentile(List<Double> sorted, double p) {
        double position = p * (sorted.size() - 1);
        int lo = (int) Math.floor(position);
        int hi = Math.min(sorted.size() - 1, lo + 1);
        double f = position - lo;
        return sorted.get(lo) * (1.0 - f) + sorted.get(hi) * f;
    }
}
