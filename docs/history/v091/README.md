# Dennis AI Mastering Lab 0.9.1

Private, fully offline Android mastering app. Its governing question is:
**would this be the restrained, musical remaster chosen from the original
stereo mix in a professional mastering session?** The complete v0.7 decision
path is therefore the frozen natural reference. Later modules may replace it
only after proving a larger technical benefit and preserving the music.

## Build identity

- Package: `com.dennis.aimasteringlab`
- Version: `0.9.1`; version code `11`
- Minimum Android: API 29; compile/target API 34
- Internet, microphone and broad storage/media permissions: none
- Requested permissions: foreground data processing, notifications and a
  bounded wake lock
- Signing-certificate SHA-256:
  `2d6822f2d6e3785a03bfe61d73d5139822c78c9b2dedbbc64aa0424ad44eff70`

Install directly over v0.9.0 or an earlier compatible build. Do not uninstall
first. Stored Preference Memory and Album Consistency history are retained.

## Natural professional policy

- Core, Stereo Intelligence and all v0.7 section/artifact/transient gates run
  unchanged and form the complete natural reference.
- v0.8/v0.9 candidates are ranked by technical score, not learned preference.
- Later processing must clear stricter mode-specific margins, demonstrate a
  targeted benefit and preserve LRA, crest, limiter pressure, correlation,
  protected edges, duration and continuity relative to v0.7.
- Translation Consensus uses all 12 playback-response simulations for QC only.
  It never creates EQ or another processing candidate.
- Persistent-tone and stable channel repair remain eligible only as narrow,
  confidently proven technical corrections.
- Optional reference calibration remains conservative and cannot import the
  reference's loudness or curve.
- A 24-bit/48 kHz, target-compliant release master is treated as already
  mastered even when its filename has been changed.

## Learned profiles

Preference Memory and Album Consistency are local mastering profiles, not the
12 translation-device simulations. Both controls start **OFF** in v0.9.1,
including after an in-place update. Their stored histories are retained and
can be explicitly enabled for a run. Preference can only act as a bounded safe
tie-break; neither profile can override hard QC or promote post-v0.7 processing.

## Deterministic render integrity

Every final candidate is rendered to a staging file, synchronised, checked for
exact WAV header/length/frame identity, then published. The engine retains only
the still-relevant finalist from each candidate family to bound cache use. If
any required candidate is shortened or cannot be verified, the run fails
closed and selects no winner; it cannot silently choose from a reduced pool.

## Retained system

The app retains Stereo Event Repair, Low-End Intelligence, Stereo Vocal Focus,
Section-Aware Mastering, artifact and transient repair, Stereo Depth/Reverb
Intelligence, Limiter/Crest Optimisation, LF Phase Rescue, Vocal Artifact
Intelligence, persistent-tone and channel repair, source guards, optional
reference/stems, Click/Dropout Repair, codec QC and the Confidence Map.

Optional stems retain the three-tier policy: Tier 1 may contribute guarded
processed-minus-original deltas, Tier 2 supplies measurements only and Tier 3
falls back to stereo. Raw stem sums never replace the authoritative mix.

## Use and background limits

1. Select the original authoritative 48 kHz stereo WAV, not a previous master.
2. Leave learned profiles off for neutral professional mastering unless an
   album or an explicit learned preference is intentionally required.
3. Optionally select a trusted stem ZIP or professional reference.
4. Create the master and review the winner, QC JSON and Confidence Map.

The foreground service may continue while the app is minimized or the screen
is off. It cannot resume a partial render after force-stop, reboot or Android
process death.

## Build

The private update-signing keystore is retained in this private handoff. Do not
publish or replace it. With Android platform/build-tools 34 installed:

```bash
export DAML_ANDROID_SDK=/path/to/android-sdk
./tools/build_apk.sh
```

See `Dennis_AI_Mastering_Lab_Handover_v0.9.1.md`,
`V091_SCOPE_AND_LIMITS.md` and `TEST_RESULTS.md`.
