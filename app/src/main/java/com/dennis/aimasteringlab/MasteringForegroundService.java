package com.dennis.aimasteringlab;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

/** Visible offline foreground service that keeps an active file job alive. */
public final class MasteringForegroundService extends Service {
    private static final String CHANNEL="dennis_ai_mastering";
    private static final String ACTION_CANCEL="com.dennis.aimasteringlab.CANCEL";
    private static final int ID=9109;
    private static volatile MasteringForegroundService instance;
    private PowerManager.WakeLock wakeLock;
    private boolean normalFinish;
    private String song="song";

    static void start(Context context,String songName) {
        Intent i=new Intent(context,MasteringForegroundService.class);
        i.putExtra("song",songName);
        if(Build.VERSION.SDK_INT>=26)context.startForegroundService(i);
        else context.startService(i);
    }

    static void update(String stage,int percent) {
        MasteringJobControl.update(stage,percent);
        MasteringForegroundService s=instance;
        if(s!=null)s.show(stage,percent,true);
    }

    static void finish(boolean success,String message) {
        MasteringJobControl.complete(success,message);
        MasteringForegroundService s=instance;
        if(s==null)return;
        s.normalFinish=true;
        s.releaseWakeLock();
        s.show(message,100,false);
        if(Build.VERSION.SDK_INT>=24)s.stopForeground(STOP_FOREGROUND_DETACH);
        else s.stopForeground(false);
        s.stopSelf();
    }

    @Override public void onCreate(){super.onCreate();instance=this;createChannel();}

    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent!=null&&ACTION_CANCEL.equals(intent.getAction())) {
            MasteringJobControl.cancel();
            show("Cancellation requested — finishing a safe file boundary",-1,true);
            return START_NOT_STICKY;
        }
        if(intent!=null&&intent.getStringExtra("song")!=null)
            song=intent.getStringExtra("song");
        normalFinish=false; acquireWakeLock();
        Notification n=notification("Starting autonomous master",0,true);
        if(Build.VERSION.SDK_INT>=29)startForeground(ID,n,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        else startForeground(ID,n);
        MasteringJobControl.Snapshot state=MasteringJobControl.snapshot();
        if(state.finished&&!MasteringJobControl.running())finish(state.success,state.stage);
        return START_NOT_STICKY;
    }

    @Override public IBinder onBind(Intent intent){return null;}

    @Override public void onDestroy(){
        instance=null;releaseWakeLock();
        if(!normalFinish)MasteringJobControl.cancel();
        super.onDestroy();
    }

    private void acquireWakeLock(){
        if(wakeLock!=null&&wakeLock.isHeld())return;
        PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
        if(pm==null)return;
        wakeLock=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "DennisAiMasteringLab:Mastering");
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire(6L*60L*60L*1000L);
    }
    private void releaseWakeLock(){if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release();}
    private void createChannel(){if(Build.VERSION.SDK_INT>=26){NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(nm!=null){NotificationChannel c=new NotificationChannel(CHANNEL,"AI mastering jobs",NotificationManager.IMPORTANCE_LOW);c.setDescription("Progress and cancellation for offline mastering");nm.createNotificationChannel(c);}}}
    private void show(String text,int percent,boolean ongoing){NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(nm!=null)nm.notify(ID,notification(text,percent,ongoing));}
    private Notification notification(String text,int percent,boolean ongoing){
        Intent open=new Intent(this,StudioActivity.class);
        PendingIntent content=PendingIntent.getActivity(this,0,open,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Intent cancel=new Intent(this,MasteringForegroundService.class);
        cancel.setAction(ACTION_CANCEL);
        PendingIntent cancelIntent=PendingIntent.getService(this,1,cancel,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26
                ?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle(ongoing?"Mastering "+song:"Dennis AI Mastering Lab")
                .setContentText(text).setContentIntent(content)
                .setOngoing(ongoing).setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_PROGRESS);
        if(ongoing){b.addAction(new Notification.Action.Builder(
                android.R.drawable.ic_menu_close_clear_cancel,"Cancel",cancelIntent).build());
            if(percent>=0)b.setProgress(100,Math.max(0,Math.min(100,percent)),false);
            else b.setProgress(0,0,true);
        } else b.setAutoCancel(true);
        return b.build();
    }
}
