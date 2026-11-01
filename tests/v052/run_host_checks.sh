#!/usr/bin/env bash
# v0.5.2 host checks. Usage: run_host_checks.sh <new-source-root> <v0.5.1-source-root> <work-dir>
set -euo pipefail
NEW=$(cd "$1" && pwd); OLD=$(cd "$2" && pwd); WORK=$3; T=$NEW/tests/v052
mkdir -p "$WORK"/fx "$WORK"/old "$WORK"/new; WORK=$(cd "$WORK" && pwd)
compile() { # root out
  local root=$1 out=$2; local S=$root/app/src/main/java/com/dennis/aimasteringlab
  { ls "$S"/*.java | grep -vE "/(AiLabActivity|AndroidCodecDecoder|AndroidCodecEncoder|ComparisonPlayer|MainActivity|MasteringForegroundService|StudioActivity|StudioJobs|StudioVisuals|StudioWaveformView)\.java"; find "$root/app/src/main/java/de" -name '*.java'; } > "$out.files"
  javac -nowarn -encoding UTF-8 -d "$out" @"$out.files" "$NEW"/tests/hoststubs/com/dennis/aimasteringlab/*.java \
    "$NEW"/tests/BuildConfig.java "$NEW"/tests/org/json/*.java "$T"/StudioPathRegression.java "$T"/ImportedTrialBHarness.java "$T"/AppDeliver.java
}
compile "$OLD" "$WORK/old"; compile "$NEW" "$WORK/new"
python3 "$T/make_fixtures.py" "$T" "$WORK/fx"
cd "$WORK/fx"
echo "== 1. One mastering path: base masters must be bit-identical to v0.5.1"
for k in mix lm1; do
  o=$(java -Xmx3g -cp "$WORK/old" com.dennis.aimasteringlab.StudioPathRegression $k.wav "$WORK/run-old-$k" | grep '^STUDIO')
  n=$(java -Xmx3g -cp "$WORK/new" com.dennis.aimasteringlab.StudioPathRegression $k.wav "$WORK/run-new-$k" | grep '^STUDIO')
  echo "old: $o"; echo "new: $n"
  [ "$(echo "$o" | grep -o 'base=[0-9a-f]*')" = "$(echo "$n" | grep -o 'base=[0-9a-f]*')" ] || { echo "BASE MASTER CHANGED for $k"; exit 1; }
done
echo "== 2. Trial B on app-style masters (peaks at/above the ceiling): loudness must be kept"
java -cp "$WORK/new" com.dennis.aimasteringlab.AppDeliver lm1c.wav own.wav -10.6
for db in 0.2 0.5; do
  python3 "$T/scale_wav.py" "$T" own.wav own_$db.wav $db
  for v in old new; do
    java -Dfile.encoding=UTF-8 -cp "$WORK/$v" com.dennis.aimasteringlab.ImportedTrialBHarness own_$db.wav "$WORK/tb-$v" | grep '^TRIALB ' | sed "s/^/[$v] /" | tee -a "$WORK/trialb.txt"
  done
done
grep '^\[new\]' "$WORK/trialb.txt" | python3 -c '
import re,sys
for line in sys.stdin:
    ch=float(re.search(r"change=([+-][0-9.]+)",line).group(1)); tp=float(re.search(r"out=[-0-9.]+ LUFS/([-0-9.]+) dBTP",line).group(1))
    assert abs(ch)<=0.10, "loudness not kept: "+line
    assert tp<=-0.995, "true peak above -1.0: "+line
print("TRIAL_B_LOUDNESS_KEPT_PASS")'
echo "== 3. Trial B on a master already over the ceiling: peak-safe and clearly reported"
java -Dfile.encoding=UTF-8 -cp "$WORK/new" com.dennis.aimasteringlab.ImportedTrialBHarness hot.wav "$WORK/tb-hot" | grep '^TRIALB ' | tee "$WORK/hot.txt" || true
if grep -q 'Exception' "$WORK/hot.txt"; then exit 1; fi
python3 -c '
import re
line=open("'"$WORK"'/hot.txt").read()
tp=float(re.search(r"out=[-0-9.]+ LUFS/([-0-9.]+) dBTP",line).group(1)); assert tp<=-0.995, line
assert ("Loudness kept" in line) or ("Quieter than the original because" in line), line
print("TRIAL_B_HOT_MASTER_PASS")'
echo "ALL_V052_HOST_CHECKS_PASS"
