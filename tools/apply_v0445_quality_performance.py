#!/usr/bin/env python3
"""Photo Master AI v0.4.45, conservative adaptive QC + pastel headroom.

Apply after v0.4.44 ONLY. All established neural models, pixel equations,
adaptive strengths, prior acceptance limits, P3 route, denoising, and modes
are retained. Cheap necessary-condition QC can reject without calculating
remaining expensive metrics. sRGB pale-pink source headroom rescue remains
inside existing full-image Studio acceptance/rollback guard.
"""
from pathlib import Path
import shutil
import sys
if len(sys.argv)!=2:
    raise SystemExit("usage: apply_v0445_quality_performance.py <project>")
root=Path(sys.argv[1])
build=root/"app/build.gradle"
gradle=build.read_text()
for tag in ("versionCode 53","versionName '0.4.44-studio-fastpath'",
            "applicationId 'com.dennis.photomasterai.studiolab'"):
    if gradle.count(tag)!=1:
        raise RuntimeError("Photo Master AI v0.4.44 source not verified: "+tag)
build.write_text(gradle.replace("versionCode 53","versionCode 54",1)
    .replace("versionName '0.4.44-studio-fastpath'",
             "versionName '0.4.45-pastel-qc-fastpath'",1))
java=root/"app/src/main/java/com/dennis/photomasterai"
def replace_once(source,old,new,label,count=1):
    n=source.count(old)
    if n!=count:raise RuntimeError(f"{label}: expected {count}, found {n}")
    return source.replace(old,new,count)
def read(name):
    f=java/name
    return f, f.read_text()
shutil.copyfile("tools/studio/PastelHighlightPolicy.java",java/"PastelHighlightPolicy.java")

# Auto Light: no QC analysis if candidate has ALREADY failed a hard,
# unchanged movement gate; identical winners and acceptability.
f,s=read("IntelligentAuto.java")
s=replace_once(s,
    '''                Render candidate=render(base.bitmap,a,strength,false);
                PhotoAnalysis after=PhotoAnalysisCache.analyse(candidate.bitmap);''',
    '''                Render candidate=render(base.bitmap,a,strength,false);
                if (candidate.edited==0
                        || candidate.meanMove>AutoTonePolicy.MAX_MEAN_CHANNEL_CHANGE
                        || candidate.maxMove>AutoTonePolicy.MAX_CHANNEL_EDIT) {
                    log.append(String.format(Locale.US,
                            "\\n• Light %.2f rejected by pre-QC hard movement gate"
                            +" | average RGB %.5f | peak %d/255 | edit %.1f%%"
                            +" | detailed image QC not needed",
                            strength,candidate.meanMove,candidate.maxMove,
                            candidate.edited*100.0/
                                (base.bitmap.getWidth()*(double)base.bitmap.getHeight())));
                    candidate.bitmap.recycle();
                    continue;
                }
                PhotoAnalysis after=PhotoAnalysisCache.analyse(candidate.bitmap);''',
    "Auto Light hard-gate early rejection")
s=replace_once(s,
    '''                Render candidate=render(base.bitmap,a,strength,true);
                PhotoAnalysis after=PhotoAnalysisCache.analyse(candidate.bitmap);''',
    '''                Render candidate=render(base.bitmap,a,strength,true);
                if (candidate.edited==0
                        || candidate.meanMove>WB_MAX_MEAN_MOVE
                        || candidate.maxMove>WB_MAX_CHANNEL_EDIT) {
                    log.append(String.format(Locale.US,
                            "\\n• WB %.2f rejected by pre-QC hard movement gate"
                            +" | average RGB %.5f | peak %d/255"
                            +" | detailed image QC not needed",
                            strength,candidate.meanMove,candidate.maxMove));
                    candidate.bitmap.recycle();
                    continue;
                }
                PhotoAnalysis after=PhotoAnalysisCache.analyse(candidate.bitmap);''',
    "Auto WB hard-gate early rejection")
f.write_text(s)

# Neural colour: ensure attempted movement cannot pass a hard existing gate
# BEFORE expensive detailed per-candidate QC is calculated.
f,s=read("AdaptiveNeuralColour.java")
s=replace_once(s,
    '''                PhotoAnalysis after = PhotoAnalysisCache.analyse(correction.bitmap);
                double detailRatio = before.detail > 1e-9''',
    '''                if(correction.editedPixels==0
                        || correction.meanChannelMove>MAX_MEAN_CHANNEL_MOVE
                        || correction.maxChannelEdit>MAX_CHANNEL_EDIT) {
                    attemptTrace.append(String.format(Locale.US,
                            "\\n• Trial %.2f rejected by pre-QC hard movement gate"
                            +" | mean channel %.5f | max %d/255"
                            +" | edits %d | detailed image QC not needed",
                            strength,correction.meanChannelMove,
                            correction.maxChannelEdit,correction.editedPixels));
                    correction.bitmap.recycle();
                    continue;
                }
                PhotoAnalysis after = PhotoAnalysisCache.analyse(correction.bitmap);
                double detailRatio = before.detail > 1e-9''',
    "Neural LUT hard-gate early rejection")
f.write_text(s)

# Studio finish normally passes on this photograph; don't alter it.
# Extend the *existing* cumulative render in-place; no new full-res pass.
f,s=read("CumulativeColourGuard.java")
s=replace_once(s,
    "        long brightIncrease;",
    "        long brightIncrease;\n        long pastelRecoveredPixels;",
    "New pastel diagnostic counter")
s=replace_once(s,
    '''                int b=SelectiveColourPolicy.guardChannel(b0,b1,mask);
                if(r!=r1 || g!=g1 || b!=b1)status.affected++;''',
    '''                int b=SelectiveColourPolicy.guardChannel(b0,b1,mask);
                if(!nativeP3) {
                    int protectedRgb=PastelHighlightPolicy.rescue(
                            sr,sg,sb,r,g,b,mask);
                    int protectedR=(protectedRgb>>>16)&255;
                    int protectedG=(protectedRgb>>>8)&255;
                    int protectedB=protectedRgb&255;
                    if(protectedR!=r || protectedG!=g || protectedB!=b)
                        status.pastelRecoveredPixels++;
                    r=protectedR;g=protectedG;b=protectedB;
                }
                if(r!=r1 || g!=g1 || b!=b1)status.affected++;''',
    "In-place pastel source headroom, sRGB only")
s=replace_once(s,
    '''                    +"\\n• Stage QC %s",''',
    '''                    +"\\n• Original-referenced bright pastel pixels moderated %d"
                    + "\\n• Stage QC %s",''',
    "Final guard diagnostic")
s=replace_once(s,
    '''                    before.ringing,preGuard.ringing,after.ringing,
                    accepted?"ACCEPTED":"rejected");''',
    '''                    before.ringing,preGuard.ringing,after.ringing,
                    guard.pastelRecoveredPixels,
                    accepted?"ACCEPTED":"rejected");''',
    "Final guard diagnostics args")
f.write_text(s)

# QC and historical components are *not* weakened or modified.
for name,expect in (
    ("SelectiveColourPolicy.java", "MAX_PINK_CHANNEL_DELTA = 5"),
    ("SelectiveColourPolicy.java", "MIN_DETAIL_RETAINED = .985"),
    ("SelectiveColourPolicy.java", "MAX_RING_INCREASE = .006"),
    ("SelectiveColourPolicy.java", "MAX_PINK_NEARCLIP_GROWTH = .015"),
    ("AutoTonePolicy.java", "MAX_MEAN_CHANNEL_CHANGE = .023"),
    ("AdaptiveNeuralColour.java", "MAX_MEAN_CHANNEL_MOVE = .007"),
    ("NightLightControlPolicy.java", "MAX_MEAN_CHANNEL_DELTA = 0.0080"),
    ("MainActivity.java", "STUDIO PERFORMANCE"),
    ("MainActivity.java", "CumulativeColourGuard.apply("),
    ("MainActivity.java", "AI MAX + LUT"),
    ("MainActivity.java", "PhotoMasterEngine.Mode.AI_MAX"),
):
    if expect not in (java/name).read_text():
        raise RuntimeError("Lost baseline invariant: "+name+" / "+expect)
print("Photo Master AI v0.4.45: hard-gate early QC and sRGB pastel source-highlight guard ready.")
