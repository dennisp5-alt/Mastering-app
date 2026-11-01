package com.dennis.aimasteringlab;

import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.json.*;

/** Bulk export/removal ONLY for UUID-named local project directories. */
final class StudioSelectedProjects {
    private StudioSelectedProjects() {}

    static StudioProject loadSelected(File projects,String id) throws Exception {
        if(id==null||!id.matches("[a-fA-F0-9-]{36}"))throw new IOException("Invalid project selection");
        File root=projects.getCanonicalFile();
        File dir=new File(root,id).getCanonicalFile();
        if(!root.equals(dir.getParentFile())||!dir.isDirectory())
            throw new IOException("Project no longer exists: "+id);
        return StudioProject.load(dir);
    }

    static void exportMasters(File projects,List<String> ids,File output) throws Exception {
        if(ids.isEmpty())throw new IOException("No projects selected");
        LinkedHashSet<String> unique=new LinkedHashSet<>(ids);
        List<StudioProject> ready=new ArrayList<>();
        for(String id:unique){
            StudioProject p=loadSelected(projects,id);
            if(!p.validFile("master","master_hash"))
                throw new IOException("Master isn't complete or verified: "+p.name()+". Export complete projects only.");
            ready.add(p);
        }
        File stage=new File(output.getPath()+".tmp");
        Set<String> filenames=new HashSet<>();
        try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(stage)))){
            zip.setLevel(1);
            byte[] buffer=new byte[262144];
            for(StudioProject p:ready){
                StudioAudio.cancel();
                String base=ImportedMasterTrialB.cleanTitle(p.data.optString("trial_original_title",p.name()));
                String suffix=p.data.optBoolean("imported_master_trial_b",false)?"_TrialB_ImportedMaster_24bit-48kHz.wav":
                    p.data.has("trial_original_title")?"_TrialB_24bit-48kHz.wav":"_Master_24bit-48kHz.wav";
                String name=base+suffix;
                int n=2;while(!filenames.add(name.toLowerCase(Locale.ROOT)))name=base+"_"+(n++)+suffix;
                zip.putNextEntry(new ZipEntry(name));
                try(InputStream in=new BufferedInputStream(new FileInputStream(p.file("master")))){
                    int size;while((size=in.read(buffer))!=-1){StudioAudio.cancel();zip.write(buffer,0,size);}
                }
                zip.closeEntry();
            }
        }catch(Exception e){stage.delete();throw e;}
        if(!stage.renameTo(output)){stage.delete();throw new IOException("Could not finalise selected masters ZIP");}
    }

    static void delete(File projects,List<String> ids) throws Exception {
        LinkedHashSet<String> unique=new LinkedHashSet<>(ids);
        // Validate every selection BEFORE deleting any project.
        ArrayList<StudioProject> validated=new ArrayList<>();
        for(String id:unique)validated.add(loadSelected(projects,id));
        for(StudioProject p:validated){
            StudioAudio.cancel();
            StudioPipeline.removeTree(p.dir);
            if(p.dir.exists())throw new IOException("Could not remove project: "+p.name()+". Other projects were left in place.");
        }
    }
}
