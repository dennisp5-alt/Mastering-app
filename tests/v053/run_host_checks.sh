#!/usr/bin/env bash
# v0.5.3 host checks. Usage: run_host_checks.sh <new-source-root> <v0.5.1-source-root> <work-dir>
set -euo pipefail
NEW=$(cd "$1" && pwd); OLD=$(cd "$2" && pwd); WORK=$3
# v0.5.2 checks first (Trial B, one path), with the chain off for the old/new comparison.
mkdir -p "$WORK"; WORK=$(cd "$WORK" && pwd)
JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dstemlab.chained=false" bash "$NEW/tests/v052/run_host_checks.sh" "$NEW" "$OLD" "$WORK/v052"
S=$NEW/app/src/main/java/com/dennis/aimasteringlab
{ ls "$S"/*.java | grep -vE "/(AiLabActivity|AndroidCodecDecoder|AndroidCodecEncoder|ComparisonPlayer|MasteringForegroundService|StudioActivity|StudioJobs|StudioVisuals|StudioWaveformView)\.java"; find "$NEW/app/src/main/java/de" -name '*.java'; } > "$WORK/files"
mkdir -p "$WORK/cls"
javac -nowarn -encoding UTF-8 -d "$WORK/cls" @"$WORK/files" "$NEW"/tests/hoststubs/com/dennis/aimasteringlab/*.java \
  "$NEW"/tests/BuildConfig.java "$NEW"/tests/org/json/*.java "$NEW"/tests/v053/V053Checks.java
cd "$WORK/v052/fx"
echo "== Chained mastering (tone + repairs) with a loud −10 LUFS delivery"
for k in mix lm1 lm1c; do
  java -Xmx3g -Dfile.encoding=UTF-8 -cp "$WORK/cls" com.dennis.aimasteringlab.V053Checks chained $k.wav "$WORK/chained-$k" | grep '^CHAINED'
done
echo "ALL_V053_HOST_CHECKS_PASS"
