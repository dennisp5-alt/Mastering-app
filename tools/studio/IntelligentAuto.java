package com.dennis.photomasterai;

import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import java.nio.ByteBuffer;
import java.util.Locale;

/**
 * Offline, independent Auto Light and Auto White Balance stages.
 * Photo Master AI original master is always held for individual rollback.
 * No user photo is transmitted or reconstructed generatively.
 */
public final class IntelligentAuto {
    private IntelligentAuto() {}
    private static final float[] TONE_STRENGTHS={1f,.70f,.45f};
    private static final float[] WB_STRENGTHS={1f,.70f,.45f};
    private static final double WB_MAX_MEAN_MOVE=.016;
    private static final int WB_MAX_CHANNEL_EDIT=17;
    private static final double WB_MIN_DETAIL=.985;

    private static final class Analysis {
        int w,h;
        float[] local,texture;
        AutoTonePolicy.Plan plan;
        int eligibleCells;
        int eligiblePixels;
        double neutralR,neutralG,neutralB;
        float redGain=1f,blueGain=1f;
        boolean wbSupported;
        boolean p3;
    }
    private static final class Render {
        Bitmap bitmap;
        double meanMove;
        int maxMove;
        long edited;
        Render(Bitmap image,double avg,int max,long n){
            bitmap=image;meanMove=avg;maxMove=max;edited=n;
        }
    }
    public static PhotoMasterEngine.Result autoTone(
            Bitmap original,PhotoMasterEngine.Result base) {
        if(!supported(original,base))
            return report(base,"AUTO LIGHT","WITHHELD — unsupported or mismatched colour-managed inputs");
        try {
            Analysis a=analyse(base.bitmap);
            PhotoAnalysis before=PhotoAnalysis.analyse(base.bitmap);
            StringBuilder log=new StringBuilder(String.format(Locale.US,
                    "Scene p10 %.0f p50 %.0f p90 %.0f p98 %.0f"
                    +" | low-light %s | fire-light %s",
                    a.plan.p10,a.plan.p50,a.plan.p90,a.plan.p98,
                    a.plan.lowLight,a.plan.firelight));
            for(float strength:TONE_STRENGTHS) {
                Render candidate=render(base.bitmap,a,strength,false);
                PhotoAnalysis after=PhotoAnalysis.analyse(candidate.bitmap);
                double ratio=before.detail>1e-9 ? after.detail/before.detail:1.0;
                boolean accepted=candidate.edited>0
                        && ratio>=AutoTonePolicy.MIN_DETAIL_RETAINED
                        && after.ringing<=before.ringing+AutoTonePolicy.MAX_RING_INCREASE
                        && after.noise<=before.noise+.002
                        && after.flatChromaNoise<=before.flatChromaNoise+.002
                        && after.shadowClip<=before.shadowClip+AutoTonePolicy.MAX_CLIP_INCREASE
                        && after.highlightClip<=before.highlightClip+AutoTonePolicy.MAX_CLIP_INCREASE
                        && Math.abs(after.avgSat-before.avgSat)<=.022
                        && candidate.meanMove<=AutoTonePolicy.MAX_MEAN_CHANNEL_CHANGE
                        && candidate.maxMove<=AutoTonePolicy.MAX_CHANNEL_EDIT;
                log.append(String.format(Locale.US,
                        "\n• Light %.2f %s | detail %.2f%% | ring %.4f→%.4f"
                        +" | clip S %.4f→%.4f H %.4f→%.4f"
                        +" | average RGB %.5f | peak %d/255 | edit %.1f%%",
                        strength,accepted?"ACCEPTED":"rejected",
                        ratio*100,before.ringing,after.ringing,
                        before.shadowClip,after.shadowClip,
                        before.highlightClip,after.highlightClip,
                        candidate.meanMove,candidate.maxMove,
                        candidate.edited*100.0/(base.bitmap.getWidth()*(double)base.bitmap.getHeight())));
                if(accepted)
                    return new PhotoMasterEngine.Result(candidate.bitmap,
                            base.report+"\n\nAUTO LIGHT — ACCEPTED\n• "+log,after);
                candidate.bitmap.recycle();
            }
            return report(base,"AUTO LIGHT",
                    "ROLLED BACK — all proposed tonal changes rejected\n• "+log);
        } catch(Throwable problem){
            return report(base,"AUTO LIGHT",
                    "WITHHELD — "+problem.getClass().getSimpleName()
                    +" ("+String.valueOf(problem.getMessage())+")");
        }
    }

    public static PhotoMasterEngine.Result autoWhiteBalance(
            Bitmap original,PhotoMasterEngine.Result base) {
        if(!supported(original,base))
            return report(base,"AUTO WHITE BALANCE","WITHHELD — profile or geometry mismatch");
        try {
            Analysis a=analyse(base.bitmap);
            if(a.plan.firelight)
                return report(base,"AUTO WHITE BALANCE",
                        "PRESERVED — intentional warm firelight; no confident neutral illuminant");
            if(!a.wbSupported)
                return report(base,"AUTO WHITE BALANCE",
                        String.format(Locale.US,
                                "WITHHELD — insufficient spatially distributed neutral evidence"
                                +" (samples %d | cells %d); camera colour retained",
                                a.eligiblePixels,a.eligibleCells));
            float r=a.redGain,b=a.blueGain;
            if(Math.abs(r-1f)<.012f && Math.abs(b-1f)<.012f)
                return report(base,"AUTO WHITE BALANCE",
                        String.format(Locale.US,
                                "PRESERVED — camera white balance already neutral"
                                +" | measured gains R %.4f B %.4f | neutral regions %d",
                                r,b,a.eligibleCells));
            PhotoAnalysis before=PhotoAnalysis.analyse(base.bitmap);
            StringBuilder log=new StringBuilder(String.format(Locale.US,
                    "Candidate neural-independent neutral estimate: R %.4f, B %.4f"
                    +" | eligible pixels %d | distributed cells %d",
                    r,b,a.eligiblePixels,a.eligibleCells));
            for(float strength:WB_STRENGTHS) {
                Render candidate=render(base.bitmap,a,strength,true);
                PhotoAnalysis after=PhotoAnalysis.analyse(candidate.bitmap);
                double ratio=before.detail>1e-9 ? after.detail/before.detail:1.0;
                boolean accepted=candidate.edited>0
                        && ratio>=WB_MIN_DETAIL
                        && after.ringing<=before.ringing+.006
                        && after.noise<=before.noise+.002
                        && after.flatChromaNoise<=before.flatChromaNoise+.002
                        && after.shadowClip<=before.shadowClip+.001
                        && after.highlightClip<=before.highlightClip+.001
                        && Math.abs(after.avgSat-before.avgSat)<=.022
                        && candidate.meanMove<=WB_MAX_MEAN_MOVE
                        && candidate.maxMove<=WB_MAX_CHANNEL_EDIT;
                log.append(String.format(Locale.US,
                        "\n• WB %.2f %s | detail %.2f%% | ring %.4f→%.4f"
                        +" | mean change %.5f | peak %d/255 | pixels %.1f%%",
                        strength,accepted?"ACCEPTED":"rejected",
                        ratio*100,before.ringing,after.ringing,
                        candidate.meanMove,candidate.maxMove,
                        candidate.edited*100.0/(base.bitmap.getWidth()*(double)base.bitmap.getHeight())));
                if(accepted)
                    return new PhotoMasterEngine.Result(candidate.bitmap,
                            base.report+"\n\nAUTO WHITE BALANCE — ACCEPTED\n• "+log,after);
                candidate.bitmap.recycle();
            }
            return report(base,"AUTO WHITE BALANCE",
                    "ROLLED BACK — QC prevented colour shift\n• "+log);
        }catch(Throwable issue){
            return report(base,"AUTO WHITE BALANCE",
                    "WITHHELD — "+issue.getClass().getSimpleName()
                    +" ("+String.valueOf(issue.getMessage())+")");
        }
    }

    private static PhotoMasterEngine.Result report(
            PhotoMasterEngine.Result base,String name,String message){
        return new PhotoMasterEngine.Result(base.bitmap,
                base.report+"\n\n"+name+"\n• "+message,
                PhotoAnalysis.analyse(base.bitmap));
    }
    private static boolean supported(Bitmap original,PhotoMasterEngine.Result base){
        if(original==null || original.isRecycled() || base==null
                || base.bitmap==null || base.bitmap.isRecycled()
                || original.getWidth()!=base.bitmap.getWidth()
                || original.getHeight()!=base.bitmap.getHeight())
            return false;
        ColorSpace a=original.getColorSpace(),b=base.bitmap.getColorSpace();
        ColorSpace p3=ColorSpace.get(ColorSpace.Named.DISPLAY_P3);
        return a!=null && b!=null &&
                ((a.isSrgb() && b.isSrgb()) || (p3.equals(a) && p3.equals(b)));
    }
    private static Analysis analyse(Bitmap image){
        Analysis a=new Analysis();
        ColorSpace p3=ColorSpace.get(ColorSpace.Named.DISPLAY_P3);
        a.p3=p3.equals(image.getColorSpace());
        float ratio=Math.min(1f,384f/Math.max(image.getWidth(),image.getHeight()));
        a.w=Math.max(1,Math.round(image.getWidth()*ratio));
        a.h=Math.max(1,Math.round(image.getHeight()*ratio));
        Bitmap preview=Bitmap.createScaledBitmap(image,a.w,a.h,true);
        int[] pixels=new int[a.w*a.h];
        preview.getPixels(pixels,0,a.w,0,0,a.w,a.h);
        if(preview!=image)preview.recycle();
        int[] hist=new int[256];
        float[] lum=new float[pixels.length];
        int dark=0,warmBright=0;
        boolean[] cells=new boolean[12*16];
        double totalR=0,totalG=0,totalB=0;
        int eligible=0;
        for(int i=0;i<pixels.length;i++){
            int px=pixels[i];int r=(px>>16)&255,g=(px>>8)&255,b=px&255;
            float y=.2126f*r+.7152f*g+.0722f*b;
            lum[i]=y;
            hist[Math.min(255,Math.round(y))]++;
            if(y<45)dark++;
            boolean fire=r>g*1.30f && g>b*1.20f && r>145;
            if(fire)warmBright++;
            float spread=(Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b)))
                    /Math.max(1f,y);
            boolean reliable = y>56 && y<220
                    && spread<.16f
                    && (Math.max(r,Math.max(g,b))<240)
                    && Math.abs(r-b)<29;
            if(reliable){
                eligible++;totalR+=r;totalG+=g;totalB+=b;
                int x=i%a.w,yy=i/a.w;
                int ci=(yy*12/a.h)*16+(x*16/a.w);
                cells[ci]=true;
            }
        }
        float[] p=new float[4];
        int[] cuts={10,50,90,98};int t=0,sum=0;
        for(int i=0;i<256;i++){
            sum+=hist[i];
            while(t<cuts.length && sum>=Math.max(1,pixels.length*cuts[t]/100)){
                p[t]=i;t++;
            }
        }
        a.plan=new AutoTonePolicy.Plan(p[0],p[1],p[2],p[3],
                dark/(float)pixels.length,warmBright/(float)pixels.length);
        a.eligiblePixels=eligible;
        for(boolean v:cells)if(v)a.eligibleCells++;
        a.wbSupported=eligible>=Math.max(45,pixels.length/300)
                && a.eligibleCells>=5 && totalR>0 && totalB>0;
        if(a.wbSupported){
            float gr=(float)(totalG/totalR),gb=(float)(totalG/totalB);
            a.redGain=clamp(1f+.58f*(gr-1f),.91f,1.09f);
            a.blueGain=clamp(1f+.58f*(gb-1f),.91f,1.09f);
        }
        a.local=new float[pixels.length];
        a.texture=new float[pixels.length];
        for(int y=0;y<a.h;y++)for(int x=0;x<a.w;x++){
            int idx=y*a.w+x;
            float center=lum[idx],sumLocal=0,ws=0,vSum=0,vSq=0;
            for(int dy=-2;dy<=2;dy++)for(int dx=-2;dx<=2;dx++){
                int yy=Math.max(0,Math.min(a.h-1,y+dy));
                int xx=Math.max(0,Math.min(a.w-1,x+dx));
                float val=lum[yy*a.w+xx];
                float d=(val-center)/28f;
                float wgt=1f/(1f+d*d);
                ws+=wgt;sumLocal+=val*wgt;
                vSum+=val;vSq+=val*val;
            }
            a.local[idx]=sumLocal/Math.max(1e-5f,ws);
            float avg=vSum/25f;
            a.texture[idx]=(float)Math.sqrt(Math.max(0,vSq/25f-avg*avg));
        }
        return a;
    }
    private static float clamp(float v,float lo,float hi){return Math.max(lo,Math.min(hi,v));}
    private static int byteClamp(float f){return Math.max(0,Math.min(255,Math.round(f)));}
    private static float local(float[] field,int w,int h,float x,float y){
        float xx=clamp(x,0,w-1),yy=clamp(y,0,h-1);
        int x0=(int)xx,y0=(int)yy,x1=Math.min(w-1,x0+1),y1=Math.min(h-1,y0+1);
        float fx=xx-x0,fy=yy-y0;
        return ((field[y0*w+x0]*(1-fx)+field[y0*w+x1]*fx)*(1-fy)
                +(field[y1*w+x0]*(1-fx)+field[y1*w+x1]*fx)*fy);
    }
    private static Render render(Bitmap source,Analysis a,float strength,boolean wb){
        final int w=source.getWidth(),h=source.getHeight();
        final ColorSpace profile=source.getColorSpace();
        Bitmap image=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888,true,profile);
        ByteBuffer nativeInput=null,nativeOut=null;
        ColorSpace.Connector transform=null;
        if(a.p3){
            if(source.getConfig()!=Bitmap.Config.ARGB_8888
                || source.getRowBytes()!=w*4 || image.getRowBytes()!=w*4
                || (long)w*h>12_600_000L)
                throw new IllegalStateException("P3 bitmap storage unsupported");
            nativeInput=ByteBuffer.allocateDirect(source.getByteCount());
            nativeOut=ByteBuffer.allocateDirect(source.getByteCount());
            source.copyPixelsToBuffer(nativeInput);
            nativeInput.rewind();
            for(int k=0;k<7;k++){
                int x=(k+1)*w/9,y=(k+2)*h/10,off=(y*w+x)*4;
                android.graphics.Color value=source.getColor(x,y);
                if(!profile.equals(value.getColorSpace())
                    ||Math.abs(value.red()-(nativeInput.get(off)&255)/255f)>.015f
                    ||Math.abs(value.green()-(nativeInput.get(off+1)&255)/255f)>.015f
                    ||Math.abs(value.blue()-(nativeInput.get(off+2)&255)/255f)>.015f)
                        throw new IllegalStateException("Native P3 layout uncertain");
            }
            if(wb)transform=ColorSpace.connect(
                    ColorSpace.get(ColorSpace.Named.SRGB),profile);
        }
        final int chunks=Math.min(48,h);
        int[] row=new int[w*chunks],out=new int[w*chunks];
        long edited=0;double delta=0;int peak=0;
        for(int top=0;top<h;top+=chunks){
            int count=Math.min(chunks,h-top);
            source.getPixels(row,0,w,0,top,w,count);
            for(int y=0;y<count;y++)for(int x=0;x<w;x++){
                int i=y*w+x,base=row[i];
                int r=(base>>16)&255,g=(base>>8)&255,b=base&255;
                float lum=.2126f*r+.7152f*g+.0722f*b;
                float gx=(x+.5f)*a.w/w-.5f,gy=(top+y+.5f)*a.h/h-.5f;
                float localB=local(a.local,a.w,a.h,gx,gy);
                float tex=local(a.texture,a.w,a.h,gx,gy);
                float warm=AutoTonePolicy.smooth((r-g-27f)/73f)*
                        AutoTonePolicy.smooth((g-b-16f)/69f)*
                        AutoTonePolicy.smooth((lum-125f)/90f);
                float skin=StudioFinishPolicy.skinLikelihood(r,g,b,(int)lum);
                int nr=r,ng=g,nb=b;
                if(wb){
                    float sat=(Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b)))/Math.max(1f,lum);
                    float protect=AutoTonePolicy.smooth((lum-25f)/45f)
                            *(1f-AutoTonePolicy.smooth((lum-221f)/34f))
                            *(1f-.72f*AutoTonePolicy.smooth((sat-.20f)/.4f))
                            *(1f-.85f*warm)*(1f-.65f*skin);
                    float rd=r*(1f+(a.redGain-1f)*protect*strength);
                    float bd=b*(1f+(a.blueGain-1f)*protect*strength);
                    float balance=.2126f*(rd-r)+.0722f*(bd-b);
                    nr=byteClamp(r+clamp(rd-r-balance,-WB_MAX_CHANNEL_EDIT,WB_MAX_CHANNEL_EDIT));
                    ng=byteClamp(g-balance);
                    nb=byteClamp(b+clamp(bd-b-balance,-WB_MAX_CHANNEL_EDIT,WB_MAX_CHANNEL_EDIT));
                }else{
                    float move=AutoTonePolicy.offset(a.plan,lum,localB,tex,warm,skin,strength);
                    int requested=Math.round(move);
                    requested=Math.max(-AutoTonePolicy.MAX_CHANNEL_EDIT,
                            Math.min(AutoTonePolicy.MAX_CHANNEL_EDIT,requested));
                    if(requested>0)requested=Math.min(requested,255-Math.max(r,Math.max(g,b)));
                    if(requested<0)requested=Math.max(requested,-Math.min(r,Math.min(g,b)));
                    nr=r+requested;ng=g+requested;nb=b+requested;
                }
                int originalR=r,originalG=g,originalB=b;
                if(a.p3){
                    int off=((top+y)*w+x)*4;
                    originalR=nativeInput.get(off)&255;
                    originalG=nativeInput.get(off+1)&255;
                    originalB=nativeInput.get(off+2)&255;
                    int alpha=nativeInput.get(off+3)&255;
                    if(alpha!=255)throw new IllegalStateException("Native P3 alpha mismatch");
                    if(wb){
                        float[] before={r/255f,g/255f,b/255f};
                        float[] after={nr/255f,ng/255f,nb/255f};
                        transform.transform(before);transform.transform(after);
                        float maximum=WB_MAX_CHANNEL_EDIT/255f,fit=1f;
                        int[] bases={originalR,originalG,originalB};
                        for(int k=0;k<3;k++){
                            float d=after[k]-before[k];
                            if(d>1e-9f)fit=Math.min(fit,(1f-bases[k]/255f)/d);
                            if(d< -1e-9f)fit=Math.min(fit,-bases[k]/255f/d);
                            if(Math.abs(d)>1e-9f)fit=Math.min(fit,maximum/Math.abs(d));
                        }
                        fit=clamp(fit,0f,1f);
                        nr=byteClamp(originalR+fit*(after[0]-before[0])*255f);
                        ng=byteClamp(originalG+fit*(after[1]-before[1])*255f);
                        nb=byteClamp(originalB+fit*(after[2]-before[2])*255f);
                    }else{
                        int d=nr-r;
                        if(d>0)d=Math.min(d,255-Math.max(originalR,Math.max(originalG,originalB)));
                        if(d<0)d=Math.max(d,-Math.min(originalR,Math.min(originalG,originalB)));
                        nr=originalR+d;ng=originalG+d;nb=originalB+d;
                    }
                    nativeOut.put((byte)nr).put((byte)ng).put((byte)nb).put((byte)alpha);
                }else{
                    out[i]=(base&0xff000000)|(nr<<16)|(ng<<8)|nb;
                }
                int dr=Math.abs(nr-originalR),dg=Math.abs(ng-originalG),db=Math.abs(nb-originalB);
                if(dr+dg+db>0)edited++;
                delta+=(dr+dg+db)/(3.0*255);
                peak=Math.max(peak,Math.max(dr,Math.max(dg,db)));
            }
            if(!a.p3)image.setPixels(out,0,w,0,top,w,count);
        }
        if(a.p3){
            nativeOut.flip();
            image.copyPixelsFromBuffer(nativeOut);
            if(!profile.equals(image.getColorSpace()))
                throw new IllegalStateException("Native P3 output loses profile");
        }
        return new Render(image,delta/(w*(double)h),peak,edited);
    }
}
