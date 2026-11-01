package com.dennis.aimasteringlab;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

final class ReleaseArtifacts {
    String baseName;
    AnalysisResult source;
    MasterRenderer.Result masterRender;
    AnalysisResult master;
    AndroidCodecEncoder.Result mp3;
    AndroidCodecEncoder.Result aac;
    AndroidCodecEncoder.Result translationMp3;
    AndroidCodecDecoder.Result mp3Decoded;
    AndroidCodecDecoder.Result aacDecoded;
    AndroidCodecDecoder.Result translationMp3Decoded;
    TranslationComposite.Result translationComposite;
    TranslationAnalyzer.Result translation;
    SbcProxy.Result sbc;
    FormalAssessment assessment;
    File primaryWav;
    File mp3File;
    File aacFile;
    File translationWav;
    File translationMp3File;
    File masteringReport;
    File translationReport;
    File qcJson;
    File verificationReport;
    File checksums;
    File coreZip;
    File completeZip;
    final Map<String, String> sha256 = new LinkedHashMap<>();
}
