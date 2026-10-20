package com.dennis.photomasterai;

/**
 * Conservative scene-independent source-referenced rescue for near-white
 * pale-pink/rose image regions. Applied only in sRGB Studio Lab after the
 * original cumulative guard, and subject to its original final QC.
 */
public final class PastelHighlightPolicy {
    private PastelHighlightPolicy() {}

    private static int maximum(int r,int g,int b) {
        return Math.max(r,Math.max(g,b));
    }
    public static int rescue(
            int sr,int sg,int sb, int r,int g,int b, float existingPinkMask) {
        int unchanged = pack(r,g,b);
        int originalPeak=maximum(sr,sg,sb);
        int newPeak=maximum(r,g,b);
        // Original already near clipped: no detail can be recovered by
        // artificially lowering the new result; leave these pixels alone.
        if(originalPeak >= 242 || newPeak < 243
                || newPeak <= originalPeak+7 || existingPinkMask < .4f)
            return unchanged;
        // Spatially soft selection: no hard on/off step within the petal.
        float sourceHeadroom=SelectiveColourPolicy.smooth((242f-originalPeak)/12f);
        float bloom=SelectiveColourPolicy.smooth((newPeak-239f)/13f);
        float increase=SelectiveColourPolicy.smooth((newPeak-originalPeak-6f)/14f);
        float selection=SelectiveColourPolicy.smooth(
                (existingPinkMask-.35f)/.55f);
        float mix=.80f*sourceHeadroom*bloom*increase*selection;
        if(mix <= 0.0001f) return unchanged;
        return pack(roundBlend(r,sr,mix),roundBlend(g,sg,mix),roundBlend(b,sb,mix));
    }
    private static int pack(int r,int g,int b) {
        return (r<<16)|(g<<8)|b;
    }
    private static int roundBlend(int candidate,int source,float mix) {
        return Math.max(0,Math.min(255,
                Math.round(candidate+(source-candidate)*mix)));
    }
}
