package com.dennis.aimasteringlab;
import java.io.*;
import java.util.*;

/** Format, timing, anti-alias, codec headroom and isolation regressions. */
public final class V012ExportsHarness {
    static final AudioAnalyzer.Progress QUIET=(s,p)->{};
    static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static AnalysisResult measure(File f)throws Exception{return AudioAnalyzer.analyze(f.getName(),()->new FileInputStream(f),QUIET);}
    public static void main(String[] args)throws Exception{
        File work=new File(args[0]);work.mkdirs();resampling();
        File source=new File(work,"Source.wav");int frames=48000*8;
        try(PcmExportWriter w=new PcmExportWriter(source,48000,32,frames)){
            Random rng=new Random(991);for(int i=0;i<frames;i++){
                double t=i/48000.0,edge=Math.min(1,Math.min(t/.02,(8-t)/.05));
                double m=.14*Math.sin(2*Math.PI*613*t)+.08*Math.sin(2*Math.PI*2197*t)+.025*(rng.nextDouble()*2-1);
                if(i%48000==24000)m=.86;
                double s=.03*Math.sin(2*Math.PI*911*t);w.frame((m+s)*edge,(m-s)*edge);
            }
        }
        String sha=Hashing.sha256(source);AnalysisResult a=measure(source);
        ReleaseFormats.Result r=ReleaseFormats.create(source,a,work,"Codec Fixture_v2.wav",QUIET);
        require(r.complete(),r.error);require(sha.equals(Hashing.sha256(source)),"Derivative modified original master");
        require(r.wav16.getName().equals("CodecFixture_v2_Master_16bit-44.1kHz.wav"),"Versioned CamelCase naming");
        require(r.wav16Qc.wav.frames==352800&&r.mp3Qc.wav.frames==352800,"Decoded duration or resample duration");
        require(r.wav16Qc.wav.bitsPerSample==16&&r.wav16Qc.truePeakEstimateDbtp<=-1.02&&r.mp3Qc.truePeakEstimateDbtp<=-1.05,"Output format/peak safety");
        // A single interrupted/short temporary write must be recreated, never accepted.
        final boolean[] injected={false};
        ReleaseFormats.Result recovered=ReleaseFormats.create(source,a,work,"Recovery",(stage,percent)->{
            if(!injected[0]&&stage.equals("Checksumming source")){
                File[] files=work.listFiles((dir,name)->name.startsWith("daml-export16-")&&name.endsWith(".part"));
                if(files!=null&&files.length>0){injected[0]=true;try(RandomAccessFile f=new RandomAccessFile(files[0],"rw")){f.setLength(1000);}catch(IOException e){throw new RuntimeException(e);}}
            }
        });
        require(injected[0]&&recovered.complete()&&recovered.exportAttempts==2&&recovered.firstAttemptError!=null,"Incomplete export was not independently recovered");
        // Decode this independently with ffmpeg in the external gate as well.
        File second=new File(work,"IndependentDecode.mp3"),decoded=new File(work,"BundledDecode.wav");
        OfflineMp3.Encoded metadata=OfflineMp3.encode(r.wav16,second,"Codec Fixture",0,QUIET);
        OfflineMp3.decode(second,metadata,decoded,QUIET);
        try(RandomAccessFile f=new RandomAccessFile(second,"rw")){f.setLength(f.length()-150);}
        try{OfflineMp3.verifyFrames(second);throw new AssertionError("Truncated MP3 accepted");}catch(IOException expected){}
        second.delete();
        Thread.currentThread().interrupt();
        try{ReleaseFormats.create(source,a,new File(work,"cancel"),"Cancelled",QUIET);throw new AssertionError("Cancelled exports continued");}
        catch(IOException expected){}finally{Thread.interrupted();}
        // An optional export failure retains a valid master and exposes no partial format.
        File blocker=new File(work,"blocked-path");java.nio.file.Files.write(blocker.toPath(),new byte[]{1});
        ReleaseFormats.Result fail=ReleaseFormats.create(source,a,blocker,"Failure",QUIET);
        require(!fail.complete()&&fail.exportAttempts==2&&fail.error!=null&&fail.wav16Qc==null&&fail.mp3Qc==null&&sha.equals(Hashing.sha256(source)),"Export failure damaged or falsely verified master");
        java.nio.file.Files.write(new File(work,"format-qc.json").toPath(),ReleaseFormats.json(r).toString(2).getBytes("UTF-8"));
        System.out.println("V012_EXPORTS_PASS "+r.summary()+" frames="+r.mp3Qc.wav.frames+" attempts="+r.mp3Passes);
    }
    static void resampling()throws Exception{
        for(int n:new int[]{1,31,200,4096}){
            ArrayList<Double> y=new ArrayList<>();SincResampler r=new SincResampler(48000,44100,96,(l,rr)->{require(Math.abs(l+rr)<1e-14,"Resampler stereo phase");y.add(l);});
            int impulse=n/2;for(int i=0;i<n;i++)r.add(i==impulse?.1:0,i==impulse?-.1:0);r.finish();
            require(y.size()==Math.round(n*44100.0/48000),"Resampler wrong duration");
            int max=0;for(int i=0;i<y.size();i++)if(Math.abs(y.get(i))>Math.abs(y.get(max)))max=i;
            require(Math.abs(max-impulse*44100.0/48000)<=1,"Resampler uncompensated latency");
        }
        double pass=tone(18000),stop=tone(23500);
        require(Math.abs(pass)<.1,"18 kHz passband loss "+pass);require(stop<-65,"23.5 kHz alias attenuation "+stop);
        System.out.printf(Locale.US,"SINC_TIMING_ALIAS_PASS 18kHz=%.4f dB 23.5kHz=%.2f dB%n",pass,stop);
    }
    static double tone(double hz)throws Exception{
        final double[] sum={0};final int[] n={0};SincResampler r=new SincResampler(48000,44100,96,(l,rr)->{if(n[0]>=1000&&n[0]<43000)sum[0]+=l*l;n[0]++;});
        for(int i=0;i<48000;i++){double v=Math.sin(2*Math.PI*hz*i/48000);r.add(v,v);}r.finish();return 10*Math.log10(sum[0]/42000/.5);
    }
}
