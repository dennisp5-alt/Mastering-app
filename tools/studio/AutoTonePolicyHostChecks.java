package com.dennis.photomasterai;

public final class AutoTonePolicyHostChecks {
    private static void yes(boolean ok,String label){
        if(!ok)throw new AssertionError(label);
    }
    private static float apply(AutoTonePolicy.Plan plan,float lum,float local,
            float texture,float fire,float skin){
        return AutoTonePolicy.offset(plan,lum,local,texture,fire,skin,1f);
    }
    public static void main(String[] args){
        yes(AutoTonePolicy.MIN_DETAIL_RETAINED==.985,"detail 98.5%");
        yes(AutoTonePolicy.MAX_RING_INCREASE==.006,"ringing QC");
        yes(AutoTonePolicy.MAX_CLIP_INCREASE==.0010,"clip QC");
        yes(AutoTonePolicy.MAX_CHANNEL_EDIT==21,"hard channel cap");
        AutoTonePolicy.Plan fire=new AutoTonePolicy.Plan(
                0,18,106,240,.81f,.03f);
        yes(fire.lowLight && fire.firelight,"firelight scene classification");
        float dark=apply(fire,2,2,0,0,0);
        float nearFireShadow=apply(fire,84,83,5,0,0);
        float awayFromLight=apply(fire,84,0,5,0,0);
        float brightFlame=apply(fire,221,218,4,1,0);
        yes(Math.abs(dark)<.1f,"true-black mood retained");
        yes(nearFireShadow>3f,"noticeable localized firelight shadow shaping");
        yes(nearFireShadow>awayFromLight+1.5f,"do not lift dark surroundings globally");
        yes(Math.abs(brightFlame)<3.0f,"fire flames must stay luminous");
        AutoTonePolicy.Plan normal=new AutoTonePolicy.Plan(
                29,96,185,240,.15f,.00001f);
        yes(!normal.lowLight&&!normal.firelight,"daylight plan");
        yes(apply(normal,79,79,2,0,0)>0,"normal shadow improvement");
        yes(apply(normal,230,231,2,0,0)<0,"highlights controlled");
        float skin=apply(normal,114,115,0,0,.9f);
        float noSkin=apply(normal,114,115,0,0,0);
        yes(Math.abs(skin)<Math.abs(noSkin),"skin protection");
        for(int l=0;l<=255;l++){
            for(float tex:new float[]{0,24,80}){
                float delta=apply(normal,l,l,tex,0,0);
                yes(Float.isFinite(delta),"no NaN/Inf");
                yes(Math.abs(delta)<=21,"tone bound");
            }
        }
        System.out.println("PASS Auto tone: black/mood, natural shadows, fire protection, highlights and fixed QC");
    }
}
