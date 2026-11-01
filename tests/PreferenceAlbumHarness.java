package com.dennis.aimasteringlab;

/** Persistence/math regression for explicit A/B learning and album targets. */
public final class PreferenceAlbumHarness {
    private PreferenceAlbumHarness() {}

    public static void main(String[] args) throws Exception {
        DennisPreferenceMemory.Profile preference = new DennisPreferenceMemory.Profile();
        for (int i = 0; i < 6; i++) {
            if (!preference.record("comparison-" + i,
                    "tonal_corrective", "transparent_flat_gain")) {
                throw new AssertionError("Preference comparison was not recorded");
            }
        }
        if (preference.bonusFor("tonal_corrective") <= 0.0
                || preference.bonusFor("transparent_flat_gain") >= 0.0
                || preference.record("comparison-0", "tonal_corrective",
                "transparent_flat_gain")) {
            throw new AssertionError("Preference consistency or duplicate guard failed");
        }
        DennisPreferenceMemory.Profile restored =
                DennisPreferenceMemory.Profile.fromJson(preference.toJson());
        if (restored.comparisons != 6
                || restored.bonusFor("tonal_corrective") <= 0.0) {
            throw new AssertionError("Preference JSON round-trip failed");
        }

        FullAiMasteringEngine.Candidate anchor = candidate(
                "album-anchor-hash", -6.0, -7.0, -5.0, -12.0,
                -15.0, -13.0, 5.0, 12.0);
        AlbumConsistencyModel.Profile album = new AlbumConsistencyModel.Profile();
        album.active = true;
        if (!album.add(anchor) || album.add(anchor) || !album.usable()) {
            throw new AssertionError("Album anchor or duplicate guard failed");
        }
        AlbumConsistencyModel.Profile albumRestored =
                AlbumConsistencyModel.Profile.fromJson(album.toJson());
        if (!albumRestored.usable() || albumRestored.songs != 1) {
            throw new AssertionError("Album JSON round-trip failed");
        }
        AutonomousMasteringBrain.Features current = features(
                -10.0, -10.0, -8.0, -16.0, -20.0, -9.0);
        AnalysisResult source = measurement("source", 7.0, 14.0);
        AutonomousMasteringBrain.Plan plan = AlbumConsistencyModel.makePlan(
                albumRestored, current, source);
        if (plan == null || !"album_consistency_guided".equals(plan.strategy)
                || Math.abs(plan.lowShelfDb) > 0.45
                || Math.abs(plan.lowMidDb) > 0.42
                || Math.abs(plan.presenceDb) > 0.40
                || Math.abs(plan.highShelfDb) > 0.35
                || plan.sideScale > 1.0 || plan.compressorEnabled) {
            throw new AssertionError("Album candidate exceeded its bounded plan");
        }
        System.out.println("PREFERENCE_ALBUM_PASS preference="
                + restored.compactSummary() + " album=" + albumRestored.compactSummary());
    }

    private static FullAiMasteringEngine.Candidate candidate(
            String hash, double low, double lowMid, double presence,
            double harsh, double air, double side, double lra, double crest) {
        FullAiMasteringEngine.Candidate out = new FullAiMasteringEngine.Candidate();
        out.strategy = "tonal_corrective";
        out.measuredFeatures = features(low, lowMid, presence, harsh, air, side);
        out.render = new AutonomousMasteringRenderer.Result();
        out.render.master = new MasterRenderer.Result();
        out.render.master.verification = measurement(hash, lra, crest);
        return out;
    }

    private static AutonomousMasteringBrain.Features features(
            double low, double lowMid, double presence, double harsh,
            double air, double side) {
        AutonomousMasteringBrain.Features out = new AutonomousMasteringBrain.Features();
        out.lowShareDb = low;
        out.lowMidShareDb = lowMid;
        out.presenceShareDb = presence;
        out.harshShareDb = harsh;
        out.airShareDb = air;
        out.sideShareDb = side;
        out.reliability = 1.0;
        return out;
    }

    private static AnalysisResult measurement(String hash, double lra, double crest) {
        AnalysisResult out = new AnalysisResult();
        out.sourceSha256 = hash;
        out.lraLu = lra;
        out.crestFactorDb = crest;
        out.stereoCorrelation = 0.8;
        out.lowBandCorrelation = 0.9;
        return out;
    }
}
