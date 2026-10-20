package com.dennis.photomasterai;
public final class PastelHighlightPolicyHostChecks {
    private static void ok(boolean b,String label) {
        if(!b) throw new AssertionError(label);
    }
    private static void identical(int px,int r,int g,int b,String why) {
        ok(px == ((r<<16)|(g<<8)|b),why);
    }
    private static int red(int px) { return (px>>16)&255; }
    private static int green(int px) { return (px>>8)&255; }
    private static int blue(int px) { return px&255; }
    public static void main(String[] args) {
        // Strong selective protection should never invent a new peak.
        int pastel=PastelHighlightPolicy.rescue(231,198,221,252,214,238,1f);
        ok(red(pastel)<252,"strong clipped-looking pink highlight softened");
        ok(red(pastel)>=231&&green(pastel)>=198&&blue(pastel)>=221,
                "no overcorrection below original");
        // Uniform candidate unchanged if it is already safe.
        identical(PastelHighlightPolicy.rescue(220,192,210,233,202,223,1f),
                233,202,223,"no needless highlight darkening");
        // Already clipped original cannot regain nonexistent detail.
        identical(PastelHighlightPolicy.rescue(251,229,245,255,235,250,1f),
                255,235,250,"no fabricated clipping recovery");
        // Other hue types must not be affected by this new guard.
        identical(PastelHighlightPolicy.rescue(233,126,18,249,146,27,0f),
                249,146,27,"orange retained");
        identical(PastelHighlightPolicy.rescue(190,42,55,250,48,62,0f),
                250,48,62,"red retained");
        identical(PastelHighlightPolicy.rescue(44,192,60,50,248,70,0f),
                50,248,70,"green retained");
        for(int s=0;s<=255;s+=13)
            for(int e=s;e<=255;e+=13){
                int out=PastelHighlightPolicy.rescue(s,s*9/10,s*19/20,
                        e,Math.min(255,e*9/10+3),
                        Math.min(255,e*19/20+3),1f);
                ok(red(out)>=s && red(out)<=e,"red monotonic bounded");
                ok(green(out)>=Math.min(s*9/10,Math.min(255,e*9/10+3)),
                        "green never overshot");
            }
        System.out.println("PastelHighlightPolicy deterministic host checks PASSED");
    }
}
