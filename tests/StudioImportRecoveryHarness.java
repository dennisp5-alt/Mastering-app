package com.dennis.aimasteringlab;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Production import, old-project recovery, and a real queue run under an Android-style path alias. */
public final class StudioImportRecoveryHarness {
    static final AudioAnalyzer.Progress Q=(s,p)->{};
    interface Attempt{void run()throws Exception;}
    static void require(boolean b,String why){if(!b)throw new AssertionError(why);}
    static void rejects(Attempt action,String message)throws Exception{
        try{action.run();throw new AssertionError("Expected rejection: "+message);}
        catch(IOException expected){require(expected.getMessage()!=null&&expected.getMessage().contains(message),"Wrong error: "+expected);}
    }
    public static void main(String[] args)throws Exception{
        File work=new File(args[0]);work.mkdirs();Path physical=work.toPath().resolve("data/data/app/files/studio-projects");Files.createDirectories(physical);
        Path user=work.toPath().resolve("data/user");Files.createDirectories(user);Files.createSymbolicLink(user.resolve("0"),Paths.get("../data"));
        File root=user.resolve("0/app/files/studio-projects").toFile();
        File input=StudioV013Harness.fixture(work,"import-fixture.wav",false);String expected=Hashing.sha256(input);
        StudioProject fresh=StudioProject.create(root,"New import");StudioImports.source(fresh,new FileInputStream(input),Q);
        require(fresh.data.getString("source").equals("source.wav"),"Fresh source path is not portable");
        fresh=StudioProject.load(new File(root,fresh.dir.getName()));
        require(fresh.state().equals("QUEUED")&&fresh.validFile("source","source_hash"),"New import failed after reload");
        require(expected.equals(fresh.data.getString("source_hash")),"Import bytes did not match selected source");
        StudioImports.requireSource(fresh,Q);
        System.out.println("NEW_ALIAS_IMPORT_PASS selected-stream/copy-checksum/analyze/commit/reload");

        List<StudioProject> recovered=new ArrayList<>();
        for(int i=0;i<4;i++){
            StudioProject p=StudioProject.create(root,"Failed v0.13 import "+i);StudioProject.copy(new FileInputStream(input),p.child("source.wav"));
            p.state("FAILED",i==0?"Invalid project file path":"IOException: Saved source is missing or changed; re-import the WAV");
            p=StudioProject.load(new File(root,p.dir.getName()));StudioImports.requireSource(p,Q);
            require(p.validFile("source","source_hash")&&p.data.getString("source_hash").equals(expected),"Legacy recovery changed or lost source");
            require(p.data.has("source_recovery_notice"),"First-checksum recovery was not disclosed");
            recovered.add(p);
        }
        StudioProject saved=recovered.get(0);saved.data.put("source","../../invalid-old-alias.wav");saved.save();
        saved=StudioProject.load(saved.dir);StudioImports.requireSource(saved,Q);
        require(saved.data.getString("source").equals("source.wav")&&saved.data.getString("source_hash").equals(expected),"Known-checksum path recovery failed");
        System.out.println("FOUR_LEGACY_IMPORTS_RECOVERED_PASS plus known-checksum-path-recovery");

        StudioProject changed=StudioProject.create(root,"Changed source");StudioImports.source(changed,new FileInputStream(input),Q);
        try(RandomAccessFile f=new RandomAccessFile(changed.file("source"),"rw")){f.seek(f.length()-8);int b=f.read();f.seek(f.length()-8);f.write(b^1);}
        rejects(()->StudioImports.requireSource(changed,Q),"checksum has changed");
        require(changed.data.getString("source_hash").equals(expected),"Changed file silently received a new checksum");
        StudioProject missing=StudioProject.create(root,"Missing source");missing.data.put("import_error","read failed during original import");missing.save();
        rejects(()->StudioImports.requireSource(missing,Q),"Original import error: read failed");
        StudioProject partial=StudioProject.create(root,"Partial copy");StudioProject.copy(new FileInputStream(input),partial.child("source.wav.importing"));
        rejects(()->StudioImports.requireSource(partial,Q),"no completed source WAV copy");
        StudioProject truncated=StudioProject.create(root,"Truncated old copy");StudioProject.copy(new FileInputStream(input),truncated.child("source.wav"));
        try(RandomAccessFile f=new RandomAccessFile(truncated.child("source.wav"),"rw")){f.setLength(f.length()-200);}
        try{StudioImports.requireSource(truncated,Q);throw new AssertionError("Truncated WAV recovered");}catch(IOException expectedError){}
        require(!truncated.data.has("source_hash"),"Truncated file received import identity");
        StudioProject rendered=StudioProject.create(root,"Missing identity after render");StudioProject.copy(new FileInputStream(input),rendered.child("source.wav"));rendered.data.put("master","old-master.wav");rendered.save();
        rejects(()->StudioImports.requireSource(rendered,Q),"no trusted import checksum");
        StudioProject interrupted=StudioProject.create(root,"Interrupted analysis");StudioImports.source(interrupted,new FileInputStream(input),Q);interrupted.data.put("source_verified",false);interrupted.state("PAUSED","analysis interrupted");
        StudioImports.requireSource(interrupted,Q);require(interrupted.data.optBoolean("source_verified",false),"Interrupted analysis not resumed");
        Path outside=work.toPath().resolve("outside");Files.createDirectories(outside);Files.createSymbolicLink(fresh.dir.toPath().resolve("escape"),outside.toAbsolutePath());
        StudioProject scope=fresh;rejects(()->scope.child("escape/source.wav"),"Invalid project file path");
        rejects(()->scope.file("bad",new File(work,"outside.wav")),"Invalid project file path");
        StudioProject cancelled=StudioProject.create(root,"Cancelled copy");Thread.currentThread().interrupt();
        try{StudioImports.source(cancelled,new FileInputStream(input),Q);throw new AssertionError("Cancelled import continued");}catch(IOException expectedError){}finally{Thread.interrupted();}
        require(!cancelled.child("source.wav").exists()&&!cancelled.child("source.wav.importing").exists(),"Cancelled import published partial WAV");
        System.out.println("IMPORT_GUARDS_PASS changed/missing/partial/truncated/untrusted-render/cancelled/escaped-source plus resumed-analysis");

        if(args.length>1){
            File real=new File(args[1]);StudioProject p=StudioProject.create(root,real.getName());StudioImports.source(p,new FileInputStream(real),Q);p=StudioProject.load(p.dir);StudioImports.requireSource(p,Q);
            require(p.data.getString("source_hash").equals(Hashing.sha256(real)),"Real song source changed");
            System.out.println("REAL_WAV_ALIAS_IMPORT_PASS "+p.data.getJSONObject("source_meters"));
            // Full mastering requires a song long enough for section mapping. The short
            // synthetic import fixtures above intentionally exercise only file handling.
            StudioProject legacy=StudioProject.create(root,"Recovered "+real.getName());
            StudioProject.copy(new FileInputStream(real),legacy.child("source.wav"));legacy.state("FAILED","Invalid project file path");
            final long[] last={0};AudioAnalyzer.Progress progress=(stage,percent)->{
                long now=System.currentTimeMillis();if(now-last[0]>12000){System.out.println("PROGRESS "+stage+" "+percent+"%");last[0]=now;}
            };
            try{StudioQueue.run(Collections.singletonList(legacy),StudioQueue::process,progress);}
            catch(Exception e){throw new IOException(StudioProject.load(legacy.dir).data.optString("message"),e);}
            StudioProject complete=StudioProject.load(legacy.dir);
            require(complete.state().equals("COMPLETE"),"Recovered queue job did not finish: "+complete.state());
            require(complete.validFile("source","source_hash")&&complete.validFile("master","master_hash")&&complete.validFile("package","package_hash"),"Recovered outputs failed checksums");
            require(complete.file("wav16").isFile()&&complete.file("mp3").isFile(),"Recovered queue omitted release formats");
            require(complete.data.getJSONObject("final_qc").has("source_recovery_notice"),"QC lost source recovery disclosure");
            require(complete.data.getString("source_hash").equals(Hashing.sha256(real)),"Recovered real song changed");
            String master=complete.data.getString("master_hash");StudioPipeline.master(complete,Q);
            require(complete.data.getString("master_hash").equals(master),"Base resume altered completed output");
            StudioProject.atomic(new File(work,"Recovered_RealSong_QC.json"),complete.data.getJSONObject("final_qc").toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            System.out.println("RECOVERED_QUEUE_COMPLETE_PASS base/master/WAV16/MP3/QC/ZIP/checksums/reload/resume "+complete.data.getJSONObject("master_meters"));
        }
        System.out.println("STUDIO_V0131_IMPORT_RECOVERY_PASS");
    }
}
