package com.dennis.photomasterai;

/**
 * Experimental restrained photographic tone-finishing curves.
 * Pure deterministic math so limits can be tested on the build runner.
 */
public final class StudioFinishPolicy {
    private StudioFinishPolicy() {}
    public static final int MAX_CHANNEL_EDIT = 9;
    public static final double MIN_DETAIL_RETAINED = 0.985;
    public static final double MAX_RING_INCREASE = 0.006;
    public static final double MAX_MEAN_CHANNEL_DELTA = 0.010;
    public static final double MAX_CLIP_INCREASE = 0.0005;

    public static float smooth(float value) {
        float t=Math.max(0f,Math.min(1f,value));
        return t*t*(3f-2f*t);
    }

    /**
     * Smooth, luma-only tonal proposal. No saturation boost, edge synthesis,
     * global white-balance shift or generative content. Texture/edges protected
     * from being pushed beyond the existing camera/master result.
     */
    public static float requestedTone(
            float luma,float localBackground,float sceneP90,
            float texture,float strength,float skinLikelihood) {
        float bright=smooth((luma-126f)/96f) * smooth((localBackground-104f)/90f);
        float shadow=smooth((luma-22f)/65f)
                * (1f-smooth((luma-106f)/66f));
        float strongerLight=0.65f+0.35f*smooth((sceneP90-161f)/70f);
        float compress=-7.0f*bright*strongerLight;
        float open=3.5f*shadow*(1f-0.65f*smooth((sceneP90-217f)/38f));
        float edgeProtect=1f-0.58f*smooth((texture-11f)/43f);
        float skinProtect=1f-0.38f*skinLikelihood;
        float blackProtect=smooth((luma-7f)/20f);
        float whiteProtect=1f-smooth((luma-245f)/10f);
        float adjustment=(compress+open)*edgeProtect*skinProtect
                *blackProtect*whiteProtect*strength;
        return Math.max(-MAX_CHANNEL_EDIT,Math.min(MAX_CHANNEL_EDIT,adjustment));
    }

    public static int channelWithTone(int channel, float tone) {
        int move=Math.round(tone);
        move=Math.max(-MAX_CHANNEL_EDIT,Math.min(MAX_CHANNEL_EDIT,move));
        return Math.max(0,Math.min(255,channel+move));
    }

    /**
     * A deliberately conservative skin evidence factor, to avoid over-
     * processing natural warm skin, orange paint, flowers, or pumpkins.
     */
    public static float skinLikelihood(int r,int g,int b,int luma) {
        float warm=smooth((r-g-3f)/30f)
                *smooth((g-b-2f)/24f);
        float bright=smooth((luma-47f)/66f)
                *(1f-smooth((luma-240f)/16f));
        float extremeOrange=smooth((r-g-72f)/56f);
        return warm*bright*(1f-0.65f*extremeOrange);
    }
}
