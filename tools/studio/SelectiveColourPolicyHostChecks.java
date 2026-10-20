package com.dennis.photomasterai;

public final class SelectiveColourPolicyHostChecks {
    private static void yes(boolean condition,String description) {
        if(!condition)throw new AssertionError(description);
    }
    public static void main(String[] args) {
        float lightPink=SelectiveColourPolicy.palePinkProtection(243,191,203);
        float mediumPink=SelectiveColourPolicy.palePinkProtection(226,170,190);
        float orangePumpkin=SelectiveColourPolicy.palePinkProtection(255,144,22);
        float redBottlebrush=SelectiveColourPolicy.palePinkProtection(207,35,53);
        float naturalLeaf=SelectiveColourPolicy.palePinkProtection(72,125,49);
        float paleSkin=SelectiveColourPolicy.palePinkProtection(227,173,148);
        yes(lightPink>.80f, "pale pink clothing highlight must be recognised");
        yes(mediumPink>.50f,"medium rose pink fabric must be recognised");
        yes(orangePumpkin<.05f,"orange pumpkins must retain Auto/Studio colour");
        yes(redBottlebrush<.05f,"saturated red bottlebrush flowers must be protected from false mask");
        yes(naturalLeaf<.05f,"normal foliage cannot be classified as pink");
        yes(paleSkin<.40f,"ordinary skin must not be misclassified as fabric");

        int noMask=SelectiveColourPolicy.guardChannel(205,224,0f);
        int pink=SelectiveColourPolicy.guardChannel(205,224,1f);
        yes(noMask==224,"unprotected edits within cap should be preserved");
        yes(pink>=205 && pink<=208,"pink fabric cumulative edit should be <=3 codes");

        // Smooth gradients prevent abrupt colour transitions around fabric.
        int last=300;
        for(int k=0;k<=100;k++){
            float m=k/100f;
            int r=SelectiveColourPolicy.guardChannel(200,235,m);
            yes(r<=last,"protection should only reduce excessive gain");
            yes(r>=200 && r<=224,"guard cannot overshoot baseline");
            last=r;
        }
        yes(SelectiveColourPolicy.MIN_DETAIL_RETAINED==.985,
                "detail safety cannot be weakened");
        yes(SelectiveColourPolicy.MAX_RING_INCREASE==.006,
                "halo/ringing gate cannot be weakened");
        yes(SelectiveColourPolicy.MAX_REGIONAL_MEAN_DELTA==.020,
                "local colour QC must remain strict");
        yes(SelectiveColourPolicy.MAX_PINK_NEARCLIP_GROWTH==.015,
                "fabric highlight clipping protection required");
        System.out.println("PASS selective colour: pink fabric, flowers, pumpkins,"
                +" foliage, skin, smooth clamp and global/regional QC invariants");
    }
}
