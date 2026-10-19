package com.dennis.photomasterai;

import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import java.nio.ByteBuffer;
import java.util.Locale;

/**
 * Optional AI STUDIO MASTER post-processing stage.
 *
 * Runs only after the complete AI MAX baseline and independently evaluated
 * pretrained neural-colour proposal. Never performs sharpening, synthetic
 * detail creation, hue rotation, or saturation boost. Keeps the approved
 * baseline unchanged if ANY stage-local integrity test fails.
 */
public final class StudioFinish {
    private StudioFinish() {}

    private static final float[] STRENGTHS={1.0f,.72f,.45f};

    private static final class Guide {
        int width,height;
        float[] background,texture;
        float p90;
    }

    private static final class Candidate {
        Bitmap image;
        int maxEdit;
        long edited;
        double meanEdit;
        Candidate(Bitmap image,int maxEdit,long edited,double meanEdit){
            this.image=image;
            this.maxEdit=maxEdit;
            this.edited=edited;
            this.meanEdit=meanEdit;
        }
    }

    public static PhotoMasterEngine.Result apply(
            Bitmap authoritativeOriginal,
            PhotoMasterEngine.Result baseline) {
        if (baseline==null || baseline.bitmap==null
                || baseline.bitmap.isRecycled())
            return baseline;
        final Bitmap mastered=baseline.bitmap;
        if (authoritativeOriginal==null || authoritativeOriginal.isRecycled()
                || mastered.getWidth()!=authoritativeOriginal.getWidth()
                || mastered.getHeight()!=authoritativeOriginal.getHeight())
            return info(baseline,"WITHHELD — original/master geometry mismatch");
        ColorSpace nativeSpace=mastered.getColorSpace();
        ColorSpace originalSpace=authoritativeOriginal.getColorSpace();
        final ColorSpace p3=ColorSpace.get(ColorSpace.Named.DISPLAY_P3);
        final boolean isP3=p3.equals(nativeSpace)&&p3.equals(originalSpace);
        final boolean isSrgb=nativeSpace!=null && nativeSpace.isSrgb()
                && originalSpace!=null && originalSpace.isSrgb();
        if(!isP3 && !isSrgb)
            return info(baseline,"WITHHELD — unverified source/output colour profile");

        if (isP3 && (mastered.getConfig()!=Bitmap.Config.ARGB_8888
                || mastered.getRowBytes()!=(long)mastered.getWidth()*4
                || (long)mastered.getWidth()*mastered.getHeight()>12_600_000L))
            return info(baseline,"WITHHELD — unsupported native P3 size/storage");

        try {
            Guide guide=buildGuide(mastered);
            PhotoAnalysis before=PhotoAnalysis.analyse(mastered);
            StringBuilder attempts=new StringBuilder();
            for(float strength:STRENGTHS){
                Candidate c=create(mastered,guide,strength,isP3);
                PhotoAnalysis after=PhotoAnalysis.analyse(c.image);
                double detail=before.detail>1e-9
                        ? after.detail/before.detail:1.0;
                boolean accepted=c.edited>0
                        && detail>=StudioFinishPolicy.MIN_DETAIL_RETAINED
                        && after.ringing<=before.ringing+StudioFinishPolicy.MAX_RING_INCREASE
                        && after.noise<=before.noise+.0015
                        && after.flatChromaNoise<=before.flatChromaNoise+.0015
                        && after.shadowClip<=before.shadowClip+StudioFinishPolicy.MAX_CLIP_INCREASE
                        && after.highlightClip<=before.highlightClip+StudioFinishPolicy.MAX_CLIP_INCREASE
                        && Math.abs(after.avgSat-before.avgSat)<=.018
                        && c.meanEdit<=StudioFinishPolicy.MAX_MEAN_CHANNEL_DELTA
                        && c.maxEdit<=StudioFinishPolicy.MAX_CHANNEL_EDIT;
                attempts.append(String.format(Locale.US,
                        "\n• Studio %.2f: %s | detail %.2f%% | ring %.4f→%.4f"
                                +" | channel %.5f, max %d/255 | edit %.2f%%",
                        strength,accepted?"ACCEPTED":"rejected",100*detail,
                        before.ringing,after.ringing,c.meanEdit,c.maxEdit,
                        c.edited*100.0/(mastered.getWidth()*(double)mastered.getHeight())));
                if (accepted) {
                    return new PhotoMasterEngine.Result(
                        c.image,
                        baseline.report+"\n\nAI STUDIO MASTER\n• Studio tonal finish ACCEPTED"
                            +"\n• Local highlight roll-off + delicate shadow shaping"
                            +"\n• Neutral-hue finishing, zero saturation/clarity boost"
                            +"\n• Skin, hard edges, strong chroma and black-point protected"
                            +"\n• Native output space: "+(isP3?"Display P3":"sRGB")
                            + attempts,
                        after);
                }
                c.image.recycle();
            }
            return info(baseline,
                    "Studio tonal stage ROLLED BACK — existing master retained"
                        +attempts.toString());
        } catch(Throwable problem) {
            return info(baseline,
                    "WITHHELD — studio stage unsafe or unsupported ("
                        +problem.getClass().getSimpleName()+")"
                        +"\n• Existing master retained");
        }
    }

    private static PhotoMasterEngine.Result info(
            PhotoMasterEngine.Result base,String message) {
        return new PhotoMasterEngine.Result(base.bitmap,
                base.report+"\n\nAI STUDIO MASTER\n• "+message,
                PhotoAnalysis.analyse(base.bitmap));
    }

    private static Guide buildGuide(Bitmap image) {
        Guide g=new Guide();
        int w=image.getWidth(),h=image.getHeight();
        float scale=Math.min(1f,512f/Math.max(w,h));
        g.width=Math.max(1,Math.round(w*scale));
        g.height=Math.max(1,Math.round(h*scale));
        Bitmap preview=Bitmap.createScaledBitmap(image,g.width,g.height,true);
        int[] px=new int[g.width*g.height];
        preview.getPixels(px,0,g.width,0,0,g.width,g.height);
        if(preview!=image)preview.recycle();
        float[] luma=new float[px.length];
        int[] hist=new int[256];
        for(int i=0;i<px.length;i++){
            int p=px[i];
            luma[i]=.2126f*((p>>16)&255)+.7152f*((p>>8)&255)+.0722f*(p&255);
            hist[Math.min(255,(int)luma[i])]++;
        }
        int need=Math.max(1,(int)(px.length*.9));
        int n=0;
        for(int i=0;i<256;i++){
            n+=hist[i];
            if(n>=need){g.p90=i;break;}
        }
        g.background=new float[px.length];
        g.texture=new float[px.length];
        for(int y=0;y<g.height;y++) for(int x=0;x<g.width;x++){
            int idx=y*g.width+x;
            float center=luma[idx],ws=0,sum=0,sumSq=0,varianceWeight=0;
            float unweighted=0;
            for(int dy=-2;dy<=2;dy++){
                int yy=Math.max(0,Math.min(g.height-1,y+dy));
                for(int dx=-2;dx<=2;dx++){
                    int xx=Math.max(0,Math.min(g.width-1,x+dx));
                    float v=luma[yy*g.width+xx];
                    float dist=(v-center)/27f;
                    float weight=1f/(1f+dist*dist);
                    sum+=v*weight;ws+=weight;
                    unweighted+=v;
                    sumSq+=v*v;
                    varianceWeight++;
                }
            }
            float localMean=unweighted/varianceWeight;
            g.background[idx]=sum/Math.max(ws,1e-6f);
            g.texture[idx]=(float)Math.sqrt(
                    Math.max(0,sumSq/varianceWeight-localMean*localMean));
        }
        return g;
    }

    private static float sample(float[] f,int w,int h,float x,float y){
        float fx=Math.max(0,Math.min(w-1,x)),fy=Math.max(0,Math.min(h-1,y));
        int x0=(int)fx,y0=(int)fy,x1=Math.min(w-1,x0+1),y1=Math.min(h-1,y0+1);
        float a=fx-x0,b=fy-y0;
        return ((f[y0*w+x0]*(1-a)+f[y0*w+x1]*a)*(1-b)
                +(f[y1*w+x0]*(1-a)+f[y1*w+x1]*a)*b);
    }

    private static Candidate create(Bitmap source,Guide guide,float strength,
                                    boolean nativeP3){
        int w=source.getWidth(),h=source.getHeight();
        final ColorSpace profile=source.getColorSpace();
        Bitmap out=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888,true,profile);
        ByteBuffer originalBytes=null;
        ByteBuffer outputBytes=null;
        if(nativeP3){
            if(out.getRowBytes()!=w*4)throw new IllegalStateException("Native stride");
            originalBytes=ByteBuffer.allocateDirect(source.getByteCount());
            source.copyPixelsToBuffer(originalBytes);
            outputBytes=ByteBuffer.allocateDirect(source.getByteCount());
            originalBytes.rewind();
            for(int n=0;n<7;n++){
                int x=(n+1)*w/9,y=(n+2)*h/10;
                int off=(y*w+x)*4;
                android.graphics.Color actual=source.getColor(x,y);
                if(!profile.equals(actual.getColorSpace())
                    ||Math.abs(actual.red()-(originalBytes.get(off)&255)/255f)>.015f
                    ||Math.abs(actual.green()-(originalBytes.get(off+1)&255)/255f)>.015f
                    ||Math.abs(actual.blue()-(originalBytes.get(off+2)&255)/255f)>.015f)
                        throw new IllegalStateException("P3 RGBA byte order mismatch");
            }
        }
        int chunk=Math.min(h,48);
        int[] row=new int[w*chunk],edited=new int[w*chunk];
        long editedPixels=0;
        double channelDelta=0;
        int maxEdit=0;
        for(int top=0;top<h;top+=chunk){
            int count=Math.min(h-top,chunk);
            source.getPixels(row,0,w,0,top,w,count);
            for(int y=0;y<count;y++){
                float guideY=(top+y+.5f)*guide.height/h-.5f;
                for(int x=0;x<w;x++){
                    int pos=y*w+x;
                    int px=row[pos];
                    int rr=(px>>16)&255,gg=(px>>8)&255,bb=px&255;
                    float lum=.2126f*rr+.7152f*gg+.0722f*bb;
                    float guideX=(x+.5f)*guide.width/w-.5f;
                    float local=sample(guide.background,guide.width,guide.height,guideX,guideY);
                    float texture=sample(guide.texture,guide.width,guide.height,guideX,guideY);
                    float skin=StudioFinishPolicy.skinLikelihood(
                            rr,gg,bb,(int)lum);
                    float requested=StudioFinishPolicy.requestedTone(
                            lum,local,guide.p90,texture,strength,skin);
                    int move=Math.round(requested);

                    int red,green,blue,alpha;
                    if(nativeP3){
                        int off=((top+y)*w+x)*4;
                        int r=originalBytes.get(off)&255;
                        int g=originalBytes.get(off+1)&255;
                        int b=originalBytes.get(off+2)&255;
                        alpha=originalBytes.get(off+3)&255;
                        if(alpha!=255)
                            throw new IllegalStateException("Transparent P3 source");
                        // Small equal-channel increments retain encoded P3
                        // hue/chroma; gamut protection is fitted to all
                        // three native channels, not retagged as sRGB.
                        if(move>0)
                            move=Math.min(move,255-Math.max(r,Math.max(g,b)));
                        else if(move<0)
                            move=Math.max(move,-Math.min(r,Math.min(g,b)));
                        red=r+move;green=g+move;blue=b+move;
                        outputBytes.put((byte)red).put((byte)green)
                                .put((byte)blue).put((byte)alpha);
                    }else{
                        red=StudioFinishPolicy.channelWithTone(rr,requested);
                        green=StudioFinishPolicy.channelWithTone(gg,requested);
                        blue=StudioFinishPolicy.channelWithTone(bb,requested);
                        alpha=px>>>24;
                        edited[pos]=(alpha<<24)|(red<<16)|(green<<8)|blue;
                    }
                    int delta=Math.abs(move);
                    if(delta>0)editedPixels++;
                    channelDelta+=delta/255.0;
                    maxEdit=Math.max(maxEdit,delta);
                }
            }
            if(!nativeP3)out.setPixels(edited,0,w,0,top,w,count);
        }
        if(nativeP3){
            outputBytes.rewind();
            out.copyPixelsFromBuffer(outputBytes);
            if(!profile.equals(out.getColorSpace()))
                throw new IllegalStateException("Native P3 output profile lost");
            for(int n=0;n<7;n++){
                int x=(n+1)*w/9,y=(n+2)*h/10;
                int off=(y*w+x)*4;
                android.graphics.Color actual=out.getColor(x,y);
                if(!profile.equals(actual.getColorSpace())
                    ||Math.abs(actual.red()-(outputBytes.get(off)&255)/255f)>.015f
                    ||Math.abs(actual.green()-(outputBytes.get(off+1)&255)/255f)>.015f
                    ||Math.abs(actual.blue()-(outputBytes.get(off+2)&255)/255f)>.015f)
                        throw new IllegalStateException("P3 output pixel verification failed");
            }
        }
        return new Candidate(out,maxEdit,editedPixels,
                channelDelta/(w*(double)h));
    }
}
