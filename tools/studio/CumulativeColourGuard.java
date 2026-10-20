package com.dennis.photomasterai;

import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import java.nio.ByteBuffer;
import java.util.Locale;

/**
 * Final, baseline-referenced colour QC that operates AFTER the four
 * independent AI Studio enhancement stages.
 *
 * Prevents several individually successful stages cumulatively bleaching
 * bright pink/magenta fabric. The remainder of the image retains accepted
 * corrections. If the final stage cannot prove safety, the approved original
 * AI MAX baseline is retained rather than publishing an unsafe candidate.
 */
public final class CumulativeColourGuard {
    private CumulativeColourGuard() {}
    private static final int GRID_X=16,GRID_Y=12;

    private static final class Render {
        Bitmap image;
        long affected,protectedPixels,eligiblePixels;
        long protectedNearclipBefore,protectedNearclipAfter;
        long[] cellProtected = new long[GRID_X*GRID_Y];
        double[] cellProtectedDelta=new double[GRID_X*GRID_Y];
        double protectedMeanDelta;
        double worstRegionalMeanDelta;
        long brightIncrease;
        Render(Bitmap image){this.image=image;}
    }

    public static PhotoMasterEngine.Result apply(Bitmap original,
            PhotoMasterEngine.Result baseline,
            PhotoMasterEngine.Result studio) {
        if (baseline==null || studio==null || original==null
                || baseline.bitmap==null || studio.bitmap==null
                || original.isRecycled() || baseline.bitmap.isRecycled()
                || studio.bitmap.isRecycled())
            return studio;
        Bitmap reference=baseline.bitmap;
        Bitmap candidate=studio.bitmap;
        if (reference==candidate)
            return message(studio,
                    "PRESERVED — no cumulative candidate beyond AI MAX");
        if (reference.getWidth()!=candidate.getWidth()
                || reference.getHeight()!=candidate.getHeight()
                || original.getWidth()!=reference.getWidth()
                || original.getHeight()!=reference.getHeight())
            return message(studio,
                    "WITHHELD — mismatched geometry; prior result retained");

        ColorSpace p3=ColorSpace.get(ColorSpace.Named.DISPLAY_P3);
        ColorSpace cs=reference.getColorSpace();
        ColorSpace other=candidate.getColorSpace();
        ColorSpace src=original.getColorSpace();
        boolean nativeP3=p3.equals(cs)&&p3.equals(other)&&p3.equals(src);
        boolean srgb=cs!=null&&cs.isSrgb()
                && other!=null&&other.isSrgb()
                && src!=null&&src.isSrgb();
        if(!nativeP3&&!srgb)
            return message(studio,
                    "WITHHELD — unverified colour-space conversion; prior Studio result retained");

        Render guard=null;
        try {
            guard=render(original,reference,candidate,nativeP3);
            if(guard.affected==0){
                guard.image.recycle();
                return message(studio,
                        "PRESERVED — cumulative adjustments below regional thresholds");
            }
            PhotoAnalysis before=PhotoAnalysis.analyse(reference);
            PhotoAnalysis preGuard=PhotoAnalysis.analyse(candidate);
            PhotoAnalysis after=PhotoAnalysis.analyse(guard.image);
            double detailReference=before.detail>1e-9
                    ?after.detail/before.detail:1;
            double detailOriginal=preGuard.detail>1e-9
                    ?after.detail/preGuard.detail:1;
            double protectedClipGrowth=guard.eligiblePixels>0
                    ?(guard.protectedNearclipAfter
                        -guard.protectedNearclipBefore)/(double)guard.eligiblePixels
                    :0;

            boolean accepted=detailReference>=SelectiveColourPolicy.MIN_DETAIL_RETAINED
                    && detailOriginal>=SelectiveColourPolicy.MIN_DETAIL_RETAINED
                    && after.ringing<=before.ringing+SelectiveColourPolicy.MAX_RING_INCREASE
                    && after.ringing<=preGuard.ringing+SelectiveColourPolicy.MAX_RING_INCREASE
                    && after.noise<=before.noise+.003
                    && after.flatChromaNoise<=before.flatChromaNoise+.003
                    && after.shadowClip<=before.shadowClip+SelectiveColourPolicy.MAX_CLIP_INCREASE
                    && after.highlightClip<=before.highlightClip+SelectiveColourPolicy.MAX_CLIP_INCREASE
                    && Math.abs(after.avgSat-before.avgSat)<=.045
                    && guard.worstRegionalMeanDelta<=SelectiveColourPolicy.MAX_REGIONAL_MEAN_DELTA
                    && protectedClipGrowth<=SelectiveColourPolicy.MAX_PINK_NEARCLIP_GROWTH;

            String details=String.format(Locale.US,
                    "• Protected pale-pink pixels %d (%.2f%% of frame)"
                    +"\n• Candidate pixels moderated %d (%.2f%%)"
                    +"\n• Protected highlight >=248/255 before %d after %d"
                    +"\n• Protected newly near-clipped growth %.3f%%"
                    +"\n• Worst regional protected cumulative RGB delta %.5f"
                    +"\n• Detail retention baseline %.2f%% / pre-guard %.2f%%"
                    +"\n• Ringing AI MAX %.4f / Studio %.4f / guarded %.4f"
                    +"\n• Stage QC %s",
                    guard.eligiblePixels,
                    guard.eligiblePixels*100.0/(reference.getWidth()*(double)reference.getHeight()),
                    guard.affected,
                    guard.affected*100.0/(reference.getWidth()*(double)reference.getHeight()),
                    guard.protectedNearclipBefore,
                    guard.protectedNearclipAfter,
                    protectedClipGrowth*100,
                    guard.worstRegionalMeanDelta,
                    detailReference*100,detailOriginal*100,
                    before.ringing,preGuard.ringing,after.ringing,
                    accepted?"ACCEPTED":"rejected");

            if(accepted)
                return new PhotoMasterEngine.Result(guard.image,
                        studio.report+"\n\nCUMULATIVE COLOUR PROTECTION"
                            +"\n• ACCEPTED — bright pink fabric preserved without weakening the rest of Studio"
                            +"\n"+details,after);

            guard.image.recycle();
            return new PhotoMasterEngine.Result(reference,
                    studio.report+"\n\nCUMULATIVE COLOUR PROTECTION"
                            +"\n• ROLLED BACK TO AI MAX — unsafe combined result"
                            +"\n"+details,before);
        } catch(Throwable failure){
            if(guard!=null&&guard.image!=null&&!guard.image.isRecycled())
                guard.image.recycle();
            return new PhotoMasterEngine.Result(reference,
                    studio.report+"\n\nCUMULATIVE COLOUR PROTECTION"
                            +"\n• ROLLED BACK TO AI MAX — regional safety check failed: "
                            +failure.getClass().getSimpleName()
                            +" ("+String.valueOf(failure.getMessage())+")",
                    PhotoAnalysis.analyse(reference));
        }
    }

    private static PhotoMasterEngine.Result message(
            PhotoMasterEngine.Result result,String content){
        return new PhotoMasterEngine.Result(result.bitmap,
                result.report+"\n\nCUMULATIVE COLOUR PROTECTION\n• "+content,
                PhotoAnalysis.analyse(result.bitmap));
    }

    private static Render render(Bitmap original,Bitmap baseline,
            Bitmap enhanced,boolean nativeP3) {
        int w=baseline.getWidth(),h=baseline.getHeight();
        Bitmap output=Bitmap.createBitmap(
                w,h,Bitmap.Config.ARGB_8888,true,baseline.getColorSpace());
        Render status=new Render(output);
        ByteBuffer baseBytes=null,enhancedBytes=null,outBytes=null;
        if(nativeP3){
            if(baseline.getConfig()!=Bitmap.Config.ARGB_8888
                    ||enhanced.getConfig()!=Bitmap.Config.ARGB_8888
                    ||baseline.getRowBytes()!=w*4
                    ||enhanced.getRowBytes()!=w*4
                    ||output.getRowBytes()!=w*4
                    ||(long)w*h>12_600_000L)
                throw new IllegalStateException("P3 native RGB layout not available");
            baseBytes=ByteBuffer.allocateDirect(baseline.getByteCount());
            enhancedBytes=ByteBuffer.allocateDirect(enhanced.getByteCount());
            outBytes=ByteBuffer.allocateDirect(output.getByteCount());
            baseline.copyPixelsToBuffer(baseBytes);
            enhanced.copyPixelsToBuffer(enhancedBytes);
            baseBytes.rewind(); enhancedBytes.rewind();
            // Fail closed if device-specific ARGB pixel byte order differs.
            for(int k=0;k<7;k++){
                int x=(k+1)*w/9,y=(k+2)*h/10,idx=(y*w+x)*4;
                android.graphics.Color b=baseline.getColor(x,y);
                android.graphics.Color e=enhanced.getColor(x,y);
                if(Math.abs(b.red()-(baseBytes.get(idx)&255)/255f)>.015f
                   ||Math.abs(b.green()-(baseBytes.get(idx+1)&255)/255f)>.015f
                   ||Math.abs(b.blue()-(baseBytes.get(idx+2)&255)/255f)>.015f
                   ||Math.abs(e.red()-(enhancedBytes.get(idx)&255)/255f)>.015f
                   ||Math.abs(e.green()-(enhancedBytes.get(idx+1)&255)/255f)>.015f
                   ||Math.abs(e.blue()-(enhancedBytes.get(idx+2)&255)/255f)>.015f)
                    throw new IllegalStateException("P3 RGBA byte order uncertain");
            }
        }
        int chunk=Math.min(48,h);
        int[] src=new int[w*chunk],base=new int[w*chunk],
                finalPx=new int[w*chunk],out=new int[w*chunk];
        for(int top=0;top<h;top+=chunk){
            int n=Math.min(chunk,h-top);
            original.getPixels(src,0,w,0,top,w,n);
            if(!nativeP3){
                baseline.getPixels(base,0,w,0,top,w,n);
                enhanced.getPixels(finalPx,0,w,0,top,w,n);
            }
            for(int y=0;y<n;y++)for(int x=0;x<w;x++){
                int i=y*w+x,sp=src[i];
                int sr=(sp>>16)&255,sg=(sp>>8)&255,sb=sp&255;
                float mask=SelectiveColourPolicy.palePinkProtection(sr,sg,sb);
                if(mask>.50f)status.eligiblePixels++;
                int r0,g0,b0,r1,g1,b1,alpha;
                if(nativeP3){
                    int idx=((top+y)*w+x)*4;
                    r0=baseBytes.get(idx)&255;
                    g0=baseBytes.get(idx+1)&255;
                    b0=baseBytes.get(idx+2)&255;
                    r1=enhancedBytes.get(idx)&255;
                    g1=enhancedBytes.get(idx+1)&255;
                    b1=enhancedBytes.get(idx+2)&255;
                    alpha=enhancedBytes.get(idx+3)&255;
                    if(alpha!=255 || (baseBytes.get(idx+3)&255)!=255)
                        throw new IllegalStateException("Unexpected P3 transparency");
                }else{
                    int bp=base[i],ep=finalPx[i];
                    r0=(bp>>16)&255;g0=(bp>>8)&255;b0=bp&255;
                    r1=(ep>>16)&255;g1=(ep>>8)&255;b1=ep&255;
                    alpha=ep>>>24;
                }
                int r=SelectiveColourPolicy.guardChannel(r0,r1,mask);
                int g=SelectiveColourPolicy.guardChannel(g0,g1,mask);
                int b=SelectiveColourPolicy.guardChannel(b0,b1,mask);
                if(r!=r1 || g!=g1 || b!=b1)status.affected++;
                if(mask>.50f){
                    status.protectedPixels++;
                    status.protectedNearclipBefore +=
                            (Math.max(r0,Math.max(g0,b0))>=248)?1:0;
                    status.protectedNearclipAfter +=
                            (Math.max(r,Math.max(g,b))>=248)?1:0;
                    int region=((top+y)*GRID_Y/h)*GRID_X+x*GRID_X/w;
                    status.cellProtected[region]++;
                    status.cellProtectedDelta[region]+=
                            (Math.abs(r-r0)+Math.abs(g-g0)+Math.abs(b-b0))
                            /765.0;
                    status.protectedMeanDelta +=
                            (Math.abs(r-r0)+Math.abs(g-g0)+Math.abs(b-b0))
                            /765.0;
                }
                if(nativeP3)
                    outBytes.put((byte)r).put((byte)g)
                            .put((byte)b).put((byte)alpha);
                else out[i]=(alpha<<24)|(r<<16)|(g<<8)|b;
            }
            if(!nativeP3)output.setPixels(out,0,w,0,top,w,n);
        }
        if(nativeP3){
            outBytes.flip();
            output.copyPixelsFromBuffer(outBytes);
            if(!output.getColorSpace().equals(baseline.getColorSpace()))
                throw new IllegalStateException("P3 colour space lost");
        }
        for(int cell=0;cell<status.cellProtected.length;cell++)
            if(status.cellProtected[cell]>=16)
                status.worstRegionalMeanDelta=Math.max(
                        status.worstRegionalMeanDelta,
                        status.cellProtectedDelta[cell]/status.cellProtected[cell]);
        return status;
    }
}
