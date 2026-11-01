package com.dennis.aimasteringlab;

import java.io.IOException;

/** Re-meter the verified repair while preserving original-source audit evidence. */
final class TechnicalSourcePreparation {
    private TechnicalSourcePreparation() {}
    static AnalysisResult reanalyze(AudioAnalyzer.StreamProvider source,
            AnalysisResult original, AudioAnalyzer.Progress progress) throws IOException {
        AnalysisResult a = AudioAnalyzer.analyze(original.sourceName, source, progress);
        if (a.wav.frames != original.wav.frames || a.wav.channels != original.wav.channels
                || a.wav.sampleRate != original.wav.sampleRate || a.nonFiniteSamples != 0) {
            throw new IOException("Repaired source failed duration/format/finite verification");
        }
        a.originalSourceAnalysis = original;
        a.clickDropoutRepair = original.clickDropoutRepair;
        a.sourceGuard = original.sourceGuard;
        a.sections = original.sections;
        try {
            a.aiDiagnostics = AdaptiveDiagnosticModel.analyze(source, a, progress);
        } catch (IOException optionalError) {
            if (Thread.currentThread().isInterrupted()) throw optionalError;
            a.aiDiagnostics = AdaptiveDiagnosticModel.unavailable(
                    "Optional repaired-source diagnostic unavailable: " + optionalError.getMessage(), a);
        }
        a.stems.addAll(original.stems);
        a.stemReconstruction = original.stemReconstruction;
        a.stemAlignment = original.stemAlignment;
        a.stemTrust = original.stemTrust;
        a.stemArchiveName = original.stemArchiveName;
        a.stemArchiveSha256 = original.stemArchiveSha256;
        a.stemArchiveAccepted = original.stemArchiveAccepted;
        a.stemArchiveAnalysisOnlyAccepted = original.stemArchiveAnalysisOnlyAccepted;
        a.stemAssistStatus = original.stemAssistStatus;
        a.stemTrustTier = original.stemTrustTier;
        a.wavStemCount = original.wavStemCount;
        a.crcVerifiedStemCount = original.crcVerifiedStemCount;
        a.stemDurationMismatchCount = original.stemDurationMismatchCount;
        a.shortestStemDurationSeconds = original.shortestStemDurationSeconds;
        a.longestStemDurationSeconds = original.longestStemDurationSeconds;
        a.largestStemDurationDeltaSeconds = original.largestStemDurationDeltaSeconds;
        a.commonStemTailShortfallSeconds = original.commonStemTailShortfallSeconds;
        a.vocalHumanity = original.vocalHumanity;
        a.maskingPockets = original.maskingPockets;
        return a;
    }
}
