#!/usr/bin/env python3
"""v0.4.40 experimental all-in-one studio master.

Post-v0.4.39; changes are confined to a new standalone package.
Approved Photo Master AI v0.4.37 / AutoPhoto Polished remain unchanged.
"""
from pathlib import Path
import shutil
import sys

if len(sys.argv)!=2:
    raise SystemExit("usage: apply_v0440_studio_lab.py <project>")
project=Path(sys.argv[1])
gradle_file=project/"app/build.gradle"
gradle=gradle_file.read_text()

def replace_once(txt,before,after,label):
    n=txt.count(before)
    if n!=1:
        raise RuntimeError(f"{label} anchor count: {n}")
    return txt.replace(before,after,1)

gradle=replace_once(gradle,"versionCode 48","versionCode 49","version code")
gradle=replace_once(gradle,"versionName '0.4.39-p3-neural-lab'",
                    "versionName '0.4.40-studio-lab'","version name")
gradle=replace_once(gradle,
                    "applicationId 'com.dennis.photomasterai.neurallab.p3'",
                    "applicationId 'com.dennis.photomasterai.studiolab'","separate ID")
gradle_file.write_text(gradle)

manifest=project/"app/src/main/AndroidManifest.xml"
s=manifest.read_text()
s=replace_once(s,'android:label="Photo Master AI P3 Neural Lab"',
               'android:label="Photo Master AI Studio Lab"',"app label")
manifest.write_text(s)

root=project/"app/src/main/java/com/dennis/photomasterai"
for name in ("StudioFinishPolicy.java","StudioFinish.java"):
    shutil.copyfile(Path("tools/studio")/name,root/name)

# Improved neural diagnostics and four adaptive strengths. No weakening
# of channel, clipping, detail retention or ringing acceptance limits.
engine_file=root/"AdaptiveNeuralColour.java"
eng=engine_file.read_text()
eng=replace_once(eng,
    "private static final float[] STRENGTHS = {.28f, .20f, .12f};",
    "private static final float[] STRENGTHS = {.40f, .28f, .20f, .12f};",
    "adaptive neural strengths")
eng=replace_once(eng,
    'String rejection = "no strength passed QC";',
    '''String rejection = "no strength passed QC";
            StringBuilder attemptTrace = new StringBuilder();
            attemptTrace.append("\\n• Source profile: ")
                    .append(a.getName())
                    .append(" | mastered: ").append(b.getName())
                    .append(" | route: ").append(p3Path ? "native P3" : "sRGB")
                    .append("\\n• Coefficients ")
                    .append(String.format(Locale.US,"%.4f / %.4f / %.4f",
                            coeff[0],coeff[1],coeff[2]));''',
    "neural stage diagnostics")
eng=replace_once(eng,
    '''                if (pass) {
                    String info = String.format(Locale.US,''',
    '''                attemptTrace.append(String.format(Locale.US,
                        "\\n• Trial %.2f: %s | detail %.2f%% | ring %.4f→%.4f"
                        + " | clipped S %.4f→%.4f H %.4f→%.4f"
                        + " | mean channel %.5f | max %d/255 | edits %d",
                        strength, pass?"ACCEPTED":"rejected",
                        100*detailRatio,before.ringing,after.ringing,
                        before.shadowClip,after.shadowClip,
                        before.highlightClip,after.highlightClip,
                        correction.meanChannelMove,correction.maxChannelEdit,
                        correction.editedPixels));
                if (pass) {
                    String info = String.format(Locale.US,''',
    "record each attempted strength")
eng=replace_once(eng,
    '''                            + "\\n• AI MAX baseline preserved as rollback candidate"
                            + "\\n• Colour pipeline: " + (p3Path''',
    '''                            + "\\n• AI MAX baseline preserved as rollback candidate"
                            + attemptTrace.toString()
                            + "\\n• Colour pipeline: " + (p3Path''',
    "accepted report diagnostics")
eng=replace_once(eng,
    '''return withReport(baseline, "ROLLED BACK — " + rejection
                    + "\\n• Unmodified AI MAX output retained");''',
    '''return withReport(baseline, "ROLLED BACK — " + rejection
                    + attemptTrace.toString()
                    + "\\n• Unmodified AI MAX output retained");''',
    "full rollback attempts report")
eng=replace_once(eng,
    '''+ failure.getClass().getSimpleName() + ")"
                    + "\\n• Unmodified AI MAX output retained");''',
    '''+ failure.getClass().getSimpleName() + ": "
                    + String.valueOf(failure.getMessage()) + ")"
                    + "\\n• Unmodified AI MAX output retained");''',
    "neural runtime diagnostic error")

for inv in (
    "MIN_DETAIL_RETAINED = .985","MAX_RING_INCREASE = .006",
    "MAX_MEAN_CHANNEL_MOVE = .007","MAX_CHANNEL_EDIT = 12",
    "MAX_CLIP_INCREASE = .002", "renderP3(original, mastered, lut, strength)",
    "verifyP3JpegEncoder()", "copyPixelsFromBuffer(output)",
    "MAX_CHANNEL_EDIT = 12", "ONNX Image-Adaptive 3D LUT"
):
    if inv not in eng: raise RuntimeError("Neural safeguard missing: "+inv)
engine_file.write_text(eng)

ui_file=root/"MainActivity.java"
ui=ui_file.read_text()
ui=replace_once(ui,
    'text("PHOTO MASTER AI • P3 NEURAL LAB", 23, TEXT, true)',
    'text("PHOTO MASTER AI • STUDIO LAB", 23, TEXT, true)',"lab title")
ui=replace_once(ui,
    '    private boolean neuralColourExperiment = false;',
    '''    private boolean neuralColourExperiment = false;
    private boolean studioMasterExperiment = false;''',"independent studio selection")
ui=replace_once(ui,
    '''"AI MAX", "AI MAX + LUT"};''',
    '''"AI MAX", "AI MAX + LUT", "AI MAX + STUDIO"};''',
    "new mode label")
ui=replace_once(ui,
    'neuralColourExperiment = "AI MAX + LUT".equals(tag);',
    '''neuralColourExperiment = "AI MAX + LUT".equals(tag);
            studioMasterExperiment = "AI MAX + STUDIO".equals(tag);''',
    "new mode selection")
ui=replace_once(ui,
    'else if ("AI MAX".equals(tag) || "AI MAX + LUT".equals(tag)) currentMode = PhotoMasterEngine.Mode.AI_MAX;',
    'else if ("AI MAX".equals(tag) || "AI MAX + LUT".equals(tag) || "AI MAX + STUDIO".equals(tag)) currentMode = PhotoMasterEngine.Mode.AI_MAX;',
    "AI MAX mapped unchanged")
ui=replace_once(ui,
    'dp("AI MAX + LUT".equals(labels[i]) ? 180 : labels[i].startsWith("AI") ? 132 : 105)',
    'dp("AI MAX + STUDIO".equals(labels[i]) ? 205 : "AI MAX + LUT".equals(labels[i]) ? 180 : labels[i].startsWith("AI") ? 132 : 105)',
    "wide toggle")
ui=replace_once(ui,
    'final boolean useNeuralColourExperiment = neuralColourExperiment;',
    '''final boolean useNeuralColourExperiment = neuralColourExperiment;
        final boolean useStudioMaster = studioMasterExperiment;''',
    "snapshot selected studio mode")

old='''                final PhotoMasterEngine.Result displayedResult =
                        useNeuralColourExperiment
                                ? AdaptiveNeuralColour.apply(this, originalBitmap, result)
                                : result;'''
new='''                final PhotoMasterEngine.Result neuralOrBaseline =
                        (useNeuralColourExperiment || useStudioMaster)
                                ? AdaptiveNeuralColour.apply(this, originalBitmap, result)
                                : result;
                final PhotoMasterEngine.Result displayedResult =
                        useStudioMaster
                                ? StudioFinish.apply(originalBitmap, neuralOrBaseline)
                                : neuralOrBaseline;

                // Keep only the accepted final result. The original image
                // is never recycled; rejected stages keep their baseline.
                if (neuralOrBaseline.bitmap != result.bitmap
                        && neuralOrBaseline.bitmap != displayedResult.bitmap
                        && neuralOrBaseline.bitmap != originalBitmap
                        && !neuralOrBaseline.bitmap.isRecycled())
                    neuralOrBaseline.bitmap.recycle();
                if (result.bitmap != displayedResult.bitmap
                        && result.bitmap != originalBitmap
                        && !result.bitmap.isRecycled())
                    result.bitmap.recycle();'''
ui=replace_once(ui,old,new,"separately gated neural/studio stages")
ui=replace_once(ui,
    '(useNeuralColourExperiment ? " + LUT LAB" : "")',
    '(useStudioMaster ? " • STUDIO LAB" : useNeuralColourExperiment ? " + LUT LAB" : "")',
    "new preview mode label")
ui=replace_once(ui,
    '(neuralColourExperiment ? " + LUT LAB" : "")',
    '(studioMasterExperiment ? " • STUDIO LAB" : neuralColourExperiment ? " + LUT LAB" : "")',
    "showMaster mode label")
ui=replace_once(ui,
    '(neuralColourExperiment ? "-NeuralLUT-LAB" : "")',
    '(studioMasterExperiment ? "-StudioMaster-LAB" : neuralColourExperiment ? "-NeuralLUT-LAB" : "")',
    "save suffix")
for inv in (
    "StudioFinish.apply(originalBitmap, neuralOrBaseline)",
    "AdaptiveNeuralColour.apply(this, originalBitmap, result)",
    "studioMasterExperiment = \"AI MAX + STUDIO\".equals(tag)",
    "AI MAX + STUDIO","PhotoMasterEngine.Mode.AI_MAX",
    "applyNightLightControl(", "applyPerceptualImprovementController(",
):
    if inv not in ui: raise RuntimeError("Studio/UI invariant missing: "+inv)
ui_file.write_text(ui)

host=project/"tools/StudioFinishPolicyHostChecks.java"
host.write_text("""package com.dennis.photomasterai;

public final class StudioFinishPolicyHostChecks {
    static void ok(boolean value,String name) {
        if(!value) throw new AssertionError(name);
    }
    public static void main(String[] ignored) {
        StudioFinishPolicy p = null;
        ok(StudioFinishPolicy.MAX_CHANNEL_EDIT == 9, "studio channel limit");
        ok(StudioFinishPolicy.MIN_DETAIL_RETAINED == .985, "detail threshold");
        ok(StudioFinishPolicy.MAX_RING_INCREASE == .006, "ring protection");
        ok(StudioFinishPolicy.MAX_MEAN_CHANNEL_DELTA == .010, "mean movement cap");
        ok(StudioFinishPolicy.MAX_CLIP_INCREASE == .0005, "clip protection");
        for (int x=0;x<=255;x++) {
            for(float texture:new float[]{0f,18f,70f}) {
                float y=StudioFinishPolicy.requestedTone(x,x,190f,texture,1f,0f);
                ok(Float.isFinite(y) && Math.abs(y)<=9f,"tone bound");
                ok(StudioFinishPolicy.channelWithTone(0,y)>=0,"shadow clipping");
                ok(StudioFinishPolicy.channelWithTone(255,y)<=255,"highlight clipping");
            }
        }
        float bright=StudioFinishPolicy.requestedTone(195f,195f,220f,5f,1f,0f);
        ok(bright<0,"highlight rolloff should reduce excessive brightness");
        float shadow=StudioFinishPolicy.requestedTone(76f,76f,184f,5f,1f,0f);
        ok(shadow>0,"subtle shadow shaping should be possible");
        float smooth=StudioFinishPolicy.requestedTone(182f,182f,205f,2f,1f,0f);
        float textured=StudioFinishPolicy.requestedTone(182f,182f,205f,80f,1f,0f);
        ok(Math.abs(textured)<Math.abs(smooth),"high texture guard");
        float skin=StudioFinishPolicy.requestedTone(170f,170f,200f,10f,1f,.9f);
        float noSkin=StudioFinishPolicy.requestedTone(170f,170f,200f,10f,1f,0f);
        ok(Math.abs(skin)<Math.abs(noSkin),"skin guard");
        System.out.println("AI Studio policy: deterministic tonal QC checks PASSED");
    }
}
""")
print("Photo Master AI v0.4.40 Studio Lab source ready; approved builds untouched")
