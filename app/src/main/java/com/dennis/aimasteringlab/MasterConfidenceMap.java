package com.dennis.aimasteringlab;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Section-by-section explanation of applied, rejected and untouched evidence. */
final class MasterConfidenceMap {
    static final class Section {
        String role;
        double startSeconds;
        double endSeconds;
        double mappingConfidence;
        boolean globalProcessingApplies;
        boolean sectionSpecificChange;
        final List<String> changed = new ArrayList<>();
        final List<String> rejected = new ArrayList<>();
        final List<String> untouched = new ArrayList<>();
    }

    static final class Result {
        String method = "v0.14.5 Sonic Finish macro arrangement summary + selected-chain global/local event audit";
        String aggregationMethod = "adjacent learned microstates summarized into duration-scaled macro windows; low-occupancy states are not labeled silence unless measured at or below -55 LUFS";
        String selectedStrategy;
        int rawLearnedSectionCount;
        final List<Section> sections = new ArrayList<>();
        final List<String> globalDecisions = new ArrayList<>();
        final List<String> limitations = new ArrayList<>();
    }

    private MasterConfidenceMap() {}

    static Result build(AnalysisResult source,
                        FullAiMasteringEngine.Result engine,
                        FullAiReleaseQc.Result qc) {
        Result out = new Result();
        FullAiMasteringEngine.Candidate selected = qc != null && qc.finalCandidate != null
                ? qc.finalCandidate : engine.selected;
        out.selectedStrategy = selected == null ? "none" : selected.strategy;
        buildSections(source, out);
        if (selected == null || selected.render == null) {
            out.limitations.add("No selected render was available; the map contains analysis evidence only.");
            return out;
        }
        addGlobalChain(selected, source, out);
        addContinuity(source, out);
        addDiagnostics(source, selected, out);
        addStereoIntelligence(source, selected, out);
        addAdvancedIntelligence(source, selected, out);
        addProfessionalIntelligence(source, selected, out);
        addSourceGuard(source, out);
        addNextGenerationIntelligence(source, selected, out);
        addStemEvents(source, selected, out);
        protectEdges(source, out);
        for (Section section : out.sections) {
            if (!section.sectionSpecificChange) {
                section.untouched.add(section.globalProcessingApplies
                        ? "No additional section-specific repair envelope was applied; only the listed global chain affected this section."
                        : "No section-specific repair was applied; the local musical waveform was retained.");
            }
            if (section.rejected.isEmpty()) {
                section.untouched.add("No rejected high-confidence local correction was recorded in this section.");
            }
        }
        out.limitations.add("Section roles are learned arrangement candidates, not confirmed composer labels.");
        out.limitations.add("Changed means an actual selected render stage; rejected means evidence was detected but did not clear every inference, coverage, promotion or safety gate.");
        out.limitations.add("The map is an engineering audit and does not claim physical listening or artistic approval.");
        return out;
    }

    static JSONObject json(Result map) throws JSONException {
        JSONObject out = new JSONObject();
        out.put("method", map.method);
        out.put("aggregation_method", map.aggregationMethod);
        out.put("raw_learned_section_count", map.rawLearnedSectionCount);
        out.put("reported_macro_section_count", map.sections.size());
        int anyChanged = 0;
        int globalChanged = 0;
        int sectionSpecificChanged = 0;
        for (Section section : map.sections) {
            if (!section.changed.isEmpty()) anyChanged++;
            if (section.globalProcessingApplies) globalChanged++;
            if (section.sectionSpecificChange) sectionSpecificChanged++;
        }
        out.put("sections_with_any_selected_change", anyChanged);
        out.put("sections_with_global_processing", globalChanged);
        out.put("sections_with_section_specific_change", sectionSpecificChanged);
        out.put("selected_strategy", map.selectedStrategy);
        out.put("global_decisions", new JSONArray(map.globalDecisions));
        JSONArray sections = new JSONArray();
        for (Section section : map.sections) {
            JSONObject item = new JSONObject();
            item.put("role", section.role);
            item.put("start_seconds", round(section.startSeconds, 4));
            item.put("end_seconds", round(section.endSeconds, 4));
            item.put("mapping_confidence", finite(section.mappingConfidence));
            item.put("global_processing_applies", section.globalProcessingApplies);
            item.put("section_specific_change", section.sectionSpecificChange);
            item.put("changed", new JSONArray(section.changed));
            item.put("rejected_or_review_only", new JSONArray(section.rejected));
            item.put("left_untouched", new JSONArray(section.untouched));
            sections.put(item);
        }
        out.put("sections", sections);
        out.put("limitations", new JSONArray(map.limitations));
        return out;
    }

    static String text(Result map) {
        StringBuilder out = new StringBuilder();
        out.append("DENNIS AI MASTERING LAB — MASTER CONFIDENCE MAP\n")
                .append("Selected strategy: ").append(map.selectedStrategy).append("\n")
                .append("Method: ").append(map.method).append("\n")
                .append("Section summary: ").append(map.rawLearnedSectionCount)
                .append(" learned microstates -> ").append(map.sections.size())
                .append(" macro sections\n")
                .append("Aggregation: ").append(map.aggregationMethod).append("\n\n")
                .append("GLOBAL DECISIONS\n");
        for (String decision : map.globalDecisions) out.append("- ").append(decision).append('\n');
        for (Section section : map.sections) {
            out.append(String.format(Locale.US, "\n%.3f–%.3f s  %s  (map confidence %.2f)\n",
                    section.startSeconds, section.endSeconds, section.role,
                    section.mappingConfidence));
            out.append("  CHANGED\n");
            if (section.changed.isEmpty()) out.append("  - None\n");
            else for (String value : section.changed) out.append("  - ").append(value).append('\n');
            out.append("  REJECTED / REVIEW ONLY\n");
            if (section.rejected.isEmpty()) out.append("  - None\n");
            else for (String value : section.rejected) out.append("  - ").append(value).append('\n');
            out.append("  LEFT UNTOUCHED\n");
            if (section.untouched.isEmpty()) out.append("  - None\n");
            else for (String value : section.untouched) out.append("  - ").append(value).append('\n');
        }
        out.append("\nLIMITATIONS\n");
        for (String value : map.limitations) out.append("- ").append(value).append('\n');
        return out.toString();
    }

    private static void buildSections(AnalysisResult source, Result out) {
        if (source.sections != null && !source.sections.learnedStructure.isEmpty()) {
            List<TrackSections.Segment> learned = source.sections.learnedStructure;
            out.rawLearnedSectionCount = learned.size();
            double duration = Math.max(0.0, source.wav.durationSeconds);
            int target = macroSectionTarget(duration, learned.size());
            for (int index = 0; index < target; index++) {
                double start = duration * index / target;
                double end = index + 1 == target
                        ? duration : duration * (index + 1) / target;
                MacroStats stats = summarize(learned, start, end,
                        source.integratedLufs);
                Section section = new Section();
                section.role = macroRole(stats, index, target);
                section.startSeconds = start;
                section.endSeconds = end;
                section.mappingConfidence = Double.isFinite(stats.confidence)
                        ? stats.confidence : source.sections.mappingConfidence;
                if (section.endSeconds > section.startSeconds) out.sections.add(section);
            }
        }
        if (out.sections.isEmpty()) {
            Section section = new Section();
            section.role = "whole_song_fallback";
            section.startSeconds = 0.0;
            section.endSeconds = source.wav.durationSeconds;
            section.mappingConfidence = 0.25;
            out.sections.add(section);
        }
    }

    private static int macroSectionTarget(double duration, int rawCount) {
        if (rawCount <= 1) return 1;
        int target;
        if (duration < 60.0) {
            target = Math.max(1, (int) Math.round(duration / 18.0));
        } else {
            target = Math.max(8, Math.min(15,
                    (int) Math.round(duration / 18.0)));
        }
        return Math.max(1, Math.min(rawCount, target));
    }

    private static MacroStats summarize(List<TrackSections.Segment> learned,
                                        double start, double end,
                                        double integratedLufs) {
        MacroStats out = new MacroStats();
        double confidenceWeight = 0.0;
        double energy = 0.0;
        double levelWeight = 0.0;
        for (TrackSections.Segment segment : learned) {
            double segmentEnd = segment.startSeconds + segment.durationSeconds;
            double overlap = Math.max(0.0,
                    Math.min(end, segmentEnd) - Math.max(start, segment.startSeconds));
            if (overlap <= 0.0) continue;
            String role = segment.role == null ? "" : segment.role;
            if (role.contains("high_energy")) out.highWeight += overlap;
            else if (role.contains("quieter")) out.quietWeight += overlap;
            else if (role.contains("silence_or_tail")) out.lowOccupancyWeight += overlap;
            else out.alternateWeight += overlap;
            if (Double.isFinite(segment.confidence)) {
                out.confidence += overlap * segment.confidence;
                confidenceWeight += overlap;
            }
            if (Double.isFinite(segment.averageLufs)) {
                energy += overlap * Math.pow(10.0,
                        (segment.averageLufs + 0.691) / 10.0);
                levelWeight += overlap;
            }
        }
        out.confidence = confidenceWeight > 0.0
                ? out.confidence / confidenceWeight : Double.NaN;
        out.averageLufs = levelWeight > 0.0
                ? -0.691 + 10.0 * Math.log10(Math.max(1.0e-30,
                    energy / levelWeight))
                : integratedLufs;
        return out;
    }

    private static String macroRole(MacroStats stats, int index, int count) {
        boolean intro = index == 0;
        boolean outro = index == count - 1;
        String role;
        if (stats.averageLufs <= -55.0) {
            role = outro ? "silence_or_tail_candidate" : "measured_silence_candidate";
        } else {
            double strongestActive = Math.max(stats.highWeight,
                    Math.max(stats.quietWeight, stats.alternateWeight));
            if (stats.lowOccupancyWeight > strongestActive) {
                role = "low_occupancy_transition_candidate";
            } else if (stats.highWeight >= stats.quietWeight
                    && stats.highWeight >= stats.alternateWeight) {
                role = "recurring_high_energy_macro_candidate";
            } else if (stats.quietWeight >= stats.alternateWeight) {
                role = "recurring_quieter_macro_candidate";
            } else {
                role = "alternate_arrangement_macro_candidate";
            }
        }
        if (intro) return "intro_" + role;
        if (outro && !role.contains("tail")) return "outro_" + role;
        return role;
    }

    private static final class MacroStats {
        double highWeight;
        double quietWeight;
        double alternateWeight;
        double lowOccupancyWeight;
        double confidence;
        double averageLufs;
    }

    private static void addGlobalChain(FullAiMasteringEngine.Candidate selected,
                                       AnalysisResult source, Result out) {
        AutonomousMasteringBrain.Plan plan = selected.plan;
        AutonomousMasteringRenderer.Result render = selected.render;
        List<String> applied = new ArrayList<>();
        if (Math.abs(render.master.gainDb) >= 0.001) {
            String decision = String.format(Locale.US,
                    "Verified flat loudness gain %+.4f dB; final integrated loudness %.4f LUFS-I.",
                    render.master.gainDb, render.master.verification.integratedLufs);
            out.globalDecisions.add(decision);
            applied.add(decision);
        } else {
            out.globalDecisions.add("No loudness gain was required after verification.");
        }
        if (plan != null) {
            addEq(out.globalDecisions, applied, "110 Hz low shelf", plan.lowShelfDb);
            addEq(out.globalDecisions, applied, "320 Hz low-mid bell", plan.lowMidDb);
            addEq(out.globalDecisions, applied, "3.2 kHz presence bell", plan.presenceDb);
            addEq(out.globalDecisions, applied, "9 kHz high shelf", plan.highShelfDb);
            if (Math.abs(plan.sideScale - 1.0) > 0.0005) {
                String decision = String.format(Locale.US,
                        "Stereo side scale %.5f was applied; widening was not used.",
                        plan.sideScale);
                out.globalDecisions.add(decision);
                applied.add(decision);
            }
        }
        if (render.maximumCompressionReductionDb > 0.001) {
            String decision = String.format(Locale.US,
                    "Stereo-linked compression reached %.3f dB maximum reduction.",
                    render.maximumCompressionReductionDb);
            out.globalDecisions.add(decision);
            applied.add(decision);
        } else out.globalDecisions.add("Bus compression bypassed.");
        if (render.master.limiterUsed) {
            String decision = String.format(Locale.US,
                    "Safety limiting used %s (%.3f ms look-ahead / %.3f ms release) and reached %.3f dB maximum reduction.",
                    render.master.limiterProfile,
                    render.master.limiterLookaheadMs,
                    render.master.limiterReleaseMs,
                    render.master.maximumLimiterReductionDb);
            out.globalDecisions.add(decision);
            applied.add(decision);
        } else out.globalDecisions.add("Limiter bypassed; flat gain remained below the true-peak ceiling.");
        out.globalDecisions.add(String.format(Locale.US,
                "Final true peak %.3f dBTP; source duration %.6f seconds retained.",
                render.master.verification.truePeakEstimateDbtp,
                source.wav.durationSeconds));
        for (Section section : out.sections) {
            section.globalProcessingApplies = !applied.isEmpty();
            for (String decision : applied) {
                section.changed.add("[GLOBAL — applies throughout this section] "
                        + decision);
            }
        }
    }

    private static void addContinuity(AnalysisResult source, Result out) {
        ClickDropoutRepair.Result repair = source.clickDropoutRepair;
        if (repair != null && repair.repairApplied) {
            for (ClickDropoutRepair.Event event : repair.events) {
                double start = event.startFrame / (double) source.wav.sampleRate;
                double end = (event.endFrame + 1L) / (double) source.wav.sampleRate;
                String label = event.kind == ClickDropoutRepair.Kind.ISOLATED_CLICK
                        ? "Isolated click repaired"
                        : event.kind == ClickDropoutRepair.Kind.ISOLATED_CLIP_RUN
                        ? "Isolated digital clip plateau interpolated"
                        : "Short exact-zero dropout interpolated";
                addChanged(out, start, end, String.format(Locale.US,
                        "%s at %.6f–%.6f s (confidence %.3f).",
                        label, start, end, event.confidence));
            }
        }
        for (AnalysisResult.ZeroRegion region : source.internalZeroRegions) {
            double start = region.startSeconds(source.wav.sampleRate);
            double end = region.endSeconds(source.wav.sampleRate);
            addRejected(out, start, end, String.format(Locale.US,
                    "Source exact-zero region %.6f–%.6f s (%d frames) remained review-only; final-output dither is not classified as continuity repair.",
                    start, end, region.frames()));
        }
    }

    private static void addDiagnostics(AnalysisResult source,
                                       FullAiMasteringEngine.Candidate selected,
                                       Result out) {
        if (source.aiDiagnostics == null) return;
        boolean eventStageUsed = selected.plan != null && selected.plan.eventScale > 0.0;
        for (AdaptiveDiagnosticModel.Event event : source.aiDiagnostics.events) {
            String description = String.format(Locale.US,
                    "%s %.3f–%.3f s (confidence %.3f)",
                    AdaptiveDiagnosticModel.label(event.type), event.startSeconds,
                    event.endSeconds, event.confidence);
            if (event.autoCorrection && eventStageUsed) {
                addChanged(out, event.startSeconds, event.endSeconds,
                        description + String.format(Locale.US,
                                "; selected cut %.3f dB after mode scaling.",
                                event.correctionDb * selected.plan.eventScale));
            } else {
                addRejected(out, event.startSeconds, event.endSeconds,
                        description + "; retained as diagnostic/review-only evidence.");
            }
        }
    }

    private static void addStereoIntelligence(AnalysisResult source,
                                               FullAiMasteringEngine.Candidate selected,
                                               Result out) {
        if (source.stereoIntelligence == null) return;
        for (StereoIntelligenceModel.Event event : source.stereoIntelligence.events) {
            if (!event.automatic) continue;
            String description = String.format(Locale.US,
                    "%s evidence %.3f–%.3f s (confidence %.3f)",
                    event.kind.name().toLowerCase(Locale.US), event.startSeconds,
                    event.endSeconds, event.confidence);
            if (selected.intelligenceAware
                    && modeContains(selected.intelligenceMode, event.kind)) {
                addChanged(out, event.startSeconds, event.endSeconds,
                        description + "; authoritative-stereo module promoted.");
            } else {
                addRejected(out, event.startSeconds, event.endSeconds,
                        description + "; module candidate was not promoted.");
            }
        }
    }

    private static void addStemEvents(AnalysisResult source,
                                      FullAiMasteringEngine.Candidate selected,
                                      Result out) {
        if (!selected.stemAware || selected.stemAssist == null) return;
        StemAssistRenderer.Mode mode = selected.stemAssist.mode;
        boolean vocalStageUsed = mode == StemAssistRenderer.Mode.VOCAL_HUMANITY
                || mode == StemAssistRenderer.Mode.COMBINED
                || mode == StemAssistRenderer.Mode.ANALYSIS_ONLY_GUIDED;
        boolean maskingStageUsed = mode == StemAssistRenderer.Mode.MASKING_POCKETS
                || mode == StemAssistRenderer.Mode.COMBINED
                || mode == StemAssistRenderer.Mode.ANALYSIS_ONLY_GUIDED;
        if (vocalStageUsed && source.vocalHumanity != null) {
            for (VocalHumanityModel.Phrase phrase : source.vocalHumanity.phrases) {
                if (Math.abs(phrase.rideDb) < 0.001) continue;
                addChanged(out, phrase.startSeconds, phrase.endSeconds,
                        String.format(Locale.US,
                                "Vocal phrase control %+.3f dB at %.3f–%.3f s.",
                                phrase.rideDb, phrase.startSeconds, phrase.endSeconds));
            }
            for (VocalHumanityModel.DeEssEvent event : source.vocalHumanity.deEssEvents) {
                addChanged(out, event.startSeconds, event.endSeconds,
                        String.format(Locale.US,
                                "Event de-essing %.3f dB at %.3f–%.3f s.",
                                event.attenuationDb, event.startSeconds, event.endSeconds));
            }
        }
        if (maskingStageUsed && source.maskingPockets != null) {
            for (MaskingPocketModel.Event event : source.maskingPockets.events) {
                addChanged(out, event.startSeconds, event.endSeconds,
                        String.format(Locale.US,
                                "Masking pocket on %s, %s, %.3f dB at %.3f–%.3f s.",
                                event.stemPath, event.bandLabel, event.attenuationDb,
                                event.startSeconds, event.endSeconds));
            }
        }
    }

    private static void addAdvancedIntelligence(
            AnalysisResult source,
            FullAiMasteringEngine.Candidate selected,
            Result out) {
        if (source.advancedIntelligence == null) return;
        for (AdvancedStereoModel.Event event
                : source.advancedIntelligence.events) {
            String description = String.format(Locale.US,
                    "%s evidence %.3f–%.3f s (confidence %.3f)",
                    event.kind.name().toLowerCase(Locale.US),
                    event.startSeconds, event.endSeconds, event.confidence);
            if (event.kind == AdvancedStereoModel.Kind.TRANSIENT_PROTECTED) {
                addUntouched(out, event.startSeconds, event.endSeconds,
                        description + "; natural attack explicitly protected from automatic control.");
            } else if (event.automatic && selected.advancedAware
                    && AdvancedStereoModel.kindInMode(event.kind,
                    selected.advancedMode)) {
                addChanged(out, event.startSeconds, event.endSeconds,
                        description + String.format(Locale.US,
                                "; section/artifact/transient module promoted, nominal edit %+.3f dB.",
                                event.primaryDb));
            } else {
                addRejected(out, event.startSeconds, event.endSeconds,
                        description + (event.automatic
                                ? "; section/artifact/transient candidate was not promoted."
                                : "; retained as diagnostic/review-only evidence."));
            }
        }
    }

    private static void addProfessionalIntelligence(
            AnalysisResult source,
            FullAiMasteringEngine.Candidate selected,
            Result out) {
        if (source.professionalIntelligence == null) return;
        for (ProfessionalMasteringModel.Event event
                : source.professionalIntelligence.events) {
            String description = String.format(Locale.US,
                    "%s evidence %.3f–%.3f s (confidence %.3f)",
                    event.kind.name().toLowerCase(Locale.US),
                    event.startSeconds, event.endSeconds,
                    event.confidence);
            if (event.automatic && selected.professionalAware
                    && ProfessionalMasteringRenderer.kindInMode(event.kind,
                    selected.professionalMode)) {
                addChanged(out, event.startSeconds, event.endSeconds,
                        description + String.format(Locale.US,
                                "; v0.8 module promoted, nominal edit %+.3f dB; no widening used.",
                                event.primaryDb));
            } else if (event.kind == ProfessionalMasteringModel.Kind
                    .DEPTH_FLAT_DIAGNOSTIC) {
                addRejected(out, event.startSeconds, event.endSeconds,
                        description + "; retained as diagnostic because correction would require automatic widening, which is prohibited.");
            } else {
                addRejected(out, event.startSeconds, event.endSeconds,
                        description + (event.automatic
                                ? "; v0.8 candidate was not promoted."
                                : "; retained as diagnostic/review-only evidence."));
            }
        }
        if (selected.professionalAware
                && selected.professionalIntelligence != null
                && selected.professionalIntelligence
                .limiterOptimisation != null) {
            out.globalDecisions.add("Limiter/Crest Optimisation: "
                    + selected.professionalIntelligence
                    .limiterOptimisation.decision);
        }
    }

    private static void addSourceGuard(AnalysisResult source, Result out) {
        if (source.sourceGuard == null) return;
        out.globalDecisions.add("Source guard: "
                + source.sourceGuard.compactSummary());
        if (source.sourceGuard.secondPassGuard) {
            for (Section section : out.sections) {
                section.rejected.add("Broad second-pass mastering and limiter escalation were blocked by previous-master evidence.");
            }
        }
        if (source.sourceGuard.highBoostBlocked) {
            for (Section section : out.sections) {
                section.rejected.add("High-frequency boosting was blocked by source-aware codec/previous-master protection.");
            }
        }
    }

    private static void addNextGenerationIntelligence(
            AnalysisResult source,
            FullAiMasteringEngine.Candidate selected,
            Result out) {
        NextGenerationMasteringModel.Result model =
                source.nextGenerationIntelligence;
        if (model == null) return;
        boolean promoted = selected.nextGenerationAware;
        NextGenerationMasteringRenderer.Mode mode =
                selected.nextGenerationMode;
        if (model.translation != null) {
            for (TranslationConsensusModel.Event e : model.translation.events) {
                String text = e.evidence;
                if (e.automatic && promoted
                        && NextGenerationMasteringRenderer.inMode(mode,
                        NextGenerationMasteringRenderer.Mode
                        .TRANSLATION_CONSENSUS)) {
                    addChanged(out, e.startSeconds, e.endSeconds,
                            text + String.format(Locale.US,
                            "; selected translation cut %.3f dB.",
                            e.attenuationDb));
                } else {
                    addRejected(out, e.startSeconds, e.endSeconds,
                            text + (e.automatic
                                    ? "; source-aware candidate was not promoted."
                                    : "; diagnostic-only."));
                }
            }
        }
        if (model.tones != null) {
            for (PersistentToneModel.Event e : model.tones.events) {
                String text = String.format(Locale.US,
                        "%s %.2f Hz: %s",
                        e.kind.name().toLowerCase(Locale.US),
                        e.frequencyHz, e.evidence);
                if (e.automatic && promoted
                        && NextGenerationMasteringRenderer.inMode(mode,
                        NextGenerationMasteringRenderer.Mode
                        .PERSISTENT_TONE_REPAIR)) {
                    addChanged(out, e.startSeconds, e.endSeconds,
                            text + String.format(Locale.US,
                            "; selected narrow notch %.3f dB, Q %.2f.",
                            e.attenuationDb, e.q));
                } else {
                    addRejected(out, e.startSeconds, e.endSeconds,
                            text + (e.automatic
                                    ? "; candidate was not promoted."
                                    : "; musical-risk diagnostic only."));
                }
            }
        }
        if (model.channel != null) {
            for (ChannelIntegrityModel.Event e : model.channel.events) {
                if (e.automatic && promoted
                        && NextGenerationMasteringRenderer.inMode(mode,
                        NextGenerationMasteringRenderer.Mode
                        .CHANNEL_INTEGRITY)) {
                    addChanged(out, e.startSeconds, e.endSeconds,
                            e.evidence + "; stable technical correction selected.");
                } else {
                    addRejected(out, e.startSeconds, e.endSeconds,
                            e.evidence + (e.automatic
                                    ? "; candidate was not promoted."
                                    : "; diagnostic-only."));
                }
            }
        }
        if (model.reference != null && model.reference.supplied) {
            out.globalDecisions.add("Optional reference: "
                    + model.reference.compactSummary()
                    + "; reference loudness and exact EQ curve were not copied.");
            if (promoted && NextGenerationMasteringRenderer.inMode(mode,
                    NextGenerationMasteringRenderer.Mode.REFERENCE_AWARE)) {
                for (Section section : out.sections) {
                    section.changed.add("Bounded broad reference calibration applied; no widening and no reference loudness matching.");
                    section.globalProcessingApplies = true;
                }
            }
        }
    }

    private static void protectEdges(AnalysisResult source, Result out) {
        double tailStart = Math.max(2.0, source.wav.durationSeconds
                - Math.max(8.0, source.wav.durationSeconds * 0.04));
        addUntouched(out, 0.0, Math.min(2.0, source.wav.durationSeconds),
                "Protected intro: no local AI repair envelope was permitted.");
        addUntouched(out, tailStart, source.wav.durationSeconds,
                "Protected ending: no local AI repair envelope was permitted.");
    }

    private static boolean modeContains(StereoIntelligenceRenderer.Mode mode,
                                        StereoIntelligenceModel.Kind kind) {
        if (mode == null) return false;
        if (mode == StereoIntelligenceRenderer.Mode.COMBINED) return true;
        if (mode == StereoIntelligenceRenderer.Mode.EVENT_REPAIR) {
            return kind == StereoIntelligenceModel.Kind.SPECTRAL_HF
                    || kind == StereoIntelligenceModel.Kind.SPECTRAL_HARSH
                    || kind == StereoIntelligenceModel.Kind.SPECTRAL_MUD;
        }
        if (mode == StereoIntelligenceRenderer.Mode.LOW_END) {
            return kind == StereoIntelligenceModel.Kind.LOW_SUB_BLOOM
                    || kind == StereoIntelligenceModel.Kind.LOW_BASS_BLOOM
                    || kind == StereoIntelligenceModel.Kind.LOW_PHASE_RISK;
        }
        return mode == StereoIntelligenceRenderer.Mode.VOCAL_FOCUS
                && kind == StereoIntelligenceModel.Kind.VOCAL_FOCUS;
    }

    private static void addEq(List<String> output, List<String> applied,
                              String label, double value) {
        if (Math.abs(value) >= 0.005) {
            String decision = String.format(Locale.US,
                    "%s %+.3f dB.", label, value);
            output.add(decision);
            applied.add(decision);
        }
    }

    private static void addChanged(Result map, double start, double end, String value) {
        for (Section section : map.sections) {
            if (overlap(section, start, end)) {
                section.changed.add(value);
                section.sectionSpecificChange = true;
            }
        }
    }

    private static void addRejected(Result map, double start, double end, String value) {
        for (Section section : map.sections) {
            if (overlap(section, start, end)) section.rejected.add(value);
        }
    }

    private static void addUntouched(Result map, double start, double end, String value) {
        for (Section section : map.sections) {
            if (overlap(section, start, end)) section.untouched.add(value);
        }
    }

    private static boolean overlap(Section section, double start, double end) {
        return start < section.endSeconds && section.startSeconds < end;
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
