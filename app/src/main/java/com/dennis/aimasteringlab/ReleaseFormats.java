package com.dennis.aimasteringlab;
import java.io.*;
import java.util.Locale;
import org.json.*;

/** Three-format release derivatives. Every encoded file is decoded and metered;
 * format-specific headroom changes never alter the verified 24-bit master. */
final class ReleaseFormats {
    static final class Result {
        File wav16,mp3;AnalysisResult wav16Qc,mp3Qc;
        double wavGainDb,mp3GainDb;int mp3Passes;String error,firstAttemptError;int exportAttempts=1;boolean masterUnchanged,wavOnly;
        boolean complete(){return error==null&&wav16Qc!=null&&(wavOnly||mp3Qc!=null);}
        String summary(){
            if(!complete())return "Release formats incomplete: "+error;
            if(wavOnly)return String.format(Locale.US,"Two-WAV delivery verified: 24/48 master + 16/44.1 WAV; 16-bit %.3f LUFS / %.3f dBTP",wav16Qc.integratedLufs,wav16Qc.truePeakEstimateDbtp);
            return String.format(Locale.US,"Release formats verified: 24/48 WAV + 16/44.1 WAV + MP3 320; MP3 %.3f LUFS / %.3f dBTP%s",mp3Qc.integratedLufs,mp3Qc.truePeakEstimateDbtp,mp3GainDb<-.001?String.format(Locale.US," (%.2f dB codec headroom)",mp3GainDb):"");
        }
    }

    static Result createWavOnly(File master,AnalysisResult source,File work,String title,AudioAnalyzer.Progress progress,StudioRecipe metadata)throws IOException{
        Result r=new Result();r.wavOnly=true;String base=camelBase(title);
        r.wav16=new File(work,base+"_Master_16bit-44.1kHz.wav");File stage=null;
        long expected=Math.round(source.wav.frames*44100.0/source.wav.sampleRate);
        String originalHash=Hashing.sha256(master);double wavCeiling=metadata==null?-1.02:metadata.ceiling-.02;
        try{
            stage=WavRenderIntegrity.stagingFile(r.wav16,"export16");final File wavStage=stage;
            for(int pass=0;pass<3;pass++){
                render16(master,source,stage,r.wavGainDb,progress);
                r.wav16Qc=AudioAnalyzer.analyze(r.wav16.getName(),()->new FileInputStream(wavStage),progress);
                valid(r.wav16Qc,expected);
                if(r.wav16Qc.truePeakEstimateDbtp<=wavCeiling)break;
                r.wavGainDb-=r.wav16Qc.truePeakEstimateDbtp-wavCeiling+.03;
                if(pass==2)throw new IOException("16-bit peak verification did not converge");
            }
            if(Math.abs(r.wav16Qc.integratedLufs-(source.integratedLufs+r.wavGainDb))>.15)throw new IOException("Resampled WAV loudness changed unexpectedly");
            WavRenderIntegrity.commit(stage,r.wav16,expected*4,44100,2,16,false);stage=null;
            if(!originalHash.equals(Hashing.sha256(master)))throw new IOException("Master changed during derivative export");
            r.masterUnchanged=true;return r;
        }catch(IOException | RuntimeException failure){
            WavRenderIntegrity.discard(r.wav16);r.wav16Qc=null;
            if(Thread.currentThread().isInterrupted())throw new IOException("Release export cancelled",failure);
            r.masterUnchanged=originalHash.equals(Hashing.sha256(master));
            if(!r.masterUnchanged)throw new IOException("Master integrity changed during export",failure);
            r.error=failure.getClass().getSimpleName()+": "+failure.getMessage();return r;
        }finally{WavRenderIntegrity.discard(stage);}
    }

    static Result create(File master,AnalysisResult source,File work,String title,AudioAnalyzer.Progress progress)throws IOException{
        return create(master,source,work,title,progress,null);
    }
    static Result create(File master,AnalysisResult source,File work,String title,AudioAnalyzer.Progress progress,StudioRecipe metadata)throws IOException{
        Result first=createOnce(master,source,work,title,progress,metadata);
        if(first.complete()||!first.masterUnchanged)return first;
        if(Thread.currentThread().isInterrupted())throw new IOException("Release export cancelled");
        progress.update("Retrying incomplete release export from verified master",0);
        Result retry=createOnce(master,source,work,title,progress,metadata);
        retry.exportAttempts=2;retry.firstAttemptError=first.error;return retry;
    }
    private static Result createOnce(File master,AnalysisResult source,File work,String title,AudioAnalyzer.Progress progress,StudioRecipe metadata)throws IOException{
        Result r=new Result();String base=camelBase(title);
        r.wav16=new File(work,base+"_Master_16bit-44.1kHz.wav");r.mp3=new File(work,base+"_Master_320kbps.mp3");
        File stage=null,encoded=null;
        File decoded=new File(work,"mp3-verification-float.wav");
        long expected=Math.round(source.wav.frames*44100.0/source.wav.sampleRate);
        String originalHash=Hashing.sha256(master);
        double wavCeiling=metadata==null?-1.02:metadata.ceiling-.02;
        double mp3Ceiling=metadata==null?-1.05:metadata.ceiling-.05;
        try{
            if(Thread.currentThread().isInterrupted())throw new IOException("Export cancelled");
            stage=WavRenderIntegrity.stagingFile(r.wav16,"export16");
            encoded=WavRenderIntegrity.stagingFile(r.mp3,"mp3");
            final File wavStage=stage;
            for(int pass=0;pass<3;pass++){
                render16(master,source,stage,r.wavGainDb,progress);
                r.wav16Qc=AudioAnalyzer.analyze(r.wav16.getName(),()->new FileInputStream(wavStage),progress);
                valid(r.wav16Qc,expected);
                if(r.wav16Qc.truePeakEstimateDbtp<=wavCeiling)break;
                r.wavGainDb-=r.wav16Qc.truePeakEstimateDbtp-wavCeiling+.03;
                if(pass==2)throw new IOException("16-bit peak verification did not converge");
            }
            if(Math.abs(r.wav16Qc.integratedLufs-(source.integratedLufs+r.wavGainDb))>.15)throw new IOException("Resampled WAV loudness changed unexpectedly");
            WavRenderIntegrity.commit(stage,r.wav16,expected*4,44100,2,16,false);
            for(int pass=0;pass<4;pass++){
                r.mp3Passes=pass+1;
                OfflineMp3.Encoded encodedInfo=OfflineMp3.encode(r.wav16,encoded,title,r.mp3GainDb,progress,metadata);
                OfflineMp3.decode(encoded,encodedInfo,decoded,progress);
                r.mp3Qc=AudioAnalyzer.analyze(r.mp3.getName(),()->new FileInputStream(decoded),progress);
                valid(r.mp3Qc,expected);
                if(Math.abs(r.mp3Qc.integratedLufs-(r.wav16Qc.integratedLufs+r.mp3GainDb))>.25)throw new IOException("Decoded MP3 loudness changed unexpectedly");
                if(r.mp3Qc.truePeakEstimateDbtp<=mp3Ceiling)break;
                r.mp3GainDb-=r.mp3Qc.truePeakEstimateDbtp-mp3Ceiling+.10;
                if(pass==3||r.mp3GainDb<-3)throw new IOException("MP3 peak safety did not converge within bounded flat attenuation");
            }
            if(r.mp3.exists()&&!r.mp3.delete())throw new IOException("Could not replace MP3 export");
            if(!encoded.renameTo(r.mp3))throw new IOException("Could not publish verified MP3");
            OfflineMp3.verifyFrames(r.mp3);
            if(!originalHash.equals(Hashing.sha256(master)))throw new IOException("Master changed during derivative export");
            r.masterUnchanged=true;return r;
        }catch(IOException | RuntimeException failure){
            WavRenderIntegrity.discard(r.wav16);WavRenderIntegrity.discard(r.mp3);r.wav16Qc=null;r.mp3Qc=null;
            if(Thread.currentThread().isInterrupted())throw new IOException("Release export cancelled",failure);
            r.masterUnchanged=originalHash.equals(Hashing.sha256(master));
            if(!r.masterUnchanged)throw new IOException("Master integrity changed during export",failure);
            r.error=failure.getClass().getSimpleName()+": "+failure.getMessage();return r;
        }finally{WavRenderIntegrity.discard(stage);WavRenderIntegrity.discard(encoded);WavRenderIntegrity.discard(decoded);}
    }
    static void render16(File master,AnalysisResult source,File output,double gainDb,AudioAnalyzer.Progress progress)throws IOException{
        long expected=Math.round(source.wav.frames*44100.0/source.wav.sampleRate);double gain=Math.pow(10,gainDb/20);
        try(WavFile.Reader reader=WavFile.open(new FileInputStream(master));PcmExportWriter writer=new PcmExportWriter(output,44100,16,expected)){
            SincResampler resampler=new SincResampler(reader.info.sampleRate,44100,96,(l,r)->writer.frame(l*gain,r*gain));
            float[][] buffer=new float[2][4096];int n;long done=0;
            while((n=reader.readFrames(buffer,4096))>0){for(int i=0;i<n;i++)resampler.add(buffer[0][i],buffer[1][i]);done+=n;
                progress.update("Rendering 16-bit/44.1 kHz WAV",(int)(done*100/source.wav.frames));
                if(Thread.currentThread().isInterrupted())throw new IOException("Export cancelled");}
            resampler.finish();if(writer.clips!=0)throw new IOException("16-bit quantizer clipped");
        }
        WavRenderIntegrity.verify(output,expected*4,44100,2,16,false);
    }
    private static void valid(AnalysisResult a,long frames)throws IOException{
        if(a.wav.frames!=frames||a.wav.channels!=2||a.wav.sampleRate!=44100||a.nonFiniteSamples!=0||!Double.isFinite(a.integratedLufs)||!Double.isFinite(a.truePeakEstimateDbtp))throw new IOException("Derivative integrity failed");
    }
    static String camelBase(String input){
        String text=input==null?"Song":input.replaceFirst("(?i)\\.wav$","");
        String suffix="";java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?i)_v(\\d+)$").matcher(text);
        if(m.find()){suffix="_v"+m.group(1);text=text.substring(0,m.start());}
        StringBuilder b=new StringBuilder();for(String word:text.split("[^A-Za-z0-9]+"))if(!word.isEmpty())b.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        if(b.length()==0)b.append("Song");if(b.length()>70)b.setLength(70);return b.toString()+suffix;
    }
    static JSONObject json(Result r)throws JSONException{
        JSONObject j=new JSONObject();if(r==null)return j;
        j.put("status",r.complete()?"VERIFIED":"INCOMPLETE");j.put("master_unchanged_by_derivatives",r.masterUnchanged);
        j.put("resampling","193-tap phase-normalized Blackman sinc; exact rational timing; one final high-pass triangular dither");
        j.put("wav16",measurement(r.wav16,r.wav16Qc,r.wavGainDb));
        if(!r.wavOnly){j.put("mp3_encoder","offline Jump3r 1.0.5 / LAME 3.98.4, 320 kbps CBR joint stereo; gapless metadata");j.put("mp3",measurement(r.mp3,r.mp3Qc,r.mp3GainDb));j.put("mp3_attempts",r.mp3Passes);}
        j.put("export_attempts",r.exportAttempts);
        if(r.firstAttemptError!=null)j.put("first_attempt_error",r.firstAttemptError);
        if(r.error!=null)j.put("error",r.error);return j;
    }
    private static JSONObject measurement(File f,AnalysisResult a,double gain)throws JSONException{
        JSONObject j=new JSONObject();if(a==null)return j;j.put("file",f.getName());j.put("frames",a.wav.frames);j.put("sample_rate",a.wav.sampleRate);
        j.put("lufs_i",a.integratedLufs);j.put("true_peak_dbtp",a.truePeakEstimateDbtp);j.put("lra_lu",a.lraLu);j.put("additional_flat_gain_db",gain);
        return j;
    }
}
