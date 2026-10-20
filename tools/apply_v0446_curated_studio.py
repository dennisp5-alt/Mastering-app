#!/usr/bin/env python3
"""Photo Master AI v0.4.46: curated Studio interface, reversible workflow.

Only expose the two meaningful workflows: Studio and AI MAX reference.
Do not change the underlying rendering engines, neural model, QC limits,
colour management, export, safety fallbacks, or on-device project data.
"""
from pathlib import Path
import sys
if len(sys.argv)!=2:
    raise SystemExit("usage: apply_v0446_curated_studio.py <project>")
root=Path(sys.argv[1])
gradle=root/"app/build.gradle"
b=gradle.read_text()
for expected in ("versionCode 54", "versionName '0.4.45-pastel-qc-fastpath'",
                 "applicationId 'com.dennis.photomasterai.studiolab'"):
    if b.count(expected)!=1: raise RuntimeError("v0.4.45 source mismatch: "+expected)
gradle.write_text(b.replace("versionCode 54","versionCode 55",1)
    .replace("versionName '0.4.45-pastel-qc-fastpath'",
             "versionName '0.4.46-curated-studio'",1))

java=root/"app/src/main/java/com/dennis/photomasterai"
mainfile=java/"MainActivity.java"
s=mainfile.read_text()

def change(old,new,why):
    global s
    n=s.count(old)
    if n!=1: raise RuntimeError("%s expected once; found %s"%(why,n))
    s=s.replace(old,new,1)

# The original TRUE/PRO/NAT GEO/LIFE/AI ENHANCE and stand-alone LUT
# engines remain intact, but their toggles are not exposed here.
# Studio is the only experimental finishing path; AI MAX is the control.
change('String[] labels = {"TRUE", "PRO", "NAT GEO", "LIFE", "AI ENHANCE", "AI MAX", "AI MAX + LUT", "AI MAX + STUDIO"};',
       'String[] labels = {"AI MAX + STUDIO", "AI MAX"};',
       "curate visible modes")
change('private PhotoMasterEngine.Mode currentMode = PhotoMasterEngine.Mode.TRUE;',
       'private PhotoMasterEngine.Mode currentMode = PhotoMasterEngine.Mode.AI_MAX;',
       "default AI MAX engine")
change('private boolean studioMasterExperiment = false;',
       'private boolean studioMasterExperiment = true;',
       "default visible Studio mode")
change('neuralColourExperiment = "AI MAX + LUT".equals(tag);',
       'neuralColourExperiment = false; // retired standalone LUT toggle in curated UI',
       "disable unexposed LUT mode")
# The selected Studio mode still runs learned neural colour, as part
# of the established stage. No feature is disabled inside Studio.
# Reduce UI choice widths to two simple buttons while retaining touch area.
change('dp("AI MAX + STUDIO".equals(labels[i]) ? 205 : "AI MAX + LUT".equals(labels[i]) ? 180 : labels[i].startsWith("AI") ? 132 : 105)',
       'dp("AI MAX + STUDIO".equals(labels[i]) ? 205 : 132)',
       "curated mode selector layout")

# The old code deliberately treats all seven engines as maintained modes.
# Don't remove their Kotlin/Java classes because their historical recovery
# and QC scaffolding still use those source modules.
for invariant in (
    'String[] labels = {"AI MAX + STUDIO", "AI MAX"};',
    "PhotoMasterEngine.Mode.AI_MAX",
    'studioMasterExperiment = "AI MAX + STUDIO".equals(tag);',
    'useStudioMaster',
    'IntelligentAuto.autoTone(',
    'IntelligentAuto.autoWhiteBalance(',
    'AdaptiveNeuralColour.apply(',
    'StudioFinish.apply(',
    'CumulativeColourGuard.apply(',
    'PhotoAnalysisCache.clear();',
    'STUDIO PERFORMANCE',
    'previewBadge.setText(',
):
    if invariant not in s: raise RuntimeError("Lost required Studio/UI invariant: "+invariant)
if "private boolean studioMasterExperiment = true;" not in s:
    raise RuntimeError("Studio not initial mode")
if "private PhotoMasterEngine.Mode currentMode = PhotoMasterEngine.Mode.AI_MAX;" not in s:
    raise RuntimeError("Initial AI MAX engine mismatch")
mainfile.write_text(s)

# Independently validate compiled policy limits, signing and colour safeguards.
for name,anchors in {
    "AutoTonePolicy.java":("MIN_DETAIL_RETAINED = .985","MAX_MEAN_CHANNEL_CHANGE = .023"),
    "StudioFinishPolicy.java":("MIN_DETAIL_RETAINED = 0.985","MAX_RING_INCREASE = .006"),
    "SelectiveColourPolicy.java":("MAX_PINK_CHANNEL_DELTA = 5","MIN_DETAIL_RETAINED = .985"),
    "AdaptiveNeuralColour.java":("MAX_RING_INCREASE = .006","MAX_CHANNEL_EDIT = 12"),
    "NightLightControlPolicy.java":("MAX_MEAN_CHANNEL_DELTA = 0.0080",),
    "PhotoAnalysisCache.java":("PhotoAnalysis.analyse(image)",),
    "CumulativeColourGuard.java":("PastelHighlightPolicy.rescue(",),
}.items():
    source=(java/name).read_text()
    for anchor in anchors:
        if anchor not in source: raise RuntimeError("Lost safety invariant "+name+": "+anchor)
print("v0.4.46: Studio + AI MAX only, Studio default, all pixel pipelines/QC retained.")
