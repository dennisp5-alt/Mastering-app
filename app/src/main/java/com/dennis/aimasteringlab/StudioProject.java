package com.dennis.aimasteringlab;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.json.*;

/** Disk-owned project state; completed audio is immutable and commits use atomic rename. */
final class StudioProject {
    final File dir;
    JSONObject data;
    private StudioProject(File dir,JSONObject data)throws IOException{this.dir=dir.getCanonicalFile();this.data=data;}
    static StudioProject create(File root,String title)throws Exception{
        File dir=new File(root,UUID.randomUUID().toString()).getCanonicalFile();if(!dir.mkdirs())throw new IOException("Could not create saved project");
        StudioRecipe r=new StudioRecipe();r.title=title.replaceFirst("(?i)\\.wav$","");
        JSONObject j=new JSONObject().put("schema",1).put("id",dir.getName()).put("name",r.title)
                .put("created",System.currentTimeMillis()).put("state","IMPORTING").put("revision",0).put("order",System.currentTimeMillis())
                .put("recipe",r.json()).put("references",new JSONArray()).put("history",new JSONArray());
        StudioProject p=new StudioProject(dir,j);p.save();return p;
    }
    static StudioProject load(File dir)throws Exception{
        Exception first;
        try{return parse(dir,new File(dir,"project.json"));}catch(Exception e){first=e;}
        try{
            StudioProject p=parse(dir,new File(dir,"project.json.bak"));
            p.data.put("state","PAUSED").put("message","Recovered the previous saved project state; review before resuming");
            atomic(new File(dir,"project.json"),p.data.toString(2).getBytes(StandardCharsets.UTF_8));return p;
        }catch(Exception e){throw new IOException("Project state could not be read: "+dir.getName(),first);}
    }
    private static StudioProject parse(File dir,File path)throws Exception{
        if(path.length()>4*1024*1024)throw new IOException("Project state is too large");
        JSONObject j=new JSONObject(new String(Files.readAllBytes(path.toPath()),StandardCharsets.UTF_8));
        if(j.optInt("schema",0)!=1||!dir.getName().equals(j.optString("id","")))throw new IOException("Project identity/schema mismatch");
        return new StudioProject(dir,j);
    }
    synchronized void save()throws Exception{
        data.put("updated",System.currentTimeMillis());
        File target=new File(dir,"project.json");
        if(target.isFile())atomic(new File(dir,"project.json.bak"),Files.readAllBytes(target.toPath()));
        atomic(target,data.toString(2).getBytes(StandardCharsets.UTF_8));
    }
    synchronized void state(String state,String message)throws Exception{data.put("state",state).put("message",message);save();}
    String state(){return data.optString("state","PAUSED");}
    String name(){return data.optString("name","Song");}
    StudioRecipe recipe(){return StudioRecipe.read(data.optJSONObject("recipe"));}
    void recipe(StudioRecipe r)throws Exception{
        JSONArray history=data.optJSONArray("history");if(history==null)history=new JSONArray();
        if(data.has("recipe"))history.put(new JSONObject(data.getJSONObject("recipe").toString()));
        if(history.length()>20){JSONArray n=new JSONArray();for(int i=history.length()-20;i<history.length();i++)n.put(history.get(i));history=n;}
        data.put("history",history).put("recipe",r.json()).put("name",r.title).put("revision",data.optInt("revision",0)+1);save();
    }
    boolean undo()throws Exception{
        JSONArray h=data.optJSONArray("history");if(h==null||h.length()==0)return false;
        data.put("recipe",h.getJSONObject(h.length()-1));JSONArray n=new JSONArray();for(int i=0;i<h.length()-1;i++)n.put(h.get(i));
        data.put("history",n).put("name",recipe().title).put("revision",data.optInt("revision",0)+1);save();return true;
    }
    File file(String key)throws IOException{
        String relative=data.optString(key,"");if(relative.isEmpty())return null;return child(relative);
    }
    File child(String path)throws IOException{
        File f=new File(dir,path).getCanonicalFile();String root=dir.getCanonicalPath()+File.separator;
        if(!f.getPath().startsWith(root))throw new IOException("Invalid project file path");return f;
    }
    void file(String key,File file)throws Exception{
        // Android can expose the same directory through /data/user/0 and /data/data.
        // Both sides must use the same physical root before computing a relative path.
        File canonical=file.getCanonicalFile();
        String relative=dir.getCanonicalFile().toPath().relativize(canonical.toPath()).toString();child(relative);data.put(key,relative);
    }
    boolean validFile(String key,String hashKey)throws IOException{
        File f=file(key);return f!=null&&f.isFile()&&!data.optString(hashKey,"").isEmpty()&&Hashing.sha256(f).equals(data.optString(hashKey,""));
    }
    static List<StudioProject> list(File root,List<String> errors){
        ArrayList<StudioProject> out=new ArrayList<>();File[] dirs=root.listFiles();if(dirs!=null)for(File d:dirs)if(d.isDirectory())try{out.add(load(d));}catch(Exception e){if(errors!=null)errors.add(e.getMessage());}
        out.sort(Comparator.comparingLong(p->p.data.optLong("order",0)));return out;
    }
    static boolean interruptedState(String s){return s.equals("MASTERING")||s.equals("RENDERING")||s.equals("IMPORTING");}
    static void atomic(File output,byte[] bytes)throws IOException{
        File parent=output.getParentFile();if(parent!=null&&!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Could not create project folder");
        File stage=new File(output.getPath()+".tmp");
        try(FileOutputStream f=new FileOutputStream(stage)){f.write(bytes);f.getFD().sync();}
        try{Files.move(stage.toPath(),output.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        catch(AtomicMoveNotSupportedException e){Files.move(stage.toPath(),output.toPath(),StandardCopyOption.REPLACE_EXISTING);}
    }
    static void copy(InputStream input,File target)throws IOException{
        File stage=new File(target.getPath()+".importing");
        try(InputStream in=input;FileOutputStream out=new FileOutputStream(stage)){
            byte[] b=new byte[262144];int n;while((n=in.read(b))!=-1){if(n>0)out.write(b,0,n);StudioAudio.cancel();}out.getFD().sync();
        }catch(IOException e){stage.delete();throw e;}
        Files.move(stage.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);
    }
}
