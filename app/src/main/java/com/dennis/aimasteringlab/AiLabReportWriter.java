package com.dennis.aimasteringlab;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Machine-readable analysis, plan, render and safety handoff for the AI lab. */
final class AiLabReportWriter {
    private AiLabReportWriter() {}

    static String create(AnalysisResult source,
                         AutonomousMasteringBrain.Result brain,
                         AnalysisResult reference,
                         MasterRenderer.Result referenceRender,
                         AutonomousMasteringRenderer.Result conservative,
                         AutonomousMasteringRenderer.Result preferred)
            throws JSONException {
        JSONObject root = new JSONObject();
        root.put("app", "Dennis AI Mastering Lab");
        root.put("version", "0.1.0");
        root.put("package", "com.dennis.aimasteringlab");
        root.put("processing_status",
                "experimental A/B laboratory; no candidate is automatically promoted to release master");
        root.put("evidence_labels", new JSONArray()
                .put("objective_measurement")
                .put("track-trained_inference")
                .put("device_response_simulation")
                .put("human_listening_observation_if_supplied"));
        root.put("source", source(source));
        root.put("ai_controller", controller(brain));
        root.put("local_diagnostics", diagnostics(source.aiDiagnostics));
        JSONObject outputs = new JSONObject();
        if (reference != null) {
            JSONObject referenceOutput = measurement(reference);
            referenceOutput.put("filename", reference.sourceName);
            referenceOutput.put("sha256", reference.sourceSha256);
            referenceOutput.put("source_preserved_without_render", referenceRender == null);
            referenceOutput.put("limiter_used",
                    referenceRender != null && referenceRender.limiterUsed);
            referenceOutput.put("maximum_limiter_reduction_db", referenceRender == null
                    ? 0.0 : round(referenceRender.maximumLimiterReductionDb, 4));
            outputs.put("flat_gain_reference", referenceOutput);
        }
        if (conservative != null) outputs.put("ai_conservative", candidate(conservative));
        if (preferred != null) outputs.put("ai_preferred", candidate(preferred));
        root.put("verified_outputs", outputs);
        root.put("selection_rule",
                "Compare saved WAVs at matched playback level. Dennis or a qualified listener must select a candidate manually.");
        root.put("physical_listening_performed_by_app", false);
        root.put("authoritative_release_master_created", false);
        return root.toString(2);
    }

    private static JSONObject source(AnalysisResult source) throws JSONException {
        JSONObject out = new JSONObject();
        out.put("filename", source.sourceName);
        out.put("sha256", source.sourceSha256);
        out.put("sample_rate_hz", source.wav.sampleRate);
        out.put("bits_per_sample", source.wav.bitsPerSample);
        out.put("effective_valid_bits", source.wav.validBitsPerSample);
        out.put("floating_point", source.wav.floatingPoint);
        out.put("channels", source.wav.channels);
        out.put("sample_frames", source.wav.frames);
        out.put("duration_seconds", round(source.wav.durationSeconds, 6));
        out.put("measurements", measurement(source));
        out.put("meter", source.meterVersion);
        return out;
    }

    private static JSONObject controller(AutonomousMasteringBrain.Result brain)
            throws JSONException {
        JSONObject out = new JSONObject();
        out.put("model", brain.model);
        out.put("method", brain.method);
        AutonomousMasteringBrain.Features f = brain.features;
        JSONObject features = new JSONObject();
        features.put("windows", f.windows);
        features.put("active_windows", f.activeWindows);
        features.put("active_floor_dbfs", round(f.activeFloorDbfs, 4));
        features.put("low_energy_share_db", round(f.lowShareDb, 4));
        features.put("low_mid_energy_share_db", round(f.lowMidShareDb, 4));
        features.put("presence_energy_share_db", round(f.presenceShareDb, 4));
        features.put("harsh_band_energy_share_db", round(f.harshShareDb, 4));
        features.put("air_energy_share_db", round(f.airShareDb, 4));
        features.put("side_energy_share_db", round(f.sideShareDb, 4));
        features.put("median_window_crest_db", round(f.medianWindowCrestDb, 4));
        features.put("p90_window_crest_db", round(f.highWindowCrestDb, 4));
        features.put("feature_reliability", round(f.reliability, 4));
        out.put("learned_features", features);
        out.put("conservative_plan", plan(brain.conservative));
        out.put("preferred_plan", plan(brain.preferred));
        out.put("limitations", strings(brain.limitations));
        return out;
    }

    private static JSONObject plan(AutonomousMasteringBrain.Plan plan)
            throws JSONException {
        JSONObject out = new JSONObject();
        out.put("mode", plan.mode.name().toLowerCase());
        out.put("confidence", round(plan.confidence, 4));
        JSONObject eq = new JSONObject();
        eq.put("low_shelf_110_hz_db", round(plan.lowShelfDb, 4));
        eq.put("low_mid_320_hz_q0_82_db", round(plan.lowMidDb, 4));
        eq.put("presence_3200_hz_q0_88_db", round(plan.presenceDb, 4));
        eq.put("high_shelf_9000_hz_db", round(plan.highShelfDb, 4));
        out.put("minimum_phase_eq", eq);
        out.put("side_scale", round(plan.sideScale, 5));
        out.put("local_event_depth_scale", round(plan.eventScale, 4));
        JSONObject compressor = new JSONObject();
        compressor.put("enabled", plan.compressorEnabled);
        if (plan.compressorEnabled) {
            compressor.put("threshold_dbfs", round(plan.compressorThresholdDbfs, 4));
            compressor.put("ratio", round(plan.compressorRatio, 4));
            compressor.put("attack_ms", round(plan.compressorAttackMs, 3));
            compressor.put("release_ms", round(plan.compressorReleaseMs, 3));
            compressor.put("maximum_reduction_db",
                    round(plan.compressorMaximumReductionDb, 4));
        }
        out.put("stereo_linked_compressor", compressor);
        out.put("objective_proxy_before", round(plan.objectiveBefore, 3));
        out.put("predicted_objective_proxy_after",
                round(plan.predictedObjectiveAfter, 3));
        out.put("decisions", strings(plan.decisions));
        out.put("bypassed_stages", strings(plan.bypassed));
        return out;
    }

    private static JSONArray diagnostics(AdaptiveDiagnosticModel.Result diagnostics)
            throws JSONException {
        JSONArray out = new JSONArray();
        if (diagnostics == null) return out;
        for (AdaptiveDiagnosticModel.Event event : diagnostics.events) {
            JSONObject item = new JSONObject();
            item.put("type", event.type.name().toLowerCase());
            item.put("start_seconds", round(event.startSeconds, 4));
            item.put("end_seconds", round(event.endSeconds, 4));
            item.put("confidence", round(event.confidence, 4));
            item.put("automatic_correction", event.autoCorrection);
            item.put("correction_db", round(event.correctionDb, 4));
            item.put("evidence", event.evidence);
            out.put(item);
        }
        return out;
    }

    private static JSONObject candidate(AutonomousMasteringRenderer.Result result)
            throws JSONException {
        JSONObject out = new JSONObject();
        out.put("plan", plan(result.plan));
        out.put("corrected_premaster", measurement(result.correctedPremaster));
        out.put("maximum_global_eq_db", round(result.maximumGlobalEqDb, 4));
        out.put("maximum_local_cut_db", round(result.maximumLocalCutDb, 4));
        out.put("maximum_compression_reduction_db",
                round(result.maximumCompressionReductionDb, 4));
        out.put("guardrail_verdict", result.guardrailVerdict);
        JSONObject finalRender = measurement(result.master.verification);
        finalRender.put("filename", result.master.savedFileName == null
                ? result.master.file.getName() : result.master.savedFileName);
        finalRender.put("sha256", result.master.verification.sourceSha256);
        finalRender.put("applied_gain_db", round(result.master.gainDb, 4));
        finalRender.put("limiter_used", result.master.limiterUsed);
        finalRender.put("maximum_limiter_reduction_db",
                round(result.master.maximumLimiterReductionDb, 4));
        finalRender.put("exact_minus_14_target_reached",
                result.master.exactTargetReached);
        finalRender.put("render_passes", result.master.renderPasses);
        out.put("verified_final", finalRender);
        return out;
    }

    private static JSONObject measurement(AnalysisResult analysis)
            throws JSONException {
        JSONObject out = new JSONObject();
        out.put("integrated_lufs", round(analysis.integratedLufs, 4));
        out.put("true_peak_estimate_dbtp", round(analysis.truePeakEstimateDbtp, 4));
        out.put("sample_peak_dbfs", round(analysis.samplePeakDbfs, 4));
        out.put("lra_lu", round(analysis.lraLu, 4));
        out.put("rms_dbfs", round(analysis.rmsDbfs, 4));
        out.put("crest_factor_db", round(analysis.crestFactorDb, 4));
        out.put("stereo_correlation", round(analysis.stereoCorrelation, 6));
        out.put("below_160_hz_correlation", round(analysis.lowBandCorrelation, 6));
        out.put("mono_fold_loss_db", round(analysis.monoFoldLossDb, 4));
        out.put("clipped_samples", analysis.clippedSamples);
        out.put("non_finite_samples", analysis.nonFiniteSamples);
        out.put("internal_zero_interruptions", analysis.internalZeroRuns);
        out.put("sample_rate_hz", analysis.wav.sampleRate);
        out.put("bits_per_sample", analysis.wav.bitsPerSample);
        out.put("channels", analysis.wav.channels);
        out.put("sample_frames", analysis.wav.frames);
        out.put("duration_seconds", round(analysis.wav.durationSeconds, 6));
        return out;
    }

    private static JSONArray strings(Iterable<String> values) {
        JSONArray out = new JSONArray();
        for (String value : values) out.put(value);
        return out;
    }

    private static double round(double value, int places) {
        if (!Double.isFinite(value)) return value;
        double scale = Math.pow(10.0, places);
        return Math.rint(value * scale) / scale;
    }
}
