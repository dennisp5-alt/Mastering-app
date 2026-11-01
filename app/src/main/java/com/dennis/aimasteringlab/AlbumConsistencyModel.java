package com.dennis.aimasteringlab;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;

/** Persistent, measurement-only album target and bounded matching plan. */
final class AlbumConsistencyModel {
    static final String PREFERENCES_NAME = "daml_album_consistency_v060";
    static final String PROFILE_KEY = "album_profile_json";

    static final class Profile {
        boolean active;
        int songs;
        String name = "Current album";
        double lowShareDb;
        double lowMidShareDb;
        double presenceShareDb;
        double harshShareDb;
        double airShareDb;
        double sideShareDb;
        double lraLu;
        double crestDb;
        final ArrayList<String> songHashes = new ArrayList<>();

        boolean usable() {
            return active && songs > 0;
        }

        boolean add(FullAiMasteringEngine.Candidate candidate) {
            if (candidate == null || candidate.measuredFeatures == null
                    || candidate.render == null) return false;
            String hash = candidate.render.master.verification.sourceSha256;
            if (hash == null || hash.isEmpty() || songHashes.contains(hash)) return false;
            AutonomousMasteringBrain.Features f = candidate.measuredFeatures;
            AnalysisResult a = candidate.render.master.verification;
            int next = songs + 1;
            lowShareDb = average(lowShareDb, f.lowShareDb, songs, next);
            lowMidShareDb = average(lowMidShareDb, f.lowMidShareDb, songs, next);
            presenceShareDb = average(presenceShareDb, f.presenceShareDb, songs, next);
            harshShareDb = average(harshShareDb, f.harshShareDb, songs, next);
            airShareDb = average(airShareDb, f.airShareDb, songs, next);
            sideShareDb = average(sideShareDb, f.sideShareDb, songs, next);
            lraLu = average(lraLu, a.lraLu, songs, next);
            crestDb = average(crestDb, a.crestFactorDb, songs, next);
            songs = next;
            songHashes.add(hash);
            while (songHashes.size() > 100) songHashes.remove(0);
            return true;
        }

        double distance(AutonomousMasteringBrain.Features f, AnalysisResult a) {
            if (!usable() || f == null || a == null) return Double.NaN;
            double tonal = Math.abs(f.lowShareDb - lowShareDb) / 2.2
                    + Math.abs(f.lowMidShareDb - lowMidShareDb) / 2.2
                    + Math.abs(f.presenceShareDb - presenceShareDb) / 2.4
                    + Math.abs(f.harshShareDb - harshShareDb) / 3.0
                    + Math.abs(f.airShareDb - airShareDb) / 3.2;
            double spatial = Math.abs(f.sideShareDb - sideShareDb) / 3.0;
            double dynamics = Math.abs(a.lraLu - lraLu) / 1.5
                    + Math.abs(a.crestFactorDb - crestDb) / 2.2;
            return tonal * 0.65 / 5.0 + spatial * 0.12
                    + dynamics * 0.23 / 2.0;
        }

        double bonus(AutonomousMasteringBrain.Features f, AnalysisResult a) {
            double distance = distance(f, a);
            if (!Double.isFinite(distance)) return 0.0;
            double profileConfidence = clamp(0.55 + songs * 0.15, 0.0, 1.0);
            return clamp((0.080 - 0.050 * distance) * profileConfidence,
                    -0.040, 0.080);
        }

        String compactSummary() {
            if (!active) return "Off — no album influence";
            if (songs == 0) return "Active — add the first verified winner as the album anchor";
            return String.format(Locale.US,
                    "%s • %d verified song%s • target LRA %.2f LU • crest %.2f dB",
                    name, songs, songs == 1 ? "" : "s", lraLu, crestDb);
        }

        String toJson() {
            try {
                JSONObject out = new JSONObject();
                out.put("schema", 1);
                out.put("active", active);
                out.put("songs", songs);
                out.put("name", name);
                out.put("low_share_db", lowShareDb);
                out.put("low_mid_share_db", lowMidShareDb);
                out.put("presence_share_db", presenceShareDb);
                out.put("harsh_share_db", harshShareDb);
                out.put("air_share_db", airShareDb);
                out.put("side_share_db", sideShareDb);
                out.put("lra_lu", lraLu);
                out.put("crest_db", crestDb);
                out.put("song_hashes", new JSONArray(songHashes));
                return out.toString();
            } catch (JSONException impossible) {
                return "{}";
            }
        }

        static Profile fromJson(String json) {
            Profile out = new Profile();
            if (json == null || json.trim().isEmpty()) return out;
            try {
                JSONObject value = new JSONObject(json);
                out.active = value.optBoolean("active", false);
                out.songs = Math.max(0, value.optInt("songs", 0));
                out.name = safeName(value.optString("name", "Current album"));
                out.lowShareDb = finite(value.optDouble("low_share_db", 0.0));
                out.lowMidShareDb = finite(value.optDouble("low_mid_share_db", 0.0));
                out.presenceShareDb = finite(value.optDouble("presence_share_db", 0.0));
                out.harshShareDb = finite(value.optDouble("harsh_share_db", 0.0));
                out.airShareDb = finite(value.optDouble("air_share_db", 0.0));
                out.sideShareDb = finite(value.optDouble("side_share_db", 0.0));
                out.lraLu = finite(value.optDouble("lra_lu", 0.0));
                out.crestDb = finite(value.optDouble("crest_db", 0.0));
                JSONArray hashes = value.optJSONArray("song_hashes");
                if (hashes != null) {
                    int first = Math.max(0, hashes.length() - 100);
                    for (int i = first; i < hashes.length(); i++) {
                        String hash = hashes.optString(i, null);
                        if (hash != null && !hash.isEmpty()) out.songHashes.add(hash);
                    }
                }
            } catch (JSONException ignored) {
                return new Profile();
            }
            return out;
        }
    }

    private AlbumConsistencyModel() {}

    static AutonomousMasteringBrain.Plan makePlan(Profile profile,
                                                   AutonomousMasteringBrain.Features current,
                                                   AnalysisResult source) {
        if (profile == null || !profile.usable() || current == null) return null;
        AutonomousMasteringBrain.Plan plan = new AutonomousMasteringBrain.Plan();
        plan.mode = AutonomousMasteringBrain.Mode.CONSERVATIVE;
        plan.strategy = "album_consistency_guided";
        plan.lowShelfDb = bounded((profile.lowShareDb - current.lowShareDb) * 0.20, 0.45);
        plan.lowMidDb = bounded((profile.lowMidShareDb - current.lowMidShareDb) * 0.18, 0.42);
        plan.presenceDb = bounded((profile.presenceShareDb - current.presenceShareDb) * 0.17, 0.40);
        double highDelta = (profile.airShareDb - current.airShareDb) * 0.11
                + (profile.harshShareDb - current.harshShareDb) * 0.07;
        plan.highShelfDb = bounded(highDelta, 0.35);
        double excessSide = current.sideShareDb - profile.sideShareDb;
        if (excessSide > 1.0) {
            plan.sideScale = 1.0 - Math.min(0.025, (excessSide - 1.0) * 0.006);
            plan.decisions.add("A tiny side reduction was proposed to approach the verified album profile; widening is prohibited.");
        }
        plan.eventScale = 0.0;
        plan.compressorEnabled = false;
        plan.confidence = clamp(current.reliability
                * (0.55 + Math.min(0.35, profile.songs * 0.10)), 0.35, 0.92);
        plan.objectiveBefore = AutonomousMasteringBrain.objective(current, source);
        plan.predictedObjectiveAfter = plan.objectiveBefore;
        threshold(plan);
        if (plan.hasAudioChanges()) {
            plan.decisions.add("Bounded album-profile matching candidate derived from previously accepted verified masters.");
        } else {
            plan.bypassed.add("Album profile already matches this song within the no-change corridor.");
        }
        plan.bypassed.add("Album mode never raises loudness above the normal -14 LUFS-I target.");
        plan.bypassed.add("Album mode compression bypassed; dynamics are matched only by choosing among safe candidates.");
        return plan;
    }

    private static void threshold(AutonomousMasteringBrain.Plan plan) {
        if (Math.abs(plan.lowShelfDb) < 0.05) plan.lowShelfDb = 0.0;
        if (Math.abs(plan.lowMidDb) < 0.05) plan.lowMidDb = 0.0;
        if (Math.abs(plan.presenceDb) < 0.05) plan.presenceDb = 0.0;
        if (Math.abs(plan.highShelfDb) < 0.05) plan.highShelfDb = 0.0;
        if (Math.abs(plan.sideScale - 1.0) < 0.002) plan.sideScale = 1.0;
    }

    private static double average(double prior, double value, int priorCount, int nextCount) {
        if (priorCount <= 0) return value;
        return prior + (value - prior) / nextCount;
    }

    private static double bounded(double value, double maximum) {
        return clamp(value, -maximum, maximum);
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private static String safeName(String value) {
        if (value == null || value.trim().isEmpty()) return "Current album";
        String clean = value.trim();
        return clean.length() > 48 ? clean.substring(0, 48) : clean;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
