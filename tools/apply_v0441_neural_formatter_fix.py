#!/usr/bin/env python3
"""v0.4.41: fix accepted-neural reporting crash without changing imaging DSP.

The 0.4.40 patch inserted a preformatted per-trial report into the
String.format() FORMAT argument. Its literal "98.6%" values are re-parsed
as format directives, yielding UnknownFormatConversionException.
Keep ALL per-trial diagnostics outside the format argument, preserving
neural acceptance and all QC thresholds.
"""
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: apply_v0441_neural_formatter_fix.py <project>")
root=Path(sys.argv[1])
gradle=root/"app/build.gradle"
build=gradle.read_text()
if build.count("versionCode 49")!=1 or build.count("versionName '0.4.40-studio-lab'")!=1:
    raise RuntimeError("Expected successful 0.4.40 Studio baseline")
if "applicationId 'com.dennis.photomasterai.studiolab'" not in build:
    raise RuntimeError("Unexpected Studio Lab app identity")
build=build.replace("versionCode 49","versionCode 50",1)
build=build.replace("versionName '0.4.40-studio-lab'",
                    "versionName '0.4.41-studio-lab'",1)
gradle.write_text(build)

java=root/"app/src/main/java/com/dennis/photomasterai/AdaptiveNeuralColour.java"
s=java.read_text()
bad='''                            + "\\n• AI MAX baseline preserved as rollback candidate"
                            + attemptTrace.toString()
                            + "\\n• Colour pipeline: " + (p3Path'''
good='''                            + "\\n• AI MAX baseline preserved as rollback candidate"
                            + "\\n• Colour pipeline: " + (p3Path'''
if s.count(bad)!=1:
    raise RuntimeError("Expected bad accepted-candidate format argument not found")
s=s.replace(bad,good,1)

old='''correction.bitmap, baseline.report + "\\n\\nNEURAL COLOUR EXPERIMENT\\n• " + info,
                            after);'''
new='''correction.bitmap, baseline.report + "\\n\\nNEURAL COLOUR EXPERIMENT\\n• "
                                    + info + attemptTrace.toString(),
                            after);'''
if s.count(old)!=1:
    raise RuntimeError("Expected neural ACCEPTED result anchor missing")
s=s.replace(old,new,1)

# This is intentionally the *only* change in the neural processor:
# no render, source profile, neural coefficients, LUT strengths, or QC.
for invariant in (
    'STRENGTHS = {.40f, .28f, .20f, .12f}',
    'MIN_DETAIL_RETAINED = .985',
    'MAX_RING_INCREASE = .006',
    'MAX_MEAN_CHANNEL_MOVE = .007',
    'MAX_CLIP_INCREASE = .002',
    'MAX_CHANNEL_EDIT = 12',
    'renderP3(original, mastered, lut, strength)',
    'verifyP3JpegEncoder()',
    'StringBuilder attemptTrace = new StringBuilder()',
    '+ info + attemptTrace.toString(),',
):
    if invariant not in s:
        raise RuntimeError("Neural safety invariant missing: "+invariant)
java.write_text(s)

studio=root/"app/src/main/java/com/dennis/photomasterai/StudioFinishPolicy.java"
studio_source=studio.read_text()
for v in ("MIN_DETAIL_RETAINED = 0.985",
          "MAX_CHANNEL_EDIT = 9",
          "MAX_MEAN_CHANNEL_DELTA = 0.010",
          "MAX_CLIP_INCREASE = 0.0005"):
    if v not in studio_source:
        raise RuntimeError("Studio baseline invariant changed: "+v)
print("v0.4.41 accepted-neural report formatter fixed; all DSP and QC unchanged")
