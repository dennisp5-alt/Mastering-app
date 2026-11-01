package com.dennis.aimasteringlab;

import java.util.ArrayList;
import java.util.List;

final class AnalysisResult {
    static final class ZeroRegion {
        long startFrame;
        long endFrameExclusive;

        double startSeconds(int sampleRate) {
            return startFrame / (double) Math.max(1, sampleRate);
        }

        double endSeconds(int sampleRate) {
            return endFrameExclusive / (double) Math.max(1, sampleRate);
        }

        long frames() {
            return Math.max(0L, endFrameExclusive - startFrame);
        }
    }

    AnalysisResult originalSourceAnalysis;
    WavFile.Info wav;
    String sourceName;
    String sourceSha256;
    long totalSamples;
    long clippedSamples;
    long nonFiniteSamples;
    long internalZeroRuns;
    double longestInternalZeroSeconds;
    final List<ZeroRegion> internalZeroRegions = new ArrayList<>();
    double integratedLufs;
    double lraLu;
    double samplePeakDbfs;
    double truePeakEstimateDbtp;
    double rmsDbfs;
    double[] dcOffset;
    double stereoCorrelation;
    double lowBandCorrelation;
    double monoFoldRmsDbfs;
    double monoFoldPeakDbfs;
    double monoFoldLossDb;
    double firstFramePeak;
    double lastFramePeak;
    double targetGainDb;
    double appliedSafeGainDb;
    double predictedOutputLufs;
    double predictedOutputTruePeakDbtp;
    boolean ceilingLimited;
    boolean limiterRequired;
    boolean limiterAllowed;
    double estimatedLimiterReductionDb;
    double limiterReductionAllowanceDb;
    boolean alreadyAtTargetNoRender;
    boolean alreadyCompliantNoRender;
    boolean noRenderNeeded;
    String noRenderReason;
    double crestFactorDb;
    int wavStemCount;
    int crcVerifiedStemCount;
    int stemDurationMismatchCount;
    double shortestStemDurationSeconds = Double.NaN;
    double longestStemDurationSeconds = Double.NaN;
    double largestStemDurationDeltaSeconds;
    double commonStemTailShortfallSeconds;
    TrackSections sections;
    StemReconstruction.Result stemReconstruction;
    StemAlignmentValidator.Result stemAlignment;
    StemTrustClassifier.Result stemTrust;
    AdaptiveDiagnosticModel.Result aiDiagnostics;
    StereoIntelligenceModel.Result stereoIntelligence;
    AdvancedStereoModel.Result advancedIntelligence;
    ProfessionalMasteringModel.Result professionalIntelligence;
    SourceGuardModel.Result sourceGuard;
    NextGenerationMasteringModel.Result nextGenerationIntelligence;
    ClickDropoutRepair.Result clickDropoutRepair;
    VocalHumanityModel.Result vocalHumanity;
    MaskingPocketModel.Result maskingPockets;
    String stemArchiveName;
    String stemArchiveSha256;
    String stemAssistStatus = "No optional stem archive supplied; stereo-only fallback active";
    String stemTrustTier = "NONE";
    boolean stemArchiveAccepted;
    boolean stemArchiveAnalysisOnlyAccepted;
    String meterVersion = "DAML-R128-v0.10 / 4x polyphase windowed-sinc TP";
    final List<StemInventory.Item> stems = new ArrayList<>();
}
