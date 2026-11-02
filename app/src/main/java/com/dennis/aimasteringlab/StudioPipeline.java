package com.dennis.aimasteringlab;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import org.json.*;

/** Restartable at verified base-master and finished-export boundaries. */
final class StudioPipeline {
    static void master(StudioProject p,AudioAnalyzer.Progress progress)throws Exception{
        StudioPerformance perf=new StudioPerformance();
        File input=StudioImports.requireSource(p,rangeProgress(progress,"Import verification",0,2));
        if(p.validFile("base","base_hash")){progress.update("Resuming from verified base master",100);return;}
        p.state("MASTERING","Building verified base master");
        File work=p.child("base-work-"+System.currentTimeMillis());if(!work.mkdirs())throw new IOException("Cannot create mastering workspace");
        AudioAnalyzer.StreamProvider raw=()->new FileInputStream(input);
        AnalysisResult original=StudioAudio.measure(input,rangeProgress(progress,"Source analysis",2,10));
        perf.checkpoint("source_import_and_analysis_ms");
        original.sourceGuard=SourceGuardModel.analyze(raw,original,rangeProgress(progress,"Source guard",10,14));
        perf.checkpoint("source_guard_ms");
        // v0.5.2: one shared mastering path (also used by Classic mastering).
        final AudioAnalyzer.Progress coreProgress=rangeProgress(progress,"Mastering",14,92);
        MasteringCore.Result core=MasteringCore.master(raw,original,work,new MasteringCore.Options(),
                (step,start,end)->rangeProgress(coreProgress,step,start,end));
        AudioAnalyzer.StreamProvider processing=core.processing;
        AnalysisResult source=core.analysis;
        AutonomousMasteringBrain.Result brain=core.brain;
        FullAiMasteringEngine.Result engine=core.engine;
        // Broad-mastering decisions belong to the tone stage (a repair stage re-measures
        // the tone master and would report its own, misleading, "unresolved" verdict).
        FullAiMasteringEngine.Result tone=core.toneEngine!=null?core.toneEngine:engine;
        perf.checkpoint("analysis_repairs_and_candidate_competition_ms");
        FullAiReleaseQc.Result qc=FullAiReleaseQc.runStudioPcmOnly(processing,source,engine,work,ReleaseFormats.camelBase(p.name()),rangeProgress(progress,"Final PCM QC",92,100));
        perf.checkpoint("final_candidate_qc_ms");
        File base=p.child("base-master.wav");StudioProject.copy(new FileInputStream(qc.finalMaster),base);
        if(!Hashing.sha256(base).equals(qc.finalMasterSha256))throw new IOException("Base master copy did not verify");
        File report=p.child("Base_Master_QC.json");StudioProject.atomic(report,FullAiReportWriter.create(source,brain,engine,qc).getBytes(StandardCharsets.UTF_8));
        p.file("base",base);p.file("base_report",report);p.data.put("base_hash",qc.finalMasterSha256)
                .put("base_meters",StudioAudio.meters(qc.finalMasterVerification)).put("base_winner",qc.finalCandidate.strategy)
                .put("base_chain",new JSONArray(core.chain)).put("base_repairs",new JSONArray(core.repairs))
                .put("base_stage_ms",new JSONObject(core.stageMs))
                .put("base_tone_winner",tone.selected==null?"":tone.selected.strategy)
                .put("base_tone_decision",tone.meaningfulChangeDecision==null?"":tone.meaningfulChangeDecision)
                .put("base_meaningful_change",engine.selected!=engine.transparent)
                .put("base_mastering_decision",engine.meaningfulChangeDecision)
                .put("base_final_gate_candidate",engine.meaningfulChangeComparedStrategy==null?"":engine.meaningfulChangeComparedStrategy)
                .put("base_broad_mastering_required",tone.broadMasteringNeed!=null&&tone.broadMasteringNeed.required)
                .put("base_broad_mastering_need",tone.broadMasteringNeed==null?"":tone.broadMasteringNeed.summary())
                .put("base_broad_mastering_decision",tone.broadMasteringDecision)
                .put("base_broad_mastering_anchor",tone.broadMasteringAnchor==null?"":tone.broadMasteringAnchor.strategy)
                .put("base_broad_mastering_anchor_used",tone.broadMasteringAnchorUsed)
                .put("base_broad_mastering_unresolved",tone.broadMasteringUnresolved)
                .put("base_early_sparse_peak_shortcut",tone.earlySparsePeakShortcut)
                .put("base_early_sparse_peak_decision",tone.earlySparsePeakDecision)
                .put("base_adaptive_broad_attempted",tone.adaptiveBroadCandidatesAttempted)
                .put("base_adaptive_broad_eligible",tone.adaptiveBroadCandidatesEligible)
                .put("base_adaptive_broad_decision",tone.adaptiveBroadDecision)
                .put("base_adaptive_broad_diagnostics",new JSONArray(tone.adaptiveBroadDiagnostics))
                .put("base_adaptive_broad_anchor",tone.adaptiveBroadAnchor==null?"":tone.adaptiveBroadAnchor.strategy);
        for (FullAiMasteringEngine.Candidate c : engine.candidates) {
            if (!c.sparsePeakAware) continue;
            p.data.put("sparse_peak_audition_status",c.status==null?"NOT_RUN":c.status)
                    .put("sparse_peak_audition_eligible",c.eligible && !c.promotionBlocked)
                    .put("sparse_peak_audition_selected",c==engine.selected);
            if (c.render!=null) p.data.put("sparse_peak_audition_limiter_max_db",c.render.master.maximumLimiterReductionDb)
                    .put("sparse_peak_audition_limiter_mean_db",c.render.master.averageLimiterReductionDb)
                    .put("sparse_peak_audition_limiter_above_1db_percent",c.render.master.limiterFramesAboveOneDbPercent)
                    .put("sparse_peak_audition_limiter_above_3db_percent",c.render.master.limiterFramesAboveThreeDbPercent);
            if(c.passageCheck!=null) p.data.put("sparse_peak_audition_passage",c.passageCheck.summary())
                    .put("sparse_peak_audition_reviewed_windows",c.passageCheck.sparsePeakWindowsReviewed);
            break;
        }
        if (engine.transparent != null && engine.transparent.render != null) {
            MasterRenderer.Result transparentMaster = engine.transparent.render.master;
            p.data.put("transparent_peak_safety_retries",transparentMaster.peakSafetyRecoveryPasses)
                    .put("transparent_peak_safety_attenuation_db",transparentMaster.peakSafetyRecoveryAttenuationDb)
                    .put("transparent_verified_target_shortfall_lu",transparentMaster.targetShortfallLu);
        }

        if(engine.selectedVsTransparentDelta!=null){
            p.data.put("base_final_gate_delta_vs_transparent_db",engine.selectedVsTransparentDelta.levelMatchedResidualRelativeDb)
                    .put("base_final_gate_delta_verdict",engine.selectedVsTransparentDelta.verdict);
        }else{
            p.data.remove("base_final_gate_delta_vs_transparent_db");p.data.remove("base_final_gate_delta_verdict");
        }
        perf.checkpoint("base_copy_persistence_and_hash_ms");
        p.data.put("base_performance_ms",perf.json()).put("base_candidates_considered",engine.candidates.size());
        long rendered=0;for(FullAiMasteringEngine.Candidate c:engine.candidates)if(c.rendered)rendered++;
        int inactiveCoreEventFiltersBypassed = 0;
        for (FullAiMasteringEngine.Candidate c : engine.candidates) {
            if (c.rendered && c.render != null && c.render.bypassedInactiveEventFilters) {
                inactiveCoreEventFiltersBypassed++;
            }
        }
        int vocalAuditionProfiles = 0;
        int vocalSparseAuditions = 0;
        for (FullAiMasteringEngine.Candidate c : engine.candidates) {
            if (c.professionalMode == null
                    || !ProfessionalMasteringRenderer.isVocal(c.professionalMode)
                    || c.professionalIntelligence == null
                    || c.professionalIntelligence.limiterOptimisation == null) continue;
            LimiterCrestOptimizer.Result limiter = c.professionalIntelligence.limiterOptimisation;
            vocalAuditionProfiles += limiter.trials.size();
            if (limiter.sparseVocalEarlyStop) vocalSparseAuditions++;
        }
        p.data.put("core_no_event_filter_fast_paths",inactiveCoreEventFiltersBypassed)
                .put("base_candidates_rendered",rendered)
                .put("candidate_stage_ms",new JSONObject(engine.candidateStageMs))
                .put("professional_vocal_limiter_profiles_rendered",vocalAuditionProfiles)
                .put("professional_vocal_sparse_exits",vocalSparseAuditions);
        p.state("BASE_READY","Verified base master saved; finishing delivery next");
        removeTree(work);
    }
    static void finish(StudioProject p,AudioAnalyzer.Progress progress)throws Exception{
        if(!p.validFile("base","base_hash"))throw new IOException("Verified base master is unavailable");
        StudioPerformance perf=new StudioPerformance();
        p.state("RENDERING","Applying saved edits and verifying delivery");
        File base=p.file("base");AnalysisResult before=StudioAudio.measure(base,rangeProgress(progress,"Delivery source verification",0,8));StudioRecipe recipe=p.recipe();recipe.validate(before.wav.durationSeconds);
        File run=p.child("render-"+System.currentTimeMillis());if(!run.mkdirs())throw new IOException("Cannot create render workspace");
        StudioLowEnd.Scan beforeLow=null;
        if(recipe.lowEnd&&!p.data.optBoolean("bass_scanned",false)){
            beforeLow=StudioLowEnd.scan(base,rangeProgress(progress,"Low-end scan",8,15));
            recipe.edits.addAll(StudioLowEnd.propose(beforeLow));p.data.put("bass_scanned",true);p.recipe(recipe);
        }
        for(StudioRecipe.Edit e:recipe.edits)if(e.automatic&&!recipe.lowEnd)e.enabled=false;
        boolean hasAutomatic=false;for(StudioRecipe.Edit e:recipe.edits)hasAutomatic|=e.enabled&&e.automatic;
        JSONObject autoQc=new JSONObject().put("passed",true).put("events_checked",0);
        File premaster=new File(run,"edited-float.wav");
        perf.checkpoint("base_verification_and_low_end_analysis_ms");
        boolean editorPassthrough=StudioAudio.studioPassthroughSafe(base,before,recipe);
        StudioAudio.EditResult edited;
        if(editorPassthrough){
            edited=new StudioAudio.EditResult();edited.file=base;edited.analysis=before;
            progress.update("Unchanged edit chain • verified base reused without an extra render/analysis",32);
        }else{
            edited=StudioAudio.edit(base,before,recipe,premaster,rangeProgress(progress,"Applying finishing edits",15,32));
        }
        if(hasAutomatic){
            if(beforeLow==null)beforeLow=StudioLowEnd.scan(base,rangeProgress(progress,"Low-end reference scan",8,15));
            autoQc=StudioLowEnd.verify(beforeLow,StudioLowEnd.scan(premaster,rangeProgress(progress,"Low-end edit verification",32,39)),recipe.edits,recipe.trimStart);
            if(!autoQc.optBoolean("passed",false)){
                for(StudioRecipe.Edit e:recipe.edits)if(e.automatic)e.enabled=false;
                autoQc.put("decision","Automatic bass edits rejected; manual edits retained");p.recipe(recipe);
                edited=StudioAudio.edit(base,before,recipe,premaster,rangeProgress(progress,"Re-rendering without rejected automatic edits",39,46));
            }else autoQc.put("decision","Automatic bass edits passed measured local benefit checks");
        }
        perf.checkpoint("studio_edit_processing_ms");
        String name=ReleaseFormats.camelBase(recipe.title);
        File master=new File(run,name+"_Master_24bit-48kHz.wav");
        boolean unchanged=recipe.trimStart==0&&recipe.trimEnd==0&&edited.fadeIn==0&&edited.fadeOut==0;
        for(StudioRecipe.Edit e:recipe.edits)if(e.enabled&&Math.abs(e.db)>1e-9)unchanged=false;
        StudioAudio.Delivery delivered=StudioAudio.deliver(unchanged?base:premaster,unchanged?before:edited.analysis,recipe,master,rangeProgress(progress,"24-bit WAV delivery",46,62));
        StudioMetadata.wav(master,recipe);delivered.analysis=StudioAudio.measure(master,rangeProgress(progress,"24-bit WAV verification",62,70));
        StudioAudio.valid(delivered.analysis,edited.analysis.wav.frames);
        perf.checkpoint("wav24_delivery_and_validation_ms");
        ReleaseFormats.Result formats=ReleaseFormats.createWavOnly(master,delivered.analysis,run,recipe.title,rangeProgress(progress,"16-bit WAV delivery",70,84),recipe);
        if(formats.complete()){
            StudioMetadata.wav(formats.wav16,recipe);
            AnalysisResult tagged=StudioAudio.measure(formats.wav16,rangeProgress(progress,"16-bit WAV verification",84,90));
            if(tagged.wav.frames!=formats.wav16Qc.wav.frames||Math.abs(tagged.integratedLufs-formats.wav16Qc.integratedLufs)>.00001)throw new IOException("WAV metadata changed audio");
            formats.wav16Qc=tagged;
        }
        perf.checkpoint("wav16_resampling_and_validation_ms");
        File original=p.file("source"),aligned=new File(run,"source-aligned.wav");
        StudioAudio.region(original,aligned,recipe.trimStart,delivered.analysis.wav.durationSeconds,rangeProgress(progress,"Preparing A-B reference",90,94));
        AnalysisResult alignedAnalysis=StudioAudio.measure(aligned,rangeProgress(progress,"A-B reference verification",94,96));
        perf.checkpoint("ab_reference_preparation_ms");
        JSONObject radio=new JSONObject().put("status","NOT_RUN_WAV_ONLY")
                .put("reason","Studio v" + StudioQcReporting.APP_VERSION + " publishes two PCM WAV files; broadcaster/device simulation is not part of this delivery path")
                .put("listening_checks",new JSONArray());
        JSONObject report=StudioQcReporting.masteringFlags(
                p.data.optBoolean("base_meaningful_change",false),unchanged)
                .put("app_version",StudioQcReporting.APP_VERSION)
                .put("project_id",p.dir.getName())
                .put("source_sha256",p.data.getString("source_hash")).put("base_sha256",p.data.getString("base_hash"))
                .put("source_recovery_notice",p.data.optString("source_recovery_notice",""))
                .put("recipe",recipe.json()).put("base_winner",p.data.optString("base_winner"))
                .put("base_chain",p.data.optJSONArray("base_chain")==null?new JSONArray():p.data.optJSONArray("base_chain"))
                .put("base_repairs",p.data.optJSONArray("base_repairs")==null?new JSONArray():p.data.optJSONArray("base_repairs"))
                .put("base_tone_winner",p.data.optString("base_tone_winner",""))
                .put("base_tone_decision",p.data.optString("base_tone_decision",""))
                .put("base_stage_ms",p.data.optJSONObject("base_stage_ms")==null?new JSONObject():p.data.optJSONObject("base_stage_ms"))
                .put("base_meaningful_change",p.data.optBoolean("base_meaningful_change",false))
                .put("base_mastering_decision",p.data.optString("base_mastering_decision",""))
                .put("base_final_gate_candidate",p.data.optString("base_final_gate_candidate",""))
                .put("base_final_gate_delta_vs_transparent_db",p.data.has("base_final_gate_delta_vs_transparent_db")?p.data.optDouble("base_final_gate_delta_vs_transparent_db"):JSONObject.NULL)
                .put("base_final_gate_delta_verdict",p.data.has("base_final_gate_delta_verdict")?p.data.optString("base_final_gate_delta_verdict"):JSONObject.NULL)
                .put("base_broad_mastering_required",p.data.optBoolean("base_broad_mastering_required",false))
                .put("base_broad_mastering_need",p.data.optString("base_broad_mastering_need",""))
                .put("base_broad_mastering_decision",p.data.optString("base_broad_mastering_decision",""))
                .put("base_broad_mastering_anchor",p.data.optString("base_broad_mastering_anchor",""))
                .put("base_broad_mastering_anchor_used",p.data.optBoolean("base_broad_mastering_anchor_used",false))
                .put("base_broad_mastering_unresolved",p.data.optBoolean("base_broad_mastering_unresolved",false))
                .put("base_early_sparse_peak_shortcut",p.data.optBoolean("base_early_sparse_peak_shortcut",false))
                .put("base_early_sparse_peak_decision",p.data.optString("base_early_sparse_peak_decision","Not attempted"))
                .put("base_adaptive_broad_attempted",p.data.optInt("base_adaptive_broad_attempted",0))
                .put("base_adaptive_broad_eligible",p.data.optInt("base_adaptive_broad_eligible",0))
                .put("base_adaptive_broad_decision",p.data.optString("base_adaptive_broad_decision",""))
                .put("base_adaptive_broad_diagnostics",p.data.optJSONArray("base_adaptive_broad_diagnostics")==null?new JSONArray():p.data.optJSONArray("base_adaptive_broad_diagnostics"))
                .put("base_adaptive_broad_anchor",p.data.optString("base_adaptive_broad_anchor",""))
                .put("transparent_peak_safety_retries",p.data.optInt("transparent_peak_safety_retries",0))
                .put("transparent_peak_safety_attenuation_db",p.data.optDouble("transparent_peak_safety_attenuation_db",0))
                .put("transparent_verified_target_shortfall_lu",p.data.optDouble("transparent_verified_target_shortfall_lu",0))
                .put("master",StudioAudio.meters(delivered.analysis))
                .put("delivery_note",delivered.note).put("target_lufs",recipe.target).put("peak_ceiling_dbtp",recipe.ceiling)
                .put("fade_in_seconds",edited.fadeIn).put("fade_out_seconds",edited.fadeOut)
                .put("boundary_in_repair",edited.boundaryIn).put("boundary_out_repair",edited.boundaryOut)
                .put("bass_verification",autoQc).put("release_formats",ReleaseFormats.json(formats))
                .put("radio_preparation",radio.put("target_lufs_i",recipe.target).put("true_peak_ceiling_dbtp",recipe.ceiling))
                .put("verification_scope","Final 24-bit/48 kHz and 16-bit/44.1 kHz PCM measured; base mastering QC is separate. Codec, device-translation and broadcaster-specific simulations are not run in Studio WAV-only delivery.");
        if(delivered.render!=null)report.put("limiter_max_reduction_db",delivered.render.maximumLimiterReductionDb)
                .put("limiter_mean_reduction_db",delivered.render.averageLimiterReductionDb).put("limiter_used",delivered.render.limiterUsed)
                .put("limiter_peak_corrections",delivered.limiterPeakCorrections);
        report.put("loud_master",delivered.loud).put("glue_mean_reduction_db",delivered.glueMeanReductionDb)
                .put("glue_max_reduction_db",delivered.glueMaxReductionDb);
        File qc=new File(run,name+"_Studio_QC.json"),recipeFile=new File(run,name+"_Recipe.json");
        StudioProject.atomic(recipeFile,recipe.json().toString(2).getBytes(StandardCharsets.UTF_8));
        // Publish only the two requested release WAVs; QC/recipe data remain inside the project for diagnostics and recovery.
        p.file("master",master);p.file("report",qc);p.file("aligned_source",aligned);
        p.data.remove("package");p.data.remove("package_hash");p.data.remove("mp3");
        p.data.put("aligned_meters",StudioAudio.meters(alignedAnalysis)).put("master_hash",Hashing.sha256(master))
                .put("master_meters",StudioAudio.meters(delivered.analysis))
                .put("final_profile",StudioReferences.profile(master,rangeProgress(progress,"Album/reference profile",96,100))).put("last_recipe",recipe.json())
                .put("rendered_revision",p.data.optInt("revision",0)).put("final_qc",report);
        perf.checkpoint("album_profile_and_project_metadata_ms");
        report.put("performance",new JSONObject()
                .put("base",p.data.optJSONObject("base_performance_ms"))
                .put("delivery",perf.json())
                .put("candidates_considered",p.data.optLong("base_candidates_considered",-1))
                .put("candidates_rendered",p.data.optLong("base_candidates_rendered",-1))
                .put("candidate_stage_ms",p.data.optJSONObject("candidate_stage_ms"))
                .put("professional_vocal_limiter_profiles_rendered",p.data.optInt("professional_vocal_limiter_profiles_rendered",0))
                .put("core_no_event_filter_fast_paths",p.data.optInt("core_no_event_filter_fast_paths",0))
                .put("professional_vocal_sparse_exits",p.data.optInt("professional_vocal_sparse_exits",0))
                .put("timing_note","Monotonic wall-clock milliseconds; resumed base and delivery stages tracked separately"));
        p.data.put("final_qc",report);
        StudioProject.atomic(qc,report.toString(2).getBytes(StandardCharsets.UTF_8));
        if(formats.complete()){p.file("wav16",formats.wav16);}else{p.data.remove("wav16");}
        String masteringSummary=p.data.optBoolean("base_meaningful_change",false)
                ? "Sonic mastering change verified"
                : "Transparent mastering control retained; no justified sonic change";
        p.state(formats.complete()?"COMPLETE":"EXPORT_REVIEW",masteringSummary+" • "+delivered.note+(formats.complete()?"":"; "+formats.summary()));
        progress.update("Studio WAV delivery complete",100);
        WavRenderIntegrity.discard(premaster);
        if(!p.validFile("source","source_hash")||!p.validFile("base","base_hash"))throw new IOException("Retained source/base integrity changed");
        // v0.5.4: a re-render (new loudness, edits) replaces the delivered files; earlier
        // render folders are no longer referenced, so they are removed to save storage.
        File[] older=p.dir.listFiles((d,n)->n.startsWith("render-"));
        if(older!=null)for(File f:older)if(f.isDirectory()&&!f.getCanonicalPath().equals(run.getCanonicalPath()))removeTree(f);
    }
    private static AudioAnalyzer.Progress rangeProgress(AudioAnalyzer.Progress parent,String prefix,int start,int end){
        final int lo=Math.max(0,Math.min(100,start)),hi=Math.max(lo,Math.min(100,end));
        final int[] last={lo};
        return (stage,percent)->{
            int p=Math.max(0,Math.min(100,percent));
            int mapped=lo+(int)Math.round((hi-lo)*(p/100.0));
            if(mapped<last[0])mapped=last[0];else last[0]=mapped;
            parent.update(prefix+" • "+stage,mapped);
        };
    }

    static void packageFiles(File output,List<File> files,AudioAnalyzer.Progress progress)throws IOException{
        File stage=new File(output.getPath()+".tmp");StringBuilder hashes=new StringBuilder();HashSet<String> names=new HashSet<>();
        try(ZipOutputStream z=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(stage),262144))){
            z.setLevel(1);byte[] b=new byte[262144];int i=0;
            for(File f:files){if(f==null||!f.isFile())throw new IOException("Package input missing");if(!names.add(f.getName()))throw new IOException("Duplicate package filename "+f.getName());
                hashes.append(Hashing.sha256(f)).append("  ").append(f.getName()).append('\n');z.putNextEntry(new ZipEntry(f.getName()));
                try(InputStream in=new FileInputStream(f)){int n;while((n=in.read(b))!=-1){z.write(b,0,n);StudioAudio.cancel();}}z.closeEntry();
                progress.update("Packaging verified files",++i*100/files.size());
            }z.putNextEntry(new ZipEntry("SHA256.txt"));z.write(hashes.toString().getBytes(StandardCharsets.UTF_8));z.closeEntry();
        }
        try(ZipFile z=new ZipFile(stage)){byte[] b=new byte[262144];for(File f:files)try(InputStream in=z.getInputStream(z.getEntry(f.getName()))){
            java.security.MessageDigest digest;try{digest=java.security.MessageDigest.getInstance("SHA-256");}catch(Exception e){throw new IOException(e);}
            int n;while((n=in.read(b))!=-1){digest.update(b,0,n);StudioAudio.cancel();}StringBuilder h=new StringBuilder();for(byte v:digest.digest())h.append(String.format(java.util.Locale.US,"%02x",v&255));
            if(!h.toString().equals(Hashing.sha256(f)))throw new IOException("Package checksum mismatch");
        }}
        java.nio.file.Files.move(stage.toPath(),output.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
    static void removeTree(File file){if(file.isDirectory()){File[] c=file.listFiles();if(c!=null)for(File f:c)removeTree(f);}file.delete();}
}
