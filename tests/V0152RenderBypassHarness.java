package com.dennis.aimasteringlab;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.Arrays;

/** Compare the unmodified v0.15.1 core renderer and the zero-event bypass byte for byte. */
public final class V0152RenderBypassHarness {
    private static void check(boolean ok, String why) { if(!ok) throw new AssertionError(why); }
    private static byte[] digest(File file) throws Exception {
        MessageDigest md=MessageDigest.getInstance("SHA-256");
        try(FileInputStream in=new FileInputStream(file)) {
            byte[] block=new byte[16384]; int n;
            while((n=in.read(block))!=-1) md.update(block,0,n);
        }
        return md.digest();
    }
    private static void render(String name, AudioAnalyzer.StreamProvider input,
                               AnalysisResult analysis, AutonomousMasteringBrain.Plan plan,
                               File target) throws Exception {
        Class<?> c=Class.forName("com.dennis.aimasteringlab."+name);
        Method m=c.getDeclaredMethod("renderFloat",AudioAnalyzer.StreamProvider.class,
                AnalysisResult.class,AutonomousMasteringBrain.Plan.class,File.class,AudioAnalyzer.Progress.class);
        m.setAccessible(true);
        m.invoke(null,input,analysis,plan,target,(AudioAnalyzer.Progress)(stage,percent)->{});
    }
    public static void main(String[] args) throws Exception {
        File dir=new File(args.length>0?args[0]:"host-test-work/v0152");dir.mkdirs();
        int tested=0;
        for(int content=0;content<3;content++) {
            File original=new File(dir,"input-"+content+".wav");
            final int length=48000*2;
            try(PcmExportWriter wav=new PcmExportWriter(original,48000,32,length)) {
                for(int i=0;i<length;i++) {
                    double l=content==0?0.0:content==1?
                            ((i%12907==0)?0.88:((i%300)<90?0.04:0.0)):
                            0.20*Math.sin(i*.073)+0.071*Math.cos(i*.174);
                    double r=content==0?0.0:content==1? -l*.82:
                            0.17*Math.sin(i*.041)+0.055*Math.cos(i*.097);
                    wav.frame(l,r);
                }
            }
            AnalysisResult a=new AnalysisResult();
            try(WavFile.Reader reader=WavFile.open(new FileInputStream(original))) {
                a.wav=reader.info;
            }
            check(a.aiDiagnostics==null,"no-event fixture must be empty");
            for(int mode=0;mode<3;mode++) {
                AutonomousMasteringBrain.Plan plan=new AutonomousMasteringBrain.Plan();
                plan.strategy="v0152-test-"+mode;
                plan.mode=mode==2?AutonomousMasteringBrain.Mode.PREFERRED:AutonomousMasteringBrain.Mode.CONSERVATIVE;
                plan.eventScale=mode==0?0:1;
                plan.lowShelfDb=mode==0?0:.43;
                plan.lowMidDb=mode==0?0:-.18;
                plan.presenceDb=mode==0?0:.22;
                plan.highShelfDb=mode==0?0:-.14;
                plan.sideScale=mode==0?1:.985;
                plan.compressorEnabled=mode==2;
                if(plan.compressorEnabled) {
                    plan.compressorThresholdDbfs=-13;
                    plan.compressorRatio=1.3;
                    plan.compressorAttackMs=24;
                    plan.compressorReleaseMs=165;
                    plan.compressorMaximumReductionDb=1;
                }
                AudioAnalyzer.StreamProvider source=()->new FileInputStream(original);
                File oldFile=new File(dir,"old-"+content+"-"+mode+".wav");
                File newFile=new File(dir,"new-"+content+"-"+mode+".wav");
                render("LegacyAutonomousMasteringRenderer",source,a,plan,oldFile);
                render("AutonomousMasteringRenderer",source,a,plan,newFile);
                check(Arrays.equals(digest(oldFile),digest(newFile)),
                        "rendered WAV differs for content="+content+" mode="+mode);
                check(oldFile.length()==newFile.length(),"frame count/length differ");
                tested++;
            }
        }
        check(StudioQcReporting.APP_VERSION.equals(BuildConfig.VERSION_NAME),"runtime package version is the QC version");
        check("0.15.7".equals(StudioQcReporting.APP_VERSION),"host version fixture must be v0.15.7");
        System.out.println("V0152_EXACT_RENDER_PASS "+tested+" bit-identical WAV comparisons; BuildConfig version matches QC");
    }
}
