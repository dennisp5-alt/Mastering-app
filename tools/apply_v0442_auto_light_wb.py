#!/usr/bin/env python3
"""Photo Master AI v0.4.42 Studio Lab: bulk genuine Auto Light + Auto WB.

Original AI MAX, existing Neural LUT, Night Light and photo QC are
unchanged. Two new independent post-master stages only run when the
user explicitly selects AI MAX + STUDIO.
"""
from pathlib import Path
import shutil
import sys

if len(sys.argv)!=2: raise SystemExit("usage: apply_v0442_auto_light_wb.py <project>")
project=Path(sys.argv[1])
buildfile=project/"app/build.gradle"
build=buildfile.read_text()
for anchor in ("versionCode 50","versionName '0.4.41-studio-lab'",
               "applicationId 'com.dennis.photomasterai.studiolab'"):
    if build.count(anchor)!=1:
        raise RuntimeError("v0.4.41 Studio Lab baseline not verified: "+anchor)
build=build.replace("versionCode 50","versionCode 51",1)
build=build.replace("versionName '0.4.41-studio-lab'",
                    "versionName '0.4.42-studio-auto-wb-lab'",1)
buildfile.write_text(build)

javadir=project/"app/src/main/java/com/dennis/photomasterai"
for name in ("AutoTonePolicy.java","IntelligentAuto.java"):
    shutil.copyfile(Path("tools/studio")/name,javadir/name)

# Keep original behaviour accessible through AI MAX and AI MAX + LUT;
# only the explicit AI MAX + STUDIO path adds 2 new Auto stages.
mainfile=javadir/"MainActivity.java"
main=mainfile.read_text()
before='''                final PhotoMasterEngine.Result neuralOrBaseline =
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
after='''                // The two real Auto decisions are independent: the second
                // keeps the accepted Light output if WB is withheld or fails QC.
                // Neither process modifies the existing master in-place.
                final PhotoMasterEngine.Result autoLightResult =
                        useStudioMaster
                                ? IntelligentAuto.autoTone(originalBitmap, result)
                                : result;
                final PhotoMasterEngine.Result autoWhiteResult =
                        useStudioMaster
                                ? IntelligentAuto.autoWhiteBalance(
                                        originalBitmap, autoLightResult)
                                : autoLightResult;

                final PhotoMasterEngine.Result neuralOrBaseline =
                        (useNeuralColourExperiment || useStudioMaster)
                                ? AdaptiveNeuralColour.apply(
                                        this, originalBitmap, autoWhiteResult)
                                : autoWhiteResult;
                final PhotoMasterEngine.Result displayedResult =
                        useStudioMaster
                                ? StudioFinish.apply(originalBitmap, neuralOrBaseline)
                                : neuralOrBaseline;

                // Explicitly retire successful intermediate candidates
                // only after the final bitmap has been selected. Never
                // recycle the original or the accepted final image.
                android.graphics.Bitmap[] intermediate = {
                        result.bitmap,
                        autoLightResult.bitmap,
                        autoWhiteResult.bitmap,
                        neuralOrBaseline.bitmap
                };
                java.util.IdentityHashMap<android.graphics.Bitmap, Boolean>
                        retired = new java.util.IdentityHashMap<>();
                for (android.graphics.Bitmap tmp : intermediate) {
                    if (tmp != null && tmp != originalBitmap
                            && tmp != displayedResult.bitmap
                            && !retired.containsKey(tmp)
                            && !tmp.isRecycled()) {
                        tmp.recycle();
                        retired.put(tmp, Boolean.TRUE);
                    }
                }'''
if main.count(before)!=1:
    raise RuntimeError("Exact existing independent Studio + neural integration missing")
main=main.replace(before,after,1)
assert 'AI MAX + STUDIO' in main and "AI MAX + LUT" in main
assert 'PhotoMasterEngine.Mode.AI_MAX' in main
assert 'applyNightLightControl(' in main
assert 'applyPerceptualImprovementController(' in main
assert 'IntelligentAuto.autoTone(' in main
assert 'IntelligentAuto.autoWhiteBalance(' in main
mainfile.write_text(main)

for name,pieces in {
    "AutoTonePolicy.java":[
        "MIN_DETAIL_RETAINED = .985",
        "MAX_RING_INCREASE = .006",
        "MAX_CLIP_INCREASE = .0010",
        "firelight",
        "shadowLift=11f"
    ],
    "IntelligentAuto.java":[
        "autoTone(","autoWhiteBalance(",
        "ColorSpace.Named.DISPLAY_P3",
        "PhotoAnalysis.analyse(",
        "WB_MAX_CHANNEL_EDIT=17",
        "return report(base,",
        "intentional warm firelight",
        "MIN_DETAIL_RETAINED",
        "nativeOut.copy",
    ]
}.items():
    text=(javadir/name).read_text()
    for item in pieces:
        if item=="nativeOut.copy":continue
        if item not in text:
            raise RuntimeError(f"{name} safety invariant missing: {item}")
studio=(javadir/"StudioFinishPolicy.java").read_text()
if "MIN_DETAIL_RETAINED = 0.985" not in studio:
    raise RuntimeError("Existing Studio QC changed")
neural=(javadir/"AdaptiveNeuralColour.java").read_text()
for marker in (
    "MIN_DETAIL_RETAINED = .985",
    "MAX_RING_INCREASE = .006",
    "ONNX Image-Adaptive 3D LUT"
):
    if marker not in neural:
        raise RuntimeError("Existing neural QC changed")
print("Photo Master AI v0.4.42 Auto Light + White Balance:"
      " explicit Studio-only mode, independent QC and fallback; AI MAX unchanged")
