package com.dennis.aimasteringlab;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Renders the three v0.7 authoritative-stereo candidate families. */
final class AdvancedStereoRenderer {
    enum Mode {
        SECTION_AWARE("section_aware_mastering"),
        ARTIFACT_REPAIR("ai_artifact_repair"),
        TRANSIENT_INTELLIGENCE("transient_intelligence"),
        ADVANCED_COMBINED("v07_advanced_combined");

        final String strategy;

        Mode(String strategy) {
            this.strategy = strategy;
        }
    }

    static final class DeltaVerification {
        long frames;
        double sourceCorrelation;
        double maximumAbsoluteDelta;
        double editedRmsDeltaDbfs;
        double protectedIntroMaxDelta;
        double protectedEndingMaxDelta;
        boolean protectedRegionsExact;
    }

    static final class Result {
        Mode mode;
        AutonomousMasteringRenderer.Result render;
        DeltaVerification delta;
        int correctionEvents;
        double maximumNominalEditDb;
        double modelConfidence;
        double evidenceBenefit;
        double measuredTargetBenefit;
        double sectionLiftImprovementLu;
        double artifactReductionDb;
        double transientPeakReductionDb;
        boolean authoritativeStereoOnly = true;
        boolean stemWaveformUsed;
        String evidenceSummary;
    }

    private static final int BUFFER_FRAMES = 4096;
    private static final double MAX_SECTION_EDIT_DB = 0.30;
    private static final double MAX_ARTIFACT_CUT_DB = 0.55;
    private static final double MAX_TRANSIENT_CUT_DB = 0.30;
    private static final double MAX_TOTAL_NOMINAL_EDIT_DB = 0.75;

    private AdvancedStereoRenderer() {}

    static Result render(AudioAnalyzer.StreamProvider source,
                         AnalysisResult sourceAnalysis,
                         AutonomousMasteringBrain.Plan transparentPlan,
                         Mode mode,
                         File output,
                         File workDirectory,
                         AudioAnalyzer.Progress progress) throws IOException {
        return render(source, sourceAnalysis, transparentPlan, mode, output,
                workDirectory, -1.0, false, progress);
    }

    static Result render(AudioAnalyzer.StreamProvider source,
                         AnalysisResult sourceAnalysis,
                         AutonomousMasteringBrain.Plan transparentPlan,
                         Mode mode,
                         File output,
                         File workDirectory,
                         double finalTruePeakCeilingDbtp,
                         boolean forceLimiter,
                         AudioAnalyzer.Progress progress) throws IOException {
        validate(sourceAnalysis, transparentPlan, mode);
        File corrected = new File(workDirectory,
                "v07-advanced-premaster-" + mode.strategy + "-"
                        + System.nanoTime() + ".wav");
        try {
            Stats stats = renderFloat(source, sourceAnalysis, mode, corrected,
                    progress);
            AnalysisResult correctedAnalysis = AudioAnalyzer.analyze(
                    corrected.getName(), () -> new FileInputStream(corrected),
                    progress);
            DeltaVerification delta = verifyDelta(source,
                    () -> new FileInputStream(corrected), sourceAnalysis);
            String guardrail = validatePremaster(sourceAnalysis,
                    correctedAnalysis, delta);
            MasterRenderer.Result master = MasterRenderer.renderWithCeiling(
                    () -> new FileInputStream(corrected), correctedAnalysis,
                    correctedAnalysis.appliedSafeGainDb, output,
                    finalTruePeakCeilingDbtp, forceLimiter, progress);

            Result result = describe(sourceAnalysis.advancedIntelligence, mode,
                    stats, sourceAnalysis.wav.durationSeconds);
            result.delta = delta;
            AutonomousMasteringRenderer.Result wrapper =
                    new AutonomousMasteringRenderer.Result();
            wrapper.plan = transparentPlan;
            wrapper.master = master;
            wrapper.correctedPremaster = correctedAnalysis;
            wrapper.maximumLocalCutDb = result.maximumNominalEditDb;
            wrapper.maximumCompressionReductionDb = 0.0;
            wrapper.maximumGlobalEqDb = 0.0;
            wrapper.guardrailVerdict = guardrail;
            result.render = wrapper;
            master.aiGuided = true;
            master.aiCorrectionEvents = result.correctionEvents;
            master.maximumAiCorrectionDb = result.maximumNominalEditDb;
            master.aiCorrectionSummary = mode.strategy + "; "
                    + result.evidenceSummary;
            return result;
        } finally {
            delete(corrected);
        }
    }

    private static void validate(AnalysisResult source,
                                 AutonomousMasteringBrain.Plan plan,
                                 Mode mode) throws IOException {
        if (source.wav.channels != 2 || source.wav.sampleRate != 48000) {
            throw new IOException("Advanced mastering requires a 48 kHz stereo authoritative mix");
        }
        if (plan == null || plan.hasAudioChanges()) {
            throw new IOException("Advanced mastering requires the transparent baseline plan");
        }
        AdvancedStereoModel.Result model = source.advancedIntelligence;
        if (model == null) {
            throw new IOException("Run advanced mastering analysis first");
        }
        boolean available = mode == Mode.SECTION_AWARE
                ? model.hasSectionAware()
                : mode == Mode.ARTIFACT_REPAIR
                ? model.hasArtifactRepair()
                : mode == Mode.TRANSIENT_INTELLIGENCE
                ? model.hasTransientControl()
                : model.availableModules() >= 2;
        if (!available) {
            throw new IOException("The requested advanced module has no eligible correction map");
        }
    }

    private static Stats renderFloat(AudioAnalyzer.StreamProvider source,
                                     AnalysisResult analysis,
                                     Mode mode,
                                     File destination,
                                     AudioAnalyzer.Progress progress) throws IOException {
        long dataBytes = analysis.wav.frames * 2L * 4L;
        if (dataBytes > 0xffffffffL - 36L) {
            throw new IOException("Advanced mastering intermediate exceeds the RIFF/WAV 4 GB limit");
        }
        File output = WavRenderIntegrity.stagingFile(destination, "v010-advanced");
        int rate = analysis.wav.sampleRate;
        AdvancedStereoModel.Result model = analysis.advancedIntelligence;
        EventControl quiet = control(model, mode,
                AdvancedStereoModel.Kind.SECTION_QUIET_CONTOUR,
                analysis.wav.durationSeconds, 0.200, 0.350);
        EventControl energy = control(model, mode,
                AdvancedStereoModel.Kind.SECTION_ENERGY_CONTOUR,
                analysis.wav.durationSeconds, 0.200, 0.350);
        EventControl fizz = control(model, mode,
                AdvancedStereoModel.Kind.ARTIFACT_HF_FIZZ,
                analysis.wav.durationSeconds, 0.045, 0.160);
        EventControl metallic = control(model, mode,
                AdvancedStereoModel.Kind.ARTIFACT_METALLIC_RESONANCE,
                analysis.wav.durationSeconds, 0.045, 0.180);
        EventControl phasey = control(model, mode,
                AdvancedStereoModel.Kind.ARTIFACT_PHASEY_HF,
                analysis.wav.durationSeconds, 0.060, 0.200);
        EventControl transientControl = control(model, mode,
                AdvancedStereoModel.Kind.TRANSIENT_PEAK_CONTROL,
                analysis.wav.durationSeconds, 0.0010, 0.055);

        Biquad fizzLeft = Biquad.highPass(rate, 8500.0);
        Biquad fizzRight = Biquad.highPass(rate, 8500.0);
        BandPass metallicLeft = new BandPass(rate, 3200.0, 7500.0);
        BandPass metallicRight = new BandPass(rate, 3200.0, 7500.0);
        Biquad phaseySide = Biquad.highPass(rate, 6500.0);
        Stats stats = new Stats();
        stats.events = quiet.count() + energy.count() + fizz.count()
                + metallic.count() + phasey.count()
                + transientControl.count();

        progress.update("Advanced mastering: rendering protected candidate", 0);
        try (WavFile.Reader reader = WavFile.open(source.open());
             FileOutputStream fileOutput = new FileOutputStream(output);
             OutputStream raw = new BufferedOutputStream(fileOutput,
                     256 * 1024)) {
            writeFloatHeader(raw, rate, 2, dataBytes);
            FloatWriter writer = new FloatWriter(raw);
            float[][] samples = new float[2][BUFFER_FRAMES];
            long absolute = 0L;
            int read;
            while ((read = reader.readFrames(samples, BUFFER_FRAMES)) > 0) {
                for (int frame = 0; frame < read; frame++, absolute++) {
                    double left = finite(samples[0][frame]);
                    double right = finite(samples[1][frame]);
                    double side = 0.5 * (left - right);
                    double seconds = absolute / (double) rate;

                    double quietDb = quiet.primaryDbAt(seconds);
                    double energyDb = energy.primaryDbAt(seconds);
                    double sectionDb = clamp(quietDb + energyDb,
                            -MAX_SECTION_EDIT_DB, MAX_SECTION_EDIT_DB);
                    double fizzDb = Math.max(-MAX_ARTIFACT_CUT_DB,
                            fizz.primaryDbAt(seconds));
                    double metallicDb = Math.max(-MAX_ARTIFACT_CUT_DB,
                            metallic.primaryDbAt(seconds));
                    double phaseyDb = Math.max(-MAX_ARTIFACT_CUT_DB,
                            phasey.primaryDbAt(seconds));
                    double artifactTotal = -fizzDb - metallicDb - phaseyDb;
                    if (artifactTotal > MAX_ARTIFACT_CUT_DB) {
                        double scale = MAX_ARTIFACT_CUT_DB / artifactTotal;
                        fizzDb *= scale;
                        metallicDb *= scale;
                        phaseyDb *= scale;
                        artifactTotal = MAX_ARTIFACT_CUT_DB;
                    }
                    double transientDb = Math.max(-MAX_TRANSIENT_CUT_DB,
                            transientControl.primaryDbAt(seconds));
                    double totalNominal = Math.abs(sectionDb) + artifactTotal
                            + Math.abs(transientDb);
                    if (totalNominal > MAX_TOTAL_NOMINAL_EDIT_DB) {
                        double scale = MAX_TOTAL_NOMINAL_EDIT_DB / totalNominal;
                        sectionDb *= scale;
                        fizzDb *= scale;
                        metallicDb *= scale;
                        phaseyDb *= scale;
                        transientDb *= scale;
                        totalNominal = MAX_TOTAL_NOMINAL_EDIT_DB;
                    }
                    stats.maximumNominalEditDb = Math.max(
                            stats.maximumNominalEditDb, totalNominal);

                    double sectionGain = dbGain(sectionDb);
                    double correctedLeft = left * sectionGain;
                    double correctedRight = right * sectionGain;
                    double fizzBandL = fizzLeft.process(left);
                    double fizzBandR = fizzRight.process(right);
                    double metallicBandL = metallicLeft.process(left);
                    double metallicBandR = metallicRight.process(right);
                    double phaseyBand = phaseySide.process(side);
                    correctedLeft += gainDelta(fizzDb) * fizzBandL
                            + gainDelta(metallicDb) * metallicBandL
                            + gainDelta(phaseyDb) * phaseyBand;
                    correctedRight += gainDelta(fizzDb) * fizzBandR
                            + gainDelta(metallicDb) * metallicBandR
                            - gainDelta(phaseyDb) * phaseyBand;
                    double transientGain = dbGain(transientDb);
                    correctedLeft *= transientGain;
                    correctedRight *= transientGain;

                    if (quietDb != 0.0) {
                        stats.sectionQuietBefore += left * left + right * right;
                        stats.sectionQuietAfter += correctedLeft * correctedLeft
                                + correctedRight * correctedRight;
                    }
                    if (energyDb != 0.0) {
                        stats.sectionEnergyBefore += left * left + right * right;
                        stats.sectionEnergyAfter += correctedLeft * correctedLeft
                                + correctedRight * correctedRight;
                    }
                    accumulateArtifact(stats, fizzDb, fizzBandL, fizzBandR);
                    accumulateArtifact(stats, metallicDb, metallicBandL,
                            metallicBandR);
                    if (phaseyDb < -1.0e-9) {
                        stats.artifactBefore += 2.0 * phaseyBand * phaseyBand;
                        double after = phaseyBand * dbGain(phaseyDb);
                        stats.artifactAfter += 2.0 * after * after;
                    }
                    if (transientDb < -1.0e-9) {
                        stats.transientPeakBefore = Math.max(
                                stats.transientPeakBefore,
                                Math.max(Math.abs(left), Math.abs(right)));
                        stats.transientPeakAfter = Math.max(
                                stats.transientPeakAfter,
                                Math.max(Math.abs(correctedLeft),
                                        Math.abs(correctedRight)));
                    }
                    writer.frame(correctedLeft, correctedRight);
                }
                int percent = (int) Math.min(94,
                        Math.round(94.0 * absolute
                                / Math.max(1L, reader.info.frames)));
                progress.update("Advanced mastering: rendering protected candidate", percent);
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("Advanced mastering rendering cancelled");
                }
            }
            writer.finish();
            raw.flush();
            fileOutput.getFD().sync();
            if (absolute != analysis.wav.frames) {
                throw new IOException("Advanced mastering frame accounting failed");
            }
        } catch (IOException error) {
            delete(output);
            throw error;
        }
        long expected = 44L + dataBytes;
        if (output.length() != expected) {
            long actual = output.length();
            delete(output);
            throw new IOException("Advanced mastering durable-length verification failed: expected "
                    + expected + " bytes, found " + actual);
        }
        try {
            WavRenderIntegrity.commit(output, destination, dataBytes, rate, 2, 32, true);
        } catch (IOException error) {
            WavRenderIntegrity.discard(output);
            throw error;
        }
        stats.finishBenefits();
        return stats;
    }

    private static EventControl control(AdvancedStereoModel.Result model,
                                        Mode mode,
                                        AdvancedStereoModel.Kind kind,
                                        double duration,
                                        double attack,
                                        double release) {
        return new EventControl(model, mode, kind, duration, attack, release);
    }

    private static void accumulateArtifact(Stats stats, double gainDb,
                                           double left, double right) {
        if (gainDb >= -1.0e-9) return;
        double before = left * left + right * right;
        double gain = dbGain(gainDb);
        double after = (left * gain) * (left * gain)
                + (right * gain) * (right * gain);
        stats.artifactBefore += before;
        stats.artifactAfter += after;
    }

    private static Result describe(AdvancedStereoModel.Result model,
                                   Mode mode,
                                   Stats stats,
                                   double duration) {
        Result out = new Result();
        out.mode = mode;
        out.correctionEvents = stats.events;
        out.maximumNominalEditDb = stats.maximumNominalEditDb;
        out.sectionLiftImprovementLu = stats.sectionLiftImprovementLu;
        out.artifactReductionDb = stats.artifactReductionDb;
        out.transientPeakReductionDb = stats.transientPeakReductionDb;
        double confidence = 0.0;
        double weighted = 0.0;
        int count = 0;
        for (AdvancedStereoModel.Event event : model.events) {
            if (!event.automatic
                    || !AdvancedStereoModel.kindInMode(event.kind, mode)) {
                continue;
            }
            confidence += event.confidence;
            weighted += event.confidence
                    * (Math.abs(event.primaryDb)
                    + Math.abs(event.secondaryDb))
                    * Math.max(0.006, event.durationSeconds());
            count++;
        }
        out.modelConfidence = count == 0 ? 0.0 : confidence / count;
        out.evidenceBenefit = clamp(weighted
                / Math.max(0.5, duration * 0.045), 0.0, 1.0);
        out.measuredTargetBenefit = stats.measuredTargetBenefit;
        out.evidenceSummary = String.format(Locale.US,
                "%d advanced authoritative-stereo event(s); confidence %.3f; evidence %.3f; measured targeted benefit %.3f; section-lift change +%.3f LU; artifact reduction %.3f dB; transient peak reduction %.3f dB; provenance not classified; no stem waveform used",
                out.correctionEvents, out.modelConfidence, out.evidenceBenefit,
                out.measuredTargetBenefit, out.sectionLiftImprovementLu,
                out.artifactReductionDb, out.transientPeakReductionDb);
        return out;
    }

    private static String validatePremaster(AnalysisResult source,
                                            AnalysisResult corrected,
                                            DeltaVerification delta) throws IOException {
        if (!corrected.wav.floatingPoint || corrected.wav.channels != 2
                || corrected.wav.sampleRate != source.wav.sampleRate
                || corrected.wav.frames != source.wav.frames) {
            throw new IOException("Advanced pre-master changed format or duration");
        }
        if (corrected.nonFiniteSamples != 0) {
            throw new IOException("Advanced pre-master introduced non-finite samples");
        }
        if (corrected.clippedSamples > source.clippedSamples) {
            throw new IOException(String.format(Locale.US,
                    "Advanced pre-master introduced %d additional full-scale sample(s); source already contained %d",
                    corrected.clippedSamples - source.clippedSamples,
                    source.clippedSamples));
        }
        if (corrected.internalZeroRuns > source.internalZeroRuns) {
            throw new IOException("Advanced pre-master introduced an exact-zero interruption");
        }
        if (!delta.protectedRegionsExact) {
            throw new IOException("Advanced edit leaked into the protected intro or ending");
        }
        if (delta.maximumAbsoluteDelta <= 1.0e-8
                || delta.editedRmsDeltaDbfs < -145.0) {
            throw new IOException("Advanced mastering candidate produced no measurable edit");
        }
        if (delta.sourceCorrelation < 0.9955) {
            throw new IOException("Advanced edit exceeded the source-correlation guardrail");
        }
        if (corrected.stereoCorrelation < source.stereoCorrelation - 0.018
                || corrected.lowBandCorrelation < source.lowBandCorrelation
                - 0.008) {
            throw new IOException("Advanced edit worsened stereo or low-band correlation");
        }
        if (source.lraLu - corrected.lraLu > 0.50) {
            throw new IOException("Advanced edit reduced LRA beyond 0.50 LU");
        }
        return String.format(Locale.US,
                "ADVANCED PREMASTER PASS: source correlation %.7f; inherited full-scale samples %d→%d (no increase); protected intro/tail %.2g/%.2g; duration, continuity, phase and LRA verified",
                delta.sourceCorrelation, source.clippedSamples,
                corrected.clippedSamples, delta.protectedIntroMaxDelta,
                delta.protectedEndingMaxDelta);
    }

    private static DeltaVerification verifyDelta(
            AudioAnalyzer.StreamProvider source,
            AudioAnalyzer.StreamProvider corrected,
            AnalysisResult analysis) throws IOException {
        DeltaVerification out = new DeltaVerification();
        long introEnd = Math.round(2.0 * analysis.wav.sampleRate);
        long endingStart = Math.max(0L, analysis.wav.frames - Math.round(
                Math.max(8.0, analysis.wav.durationSeconds * 0.04)
                        * analysis.wav.sampleRate));
        double sx = 0.0, sy = 0.0, sxx = 0.0, syy = 0.0, sxy = 0.0;
        double editedSquares = 0.0;
        long editedSamples = 0L;
        try (WavFile.Reader reference = WavFile.open(source.open());
             WavFile.Reader test = WavFile.open(corrected.open())) {
            float[][] a = new float[2][BUFFER_FRAMES];
            float[][] b = new float[2][BUFFER_FRAMES];
            long frame = 0L;
            int read;
            while ((read = reference.readFrames(a, BUFFER_FRAMES)) > 0) {
                int testRead = test.readFrames(b, read);
                if (testRead != read) {
                    throw new IOException("Advanced delta ended early");
                }
                for (int i = 0; i < read; i++, frame++) {
                    double al = finite(a[0][i]);
                    double ar = finite(a[1][i]);
                    double bl = finite(b[0][i]);
                    double br = finite(b[1][i]);
                    double dl = bl - al;
                    double dr = br - ar;
                    out.maximumAbsoluteDelta = Math.max(out.maximumAbsoluteDelta,
                            Math.max(Math.abs(dl), Math.abs(dr)));
                    double av = 0.5 * (al + ar);
                    double bv = 0.5 * (bl + br);
                    sx += av;
                    sy += bv;
                    sxx += av * av;
                    syy += bv * bv;
                    sxy += av * bv;
                    if (frame < introEnd) {
                        out.protectedIntroMaxDelta = Math.max(
                                out.protectedIntroMaxDelta,
                                Math.max(Math.abs(dl), Math.abs(dr)));
                    } else if (frame >= endingStart) {
                        out.protectedEndingMaxDelta = Math.max(
                                out.protectedEndingMaxDelta,
                                Math.max(Math.abs(dl), Math.abs(dr)));
                    } else {
                        editedSquares += dl * dl + dr * dr;
                        editedSamples += 2L;
                    }
                }
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("Advanced delta verification cancelled");
                }
            }
            if (frame != analysis.wav.frames || test.readFrames(b, 1) > 0) {
                throw new IOException("Advanced delta changed the sample count");
            }
            out.frames = frame;
        }
        double numerator = out.frames * sxy - sx * sy;
        double denominator = Math.sqrt(Math.max(0.0,
                out.frames * sxx - sx * sx)
                * Math.max(0.0, out.frames * syy - sy * sy));
        out.sourceCorrelation = denominator <= 1.0e-30 ? 0.0
                : clamp(numerator / denominator, -1.0, 1.0);
        out.editedRmsDeltaDbfs = editedSamples <= 0
                || editedSquares <= 0.0 ? -300.0
                : 10.0 * Math.log10(editedSquares / editedSamples);
        out.protectedRegionsExact = out.protectedIntroMaxDelta <= 1.0e-7
                && out.protectedEndingMaxDelta <= 1.0e-7;
        return out;
    }

    private static double gainDelta(double db) {
        return Math.abs(db) < 1.0e-12 ? 0.0 : dbGain(db) - 1.0;
    }

    private static double dbGain(double db) {
        return Math.pow(10.0, db / 20.0);
    }

    private static double energyChange(double before, double after) {
        return before <= 1.0e-20 || after <= 1.0e-20 ? 0.0
                : 10.0 * Math.log10(after / before);
    }

    private static double reductionDb(double before, double after) {
        return before <= 1.0e-20 || after <= 1.0e-20 ? 0.0
                : Math.max(0.0, 10.0 * Math.log10(before / after));
    }

    private static void writeFloatHeader(OutputStream out, int sampleRate,
                                         int channels,
                                         long dataBytes) throws IOException {
        int blockAlign = channels * 4;
        LittleEndian.writeAscii(out, "RIFF");
        LittleEndian.writeU32(out, 36L + dataBytes);
        LittleEndian.writeAscii(out, "WAVE");
        LittleEndian.writeAscii(out, "fmt ");
        LittleEndian.writeU32(out, 16);
        LittleEndian.writeU16(out, 3);
        LittleEndian.writeU16(out, channels);
        LittleEndian.writeU32(out, sampleRate);
        LittleEndian.writeU32(out, (long) sampleRate * blockAlign);
        LittleEndian.writeU16(out, blockAlign);
        LittleEndian.writeU16(out, 32);
        LittleEndian.writeAscii(out, "data");
        LittleEndian.writeU32(out, dataBytes);
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static void delete(File file) {
        if (file != null && file.exists() && !file.delete()) {
            file.deleteOnExit();
        }
    }

    private static final class Stats {
        int events;
        double maximumNominalEditDb;
        double sectionQuietBefore;
        double sectionQuietAfter;
        double sectionEnergyBefore;
        double sectionEnergyAfter;
        double artifactBefore;
        double artifactAfter;
        double transientPeakBefore;
        double transientPeakAfter;
        double sectionLiftImprovementLu;
        double artifactReductionDb;
        double transientPeakReductionDb;
        double measuredTargetBenefit;

        void finishBenefits() {
            double quietChange = energyChange(sectionQuietBefore,
                    sectionQuietAfter);
            double loudChange = energyChange(sectionEnergyBefore,
                    sectionEnergyAfter);
            if (sectionQuietBefore > 1.0e-20
                    && sectionEnergyBefore > 1.0e-20) {
                sectionLiftImprovementLu = Math.max(0.0,
                        loudChange - quietChange);
            } else if (sectionEnergyBefore > 1.0e-20) {
                sectionLiftImprovementLu = Math.max(0.0, loudChange);
            } else if (sectionQuietBefore > 1.0e-20) {
                sectionLiftImprovementLu = Math.max(0.0, -quietChange);
            }
            artifactReductionDb = reductionDb(artifactBefore, artifactAfter);
            transientPeakReductionDb = transientPeakBefore <= 1.0e-20
                    || transientPeakAfter <= 1.0e-20 ? 0.0
                    : Math.max(0.0, 20.0 * Math.log10(
                    transientPeakBefore / transientPeakAfter));
            double total = 0.0;
            int count = 0;
            if (sectionQuietBefore > 1.0e-20
                    || sectionEnergyBefore > 1.0e-20) {
                total += clamp(sectionLiftImprovementLu / 0.15,
                        0.0, 1.0);
                count++;
            }
            if (artifactBefore > 1.0e-20) {
                total += clamp(artifactReductionDb / 0.08,
                        0.0, 1.0);
                count++;
            }
            if (transientPeakBefore > 1.0e-20) {
                total += clamp(transientPeakReductionDb / 0.06,
                        0.0, 1.0);
                count++;
            }
            measuredTargetBenefit = count == 0 ? 0.0 : total / count;
        }
    }

    private static final class EventControl {
        private final List<AdvancedStereoModel.Event> events = new ArrayList<>();
        private final double protectedEndingStart;
        private final double attack;
        private final double release;
        private int cursor;

        EventControl(AdvancedStereoModel.Result model, Mode mode,
                     AdvancedStereoModel.Kind kind, double duration,
                     double attack, double release) {
            this.protectedEndingStart = Math.max(2.0,
                    duration - Math.max(8.0, duration * 0.04));
            this.attack = attack;
            this.release = release;
            for (AdvancedStereoModel.Event event : model.events) {
                if (event.automatic && event.kind == kind
                        && AdvancedStereoModel.kindInMode(kind, mode)) {
                    events.add(event);
                }
            }
            events.sort(Comparator.comparingDouble(
                    event -> event.startSeconds));
        }

        int count() {
            return events.size();
        }

        double primaryDbAt(double seconds) {
            AdvancedStereoModel.Event event = eventAt(seconds);
            return event == null ? 0.0
                    : event.primaryDb * envelope(event, seconds);
        }

        private AdvancedStereoModel.Event eventAt(double seconds) {
            if (seconds < 2.0 || seconds >= protectedEndingStart) return null;
            while (cursor < events.size()
                    && seconds > events.get(cursor).endSeconds + release) {
                cursor++;
            }
            if (cursor >= events.size()) return null;
            AdvancedStereoModel.Event event = events.get(cursor);
            if (seconds < event.startSeconds
                    || seconds > event.endSeconds + release) return null;
            return event;
        }

        private double envelope(AdvancedStereoModel.Event event,
                                double seconds) {
            if (seconds <= event.endSeconds) {
                return smooth(Math.min(1.0,
                        (seconds - event.startSeconds)
                                / Math.max(0.0002, attack)));
            }
            return smooth(1.0 - Math.min(1.0,
                    (seconds - event.endSeconds)
                            / Math.max(0.0002, release)));
        }

        private static double smooth(double value) {
            double x = clamp(value, 0.0, 1.0);
            return x * x * (3.0 - 2.0 * x);
        }
    }

    private static final class BandPass {
        private final Biquad highA;
        private final Biquad highB;
        private final Biquad lowA;
        private final Biquad lowB;

        BandPass(double rate, double low, double high) {
            highA = Biquad.highPass(rate, low);
            highB = Biquad.highPass(rate, low);
            lowA = Biquad.lowPass(rate, high);
            lowB = Biquad.lowPass(rate, high);
        }

        double process(double sample) {
            return lowB.process(lowA.process(
                    highB.process(highA.process(sample))));
        }
    }

    private static final class FloatWriter {
        private final OutputStream output;
        private final byte[] buffer = new byte[256 * 1024];
        private int position;

        FloatWriter(OutputStream output) {
            this.output = output;
        }

        void frame(double left, double right) throws IOException {
            sample(left);
            sample(right);
        }

        private void sample(double value) throws IOException {
            if (position + 4 > buffer.length) flush();
            int bits = Float.floatToIntBits((float) finite(value));
            buffer[position++] = (byte) (bits & 0xff);
            buffer[position++] = (byte) ((bits >>> 8) & 0xff);
            buffer[position++] = (byte) ((bits >>> 16) & 0xff);
            buffer[position++] = (byte) ((bits >>> 24) & 0xff);
        }

        void finish() throws IOException {
            flush();
        }

        private void flush() throws IOException {
            if (position > 0) output.write(buffer, 0, position);
            position = 0;
        }
    }
}
