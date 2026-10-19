package com.dennis.photomasterai;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ColorSpace;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Locale;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

/**
 * Isolated, opt-in AI MAX colour experiment. It never changes the normal
 * AI MAX path and never edits its supplied baseline bitmap.
 *
 * The learned LUT represents a colour/tone proposal inferred from the
 * authoritative ORIGINAL. Apply only the neural delta to the finished
 * baseline, not AutoPhoto's independent Polished tonal finish.
 */
public final class AdaptiveNeuralColour {
    private AdaptiveNeuralColour() {}

    private static final int DIM = 33;
    private static final int CELLS = DIM * DIM * DIM;
    private static final int MAX_CHANNEL_EDIT = 12;
    private static final double MIN_DETAIL_RETAINED = .985;
    private static final double MAX_RING_INCREASE = .006;
    private static final double MAX_MEAN_CHANNEL_MOVE = .007;
    private static final double MAX_CLIP_INCREASE = .002;
    private static final float[] STRENGTHS = {.28f, .20f, .12f};

    public static PhotoMasterEngine.Result apply(
            Context context, Bitmap original, PhotoMasterEngine.Result baseline) {
        if (original == null || original.isRecycled() || baseline == null
                || baseline.bitmap == null || baseline.bitmap.isRecycled())
            return baseline;

        Bitmap mastered = baseline.bitmap;
        if (original.getWidth() != mastered.getWidth()
                || original.getHeight() != mastered.getHeight())
            return withReport(baseline, "WITHHELD — different source/master dimensions");

        // The pretrained classifier and LUTs were trained in sRGB. Do not
        // retag P3 bitmaps, or erase source wide-gamut image information.
        ColorSpace a = original.getColorSpace();
        ColorSpace b = mastered.getColorSpace();
        if (a == null || b == null || !a.isSrgb() || !b.isSrgb())
            return withReport(baseline, "WITHHELD — only verified sRGB input is enabled in this experiment");

        try {
            final long start = android.os.SystemClock.elapsedRealtime();
            float[] coeff = inferCoefficients(context, original);
            if (coeff.length != 3)
                throw new IllegalStateException("Classifier returned an unexpected tensor");
            for (float c : coeff)
                if (!Float.isFinite(c) || Math.abs(c) > 30f)
                    throw new IllegalStateException("Unstable pretrained LUT coefficient");

            final float[] lut = fuseBases(context, coeff);
            final PhotoAnalysis before = PhotoAnalysis.analyse(mastered);
            String rejection = "no strength passed QC";
            for (float strength : STRENGTHS) {
                Correction correction = render(original, mastered, lut, strength);
                if (correction.bitmap == null) {
                    rejection = "render or memory allocation unavailable";
                    break;
                }
                PhotoAnalysis after = PhotoAnalysis.analyse(correction.bitmap);
                double detailRatio = before.detail > 1e-9
                        ? after.detail / before.detail : 1.0;
                boolean pass = correction.editedPixels > 0
                        && detailRatio >= MIN_DETAIL_RETAINED
                        && after.ringing <= before.ringing + MAX_RING_INCREASE
                        && after.noise <= before.noise + .002
                        && after.flatChromaNoise <= before.flatChromaNoise + .002
                        && after.shadowClip <= before.shadowClip + MAX_CLIP_INCREASE
                        && after.highlightClip <= before.highlightClip + MAX_CLIP_INCREASE
                        && Math.abs(after.avgSat - before.avgSat) <= .025
                        && correction.meanChannelMove <= MAX_MEAN_CHANNEL_MOVE
                        && correction.maxChannelEdit <= MAX_CHANNEL_EDIT;
                if (pass) {
                    String info = String.format(Locale.US,
                            "ACCEPTED • ONNX Image-Adaptive 3D LUT • strength %.2f"
                            + "\n• Learned weights %.4f | %.4f | %.4f"
                            + "\n• Candidate pixels %.2f%% | mean channel move %.5f | max edit %d/255"
                            + "\n• Detail retention %.2f%% | ringing %.4f -> %.4f"
                            + "\n• Neural inference/total %.0f ms (total including render and QC)"
                            + "\n• AI MAX baseline preserved as rollback candidate",
                            strength, coeff[0], coeff[1], coeff[2],
                            100.0 * correction.editedPixels /
                                    ((double) mastered.getWidth() * mastered.getHeight()),
                            correction.meanChannelMove, correction.maxChannelEdit,
                            100 * detailRatio, before.ringing, after.ringing,
                            (double) (android.os.SystemClock.elapsedRealtime() - start));
                    return new PhotoMasterEngine.Result(
                            correction.bitmap, baseline.report + "\n\nNEURAL COLOUR EXPERIMENT\n• " + info,
                            after);
                }
                rejection = String.format(Locale.US,
                        "strength %.2f failed unchanged safety gates"
                        + " (detail %.2f%%, ring %.4f -> %.4f, channel %.5f)",
                        strength, detailRatio * 100, before.ringing, after.ringing,
                        correction.meanChannelMove);
                correction.bitmap.recycle();
            }
            return withReport(baseline, "ROLLED BACK — " + rejection
                    + "\n• Unmodified AI MAX output retained");
        } catch (Throwable failure) {
            return withReport(baseline, "WITHHELD — neural runtime unavailable or unsafe ("
                    + failure.getClass().getSimpleName() + ")"
                    + "\n• Unmodified AI MAX output retained");
        }
    }

    private static PhotoMasterEngine.Result withReport(PhotoMasterEngine.Result base, String report) {
        return new PhotoMasterEngine.Result(base.bitmap,
                base.report + "\n\nNEURAL COLOUR EXPERIMENT\n• " + report,
                PhotoAnalysis.analyse(base.bitmap));
    }

    private static float[] inferCoefficients(Context ctx, Bitmap source) throws Exception {
        Bitmap preview = Bitmap.createScaledBitmap(source, 256, 256, true);
        int[] src = new int[256 * 256];
        preview.getPixels(src, 0, 256, 0, 0, 256, 256);
        if (preview != source) preview.recycle();
        FloatBuffer input = ByteBuffer.allocateDirect(3 * src.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        // Training expects CHW RGB [0,1], never NHWC or BGR.
        for (int c = 0; c < 3; c++)
            for (int p : src) input.put(((p >> (16 - 8 * c)) & 255) / 255f);
        input.rewind();
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        try (OrtSession.SessionOptions opts = new OrtSession.SessionOptions()) {
            opts.setIntraOpNumThreads(4);
            byte[] model = readAsset(ctx, "lut_classifier.onnx");
            try (OrtSession session = env.createSession(model, opts);
                 OnnxTensor tensor = OnnxTensor.createTensor(env, input, new long[]{1,3,256,256})) {
                String name = session.getInputNames().iterator().next();
                try (OrtSession.Result result = session.run(java.util.Collections.singletonMap(name,tensor))) {
                    Object value = result.get(0).getValue();
                    if (!(value instanceof float[][]))
                        throw new IllegalStateException("Unexpected ONNX output type");
                    return ((float[][]) value)[0].clone();
                }
            }
        }
    }

    private static byte[] readAsset(Context ctx, String name) throws Exception {
        try (InputStream in = ctx.getAssets().open(name)) { return in.readAllBytes(); }
    }

    private static float[] fuseBases(Context ctx, float[] coefficients) throws Exception {
        byte[] bytes = readAsset(ctx, "lut_basis_33.bin");
        if (bytes.length != 3 * 3 * CELLS * 4)
            throw new IllegalStateException("Invalid learned LUT file size");
        FloatBuffer bases = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
        float[] result = new float[3 * CELLS];
        for (int c=0;c<3;c++) for (int i=0;i<CELLS;i++) {
            float val = 0;
            for(int k=0;k<3;k++)
                val += coefficients[k] * bases.get((k * 3 + c) * CELLS + i);
            if (!Float.isFinite(val)) throw new IllegalStateException("Invalid LUT value");
            result[c*CELLS+i]=val*255f;
        }
        return result;
    }

    private static float get(float[] lut,int c,int r,int g,int b) {
        return lut[c * CELLS + b * DIM * DIM + g * DIM + r];
    }

    private static float sample(float[] lut,int c,int r,int g,int b) {
        float rf = r * (32f/255f), gf = g * (32f/255f), bf = b * (32f/255f);
        int r0=(int)rf,g0=(int)gf,b0=(int)bf;
        int r1=Math.min(32,r0+1),g1=Math.min(32,g0+1),b1=Math.min(32,b0+1);
        float x=rf-r0,y=gf-g0,z=bf-b0;
        float p00=get(lut,c,r0,g0,b0)*(1-x)+get(lut,c,r1,g0,b0)*x;
        float p10=get(lut,c,r0,g1,b0)*(1-x)+get(lut,c,r1,g1,b0)*x;
        float p01=get(lut,c,r0,g0,b1)*(1-x)+get(lut,c,r1,g0,b1)*x;
        float p11=get(lut,c,r0,g1,b1)*(1-x)+get(lut,c,r1,g1,b1)*x;
        return (p00*(1-y)+p10*y)*(1-z)+(p01*(1-y)+p11*y)*z;
    }

    private static float smooth(float t) {
        float x=Math.max(0f,Math.min(1f,t));
        return x*x*(3f-2f*x);
    }

    private static final class Correction {
        final Bitmap bitmap;
        final long editedPixels;
        final double meanChannelMove;
        final int maxChannelEdit;
        Correction(Bitmap b,long count,double mean,int max){
            bitmap=b;editedPixels=count;meanChannelMove=mean;maxChannelEdit=max;
        }
    }

    private static Correction render(Bitmap original,Bitmap mastered,float[] lut,float strength) {
        final int w=original.getWidth(),h=original.getHeight();
        Bitmap candidate=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        int rows=Math.min(h,64);
        int[] src=new int[w*rows],base=new int[w*rows],out=new int[w*rows];
        long edited=0;
        double delta=0;
        int max=0;
        for(int top=0;top<h;top+=rows) {
            int n=Math.min(rows,h-top);
            original.getPixels(src,0,w,0,top,w,n);
            mastered.getPixels(base,0,w,0,top,w,n);
            for(int i=0;i<w*n;i++) {
                int op=src[i],bp=base[i];
                int r=(op>>16)&255,g=(op>>8)&255,b=op&255;
                int br=(bp>>16)&255,bg=(bp>>8)&255,bb=bp&255;
                float lum=.2126f*br+.7152f*bg+.0722f*bb;
                float darkest=Math.min(br,Math.min(bg,bb));
                float brightest=Math.max(br,Math.max(bg,bb));
                float saturation=(brightest-darkest)/Math.max(1f,brightest);
                float protect=smooth((lum-22f)/70f)*
                        (1f-smooth((lum-175f)/75f))*
                        (1f-.65f*smooth((saturation-.50f)/.45f));
                if ((r>g*1.24f && b>g*1.12f && r>80)
                     || (r>95 && g>55 && b>32 && r>g && g>b && (r-b)<110))
                    protect*=.38f; // protect bright petals and skin-like pixels
                float gain=strength*protect;
                int nr=br+clampedDiff((sample(lut,0,r,g,b)-r)*gain);
                int ng=bg+clampedDiff((sample(lut,1,r,g,b)-g)*gain);
                int nb=bb+clampedDiff((sample(lut,2,r,g,b)-b)*gain);
                nr=Math.max(0,Math.min(255,nr));
                ng=Math.max(0,Math.min(255,ng));
                nb=Math.max(0,Math.min(255,nb));
                int dr=Math.abs(nr-br),dg=Math.abs(ng-bg),db=Math.abs(nb-bb);
                if(dr+dg+db>0)edited++;
                delta+=(dr+dg+db)/3.0/255.0;
                max=Math.max(max,Math.max(dr,Math.max(dg,db)));
                out[i]=(bp&0xff000000)|(nr<<16)|(ng<<8)|nb;
            }
            candidate.setPixels(out,0,w,0,top,w,n);
        }
        return new Correction(candidate,edited,delta/(w*(double)h),max);
    }

    private static int clampedDiff(float requested) {
        if(!Float.isFinite(requested))return 0;
        return Math.max(-MAX_CHANNEL_EDIT,
                Math.min(MAX_CHANNEL_EDIT,Math.round(requested)));
    }
}
