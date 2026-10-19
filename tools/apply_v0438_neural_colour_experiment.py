#!/usr/bin/env python3
"""Add opt-in neural colour as a SECOND, independently installable app.

Normal Photo Master AI v0.4.37 and baseline AI MAX are not modified.
Call only after applying every source patch through v0.4.37.
"""
from pathlib import Path
import re
import shutil
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: apply_v0438_neural_colour_experiment.py <project>")
project=Path(sys.argv[1])
build_file=project/"app/build.gradle"
build=build_file.read_text()
for anchor in ("versionCode 46", "versionName '0.4.37'",
               "applicationId 'com.dennis.photomasterai'",
               "dependencies {"):
    if anchor not in build:
        raise RuntimeError("Baseline Android app not verified: " + anchor)
build=build.replace("versionCode 46","versionCode 47",1)
build=build.replace("versionName '0.4.37'","versionName '0.4.38-neural-lab'",1)
build=build.replace("applicationId 'com.dennis.photomasterai'",
                    "applicationId 'com.dennis.photomasterai.neurallab'",1)
build=build.replace("dependencies {",
                    """dependencies {
    implementation 'com.microsoft.onnxruntime:onnxruntime-android:1.20.0'""",1)
build_file.write_text(build)

java_dir=project/"app/src/main/java/com/dennis/photomasterai"
shutil.copyfile("tools/neural_colour/AdaptiveNeuralColour.java",
                java_dir/"AdaptiveNeuralColour.java")

manifest=project/"app/src/main/AndroidManifest.xml"
manifest_s=manifest.read_text()
manifest_s,n=re.subn(r'android:label="[^"]+"', 'android:label="Photo Master AI Neural Lab"',
                     manifest_s,count=1)
if n!=1:
    raise RuntimeError("Manifest app label could not be independently changed")
manifest.write_text(manifest_s)

main_file=java_dir/"MainActivity.java"
main=main_file.read_text()

def substitute_once(old,new,label):
    global main
    count=main.count(old)
    if count!=1:
        raise RuntimeError(f"Ambiguous {label}: {count} occurrences")
    main=main.replace(old,new,1)

substitute_once('    private PhotoMasterEngine.Mode currentMode = PhotoMasterEngine.Mode.TRUE;',
'''    private PhotoMasterEngine.Mode currentMode = PhotoMasterEngine.Mode.TRUE;
    private boolean neuralColourExperiment = false;''',"UI experiment state")

# Preserve every old processing mode; the additional option maps to the
# existing AI MAX and only activates an independent POST-MASTER candidate.
old='String[] labels = {"TRUE", "PRO", "NAT GEO", "LIFE", "AI ENHANCE", "AI MAX"};'
new='String[] labels = {"TRUE", "PRO", "NAT GEO", "LIFE", "AI ENHANCE", "AI MAX", "AI MAX + LUT"};'
substitute_once(old,new,"AI MAX + LUT choice")
substitute_once(
    'String tag = String.valueOf(rb.getTag());',
    '''String tag = String.valueOf(rb.getTag());
            neuralColourExperiment = "AI MAX + LUT".equals(tag);''',
    "mode-switch eligibility")
substitute_once(
    'else if ("AI MAX".equals(tag)) currentMode = PhotoMasterEngine.Mode.AI_MAX;',
    'else if ("AI MAX".equals(tag) || "AI MAX + LUT".equals(tag)) currentMode = PhotoMasterEngine.Mode.AI_MAX;',
    "AI MAX mode mapping")
substitute_once(
    'modes.addView(rb, new RadioGroup.LayoutParams(dp(labels[i].startsWith("AI") ? 132 : 105), -2));',
    'modes.addView(rb, new RadioGroup.LayoutParams(dp("AI MAX + LUT".equals(labels[i]) ? 180 : labels[i].startsWith("AI") ? 132 : 105), -2));',
    "experimental mode width")
substitute_once(
    'PhotoMasterEngine.Mode mode = currentMode;',
    '''PhotoMasterEngine.Mode mode = currentMode;
        final boolean useNeuralColourExperiment = neuralColourExperiment;''',
    "selected experiment snapshot")

# Existing AI MAX pipeline, Night Light, repairs, perceptual, profile management,
# geometry and safety all run FIRST, unchanged. The colour experiment receives
# that *complete* baseline and returns it untouched on QC rejection.
marker='final String neuralReport = neuralResult == null ? null : neuralResult.report;'
substitute_once(marker,
    marker+'''
                final PhotoMasterEngine.Result displayedResult =
                        useNeuralColourExperiment
                                ? AdaptiveNeuralColour.apply(this, originalBitmap, result)
                                : result;
''',
    "independent candidate after AI MAX")
start=main.index('final PhotoMasterEngine.Result displayedResult')
end=main.index('} catch (Throwable e)',start)
old_block=main[start:end]
if "result.bitmap" not in old_block or "result.report" not in old_block:
    raise RuntimeError("UI results pipeline changed; refuse silent patch")
new_block=old_block.replace("result.bitmap","displayedResult.bitmap")
new_block=new_block.replace("result.report","displayedResult.report")
# Always display exactly which option was used, even if QC rolled back.
new_block=new_block.replace(
    'previewBadge.setText("MASTER • " + modeLabel(mode));',
    'previewBadge.setText("MASTER • " + modeLabel(mode) + (useNeuralColourExperiment ? " + LUT LAB" : ""));'
)
main=main[:start]+new_block+main[end:]

# User can toggle Original / Master and compare the experiment.
main=main.replace(
    'previewBadge.setText("MASTER • " + modeLabel(currentMode));',
    'previewBadge.setText("MASTER • " + modeLabel(currentMode) + (neuralColourExperiment ? " + LUT LAB" : ""));',
    1)
main=main.replace(
    'modeLabel(currentMode).replace(" ", "-") + ".jpg"',
    'modeLabel(currentMode).replace(" ", "-") + (neuralColourExperiment ? "-NeuralLUT-LAB" : "") + ".jpg"',
    1)
# A visually distinct experimental heading; zero modifications to default
# AI MAX model and processing logic.
main=main.replace('text("PHOTO MASTER AI", 26, TEXT, true)',
                  'text("PHOTO MASTER AI • NEURAL LAB", 24, TEXT, true)',1)
main_file.write_text(main)

if 'AdaptiveNeuralColour.apply(this, originalBitmap, result)' not in main:
    raise RuntimeError("Neural colour experiment not wired")
if 'neuralColourExperiment = "AI MAX + LUT".equals(tag)' not in main:
    raise RuntimeError("Mode does not require explicit opt-in")
if 'PhotoMasterEngine.master(processingBitmap' not in main:
    raise RuntimeError("Original AI MAX processing call was lost")
if 'NightLightControlIntelligence.process(' not in main:
    raise RuntimeError("Night Light safety pipeline was lost")
if 'MAX_MEAN_CHANNEL_DELTA = 0.0080' not in (java_dir/"NightLightControlPolicy.java").read_text():
    raise RuntimeError("Existing Night Light channel QC changed")
print("Neural Lab v0.4.38 source created as separately installable opt-in AI MAX + LUT experiment")
