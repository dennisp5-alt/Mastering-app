package com.dennis.aimasteringlab;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.zip.ZipFile;
import org.json.JSONObject;

/** End-to-end reproduction of the supplied, formerly rejected Claude master. */
public final class CompliantMasterRecoveryHarness {
    static final AudioAnalyzer.Progress QUIET = (s,p) -> {};
    static void require(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
    public static void main(String[] args) throws Exception {
        File input = new File(args[0]), work = new File(args[1]); work.mkdirs();
        AudioAnalyzer.StreamProvider source = () -> new FileInputStream(input);
        AnalysisResult a = AudioAnalyzer.analyze(input.getName(), source, QUIET);
        require("f78a0f49a215f07a00c0d0ff524a0fc9e6f6af731f716046fb9486ea0834676d".equals(a.sourceSha256), "Wrong or incomplete reproduction input");
        require(a.alreadyCompliantNoRender && !a.alreadyAtTargetNoRender && a.appliedSafeGainDb == 0, "Compliant preservation policy");
        a.sourceGuard = SourceGuardModel.analyze(source, a, QUIET);
        require(a.sourceGuard.secondPassGuard, "Good master must block broad second-pass edits");
        System.out.printf(Locale.US,"SOURCE_VERIFIED lufs=%.8f tp=%.8f frames=%d noRender=%s%n", a.integratedLufs,a.truePeakEstimateDbtp,a.wav.frames,a.noRenderNeeded);
        a.sections = LearnedSectionMapper.analyze(source, a, QUIET);
        a.aiDiagnostics = AdaptiveDiagnosticModel.analyze(source, a, QUIET);
        a.clickDropoutRepair = ClickDropoutRepair.analyze(source, a, QUIET);
        require(!a.clickDropoutRepair.hasRepairs(), "This source must not acquire speculative repairs");
        a.nextGenerationIntelligence = NextGenerationMasteringModel.analyze(source,a,null,null,null,QUIET);
        System.out.println("SOURCE_DIAGNOSTICS_COMPLETE");
        AutonomousMasteringBrain.Result brain = AutonomousMasteringBrain.analyze(source,a,QUIET);
        FullAiMasteringEngine.Result engine = FullAiMasteringEngine.run(source,a,brain,work,QUIET);
        require(engine.selected == engine.transparent, "Transparent winner must survive");
        MasterRenderer.Result m = engine.selected.render.master;
        require(m.renderPasses == 0 && !m.limiterUsed && m.gainDb == 0 && !m.exactTargetReached, "Preservation must not masquerade as exact-target rendering");
        require(MasterRenderer.isCompliantSourcePreserved(a,m.verification,m), "Independent preservation verification");
        require(a.sourceSha256.equals(Hashing.sha256(engine.selected.file)), "Winner altered original bytes");
        System.out.println("COMPLIANT_MASTER_ENGINE_PASS winner="+engine.selected.strategy);
        FullAiReleaseQc.Result qc = FullAiReleaseQc.run(source,a,engine,work,"LastRunDownTheRange",QUIET);
        require("COMPLIANT_SOURCE_PRESERVED".equals(qc.loudnessVerdict), "Honest loudness result");
        require("CONDITIONAL".equals(qc.technicalVerdict), "Host must not claim unavailable Android codec approval");
        require(qc.aac == null || !qc.aac.available, "Host codec claim");
        require(a.sourceSha256.equals(qc.finalMasterSha256), "QC altered original master");
        int mono = 0, endings = 0;
        for (RadioPreparation.Issue i : qc.radioPreparation.issues) {
            require(i.presentInSource, "Unchanged source issue incorrectly blamed on mastering");
            if (i.kind.equals("MONO_SECTION")) mono++;
            if (i.kind.equals("ENDING_TRANSIENT")) {
                endings++;
                require(i.startSeconds > 269.6 && i.endSeconds <= 269.734, "Ending timestamp");
            }
        }
        require(mono > 0 && endings == 1, "Known source mono and ending issues must be exposed");
        FullAiPackageBuilder.Result pack = FullAiPackageBuilder.build(a,brain,engine,qc,work,"LastRunDownTheRange",QUIET);
        require(qc.releaseFormats != null && qc.releaseFormats.complete(), "Release formats incomplete: "+(qc.releaseFormats==null?"missing":qc.releaseFormats.error));
        JSONObject report = new JSONObject(Files.readString(pack.report.toPath(),StandardCharsets.UTF_8));
        require("0.12.0".equals(report.getString("version")) && report.has("radio_preparation"), "New QC report schema");
        String prep = Files.readString(pack.preparationReport.toPath(),StandardCharsets.UTF_8);
        require(prep.contains("Brief late transient") && prep.contains("mono RMS change"), "Full preparation report omitted findings");
        try (ZipFile zip = new ZipFile(pack.zip)) {
            require(zip.getEntry(pack.preparationReport.getName()) != null, "Preparation report missing from package");
        }
        System.out.printf(Locale.US,"REAL_FILE_RECOVERY_PASS SHA256=%s LUFS=%.8f TP=%.8f monoWindows=%d endingFindings=%d package=%s%n",qc.finalMasterSha256,qc.finalMasterVerification.integratedLufs,qc.finalMasterVerification.truePeakEstimateDbtp,mono,endings,pack.zip.getName());
        flatGainRegression(input,a,work);
    }
    static void flatGainRegression(File input, AnalysisResult original, File work) throws Exception {
        File lowered = new File(work,"minus-one-db.wav");
        try (WavFile.Reader r = WavFile.open(new FileInputStream(input)); OutputStream out = new BufferedOutputStream(new FileOutputStream(lowered),262144)) {
            MasterRenderer.writeHeader(out,48000,2,original.wav.frames*6,24);
            float[][] x = new float[2][4096]; int n; double g = Math.pow(10,-1.0/20);
            while ((n=r.readFrames(x,4096))>0) for(int i=0;i<n;i++) for(int c=0;c<2;c++) {
                int q=(int)Math.round(x[c][i]*g*8388608.0); out.write(q); out.write(q>>8); out.write(q>>16);
            }
        }
        AnalysisResult a = AudioAnalyzer.analyze(lowered.getName(),()->new FileInputStream(lowered),QUIET);
        double predicted = a.truePeakEstimateDbtp + a.appliedSafeGainDb;
        require(!a.noRenderNeeded && !a.limiterRequired && predicted > -1.1 && predicted < -1.0, "Regression must exercise the former flat-gain gap");
        MasterRenderer.Result m = MasterRenderer.render(()->new FileInputStream(lowered),a,a.appliedSafeGainDb,new File(work,"flat-normalized.wav"),QUIET);
        require(!m.limiterUsed && m.exactTargetReached && m.verification.truePeakEstimateDbtp <= -1.0 && m.verification.wav.frames == a.wav.frames, "Safe flat gain was rejected or unnecessarily limited");
        for (double ceiling : new double[]{-1.0,-1.2}) {
            double gain = ceiling == -1.0 ? a.appliedSafeGainDb+.2 : a.appliedSafeGainDb;
            File rejected = new File(work,"unsafe.wav");
            try { MasterRenderer.renderWithCeiling(()->new FileInputStream(lowered),a,gain,rejected,ceiling,false,QUIET); throw new AssertionError("Unsafe gain accepted"); }
            catch (IOException expected) { require(expected.getMessage().contains("above the") && !rejected.exists(), "Unsafe gain must identify actual ceiling and leave no output"); }
        }
        System.out.printf(Locale.US,"FLAT_GAIN_GAP_FIXED predicted=%.8f output=%.8f TP=%.8f unsafeAndStricterCeilingRejected=true%n",predicted,m.verification.integratedLufs,m.verification.truePeakEstimateDbtp);
    }
}
