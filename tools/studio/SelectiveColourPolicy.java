package com.dennis.photomasterai;

/**
 * Source-anchored protection for pale pink / rose-magenta highlight detail.
 * Smooth, continuous weights; avoids orange pumpkins, red bottlebrush
 * flowers, green foliage, ordinary dark colours and skin by default.
 *
 * This is intentionally an optional FINAL safeguard for Studio Lab only.
 * It cannot change the neural model or the approved AutoPhoto Polished app.
 */
public final class SelectiveColourPolicy {
    private SelectiveColourPolicy() {}

    public static final int MAX_GLOBAL_CHANNEL_DELTA = 24;
    public static final int MAX_PINK_CHANNEL_DELTA = 5;
    public static final double MIN_DETAIL_RETAINED = .985;
    public static final double MAX_RING_INCREASE = .006;
    public static final double MAX_CLIP_INCREASE = .002;
    public static final double MAX_REGIONAL_MEAN_DELTA = .020;
    public static final double MAX_PINK_NEARCLIP_GROWTH = .015;

    public static float smooth(float v) {
        float t=Math.max(0f,Math.min(1f,v));
        return t*t*(3f-2f*t);
    }

    public static float palePinkProtection(int r,int g,int b) {
        float luminance=.2126f*r+.7152f*g+.0722f*b;
        float saturation=(Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b)))
                /Math.max(1f,Math.max(r,Math.max(g,b)));
        float warmHue=smooth((r-g-8f)/30f);
        float magentaHue=smooth((b-g+28f)/30f);
        float lightColour=smooth((g-95f)/55f)
                *smooth((luminance-110f)/65f)
                *(1f-smooth((saturation-.36f)/.23f));
        return warmHue*magentaHue*lightColour;
    }

    public static int allowableDelta(float protection) {
        return Math.round(MAX_GLOBAL_CHANNEL_DELTA
                +(MAX_PINK_CHANNEL_DELTA-MAX_GLOBAL_CHANNEL_DELTA)
                *smooth(protection));
    }

    /**
     * Original AI MAX output is the authoritative cumulative reference.
     * At-risk colour may receive ONLY small additional channel movement,
     * but the remainder of the photograph still keeps the Studio finish.
     */
    public static int guardChannel(int reference,int proposed,
                                   float protection) {
        int max=allowableDelta(protection);
        int raw=proposed-reference;
        int clamped=Math.max(-max,Math.min(max,raw));
        // Gradually blend protected areas back toward the known-good
        // master; avoid a hard boundary on fabric edges.
        float retained=1f-.72f*smooth(protection);
        return Math.max(0,Math.min(255,
                reference+Math.round(clamped*retained)));
    }
}
