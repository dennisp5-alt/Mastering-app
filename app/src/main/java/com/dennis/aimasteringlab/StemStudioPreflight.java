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
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Technical studio-preparation pass that inspects each supplied stem before it
 * is summed. Only sparse, sample-provable repairs and persistent narrow tones
 * can alter audio. Broadband noise remains diagnostic to avoid denoising
 * texture, breaths, cymbals or reverb into an artificial sound.
 */
final class StemStudioPreflight {
    enum Kind {
        ISOLATED_CLICK,
        EXACT_ZERO_DROPOUT,
        ISOLATED_CLIP_RUN
    }

    static final class Event {
        Kind kind;
        long startFrame;
        long endFrame;
        int channelMask;
        double confidence;
        final double[] before = new double[2];
        final double[] after = new double[2];
        final double[] replacement = new double[2];
        String evidence;

        long frames() {
            return Math.max(0L, endFrame - startFrame + 1L);
        }
    }

    static final class ToneCorrection {
        String kind;
        double frequencyHz;
        double q;
        double attenuationDb;
        double prominenceDb;
        double levelDbfs;
        int stableWindows;
        int measuredWindows;
        boolean automatic;
        String evidence;
    }

    static final class StemResult {
        String path;
        String role;
        int channels;
        long frames;
        String inputSha256;
        String outputSha256;
        final List<Event> events = new ArrayList<>();
        final List<ToneCorrection> tones = new ArrayList<>();
        final List<String> diagnostics = new ArrayList<>();
        int isolatedClicks;
        int mouthClicks;
        int exactZeroDropouts;
        int isolatedClipRuns;
        int protectedTransientCandidates;
        boolean continuousCrackleDiagnostic;
        boolean broadbandNoiseDiagnostic;
        double highBandNoiseFloorDbfs = Double.NaN;
        boolean repairApplied;
        long repairedEventFrames;
        double changedSamplePercent;
        double sourceCorrelation = 1.0;
        double maximumAbsoluteDelta;
        double inputPeak;
        double outputPeak;
        long inputClippedSamples;
        long outputClippedSamples;
        String status = "No per-stem repair cleared every gate";

        int automaticToneCount() {
            int count = 0;
            for (ToneCorrection tone : tones) if (tone.automatic) count++;
            return count;
        }

        boolean hasAutomaticWork() {
            return !events.isEmpty() || automaticToneCount() > 0;
        }

        String compactSummary() {
            return String.format(Locale.US,
                    "%s • %s • clicks %d%s • dropouts %d • declips %d • narrow tones %d • repaired %s • correlation %.8f",
                    path, role, isolatedClicks,
                    mouthClicks > 0 ? " (mouth-like " + mouthClicks + ")" : "",
                    exactZeroDropouts, isolatedClipRuns, automaticToneCount(),
                    repairApplied ? "yes" : "no", sourceCorrelation);
        }
    }

    static final class Result {
        String model = "Stem Lab v0.4 guarded per-stem studio preparation";
        String method = "CRC-bound per-stem continuity/declip analysis, stable multi-window mains-family and ultrasonic-carrier detection, verified 32-bit-float repair archive";
        File effectiveArchive;
        String effectiveArchiveSha256;
        boolean repairEnabled;
        boolean preparedArchiveUsed;
        int stemsInspected;
        int stemsRepaired;
        int isolatedClicks;
        int mouthClicks;
        int exactZeroDropouts;
        int isolatedClipRuns;
        int humStems;
        int whineStems;
        int broadbandNoiseDiagnostics;
        long repairedEventFrames;
        double worstSourceCorrelation = 1.0;
        double maximumAbsoluteDelta;
        final List<StemResult> stems = new ArrayList<>();
        final List<String> safetyNotes = new ArrayList<>();
        String status;

        String compactSummary() {
            return String.format(Locale.US,
                    "Studio preflight %s • %d/%d stems repaired • clicks %d • dropouts %d • declips %d • hum %d • whine %d • noise diagnostics %d • worst correlation %.8f",
                    preparedArchiveUsed ? "APPLIED" : "NO-CHANGE",
                    stemsRepaired, stemsInspected, isolatedClicks,
                    exactZeroDropouts, isolatedClipRuns, humStems, whineStems,
                    broadbandNoiseDiagnostics, worstSourceCorrelation);
        }
    }

    private static final int BUFFER_FRAMES = 4096;
    private static final int MAX_AUTOMATIC_MICRO_EVENTS = 48;
    private static final int MAX_ZERO_FRAMES = 960; // 20 ms at 48 kHz
    private static final int MAX_CLIP_RUN_FRAMES = 32;
    private static final double MAX_MICRO_EVENT_COVERAGE = 0.0025;
    private static final double MIN_VERIFIED_CORRELATION = 0.997;
    private static final int SPECTRUM_SIZE = 16384;
    private static final int SPECTRUM_WINDOWS = 8;

    private StemStudioPreflight() {}

    static Result run(File archive, File workDirectory, boolean allowRepair,
                      AudioAnalyzer.Progress progress) throws IOException {
        if (archive == null || !archive.isFile()) {
            throw new IOException("Stem studio preparation requires an intact ZIP");
        }
        if (!workDirectory.exists() && !workDirectory.mkdirs()) {
            throw new IOException("Could not create the Stem Lab studio-preparation cache");
        }
        Result result = new Result();
        result.repairEnabled = allowRepair;
        List<StemInventory.Item> inventory = StemInventory.inspect(archive, progress);
        Map<String, StemInventory.Item> expected = new HashMap<>();
        for (StemInventory.Item item : inventory) {
            if (item.wav && item.wavInfo != null && item.error == null
                    && item.crcVerified) expected.put(item.path, item);
        }
        File prepared = new File(workDirectory, "studio-prepared-stems.zip");
        int entryIndex = 0;
        try (ZipFile source = new ZipFile(archive);
             ZipOutputStream output = new ZipOutputStream(new BufferedOutputStream(
                     new FileOutputStream(prepared), 256 * 1024))) {
            output.setLevel(1);
            Enumeration<? extends ZipEntry> entries = source.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                ZipEntry destination = new ZipEntry(entry.getName());
                destination.setTime(entry.getTime());
                output.putNextEntry(destination);
                StemInventory.Item item = expected.get(entry.getName());
                if (item == null || entry.isDirectory()) {
                    if (!entry.isDirectory()) {
                        try (InputStream input = source.getInputStream(entry)) {
                            copy(input, output);
                        }
                    }
                    output.closeEntry();
                    entryIndex++;
                    continue;
                }

                result.stemsInspected++;
                File extracted = new File(workDirectory,
                        String.format(Locale.US, "studio-source-%02d.wav", entryIndex));
                File repaired = new File(workDirectory,
                        String.format(Locale.US, "studio-repaired-%02d.wav", entryIndex));
                StemResult stem = null;
                try {
                    try (InputStream input = source.getInputStream(entry);
                         OutputStream file = new BufferedOutputStream(
                                 new FileOutputStream(extracted), 256 * 1024)) {
                        copy(input, file);
                    }
                    stem = analyze(extracted, item, progress);
                    if (allowRepair && stem.hasAutomaticWork()) {
                        render(extracted, repaired, item, stem, progress);
                        verify(extracted, repaired, item, stem);
                    }
                    File chosen = stem.repairApplied ? repaired : extracted;
                    try (InputStream input = new BufferedInputStream(
                            new FileInputStream(chosen), 256 * 1024)) {
                        copy(input, output);
                    }
                    collect(result, stem);
                    result.stems.add(stem);
                } catch (Exception stemError) {
                    if (stem == null) {
                        stem = new StemResult();
                        stem.path = item.path;
                        stem.role = StemPremixBuilder.role(item.path);
                        stem.channels = item.wavInfo.channels;
                        stem.frames = item.wavInfo.frames;
                    }
                    stem.repairApplied = false;
                    stem.status = "Untouched fallback: " + clean(stemError);
                    stem.diagnostics.add("The original CRC-verified stem bytes were retained after studio-preparation rejection.");
                    result.stems.add(stem);
                    try (InputStream input = source.getInputStream(entry)) {
                        copy(input, output);
                    }
                } finally {
                    delete(extracted);
                    delete(repaired);
                }
                output.closeEntry();
                entryIndex++;
                progress.update("Studio preflight: " + result.stemsInspected
                        + " stem(s) inspected", -1);
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("Per-stem studio preparation cancelled");
                }
            }
        } catch (IOException error) {
            delete(prepared);
            throw error;
        }

        if (allowRepair && result.stemsRepaired > 0) {
            // Re-open the derived archive through the same CRC/format guard used
            // for a user-selected archive before it is allowed to feed premixing.
            List<StemInventory.Item> verified = StemInventory.inspect(prepared, progress);
            int verifiedWavs = 0;
            for (StemInventory.Item item : verified) {
                if (!item.wav) continue;
                if (item.error != null || !item.crcVerified || item.wavInfo == null
                        || item.wavInfo.sampleRate != 48000
                        || (item.wavInfo.channels != 1 && item.wavInfo.channels != 2)) {
                    delete(prepared);
                    throw new IOException("Prepared stem archive failed CRC or format verification");
                }
                verifiedWavs++;
            }
            if (verifiedWavs != result.stemsInspected) {
                delete(prepared);
                throw new IOException("Prepared stem archive changed the WAV inventory");
            }
            result.effectiveArchive = prepared;
            result.effectiveArchiveSha256 = Hashing.sha256(prepared);
            result.preparedArchiveUsed = true;
            result.status = result.stemsRepaired + " stem(s) received verified technical repairs before premixing";
        } else {
            delete(prepared);
            result.effectiveArchive = archive;
            result.effectiveArchiveSha256 = Hashing.sha256(archive);
            result.preparedArchiveUsed = false;
            result.status = allowRepair
                    ? "No stem defect cleared every automatic repair gate; original archive retained byte-for-byte"
                    : "Original + Stems mode: per-stem findings are diagnostic and original archive bytes remain authoritative";
        }
        result.safetyNotes.add("Only isolated clicks, short exact-zero dropouts, isolated full-scale clip runs, a persistent three-harmonic mains family, or a stable ultrasonic carrier can change samples.");
        result.safetyNotes.add("Broadband hiss/crackle is reported but never broadly denoised; breaths, cymbals, reverb, musical transients, pitch, timing, panning and stem fader balance remain untouched.");
        result.safetyNotes.add("Bass, drums, synth, strings, brass and effects stems cannot receive automatic tone notches; ambiguous stable tones remain diagnostic.");
        result.safetyNotes.add("Every modified stem must retain sample rate, channels and frame count, introduce no invalid sample or new clipping, and correlate at least 0.997 with its source.");
        return result;
    }

    private static StemResult analyze(File file, StemInventory.Item item,
                                      AudioAnalyzer.Progress progress)
            throws IOException {
        StemResult out = new StemResult();
        out.path = item.path;
        out.role = StemPremixBuilder.role(item.path);
        out.channels = item.wavInfo.channels;
        out.frames = item.wavInfo.frames;
        out.inputSha256 = Hashing.sha256(file);
        out.inputClippedSamples = item.clippedSamples;
        scanMicroEvents(file, item, out, progress);
        analyzePersistentSpectrum(file, item, out, progress);
        pruneAndCount(out);
        if (out.hasAutomaticWork()) {
            out.status = "High-confidence per-stem technical repair available; rendered verification required";
        }
        return out;
    }

    private static void scanMicroEvents(File file, StemInventory.Item item,
                                        StemResult out,
                                        AudioAnalyzer.Progress progress)
            throws IOException {
        int channels = item.wavInfo.channels;
        Rolling[] rolling = new Rolling[channels];
        ClipRun[] clips = new ClipRun[channels];
        for (int channel = 0; channel < channels; channel++) {
            rolling[channel] = new Rolling();
            clips[channel] = new ClipRun();
        }
        double[] previous2 = new double[channels];
        double[] previous1 = new double[channels];
        boolean have1 = false;
        boolean have2 = false;
        long absolute = 0L;
        long zeroStart = -1L;
        int zeroFrames = 0;
        double[] zeroBefore = new double[2];
        double zeroPreRms = 0.0;
        long suppressClicksUntil = -1L;
        long introEnd = Math.round(item.wavInfo.sampleRate * 2.0);
        long tailStart = Math.max(introEnd, item.wavInfo.frames - Math.round(
                item.wavInfo.sampleRate * Math.max(8.0,
                        item.wavInfo.durationSeconds * 0.04)));
        double clipThreshold = item.wavInfo.floatingPoint ? 0.9999
                : 1.0 - 1.0 / Math.pow(2.0,
                Math.max(2, item.wavInfo.validBitsPerSample - 1));
        float[][] samples = new float[channels][BUFFER_FRAMES];
        try (WavFile.Reader reader = WavFile.open(new FileInputStream(file))) {
            int read;
            while ((read = reader.readFrames(samples, BUFFER_FRAMES)) > 0) {
                for (int frame = 0; frame < read; frame++, absolute++) {
                    double[] current = new double[channels];
                    boolean exactZero = true;
                    double currentEnergy = 0.0;
                    for (int channel = 0; channel < channels; channel++) {
                        current[channel] = finite(samples[channel][frame]);
                        exactZero &= current[channel] == 0.0;
                        currentEnergy += current[channel] * current[channel];
                        handleClipSample(out, clips[channel], channel,
                                current[channel], have1 ? previous1[channel] : 0.0,
                                absolute, clipThreshold, introEnd, tailStart);
                    }

                    if (exactZero) {
                        if (zeroStart < 0L) {
                            zeroStart = absolute;
                            zeroFrames = 0;
                            zeroPreRms = combinedRms(rolling);
                            for (int channel = 0; channel < channels; channel++) {
                                zeroBefore[channel] = have1 ? previous1[channel] : 0.0;
                            }
                        }
                        zeroFrames++;
                    } else if (zeroStart >= 0L) {
                        if (zeroFrames >= 2 && zeroFrames <= MAX_ZERO_FRAMES
                                && zeroStart >= introEnd && absolute < tailStart
                                && zeroPreRms >= 0.0025
                                && Math.max(Math.sqrt(currentEnergy / channels),
                                vectorRms(zeroBefore, channels)) >= 0.0025) {
                            Event event = new Event();
                            event.kind = Kind.EXACT_ZERO_DROPOUT;
                            event.startFrame = zeroStart;
                            event.endFrame = zeroStart + zeroFrames - 1L;
                            event.channelMask = (1 << channels) - 1;
                            for (int channel = 0; channel < channels; channel++) {
                                event.before[channel] = zeroBefore[channel];
                                event.after[channel] = current[channel];
                            }
                            event.confidence = clamp(0.94
                                    + 0.012 * Math.log1p(zeroFrames), 0.94, 0.999);
                            event.evidence = "all-channel exact-zero run of "
                                    + zeroFrames + " frames bracketed by active stem audio";
                            out.events.add(event);
                            suppressClicksUntil = absolute + 3L;
                        } else if (zeroFrames > MAX_ZERO_FRAMES) {
                            out.diagnostics.add("A zero region longer than 20 ms was retained as possible intentional silence.");
                        }
                        zeroStart = -1L;
                        zeroFrames = 0;
                    }

                    if (!exactZero && have2 && absolute > suppressClicksUntil) {
                        int mask = 0;
                        double maximumRatio = 0.0;
                        double[] replacements = new double[channels];
                        for (int channel = 0; channel < channels; channel++) {
                            double prediction = 0.5
                                    * (previous2[channel] + current[channel]);
                            double residual = Math.abs(previous1[channel] - prediction);
                            double rms = Math.sqrt(Math.max(1.0e-12,
                                    rolling[channel].energy));
                            double threshold = Math.max(0.055,
                                    Math.max(rolling[channel].residual * 24.0,
                                            rms * 1.70));
                            double neighbourMotion = Math.abs(
                                    current[channel] - previous2[channel]);
                            double isolation = residual / Math.max(1.0e-7,
                                    0.25 * neighbourMotion
                                            + 3.0 * rolling[channel].residual);
                            double neighbourPeak = Math.max(
                                    Math.abs(previous2[channel]),
                                    Math.abs(current[channel]));
                            double dominance = (Math.abs(previous1[channel]) + 0.002)
                                    / (neighbourPeak + 0.002);
                            if (residual > threshold && isolation >= 5.5
                                    && dominance >= 3.0) {
                                mask |= 1 << channel;
                                replacements[channel] = prediction;
                                maximumRatio = Math.max(maximumRatio,
                                        residual / Math.max(1.0e-8, threshold));
                            }
                            rolling[channel].updateResidual(residual);
                        }
                        long centre = absolute - 1L;
                        if (mask != 0 && centre >= introEnd && centre < tailStart
                                && !overlaps(out.events, centre, centre, 2L)) {
                            boolean protectedRole = "drums".equals(out.role)
                                    || "bass".equals(out.role);
                            boolean stereoLike = channels == 2 && mask == 3;
                            if (protectedRole || (stereoLike && maximumRatio < 1.45)) {
                                out.protectedTransientCandidates++;
                            } else {
                                Event event = new Event();
                                event.kind = Kind.ISOLATED_CLICK;
                                event.startFrame = centre;
                                event.endFrame = centre;
                                event.channelMask = mask;
                                for (int channel = 0; channel < channels; channel++) {
                                    event.replacement[channel] = replacements[channel];
                                }
                                event.confidence = clamp(0.90
                                        + 0.05 * Math.log1p(maximumRatio), 0.90, 0.999);
                                event.evidence = "one-sample prediction residual exceeded the local derivative and transient-protection floors";
                                out.events.add(event);
                            }
                        }
                    }

                    if (!exactZero) {
                        for (int channel = 0; channel < channels; channel++) {
                            rolling[channel].updateEnergy(current[channel]);
                        }
                    }
                    for (int channel = 0; channel < channels; channel++) {
                        previous2[channel] = previous1[channel];
                        previous1[channel] = current[channel];
                    }
                    have2 = have1;
                    have1 = true;
                }
                int percent = (int) Math.min(92,
                        Math.round(92.0 * absolute
                                / Math.max(1L, item.wavInfo.frames)));
                progress.update("Studio preflight: continuity/declip "
                        + item.path, percent);
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("Per-stem continuity analysis cancelled");
                }
            }
        }
        for (int channel = 0; channel < channels; channel++) {
            if (clips[channel].active) {
                clips[channel].rejectedAtEnd = true;
                out.diagnostics.add("A clip run at the final boundary was retained because post-event waveform evidence was unavailable.");
            }
        }
    }

    private static void handleClipSample(StemResult out, ClipRun run,
                                         int channel, double value,
                                         double previous, long frame,
                                         double threshold, long introEnd,
                                         long tailStart) {
        boolean clipped = Math.abs(value) >= threshold;
        if (clipped) {
            int sign = value < 0.0 ? -1 : 1;
            if (!run.active) {
                run.active = true;
                run.start = frame;
                run.frames = 0;
                run.sign = sign;
                run.before = previous;
                run.minimum = value;
                run.maximum = value;
                run.signConsistent = true;
            }
            run.frames++;
            run.signConsistent &= sign == run.sign;
            run.minimum = Math.min(run.minimum, value);
            run.maximum = Math.max(run.maximum, value);
            return;
        }
        if (!run.active) return;
        long end = frame - 1L;
        boolean plateau = run.maximum - run.minimum <= 2.5e-4;
        boolean sameSide = Math.signum(run.before) == run.sign
                && Math.signum(value) == run.sign;
        if (run.frames >= 2 && run.frames <= MAX_CLIP_RUN_FRAMES
                && run.start >= introEnd && end < tailStart
                && run.signConsistent && plateau && sameSide
                && Math.abs(run.before) >= 0.05 && Math.abs(value) >= 0.05) {
            Event event = new Event();
            event.kind = Kind.ISOLATED_CLIP_RUN;
            event.startFrame = run.start;
            event.endFrame = end;
            event.channelMask = 1 << channel;
            event.before[channel] = run.before;
            event.after[channel] = value;
            event.confidence = clamp(0.91 + 0.02 * Math.log1p(run.frames),
                    0.91, 0.995);
            event.evidence = "isolated " + run.frames
                    + "-frame full-scale flat top with same-polarity waveform evidence on both sides";
            out.events.add(event);
        } else if (run.frames > MAX_CLIP_RUN_FRAMES) {
            out.diagnostics.add("A clip run longer than 32 frames was retained; widespread or intentionally limited audio is not automatically reconstructed.");
        }
        run.reset();
    }

    private static void analyzePersistentSpectrum(File file,
                                                  StemInventory.Item item,
                                                  StemResult out,
                                                  AudioAnalyzer.Progress progress)
            throws IOException {
        List<Spectrum> spectra = spectra(file, item, progress);
        if (spectra.size() < 5) {
            out.diagnostics.add("The stem was too short or quiet for stable persistent-tone analysis.");
            return;
        }
        HumEvidence hum50 = humEvidence(spectra, 50.0);
        HumEvidence hum60 = humEvidence(spectra, 60.0);
        HumEvidence hum = hum50.components.size() > hum60.components.size()
                ? hum50 : hum60.components.size() > hum50.components.size()
                ? hum60 : hum50.meanProminence >= hum60.meanProminence
                ? hum50 : hum60;
        boolean safeToneRole = safeToneRole(out.role);
        if (hum.components.size() >= 3) {
            boolean automatic = safeToneRole;
            for (ToneEvidence component : hum.components) {
                ToneCorrection tone = new ToneCorrection();
                tone.kind = "MAINS_HUM_HARMONIC";
                tone.frequencyHz = component.frequency;
                tone.q = 35.0;
                tone.attenuationDb = -clamp(2.5
                        + 0.22 * (component.prominence - 12.0), 2.5, 6.0);
                tone.prominenceDb = component.prominence;
                tone.levelDbfs = component.level;
                tone.stableWindows = component.stable;
                tone.measuredWindows = spectra.size();
                tone.automatic = automatic;
                tone.evidence = String.format(Locale.US,
                        "%.0f Hz mains family: %d harmonics were independently stable; this component persisted in %d/%d windows at %.2f dB prominence",
                        hum.baseFrequency, hum.components.size(),
                        component.stable, spectra.size(), component.prominence);
                out.tones.add(tone);
            }
            if (!automatic) {
                out.diagnostics.add("A persistent mains-harmonic family was detected but retained because the stem role could contain intentional low-frequency harmonics.");
            }
        }

        ToneEvidence whine = whineEvidence(spectra);
        if (whine != null) {
            ToneCorrection tone = new ToneCorrection();
            tone.kind = "ULTRASONIC_CARRIER";
            tone.frequencyHz = whine.frequency;
            tone.q = 80.0;
            tone.attenuationDb = -clamp(3.0
                    + 0.18 * (whine.prominence - 18.0), 3.0, 7.0);
            tone.prominenceDb = whine.prominence;
            tone.levelDbfs = whine.level;
            tone.stableWindows = whine.stable;
            tone.measuredWindows = spectra.size();
            tone.automatic = safeToneRole && whine.frequency >= 14500.0;
            tone.evidence = String.format(Locale.US,
                    "narrow %.1f Hz carrier remained within 18 Hz in %d/%d distributed windows; median prominence %.2f dB",
                    whine.frequency, whine.stable, spectra.size(),
                    whine.prominence);
            out.tones.add(tone);
            if (!tone.automatic) {
                out.diagnostics.add("A stable narrow high-frequency tone was retained because its frequency or stem role could be musical.");
            }
        }

        double[] floors = new double[spectra.size()];
        for (int i = 0; i < floors.length; i++) {
            floors[i] = spectra.get(i).medianBand(12000.0, 20500.0);
        }
        out.highBandNoiseFloorDbfs = percentile(floors, 0.50);
        double lowReference = 0.0;
        for (Spectrum spectrum : spectra) {
            lowReference += spectrum.medianBand(1200.0, 7000.0);
        }
        lowReference /= spectra.size();
        out.broadbandNoiseDiagnostic = out.highBandNoiseFloorDbfs > -79.0
                && out.highBandNoiseFloorDbfs > lowReference - 24.0;
        if (out.broadbandNoiseDiagnostic) {
            out.diagnostics.add(String.format(Locale.US,
                    "Persistent broadband high-frequency floor measured %.2f dBFS/bin; diagnostic only because broad denoising could damage air, consonants, cymbals or reverb.",
                    out.highBandNoiseFloorDbfs));
        }
    }

    private static List<Spectrum> spectra(File file, StemInventory.Item item,
                                          AudioAnalyzer.Progress progress)
            throws IOException {
        long maximumStart = Math.max(0L, item.wavInfo.frames - SPECTRUM_SIZE);
        long usableStart = Math.min(maximumStart,
                Math.round(item.wavInfo.sampleRate * 2.0));
        long preferredTail = Math.round(item.wavInfo.sampleRate
                * Math.max(8.0, item.wavInfo.durationSeconds * 0.04));
        long usableLastStart = Math.max(usableStart,
                Math.min(maximumStart, item.wavInfo.frames
                        - preferredTail - SPECTRUM_SIZE));
        long span = Math.max(0L, usableLastStart - usableStart);
        int windowCount = span <= 0L ? 1 : Math.min(SPECTRUM_WINDOWS,
                1 + (int) (span / Math.max(1, SPECTRUM_SIZE / 2)));
        long[] starts = new long[windowCount];
        for (int i = 0; i < starts.length; i++) {
            starts[i] = starts.length == 1 ? usableStart
                    : usableStart + Math.round(span * i
                    / (double) (starts.length - 1));
        }
        double[][] windows = new double[starts.length][SPECTRUM_SIZE];
        int[] filled = new int[starts.length];
        float[][] samples = new float[item.wavInfo.channels][BUFFER_FRAMES];
        long absolute = 0L;
        try (WavFile.Reader reader = WavFile.open(new FileInputStream(file))) {
            int read;
            while ((read = reader.readFrames(samples, BUFFER_FRAMES)) > 0) {
                for (int frame = 0; frame < read; frame++, absolute++) {
                    double mono = finite(samples[0][frame]);
                    if (item.wavInfo.channels == 2) {
                        mono = 0.5 * (mono + finite(samples[1][frame]));
                    }
                    for (int window = 0; window < starts.length; window++) {
                        long offset = absolute - starts[window];
                        if (offset >= 0 && offset < SPECTRUM_SIZE) {
                            windows[window][(int) offset] = mono;
                            filled[window]++;
                        }
                    }
                }
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("Per-stem spectral analysis cancelled");
                }
            }
        }
        ArrayList<Spectrum> output = new ArrayList<>();
        for (int i = 0; i < windows.length; i++) {
            if (filled[i] != SPECTRUM_SIZE) continue;
            double energy = 0.0;
            for (double value : windows[i]) energy += value * value;
            double rms = Math.sqrt(energy / SPECTRUM_SIZE);
            if (AudioAnalyzer.toDb(rms) < -72.0) continue;
            output.add(Spectrum.create(windows[i], item.wavInfo.sampleRate));
            progress.update("Studio preflight: persistent tones " + item.path,
                    (int) Math.round(100.0 * output.size() / windows.length));
        }
        return output;
    }

    private static HumEvidence humEvidence(List<Spectrum> spectra,
                                            double base) {
        HumEvidence out = new HumEvidence();
        out.baseFrequency = base;
        double prominenceSum = 0.0;
        for (int harmonic = 1; harmonic <= 5; harmonic++) {
            double frequency = base * harmonic;
            ToneEvidence evidence = stableTone(spectra, frequency,
                    10.0, 12.0, -70.0);
            if (evidence != null) {
                out.components.add(evidence);
                prominenceSum += evidence.prominence;
            }
        }
        out.meanProminence = out.components.isEmpty() ? 0.0
                : prominenceSum / out.components.size();
        return out;
    }

    private static ToneEvidence stableTone(List<Spectrum> spectra,
                                           double frequency,
                                           double perWindowProminence,
                                           double medianProminence,
                                           double minimumLevel) {
        ArrayList<Double> prominences = new ArrayList<>();
        ArrayList<Double> levels = new ArrayList<>();
        int stable = 0;
        for (Spectrum spectrum : spectra) {
            double level = spectrum.peakNear(frequency, 2);
            double floor = spectrum.localFloor(frequency, 6, 16);
            double prominence = level - floor;
            prominences.add(prominence);
            levels.add(level);
            if (prominence >= perWindowProminence && level >= minimumLevel) {
                stable++;
            }
        }
        int required = Math.max(4, (int) Math.ceil(spectra.size() * 0.70));
        double prominence = percentile(prominences, 0.50);
        double level = percentile(levels, 0.50);
        if (stable < required || prominence < medianProminence
                || level < minimumLevel) return null;
        ToneEvidence out = new ToneEvidence();
        out.frequency = frequency;
        out.prominence = prominence;
        out.level = level;
        out.stable = stable;
        return out;
    }

    private static ToneEvidence whineEvidence(List<Spectrum> spectra) {
        ArrayList<Peak> peaks = new ArrayList<>();
        for (Spectrum spectrum : spectra) peaks.add(spectrum.bestNarrowPeak(
                12000.0, 22000.0));
        Peak best = null;
        int bestCount = 0;
        for (Peak candidate : peaks) {
            if (candidate == null) continue;
            int count = 0;
            for (Peak peak : peaks) {
                if (peak != null && Math.abs(peak.frequency
                        - candidate.frequency) <= 18.0) count++;
            }
            if (count > bestCount || (count == bestCount && best != null
                    && candidate.prominence > best.prominence)) {
                best = candidate;
                bestCount = count;
            }
        }
        int required = Math.max(5, (int) Math.ceil(spectra.size() * 0.75));
        if (best == null || bestCount < required) return null;
        ArrayList<Double> frequencies = new ArrayList<>();
        ArrayList<Double> prominences = new ArrayList<>();
        ArrayList<Double> levels = new ArrayList<>();
        for (Peak peak : peaks) {
            if (peak == null || Math.abs(peak.frequency - best.frequency) > 18.0) {
                continue;
            }
            frequencies.add(peak.frequency);
            prominences.add(peak.prominence);
            levels.add(peak.level);
        }
        double prominence = percentile(prominences, 0.50);
        double level = percentile(levels, 0.50);
        if (prominence < 18.0 || level < -68.0) return null;
        ToneEvidence out = new ToneEvidence();
        out.frequency = percentile(frequencies, 0.50);
        out.prominence = prominence;
        out.level = level;
        out.stable = bestCount;
        return out;
    }

    private static void pruneAndCount(StemResult out) {
        Collections.sort(out.events, Comparator.comparingLong(event -> event.startFrame));
        ArrayList<Event> accepted = new ArrayList<>();
        for (Event event : out.events) {
            if (event.kind == Kind.ISOLATED_CLICK
                    && overlapsKind(out.events, event, Kind.EXACT_ZERO_DROPOUT,
                    Kind.ISOLATED_CLIP_RUN)) continue;
            accepted.add(event);
        }
        int clicks = 0;
        for (Event event : accepted) {
            if (event.kind == Kind.ISOLATED_CLICK) clicks++;
        }
        if (clicks > MAX_AUTOMATIC_MICRO_EVENTS) {
            out.continuousCrackleDiagnostic = true;
            out.diagnostics.add(clicks
                    + " impulse candidates were retained as continuous-crackle diagnostics; broad automatic interpolation is disabled.");
            ArrayList<Event> limited = new ArrayList<>();
            for (Event event : accepted) {
                if (event.kind != Kind.ISOLATED_CLICK) limited.add(event);
            }
            accepted = limited;
        }
        long coverage = 0L;
        for (Event event : accepted) coverage += event.frames();
        if (coverage > Math.round(out.frames * MAX_MICRO_EVENT_COVERAGE)) {
            out.diagnostics.add("Micro-repair coverage exceeded 0.25% of the stem; click/declip edits were retained as diagnostics.");
            ArrayList<Event> limited = new ArrayList<>();
            for (Event event : accepted) {
                if (event.kind == Kind.EXACT_ZERO_DROPOUT) limited.add(event);
            }
            accepted = limited;
        }
        out.events.clear();
        out.events.addAll(accepted);
        for (Event event : out.events) {
            out.repairedEventFrames += event.frames();
            if (event.kind == Kind.ISOLATED_CLICK) {
                out.isolatedClicks++;
                if ("lead_vocal".equals(out.role) || "vocal".equals(out.role)) {
                    out.mouthClicks++;
                }
            } else if (event.kind == Kind.EXACT_ZERO_DROPOUT) {
                out.exactZeroDropouts++;
            } else if (event.kind == Kind.ISOLATED_CLIP_RUN) {
                out.isolatedClipRuns++;
            }
        }
    }

    private static void render(File source, File output,
                               StemInventory.Item item, StemResult model,
                               AudioAnalyzer.Progress progress)
            throws IOException {
        long dataBytes = item.wavInfo.frames * item.wavInfo.channels * 4L;
        if (dataBytes <= 0L || dataBytes > 0xffffffffL - 36L) {
            throw new IOException("Prepared stem exceeds the RIFF/WAVE size limit");
        }
        ArrayList<ToneCorrection> automaticTones = new ArrayList<>();
        for (ToneCorrection tone : model.tones) {
            if (tone.automatic) automaticTones.add(tone);
        }
        Biquad[][] filters = new Biquad[item.wavInfo.channels][automaticTones.size()];
        for (int channel = 0; channel < filters.length; channel++) {
            for (int tone = 0; tone < automaticTones.size(); tone++) {
                ToneCorrection value = automaticTones.get(tone);
                filters[channel][tone] = Biquad.peaking(item.wavInfo.sampleRate,
                        value.frequencyHz, value.q, value.attenuationDb);
            }
        }
        List<Event> events = new ArrayList<>(model.events);
        Collections.sort(events, Comparator.comparingLong(event -> event.startFrame));
        float[][] samples = new float[item.wavInfo.channels][BUFFER_FRAMES];
        long absolute = 0L;
        int cursor = 0;
        try (WavFile.Reader reader = WavFile.open(new FileInputStream(source));
             FileOutputStream file = new FileOutputStream(output);
             OutputStream raw = new BufferedOutputStream(file, 256 * 1024)) {
            writeFloatHeader(raw, item.wavInfo.sampleRate,
                    item.wavInfo.channels, dataBytes);
            FloatWriter writer = new FloatWriter(raw);
            int read;
            while ((read = reader.readFrames(samples, BUFFER_FRAMES)) > 0) {
                for (int frame = 0; frame < read; frame++, absolute++) {
                    while (cursor < events.size()
                            && absolute > events.get(cursor).endFrame) cursor++;
                    for (int channel = 0; channel < item.wavInfo.channels; channel++) {
                        double value = finite(samples[channel][frame]);
                        for (int index = cursor; index < events.size(); index++) {
                            Event event = events.get(index);
                            if (event.startFrame > absolute) break;
                            if (absolute > event.endFrame
                                    || (event.channelMask & (1 << channel)) == 0) continue;
                            if (event.kind == Kind.ISOLATED_CLICK) {
                                value = event.replacement[channel];
                            } else {
                                double t = (absolute - event.startFrame + 1.0)
                                        / (event.frames() + 1.0);
                                double smooth = t * t * (3.0 - 2.0 * t);
                                value = event.before[channel] * (1.0 - smooth)
                                        + event.after[channel] * smooth;
                            }
                            break;
                        }
                        for (Biquad filter : filters[channel]) {
                            value = filter.process(value);
                        }
                        writer.sample(value);
                    }
                }
                progress.update("Studio preflight: rendering " + item.path,
                        (int) Math.min(96, Math.round(96.0 * absolute
                                / Math.max(1L, item.wavInfo.frames))));
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("Per-stem repair rendering cancelled");
                }
            }
            writer.finish();
            raw.flush();
            file.getFD().sync();
        } catch (IOException error) {
            delete(output);
            throw error;
        }
        if (absolute != item.wavInfo.frames || output.length() != 44L + dataBytes) {
            delete(output);
            throw new IOException("Prepared stem changed frame count or was incomplete");
        }
    }

    private static void verify(File source, File output,
                               StemInventory.Item item, StemResult model)
            throws IOException {
        double dot = 0.0;
        double sourceEnergy = 0.0;
        double outputEnergy = 0.0;
        long changed = 0L;
        long samplesSeen = 0L;
        long outputClips = 0L;
        double inputPeak = 0.0;
        double outputPeak = 0.0;
        boolean invalid = false;
        try (WavFile.Reader a = WavFile.open(new FileInputStream(source));
             WavFile.Reader b = WavFile.open(new FileInputStream(output))) {
            if (b.info.sampleRate != a.info.sampleRate
                    || b.info.channels != a.info.channels
                    || b.info.frames != a.info.frames
                    || !b.info.floatingPoint) {
                throw new IOException("Prepared stem changed format or duration");
            }
            float[][] x = new float[a.info.channels][BUFFER_FRAMES];
            float[][] y = new float[a.info.channels][BUFFER_FRAMES];
            int read;
            while ((read = a.readFrames(x, BUFFER_FRAMES)) > 0) {
                if (b.readFrames(y, read) != read) {
                    throw new IOException("Prepared stem ended before its source");
                }
                for (int frame = 0; frame < read; frame++) {
                    for (int channel = 0; channel < a.info.channels; channel++) {
                        double left = x[channel][frame];
                        double right = y[channel][frame];
                        if (!Double.isFinite(right)) invalid = true;
                        double delta = right - left;
                        if (Math.abs(delta) > 1.0e-9) changed++;
                        model.maximumAbsoluteDelta = Math.max(
                                model.maximumAbsoluteDelta, Math.abs(delta));
                        inputPeak = Math.max(inputPeak, Math.abs(left));
                        outputPeak = Math.max(outputPeak, Math.abs(right));
                        if (Math.abs(right) >= 1.0) outputClips++;
                        dot += left * right;
                        sourceEnergy += left * left;
                        outputEnergy += right * right;
                        samplesSeen++;
                    }
                }
            }
            if (b.readFrames(y, 1) > 0) {
                throw new IOException("Prepared stem extended beyond its source");
            }
        }
        model.sourceCorrelation = dot / Math.sqrt(Math.max(1.0e-30,
                sourceEnergy * outputEnergy));
        model.changedSamplePercent = 100.0 * changed / Math.max(1L, samplesSeen);
        model.inputPeak = inputPeak;
        model.outputPeak = outputPeak;
        model.outputClippedSamples = outputClips;
        boolean tones = model.automaticToneCount() > 0;
        if (invalid || model.sourceCorrelation < MIN_VERIFIED_CORRELATION
                || outputPeak > Math.max(1.0, inputPeak + 0.02)
                || outputClips > model.inputClippedSamples
                || (!tones && model.changedSamplePercent > 0.50)) {
            delete(output);
            throw new IOException(String.format(Locale.US,
                    "Prepared stem failed integrity guard: correlation %.7f, changed %.4f%%, peak %.5f -> %.5f, clips %d -> %d",
                    model.sourceCorrelation, model.changedSamplePercent,
                    inputPeak, outputPeak, model.inputClippedSamples,
                    outputClips));
        }
        model.repairApplied = true;
        model.outputSha256 = Hashing.sha256(output);
        model.status = String.format(Locale.US,
                "VERIFIED: %d sparse event(s), %d narrow tone notch(es), %.6f%% changed samples, correlation %.8f",
                model.events.size(), model.automaticToneCount(),
                model.changedSamplePercent, model.sourceCorrelation);
    }

    private static void collect(Result result, StemResult stem) {
        if (stem.repairApplied) result.stemsRepaired++;
        result.isolatedClicks += stem.isolatedClicks;
        result.mouthClicks += stem.mouthClicks;
        result.exactZeroDropouts += stem.exactZeroDropouts;
        result.isolatedClipRuns += stem.isolatedClipRuns;
        result.repairedEventFrames += stem.repairedEventFrames;
        result.maximumAbsoluteDelta = Math.max(result.maximumAbsoluteDelta,
                stem.maximumAbsoluteDelta);
        if (stem.repairApplied) {
            result.worstSourceCorrelation = Math.min(
                    result.worstSourceCorrelation, stem.sourceCorrelation);
        }
        if (stem.broadbandNoiseDiagnostic) result.broadbandNoiseDiagnostics++;
        boolean hum = false;
        boolean whine = false;
        for (ToneCorrection tone : stem.tones) {
            if (!tone.automatic) continue;
            if ("MAINS_HUM_HARMONIC".equals(tone.kind)) hum = true;
            if ("ULTRASONIC_CARRIER".equals(tone.kind)) whine = true;
        }
        if (hum) result.humStems++;
        if (whine) result.whineStems++;
    }

    private static boolean safeToneRole(String role) {
        return "lead_vocal".equals(role) || "vocal".equals(role)
                || "guitar".equals(role) || "keys".equals(role);
    }

    private static boolean overlaps(List<Event> events, long start,
                                    long end, long padding) {
        for (Event event : events) {
            if (start <= event.endFrame + padding
                    && end >= event.startFrame - padding) return true;
        }
        return false;
    }

    private static boolean overlapsKind(List<Event> events, Event target,
                                        Kind first, Kind second) {
        for (Event event : events) {
            if (event == target || (event.kind != first && event.kind != second)) {
                continue;
            }
            if (target.startFrame <= event.endFrame + 2L
                    && target.endFrame >= event.startFrame - 2L) return true;
        }
        return false;
    }

    private static double combinedRms(Rolling[] rolling) {
        double value = 0.0;
        for (Rolling item : rolling) value += item.energy;
        return Math.sqrt(value / Math.max(1, rolling.length));
    }

    private static double vectorRms(double[] values, int count) {
        double energy = 0.0;
        for (int i = 0; i < count; i++) energy += values[i] * values[i];
        return Math.sqrt(energy / Math.max(1, count));
    }

    private static double percentile(List<Double> values, double fraction) {
        if (values.isEmpty()) return Double.NaN;
        double[] data = new double[values.size()];
        for (int i = 0; i < data.length; i++) data[i] = values.get(i);
        return percentile(data, fraction);
    }

    private static double percentile(double[] values, double fraction) {
        if (values.length == 0) return Double.NaN;
        double[] data = values.clone();
        java.util.Arrays.sort(data);
        double position = clamp(fraction, 0.0, 1.0) * (data.length - 1);
        int lower = (int) Math.floor(position);
        int upper = Math.min(data.length - 1, lower + 1);
        double mix = position - lower;
        return data[lower] * (1.0 - mix) + data[upper] * mix;
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static String clean(Throwable error) {
        String value = error == null ? "unknown error" : error.getMessage();
        if (value == null || value.trim().isEmpty()) {
            value = error == null ? "unknown error"
                    : error.getClass().getSimpleName();
        }
        return value.replace('\n', ' ').replace('\r', ' ');
    }

    private static void copy(InputStream input, OutputStream output)
            throws IOException {
        byte[] buffer = new byte[256 * 1024];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read > 0) output.write(buffer, 0, read);
            if (Thread.currentThread().isInterrupted()) {
                throw new IOException("Stem studio preparation cancelled");
            }
        }
    }

    private static void delete(File file) {
        if (file != null && file.exists() && !file.delete()) file.deleteOnExit();
    }

    private static void writeFloatHeader(OutputStream out, int sampleRate,
                                         int channels, long dataBytes)
            throws IOException {
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

    private static final class FloatWriter {
        private final OutputStream output;
        private final byte[] buffer = new byte[256 * 1024];
        private int position;

        FloatWriter(OutputStream output) {
            this.output = output;
        }

        void sample(double value) throws IOException {
            if (position + 4 > buffer.length) flush();
            int bits = Float.floatToIntBits((float) finite(value));
            buffer[position++] = (byte) bits;
            buffer[position++] = (byte) (bits >>> 8);
            buffer[position++] = (byte) (bits >>> 16);
            buffer[position++] = (byte) (bits >>> 24);
        }

        void finish() throws IOException {
            flush();
        }

        private void flush() throws IOException {
            if (position > 0) output.write(buffer, 0, position);
            position = 0;
        }
    }

    private static final class Rolling {
        double energy = 1.0e-8;
        double residual = 1.0e-6;

        void updateEnergy(double value) {
            energy += 0.0015 * (value * value - energy);
        }

        void updateResidual(double value) {
            residual += 0.004 * (value - residual);
        }
    }

    private static final class ClipRun {
        boolean active;
        boolean signConsistent;
        boolean rejectedAtEnd;
        long start;
        int frames;
        int sign;
        double before;
        double minimum;
        double maximum;

        void reset() {
            active = false;
            signConsistent = false;
            start = 0L;
            frames = 0;
            sign = 0;
            before = 0.0;
            minimum = 0.0;
            maximum = 0.0;
        }
    }

    private static final class ToneEvidence {
        double frequency;
        double prominence;
        double level;
        int stable;
    }

    private static final class HumEvidence {
        double baseFrequency;
        double meanProminence;
        final List<ToneEvidence> components = new ArrayList<>();
    }

    private static final class Peak {
        double frequency;
        double prominence;
        double level;
    }

    private static final class Spectrum {
        final double[] db;
        final double binHz;

        Spectrum(double[] db, double binHz) {
            this.db = db;
            this.binHz = binHz;
        }

        static Spectrum create(double[] input, int sampleRate) {
            int size = input.length;
            double[] real = new double[size];
            double[] imaginary = new double[size];
            for (int i = 0; i < size; i++) {
                double window = 0.5 - 0.5 * Math.cos(
                        2.0 * Math.PI * i / (size - 1.0));
                real[i] = input[i] * window;
            }
            fft(real, imaginary);
            double[] db = new double[size / 2 + 1];
            double normalization = Math.max(1.0, size * 0.5);
            for (int bin = 0; bin < db.length; bin++) {
                double magnitude = Math.hypot(real[bin], imaginary[bin])
                        / normalization;
                db[bin] = AudioAnalyzer.toDb(magnitude);
            }
            return new Spectrum(db, sampleRate / (double) size);
        }

        double peakNear(double frequency, int radius) {
            int centre = bin(frequency);
            double peak = -200.0;
            for (int i = Math.max(1, centre - radius);
                 i <= Math.min(db.length - 2, centre + radius); i++) {
                peak = Math.max(peak, db[i]);
            }
            return peak;
        }

        double localFloor(double frequency, int inner, int outer) {
            int centre = bin(frequency);
            ArrayList<Double> values = new ArrayList<>();
            for (int offset = -outer; offset <= outer; offset++) {
                if (Math.abs(offset) < inner) continue;
                int index = centre + offset;
                if (index > 0 && index < db.length) values.add(db[index]);
            }
            return percentile(values, 0.50);
        }

        double medianBand(double low, double high) {
            int start = Math.max(1, bin(low));
            int end = Math.min(db.length - 1, bin(high));
            double[] values = new double[Math.max(0, end - start + 1)];
            for (int i = 0; i < values.length; i++) values[i] = db[start + i];
            return percentile(values, 0.50);
        }

        Peak bestNarrowPeak(double low, double high) {
            int start = Math.max(40, bin(low));
            int end = Math.min(db.length - 40, bin(high));
            Peak best = null;
            for (int index = start; index <= end; index += 2) {
                double frequency = index * binHz;
                double floor = localFloor(frequency, 7, 30);
                double prominence = db[index] - floor;
                if (best == null || prominence > best.prominence) {
                    best = new Peak();
                    best.frequency = frequency;
                    best.prominence = prominence;
                    best.level = db[index];
                }
            }
            return best;
        }

        private int bin(double frequency) {
            return (int) Math.round(frequency / binHz);
        }

        private static void fft(double[] real, double[] imaginary) {
            int n = real.length;
            int j = 0;
            for (int i = 1; i < n; i++) {
                int bit = n >> 1;
                while ((j & bit) != 0) {
                    j ^= bit;
                    bit >>= 1;
                }
                j ^= bit;
                if (i < j) {
                    double value = real[i];
                    real[i] = real[j];
                    real[j] = value;
                }
            }
            for (int length = 2; length <= n; length <<= 1) {
                double angle = -2.0 * Math.PI / length;
                double stepReal = Math.cos(angle);
                double stepImaginary = Math.sin(angle);
                for (int start = 0; start < n; start += length) {
                    double wr = 1.0;
                    double wi = 0.0;
                    for (int offset = 0; offset < length / 2; offset++) {
                        int even = start + offset;
                        int odd = even + length / 2;
                        double tr = wr * real[odd] - wi * imaginary[odd];
                        double ti = wr * imaginary[odd] + wi * real[odd];
                        real[odd] = real[even] - tr;
                        imaginary[odd] = imaginary[even] - ti;
                        real[even] += tr;
                        imaginary[even] += ti;
                        double nextWr = wr * stepReal - wi * stepImaginary;
                        wi = wr * stepImaginary + wi * stepReal;
                        wr = nextWr;
                    }
                }
            }
        }
    }
}
