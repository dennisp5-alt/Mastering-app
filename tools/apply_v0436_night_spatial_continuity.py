#!/usr/bin/env python3
"""v0.4.36: fix Night Light spatial mask discontinuities and tile-edge detail accounting.

Applies after the exact v0.4.35 pipeline has been reconstructed. Fails
closed when an expected structural anchor is missing. Does NOT relax QC.
"""
from pathlib import Path
import re
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: apply_v0436_night_spatial_continuity.py <project>")

project = Path(sys.argv[1])
build_file = project / "app/build.gradle"
build = build_file.read_text()
if "versionCode 44" not in build or "versionName '0.4.35'" not in build:
    raise RuntimeError("v0.4.35 base version is missing")
build = build.replace("versionCode 44", "versionCode 45", 1)
build = build.replace("versionName '0.4.35'", "versionName '0.4.36'", 1)
build_file.write_text(build)

java_dir = project / "app/src/main/java/com/dennis/photomasterai"
policy_file = java_dir / "NightLightControlPolicy.java"
policy = policy_file.read_text()

for required in (
    "MAX_MEAN_CHANNEL_DELTA = 0.0080",
    "MAX_CHANNEL_DELTA = 0.0320",
    "adaptiveStrengthFactor",
    "textureProtection",
):
    if required not in policy:
        raise RuntimeError("Night Light policy invariant absent: " + required)

policy_anchor = "    private static void add("
if policy.count(policy_anchor) != 1:
    raise RuntimeError("Ambiguous policy helper anchor")

policy_helpers = """
    /*
     * C1-continuous luminance/saturation transition. Avoids the hard
     * pixel thresholds that can create contour edges under night lighting.
     */
    public static double softGate(
            double value, double low, double high) {
        if (!Double.isFinite(value) || high <= low)
            return 0.0;
        double t = Math.max(0.0,
                Math.min(1.0, (value - low) / (high - low)));
        return t * t * (3.0 - 2.0 * t);
    }

    /*
     * Edit strength approaches zero smoothly at the 8x8 analysis-cell
     * boundaries. This prevents discontinuities between eligible and
     * ineligible cells without ever extending edits into protected cells.
     */
    public static double cellFeather(double u, double v) {
        if (!Double.isFinite(u) || !Double.isFinite(v))
            return 0.0;
        double dx = Math.max(0.0, Math.min(u, 1.0 - u));
        double dy = Math.max(0.0, Math.min(v, 1.0 - v));
        return softGate(dx, 0.0, 0.075)
                * softGate(dy, 0.0, 0.075);
    }

"""
policy = policy.replace(policy_anchor, policy_helpers + policy_anchor, 1)
policy_file.write_text(policy)

engine_file = java_dir / "NightLightControlIntelligence.java"
engine = engine_file.read_text()

def replace_once(pattern, replacement, label, *, flags=re.S):
    global engine
    engine, n = re.subn(pattern, replacement, engine, count=1, flags=flags)
    if n != 1:
        raise RuntimeError("Night Light structural patch failed: " + label)

# Each independently rendered v0.4.35 candidate still reads the SAME
# mastered source. A one-row halo also gives 3x3 edge/texture analysis valid
# neighbours across the 128-row chunk boundaries.
replace_once(
    r'(source\.getPixels\(\s*pixels,\s*0,\s*width,\s*0,\s*y0,\s*width,\s*rows\s*\);)',
    lambda m: m.group(1) + """
                      int haloStart = Math.max(0, y0 - 1);
                      int haloEnd = Math.min(height, y0 + rows + 1);
                      int haloRows = haloEnd - haloStart;
                      int[] haloPixels = new int[width * haloRows];
                      source.getPixels(
                              haloPixels, 0, width, 0, haloStart,
                              width, haloRows);
""",
    "one-row source halo"
)

# Replace hard stepwise luma/saturation eligibility with feathered weights.
# Dark sky and high-saturation short circuits above this block remain intact.
# No additional correction may extend beyond an eligible evidence cell.
replace_once(
    r'double\s+weight\s*=\s*0\.0\s*;'
    r'.*?'
    r'if\s*\(\s*weight\s*<=\s*0\.0\s*\)\s*\{',
    """double weight = 0.0;

                              if (hotspotCell) {
                                  double lumaGate =
                                          NightLightControlPolicy.softGate(
                                                  lum, 0.72, 0.96);
                                  double colourGate =
                                          1.0 - NightLightControlPolicy.softGate(
                                                  sat, 0.34, 0.47);
                                  weight = lumaGate * colourGate;
                              }

                              if (spillCell) {
                                  double lumaGate =
                                          NightLightControlPolicy.softGate(
                                                  lum, 0.62, 0.76);
                                  double colourGate =
                                          1.0 - NightLightControlPolicy.softGate(
                                                  sat, 0.28, 0.42);
                                  weight = Math.max(
                                          weight,
                                          0.38 * lumaGate * colourGate);
                              }

                              double u =
                                      (x + 0.5) * GX / (double) width - gx;
                              double v =
                                      (y + 0.5) * GY / (double) height - gy;
                              weight *=
                                      NightLightControlPolicy.cellFeather(u, v);

                              if (weight <= 0.0) {""",
    "pixel and cell weight smoothing"
)

replace_once(
    r'localEdgeStrength\(\s*sourcePixels,\s*width,\s*rows,\s*localY,\s*x,\s*lum\s*\)',
    "localEdgeStrength(haloPixels, width, haloRows, y - haloStart, x, lum)",
    "edge halo"
)
replace_once(
    r'localTextureStrength\(\s*sourcePixels,\s*width,\s*rows,\s*localY,\s*x,\s*lum\s*\)',
    "localTextureStrength(haloPixels, width, haloRows, y - haloStart, x, lum)",
    "texture halo"
)

# Pixel-by-pixel transfer based on its OWN luminance flattens contrast
# within local textures. Derive the gain from a 3x3 low-frequency local
# estimate instead; the same local gain carries individual pixel detail.
# Preserve the old 4% scale cap, per-channel 8-code cap and ALL QC gates.
replace_once(
    r'double\s+targetLum\s*=\s*lum\s*-\s*compression\s*\*\s*'
    r'Math\.max\(\s*0\.0\s*,\s*lum\s*-\s*0\.60\s*\)\s*;',
    """double baseLuma =
                                      localMeanLuma(
                                              haloPixels, width, haloRows,
                                              y - haloStart, x);

                              double targetLum =
                                      lum - compression
                                              * Math.max(0.0, baseLuma - 0.60)
                                              * (lum / Math.max(1e-6, baseLuma));""",
    "low-frequency contrast-preserving compression"
)

helper_anchor = "    private static double localTextureStrength("
if engine.count(helper_anchor) != 1:
    raise RuntimeError("Ambiguous texture helper anchor")

mean_helper = """
    private static double localMeanLuma(
            int[] pixels, int width, int rows, int y, int x) {
        double weightedSum = 0.0;
        double weightSum = 0.0;
        for (int dy = -1; dy <= 1; dy++) {
            int yy = y + dy;
            if (yy < 0 || yy >= rows) continue;
            for (int dx = -1; dx <= 1; dx++) {
                int xx = x + dx;
                if (xx < 0 || xx >= width) continue;
                double w = (dx == 0 ? 2.0 : 1.0)
                        * (dy == 0 ? 2.0 : 1.0);
                weightedSum += w * pixelLuma(pixels[yy * width + xx]);
                weightSum += w;
            }
        }
        return weightSum > 0.0 ? weightedSum / weightSum : 0.0;
    }

"""
engine = engine.replace(helper_anchor, mean_helper + helper_anchor, 1)
engine_file.write_text(engine)

# Compile-time policy regression checks; image QC still runs on device.
test_file = project / "tools/NightLightSpatialContinuityHostChecks.java"
test_file.write_text("""package com.dennis.photomasterai;

public final class NightLightSpatialContinuityHostChecks {
    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
    }
    private static void near(double value, double expected, String message) {
        check(Math.abs(value - expected) < 0.00000001, message);
    }
    public static void main(String[] args) {
        near(NightLightControlPolicy.softGate(0.1, 0.2, 0.8),
             0.0, "low gating");
        near(NightLightControlPolicy.softGate(0.5, 0.2, 0.8),
             0.5, "midpoint");
        near(NightLightControlPolicy.softGate(0.9, 0.2, 0.8),
             1.0, "upper gating");
        double last = 0.0;
        for (int i = 0; i <= 100; i++) {
            double value = NightLightControlPolicy.softGate(
                    0.2 + i * 0.006, 0.2, 0.8);
            check(value >= last - 1e-10 && value <= 1.0,
                  "soft gate monotonicity");
            last = value;
        }
        near(NightLightControlPolicy.cellFeather(0.5, 0.5),
             1.0, "cell centre");
        near(NightLightControlPolicy.cellFeather(0.0, 0.5),
             0.0, "left boundary");
        near(NightLightControlPolicy.cellFeather(1.0, 0.5),
             0.0, "right boundary");
        double feather = NightLightControlPolicy.cellFeather(0.04, 0.5);
        check(feather > 0.0 && feather < 1.0, "smooth cell transition");
        check(NightLightControlPolicy.MAX_MEAN_CHANNEL_DELTA == 0.0080,
              "global channel safety limit changed");
        check(NightLightControlPolicy.MAX_CHANNEL_DELTA == 0.0320,
              "max channel safety limit changed");
        near(NightLightControlPolicy.adaptiveStrengthFactor(0), 1.0,
             "100% search strength changed");
        near(NightLightControlPolicy.adaptiveStrengthFactor(4), 0.50,
             "minimum search strength changed");
        System.out.println("NightLightSpatialContinuityHostChecks passed");
    }
}
""")

for invariant in (
    "if (darkSky)",
    "if (sat > 0.68)",
    "MAX_CHANNEL_CODE_DELTA = 8",
    "localMeanLuma(",
    "cellFeather(u, v)",
    "haloStart",
    "Night-light control ROLLED BACK",
    "before.detail",
):
    if invariant not in engine:
        raise RuntimeError("Night Light engine invariant missing: " + invariant)

print("v0.4.36 applied: smooth illumination/cell masks, halo-correct edge/texture, "
      "low-frequency base compression. Unchanged: QC, 98.5% detail threshold, "
      "dark sky, colour, channel caps, independent adaptive attempts and rollback.")
