package com.dennis.aimasteringlab;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

final class ReportWriter {
    private ReportWriter() {}

    static String create(AnalysisResult source, MasterRenderer.Result render) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("schema", "dennis.mastering.analysis.v0.4");
        root.put("app", "Dennis Mastering Studio 0.4.0");
        root.put("generated_utc", timestamp());
        root.put("source", sourceObject(source));
        root.put("measurements", measurements(source));

        JSONObject recommendation = new JSONObject();
        recommendation.put("target_lufs_i", -14.0);
        recommendation.put("true_peak_ceiling_dbtp", -1.0);
        recommendation.put("limiter_working_ceiling_dbtp", -1.2);
        recommendation.put("flat_gain_to_target_db", round(source.targetGainDb, 4));
        recommendation.put("safe_flat_gain_db", round(source.appliedSafeGainDb, 4));
        recommendation.put("predicted_lufs_i", round(source.predictedOutputLufs, 3));
        recommendation.put("predicted_true_peak_estimate_dbtp",
                round(source.predictedOutputTruePeakDbtp, 3));
        recommendation.put("limiter_required_for_exact_target", source.limiterRequired);
        recommendation.put("true_peak_limiter_required", source.limiterRequired);
        recommendation.put("limiter_allowed_by_dynamics_gate", source.limiterAllowed);
        recommendation.put("estimated_limiter_reduction_db", round(source.estimatedLimiterReductionDb, 4));
        recommendation.put("derived_limiter_reduction_allowance_db",
                round(source.limiterReductionAllowanceDb, 4));
        recommendation.put("already_at_target_no_render", source.alreadyAtTargetNoRender);
        recommendation.put("already_compliant_no_render", source.alreadyCompliantNoRender);
        recommendation.put("no_change_tolerance_lu", AudioAnalyzer.COMPLIANT_MASTER_TOLERANCE_LU);
        recommendation.put("no_render_needed", source.noRenderNeeded);
        recommendation.put("no_render_reason", source.noRenderReason);
        recommendation.put("action", source.alreadyAtTargetNoRender
                ? "ALREADY_AT_TARGET_NO_RENDER_NEEDED"
                : source.alreadyCompliantNoRender
                ? "COMPLIANT_SOURCE_NEAR_TARGET_NO_RENDER_NEEDED"
                : source.noRenderNeeded
                ? "DYNAMICS_PROTECTED_SOURCE_NO_RENDER_NEEDED"
                : source.limiterRequired && source.limiterAllowed
                ? "GUARDED_LOOKAHEAD_LIMITER_AVAILABLE"
                : source.ceilingLimited
                ? "SAFE_GAIN_ONLY_BELOW_TARGET; DO NOT CLAIM FINAL RELEASE MASTER"
                : "FLAT_GAIN_RENDER_IS_TECHNICALLY AVAILABLE");
        root.put("recommendation", recommendation);

        JSONArray stems = new JSONArray();
        for (StemInventory.Item item : source.stems) stems.put(stemObject(item));
        root.put("stem_inventory", stems);
        root.put("stem_comparison", stemComparison(source));
        root.put("stem_reconstruction", reconstruction(source.stemReconstruction));
        root.put("section_candidates", sections(source.sections));
        root.put("learned_section_map", learnedSectionMap(source.sections));
        root.put("ai_diagnostic", aiDiagnostic(source.aiDiagnostics));
        root.put("vocal_humanity", vocalHumanity(source.vocalHumanity));

        JSONObject planSchema = new JSONObject();
        planSchema.put("schema", "dennis.mastering.plan.v0.1");
        planSchema.put("source_sha256", source.sourceSha256);
        planSchema.put("gain_db", round(source.appliedSafeGainDb, 4));
        planSchema.put("note", "Imported plans remain checksum-bound flat-gain instructions; AI-guided test rendering derives a new post-correction gain internally");
        root.put("importable_plan_example", planSchema);

        if (render != null) root.put("render_verification", renderObject(render));

        JSONObject gate = new JSONObject();
        boolean technicalPass = source.nonFiniteSamples == 0
                && source.wav.channels == 2
                && source.wav.frames > 0;
        gate.put("technical_intake", technicalPass ? "PASS" : "FAIL");
        gate.put("release_master", render == null
                ? source.noRenderNeeded
                ? source.alreadyCompliantNoRender
                ? "SOURCE ALREADY COMPLIANT; NO RENDER"
                : "DYNAMICS-PROTECTED SOURCE RETAINED; NO RENDER"
                : "NOT RENDERED"
                : render.verification.truePeakEstimateDbtp <= -1.0
                && render.quantizerClips == 0 ? "PROVISIONAL PASS" : "FAIL");
        gate.put("stem_alignment_readiness", source.wavStemCount == 0
                ? "NOT SUPPLIED"
                : source.stemDurationMismatchCount == 0
                ? "DURATION MATCH"
                : source.commonStemTailShortfallSeconds > 0.0
                ? "REVIEW: PRESERVE ORIGINAL-ONLY TAIL"
                : "REVIEW: DURATION MISMATCH");
        gate.put("production_ar", "ADVISORY GATE CREATED DURING COMPLETE RELEASE PACKAGING");
        gate.put("ai_correction", source.aiDiagnostics == null
                ? "NOT RUN"
                : source.aiDiagnostics.hasAutoCorrections()
                ? "OPTIONAL A/B TEST AVAILABLE; NOT AUTOMATICALLY PROMOTED"
                : "DIAGNOSTIC ONLY; NO EVENT PASSED THE CORRECTION GATE");
        gate.put("vocal_humanity_edit", source.vocalHumanity == null
                ? "NOT AVAILABLE"
                : source.vocalHumanity.hasCorrections()
                ? "OPTIONAL STEM-DELTA A/B TEST AVAILABLE; NOT AUTOMATICALLY PROMOTED"
                : source.vocalHumanity.status);
        gate.put("major_label_claim", "NOT PERMITTED FROM AUTOMATED ANALYSIS ALONE");
        root.put("formal_gate", gate);

        JSONArray limits = new JSONArray();
        limits.put("True peak uses a regression-validated 4x polyphase windowed-sinc meter but has not received external laboratory certification.");
        limits.put("Playback profiles are simulations, not claims of listening through physical devices.");
        limits.put("Learned arrangement states and musical-role labels remain unsupervised candidates requiring perceptual confirmation.");
        limits.put("Codec availability depends on encoders installed on the Android device.");
        limits.put("16-bit/44.1 kHz WAV and FLAC remain intentionally omitted from the Dennis delivery profile.");
        limits.put("The v0.4 AI models are per-track unsupervised/adaptive models, not perceptual guarantees or generative mastering models.");
        limits.put("The optional mix test remains attenuation-only. The optional vocal test uses capped phrase rides and event de-essing only; it never autotunes, denoises, gates or regenerates the singer.");
        limits.put("A stem-delta test changes only processed-minus-original lead content around the authoritative stereo mix and requires listening before release use.");
        root.put("v0_4_limitations", limits);
        return root.toString(2);
    }

    private static JSONObject sourceObject(AnalysisResult r) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("name", r.sourceName);
        o.put("sha256", r.sourceSha256);
        o.put("format", r.wav.formatName());
        o.put("sample_rate_hz", r.wav.sampleRate);
        o.put("bits_per_sample", r.wav.bitsPerSample);
        o.put("valid_bits_per_sample", r.wav.validBitsPerSample);
        o.put("channels", r.wav.channels);
        o.put("frames", r.wav.frames);
        o.put("duration_seconds", round(r.wav.durationSeconds, 6));
        o.put("audio_data_bytes", r.wav.dataBytes);
        return o;
    }

    private static JSONObject measurements(AnalysisResult r) throws JSONException {
        JSONObject o = new JSONObject();
        putNumber(o, "integrated_lufs_i", r.integratedLufs, 3);
        putNumber(o, "lra_lu", r.lraLu, 3);
        putNumber(o, "sample_peak_dbfs", r.samplePeakDbfs, 3);
        putNumber(o, "true_peak_estimate_dbtp", r.truePeakEstimateDbtp, 3);
        putNumber(o, "rms_dbfs", r.rmsDbfs, 3);
        putNumber(o, "stereo_correlation", r.stereoCorrelation, 6);
        putNumber(o, "below_160hz_correlation", r.lowBandCorrelation, 6);
        putNumber(o, "mono_fold_rms_dbfs", r.monoFoldRmsDbfs, 3);
        putNumber(o, "mono_fold_peak_dbfs", r.monoFoldPeakDbfs, 3);
        putNumber(o, "mono_fold_level_change_db", r.monoFoldLossDb, 3);
        o.put("clipped_samples", r.clippedSamples);
        o.put("non_finite_samples", r.nonFiniteSamples);
        o.put("internal_zero_runs_100ms_or_longer", r.internalZeroRuns);
        o.put("longest_internal_zero_seconds", round(r.longestInternalZeroSeconds, 6));
        o.put("first_frame_peak_linear", round(r.firstFramePeak, 9));
        o.put("last_frame_peak_linear", round(r.lastFramePeak, 9));
        o.put("file_edge_assessment", r.firstFramePeak < 0.999 && r.lastFramePeak < 0.999
                ? "NO FULL-SCALE FILE-EDGE SAMPLE; PRESERVE ORIGINAL INTRO AND ENDING"
                : "REVIEW FULL-SCALE FILE EDGE");
        JSONArray dc = new JSONArray();
        for (double v : r.dcOffset) dc.put(round(v, 10));
        o.put("dc_offset_by_channel", dc);
        o.put("meter", r.meterVersion);
        return o;
    }

    private static JSONObject stemObject(StemInventory.Item item) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("path", item.path);
        o.put("directory", item.directory);
        o.put("wav", item.wav);
        o.put("uncompressed_bytes", item.uncompressedBytes);
        o.put("compressed_bytes", item.compressedBytes);
        o.put("crc32", item.crc32);
        o.put("crc_verified", item.crcVerified);
        if (item.wavInfo != null) {
            JSONObject w = new JSONObject();
            w.put("sample_rate_hz", item.wavInfo.sampleRate);
            w.put("bits_per_sample", item.wavInfo.bitsPerSample);
            w.put("channels", item.wavInfo.channels);
            w.put("duration_seconds", round(item.wavInfo.durationSeconds, 6));
            w.put("frames", item.wavInfo.frames);
            o.put("wav_info", w);
            putNumber(o, "full_duration_rms_dbfs", item.rmsDbfs, 3);
            putNumber(o, "track_relative_active_rms_dbfs", item.activeRmsDbfs, 3);
            putNumber(o, "track_relative_active_fraction", item.activeFraction, 6);
            putNumber(o, "peak_dbfs", item.peakDbfs, 3);
            putNumber(o, "dc_offset", item.dcOffset, 9);
            putNumber(o, "crest_factor_db", item.crestFactorDb, 3);
            o.put("clipped_samples", item.clippedSamples);
        }
        if (item.error != null) o.put("error", item.error);
        return o;
    }

    private static JSONObject reconstruction(StemReconstruction.Result r) throws JSONException {
        if (r == null) return null;
        JSONObject o = new JSONObject();
        o.put("attempted", r.attempted);
        o.put("compatible", r.compatible);
        o.put("verdict", r.verdict);
        o.put("stems_compared", r.stemsCompared);
        o.put("frames_compared", r.framesCompared);
        putNumber(o, "waveform_correlation", r.waveformCorrelation, 8);
        putNumber(o, "least_squares_gain", r.leastSquaresGain, 8);
        putNumber(o, "least_squares_gain_db", r.leastSquaresGainDb, 4);
        putNumber(o, "residual_snr_db", r.residualSnrDb, 3);
        putNumber(o, "start_lag_ms", r.startLagMs, 3);
        putNumber(o, "middle_lag_ms", r.middleLagMs, 3);
        putNumber(o, "end_lag_ms", r.endLagMs, 3);
        putNumber(o, "measured_drift_ms", r.measuredDriftMs, 3);
        o.put("global_polarity_risk", r.globalPolarityRisk);
        o.put("raw_stem_sum_allowed", r.rawStemSumAllowed);
        o.put("likely_separation_derived", r.likelySeparationDerived);
        o.put("mix_policy", r.mixPolicy);
        if (r.error != null) o.put("error", r.error);
        return o;
    }

    private static JSONArray sections(TrackSections sections) throws JSONException {
        JSONArray out = new JSONArray();
        if (sections == null) return out;
        for (TrackSections.Segment segment : sections.segments) {
            JSONObject o = new JSONObject();
            o.put("role", segment.role);
            putNumber(o, "start_seconds", segment.startSeconds, 3);
            putNumber(o, "duration_seconds", segment.durationSeconds, 3);
            putNumber(o, "average_lufs", segment.averageLufs, 3);
            putNumber(o, "confidence", segment.confidence, 4);
            o.put("learned_state", segment.learnedState >= 0
                    ? segment.learnedState : JSONObject.NULL);
            out.put(o);
        }
        return out;
    }

    private static JSONObject learnedSectionMap(TrackSections sections) throws JSONException {
        if (sections == null) return null;
        JSONObject out = new JSONObject();
        out.put("method", sections.method);
        out.put("learned_mapping", sections.learnedMapping);
        out.put("learned_states", sections.learnedStates);
        out.put("boundary_candidates", sections.boundaryCandidates);
        putNumber(out, "mapping_confidence", sections.mappingConfidence, 4);
        putNumber(out, "quiet_to_chorus_lift_lu", sections.quietToChorusLiftLu, 3);
        JSONArray structure = new JSONArray();
        for (TrackSections.Segment segment : sections.learnedStructure) {
            JSONObject item = new JSONObject();
            item.put("role", segment.role);
            item.put("learned_state", segment.learnedState >= 0
                    ? segment.learnedState : JSONObject.NULL);
            putNumber(item, "start_seconds", segment.startSeconds, 3);
            putNumber(item, "duration_seconds", segment.durationSeconds, 3);
            putNumber(item, "average_lufs", segment.averageLufs, 3);
            putNumber(item, "confidence", segment.confidence, 4);
            structure.put(item);
        }
        out.put("structure", structure);
        out.put("interpretation", "Candidate labels are learned from repetition, level, spectrum, width and transient density; listen to confirm verse/chorus/bridge names.");
        return out;
    }

    private static JSONObject stemComparison(AnalysisResult r) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("wav_stems", r.wavStemCount);
        o.put("crc_verified_wav_stems", r.crcVerifiedStemCount);
        o.put("duration_mismatch_count", r.stemDurationMismatchCount);
        putNumber(o, "shortest_stem_duration_seconds", r.shortestStemDurationSeconds, 6);
        putNumber(o, "longest_stem_duration_seconds", r.longestStemDurationSeconds, 6);
        o.put("largest_absolute_duration_delta_seconds",
                round(r.largestStemDurationDeltaSeconds, 6));
        o.put("common_original_only_tail_seconds",
                round(r.commonStemTailShortfallSeconds, 6));
        o.put("tail_policy", r.commonStemTailShortfallSeconds > 0.0
                ? "PRESERVE AUTHORITATIVE MIX TAIL; NEVER REPLACE WITH RAW STEM SUM"
                : "NO COMMON SHORTFALL DETECTED BY DURATION CHECK");
        return o;
    }

    private static JSONObject renderObject(MasterRenderer.Result render) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("file_name", render.savedFileName != null
                ? render.savedFileName : render.file.getName());
        o.put("sha256", render.verification.sourceSha256);
        o.put("applied_gain_db", round(render.gainDb, 4));
        o.put("limiter_used", render.limiterUsed);
        o.put("maximum_limiter_reduction_db", round(render.maximumLimiterReductionDb, 4));
        o.put("dynamics_protected", render.dynamicsProtected);
        o.put("exact_target_reached", render.exactTargetReached);
        o.put("compliant_source_preserved", render.compliantSourcePreserved);
        o.put("render_passes", render.renderPasses);
        o.put("ai_guided_test", render.aiGuided);
        if (render.aiGuided) {
            o.put("ai_correction_events", render.aiCorrectionEvents);
            o.put("maximum_ai_correction_db", round(render.maximumAiCorrectionDb, 4));
            o.put("ai_correction_summary", render.aiCorrectionSummary);
            o.put("corrected_premaster_lufs_i", round(render.aiCorrectedPremasterLufs, 3));
            o.put("corrected_premaster_true_peak_dbtp",
                    round(render.aiCorrectedPremasterTruePeakDbtp, 3));
            o.put("release_status", "A/B TEST ONLY; REQUIRES PERCEPTUAL COMPARISON BEFORE PROMOTION");
        }
        o.put("vocal_humanity_test", render.vocalHumanityGuided);
        if (render.vocalHumanityGuided) {
            o.put("lead_stem_path", render.vocalStemPath);
            o.put("phrase_rides", render.vocalRideCount);
            o.put("de_ess_events", render.vocalDeEssEvents);
            putNumber(o, "actual_stem_ride_down_db", render.vocalRideDownDb, 4);
            putNumber(o, "actual_stem_ride_up_db", render.vocalRideUpDb, 4);
            putNumber(o, "maximum_event_de_ess_db", render.vocalMaximumDeEssDb, 4);
            putNumber(o, "processed_minus_original_delta_scale", render.vocalDeltaScale, 4);
            o.put("vocal_correction_summary", render.vocalCorrectionSummary);
            o.put("corrected_premaster_lufs_i", round(render.aiCorrectedPremasterLufs, 3));
            o.put("corrected_premaster_true_peak_dbtp",
                    round(render.aiCorrectedPremasterTruePeakDbtp, 3));
            o.put("release_status", "A/B TEST ONLY; REQUIRES PERCEPTUAL COMPARISON BEFORE PROMOTION");
        }
        o.put("triangular_dither", true);
        o.put("quantizer_clips", render.quantizerClips);
        o.put("format", "24-bit PCM WAV");
        o.put("sample_rate_hz", render.verification.wav.sampleRate);
        o.put("channels", render.verification.wav.channels);
        o.put("duration_seconds", round(render.verification.wav.durationSeconds, 6));
        o.put("measurements", measurements(render.verification));
        return o;
    }

    static JSONObject vocalHumanity(VocalHumanityModel.Result result) throws JSONException {
        if (result == null) return null;
        JSONObject out = new JSONObject();
        out.put("available", result.available);
        out.put("automatic_correction_available", result.hasCorrections());
        out.put("status", result.status);
        out.put("method", result.method);
        if (result.leadStemPath != null) out.put("lead_stem_path", result.leadStemPath);
        if (result.leadSelectionEvidence != null) {
            out.put("lead_selection_evidence", result.leadSelectionEvidence);
        }
        putNumber(out, "processed_minus_original_delta_scale", result.deltaScale, 4);
        putNumber(out, "learned_active_floor_dbfs", result.activeFloorDbfs, 3);
        putNumber(out, "learned_phrase_floor_dbfs", result.phraseFloorDbfs, 3);
        putNumber(out, "learned_sibilance_median_db", result.learnedSibilanceMedianDb, 3);
        putNumber(out, "learned_sibilance_robust_scale_db", result.learnedSibilanceScaleDb, 3);
        putNumber(out, "learned_sibilance_trigger_db", result.learnedSibilanceTriggerDb, 3);
        out.put("phrases_analyzed", result.phrasesAnalyzed);
        out.put("phrase_rides_applied", result.rideCount());
        putNumber(out, "actual_phrase_ride_down_db", result.maximumRideDownDb, 4);
        putNumber(out, "actual_phrase_ride_up_db", result.maximumRideUpDb, 4);
        out.put("de_ess_events_applied", result.deEssEvents.size());
        putNumber(out, "maximum_event_de_ess_db", result.maximumDeEssDb, 4);
        JSONArray phrases = new JSONArray();
        for (VocalHumanityModel.Phrase phrase : result.phrases) {
            JSONObject item = new JSONObject();
            putNumber(item, "start_seconds", phrase.startSeconds, 3);
            putNumber(item, "end_seconds", phrase.endSeconds, 3);
            putNumber(item, "level_dbfs", phrase.levelDbfs, 3);
            putNumber(item, "ride_db", phrase.rideDb, 4);
            putNumber(item, "confidence", phrase.confidence, 4);
            item.put("learned_arrangement_state", phrase.learnedState >= 0
                    ? phrase.learnedState : JSONObject.NULL);
            phrases.put(item);
        }
        out.put("phrases", phrases);
        JSONArray deEss = new JSONArray();
        for (VocalHumanityModel.DeEssEvent event : result.deEssEvents) {
            JSONObject item = new JSONObject();
            putNumber(item, "start_seconds", event.startSeconds, 3);
            putNumber(item, "end_seconds", event.endSeconds, 3);
            putNumber(item, "attenuation_db", event.attenuationDb, 4);
            putNumber(item, "confidence", event.confidence, 4);
            putNumber(item, "sibilance_robust_z", event.sibilanceZ, 4);
            deEss.put(item);
        }
        out.put("de_ess_events", deEss);
        out.put("safety_notes", new JSONArray(result.safetyNotes));
        return out;
    }

    static JSONObject aiDiagnostic(AdaptiveDiagnosticModel.Result result) throws JSONException {
        if (result == null) return null;
        JSONObject out = new JSONObject();
        out.put("model", result.model);
        out.put("method", result.method);
        out.put("windows", result.windows);
        out.put("active_windows", result.activeWindows);
        out.put("learned_states", result.clusters);
        putNumber(out, "window_seconds", result.windowSeconds, 6);
        putNumber(out, "learned_active_floor_dbfs", result.activeFloorDbfs, 3);
        out.put("stem_trust_risk", result.stemTrustRisk);
        out.put("stem_trust_assessment", result.stemTrustAssessment);
        out.put("automatic_correction_available", result.hasAutoCorrections());
        out.put("automatic_correction_events", result.autoCorrectionCount());
        putNumber(out, "maximum_automatic_cut_db", result.maximumCorrectionDb(), 3);
        JSONArray events = new JSONArray();
        for (AdaptiveDiagnosticModel.Event event : result.events) {
            JSONObject item = new JSONObject();
            item.put("type", event.type.name());
            item.put("label", AdaptiveDiagnosticModel.label(event.type));
            putNumber(item, "start_seconds", event.startSeconds, 3);
            putNumber(item, "end_seconds", event.endSeconds, 3);
            putNumber(item, "confidence", event.confidence, 4);
            item.put("evidence", event.evidence);
            item.put("automatic_correction", event.autoCorrection);
            if (event.autoCorrection) {
                putNumber(item, "requested_attenuation_db_before_combined_1db_cap",
                        event.correctionDb, 3);
            }
            events.put(item);
        }
        out.put("events", events);
        out.put("safety_notes", new JSONArray(result.safetyNotes));
        return out;
    }

    private static void putNumber(JSONObject object, String key, double value, int places)
            throws JSONException {
        object.put(key, Double.isFinite(value) ? round(value, places) : JSONObject.NULL);
    }

    private static double round(double value, int places) {
        if (!Double.isFinite(value)) return value;
        double scale = Math.pow(10.0, places);
        return Math.rint(value * scale) / scale;
    }

    private static String timestamp() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }
}
