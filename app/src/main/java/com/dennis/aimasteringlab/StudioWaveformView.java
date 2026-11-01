package com.dennis.aimasteringlab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import java.io.File;
import java.io.FileInputStream;

/** Lightweight visual preview of real WAV data, never used for mastering decisions. */
final class StudioWaveformView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private volatile float[] peaks;
    private int request;
    StudioWaveformView(Context c,File file){super(c);setMinimumHeight(StudioVisuals.dp(c,108));
        if(file!=null&&file.isFile())load(file);
    }
    private void load(File file){final int id=++request;
        Thread task=new Thread(()->{
            float[] values=new float[144];
            try(WavFile.Reader wav=WavFile.open(new FileInputStream(file))){
                long position=0, frames=wav.info.frames;float[][] block=new float[wav.info.channels][256];
                for(int i=0;i<values.length;i++){
                    long target=frames*i/values.length;
                    if(target>position){wav.skipFrames(target-position);position=target;}
                    int n=wav.readFrames(block,(int)Math.min(256,frames-position));
                    if(n<1)break;position+=n;
                    float high=0;for(int j=0;j<n;j++)for(float[] channel:block)high=Math.max(high,Math.abs(channel[j]));
                    values[i]=high;
                }
                post(()->{if(id==request){peaks=values;invalidate();}});
            }catch(Exception ignored){/* unavailable media shows an empty preview, not fake audio */}
        },"Studio-WAV-waveform");task.setDaemon(true);task.start();
    }
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);
        int w=getWidth(),h=getHeight();float mid=h*.5f;
        paint.setColor(0x4429C9D6);paint.setStrokeWidth(1);canvas.drawLine(0,mid,w,mid,paint);
        float[] p=peaks;if(p==null)return;
        float step=w/(float)p.length;paint.setStrokeWidth(Math.max(2,step*.48f));
        for(int i=0;i<p.length;i++){
            float amp=(float)Math.sqrt(Math.min(1,p[i]));float len=Math.max(1,amp*(h*.43f));
            paint.setColor(i%7==0?0xFF69F6FD:0xFF1BD7E4);
            float x=i*step+step/2;canvas.drawLine(x,mid-len,x,mid+len,paint);
        }
    }
}
