from pathlib import Path
import re
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: apply_v0435_night_adaptive_backoff.py <project>")

project = Path(sys.argv[1])

# ------------------------------------------------------------
# VERSION
# ------------------------------------------------------------
build_file = project / "app/build.gradle"
build = build_file.read_text()

if "versionCode 43" not in build:
    raise RuntimeError("v0.4.34 versionCode not found")

if "versionName '0.4.34'" not in build:
    raise RuntimeError("v0.4.34 versionName not found")

build = build.replace("versionCode 43", "versionCode 44", 1)
build = build.replace("versionName '0.4.34'", "versionName '0.4.35'", 1)
build_file.write_text(build)

# ------------------------------------------------------------
# POLICY: ADAPTIVE SAFE-STRENGTH SCHEDULE
# ------------------------------------------------------------
policy_file = project / (
    "app/src/main/java/com/dennis/"
    "photomasterai/NightLightControlPolicy.java"
)
policy = policy_file.read_text()

for invariant in (
    "MAX_MEAN_CHANNEL_DELTA = 0.0080",
    "MAX_CHANNEL_DELTA = 0.0320",
):
    if invariant not in policy:
        raise RuntimeError("Night Light safety invariant missing: " + invariant)

helper_anchor = "    private static void add("

if helper_anchor not in policy:
    raise RuntimeError("Night Light policy helper anchor not found")

adaptive_helpers = r"""
    public static final int MAX_ADAPTIVE_ATTEMPTS =
            5;

    public static double adaptiveStrengthFactor(
            int attempt) {

        /*
         * Descending safe-strength search.
         *
         * The first candidate always uses the normal effective
         * strength. Later candidates reduce only correction strength;
         * all existing QC limits stay unchanged. The engine accepts
         * the first (strongest) candidate that passes every QC gate.
         */

        switch (attempt) {

            case 0:
                return 1.00;

            case 1:
                return 0.85;

            case 2:
                return 0.72;

            case 3:
                return 0.60;

            default:
                return 0.50;
        }
    }

"""

policy = policy.replace(
    helper_anchor,
    adaptive_helpers + helper_anchor,
    1,
)

policy_file.write_text(policy)

# ------------------------------------------------------------
# ENGINE: REPLACE SINGLE-SHOT QC WITH ADAPTIVE BACKOFF
# ------------------------------------------------------------
engine_file = project / (
    "app/src/main/java/com/dennis/"
    "photomasterai/NightLightControlIntelligence.java"
)
engine = engine_file.read_text()

apply_region_start = engine.find(
    "if (!plan.apply)"
)

if apply_region_start < 0:
    raise RuntimeError(
        "Night Light plan/apply region not found"
    )

analysis_region_start = engine.find(
    "private static Evidence analyse(",
    apply_region_start,
)

if analysis_region_start < 0:
    raise RuntimeError(
        "Night Light analyse-method boundary not found"
    )

summary_token = "RenderSummary summary ="

summary_index = engine.find(
    summary_token,
    apply_region_start,
    analysis_region_start,
)

if summary_index < 0:
    raise RuntimeError(
        "Night Light RenderSummary block not found"
    )

start_index = engine.rfind(
    "\n",
    apply_region_start,
    summary_index,
)

if start_index < 0:
    start_index = summary_index
else:
    start_index += 1

accepted_text_index = engine.find(
    "Night-light control ACCEPTED",
    summary_index,
    analysis_region_start,
)

if accepted_text_index < 0:
    raise RuntimeError(
        "Night Light accepted-report anchor not found"
    )

accepted_return_index = engine.find(
    "return new Outcome(",
    accepted_text_index,
    analysis_region_start,
)

if accepted_return_index < 0:
    raise RuntimeError(
        "Night Light accepted return anchor not found"
    )

accepted_return_end = engine.find(
    "report.toString());",
    accepted_return_index,
    analysis_region_start,
)

if accepted_return_end < 0:
    raise RuntimeError(
        "Night Light accepted return end not found"
    )

end_index = (
    accepted_return_end
    + len("report.toString());")
)

replacement = r'''                  Bitmap acceptedCandidate =
                          null;

                  PhotoAnalysis acceptedAfter =
                          null;

                  NightLightControlPolicy.QcResult lastQc =
                          null;

                  double selectedStrength =
                          0.0;

                  int selectedAttempt =
                          -1;

                  for (
                          int attempt = 0;
                          attempt
                                  < NightLightControlPolicy
                                  .MAX_ADAPTIVE_ATTEMPTS;
                          attempt++) {

                      double factor =
                              NightLightControlPolicy
                                      .adaptiveStrengthFactor(
                                              attempt);

                      double attemptStrength =
                              effectiveStrength
                                      * factor;

                      RenderSummary summary =
                              new RenderSummary();

                      Bitmap candidate =
                              render(
                                      mastered,
                                      evidence,
                                      attemptStrength,
                                      summary);

                      if (candidate == null) {

                          report.append(
                                  "• Adaptive Night Light render unavailable at attempt ")
                                  .append(
                                          attempt + 1)
                                  .append(
                                          "\n");

                          break;
                      }

                      PhotoAnalysis after =
                              PhotoAnalysis.analyse(
                                      candidate);

                      double hotspotBefore =
                              summary.hotspotCount > 0
                                      ? summary.hotspotBeforeSum
                                      / summary.hotspotCount
                                      : 0.0;

                      double hotspotAfter =
                              summary.hotspotCount > 0
                                      ? summary.hotspotAfterSum
                                      / summary.hotspotCount
                                      : 0.0;

                      double hotspotReduction =
                              hotspotBefore
                                      - hotspotAfter;

                      double darkSkyMeanDelta =
                              summary.darkSkyCount > 0
                                      ? summary.darkSkyDeltaSum
                                      / summary.darkSkyCount
                                      : 0.0;

                      double activeFraction =
                              summary.totalPixels > 0
                                      ? summary.activePixels
                                      / (double)
                                      summary.totalPixels
                                      : 0.0;

                      double globalMeanChannelDelta =
                              NightLightControlPolicy
                                      .globalMeanChannelDelta(
                                              summary.channelDeltaSum,
                                              summary.totalPixels);

                      double activeMeanChannelDelta =
                              NightLightControlPolicy
                                      .activeMeanChannelDelta(
                                              summary.channelDeltaSum,
                                              summary.activePixels);

                      NightLightControlPolicy.QcResult qc =
                              NightLightControlPolicy
                                      .evaluateCandidate(
                                              before.detail,
                                              after.detail,

                                              before.noise,
                                              after.noise,

                                              before.flatChromaNoise,
                                              after.flatChromaNoise,

                                              before.ringing,
                                              after.ringing,

                                              before.avgSat,
                                              after.avgSat,

                                              before.shadowClip,
                                              after.shadowClip,

                                              before.highlightClip,
                                              after.highlightClip,

                                              hotspotReduction,
                                              darkSkyMeanDelta,
                                              activeFraction,
                                              globalMeanChannelDelta,
                                              summary.maxChannelDelta);

                      lastQc =
                              qc;

                      report.append(
                              String.format(
                                      Locale.US,
                                      "• Adaptive attempt %d/%d | strength %.3f (%.0f%% of guarded strength)%n",
                                      attempt + 1,
                                      NightLightControlPolicy
                                              .MAX_ADAPTIVE_ATTEMPTS,
                                      attemptStrength,
                                      factor * 100.0));

                      report.append(
                              String.format(
                                      Locale.US,
                                      "• Hotspot/spill luma %.4f -> %.4f | reduction %.4f%n",
                                      hotspotBefore,
                                      hotspotAfter,
                                      hotspotReduction));

                      report.append(
                              String.format(
                                      Locale.US,
                                      "• Active pixels %.1f%% | dark-sky movement %.5f | global mean channel delta %.4f | active mean channel delta %.4f | max channel delta %.4f%n",
                                      activeFraction * 100.0,
                                      darkSkyMeanDelta,
                                      globalMeanChannelDelta,
                                      activeMeanChannelDelta,
                                      summary.maxChannelDelta));

                      report.append(
                              String.format(
                                      Locale.US,
                                      "• Protected dark pixels %d | protected saturated pixels %d%n",
                                      summary.protectedDarkPixels,
                                      summary.protectedSaturatedPixels));

                      report.append(
                              String.format(
                                      Locale.US,
                                      "• Detail guard edge-protected pixels %d | mean eligible edge %.4f%n",
                                      summary.protectedEdgePixels,
                                      summary.edgeSampleCount > 0
                                              ? summary.edgeStrengthSum
                                              / summary.edgeSampleCount
                                              : 0.0));

                      report.append(
                              String.format(
                                      Locale.US,
                                      "• Texture guard protected pixels %d | mean eligible texture %.4f%n",
                                      summary.protectedTexturePixels,
                                      summary.textureSampleCount > 0
                                              ? summary.textureStrengthSum
                                              / summary.textureSampleCount
                                              : 0.0));

                      report.append(
                              String.format(
                                      Locale.US,
                                      "• Detail %.4f -> %.4f | noise %.4f -> %.4f | ring %.4f -> %.4f | highlight clip %.4f -> %.4f%n",
                                      before.detail,
                                      after.detail,
                                      before.noise,
                                      after.noise,
                                      before.ringing,
                                      after.ringing,
                                      before.highlightClip,
                                      after.highlightClip));

                      if (qc.accepted) {

                          acceptedCandidate =
                                  candidate;

                          acceptedAfter =
                                  after;

                          selectedStrength =
                                  attemptStrength;

                          selectedAttempt =
                                  attempt;

                          break;
                      }

                      report.append(
                              "• Attempt rejected — ")
                              .append(
                                      qc.reason)
                              .append(
                                      "\n");

                      if (!candidate.isRecycled())
                          candidate.recycle();
                  }

                  if (acceptedCandidate == null) {

                      report.append(
                              "• Night-light control ROLLED BACK — adaptive safe-strength search exhausted");

                      if (lastQc != null) {

                          report.append(
                                  "; last QC: ")
                                  .append(
                                          lastQc.reason);
                      }

                      report.append(
                              "\n");

                      return new Outcome(
                              mastered,
                              before,
                              false,
                              report.toString());
                  }

                  report.append(
                          String.format(
                                  Locale.US,
                                  "• Night-light control ACCEPTED — adaptive strength %.3f on attempt %d/%d; all original QC gates passed%n",
                                  selectedStrength,
                                  selectedAttempt + 1,
                                  NightLightControlPolicy
                                          .MAX_ADAPTIVE_ATTEMPTS));

                  return new Outcome(
                          acceptedCandidate,
                          acceptedAfter,
                          true,
                          report.toString());'''

engine = (
    engine[:start_index]
    + replacement
    + engine[end_index:]
)

engine_file.write_text(engine)

# ------------------------------------------------------------
# HOST CHECK
# ------------------------------------------------------------
host_file = project / "tools/NightLightAdaptiveBackoffHostChecks.java"

host_file.write_text(
r"""package com.dennis.photomasterai;

public final class NightLightAdaptiveBackoffHostChecks {

    public static void main(String[] args) {

        require(
                NightLightControlPolicy.MAX_ADAPTIVE_ATTEMPTS == 5,
                "adaptive attempt count changed");

        double[] expected = {
                1.00,
                0.85,
                0.72,
                0.60,
                0.50
        };

        double previous =
                Double.POSITIVE_INFINITY;

        for (int i = 0; i < expected.length; i++) {

            double factor =
                    NightLightControlPolicy
                            .adaptiveStrengthFactor(i);

            requireClose(
                    factor,
                    expected[i],
                    "adaptive factor incorrect at attempt " + i);

            require(
                    factor < previous,
                    "adaptive factors must strictly descend");

            previous =
                    factor;
        }

        require(
                NightLightControlPolicy.MAX_MEAN_CHANNEL_DELTA
                        == 0.0080,
                "global mean channel QC ceiling changed");

        require(
                NightLightControlPolicy.MAX_CHANNEL_DELTA
                        == 0.0320,
                "max channel QC ceiling changed");

        requireClose(
                NightLightControlPolicy.adaptiveStrengthFactor(0)
                        * 0.225,
                0.225,
                "first attempt must preserve normal guarded strength");

        requireClose(
                NightLightControlPolicy.adaptiveStrengthFactor(4)
                        * 0.225,
                0.1125,
                "final attempt strength incorrect");

        System.out.println(
                "NightLightAdaptiveBackoffHostChecks passed");
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

# ------------------------------------------------------------
# SOURCE INVARIANTS
# ------------------------------------------------------------
assert "versionCode 44" in build_file.read_text()
assert "versionName '0.4.35'" in build_file.read_text()
assert "MAX_ADAPTIVE_ATTEMPTS" in policy_file.read_text()
assert "adaptiveStrengthFactor" in policy_file.read_text()
assert "adaptive safe-strength search exhausted" in engine_file.read_text()
assert "Night-light control ACCEPTED — adaptive strength" in engine_file.read_text()

print(
    "v0.4.35 applied: Night Light now searches downward for the "
    "strongest candidate that passes the unchanged QC gates"
)
