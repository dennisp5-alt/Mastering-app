package com.dennis.aimasteringlab;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * v0.5.2 — the ONE mastering path. Studio projects, the queue, stem-only imports and
 * Classic mastering all reach the candidate engine through this class, so the same
 * song gets the same analysis, the same repair and the same engine decision whichever
 * button started it.
 *
 * Before v0.5.2 Studio and Classic each carried their own copy of this sequence.
 * The copies had drifted: Studio failed a whole song if section mapping, local
 * diagnostics or technical analysis threw, while Classic continued without that
 * optional evidence. The fault-tolerant behaviour is now used everywhere. When every
 * step succeeds the audio is unchanged from v0.15.7 (checked bit-for-bit on host).
 */
final class MasteringCore {
    /** Maps a named step to a slice of the caller's progress bar. */
    interface Stages {
        AudioAnalyzer.Progress at(String step, int start, int end);
    }

    /** Optional inputs only Classic mastering offers today. All may be null. */
    static final class Options {
        File stemArchive;
        AudioAnalyzer.StreamProvider reference;
        String referenceName;
        String referenceSha256;
        DennisPreferenceMemory.Profile preference;
        AlbumConsistencyModel.Profile album;
        String repairedFileName = "repaired-source.wav";
        /** v0.5.3: tone stage, then up to [MAX_REPAIR_STAGES] repair stages on its winner. */
        boolean chained = CHAINED_BY_DEFAULT;
    }

    /** Host regression checks turn this off to compare against the v0.5.2 competition. */
    static volatile boolean CHAINED_BY_DEFAULT = !"false".equals(System.getProperty("stemlab.chained"));

    /** Repairs stacked on the tone winner at most (each a different family). */
    static final int MAX_REPAIR_STAGES = 2;

    static final class Result {
        /** Analysis the engine mastered from (the repaired source when repairs ran). */
        AnalysisResult analysis;
        /** Stream of the audio the engine mastered from. */
        AudioAnalyzer.StreamProvider processing;
        /** Continuity-repaired source, or null when the original was used. */
        File repairedSource;
        AutonomousMasteringBrain.Result brain;
        FullAiMasteringEngine.Result engine;
        /** What each stage chose, in order ("tone: sonic_finish", "repair: …"). */
        final java.util.List<String> chain = new java.util.ArrayList<>();
        /** Families stacked on the tone winner (stereo, advanced, professional, nextgen, stem). */
        final java.util.List<String> repairs = new java.util.ArrayList<>();
    }

    private MasteringCore() {}

    /**
     * @param raw      the authoritative source as imported
     * @param measured its measurement, with {@code sourceGuard} (and any stem evidence) set
     */
    static Result master(AudioAnalyzer.StreamProvider raw, AnalysisResult measured, File work,
                         Options options, Stages stages) throws IOException {
        if (options == null) options = new Options();
        if (measured.wav.channels != 2 || measured.wav.sampleRate != 48000)
            throw new IOException("Mastering requires a 48 kHz stereo WAV");
        if (measured.sourceGuard == null)
            measured.sourceGuard = SourceGuardModel.analyze(raw, measured, stages.at("Source guard", 0, 2));
        try {
            measured.sections = LearnedSectionMapper.analyze(raw, measured, stages.at("Section mapping", 2, 9));
        } catch (Exception sectionError) {
            interrupted(sectionError);
            // Energy-derived sections from the base meter remain usable.
        }
        try {
            measured.aiDiagnostics = AdaptiveDiagnosticModel.analyze(raw, measured, stages.at("Adaptive diagnostics", 9, 16));
        } catch (Exception diagnosticError) {
            interrupted(diagnosticError);
            measured.aiDiagnostics = AdaptiveDiagnosticModel.unavailable(
                    "Local diagnostic unavailable: " + clean(diagnosticError), measured);
        }
        File repaired = new File(work, options.repairedFileName);
        boolean useRepaired = false;
        try {
            measured.clickDropoutRepair = ClickDropoutRepair.analyze(raw, measured, stages.at("Event repair analysis", 16, 21));
            if (measured.clickDropoutRepair.hasRepairs()) {
                ClickDropoutRepair.render(raw, measured, measured.clickDropoutRepair, repaired,
                        stages.at("Event repair render", 21, 27));
                useRepaired = true;
            }
        } catch (Exception repairError) {
            interrupted(repairError);
            repaired.delete();
            useRepaired = false;
            measured.clickDropoutRepair = new ClickDropoutRepair.Result();
            measured.clickDropoutRepair.inheritedClipSamplesRemaining = measured.clippedSamples;
            measured.clickDropoutRepair.status = "Repair bypassed: " + clean(repairError);
            measured.clickDropoutRepair.safetyNotes.add("The untouched authoritative source continued through mastering.");
        }
        Result out = new Result();
        out.repairedSource = useRepaired ? repaired : null;
        final File repairedFile = out.repairedSource;
        out.processing = repairedFile == null ? raw : () -> new FileInputStream(repairedFile);
        out.analysis = repairedFile == null ? measured
                : TechnicalSourcePreparation.reanalyze(out.processing, measured, stages.at("Repaired-source verification", 27, 33));
        try {
            out.analysis.nextGenerationIntelligence = NextGenerationMasteringModel.analyze(out.processing, out.analysis,
                    options.reference, options.referenceName, options.referenceSha256,
                    stages.at("Technical intelligence", 33, 40));
        } catch (Exception technicalError) {
            interrupted(technicalError);
            out.analysis.nextGenerationIntelligence = new NextGenerationMasteringModel.Result();
            out.analysis.nextGenerationIntelligence.safetyNotes.add(
                    "Optional technical/reference analysis unavailable: " + clean(technicalError));
        }
        out.brain = AutonomousMasteringBrain.analyze(out.processing, out.analysis, options.album,
                stages.at("Mastering analysis", 40, 50));
        if (!options.chained) {
            out.engine = FullAiMasteringEngine.run(out.processing, out.analysis, out.brain, options.stemArchive,
                    work, options.preference, options.album, stages.at("Candidate mastering", 50, 100));
            out.chain.add("full: " + out.engine.selected.strategy);
            return out;
        }
        // 1. Tone: the core EQ/dynamics plans compete (and the adaptive broad ladder).
        out.engine = FullAiMasteringEngine.run(out.processing, out.analysis, out.brain, options.stemArchive,
                work, options.preference, options.album, FullAiMasteringEngine.Scope.TONE,
                stages.at("Tone mastering", 50, 76));
        out.chain.add("tone: " + out.engine.selected.strategy);
        // 2. Repairs on top of the tone winner: each repair family competes against the
        //    winner itself (as the transparent control), and a family that wins is stacked
        //    and not offered again. Stops as soon as no repair proves a benefit.
        final AnalysisResult measuredSource = measured;
        final AnalysisResult toneVerification = out.engine.selected.render == null ? null
                : out.engine.selected.render.master.verification;
        java.util.Set<String> used = new java.util.HashSet<>();
        for (int stage = 1; stage <= MAX_REPAIR_STAGES; stage++) {
            int from = 76 + (stage - 1) * 12, to = from + 12;
            File winner = out.engine.selected == null ? null : out.engine.selected.file;
            if (winner == null || !winner.isFile()) break;
            File input = new File(work, "chain-" + stage + "-input.wav");
            copy(winner, input);
            final File stageInput = input;
            AudioAnalyzer.StreamProvider provider = () -> new FileInputStream(stageInput);
            AnalysisResult a = AudioAnalyzer.analyze(input.getName(), provider,
                    stages.at("Repairs " + stage + " • measuring the tone master", from, from + 2));
            carryOver(measuredSource, a);
            try {
                a.sections = LearnedSectionMapper.analyze(provider, a, stages.at("Repairs " + stage + " • sections", from + 2, from + 3));
            } catch (Exception sectionError) { interrupted(sectionError); }
            try {
                a.aiDiagnostics = AdaptiveDiagnosticModel.analyze(provider, a, stages.at("Repairs " + stage + " • diagnostics", from + 3, from + 4));
            } catch (Exception diagnosticError) {
                interrupted(diagnosticError);
                a.aiDiagnostics = AdaptiveDiagnosticModel.unavailable("Local diagnostic unavailable: " + clean(diagnosticError), a);
            }
            try {
                a.nextGenerationIntelligence = NextGenerationMasteringModel.analyze(provider, a,
                        options.reference, options.referenceName, options.referenceSha256,
                        stages.at("Repairs " + stage + " • technical analysis", from + 4, from + 5));
            } catch (Exception technicalError) {
                interrupted(technicalError);
                a.nextGenerationIntelligence = new NextGenerationMasteringModel.Result();
                a.nextGenerationIntelligence.safetyNotes.add("Optional technical/reference analysis unavailable: " + clean(technicalError));
            }
            AutonomousMasteringBrain.Result brain = AutonomousMasteringBrain.analyze(provider, a, options.album,
                    stages.at("Repairs " + stage + " • analysis", from + 5, from + 6));
            File stageWork = new File(work, "repairs-" + stage);
            FullAiMasteringEngine.Result engine;
            try {
                engine = FullAiMasteringEngine.run(provider, a, brain, options.stemArchive, stageWork,
                        options.preference, options.album, FullAiMasteringEngine.Scope.repairs(used),
                        stages.at("Repairs " + stage, from + 6, to));
            } catch (IOException repairError) {
                interrupted(repairError);
                out.chain.add("repairs " + stage + ": skipped (" + clean(repairError) + ")");
                break;
            }
            String family = FullAiMasteringEngine.familyOf(engine.selected);
            if (engine.selected == null || engine.selected == engine.transparent || "core".equals(family)) {
                out.chain.add("repairs " + stage + ": none proved a benefit");
                break;
            }
            // Stacked repairs may not add up to over-processing: against the tone
            // master, the song keeps its punch (crest), dynamics (LRA) and width.
            String drift = drift(toneVerification, engine.selected.render.master.verification);
            if (drift != null) {
                out.chain.add("repair " + stage + " (" + family + "): " + engine.selected.strategy
                        + " rejected, " + drift);
                break;
            }
            used.add(family);
            out.repairs.add(family);
            out.chain.add("repair " + stage + " (" + family + "): " + engine.selected.strategy);
            out.processing = provider;
            out.analysis = a;
            out.brain = brain;
            out.engine = engine;
        }
        return out;
    }

    /**
     * The tone master is a new recording of the same song: its source-history guard
     * (lossy, bandwidth, previous-master evidence) and the optional stems' evidence
     * belong to the original and are kept; click/dropout repair already happened.
     */
    private static void carryOver(AnalysisResult original, AnalysisResult a) {
        a.sourceSha256 = a.sourceSha256 == null ? original.sourceSha256 : a.sourceSha256;
        a.sourceGuard = original.sourceGuard;
        a.clickDropoutRepair = new ClickDropoutRepair.Result();
        a.clickDropoutRepair.status = "Applied to the original before the tone stage";
        a.stemArchiveName = original.stemArchiveName;
        a.stemArchiveSha256 = original.stemArchiveSha256;
        a.stems.clear();
        a.stems.addAll(original.stems);
        a.wavStemCount = original.wavStemCount;
        a.stemReconstruction = original.stemReconstruction;
        a.stemAlignment = original.stemAlignment;
        a.stemTrust = original.stemTrust;
        a.stemTrustTier = original.stemTrustTier;
        a.stemArchiveAccepted = original.stemArchiveAccepted;
        a.stemArchiveAnalysisOnlyAccepted = original.stemArchiveAnalysisOnlyAccepted;
        a.vocalHumanity = original.vocalHumanity;
        a.maskingPockets = original.maskingPockets;
    }

    /** Why [after] drifted too far from the tone master [tone], or null when it did not. */
    static String drift(AnalysisResult tone, AnalysisResult after) {
        if (tone == null || after == null) return null;
        if (tone.crestFactorDb - after.crestFactorDb > 1.0)
            return String.format(java.util.Locale.US, "it cost %.1f dB of punch (crest)", tone.crestFactorDb - after.crestFactorDb);
        if (Math.abs(tone.lraLu - after.lraLu) > 1.0)
            return String.format(java.util.Locale.US, "it changed the dynamics by %.1f LU", Math.abs(tone.lraLu - after.lraLu));
        if (tone.stereoCorrelation - after.stereoCorrelation > 0.08)
            return String.format(java.util.Locale.US, "it widened the stereo image too far (correlation %.2f → %.2f)", tone.stereoCorrelation, after.stereoCorrelation);
        return null;
    }

    private static void copy(File from, File to) throws IOException {
        java.nio.file.Files.copy(from.toPath(), to.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private static void interrupted(Exception error) throws IOException {
        if (Thread.currentThread().isInterrupted()) {
            if (error instanceof IOException) throw (IOException) error;
            throw new IOException("Mastering cancelled", error);
        }
    }

    static String clean(Throwable error) {
        if (error == null) return "unknown error";
        String message = error.getMessage();
        return error.getClass().getSimpleName() + (message == null || message.isEmpty() ? "" : ": " + message);
    }
}
