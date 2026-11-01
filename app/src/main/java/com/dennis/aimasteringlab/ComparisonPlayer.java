package com.dennis.aimasteringlab;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import java.io.File;
import java.io.IOException;

/** One Android output queue for both versions; switching never seeks or restarts either file. */
final class ComparisonPlayer {
    interface Listener {
        void position(double seconds,boolean playing);
        void failure(String message);
    }
    private final File first,second;
    private final AnalysisResult firstAnalysis,secondAnalysis;
    private final Listener listener;
    private final Object lock=new Object();
    private final AudioManager manager;
    private final AudioFocusRequest focus;
    private volatile AudioTrack track;
    private volatile boolean closed,paused=true,alternative,mono,delta;
    private volatile long loopStart,loopEnd=-1;
    private volatile long seek=-1;
    private final Thread thread;

    ComparisonPlayer(Context context,File first,AnalysisResult firstAnalysis,
                     File second,AnalysisResult secondAnalysis,Listener listener){
        this.first=first;this.second=second;this.firstAnalysis=firstAnalysis;this.secondAnalysis=secondAnalysis;this.listener=listener;
        manager=(AudioManager)context.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes()).setOnAudioFocusChangeListener(change->{
                    if(change!=AudioManager.AUDIOFOCUS_GAIN)pause();
                }).build();
        thread=new Thread(this::run,"DAML-aligned-audio-comparison");thread.start();
    }
    void play(boolean useAlternative){
        if(closed)return;
        if(manager!=null&&manager.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){
            listener.failure("Audio focus is unavailable; try playback again after the other audio stops.");return;
        }
        alternative=useAlternative;paused=false;synchronized(lock){lock.notifyAll();}
    }
    void pause(){paused=true;AudioTrack t=track;if(t!=null)try{t.pause();}catch(IllegalStateException ignored){}
        synchronized(lock){lock.notifyAll();}}
    void mono(boolean enabled){mono=enabled;}
    void delta(boolean enabled){delta=enabled;}
    void loopSeconds(double start,double end,boolean enabled){
        loopStart=Math.max(0,Math.round(start*48000));loopEnd=enabled?Math.min(firstAnalysis.wav.frames,Math.round(end*48000)):-1;
        if(loopEnd<=loopStart+2400)loopEnd=-1;
        if(enabled&&loopEnd>0)seekSeconds(start);
    }
    void seekSeconds(double seconds){synchronized(lock){seek=Math.max(0,Math.min(firstAnalysis.wav.frames-1,Math.round(seconds*48000)));lock.notifyAll();}}
    void close(){closed=true;pause();thread.interrupt();AudioTrack t=track;if(t!=null)try{t.flush();}catch(IllegalStateException ignored){}
        if(manager!=null)manager.abandonAudioFocusRequest(focus);}
    private static AudioAttributes attributes(){return new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();}
    private void run(){
        try(ComparisonAudioStream stream=new ComparisonAudioStream(first,firstAnalysis,second,secondAnalysis)){
            int minimum=AudioTrack.getMinBufferSize(48000,AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_FLOAT);
            if(minimum<=0)throw new IOException("Android float-stereo playback is unavailable");
            AudioTrack output=new AudioTrack.Builder().setAudioAttributes(attributes())
                    .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(48000).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                    .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(Math.max(minimum,8192)).build();
            track=output;
            if(output.getState()!=AudioTrack.STATE_INITIALIZED)throw new IOException("Android could not initialize aligned playback");
            float[] samples=new float[2048];long offset=0,lastNotify=0;boolean atEnd=false;
            int available=0,written=0;
            while(!closed){
                long wanted;
                synchronized(lock){wanted=seek;seek=-1;}
                if(wanted>=0){output.pause();output.flush();stream.seek(wanted);offset=wanted;atEnd=false;available=written=0;}
                if(paused){
                    output.pause();listener.position((offset+Integer.toUnsignedLong(output.getPlaybackHeadPosition()))/48000.0,false);
                    synchronized(lock){if(!closed&&paused&&seek<0)lock.wait();}
                    continue;
                }
                if(atEnd){output.pause();output.flush();stream.seek(0);offset=0;atEnd=false;available=written=0;}
                stream.choose(alternative);stream.mono(mono);stream.delta(delta);
                stream.loopBoundaries(loopStart,loopEnd);
                output.play();
                long endFrame=loopEnd>0?loopEnd:stream.frames;
                int wantedFrames=(int)Math.max(0,Math.min(1024,endFrame-stream.position()));
                int frames=written>=available ? (wantedFrames==0?-1:stream.read(samples,wantedFrames)) : 0;
                if(frames<0){
                    while(!closed&&!paused&&seek<0&&offset+Integer.toUnsignedLong(output.getPlaybackHeadPosition())<endFrame)Thread.sleep(10);
                    if(seek>=0)continue;
                    if(!paused&&loopEnd>0){output.pause();output.flush();stream.seek(loopStart);offset=loopStart;available=written=0;continue;}
                    if(!paused){paused=true;atEnd=true;listener.position(stream.frames/48000.0,false);}
                    continue;
                }
                if(frames>0){available=frames*2;written=0;}
                int n=output.write(samples,written,available-written,AudioTrack.WRITE_NON_BLOCKING);
                if(n<0)throw new IOException("Android audio write failed ("+n+")");
                written+=n;
                if(n==0)synchronized(lock){if(!closed&&!paused&&seek<0)lock.wait(5);}
                long now=System.nanoTime();
                if(now-lastNotify>200000000L){listener.position(Math.min(stream.frames,offset+Integer.toUnsignedLong(output.getPlaybackHeadPosition()))/48000.0,!paused);lastNotify=now;}
            }
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
        catch(Exception error){if(!closed)listener.failure(error.getMessage()==null?error.getClass().getSimpleName():error.getMessage());}
        finally{AudioTrack t=track;track=null;if(t!=null){try{t.pause();t.flush();}catch(Exception ignored){}t.release();}
            if(manager!=null)manager.abandonAudioFocusRequest(focus);}
    }
}
