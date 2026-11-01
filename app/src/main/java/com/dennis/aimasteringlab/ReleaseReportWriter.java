package com.dennis.aimasteringlab;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

final class ReleaseReportWriter {
    private ReleaseReportWriter() {}

    static void writeAll(ReleaseArtifacts artifacts) throws IOException, JSONException {
        write(artifacts.masteringReport, masteringMarkdown(artifacts));
        write(artifacts.translationReport, translationMarkdown(artifacts));
        write(artifacts.qcJson, qcJson(artifacts).toString(2));
        write(artifacts.verificationReport, verificationText(artifacts));
    }

    static String masteringMarkdown(ReleaseArtifacts a) {
        StringBuilder out = new StringBuilder(8192);
        out.append("# Dennis Mastering Studio v0.4.0 — Mastering Report\n\n");
        out.append("Generated: ").append(timestamp()).append("\n\n");
        out.append("## Authoritative source\n\n");
        appendAudio(out, a.source);
        out.append("\n## Verified primary master\n\n");
        appendAudio(out, a.master);
        out.append("\n## Ordered processing chain\n\n");
        if (a.source.noRenderNeeded) {
            out.append("1. Existing 24-bit/48 kHz source retained without re-rendering or re-dithering: ")
                    .append(a.source.noRenderReason).append(".\n");
        } else {
            out.append("1. Transparent input gain: ")
                    .append(formatSigned(a.masterRender.gainDb, 4)).append(" dB.\n");
            if (a.masterRender.limiterUsed) {
                out.append("2. Stereo-linked 5 ms look-ahead safety limiter, 150 ms release, working ceiling below -1 dBTP; measured maximum reduction ")
                        .append(format(a.masterRender.maximumLimiterReductionDb, 3)).append(" dB.\n");
            } else {
                out.append("2. Safety limiter bypassed because measured peaks did not require it.\n");
            }
            out.append("3. 24-bit triangular-PDF output quantization; post-render file reopened and remeasured.\n");
        }

        out.append("4. Consumer delivery: ")
                .append(codecLine(a.mp3)).append("; ").append(codecLine(a.aac)).append(".\n");
        out.append("5. Actual codec decodes were measured where the device supplied the required encoder and decoder.\n");
        out.append("6. No blanket EQ, bus compression, multiband compression, saturation or widening was applied.\n");

        out.append("\n## Vocal Humanity evidence\n\n");
        if (a.source.vocalHumanity == null) {
            out.append("No reliable lead-vocal edit evidence was available.\n");
        } else {
            out.append(a.source.vocalHumanity.compactSummary()).append(".\n\n")
                    .append("Any Vocal Humanity output is a separate centered stem-delta A/B test. The deterministic packaged master remains authoritative unless a human promotes the test.\n");
        }

        out.append("\n## AI diagnostic and optional correction gate\n\n");
        if (a.source.aiDiagnostics == null) {
            out.append("AI diagnostic was not available for this package.\n");
        } else {
            out.append(a.source.aiDiagnostics.compactSummary()).append(".\n\n");
            out.append("Stem trust: ").append(a.source.aiDiagnostics.stemTrustAssessment).append("\n\n");
            out.append("The packaged primary master follows the established deterministic release path. ")
                    .append("Any AI-guided output is a separate level-matched A/B test and is never promoted automatically.\n");
        }

        out.append("\n## Stem validation and reconstruction\n\n");
        out.append("WAV stems: ").append(a.source.wavStemCount)
                .append("; CRC verified: ").append(a.source.crcVerifiedStemCount)
                .append("; duration mismatches: ").append(a.source.stemDurationMismatchCount).append(".\n\n");
        if (a.source.commonStemTailShortfallSeconds > 0.0) {
            out.append("The stems end ").append(format(a.source.commonStemTailShortfallSeconds, 6))
                    .append(" seconds before the authoritative stereo mix. The original-only ending is preserved.\n\n");
        }
        out.append(StemReconstruction.compact(a.source.stemReconstruction)).append("\n\n");
        if (a.source.stemReconstruction != null) {
            out.append("Policy: ").append(a.source.stemReconstruction.mixPolicy).append("\n");
        }

        out.append("\n## Section and dynamics evidence\n\n");
        if (a.source.sections != null) {
            out.append("Method: ").append(a.source.sections.method).append(".\n\n");
            for (TrackSections.Segment segment : a.source.sections.segments) {
                out.append("- ").append(segment.role).append(": ")
                        .append(format(segment.startSeconds, 3)).append("–")
                        .append(format(segment.startSeconds + segment.durationSeconds, 3))
                        .append(" s, average ").append(format(segment.averageLufs, 2)).append(" LUFS.\n");
            }
            out.append("- Quiet-to-chorus energy lift: ")
                    .append(format(a.source.sections.quietToChorusLiftLu, 2)).append(" LU.\n");
        }
        out.append("\n## Formal gates\n\n");
        out.append("- Technical mastering: ").append(a.assessment.technicalVerdict)
                .append(" (").append(a.assessment.technicalScore).append("/100).\n");
        out.append("- Production/A&R proxy: ").append(a.assessment.productionArBand)
                .append(" (").append(a.assessment.productionArScore).append("/100).\n");
        out.append("- Human production/A&R review required: YES.\n");
        out.append("- Major-label claim: ").append(a.assessment.majorLabelClaim).append(".\n");

        out.append("\n## Important limitations\n\n");
        out.append("- True peak uses a 4× polyphase windowed-sinc implementation validated against independent software fixtures; it has not received external laboratory certification.\n");
        out.append("- Playback profiles are offline response simulations, not claims of listening through the physical devices.\n");
        out.append("- Learned musical-section labels are unsupervised candidates and require perceptual confirmation.\n");
        out.append("- The v0.4 AI models are per-track adaptive inference, not proof that a finding is audible.\n");
        out.append("- Optional mix correction is local and attenuation-only. Optional Vocal Humanity editing is capped, stem-delta based and never changes pitch/timing or performs denoising. Both require A/B review.\n");
        return out.toString();
    }

    static String translationMarkdown(ReleaseArtifacts a) {
        StringBuilder out = new StringBuilder(8192);
        out.append("# Device Translation Report\n\n");
        out.append("Method: ").append(a.translation.method).append(".\n\n");
        out.append("Composite sections:\n\n");
        for (TranslationComposite.SegmentMap segment : a.translationComposite.segments) {
            out.append("- ").append(segment.role).append(": source ")
                    .append(format(segment.sourceStartSeconds, 3)).append(" s for ")
                    .append(format(segment.sourceDurationSeconds, 3)).append(" s; composite start ")
                    .append(format(segment.compositeStartSeconds, 3)).append(" s.\n");
        }
        out.append("\n| Playback profile | Gate | Peak dBFS | Vocal-band share dB | Correlation | Chorus lift dB |\n");
        out.append("| --- | --- | ---: | ---: | ---: | ---: |\n");
        for (TranslationAnalyzer.DeviceResult device : a.translation.devices) {
            out.append("| ").append(device.name).append(" | ").append(device.verdict)
                    .append(" | ").append(format(device.peakDbfs, 2))
                    .append(" | ").append(format(device.vocalBandShareDb, 2))
                    .append(" | ").append(format(device.stereoCorrelation, 3))
                    .append(" | ").append(format(device.chorusLiftDb, 2)).append(" |\n");
        }
        out.append("\nFor bandwidth-limited phone profiles, the gate emphasizes preserved section lift, mid-band audibility, clipping risk and mono stability. Raw full-range correlation is not used as the sole criterion.\n");
        return out.toString();
    }

    static JSONObject qcJson(ReleaseArtifacts a) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("schema", "dennis.mastering.release-qc.v0.4");
        root.put("app", "Dennis Mastering Studio 0.4.0");
        root.put("generated_utc", timestamp());
        root.put("source", audio(a.source));
        root.put("primary_master", audio(a.master));
        root.put("render", render(a.masterRender));
        root.put("stem_inventory", stems(a.source));
        JSONObject stemComparison = new JSONObject();
        stemComparison.put("wav_stems", a.source.wavStemCount);
        stemComparison.put("crc_verified_wav_stems", a.source.crcVerifiedStemCount);
        stemComparison.put("duration_mismatch_count", a.source.stemDurationMismatchCount);
        put(stemComparison, "common_original_only_tail_seconds",
                a.source.commonStemTailShortfallSeconds, 6);
        root.put("stem_comparison", stemComparison);
        root.put("stem_reconstruction", reconstruction(a.source.stemReconstruction));
        root.put("sections", sections(a.source.sections));
        root.put("ai_diagnostic", ReportWriter.aiDiagnostic(a.source.aiDiagnostics));
        root.put("vocal_humanity", ReportWriter.vocalHumanity(a.source.vocalHumanity));
        JSONObject codecs = new JSONObject();
        codecs.put("mp3_320", codecEncodeDecode(a.mp3, a.mp3Decoded));
        codecs.put("aac_256", codecEncodeDecode(a.aac, a.aacDecoded));
        codecs.put("translation_mp3_320",
                codecEncodeDecode(a.translationMp3, a.translationMp3Decoded));
        codecs.put("sbc_328_proxy", a.sbc == null ? JSONObject.NULL : audio(a.sbc.verification));
        root.put("codec_qc", codecs);
        JSONArray devices = new JSONArray();
        for (TranslationAnalyzer.DeviceResult device : a.translation.devices) {
            JSONObject item = new JSONObject();
            item.put("name", device.name);
            item.put("verdict", device.verdict);
            item.put("mono", device.mono);
            put(item, "peak_dbfs", device.peakDbfs, 3);
            put(item, "vocal_band_share_db", device.vocalBandShareDb, 3);
            put(item, "stereo_correlation", device.stereoCorrelation, 6);
            put(item, "chorus_lift_db", device.chorusLiftDb, 3);
            put(item, "busy_lift_db", device.busyLiftDb, 3);
            item.put("clipping_risk", device.clippingRisk);
            item.put("mono_phase_risk", device.monoPhaseRisk);
            devices.put(item);
        }
        root.put("device_translation", devices);
        JSONObject gate = new JSONObject();
        gate.put("technical_score", a.assessment.technicalScore);
        gate.put("technical_verdict", a.assessment.technicalVerdict);
        gate.put("production_ar_proxy_score", a.assessment.productionArScore);
        gate.put("production_ar_band", a.assessment.productionArBand);
        gate.put("human_review_required", a.assessment.humanReviewRequired);
        gate.put("major_label_claim", a.assessment.majorLabelClaim);
        gate.put("technical_findings", new JSONArray(a.assessment.technicalFindings));
        gate.put("production_findings", new JSONArray(a.assessment.productionFindings));
        root.put("formal_gate", gate);
        JSONObject files = new JSONObject();
        for (Map.Entry<String, String> entry : a.sha256.entrySet()) files.put(entry.getKey(), entry.getValue());
        root.put("sha256", files);
        JSONArray limitations = new JSONArray();
        limitations.put("True-peak implementation is independently regression-validated but not externally laboratory-certified.");
        limitations.put("Device translation uses response-profile simulations, not physical playback or microphone capture.");
        limitations.put("Production/A&R scoring is advisory and cannot certify label acceptance.");
        limitations.put("Codec availability depends on the Android device's installed hardware/software encoders.");
        limitations.put("16-bit/44.1 kHz WAV and FLAC remain intentionally omitted from the Dennis delivery profile.");
        limitations.put("The AI diagnostic is track-trained unsupervised inference and does not replace perceptual review.");
        limitations.put("Mix correction is an optional attenuation-only A/B test. Vocal Humanity is an optional capped stem-delta A/B test; neither is automatically promoted.");
        limitations.put("Vocal Humanity does not autotune, hard-gate, denoise, regenerate or retime the singer.");
        root.put("limitations", limitations);
        return root;
    }

    static String verificationText(ReleaseArtifacts a) {
        StringBuilder out = new StringBuilder();
        out.append("DENNIS MASTERING STUDIO v0.4.0 FINAL VERIFICATION\n\n");
        out.append("Technical gate: ").append(a.assessment.technicalVerdict)
                .append(" (").append(a.assessment.technicalScore).append("/100)\n");
        out.append("Production/A&R proxy: ").append(a.assessment.productionArBand)
                .append(" (").append(a.assessment.productionArScore).append("/100; human review required)\n\n");
        out.append("SHA-256\n");
        for (Map.Entry<String, String> entry : a.sha256.entrySet()) {
            out.append(entry.getValue()).append("  ").append(entry.getKey()).append("\n");
        }
        return out.toString();
    }

    private static JSONObject audio(AnalysisResult result) throws JSONException {
        if (result == null) return null;
        JSONObject out = new JSONObject();
        out.put("name", result.sourceName);
        out.put("sha256", result.sourceSha256);
        out.put("sample_rate_hz", result.wav.sampleRate);
        out.put("bits_per_sample", result.wav.bitsPerSample);
        out.put("channels", result.wav.channels);
        out.put("frames", result.wav.frames);
        put(out, "duration_seconds", result.wav.durationSeconds, 6);
        put(out, "integrated_lufs_i", result.integratedLufs, 3);
        put(out, "true_peak_dbtp", result.truePeakEstimateDbtp, 3);
        put(out, "sample_peak_dbfs", result.samplePeakDbfs, 3);
        put(out, "lra_lu", result.lraLu, 3);
        put(out, "stereo_correlation", result.stereoCorrelation, 6);
        put(out, "below_160hz_correlation", result.lowBandCorrelation, 6);
        put(out, "mono_fold_rms_dbfs", result.monoFoldRmsDbfs, 3);
        put(out, "mono_fold_peak_dbfs", result.monoFoldPeakDbfs, 3);
        put(out, "mono_fold_level_change_db", result.monoFoldLossDb, 3);
        put(out, "rms_dbfs", result.rmsDbfs, 3);
        put(out, "crest_factor_db", result.crestFactorDb, 3);
        out.put("clipped_samples", result.clippedSamples);
        out.put("non_finite_samples", result.nonFiniteSamples);
        out.put("internal_zero_runs", result.internalZeroRuns);
        put(out, "longest_internal_zero_seconds", result.longestInternalZeroSeconds, 6);
        put(out, "first_frame_peak_linear", result.firstFramePeak, 9);
        put(out, "last_frame_peak_linear", result.lastFramePeak, 9);
        JSONArray dc = new JSONArray();
        for (double value : result.dcOffset) dc.put(round(value, 10));
        out.put("dc_offset_by_channel", dc);
        out.put("file_edge_assessment",
                result.firstFramePeak < 0.999 && result.lastFramePeak < 0.999
                        ? "NO FULL-SCALE FILE-EDGE SAMPLE; PRESERVE ORIGINAL INTRO AND ENDING"
                        : "REVIEW FULL-SCALE FILE EDGE");
        out.put("meter", result.meterVersion);
        return out;
    }

    private static JSONObject render(MasterRenderer.Result result) throws JSONException {
        if (result == null) return null;
        JSONObject out = new JSONObject();
        put(out, "input_gain_db", result.gainDb, 4);
        out.put("limiter_used", result.limiterUsed);
        put(out, "maximum_limiter_reduction_db", result.maximumLimiterReductionDb, 4);
        out.put("dynamics_protected", result.dynamicsProtected);
        out.put("exact_target_reached", result.exactTargetReached);
        out.put("render_passes", result.renderPasses);
        out.put("quantizer_clips", result.quantizerClips);
        out.put("ai_guided_test", result.aiGuided);
        if (result.aiGuided) {
            out.put("ai_correction_events", result.aiCorrectionEvents);
            put(out, "maximum_ai_correction_db", result.maximumAiCorrectionDb, 4);
            out.put("ai_correction_summary", result.aiCorrectionSummary);
        }
        out.put("vocal_humanity_test", result.vocalHumanityGuided);
        if (result.vocalHumanityGuided) {
            out.put("lead_stem_path", result.vocalStemPath);
            out.put("phrase_rides", result.vocalRideCount);
            out.put("de_ess_events", result.vocalDeEssEvents);
            put(out, "actual_stem_ride_down_db", result.vocalRideDownDb, 4);
            put(out, "actual_stem_ride_up_db", result.vocalRideUpDb, 4);
            put(out, "maximum_event_de_ess_db", result.vocalMaximumDeEssDb, 4);
            put(out, "processed_minus_original_delta_scale", result.vocalDeltaScale, 4);
            out.put("vocal_correction_summary", result.vocalCorrectionSummary);
        }
        return out;
    }

    private static JSONObject reconstruction(StemReconstruction.Result result) throws JSONException {
        if (result == null) return null;
        JSONObject out = new JSONObject();
        out.put("verdict", result.verdict);
        out.put("stems_compared", result.stemsCompared);
        put(out, "waveform_correlation", result.waveformCorrelation, 8);
        put(out, "least_squares_gain_db", result.leastSquaresGainDb, 4);
        put(out, "residual_snr_db", result.residualSnrDb, 3);
        put(out, "start_lag_ms", result.startLagMs, 3);
        put(out, "middle_lag_ms", result.middleLagMs, 3);
        put(out, "end_lag_ms", result.endLagMs, 3);
        put(out, "measured_drift_ms", result.measuredDriftMs, 3);
        out.put("raw_stem_sum_allowed", result.rawStemSumAllowed);
        out.put("likely_separation_derived", result.likelySeparationDerived);
        out.put("mix_policy", result.mixPolicy);
        if (result.error != null) out.put("error", result.error);
        return out;
    }

    private static JSONArray stems(AnalysisResult source) throws JSONException {
        JSONArray out = new JSONArray();
        for (StemInventory.Item stem : source.stems) {
            JSONObject item = new JSONObject();
            item.put("path", stem.path);
            item.put("wav", stem.wav);
            item.put("directory", stem.directory);
            item.put("crc32", stem.crc32);
            item.put("crc_verified", stem.crcVerified);
            if (stem.wavInfo != null) {
                item.put("sample_rate_hz", stem.wavInfo.sampleRate);
                item.put("bits_per_sample", stem.wavInfo.bitsPerSample);
                item.put("channels", stem.wavInfo.channels);
                put(item, "duration_seconds", stem.wavInfo.durationSeconds, 6);
                put(item, "full_duration_rms_dbfs", stem.rmsDbfs, 3);
                put(item, "track_relative_active_rms_dbfs", stem.activeRmsDbfs, 3);
                put(item, "track_relative_active_fraction", stem.activeFraction, 6);
                put(item, "peak_dbfs", stem.peakDbfs, 3);
                put(item, "dc_offset", stem.dcOffset, 10);
                put(item, "crest_factor_db", stem.crestFactorDb, 3);
                item.put("clipped_samples", stem.clippedSamples);
            }
            if (stem.error != null) item.put("error", stem.error);
            out.put(item);
        }
        return out;
    }

    private static JSONArray sections(TrackSections sections) throws JSONException {
        JSONArray out = new JSONArray();
        if (sections == null) return out;
        for (TrackSections.Segment segment : sections.segments) {
            JSONObject item = new JSONObject();
            item.put("role", segment.role);
            put(item, "start_seconds", segment.startSeconds, 3);
            put(item, "duration_seconds", segment.durationSeconds, 3);
            put(item, "average_lufs", segment.averageLufs, 3);
            put(item, "confidence", segment.confidence, 4);
            item.put("learned_state", segment.learnedState >= 0
                    ? segment.learnedState : JSONObject.NULL);
            out.put(item);
        }
        return out;
    }

    private static JSONObject codecEncodeDecode(AndroidCodecEncoder.Result encoder,
                                                 AndroidCodecDecoder.Result decoder)
            throws JSONException {
        JSONObject out = new JSONObject();
        if (encoder == null) {
            out.put("available", false);
            return out;
        }
        out.put("available", encoder.available);
        out.put("codec", encoder.codec);
        out.put("bitrate", encoder.bitrate);
        if (encoder.encoderName != null) out.put("encoder", encoder.encoderName);
        if (encoder.error != null) out.put("encoder_error", encoder.error);
        if (decoder != null) {
            if (decoder.decoderName != null) out.put("decoder", decoder.decoderName);
            if (decoder.error != null) out.put("decoder_error", decoder.error);
            if (decoder.verification != null) out.put("decoded_measurements", audio(decoder.verification));
        }
        return out;
    }

    private static String codecLine(AndroidCodecEncoder.Result result) {
        if (result == null || !result.available) {
            return result != null && result.error != null ? "unavailable (" + result.error + ")" : "unavailable";
        }
        return result.codec + " at " + (result.bitrate / 1000) + " kbps via " + result.encoderName;
    }

    private static void appendAudio(StringBuilder out, AnalysisResult value) {
        if (value == null) {
            out.append("Not available.\n");
            return;
        }
        out.append("- File: ").append(value.sourceName).append("\n")
                .append("- Format: ").append(value.wav.bitsPerSample).append("-bit / ")
                .append(value.wav.sampleRate).append(" Hz / ").append(value.wav.channels).append(" channels\n")
                .append("- Duration: ").append(format(value.wav.durationSeconds, 6)).append(" s\n")
                .append("- Integrated loudness: ").append(format(value.integratedLufs, 3)).append(" LUFS-I\n")
                .append("- True peak: ").append(format(value.truePeakEstimateDbtp, 3)).append(" dBTP\n")
                .append("- LRA: ").append(format(value.lraLu, 3)).append(" LU\n")
                .append("- Stereo correlation: ").append(format(value.stereoCorrelation, 6)).append("\n")
                .append("- Below-160 Hz correlation: ").append(format(value.lowBandCorrelation, 6)).append("\n")
                .append("- SHA-256: `").append(value.sourceSha256).append("`\n");
    }

    private static void write(File file, String text) throws IOException {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(text);
        }
    }

    private static void put(JSONObject object, String key, double value, int places)
            throws JSONException {
        object.put(key, Double.isFinite(value) ? round(value, places) : JSONObject.NULL);
    }

    private static String format(double value, int places) {
        return Double.isFinite(value) ? String.format(Locale.US, "%." + places + "f", value) : "n/a";
    }

    private static String formatSigned(double value, int places) {
        return Double.isFinite(value) ? String.format(Locale.US, "%+." + places + "f", value) : "n/a";
    }

    private static double round(double value, int places) {
        double scale = Math.pow(10.0, places);
        return Math.rint(value * scale) / scale;
    }

    private static String timestamp() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }
}
