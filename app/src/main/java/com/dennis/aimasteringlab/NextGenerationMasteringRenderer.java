package com.dennis.aimasteringlab;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Renders bounded v0.9 source-aware candidates around the stereo mix. */
final class NextGenerationMasteringRenderer {
    enum Mode {
        TRANSLATION_CONSENSUS("translation_consensus_correction"),
        PERSISTENT_TONE_REPAIR("hum_whine_persistent_tone_repair"),
        CHANNEL_INTEGRITY("channel_integrity_stereo_balance_repair"),
        REFERENCE_AWARE("optional_reference_aware_calibration"),
        V09_COMBINED("v09_source_aware_combined");
        final String strategy;
        Mode(String strategy){this.strategy=strategy;}
    }

    static final class DeltaVerification {
        long frames; double sourceCorrelation; double maximumAbsoluteDelta;
        double protectedIntroMaxDelta; double protectedEndingMaxDelta;
        boolean protectedRegionsExact;
    }

    static final class Result {
        Mode mode;
        AutonomousMasteringRenderer.Result render;
        DeltaVerification delta;
        int correctionEvents;
        double maximumNominalEditDb;
        double modelConfidence;
        double evidenceBenefit;
        double measuredTargetBenefit;
        double translationReductionDb;
        double toneReductionDb;
        double channelBalanceReductionDb;
        double phaseImprovement;
        double referenceDistanceReduction;
        boolean wideningUsed;
        String evidenceSummary;
    }

    private static final int BUFFER=4096;
    private NextGenerationMasteringRenderer(){}

    static Result render(AudioAnalyzer.StreamProvider source,
                         AnalysisResult sourceAnalysis,
                         AutonomousMasteringBrain.Plan transparentPlan,
                         Mode mode, File output, File workDirectory,
                         AudioAnalyzer.Progress progress) throws IOException {
        return render(source,sourceAnalysis,transparentPlan,mode,output,
                workDirectory,-1.0,false,progress);
    }

    static Result render(AudioAnalyzer.StreamProvider source,
                         AnalysisResult sourceAnalysis,
                         AutonomousMasteringBrain.Plan transparentPlan,
                         Mode mode, File output, File workDirectory,
                         double ceiling, boolean forceLimiter,
                         AudioAnalyzer.Progress progress) throws IOException {
        validate(sourceAnalysis,transparentPlan,mode);
        File premaster=new File(workDirectory,"v09-premaster-"+mode.strategy+"-"+System.nanoTime()+".wav");
        try {
            RenderStats stats=renderFloat(source,sourceAnalysis,mode,premaster,progress);
            AnalysisResult corrected=AudioAnalyzer.analyze(premaster.getName(),
                    ()->new FileInputStream(premaster),progress);
            DeltaVerification delta=verifyDelta(source,()->new FileInputStream(premaster),sourceAnalysis);
            validatePremaster(sourceAnalysis,corrected,delta,stats);
            boolean limiterAllowed=forceLimiter && (sourceAnalysis.sourceGuard==null
                    || !sourceAnalysis.sourceGuard.limiterEscalationBlocked);
            MasterRenderer.Result master=MasterRenderer.renderWithCeiling(
                    ()->new FileInputStream(premaster),corrected,
                    corrected.appliedSafeGainDb,output,ceiling,
                    limiterAllowed,progress);
            Result result=describe(sourceAnalysis,corrected,mode,stats);
            result.delta=delta;
            AutonomousMasteringRenderer.Result wrapper=new AutonomousMasteringRenderer.Result();
            wrapper.plan=transparentPlan; wrapper.master=master;
            wrapper.correctedPremaster=corrected;
            wrapper.maximumLocalCutDb=result.maximumNominalEditDb;
            wrapper.guardrailVerdict="V0.9 PASS: bounded stereo-derived edits preserved duration, protected edges, phase, continuity and no-widening policy";
            result.render=wrapper;
            master.aiGuided=true; master.aiCorrectionEvents=result.correctionEvents;
            master.maximumAiCorrectionDb=result.maximumNominalEditDb;
            master.aiCorrectionSummary=mode.strategy+"; "+result.evidenceSummary;
            return result;
        } finally { if(premaster.exists()&&!premaster.delete())premaster.deleteOnExit(); }
    }

    private static void validate(AnalysisResult source,
                                 AutonomousMasteringBrain.Plan plan,
                                 Mode mode) throws IOException {
        if (mode == Mode.TRANSLATION_CONSENSUS || mode == Mode.REFERENCE_AWARE
                || mode == Mode.V09_COMBINED) {
            throw new IOException("Translation, reference matching and combined later shaping remain diagnostic-only");
        }
        if(source.wav.channels!=2||source.wav.sampleRate!=48000)throw new IOException("v0.9 source-aware intelligence requires a 48 kHz stereo WAV");
        if(plan==null||plan.hasAudioChanges())throw new IOException("v0.9 source-aware candidates require the transparent baseline plan");
        NextGenerationMasteringModel.Result model=source.nextGenerationIntelligence;
        if(model==null)throw new IOException("Run v0.9 intelligence first");
        boolean available=mode==Mode.TRANSLATION_CONSENSUS?model.translation!=null&&model.translation.automaticCount()>0
                :mode==Mode.PERSISTENT_TONE_REPAIR?model.tones!=null&&model.tones.automaticCount()>0
                :mode==Mode.CHANNEL_INTEGRITY?model.channel!=null&&model.channel.automaticCount()>0
                :mode==Mode.REFERENCE_AWARE?model.reference!=null&&model.reference.usable&&model.reference.automaticCount()>0
                :model.availableModules()>=2;
        if(!available)throw new IOException("Requested v0.9 module has no eligible correction map");
        if(source.sourceGuard!=null&&source.sourceGuard.secondPassGuard
                && (mode==Mode.TRANSLATION_CONSENSUS||mode==Mode.REFERENCE_AWARE||mode==Mode.V09_COMBINED))
            throw new IOException("Previous-master guard blocked broad v0.9 re-processing");
    }

    private static RenderStats renderFloat(AudioAnalyzer.StreamProvider source,
                                           AnalysisResult analysis,Mode mode,
                                           File output,AudioAnalyzer.Progress progress) throws IOException {
        NextGenerationMasteringModel.Result model=analysis.nextGenerationIntelligence;
        int rate=analysis.wav.sampleRate; double duration=analysis.wav.durationSeconds;
        ArrayList<TimedFilter> timed=new ArrayList<>();
        if(inMode(mode,Mode.TRANSLATION_CONSENSUS)&&model.translation!=null) {
            for(TranslationConsensusModel.Event e:model.translation.events) if(e.automatic) {
                timed.add(new TimedFilter(rate,e.startSeconds,e.endSeconds,
                        Math.sqrt(e.lowHz*e.highHz),Math.max(0.55,
                        Math.sqrt(e.lowHz*e.highHz)/(e.highHz-e.lowHz)),
                        e.attenuationDb,e.kind==TranslationConsensusModel.Kind.VOCAL_MASKING));
            }
        }
        ArrayList<NotchPair> notches=new ArrayList<>(); double toneTotal=0;
        if(inMode(mode,Mode.PERSISTENT_TONE_REPAIR)&&model.tones!=null) {
            for(PersistentToneModel.Event e:model.tones.events) if(e.automatic)toneTotal+=Math.abs(e.attenuationDb);
            double scale=toneTotal>2.5?2.5/toneTotal:1.0;
            for(PersistentToneModel.Event e:model.tones.events) if(e.automatic)
                notches.add(new NotchPair(rate,e.frequencyHz,e.q,e.attenuationDb*scale));
        }
        ChannelPlan channel=ChannelPlan.from(inMode(mode,Mode.CHANNEL_INTEGRITY)?model.channel:null);
        ReferencePlan reference=ReferencePlan.from(rate,
                inMode(mode,Mode.REFERENCE_AWARE)?model.reference:null);
        long dataBytes=analysis.wav.frames*8L;
        if(dataBytes>0xffffffffL-36L)throw new IOException("v0.9 premaster exceeds RIFF/WAV size limit");
        RenderStats stats=new RenderStats();
        File staging=WavRenderIntegrity.stagingFile(output,"v091-premaster");
        try(WavFile.Reader reader=WavFile.open(source.open());
            FileOutputStream fileOutput=new FileOutputStream(staging);
            OutputStream raw=new BufferedOutputStream(fileOutput,256*1024)) {
            writeFloatHeader(raw,rate,2,dataBytes);
            float[][] b=new float[2][BUFFER]; long frame=0; int read;
            double[] delayLeft=new double[25],delayRight=new double[25];int delayAt=0;
            while((read=reader.readFrames(b,BUFFER))>0) {
                for(int n=0;n<read;n++,frame++) {
                    double l=finite(b[0][n]),r=finite(b[1][n]);
                    double originalL=l,originalR=r;
                    double seconds=frame/(double)rate;
                    double edge=edgeWeight(seconds,duration);
                    for(TimedFilter filter:timed) {
                        double[] lr=filter.process(l,r,seconds,edge);l=lr[0];r=lr[1];
                    }
                    for(NotchPair notch:notches){double nl=notch.left.process(l),nr=notch.right.process(r);l+=edge*(nl-l);r+=edge*(nr-r);}
                    if(reference.active&&edge>0){double[]lr=reference.process(l,r,edge);l=lr[0];r=lr[1];}
                    delayLeft[delayAt]=l;delayRight[delayAt]=r;
                    if(channel.delayLeft>0){int at=(delayAt-channel.delayLeft+delayLeft.length)%delayLeft.length;double d=delayLeft[at];l+=edge*(d-l);}
                    if(channel.delayRight>0){int at=(delayAt-channel.delayRight+delayRight.length)%delayRight.length;double d=delayRight[at];r+=edge*(d-r);}
                    delayAt=(delayAt+1)%delayLeft.length;
                    if(channel.invertRight)r*=1.0-2.0*edge;
                    if(channel.leftGain<1.0)l*=1.0-edge*(1.0-channel.leftGain);
                    if(channel.rightGain<1.0)r*=1.0-edge*(1.0-channel.rightGain);
                    for (NotchPair notch : notches) notch.proof.observe(originalL, originalR, l, r, edge >= 0.999);
                    stats.maxDelta=Math.max(stats.maxDelta,Math.max(Math.abs(l-originalL),Math.abs(r-originalR)));
                    writeFloat(raw,l);writeFloat(raw,r);stats.frames++;
                }
                if(progress!=null)progress.update("v0.9: rendering "+mode.strategy,
                        (int)Math.min(98,Math.round(98.0*frame/Math.max(1L,analysis.wav.frames))));
                if(Thread.currentThread().isInterrupted())throw new IOException("v0.9 render cancelled");
            }
            raw.flush();fileOutput.getFD().sync();
            if(frame!=analysis.wav.frames||stats.frames!=analysis.wav.frames)
                throw new IOException("v0.9 premaster frame accounting failed");
        } catch(IOException error) {
            WavRenderIntegrity.discard(staging);throw error;
        }
        try {
            WavRenderIntegrity.commit(staging,output,dataBytes,rate,2,32,true);
        } catch(IOException error) {
            WavRenderIntegrity.discard(staging);throw error;
        }
        double measuredToneBefore=0, measuredToneAfter=0;
        for (NotchPair notch : notches) {
            notch.proof.finish();
            measuredToneBefore += notch.proof.before;
            measuredToneAfter += notch.proof.after;
        }
        stats.measuredToneReductionDb = measuredToneBefore > 1e-16
                ? Math.max(0, 10 * Math.log10(measuredToneBefore / Math.max(1e-30, measuredToneAfter))) : 0;
        stats.events=countEvents(model,mode);stats.maximumNominalEditDb=maxEdit(model,mode);
        return stats;
    }

    static boolean inMode(Mode selected,Mode family){return selected==family||selected==Mode.V09_COMBINED;}
    private static int countEvents(NextGenerationMasteringModel.Result m,Mode mode){int n=0;if(inMode(mode,Mode.TRANSLATION_CONSENSUS)&&m.translation!=null)n+=m.translation.automaticCount();if(inMode(mode,Mode.PERSISTENT_TONE_REPAIR)&&m.tones!=null)n+=m.tones.automaticCount();if(inMode(mode,Mode.CHANNEL_INTEGRITY)&&m.channel!=null)n+=m.channel.automaticCount();if(inMode(mode,Mode.REFERENCE_AWARE)&&m.reference!=null)n+=m.reference.automaticCount();return n;}
    private static double maxEdit(NextGenerationMasteringModel.Result m,Mode mode){double x=0;if(inMode(mode,Mode.TRANSLATION_CONSENSUS)&&m.translation!=null)for(TranslationConsensusModel.Event e:m.translation.events)if(e.automatic)x=Math.max(x,Math.abs(e.attenuationDb));if(inMode(mode,Mode.PERSISTENT_TONE_REPAIR)&&m.tones!=null){double s=0;for(PersistentToneModel.Event e:m.tones.events)if(e.automatic)s+=Math.abs(e.attenuationDb);x=Math.max(x,Math.min(2.5,s));}if(inMode(mode,Mode.CHANNEL_INTEGRITY)&&m.channel!=null)for(ChannelIntegrityModel.Event e:m.channel.events)if(e.automatic)x=Math.max(x,Math.max(Math.abs(e.correctionDb),e.kind==ChannelIntegrityModel.Kind.POLARITY_REVERSAL?0.75:0));if(inMode(mode,Mode.REFERENCE_AWARE)&&m.reference!=null){double s=0;for(ReferenceCalibrationModel.Event e:m.reference.events)if(e.automatic)s+=Math.abs(e.adjustmentDb);x=Math.max(x,Math.min(1.4,s));}return x;}

    private static Result describe(AnalysisResult source,AnalysisResult corrected,Mode mode,RenderStats stats){Result r=new Result();r.mode=mode;r.correctionEvents=stats.events;r.maximumNominalEditDb=stats.maximumNominalEditDb;r.modelConfidence=Math.min(0.94,0.72+0.03*stats.events);r.evidenceBenefit=Math.min(1.0,0.18*stats.events);r.phaseImprovement=Math.max(0,corrected.lowBandCorrelation-source.lowBandCorrelation)+Math.max(0,corrected.stereoCorrelation-source.stereoCorrelation);r.translationReductionDb=inMode(mode,Mode.TRANSLATION_CONSENSUS)?Math.min(0.9,0.15*stats.events):0;r.toneReductionDb=inMode(mode,Mode.PERSISTENT_TONE_REPAIR)?Math.min(2.5,stats.measuredToneReductionDb):0;r.channelBalanceReductionDb=inMode(mode,Mode.CHANNEL_INTEGRITY)?Math.min(.75,stats.maximumNominalEditDb):0;r.referenceDistanceReduction=inMode(mode,Mode.REFERENCE_AWARE)?Math.min(.8,.08*stats.events):0;r.measuredTargetBenefit=Math.min(1.0,r.translationReductionDb/0.25+r.toneReductionDb/1.0+r.channelBalanceReductionDb/.75+r.phaseImprovement/.04+r.referenceDistanceReduction/.25);r.evidenceSummary=String.format(Locale.US,"%d bounded correction event(s); translation %.3f dB, tone %.3f dB, balance %.3f dB, phase +%.4f, reference %.3f; no widening",stats.events,r.translationReductionDb,r.toneReductionDb,r.channelBalanceReductionDb,r.phaseImprovement,r.referenceDistanceReduction);return r;}

    private static void validatePremaster(AnalysisResult src,AnalysisResult out,DeltaVerification d,RenderStats s)throws IOException{if(out.wav.frames!=src.wav.frames||out.wav.channels!=2||out.wav.sampleRate!=48000)throw new IOException("v0.9 changed format or duration");if(out.nonFiniteSamples>0||out.clippedSamples>src.clippedSamples)throw new IOException("v0.9 introduced invalid or clipped samples");if(out.internalZeroRuns>src.internalZeroRuns)throw new IOException("v0.9 introduced an exact-zero interruption");if(!d.protectedRegionsExact)throw new IOException("v0.9 leaked into protected intro or ending");if(d.sourceCorrelation<0.985)throw new IOException("v0.9 exceeded source-correlation guardrail");if(out.stereoCorrelation<src.stereoCorrelation-.025||out.lowBandCorrelation<src.lowBandCorrelation-.020)throw new IOException("v0.9 worsened stereo or LF phase");if(s.maxDelta<1e-9)throw new IOException("v0.9 candidate produced no measurable edit");}

    private static DeltaVerification verifyDelta(AudioAnalyzer.StreamProvider a,AudioAnalyzer.StreamProvider b,AnalysisResult src)throws IOException{DeltaVerification d=new DeltaVerification();double xy=0,xx=0,yy=0;long intro=Math.round(2*src.wav.sampleRate);long ending=Math.round((src.wav.durationSeconds-Math.max(8,src.wav.durationSeconds*.04))*src.wav.sampleRate);try(WavFile.Reader x=WavFile.open(a.open());WavFile.Reader y=WavFile.open(b.open())){float[][]xb=new float[2][4096],yb=new float[2][4096];int nr;long f=0;while((nr=x.readFrames(xb,4096))>0){int ny=y.readFrames(yb,nr);if(ny!=nr)throw new IOException("v0.9 delta length mismatch");for(int i=0;i<nr;i++,f++)for(int c=0;c<2;c++){double xv=xb[c][i],yv=yb[c][i],delta=Math.abs(yv-xv);xy+=xv*yv;xx+=xv*xv;yy+=yv*yv;d.maximumAbsoluteDelta=Math.max(d.maximumAbsoluteDelta,delta);if(f<intro)d.protectedIntroMaxDelta=Math.max(d.protectedIntroMaxDelta,delta);if(f>=ending)d.protectedEndingMaxDelta=Math.max(d.protectedEndingMaxDelta,delta);}d.frames+=nr;}}d.sourceCorrelation=xy/Math.sqrt(Math.max(1e-30,xx*yy));d.protectedRegionsExact=d.protectedIntroMaxDelta<=1e-7&&d.protectedEndingMaxDelta<=1e-7;return d;}

    private static double edgeWeight(double t,double duration){double ending=duration-Math.max(8,duration*.04);if(t<2||t>=ending)return 0;double in=Math.min(1,(t-2)/.10),out=Math.min(1,(ending-t)/.10);double x=Math.max(0,Math.min(in,out));return x*x*(3-2*x);}
    private static double finite(double v){return Double.isFinite(v)?v:0;}
    private static double gain(double db){return Math.pow(10,db/20);}
    private static void writeFloat(OutputStream out,double v)throws IOException{int bits=Float.floatToIntBits((float)finite(v));LittleEndian.writeU32(out,bits&0xffffffffL);}
    private static void writeFloatHeader(OutputStream out,int rate,int channels,long bytes)throws IOException{LittleEndian.writeAscii(out,"RIFF");LittleEndian.writeU32(out,36+bytes);LittleEndian.writeAscii(out,"WAVE");LittleEndian.writeAscii(out,"fmt ");LittleEndian.writeU32(out,16);LittleEndian.writeU16(out,3);LittleEndian.writeU16(out,channels);LittleEndian.writeU32(out,rate);LittleEndian.writeU32(out,rate*channels*4L);LittleEndian.writeU16(out,channels*4);LittleEndian.writeU16(out,32);LittleEndian.writeAscii(out,"data");LittleEndian.writeU32(out,bytes);}

    private static final class RenderStats{long frames;int events;double maximumNominalEditDb,maxDelta,measuredToneReductionDb;}
    private static final class TimedFilter{final double start,end,fade=.05;final boolean side;final Biquad l,r;TimedFilter(int rate,double s,double e,double f,double q,double db,boolean side){start=s;end=e;this.side=side;l=Biquad.peaking(rate,f,q,db);r=Biquad.peaking(rate,f,q,db);}double[]process(double left,double right,double t,double edge){double w=0;if(t>=start&&t<=end){w=Math.min(1,Math.min((t-start)/fade,(end-t)/fade));w=Math.max(0,w);w=w*w*(3-2*w)*edge;}if(side){double m=.5*(left+right),s=.5*(left-right),fs=l.process(s);s+=w*(fs-s);return new double[]{m+s,m-s};}double fl=l.process(left),fr=r.process(right);return new double[]{left+w*(fl-left),right+w*(fr-right)};}}
    private static final class NotchPair {
        final Biquad left, right;
        final ToneProof proof;
        NotchPair(int rate,double f,double q,double db) {
            left=Biquad.peaking(rate,f,q,db); right=Biquad.peaking(rate,f,q,db);
            proof=new ToneProof(rate,f);
        }
    }
    /** Independent before/after coherent tone energy in half-second windows. */
    private static final class ToneProof {
        final double rotateCos,rotateSin; final int window;
        double phaseCos=1,phaseSin=0,before,after;
        final double[] inputReal=new double[2],inputImag=new double[2];
        final double[] outputReal=new double[2],outputImag=new double[2];
        int count;
        ToneProof(int rate,double frequency) {
            rotateCos=Math.cos(2*Math.PI*frequency/rate);
            rotateSin=Math.sin(2*Math.PI*frequency/rate); window=rate/2;
        }
        void observe(double l,double r,double outL,double outR,boolean active) {
            if(active) {
                inputReal[0]+=l*phaseCos; inputImag[0]+=l*phaseSin;
                inputReal[1]+=r*phaseCos; inputImag[1]+=r*phaseSin;
                outputReal[0]+=outL*phaseCos; outputImag[0]+=outL*phaseSin;
                outputReal[1]+=outR*phaseCos; outputImag[1]+=outR*phaseSin;
                if(++count>=window) finish();
            } else if(count>0) finish();
            double next=phaseCos*rotateCos-phaseSin*rotateSin;
            phaseSin=phaseSin*rotateCos+phaseCos*rotateSin; phaseCos=next;
        }
        void finish() {
            if(count>0) for(int c=0;c<2;c++) {
                if(count>=window/2) {
                    before+=(inputReal[c]*inputReal[c]+inputImag[c]*inputImag[c])/count;
                    after+=(outputReal[c]*outputReal[c]+outputImag[c]*outputImag[c])/count;
                }
                inputReal[c]=inputImag[c]=outputReal[c]=outputImag[c]=0;
            }
            count=0;
        }
    }
    private static final class ChannelPlan{double leftGain=1,rightGain=1;boolean invertRight;int delayLeft,delayRight;static ChannelPlan from(ChannelIntegrityModel.Result r){ChannelPlan p=new ChannelPlan();if(r==null)return p;for(ChannelIntegrityModel.Event e:r.events)if(e.automatic){if(e.kind==ChannelIntegrityModel.Kind.LEVEL_BALANCE){if(e.correctionDb<0)p.leftGain=gain(e.correctionDb);else p.rightGain=gain(-e.correctionDb);}else if(e.kind==ChannelIntegrityModel.Kind.POLARITY_REVERSAL)p.invertRight=true;else if(e.kind==ChannelIntegrityModel.Kind.FIXED_DELAY){if(e.lagSamples>0)p.delayLeft=e.lagSamples;else p.delayRight=-e.lagSamples;}}return p;}}
    private static final class ReferencePlan{boolean active;Biquad lowL,lowR,midL,midR,presL,presR,highL,highR;double sideGain=1;static ReferencePlan from(int rate,ReferenceCalibrationModel.Result r){ReferencePlan p=new ReferencePlan();if(r==null||!r.usable)return p;double low=0,mid=0,pres=0,high=0,side=0;for(ReferenceCalibrationModel.Event e:r.events)if(e.automatic){switch(e.kind){case LOW_SHELF:low+=e.adjustmentDb;break;case LOW_MID:mid+=e.adjustmentDb;break;case PRESENCE:pres+=e.adjustmentDb;break;case HIGH_SHELF:high+=e.adjustmentDb;break;case SIDE_REDUCTION:case VOCAL_POCKET:side+=e.adjustmentDb;break;default:}}p.lowL=Biquad.lowShelf(rate,150,low);p.lowR=Biquad.lowShelf(rate,150,low);p.midL=Biquad.peaking(rate,650,.7,mid);p.midR=Biquad.peaking(rate,650,.7,mid);p.presL=Biquad.peaking(rate,2800,.8,pres);p.presR=Biquad.peaking(rate,2800,.8,pres);p.highL=Biquad.highShelf(rate,7500,high);p.highR=Biquad.highShelf(rate,7500,high);p.sideGain=gain(Math.max(-.60,side));p.active=true;return p;}double[]process(double l,double r,double w){double fl=highL.process(presL.process(midL.process(lowL.process(l))));double fr=highR.process(presR.process(midR.process(lowR.process(r))));l+=w*(fl-l);r+=w*(fr-r);double m=.5*(l+r),s=.5*(l-r);s*=1-w*(1-sideGain);return new double[]{m+s,m-s};}}
}
