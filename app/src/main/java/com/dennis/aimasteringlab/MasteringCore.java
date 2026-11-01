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
    }

    static final class Result {
        /** Analysis the engine mastered from (the repaired source when repairs ran). */
        AnalysisResult analysis;
        /** Stream of the audio the engine mastered from. */
        AudioAnalyzer.StreamProvider processing;
        /** Continuity-repaired source, or null when the original was used. */
        File repairedSource;
        AutonomousMasteringBrain.Result brain;
        FullAiMasteringEngine.Result engine;
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
        out.engine = FullAiMasteringEngine.run(out.processing, out.analysis, out.brain, options.stemArchive,
                work, options.preference, options.album, stages.at("Candidate mastering", 50, 100));
        return out;
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
