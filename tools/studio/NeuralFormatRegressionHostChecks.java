package com.dennis.photomasterai;

import java.util.Locale;

/**
 * Regression: a preformatted trace contains human-readable percentages.
 * It must NEVER be concatenated into a subsequent String.format FORMAT
 * argument; Java then treats literal "98.55%" as another format directive.
 */
public final class NeuralFormatRegressionHostChecks {
    private static void check(boolean good, String message) {
        if (!good) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        StringBuilder trace=new StringBuilder();
        trace.append("\n• Source profile: sRGB | mastered: sRGB | route: sRGB");
        trace.append(String.format(Locale.US,
                "\n• Trial %.2f: %s | detail %.2f%% | ring %.4f→%.4f"
                + " | clipped S %.4f→%.4f H %.4f→%.4f"
                + " | mean channel %.5f | max %d/255 | edits %d",
                .40,"ACCEPTED",98.55,1.0,1.0,
                .002,.002,.001,.001,.005,5,11743L));
        String safeInfo=String.format(Locale.US,
                "ACCEPTED • ONNX Image-Adaptive 3D LUT • strength %.2f"
                + "\n• Learned weights %.4f | %.4f | %.4f"
                + "\n• Candidate pixels %.2f%% | mean channel move %.5f | max edit %d/255"
                + "\n• Detail retention %.2f%% | ringing %.4f -> %.4f"
                + "\n• Neural inference/total %.0f ms (total including render and QC)"
                + "\n• AI MAX baseline preserved as rollback candidate"
                + "\n• Colour pipeline: sRGB neural LUT path",
                .40,.2,.3,.5,68.45,.005,5,98.55,1.0,1.0,433.0);
        String complete=safeInfo+trace.toString();
        check(complete.contains("ACCEPTED • ONNX"),"acceptance missing");
        check(complete.contains("detail 98.55%"),"trial percentage missing");
        check(complete.contains("Candidate pixels 68.45%"),"candidate percentage missing");
        check(complete.contains("edits 11743"),"trial edited pixels missing");

        boolean oldBugCaught=false;
        try {
            String.format(Locale.US,"Formatted info %.2f"+trace, .4);
        } catch(java.util.UnknownFormatConversionException expected) {
            oldBugCaught=true;
        }
        check(oldBugCaught,"test fixture did not reproduce the original exception");
        System.out.println("PASS neural ACCEPTED report: percentages stay outside format string");
    }
}
