package com.dennis.aimasteringlab;

import java.util.ArrayList;
import java.util.List;

final class FormalAssessment {
    int technicalScore;
    String technicalVerdict;
    int productionArScore;
    String productionArBand;
    boolean humanReviewRequired = true;
    String majorLabelClaim = "NOT PERMITTED — automated measurements cannot certify artistic or commercial acceptance";
    final List<String> technicalFindings = new ArrayList<>();
    final List<String> productionFindings = new ArrayList<>();

    static FormalAssessment evaluate(AnalysisResult source,
                                     MasterRenderer.Result masterRender,
                                     AnalysisResult master,
                                     AndroidCodecEncoder.Result mp3,
                                     AndroidCodecDecoder.Result mp3Decoded,
                                     AndroidCodecEncoder.Result aac,
                                     AndroidCodecDecoder.Result aacDecoded,
                                     SbcProxy.Result sbc,
                                     TranslationAnalyzer.Result translation) {
        FormalAssessment out = new FormalAssessment();
        int technical = 100;
        if (master == null) {
            technical = 0;
            out.technicalFindings.add("No verified primary master was produced");
        } else {
            double loudnessError = Math.abs(master.integratedLufs + 14.0);
            if (loudnessError > 0.05) {
                technical -= 20;
                out.technicalFindings.add("Primary master is outside ±0.05 LU of -14.00 LUFS-I");
            } else {
                out.technicalFindings.add("Primary loudness target verified");
            }
            if (master.truePeakEstimateDbtp > -1.0) {
                technical -= 35;
                out.technicalFindings.add("Primary master exceeds the -1.00 dBTP ceiling");
            } else {
                out.technicalFindings.add("Primary true-peak ceiling verified");
            }
            if (master.clippedSamples != 0 || master.nonFiniteSamples != 0) {
                technical -= 35;
                out.technicalFindings.add("Primary master contains clipped or non-finite samples");
            }
            if (master.monoFoldLossDb < -6.0) {
                technical -= 8;
                out.technicalFindings.add("Primary mono fold-down loses more than 6 dB and needs phase review");
            } else {
                out.technicalFindings.add("Primary mono fold-down remains level-stable");
            }
            if (masterRender != null && masterRender.dynamicsProtected) {
                technical -= 4;
                out.technicalFindings.add("Exact target was relaxed to preserve dynamics and peak safety");
            }
        }
        boolean requiredDeliveryMissing = false;
        if (mp3 == null || !mp3.available) {
            technical -= 5;
            requiredDeliveryMissing = true;
            out.technicalFindings.add("320 kbps MP3 encoder unavailable on this device");
        } else {
            technical -= decodedPenalty("320 kbps MP3", mp3Decoded, master,
                    out.technicalFindings);
        }
        if (aac == null || !aac.available) {
            technical -= 5;
            requiredDeliveryMissing = true;
            out.technicalFindings.add("256 kbps AAC encoder unavailable on this device");
        } else {
            technical -= decodedPenalty("256 kbps AAC", aacDecoded, master,
                    out.technicalFindings);
        }
        if (sbc == null || sbc.verification == null) {
            technical -= 4;
            out.technicalFindings.add("SBC/Bluetooth proxy verification is missing");
        } else if (sbc.verification.clippedSamples != 0
                || sbc.verification.nonFiniteSamples != 0) {
            technical -= 4;
            out.technicalFindings.add("SBC/Bluetooth proxy introduced invalid or clipped samples");
        } else {
            out.technicalFindings.add("SBC/Bluetooth proxy decoded stress result verified");
        }
        if (translation != null && translation.reviewCount > 0) {
            technical -= Math.min(8, translation.reviewCount);
            out.technicalFindings.add(translation.reviewCount
                    + " simulated playback profiles require review");
        }
        out.technicalScore = Math.max(0, technical);
        out.technicalVerdict = out.technicalScore >= 95 && !requiredDeliveryMissing ? "PASS"
                : out.technicalScore >= 80 ? "CONDITIONAL PASS" : "FAIL";

        int production = 50;
        TrackSections sections = source.sections;
        if (sections != null && Double.isFinite(sections.quietToChorusLiftLu)) {
            if (sections.quietToChorusLiftLu >= 2.0) {
                production += 15;
                out.productionFindings.add("Measured quiet-to-chorus lift is clearly preserved");
            } else if (sections.quietToChorusLiftLu >= 0.75) {
                production += 8;
                out.productionFindings.add("Measured section lift is modest");
            } else {
                production -= 8;
                out.productionFindings.add("Energy-derived chorus lift is weak and needs perceptual review");
            }
        }
        if (source.lraLu >= 4.0 && source.lraLu <= 14.0) {
            production += 10;
            out.productionFindings.add("Macro-dynamics fall in a healthy release range");
        } else if (source.lraLu < 2.5) {
            production -= 8;
            out.productionFindings.add("Low LRA may indicate flattened musical dynamics");
        }
        if (source.stereoCorrelation >= 0.10 && source.stereoCorrelation <= 0.98) {
            production += 8;
            out.productionFindings.add("Stereo field is broad but globally phase-safe");
        }
        if (source.lowBandCorrelation >= 0.70) {
            production += 7;
            out.productionFindings.add("Low-frequency mono compatibility is strong");
        } else {
            production -= 7;
            out.productionFindings.add("Low-frequency phase stability needs review");
        }
        if (translation != null) {
            if (translation.reviewCount == 0) {
                production += 10;
                out.productionFindings.add("All simulated playback profiles preserved lift and stability");
            } else {
                production += Math.max(0, 10 - translation.reviewCount * 2);
            }
        }
        if (source.stemReconstruction != null && source.stemReconstruction.likelySeparationDerived) {
            out.productionFindings.add("Stems reconstruct poorly; the authoritative stereo mix must remain primary");
        }
        if (source.aiDiagnostics != null) {
            out.productionFindings.add("Per-track AI diagnostic logged "
                    + source.aiDiagnostics.events.size() + " confidence-ranked local finding(s); "
                    + source.aiDiagnostics.autoCorrectionCount()
                    + " passed the optional attenuation-only A/B gate");
            if (source.aiDiagnostics.hasAutoCorrections()) {
                out.productionFindings.add("AI-guided audio remains a separate test candidate and is not promoted by the automated A&R proxy");
            }
        }
        out.productionArScore = Math.max(0, Math.min(100, production));
        out.productionArBand = out.productionArScore >= 88 ? "GREEN"
                : out.productionArScore >= 75 ? "AMBER" : "RED";
        out.productionFindings.add("Automated score covers measurable proxies only; human artistic/A&R review remains mandatory");
        return out;
    }

    private static int decodedPenalty(String name, AndroidCodecDecoder.Result decoded,
                                      AnalysisResult master, List<String> findings) {
        if (decoded == null || decoded.error != null || decoded.verification == null) {
            findings.add(name + " actual decoded-result verification failed"
                    + (decoded != null && decoded.error != null ? ": " + decoded.error : ""));
            return 6;
        }
        AnalysisResult value = decoded.verification;
        int penalty = 0;
        if (value.clippedSamples != 0 || value.nonFiniteSamples != 0) {
            penalty += 6;
            findings.add(name + " decode contains clipped or non-finite samples");
        }
        if (value.internalZeroRuns > master.internalZeroRuns) {
            penalty += 3;
            findings.add(name + " decode introduced an exact-zero interruption");
        }
        if (Math.abs(value.wav.durationSeconds - master.wav.durationSeconds) > 0.150) {
            penalty += 2;
            findings.add(name + " decoded duration differs by more than 150 ms");
        }
        if (Math.abs(value.integratedLufs - master.integratedLufs) > 0.30) {
            penalty += 2;
            findings.add(name + " decoded loudness differs by more than 0.30 LU");
        }
        if (value.truePeakEstimateDbtp > -1.0) {
            penalty += 2;
            findings.add(name + " decoded true peak exceeds -1.00 dBTP");
        }
        if (penalty == 0) findings.add(name + " actual decoded result verified");
        return penalty;
    }
}
