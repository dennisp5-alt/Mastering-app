package com.dennis.aimasteringlab;

import java.util.ArrayList;
import java.util.List;

/** Sparse digital flat-top detection; never reconstructs sustained limiting. */
final class IsolatedClipDetector {
    private final int channel;
    private final double threshold;
    private final long introEnd, tailStart;
    private boolean active;
    private long start;
    private int frames, sign;
    private double before, minimum, maximum;
    private boolean sameSign;
    int qualifiedRuns;
    long qualifiedFrames;
    int rejectedRuns;
    final List<ClickDropoutRepair.Event> events = new ArrayList<>();

    IsolatedClipDetector(int channel, WavFile.Info info, long introEnd, long tailStart) {
        this.channel = channel;
        double lsb = Math.pow(2.0, 1.0 - info.validBitsPerSample);
        threshold = info.floatingPoint ? 0.9999998 : 1.0 - Math.max(2e-7, lsb * 1.1);
        this.introEnd = introEnd;
        this.tailStart = tailStart;
    }

    void observe(double value, double previous, long frame) {
        if (Math.abs(value) >= threshold) {
            int currentSign = value < 0 ? -1 : 1;
            if (!active) {
                active = true;
                start = frame;
                frames = 0;
                sign = currentSign;
                before = previous;
                minimum = maximum = value;
                sameSign = true;
            }
            frames++;
            sameSign &= sign == currentSign;
            minimum = Math.min(minimum, value);
            maximum = Math.max(maximum, value);
            return;
        }
        if (!active) return;
        // Both neighbours must be active, same-polarity, and clearly below
        // full scale. This excludes normal rounded peaks near full scale.
        boolean neighbours = Math.signum(before) == sign && Math.signum(value) == sign
                && Math.abs(before) >= 0.05 && Math.abs(value) >= 0.05
                && Math.abs(before) <= 0.95 && Math.abs(value) <= 0.95;
        if (frames >= 2 && frames <= 32 && start >= introEnd && frame < tailStart
                && sameSign && maximum - minimum <= 2.5e-4 && neighbours) {
            qualifiedRuns++;
            qualifiedFrames += frames;
            if (events.size() < 17) {
                ClickDropoutRepair.Event event = new ClickDropoutRepair.Event();
                event.kind = ClickDropoutRepair.Kind.ISOLATED_CLIP_RUN;
                event.startFrame = start;
                event.endFrame = frame - 1;
                event.channelMask = 1 << channel;
                event.before[channel] = before;
                event.after[channel] = value;
                event.confidence = 0.985;
                event.evidence = "isolated " + frames + "-frame full-scale digital plateau; "
                        + "same-polarity active neighbours and sparse source-wide coverage required";
                events.add(event);
            }
        } else rejectedRuns++;
        active = false;
    }
}
