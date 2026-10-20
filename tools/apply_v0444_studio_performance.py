#!/usr/bin/env python3
"""Photo Master AI v0.4.44: conservative zero-quality-loss Studio fast path.

Runs only AFTER v0.4.43 has rebuilt the signed Studio Lab source.
No changes to pixel equations, strength choices, QC thresholds, denoise,
LUT basis values, profiles, colour policies, or recovery behaviour.
"""
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: apply_v0444_studio_performance.py <project>")
root = Path(sys.argv[1])
buildfile = root / "app/build.gradle"
build = buildfile.read_text()
for marker in (
    "versionCode 52",
    "versionName '0.4.43-studio-colour-guard'",
    "applicationId 'com.dennis.photomasterai.studiolab'",
):
    if build.count(marker) != 1:
        raise RuntimeError("v0.4.43 verified baseline missing: " + marker)
buildfile.write_text(build.replace("versionCode 52", "versionCode 53", 1).replace(
    "versionName '0.4.43-studio-colour-guard'",
    "versionName '0.4.44-studio-fastpath'", 1))
java = root / "app/src/main/java/com/dennis/photomasterai"

def load(name):
    file = java / name
    return file, file.read_text()

def one(text, old, new, label, number=1):
    count = text.count(old)
    if count != number:
        raise RuntimeError("%s: expected %d anchors, found %d" % (label, number, count))
    return text.replace(old, new)

# Result.analysis was already computed against exactly the bitmap that the
# next stage receives. Reuse it; null still takes the original analysis path.
file, s = load("IntelligentAuto.java")
s = one(s, "PhotoAnalysis before=PhotoAnalysis.analyse(base.bitmap);",
    "PhotoAnalysis before=base.analysis != null ? base.analysis : PhotoAnalysis.analyse(base.bitmap);",
    "Auto Light/WB before QC", 2)
s = one(s, "PhotoAnalysis.analyse(base.bitmap));",
    "(base.analysis != null ? base.analysis : PhotoAnalysis.analyse(base.bitmap)));",
    "Auto report fallback")
file.write_text(s)

file, s = load("StudioFinish.java")
s = one(s, "PhotoAnalysis before=PhotoAnalysis.analyse(mastered);",
    "PhotoAnalysis before=baseline.analysis != null ? baseline.analysis : PhotoAnalysis.analyse(mastered);",
    "Studio before QC")
s = one(s, "PhotoAnalysis.analyse(base.bitmap));",
    "(base.analysis != null ? base.analysis : PhotoAnalysis.analyse(base.bitmap)));",
    "Studio report fallback")
file.write_text(s)

file, s = load("CumulativeColourGuard.java")
s = one(s, "PhotoAnalysis before=PhotoAnalysis.analyse(reference);",
    "PhotoAnalysis before=baseline.analysis != null ? baseline.analysis : PhotoAnalysis.analyse(reference);",
    "Cumulative baseline QC")
s = one(s, "PhotoAnalysis preGuard=PhotoAnalysis.analyse(candidate);",
    "PhotoAnalysis preGuard=studio.analysis != null ? studio.analysis : PhotoAnalysis.analyse(candidate);",
    "Cumulative candidate QC")
s = one(s, "PhotoAnalysis.analyse(reference));",
    "(baseline.analysis != null ? baseline.analysis : PhotoAnalysis.analyse(reference)));",
    "Cumulative rollback QC")
s = one(s, "PhotoAnalysis.analyse(result.bitmap));",
    "(result.analysis != null ? result.analysis : PhotoAnalysis.analyse(result.bitmap)));",
    "Cumulative message QC")
file.write_text(s)

file, s = load("AdaptiveNeuralColour.java")
s = one(s, "final PhotoAnalysis before = PhotoAnalysis.analyse(mastered);",
    "final PhotoAnalysis before = baseline.analysis != null ? baseline.analysis : PhotoAnalysis.analyse(mastered);",
    "Neural before QC")
s = one(s, "PhotoAnalysis.analyse(base.bitmap));",
    "(base.analysis != null ? base.analysis : PhotoAnalysis.analyse(base.bitmap)));",
    "Neural rollback QC")

# Model coefficients and LUT basis mathematics remain byte-for-byte unchanged.
# Create ONNX session only once per app process (same environment and 4 threads).
old_session = """        try (OrtSession.SessionOptions opts = new OrtSession.SessionOptions()) {
            opts.setIntraOpNumThreads(4);
            byte[] model = readAsset(ctx, "lut_classifier.onnx");
            try (OrtSession session = env.createSession(model, opts);
                 OnnxTensor tensor = OnnxTensor.createTensor(env, input, new long[]{1,3,256,256})) {
                String name = session.getInputNames().iterator().next();
                try (OrtSession.Result result = session.run(java.util.Collections.singletonMap(name,tensor))) {
                    Object value = result.get(0).getValue();
                    if (!(value instanceof float[][]))
                        throw new IllegalStateException("Unexpected ONNX output type");
                    return ((float[][]) value)[0].clone();
                }
            }
        }"""
new_session = """        OrtSession session = classifier(ctx, env);
        try (OnnxTensor tensor = OnnxTensor.createTensor(env, input, new long[]{1,3,256,256})) {
            String name = session.getInputNames().iterator().next();
            try (OrtSession.Result result = session.run(java.util.Collections.singletonMap(name,tensor))) {
                Object value = result.get(0).getValue();
                if (!(value instanceof float[][]))
                    throw new IllegalStateException("Unexpected ONNX output type");
                return ((float[][]) value)[0].clone();
            }
        }"""
s = one(s, old_session, new_session, "Identical ONNX inference with retained session")
anchor = "    private static byte[] readAsset(Context ctx, String name) throws Exception {"
helper = """    // The model is immutable; the process owns its ONNX session until exit.
    // Synchronization prevents racing initialisation on parallel UI requests.
    private static volatile OrtSession cachedClassifier;
    private static volatile byte[] cachedLutBasis;

    private static OrtSession classifier(Context context, OrtEnvironment env) throws Exception {
        OrtSession existing = cachedClassifier;
        if (existing != null) return existing;
        synchronized (AdaptiveNeuralColour.class) {
            existing = cachedClassifier;
            if (existing == null) {
                try (OrtSession.SessionOptions opts = new OrtSession.SessionOptions()) {
                    opts.setIntraOpNumThreads(4);
                    existing = env.createSession(readAsset(context, "lut_classifier.onnx"), opts);
                }
                cachedClassifier = existing;
            }
        }
        return existing;
    }

    private static byte[] lutBasis(Context context) throws Exception {
        byte[] bytes = cachedLutBasis;
        if (bytes != null) return bytes;
        synchronized (AdaptiveNeuralColour.class) {
            bytes = cachedLutBasis;
            if (bytes == null) {
                bytes = readAsset(context, "lut_basis_33.bin");
                cachedLutBasis = bytes;
            }
        }
        return bytes;
    }

"""
s = one(s, anchor, helper + anchor, "Insert immutable model caches")
s = one(s, 'byte[] bytes = readAsset(ctx, "lut_basis_33.bin");',
    "byte[] bytes = lutBasis(ctx);", "Reuse identical LUT bytes")
file.write_text(s)

# Expose reliable on-device timings: these encompass full-resolution
# rendering, all adaptive retries and every existing QC analysis.
file, s = load("MainActivity.java")
s = one(s,
    "                // The two real Auto decisions are independent:",
    "                final long studioPerfT0 = android.os.SystemClock.elapsedRealtimeNanos();\n"
    "                // The two real Auto decisions are independent:",
    "Studio timing start")
s = one(s,
    """                                ? IntelligentAuto.autoTone(originalBitmap, result)
                                : result;
                final PhotoMasterEngine.Result autoWhiteResult =""",
    """                                ? IntelligentAuto.autoTone(originalBitmap, result)
                                : result;
                final long studioPerfT1 = android.os.SystemClock.elapsedRealtimeNanos();
                final PhotoMasterEngine.Result autoWhiteResult =""",
    "Auto Light timing")
s = one(s,
    """                                        originalBitmap, autoLightResult)
                                : autoLightResult;

                final PhotoMasterEngine.Result neuralOrBaseline =""",
    """                                        originalBitmap, autoLightResult)
                                : autoLightResult;
                final long studioPerfT2 = android.os.SystemClock.elapsedRealtimeNanos();

                final PhotoMasterEngine.Result neuralOrBaseline =""",
    "Auto White Balance timing")
s = one(s,
    """                                        this, originalBitmap, autoWhiteResult)
                                : autoWhiteResult;
                final PhotoMasterEngine.Result preGuardResult =""",
    """                                        this, originalBitmap, autoWhiteResult)
                                : autoWhiteResult;
                final long studioPerfT3 = android.os.SystemClock.elapsedRealtimeNanos();
                final PhotoMasterEngine.Result preGuardResult =""",
    "Neural Colour timing")
s = one(s,
    """                                ? StudioFinish.apply(originalBitmap, neuralOrBaseline)
                                : neuralOrBaseline;
                final PhotoMasterEngine.Result displayedResult =
                        useStudioMaster
                                ? CumulativeColourGuard.apply(
                                        originalBitmap, result, preGuardResult)
                                : preGuardResult;""",
    """                                ? StudioFinish.apply(originalBitmap, neuralOrBaseline)
                                : neuralOrBaseline;
                final long studioPerfT4 = android.os.SystemClock.elapsedRealtimeNanos();
                final PhotoMasterEngine.Result guardedResult =
                        useStudioMaster
                                ? CumulativeColourGuard.apply(
                                        originalBitmap, result, preGuardResult)
                                : preGuardResult;
                final long studioPerfT5 = android.os.SystemClock.elapsedRealtimeNanos();
                final PhotoMasterEngine.Result displayedResult =
                        useStudioMaster
                                ? new PhotoMasterEngine.Result(
                                        guardedResult.bitmap,
                                        guardedResult.report + String.format(java.util.Locale.US,
                                                "\\n\\nSTUDIO PERFORMANCE — milliseconds"
                                                + "\\n• Auto Light %.0f"
                                                + "\\n• Auto WB %.0f"
                                                + "\\n• Neural Colour %.0f"
                                                + "\\n• Studio Finish %.0f"
                                                + "\\n• Cumulative Guard %.0f"
                                                + "\\n• Extra Studio stages total %.0f",
                                                (studioPerfT1 - studioPerfT0) / 1e6,
                                                (studioPerfT2 - studioPerfT1) / 1e6,
                                                (studioPerfT3 - studioPerfT2) / 1e6,
                                                (studioPerfT4 - studioPerfT3) / 1e6,
                                                (studioPerfT5 - studioPerfT4) / 1e6,
                                                (studioPerfT5 - studioPerfT0) / 1e6),
                                        guardedResult.analysis)
                                : guardedResult;""",
    "Guard and end-to-end Studio diagnostics")
file.write_text(s)

# Fail closed if either branch/mode or a safety setting disappeared.
for required in (
    "PhotoMasterEngine.Mode.AI_MAX", "AI MAX + LUT", "AI MAX + STUDIO",
    "applyNightLightControl(", "applyPerceptualImprovementController(",
    "IntelligentAuto.autoTone(", "IntelligentAuto.autoWhiteBalance(",
    "AdaptiveNeuralColour.apply(", "StudioFinish.apply(",
    "CumulativeColourGuard.apply(", "retired.containsKey(tmp)",
    "STUDIO PERFORMANCE",
):
    if required not in s:
        raise RuntimeError("Pipeline/timing invariant missing: " + required)
for name, needle in (
    ("AdaptiveNeuralColour.java", "MAX_RING_INCREASE = .006"),
    ("AdaptiveNeuralColour.java", "MIN_DETAIL_RETAINED = .985"),
    ("StudioFinishPolicy.java", "MIN_DETAIL_RETAINED = 0.985"),
    ("SelectiveColourPolicy.java", "MAX_PINK_CHANNEL_DELTA = 5"),
    ("NightLightControlPolicy.java", "MAX_MEAN_CHANNEL_DELTA = 0.0080"),
):
    if needle not in (java / name).read_text():
        raise RuntimeError("Safety invariant changed in " + name)
print("Photo Master AI v0.4.44: image-equivalent QC reuse, reusable ONNX assets/session, per-stage timing.")
