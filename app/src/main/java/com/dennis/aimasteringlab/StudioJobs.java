package com.dennis.aimasteringlab;
import android.content.Context;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Application-context-owned worker; projects survive activity and process recreation. */
final class StudioJobs {
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor();
    private static volatile boolean busy;
    interface Task {void run()throws Exception;}
    static boolean busy(){return busy||MasteringJobControl.running();}
    static void after(Runnable action){WORKER.execute(()->new android.os.Handler(android.os.Looper.getMainLooper()).post(action));}
    static synchronized void task(Context context,String title,Task task)throws IOException{
        if(busy())throw new IOException("A job is already running. Pause it before starting another operation.");
        busy=true;Context app=context.getApplicationContext();
        try{MasteringForegroundService.start(app,title);}catch(RuntimeException e){busy=false;throw new IOException("Android could not start the background job. Keep the app open and retry.",e);}
        FutureTask<Void> future=new FutureTask<Void>(()->{
            try{task.run();MasteringForegroundService.finish(true,"Studio job complete; results saved in Projects");}
            catch(Exception e){MasteringForegroundService.finish(false,Thread.currentThread().isInterrupted()?"Paused — reopen Studio and resume":"Studio: "+e.getMessage());}
            return null;
        }){
            @Override public void run(){try{super.run();}finally{
                if(isCancelled())MasteringForegroundService.finish(false,"Paused — saved projects can be resumed");
                MasteringJobControl.clear(this);
                busy=false;
            }}
        };
        MasteringJobControl.register(future);WORKER.execute(future);
    }
    static void queue(Context context,File root,boolean failed)throws Exception{
        List<StudioProject> selected=StudioQueue.select(root,failed);
        if(selected.isEmpty())throw new IOException("No queued or paused projects. Import WAVs, queue saved edits, or choose Retry failed.");
        run(context,selected);
    }
    static void project(Context context,StudioProject p)throws Exception{run(context,java.util.Collections.singletonList(p));}
    private static void run(Context context,List<StudioProject> selected)throws Exception{
        task(context,"studio queue",()->StudioQueue.run(selected,StudioQueue::process,MasteringForegroundService::update));
    }
}
