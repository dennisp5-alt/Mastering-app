package com.dennis.aimasteringlab;

import java.io.*;
import java.util.*;

/** Failure injection, per-channel repair, idempotence and measured limiter regressions. */
public final class NaturalStudioRecoveryHarness {
    static final AudioAnalyzer.Progress QUIET=(s,p)->{};
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static AnalysisResult measure(File f)throws Exception{return AudioAnalyzer.analyze(f.getName(),()->new FileInputStream(f),QUIET);}
    public static void main(String[] args)throws Exception{
        File input=new File(args[1]),work=new File(args[2]);work.mkdirs();
        if(args[0].equals("repair")){ repair(input,work); return; }
        if(args[0].equals("negative")){AnalysisResult a=measure(input);ClickDropoutRepair.Result r=ClickDropoutRepair.analyze(()->new FileInputStream(input),a,QUIET);require(r.isolatedClipRuns==0,"ambiguous or dense clipping admitted");System.out.println("DECLIP_NEGATIVE_PASS "+input.getName());return;}
        if(args[0].equals("sparse")){sparse(input,work);return;}
        if(args[0].equals("reprocess")){reprocess(input,work);return;}
        recovery(input,work,args[0].equals("cancel"));
    }
    static void repair(File input,File work)throws Exception{
        AnalysisResult a=measure(input); a.sourceGuard=SourceGuardModel.analyze(()->new FileInputStream(input),a,QUIET);
        a.clickDropoutRepair=ClickDropoutRepair.analyze(()->new FileInputStream(input),a,QUIET);
        require(a.clickDropoutRepair.isolatedClipRuns==3,"expected three per-channel plateaux: "+a.clickDropoutRepair.compactSummary());
        File out=new File(work,"repaired.wav");ClickDropoutRepair.render(()->new FileInputStream(input),a,a.clickDropoutRepair,out,QUIET);
        AnalysisResult b=TechnicalSourcePreparation.reanalyze(()->new FileInputStream(out),a,QUIET);
        require(b.originalSourceAnalysis==a&&b.wav.floatingPoint&&b.clippedSamples<a.clippedSamples,"fresh repair measurements and original audit");
        long changed=0,f=0;float[][]x=new float[2][4096],y=new float[2][4096];
        try(WavFile.Reader r=WavFile.open(new FileInputStream(input));WavFile.Reader q=WavFile.open(new FileInputStream(out))){int n;while((n=r.readFrames(x,4096))>0){require(q.readFrames(y,n)==n,"length");for(int i=0;i<n;i++,f++)for(int c=0;c<2;c++){
            boolean allowed=false;for(ClickDropoutRepair.Event e:a.clickDropoutRepair.events)if(f>=e.startFrame&&f<=e.endFrame&&(e.channelMask&(1<<c))!=0)allowed=true;
            if(x[c][i]!=y[c][i]){require(allowed,"repair modified unaffected channel/frame "+c+"/"+f);changed++;}
        }}}
        require(changed==12,"all overlapping channel plateaux repaired, expected 12 samples got "+changed);
        File master=new File(work,"master.wav");MasterRenderer.Result m=MasterRenderer.render(()->new FileInputStream(out),b,b.appliedSafeGainDb,master,QUIET);
        require(m.verification.clippedSamples==0&&m.verification.wav.frames==a.wav.frames,"repaired source masters successfully");
        System.out.printf(Locale.US,"DECLIP_REPAIR_PASS events=3 changedSamples=%d originalClips=%d repairedClips=%d LUFS=%.4f TP=%.4f%n",changed,a.clippedSamples,b.clippedSamples,m.verification.integratedLufs,m.verification.truePeakEstimateDbtp);
    }
    static void sparse(File input,File work)throws Exception{
        AnalysisResult a=measure(input);File out=new File(work,"sparse-master.wav");
        MasterRenderer.Result r=MasterRenderer.render(()->new FileInputStream(input),a,a.appliedSafeGainDb,out,QUIET);
        System.out.printf(Locale.US,"SPARSE_RESULT source=%.4f output=%.4f TP=%.4f max=%.4f avg=%.5f above1=%.4f above3=%.4f sparse=%s%n",a.integratedLufs,r.verification.integratedLufs,r.verification.truePeakEstimateDbtp,r.maximumLimiterReductionDb,r.averageLimiterReductionDb,r.limiterFramesAboveOneDbPercent,r.limiterFramesAboveThreeDbPercent,r.sparsePeakReleaseMode);
        require(r.exactTargetReached&&r.sparsePeakReleaseMode,"sparse high-crest source must reach target without dense limiting");
        require(Math.abs(r.verification.integratedLufs+14)<.02&&r.verification.truePeakEstimateDbtp<=-1,"target and true peak");
        File atten=new File(work,"codec-flat.wav");MasterRenderer.Result flat=MasterRenderer.renderCodecSafetyAttenuation(()->new FileInputStream(out),r.verification,r,.22,atten,QUIET);
        double g=Math.pow(10,-.22/20),maxError=0;float[][]x=new float[2][4096],y=new float[2][4096];
        try(WavFile.Reader left=WavFile.open(new FileInputStream(out));WavFile.Reader right=WavFile.open(new FileInputStream(atten))){int n;while((n=left.readFrames(x,4096))>0){require(right.readFrames(y,n)==n,"attenuation length");for(int c=0;c<2;c++)for(int i=0;i<n;i++)maxError=Math.max(maxError,Math.abs(y[c][i]-x[c][i]*g));}}
        require(maxError<3e-7&&flat.maximumLimiterReductionDb==r.maximumLimiterReductionDb,"codec safety changed envelope");
        System.out.printf(Locale.US,"FLAT_CODEC_PASS sampleError=%.9f LUFS=%.4f%n",maxError,flat.verification.integratedLufs);
    }
    static void reprocess(File input,File work)throws Exception{
        AnalysisResult a=measure(input);require(a.alreadyAtTargetNoRender,"test master must be compliant");
        AutonomousMasteringBrain.Result b=AutonomousMasteringBrain.analyze(()->new FileInputStream(input),a,QUIET);
        FullAiMasteringEngine.Result r=FullAiMasteringEngine.run(()->new FileInputStream(input),a,b,work,QUIET);
        require(r.selected.render.master.renderPasses==0&&Hashing.sha256(input).equals(Hashing.sha256(r.selected.file)),"reprocessing changed compliant master");
        require(r.professionalCandidatesAttempted==0,"clean compliant fixture unexpectedly required cleanup");
        for(FullAiMasteringEngine.Candidate c:r.candidates)require(c.nextGenerationMode!=NextGenerationMasteringRenderer.Mode.TRANSLATION_CONSENSUS&&c.nextGenerationMode!=NextGenerationMasteringRenderer.Mode.REFERENCE_AWARE,"diagnostic module entered competition");
        System.out.println("REPROCESS_BYTE_EXACT_PASS "+r.selected.strategy);
    }
    static void recovery(File input,File work,boolean cancel)throws Exception{
        AnalysisResult a=measure(input);
        a.sections=new TrackSections();
        a.advancedIntelligence=new AdvancedStereoModel.Result();
        AdvancedStereoModel.Event e=new AdvancedStereoModel.Event();
        e.kind=AdvancedStereoModel.Kind.SECTION_ENERGY_CONTOUR;e.startSeconds=5;e.endSeconds=7;e.primaryDb=.15;e.confidence=.99;e.automatic=true;e.evidence="fault-injection candidate";
        a.advancedIntelligence.events.add(e);
        a.stereoIntelligence=new StereoIntelligenceModel.Result();
        a.professionalIntelligence=new ProfessionalMasteringModel.Result();
        a.nextGenerationIntelligence=new NextGenerationMasteringModel.Result();
        AutonomousMasteringBrain.Result b=AutonomousMasteringBrain.analyze(()->new FileInputStream(input),a,QUIET);
        b.internalPlans.removeIf(p->!"transparent_flat_gain".equals(p.strategy));
        final boolean[] armed={false},triggered={false};
        AudioAnalyzer.Progress progress=(stage,percent)->{
            if(stage.startsWith("Full AI: rendering section_")&&!triggered[0]){triggered[0]=true;armed[0]=true;if(cancel)Thread.currentThread().interrupt();}
        };
        AudioAnalyzer.StreamProvider provider=()->{
            if(armed[0]){armed[0]=false;return new FilterInputStream(new FileInputStream(input)){
                int remaining=10000;
                public int read(byte[]b,int o,int n)throws IOException{if(remaining<=0)throw new EOFException("Unexpected end of file: injected optional candidate short read");int k=super.read(b,o,Math.min(n,remaining));if(k>0)remaining-=k;return k;}
                public int read()throws IOException{if(--remaining<=0)throw new EOFException("Unexpected end of file: injected optional candidate short read");return super.read();}
            };}
            return new FileInputStream(input);
        };
        FullAiMasteringEngine.Result r=null;
        try{r=FullAiMasteringEngine.run(provider,a,b,work,progress);require(!cancel,"cancellation returned a master");}
        catch(IOException ex){if(!cancel)throw ex;require(triggered[0]&&Thread.currentThread().isInterrupted(),"real cancellation must propagate");System.out.println("CANCELLATION_FAIL_CLOSED_PASS");return;}
        finally{Thread.interrupted();}
        require(triggered[0]&&!r.renderIntegrityFailures.isEmpty(),"optional fault was not exercised");
        require(r.selected==r.transparent&&r.selected.file.isFile()&&!r.selected.promotionBlocked,"verified baseline not retained");
        require(Hashing.sha256(r.selected.file).equals(r.selected.render.master.verification.sourceSha256),"baseline changed during failure");
        File damaged=new File(work,"truncated.wav");try(RandomAccessFile f=new RandomAccessFile(damaged,"rw")){f.write(java.nio.file.Files.readAllBytes(input.toPath()));f.setLength(1000);}
        try{measure(damaged);throw new AssertionError("truncated source accepted");}catch(IOException expected){}
        File dest=new File(work,"must-not-publish.wav");try{WavRenderIntegrity.commit(damaged,dest,a.wav.frames*6,48000,2,24,false);throw new AssertionError("partial WAV published");}catch(IOException expected){require(!dest.exists(),"partial destination exists");}
        System.out.println("OPTIONAL_EOF_RECOVERY_PASS winner="+r.selected.strategy+" warnings="+r.renderIntegrityFailures.size());
        System.out.println("TRUNCATED_SOURCE_AND_STAGING_FAIL_CLOSED_PASS");
    }
}
