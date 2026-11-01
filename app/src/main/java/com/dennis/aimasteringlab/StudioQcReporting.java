package com.dennis.aimasteringlab;

import org.json.JSONObject;

/** QC semantics only: separate the mastered base from additional Studio edits. */
final class StudioQcReporting {
    static final String APP_VERSION = BuildConfig.VERSION_NAME;

    private StudioQcReporting() {}

    static JSONObject masteringFlags(boolean baseMasterChanged, boolean deliveryEditsBypassed) throws org.json.JSONException {
        return new JSONObject()
                .put("base_mastering_changed_audio", baseMasterChanged)
                .put("delivery_edit_stage_passthrough", deliveryEditsBypassed)
                .put("audio_unchanged_before_delivery_gain", !baseMasterChanged && deliveryEditsBypassed)
                .put("audio_unchanged_before_delivery_gain_scope",
                        "Whole processing chain: base mastering plus Studio edits; delivery gain excluded")
                .put("delivery_input_source", deliveryEditsBypassed
                        ? "verified_base_master" : "studio_edited_premaster");
    }
}
