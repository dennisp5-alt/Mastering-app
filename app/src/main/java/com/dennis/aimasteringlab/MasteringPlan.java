package com.dennis.aimasteringlab;

import org.json.JSONException;
import org.json.JSONObject;

final class MasteringPlan {
    String sourceSha256;
    double gainDb;
    String note;

    static MasteringPlan parse(String text) throws JSONException {
        JSONObject json = new JSONObject(text);
        String schema = json.optString("schema", "");
        if (!"dennis.mastering.plan.v0.1".equals(schema)) {
            throw new JSONException("Expected schema dennis.mastering.plan.v0.1");
        }
        MasteringPlan plan = new MasteringPlan();
        plan.sourceSha256 = json.getString("source_sha256");
        plan.gainDb = json.getDouble("gain_db");
        plan.note = json.optString("note", "Imported mastering plan");
        if (!Double.isFinite(plan.gainDb) || plan.gainDb < -30.0 || plan.gainDb > 30.0) {
            throw new JSONException("gain_db is outside the permitted range");
        }
        return plan;
    }
}
