package com.dennis.aimasteringlab;
import java.io.*;
import java.util.*;
import org.json.*;

public final class V0141RepairHarness {
  static final class Progress implements AudioAnalyzer.Progress {
    int last=-1; int updates=0; String lastStage="";
    public void update(String stage,int percent){
      if(percent<last) throw new AssertionError("progress regressed "+last+" -> "+percent+" at "+stage+" after "+lastStage);
      if(percent<0||percent>100) throw new AssertionError("progress out of range: "+percent);
      last=percent; lastStage=stage; updates++;
    }
    void reset(){last=-1;updates=0;lastStage="";}
  }
  static void req(boolean x,String m){if(!x)throw new AssertionError(m);}
  static File fixture(File work)throws Exception{
    File f=new File(work,"raw.wav"); long frames=48000L*24L;
    try(PcmExportWriter w=new PcmExportWriter(f,48000,32,frames)){
      for(long i=0;i<frames;i++){
        double t=i/48000.0;
        double env=Math.min(1.0,Math.min(t/0.2,(24.0-t)/0.25)); if(env<0)env=0;
        double verse=(t<8||t>=16)?1.0:1.32;
        double bass=.10*Math.sin(2*Math.PI*82*t)+.035*Math.sin(2*Math.PI*164*t);
        double body=.075*Math.sin(2*Math.PI*420*t)+.055*Math.sin(2*Math.PI*1250*t);
        double air=.020*Math.sin(2*Math.PI*6100*t);
        double m=env*verse*(bass+body+air);
        double s=env*.018*Math.sin(2*Math.PI*730*t);
        w.frame(m+s,m-s);
      }
    }
    return f;
  }
  public static void main(String[] args)throws Exception{
    File work=new File(args[0]); work.mkdirs(); File projects=new File(work,"projects");projects.mkdirs();
    File raw=fixture(work); StudioProject p=StudioProject.create(projects,"v0141 fixture");
    Progress progress=new Progress();
    StudioImports.source(p,new FileInputStream(raw),progress);
    StudioRecipe recipe=p.recipe();recipe.lowEnd=false;recipe.boundaryRepair=false;recipe.title="v0141 fixture";p.recipe(recipe);
    progress.reset();
    StudioPipeline.master(p,progress);
    req(p.state().equals("BASE_READY"),"master did not reach BASE_READY: "+p.state());
    req(progress.last==100,"master progress did not finish at 100: "+progress.last);
    req(p.validFile("base","base_hash"),"base master missing/hash invalid");
    JSONObject baseQc=new JSONObject(new String(java.nio.file.Files.readAllBytes(p.file("base_report").toPath()),java.nio.charset.StandardCharsets.UTF_8));
    JSONObject releaseQc=baseQc.getJSONObject("release_qc");
    req("NOT_RUN_STUDIO_PCM_ONLY".equals(releaseQc.getString("translation_verdict")),"Studio base QC unexpectedly ran translation path");
    req(releaseQc.getJSONArray("codec_attempts").length()==0,"Studio base QC unexpectedly ran codec attempts");
    progress.reset();
    StudioPipeline.finish(p,progress);
    p=StudioProject.load(p.dir);
    req(p.state().equals("COMPLETE"),"finish not COMPLETE: "+p.state()+" / "+p.data.optString("message"));
    req(progress.last==100,"finish progress did not finish at 100: "+progress.last);
    req(p.file("master")!=null&&p.file("master").isFile(),"24/48 master missing");
    req(p.file("wav16")!=null&&p.file("wav16").isFile(),"16/44.1 WAV missing");
    req(p.file("mp3")==null,"MP3 reference should not exist in Studio v0.14.1");
    req(p.file("package")==null,"release ZIP reference should not exist in Studio v0.14.1");
    AnalysisResult m=StudioAudio.measure(p.file("master"),(s,n)->{}), w=StudioAudio.measure(p.file("wav16"),(s,n)->{});
    req(m.wav.sampleRate==48000&&m.wav.bitsPerSample==24&&m.wav.channels==2,"24/48 format wrong");
    req(w.wav.sampleRate==44100&&w.wav.bitsPerSample==16&&w.wav.channels==2,"16/44.1 format wrong");
    JSONObject finalQc=p.data.getJSONObject("final_qc");
    req(!finalQc.getJSONObject("release_formats").has("mp3"),"Final QC still exposes MP3");
    req("NOT_RUN_WAV_ONLY".equals(finalQc.getJSONObject("radio_preparation").getString("status")),"Radio simulation was not marked skipped");

    // Re-import the finished, already-compliant master and ensure the same bounded
    // progress/QC architecture completes without re-entering the codec path.
    StudioProject second=StudioProject.create(projects,"already mastered fixture");
    Progress secondImport=new Progress();StudioImports.source(second,new FileInputStream(p.file("master")),secondImport);
    StudioRecipe secondRecipe=second.recipe();secondRecipe.lowEnd=false;secondRecipe.boundaryRepair=false;second.recipe(secondRecipe);
    Progress secondProgress=new Progress();StudioPipeline.master(second,secondProgress);
    req(second.state().equals("BASE_READY"),"already-mastered input did not reach BASE_READY");
    req(secondProgress.last==100,"already-mastered progress did not finish at 100");
    JSONObject secondQc=new JSONObject(new String(java.nio.file.Files.readAllBytes(second.file("base_report").toPath()),java.nio.charset.StandardCharsets.UTF_8));
    req(secondQc.getJSONObject("release_qc").getJSONArray("codec_attempts").length()==0,"already-mastered Studio path unexpectedly ran codec attempts");
    req("NOT_RUN_STUDIO_PCM_ONLY".equals(secondQc.getJSONObject("release_qc").getString("translation_verdict")),"already-mastered Studio path unexpectedly ran translation QC");
    System.out.println("V0141_REPAIR_HARNESS_PASS rawWinner="+p.data.optString("base_winner")+" remasterWinner="+second.data.optString("base_winner")+" master="+m.integratedLufs+" LUFS wav16="+w.integratedLufs+" LUFS");
  }
}
