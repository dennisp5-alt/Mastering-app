package com.dennis.aimasteringlab;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Renders the controller's internal strategies, measures the actual files and
 * selects one winner without asking the user to choose between candidates.
 */
final class FullAiMasteringEngine {
    static final class Candidate {
        String strategy;
        AutonomousMasteringBrain.Plan plan;
        File file;
        AutonomousMasteringRenderer.Result render;
        AutonomousMasteringBrain.Features measuredFeatures;
        double measuredObjective;
        double technicalScore = Double.NEGATIVE_INFINITY;
        double score = Double.NEGATIVE_INFINITY;
        double processingCost;
        double preferenceBonus;
        double albumDistance = Double.NaN;
        double albumBonus;
        int rank;
        int renderAttempts;
        boolean eligible;
        boolean rendered;
        boolean intelligenceAware;
        StereoIntelligenceRenderer.Mode intelligenceMode;
        StereoIntelligenceRenderer.Result stereoIntelligence;
        double intelligenceEvidenceBonus;
        boolean advancedAware;
        AdvancedStereoRenderer.Mode advancedMode;
        AdvancedStereoRenderer.Result advancedIntelligence;
        double advancedEvidenceBonus;
        boolean professionalAware;
        ProfessionalMasteringRenderer.Mode professionalMode;
        ProfessionalMasteringRenderer.Result professionalIntelligence;
        double professionalEvidenceBonus;
        MasteringDeltaEvidence.Result masteringDelta;
        boolean meaningfulChangePassed;
        String meaningfulChangeDecision = "Not evaluated";
        boolean nextGenerationAware;
        NextGenerationMasteringRenderer.Mode nextGenerationMode;
        NextGenerationMasteringRenderer.Result nextGenerationIntelligence;
        double nextGenerationEvidenceBonus;
        boolean stemAware;
        StemAssistRenderer.Mode stemMode;
        StemAssistRenderer.Result stemAssist;
        double stemEvidenceBonus;
        boolean promotionBlocked;
        boolean selectedAfterPromotionGates;
        String duplicateOf;
        String status;
        PassagePreservation.Result passageCheck;
        boolean gentlerChallenger;
        boolean sparsePeakAware;
        TargetedQualityEvidence.Result qualityEvidence;
        String qualityDecision;
        final List<String> rejectionReasons = new ArrayList<>();

        String compactSummary() {
            if (!rendered) {
                return strategy + " • " + status;
            }
            AnalysisResult v = render.master.verification;
            return String.format(Locale.US,
                    "%s%s • raw score rank %d • score %.3f • objective %.3f • preference %+.3f • album %+.3f • %.4f LUFS-I / %.3f dBTP / LRA %.3f • %s",
                    strategy, nextGenerationAware
                            ? " [source-aware intelligence]"
                            : (professionalAware
                            ? " [quality-first cleanup/limiter competition]"
                            : (advancedAware
                            ? " [section/artifact/transient intelligence]"
                            : (intelligenceAware
                            ? " [authoritative-stereo intelligence]"
                            : (stemAware
                                ? (stemAssist != null && stemAssist.analysisOnlyControl
                                    ? " [analysis-only stem control]" : " [stem-audio delta]")
                                : "")))), rank, score, measuredObjective,
                    preferenceBonus, albumBonus, v.integratedLufs,
                    v.truePeakEstimateDbtp, v.lraLu,
                    decisionOutcome());
        }

        String decisionOutcome() {
            if (!rendered) {
                return duplicateOf != null ? "DUPLICATE_NOT_RENDERED" : "NOT_RENDERED";
            }
            if (!eligible) return "HARD_SAFETY_REJECTED";
            if (selectedAfterPromotionGates) return "SELECTED_AFTER_PROMOTION_GATES";
            if (promotionBlocked) return "PROMOTION_GATE_BLOCKED";
            return "ELIGIBLE_NOT_SELECTED";
        }
    }

    /**
     * v0.5.3: which candidate families one engine run may render. The chained master
     * runs TONE first (core EQ/dynamics plans compete), then one or two REPAIRS runs on
     * the tone winner, so a song can get a tonal correction AND a repair instead of
     * one or the other. FULL is the v0.15.7 competition, unchanged.
     */
    static final class Scope {
        final String name;
        /** Core plans beyond the transparent control, adaptive broad and sparse-peak auditions. */
        final boolean coreAlternatives;
        final boolean specialists;
        /** One render per family (its combined/gentlest mode); no limiter re-optimisation. */
        final boolean leanModes;
        final java.util.Set<String> excluded;

        private Scope(String name, boolean coreAlternatives, boolean specialists, boolean leanModes,
                      java.util.Set<String> excluded) {
            this.name = name; this.coreAlternatives = coreAlternatives; this.specialists = specialists;
            this.leanModes = leanModes; this.excluded = excluded;
        }

        static final Scope FULL = new Scope("full", true, true, false, java.util.Collections.<String>emptySet());
        static final Scope TONE = new Scope("tone", true, false, false, java.util.Collections.<String>emptySet());

        static Scope repairs(java.util.Set<String> excluded) {
            return new Scope("repairs", false, true, true, new java.util.HashSet<>(excluded));
        }

        /** Families: stereo, advanced, professional, nextgen, stem. */
        boolean family(String family) { return specialists && !excluded.contains(family); }
    }

    /** The family a selected candidate came from: core, stereo, advanced, professional, nextgen or stem. */
    static String familyOf(Candidate c) {
        if (c == null) return "none";
        if (c.stemAware) return "stem";
        if (c.intelligenceAware) return "stereo";
        if (c.advancedAware) return "advanced";
        if (c.professionalAware) return "professional";
        if (c.nextGenerationAware) return "nextgen";
        return "core";
    }

    static final class Result {
        Scope scope = Scope.FULL;
        String model = "DAML v" + StudioQcReporting.APP_VERSION + " Verified Candidate Efficiency competition";
        String rule = "bounded 0.050 objective / 0.300 raw technical tolerance restricted to verified unresolved dynamics-led adaptive fallback, hard safety rejection, deterministic render integrity, measured broad-mastering requirement before narrow-repair promotion, three-rung source-shaped adaptive broad fallback only when normal broad candidates fail, dynamics-demand-aware preservation only inside unresolved dynamics-led fallback, independently verified corrective EQ/dynamics/cleanup/limiter candidates, level-matched meaningful-mastering gate against the transparent control, diagnostic-only translation simulations, bounded learned-profile tie breaks and tiered stems";
        double sourceObjective;
        final List<Candidate> candidates = new ArrayList<>();
        final LinkedHashMap<String,Long> candidateStageMs = new LinkedHashMap<>();
        final List<Candidate> rankedEligible = new ArrayList<>();
        Candidate selected;
        Candidate transparent;
        Candidate naturalReference;
        PassagePreservation.Scan passageBaseline;
        Candidate gentlerCandidate;
        Candidate gentlerParent;
        String gentlerDecision = "Not evaluated";
        boolean transparentRollback;
        boolean translationConsensusDiagnosticOnly = true;
        int intelligenceCandidatesAttempted;
        int intelligenceCandidatesEligible;
        boolean intelligenceSelectionBlocked;
        String intelligenceDecision = "No authoritative-stereo intelligence correction cleared its inference gate";
        int advancedCandidatesAttempted;
        int advancedCandidatesEligible;
        boolean advancedSelectionBlocked;
        String advancedDecision = "No section/artifact/transient correction cleared its inference gate";
        int professionalCandidatesAttempted;
        int professionalCandidatesEligible;
        boolean professionalSelectionBlocked;
        String professionalDecision = "No v0.8 professional correction cleared its inference gate";
        int nextGenerationCandidatesAttempted;
        int nextGenerationCandidatesEligible;
        boolean nextGenerationSelectionBlocked;
        String nextGenerationDecision = "No source-aware correction cleared its inference gate";
        boolean stemArchiveSupplied;
        int stemCandidatesAttempted;
        int stemCandidatesEligible;
        boolean stereoFallback;
        boolean stemSelectionBlocked;
        String stemDecision = "No optional stem archive supplied; authoritative-stereo competition used";
        String selectionReason;
        boolean meaningfulChangeRollback;
        String meaningfulChangeDecision = "Not evaluated";
        String meaningfulChangeComparedStrategy;
        MasteringDeltaEvidence.Result selectedVsTransparentDelta;
        BroadMasteringNeed.Result broadMasteringNeed;
        Candidate broadMasteringAnchor;
        MasteringDeltaEvidence.Result broadMasteringAnchorDelta;
        boolean broadMasteringAnchorUsed;
        boolean broadMasteringUnresolved;
        boolean earlySparsePeakShortcut;
        String earlySparsePeakDecision = "Not attempted";
        int adaptiveBroadCandidatesAttempted;
        int adaptiveBroadCandidatesEligible;
        String adaptiveBroadDecision = "Not required";
        final List<String> adaptiveBroadDiagnostics = new ArrayList<>();
        Candidate adaptiveBroadAnchor;
        String broadMasteringDecision = "Not evaluated";
        DennisPreferenceMemory.Profile preferenceProfile;
        AlbumConsistencyModel.Profile albumProfile;
        final List<String> renderIntegrityFailures = new ArrayList<>();

        String compactSummary() {
            return selected == null ? "No Full AI candidate survived"
                    : "Selected " + selected.strategy + " autonomously. "
                    + selectionReason + "\n" + stemDecision + "\n" + selected.compactSummary();
        }
    }

    private FullAiMasteringEngine() {}

    static Result run(AudioAnalyzer.StreamProvider source,
                      AnalysisResult sourceAnalysis,
                      AutonomousMasteringBrain.Result brain,
                      File workDirectory,
                      AudioAnalyzer.Progress progress) throws IOException {
        return run(source, sourceAnalysis, brain, null, workDirectory,
                null, null, progress);
    }

    static Result run(AudioAnalyzer.StreamProvider source,
                      AnalysisResult sourceAnalysis,
                      AutonomousMasteringBrain.Result brain,
                      File stemArchive,
                      File workDirectory,
                      AudioAnalyzer.Progress progress) throws IOException {
        return run(source, sourceAnalysis, brain, stemArchive, workDirectory,
                null, brain == null ? null : brain.albumProfile, progress);
    }

    static Result run(AudioAnalyzer.StreamProvider source,
                      AnalysisResult sourceAnalysis,
                      AutonomousMasteringBrain.Result brain,
                      File stemArchive,
                      File workDirectory,
                      DennisPreferenceMemory.Profile preferenceProfile,
                      AlbumConsistencyModel.Profile albumProfile,
                      AudioAnalyzer.Progress progress) throws IOException {
        return run(source, sourceAnalysis, brain, stemArchive, workDirectory,
                preferenceProfile, albumProfile, Scope.FULL, progress);
    }

    static Result run(AudioAnalyzer.StreamProvider source,
                      AnalysisResult sourceAnalysis,
                      AutonomousMasteringBrain.Result brain,
                      File stemArchive,
                      File workDirectory,
                      DennisPreferenceMemory.Profile preferenceProfile,
                      AlbumConsistencyModel.Profile albumProfile,
                      Scope scope,
                      AudioAnalyzer.Progress progress) throws IOException {
        if (scope == null) scope = Scope.FULL;
        if (sourceAnalysis.wav.channels != 2 || sourceAnalysis.wav.sampleRate != 48000) {
            throw new IOException("Full AI mastering requires a 48 kHz stereo authoritative WAV");
        }
        if (!workDirectory.exists() && !workDirectory.mkdirs()) {
            throw new IOException("Could not create the Full AI candidate cache");
        }
        if (sourceAnalysis.sourceGuard == null) {
            sourceAnalysis.sourceGuard = SourceGuardModel.analyze(
                    source, sourceAnalysis, progress);
        }
        Result result = new Result();
        result.scope = scope;
        result.preferenceProfile = preferenceProfile;
        result.albumProfile = albumProfile;
        result.stemArchiveSupplied = stemArchive != null
                || sourceAnalysis.stemArchiveName != null;
        result.sourceObjective = AutonomousMasteringBrain.objective(
                brain.features, sourceAnalysis);
        result.broadMasteringNeed = BroadMasteringNeed.assess(sourceAnalysis, brain);
        Map<String, Candidate> fingerprints = new LinkedHashMap<>();
        List<AutonomousMasteringBrain.Plan> corePlans = shortlistCorePlans(brain.internalPlans);
        if (!scope.coreAlternatives) {
            // Repairs stage: the input is already the tone winner; only its transparent
            // delivery control is rendered as the reference the repairs must beat.
            ArrayList<AutonomousMasteringBrain.Plan> only = new ArrayList<>();
            for (AutonomousMasteringBrain.Plan p : corePlans)
                if ("transparent_flat_gain".equals(p.strategy)) only.add(p);
            corePlans = only;
        }
        long timerMark=System.nanoTime();

        for (int index = 0; index < corePlans.size(); index++) {
            AutonomousMasteringBrain.Plan plan = corePlans.get(index);
            Candidate candidate = new Candidate();
            candidate.plan = plan;
            candidate.strategy = plan.strategy == null
                    ? "candidate_" + (index + 1) : plan.strategy;
            result.candidates.add(candidate);
            if ("transparent_flat_gain".equals(candidate.strategy)) {
                result.transparent = candidate;
            }
            if (sourceAnalysis.sourceGuard.secondPassGuard
                    && plan.hasAudioChanges()) {
                candidate.status = "not rendered: previous-master guard blocked broad second-pass processing";
                continue;
            }
            if (sourceAnalysis.sourceGuard.highBoostBlocked
                    && plan.highShelfDb > 0.001) {
                candidate.status = "not rendered: source guard blocked high-frequency boosting";
                continue;
            }

            String fingerprint = fingerprint(plan);
            Candidate prior = fingerprints.get(fingerprint);
            if (prior != null) {
                candidate.duplicateOf = prior.strategy;
                candidate.status = "not rendered: audio-identical plan duplicate of "
                        + prior.strategy;
                continue;
            }
            fingerprints.put(fingerprint, candidate);
            String safe = candidate.strategy.replaceAll("[^A-Za-z0-9]+", "_")
                    .replaceAll("^_+|_+$", "");
            candidate.file = new File(workDirectory,
                    String.format(Locale.US, "internal-%02d-%s.wav", index + 1, safe));
            String coreLabel = "Core candidate " + (index + 1) + "/" + corePlans.size()
                    + " • " + candidate.strategy;
            int coreStart = segmentStart(index, corePlans.size(), 0, 34);
            int coreEnd = segmentEnd(index, corePlans.size(), 0, 34);
            int coreMid = coreStart + Math.max(1, (int) Math.round((coreEnd - coreStart) * 0.58));
            progress.update(coreLabel + " • preparing", coreStart);
            try {
                Exception lastFailure = null;
                for (int attempt = 1; attempt <= 2; attempt++) {
                    candidate.renderAttempts = attempt;
                    try {
                        candidate.render = renderCandidate(source, sourceAnalysis,
                                plan, candidate.file, candidateWork(workDirectory, candidate),
                                rangeProgress(progress, coreLabel + " • render", coreStart, coreMid));
                        if(sourceAnalysis.sourceSha256!=null && sourceAnalysis.sourceSha256.equalsIgnoreCase(
                                candidate.render.master.verification.sourceSha256)){
                            candidate.measuredFeatures=brain.features;
                            candidate.measuredObjective=result.sourceObjective;
                            progress.update(coreLabel+" • verification • exact source-control features reused",coreEnd);
                        }else{
                            candidate.measuredFeatures = AutonomousMasteringBrain.measureFeatures(
                                    () -> new FileInputStream(candidate.file),
                                    candidate.render.master.verification,
                                    rangeProgress(progress, coreLabel + " • verification", coreMid, coreEnd));
                            candidate.measuredObjective = AutonomousMasteringBrain.objective(
                                    candidate.measuredFeatures, candidate.render.master.verification);
                        }
                        lastFailure = null;
                        break;
                    } catch (Exception error) {
                        lastFailure = error;
                        if (candidate.file.exists() && !candidate.file.delete()) {
                            candidate.file.deleteOnExit();
                        }
                        cleanupPremasters(candidateWork(workDirectory, candidate), candidate.strategy);
                        if (attempt == 1 && retriableIo(error)) {
                            progress.update(coreLabel + " • retrying a transient candidate read", coreStart);
                            continue;
                        }
                        throw error;
                    }
                }
                if (lastFailure != null || candidate.render == null) {
                    throw lastFailure == null
                            ? new IOException("candidate render returned no result") : lastFailure;
                }
                candidate.rendered = true;
                evaluate(candidate, sourceAnalysis, result);
            } catch (Exception error) {
                candidate.status = "render rejected: " + clean(error);
                candidate.rejectionReasons.add(candidate.status);
                candidate.eligible = false;
                recordIntegrityFailure(result, candidate, error);
                if (candidate.file.exists() && !candidate.file.delete()) {
                    candidate.file.deleteOnExit();
                }
            }
        }
        if (Thread.currentThread().isInterrupted()) throw new IOException("Mastering cancelled");
        if (result.transparent == null || !result.transparent.eligible
                || result.transparent.file == null || !result.transparent.file.isFile()) {
            throw baselineFailure(result.transparent, "initial verification");
        }
        retainCoreCompetitionAudio(result);
        timerMark=markCandidateStage(result,"core_candidate_rendering_and_qc_ms",timerMark);

        // Peak-constrained performance tier: prove the identical sparse candidate
        // using complete measurements before spending multiple full renders on
        // alternate families. The narrow preflight is never a promotion gate.
        boolean earlySparseSolved = false;
        if (scope.coreAlternatives && SparsePeakRecovery.earlyAuditionSafe(sourceAnalysis,
                result.broadMasteringNeed, result.stemArchiveSupplied,
                result.broadMasteringAnchor != null)) {
            earlySparseSolved = tryEarlySparsePeakCandidate(source,
                    sourceAnalysis, brain, result, workDirectory,
                    rangeProgress(progress, "Verified sparse-peak preflight", 34, 39));
        }
        timerMark=markCandidateStage(result,"early_sparse_peak_audition_ms",timerMark);

        // On verified early success, retain the transparent control and the
        // verified sparse-peak broad anchor. Full-format, passage and final-copy
        // gates remain active. On any failure run the original competition.
        if (!earlySparseSolved) {


        if (scope.family("stereo") && sourceAnalysis.stereoIntelligence == null) {
            try {
                sourceAnalysis.stereoIntelligence = StereoIntelligenceModel.analyze(
                        source, sourceAnalysis, rangeProgress(progress, "Stereo analysis", 34, 39));
            } catch (Exception intelligenceError) {
                sourceAnalysis.stereoIntelligence = StereoIntelligenceModel.unavailable(
                        "Stereo-intelligence analysis unavailable: "
                                + clean(intelligenceError), sourceAnalysis);
            }
        }
        if (!scope.family("stereo")) {
            result.intelligenceDecision = "Not part of this " + scope.name + " stage.";
        } else if (!sourceAnalysis.sourceGuard.secondPassGuard) {
            addStereoIntelligenceCandidates(source, sourceAnalysis, result,
                    workDirectory, rangeProgress(progress, "Stereo mastering", 39, 50));
        } else result.intelligenceDecision = "Previous-master guard blocked stereo tonal/intelligibility candidates; diagnostics were retained.";
        timerMark=markCandidateStage(result,"stereo_intelligence_candidates_ms",timerMark);
        if (scope.family("advanced") && sourceAnalysis.advancedIntelligence == null) {
            try {
                sourceAnalysis.advancedIntelligence = AdvancedStereoModel.analyze(
                        source, sourceAnalysis, rangeProgress(progress, "Advanced analysis", 50, 55));
            } catch (Exception advancedError) {
                sourceAnalysis.advancedIntelligence = AdvancedStereoModel.unavailable(
                        "Advanced analysis unavailable: "
                                + clean(advancedError), sourceAnalysis);
            }
        }
        if (!scope.family("advanced")) {
            result.advancedDecision = "Not part of this " + scope.name + " stage.";
        } else if (!sourceAnalysis.sourceGuard.secondPassGuard) {
            addAdvancedCandidates(source, sourceAnalysis, result,
                    workDirectory, rangeProgress(progress, "Advanced mastering", 55, 66));
        } else result.advancedDecision = "Previous-master guard blocked section/artifact/transient candidates; diagnostics were retained.";
        timerMark=markCandidateStage(result,"advanced_candidates_ms",timerMark);
        if (scope.family("professional") && sourceAnalysis.professionalIntelligence == null) {
            try {
                sourceAnalysis.professionalIntelligence =
                        ProfessionalMasteringModel.analyze(source,
                                sourceAnalysis, rangeProgress(progress, "Quality analysis", 66, 70));
            } catch (Exception professionalError) {
                sourceAnalysis.professionalIntelligence =
                        ProfessionalMasteringModel.unavailable(
                                "v0.8 professional analysis unavailable: "
                                        + clean(professionalError),
                                sourceAnalysis);
            }
        }
        if (scope.family("professional")) addProfessionalCandidates(source, sourceAnalysis, result, workDirectory,
                rangeProgress(progress, "Quality mastering", 70, 81));
        else result.professionalDecision = "Not part of this " + scope.name + " stage.";
        timerMark=markCandidateStage(result,"professional_candidates_ms",timerMark);
        if (scope.family("nextgen") && sourceAnalysis.nextGenerationIntelligence == null) {
            try {
                sourceAnalysis.nextGenerationIntelligence =
                        NextGenerationMasteringModel.analyze(source,
                                sourceAnalysis, null, null, null,
                                rangeProgress(progress, "Technical analysis", 81, 85));
            } catch (Exception nextError) {
                sourceAnalysis.nextGenerationIntelligence =
                        new NextGenerationMasteringModel.Result();
                sourceAnalysis.nextGenerationIntelligence.safetyNotes.add(
                        "Source-aware analysis unavailable: "
                                + clean(nextError));
            }
        }
        if (scope.family("nextgen")) addNextGenerationCandidates(source, sourceAnalysis, result,
                workDirectory, rangeProgress(progress, "Technical repair mastering", 85, 93));
        else result.nextGenerationDecision = "Not part of this " + scope.name + " stage.";
        timerMark=markCandidateStage(result,"next_generation_candidates_ms",timerMark);
        if (!scope.family("stem")) {
            if (stemArchive != null) result.stemDecision = "Not part of this " + scope.name + " stage.";
        } else if (!sourceAnalysis.sourceGuard.secondPassGuard) {
            addStemCandidates(source, sourceAnalysis, stemArchive, result,
                    workDirectory, rangeProgress(progress, "Stem-assisted mastering", 93, 97));
        } else if (stemArchive != null || sourceAnalysis.stemArchiveName != null) {
            result.stemDecision = "Previous-master guard blocked stem-derived remastering; stem evidence remained diagnostic.";
            result.stereoFallback = true;
        }

        // v0.14.5: only after the normal candidate families have run, create
        // a tiny source-shaped adaptive ladder when reliable broad demand is
        // still unresolved. Hard safety gates remain fixed; only dynamics-led
        // musical-preservation tolerances may use the bounded demand-aware envelope.
        timerMark=markCandidateStage(result,"stem_candidates_ms",timerMark);
        if (scope.coreAlternatives
                && result.broadMasteringNeed != null && result.broadMasteringNeed.required
                && result.broadMasteringAnchor == null
                && !sourceAnalysis.sourceGuard.secondPassGuard) {
            addAdaptiveBroadCandidates(source, sourceAnalysis, brain, result,
                    workDirectory, rangeProgress(progress,
                    "Adaptive broad mastering fallback", 97, 99));
        }

        } else {
            result.intelligenceDecision = "v0.15.6: specialist candidate rendering bypassed after verified sparse-peak broad anchor; source-specific fast preflight";
            result.advancedDecision = "v0.15.6: specialist candidate rendering bypassed after verified sparse-peak broad anchor";
            result.professionalDecision = "v0.15.6: specialist candidate rendering bypassed after verified sparse-peak broad anchor";
            result.nextGenerationDecision = "v0.15.6: specialist candidate rendering bypassed after verified sparse-peak broad anchor";
            timerMark=markCandidateStage(result,"other_candidate_families_skipped_ms",timerMark);
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new IOException("Mastering cancelled");
        }
        // Optional failures stay visible in QC. An invalid source/baseline still fails closed.
        if (result.transparent == null || !result.transparent.eligible
                || result.transparent.file == null || !result.transparent.file.isFile()) {
            throw baselineFailure(result.transparent, "final selection");
        }

        timerMark=markCandidateStage(result,"adaptive_broad_fallback_ms",timerMark);
        for (Candidate candidate : result.candidates) {
            if (candidate.eligible && candidate.file != null && candidate.file.isFile()) {
                result.rankedEligible.add(candidate);
            }
        }
        Collections.sort(result.rankedEligible, new Comparator<Candidate>() {
            @Override public int compare(Candidate left, Candidate right) {
                int score = Double.compare(right.score, left.score);
                if (score != 0) return score;
                return Double.compare(left.processingCost, right.processingCost);
            }
        });
        for (int i = 0; i < result.rankedEligible.size(); i++) {
            result.rankedEligible.get(i).rank = i + 1;
        }
        if (result.rankedEligible.isEmpty()) {
            throw new IOException("Every internal Full AI strategy failed a hard safety gate");
        }

        Candidate transparent = result.transparent != null && result.transparent.eligible
                ? result.transparent : null;
        Candidate bestCoreStereo = null;
        for (Candidate candidate : result.rankedEligible) {
            if (!candidate.stemAware && !candidate.intelligenceAware
                    && !candidate.advancedAware
                    && !candidate.professionalAware
                    && !candidate.nextGenerationAware) {
                bestCoreStereo = candidate;
                break;
            }
        }
        if (bestCoreStereo == null) {
            throw new IOException("No core stereo fallback survived the hard safety gates");
        }

        // v0.14.5: when source analysis says the track still needs a broad
        // whole-song master, establish a safe broad core anchor before any
        // narrow artifact/repair candidate is allowed to become the winner.
        Candidate broadAnchor = null;
        if (result.broadMasteringNeed != null
                && result.broadMasteringNeed.required && transparent != null) {
            broadAnchor = bestBroadCoreAnchor(result, transparent);
            result.broadMasteringAnchor = broadAnchor;
            if (broadAnchor != null) {
                bestCoreStereo = broadAnchor;
                result.broadMasteringAnchorDelta = broadAnchor.masteringDelta;
                result.broadMasteringDecision = "Broad mastering demand was measured and "
                        + broadAnchor.strategy
                        + " established a safe whole-song anchor: "
                        + (broadAnchor.masteringDelta == null ? "delta unavailable"
                        : broadAnchor.masteringDelta.summary());
            } else {
                result.broadMasteringUnresolved = true;
                result.broadMasteringDecision = result.adaptiveBroadCandidatesAttempted > 0
                        ? "Broad mastering demand was measured; normal candidates and the v0.14.6 adaptive broad ladder all failed to simultaneously clear broad-delta and preservation gates, so transparent fallback remains authoritative."
                        : "Broad mastering demand was measured, but no core candidate simultaneously cleared the broad-delta and preservation gates.";
            }
        } else if (result.broadMasteringNeed != null) {
            result.broadMasteringDecision = result.broadMasteringNeed.summary();
        }

        Candidate guardedCoreStereo = bestCoreStereo;
        boolean stereoReferenceRolledBack = false;
        double stereoReferenceAdvantage = Double.NaN;
        double stereoReferenceObjectiveGain = Double.NaN;
        if (transparent != null && bestCoreStereo != transparent) {
            stereoReferenceAdvantage = bestCoreStereo.score - transparent.score;
            stereoReferenceObjectiveGain = bestCoreStereo.measuredObjective
                    - result.sourceObjective;
            double phaseGain = phaseGain(sourceAnalysis,
                    bestCoreStereo.render.master.verification);
            boolean albumJustified = result.albumProfile != null
                    && result.albumProfile.usable()
                    && Double.isFinite(bestCoreStereo.albumDistance)
                    && Double.isFinite(transparent.albumDistance)
                    && bestCoreStereo.albumDistance + 0.10 < transparent.albumDistance
                    && bestCoreStereo.technicalScore >= transparent.technicalScore - 0.025
                    && bestCoreStereo.processingCost <= 0.10;
            boolean requiredBroadAnchor = result.broadMasteringNeed != null
                    && result.broadMasteringNeed.required
                    && bestCoreStereo == result.broadMasteringAnchor;
            if (!requiredBroadAnchor && !albumJustified && stereoReferenceAdvantage < 0.020
                    && stereoReferenceObjectiveGain < 0.080 && phaseGain < 0.01) {
                guardedCoreStereo = transparent;
                stereoReferenceRolledBack = true;
                bestCoreStereo.promotionBlocked = true;
                result.transparentRollback = true;
                result.selectionReason = String.format(Locale.US,
                        "The best core stereo processing did not prove a measurable Sonic Finish benefit (score margin %.3f; objective gain %.3f), so the transparent control remained the core reference.",
                        stereoReferenceAdvantage, stereoReferenceObjectiveGain);
            } else if (requiredBroadAnchor) {
                result.selectionReason = String.format(Locale.US,
                        "%s retained as the required broad-mastering anchor after clearing level-matched broad-delta and natural-preservation gates; raw-score margin %.3f and objective gain %.3f remain diagnostic rather than automatic rollback triggers.",
                        bestCoreStereo.strategy, stereoReferenceAdvantage,
                        stereoReferenceObjectiveGain);
            } else if (albumJustified) {
                result.selectionReason = String.format(Locale.US,
                        "%s cleared the bounded Album Consistency gate (album distance %.3f vs transparent %.3f; technical score delta %+.3f).",
                        bestCoreStereo.strategy, bestCoreStereo.albumDistance,
                        transparent.albumDistance,
                        bestCoreStereo.technicalScore - transparent.technicalScore);
            }
        }
        for (Candidate candidate : result.candidates) {
            if (!candidate.stemAware && !candidate.intelligenceAware
                    && !candidate.advancedAware
                    && !candidate.professionalAware
                    && !candidate.nextGenerationAware
                    && candidate != guardedCoreStereo) {
                candidate.promotionBlocked = true;
            }
        }
        discardPromotionBlockedAudio(result);

        Candidate guardedStereo = guardedCoreStereo;
        Candidate bestIntelligence = null;
        for (Candidate candidate : result.rankedEligible) {
            if (candidate.intelligenceAware) {
                bestIntelligence = candidate;
                break;
            }
        }
        boolean intelligencePromoted = false;
        if (bestIntelligence != null && bestIntelligence.score > guardedCoreStereo.score) {
            double margin = bestIntelligence.score - guardedCoreStereo.score;
            double objectiveGain = bestIntelligence.measuredObjective
                    - guardedCoreStereo.measuredObjective;
            double targeted = bestIntelligence.stereoIntelligence == null ? 0.0
                    : bestIntelligence.stereoIntelligence.measuredTargetBenefit;
            if (margin >= 0.035 && targeted >= 0.08) {
                guardedStereo = bestIntelligence;
                intelligencePromoted = true;
                result.intelligenceDecision = String.format(Locale.US,
                        "%s cleared the authoritative-stereo intelligence gate over %s (score margin %.3f; objective gain %.3f; measured targeted benefit %.3f).",
                        bestIntelligence.strategy, guardedCoreStereo.strategy,
                        margin, objectiveGain, targeted);
                result.selectionReason = result.intelligenceDecision;
            } else {
                result.intelligenceSelectionBlocked = true;
                result.intelligenceDecision = String.format(Locale.US,
                        "%s survived hard QC but did not clear the intelligence promotion gate (required score margin 0.035 and targeted benefit 0.080; achieved %.3f/%.3f); %s retained.",
                        bestIntelligence.strategy, margin, targeted,
                        guardedCoreStereo.strategy);
            }
        } else if (bestIntelligence != null) {
            result.intelligenceDecision = String.format(Locale.US,
                    "Eligible stereo-intelligence candidates were measured, but %s retained the stronger verified score.",
                    guardedCoreStereo.strategy);
        }
        for (Candidate candidate : result.candidates) {
            if (candidate.intelligenceAware
                    && (!intelligencePromoted || candidate != guardedStereo)) {
                candidate.promotionBlocked = true;
            }
        }
        discardPromotionBlockedAudio(result);

        Candidate guardedAdvanced = guardedStereo;
        Candidate bestAdvanced = null;
        for (Candidate candidate : result.rankedEligible) {
            if (candidate.advancedAware) {
                bestAdvanced = candidate;
                break;
            }
        }
        boolean advancedPromoted = false;
        if (bestAdvanced != null && bestAdvanced.score > guardedStereo.score) {
            double margin = bestAdvanced.score - guardedStereo.score;
            double objectiveGain = bestAdvanced.measuredObjective
                    - guardedStereo.measuredObjective;
            double targeted = bestAdvanced.advancedIntelligence == null ? 0.0
                    : bestAdvanced.advancedIntelligence.measuredTargetBenefit;
            boolean moduleBenefit = advancedModuleBenefitPassed(bestAdvanced);
            if (margin >= 0.030 && targeted >= 0.08 && moduleBenefit) {
                guardedAdvanced = bestAdvanced;
                advancedPromoted = true;
                result.advancedDecision = String.format(Locale.US,
                        "%s cleared the measured module promotion gate over %s (score margin %.3f; objective gain %.3f; measured targeted benefit %.3f).",
                        bestAdvanced.strategy, guardedStereo.strategy,
                        margin, objectiveGain, targeted);
                result.selectionReason = result.advancedDecision;
            } else {
                result.advancedSelectionBlocked = true;
                result.advancedDecision = String.format(Locale.US,
                        "%s survived hard QC but did not clear the measured module promotion gate (required score margin 0.030, targeted benefit 0.080 and module-specific measured benefit; achieved %.3f/%.3f, module gate %s); %s retained.",
                        bestAdvanced.strategy, margin, targeted,
                        moduleBenefit ? "PASS" : "BLOCKED",
                        guardedStereo.strategy);
            }
        } else if (bestAdvanced != null) {
            result.advancedDecision = String.format(Locale.US,
                    "Eligible advanced candidates were measured, but %s retained the stronger verified score.",
                    guardedStereo.strategy);
        }
        for (Candidate candidate : result.candidates) {
            if (candidate.advancedAware
                    && (!advancedPromoted || candidate != guardedAdvanced)) {
                candidate.promotionBlocked = true;
            }
        }
        discardPromotionBlockedAudio(result);
        // Retain the strongest verified pre-specialist reference for later
        // quality and repair families; it is no longer a fixed v0.7 sonic target.
        result.naturalReference = guardedAdvanced;

        Candidate guardedProfessional = guardedAdvanced;
        boolean professionalPromoted = false;
        double bestQualityValue=Double.NEGATIVE_INFINITY;
        for(Candidate candidate:result.rankedEligible){
            if(!candidate.professionalAware || candidate.file==null || !candidate.file.isFile())continue;
            double value=qualityPromotionValue(candidate,guardedAdvanced,sourceAnalysis);
            if(value>bestQualityValue){
                bestQualityValue=value;guardedProfessional=candidate;
            }
        }
        if(guardedProfessional!=guardedAdvanced){
            professionalPromoted=true;
            result.professionalDecision=guardedProfessional.strategy+" selected by independent quality evidence against "
                    +guardedAdvanced.strategy+": "+guardedProfessional.qualityDecision;
            result.selectionReason=result.professionalDecision;
        }else{
            result.professionalSelectionBlocked=result.professionalCandidatesAttempted>0;
            result.professionalDecision="Verified comparison winner retained: no quality-first cleanup or limiter candidate cleared its finished-audio benefit and preservation gates.";
        }
        for (Candidate candidate : result.candidates) {
            if (candidate.professionalAware
                    && (!professionalPromoted
                    || candidate != guardedProfessional)) {
                candidate.promotionBlocked = true;
            }
        }
        discardPromotionBlockedAudio(result);

        Candidate guardedNextGeneration = guardedProfessional;
        Candidate bestNextGeneration = null;
        for (Candidate candidate : result.rankedEligible) {
            if (candidate.nextGenerationAware
                    && (bestNextGeneration == null
                    || candidate.technicalScore > bestNextGeneration.technicalScore
                    || (candidate.technicalScore == bestNextGeneration.technicalScore
                    && candidate.processingCost < bestNextGeneration.processingCost))) {
                bestNextGeneration = candidate;
            }
        }
        boolean nextPromoted = false;
        if (bestNextGeneration != null
                && bestNextGeneration.technicalScore
                > guardedProfessional.technicalScore) {
            double margin = bestNextGeneration.technicalScore
                    - guardedProfessional.technicalScore;
            double objectiveGain = bestNextGeneration.measuredObjective
                    - guardedProfessional.measuredObjective;
            double targeted = bestNextGeneration.nextGenerationIntelligence == null
                    ? 0.0 : bestNextGeneration.nextGenerationIntelligence
                    .measuredTargetBenefit;
            boolean technicalRepair = bestNextGeneration.nextGenerationMode
                    == NextGenerationMasteringRenderer.Mode.PERSISTENT_TONE_REPAIR
                    || bestNextGeneration.nextGenerationMode
                    == NextGenerationMasteringRenderer.Mode.CHANNEL_INTEGRITY;
            double requiredMargin = technicalRepair ? 0.045 : 0.065;
            double requiredTarget = technicalRepair ? 0.10 : 0.10;
            boolean natural = naturalPreservationPassed(
                    bestNextGeneration, guardedProfessional)
                    && retainsTargetedImprovement(bestNextGeneration, guardedProfessional, sourceAnalysis);
            if (margin >= requiredMargin && targeted >= requiredTarget
                    && natural) {
                guardedNextGeneration = bestNextGeneration;
                nextPromoted = true;
                result.nextGenerationDecision = String.format(Locale.US,
                        "%s proved a narrow professional improvement over %s (technical margin %.3f; objective gain %.3f; measured targeted benefit %.3f); translation profiles remained diagnostic-only.",
                        bestNextGeneration.strategy,
                        guardedProfessional.strategy, margin,
                        objectiveGain, targeted);
                result.selectionReason = result.nextGenerationDecision;
            } else {
                result.nextGenerationSelectionBlocked = true;
                result.nextGenerationDecision = String.format(Locale.US,
                        "%s survived hard QC but did not prove a narrow natural improvement (required technical margin %.3f, targeted benefit %.3f and musical-preservation PASS; achieved %.3f/%.3f, preservation %s); %s retained. Translation profiles remained diagnostic-only.",
                        bestNextGeneration.strategy, requiredMargin,
                        requiredTarget, margin, targeted,
                        natural ? "PASS" : "BLOCKED",
                        guardedProfessional.strategy);
            }
        } else if (bestNextGeneration != null) {
            result.nextGenerationDecision = String.format(Locale.US,
                    "Eligible source-aware candidates were measured, but %s retained the stronger verified score.",
                    guardedProfessional.strategy);
        }
        for (Candidate candidate : result.candidates) {
            if (candidate.nextGenerationAware
                    && (!nextPromoted || candidate != guardedNextGeneration)) {
                candidate.promotionBlocked = true;
            }
        }
        discardPromotionBlockedAudio(result);

        Candidate best = guardedNextGeneration;
        Candidate bestStem = null;
        for (Candidate candidate : result.rankedEligible) {
            if (candidate.stemAware
                    && (bestStem == null
                    || candidate.technicalScore > bestStem.technicalScore
                    || (candidate.technicalScore == bestStem.technicalScore
                    && candidate.processingCost < bestStem.processingCost))) {
                bestStem = candidate;
            }
        }
        if (bestStem != null && bestStem.technicalScore
                > guardedNextGeneration.technicalScore) {
            double margin = bestStem.technicalScore
                    - guardedNextGeneration.technicalScore;
            double objectiveGain = bestStem.measuredObjective
                    - guardedNextGeneration.measuredObjective;
            boolean analysisOnly = bestStem.stemAssist != null
                    && bestStem.stemAssist.analysisOnlyControl;
            double requiredMargin = analysisOnly ? 0.12 : 0.10;
            boolean benefitGateFailed = analysisOnly
                    ? objectiveGain < 0.015
                    : objectiveGain < 0.01 && bestStem.stemEvidenceBonus < 0.04;
            boolean natural = naturalPreservationPassed(
                    bestStem, guardedNextGeneration)
                    && retainsTargetedImprovement(bestStem, guardedNextGeneration, sourceAnalysis);
            if (margin < requiredMargin || benefitGateFailed || !natural) {
                result.stemSelectionBlocked = true;
                result.stereoFallback = true;
                result.stemDecision = String.format(Locale.US,
                        "%s candidate %s was measured but did not clear its musical-preservation promotion gate (required technical margin %.3f; achieved %.3f; objective gain %.3f; evidence bonus %.3f; preservation %s); stereo winner %s retained.",
                        analysisOnly ? "Analysis-only stem-control" : "Stem-audio delta",
                        bestStem.strategy, requiredMargin, margin, objectiveGain,
                        bestStem.stemEvidenceBonus,
                        natural ? "PASS" : "BLOCKED",
                        guardedNextGeneration.strategy);
            } else {
                best = bestStem;
                result.stemDecision = String.format(Locale.US,
                        "%s candidate %s cleared the promotion gate over stereo winner %s (score margin %.3f; objective gain %.3f; evidence bonus %.3f).",
                        analysisOnly ? "Analysis-only stem-control" : "Stem-audio delta",
                        bestStem.strategy, guardedNextGeneration.strategy,
                        margin,
                        objectiveGain, bestStem.stemEvidenceBonus);
                result.selectionReason = result.stemDecision;
            }
        } else if (result.stemArchiveSupplied) {
            result.stereoFallback = true;
            result.stemDecision = result.stemCandidatesEligible == 0
                    ? (sourceAnalysis.stemArchiveAnalysisOnlyAccepted
                        ? "Analysis-only stems were admitted but produced no safe stereo-control candidate; stereo-only winner retained."
                        : "Optional stems were rejected or produced no safe candidate; stereo-only winner retained.")
                    : "Eligible stem candidates were measured but the stereo-only strategy achieved the stronger verified score.";
        }
        boolean stemPromoted = best.stemAware;
        for (Candidate candidate : result.candidates) {
            if (candidate.stemAware && (!stemPromoted || candidate != best)) {
                candidate.promotionBlocked = true;
            }
        }
        discardPromotionBlockedAudio(result);

        // v0.14.5 final meaningful-mastering gate. A non-transparent winner
        // must contain a measurable level-matched sonic delta against the
        // transparent delivery control, unless a narrow event-specific repair
        // has already proved benefit with its own independent evidence.
        if (transparent != null && best != transparent) {
            try {
                result.meaningfulChangeComparedStrategy = best.strategy;
                result.selectedVsTransparentDelta = MasteringDeltaEvidence.compare(
                        transparent.file, best.file);
                best.masteringDelta = result.selectedVsTransparentDelta;
                boolean targeted = strongTargetedJustification(best);
                boolean broadRequired = result.broadMasteringNeed != null
                        && result.broadMasteringNeed.required;
                boolean narrowCannotReplaceBroad = broadRequired
                        && !result.selectedVsTransparentDelta.broadMeaningful
                        && result.broadMasteringAnchor != null
                        && best != result.broadMasteringAnchor;

                if (narrowCannotReplaceBroad) {
                    Candidate rejectedNarrowOnly = best;
                    rejectedNarrowOnly.promotionBlocked = true;
                    rejectedNarrowOnly.rejectionReasons.add(
                            "v0.14.5 broad-mastering requirement: narrow-only candidate cannot replace the verified broad anchor");
                    best = result.broadMasteringAnchor;
                    result.broadMasteringAnchorUsed = true;
                    result.meaningfulChangeRollback = true;
                    result.meaningfulChangeComparedStrategy = best.strategy;
                    result.selectedVsTransparentDelta = result.broadMasteringAnchorDelta != null
                            ? result.broadMasteringAnchorDelta
                            : MasteringDeltaEvidence.compare(transparent.file, best.file);
                    best.masteringDelta = result.selectedVsTransparentDelta;
                    best.meaningfulChangePassed = true;
                    best.meaningfulChangeDecision = "PASS: verified broad-mastering anchor retained because "
                            + rejectedNarrowOnly.strategy
                            + " only produced a narrow/sparse delta and cannot stand in for whole-song mastering on this source; "
                            + result.selectedVsTransparentDelta.summary();
                    result.meaningfulChangeDecision = best.strategy + ": "
                            + best.meaningfulChangeDecision;
                    result.selectionReason = String.format(Locale.US,
                            "%s had independent narrow-repair evidence but only a %.2f dB level-matched residual; source analysis required broad mastering, so verified broad anchor %s was retained.",
                            rejectedNarrowOnly.strategy,
                            rejectedNarrowOnly.masteringDelta == null
                                    ? Double.NaN
                                    : rejectedNarrowOnly.masteringDelta.levelMatchedResidualRelativeDb,
                            best.strategy);
                    discardCandidateAudio(rejectedNarrowOnly);
                } else {
                    best.meaningfulChangePassed = result.selectedVsTransparentDelta.broadMeaningful
                            || targeted;
                    best.meaningfulChangeDecision = targeted
                            ? "PASS: narrow correction retained by independent target-specific evidence; "
                            + result.selectedVsTransparentDelta.summary()
                            : (result.selectedVsTransparentDelta.broadMeaningful
                            ? "PASS: level-matched whole-song sonic delta cleared the broad-change floor; "
                            + result.selectedVsTransparentDelta.summary()
                            : "BLOCKED: candidate was a near-copy after level matching and had no independent narrow-repair justification; "
                            + result.selectedVsTransparentDelta.summary());
                    result.meaningfulChangeDecision = best.strategy + ": "
                            + best.meaningfulChangeDecision;
                }
                if (!best.meaningfulChangePassed) {
                    Candidate rejectedNearCopy = best;
                    rejectedNearCopy.promotionBlocked = true;
                    rejectedNearCopy.rejectionReasons.add(
                            "v0.14.5 meaningful-mastering gate: near-copy after level matching");
                    result.meaningfulChangeRollback = true;
                    result.transparentRollback = true;
                    best = transparent;
                    result.selectionReason = String.format(Locale.US,
                            "%s survived technical QC but failed the final meaningful-mastering gate (level-matched residual %.2f dB relative to transparent); transparent control retained rather than claiming a sonic master that was effectively delivery-only.",
                            rejectedNearCopy.strategy,
                            result.selectedVsTransparentDelta.levelMatchedResidualRelativeDb);
                    discardCandidateAudio(rejectedNearCopy);
                }
            } catch (IOException deltaError) {
                Candidate rejectedUnverified = best;
                rejectedUnverified.promotionBlocked = true;
                rejectedUnverified.rejectionReasons.add(
                        "v0.14.5 meaningful-mastering gate could not verify the candidate: "
                                + clean(deltaError));
                result.meaningfulChangeRollback = true;
                result.transparentRollback = true;
                result.meaningfulChangeDecision = rejectedUnverified.strategy
                        + ": verification unavailable; transparent control retained ("
                        + clean(deltaError) + ")";
                best = transparent;
                result.selectionReason = result.meaningfulChangeDecision;
                discardCandidateAudio(rejectedUnverified);
            }
        } else if (best == transparent) {
            best.meaningfulChangePassed = true;
            best.meaningfulChangeDecision =
                    "Transparent control selected: no non-transparent candidate proved a justified sonic improvement.";
            result.meaningfulChangeDecision = best.meaningfulChangeDecision;
        }

        if (result.selectionReason == null) {
            result.selectionReason = defaultSelectionReason(
                    result.rankedEligible.get(0), best);
        }
        best.promotionBlocked = false;
        for (Candidate candidate : result.candidates) {
            candidate.selectedAfterPromotionGates = candidate == best;
        }
        result.selected = best;
        result.gentlerDecision = "v0.14.5 candidate-budget policy: no automatic gentler re-render after a verified winner is selected.";
        progress.update("Mastering winner selected • " + result.selected.strategy, 100);
        return result;
    }



    /**
     * Render up to three source-shaped fallback plans only when the normal
     * broad candidate set could not establish a safe anchor. The first
     * qualifying candidate is not automatically accepted; all rungs are
     * measured, then the safest candidate within a small technical-score
     * window of the best result is retained.
     */
    /**
     * Performs the same fully-verified v0.15.5 sparse candidate render as the
     * fifth fallback rung. Only exceptionally peak-limited original sources
     * get this early audition. A failed audition cannot suppress competitors.
     */
    private static boolean tryEarlySparsePeakCandidate(
            AudioAnalyzer.StreamProvider source,
            AnalysisResult sourceAnalysis,
            AutonomousMasteringBrain.Result brain,
            Result result,
            File workDirectory,
            AudioAnalyzer.Progress progress) {
        Candidate transparent = result.transparent;
        if (transparent == null || !transparent.eligible || transparent.render == null
                || result.passageBaseline == null) return false;
        AutonomousMasteringBrain.Plan plan = AdaptiveBroadMastering.sparsePeakPlan(
                result.broadMasteringNeed, brain);
        if (plan == null) return false;
        Candidate c = new Candidate();
        c.sparsePeakAware = true;
        c.plan = plan;
        c.strategy = plan.strategy;
        c.file = new File(workDirectory, "internal-adaptive-05-" + safeName(c.strategy) + ".wav");
        c.renderAttempts = 1;
        result.candidates.add(c);
        result.adaptiveBroadCandidatesAttempted++;
        progress.update("Early sparse-peak audition: measured full-resolution render", 0);
        try {
            c.render = AutonomousMasteringRenderer.render(source, sourceAnalysis, plan,
                    c.file, candidateWork(workDirectory, c), -1.10, true,
                    MasterRenderer.LimiterProfile.SPARSE_MUSICAL,
                    rangeProgress(progress, "Early sparse-peak • render", 0, 58));
            c.rendered = true;
            c.measuredFeatures = AutonomousMasteringBrain.measureFeatures(
                    () -> new FileInputStream(c.file), c.render.master.verification,
                    rangeProgress(progress, "Early sparse-peak • verification", 58, 100));
            c.measuredObjective = AutonomousMasteringBrain.objective(
                    c.measuredFeatures, c.render.master.verification);
            evaluate(c, sourceAnalysis, result,
                    AdaptiveDynamicsPreservation.envelope(result.broadMasteringNeed));
            if (c.eligible) {
                c.masteringDelta = MasteringDeltaEvidence.compare(transparent.file, c.file);
            }
            if (SparsePeakRecovery.promotionSafe(c, transparent,
                    result.sourceObjective)) {
                result.earlySparsePeakShortcut = true;
                result.earlySparsePeakDecision = "PASS: full WAV render, verified target and true peak, sparse limiter, 270-window passage preservation, whole-song delta and unchanged technical/objective gates";
                result.adaptiveBroadCandidatesEligible++;
                result.adaptiveBroadAnchor = c;
                result.broadMasteringAnchor = c;
                result.broadMasteringAnchorDelta = c.masteringDelta;
                result.broadMasteringUnresolved = false;
                result.adaptiveBroadDecision = "v0.15.6 early verified sparse-peak anchor retained; competing full renders avoided only for constrained peak-recovery source";
                result.adaptiveBroadDiagnostics.add("Early guarded sparse-peak audition PASS: "
                        + c.masteringDelta.summary() + " • " + c.passageCheck.summary());
                return true;
            }
            c.promotionBlocked = true;
            result.earlySparsePeakDecision = "Candidate failed normal promotion, full competition restored";
            result.adaptiveBroadDiagnostics.add("Early guarded sparse-peak audition rejected: "
                    + (c.passageCheck == null ? "no passage result" : c.passageCheck.summary()));
        } catch (Exception error) {
            result.earlySparsePeakDecision = "Audition error: " + clean(error)
                    + "; full competition restored";
            recordIntegrityFailure(result, c, error);
        }
        // Unlike a successful shortcut, this control is not eligible to promote.
        c.eligible = false;
        c.promotionBlocked = true;
        discardCandidateAudio(c);
        return false;
    }

    private static void addAdaptiveBroadCandidates(
            AudioAnalyzer.StreamProvider source,
            AnalysisResult sourceAnalysis,
            AutonomousMasteringBrain.Result brain,
            Result result,
            File workDirectory,
            AudioAnalyzer.Progress progress) {
        Candidate transparent = result.transparent;
        if (transparent == null || transparent.file == null || !transparent.file.isFile()) {
            result.adaptiveBroadDecision = "Adaptive broad fallback unavailable: transparent control missing";
            return;
        }
        List<AutonomousMasteringBrain.Plan> plans = new ArrayList<>(AdaptiveBroadMastering.ladder(
                result.broadMasteringNeed, brain));
        if (plans.isEmpty()) {
            result.adaptiveBroadDecision = "Adaptive broad fallback unavailable: no source-shaped ladder could be generated";
            return;
        }
        ArrayList<Candidate> qualified = new ArrayList<>();
        AdaptiveDynamicsPreservation.Envelope dynamicsEnvelope =
                AdaptiveDynamicsPreservation.envelope(result.broadMasteringNeed);
        final int ordinaryRungs = plans.size();
        int transientCrestRejections = 0;
        for (int i = 0; i <= ordinaryRungs + 1; i++) {
            if (i == ordinaryRungs) {
                if (!AdaptiveBroadMastering.transientSafeTonalEligible(
                        result.broadMasteringNeed, ordinaryRungs,
                        transientCrestRejections, qualified.size())) break;
                AutonomousMasteringBrain.Plan extra =
                        AdaptiveBroadMastering.transientSafeTonalPlan(result.broadMasteringNeed, brain);
                if (extra == null) break;
                plans.add(extra);
                result.adaptiveBroadDiagnostics.add(
                        "v0.15.4 tonal-only audition triggered by "
                        + transientCrestRejections + " independently verified crest-loss rejections; standard passage/peak/quality gates unchanged");
            }
            if (i == ordinaryRungs + 1) {
                if (!SparsePeakRecovery.mayAudition(sourceAnalysis,
                        result.broadMasteringNeed, transientCrestRejections,
                        qualified.size())) break;
                AutonomousMasteringBrain.Plan extra =
                        AdaptiveBroadMastering.sparsePeakPlan(result.broadMasteringNeed, brain);
                if (extra == null) break;
                plans.add(extra);
                result.adaptiveBroadDiagnostics.add(
                        "v0.15.5 sparse-peak audition triggered by verified peak constraint and "
                        + transientCrestRejections + " transient-crest rejections; limiter "
                        + "activity, musical windows, measured loudness and peak safety gated separately");
            }
            AutonomousMasteringBrain.Plan plan = plans.get(i);
            Candidate c = new Candidate();
            c.sparsePeakAware = i == ordinaryRungs + 1;
            c.plan = plan;
            c.strategy = plan.strategy;
            c.file = new File(workDirectory, "internal-adaptive-" + (i + 1)
                    + "-" + safeName(c.strategy) + ".wav");
            c.renderAttempts = 1;
            result.candidates.add(c);
            result.adaptiveBroadCandidatesAttempted++;
            String label = "Adaptive broad candidate " + (i + 1) + "/" + plans.size()
                    + " • " + c.strategy;
            int start = i == ordinaryRungs ? 96 : segmentStart(i, ordinaryRungs, 0, 95);
            int end = i == ordinaryRungs ? 100 : segmentEnd(i, ordinaryRungs, 0, 95);
            int mid = start + Math.max(1, (int) Math.round((end - start) * 0.58));
            progress.update(label + " • preparing", start);
            try {
                c.render = c.sparsePeakAware
                        ? AutonomousMasteringRenderer.render(source, sourceAnalysis, plan,
                                c.file, candidateWork(workDirectory, c), -1.10, true,
                                MasterRenderer.LimiterProfile.SPARSE_MUSICAL,
                                rangeProgress(progress, label + " • render", start, mid))
                        : renderCandidate(source, sourceAnalysis, plan, c.file,
                                candidateWork(workDirectory, c),
                                rangeProgress(progress, label + " • render", start, mid));
                c.rendered = true;
                c.measuredFeatures = AutonomousMasteringBrain.measureFeatures(
                        () -> new FileInputStream(c.file),
                        c.render.master.verification,
                        rangeProgress(progress, label + " • verification", mid, end));
                c.measuredObjective = AutonomousMasteringBrain.objective(
                        c.measuredFeatures, c.render.master.verification);
                evaluate(c, sourceAnalysis, result, dynamicsEnvelope);
                if (i < ordinaryRungs && c.passageCheck != null) {
                    for (PassagePreservation.Finding finding : c.passageCheck.findings) {
                        if ("TRANSIENT_CREST_LOSS".equals(finding.kind)) {
                            transientCrestRejections++;
                            break; // count each failed candidate once, not every window
                        }
                    }
                }
                if (!c.eligible) {
                    String diagnostic = String.format(Locale.US,
                            "%s: HARD_SAFETY_OR_PASSAGE_REJECTED • %s",
                            c.strategy, c.rejectionReasons.isEmpty()
                                    ? c.status : String.join(" | ", c.rejectionReasons));
                    result.adaptiveBroadDiagnostics.add(diagnostic);
                    continue;
                }
                c.masteringDelta = MasteringDeltaEvidence.compare(transparent.file, c.file);
                AdaptiveDynamicsPreservation.Result dynamicsPreservation =
                        AdaptiveDynamicsPreservation.assess(c, transparent,
                                result.broadMasteringNeed, result.sourceObjective);
                boolean demandAware = dynamicsEnvelope.active;
                DemandAwareObjectiveGate.Decision demandGate = demandAware
                        ? DemandAwareObjectiveGate.assess(c, transparent,
                                result.broadMasteringNeed, dynamicsPreservation,
                                result.sourceObjective,
                                result.broadMasteringAnchor == null,
                                c.masteringDelta.broadMeaningful)
                        : null;
                boolean preserve = c.sparsePeakAware
                        ? SparsePeakRecovery.stableOutput(sourceAnalysis,
                                transparent.render.master.verification, c.render.master)
                                && c.passageCheck != null && c.passageCheck.passed()
                        : demandAware ? dynamicsPreservation.envelopePassed
                        : naturalPreservationPassed(c, transparent);
                boolean technical = c.sparsePeakAware
                        ? c.technicalScore >= transparent.technicalScore - 0.75
                        : demandAware ? demandGate.technicalPassed
                        : c.technicalScore >= transparent.technicalScore - 0.10;
                boolean objective = c.sparsePeakAware
                        ? c.measuredObjective >= result.sourceObjective - 0.05
                        : demandAware ? demandGate.objectivePassed
                        : c.measuredObjective >= result.sourceObjective - 0.02;
                boolean accepted = c.sparsePeakAware
                        ? SparsePeakRecovery.promotionSafe(c, transparent,
                                result.sourceObjective)
                        : demandAware ? demandGate.passed
                        : c.masteringDelta.broadMeaningful && preserve && technical && objective;
                String diagnostic = String.format(Locale.US,
                        "%s: broad=%s • preservation=%s • technical=%s (raw %.4f vs ref %.4f) • objective=%s (%.4f vs source %.4f) • passage=%s • %s • %s • %s",
                        c.strategy, c.masteringDelta.broadMeaningful, preserve,
                        technical, c.technicalScore, transparent.technicalScore,
                        objective, c.measuredObjective, result.sourceObjective,
                        c.passageCheck == null ? "NOT_CHECKED" : c.passageCheck.summary(),
                        demandAware ? dynamicsPreservation.decision
                                : "standard natural-preservation envelope",
                        demandAware ? demandGate.diagnostic
                                : "standard objective/technical gates",
                        c.masteringDelta.summary());
                result.adaptiveBroadDiagnostics.add(diagnostic);
                if (accepted) {
                    qualified.add(c);
                    result.adaptiveBroadCandidatesEligible++;
                } else {
                    c.promotionBlocked = true;
                    c.rejectionReasons.add("v0.14.6 adaptive broad gate: " + diagnostic);
                }
            } catch (Exception error) {
                c.status = "adaptive broad render rejected: " + clean(error);
                c.rejectionReasons.add(c.status);
                c.eligible = false;
                recordIntegrityFailure(result, c, error);
            }
        }
        // A dynamics-led fallback has already proved the required measured benefit.
        // Prefer the mildest eligible rung; do not chase an abstract score lead.
        Candidate chosen = dynamicsEnvelope.active
                ? leastAggressiveAdaptiveBroad(qualified)
                : safestAdaptiveBroad(qualified);
        for (Candidate c : result.candidates) {
            if (c.strategy != null && c.strategy.startsWith("adaptive_broad_")
                    && c != chosen) {
                c.promotionBlocked = true;
                discardCandidateAudio(c);
            }
        }
        if (chosen != null) {
            result.adaptiveBroadAnchor = chosen;
            result.broadMasteringAnchor = chosen;
            result.broadMasteringAnchorDelta = chosen.masteringDelta;
            result.broadMasteringUnresolved = false;
            result.adaptiveBroadDecision = String.format(Locale.US,
                    "%s established a verified adaptive broad anchor after %d/%d auditions cleared unchanged hard safety, passage, quality and objective gates; %s",
                    chosen.strategy, result.adaptiveBroadCandidatesEligible,
                    result.adaptiveBroadCandidatesAttempted,
                    chosen.masteringDelta == null ? "delta unavailable" : chosen.masteringDelta.summary());
        } else {
            result.adaptiveBroadDecision = String.format(Locale.US,
                    "0/%d adaptive broad auditions cleared broad-delta plus applicable preservation/technical/objective gates; transparent fallback remains available",
                    result.adaptiveBroadCandidatesAttempted);
        }
    }

    static Candidate leastAggressiveAdaptiveBroad(List<Candidate> candidates) {
        Candidate best = null;
        for (Candidate c : candidates) {
            if (c == null || !c.eligible || c.promotionBlocked) continue;
            if (best == null || adaptiveRung(c.strategy) < adaptiveRung(best.strategy)
                    || (adaptiveRung(c.strategy) == adaptiveRung(best.strategy)
                        && c.processingCost < best.processingCost)) best = c;
        }
        return best;
    }

    private static int adaptiveRung(String strategy) {
        if ("adaptive_broad_gentle".equals(strategy)) return 0;
        if ("adaptive_broad_balanced".equals(strategy)) return 1;
        if ("adaptive_broad_firm".equals(strategy)) return 2;
        return 3;
    }

    static Candidate safestAdaptiveBroad(List<Candidate> candidates) {
        Candidate bestTechnical = null;
        for (Candidate c : candidates) {
            if (c == null || !c.eligible || c.promotionBlocked) continue;
            if (bestTechnical == null || c.technicalScore > bestTechnical.technicalScore) {
                bestTechnical = c;
            }
        }
        if (bestTechnical == null) return null;
        Candidate safest = bestTechnical;
        for (Candidate c : candidates) {
            if (c == null || !c.eligible || c.promotionBlocked) continue;
            if (c.technicalScore >= bestTechnical.technicalScore - 0.030
                    && c.processingCost < safest.processingCost) {
                safest = c;
            }
        }
        return safest;
    }

    /**
     * Pick the strongest safe whole-song core candidate when source evidence
     * says broad mastering is still required. A broad anchor must make a real
     * level-matched whole-song change and preserve natural dynamics/limiting.
     * It may trail the transparent control slightly on a generic score because
     * that score is not allowed to collapse "needs mastering" into "do less".
     */
    static Candidate bestBroadCoreAnchor(Result result,
                                                 Candidate transparent) {
        Candidate best = null;
        for (Candidate candidate : result.rankedEligible) {
            if (candidate == transparent || candidate.stemAware
                    || candidate.intelligenceAware || candidate.advancedAware
                    || candidate.professionalAware || candidate.nextGenerationAware
                    || candidate.file == null || !candidate.file.isFile()) {
                continue;
            }
            // Measure whole-song delta before final gating: the adaptive
            // fallback always requires independently verified broad evidence.
            try {
                MasteringDeltaEvidence.Result delta = MasteringDeltaEvidence.compare(
                        transparent.file, candidate.file);
                candidate.masteringDelta = delta;
                if (!delta.broadMeaningful) continue;
            } catch (IOException error) {
                candidate.rejectionReasons.add(
                        "Broad-anchor delta verification unavailable: " + clean(error));
                continue;
            }
            boolean adaptive = candidate.strategy != null
                    && candidate.strategy.startsWith("adaptive_broad_");
            AdaptiveDynamicsPreservation.Result dynamicsPreservation = adaptive
                    ? AdaptiveDynamicsPreservation.assess(candidate, transparent,
                            result.broadMasteringNeed, result.sourceObjective)
                    : null;
            boolean demandAware = dynamicsPreservation != null
                    && AdaptiveDynamicsPreservation.envelope(result.broadMasteringNeed).active;
            if (demandAware) {
                // Only the chosen, already hard-verified unresolved fallback can
                // use the relaxed limits. Any other adaptive candidate fails closed.
                DemandAwareObjectiveGate.Decision gate = DemandAwareObjectiveGate.assess(
                        candidate, transparent, result.broadMasteringNeed,
                        dynamicsPreservation, result.sourceObjective,
                        result.adaptiveBroadAnchor == candidate,
                        candidate.masteringDelta != null
                                && candidate.masteringDelta.broadMeaningful);
                if (!gate.passed) {
                    candidate.rejectionReasons.add(gate.diagnostic);
                    continue;
                }
            } else {
                if (candidate.sparsePeakAware) {
                    if (!SparsePeakRecovery.promotionSafe(candidate, transparent,
                            result.sourceObjective)) continue;
                } else {
                    if (!naturalPreservationPassed(candidate, transparent)) continue;
                    if (candidate.technicalScore < transparent.technicalScore - 0.10) continue;
                }
            }
            if (candidate == result.adaptiveBroadAnchor && demandAware) {
                return candidate;
            }
            if (best == null || candidate.score > best.score
                    || (candidate.score == best.score
                    && candidate.processingCost < best.processingCost)) {
                best = candidate;
            }
        }
        return best;
    }

    private static boolean strongTargetedJustification(Candidate candidate) {
        if (candidate == null) return false;
        if (candidate.professionalAware
                && candidate.professionalMode != null
                && ProfessionalMasteringRenderer.isVocal(candidate.professionalMode)
                && candidate.qualityEvidence != null
                && candidate.qualityEvidence.passed()) {
            return true;
        }
        if (candidate.advancedAware && candidate.advancedIntelligence != null
                && candidate.advancedMode == AdvancedStereoRenderer.Mode.ARTIFACT_REPAIR) {
            return candidate.advancedIntelligence.correctionEvents >= 2
                    && candidate.advancedIntelligence.artifactReductionDb >= 0.08;
        }
        if (candidate.intelligenceAware && candidate.stereoIntelligence != null) {
            return candidate.stereoIntelligence.correctionEvents >= 2
                    && candidate.stereoIntelligence.measuredTargetBenefit >= 0.15;
        }
        if (candidate.nextGenerationAware
                && candidate.nextGenerationIntelligence != null
                && (candidate.nextGenerationMode
                == NextGenerationMasteringRenderer.Mode.PERSISTENT_TONE_REPAIR
                || candidate.nextGenerationMode
                == NextGenerationMasteringRenderer.Mode.CHANNEL_INTEGRITY)) {
            return candidate.nextGenerationIntelligence.correctionEvents > 0
                    && candidate.nextGenerationIntelligence.measuredTargetBenefit >= 0.10;
        }
        if (candidate.stemAware && candidate.stemAssist != null) {
            return candidate.stemAssist.correctionEvents > 0
                    && candidate.stemEvidenceBonus >= 0.04;
        }
        return false;
    }


    private static boolean promotionEvidenceWorthFullScan(Candidate candidate,double measured,double floor,String label){
        if(Double.isFinite(measured)&&measured>=floor)return true;
        candidate.eligible=false;candidate.promotionBlocked=true;
        candidate.status=String.format(Locale.US,"shortlisted render measured, but full feature scan skipped: %s %.3f below %.3f promotion floor",label,measured,floor);
        candidate.rejectionReasons.add(candidate.status);
        discardCandidateAudio(candidate);
        return false;
    }

    private static long markCandidateStage(Result result,String stage,long started){
        long now=System.nanoTime();
        result.candidateStageMs.put(stage,Math.max(0L,(now-started)/1_000_000L));
        return now;
    }

    /** Repairs stage: one render per family, its first (combined or gentlest) mode. */
    private static <T> void leanModes(Result result, List<T> modes) {
        if (result.scope.leanModes && modes.size() > 1) {
            T first = modes.get(0);
            modes.clear();
            modes.add(first);
        }
    }

    private static List<AutonomousMasteringBrain.Plan> shortlistCorePlans(
            List<AutonomousMasteringBrain.Plan> plans) {
        ArrayList<AutonomousMasteringBrain.Plan> out = new ArrayList<>();
        AutonomousMasteringBrain.Plan transparent=null, conservative=null, sonic=null, full=null, album=null, fallback=null;
        for(AutonomousMasteringBrain.Plan p:plans){
            if(p==null)continue;String name=p.strategy==null?"":p.strategy;
            if("transparent_flat_gain".equals(name))transparent=p;
            else if("conservative_corrective".equals(name))conservative=p;
            else if("sonic_finish".equals(name))sonic=p;
            else if("full_combined".equals(name))full=p;
            else if(name.toLowerCase(Locale.US).contains("album"))album=p;
            if(fallback==null&&p.hasAudioChanges())fallback=p;
        }
        if(transparent!=null)out.add(transparent);
        if(conservative!=null&&conservative.hasAudioChanges())out.add(conservative);
        AutonomousMasteringBrain.Plan strong=sonic!=null?sonic:full;
        if(strong!=null&&strong.hasAudioChanges()&&!out.contains(strong))out.add(strong);
        if(album!=null&&album.hasAudioChanges()&&!out.contains(album))out.add(album);
        if(out.size()==1&&fallback!=null&&!out.contains(fallback))out.add(fallback);
        return out;
    }

    private static AudioAnalyzer.Progress scopedProgress(AudioAnalyzer.Progress parent,
                                                         String candidate,
                                                         String phase) {
        return (stage, percent) -> parent.update(candidate + " • " + phase + " • " + stage, percent);
    }

    private static AudioAnalyzer.Progress rangeProgress(AudioAnalyzer.Progress parent,
                                                        String prefix, int start, int end) {
        final int lo = Math.max(0, Math.min(100, start));
        final int hi = Math.max(lo, Math.min(100, end));
        final int[] last = {lo};
        return (stage, percent) -> {
            int p = Math.max(0, Math.min(100, percent));
            int mapped = lo + (int) Math.round((hi - lo) * (p / 100.0));
            if (mapped < last[0]) mapped = last[0];
            else last[0] = mapped;
            parent.update(prefix == null || prefix.isEmpty() ? stage : prefix + " • " + stage, mapped);
        };
    }

    private static int segmentStart(int index, int count, int start, int end) {
        if (count <= 0) return start;
        return start + (int) Math.floor((end - start) * (index / (double) count));
    }

    private static int segmentEnd(int index, int count, int start, int end) {
        if (count <= 0) return end;
        return start + (int) Math.floor((end - start) * ((index + 1) / (double) count));
    }

    static String defaultSelectionReason(Candidate rawScoreLeader, Candidate selected) {
        return rawScoreLeader == selected
                ? String.format(Locale.US,
                    "%s held raw score rank 1 at %.3f and cleared every applicable no-change and module-promotion gate while preserving duration, loudness, peaks, continuity, phase and dynamics guardrails.",
                    selected.strategy, selected.score)
                : String.format(Locale.US,
                    "%s led the raw score table at %.3f but did not clear the applicable promotion gate; %s (raw score rank %d, %.3f) was selected after the no-change and module-promotion gates.",
                    rawScoreLeader.strategy, rawScoreLeader.score,
                    selected.strategy, selected.rank, selected.score);
    }

    private static void addStereoIntelligenceCandidates(
            AudioAnalyzer.StreamProvider source,
            AnalysisResult sourceAnalysis,
            Result result,
            File workDirectory,
            AudioAnalyzer.Progress progress) {
        StereoIntelligenceModel.Result model = sourceAnalysis.stereoIntelligence;
        Candidate transparent = result.transparent;
        if (model == null || transparent == null || transparent.plan == null
                || transparent.plan.hasAudioChanges()) {
            result.intelligenceDecision = "Stereo-intelligence analysis or transparent baseline was unavailable";
            return;
        }
        ArrayList<StereoIntelligenceRenderer.Mode> modes = new ArrayList<>();
        Candidate retainedFamilyFinalist = null;
        if (model.availableModules() >= 2) {
            modes.add(StereoIntelligenceRenderer.Mode.COMBINED);
            int eventCount=model.automaticCount(StereoIntelligenceModel.Kind.SPECTRAL_HF,StereoIntelligenceModel.Kind.SPECTRAL_HARSH,StereoIntelligenceModel.Kind.SPECTRAL_MUD);
            int lowCount=model.automaticCount(StereoIntelligenceModel.Kind.LOW_SUB_BLOOM,StereoIntelligenceModel.Kind.LOW_BASS_BLOOM,StereoIntelligenceModel.Kind.LOW_PHASE_RISK);
            int vocalCount=model.automaticCount(StereoIntelligenceModel.Kind.VOCAL_FOCUS);
            if(eventCount>=lowCount&&eventCount>=vocalCount&&eventCount>0)modes.add(StereoIntelligenceRenderer.Mode.EVENT_REPAIR);
            else if(lowCount>=vocalCount&&lowCount>0)modes.add(StereoIntelligenceRenderer.Mode.LOW_END);
            else if(vocalCount>0)modes.add(StereoIntelligenceRenderer.Mode.VOCAL_FOCUS);
        } else if (model.hasEventRepair()) modes.add(StereoIntelligenceRenderer.Mode.EVENT_REPAIR);
        else if (model.hasLowEnd()) modes.add(StereoIntelligenceRenderer.Mode.LOW_END);
        else if (model.hasVocalFocus()) modes.add(StereoIntelligenceRenderer.Mode.VOCAL_FOCUS);
        leanModes(result, modes);
        for (int modeIndex=0; modeIndex<modes.size(); modeIndex++) {
            StereoIntelligenceRenderer.Mode mode=modes.get(modeIndex);
            Candidate candidate = new Candidate();
            candidate.strategy = mode.strategy;
            candidate.plan = transparent.plan;
            candidate.intelligenceAware = true;
            candidate.intelligenceMode = mode;
            candidate.renderAttempts = 1;
            candidate.file = new File(workDirectory,
                    "internal-stereo-intelligence-" + safeName(mode.strategy) + ".wav");
            result.candidates.add(candidate);
            result.intelligenceCandidatesAttempted++;
            String label="Stereo candidate "+(modeIndex+1)+"/"+modes.size()+" • "+mode.strategy;
            int localStart=segmentStart(modeIndex,modes.size(),0,100);
            int localEnd=segmentEnd(modeIndex,modes.size(),0,100);
            int localMid=localStart+Math.max(1,(int)Math.round((localEnd-localStart)*0.58));
            progress.update(label+" • preparing", localStart);
            try {
                candidate.stereoIntelligence = StereoIntelligenceRenderer.render(
                        source, sourceAnalysis, transparent.plan, mode,
                        candidate.file, candidateWork(workDirectory, candidate),
                        rangeProgress(progress,label+" • render",localStart,localMid));
                candidate.render = candidate.stereoIntelligence.render;
                candidate.rendered = true;
                if(!promotionEvidenceWorthFullScan(candidate,candidate.stereoIntelligence.measuredTargetBenefit,0.08,
                        "authoritative-stereo targeted benefit"))continue;
                candidate.measuredFeatures = AutonomousMasteringBrain.measureFeatures(
                        () -> new FileInputStream(candidate.file),
                        candidate.render.master.verification, rangeProgress(progress,label+" • verification",localMid,localEnd));
                candidate.measuredObjective = AutonomousMasteringBrain.objective(
                        candidate.measuredFeatures,
                        candidate.render.master.verification);
                candidate.rendered = true;
                candidate.intelligenceEvidenceBonus = 0.09
                        * candidate.stereoIntelligence.evidenceBenefit
                        + 0.055 * candidate.stereoIntelligence.measuredTargetBenefit
                        + 0.025 * candidate.stereoIntelligence.modelConfidence;
                evaluate(candidate, sourceAnalysis, result);
                if (candidate.eligible) result.intelligenceCandidatesEligible++;
                retainedFamilyFinalist = retainFamilyFinalistAudio(
                        retainedFamilyFinalist, candidate, false);
            } catch (Exception error) {
                candidate.status = "stereo-intelligence render rejected: " + clean(error);
                candidate.rejectionReasons.add(candidate.status);
                candidate.eligible = false;
                recordIntegrityFailure(result, candidate, error);
                if (candidate.file.exists() && !candidate.file.delete()) {
                    candidate.file.deleteOnExit();
                }
            }
        }
        result.intelligenceDecision = String.format(Locale.US,
                "%d/%d authoritative-stereo intelligence candidate(s) survived hard render verification; promotion still requires measured targeted benefit",
                result.intelligenceCandidatesEligible,
                result.intelligenceCandidatesAttempted);
    }

    private static void addStemCandidates(
            AudioAnalyzer.StreamProvider source,
            AnalysisResult sourceAnalysis,
            File stemArchive,
            Result result,
            File workDirectory,
            AudioAnalyzer.Progress progress) {
        if (stemArchive == null) return;
        if (!sourceAnalysis.stemArchiveAccepted
                && !sourceAnalysis.stemArchiveAnalysisOnlyAccepted) {
            result.stemDecision = "Optional stem archive reached Tier 3 rejection; stereo-only fallback remained active.";
            return;
        }
        Candidate transparent = result.transparent;
        if (transparent == null || transparent.plan == null
                || transparent.plan.hasAudioChanges()) {
            result.stemDecision = "Transparent stereo baseline was unavailable; stem candidates were not rendered.";
            return;
        }
        StemAssistRenderer.Mode[] modes = sourceAnalysis.stemArchiveAccepted
                ? new StemAssistRenderer.Mode[]{
                    StemAssistRenderer.Mode.VOCAL_HUMANITY,
                    StemAssistRenderer.Mode.MASKING_POCKETS,
                    StemAssistRenderer.Mode.COMBINED
                }
                : new StemAssistRenderer.Mode[]{
                    StemAssistRenderer.Mode.ANALYSIS_ONLY_GUIDED
                };
        Candidate retainedFamilyFinalist = null;
        if (result.scope.leanModes && modes.length > 1)
            modes = new StemAssistRenderer.Mode[]{modes[modes.length - 1]};
        for (int modeIndex=0; modeIndex<modes.length; modeIndex++) {
            StemAssistRenderer.Mode mode=modes[modeIndex];
            Candidate candidate = new Candidate();
            candidate.strategy = mode.strategy;
            candidate.plan = transparent.plan;
            candidate.stemAware = true;
            candidate.stemMode = mode;
            candidate.renderAttempts = 1;
            result.candidates.add(candidate);
            result.stemCandidatesAttempted++;
            String safe = safeName(mode.strategy);
            candidate.file = new File(workDirectory,
                    "internal-stem-" + safe + ".wav");
            String label="Stem candidate "+(modeIndex+1)+"/"+modes.length+" • "+safe;
            int localStart=segmentStart(modeIndex,modes.length,0,100);
            int localEnd=segmentEnd(modeIndex,modes.length,0,100);
            int localMid=localStart+Math.max(1,(int)Math.round((localEnd-localStart)*0.58));
            progress.update(label+" • preparing", localStart);
            try {
                candidate.stemAssist = StemAssistRenderer.render(
                        source, sourceAnalysis, stemArchive, transparent.plan,
                        mode, candidate.file, candidateWork(workDirectory, candidate), rangeProgress(progress,label+" • render",localStart,localMid));
                candidate.render = candidate.stemAssist.render;
                candidate.measuredFeatures = AutonomousMasteringBrain.measureFeatures(
                        () -> new FileInputStream(candidate.file),
                        candidate.render.master.verification, rangeProgress(progress,label+" • verification",localMid,localEnd));
                candidate.measuredObjective = AutonomousMasteringBrain.objective(
                        candidate.measuredFeatures,
                        candidate.render.master.verification);
                candidate.rendered = true;
                candidate.stemEvidenceBonus = 0.12
                        * candidate.stemAssist.evidenceBenefit
                        + 0.04 * candidate.stemAssist.modelConfidence;
                if (candidate.stemAssist.analysisOnlyControl) {
                    candidate.stemEvidenceBonus *= 0.55;
                }
                evaluate(candidate, sourceAnalysis, result);
                if (candidate.eligible) result.stemCandidatesEligible++;
                retainedFamilyFinalist = retainFamilyFinalistAudio(
                        retainedFamilyFinalist, candidate, true);
            } catch (Exception error) {
                candidate.status = "stem render rejected: " + clean(error);
                candidate.rejectionReasons.add(candidate.status);
                candidate.eligible = false;
                if (candidate.file.exists() && !candidate.file.delete()) {
                    candidate.file.deleteOnExit();
                }
            }
        }
        result.stemDecision = String.format(Locale.US,
                "%d/%d guarded %s candidate(s) survived render verification; final promotion still requires a measured win over stereo.",
                result.stemCandidatesEligible, result.stemCandidatesAttempted,
                sourceAnalysis.stemArchiveAnalysisOnlyAccepted
                        ? "analysis-only stem-control" : "stem-audio delta");
    }

    private static void addAdvancedCandidates(
            AudioAnalyzer.StreamProvider source,
            AnalysisResult sourceAnalysis,
            Result result,
            File workDirectory,
            AudioAnalyzer.Progress progress) {
        AdvancedStereoModel.Result model = sourceAnalysis.advancedIntelligence;
        Candidate transparent = result.transparent;
        if (model == null || transparent == null || transparent.plan == null
                || transparent.plan.hasAudioChanges()) {
            result.advancedDecision = "Advanced analysis or transparent baseline was unavailable";
            return;
        }
        ArrayList<AdvancedStereoRenderer.Mode> modes = new ArrayList<>();
        Candidate retainedFamilyFinalist = null;
        if (model.availableModules() >= 2) {
            modes.add(AdvancedStereoRenderer.Mode.ADVANCED_COMBINED);
            int artifactCount=model.automaticCount(AdvancedStereoModel.Kind.ARTIFACT_HF_FIZZ,AdvancedStereoModel.Kind.ARTIFACT_METALLIC_RESONANCE,AdvancedStereoModel.Kind.ARTIFACT_PHASEY_HF);
            int transientCount=model.automaticCount(AdvancedStereoModel.Kind.TRANSIENT_PEAK_CONTROL);
            int sectionCount=model.automaticCount(AdvancedStereoModel.Kind.SECTION_QUIET_CONTOUR,AdvancedStereoModel.Kind.SECTION_ENERGY_CONTOUR);
            if(artifactCount>=transientCount&&artifactCount>=sectionCount&&artifactCount>0)modes.add(AdvancedStereoRenderer.Mode.ARTIFACT_REPAIR);
            else if(transientCount>=sectionCount&&transientCount>0)modes.add(AdvancedStereoRenderer.Mode.TRANSIENT_INTELLIGENCE);
            else if(sectionCount>0)modes.add(AdvancedStereoRenderer.Mode.SECTION_AWARE);
        } else if (model.hasArtifactRepair()) modes.add(AdvancedStereoRenderer.Mode.ARTIFACT_REPAIR);
        else if (model.hasTransientControl()) modes.add(AdvancedStereoRenderer.Mode.TRANSIENT_INTELLIGENCE);
        else if (model.hasSectionAware()) modes.add(AdvancedStereoRenderer.Mode.SECTION_AWARE);
        leanModes(result, modes);
        for (int modeIndex=0; modeIndex<modes.size(); modeIndex++) {
            AdvancedStereoRenderer.Mode mode=modes.get(modeIndex);
            Candidate candidate = new Candidate();
            candidate.strategy = mode.strategy;
            candidate.plan = transparent.plan;
            candidate.advancedAware = true;
            candidate.advancedMode = mode;
            candidate.renderAttempts = 1;
            candidate.file = new File(workDirectory,
                    "internal-v07-advanced-" + safeName(mode.strategy) + ".wav");
            result.candidates.add(candidate);
            result.advancedCandidatesAttempted++;
            String label="Advanced candidate "+(modeIndex+1)+"/"+modes.size()+" • "+mode.strategy;
            int localStart=segmentStart(modeIndex,modes.size(),0,100);
            int localEnd=segmentEnd(modeIndex,modes.size(),0,100);
            int localMid=localStart+Math.max(1,(int)Math.round((localEnd-localStart)*0.58));
            progress.update(label+" • preparing", localStart);
            try {
                candidate.advancedIntelligence = AdvancedStereoRenderer.render(
                        source, sourceAnalysis, transparent.plan, mode,
                        candidate.file, candidateWork(workDirectory, candidate), rangeProgress(progress,label+" • render",localStart,localMid));
                candidate.render = candidate.advancedIntelligence.render;
                candidate.rendered = true;
                if(!promotionEvidenceWorthFullScan(candidate,candidate.advancedIntelligence.measuredTargetBenefit,0.08,
                        "advanced-module targeted benefit"))continue;
                candidate.measuredFeatures = AutonomousMasteringBrain.measureFeatures(
                        () -> new FileInputStream(candidate.file),
                        candidate.render.master.verification, rangeProgress(progress,label+" • verification",localMid,localEnd));
                candidate.measuredObjective = AutonomousMasteringBrain.objective(
                        candidate.measuredFeatures,
                        candidate.render.master.verification);
                candidate.rendered = true;
                candidate.advancedEvidenceBonus = 0.09
                        * candidate.advancedIntelligence.evidenceBenefit
                        + 0.060
                        * candidate.advancedIntelligence.measuredTargetBenefit
                        + 0.025
                        * candidate.advancedIntelligence.modelConfidence;
                evaluate(candidate, sourceAnalysis, result);
                if (candidate.eligible) result.advancedCandidatesEligible++;
                retainedFamilyFinalist = retainFamilyFinalistAudio(
                        retainedFamilyFinalist, candidate, false);
            } catch (Exception error) {
                candidate.status = "Advanced intelligence render rejected: "
                        + clean(error);
                candidate.rejectionReasons.add(candidate.status);
                candidate.eligible = false;
                recordIntegrityFailure(result, candidate, error);
                if (candidate.file.exists() && !candidate.file.delete()) {
                    candidate.file.deleteOnExit();
                }
            }
        }
        result.advancedDecision = String.format(Locale.US,
                "%d/%d section/artifact/transient candidate(s) survived hard render verification; promotion still requires measured module-specific benefit",
                result.advancedCandidatesEligible,
                result.advancedCandidatesAttempted);
    }

    private static void addProfessionalCandidates(
            AudioAnalyzer.StreamProvider source,
            AnalysisResult sourceAnalysis,
            Result result,
            File workDirectory,
            AudioAnalyzer.Progress progress) {
        ProfessionalMasteringModel.Result model =
                sourceAnalysis.professionalIntelligence;
        Candidate transparent = result.transparent;
        if (model == null || transparent == null || transparent.plan == null
                || transparent.plan.hasAudioChanges()) {
            result.professionalDecision = "quality-first analysis or transparent baseline was unavailable";
            return;
        }
        ArrayList<ProfessionalMasteringRenderer.Mode> modes =
                new ArrayList<>();
        if(model.limiterCrestAvailable && !sourceAnalysis.sourceGuard.secondPassGuard)
            modes.add(ProfessionalMasteringRenderer.Mode.LIMITER_CREST);
        if(model.hasVocalArtifacts()){
            modes.add(ProfessionalMasteringRenderer.Mode.VOCAL_ARTIFACT_GENTLE);
            modes.add(ProfessionalMasteringRenderer.Mode.VOCAL_ARTIFACT);
        }
        if (result.scope.leanModes) modes.remove(ProfessionalMasteringRenderer.Mode.LIMITER_CREST);
        leanModes(result, modes);
        for (int modeIndex=0; modeIndex<modes.size(); modeIndex++) {
            ProfessionalMasteringRenderer.Mode mode=modes.get(modeIndex);
            Candidate candidate = new Candidate();
            candidate.strategy = mode.strategy;
            candidate.plan = transparent.plan;
            candidate.professionalAware = true;
            candidate.professionalMode = mode;
            candidate.renderAttempts = 1;
            candidate.file = new File(workDirectory,
                    "internal-quality-"
                            + safeName(mode.strategy) + ".wav");
            result.candidates.add(candidate);
            result.professionalCandidatesAttempted++;
            String label="Quality candidate "+(modeIndex+1)+"/"+modes.size()+" • "+mode.strategy;
            int localStart=segmentStart(modeIndex,modes.size(),0,100);
            int localEnd=segmentEnd(modeIndex,modes.size(),0,100);
            int localMid=localStart+Math.max(1,(int)Math.round((localEnd-localStart)*0.58));
            progress.update(label+" • preparing", localStart);
            try {
                candidate.professionalIntelligence =
                        ProfessionalMasteringRenderer.render(source,
                                sourceAnalysis, transparent.plan, mode,
                                candidate.file, candidateWork(workDirectory, candidate), rangeProgress(progress,label+" • render",localStart,localMid));
                candidate.render = candidate.professionalIntelligence.render;
                candidate.rendered = true;
                if(mode==ProfessionalMasteringRenderer.Mode.LIMITER_CREST &&
                        !promotionEvidenceWorthFullScan(candidate,candidate.professionalIntelligence.measuredTargetBenefit,0.08,
                                "limiter/crest targeted benefit"))continue;
                candidate.measuredFeatures =
                        AutonomousMasteringBrain.measureFeatures(
                                () -> new FileInputStream(candidate.file),
                                candidate.render.master.verification,
                                rangeProgress(progress,label+" • verification",localMid,localEnd));
                candidate.measuredObjective =
                        AutonomousMasteringBrain.objective(
                                candidate.measuredFeatures,
                                candidate.render.master.verification);
                candidate.rendered = true;
                candidate.professionalEvidenceBonus = 0.090
                        * candidate.professionalIntelligence.evidenceBenefit
                        + 0.065 * candidate.professionalIntelligence
                        .measuredTargetBenefit
                        + 0.025 * candidate.professionalIntelligence
                        .modelConfidence;
                evaluate(candidate, sourceAnalysis, result);
                if (candidate.eligible) {
                    result.professionalCandidatesEligible++;
                }
                // At most three quality candidates: retain all until independent benefit comparison.
            } catch (Exception error) {
                candidate.status = "quality-first render rejected: "
                        + clean(error);
                candidate.rejectionReasons.add(candidate.status);
                candidate.eligible = false;
                recordIntegrityFailure(result, candidate, error);
                if (candidate.file.exists() && !candidate.file.delete()) {
                    candidate.file.deleteOnExit();
                }
            }
        }
        result.professionalDecision = String.format(Locale.US,
                "%d/%d quality-first candidate(s) survived hard render verification; promotion still requires measured module-specific benefit",
                result.professionalCandidatesEligible,
                result.professionalCandidatesAttempted);
    }

    private static void addNextGenerationCandidates(
            AudioAnalyzer.StreamProvider source,
            AnalysisResult sourceAnalysis,
            Result result,
            File workDirectory,
            AudioAnalyzer.Progress progress) {
        NextGenerationMasteringModel.Result model =
                sourceAnalysis.nextGenerationIntelligence;
        Candidate transparent = result.transparent;
        if (model == null || transparent == null || transparent.plan == null
                || transparent.plan.hasAudioChanges()) {
            result.nextGenerationDecision =
                    "Source-aware analysis or transparent baseline was unavailable";
            return;
        }
        ArrayList<NextGenerationMasteringRenderer.Mode> modes =
                new ArrayList<>();
        Candidate retainedFamilyFinalist = null;
        // Translation profiles remain twelve independent QC listeners. They
        // intentionally do not generate or promote tonal processing in v0.9.1.
        if (model.tones != null && model.tones.automaticCount() > 0) {
            modes.add(NextGenerationMasteringRenderer.Mode
                    .PERSISTENT_TONE_REPAIR);
        }
        if (model.channel != null && model.channel.automaticCount() > 0) {
            modes.add(NextGenerationMasteringRenderer.Mode.CHANNEL_INTEGRITY);
        }
        // Reference matching and translation consensus never enter audio in v0.10.
        leanModes(result, modes);
        for (int modeIndex=0; modeIndex<modes.size(); modeIndex++) {
            NextGenerationMasteringRenderer.Mode mode=modes.get(modeIndex);
            Candidate candidate = new Candidate();
            candidate.strategy = mode.strategy;
            candidate.plan = transparent.plan;
            candidate.nextGenerationAware = true;
            candidate.nextGenerationMode = mode;
            candidate.renderAttempts = 1;
            candidate.file = new File(workDirectory,
                    "internal-v09-" + safeName(mode.strategy) + ".wav");
            result.candidates.add(candidate);
            result.nextGenerationCandidatesAttempted++;
            String label="Technical repair candidate "+(modeIndex+1)+"/"+modes.size()+" • "+mode.strategy;
            int localStart=segmentStart(modeIndex,modes.size(),0,100);
            int localEnd=segmentEnd(modeIndex,modes.size(),0,100);
            int localMid=localStart+Math.max(1,(int)Math.round((localEnd-localStart)*0.58));
            progress.update(label+" • preparing", localStart);
            try {
                candidate.nextGenerationIntelligence =
                        NextGenerationMasteringRenderer.render(source,
                                sourceAnalysis, transparent.plan, mode,
                                candidate.file, candidateWork(workDirectory, candidate), rangeProgress(progress,label+" • render",localStart,localMid));
                candidate.render = candidate.nextGenerationIntelligence.render;
                candidate.rendered = true;
                if(!promotionEvidenceWorthFullScan(candidate,candidate.nextGenerationIntelligence.measuredTargetBenefit,0.10,
                        "technical-repair targeted benefit"))continue;
                candidate.measuredFeatures =
                        AutonomousMasteringBrain.measureFeatures(
                                () -> new FileInputStream(candidate.file),
                                candidate.render.master.verification,
                                rangeProgress(progress,label+" • verification",localMid,localEnd));
                candidate.measuredObjective =
                        AutonomousMasteringBrain.objective(
                                candidate.measuredFeatures,
                                candidate.render.master.verification);
                candidate.rendered = true;
                candidate.nextGenerationEvidenceBonus = 0.085
                        * candidate.nextGenerationIntelligence.evidenceBenefit
                        + 0.060 * candidate.nextGenerationIntelligence
                        .measuredTargetBenefit
                        + 0.020 * candidate.nextGenerationIntelligence
                        .modelConfidence;
                evaluate(candidate, sourceAnalysis, result);
                if (candidate.eligible) {
                    result.nextGenerationCandidatesEligible++;
                }
                retainedFamilyFinalist = retainFamilyFinalistAudio(
                        retainedFamilyFinalist, candidate, true);
            } catch (Exception error) {
                candidate.status = "Source-aware render rejected: "
                        + clean(error);
                candidate.rejectionReasons.add(candidate.status);
                candidate.eligible = false;
                recordIntegrityFailure(result, candidate, error);
                if (candidate.file.exists() && !candidate.file.delete()) {
                    candidate.file.deleteOnExit();
                }
            }
        }
        result.nextGenerationDecision = String.format(Locale.US,
                "%d/%d narrow source-aware technical candidate(s) survived hard render verification; translation consensus remained diagnostic-only",
                result.nextGenerationCandidatesEligible,
                result.nextGenerationCandidatesAttempted);
    }

    private static boolean retainsTargetedImprovement(Candidate proposed,Candidate reference,AnalysisResult source)throws IOException {
        if(reference.qualityEvidence==null||!reference.qualityEvidence.passed())return true;
        try{
            TargetedQualityEvidence.Result e=TargetedQualityEvidence.compare(reference.file,reference.render.master.verification,
                    proposed.file,proposed.render.master.verification,source.professionalIntelligence);
            return e.error==null&&e.events>0&&e.worsenedEvents==0&&e.meanTargetReductionDb>=-.03;
        }catch(IOException error){if(Thread.currentThread().isInterrupted())throw error;return false;}
    }

    static double qualityPromotionValue(Candidate candidate,Candidate reference,AnalysisResult source) throws IOException {
        candidate.qualityDecision="Preservation or measurable benefit did not pass";
        if(!qualityPreservationPassed(candidate,reference))return Double.NEGATIVE_INFINITY;
        if(ProfessionalMasteringRenderer.isVocal(candidate.professionalMode)){
            try{
                candidate.qualityEvidence=TargetedQualityEvidence.compare(reference.file,reference.render.master.verification,
                        candidate.file,candidate.render.master.verification,source.professionalIntelligence);
            }catch(IOException error){
                if(Thread.currentThread().isInterrupted())throw error;
                candidate.qualityEvidence=new TargetedQualityEvidence.Result();candidate.qualityEvidence.error=clean(error);
            }
            candidate.qualityDecision=candidate.qualityEvidence.summary();
            if(!candidate.qualityEvidence.passed())return Double.NEGATIVE_INFINITY;
            return Math.min(.75,candidate.qualityEvidence.meanTargetReductionDb)
                    -.05*candidate.processingCost;
        }
        if(candidate.professionalMode==ProfessionalMasteringRenderer.Mode.LIMITER_CREST){
            double improvement=LimiterCrestOptimizer.qualityCost(reference.render.master,source)
                    -LimiterCrestOptimizer.qualityCost(candidate.render.master,source);
            candidate.qualityDecision=String.format(Locale.US,"Limiter stress cost improved %.4f; gain-step %.4f to %.4f dB/sample; preservation passed (engineering proxies, not a listening verdict)",
                    improvement,reference.render.master.maximumLimiterGainStepDb,candidate.render.master.maximumLimiterGainStepDb);
            if(Double.isFinite(improvement)&&improvement>=.05)return Math.min(.70,improvement);
        }
        return Double.NEGATIVE_INFINITY;
    }

    static boolean qualityPreservationPassed(Candidate c,Candidate reference){
        if(c==null||reference==null||!c.eligible||c.render==null||reference.render==null
                ||c.passageCheck==null||!c.passageCheck.passed())return false;
        MasterRenderer.Result a=reference.render.master,b=c.render.master;
        AnalysisResult x=a.verification,y=b.verification;
        return Double.isFinite(y.lraLu)&&Double.isFinite(y.crestFactorDb)
                &&x.lraLu-y.lraLu<=.55 && x.crestFactorDb-y.crestFactorDb<=.80
                &&b.maximumLimiterReductionDb<=a.maximumLimiterReductionDb+.25
                &&b.averageLimiterReductionDb<=a.averageLimiterReductionDb+.06
                &&Math.abs(y.integratedLufs+14)<=Math.max(.10,Math.abs(x.integratedLufs+14)+.02);
    }

    private static boolean professionalModuleBenefitPassed(
            Candidate candidate) {
        ProfessionalMasteringRenderer.Result professional =
                candidate.professionalIntelligence;
        if (professional == null || candidate.professionalMode == null) {
            return false;
        }
        if (candidate.professionalMode
                == ProfessionalMasteringRenderer.Mode
                .STEREO_DEPTH_REVERB) {
            return professional.depthSideReductionDb >= 0.05;
        }
        if (candidate.professionalMode
                == ProfessionalMasteringRenderer.Mode.LIMITER_CREST) {
            return professional.limiterOptimisation != null
                    && professional.limiterOptimisation
                    .alternativeProfileSelected
                    && professional.limiterOptimisation
                    .measuredTargetBenefit >= 0.08;
        }
        if (candidate.professionalMode
                == ProfessionalMasteringRenderer.Mode
                .LOW_FREQUENCY_PHASE_RESCUE) {
            return professional.lowPhaseImprovement >= 0.015;
        }
        if (candidate.professionalMode
                == ProfessionalMasteringRenderer.Mode.VOCAL_ARTIFACT) {
            return professional.vocalArtifactReductionDb >= 0.05;
        }
        int cleared = 0;
        if (professional.depthSideReductionDb >= 0.05) cleared++;
        if (professional.lowPhaseImprovement >= 0.015) cleared++;
        if (professional.vocalArtifactReductionDb >= 0.05) cleared++;
        if (professional.limiterOptimisation != null
                && professional.limiterOptimisation
                .alternativeProfileSelected
                && professional.limiterOptimisation
                .measuredTargetBenefit >= 0.08) {
            cleared++;
        }
        return cleared >= 2;
    }

    private static double professionalRequiredMargin(Candidate candidate) {
        if (candidate == null || candidate.professionalMode == null) return 0.12;
        switch (candidate.professionalMode) {
            case LIMITER_CREST:
                return 0.075;
            case LOW_FREQUENCY_PHASE_RESCUE:
                return 0.085;
            case PROFESSIONAL_COMBINED:
                return 0.135;
            default:
                return 0.100;
        }
    }

    /**
     * Specialist processing must retain the musical movement and transient life
     * of the established reference. Technical polarity/delay and narrow-tone
     * repairs remain possible, but profile bonuses cannot satisfy this gate.
     */
    private static boolean naturalPreservationPassed(Candidate candidate,
                                                      Candidate reference) {
        if (candidate == null || reference == null || candidate.render == null
                || reference.render == null) return false;
        AnalysisResult out = candidate.render.master.verification;
        AnalysisResult baseline = reference.render.master.verification;
        if (baseline.lraLu - out.lraLu > 0.60) return false;
        if (baseline.crestFactorDb - out.crestFactorDb > 0.85) return false;
        if (candidate.render.master.maximumLimiterReductionDb
                > reference.render.master.maximumLimiterReductionDb + 0.25) {
            return false;
        }
        if (candidate.professionalAware
                && candidate.professionalIntelligence != null) {
            ProfessionalMasteringRenderer.Result professional =
                    candidate.professionalIntelligence;
            if (professional.maximumNominalEditDb > 1.50) return false;
            if (professional.delta != null
                    && professional.delta.sourceCorrelation < 0.9965) {
                return false;
            }
        }
        if (candidate.nextGenerationAware
                && candidate.nextGenerationIntelligence != null) {
            boolean technicalChannel = candidate.nextGenerationMode
                    == NextGenerationMasteringRenderer.Mode.CHANNEL_INTEGRITY;
            boolean narrowTone = candidate.nextGenerationMode
                    == NextGenerationMasteringRenderer.Mode.PERSISTENT_TONE_REPAIR;
            if (!technicalChannel && !narrowTone
                    && candidate.nextGenerationIntelligence.maximumNominalEditDb
                    > 1.40) return false;
            if (!technicalChannel
                    && candidate.nextGenerationIntelligence.delta != null
                    && candidate.nextGenerationIntelligence.delta.sourceCorrelation
                    < 0.9965) return false;
        }
        return true;
    }

    private static boolean advancedModuleBenefitPassed(Candidate candidate) {
        AdvancedStereoRenderer.Result advanced = candidate.advancedIntelligence;
        if (advanced == null || candidate.advancedMode == null) return false;
        if (candidate.advancedMode == AdvancedStereoRenderer.Mode.SECTION_AWARE) {
            return advanced.sectionLiftImprovementLu >= 0.08;
        }
        if (candidate.advancedMode == AdvancedStereoRenderer.Mode.ARTIFACT_REPAIR) {
            return advanced.artifactReductionDb >= 0.05;
        }
        if (candidate.advancedMode
                == AdvancedStereoRenderer.Mode.TRANSIENT_INTELLIGENCE) {
            return advanced.transientPeakReductionDb >= 0.10;
        }
        int cleared = 0;
        if (advanced.sectionLiftImprovementLu >= 0.08) cleared++;
        if (advanced.artifactReductionDb >= 0.05) cleared++;
        if (advanced.transientPeakReductionDb >= 0.10) cleared++;
        return cleared >= 2;
    }

    private static void evaluate(Candidate candidate,
                                 AnalysisResult source,
                                 Result result) {
        evaluate(candidate, source, result, null);
    }

    private static void evaluate(Candidate candidate,
                                 AnalysisResult source,
                                 Result result,
                                 AdaptiveDynamicsPreservation.Envelope dynamicsEnvelope) {
        AnalysisResult out = candidate.render.master.verification;
        if (out.wav.sampleRate != source.wav.sampleRate
                || out.wav.channels != 2
                || out.wav.bitsPerSample != 24
                || out.wav.frames != source.wav.frames) {
            reject(candidate, "format, sample count or duration changed");
        }
        if (out.truePeakEstimateDbtp > -0.999) {
            reject(candidate, "verified true peak exceeded -1.00 dBTP");
        }
        if (!candidate.render.master.exactTargetReached
                && !candidate.render.master.dynamicsProtected
                && !MasterRenderer.isCompliantSourcePreserved(source, out,
                candidate.render.master)) {
            reject(candidate, "verified loudness did not reach -14.00 LUFS-I and no verified source-preservation or dynamics exception applies");
        }
        if (out.clippedSamples != 0 || out.nonFiniteSamples != 0) {
            reject(candidate, "clipped or non-finite samples were introduced");
        }
        if (out.internalZeroRuns > source.internalZeroRuns) {
            reject(candidate, "an exact-zero interruption was introduced");
        }
        if (out.stereoCorrelation < source.stereoCorrelation - 0.035) {
            reject(candidate, "global stereo correlation worsened beyond the guardrail");
        }
        if (out.lowBandCorrelation < source.lowBandCorrelation - 0.025) {
            reject(candidate, "below-160 Hz correlation worsened beyond the guardrail");
        }
        double lraLoss = source.lraLu - out.lraLu;
        if (lraLoss > 1.80) {
            reject(candidate, "LRA loss exceeded 1.80 LU");
        }
        if (candidate.rejectionReasons.isEmpty()) {
            try {
                PassagePreservation.Scan scan = PassagePreservation.scan(candidate.file, out);
                if (candidate == result.transparent) result.passageBaseline = scan;
                candidate.passageCheck = candidate.sparsePeakAware
                        && SparsePeakRecovery.stableOutput(source, result.transparent.render.master.verification, candidate.render.master)
                        ? PassagePreservation.compareSparsePeak(result.passageBaseline, scan)
                        : dynamicsEnvelope != null && dynamicsEnvelope.active
                        ? PassagePreservation.compareDynamicsLed(result.passageBaseline, scan,
                                dynamicsEnvelope.passageTransientFloorDb)
                        : PassagePreservation.compare(result.passageBaseline, scan);
                if (!candidate.passageCheck.passed()) reject(candidate, candidate.passageCheck.summary());
            } catch (IOException error) {
                candidate.passageCheck = new PassagePreservation.Result();
                candidate.passageCheck.error = clean(error);
                reject(candidate, candidate.passageCheck.summary());
            }
        }
        if (!candidate.rejectionReasons.isEmpty()) {
            candidate.status = "hard safety rejection";
            candidate.eligible = false;
            return;
        }

        double correlationLoss = Math.max(0.0,
                source.stereoCorrelation - out.stereoCorrelation - 0.006);
        double lowCorrelationLoss = Math.max(0.0,
                source.lowBandCorrelation - out.lowBandCorrelation - 0.004);
        double monoLossWorsening = Math.max(0.0,
                source.monoFoldLossDb - out.monoFoldLossDb - 0.10);
        double eqMagnitude = Math.abs(candidate.plan.lowShelfDb)
                + Math.abs(candidate.plan.lowMidDb)
                + Math.abs(candidate.plan.presenceDb)
                + Math.abs(candidate.plan.highShelfDb);
        candidate.processingCost = 0.025 * eqMagnitude
                + 0.85 * Math.max(0.0, 1.0 - candidate.plan.sideScale)
                + 0.030 * candidate.render.maximumLocalCutDb
                + 0.18 * candidate.render.maximumCompressionReductionDb
                + limiterActivityCost(candidate.render.master);
        if (candidate.stemAware && candidate.stemAssist != null) {
            candidate.processingCost += 0.03
                    * candidate.stemAssist.maximumNominalEditDb
                    + 0.005 * Math.min(12, candidate.stemAssist.correctionEvents);
            if (candidate.stemAssist.analysisOnlyControl) {
                candidate.processingCost += 0.015;
            }
        }
        if (candidate.intelligenceAware && candidate.stereoIntelligence != null) {
            candidate.processingCost += 0.035
                    * candidate.stereoIntelligence.maximumNominalEditDb
                    + 0.004 * Math.min(16,
                    candidate.stereoIntelligence.correctionEvents)
                    + (candidate.intelligenceMode
                    == StereoIntelligenceRenderer.Mode.COMBINED ? 0.010 : 0.0);
        }
        if (candidate.advancedAware && candidate.advancedIntelligence != null) {
            candidate.processingCost += 0.035
                    * candidate.advancedIntelligence.maximumNominalEditDb
                    + 0.004 * Math.min(18,
                    candidate.advancedIntelligence.correctionEvents)
                    + (candidate.advancedMode
                    == AdvancedStereoRenderer.Mode.ADVANCED_COMBINED
                    ? 0.010 : 0.0);
        }
        if (candidate.professionalAware
                && candidate.professionalIntelligence != null) {
            candidate.processingCost += 0.035
                    * candidate.professionalIntelligence
                    .maximumNominalEditDb
                    + 0.004 * Math.min(18,
                    candidate.professionalIntelligence.correctionEvents)
                    + (candidate.professionalMode
                    == ProfessionalMasteringRenderer.Mode
                    .PROFESSIONAL_COMBINED ? 0.010 : 0.0);
            if (candidate.professionalIntelligence
                    .limiterOptimisation != null
                    && candidate.professionalIntelligence
                    .limiterOptimisation.alternativeProfileSelected) {
                candidate.processingCost += 0.004;
            }
        }
        if (candidate.nextGenerationAware
                && candidate.nextGenerationIntelligence != null) {
            candidate.processingCost += 0.030
                    * candidate.nextGenerationIntelligence
                    .maximumNominalEditDb
                    + 0.0035 * Math.min(20,
                    candidate.nextGenerationIntelligence.correctionEvents)
                    + (candidate.nextGenerationMode
                    == NextGenerationMasteringRenderer.Mode.V09_COMBINED
                    ? 0.012 : 0.0);
        }
        double objectiveGain = candidate.measuredObjective - result.sourceObjective;
        double evidenceBonus = Math.max(0.0, objectiveGain)
                * 0.30 * candidate.plan.confidence;
        candidate.technicalScore = candidate.measuredObjective
                + evidenceBonus
                + candidate.intelligenceEvidenceBonus
                + candidate.advancedEvidenceBonus
                + candidate.professionalEvidenceBonus
                + candidate.nextGenerationEvidenceBonus
                + candidate.stemEvidenceBonus
                - candidate.processingCost
                - 0.80 * Math.max(0.0, lraLoss - 0.45)
                - 10.0 * correlationLoss
                - 12.0 * lowCorrelationLoss
                - 2.0 * monoLossWorsening;
        candidate.preferenceBonus = result.preferenceProfile == null ? 0.0
                : result.preferenceProfile.bonusFor(candidate.strategy);
        if (result.albumProfile != null && result.albumProfile.usable()) {
            candidate.albumDistance = result.albumProfile.distance(
                    candidate.measuredFeatures, out);
            candidate.albumBonus = result.albumProfile.bonus(
                    candidate.measuredFeatures, out);
        }
        candidate.score = candidate.technicalScore
                + candidate.preferenceBonus + candidate.albumBonus;
        if (!candidate.render.master.exactTargetReached) {
            candidate.score -= loudnessShortfallPenalty(out.integratedLufs);
        }
        candidate.eligible = true;
        candidate.status = "eligible after measured render verification";
    }

    static double loudnessShortfallPenalty(double lufs) {
        return Math.min(4.0, Math.abs(-14.0 - lufs) * 0.65);
    }

    /** One bounded challenger; technical repair maps and limiter safety are never weakened. */
    private static void tryGentlerChallenger(AudioAnalyzer.StreamProvider source,
            AnalysisResult analysis, Result result, File directory, AudioAnalyzer.Progress progress)
            throws IOException {
        Candidate parent = result.selected;
        if (parent == result.transparent || parent.plan == null || !parent.plan.hasAudioChanges()
                || parent.intelligenceAware || parent.advancedAware || parent.nextGenerationAware
                || parent.professionalAware || parent.stemAware || analysis.sourceGuard.secondPassGuard) {
            result.gentlerDecision = "Skipped: selected result is transparent, protected, or a targeted repair requiring its full verified map.";
            return;
        }
        Candidate c = new Candidate(); c.gentlerChallenger=true;
        c.strategy = parent.strategy + "_gentler"; c.plan = gentlerPlan(parent.plan, c.strategy);
        c.file = new File(directory, "gentler-final-challenger.wav");
        result.gentlerParent=parent;result.gentlerCandidate=c;result.candidates.add(c);
        progress.update("Full AI: testing one gentler version of the selected correction",0);
        try {
            c.render=renderCandidate(source,analysis,c.plan,c.file,candidateWork(directory,c),progress);
            c.rendered=true;c.renderAttempts=1;
            c.measuredFeatures=AutonomousMasteringBrain.measureFeatures(()->new FileInputStream(c.file),c.render.master.verification,progress);
            c.measuredObjective=AutonomousMasteringBrain.objective(c.measuredFeatures,c.render.master.verification);
            evaluate(c,analysis,result);
            if (gentlerEquivalent(parent,c)) {
                parent.selectedAfterPromotionGates=false;c.selectedAfterPromotionGates=true;
                result.selected=c;
                result.gentlerDecision="Gentler version selected only because it matched the winner within 0.003 measured score/objective points, lowered processing cost, did not increase limiter burden and passed passage preservation.";
                result.selectionReason += " " + result.gentlerDecision;
            } else {
                c.promotionBlocked=true;
                result.gentlerDecision="Original winner retained: the gentler challenger did not meet every benefit, cost, limiter and passage gate.";
            }
            if (c.eligible) {
                result.rankedEligible.add(c);
                Collections.sort(result.rankedEligible,(a,b)->Double.compare(b.score,a.score));
                for(int i=0;i<result.rankedEligible.size();i++)result.rankedEligible.get(i).rank=i+1;
            } else WavRenderIntegrity.discard(c.file);
        } catch (IOException error) {
            if(Thread.currentThread().isInterrupted())throw error;
            c.eligible=false;c.status="Gentler challenger unavailable: "+clean(error);
            c.rejectionReasons.add(c.status);WavRenderIntegrity.discard(c.file);
            result.gentlerDecision=c.status+"; verified original winner retained.";
        }
    }

    static AutonomousMasteringBrain.Plan gentlerPlan(AutonomousMasteringBrain.Plan parent,String name) {
        AutonomousMasteringBrain.Plan p=parent.copy(name);
        p.lowShelfDb*=.75;p.lowMidDb*=.75;p.presenceDb*=.75;p.highShelfDb*=.75;
        p.sideScale=1+(p.sideScale-1)*.75;p.eventScale*=.75;
        p.compressorRatio=1+(p.compressorRatio-1)*.75;p.compressorMaximumReductionDb*=.75;
        p.decisions.add("Final challenger reduced optional correction amounts to 75%; release limiter and technical repairs retain their full safety policy.");
        return p;
    }

    static boolean gentlerEquivalent(Candidate parent,Candidate c) {
        if(!c.eligible || c.passageCheck==null || !c.passageCheck.passed()
                || !naturalPreservationPassed(c,parent)
                || !Double.isFinite(c.measuredObjective) || !Double.isFinite(c.technicalScore)
                || !Double.isFinite(c.processingCost)
                || c.measuredObjective < parent.measuredObjective-0.003
                || c.technicalScore < parent.technicalScore-0.003
                || c.score < parent.score-0.003
                || c.processingCost >= parent.processingCost-1e-6) return false;
        MasterRenderer.Result a=parent.render.master,b=c.render.master;
        return b.maximumLimiterReductionDb<=a.maximumLimiterReductionDb+.02
                && b.averageLimiterReductionDb<=a.averageLimiterReductionDb+.005
                && b.limiterFramesAboveOneDbPercent<=a.limiterFramesAboveOneDbPercent+.05
                && b.limiterFramesAboveThreeDbPercent<=a.limiterFramesAboveThreeDbPercent+.05
                && Math.abs(b.verification.integratedLufs+14)<=Math.abs(a.verification.integratedLufs+14)+.02;
    }

    private static File candidateWork(File root, Candidate candidate) throws IOException {
        File directory = new File(root, "candidate-work-" + safeName(candidate.strategy));
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Could not create isolated candidate staging directory");
        }
        return directory;
    }

    static double limiterActivityCost(MasterRenderer.Result master) {
        return 0.20 * master.maximumLimiterReductionDb
                + 0.90 * master.averageLimiterReductionDb
                + 0.02 * master.limiterFramesAboveOneDbPercent;
    }

    private static AutonomousMasteringRenderer.Result renderCandidate(
            AudioAnalyzer.StreamProvider source,
            AnalysisResult sourceAnalysis,
            AutonomousMasteringBrain.Plan plan,
            File output,
            File workDirectory,
            AudioAnalyzer.Progress progress) throws IOException {
        boolean continuityPreprocessed = sourceAnalysis.clickDropoutRepair != null
                && sourceAnalysis.clickDropoutRepair.repairApplied;
        if (!plan.hasAudioChanges() && sourceAnalysis.noRenderNeeded
                && !continuityPreprocessed) {
            progress.update("Full AI: preserving already-compliant transparent source", 0);
            try (InputStream input = source.open();
                 OutputStream destination = new FileOutputStream(output)) {
                byte[] buffer = new byte[256 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) destination.write(buffer, 0, read);
                    if (Thread.currentThread().isInterrupted()) {
                        throw new IOException("Transparent source preservation cancelled");
                    }
                }
            }
            AnalysisResult verification = AudioAnalyzer.analyze(output.getName(),
                    () -> new FileInputStream(output), progress);
            if (!sourceAnalysis.sourceSha256.equalsIgnoreCase(verification.sourceSha256)
                    || verification.wav.frames != sourceAnalysis.wav.frames) {
                throw new IOException("Transparent no-change copy failed hash or duration verification");
            }
            MasterRenderer.Result master = new MasterRenderer.Result();
            master.file = output;
            master.savedFileName = output.getName();
            master.verification = verification;
            master.gainDb = 0.0;
            master.exactTargetReached = Math.abs(verification.integratedLufs + 14.0) <= 0.02;
            master.compliantSourcePreserved = sourceAnalysis.alreadyCompliantNoRender
                    && AudioAnalyzer.isCompliantReleaseMaster(verification);
            master.dynamicsProtected = sourceAnalysis.ceilingLimited;
            master.targetShortfallLu = -14.0 - verification.integratedLufs;
            master.renderPasses = 0;
            master.aiGuided = true;
            master.aiCorrectionSummary = "transparent no-change source copy";

            AutonomousMasteringRenderer.Result result =
                    new AutonomousMasteringRenderer.Result();
            result.plan = plan;
            result.master = master;
            result.correctedPremaster = sourceAnalysis;
            result.guardrailVerdict = "NO-CHANGE PASS: original compliant bytes, sample count and SHA-256 preserved";
            return result;
        }
        return AutonomousMasteringRenderer.render(source, sourceAnalysis, plan,
                output, workDirectory, progress);
    }

    private static IOException baselineFailure(Candidate baseline, String stage) {
        String reason;
        if (baseline == null) {
            reason = "the transparent plan was missing";
        } else if (!baseline.rejectionReasons.isEmpty()) {
            reason = String.join("; ", baseline.rejectionReasons);
        } else if (baseline.file == null || !baseline.file.isFile()) {
            reason = "the transparent output file is missing";
        } else {
            reason = baseline.status == null ? "verification did not complete" : baseline.status;
        }
        return new IOException("No independently verified transparent baseline survived ("
                + stage + "): " + reason + "; refusing to select a master");
    }

    private static void reject(Candidate candidate, String reason) {
        candidate.rejectionReasons.add(reason);
    }

    private static double phaseGain(AnalysisResult source, AnalysisResult output) {
        return Math.max(0.0, output.stereoCorrelation - source.stereoCorrelation)
                + Math.max(0.0, output.lowBandCorrelation - source.lowBandCorrelation);
    }

    private static String fingerprint(AutonomousMasteringBrain.Plan p) {
        return String.format(Locale.US,
                "%.5f|%.5f|%.5f|%.5f|%.5f|%.5f|%s|%.5f|%.5f|%.5f|%.5f|%.5f",
                p.lowShelfDb, p.lowMidDb, p.presenceDb, p.highShelfDb,
                p.sideScale, p.eventScale, p.compressorEnabled,
                p.compressorThresholdDbfs, p.compressorRatio,
                p.compressorAttackMs, p.compressorReleaseMs,
                p.compressorMaximumReductionDb);
    }

    private static String clean(Exception error) {
        String value = error.getMessage();
        return value == null || value.trim().isEmpty()
                ? error.getClass().getSimpleName() : value;
    }

    private static String safeName(String value) {
        return value == null ? "stem" : value.toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    private static boolean retriableIo(Exception error) {
        String message = clean(error).toLowerCase(Locale.US);
        return message.contains("unexpected end of file")
                || message.contains("temporarily")
                || message.contains("short read")
                || message.contains("durable-length")
                || message.contains("length mismatch")
                || message.contains("frame accounting")
                || message.contains("atomically publish");
    }

    private static void recordIntegrityFailure(Result result,
                                               Candidate candidate,
                                               Exception error) {
        if (result == null || candidate == null || !retriableIo(error)) return;
        result.renderIntegrityFailures.add(candidate.strategy + ": " + clean(error));
    }

    private static void discardPromotionBlockedAudio(Result result) {
        if (result == null) return;
        for (Candidate candidate : result.candidates) {
            if (!candidate.promotionBlocked || candidate.file == null
                    || !candidate.file.exists() || candidate == result.transparent) continue;
            if (!candidate.file.delete()) candidate.file.deleteOnExit();
        }
    }

    /**
     * Keeps the transparent baseline and the one core candidate that can still
     * win the later measured competition. All measurements remain in the report, but
     * audio that is mathematically unable to reach codec fallback is released
     * before specialist families begin rendering.
     */
    static void retainCoreCompetitionAudio(Result result) {
        if (result == null) return;
        Candidate best = null;
        Candidate broad = null;
        Candidate transparent = result.transparent;
        boolean preserveBroad = result.broadMasteringNeed != null
                && result.broadMasteringNeed.required
                && transparent != null && transparent.file != null
                && transparent.file.isFile();
        for (Candidate candidate : result.candidates) {
            if (!candidate.rendered || !candidate.eligible
                    || candidate.intelligenceAware || candidate.advancedAware
                    || candidate.professionalAware
                    || candidate.nextGenerationAware || candidate.stemAware) {
                continue;
            }
            if (betterFamilyFinalist(candidate, best, false)) best = candidate;
            if (preserveBroad && candidate != transparent
                    && candidate.file != null && candidate.file.isFile()
                    && naturalPreservationPassed(candidate, transparent)
                    && candidate.technicalScore >= transparent.technicalScore - 0.10) {
                try {
                    MasteringDeltaEvidence.Result delta = MasteringDeltaEvidence.compare(
                            transparent.file, candidate.file);
                    candidate.masteringDelta = delta;
                    if (delta.broadMeaningful
                            && (broad == null || betterFamilyFinalist(candidate, broad, false))) {
                        broad = candidate;
                    }
                } catch (IOException error) {
                    candidate.rejectionReasons.add(
                            "Broad-anchor retention delta unavailable: " + clean(error));
                }
            }
        }
        if (broad != null) {
            result.broadMasteringAnchor = broad;
            result.broadMasteringAnchorDelta = broad.masteringDelta;
        }
        for (Candidate candidate : result.candidates) {
            if (!candidate.rendered || candidate.file == null
                    || candidate == best || candidate == result.transparent
                    || candidate == broad
                    || candidate.intelligenceAware || candidate.advancedAware
                    || candidate.professionalAware
                    || candidate.nextGenerationAware || candidate.stemAware) {
                continue;
            }
            discardCandidateAudio(candidate);
        }
    }

    /**
     * Candidate families are measured one mode at a time. Retaining only the
     * best still-promotable render prevents a burst of full-length WAV files
     * from exhausting the device cache and changing the candidate pool.
     */
    private static Candidate retainFamilyFinalistAudio(
            Candidate retained, Candidate current, boolean technicalRanking) {
        if (current == null || !current.rendered || !current.eligible) {
            discardCandidateAudio(current);
            return retained;
        }
        if (retained == null) return current;
        if (betterFamilyFinalist(current, retained, technicalRanking)) {
            discardCandidateAudio(retained);
            return current;
        }
        discardCandidateAudio(current);
        return retained;
    }

    private static boolean betterFamilyFinalist(
            Candidate candidate, Candidate retained, boolean technicalRanking) {
        if (candidate == null) return false;
        if (retained == null) return true;
        double candidateScore = technicalRanking
                ? candidate.technicalScore : candidate.score;
        double retainedScore = technicalRanking
                ? retained.technicalScore : retained.score;
        int comparison = Double.compare(candidateScore, retainedScore);
        return comparison > 0 || (comparison == 0
                && candidate.processingCost < retained.processingCost);
    }

    private static void discardCandidateAudio(Candidate candidate) {
        if (candidate == null || candidate.file == null
                || !candidate.file.exists()) return;
        if (!candidate.file.delete()) candidate.file.deleteOnExit();
    }

    private static void cleanupPremasters(File directory, String strategy) {
        File[] files = directory.listFiles();
        if (files == null) return;
        String marker = strategy.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", "-");
        for (File file : files) {
            if (file.getName().startsWith("ai-console-premaster-")
                    && file.getName().contains(marker)
                    && !file.delete()) {
                file.deleteOnExit();
            }
        }
    }
}
