package com.dennis.aimasteringlab;

import java.io.*;
import java.util.*;
import org.json.*;

/** Bounded offline editing. Every render starts from the retained base master. */
final class StudioAudio {
    static final AudioAnalyzer.Progress QUIET=(s,p)->{};
    static void cancel()throws IOException{if(Thread.currentThread().isInterrupted())throw new IOException("Studio operation paused");}
    static AnalysisResult measure(File f,AudioAnalyzer.Progress progress)throws IOException{
        return AudioAnalyzer.analyze(f.getName(),()->new FileInputStream(f),progress);
    }
    static JSONObject meters(AnalysisResult a)throws JSONException{
        return new JSONObject().put("lufs",a.integratedLufs).put("true_peak",a.truePeakEstimateDbtp)
                .put("lra",a.lraLu).put("crest",a.crestFactorDb).put("correlation",a.stereoCorrelation)
                .put("mono_loss_db",a.monoFoldLossDb).put("frames",a.wav.frames).put("rate",a.wav.sampleRate)
                .put("duration",a.wav.durationSeconds).put("sha256",a.sourceSha256);
    }
    static void valid(AnalysisResult a,long frames)throws IOException{
        if(a.wav.frames!=frames||a.wav.channels!=2||a.wav.sampleRate!=48000||a.nonFiniteSamples!=0
                ||!Double.isFinite(a.integratedLufs)||!Double.isFinite(a.truePeakEstimateDbtp))throw new IOException("Studio audio verification failed");
    }
    static final class EditResult {
        File file;AnalysisResult analysis;double fadeIn,fadeOut;boolean boundaryIn,boundaryOut;
    }
    /** Studio-only fast path: bypasses redundant 32-bit PCM render AND full scan.
     * Safe only when the exact existing editor would make no sample changes,
     * including its implicit boundary-fade rules. Never bypasses final QC. */
    static boolean studioPassthroughSafe(File source,AnalysisResult a,StudioRecipe recipe)throws IOException{
        if(recipe.trimStart!=0||recipe.trimEnd!=0||recipe.fadeIn!=0||recipe.fadeOut!=0)return false;
        for(StudioRecipe.Edit e:recipe.edits)if(e.enabled)return false;
        if(recipe.boundaryRepair){
            double[] edge=edges(source,0,a.wav.frames);
            if(edge[0]>.005||edge[1]>.005)return false;
        }
        return true;
    }

    static EditResult edit(File source,AnalysisResult a,StudioRecipe recipe,File out,AudioAnalyzer.Progress progress)throws Exception{
        recipe.validate(a.wav.durationSeconds);EditResult result=new EditResult();result.file=out;
        long first=Math.round(recipe.trimStart*48000),last=Math.min(a.wav.frames,Math.round(recipe.end(a.wav.durationSeconds)*48000));
        long frames=last-first;
        double[] edges=edges(source,first,last);
        result.boundaryIn=recipe.boundaryRepair&&edges[0]>.005;result.boundaryOut=recipe.boundaryRepair&&edges[1]>.005;
        result.fadeIn=Math.max(recipe.fadeIn,result.boundaryIn?.005:0);result.fadeOut=Math.max(recipe.fadeOut,result.boundaryOut?.010:0);
        ArrayList<Lane> lanes=new ArrayList<>();for(StudioRecipe.Edit e:recipe.edits)if(e.enabled)lanes.add(new Lane(e));
        File stage=WavRenderIntegrity.stagingFile(out,"studio-edit");
        try(WavFile.Reader r=WavFile.open(new FileInputStream(source));PcmExportWriter w=new PcmExportWriter(stage,48000,32,frames)){
            float[][] b=new float[2][4096];long at=0;int n;
            while((n=r.readFrames(b,4096))>0){
                for(int i=0;i<n;i++,at++){
                    double l=b[0][i],right=b[1][i],mid=(l+right)*.5,side=(l-right)*.5,t=at/48000.0;
                    double dl=0,dr=0,gainDb=0,budget=0;
                    for(Lane lane:lanes){
                        lane.process(l,right,mid,side,t);
                        if(lane.edit.kind.equals("GAIN"))gainDb+=lane.applied;
                        else{dl+=lane.left;dr+=lane.right;budget+=Math.abs(lane.applied);}
                    }
                    double scale=budget>4?4/budget:1,gain=Math.pow(10,Math.max(-3,Math.min(3,gainDb))/20);
                    l=(l+dl*scale)*gain;right=(right+dr*scale)*gain;
                    if(at>=first&&at<last){
                        long pos=at-first;double fade=1;
                        if(result.fadeIn>0)fade*=smooth(pos/Math.max(1,result.fadeIn*48000));
                        if(result.fadeOut>0)fade*=smooth((frames-1-pos)/Math.max(1,result.fadeOut*48000));
                        w.frame(l*fade,right*fade);
                    }
                }
                cancel();progress.update("Rendering saved passage edits",(int)(100*at/a.wav.frames));
            }
            if(at!=a.wav.frames)throw new IOException("Source changed during studio render");
        }catch(Exception e){WavRenderIntegrity.discard(stage);throw e;}
        WavRenderIntegrity.commit(stage,out,frames*8,48000,2,32,true);
        result.analysis=measure(out,progress);valid(result.analysis,frames);return result;
    }
    private static double[] edges(File source,long first,long last)throws IOException{
        double[] p=new double[2];float[][] b=new float[2][1];
        try(WavFile.Reader r=WavFile.open(new FileInputStream(source))){r.skipFrames(first);r.readFrames(b,1);p[0]=Math.max(Math.abs(b[0][0]),Math.abs(b[1][0]));}
        try(WavFile.Reader r=WavFile.open(new FileInputStream(source))){r.skipFrames(last-1);r.readFrames(b,1);p[1]=Math.max(Math.abs(b[0][0]),Math.abs(b[1][0]));}return p;
    }
    private static final class Lane {
        final StudioRecipe.Edit edit;final Biquad a,b;double env,applied,left,right;
        Lane(StudioRecipe.Edit e){edit=e;a=Biquad.bandPass(48000,e.hz,e.q);b=Biquad.bandPass(48000,e.hz,e.q);}
        void process(double l,double r,double mid,double side,double t){
            double x=edit.channel.equals("MID")?mid:edit.channel.equals("SIDE")?side:l;
            double y=edit.channel.equals("STEREO")?r:x;
            double bandA=a.process(x),bandB=b.process(y);
            double energy=(bandA*bandA+bandB*bandB)*.5;
            env+=(energy-env)*(energy>env?.0007:.00012);
            double width=Math.min(.10,(edit.end-edit.start)*.25);
            double envelope=smooth((t-edit.start)/width)*smooth((edit.end-t)/width);
            double amount=edit.db;
            if(edit.kind.equals("DYNAMIC_EQ")){
                double over=10*Math.log10(Math.max(1e-20,env))-edit.threshold;
                amount*=smooth(over/4);
            }
            applied=amount*envelope;double c=Math.pow(10,applied/20)-1;
            if(edit.kind.equals("GAIN")){left=right=0;return;}
            // Constant-peak bandpass delta, evaluated in parallel. Zero depth is exact identity.
            if(edit.channel.equals("MID")){left=right=bandA*c;}
            else if(edit.channel.equals("SIDE")){left=bandA*c;right=-left;}
            else{left=bandA*c;right=bandB*c;}
        }
    }
    static double smooth(double x){x=Math.max(0,Math.min(1,x));return x*x*(3-2*x);}
    static final class Delivery {
        File file;AnalysisResult analysis;MasterRenderer.Result render;boolean fallback;String note;
        int limiterPeakCorrections;double limiterCeilingDbtp=Double.NaN;
    }
    static Delivery deliver(File source,AnalysisResult a,StudioRecipe r,File output,AudioAnalyzer.Progress progress)throws IOException{
        double requested=r.target-a.integratedLufs;
        double flat=Math.min(requested,r.ceiling-.025-a.truePeakEstimateDbtp);
        boolean limit=requested>flat+.02&&requested-flat<=3.0;
        Delivery d=new Delivery();d.file=output;
        if(Math.abs(requested)<.05&&a.truePeakEstimateDbtp<=r.ceiling&&a.wav.bitsPerSample==24){
            StudioProject.copy(new FileInputStream(source),output);d.analysis=measure(output,progress);
            d.note="Selected mastered candidate already met delivery target; delivery stage applied no additional gain or limiter";return d;
        }
        double gain=requested;
        if(limit){
            // v0.5.2: the 4x limiter's down-sampling filter can put the final true
            // peak a few tenths of a dB above the limiter ceiling. That used to drop
            // straight to flat gain (delivered quieter) even when every transparency
            // check passed. Now the limiter ceiling is lowered by the measured
            // overshoot and the render is checked again. Same safety limits.
            double limiterCeiling=r.ceiling-.18;
            int loudnessPasses=0,peakPasses=0;
            while(true){
                d.render=MasterRenderer.renderAttempt(()->new FileInputStream(source),a,gain,true,limiterCeiling,
                        MasterRenderer.LimiterProfile.OVERSAMPLED_CLEAN,output,progress);
                d.analysis=measure(output,progress);valid(d.analysis,a.wav.frames);
                boolean safe=d.render.maximumLimiterReductionDb<=3.1&&d.render.averageLimiterReductionDb<=.20
                        &&d.render.limiterFramesAboveOneDbPercent<=4&&Math.abs(d.analysis.lraLu-a.lraLu)<=.7
                        &&a.crestFactorDb-d.analysis.crestFactorDb<=1.5&&d.render.quantizerClips==0;
                if(!safe){d.fallback=true;break;}
                double overshoot=d.analysis.truePeakEstimateDbtp-r.ceiling;
                if(overshoot>.005){
                    if(++peakPasses>3||limiterCeiling-(overshoot+.03)<r.ceiling-1.0){d.fallback=true;break;}
                    limiterCeiling-=overshoot+.03;d.limiterPeakCorrections=peakPasses;continue;
                }
                double deficit=r.target-d.analysis.integratedLufs;
                if(Math.abs(deficit)<=.08)break;
                if(++loudnessPasses>2||gain+deficit-requested>.5){d.fallback=true;break;}
                gain+=deficit;
            }
            d.limiterCeilingDbtp=limiterCeiling;
        }
        if(!limit||d.fallback){
            d.render=MasterRenderer.renderAttempt(()->new FileInputStream(source),a,flat,false,r.ceiling,
                    MasterRenderer.LimiterProfile.BALANCED,output,progress);
            d.analysis=measure(output,progress);
        }
        valid(d.analysis,a.wav.frames);
        if(d.analysis.truePeakEstimateDbtp>r.ceiling+.005||d.render.quantizerClips!=0)throw new IOException("Delivery did not pass final true-peak/PCM checks");
        d.note=Math.abs(d.analysis.integratedLufs-r.target)>.15?"TARGET REVIEW: delivered quieter to preserve dynamics and peak safety":"Requested delivery loudness and peak checks passed";
        return d;
    }
    static void region(File source,File output,double start,double seconds,AudioAnalyzer.Progress progress)throws IOException{
        try(WavFile.Reader r=WavFile.open(new FileInputStream(source))){
            long first=Math.max(0,Math.round(start*r.info.sampleRate)),frames=Math.round(seconds*r.info.sampleRate);
            if(r.info.sampleRate!=48000||r.info.channels!=2||frames<2400||first+frames>r.info.frames)throw new IOException("Choose a passage inside both songs (at least 0.05 seconds)");
            r.skipFrames(first);
            try(PcmExportWriter w=new PcmExportWriter(output,48000,32,frames)){
                float[][] b=new float[2][4096];long done=0;
                while(done<frames){int n=r.readFrames(b,(int)Math.min(4096,frames-done));if(n<=0)throw new IOException("Truncated reference passage");for(int i=0;i<n;i++)w.frame(b[0][i],b[1][i]);done+=n;cancel();}
            }
        }
    }
}
