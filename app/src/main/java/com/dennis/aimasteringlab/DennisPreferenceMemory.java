package com.dennis.aimasteringlab;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Small, offline preference profile learned only from Dennis's explicit,
 * level-matched A/B choices. It can bias eligible candidates, but it never
 * changes a hard safety decision or creates audio processing by itself.
 */
final class DennisPreferenceMemory {
    static final String PREFERENCES_NAME = "daml_preference_memory_v060";
    static final String PROFILE_KEY = "profile_json";
    private static final int MAX_COMPARISON_IDS = 128;
    private static final double MAX_ABSOLUTE_BONUS = 0.035;

    static final class Profile {
        int comparisons;
        final LinkedHashMap<String, Integer> wins = new LinkedHashMap<>();
        final LinkedHashMap<String, Integer> losses = new LinkedHashMap<>();
        final ArrayList<String> comparisonIds = new ArrayList<>();

        boolean ready() {
            return comparisons >= 3;
        }

        double bonusFor(String strategy) {
            if (!ready() || strategy == null) return 0.0;
            int won = value(wins, strategy);
            int lost = value(losses, strategy);
            int samples = won + lost;
            if (samples < 2) return 0.0;
            double preference = (won - lost) / (double) samples;
            if (Math.abs(preference) < 0.20) return 0.0;
            double globalReliability = clamp((comparisons - 2) / 8.0, 0.0, 1.0);
            double strategyReliability = clamp(samples / 5.0, 0.0, 1.0);
            return clamp(preference * MAX_ABSOLUTE_BONUS
                            * globalReliability * strategyReliability,
                    -MAX_ABSOLUTE_BONUS, MAX_ABSOLUTE_BONUS);
        }

        boolean hasComparison(String comparisonId) {
            return comparisonId != null && comparisonIds.contains(comparisonId);
        }

        boolean record(String comparisonId, String preferred, String rejected) {
            if (comparisonId == null || comparisonId.trim().isEmpty()
                    || preferred == null || rejected == null
                    || preferred.equals(rejected) || hasComparison(comparisonId)) {
                return false;
            }
            increment(wins, preferred);
            increment(losses, rejected);
            comparisons++;
            comparisonIds.add(comparisonId);
            while (comparisonIds.size() > MAX_COMPARISON_IDS) {
                comparisonIds.remove(0);
            }
            return true;
        }

        String compactSummary() {
            if (!ready()) {
                return String.format(Locale.US,
                        "%d/3 level-matched choices recorded; learning has not influenced mastering yet",
                        comparisons);
            }
            String strongest = null;
            double strongestBonus = 0.0;
            for (String strategy : wins.keySet()) {
                double bonus = bonusFor(strategy);
                if (Math.abs(bonus) > Math.abs(strongestBonus)) {
                    strongestBonus = bonus;
                    strongest = strategy;
                }
            }
            return strongest == null
                    ? comparisons + " choices recorded; no consistent strategy preference yet"
                    : String.format(Locale.US,
                    "%d choices recorded; strongest learned bias %s %+.3f score",
                    comparisons, strongest, strongestBonus);
        }

        String toJson() {
            try {
                JSONObject root = new JSONObject();
                root.put("schema", 1);
                root.put("comparisons", comparisons);
                root.put("wins", map(wins));
                root.put("losses", map(losses));
                root.put("comparison_ids", new JSONArray(comparisonIds));
                return root.toString();
            } catch (JSONException impossible) {
                return "{}";
            }
        }

        static Profile fromJson(String json) {
            Profile profile = new Profile();
            if (json == null || json.trim().isEmpty()) return profile;
            try {
                JSONObject root = new JSONObject(json);
                profile.comparisons = Math.max(0, root.optInt("comparisons", 0));
                readMap(root.optJSONObject("wins"), profile.wins);
                readMap(root.optJSONObject("losses"), profile.losses);
                JSONArray ids = root.optJSONArray("comparison_ids");
                if (ids != null) {
                    int first = Math.max(0, ids.length() - MAX_COMPARISON_IDS);
                    for (int i = first; i < ids.length(); i++) {
                        String id = ids.optString(i, null);
                        if (id != null && !id.isEmpty()) profile.comparisonIds.add(id);
                    }
                }
            } catch (JSONException ignored) {
                return new Profile();
            }
            return profile;
        }
    }

    private DennisPreferenceMemory() {}

    static String comparisonId(FullAiMasteringEngine.Candidate first,
                               FullAiMasteringEngine.Candidate second) {
        if (first == null || second == null || first.render == null
                || second.render == null) return null;
        String a = first.render.master.verification.sourceSha256;
        String b = second.render.master.verification.sourceSha256;
        if (a == null || b == null) return null;
        return a.compareTo(b) <= 0 ? a + ":" + b : b + ":" + a;
    }

    static List<String> knownStrategies(Profile profile) {
        ArrayList<String> out = new ArrayList<>();
        if (profile == null) return out;
        for (String key : profile.wins.keySet()) if (!out.contains(key)) out.add(key);
        for (String key : profile.losses.keySet()) if (!out.contains(key)) out.add(key);
        return out;
    }

    private static JSONObject map(Map<String, Integer> values) throws JSONException {
        JSONObject out = new JSONObject();
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            out.put(entry.getKey(), entry.getValue());
        }
        return out;
    }

    private static void readMap(JSONObject input, Map<String, Integer> output) {
        if (input == null) return;
        JSONArray names = input.names();
        if (names == null) return;
        for (int i = 0; i < names.length(); i++) {
            String name = names.optString(i, null);
            if (name == null) continue;
            int value = Math.max(0, input.optInt(name, 0));
            if (value > 0) output.put(name, value);
        }
    }

    private static void increment(Map<String, Integer> values, String key) {
        values.put(key, value(values, key) + 1);
    }

    private static int value(Map<String, Integer> values, String key) {
        Integer value = values.get(key);
        return value == null ? 0 : value;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
