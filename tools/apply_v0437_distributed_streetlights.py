#!/usr/bin/env python3
"""v0.4.37: detect and safely target distributed streetlighting in dark night scenes.

Runs AFTER the v0.4.36 spatial continuity patch. All established final
image QC gates and full rollback behavior remain unchanged.
"""
from pathlib import Path
import re
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: apply_v0437_distributed_streetlights.py <project>")
project = Path(sys.argv[1])
build_file = project / "app/build.gradle"
build = build_file.read_text()
if "versionCode 45" not in build or "versionName '0.4.36'" not in build:
    raise RuntimeError("v0.4.36 baseline not found")
build = build.replace("versionCode 45", "versionCode 46", 1)
build = build.replace("versionName '0.4.36'", "versionName '0.4.37'", 1)
build_file.write_text(build)

java_dir = project / "app/src/main/java/com/dennis/photomasterai"
policy_file = java_dir / "NightLightControlPolicy.java"
policy = policy_file.read_text()
for invariant in (
    "MAX_MEAN_CHANNEL_DELTA = 0.0080",
    "MAX_CHANNEL_DELTA = 0.0320",
    "softGate(",
    "cellFeather(",
    "adaptiveStrengthFactor(",
):
    if invariant not in policy:
        raise RuntimeError("Missing inherited QC/policy protection " + invariant)

marker = "    public static QcResult evaluateCandidate("
if policy.count(marker) != 1:
    raise RuntimeError("Night Light policy QC anchor changed")

streetlight_plan = """
    /*
     * Separate STRICT eligibility path for night scenes with multiple
     * distributed, small lamp pools. Existing hotspot Night Light plan
     * is not changed. These are scene-entry thresholds only, NOT QC.
     *
     * Requiring at least two independent lower/mid-frame lamp columns
     * prevents a lone moon or isolated sky star from engaging the mode.
     */
    public static Plan distributedStreetlightPlan(
            boolean semanticExcluded,
            double upperDarkFraction,
            double lowerBrightFraction,
            int veryDarkZones,
            double luminanceSpread,
            int lampCells,
            int lampColumns,
            double lampPixelFraction) {

        if (semanticExcluded)
            return new Plan(false, 0.0, 0.0, "excluded subject");

        if (upperDarkFraction < 0.35)
            return new Plan(false, 0.0, 0.0, "not a dark upper frame");

        if (lowerBrightFraction < 0.006)
            return new Plan(false, 0.0, 0.0, "no lower-frame lit areas");

        if (veryDarkZones < 8)
            return new Plan(false, 0.0, 0.0, "not enough dark zones");

        if (luminanceSpread < 0.26)
            return new Plan(false, 0.0, 0.0, "insufficient luminance spread");

        if (lampColumns < 2 || lampCells < 2)
            return new Plan(false, 0.0, 0.0, "insufficient separated lamps");

        if (lampPixelFraction < 0.00030)
            return new Plan(false, 0.0, 0.0, "insufficient regional lamp pixels");

        double confidence = clamp01(
                0.25 * clamp01((upperDarkFraction - 0.35) / 0.45)
                + 0.20 * clamp01((lowerBrightFraction - 0.006) / 0.07)
                + 0.20 * clamp01((luminanceSpread - 0.26) / 0.40)
                + 0.20 * clamp01((lampColumns - 1.0) / 4.0)
                + 0.15 * clamp01(lampPixelFraction / 0.010));

        /*
         * Lower exposure intervention than the strong single-hotspot
         * branch. All candidates must still satisfy exactly the same
         * detail, ringing, clipping and channel-movement QC limits.
         */
        double strength = Math.min(0.18, 0.105 + 0.07 * confidence);
        return new Plan(
                true, strength, confidence,
                "distributed streetlights detected in separated lit regions");
    }

"""
policy=policy.replace(marker,streetlight_plan+marker,1)
policy_file.write_text(policy)

engine_file=java_dir/"NightLightControlIntelligence.java"
engine=engine_file.read_text()

def once(old, replacement, label):
    global engine
    count=engine.count(old)
    if count!=1:
        raise RuntimeError("Ambiguous Night Light engine anchor: "+label+" ("+str(count)+")")
    engine=engine.replace(old,replacement,1)

def regex_once(pattern,replacement,label):
    global engine
    engine,count=re.subn(pattern,replacement,engine,count=1,flags=re.S)
    if count!=1:
        raise RuntimeError("Night Light engine pattern not found: "+label)

# Evidence retained only in current render; no per-pixel output history.
once("boolean semanticExcluded;",
"""boolean semanticExcluded;

                  int lampCells;
                  int lampColumns;
                  double lampPixelFraction;
                  boolean distributedStreetlight;

                  final boolean[][] distributedLampCells =
                          new boolean[GY][GX];""","evidence fields")

# In same 8x8 spatial map as existing Night Light.
once("int[][] nearClipCounts =\n",
"""int[][] lampCandidateCounts = new int[GY][GX];

                  int[][] nearClipCounts =
""","per-cell lamp counters")
once("long hotspotPixels = 0;",
"""long hotspotPixels = 0;
                  long lampPixels = 0;""","lamp total counter")

# Count bright LOCALIZED sources in mid/lower scene independent of
# mean cell brightness, so diluted streetlights can be detected.
regex_once(r'if\s*\(\s*lum\s*>\s*0\.93\s*\)\s*hotspotPixels\+\+\s*;',
"""if (lum > 0.93)
                              hotspotPixels++;

                          // Preserve moon/star and fully dark sky
                          // by requiring mid/lower-frame sources.
                          if (y >= height * 0.32
                                  && y < height * 0.86
                                  && lum > 0.72
                                  && sat < 0.68) {
                              lampCandidateCounts[gy][gx]++;
                              lampPixels++;
                          }""","lamp pixel evidence")

# Count qualifying lamp cells and separated coarse columns.
# A small lamp need not make its whole 8x8 cell globally bright.
once("double minMean =\n",
"""boolean[] lampColumnPresence = new boolean[GX];

                  for (int yy = 0; yy < GY; yy++) {
                      for (int xx = 0; xx < GX; xx++) {
                          int n = counts[yy][xx];
                          if (n <= 0)
                              continue;
                          int needed = Math.max(3,
                                  (int) Math.ceil(n * 0.0015));
                          if (lampCandidateCounts[yy][xx] >= needed) {
                              evidence.distributedLampCells[yy][xx] = true;
                              evidence.lampCells++;
                              lampColumnPresence[xx] = true;
                          }
                      }
                  }
                  for (boolean hasLamp : lampColumnPresence) {
                      if (hasLamp)
                          evidence.lampColumns++;
                  }
                  evidence.lampPixelFraction = pixels.length > 0
                          ? lampPixels / (double) pixels.length
                          : 0.0;

                  double minMean =
""","candidate lamp cells")

plan_call_pattern=r'(NightLightControlPolicy\.Plan plan\s*=\s*NightLightControlPolicy\.plan\(.*?evidence\.hotspotPixelFraction\);)'
regex_once(plan_call_pattern,
r"""\1

                  String originalPlanReason = plan.reason;
                  if (!plan.apply) {
                      NightLightControlPolicy.Plan alternative =
                              NightLightControlPolicy.distributedStreetlightPlan(
                                      evidence.semanticExcluded,
                                      evidence.upperDarkFraction,
                                      evidence.lowerBrightFraction,
                                      evidence.veryDarkZones,
                                      evidence.luminanceSpread,
                                      evidence.lampCells,
                                      evidence.lampColumns,
                                      evidence.lampPixelFraction);
                      if (alternative.apply) {
                          plan = alternative;
                          evidence.distributedStreetlight = true;
                      }
                  }
""","alternate plan switch")

once('"• Night classification requires dark-upper + bright-lower + hotspot structure; darkness alone is insufficient\\n"',
'"• Night classification: primary hotspot structure OR separated regional streetlights with dark sky evidence; darkness alone is insufficient\\n"',"report description")

report_insert='''                  report.append(
                          String.format(
                                  Locale.US,
                                  "• Distributed streetlight evidence: cells %d | columns %d | candidate pixels %.3f%% | path %s%n",
                                  evidence.lampCells,
                                  evidence.lampColumns,
                                  evidence.lampPixelFraction * 100.0,
                                  evidence.distributedStreetlight
                                          ? "ACTIVE" : "inactive"));

'''
report_needle = '"• Night-light control WITHHELD — "'
report_location = engine.find(report_needle)
if report_location < 0:
    raise RuntimeError("Night Light withheld-report location missing")
report_if = engine.rfind("if (!plan.apply)", 0, report_location)
if report_if < 0:
    raise RuntimeError("Night Light withheld plan branch missing")
engine = engine[:report_if] + report_insert + engine[report_if:]

# Preserve dark sky by skipping all dark-sky pixel values; only a
# source-evidenced bright lamp pixel may bypass broad CELL-based sky mask.
once('if (darkSky) {',
'''boolean distributedLampHere =
                                      evidence.distributedStreetlight
                                              && evidence.distributedLampCells[gy][gx]
                                              && y >= height * 0.32
                                              && y < height * 0.86;

                              boolean actualLampPixel =
                                      distributedLampHere
                                              && lum > 0.72
                                              && sat < 0.68;

                              if (darkSky && !actualLampPixel) {''',
"dark sky stays protected for non-lamp pixels")

# Smoothly target ONLY lamps within evidence cells. Strict scene entry
# does not weaken rendering masks or safety QC.
needle='''                              double u =
                                      (x + 0.5) * GX / (double) width - gx;'''
addition='''                              if (distributedLampHere) {
                                  double lampLumaGate =
                                          NightLightControlPolicy.softGate(
                                                  lum, 0.62, 0.84);
                                  double lampSatGate =
                                          1.0 - NightLightControlPolicy.softGate(
                                                  sat, 0.48, 0.68);
                                  double regionalWeight =
                                          0.32 * lampLumaGate * lampSatGate;
                                  weight = Math.max(
                                          weight, regionalWeight);
                              }

'''
once(needle,addition+needle,"separate regional edit mask")

# For distributed lighting, report the very same positive luma-reduction
# metric across eligible ambient/lamp sources, without relaxing its gates.
# Existing strong hotspot QC measurement remains unchanged.
once('''if (
                                  lum > 0.72
                                          && sat < 0.45) {''',
'''if (
                                  (lum > 0.72 && sat < 0.45)
                                  || (distributedLampHere
                                      && lum > 0.66
                                      && sat < 0.68)) {''',
"hotspot reduction measurement for regional lamps")

for invariant in (
    "MAX_CHANNEL_CODE_DELTA = 8",
    "Night-light control ROLLED BACK",
    "adaptiveStrengthFactor(",
    "globalMeanChannelDelta(",
    "localTextureStrength(",
    "cellFeather(u, v)",
    "localMeanLuma(",
    "if (sat > 0.68)",
    "if (darkSky && !actualLampPixel)",
):
    if invariant not in engine:
        raise RuntimeError("Retained engine invariant missing: " + invariant)
engine_file.write_text(engine)

host = project/"tools/NightLightDistributedStreetlightHostChecks.java"
host.write_text("""package com.dennis.photomasterai;

public final class NightLightDistributedStreetlightHostChecks {
    private static void yes(boolean value, String name) {
        if (!value) throw new AssertionError(name);
    }
    private static NightLightControlPolicy.Plan check(
            double dark, double lowerBright, int zones,
            double spread, int cells, int columns, double pixels) {
        return NightLightControlPolicy.distributedStreetlightPlan(
                false, dark, lowerBright, zones, spread,
                cells, columns, pixels);
    }
    public static void main(String[] args) {
        NightLightControlPolicy.Plan street = check(
                0.447, 0.014, 33, 0.351, 3, 3, 0.001);
        yes(street.apply, "distributed night street lamps not detected");
        yes(street.strength <= 0.18,
                "regional lighting correction strength too high");
        yes(!check(0.028, 0.02, 3, 0.56, 3, 3, 0.001).apply,
                "moonlit/daylight scene must be excluded");
        yes(!check(0.6, 0.0, 20, 0.45, 4, 3, 0.001).apply,
                "unlit lower frame must be excluded");
        yes(!check(0.6, 0.02, 20, 0.45, 1, 1, 0.001).apply,
                "isolated point source should be excluded");
        yes(!check(0.6, 0.02, 20, 0.45, 3, 3, 0.0001).apply,
                "tiny point-source cluster should be excluded");
        yes(!NightLightControlPolicy.distributedStreetlightPlan(
                true, 0.6, 0.02, 20, 0.45, 4, 3, 0.005).apply,
                "semantic-excluded scenes must remain excluded");

        yes(NightLightControlPolicy.MAX_MEAN_CHANNEL_DELTA == 0.0080,
                "global channel-movement QC changed");
        yes(NightLightControlPolicy.MAX_CHANNEL_DELTA == 0.0320,
                "max channel-movement QC changed");
        NightLightControlPolicy.QcResult badDetail =
                NightLightControlPolicy.evaluateCandidate(
                        0.0403, 0.0392, 0.008, 0.008,
                        0.016, 0.016, 0.840, 0.840,
                        0.19, 0.19, 0, 0, 0, 0,
                        0.01, 0, 0.02, 0.004, 0.02);
        yes(!badDetail.accepted,
                "98.5-percent genuine-detail guard must remain enforced");
        System.out.println("NightLightDistributedStreetlightHostChecks passed");
    }
}
""")
print("v0.4.37 applied: dual-path distributed streetlight detection and "
      "per-cell safe-lamp rendering, unchanged original QC and full rollback.")
