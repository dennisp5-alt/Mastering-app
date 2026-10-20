#!/usr/bin/env python3
"""Photo Master AI v0.4.43 experimental Studio Lab cumulative colour QC.

One isolated final stage prevents a bright pale-pink highlight region
from accumulating Auto Light + White Balance + LUT + Studio adjustments.
All four successful engines, modes, and existing QC remain unchanged.
"""
from pathlib import Path
import shutil
import sys

if len(sys.argv)!=2:
    raise SystemExit("usage: apply_v0443_cumulative_colour_guard.py <project>")
root=Path(sys.argv[1])
gradle=root/"app/build.gradle"
build=gradle.read_text()
for required in (
    "versionCode 51",
    "versionName '0.4.42-studio-auto-wb-lab'",
    "applicationId 'com.dennis.photomasterai.studiolab'",
):
    if build.count(required)!=1:
        raise RuntimeError("Expected signed Studio Lab v0.4.42 baseline: "+required)
gradle.write_text(build.replace("versionCode 51","versionCode 52",1)
    .replace("versionName '0.4.42-studio-auto-wb-lab'",
             "versionName '0.4.43-studio-colour-guard'",1))

java=root/"app/src/main/java/com/dennis/photomasterai"
for filename in ("CumulativeColourGuard.java","SelectiveColourPolicy.java"):
    shutil.copyfile("tools/studio/"+filename,java/filename)

ui_file=java/"MainActivity.java"
ui=ui_file.read_text()
before='''                final PhotoMasterEngine.Result displayedResult =
                        useStudioMaster
                                ? StudioFinish.apply(originalBitmap, neuralOrBaseline)
                                : neuralOrBaseline;

                // Explicitly retire successful intermediate candidates'''
after='''                final PhotoMasterEngine.Result preGuardResult =
                        useStudioMaster
                                ? StudioFinish.apply(originalBitmap, neuralOrBaseline)
                                : neuralOrBaseline;
                final PhotoMasterEngine.Result displayedResult =
                        useStudioMaster
                                ? CumulativeColourGuard.apply(
                                        originalBitmap, result, preGuardResult)
                                : preGuardResult;

                // Explicitly retire successful intermediate candidates'''
if ui.count(before)!=1:
    raise RuntimeError("Studio master output insertion point missing")
ui=ui.replace(before,after,1)
before='''                        autoWhiteResult.bitmap,
                        neuralOrBaseline.bitmap
                };'''
after='''                        autoWhiteResult.bitmap,
                        neuralOrBaseline.bitmap,
                        preGuardResult.bitmap
                };'''
if ui.count(before)!=1:
    raise RuntimeError("Expected intermediate bitmap ownership list missing")
ui=ui.replace(before,after,1)

for invariant in (
    "AI MAX + STUDIO",
    "AI MAX + LUT",
    "IntelligentAuto.autoTone(",
    "IntelligentAuto.autoWhiteBalance(",
    "AdaptiveNeuralColour.apply(",
    "StudioFinish.apply(",
    "CumulativeColourGuard.apply(",
    "useStudioMaster",
    "retired.containsKey(tmp)",
    "applyNightLightControl(",
):
    if invariant not in ui:raise RuntimeError("Lost existing pipeline "+invariant)
ui_file.write_text(ui)

for required in (
    "MIN_DETAIL_RETAINED = .985",
    "MAX_RING_INCREASE = .006",
    "MAX_REGIONAL_MEAN_DELTA = .020",
    "MAX_PINK_NEARCLIP_GROWTH = .015",
    "palePinkProtection(",
    "MAX_PINK_CHANNEL_DELTA = 5"
):
    if required not in (java/"SelectiveColourPolicy.java").read_text():
        raise RuntimeError("Regional policy QC has changed: "+required)

studio=(java/"StudioFinishPolicy.java").read_text()
neural=(java/"AdaptiveNeuralColour.java").read_text()
auto=(java/"IntelligentAuto.java").read_text()
night=(java/"NightLightControlPolicy.java").read_text()
for v in (
    (studio,"MIN_DETAIL_RETAINED = 0.985"),
    (neural,"MIN_DETAIL_RETAINED = .985"),
    (auto,"AUTO LIGHT"),
    (auto,"AUTO WHITE BALANCE"),
    (night,"MAX_CHANNEL_DELTA = 0.0320"),
    (night,"MAX_MEAN_CHANNEL_DELTA = 0.0080")
):
    if v[1] not in v[0]:
        raise RuntimeError("Approved processing/QC guard unexpectedly altered")
print("v0.4.43: source-colour-referenced pink/fabric protection + regional"
      " cumulative QC added after unchanged four AI Studio stages.")
