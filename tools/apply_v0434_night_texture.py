from pathlib import Path
import os
import re
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: apply_v0434_night_texture.py <project>")

project = Path(sys.argv[1])

build_file = project / "app/build.gradle"
build = build_file.read_text()

if "versionCode 42" not in build:
    raise RuntimeError("v0.4.33 versionCode not found")

if "versionName '0.4.33'" not in build:
    raise RuntimeError("v0.4.33 versionName not found")

build = build.replace("versionCode 42", "versionCode 43", 1)
build = build.replace("versionName '0.4.33'", "versionName '0.4.34'", 1)
build_file.write_text(build)

policy_file = project / (
    "app/src/main/java/com/dennis/"
    "photomasterai/NightLightControlPolicy.java"
)
policy = policy_file.read_text()

if "MAX_MEAN_CHANNEL_DELTA = 0.0080" not in policy:
    raise RuntimeError("Night Light mean-channel safety limit changed")

if "MAX_CHANNEL_DELTA = 0.0320" not in policy:
    raise RuntimeError("Night Light max-channel safety limit changed")

high_ring_pattern = re.compile(
    r"if\s*\(ringing\s*>=\s*0\.82\)\s*\{\s*"
    r"cap\s*=\s*0\.240\s*;"
)

policy, count = high_ring_pattern.subn(
    "if (ringing >= 0.82) {\n\n"
    "            cap = 0.225;",
    policy,
    count=1,
)

if count != 1:
    raise RuntimeError("High-ring Night Light strength anchor not found")

helper_anchor = "    private static void add("

if helper_anchor not in policy:
    raise RuntimeError("Night Light policy helper anchor not found")

texture_method = r"""
    public static double textureProtection(
            double textureStrength) {

        /*
         * Fine repeated texture can have low individual edge
         * contrast while still carrying important visible detail.
         *
         * Return value is an edit multiplier. Smooth flash spill
         * remains fully eligible while grass, concrete, tyre/deck
         * texture and fine mechanical detail are progressively
         * protected.
         */

        if (textureStrength >= 0.050)
            return 0.35;

        if (textureStrength >= 0.032)
            return 0.50;

        if (textureStrength >= 0.020)
            return 0.68;

        if (textureStrength >= 0.012)
            return 0.84;

        return 1.0;
    }

"""

policy = policy.replace(
    helper_anchor,
    texture_method + helper_anchor,
    1,
)

policy_file.write_text(policy)

engine_file = project / (
    "app/src/main/java/com/dennis/"
    "photomasterai/NightLightControlIntelligence.java"
)
engine = engine_file.read_text()

field_anchor = "long protectedEdgePixels;"

if field_anchor not in engine:
    raise RuntimeError("Night Light edge diagnostic field not found")

engine = engine.replace(
    field_anchor,
    field_anchor
    + "\n"
    + "        long protectedTexturePixels;"
    + "\n"
    + "        double textureStrengthSum;"
    + "\n"
    + "        long textureSampleCount;",
    1,
)

weight_pattern = re.compile(
    r"weight\s*\*=\s*edgeProtection\s*;"
)

texture_guard = r"""double textureStrength =
                            localTextureStrength(
                                    sourcePixels,
                                    width,
                                    rows,
                                    localY,
                                    x,
                                    lum);

                    double textureProtection =
                            NightLightControlPolicy.textureProtection(
                                    textureStrength);

                    summary.textureStrengthSum +=
                            textureStrength;

                    summary.textureSampleCount++;

                    if (textureProtection < 0.999) {

                        summary.protectedTexturePixels++;
                    }

                    double structureProtection =
                            Math.min(
                                    edgeProtection,
                                    textureProtection);

                    weight *=
                            structureProtection;"""

engine, count = weight_pattern.subn(
    texture_guard,
    engine,
    count=1,
)

if count != 1:
    raise RuntimeError("Night Light edge-weight anchor not found")

helper_anchor = "    private static double localEdgeStrength("

if helper_anchor not in engine:
    raise RuntimeError("Night Light local-edge helper anchor not found")

texture_helper = r"""    private static double localTextureStrength(
            int[] pixels,
            int width,
            int rows,
            int y,
            int x,
            double centreLuma) {

        double sumAbs = 0.0;
        double minLuma = centreLuma;
        double maxLuma = centreLuma;
        int count = 0;

        for (int dy = -1; dy <= 1; dy++) {

            int yy = y + dy;

            if (yy < 0 || yy >= rows)
                continue;

            for (int dx = -1; dx <= 1; dx++) {

                if (dx == 0 && dy == 0)
                    continue;

                int xx = x + dx;

                if (xx < 0 || xx >= width)
                    continue;

                double neighbour =
                        pixelLuma(
                                pixels[
                                        yy * width
                                                + xx]);

                sumAbs +=
                        Math.abs(
                                centreLuma
                                        - neighbour);

                minLuma =
                        Math.min(
                                minLuma,
                                neighbour);

                maxLuma =
                        Math.max(
                                maxLuma,
                                neighbour);

                count++;
            }
        }

        if (count <= 0)
            return 0.0;

        double meanAbs =
                sumAbs
                        / (double)
                        count;

        double localRange =
                maxLuma
                        - minLuma;

        return Math.max(
                meanAbs,
                localRange * 0.35);
    }

"""

engine = engine.replace(
    helper_anchor,
    texture_helper + helper_anchor,
    1,
)

rollback_anchor = "        if (!qc.accepted) {"

if rollback_anchor not in engine:
    raise RuntimeError("Night Light QC rollback anchor not found")

texture_report = r"""        report.append(
                String.format(
                        Locale.US,
                        "• Texture guard protected pixels %d | mean eligible texture %.4f%n",
                        summary.protectedTexturePixels,
                        summary.textureSampleCount > 0
                                ? summary.textureStrengthSum
                                / summary.textureSampleCount
                                : 0.0));

"""

engine = engine.replace(
    rollback_anchor,
    texture_report + rollback_anchor,
    1,
)

engine_file.write_text(engine)

# Update the inherited v0.4.32 regression check to the deliberately
# more conservative v0.4.34 high-ring ceiling. The safety concept is
# unchanged; only the expected cap changes from 0.240 to 0.225.
legacy_host = project / "tools/NightLightDetailGuardHostChecks.java"
legacy = legacy_host.read_text()

if "highRing - 0.240" not in legacy:
    raise RuntimeError("Legacy Night Light high-ring host-check anchor missing")

legacy = legacy.replace(
    "highRing - 0.240",
    "highRing - 0.225",
    1,
)

legacy = legacy.replace(
    "high-ringing scene must cap strength at 0.240",
    "high-ringing scene must cap strength at 0.225",
    1,
)

legacy_host.write_text(legacy)

host_file = project / "tools/NightLightTexturePreservationHostChecks.java"
host_file.write_text(
r"""package com.dennis.photomasterai;

public final class NightLightTexturePreservationHostChecks {

    public static void main(String[] args) {

        requireClose(
                NightLightControlPolicy.textureProtection(0.005),
                1.0,
                "smooth spill should remain fully eligible");

        requireClose(
                NightLightControlPolicy.textureProtection(0.015),
                0.84,
                "fine texture protection incorrect");

        requireClose(
                NightLightControlPolicy.textureProtection(0.025),
                0.68,
                "medium texture protection incorrect");

        requireClose(
                NightLightControlPolicy.textureProtection(0.040),
                0.50,
                "strong texture protection incorrect");

        requireClose(
                NightLightControlPolicy.textureProtection(0.060),
                0.35,
                "very strong texture protection incorrect");

        requireClose(
                NightLightControlPolicy.effectiveStrength(
                        0.307,
                        0.84),
                0.225,
                "high-ringing strength cap incorrect");

        require(
                NightLightControlPolicy.MAX_MEAN_CHANNEL_DELTA
                        == 0.0080,
                "mean-channel QC ceiling changed");

        require(
                NightLightControlPolicy.MAX_CHANNEL_DELTA
                        == 0.0320,
                "max-channel QC ceiling changed");

        System.out.println(
                "NightLightTexturePreservationHostChecks passed");
    }

    private static void requireClose(
            double actual,
            double expected,
            String message) {

        if (Math.abs(actual - expected) > 0.0000001) {
            throw new AssertionError(
                    message + ": " + actual + " != " + expected);
        }
    }

    private static void require(
            boolean condition,
            String message) {

        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
"""
)

# Source-level invariants: fail immediately if the patch did not land.
assert "versionCode 43" in build_file.read_text()
assert "versionName '0.4.34'" in build_file.read_text()
assert "textureProtection" in policy_file.read_text()
assert "cap = 0.225" in policy_file.read_text()
assert "localTextureStrength" in engine_file.read_text()
assert "Texture guard protected pixels" in engine_file.read_text()

print(
    "v0.4.34 patch applied: micro-texture protection added; "
    "high-ring Night Light cap reduced to 0.225; "
    "all QC ceilings remain unchanged"
)
