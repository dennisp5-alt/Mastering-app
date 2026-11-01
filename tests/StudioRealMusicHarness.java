package com.dennis.aimasteringlab;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

public final class StudioRealMusicHarness {
    static long last;
    static final AudioAnalyzer.Progress PROGRESS=(s,p)->{long now=System.currentTimeMillis();if(now-last>12000){System.out.println("PROGRESS "+s+" "+p+"%");last=now;}};
    public static void main(String[] args)throws Exception{
        File input=new File(args[0]),root=new File(args[1]);root.mkdirs();
        StudioProject p=StudioProject.create(root,input.getName());File copied=p.child("source.wav");StudioProject.copy(new FileInputStream(input),copied);
        AnalysisResult source=StudioAudio.measure(copied,PROGRESS);p.file("source",copied);p.data.put("source_hash",source.sourceSha256).put("duration",source.wav.durationSeconds).put("source_meters",StudioAudio.meters(source));p.state("QUEUED","ready");
        StudioPipeline.master(p,PROGRESS);String baseline=p.data.getString("base_hash");
        // Simulate process loss after the first durable stage, then reopen from disk.
        p=StudioProject.load(p.dir);StudioPipeline.master(p,PROGRESS);
        if(!p.data.getString("base_hash").equals(baseline))throw new AssertionError("Resume replaced valid base master");
        StudioPipeline.finish(p,PROGRESS);p=StudioProject.load(p.dir);
        if(!p.state().equals("COMPLETE")||!p.validFile("master","master_hash")||!p.validFile("source","source_hash"))throw new AssertionError("Real-song output/source verification failed");
        JSONObject result=p.data.getJSONObject("final_qc");result.put("project_directory",p.dir.getAbsolutePath());
        StudioProject.atomic(new File(root,"REAL_SONG_RESULT.json"),result.toString(2).getBytes(StandardCharsets.UTF_8));
        System.out.println("STUDIO_REAL_MUSIC_PASS "+result.getJSONObject("master")+" bass="+result.getJSONObject("bass_verification")+" fadeOut="+result.getDouble("fade_out_seconds"));
    }
}
