package com.dennis.aimasteringlab;
import java.io.*;
import java.util.*;
import org.json.*;

/** Local excess detector. It identifies frequency/time evidence, not separated instruments. */
final class StudioLowEnd {
    static final double STEP=.10;
    static final double[] HZ={70,120,220,360};
    static final class Scan {
        double[][] band,ratio;double[] body,side;int count;double duration;
    }
    static Scan scan(File f,AudioAnalyzer.Progress progress)throws IOException{
        try(WavFile.Reader r=WavFile.open(new FileInputStream(f))){
            Scan s=new Scan();s.count=(int)((r.info.frames+4799)/4800);s.duration=r.info.durationSeconds;
            s.band=new double[4][s.count];s.ratio=new double[4][s.count];s.body=new double[s.count];s.side=new double[s.count];
            Biquad[] filters=new Biquad[4];for(int k=0;k<4;k++)filters[k]=Biquad.bandPass(48000,HZ[k],1.7);
            Biquad hi=Biquad.highPass(48000,600),lo=Biquad.lowPass(48000,3500),sideLo=Biquad.lowPass(48000,450);
            float[][] b=new float[2][4096];int n;long at=0;
            while((n=r.readFrames(b,4096))>0){for(int i=0;i<n;i++,at++){
                double m=(b[0][i]+b[1][i])*.5,side=(b[0][i]-b[1][i])*.5,body=lo.process(hi.process(m)),sv=sideLo.process(side);
                int j=(int)(at/4800);s.body[j]+=body*body;s.side[j]+=sv*sv;
                for(int k=0;k<4;k++){double v=filters[k].process(m);s.band[k][j]+=v*v;}
            }StudioAudio.cancel();progress.update("Checking local bass and low-mid excess",(int)(at*100/r.info.frames));}
            for(int j=0;j<s.count;j++){
                double frames=Math.min(4800,r.info.frames-j*4800L);s.body[j]=db(s.body[j]/frames);s.side[j]=db(s.side[j]/frames);
                for(int k=0;k<4;k++){s.band[k][j]=db(s.band[k][j]/frames);s.ratio[k][j]=s.band[k][j]-s.body[j];}
            }return s;
        }
    }
    static List<StudioRecipe.Edit> propose(Scan s){
        ArrayList<StudioRecipe.Edit> out=new ArrayList<>();boolean[] occupied=new boolean[s.count];
        int protectedEnd=(int)((s.duration-Math.max(3,s.duration*.02))/STEP);
        for(int k=0;k<4;k++)for(int j=20;j<protectedEnd-5&&out.size()<12;j++){
            double median=median(s.ratio[k],Math.max(0,j-40),Math.min(s.count,j+41));
            boolean hot=s.ratio[k][j]>median+5&&s.band[k][j]>-36&&s.body[j]>-55&&!occupied[j];
            if(hot)for(int other=0;other<4;other++)if(other!=k&&s.band[other][j]>s.band[k][j]+1
                    &&s.ratio[other][j]>median(s.ratio[other],Math.max(0,j-40),Math.min(s.count,j+41))+4.5)hot=false;
            if(!hot)continue;
            int end=j;while(end<protectedEnd&&s.ratio[k][end]>median+3.5&&!occupied[end])end++;
            int count=end-j;
            // Short/repeating attacks and long arrangement changes stay diagnostic.
            if(count>=5&&count<=22&&s.band[k][j]-s.band[k][Math.max(0,j-1)]<7){
                int nearby=0;for(int n=Math.max(0,j-40);n<Math.min(s.count,end+40);n++)if((n<j||n>=end)&&s.ratio[k][n]>median+4)nearby++;
                double sideMean=0,bandMean=0;for(int n=j;n<end;n++){sideMean+=s.side[n];bandMean+=s.band[k][n];}
                if(nearby<=6&&sideMean/count<bandMean/count-8){
                    StudioRecipe.Edit e=new StudioRecipe.Edit();e.kind="DYNAMIC_EQ";e.channel="MID";e.hz=HZ[k];e.q=1.7;
                    e.start=j*STEP;e.end=end*STEP;e.db=-1.0;e.threshold=median(s.band[k],Math.max(0,j-40),Math.min(s.count,end+40))+2;
                    e.threshold=Math.max(-80,Math.min(-3,e.threshold));e.automatic=true;
                    e.reason="Isolated centre low-frequency excess relative to nearby passages; finished-audio verification required";
                    out.add(e);for(int n=j;n<end;n++)occupied[n]=true;
                }
            }j=Math.max(j,end-1);
        }return out;
    }
    static JSONObject verify(Scan before,Scan after,List<StudioRecipe.Edit> edits,double trimStart)throws Exception{
        JSONArray events=new JSONArray();boolean pass=true;int tested=0;
        for(StudioRecipe.Edit e:edits)if(e.enabled&&e.automatic){
            int k=0;for(int b=1;b<4;b++)if(Math.abs(HZ[b]-e.hz)<Math.abs(HZ[k]-e.hz))k=b;
            double change=0,body=0;int n=0;
            for(int j=(int)Math.ceil((e.start+.1)/STEP);j<(int)Math.floor((e.end-.1)/STEP);j++){
                int a=(int)Math.round(j-trimStart/STEP);
                if(j>=0&&j<before.count&&a>=0&&a<after.count){change+=before.ratio[k][j]-after.ratio[k][a];body+=after.body[a]-before.body[j];n++;}
            }
            if(n==0)continue;tested++;
            double improvement=change/n;boolean good=improvement>=.10&&Math.abs(body/n)<=.25;
            pass&=good;events.put(new JSONObject().put("start",e.start).put("end",e.end).put("hz",e.hz)
                    .put("target_ratio_improvement_db",improvement).put("body_change_db",body/n).put("passed",good));
        }
        return new JSONObject().put("passed",pass).put("events_checked",tested).put("events",events)
                .put("limit","Signal evidence only; bass, drums and vocals are not isolated sources");
    }
    private static double median(double[] x,int start,int end){double[] a=Arrays.copyOfRange(x,start,end);Arrays.sort(a);return a.length==0?0:a[a.length/2];}
    private static double db(double e){return 10*Math.log10(Math.max(1e-20,e));}
}
