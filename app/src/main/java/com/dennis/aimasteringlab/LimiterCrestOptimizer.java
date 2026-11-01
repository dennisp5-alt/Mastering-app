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

/** Auditions verified limiter time constants and retains the lowest-cost safe render. */
final class LimiterCrestOptimizer {
    static final class Trial {
        MasterRenderer.LimiterProfile profile;
        MasterRenderer.Result master;
        File file;
        boolean eligible;
        double cost = Double.POSITIVE_INFINITY;
        double crestLossDb;
        double lraLossLu;
        String verdict;
    }

    static final class Result {
        String model = "DAML v0.12 verified limiter envelope competition";
        String method = "five rendered stereo-linked look-ahead profiles including ramped attack and four-times oversampling; actual LUFS/true-peak/LRA/crest verification; minimum-cost safe selection";
        final List<Trial> trials = new ArrayList<>();
        Trial selected;
        MasterRenderer.Result master;
        boolean limiterRequired;
        boolean alternativeProfileSelected;
        boolean sparseVocalEarlyStop;
        double balancedCost = Double.NaN;
        double selectedCost = Double.NaN;
        double costImprovement;
        double measuredTargetBenefit;
        String decision;
    }

    private LimiterCrestOptimizer() {}

    static boolean available(AnalysisResult analysis) {
        return analysis != null && analysis.limiterRequired
                && analysis.limiterAllowed;
    }

    static Result render(AudioAnalyzer.StreamProvider source,
                         AnalysisResult analysis,
                         File output,
                         File workDirectory,
                         double finalTruePeakCeilingDbtp,
                         boolean forceLimiter,
                         AudioAnalyzer.Progress progress) throws IOException {
        return render(source, analysis, output, workDirectory,
                finalTruePeakCeilingDbtp, forceLimiter, false, progress);
    }

    /**
     * Optional sparse-audition budget for an already measured, guarded vocal
     * premaster. Global limiter/crest auditions still render all five profiles.
     * The BALANCED result must first pass complete WAV, loudness and peak QC.
     */
    static Result render(AudioAnalyzer.StreamProvider source,
                         AnalysisResult analysis,
                         File output,
                         File workDirectory,
                         double finalTruePeakCeilingDbtp,
                         boolean forceLimiter,
                         boolean sparseVocalOnly,
                         AudioAnalyzer.Progress progress) throws IOException {
        if (analysis.wav.channels != 2 || analysis.wav.sampleRate != 48000) {
            throw new IOException("Limiter optimisation requires a 48 kHz stereo pre-master");
        }
        if (!available(analysis) && !forceLimiter) {
            throw new IOException("Limiter optimisation was bypassed because flat gain creates no song-derived limiter pressure");
        }
        if (!workDirectory.exists() && !workDirectory.mkdirs()) {
            throw new IOException("Could not create limiter audition cache");
        }
        Result result = new Result();
        result.limiterRequired = true;
        // All five profiles render exactly the same PCM when the limiter is
        // mathematically bypassed. Audition only BALANCED in that case; the
        // five-way tie breaker would also select BALANCED. This preserves
        // the final audio and the measured-benefit outcome, without five
        // redundant full-track renders and verification scans.
        boolean limiterEngages = MasterRenderer.limiterWillEngage(
                analysis, analysis.appliedSafeGainDb, forceLimiter);
        // Only vocal premasters may audition BALANCED first. That enables an
        // evidence-backed stop when its *actual rendered* limiter activity is
        // too sparse to justify four additional complete masters. More active
        // material still receives every profile and the original cost ranking.
        MasterRenderer.LimiterProfile[] profiles = !limiterEngages
                ? new MasterRenderer.LimiterProfile[] {MasterRenderer.LimiterProfile.BALANCED}
                : sparseVocalOnly && !forceLimiter
                ? new MasterRenderer.LimiterProfile[] {
                    MasterRenderer.LimiterProfile.BALANCED,
                    MasterRenderer.LimiterProfile.CLEAN_LONG,
                    MasterRenderer.LimiterProfile.TRANSIENT_RECOVERY,
                    MasterRenderer.LimiterProfile.RAMPED_CLEAN,
                    MasterRenderer.LimiterProfile.OVERSAMPLED_CLEAN
                } : MasterRenderer.LimiterProfile.values();
        Trial best = null;
        int index = 0;
        boolean sparseVocalResolved = false;
        for (MasterRenderer.LimiterProfile profile : profiles) {
            index++;
            Trial trial = new Trial();
            trial.profile = profile;
            trial.file = new File(workDirectory,
                    "limiter-profile-" + profile.label + "-"
                            + System.nanoTime() + ".wav");
            result.trials.add(trial);
            progress.update("Limiter intelligence: rendering profile "
                    + index + "/" + profiles.length,
                    0);
            try {
                trial.master = MasterRenderer.renderWithCeiling(source,
                        analysis, analysis.appliedSafeGainDb, trial.file,
                        finalTruePeakCeilingDbtp, forceLimiter, profile,
                        progress);
                AnalysisResult verified = trial.master.verification;
                trial.crestLossDb = Math.max(0.0,
                        analysis.crestFactorDb - verified.crestFactorDb);
                trial.lraLossLu = Math.max(0.0,
                        analysis.lraLu - verified.lraLu);
                boolean safe = verified.truePeakEstimateDbtp <= -1.0
                        && verified.clippedSamples == 0
                        && verified.nonFiniteSamples == 0
                        && verified.wav.frames == analysis.wav.frames
                        && (trial.master.exactTargetReached
                        || trial.master.dynamicsProtected);
                if (!safe) {
                    trial.verdict = "REJECT: rendered profile failed final safety verification";
                    delete(trial.file);
                    continue;
                }
                trial.cost = qualityCost(trial.master, analysis);
                trial.eligible = true;
                trial.verdict = String.format(Locale.US,
                        "PASS: cost %.4f; limiter %.4f dB; crest loss %.4f dB; LRA loss %.4f LU",
                        trial.cost, trial.master.maximumLimiterReductionDb,
                        trial.crestLossDb, trial.lraLossLu);
                if (profile == MasterRenderer.LimiterProfile.BALANCED) {
                    result.balancedCost = trial.cost;
                }
                if (best == null || trial.cost < best.cost - 1.0e-9
                        || (Math.abs(trial.cost - best.cost) <= 1.0e-9
                        && profile == MasterRenderer.LimiterProfile.BALANCED)) {
                    best = trial;
                }
                // Fail-open to the full five-way comparison unless the
                // measured balanced render satisfies every strict sparse
                // activity, dynamics, loudness and peak criterion.
                if (sparseVocalOnly && !forceLimiter
                        && profile == MasterRenderer.LimiterProfile.BALANCED
                        && sparseVocalSufficient(trial, analysis,
                                finalTruePeakCeilingDbtp)) {
                    sparseVocalResolved = true;
                    break;
                }
            } catch (Exception error) {
                if (Thread.currentThread().isInterrupted()) { cleanup(result, null); throw new IOException("Limiter comparison cancelled", error); }
                trial.verdict = "REJECT: " + clean(error);
                delete(trial.file);
            }
        }
        if (best == null || best.master == null) {
            cleanup(result, null);
            throw new IOException("Every limiter profile failed rendered-file verification");
        }
        copy(best.file, output);
        AnalysisResult copied = AudioAnalyzer.analyze(output.getName(),
                () -> new FileInputStream(output), progress);
        if (!best.master.verification.sourceSha256.equalsIgnoreCase(
                copied.sourceSha256)) {
            delete(output);
            cleanup(result, null);
            throw new IOException("Selected limiter profile failed durable-copy verification");
        }
        best.master.file = output;
        best.master.savedFileName = output.getName();
        best.master.verification = copied;
        result.selected = best;
        result.sparseVocalEarlyStop = sparseVocalResolved;
        result.master = best.master;
        result.selectedCost = best.cost;
        result.alternativeProfileSelected = best.profile
                != MasterRenderer.LimiterProfile.BALANCED;
        if (Double.isFinite(result.balancedCost)) {
            result.costImprovement = Math.max(0.0,
                    result.balancedCost - result.selectedCost);
        }
        result.measuredTargetBenefit = clamp(
                result.costImprovement / 0.08, 0.0, 1.0);
        result.decision = String.format(Locale.US,
                "%s selected after %d rendered profile(s)%s; cost %.4f vs balanced %.4f; measured benefit %.3f; limiter %.3f dB max",
                best.profile.label, result.trials.size(),
                sparseVocalResolved ? " (vocal sparse-activity gate: BALANCED complete QC passed; other profiles not rendered)"
                        : limiterEngages ? "" : " (limiter bypass proved identical for all profiles)",
                result.selectedCost, result.balancedCost, result.measuredTargetBenefit,
                best.master.maximumLimiterReductionDb);
        cleanup(result, best);
        progress.update("Limiter intelligence: verified profile selected", 100);
        return result;
    }

    /**
     * Bounded low-impact shortcut used ONLY by guarded professional vocal
     * candidates. A full verified master must already exist; skipped profiles
     * cannot be described as rendered or compared. Any significant peak,
     * average reduction, dynamics loss, gain-step or delivery concern falls
     * back to the full competition. No altered limiter DSP or safety policy.
     */
    static boolean sparseVocalSufficient(Trial balanced, AnalysisResult original,
                                          double ceilingDbtp) {
        if (balanced == null || balanced.profile != MasterRenderer.LimiterProfile.BALANCED
                || !balanced.eligible || balanced.master == null || original == null
                || balanced.master.verification == null) return false;
        MasterRenderer.Result m = balanced.master;
        AnalysisResult verified = m.verification;
        return m.limiterUsed
                && Double.isFinite(m.maximumLimiterReductionDb)
                && m.maximumLimiterReductionDb >= 0.0
                && m.maximumLimiterReductionDb <= 0.25
                && Double.isFinite(m.averageLimiterReductionDb)
                && m.averageLimiterReductionDb >= 0.0
                && m.averageLimiterReductionDb <= 0.0015
                && Double.isFinite(m.maximumLimiterGainStepDb)
                && m.maximumLimiterGainStepDb <= 0.025
                && balanced.crestLossDb <= 0.08
                && balanced.lraLossLu <= 0.08
                && verified.clippedSamples == 0
                && verified.nonFiniteSamples == 0
                && verified.wav != null && original.wav != null
                && verified.wav.frames == original.wav.frames
                && Double.isFinite(verified.integratedLufs)
                && Math.abs(verified.integratedLufs + 14.0) <= 0.02
                && Double.isFinite(verified.truePeakEstimateDbtp)
                && verified.truePeakEstimateDbtp <= ceilingDbtp;
    }

    /** Engineering stress proxies, not an auditory quality score. */
    static double qualityCost(MasterRenderer.Result master, AnalysisResult source) {
        AnalysisResult v=master.verification;
        double cost=.30*master.maximumLimiterReductionDb
                +.85*master.averageLimiterReductionDb
                +.015*master.limiterFramesAboveOneDbPercent
                +.08*master.maximumLimiterGainStepDb
                +.80*Math.max(0,source.crestFactorDb-v.crestFactorDb)
                +.70*Math.max(0,source.lraLu-v.lraLu)
                +4.0*Math.abs(v.integratedLufs+14.0)
                +2.0*Math.max(0,v.truePeakEstimateDbtp+1.05);
        return Double.isFinite(cost)?cost:Double.POSITIVE_INFINITY;
    }

    private static void cleanup(Result result, Trial keep) {
        for (Trial trial : result.trials) {
            if (trial != keep) delete(trial.file);
        }
        if (keep != null) delete(keep.file);
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
                    throw new IOException("Limiter profile copy cancelled");
                }
            }
        }
    }

    private static String clean(Exception error) {
        String value = error.getMessage();
        return value == null || value.trim().isEmpty()
                ? error.getClass().getSimpleName() : value;
    }

    private static double clamp(double value, double minimum,
                                double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static void delete(File file) {
        if (file != null && file.exists() && !file.delete()) {
            file.deleteOnExit();
        }
    }
}
