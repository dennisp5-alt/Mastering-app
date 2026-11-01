package com.dennis.aimasteringlab;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Separates waveform trust from analytical usefulness.
 *
 * Tier 1 permits the existing processed-minus-original stem delta path.
 * Tier 2 permits aligned stems to drive measurements only; their samples can
 * never be added to the authoritative mix. Tier 3 is a complete rejection.
 */
final class StemTrustClassifier {
    enum Tier {
        NONE,
        TIER_1_AUDIO_DELTA,
        TIER_2_ANALYSIS_ONLY,
        TIER_3_REJECTED
    }

    static final class Result {
        Tier tier = Tier.NONE;
        String status = "No optional stem archive supplied";
        boolean audioDeltaAllowed;
        boolean analysisOnlyAllowed;
        int secureStems;
        String secureLeadPath;
        final List<String> reasons = new ArrayList<>();

        String code() {
            return tier.name();
        }
    }

    private StemTrustClassifier() {}

    static Result classify(AnalysisResult base) {
        Result out = new Result();
        StemReconstruction.Result reconstruction = base.stemReconstruction;
        if (reconstruction == null || !reconstruction.attempted) {
            return reject(out, "Sample-domain reconstruction was not attempted");
        }
        if (!reconstruction.compatible || reconstruction.error != null) {
            return reject(out, "Stem formats did not establish a compatible reconstruction");
        }
        if (reconstruction.globalPolarityRisk) {
            return reject(out, "Global stem polarity evidence is unsafe");
        }
        if (base.stemAlignment == null
                || (base.stemAlignment.secureStems < 1
                    && base.stemAlignment.analysisSecureStems < 1)) {
            return reject(out, "No stem passed five-position local timing and polarity validation");
        }

        if (!reconstruction.likelySeparationDerived) {
            if (base.stemAlignment.secureStems < 1) {
                return reject(out,
                        "No production stem established exact zero five-position lag/drift");
            }
            if (!globalTimingSecure(reconstruction)) {
                return reject(out,
                        "Production-stem reconstruction start/middle/end lag or drift is not effectively zero");
            }
            out.tier = Tier.TIER_1_AUDIO_DELTA;
            out.secureStems = base.stemAlignment.secureStems;
            out.secureLeadPath = secureLead(base, false);
            out.audioDeltaAllowed = true;
            out.analysisOnlyAllowed = true;
            out.status = String.format(Locale.US,
                    "Tier 1 production-stem trust: %d locally secure stem(s); processed-minus-original audio deltas may compete",
                    out.secureStems);
            return out;
        }

        out.secureStems = base.stemAlignment.analysisSecureStems;
        out.secureLeadPath = secureLead(base, true);
        if (out.secureLeadPath == null) {
            return reject(out,
                    "Separation-derived reconstruction has no unambiguous locally secure lead-vocal stem");
        }
        out.tier = Tier.TIER_2_ANALYSIS_ONLY;
        out.analysisOnlyAllowed = true;
        out.reasons.add("Global reconstruction is poor or separation-derived, so stem waveform audio is prohibited");
        if (!globalTimingSecure(reconstruction)) {
            out.reasons.add("Global stem-sum envelope timing is not used for Tier 2; each admitted control stem passed five-position local validation independently");
        }
        out.status = String.format(Locale.US,
                "Tier 2 analysis-only trust: %d locally secure stem(s), lead %s; stems may guide measurements but no stem waveform or delta may enter the master",
                out.secureStems, out.secureLeadPath);
        return out;
    }

    private static Result reject(Result out, String reason) {
        out.tier = Tier.TIER_3_REJECTED;
        out.reasons.add(reason);
        out.status = "Tier 3 rejected: " + reason;
        return out;
    }

    private static boolean globalTimingSecure(StemReconstruction.Result result) {
        return Double.isFinite(result.startLagMs)
                && Double.isFinite(result.middleLagMs)
                && Double.isFinite(result.endLagMs)
                && Double.isFinite(result.measuredDriftMs)
                && Math.abs(result.startLagMs) <= 1.0
                && Math.abs(result.middleLagMs) <= 1.0
                && Math.abs(result.endLagMs) <= 1.0
                && Math.abs(result.measuredDriftMs) <= 1.0;
    }

    private static String secureLead(AnalysisResult base, boolean analysisOnly) {
        StemInventory.Item best = null;
        int bestScore = Integer.MIN_VALUE;
        int secondScore = -1000000;
        for (StemInventory.Item item : base.stems) {
            if (!valid(item, base) || base.stemAlignment == null
                    || (analysisOnly
                        ? !base.stemAlignment.analysisSecure(item.path)
                        : !base.stemAlignment.secure(item.path))) continue;
            String name = item.path.toLowerCase(Locale.US);
            int score = 0;
            if (name.contains("lead vocal")) score += 120;
            else if (name.contains("lead vox")) score += 110;
            else if (name.contains("lead")
                    && (name.contains("vocal") || name.contains("vox"))) score += 100;
            else if (name.contains("vocal")) score += 35;
            else if (name.contains("vox")) score += 25;
            if (name.contains("main")) score += 15;
            if (name.contains("backing") || name.contains("background")
                    || name.contains("bgv") || name.contains("bvox")) score -= 120;
            if (name.contains("harmony") || name.contains("choir")) score -= 70;
            if (name.contains("instrumental")) score -= 150;
            if (item.clippedSamples > 0) score -= 35;
            if (score > bestScore) {
                secondScore = bestScore;
                bestScore = score;
                best = item;
            } else if (score > secondScore) {
                secondScore = score;
            }
        }
        if (best == null || bestScore < 80 || bestScore - secondScore <= 10) return null;
        return best.path;
    }

    private static boolean valid(StemInventory.Item item, AnalysisResult base) {
        if (item == null || !item.wav || item.wavInfo == null || item.error != null
                || !item.crcVerified || item.wavInfo.sampleRate != base.wav.sampleRate
                || (item.wavInfo.channels != 1 && item.wavInfo.channels != 2)) return false;
        double excess = item.wavInfo.durationSeconds - base.wav.durationSeconds;
        double shortfall = base.wav.durationSeconds - item.wavInfo.durationSeconds;
        return excess <= 1.01 / base.wav.sampleRate && shortfall <= 2.0;
    }
}
