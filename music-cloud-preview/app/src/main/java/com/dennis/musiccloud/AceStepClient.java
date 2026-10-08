package com.dennis.musiccloud;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** ACE-Step official REST transport. Tokens are never persisted by this class. */
public final class AceStepClient {
    private final String origin,token;
    public AceStepClient(String endpoint,String apiToken) {
        String s=endpoint==null?"":endpoint.trim();
        if(!s.matches("https://[A-Za-z0-9._-]+(?::[0-9]{2,5})?/?"))
            throw new IllegalArgumentException("Enter a secure HTTPS host without a path, e.g. https://your-server.example");
        origin=s.endsWith("/")?s.substring(0,s.length()-1):s;
        token=apiToken==null?"":apiToken.trim();
    }
    public JSONObject health() throws Exception {return unwrap(request("GET","/health",null));}
    public JSONObject models() throws Exception {return unwrap(request("GET","/v1/models",null));}
    public String submit(String prompt,String lyrics,String model,int bpm,int duration) throws Exception {
        JSONObject body=makeRequest(prompt,lyrics,model,bpm,duration);
        JSONObject data=unwrap(request("POST","/release_task",body));
        String id=data.optString("task_id","");
        if(id.isEmpty())throw new IOException("API response has no task_id");
        return id;
    }
    public static JSONObject makeRequest(String prompt,String lyrics,String model,int bpm,int duration) throws JSONException {
        if(prompt==null||prompt.trim().isEmpty())throw new IllegalArgumentException("Enter a music production description");
        if(lyrics==null||lyrics.trim().isEmpty())throw new IllegalArgumentException("Enter original lyrics");
        if(bpm<30||bpm>300||duration<30||duration>600)throw new IllegalArgumentException("Invalid tempo or duration");
        JSONObject body=new JSONObject();
        body.put("prompt",prompt.trim());
        body.put("lyrics",lyrics.trim());
        body.put("model",model);
        body.put("bpm",bpm);
        body.put("audio_duration",duration);
        body.put("vocal_language","en");
        body.put("time_signature","4");
        body.put("thinking",true);
        body.put("audio_format","wav");
        body.put("batch_size",1);
        body.put("inference_steps",model.contains("turbo")?8:48);
        body.put("use_random_seed",true);
        return body;
    }
    public JSONObject poll(String taskId) throws Exception {
        if(taskId==null||!taskId.matches("[a-zA-Z0-9_-]{8,128}"))throw new IOException("Unsafe task ID");
        JSONObject query=new JSONObject().put("task_id_list",new JSONArray().put(taskId));
        JSONObject raw=request("POST","/query_result",query);
        JSONArray data=raw.optJSONArray("data");
        if(data==null||data.length()==0)throw new IOException("No task status in response");
        return data.getJSONObject(0);
    }
    public static String successFile(JSONObject status) throws Exception {
        if(status.optInt("status",-1)!=1)throw new IOException("Generation not completed successfully");
        Object raw=status.opt("result");
        JSONArray records=raw instanceof JSONArray?(JSONArray)raw:
            raw instanceof String?new JSONArray((String)raw):null;
        if(records==null||records.length()==0)throw new IOException("No generated file supplied by API");
        String file=records.getJSONObject(0).optString("file","");
        // Prevent untrusted server results redirecting the bearer key to another hostname.
        if(!file.startsWith("/v1/audio?path=")||file.contains("#")||file.contains("\r")||file.contains("\n"))
            throw new IOException("Unexpected download path from API");
        return file;
    }
    public void download(String relativePath,File target) throws Exception {
        if(!relativePath.startsWith("/v1/audio?path=")||relativePath.contains("#"))
            throw new IOException("Unsafe audio path");
        HttpURLConnection c=connection("GET",relativePath);
        try {
            if(c.getResponseCode()!=200)throw new IOException("Audio download failed: HTTP "+c.getResponseCode());
            int count=0;
            File tmp=new File(target.getAbsolutePath()+".part");
            try(InputStream in=new BufferedInputStream(c.getInputStream());OutputStream out=new BufferedOutputStream(new FileOutputStream(tmp))){
                byte[] buffer=new byte[65536];int n;
                while((n=in.read(buffer))!=-1) {
                    count+=n;
                    if(count>300*1024*1024)throw new IOException("Output exceeds 300 MB safety limit");
                    out.write(buffer,0,n);
                }
            }catch(Exception e){tmp.delete();throw e;}
            try(RandomAccessFile raf=new RandomAccessFile(tmp,"r")){
                if(raf.length()<44)throw new IOException("Downloaded WAV is incomplete");
                byte[] header=new byte[12];raf.readFully(header);
                if(header[0]!='R'||header[1]!='I'||header[2]!='F'||header[3]!='F'||
                   header[8]!='W'||header[9]!='A'||header[10]!='V'||header[11]!='E')
                    throw new IOException("Audio server did not return a standard RIFF WAV");
            }catch(Exception e){tmp.delete();throw e;}
            if(target.exists()&&!target.delete())throw new IOException("Cannot replace prior composition");
            if(!tmp.renameTo(target))throw new IOException("Cannot move downloaded WAV");
        }finally{c.disconnect();}
    }
    public static JSONObject unwrap(JSONObject wrapper)throws Exception {
        if(wrapper.optInt("code",200)!=200)
            throw new IOException(wrapper.optString("error","ACE-Step API error"));
        JSONObject data=wrapper.optJSONObject("data");
        if(data==null)throw new IOException("Expected JSON object from ACE-Step");
        return data;
    }
    private HttpURLConnection connection(String method,String path) throws Exception {
        if(!path.startsWith("/")||path.startsWith("//"))throw new IOException("Invalid path");
        URL url=new URL(origin+path);
        HttpURLConnection c=(HttpURLConnection)url.openConnection();
        c.setRequestMethod(method);c.setConnectTimeout(15000);c.setReadTimeout(60000);
        c.setInstanceFollowRedirects(false);
        if(!token.isEmpty())c.setRequestProperty("Authorization","Bearer "+token);
        c.setRequestProperty("Accept","application/json");
        return c;
    }
    private JSONObject request(String method,String path,JSONObject body) throws Exception {
        HttpURLConnection c=connection(method,path);
        try{
            if(body!=null){
                c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=utf-8");
                byte[] payload=body.toString().getBytes(StandardCharsets.UTF_8);
                try(OutputStream out=c.getOutputStream()){out.write(payload);}
            }
            int status=c.getResponseCode();
            InputStream stream=status>=400?c.getErrorStream():c.getInputStream();
            if(stream==null)throw new IOException("HTTP "+status+" (no server response)");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(InputStream in=stream){
                byte[] buf=new byte[8192];int size;
                while((size=in.read(buf))!=-1) {
                    if(bytes.size()+size>2*1024*1024)throw new IOException("Response exceeds 2 MB");
                    bytes.write(buf,0,size);
                }
            }
            String content=bytes.toString("UTF-8");
            if(status!=200)throw new IOException("HTTP "+status+": "+content.substring(0,Math.min(200,content.length())));
            return new JSONObject(content);
        }finally{c.disconnect();}
    }
}
