package com.dennis.aimasteringlab;

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

/** Process-local ownership, progress hand-off and cancellation for a foreground job. */
final class MasteringJobControl {
    interface Listener { void changed(Snapshot snapshot); }
    static final class Snapshot {
        final boolean running,finished,success;final String stage;final int percent;
        Snapshot(boolean r,boolean f,boolean s,String text,int p){running=r;finished=f;success=s;stage=text;percent=p;}
    }
    private static final AtomicReference<Future<?>> ACTIVE=new AtomicReference<>();
    private static final AtomicReference<Listener> LISTENER=new AtomicReference<>();
    private static volatile Snapshot snapshot=new Snapshot(false,false,false,"No active mastering job",0);
    private MasteringJobControl(){}
    static void register(Future<?> job){ACTIVE.set(job);publish(new Snapshot(true,false,false,"Starting autonomous Full AI mastering",0));}
    static void clear(Future<?> job){ACTIVE.compareAndSet(job,null);}
    static boolean running(){Future<?>job=ACTIVE.get();return job!=null&&!job.isDone();}
    static boolean cancel(){Future<?>job=ACTIVE.get();boolean done=job!=null&&!job.isDone()&&job.cancel(true);if(done)update("Cancellation requested — finishing a safe file boundary",-1);return done;}
    static void update(String stage,int percent){Snapshot current=snapshot;publish(new Snapshot(running(),false,false,stage==null?current.stage:stage,percent));}
    static void complete(boolean success,String stage){publish(new Snapshot(false,true,success,stage==null?(success?"Master saved and verified":"Mastering failed"):stage,success?100:0));}
    static Snapshot snapshot(){return snapshot;}
    static void attach(Listener listener){LISTENER.set(listener);if(listener!=null)listener.changed(snapshot);}
    static void detach(Listener listener){LISTENER.compareAndSet(listener,null);}
    private static void publish(Snapshot next){snapshot=next;Listener listener=LISTENER.get();if(listener!=null)listener.changed(next);}
}
