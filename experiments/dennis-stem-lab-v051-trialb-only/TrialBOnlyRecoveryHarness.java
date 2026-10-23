package com.dennis.aimasteringlab;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.json.JSONObject;

/** Exact v0.5.1 Trial B recovery: no Trial A, originals immutable, original song title retained. */
public final class TrialBOnlyRecoveryHarness {
    static void check(boolean condition,String message) {
        if(!condition)throw new AssertionError(message);
    }
    static StudioProject finished(File root,String name,boolean stems) throws Exception {
        StudioProject p=StudioProject.create(root,name);
        File source=p.child("source.wav"),base=p.child("base-master.wav"),master=p.child("final.wav");
        Files.write(source.toPath(),(name+":source").getBytes(StandardCharsets.UTF_8));
        Files.write(base.toPath(),(name+":base").getBytes(StandardCharsets.UTF_8));
        Files.write(master.toPath(),(name+":master").getBytes(StandardCharsets.UTF_8));
        p.file("source",source);p.file("base",base);p.file("master",master);
        p.data.put("source_hash",Hashing.sha256(source)).put("base_hash",Hashing.sha256(base))
          .put("master_hash",Hashing.sha256(master)).put("duration",180d)
          .put("source_meters",new JSONObject().put("lufs",-16))
          .put("master_meters",new JSONObject().put("lufs",-14.0).put("true_peak",-1.1)
             .put("lra",5.5).put("crest",13.5).put("correlation",.8));
        if(stems)p.data.put("stem_authority","STEMS_ONLY");
        p.state("COMPLETE","Verified");
        return p;
    }
    public static void main(String[] args)throws Exception {
        File dir=Files.createTempDirectory("recovered-stem-b-v051").toFile();
        StudioProject wav=finished(dir,"Boots On Vinyl",false);
        StudioProject stems=finished(dir,"Forever Girl",true);
        String aHash=wav.data.getString("master_hash"),bHash=stems.data.getString("master_hash");
        List<String> ids=Arrays.asList(wav.dir.getName(),stems.dir.getName());
        int created=StudioBulkExperiment.create(dir,ids,(stage,pct)->{});
        check(created==2,"Only one Trial B per original");
        int count=0;
        for(StudioProject p:StudioProject.list(dir,null)){
            if(!p.data.has("trial_variant"))continue;
            ++count;
            check("LOW_MID_CONTROL".equals(p.data.getString("trial_variant")),
                "Only the original v0.5.1 Trial B allowed");
            check(!p.name().contains("Trial A"),"No Trial A project");
            check(p.data.optBoolean("trial_base_reused",false),"Verified base reused");
            check(p.data.optString("trial_original_title","").equals(
                   p.name().startsWith("Boots")?"Boots On Vinyl":"Forever Girl"),
                   "Original song title retained");
            check("QUEUED".equals(p.state()),"Trial must be queued for original delivery pipeline");
            check(p.file("master")==null,"Never claim Trial B finished before render");
            check(p.validFile("source","source_hash") && p.validFile("base","base_hash"),
                "Trial source and base copies verified");
            List<StudioRecipe.Edit> edits=p.recipe().edits;
            check(edits.size()==2,"Exactly two original Trial B edits");
            StudioRecipe.Edit lo=edits.get(edits.size()-2),hi=edits.get(edits.size()-1);
            check("DYNAMIC_EQ".equals(lo.kind)&&lo.hz==260&&lo.q==.95&&lo.db==-.55
                    &&lo.threshold==-24,"Exact 260 Hz original Trial B recipe");
            check("DYNAMIC_EQ".equals(hi.kind)&&hi.hz==3500&&hi.q==1.2&&hi.db==-.45
                    &&hi.threshold==-30,"Exact 3500 Hz original Trial B recipe");
        }
        check(count==2,"Exactly two Trial B projects");
        check(aHash.equals(Hashing.sha256(wav.file("master"))),"Original Boots master unchanged");
        check(bHash.equals(Hashing.sha256(stems.file("master"))),"Original Forever master unchanged");
        File csv=new File(dir.getParentFile(),"trial-b-qc-"+System.nanoTime()+".csv");
        StudioBulkExperiment.report(dir,csv,(stage,pct)->{});
        String body=Files.readString(csv.toPath());
        check(body.contains("LOW_MID_CONTROL"),"Trial B in QC report");
        check(!body.contains("PRESENCE_CONTROL"),"No Trial A entries in QC report");
        System.out.println("PASS v0.5.1 TRIAL_B_ONLY: exact parameters, independent verified base and source, names, no Trial A");
    }
}
