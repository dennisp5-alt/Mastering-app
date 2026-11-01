package com.dennis.aimasteringlab;
import java.io.*;
import java.nio.file.Files;
import java.util.Locale;
import org.json.JSONObject;

/** Mirrors the phone's no-stems/no-reference full release path on a real WAV. */
public final class RealMusicReleaseHarness {
    static long lastProgress;
    static final AudioAnalyzer.Progress QUIET=(s,p)->{long now=System.currentTimeMillis();if(now-lastProgress>15000){System.out.println("PROGRESS "+s+" "+p+"%");lastProgress=now;}};
    static void require(boolean v,String why){if(!v)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception{
        File input=new File(args[0]),work=new File(args[1]);work.mkdirs();String title=args[2];
        AudioAnalyzer.StreamProvider raw=()->new FileInputStream(input);
        AnalysisResult original=AudioAnalyzer.analyze(input.getName(),raw,QUIET);
        original.sourceGuard=SourceGuardModel.analyze(raw,original,QUIET);
        original.sections=LearnedSectionMapper.analyze(raw,original,QUIET);
        original.aiDiagnostics=AdaptiveDiagnosticModel.analyze(raw,original,QUIET);
        original.clickDropoutRepair=ClickDropoutRepair.analyze(raw,original,QUIET);
        File repaired=new File(work,"repaired-source.wav");
        if(original.clickDropoutRepair.hasRepairs())ClickDropoutRepair.render(raw,original,original.clickDropoutRepair,repaired,QUIET);
        AudioAnalyzer.StreamProvider processing=repaired.isFile()?()->new FileInputStream(repaired):raw;
        AnalysisResult source=repaired.isFile()?TechnicalSourcePreparation.reanalyze(processing,original,QUIET):original;
        source.nextGenerationIntelligence=NextGenerationMasteringModel.analyze(processing,source,null,null,null,QUIET);
        AutonomousMasteringBrain.Result brain=AutonomousMasteringBrain.analyze(processing,source,QUIET);
        FullAiMasteringEngine.Result engine=FullAiMasteringEngine.run(processing,source,brain,work,QUIET);
        FullAiReleaseQc.Result qc=FullAiReleaseQc.run(processing,source,engine,work,title,QUIET);
        FullAiPackageBuilder.Result pack=FullAiPackageBuilder.build(source,brain,engine,qc,work,title,QUIET);
        require(qc.releaseFormats != null && qc.releaseFormats.complete(), "Release formats incomplete: "+(qc.releaseFormats==null?"missing":qc.releaseFormats.error));
        AnalysisResult v=qc.finalMasterVerification;
        require(v.wav.frames==original.wav.frames&&v.clippedSamples==0&&v.nonFiniteSamples==0&&v.truePeakEstimateDbtp<=-1,"Final technical safety failed");
        require(qc.passageCheck!=null&&qc.passageCheck.passed(),"Final passage protection failed");
        require(original.sourceSha256.equals(Hashing.sha256(input)),"Original song changed");
        require(qc.finalMasterSha256.equals(Hashing.sha256(qc.finalMaster)),"Final file changed after QC");
        require("CONDITIONAL".equals(qc.technicalVerdict),"Host must disclose unavailable Android codec verification");
        for(FullAiMasteringEngine.Candidate c:engine.candidates)if(c.qualityDecision!=null)System.out.println(title+" "+c.strategy+" "+c.qualityDecision);
        JSONObject j=new JSONObject();j.put("input",input.getAbsolutePath());j.put("input_sha256",original.sourceSha256);
        j.put("source_lufs",original.integratedLufs);j.put("source_dbtp",original.truePeakEstimateDbtp);
        j.put("frames",v.wav.frames);j.put("winner",qc.finalCandidate.strategy);j.put("master",qc.finalMaster.getAbsolutePath());
        j.put("sha256",qc.finalMasterSha256);j.put("lufs",v.integratedLufs);j.put("dbtp",v.truePeakEstimateDbtp);
        j.put("lra",v.lraLu);j.put("limiter_profile",qc.finalCandidate.render.master.limiterProfile);
        j.put("report",pack.report.getAbsolutePath());j.put("quality_decision",engine.professionalDecision);
        j.put("release_formats",ReleaseFormats.json(qc.releaseFormats));
        Files.writeString(new File(work,"validation-summary.json").toPath(),j.toString(2));
        System.out.printf(Locale.US,"REAL_MUSIC_RELEASE_PASS title=%s winner=%s LUFS=%.6f TP=%.6f limiter=%s sha=%s%n",title,qc.finalCandidate.strategy,v.integratedLufs,v.truePeakEstimateDbtp,qc.finalCandidate.render.master.limiterProfile,qc.finalMasterSha256);
    }
}
