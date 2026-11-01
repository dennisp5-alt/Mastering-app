package com.dennis.aimasteringlab;
import java.util.*;

/** Cut-only moving-frequency TPT band EQ with continuous filter state, smooth
 * envelopes and shared edit budget. Centre processing leaves side audio intact. */
final class TrackingDynamicEq {
    private final Lane[] lanes=new Lane[3];private final double[] cut=new double[3];private final double ending,amount;
    TrackingDynamicEq(ProfessionalMasteringModel.Result model,double duration,double amount){
        this.amount=amount;ending=Math.max(2,duration-Math.max(8,duration*.04));
        for(int k=0;k<3;k++)lanes[k]=new Lane(model,k*5);
    }
    double process(double mid,double seconds){
        double total=0;
        for(int k=0;k<3;k++){cut[k]=seconds>=2&&seconds<ending?lanes[k].request(seconds)*amount:0;total-=cut[k];}
        double scale=total>1.5?1.5/total:1,out=mid;
        for(int k=0;k<3;k++)out+=lanes[k].delta(mid,cut[k]*scale,seconds<2||seconds>=ending);
        return out;
    }
    private static final class Lane {
        final ArrayList<ProfessionalMasteringModel.Event> events=new ArrayList<>();int cursor;
        double ic1,ic2,frequency=2000,targetFrequency=2000,q=2.2,gainDb;
        Lane(ProfessionalMasteringModel.Result m,int offset){
            for(ProfessionalMasteringModel.Event e:m.events)if(e.automatic&&e.contextPassed&&MusicalEventGuard.offset(e.kind)==offset)events.add(e);
            events.sort(Comparator.comparingDouble(e->e.startSeconds));
        }
        double request(double t){
            while(cursor<events.size()&&t>events.get(cursor).endSeconds+.12)cursor++;
            if(cursor==events.size())return 0;ProfessionalMasteringModel.Event e=events.get(cursor);
            if(t<e.startSeconds)return 0;
            double p=(t-e.startSeconds)/MusicalEventGuard.STEP;
            int n=e.trackingFrequency.length,i=Math.max(0,Math.min(n-1,(int)p)),j=Math.min(n-1,i+1);double f=Math.max(0,Math.min(1,p-i));
            targetFrequency=e.trackingFrequency[i]*(1-f)+e.trackingFrequency[j]*f;q=e.trackedQ;
            double strength=e.trackingStrength[i]*(1-f)+e.trackingStrength[j]*f;
            double envelope=t<=e.endSeconds?smooth((t-e.startSeconds)/.012):smooth(1-(t-e.endSeconds)/.12);
            return Math.max(-1.2,e.primaryDb)*strength*envelope;
        }
        double delta(double x,double requested,boolean protectedRegion){
            frequency+=(targetFrequency-frequency)*.002;gainDb+=(requested-gainDb)*.004;
            if(protectedRegion)gainDb=0;
            double g=Math.tan(Math.PI*frequency/48000),k=1/q,a1=1/(1+g*(g+k)),a2=g*a1,a3=g*a2;
            double v3=x-ic2,v1=a1*ic1+a2*v3,v2=ic2+a2*ic1+a3*v3;ic1=2*v1-ic1;ic2=2*v2-ic2;
            return Math.abs(gainDb)<1e-12?0:(Math.pow(10,gainDb/20)-1)*k*v1;
        }
        static double smooth(double x){x=Math.max(0,Math.min(1,x));return x*x*(3-2*x);}
    }
}
