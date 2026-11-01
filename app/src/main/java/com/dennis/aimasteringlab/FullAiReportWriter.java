package com.dennis.aimasteringlab;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Machine-readable evidence and autonomous-selection record for v0.14.5. */
final class FullAiReportWriter {
    private FullAiReportWriter() {}

    static String create(AnalysisResult source,
                         AutonomousMasteringBrain.Result brain,
                         FullAiMasteringEngine.Result engine,
                         FullAiReleaseQc.Result qc) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("app", "Dennis AI Mastering Lab");
        root.put("version", "0.14.5");
        root.put("package", "com.dennis.aimasteringlab");
        root.put("processing_status",
                "offline autonomous experimental master; separate from Dennis Mastering Studio deterministic release path");
        root.put("evidence_labels", new JSONArray()
                .put("objective_measurement")
                .put("track_trained_inference")
                .put("device_response_simulation")
                .put("human_listening_observation_if_supplied"));
        root.put("physical_listening_performed_by_app", false);
        root.put("source", source(source.originalSourceAnalysis == null
                ? source : source.originalSourceAnalysis));
        if (source.originalSourceAnalysis != null) root.put("processing_source", source(source));
        root.put("natural_studio_policy", new JSONObject()
                .put("sonic_baseline", "verified transparent control render; no legacy version is a fixed sonic target")
                .put("quality_first_selection", true)
                .put("targeted_cleanup", "independent finished-audio benefit and preservation required")
                .put("transparent_first", true)
                .put("later_broad_shaping", "diagnostic only")
                .put("translation_consensus_generates_audio", false)
                .put("reference_matching_generates_audio", false)
                .put("codec_headroom", "flat attenuation, no additional limiting")
                .put("optional_candidate_failure", "reject candidate; retain verified baseline"));
        root.put("section_map", sections(source.sections));
        root.put("local_diagnostics", diagnostics(source.aiDiagnostics));
        root.put("click_dropout_repair", continuity(source.clickDropoutRepair,
                source));
        root.put("authoritative_stereo_intelligence",
                stereoIntelligence(source.stereoIntelligence));
        root.put("v07_advanced_intelligence",
                advancedIntelligence(source.advancedIntelligence));
        root.put("v08_professional_intelligence",
                professionalIntelligence(source.professionalIntelligence));
        root.put("source_guard", sourceGuard(source.sourceGuard));
        root.put("v09_source_aware_intelligence",
                nextGeneration(source.nextGenerationIntelligence));
        root.put("stem_assist", stemAssist(source));
        root.put("controller", controller(brain));
        root.put("v0156_early_sparse_peak", new JSONObject()
                .put("shortcutted_competition", engine.earlySparsePeakShortcut)
                .put("decision", engine.earlySparsePeakDecision)
                .put("limitation", "Short circuit skips later specialist auditions only for verified peak-constrained stereo sources; no claim of equivalence to exhaustive competition"));
        root.put("candidate_competition", competition(engine));
        root.put("dennis_preference_memory", preference(engine.preferenceProfile));
        root.put("album_consistency", album(engine.albumProfile));
        root.put("profile_state_semantics", new JSONObject()
                .put("qc_snapshot", "profiles captured at run start and used for this completed master")
                .put("app_panels", "local learned profiles; Preference Memory is optional and off by default")
                .put("translation_profiles", "12 QC simulations only; never candidate generation")
                .put("post_run_actions_change_completed_qc", false));
        root.put("release_qc", releaseQc(qc));
        root.put("release_formats", ReleaseFormats.json(qc.releaseFormats));
        root.put("radio_preparation", RadioPreparation.json(qc.radioPreparation));
        root.put("final_passage_preservation", PassagePreservation.json(qc.passageCheck));
        root.put("master_confidence_map", MasterConfidenceMap.json(
                MasterConfidenceMap.build(source, engine, qc)));
        root.put("technical_mastering_verdict", qc.technicalVerdict);
        root.put("translation_simulation_verdict", qc.translationVerdict);
        root.put("display_verdict", qc.combinedVerdict());
        root.put("production_ar_verdict",
                "NOT ASSESSED BY APP: artistic competitiveness requires qualified human listening and current market context");
        root.put("authoritative_dms_master_replaced", false);
        root.put("limitations", strings(brain.limitations));
        return root.toString(2);
    }

    private static JSONObject source(AnalysisResult source) throws JSONException {
        JSONObject out = measurement(source);
        out.put("filename", source.sourceName);
        out.put("sha256", source.sourceSha256);
        out.put("effective_valid_bits", source.wav.validBitsPerSample);
        out.put("floating_point", source.wav.floatingPoint);
        out.put("container", source.wav.formatName());
        out.put("meter", source.meterVersion);
        return out;
    }

    private static JSONObject stemAssist(AnalysisResult source) throws JSONException {
        JSONObject out = new JSONObject();
        out.put("archive_name", nullable(source.stemArchiveName));
        out.put("archive_sha256", nullable(source.stemArchiveSha256));
        out.put("trust_tier", source.stemTrustTier);
        out.put("accepted_for_audio_delta", source.stemArchiveAccepted);
        out.put("accepted_for_analysis_only", source.stemArchiveAnalysisOnlyAccepted);
        out.put("accepted_for_candidate_generation",
                source.stemArchiveAccepted || source.stemArchiveAnalysisOnlyAccepted);
        out.put("status", source.stemAssistStatus);
        out.put("mix_policy", "Tier 1: authoritative stereo mix + scaled(processed stem - original stem). Tier 2: authoritative stereo mix samples only; aligned stems provide event-control measurements and no stem waveform or delta enters the render. Raw stem sums never replace the mix.");
        out.put("protected_intro_seconds", 2.0);
        out.put("protected_ending_seconds", round(Math.max(8.0,
                source.wav.durationSeconds * 0.04), 4));
        JSONArray inventory = new JSONArray();
        for (StemInventory.Item stem : source.stems) {
            JSONObject item = new JSONObject();
            item.put("path", stem.path);
            item.put("wav", stem.wav);
            item.put("crc_verified", stem.crcVerified);
            item.put("error", nullable(stem.error));
            item.put("uncompressed_bytes", stem.uncompressedBytes);
            item.put("compressed_bytes", stem.compressedBytes);
            item.put("rms_dbfs", finite(stem.rmsDbfs));
            item.put("active_rms_dbfs", finite(stem.activeRmsDbfs));
            item.put("peak_dbfs", finite(stem.peakDbfs));
            item.put("clipped_samples", stem.clippedSamples);
            if (stem.wavInfo != null) {
                item.put("sample_rate_hz", stem.wavInfo.sampleRate);
                item.put("valid_bits", stem.wavInfo.validBitsPerSample);
                item.put("channels", stem.wavInfo.channels);
                item.put("sample_frames", stem.wavInfo.frames);
                item.put("duration_seconds", round(stem.wavInfo.durationSeconds, 6));
            }
            inventory.put(item);
        }
        out.put("inventory", inventory);

        if (source.stemReconstruction != null) {
            StemReconstruction.Result r = source.stemReconstruction;
            JSONObject reconstruction = new JSONObject();
            reconstruction.put("attempted", r.attempted);
            reconstruction.put("compatible", r.compatible);
            reconstruction.put("stems_compared", r.stemsCompared);
            reconstruction.put("waveform_correlation", finite(r.waveformCorrelation));
            reconstruction.put("least_squares_gain", finite(r.leastSquaresGain));
            reconstruction.put("least_squares_gain_db", finite(r.leastSquaresGainDb));
            reconstruction.put("residual_snr_db", finite(r.residualSnrDb));
            reconstruction.put("start_lag_ms", finite(r.startLagMs));
            reconstruction.put("middle_lag_ms", finite(r.middleLagMs));
            reconstruction.put("end_lag_ms", finite(r.endLagMs));
            reconstruction.put("measured_drift_ms", finite(r.measuredDriftMs));
            reconstruction.put("global_polarity_risk", r.globalPolarityRisk);
            reconstruction.put("high_fidelity_sum_validation_reference", r.rawStemSumAllowed);
            reconstruction.put("likely_separation_derived", r.likelySeparationDerived);
            reconstruction.put("verdict", r.verdict);
            reconstruction.put("error", nullable(r.error));
            out.put("reconstruction", reconstruction);
        } else out.put("reconstruction", JSONObject.NULL);

        if (source.stemTrust != null) {
            JSONObject trust = new JSONObject();
            trust.put("tier", source.stemTrust.code());
            trust.put("status", source.stemTrust.status);
            trust.put("audio_delta_allowed", source.stemTrust.audioDeltaAllowed);
            trust.put("analysis_only_allowed", source.stemTrust.analysisOnlyAllowed);
            trust.put("secure_stems", source.stemTrust.secureStems);
            trust.put("secure_lead_stem", nullable(source.stemTrust.secureLeadPath));
            trust.put("reasons", strings(source.stemTrust.reasons));
            out.put("trust_classification", trust);
        } else out.put("trust_classification", JSONObject.NULL);

        if (source.stemAlignment != null) {
            JSONObject alignment = new JSONObject();
            alignment.put("method", source.stemAlignment.method);
            alignment.put("status", source.stemAlignment.status);
            alignment.put("stems_evaluated", source.stemAlignment.stemsEvaluated);
            alignment.put("secure_stems", source.stemAlignment.secureStems);
            alignment.put("analysis_secure_stems",
                    source.stemAlignment.analysisSecureStems);
            JSONArray stems = new JSONArray();
            for (StemAlignmentValidator.StemResult stem : source.stemAlignment.stems) {
                JSONObject item = new JSONObject();
                item.put("path", stem.path);
                item.put("secure", stem.secure);
                item.put("analysis_secure", stem.analysisSecure);
                item.put("polarity_risk", stem.polarityRisk);
                item.put("informative_probes", stem.informativeProbes);
                item.put("positive_polarity_probes", stem.positivePolarityProbes);
                item.put("minimum_lag_ms", finite(stem.minimumLagMs));
                item.put("maximum_lag_ms", finite(stem.maximumLagMs));
                item.put("drift_ms", finite(stem.driftMs));
                item.put("verdict", stem.verdict);
                item.put("rejection_reasons", strings(stem.rejectionReasons));
                JSONArray probes = new JSONArray();
                for (StemAlignmentValidator.Probe probe : stem.probes) {
                    JSONObject p = new JSONObject();
                    p.put("label", probe.label);
                    p.put("position_seconds", finite(probe.positionSeconds));
                    p.put("informative", probe.informative);
                    p.put("lag_ms", finite(probe.lagMs));
                    p.put("envelope_correlation", finite(probe.envelopeCorrelation));
                    p.put("signed_correlation", finite(probe.signedCorrelation));
                    probes.put(p);
                }
                item.put("probes", probes);
                stems.put(item);
            }
            alignment.put("stems", stems);
            out.put("local_alignment", alignment);
        } else out.put("local_alignment", JSONObject.NULL);

        out.put("vocal_humanity", vocal(source.vocalHumanity));
        out.put("masking_pockets", masking(source.maskingPockets));
        return out;
    }

    private static Object vocal(VocalHumanityModel.Result vocal) throws JSONException {
        if (vocal == null) return JSONObject.NULL;
        JSONObject out = new JSONObject();
        out.put("available", vocal.available);
        out.put("analysis_only", vocal.analysisOnly);
        out.put("status", vocal.status);
        out.put("method", vocal.method);
        out.put("lead_stem", nullable(vocal.leadStemPath));
        out.put("lead_selection_evidence", nullable(vocal.leadSelectionEvidence));
        out.put("delta_scale", round(vocal.deltaScale, 4));
        out.put("ride_count", vocal.rideCount());
        out.put("vocal_phrase_regions", vocal.phrases.size());
        out.put("maximum_ride_down_db", round(vocal.maximumRideDownDb, 4));
        out.put("maximum_ride_up_db", round(vocal.maximumRideUpDb, 4));
        out.put("maximum_deess_reduction_db", round(vocal.maximumDeEssDb, 4));
        JSONArray rides = new JSONArray();
        for (VocalHumanityModel.Phrase phrase : vocal.phrases) {
            if (Math.abs(phrase.rideDb) < 0.001) continue;
            JSONObject item = new JSONObject();
            item.put("start_seconds", round(phrase.startSeconds, 4));
            item.put("end_seconds", round(phrase.endSeconds, 4));
            item.put("ride_db", round(phrase.rideDb, 4));
            item.put("confidence", round(phrase.confidence, 4));
            rides.put(item);
        }
        out.put("phrase_rides", rides);
        JSONArray deess = new JSONArray();
        for (VocalHumanityModel.DeEssEvent event : vocal.deEssEvents) {
            JSONObject item = new JSONObject();
            item.put("start_seconds", round(event.startSeconds, 4));
            item.put("end_seconds", round(event.endSeconds, 4));
            item.put("attenuation_db", round(event.attenuationDb, 4));
            item.put("confidence", round(event.confidence, 4));
            item.put("sibilance_z", round(event.sibilanceZ, 4));
            deess.put(item);
        }
        out.put("deess_events", deess);
        out.put("safety_notes", strings(vocal.safetyNotes));
        return out;
    }

    private static Object masking(MaskingPocketModel.Result masking) throws JSONException {
        if (masking == null) return JSONObject.NULL;
        JSONObject out = new JSONObject();
        out.put("available", masking.available);
        out.put("analysis_only", masking.analysisOnly);
        out.put("status", masking.status);
        out.put("method", masking.method);
        out.put("lead_stem", nullable(masking.leadStemPath));
        out.put("delta_scale", round(masking.deltaScale, 4));
        out.put("competing_stems", strings(masking.competingStemPaths));
        out.put("maximum_cut_db", round(masking.maximumCutDb, 4));
        out.put("maximum_combined_nominal_cut_db", round(masking.maximumCombinedNominalCutDb, 4));
        JSONArray events = new JSONArray();
        for (MaskingPocketModel.Event event : masking.events) {
            JSONObject item = new JSONObject();
            item.put("stem_path", event.stemPath);
            item.put("stem_role", event.stemRole);
            item.put("band", event.bandLabel);
            item.put("low_hz", round(event.lowHz, 2));
            item.put("high_hz", round(event.highHz, 2));
            item.put("start_seconds", round(event.startSeconds, 4));
            item.put("end_seconds", round(event.endSeconds, 4));
            item.put("attenuation_db", round(event.attenuationDb, 4));
            item.put("confidence", round(event.confidence, 4));
            item.put("evidence", event.evidence);
            events.put(item);
        }
        out.put("events", events);
        out.put("safety_notes", strings(masking.safetyNotes));
        return out;
    }

    private static Object sourceGuard(SourceGuardModel.Result guard)
            throws JSONException {
        if (guard == null) return JSONObject.NULL;
        JSONObject out = new JSONObject();
        out.put("model", guard.model);
        out.put("declared_bits", guard.declaredBits);
        out.put("effective_bits", guard.effectiveBits);
        out.put("artificial_24_from_16", guard.artificial24From16);
        out.put("likely_previous_dennis_master", guard.likelyPriorDennisMaster);
        out.put("already_compliant_release_master",
                guard.alreadyCompliantReleaseMaster);
        out.put("strong_limiter_signature", guard.strongLimiterSignature);
        out.put("likely_lossy_material_in_wav", guard.likelyLossyInWav);
        out.put("bandwidth_cutoff_detected", guard.bandwidthCutoffDetected);
        out.put("codec_pre_echo_detected", guard.codecPreEchoDetected);
        out.put("strong_transient_events", guard.strongTransientEvents);
        out.put("codec_pre_echo_candidate_events", guard.codecPreEchoCandidateEvents);
        out.put("codec_pre_echo_share", round(guard.codecPreEchoShare, 6));
        out.put("near_peak_share", round(guard.nearPeakShare, 6));
        out.put("high_band_relative_db", round(guard.highBandRelativeDb, 4));
        out.put("estimated_cutoff_hz", finite(guard.estimatedCutoffHz));
        out.put("second_pass_guard", guard.secondPassGuard);
        out.put("high_frequency_boost_blocked", guard.highBoostBlocked);
        out.put("limiter_escalation_blocked", guard.limiterEscalationBlocked);
        out.put("verdict", guard.verdict);
        out.put("evidence", strings(guard.evidence));
        out.put("warnings", strings(guard.warnings));
        return out;
    }

    private static Object nextGeneration(
            NextGenerationMasteringModel.Result model) throws JSONException {
        if (model == null) return JSONObject.NULL;
        JSONObject out = new JSONObject();
        out.put("model", model.model);
        out.put("method", model.method);
        out.put("translation_automatic_events", 0);
        out.put("translation_candidate_generation_enabled", false);
        out.put("translation_consensus_findings", model.translation == null
                ? 0 : model.translation.events.size());
        out.put("translation_device_profiles_measured", model.translation == null
                ? 0 : model.translation.deviceProfiles);
        out.put("translation_independent_families", model.translation == null
                ? 0 : model.translation.independentFamilies);
        out.put("persistent_tone_automatic_events", model.tones == null
                ? 0 : model.tones.automaticCount());
        out.put("channel_integrity_automatic_events", model.channel == null
                ? 0 : model.channel.automaticCount());
        out.put("channel_balance_db", model.channel == null ? JSONObject.NULL
                : round(model.channel.balanceDb, 5));
        out.put("channel_balance_mad_db", model.channel == null ? JSONObject.NULL
                : round(model.channel.balanceMadDb, 5));
        out.put("channel_spectral_balance_spread_db", model.channel == null
                ? JSONObject.NULL : round(model.channel.spectralBalanceSpreadDb, 5));
        out.put("channel_median_lag_samples", model.channel == null
                ? JSONObject.NULL : model.channel.medianLagSamples);
        out.put("channel_median_correlation", model.channel == null
                ? JSONObject.NULL : round(model.channel.medianCorrelation, 6));
        out.put("reference_supplied", model.reference != null
                && model.reference.supplied);
        out.put("reference_usable", model.reference != null
                && model.reference.usable);
        out.put("reference_name", model.reference == null
                ? JSONObject.NULL : nullable(model.reference.referenceName));
        out.put("reference_sha256", model.reference == null
                ? JSONObject.NULL : nullable(model.reference.referenceSha256));
        out.put("reference_level_match_analysis_db", model.reference == null
                ? JSONObject.NULL : finite(model.reference.analysisLevelMatchDb));
        JSONArray events = new JSONArray();
        if (model.translation != null) {
            for (TranslationConsensusModel.Event e : model.translation.events) {
                JSONObject item = new JSONObject();
                item.put("system", "translation_consensus");
                item.put("kind", e.kind.name().toLowerCase());
                item.put("start_seconds", round(e.startSeconds, 4));
                item.put("end_seconds", round(e.endSeconds, 4));
                item.put("automatic", false);
                item.put("diagnostic_only", true);
                item.put("family_count", e.familyCount);
                item.put("device_count", e.deviceCount);
                item.put("families", nullable(e.familyEvidence));
                item.put("devices", nullable(e.deviceEvidence));
                item.put("adjustment_db", 0.0);
                item.put("hypothetical_attenuation_db",
                        round(e.attenuationDb, 4));
                item.put("evidence", e.evidence); events.put(item);
            }
        }
        if (model.tones != null) {
            for (PersistentToneModel.Event e : model.tones.events) {
                JSONObject item = new JSONObject();
                item.put("system", "persistent_tone");
                item.put("kind", e.kind.name().toLowerCase());
                item.put("frequency_hz", round(e.frequencyHz, 3));
                item.put("automatic", e.automatic);
                item.put("adjustment_db", round(e.attenuationDb, 4));
                item.put("q", round(e.q, 3));
                item.put("persistence", round(e.persistence, 4));
                item.put("evidence", e.evidence); events.put(item);
            }
        }
        if (model.channel != null) {
            for (ChannelIntegrityModel.Event e : model.channel.events) {
                JSONObject item = new JSONObject();
                item.put("system", "channel_integrity");
                item.put("kind", e.kind.name().toLowerCase());
                item.put("automatic", e.automatic);
                item.put("correction_db", round(e.correctionDb, 4));
                item.put("lag_samples", e.lagSamples);
                item.put("evidence", e.evidence); events.put(item);
            }
        }
        if (model.reference != null) {
            for (ReferenceCalibrationModel.Event e : model.reference.events) {
                JSONObject item = new JSONObject();
                item.put("system", "reference_calibration");
                item.put("kind", e.kind.name().toLowerCase());
                item.put("automatic", e.automatic);
                item.put("adjustment_db", round(e.adjustmentDb, 4));
                item.put("measured_difference_db",
                        round(e.measuredDifferenceDb, 4));
                item.put("evidence", e.evidence); events.put(item);
            }
        }
        out.put("events", events);
        out.put("safety_notes", strings(model.safetyNotes));
        return out;
    }

    private static Object stereoIntelligence(StereoIntelligenceModel.Result model)
            throws JSONException {
        if (model == null) return JSONObject.NULL;
        JSONObject out = new JSONObject();
        out.put("model", model.model);
        out.put("method", model.method);
        out.put("authoritative_stereo_only", true);
        out.put("stem_waveform_used", false);
        out.put("windows", model.windows);
        out.put("active_windows", model.activeWindows);
        out.put("active_floor_dbfs", round(model.activeFloorDbfs, 4));
        out.put("kick_like_windows_protected", model.kickLikeWindowsProtected);
        out.put("event_repair_available", model.hasEventRepair());
        out.put("low_end_intelligence_available", model.hasLowEnd());
        out.put("stereo_vocal_focus_available", model.hasVocalFocus());
        out.put("available_modules", model.availableModules());
        JSONArray events = new JSONArray();
        for (StereoIntelligenceModel.Event event : model.events) {
            JSONObject item = new JSONObject();
            item.put("kind", event.kind.name().toLowerCase());
            item.put("start_seconds", round(event.startSeconds, 4));
            item.put("end_seconds", round(event.endSeconds, 4));
            item.put("confidence", round(event.confidence, 4));
            item.put("primary_edit_db", round(event.primaryDb, 4));
            item.put("secondary_edit_db", round(event.secondaryDb, 4));
            item.put("automatic", event.automatic);
            item.put("evidence", event.evidence);
            events.put(item);
        }
        out.put("events", events);
        out.put("safety_notes", strings(model.safetyNotes));
        return out;
    }

    private static JSONObject stereoCandidate(StereoIntelligenceRenderer.Result result)
            throws JSONException {
        JSONObject out = new JSONObject();
        out.put("mode", result.mode.strategy);
        out.put("authoritative_stereo_only", result.authoritativeStereoOnly);
        out.put("stem_waveform_used", result.stemWaveformUsed);
        out.put("correction_events", result.correctionEvents);
        out.put("maximum_nominal_edit_db", round(result.maximumNominalEditDb, 4));
        out.put("model_confidence", round(result.modelConfidence, 4));
        out.put("normalized_evidence_benefit", round(result.evidenceBenefit, 4));
        out.put("measured_targeted_benefit", round(result.measuredTargetBenefit, 4));
        out.put("spectral_reduction_db", round(result.spectralReductionDb, 4));
        out.put("low_end_reduction_db", round(result.lowEndReductionDb, 4));
        out.put("vocal_focus_improvement_db",
                round(result.vocalFocusImprovementDb, 4));
        out.put("evidence_summary", result.evidenceSummary);
        if (result.delta != null) {
            JSONObject delta = new JSONObject();
            delta.put("frames", result.delta.frames);
            delta.put("source_correlation", round(result.delta.sourceCorrelation, 8));
            delta.put("maximum_absolute_delta",
                    round(result.delta.maximumAbsoluteDelta, 9));
            delta.put("edited_rms_delta_dbfs",
                    round(result.delta.editedRmsDeltaDbfs, 4));
            delta.put("protected_intro_max_delta",
                    round(result.delta.protectedIntroMaxDelta, 9));
            delta.put("protected_ending_max_delta",
                    round(result.delta.protectedEndingMaxDelta, 9));
            delta.put("protected_regions_exact", result.delta.protectedRegionsExact);
            out.put("delta_verification", delta);
        }
        return out;
    }

    private static Object advancedIntelligence(AdvancedStereoModel.Result model)
            throws JSONException {
        if (model == null) return JSONObject.NULL;
        JSONObject out = new JSONObject();
        out.put("model", model.model);
        out.put("method", model.method);
        out.put("authoritative_stereo_only", true);
        out.put("stem_waveform_used", false);
        out.put("provenance_classification_performed", false);
        out.put("artifact_label_semantics",
                "measured artifact-like signal signature; not proof that the source was AI-generated");
        out.put("windows", model.windows);
        out.put("active_windows", model.activeWindows);
        out.put("active_floor_dbfs", round(model.activeFloorDbfs, 4));
        out.put("section_map_confidence", finite(model.sectionMapConfidence));
        out.put("source_section_lift_lu", finite(model.sourceSectionLiftLu));
        out.put("predicted_section_lift_change_lu",
                round(model.predictedSectionLiftChangeLu, 4));
        out.put("limiter_pressure", model.limiterPressure);
        out.put("predicted_flat_gain_true_peak_dbtp",
                finite(model.predictedFlatGainTruePeakDbtp));
        out.put("natural_transients_protected", model.transientsProtected);
        out.put("section_aware_available", model.hasSectionAware());
        out.put("ai_artifact_repair_available", model.hasArtifactRepair());
        out.put("ai_generation_artifact_repair_available",
                model.hasArtifactRepair());
        out.put("transient_intelligence_available",
                model.hasTransientControl());
        out.put("available_modules", model.availableModules());
        JSONArray events = new JSONArray();
        for (AdvancedStereoModel.Event event : model.events) {
            JSONObject item = new JSONObject();
            item.put("kind", event.kind.name().toLowerCase());
            item.put("start_seconds", round(event.startSeconds, 6));
            item.put("end_seconds", round(event.endSeconds, 6));
            item.put("confidence", round(event.confidence, 4));
            item.put("primary_edit_db", round(event.primaryDb, 4));
            item.put("secondary_edit_db", round(event.secondaryDb, 4));
            item.put("automatic", event.automatic);
            item.put("evidence", event.evidence);
            events.put(item);
        }
        out.put("events", events);
        out.put("safety_notes", strings(model.safetyNotes));
        return out;
    }

    private static JSONObject advancedCandidate(AdvancedStereoRenderer.Result result)
            throws JSONException {
        JSONObject out = new JSONObject();
        out.put("mode", result.mode.strategy);
        out.put("authoritative_stereo_only", result.authoritativeStereoOnly);
        out.put("stem_waveform_used", result.stemWaveformUsed);
        out.put("provenance_classification_performed", false);
        out.put("correction_events", result.correctionEvents);
        out.put("maximum_nominal_edit_db",
                round(result.maximumNominalEditDb, 4));
        out.put("model_confidence", round(result.modelConfidence, 4));
        out.put("normalized_evidence_benefit",
                round(result.evidenceBenefit, 4));
        out.put("measured_targeted_benefit",
                round(result.measuredTargetBenefit, 4));
        out.put("section_lift_improvement_lu",
                round(result.sectionLiftImprovementLu, 4));
        out.put("artifact_band_reduction_db",
                round(result.artifactReductionDb, 4));
        out.put("transient_peak_reduction_db",
                round(result.transientPeakReductionDb, 4));
        out.put("evidence_summary", result.evidenceSummary);
        if (result.delta != null) {
            JSONObject delta = new JSONObject();
            delta.put("frames", result.delta.frames);
            delta.put("source_correlation",
                    round(result.delta.sourceCorrelation, 8));
            delta.put("maximum_absolute_delta",
                    round(result.delta.maximumAbsoluteDelta, 9));
            delta.put("edited_rms_delta_dbfs",
                    round(result.delta.editedRmsDeltaDbfs, 4));
            delta.put("protected_intro_max_delta",
                    round(result.delta.protectedIntroMaxDelta, 9));
            delta.put("protected_ending_max_delta",
                    round(result.delta.protectedEndingMaxDelta, 9));
            delta.put("protected_regions_exact",
                    result.delta.protectedRegionsExact);
            out.put("delta_verification", delta);
        }
        return out;
    }

    private static Object professionalIntelligence(
            ProfessionalMasteringModel.Result model) throws JSONException {
        if (model == null) return JSONObject.NULL;
        JSONObject out = new JSONObject();
        out.put("model", model.model);
        out.put("method", model.method);
        out.put("authoritative_stereo_only", true);
        out.put("stem_waveform_used", false);
        out.put("automatic_widening_used", false);
        out.put("vocal_label_semantics",
                "centre-channel spectral inference; not separated-vocal provenance");
        out.put("windows", model.windows);
        out.put("active_windows", model.activeWindows);
        out.put("active_floor_dbfs", round(model.activeFloorDbfs, 4));
        out.put("stereo_depth_reverb_available", model.hasDepthReverb());
        out.put("limiter_crest_optimisation_available",
                model.limiterCrestAvailable);
        out.put("low_frequency_phase_rescue_available",
                model.hasLowPhase());
        out.put("vocal_artifact_intelligence_available",
                model.hasVocalArtifacts());
        out.put("flat_depth_diagnostic", model.flatDepthDiagnostic);
        out.put("available_modules", model.availableModules());
        JSONArray events = new JSONArray();
        for (ProfessionalMasteringModel.Event event : model.events) {
            JSONObject item = new JSONObject();
            item.put("kind", event.kind.name().toLowerCase());
            item.put("start_seconds", round(event.startSeconds, 6));
            item.put("end_seconds", round(event.endSeconds, 6));
            item.put("confidence", round(event.confidence, 4));
            item.put("edit_db", round(event.primaryDb, 4));
            item.put("automatic", event.automatic);
            item.put("musical_context_checked", event.contextChecked);
            item.put("musical_context_passed", event.contextPassed);
            item.put("musical_context_reason", event.contextReason);
            item.put("tracked_frequency_hz", round(event.trackedFrequencyHz, 2));
            item.put("tracked_q", event.trackedQ);
            if (event.trackingFrequency != null) {
                JSONArray nodes = new JSONArray();
                for (int n=0;n<event.trackingFrequency.length;n++) nodes.put(new JSONObject()
                        .put("offset_seconds", n*MusicalEventGuard.STEP)
                        .put("hz", round(event.trackingFrequency[n],2))
                        .put("strength", round(event.trackingStrength[n],4)));
                item.put("frequency_tracking",nodes);
            }
            item.put("evidence", event.evidence);
            events.put(item);
        }
        out.put("events", events);
        out.put("safety_notes", strings(model.safetyNotes));
        return out;
    }

    private static JSONObject professionalCandidate(
            ProfessionalMasteringRenderer.Result result)
            throws JSONException {
        JSONObject out = new JSONObject();
        out.put("mode", result.mode.strategy);
        out.put("authoritative_stereo_only",
                result.authoritativeStereoOnly);
        out.put("stem_waveform_used", result.stemWaveformUsed);
        out.put("automatic_widening_used", result.wideningUsed);
        out.put("correction_events", result.correctionEvents);
        out.put("maximum_nominal_edit_db",
                round(result.maximumNominalEditDb, 4));
        out.put("model_confidence", round(result.modelConfidence, 4));
        out.put("normalized_evidence_benefit",
                round(result.evidenceBenefit, 4));
        out.put("measured_targeted_benefit",
                round(result.measuredTargetBenefit, 4));
        out.put("depth_side_reduction_db",
                round(result.depthSideReductionDb, 4));
        out.put("low_frequency_phase_benefit",
                round(result.lowPhaseImprovement, 6));
        out.put("vocal_artifact_reduction_db",
                round(result.vocalArtifactReductionDb, 4));
        out.put("evidence_summary", result.evidenceSummary);
        if (result.delta != null) {
            JSONObject delta = new JSONObject();
            delta.put("frames", result.delta.frames);
            delta.put("source_correlation",
                    round(result.delta.sourceCorrelation, 8));
            delta.put("maximum_absolute_delta",
                    round(result.delta.maximumAbsoluteDelta, 9));
            delta.put("edited_rms_delta_dbfs",
                    round(result.delta.editedRmsDeltaDbfs, 4));
            delta.put("protected_intro_max_delta",
                    round(result.delta.protectedIntroMaxDelta, 9));
            delta.put("protected_ending_max_delta",
                    round(result.delta.protectedEndingMaxDelta, 9));
            delta.put("protected_regions_exact",
                    result.delta.protectedRegionsExact);
            out.put("delta_verification", delta);
        }
        if (result.limiterOptimisation != null) {
            LimiterCrestOptimizer.Result limiter =
                    result.limiterOptimisation;
            JSONObject optimizer = new JSONObject();
            optimizer.put("model", limiter.model);
            optimizer.put("method", limiter.method);
            optimizer.put("alternative_profile_selected",
                    limiter.alternativeProfileSelected);
            optimizer.put("balanced_cost", finite(limiter.balancedCost));
            optimizer.put("selected_cost", finite(limiter.selectedCost));
            optimizer.put("cost_improvement",
                    round(limiter.costImprovement, 6));
            optimizer.put("measured_targeted_benefit",
                    round(limiter.measuredTargetBenefit, 4));
            optimizer.put("decision", limiter.decision);
            JSONArray trials = new JSONArray();
            for (LimiterCrestOptimizer.Trial trial : limiter.trials) {
                JSONObject item = new JSONObject();
                item.put("profile", trial.profile.label);
                item.put("eligible", trial.eligible);
                item.put("cost", finite(trial.cost));
                item.put("crest_loss_db", round(trial.crestLossDb, 4));
                item.put("lra_loss_lu", round(trial.lraLossLu, 4));
                item.put("verdict", trial.verdict);
                if (trial.master != null) {
                    item.put("maximum_limiter_reduction_db", round(
                            trial.master.maximumLimiterReductionDb, 4));
                    item.put("lookahead_ms", round(
                            trial.master.limiterLookaheadMs, 3));
                    item.put("release_ms", round(
                            trial.master.limiterReleaseMs, 3));
                }
                trials.put(item);
            }
            optimizer.put("rendered_profile_trials", trials);
            out.put("limiter_crest_optimisation", optimizer);
        }
        return out;
    }

    private static JSONObject nextGenerationCandidate(
            NextGenerationMasteringRenderer.Result result)
            throws JSONException {
        JSONObject out = new JSONObject();
        out.put("mode", result.mode.strategy);
        out.put("correction_events", result.correctionEvents);
        out.put("maximum_nominal_edit_db",
                round(result.maximumNominalEditDb, 4));
        out.put("model_confidence", round(result.modelConfidence, 4));
        out.put("normalized_evidence_benefit",
                round(result.evidenceBenefit, 4));
        out.put("measured_targeted_benefit",
                round(result.measuredTargetBenefit, 4));
        out.put("translation_reduction_db",
                round(result.translationReductionDb, 4));
        out.put("tone_reduction_db", round(result.toneReductionDb, 4));
        out.put("channel_balance_reduction_db",
                round(result.channelBalanceReductionDb, 4));
        out.put("phase_improvement", round(result.phaseImprovement, 6));
        out.put("reference_distance_reduction",
                round(result.referenceDistanceReduction, 4));
        out.put("automatic_widening_used", result.wideningUsed);
        out.put("evidence_summary", result.evidenceSummary);
        if (result.delta != null) {
            JSONObject delta = new JSONObject();
            delta.put("frames", result.delta.frames);
            delta.put("source_correlation",
                    round(result.delta.sourceCorrelation, 8));
            delta.put("maximum_absolute_delta",
                    round(result.delta.maximumAbsoluteDelta, 9));
            delta.put("protected_intro_max_delta",
                    round(result.delta.protectedIntroMaxDelta, 9));
            delta.put("protected_ending_max_delta",
                    round(result.delta.protectedEndingMaxDelta, 9));
            delta.put("protected_regions_exact",
                    result.delta.protectedRegionsExact);
            out.put("delta_verification", delta);
        }
        return out;
    }

    static Object continuity(ClickDropoutRepair.Result repair,
                             AnalysisResult source) throws JSONException {
        return continuity(repair, source.wav.sampleRate,
                source.internalZeroRegions);
    }

    static Object continuity(ClickDropoutRepair.Result repair,
                             int sampleRate) throws JSONException {
        return continuity(repair, sampleRate,
                java.util.Collections.<AnalysisResult.ZeroRegion>emptyList());
    }

    private static Object continuity(ClickDropoutRepair.Result repair,
                                     int sampleRate,
                                     java.util.List<AnalysisResult.ZeroRegion> sourceZeroRegions)
            throws JSONException {
        if (repair == null) return JSONObject.NULL;
        JSONObject out = new JSONObject();
        out.put("model", repair.model);
        out.put("method", repair.method);
        out.put("status", repair.status);
        out.put("repair_applied", repair.repairApplied);
        out.put("isolated_clicks", repair.isolatedClicks);
        out.put("isolated_clip_runs", repair.isolatedClipRuns);
        out.put("repaired_full_scale_samples", repair.repairedClippedSamples);
        out.put("inherited_full_scale_samples_remaining", repair.inheritedClipSamplesRemaining);
        out.put("exact_zero_dropouts", repair.exactZeroDropouts);
        out.put("repaired_frames", repair.repairedFrames);
        out.put("inspected_frames", repair.inspectedFrames);
        out.put("source_correlation", finite(repair.sourceCorrelation));
        out.put("maximum_absolute_delta", round(repair.maximumAbsoluteDelta, 9));
        out.put("edited_rms_delta_dbfs", finite(repair.editedRmsDeltaDbfs));
        out.put("measured_targeted_benefit",
                round(repair.measuredTargetedBenefit, 4));
        if (repair.repairApplied) {
            out.put("protected_regions_exact", repair.protectedRegionsExact);
            out.put("protected_regions_verification",
                    repair.protectedRegionsExact ? "PASS" : "FAIL");
        } else {
            out.put("protected_regions_exact", JSONObject.NULL);
            out.put("protected_regions_verification", "NOT_APPLICABLE_NO_REPAIR");
        }
        out.put("repaired_intermediate_sha256", nullable(repair.repairedSha256));
        JSONArray events = new JSONArray();
        for (ClickDropoutRepair.Event event : repair.events) {
            JSONObject item = new JSONObject();
            item.put("kind", event.kind.name().toLowerCase());
            item.put("start_frame", event.startFrame);
            item.put("end_frame", event.endFrame);
            item.put("start_seconds", round(event.startFrame
                    / (double) Math.max(1, sampleRate), 6));
            item.put("end_seconds", round((event.endFrame + 1L)
                    / (double) Math.max(1, sampleRate), 6));
            item.put("channel_mask", event.channelMask);
            item.put("confidence", round(event.confidence, 4));
            item.put("evidence", event.evidence);
            events.put(item);
        }
        out.put("events", events);
        JSONArray unresolved = new JSONArray();
        for (AnalysisResult.ZeroRegion region : sourceZeroRegions) {
            JSONObject item = new JSONObject();
            item.put("start_frame", region.startFrame);
            item.put("end_frame_exclusive", region.endFrameExclusive);
            item.put("frames", region.frames());
            item.put("start_seconds", round(region.startSeconds(sampleRate), 6));
            item.put("end_seconds", round(region.endSeconds(sampleRate), 6));
            item.put("automatic_repair_applied", false);
            item.put("review_required", true);
            unresolved.put(item);
        }
        out.put("source_zero_regions_100ms_or_longer", unresolved);
        out.put("unresolved_source_zero_regions", unresolved.length());
        out.put("post_dither_zero_count_semantics",
                "A zero count in the final 24-bit file does not prove repair; dither may replace exact zeros with noise-floor samples. Source-region provenance remains authoritative.");
        out.put("rejected_reasons", strings(repair.rejectedReasons));
        out.put("safety_notes", strings(repair.safetyNotes));
        return out;
    }

    static JSONObject preference(DennisPreferenceMemory.Profile profile)
            throws JSONException {
        JSONObject out = new JSONObject();
        out.put("snapshot_scope", "USED_FOR_THIS_RUN_AT_RUN_START");
        out.put("post_run_choices_included", false);
        out.put("offline_only", true);
        out.put("explicit_level_matched_choices_only", true);
        out.put("hard_safety_override_allowed", false);
        if (profile == null) {
            out.put("comparisons", 0);
            out.put("ready", false);
            out.put("status", "No preference profile was supplied to this run");
            return out;
        }
        out.put("comparisons", profile.comparisons);
        out.put("ready", profile.ready());
        out.put("status", profile.compactSummary());
        JSONArray strategies = new JSONArray();
        for (String strategy : DennisPreferenceMemory.knownStrategies(profile)) {
            JSONObject item = new JSONObject();
            item.put("strategy", strategy);
            Integer wins = profile.wins.get(strategy);
            Integer losses = profile.losses.get(strategy);
            item.put("wins", wins == null ? 0 : wins);
            item.put("losses", losses == null ? 0 : losses);
            item.put("current_score_bonus", round(profile.bonusFor(strategy), 4));
            strategies.put(item);
        }
        out.put("strategies", strategies);
        return out;
    }

    static JSONObject album(AlbumConsistencyModel.Profile profile)
            throws JSONException {
        JSONObject out = new JSONObject();
        out.put("snapshot_scope", "USED_FOR_THIS_RUN_AT_RUN_START");
        out.put("post_run_additions_included", false);
        if (profile == null) {
            out.put("active", false);
            out.put("songs_in_profile", 0);
            out.put("status", "Album Consistency Mode was not supplied to this run");
            return out;
        }
        out.put("active", profile.active);
        out.put("usable", profile.usable());
        out.put("name", profile.name);
        out.put("songs_in_profile", profile.songs);
        out.put("status", profile.compactSummary());
        JSONObject target = new JSONObject();
        target.put("low_share_db", round(profile.lowShareDb, 4));
        target.put("low_mid_share_db", round(profile.lowMidShareDb, 4));
        target.put("presence_share_db", round(profile.presenceShareDb, 4));
        target.put("harsh_share_db", round(profile.harshShareDb, 4));
        target.put("air_share_db", round(profile.airShareDb, 4));
        target.put("side_share_db", round(profile.sideShareDb, 4));
        target.put("lra_lu", round(profile.lraLu, 4));
        target.put("crest_factor_db", round(profile.crestDb, 4));
        out.put("verified_target", target);
        out.put("policy", "bounded candidate generation/ranking after hard safety; no forced loudness, widening or unsafe dynamics matching");
        return out;
    }

    private static JSONObject stemCandidate(StemAssistRenderer.Result stem)
            throws JSONException {
        JSONObject out = new JSONObject();
        out.put("mode", stem.mode.strategy);
        out.put("analysis_only_control", stem.analysisOnlyControl);
        out.put("stem_waveform_used", stem.stemWaveformUsed);
        out.put("correction_events", stem.correctionEvents);
        out.put("stems_used", stem.stemsUsed);
        out.put("maximum_nominal_edit_db", round(stem.maximumNominalEditDb, 4));
        out.put("delta_scale", round(stem.deltaScale, 4));
        out.put("model_confidence", round(stem.modelConfidence, 4));
        out.put("normalized_evidence_benefit", round(stem.evidenceBenefit, 4));
        out.put("evidence_summary", stem.evidenceSummary);
        if (stem.delta != null) {
            JSONObject delta = new JSONObject();
            delta.put("source_correlation", round(stem.delta.sourceCorrelation, 8));
            delta.put("maximum_absolute_delta", round(stem.delta.maximumAbsoluteDelta, 9));
            delta.put("edited_rms_delta_dbfs", round(stem.delta.editedRmsDeltaDbfs, 4));
            delta.put("protected_intro_max_delta", round(stem.delta.protectedIntroMaxDelta, 9));
            delta.put("protected_ending_max_delta", round(stem.delta.protectedEndingMaxDelta, 9));
            delta.put("protected_regions_exact", stem.delta.protectedRegionsExact);
            delta.put("side_delta_rms_dbfs", round(stem.delta.sideDeltaRmsDbfs, 4));
            out.put("delta_verification", delta);
        }
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
        JSONArray plans = new JSONArray();
        for (AutonomousMasteringBrain.Plan plan : brain.internalPlans) {
            plans.put(plan(plan));
        }
        out.put("internal_plans", plans);
        return out;
    }

    static JSONObject competition(FullAiMasteringEngine.Result engine)
            throws JSONException {
        JSONObject out = new JSONObject();
        out.put("model", engine.model);
        out.put("selection_rule", engine.rule);
        out.put("source_objective", round(engine.sourceObjective, 4));
        out.put("pcm_selected_strategy", engine.selected.strategy);
        out.put("selection_reason", engine.selectionReason);
        out.put("gentler_challenger_decision", engine.gentlerDecision);
        out.put("gentler_challenger_scope", "one 75%-strength optional core-chain render; technical repair maps and release limiting are unchanged");
        out.put("v07_comparison_reference_strategy",
                engine.naturalReference == null ? JSONObject.NULL
                        : engine.naturalReference.strategy);
        out.put("quality_first_selection", true);
        out.put("quality_scope", "independent finished-PCM event-band benefit and control differences; limiter stress comparison; no legacy sonic version has permanent priority");
        out.put("translation_consensus_diagnostic_only",
                engine.translationConsensusDiagnosticOnly);
        out.put("render_integrity_failures",
                new JSONArray(engine.renderIntegrityFailures));
        out.put("rank_semantics", "raw_score_rank is calculated before no-change, module-promotion and codec gates; selected_after_promotion_gates identifies the authoritative decision");
        out.put("transparent_rollback", engine.transparentRollback);
        out.put("meaningful_mastering_gate_rollback", engine.meaningfulChangeRollback);
        out.put("meaningful_mastering_gate_decision", engine.meaningfulChangeDecision);
        out.put("meaningful_mastering_compared_strategy", nullable(engine.meaningfulChangeComparedStrategy));
        out.put("final_gate_delta_vs_transparent", masteringDelta(engine.selectedVsTransparentDelta));
        out.put("broad_mastering_required", engine.broadMasteringNeed != null && engine.broadMasteringNeed.required);
        out.put("broad_mastering_need", engine.broadMasteringNeed == null ? JSONObject.NULL : engine.broadMasteringNeed.summary());
        out.put("broad_mastering_decision", engine.broadMasteringDecision);
        out.put("broad_mastering_anchor", engine.broadMasteringAnchor == null ? JSONObject.NULL : engine.broadMasteringAnchor.strategy);
        out.put("broad_mastering_anchor_used", engine.broadMasteringAnchorUsed);
        out.put("broad_mastering_unresolved", engine.broadMasteringUnresolved);
        out.put("adaptive_broad_candidates_attempted", engine.adaptiveBroadCandidatesAttempted);
        out.put("adaptive_broad_candidates_eligible", engine.adaptiveBroadCandidatesEligible);
        out.put("adaptive_broad_decision", engine.adaptiveBroadDecision);
        out.put("adaptive_broad_diagnostics", new JSONArray(engine.adaptiveBroadDiagnostics));
        out.put("adaptive_broad_anchor", engine.adaptiveBroadAnchor == null ? JSONObject.NULL : engine.adaptiveBroadAnchor.strategy);
        out.put("broad_mastering_anchor_delta", masteringDelta(engine.broadMasteringAnchorDelta));
        out.put("stereo_intelligence_candidates_attempted",
                engine.intelligenceCandidatesAttempted);
        out.put("stereo_intelligence_candidates_eligible",
                engine.intelligenceCandidatesEligible);
        out.put("stereo_intelligence_selection_blocked",
                engine.intelligenceSelectionBlocked);
        out.put("stereo_intelligence_decision", engine.intelligenceDecision);
        out.put("v07_advanced_candidates_attempted",
                engine.advancedCandidatesAttempted);
        out.put("v07_advanced_candidates_eligible",
                engine.advancedCandidatesEligible);
        out.put("v07_advanced_selection_blocked",
                engine.advancedSelectionBlocked);
        out.put("v07_advanced_decision", engine.advancedDecision);
        out.put("v08_professional_candidates_attempted",
                engine.professionalCandidatesAttempted);
        out.put("v08_professional_candidates_eligible",
                engine.professionalCandidatesEligible);
        out.put("v08_professional_selection_blocked",
                engine.professionalSelectionBlocked);
        out.put("v08_professional_decision",
                engine.professionalDecision);
        out.put("v09_source_aware_candidates_attempted",
                engine.nextGenerationCandidatesAttempted);
        out.put("v09_source_aware_candidates_eligible",
                engine.nextGenerationCandidatesEligible);
        out.put("v09_source_aware_selection_blocked",
                engine.nextGenerationSelectionBlocked);
        out.put("v09_source_aware_decision",
                engine.nextGenerationDecision);
        out.put("stem_archive_supplied", engine.stemArchiveSupplied);
        out.put("stem_candidates_attempted", engine.stemCandidatesAttempted);
        out.put("stem_candidates_eligible", engine.stemCandidatesEligible);
        out.put("stereo_fallback", engine.stereoFallback);
        out.put("stem_selection_blocked", engine.stemSelectionBlocked);
        out.put("stem_decision", engine.stemDecision);
        JSONArray candidates = new JSONArray();
        for (FullAiMasteringEngine.Candidate candidate : engine.candidates) {
            JSONObject item = new JSONObject();
            item.put("strategy", candidate.strategy);
            item.put("gentler_challenger", candidate.gentlerChallenger);
            item.put("passage_preservation", PassagePreservation.json(candidate.passageCheck));
            item.put("status", candidate.status);
            item.put("duplicate_of", nullable(candidate.duplicateOf));
            item.put("rendered", candidate.rendered);
            item.put("eligible", candidate.eligible);
            item.put("raw_score_rank", candidate.rank);
            item.put("rank", candidate.rank);
            item.put("render_attempts", candidate.renderAttempts);
            item.put("score", finite(candidate.score));
            item.put("technical_score_before_personalization",
                    finite(candidate.technicalScore));
            item.put("measured_objective", finite(candidate.measuredObjective));
            item.put("processing_cost", finite(candidate.processingCost));
            item.put("preference_memory_bonus", round(candidate.preferenceBonus, 4));
            item.put("album_consistency_distance", finite(candidate.albumDistance));
            item.put("album_consistency_bonus", round(candidate.albumBonus, 4));
            item.put("promotion_blocked", candidate.promotionBlocked);
            item.put("selected_after_promotion_gates",
                    candidate.selectedAfterPromotionGates);
            item.put("decision_outcome", candidate.decisionOutcome());
            item.put("meaningful_change_passed", candidate.meaningfulChangePassed);
            item.put("meaningful_change_decision", candidate.meaningfulChangeDecision);
            item.put("level_matched_delta_vs_transparent", masteringDelta(candidate.masteringDelta));
            item.put("stereo_intelligence_aware", candidate.intelligenceAware);
            item.put("stereo_intelligence_evidence_bonus",
                    round(candidate.intelligenceEvidenceBonus, 4));
            item.put("v07_advanced_aware", candidate.advancedAware);
            item.put("v07_advanced_evidence_bonus",
                    round(candidate.advancedEvidenceBonus, 4));
            item.put("quality_decision", candidate.qualityDecision);
            item.put("independent_target_comparison", TargetedQualityEvidence.json(candidate.qualityEvidence));
            item.put("v08_professional_aware",
                    candidate.professionalAware);
            item.put("v08_professional_evidence_bonus",
                    round(candidate.professionalEvidenceBonus, 4));
            item.put("v09_source_aware", candidate.nextGenerationAware);
            item.put("v09_source_aware_evidence_bonus",
                    round(candidate.nextGenerationEvidenceBonus, 4));
            item.put("stem_aware", candidate.stemAware);
            item.put("stem_control_mode", candidate.stemAssist == null
                    ? "none"
                    : (candidate.stemAssist.analysisOnlyControl
                        ? "analysis_only" : "processed_minus_original_delta"));
            item.put("stem_evidence_bonus", round(candidate.stemEvidenceBonus, 4));
            item.put("rejection_reasons", strings(candidate.rejectionReasons));
            item.put("plan", plan(candidate.plan));
            if (candidate.render != null) {
                item.put("corrected_premaster", measurement(candidate.render.correctedPremaster));
                item.put("verified_final", rendered(candidate.render));
            }
            if (candidate.stemAssist != null) {
                JSONObject guided = stemCandidate(candidate.stemAssist);
                item.put("stem_guided_processing", guided);
                if (!candidate.stemAssist.analysisOnlyControl) {
                    item.put("stem_delta", guided);
                }
            }
            if (candidate.stereoIntelligence != null) {
                item.put("stereo_intelligence_processing",
                        stereoCandidate(candidate.stereoIntelligence));
            }
            if (candidate.advancedIntelligence != null) {
                item.put("v07_advanced_processing",
                        advancedCandidate(candidate.advancedIntelligence));
            }
            if (candidate.professionalIntelligence != null) {
                item.put("v08_professional_processing",
                        professionalCandidate(
                                candidate.professionalIntelligence));
            }
            if (candidate.nextGenerationIntelligence != null) {
                item.put("v09_source_aware_processing",
                        nextGenerationCandidate(
                                candidate.nextGenerationIntelligence));
            }
            candidates.put(item);
        }
        out.put("candidates", candidates);
        return out;
    }

    private static Object masteringDelta(MasteringDeltaEvidence.Result delta)
            throws JSONException {
        if (delta == null) return JSONObject.NULL;
        JSONObject out = new JSONObject();
        out.put("frames", delta.frames);
        out.put("samples", delta.samples);
        out.put("gain_match_db", round(delta.gainMatchDb, 6));
        out.put("correlation", round(delta.correlation, 9));
        out.put("reference_rms_dbfs", round(delta.referenceRmsDbfs, 6));
        out.put("candidate_rms_dbfs", round(delta.candidateRmsDbfs, 6));
        out.put("level_matched_residual_dbfs", round(delta.levelMatchedResidualDbfs, 6));
        out.put("level_matched_residual_relative_db",
                round(delta.levelMatchedResidualRelativeDb, 6));
        out.put("dc_difference", round(delta.dcDifference, 10));
        out.put("broad_change_floor_db", MasteringDeltaEvidence.BROAD_CHANGE_FLOOR_DB);
        out.put("narrow_change_floor_db", MasteringDeltaEvidence.NARROW_CHANGE_FLOOR_DB);
        out.put("broad_meaningful", delta.broadMeaningful);
        out.put("narrow_detectable", delta.narrowDetectable);
        out.put("verdict", delta.verdict);
        return out;
    }

    private static JSONObject releaseQc(FullAiReleaseQc.Result qc)
            throws JSONException {
        JSONObject out = new JSONObject();
        out.put("method", qc.method);
        out.put("initial_pcm_winner", qc.initialPcmWinner.strategy);
        out.put("final_strategy", qc.finalCandidate.strategy);
        out.put("codec_fallback", qc.codecFallback);
        out.put("codec_safety_rerendered", qc.codecSafetyRerendered);
        out.put("codec_safety_attenuated", qc.codecSafetyAttenuated);
        out.put("loudness_verdict", qc.loudnessVerdict);
        out.put("target_shortfall_lu", finite(qc.targetShortfallLu));
        out.put("technical_verdict", qc.technicalVerdict);
        out.put("translation_verdict", qc.translationVerdict);
        out.put("display_verdict", qc.combinedVerdict());
        out.put("continuity_review_required",
                qc.continuityReviewRequired);
        out.put("source_limitation_review_required",
                qc.sourceLimitationReviewRequired);
        out.put("unresolved_source_zero_regions",
                qc.unresolvedSourceZeroRegions);
        out.put("warnings", strings(qc.warnings));
        JSONObject master = measurement(qc.finalMasterVerification);
        master.put("filename", qc.finalMaster.getName());
        master.put("sha256", qc.finalMasterSha256);
        master.put("exact_minus_14_target_reached",
                qc.finalCandidate.render.master.exactTargetReached);
        master.put("compliant_source_preserved",
                "COMPLIANT_SOURCE_PRESERVED".equals(qc.loudnessVerdict));
        master.put("no_change_tolerance_lu", AudioAnalyzer.COMPLIANT_MASTER_TOLERANCE_LU);
        master.put("dynamics_protected_shortfall",
                qc.finalCandidate.render.master.dynamicsProtected);
        master.put("limiter_used", qc.finalCandidate.render.master.limiterUsed);
        master.put("limiter_oversampling", qc.finalCandidate.render.master.limiterOversampling);
        master.put("limiter_profile",
                qc.finalCandidate.render.master.limiterProfile);
        master.put("limiter_lookahead_ms", round(
                qc.finalCandidate.render.master.limiterLookaheadMs, 3));
        master.put("limiter_release_ms", round(
                qc.finalCandidate.render.master.limiterReleaseMs, 3));
        master.put("maximum_limiter_reduction_db", round(
                qc.finalCandidate.render.master.maximumLimiterReductionDb, 4));
        limiterMetrics(master, qc.finalCandidate.render.master);
        out.put("final_pcm_master", master);

        JSONArray codecs = new JSONArray();
        for (FullAiReleaseQc.CodecAttempt attempt : qc.codecAttempts) {
            JSONObject item = new JSONObject();
            item.put("strategy", attempt.strategy);
            item.put("pass", attempt.pass);
            item.put("verdict", attempt.verdict);
            item.put("safety_rerendered", attempt.safetyRerendered);
            item.put("flat_attenuation_db", attempt.codecSafetyAttenuationDb);
            item.put("requested_pcm_ceiling_dbtp", finite(
                    attempt.requestedPcmCeilingDbtp));
            if (attempt.aac != null) {
                JSONObject codec = new JSONObject();
                codec.put("available", attempt.aac.available);
                codec.put("codec", attempt.aac.codec);
                codec.put("bitrate", attempt.aac.bitrate);
                codec.put("encoder", nullable(attempt.aac.encoderName));
                codec.put("error", nullable(attempt.aac.error));
                if (attempt.aacDecoded != null && attempt.aacDecoded.verification != null) {
                    codec.put("decoder", nullable(attempt.aacDecoded.decoderName));
                    codec.put("decoded", measurement(attempt.aacDecoded.verification));
                }
                item.put("aac_256", codec);
            }
            if (attempt.sbc != null && attempt.sbc.verification != null) {
                JSONObject sbc = measurement(attempt.sbc.verification);
                sbc.put("method", attempt.sbc.method);
                item.put("sbc_328_proxy", sbc);
            }
            codecs.put(item);
        }
        out.put("codec_attempts", codecs);
        if (qc.finalAac != null) {
            JSONObject encoded = new JSONObject();
            encoded.put("filename", qc.finalAac.getName());
            encoded.put("sha256", qc.finalAacSha256);
            out.put("selected_aac", encoded);
        }
        out.put("translation", translation(qc.translation));
        out.put("source_translation", translation(qc.sourceTranslation));
        return out;
    }

    static Object translation(TranslationAnalyzer.Result translation)
            throws JSONException {
        if (translation == null) return JSONObject.NULL;
        JSONObject out = new JSONObject();
        out.put("method", translation.method);
        out.put("level_match_policy", translation.levelMatchPolicy);
        out.put("source_relative_judging", translation.sourceRelative);
        out.put("permitted_mastering_lift_reduction_db",
                round(translation.permittedLiftReductionDb, 4));
        out.put("source_absolute_pass_count",
                translation.sourceAbsolutePassCount);
        out.put("source_absolute_review_count",
                translation.sourceAbsoluteReviewCount);
        out.put("reference_peak_dbfs", round(translation.referencePeakDbfs, 4));
        out.put("reference_rms_dbfs", round(translation.referenceRmsDbfs, 4));
        out.put("pass_count", translation.passCount);
        out.put("review_count", translation.reviewCount);
        JSONArray devices = new JSONArray();
        for (TranslationAnalyzer.DeviceResult device : translation.devices) {
            JSONObject item = new JSONObject();
            item.put("name", device.name);
            item.put("mono", device.mono);
            item.put("raw_response_peak_dbfs", round(device.rawResponsePeakDbfs, 4));
            item.put("raw_response_rms_dbfs", round(device.rawResponseRmsDbfs, 4));
            item.put("requested_level_match_gain_db",
                    round(device.requestedLevelMatchGainDb, 4));
            item.put("applied_level_match_gain_db",
                    round(device.appliedLevelMatchGainDb, 4));
            item.put("level_match_peak_limited", device.levelMatchPeakLimited);
            item.put("raw_headroom_stress_db",
                    round(device.rawHeadroomStressDb, 4));
            item.put("level_matched_peak_dbfs", round(device.peakDbfs, 4));
            item.put("level_matched_rms_dbfs", round(device.rmsDbfs, 4));
            item.put("peak_dbfs", round(device.peakDbfs, 4));
            item.put("rms_dbfs", round(device.rmsDbfs, 4));
            item.put("vocal_band_share_db", round(device.vocalBandShareDb, 4));
            item.put("stereo_correlation", round(device.stereoCorrelation, 6));
            item.put("chorus_lift_db", finite(device.chorusLiftDb));
            item.put("busy_lift_db", finite(device.busyLiftDb));
            item.put("source_chorus_lift_db",
                    finite(device.sourceChorusLiftDb));
            item.put("source_busy_lift_db",
                    finite(device.sourceBusyLiftDb));
            item.put("chorus_lift_change_from_source_db",
                    finite(device.chorusLiftDeltaDb));
            item.put("busy_lift_change_from_source_db",
                    finite(device.busyLiftDeltaDb));
            item.put("source_stereo_correlation",
                    finite(device.sourceStereoCorrelation));
            item.put("clipping_risk", device.clippingRisk);
            item.put("clipping_risk_basis", "level_matched_peak_dbfs; raw profile peak is diagnostic headroom stress only");
            item.put("mono_phase_risk", device.monoPhaseRisk);
            item.put("source_mono_phase_risk", device.sourceMonoPhaseRisk);
            item.put("mastering_introduced_mono_phase_risk",
                    device.masteringIntroducedMonoPhaseRisk);
            item.put("absolute_lift_above_0_5_db",
                    device.absoluteLiftAboveFloor);
            item.put("source_relative_lift_safe",
                    device.sourceRelativeLiftSafe);
            item.put("mastering_introduced_lift_risk",
                    device.masteringIntroducedLiftRisk);
            item.put("lift_preserved", device.liftPreserved);
            item.put("review_reasons", strings(device.reviewReasons));
            item.put("diagnostic_notes", strings(device.diagnosticNotes));
            item.put("verdict", device.verdict);
            devices.put(item);
        }
        out.put("devices", devices);
        return out;
    }

    private static JSONObject rendered(AutonomousMasteringRenderer.Result render)
            throws JSONException {
        JSONObject out = measurement(render.master.verification);
        out.put("sha256", render.master.verification.sourceSha256);
        out.put("applied_gain_db", round(render.master.gainDb, 4));
        out.put("limiter_used", render.master.limiterUsed);
        out.put("limiter_profile", render.master.limiterProfile);
        out.put("limiter_oversampling", render.master.limiterOversampling);
        out.put("limiter_lookahead_ms", round(
                render.master.limiterLookaheadMs, 3));
        out.put("limiter_release_ms", round(
                render.master.limiterReleaseMs, 3));
        out.put("maximum_limiter_reduction_db", round(
                render.master.maximumLimiterReductionDb, 4));
        out.put("maximum_global_eq_db", round(render.maximumGlobalEqDb, 4));
        out.put("maximum_local_cut_db", round(render.maximumLocalCutDb, 4));
        out.put("maximum_compression_reduction_db", round(
                render.maximumCompressionReductionDb, 4));
        out.put("guardrail_verdict", render.guardrailVerdict);
        limiterMetrics(out, render.master);
        out.put("render_passes", render.master.renderPasses);
        out.put("verified_peak_safety_retries", render.master.peakSafetyRecoveryPasses);
        out.put("verified_peak_safety_attenuation_db", round(render.master.peakSafetyRecoveryAttenuationDb, 4));
        out.put("exact_minus_14_target_reached", render.master.exactTargetReached);
        out.put("compliant_source_preserved", render.master.compliantSourcePreserved);
        out.put("no_change_tolerance_lu", AudioAnalyzer.COMPLIANT_MASTER_TOLERANCE_LU);
        return out;
    }

    private static void limiterMetrics(JSONObject out, MasterRenderer.Result master)
            throws JSONException {
        out.put("average_limiter_reduction_db", round(master.averageLimiterReductionDb, 5));
        out.put("maximum_limiter_gain_step_db_per_sample", round(master.maximumLimiterGainStepDb, 6));
        out.put("limiter_frames_above_1_db_percent", round(master.limiterFramesAboveOneDbPercent, 5));
        out.put("limiter_frames_above_3_db_percent", round(master.limiterFramesAboveThreeDbPercent, 5));
        out.put("sparse_peak_release_mode", master.sparsePeakReleaseMode);
    }

    private static JSONObject plan(AutonomousMasteringBrain.Plan plan)
            throws JSONException {
        JSONObject out = new JSONObject();
        out.put("strategy", plan.strategy);
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
        compressor.put("threshold_dbfs", round(plan.compressorThresholdDbfs, 4));
        compressor.put("ratio", round(plan.compressorRatio, 4));
        compressor.put("attack_ms", round(plan.compressorAttackMs, 3));
        compressor.put("release_ms", round(plan.compressorReleaseMs, 3));
        compressor.put("maximum_reduction_db", round(
                plan.compressorMaximumReductionDb, 4));
        out.put("stereo_linked_compressor", compressor);
        out.put("objective_proxy_before", round(plan.objectiveBefore, 3));
        out.put("predicted_objective_proxy_after", round(
                plan.predictedObjectiveAfter, 3));
        out.put("decisions", strings(plan.decisions));
        out.put("bypassed_stages", strings(plan.bypassed));
        return out;
    }

    private static JSONObject measurement(AnalysisResult value)
            throws JSONException {
        JSONObject out = new JSONObject();
        out.put("integrated_lufs", round(value.integratedLufs, 4));
        out.put("true_peak_estimate_dbtp", round(value.truePeakEstimateDbtp, 4));
        out.put("sample_peak_dbfs", round(value.samplePeakDbfs, 4));
        out.put("lra_lu", round(value.lraLu, 4));
        out.put("rms_dbfs", round(value.rmsDbfs, 4));
        out.put("crest_factor_db", round(value.crestFactorDb, 4));
        out.put("stereo_correlation", round(value.stereoCorrelation, 6));
        out.put("below_160_hz_correlation", round(value.lowBandCorrelation, 6));
        out.put("mono_fold_loss_db", round(value.monoFoldLossDb, 4));
        out.put("clipped_samples", value.clippedSamples);
        out.put("non_finite_samples", value.nonFiniteSamples);
        out.put("internal_zero_interruptions", value.internalZeroRuns);
        JSONArray zeroRegions = new JSONArray();
        for (AnalysisResult.ZeroRegion region : value.internalZeroRegions) {
            JSONObject item = new JSONObject();
            item.put("start_frame", region.startFrame);
            item.put("end_frame_exclusive", region.endFrameExclusive);
            item.put("frames", region.frames());
            item.put("start_seconds", round(region.startSeconds(
                    value.wav.sampleRate), 6));
            item.put("end_seconds", round(region.endSeconds(
                    value.wav.sampleRate), 6));
            zeroRegions.put(item);
        }
        out.put("internal_zero_regions_100ms_or_longer", zeroRegions);
        out.put("sample_rate_hz", value.wav.sampleRate);
        out.put("bits_per_sample", value.wav.bitsPerSample);
        out.put("valid_bits_per_sample", value.wav.validBitsPerSample);
        out.put("channel_mask", value.wav.channelMask);
        out.put("channels", value.wav.channels);
        out.put("sample_frames", value.wav.frames);
        out.put("duration_seconds", round(value.wav.durationSeconds, 6));
        return out;
    }

    private static Object sections(TrackSections sections) throws JSONException {
        if (sections == null) return JSONObject.NULL;
        JSONObject out = new JSONObject();
        out.put("method", sections.method);
        out.put("learned_mapping", sections.learnedMapping);
        out.put("learned_states", sections.learnedStates);
        out.put("mapping_confidence", finite(sections.mappingConfidence));
        out.put("quiet_to_chorus_lift_lu", finite(sections.quietToChorusLiftLu));
        JSONArray segments = new JSONArray();
        for (TrackSections.Segment segment : sections.segments) {
            JSONObject item = new JSONObject();
            item.put("role", segment.role);
            item.put("start_seconds", round(segment.startSeconds, 4));
            item.put("duration_seconds", round(segment.durationSeconds, 4));
            item.put("average_lufs", finite(segment.averageLufs));
            item.put("confidence", finite(segment.confidence));
            segments.put(item);
        }
        out.put("translation_segments", segments);
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

    private static JSONArray strings(Iterable<String> values) {
        JSONArray out = new JSONArray();
        for (String value : values) out.put(value);
        return out;
    }

    private static Object nullable(String value) {
        return value == null ? JSONObject.NULL : value;
    }

    private static Object finite(double value) {
        return Double.isFinite(value) ? round(value, 4) : JSONObject.NULL;
    }

    private static double round(double value, int places) {
        if (!Double.isFinite(value)) return value;
        double scale = Math.pow(10.0, places);
        return Math.rint(value * scale) / scale;
    }
}
