package com.dennis.aimasteringlab;

import java.io.*;
import java.security.*;
import java.util.Locale;
import org.json.*;

/** Import ownership and recovery, shared by the Android picker and host regressions. */
final class StudioImports {
    static String copyVerified(InputStream input,File target)throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        StudioProject.copy(new DigestInputStream(input,digest),target);
        StringBuilder hex=new StringBuilder(64);
        for(byte b:digest.digest())hex.append(String.format(Locale.US,"%02x",b&255));
        String expected=hex.toString();
        if(!expected.equals(Hashing.sha256(target)))throw new IOException("Copied WAV does not match the selected file; import again");
        return expected;
    }

    static void source(StudioProject p,InputStream input,AudioAnalyzer.Progress progress)throws Exception {
        File f=p.child("source.wav");String hash=copyVerified(input,f);
        // Commit the copy's identity before analysis, so interruption cannot lose it.
        p.file("source",f);p.data.put("source_hash",hash).put("source_bytes",f.length())
                .put("source_import_version","0.14.5").put("source_verified",false);
        p.state("IMPORTING","Copied WAV saved; checking audio");
        AnalysisResult measured=StudioAudio.measure(f,progress);StudioAudio.valid(measured,measured.wav.frames);
        if(!hash.equals(measured.sourceSha256))throw new IOException("Saved WAV changed while importing; import again");
        commit(p,f,measured,"Imported source verified and ready");
    }

    static File requireSource(StudioProject p,AudioAnalyzer.Progress progress)throws Exception {
        String expected=p.data.optString("source_hash","");
        JSONObject meters=p.data.optJSONObject("source_meters");
        String metered=meters==null?"":meters.optString("sha256","");
        if(!expected.isEmpty()&&!metered.isEmpty()&&!expected.equals(metered))
            throw new IOException("Saved source checksum records disagree; re-import the original WAV");
        if(expected.isEmpty())expected=metered;
        if(!expected.isEmpty()&&!expected.matches("[0-9a-f]{64}"))
            throw new IOException("Saved source checksum is invalid; re-import the original WAV");
        File f=null;boolean recoveredPath=false;
        try{f=p.file("source");}catch(IOException invalidPath){recoveredPath=true;}
        if(f==null||!f.isFile()){
            File retained=p.child("source.wav");
            if(retained.isFile()){f=retained;recoveredPath=true;}
            else throw new IOException("The project has no completed source WAV copy; re-import the WAV"+originalError(p));
        }
        boolean firstChecksum=expected.isEmpty();
        if(firstChecksum&&!unfinishedLegacyImport(p))
            throw new IOException("The saved WAV has no trusted import checksum; re-import the original WAV"+originalError(p));
        progress.update("Verifying retained source WAV",0);
        String actual=Hashing.sha256(f);
        if(!firstChecksum&&!actual.equals(expected))
            throw new IOException("The saved WAV's checksum has changed; re-import the original WAV. Existing masters and edits are retained.");
        if(firstChecksum||recoveredPath||!p.data.optBoolean("source_verified",false)){
            AnalysisResult measured=StudioAudio.measure(f,progress);StudioAudio.valid(measured,measured.wav.frames);
            if(!actual.equals(measured.sourceSha256))throw new IOException("Saved WAV changed during verification; re-import the original WAV");
            if(firstChecksum)p.data.put("source_recovery_notice",
                    "Recovered a completed v0.13.0 import. The earlier import did not save a checksum; the retained WAV was re-read and its first checksum recorded. Review the source before release.");
            else if(recoveredPath&&!p.data.has("source_recovery_notice"))p.data.put("source_recovery_notice","Recovered the saved source path; WAV matches its previously saved import checksum.");
            commit(p,f,measured,firstChecksum||recoveredPath?"Retained import recovered and verified; ready to master":"Imported source verified and ready");
        }
        return f;
    }

    private static void commit(StudioProject p,File f,AnalysisResult a,String message)throws Exception {
        JSONObject meters=StudioAudio.meters(a);
        p.file("source",f);p.data.put("source_hash",a.sourceSha256).put("source_bytes",f.length())
                .put("source_meters",meters).put("duration",a.wav.durationSeconds).put("source_verified",true);
        p.state("QUEUED",message);
    }

    private static boolean unfinishedLegacyImport(StudioProject p){
        // The v0.13.0 alias bug happened before ANY source identity was committed.
        // Never invent a checksum for an existing render or a newer interrupted copy.
        if(p.data.has("source_import_version")||p.data.has("source")||p.data.has("source_meters")
                ||p.data.has("source_hash")||p.data.has("base")||p.data.has("base_hash")
                ||p.data.has("master")||p.data.has("master_hash")||p.data.has("final_qc"))return false;
        File[] children=p.dir.listFiles();if(children==null)return false;
        for(File f:children)if(f.getName().startsWith("base-")||f.getName().startsWith("render-")
                ||f.getName().equals("Base_Master_QC.json"))return false;
        return p.state().equals("FAILED")||p.state().equals("PAUSED")||p.state().equals("IMPORTING")||p.state().equals("QUEUED");
    }

    private static String originalError(StudioProject p){
        String reason=p.data.optString("import_error","");
        return reason.isEmpty()?"":". Original import error: "+reason;
    }
}
