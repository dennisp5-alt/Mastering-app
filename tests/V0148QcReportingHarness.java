package com.dennis.aimasteringlab;

import org.json.JSONObject;

public final class V0148QcReportingHarness {
    private static void req(boolean b,String why) { if(!b) throw new AssertionError(why); }
    public static void main(String[] args) throws Exception {
        JSONObject changed=StudioQcReporting.masteringFlags(true,true);
        req(changed.getBoolean("base_mastering_changed_audio"),"adaptive master must be marked changed");
        req(changed.getBoolean("delivery_edit_stage_passthrough"),"no additional edits must be reflected");
        req(!changed.getBoolean("audio_unchanged_before_delivery_gain"),"a verified broad master must not be labelled unchanged");
        req("verified_base_master".equals(changed.getString("delivery_input_source")),"passthrough source must be explicit");
        JSONObject transparent=StudioQcReporting.masteringFlags(false,true);
        req(transparent.getBoolean("audio_unchanged_before_delivery_gain"),"transparent control must remain unchanged");
        JSONObject studioEdited=StudioQcReporting.masteringFlags(false,false);
        req(!studioEdited.getBoolean("audio_unchanged_before_delivery_gain"),"edits change transparent base");
        JSONObject both=StudioQcReporting.masteringFlags(true,false);
        req(!both.getBoolean("audio_unchanged_before_delivery_gain"),"both stages changed audio");
        req("0.14.8".equals(StudioQcReporting.APP_VERSION),"app version must be current");
        System.out.println("V0148_QC_REPORTING_PASS 8 checks: unambiguous base-master/edit semantics");
    }
}
