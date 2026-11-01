package com.dennis.aimasteringlab;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Executable production-path checks: editing, persistence, recovery, delivery and album PCM. */
public final class StudioV013Harness {
    static final AudioAnalyzer.Progress Q=(s,p)->{};
    static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception{
        File work=new File(args[0]);work.mkdirs();File source=fixture(work,"clean.wav",false);
        AnalysisResult a=StudioAudio.measure(source,Q);String hash=Hashing.sha256(source);
        StudioRecipe r=new StudioRecipe();r.boundaryRepair=false;r.lowEnd=false;r.title="Fixture";
        File identity=new File(work,"identity.wav");StudioAudio.edit(source,a,r,identity,Q);
        require(Hashing.sha256(identity).equals(hash),"Disabled processing did not preserve exact float PCM");
        StudioRecipe.Edit gain=new StudioRecipe.Edit();gain.start=5;gain.end=7;gain.db=-1.5;r.edits.add(gain);
        File changed=new File(work,"gain-edit.wav");StudioAudio.edit(source,a,r,changed,Q);
        compareOutside(source,changed,5,7);
        double ratio=windowRms(changed,5.5,1)/windowRms(source,5.5,1);
        require(Math.abs(20*Math.log10(ratio)+1.5)<.0001,"Passage gain is wrong");
        gain.enabled=false;StudioAudio.edit(source,a,r,identity,Q);require(Hashing.sha256(identity).equals(hash),"Edit bypass is not identity");
        r.edits.clear();r.trimStart=1.25;r.trimEnd=18.75;r.fadeIn=.02;r.fadeOut=.05;
        StudioAudio.EditResult trimmed=StudioAudio.edit(source,a,r,new File(work,"trimmed.wav"),Q);
        require(trimmed.analysis.wav.frames==840000,"Trim lost sample accuracy");
        require(trimmed.analysis.firstFramePeak==0&&trimmed.analysis.lastFramePeak==0,"Fade boundaries are not zero");
        r.trimStart=0;r.trimEnd=0;r.fadeIn=0;r.fadeOut=0;r.boundaryRepair=true;
        StudioAudio.EditResult edges=StudioAudio.edit(source,a,r,new File(work,"edges.wav"),Q);
        require(edges.boundaryIn&&edges.boundaryOut&&edges.analysis.firstFramePeak==0&&edges.analysis.lastFramePeak==0,"Conditional edge repair failed");
        r.boundaryRepair=false;StudioRecipe.Edit eq=new StudioRecipe.Edit();eq.kind="DYNAMIC_EQ";eq.channel="MID";eq.start=5;eq.end=7;eq.hz=120;eq.db=-1.2;eq.threshold=-45;r.edits.add(eq);
        File dynamic=new File(work,"dynamic.wav");StudioAudio.edit(source,a,r,dynamic,Q);compareOutside(source,dynamic,5,7);sideExact(source,dynamic);
        require(StudioLowEnd.propose(StudioLowEnd.scan(source,Q)).isEmpty(),"Steady bass falsely proposed for repair");
        File bloom=fixture(work,"bloom.wav",true);AnalysisResult bloomAnalysis=StudioAudio.measure(bloom,Q);StudioLowEnd.Scan bloomScan=StudioLowEnd.scan(bloom,Q);
        List<StudioRecipe.Edit> proposals=StudioLowEnd.propose(bloomScan);require(!proposals.isEmpty(),"Isolated bloom positive fixture was not detected");
        StudioRecipe automatic=new StudioRecipe();automatic.boundaryRepair=false;automatic.edits.addAll(proposals);
        File repaired=new File(work,"bloom-repaired.wav");StudioAudio.edit(bloom,bloomAnalysis,automatic,repaired,Q);
        JSONObject gate=StudioLowEnd.verify(bloomScan,StudioLowEnd.scan(repaired,Q),proposals,0);
        require(gate.getBoolean("passed")&&gate.getInt("events_checked")>0,"Low-end candidate did not prove benefit: "+gate);
        require(!StudioLowEnd.verify(bloomScan,bloomScan,proposals,0).getBoolean("passed"),"Unchanged candidate incorrectly passed benefit gate");
        System.out.println("STUDIO_EDIT_PASS identity/bypass/isolated-gain/sample-exact-trim/fades/mid-side/low-end-positive-and-negative "+gate);
        persistence(work,source,a);queue(work);references(source,a,work);delivery(work,source,a);pipeline(work,source,a);
        require(Hashing.sha256(source).equals(hash),"Source modified by studio operations");
        Thread.currentThread().interrupt();try{StudioAudio.edit(source,a,new StudioRecipe(),new File(work,"cancelled.wav"),Q);throw new AssertionError("Edit cancellation ignored");}catch(IOException expected){}finally{Thread.interrupted();}
        System.out.println("STUDIO_V013_CORE_PASS");
    }
    static File fixture(File work,String name,boolean bloom)throws Exception{
        File f=new File(work,name);long frames=48000*20;
        try(PcmExportWriter w=new PcmExportWriter(f,48000,32,frames)){
            for(long i=0;i<frames;i++){
                double t=i/48000.0,bass=.035;
                if(bloom&&t>=7&&t<=8.5)bass+=.17*StudioAudio.smooth((t-7)/.4)*StudioAudio.smooth((8.5-t)/.4);
                double m=.018+bass*Math.cos(2*Math.PI*120*t)+.09*Math.sin(2*Math.PI*1100*t)+.055*Math.sin(2*Math.PI*2300*t),side=.008*Math.sin(2*Math.PI*810*t);
                w.frame(m+side,m-side);
            }
        }return f;
    }
    static void compareOutside(File a,File b,double start,double end)throws Exception{
        try(WavFile.Reader x=WavFile.open(new FileInputStream(a));WavFile.Reader y=WavFile.open(new FileInputStream(b))){
            float[][] u=new float[2][1024],v=new float[2][1024];int n;long at=0;
            while((n=x.readFrames(u,1024))>0){require(y.readFrames(v,1024)==n,"Edit frame mismatch");for(int i=0;i<n;i++,at++)if(at<start*48000||at>=end*48000)for(int c=0;c<2;c++)require(u[c][i]==v[c][i],"Edit changed protected sample "+at);}
        }
    }
    static void sideExact(File a,File b)throws Exception{
        try(WavFile.Reader x=WavFile.open(new FileInputStream(a));WavFile.Reader y=WavFile.open(new FileInputStream(b))){float[][] u=new float[2][1024],v=new float[2][1024];int n;while((n=x.readFrames(u,1024))>0){y.readFrames(v,1024);for(int i=0;i<n;i++)require(Math.abs((u[0][i]-u[1][i])-(v[0][i]-v[1][i]))<3e-8,"Mid edit modified side audio");}}
    }
    static double windowRms(File f,double start,double duration)throws Exception{
        double sum=0;long count=0;try(WavFile.Reader r=WavFile.open(new FileInputStream(f))){r.skipFrames(Math.round(start*48000));float[][] b=new float[2][1024];long frames=Math.round(duration*48000);while(count<frames){int n=r.readFrames(b,(int)Math.min(1024,frames-count));for(int i=0;i<n;i++)sum+=b[0][i]*b[0][i]+b[1][i]*b[1][i];count+=n;}}return Math.sqrt(sum/(2*count));
    }
    static void persistence(File work,File input,AnalysisResult a)throws Exception{
        File root=new File(work,"projects");root.mkdirs();StudioProject p=StudioProject.create(root,"Test");File source=p.child("source.wav");StudioProject.copy(new FileInputStream(input),source);p.file("source",source);p.data.put("source_hash",Hashing.sha256(source));p.data.put("duration",a.wav.durationSeconds);p.state("QUEUED","ready");
        StudioRecipe recipe=p.recipe();recipe.target=-16;p.recipe(recipe);recipe.target=-18;p.recipe(recipe);
        StudioProject loaded=StudioProject.load(p.dir);require(loaded.recipe().target==-18,"Saved recipe not restored");loaded.undo();require(loaded.recipe().target==-16,"Recipe undo wrong");
        loaded.state("MASTERING","interrupted");require(StudioProject.interruptedState(StudioProject.load(p.dir).state()),"Interrupted state lost");
        Files.write(new File(p.dir,"project.json").toPath(),"{truncated".getBytes(StandardCharsets.UTF_8));
        loaded=StudioProject.load(p.dir);require(loaded.state().equals("PAUSED")&&loaded.validFile("source","source_hash"),"Atomic backup recovery failed");
        try{loaded.child("../escape.wav");throw new AssertionError("Path escape allowed");}catch(IOException expected){}
        Files.write(source.toPath(),new byte[]{1,2,3});require(!loaded.validFile("source","source_hash"),"Corrupted source accepted");
        System.out.println("STUDIO_PROJECT_PASS persisted-recipes/undo/interrupted-state/corrupt-json-recovery/source-hash/path-scope");
    }
    static void references(File source,AnalysisResult a,File work)throws Exception{
        File region=new File(work,"reference-region.wav");StudioAudio.region(source,region,3,5,Q);
        JSONObject profile=StudioReferences.profile(region,Q);require(StudioReferences.suggestions(profile,profile,3,8).length()==0,"Identical reference generated EQ");
        try(ComparisonAudioStream s=new ComparisonAudioStream(source,a,source,a)){s.delta(true);float[] b=new float[2048];s.read(b,1024);for(int i=600;i<b.length;i++)require(b[i]==0,"Difference audition did not null identical files");}
        try(ComparisonAudioStream s=new ComparisonAudioStream(source,a,source,a)){s.loopBoundaries(0,1024);float[] b=new float[2048];s.read(b,1024);require(b[0]==0&&b[1]==0&&b[2046]==0&&b[2047]==0,"Loop boundary could click");}
        System.out.println("STUDIO_REFERENCE_PASS cropped-reference/identical-no-suggestion/aligned-delta-null");
    }
    static void queue(File work)throws Exception{
        File root=new File(work,"queue");root.mkdirs();StudioProject first=StudioProject.create(root,"First"),second=StudioProject.create(root,"Second"),third=StudioProject.create(root,"Third");
        first.data.put("order",1);second.data.put("order",2);third.data.put("order",3);for(StudioProject p:Arrays.asList(first,second,third))p.state("QUEUED","ready");
        try{StudioQueue.run(StudioQueue.select(root,false),(p,progress)->{
            if(p.name().equals("First"))throw new IOException("deliberately invalid input");p.state("COMPLETE","verified");
        },Q);throw new AssertionError("Failed queue item was concealed");}catch(IOException expected){}
        require(StudioProject.load(first.dir).state().equals("FAILED")&&StudioProject.load(second.dir).state().equals("COMPLETE")&&StudioProject.load(third.dir).state().equals("COMPLETE"),"Failure blocked later songs");
        require(StudioQueue.select(root,false).isEmpty()&&StudioQueue.select(root,true).size()==1,"Queue repeated completed songs");
        try{StudioQueue.run(StudioQueue.select(root,true),(p,progress)->{p.state("MASTERING","working");Thread.currentThread().interrupt();StudioAudio.cancel();},Q);throw new AssertionError("Queue ignored interruption");}catch(IOException expected){}finally{Thread.interrupted();}
        require(StudioProject.load(first.dir).state().equals("PAUSED"),"Interrupted song was not saved as paused");
        StudioQueue.run(StudioQueue.select(root,false),(p,progress)->p.state("COMPLETE","resumed"),Q);
        require(StudioQueue.select(root,true).isEmpty(),"Resumed queue not complete");System.out.println("STUDIO_QUEUE_PASS failed-item-isolation/completed-song-retention/pause/reopen/resume");
    }
    static void delivery(File work,File source,AnalysisResult a)throws Exception{
        StudioRecipe r=new StudioRecipe();r.target=-18;r.ceiling=-2;r.artist="Test Artist";r.album="Test Album";r.isrc="AUABC2600001";r.track=2;
        File out=new File(work,"Delivery_Master.wav");StudioAudio.Delivery d=StudioAudio.deliver(source,a,r,out,Q);
        require(Math.abs(d.analysis.integratedLufs+18)<.06&&d.analysis.truePeakEstimateDbtp<=-2,"Custom delivery target failed");
        double rms=windowRms(out,0,20);StudioMetadata.wav(out,r);AnalysisResult tagged=StudioAudio.measure(out,Q);require(windowRms(out,0,20)==rms&&tagged.wav.frames==a.wav.frames,"Metadata changed PCM");
        ReleaseFormats.Result formats=ReleaseFormats.create(out,tagged,work,"Metadata Test",Q,r);require(formats.complete(),"Tagged formats failed: "+formats.error);OfflineMp3.verifyFrames(formats.mp3);
        require(formats.wav16Qc.truePeakEstimateDbtp<=-2&&formats.mp3Qc.truePeakEstimateDbtp<=-2,"Delivery profile peak ceiling was not honored by derivatives");
        byte[] bytes=Files.readAllBytes(formats.mp3.toPath());String tags=new String(bytes,StandardCharsets.ISO_8859_1);require(tags.contains("Test Artist")&&tags.contains("Test Album")&&tags.contains("AUABC2600001"),"MP3 metadata missing");
        System.out.println("STUDIO_DELIVERY_PASS custom-loudness/true-peak/WAV-PCM-identity/MP3-metadata-and-decode");
    }
    static void pipeline(File work,File source,AnalysisResult a)throws Exception{
        File root=new File(work,"finished-projects");root.mkdirs();StudioProject p=StudioProject.create(root,"Project Example");File original=p.child("source.wav");StudioProject.copy(new FileInputStream(source),original);p.file("source",original);p.data.put("source_hash",Hashing.sha256(original));p.data.put("duration",a.wav.durationSeconds);
        StudioRecipe r=p.recipe();r.lowEnd=false;r.boundaryRepair=true;p.recipe(r);
        File base=p.child("base-master.wav");StudioAudio.Delivery initial=StudioAudio.deliver(original,a,r,base,Q);p.file("base",base);p.data.put("base_hash",Hashing.sha256(base));p.data.put("base_winner","fixture");File baseReport=p.child("Base_Master_QC.json");StudioProject.atomic(baseReport,"{}".getBytes(StandardCharsets.UTF_8));p.file("base_report",baseReport);p.save();
        StudioPipeline.finish(p,Q);p=StudioProject.load(p.dir);require(p.state().equals("COMPLETE")&&p.validFile("master","master_hash"),"Saved final studio package failed");
        String masterHash=p.data.getString("master_hash");File album=StudioAlbum.export(Collections.singletonList(p),new File(work,"album"),"Example Album",2,Q);require(album.isFile(),"Album export missing");
        require(masterHash.equals(Hashing.sha256(p.file("master"))),"Album changed individual master");
        require(StudioAlbum.compare(Collections.singletonList(p)).getJSONArray("songs").length()==1,"Album comparison omitted song");
        StudioRecipe changed=p.recipe();changed.target=-16;p.recipe(changed);
        try{StudioAlbum.export(Collections.singletonList(p),new File(work,"stale-album"),"Stale",0,Q);throw new AssertionError("Stale master exported as current album");}catch(IOException expected){}
        System.out.println("STUDIO_PIPELINE_PASS saved-master/formats/package/album/stale-render-rejection");
    }
}
