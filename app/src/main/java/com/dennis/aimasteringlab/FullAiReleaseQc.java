package com.dennis.aimasteringlab;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Performs autonomous codec fallback, translation simulation and final-file QC. */
final class FullAiReleaseQc {
    static final class CodecAttempt {
        String strategy;
        File masterFile;
        AndroidCodecEncoder.Result aac;
        AndroidCodecDecoder.Result aacDecoded;
        SbcProxy.Result sbc;
        boolean pass;
        boolean safetyRerendered;
        double requestedPcmCeilingDbtp = Double.NaN;
        double codecSafetyAttenuationDb;
        String verdict;
    }

    static final class Result {
        String method = "activity-measured Spotify-target release limiting + actual AAC encode/decode + flat-gain codec headroom retry + 328 kbps SBC stress proxy + source-relative response-profile translation simulation";
        FullAiMasteringEngine.Candidate initialPcmWinner;
        FullAiMasteringEngine.Candidate finalCandidate;
        final List<CodecAttempt> codecAttempts = new ArrayList<>();
        boolean codecFallback;
        boolean codecSafetyRerendered;
        boolean codecSafetyAttenuated;
        File finalMaster;
        AnalysisResult finalMasterVerification;
        File finalAac;
        String finalMasterSha256;
        String finalAacSha256;
        AndroidCodecEncoder.Result aac;
        AndroidCodecDecoder.Result aacDecoded;
        SbcProxy.Result sbc;
        TranslationComposite.Result translationComposite;
        TranslationComposite.Result sourceTranslationComposite;
        TranslationAnalyzer.Result sourceTranslation;
        TranslationAnalyzer.Result translation;
        String technicalVerdict;
        String translationVerdict = "REVIEW";
        String loudnessVerdict = "REVIEW";
        ReleaseFormats.Result releaseFormats;
        RadioPreparation.Result radioPreparation;
        PassagePreservation.Result passageCheck;
        boolean continuityReviewRequired;
        boolean sourceLimitationReviewRequired;
        int unresolvedSourceZeroRegions;
        double targetShortfallLu;
        final List<String> warnings = new ArrayList<>();

        String combinedVerdict() {
            String out = technicalVerdict;
            if ("DYNAMIC_MASTER".equals(loudnessVerdict)) {
                out += " • DYNAMIC MASTER";
            }
            if ("CODEC_SAFE_MARGIN".equals(loudnessVerdict)) {
                out += " • CODEC-SAFE MARGIN";
            }
            if ("COMPLIANT_SOURCE_PRESERVED".equals(loudnessVerdict)) {
                out += " • COMPLIANT SOURCE PRESERVED";
            }
            if (!"PASS".equals(translationVerdict)
                    && !translationVerdict.startsWith("NOT_RUN")) out += " • TRANSLATION REVIEW";
            if (radioPreparation != null && !radioPreparation.issues.isEmpty()) out += " • RADIO PREPARATION REVIEW";
            if (releaseFormats != null && !releaseFormats.complete()) out += " • EXPORT REVIEW";
            return out;
        }

        String compactSummary() {
            String codec = aac != null && aac.available && aacDecoded != null
                    && aacDecoded.verification != null
                    ? String.format(Locale.US, "AAC %.3f LUFS-I / %.3f dBTP",
                    aacDecoded.verification.integratedLufs,
                    aacDecoded.verification.truePeakEstimateDbtp)
                    : "AAC unavailable";
            return String.format(Locale.US,
                    "%s • %s • %s • PCM %.4f LUFS-I / %.3f dBTP • %s",
                    combinedVerdict(), finalCandidate.strategy,
                    codecSafetyAttenuated ? "flat codec-safety attenuation"
                            : (codecSafetyRerendered ? "codec-safety rerender"
                            : "original winning render"),
                    finalMasterVerification.integratedLufs,
                    finalMasterVerification.truePeakEstimateDbtp, codec)
                    + (releaseFormats == null ? "" : "\n" + releaseFormats.summary());
        }
    }

    private FullAiReleaseQc() {}

    static Result run(AudioAnalyzer.StreamProvider source,
                      AnalysisResult sourceAnalysis,
                      FullAiMasteringEngine.Result engine,
                      File workDirectory,
                      String requestedBase,
                      AudioAnalyzer.Progress progress) throws Exception {
        return run(source, sourceAnalysis, engine, null, workDirectory,
                requestedBase, progress);
    }

    /**
     * Studio workspace PCM-only finalization. Studio v0.14.5 publishes only the
     * 24-bit/48 kHz master and the derived 16-bit/44.1 kHz WAV, so codec
     * fallback, AAC/SBC stress passes, translation composites and radio
     * simulation are intentionally not run here. Those remain available to the
     * Classic/AI Lab release-QC path.
     */
    static Result runStudioPcmOnly(AudioAnalyzer.StreamProvider source,
                                   AnalysisResult sourceAnalysis,
                                   FullAiMasteringEngine.Result engine,
                                   File workDirectory,
                                   String requestedBase,
                                   AudioAnalyzer.Progress progress) throws Exception {
        if (engine.selected == null) throw new IOException("No PCM winner was available for Studio QC");
        if (!workDirectory.exists() && !workDirectory.mkdirs()) {
            throw new IOException("Could not create the Studio PCM QC cache");
        }
        Result result = new Result();
        result.method = "Studio PCM-only winner verification; codec/translation simulations intentionally skipped because Studio publishes WAV delivery only";
        FullAiMasteringEngine.Candidate chosen = engine.selected;
        result.initialPcmWinner = chosen;
        result.finalCandidate = chosen;
        for (FullAiMasteringEngine.Candidate candidate : engine.candidates) {
            candidate.selectedAfterPromotionGates = candidate == chosen;
        }

        progress.update("Studio PCM QC • copying selected master", 6);
        result.finalMaster = new File(workDirectory,
                ReleaseFormats.camelBase(requestedBase) + "_Master_24bit-48kHz.wav");
        copy(chosen.file, result.finalMaster);
        progress.update("Studio PCM QC • measuring selected master", 22);
        result.finalMasterVerification = AudioAnalyzer.analyze(
                result.finalMaster.getName(), () -> new FileInputStream(result.finalMaster),
                rangeProgress(progress, "Studio PCM QC • measuring selected master", 22, 64));
        verifyFinalCopy(chosen.render.master.verification, result.finalMasterVerification);

        progress.update("Studio PCM QC • checking passage preservation", 66);
        PassagePreservation.Scan finalPassages = PassagePreservation.scan(
                result.finalMaster, result.finalMasterVerification);
        // Re-check the distinct, explicitly qualified sparse-peak passage policy
        // at the authoritative final-copy boundary. All other winners retain
        // the unmodified strict passage policy.
        boolean verifiedSparsePeak = chosen.sparsePeakAware
                && engine.transparent != null && engine.transparent.render != null
                && SparsePeakRecovery.stableOutput(sourceAnalysis,
                        engine.transparent.render.master.verification, chosen.render.master);
        result.passageCheck = verifiedSparsePeak
                ? PassagePreservation.compareSparsePeak(engine.passageBaseline, finalPassages)
                : PassagePreservation.compare(engine.passageBaseline, finalPassages);
        if (!result.passageCheck.passed()) throw new IOException(result.passageCheck.summary());
        result.finalMasterSha256 = Hashing.sha256(result.finalMaster);

        result.unresolvedSourceZeroRegions = sourceAnalysis.internalZeroRegions.size();
        result.continuityReviewRequired = result.unresolvedSourceZeroRegions > 0;
        if (result.continuityReviewRequired) {
            result.warnings.add(unresolvedContinuityWarning(sourceAnalysis));
        }
        if (sourceAnalysis.sourceGuard != null) {
            SourceGuardModel.Result guard = sourceAnalysis.sourceGuard;
            result.sourceLimitationReviewRequired = guard.likelyLossyInWav
                    || guard.artificial24From16;
            result.warnings.addAll(guard.warnings);
            if (guard.likelyPriorDennisMaster) {
                result.warnings.add("Previous-master evidence was detected; broad second-pass processing and limiter escalation were blocked.");
            }
        }
        AnalysisResult original = sourceAnalysis.originalSourceAnalysis == null
                ? sourceAnalysis : sourceAnalysis.originalSourceAnalysis;
        if (original.clippedSamples > 0) {
            long repaired = sourceAnalysis.clickDropoutRepair != null
                    && sourceAnalysis.clickDropoutRepair.repairApplied
                    ? sourceAnalysis.clickDropoutRepair.repairedClippedSamples : 0;
            result.warnings.add("Original source had " + original.clippedSamples
                    + " full-scale sample(s); bounded continuity repair changed " + repaired
                    + ". Remaining flattened peaks require listening review.");
        }

        boolean pcmPass = safeRepaired(sourceAnalysis, result.finalMasterVerification,
                chosen.render.master);
        result.targetShortfallLu = Math.max(0.0,
                -14.0 - result.finalMasterVerification.integratedLufs);
        boolean exactTarget = chosen.render.master.exactTargetReached;
        boolean compliantSourcePreserved = MasterRenderer.isCompliantSourcePreserved(
                sourceAnalysis, result.finalMasterVerification, chosen.render.master);
        result.loudnessVerdict = compliantSourcePreserved ? "COMPLIANT_SOURCE_PRESERVED"
                : exactTarget ? "TARGET_REACHED"
                : chosen.render.master.dynamicsProtected ? "DYNAMIC_MASTER" : "REVIEW";
        result.translationVerdict = "NOT_RUN_STUDIO_PCM_ONLY";
        result.technicalVerdict = pcmPass ? "PASS" : "CONDITIONAL";
        if (result.continuityReviewRequired || result.sourceLimitationReviewRequired) {
            result.technicalVerdict = "CONDITIONAL";
        }
        if (engine.transparent != null && engine.transparent.render != null
                && engine.transparent.render.master.peakSafetyRecoveryPasses > 0) {
            MasterRenderer.Result baseline = engine.transparent.render.master;
            result.warnings.add(String.format(Locale.US,
                    "Transparent baseline initially exceeded true-peak ceiling; %d attenuation-only retry/retries were independently verified (%.3f dB below requested gain). Any -14 LUFS shortfall is retained, not concealed.",
                    baseline.peakSafetyRecoveryPasses,
                    baseline.peakSafetyRecoveryAttenuationDb));
        }
        result.warnings.add("Studio PCM-only QC skipped AAC/SBC, translation-device and broadcaster simulation passes; the two final WAV files are verified separately during Studio delivery.");
        for (String failure : engine.renderIntegrityFailures) {
            result.warnings.add("Optional candidate rejected; verified fallback retained: " + failure);
        }
        progress.update("Studio PCM QC • selected master verified", 100);
        return result;
    }

    static Result run(AudioAnalyzer.StreamProvider source,
                      AnalysisResult sourceAnalysis,
                      FullAiMasteringEngine.Result engine,
                      File stemArchive,
                      File workDirectory,
                      String requestedBase,
                      AudioAnalyzer.Progress progress) throws Exception {
        if (engine.selected == null) throw new IOException("No PCM winner was available for release QC");
        if (!workDirectory.exists() && !workDirectory.mkdirs()) {
            throw new IOException("Could not create the Full AI QC cache");
        }
        Result result = new Result();
        result.initialPcmWinner = engine.selected;

        FullAiMasteringEngine.Candidate chosen = null;
        CodecAttempt chosenAttempt = null;
        for (FullAiMasteringEngine.Candidate candidate : codecOrder(engine)) {
            // Module-specific promotion gates are part of the authoritative
            // decision, not merely the PCM ranking. A candidate that did not
            // clear its promotion gate must never re-enter as a codec fallback.
            if (Thread.currentThread().isInterrupted()) throw new IOException("Mastering cancelled");
            if (candidate.promotionBlocked) continue;
            CodecAttempt attempt = testCodecs(candidate.strategy, candidate.file,
                    workDirectory, progress);
            if (Thread.currentThread().isInterrupted()) throw new IOException("Mastering cancelled");
            result.codecAttempts.add(attempt);
            if (!attempt.pass && canRepairOvershoot(attempt)) {
                double attenuationDb = requiredCodecSafetyAttenuationDb(attempt);
                File repaired = new File(workDirectory,
                        "codec-safe-" + safe(candidate.strategy) + ".wav");
                try {
                    MasterRenderer.Result repairedMaster =
                            MasterRenderer.renderCodecSafetyAttenuation(
                                    () -> new FileInputStream(candidate.file),
                                    candidate.render.master.verification,
                                    candidate.render.master, attenuationDb,
                                    repaired, progress);
                    AutonomousMasteringRenderer.Result repairedRender =
                            copyWithMaster(candidate.render, repairedMaster,
                                    attenuationDb);
                    if (!safeRepaired(sourceAnalysis, repairedRender.master.verification,
                            repairedRender.master)) {
                        throw new IOException("codec-safety rerender failed PCM safety verification");
                    }
                    deleteCodecTemps(attempt);
                    attempt = testCodecs(candidate.strategy, repaired,
                            workDirectory, progress);
                    attempt.safetyRerendered = true;
                    attempt.codecSafetyAttenuationDb = attenuationDb;
                    attempt.requestedPcmCeilingDbtp =
                            repairedMaster.verification.truePeakEstimateDbtp;
                    result.codecAttempts.set(result.codecAttempts.size() - 1, attempt);
                    if (attempt.pass) {
                        if (candidate.file.exists() && !candidate.file.delete()) {
                            candidate.file.deleteOnExit();
                        }
                        candidate.file = repaired;
                        candidate.render = repairedRender;
                        result.codecSafetyRerendered = true;
                        result.codecSafetyAttenuated = true;
                    }
                } catch (Exception repairError) {
                    attempt.pass = false;
                    attempt.verdict = "codec-safety rerender rejected: " + clean(repairError);
                    if (repaired.exists() && !repaired.delete()) repaired.deleteOnExit();
                }
            }
            if (attempt.pass) {
                chosen = candidate;
                chosenAttempt = attempt;
                break;
            }
            if (attempt.aac != null && !attempt.aac.available) {
                result.warnings.add("The device AAC encoder was unavailable: "
                        + value(attempt.aac.error, "unknown encoder error"));
                break;
            }
            deleteCodecTemps(attempt);
        }

        if (chosen == null) {
            chosen = engine.selected;
            chosenAttempt = findAttempt(result.codecAttempts, chosen.strategy);
            result.warnings.add("No internal candidate completed every codec gate; the highest-scoring PCM-safe candidate was retained with a conditional verdict.");
        }
        result.finalCandidate = chosen;
        for (FullAiMasteringEngine.Candidate candidate : engine.candidates) {
            candidate.selectedAfterPromotionGates = candidate == chosen;
        }
        result.codecFallback = chosen != result.initialPcmWinner;
        if (result.codecFallback) {
            engine.selected = chosen;
            engine.selectionReason += " Codec QC rejected the initial winner and selected "
                    + chosen.strategy + " as the highest-ranked codec-safe fallback.";
        }

        String base = safeBase(requestedBase);
        result.finalMaster = new File(workDirectory, ReleaseFormats.camelBase(requestedBase) + "_Master_24bit-48kHz.wav");
        copy(chosen.file, result.finalMaster);
        result.finalMasterVerification = AudioAnalyzer.analyze(
                result.finalMaster.getName(), () -> new FileInputStream(result.finalMaster), progress);
        verifyFinalCopy(chosen.render.master.verification, result.finalMasterVerification);
        PassagePreservation.Scan finalPassages = PassagePreservation.scan(
                result.finalMaster, result.finalMasterVerification);
        // Re-check the distinct, explicitly qualified sparse-peak passage policy
        // at the authoritative final-copy boundary. All other winners retain
        // the unmodified strict passage policy.
        boolean verifiedSparsePeak = chosen.sparsePeakAware
                && engine.transparent != null && engine.transparent.render != null
                && SparsePeakRecovery.stableOutput(sourceAnalysis,
                        engine.transparent.render.master.verification, chosen.render.master);
        result.passageCheck = verifiedSparsePeak
                ? PassagePreservation.compareSparsePeak(engine.passageBaseline, finalPassages)
                : PassagePreservation.compare(engine.passageBaseline, finalPassages);
        if (!result.passageCheck.passed()) throw new IOException(result.passageCheck.summary());
        result.finalMasterSha256 = Hashing.sha256(result.finalMaster);

        if (chosenAttempt != null && chosenAttempt.pass && chosenAttempt.aac != null
                && chosenAttempt.aac.available && chosenAttempt.aac.file.exists()) {
            File encodedTemporary = chosenAttempt.aac.file;
            result.finalAac = new File(workDirectory, base + "_FullAI256.m4a");
            copy(encodedTemporary, result.finalAac);
            result.finalAacSha256 = Hashing.sha256(result.finalAac);
            result.aac = chosenAttempt.aac;
            result.aac.file = result.finalAac;
            result.aacDecoded = chosenAttempt.aacDecoded;
            result.sbc = chosenAttempt.sbc;
            delete(encodedTemporary);
            delete(result.aacDecoded == null ? null : result.aacDecoded.decodedWav);
            delete(result.sbc == null ? null : result.sbc.decodedProxyWav);
        } else if (chosenAttempt != null) {
            result.aac = chosenAttempt.aac;
            result.aacDecoded = chosenAttempt.aacDecoded;
            result.sbc = chosenAttempt.sbc;
        }

        try {
            File sourceComposite = new File(workDirectory,
                    base + "_SOURCE_TR.wav");
            result.sourceTranslationComposite = TranslationComposite.build(source,
                    sourceAnalysis, sourceAnalysis.sections,
                    sourceComposite, progress);
            result.sourceTranslation = TranslationAnalyzer.analyze(sourceComposite,
                    result.sourceTranslationComposite, progress);
            File composite = new File(workDirectory, base + "_FullAI_TR.wav");
            result.translationComposite = TranslationComposite.build(result.finalMaster,
                    result.finalMasterVerification, sourceAnalysis.sections,
                    composite, progress);
            result.translation = TranslationAnalyzer.analyze(composite,
                    result.translationComposite, progress);
            TranslationAnalyzer.applySourceBaseline(result.translation,
                    result.sourceTranslation);
            result.translationVerdict = result.translation.reviewCount == 0
                    ? "PASS" : "REVIEW";
            if (result.translation.reviewCount > 0) {
                result.warnings.add(translationReviewWarning(result.translation));
            }
        } catch (Exception translationError) {
            result.translationVerdict = "REVIEW";
            result.warnings.add("Translation simulation was incomplete: "
                    + clean(translationError));
        }

        result.unresolvedSourceZeroRegions =
                sourceAnalysis.internalZeroRegions.size();
        result.continuityReviewRequired =
                result.unresolvedSourceZeroRegions > 0;
        if (result.continuityReviewRequired) {
            result.warnings.add(unresolvedContinuityWarning(sourceAnalysis));
        }
        if (sourceAnalysis.sourceGuard != null) {
            SourceGuardModel.Result guard = sourceAnalysis.sourceGuard;
            result.sourceLimitationReviewRequired = guard.likelyLossyInWav
                    || guard.artificial24From16;
            result.warnings.addAll(guard.warnings);
            if (guard.likelyPriorDennisMaster) {
                result.warnings.add("Previous-master evidence was detected; broad second-pass processing and limiter escalation were blocked.");
            }
        }

        AnalysisResult original = sourceAnalysis.originalSourceAnalysis == null
                ? sourceAnalysis : sourceAnalysis.originalSourceAnalysis;
        if (original.clippedSamples > 0) {
            long repaired = sourceAnalysis.clickDropoutRepair != null
                    && sourceAnalysis.clickDropoutRepair.repairApplied
                    ? sourceAnalysis.clickDropoutRepair.repairedClippedSamples : 0;
            result.warnings.add("Original source had " + original.clippedSamples
                    + " full-scale sample(s); bounded continuity repair changed " + repaired
                    + ". Lower post-render clip counts alone do not prove restoration of any remaining flattened peaks.");
        }
        if (Thread.currentThread().isInterrupted()) throw new IOException("Mastering cancelled");
        boolean pcmPass = safeRepaired(sourceAnalysis, result.finalMasterVerification,
                chosen.render.master);
        boolean codecsPass = chosenAttempt != null && chosenAttempt.pass;
        result.targetShortfallLu = Math.max(0.0,
                -14.0 - result.finalMasterVerification.integratedLufs);
        boolean exactTarget = chosen.render.master.exactTargetReached;
        boolean compliantSourcePreserved = MasterRenderer.isCompliantSourcePreserved(
                sourceAnalysis, result.finalMasterVerification, chosen.render.master);
        boolean codecSafeMargin = result.codecSafetyAttenuated
                && result.targetShortfallLu <= 0.35;
        result.loudnessVerdict = compliantSourcePreserved ? "COMPLIANT_SOURCE_PRESERVED"
                : exactTarget ? "TARGET_REACHED"
                : codecSafeMargin ? "CODEC_SAFE_MARGIN"
                : chosen.render.master.dynamicsProtected
                ? "DYNAMIC_MASTER" : "REVIEW";
        if (compliantSourcePreserved) {
            result.warnings.add(String.format(Locale.US,
                    "Original WAV preserved byte-for-byte at %.4f LUFS-I, within the 0.10 LU no-change tolerance; no gain, EQ, compression or extra dither was applied. Exact-target status is reported separately.",
                    result.finalMasterVerification.integratedLufs));
        }
        if (codecSafeMargin) {
            double codecSafetyAttenuationDb = chosenAttempt == null
                    ? result.targetShortfallLu
                    : chosenAttempt.codecSafetyAttenuationDb;
            result.warnings.add(String.format(Locale.US,
                    "AAC safety required %.4f LU of flat post-master attenuation; the final remains within the 0.35 LU codec-safe target margin and no additional limiting was applied.",
                    codecSafetyAttenuationDb));
        }
        if (chosen.render.master.sparsePeakReleaseMode) {
            result.warnings.add(String.format(Locale.US,
                    "Spotify-target loudness used sparse peak control: %.3f dB maximum, %.3f dB average, %.3f%% of frames above 1 dB, and %.3f%% above 3 dB. Extended limiting was accepted because measured activity remained inside every v0.10 sparsity gate.",
                    chosen.render.master.maximumLimiterReductionDb,
                    chosen.render.master.averageLimiterReductionDb,
                    chosen.render.master.limiterFramesAboveOneDbPercent,
                    chosen.render.master.limiterFramesAboveThreeDbPercent));
        }
        if ("DYNAMIC_MASTER".equals(result.loudnessVerdict)) {
            result.warnings.add(String.format(Locale.US,
                    "Natural-first mastering retained dynamics at %.4f LUFS-I, %.4f LU below the -14 target; this is technically safe but intentionally not labelled an exact-target release PASS.",
                    result.finalMasterVerification.integratedLufs,
                    result.targetShortfallLu));
        }
        result.technicalVerdict = technicalVerdict(
                pcmPass, codecsPass, exactTarget, codecSafeMargin, compliantSourcePreserved);
        if (result.continuityReviewRequired || result.sourceLimitationReviewRequired) {
            result.technicalVerdict = "CONDITIONAL";
        }
        try {
            result.radioPreparation = RadioPreparation.analyze(source, sourceAnalysis,
                    result.finalMaster, result.finalMasterVerification, progress);
            if (!result.radioPreparation.issues.isEmpty()) {
                result.technicalVerdict = "CONDITIONAL";
                result.warnings.add(result.radioPreparation.compactSummary());
            } else if (!"PASS".equals(result.technicalVerdict)) {
                result.radioPreparation.verdict = "CONDITIONAL";
            }
        } catch (IOException checkError) {
            if (Thread.currentThread().isInterrupted()) throw checkError;
            result.radioPreparation = new RadioPreparation.Result();
            result.radioPreparation.verdict = "INCOMPLETE";
            result.radioPreparation.error = clean(checkError);
            result.technicalVerdict = "CONDITIONAL";
            result.warnings.add("Radio preparation checks incomplete: " + clean(checkError));
        }
        for (String failure : engine.renderIntegrityFailures) {
            result.warnings.add("Optional candidate rejected; verified fallback retained: " + failure);
        }
        progress.update("Full AI: final master and release QC verified", 100);
        return result;
    }

    static String unresolvedContinuityWarning(AnalysisResult source) {
        StringBuilder out = new StringBuilder(
                "Source continuity requires review: ");
        int limit = Math.min(3, source.internalZeroRegions.size());
        for (int i = 0; i < limit; i++) {
            if (i > 0) out.append(", ");
            AnalysisResult.ZeroRegion region =
                    source.internalZeroRegions.get(i);
            out.append(String.format(Locale.US, "%.6f–%.6f s",
                    region.startSeconds(source.wav.sampleRate),
                    region.endSeconds(source.wav.sampleRate)));
        }
        if (source.internalZeroRegions.size() > limit) {
            out.append(" and ")
                    .append(source.internalZeroRegions.size() - limit)
                    .append(" more region(s)");
        }
        out.append(". No sample-proven short-dropout repair was applied to these 100 ms-or-longer regions. Final 24-bit dither can make exact-zero counts disappear, but that is not evidence of continuity repair.");
        return out.toString();
    }

    static List<FullAiMasteringEngine.Candidate> codecOrder(FullAiMasteringEngine.Result engine) {
        ArrayList<FullAiMasteringEngine.Candidate> order = new ArrayList<>();
        if (engine.selected != null) order.add(engine.selected);
        for (FullAiMasteringEngine.Candidate c : engine.rankedEligible)
            if (c != engine.selected && c.eligible && !c.promotionBlocked
                    && c.file != null && c.file.isFile()) order.add(c);
        return order;
    }

    static String translationReviewWarning(TranslationAnalyzer.Result translation) {
        StringBuilder out = new StringBuilder("Translation simulation requires review: ");
        boolean firstDevice = true;
        for (TranslationAnalyzer.DeviceResult device : translation.devices) {
            if (!"REVIEW".equals(device.verdict)) continue;
            if (!firstDevice) out.append("; ");
            firstDevice = false;
            out.append(device.name);
            if (!device.reviewReasons.isEmpty()) {
                out.append(" (");
                for (int i = 0; i < device.reviewReasons.size(); i++) {
                    if (i > 0) out.append(", ");
                    out.append(device.reviewReasons.get(i));
                }
                out.append(')');
            }
        }
        if (firstDevice) out.append("unspecified response-profile condition");
        return out.toString();
    }

    private static CodecAttempt testCodecs(String strategy, File master,
                                           File workDirectory,
                                           AudioAnalyzer.Progress progress) {
        CodecAttempt out = new CodecAttempt();
        out.strategy = strategy;
        out.masterFile = master;
        String stem = safe(strategy) + "-" + System.nanoTime();
        File aac = new File(workDirectory, stem + ".m4a");
        File decoded = new File(workDirectory, stem + "-aac-decoded.wav");
        File sbc = new File(workDirectory, stem + "-sbc-proxy.wav");
        out.aac = AndroidCodecEncoder.encodeAac(master, aac, 256000, progress);
        if (!out.aac.available) {
            out.verdict = "AAC encoder unavailable: " + value(out.aac.error, "unknown error");
            return out;
        }
        out.aacDecoded = AndroidCodecDecoder.decodeAndMeasure(aac, decoded, progress);
        if (out.aacDecoded.error != null || out.aacDecoded.verification == null) {
            out.verdict = "AAC decode/measurement failed: "
                    + value(out.aacDecoded.error, "unknown error");
            return out;
        }
        try {
            out.sbc = SbcProxy.render(master, sbc, progress);
        } catch (Exception error) {
            out.verdict = "SBC stress proxy failed: " + clean(error);
            return out;
        }
        AnalysisResult pcm;
        try {
            pcm = AudioAnalyzer.analyze(master.getName(),
                    () -> new FileInputStream(master), progress);
        } catch (Exception error) {
            out.verdict = "PCM recheck failed: " + clean(error);
            return out;
        }
        AnalysisResult a = out.aacDecoded.verification;
        AnalysisResult s = out.sbc.verification;
        boolean durationSafe = Math.abs(a.wav.durationSeconds - pcm.wav.durationSeconds) <= 0.25;
        boolean aacSafe = a.truePeakEstimateDbtp <= -1.0
                && a.clippedSamples == 0 && a.nonFiniteSamples == 0
                && durationSafe && a.stereoCorrelation >= -0.10;
        boolean sbcSafe = s.truePeakEstimateDbtp <= -1.0
                && s.nonFiniteSamples == 0 && s.stereoCorrelation >= -0.10
                && Math.abs(s.wav.durationSeconds - pcm.wav.durationSeconds) <= 0.01;
        out.pass = aacSafe && sbcSafe;
        out.verdict = out.pass ? "PASS: decoded AAC and SBC proxy remained peak/continuity/phase safe"
                : String.format(Locale.US,
                "REJECT: AAC %.3f dBTP, clips %d, duration delta %.4f s; SBC %.3f dBTP, clips %d",
                a.truePeakEstimateDbtp, a.clippedSamples,
                a.wav.durationSeconds - pcm.wav.durationSeconds,
                s.truePeakEstimateDbtp, s.clippedSamples);
        return out;
    }

    private static boolean canRepairOvershoot(CodecAttempt attempt) {
        if (attempt == null || attempt.aac == null || !attempt.aac.available
                || attempt.aacDecoded == null || attempt.aacDecoded.verification == null) {
            return false;
        }
        AnalysisResult a = attempt.aacDecoded.verification;
        return a.truePeakEstimateDbtp > -1.0 && a.truePeakEstimateDbtp <= -0.35
                && a.nonFiniteSamples == 0;
    }

    static double requiredCodecSafetyAttenuationDb(CodecAttempt attempt) {
        if (!canRepairOvershoot(attempt)) return 0.0;
        double decodedPeak = attempt.aacDecoded.verification.truePeakEstimateDbtp;
        return Math.min(1.0, Math.max(0.12, decodedPeak + 1.0 + 0.12));
    }

    static String technicalVerdict(boolean pcmPass, boolean codecsPass,
                                   boolean exactTarget) {
        return technicalVerdict(pcmPass, codecsPass, exactTarget, false);
    }

    static String technicalVerdict(boolean pcmPass, boolean codecsPass,
                                   boolean exactTarget,
                                   boolean codecSafeMargin) {
        return technicalVerdict(pcmPass, codecsPass, exactTarget, codecSafeMargin, false);
    }

    static String technicalVerdict(boolean pcmPass, boolean codecsPass,
                                   boolean exactTarget, boolean codecSafeMargin,
                                   boolean compliantSourcePreserved) {
        return pcmPass && codecsPass && (exactTarget || codecSafeMargin || compliantSourcePreserved)
                ? "PASS" : "CONDITIONAL";
    }

    private static AutonomousMasteringRenderer.Result copyWithMaster(
            AutonomousMasteringRenderer.Result prior,
            MasterRenderer.Result master,
            double attenuationDb) {
        AutonomousMasteringRenderer.Result out =
                new AutonomousMasteringRenderer.Result();
        out.plan = prior.plan;
        out.master = master;
        out.correctedPremaster = prior.correctedPremaster;
        out.maximumLocalCutDb = prior.maximumLocalCutDb;
        out.maximumCompressionReductionDb = prior.maximumCompressionReductionDb;
        out.maximumGlobalEqDb = prior.maximumGlobalEqDb;
        out.guardrailVerdict = prior.guardrailVerdict
                + String.format(Locale.US,
                "; flat codec-safety attenuation %.3f dB applied after the selected chain with no additional limiting",
                attenuationDb);
        return out;
    }

    private static boolean safeRepaired(AnalysisResult source, AnalysisResult output,
                                        MasterRenderer.Result render) {
        return output.wav.frames == source.wav.frames
                && output.wav.sampleRate == source.wav.sampleRate
                && !output.wav.floatingPoint
                && output.wav.bitsPerSample == 24
                && output.wav.channels == 2
                && output.truePeakEstimateDbtp <= -1.0
                && output.clippedSamples == 0
                && output.nonFiniteSamples == 0
                && output.internalZeroRuns <= source.internalZeroRuns
                && output.stereoCorrelation >= source.stereoCorrelation - 0.035
                && output.lowBandCorrelation >= source.lowBandCorrelation - 0.025
                && source.lraLu - output.lraLu <= 1.25
                && (render.exactTargetReached || render.dynamicsProtected
                || MasterRenderer.isCompliantSourcePreserved(source, output, render));
    }

    private static void verifyFinalCopy(AnalysisResult expected,
                                        AnalysisResult actual) throws IOException {
        if (!expected.sourceSha256.equalsIgnoreCase(actual.sourceSha256)
                || expected.wav.frames != actual.wav.frames
                || actual.wav.bitsPerSample != 24 || actual.wav.sampleRate != 48000
                || actual.wav.channels != 2) {
            throw new IOException("The finalized Full AI master copy failed hash or format verification");
        }
    }

    private static CodecAttempt findAttempt(List<CodecAttempt> attempts,
                                            String strategy) {
        for (CodecAttempt attempt : attempts) {
            if (strategy.equals(attempt.strategy)) return attempt;
        }
        return null;
    }

    static void deleteCodecTemps(CodecAttempt attempt) {
        if (attempt == null) return;
        delete(attempt.aac == null ? null : attempt.aac.file);
        delete(attempt.aacDecoded == null ? null : attempt.aacDecoded.decodedWav);
        delete(attempt.sbc == null ? null : attempt.sbc.decodedProxyWav);
    }

    private static void delete(File file) {
        if (file != null && file.exists() && !file.delete()) file.deleteOnExit();
    }

    private static void copy(File source, File destination) throws IOException {
        try (InputStream input = new BufferedInputStream(
                new FileInputStream(source), 256 * 1024);
             OutputStream output = new BufferedOutputStream(
                     new FileOutputStream(destination), 256 * 1024)) {
            byte[] buffer = new byte[256 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) output.write(buffer, 0, read);
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("Full AI finalization cancelled");
                }
            }
        }
    }

    private static AudioAnalyzer.Progress rangeProgress(AudioAnalyzer.Progress parent,
                                                        String label, int start, int end) {
        final int lo = Math.max(0, Math.min(100, start));
        final int hi = Math.max(lo, Math.min(100, end));
        final int[] last = {lo};
        return (stage, percent) -> {
            int p = Math.max(0, Math.min(100, percent));
            int mapped = lo + (int) Math.round((hi - lo) * (p / 100.0));
            if (mapped < last[0]) mapped = last[0];
            else last[0] = mapped;
            parent.update(label + " • " + stage, mapped);
        };
    }

    private static String safeBase(String value) {
        String out = value == null ? "Song" : value.replaceAll("[^A-Za-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (out.isEmpty()) out = "Song";
        if (out.length() > 28) out = out.substring(0, 28).replaceAll("_+$", "");
        return out;
    }

    private static String safe(String value) {
        return safeBase(value).toLowerCase(Locale.US);
    }

    private static String value(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private static String clean(Exception error) {
        return value(error.getMessage(), error.getClass().getSimpleName());
    }
}
