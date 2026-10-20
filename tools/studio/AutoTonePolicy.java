package com.dennis.photomasterai;

/**
 * Image-adaptive AUTO exposure / tone, independent of LUT and other modules.
 * Pure Java photometric rules so the build checks intensity, highlight safety,
 * night mood and black-point anchoring without Android dependencies.
 */
public final class AutoTonePolicy {
    private AutoTonePolicy() {}
    public static final double MIN_DETAIL_RETAINED = .985;
    public static final double MAX_RING_INCREASE = .006;
    public static final double MAX_MEAN_CHANNEL_CHANGE = .023;
    public static final double MAX_CLIP_INCREASE = .0010;
    public static final int MAX_CHANNEL_EDIT = 21;
    public static float smooth(float t) {
        t=Math.max(0f,Math.min(1f,t));return t*t*(3f-2f*t);
    }
    public static final class Plan {
        public final boolean lowLight,firelight;
        public final float p10,p50,p90,p98,shadowLift,midtoneLift,highlightReduction,blackDepth;
        public Plan(float p10,float p50,float p90,float p98,float darkFraction,
                    float brightWarmFraction) {
            this.p10=p10;this.p50=p50;this.p90=p90;this.p98=p98;
            lowLight=p50<58f && darkFraction>.40f;
            firelight=lowLight && p98>180f && brightWarmFraction>.004f;
            // Dark, high-contrast nights must never be dragged toward daylight.
            if(firelight){
                shadowLift=11f;
                midtoneLift=2.8f;
                highlightReduction=2.4f;
                blackDepth=-3.5f;
            }else if(lowLight){
                shadowLift=9.0f;
                midtoneLift=4.5f;
                highlightReduction=p98>210f?5.0f:2.2f;
                blackDepth=-1.5f;
            }else{
                shadowLift=3f+7f*smooth((87f-p10)/72f);
                midtoneLift=Math.max(-6f,Math.min(10f,(116f-p50)*.24f));
                highlightReduction=2.0f+9.0f*smooth((p98-209f)/39f);
                blackDepth=p10>30f ? -2.6f : -0.9f;
            }
        }
    }
    /**
     * Sky/fire protection is achieved via source luminance, bright-region
     * support and warm-chroma masks. No unbounded automatic equalisation.
     */
    public static float offset(Plan p,float lum,float localBackground,
                               float texture,float warmFire,float skin,float strength) {
        float shadow=smooth((lum-12f)/45f)*(1f-smooth((lum-108f)/79f));
        float mid=smooth((lum-35f)/54f)*(1f-smooth((lum-170f)/73f));
        float highlights=smooth((lum-158f)/87f);
        float deepBlack=1f-smooth((lum-10f)/32f);
        float proximity=(p.lowLight?(.08f+.92f*smooth((localBackground-15f)/70f)):1f);
        float lift=p.shadowLift*shadow*proximity+p.midtoneLift*mid;
        float negative=-p.highlightReduction*highlights*(1f-.97f*warmFire);
        float black=p.blackDepth*deepBlack;
        float protectTexture=1f-.42f*smooth((texture-13f)/43f);
        float protectSkin=1f-.40f*skin;
        float protectFire=1f-.83f*warmFire;
        float offset=(lift+negative+black)*protectTexture*protectSkin*protectFire*strength;
        // Do not change black void or specular clipped whites.
        offset*=smooth((lum-5f)/19f)*(1f-smooth((lum-247f)/8f));
        return Math.max(-MAX_CHANNEL_EDIT,Math.min(MAX_CHANNEL_EDIT,offset));
    }
}
