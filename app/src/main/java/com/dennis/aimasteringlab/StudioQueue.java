package com.dennis.aimasteringlab;
import java.io.*;
import java.util.*;

/** Platform-independent queue state transitions, also exercised in host recovery tests. */
final class StudioQueue {
    interface Processor{void process(StudioProject project,AudioAnalyzer.Progress progress)throws Exception;}
    static List<StudioProject> select(File root,boolean retryFailed){
        List<StudioProject> selected=new ArrayList<>();for(StudioProject p:StudioProject.list(root,null)){
            if(p.state().equals("QUEUED")||p.state().equals("PAUSED")||p.state().equals("BASE_READY")
                    ||StudioProject.interruptedState(p.state())||(retryFailed&&(p.state().equals("FAILED")||p.state().equals("EXPORT_REVIEW"))))selected.add(p);
        }return selected;
    }
    static void run(List<StudioProject> selected,Processor processor,AudioAnalyzer.Progress progress)throws Exception{
        int index=0;List<String> failures=new ArrayList<>();
        for(StudioProject entry:selected){
            StudioAudio.cancel();StudioProject p=StudioProject.load(entry.dir);int song=++index;
            try{processor.process(p,(s,n)->progress.update("Song "+song+"/"+selected.size()+": "+s,n));}
            catch(Exception e){
                if(Thread.currentThread().isInterrupted()){p.state("PAUSED","Interrupted; verified base and earlier outputs retained");throw e;}
                p.state("FAILED",e.getClass().getSimpleName()+": "+e.getMessage());failures.add(p.name()+": "+e.getMessage());
            }
        }
        if(!failures.isEmpty())throw new IOException(failures.size()+" song(s) need review. Completed songs are saved; use Retry failed.");
    }
    static void process(StudioProject p,AudioAnalyzer.Progress progress)throws Exception{
        if(p.data.optBoolean("imported_master_trial_b",false)){
            ImportedMasterTrialB.renderStaged(p,progress);return;
        }
        File source=StudioImports.requireSource(p,progress);long need=source.length()*(p.validFile("base","base_hash")?8:24)+100*1024*1024L;
        if(p.dir.getUsableSpace()<need)throw new IOException("More free storage is needed for this song (about "+(need/1048576)+" MB)");
        StudioPipeline.master(p,progress);StudioAudio.cancel();StudioPipeline.finish(p,progress);
    }
}
