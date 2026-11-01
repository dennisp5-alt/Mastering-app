package com.dennis.aimasteringlab;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

final class StudioAlbum {
    static JSONObject compare(List<StudioProject> projects)throws Exception{
        ArrayList<StudioProject> ready=new ArrayList<>();for(StudioProject p:projects)if(p.data.optJSONObject("master_meters")!=null&&p.data.optBoolean("include_album",true))ready.add(p);
        if(ready.isEmpty())throw new IOException("No completed songs are selected for the album");
        double[] levels=new double[ready.size()];double[][] tones=new double[7][ready.size()];
        for(int i=0;i<ready.size();i++){
            JSONObject meter=ready.get(i).data.getJSONObject("master_meters");levels[i]=meter.getDouble("lufs");
            JSONArray bands=ready.get(i).data.getJSONObject("final_profile").getJSONArray("tone");
            for(int b=0;b<7;b++)tones[b][i]=((Number)bands.get(b)).doubleValue();
        }
        double median=median(levels);double[] toneMedian=new double[7];for(int b=0;b<7;b++)toneMedian[b]=median(tones[b]);
        JSONArray songs=new JSONArray();
        for(int i=0;i<ready.size();i++){
            double max=0;for(int b=0;b<7;b++)max=Math.max(max,Math.abs(tones[b][i]-toneMedian[b]));
            songs.put(new JSONObject().put("project_id",ready.get(i).dir.getName()).put("title",ready.get(i).recipe().title)
                    .put("lufs",levels[i]).put("difference_from_album_median_lu",levels[i]-median)
                    .put("maximum_broad_band_difference_db",max).put("review",Math.abs(levels[i]-median)>1||max>3));
        }
        return new JSONObject().put("songs",songs).put("median_lufs",median)
                .put("meaning","Whole-song level and broad stereo tone comparisons are review cues; arrangements and intended contrasts can justify differences.");
    }
    static File export(List<StudioProject> projects,File folder,String title,double gap,AudioAnalyzer.Progress progress)throws Exception{
        StudioRecipe.range(gap,0,10,"Album spacing");if(!folder.mkdirs()&&!folder.isDirectory())throw new IOException("Could not create album folder");
        ArrayList<StudioProject> ready=new ArrayList<>();long frames=0,gapFrames=Math.round(gap*48000);
        for(StudioProject p:projects)if(p.data.has("master_meters")&&p.data.optBoolean("include_album",true)){
            if(!p.validFile("master","master_hash"))throw new IOException("Album master is missing or changed: "+p.name());
            if(p.data.optInt("revision",0)!=p.data.optInt("rendered_revision",-1))throw new IOException("Render the latest saved edits before album export: "+p.name());
            ready.add(p);frames+=p.data.getJSONObject("master_meters").getLong("frames");
        }
        if(ready.isEmpty())throw new IOException("Select at least one completed song");frames+=(ready.size()-1)*gapFrames;
        if(frames*6>0xffffffffL-36)throw new IOException("Album exceeds standard WAV size; export individual releases instead");
        String safe=ReleaseFormats.camelBase(title);File output=new File(folder,safe+"_Sequence_24bit-48kHz.wav");
        JSONArray sequence=new JSONArray();long at=0;
        try(FileOutputStream file=new FileOutputStream(output);OutputStream out=new BufferedOutputStream(file,262144)){
            MasterRenderer.writeHeader(out,48000,2,frames*6,24);
            byte[] zeros=new byte[48000];float[][] b=new float[2][4096];
            for(int index=0;index<ready.size();index++){
                StudioProject p=ready.get(index);File source=p.file("master");
                sequence.put(new JSONObject().put("track",index+1).put("title",p.recipe().title).put("start_seconds",at/48000.0).put("source_sha256",p.data.getString("master_hash")));
                try(WavFile.Reader r=WavFile.open(new FileInputStream(source))){int n;while((n=r.readFrames(b,4096))>0){
                    for(int i=0;i<n;i++)for(int c=0;c<2;c++){int q=Math.round(b[c][i]*8388608f);out.write(q&255);out.write((q>>>8)&255);out.write((q>>>16)&255);}at+=n;StudioAudio.cancel();
                }}
                if(index<ready.size()-1){long bytes=gapFrames*6;while(bytes>0){int n=(int)Math.min(bytes,zeros.length);out.write(zeros,0,n);bytes-=n;}at+=gapFrames;}
                progress.update("Assembling album sequence",(index+1)*100/ready.size());
            }out.flush();file.getFD().sync();
        }
        AnalysisResult verified=StudioAudio.measure(output,progress);StudioAudio.valid(verified,frames);
        if(verified.truePeakEstimateDbtp> -1)throw new IOException("Album boundary peak needs a fade; edit the relevant song and re-export");
        JSONObject report=compare(ready).put("sequence",sequence).put("spacing_seconds",gap).put("sequence_meters",StudioAudio.meters(verified))
                .put("audio_policy","Song PCM retained exactly; only requested silence inserted between songs. This is a continuous listening sequence, not a DDP image.");
        File manifest=new File(folder,safe+"_Album_QC.json");StudioProject.atomic(manifest,report.toString(2).getBytes(StandardCharsets.UTF_8));
        File zip=new File(folder,safe+"_Album.zip");StudioPipeline.packageFiles(zip,Arrays.asList(output,manifest),progress);return zip;
    }
    private static double median(double[] values){double[] a=values.clone();Arrays.sort(a);return a[a.length/2];}
}
