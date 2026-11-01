# DENNIS AI MASTERING LAB — v0.4.0 HANDOVER

Handover date: 2026-08-12  
Owner: Dennis  
Status: engineering build complete; physical S23 Ultra validation pending

## 1. Build identity

- App: Dennis AI Mastering Lab
- Package: `com.dennis.aimasteringlab`
- Version: `0.4.0`
- Version code: `4`
- Minimum Android: API 29
- Compile/target Android: API 34
- Requested permissions: none
- Internet permission: none
- APK Signature Scheme: v3 verified
- Signing-certificate SHA-256:
  `2d6822f2d6e3785a03bfe61d73d5139822c78c9b2dedbbc64aa0424ad44eff70`
- APK SHA-256:
  `6f34ce1c5e8e2ee97f49ae7f24ea1e68738382f8b8fa73d635aac41eadc9dcb3`

Install v0.4.0 directly over v0.3.0. Do not uninstall first. The package and
signing identity are unchanged, so Android app data can be retained.

## 2. Purpose and authority boundary

This is Dennis's secondary Full AI mastering experiment. It autonomously
renders, measures, rejects and selects one output. It remains separate from
Dennis Mastering Studio v0.5.0, whose deterministic Standard Master remains
the authoritative release path in that app.

The original stereo WAV is always the authoritative musical mix. AI Lab
outputs do not automatically modify or replace a Dennis Mastering Studio
release. The app performs engineering measurement and inference, not physical
listening, human A&R judgment or record-label certification.

## 3. v0.4 user flow

1. Select one authoritative 48 kHz stereo WAV.
2. Optionally select a ZIP of stems.
3. Choose the destination for the Full AI 24-bit WAV.
4. The app analyses the stereo source and classifies the optional stems.
5. It renders all eligible stereo and stem-aware candidates.
6. It rejects unsafe candidates, applies promotion/no-change gates, selects one
   verified winner and runs codec/translation QC.
7. It exposes the master, AAC, QC JSON and compact package when applicable.

No manual A/B choice is required. A missing or rejected ZIP cannot prevent a
stereo result.

## 4. Guaranteed stereo baseline

The complete v0.3 five-strategy controller is retained:

- transparent flat gain;
- conservative corrective;
- tonal corrective;
- dynamic/transient;
- full combined.

Audio-identical plans are deduplicated. Every actual render is reopened and
measured. Hard safety rejection, transparent rollback, score ranking and
minimal-processing tie breaks remain active. The real-song Never Be Fancy
regression retained the exact v0.3 `tonal_corrective` winner hash and
measurements.

## 5. Why v0.4 exists

Separation services often supply stems that are musically useful but cannot
reconstruct the released stereo mix closely enough to be trusted as audio.
Earlier builds correctly rejected them from processed-minus-original deltas,
but then lost potentially useful event timing and overlap evidence.

v0.4 separates **waveform trust** from **analytical usefulness**. It can use a
locally aligned separated lead or instrument to locate a possible issue while
ensuring that every rendered sample still comes from the original stereo mix.

## 6. Three-tier stem trust

### 6.1 Tier 1 — production-stem audio delta

Tier 1 preserves the v0.3 path. It requires:

- safe ZIP paths, sizes, ratios, CRC and complete decode;
- compatible sample rate, channels and duration;
- reconstruction that is not classified as separation-derived;
- no global polarity risk;
- effectively zero reconstruction start/middle/end lag and drift;
- at least one stem with exact zero local lag/drift at five song positions;
- positive signed-polarity evidence; and
- explicit, unambiguous roles for any automatic vocal/masking use.

Tier 1 may create:

- `stem_vocal_humanity`;
- `stem_masking_pockets`;
- `stem_combined`.

Each uses only:

`output = authoritative mix + scale × (processed stem − original stem)`

A raw stem sum is analysis evidence only and is never the finished mix.

### 6.2 Tier 2 — analysis-only stem control

Tier 2 is intended for poor, partial or separation-derived reconstructions. It
still requires compatible, CRC-verified, completely decoded WAVs, safe global
polarity, an unambiguous explicit lead vocal and independent five-position
local timing/polarity evidence for each admitted control stem.

The local Tier 2 timing allowance is no more than ±5 ms lag and 5 ms measured
drift. Exact-zero alignment remains mandatory for Tier 1 audio deltas.

Tier 2 produces at most one `analysis_only_stem_guided` candidate. The renderer
does not open the stem archive. It reads only the authoritative stereo source,
so no separated-stem sample, raw stem sum or processed stem delta can enter the
output. The QC JSON explicitly records:

- `trust_tier = TIER_2_ANALYSIS_ONLY`;
- `accepted_for_analysis_only = true`;
- `accepted_for_audio_delta = false`;
- `stem_control_mode = analysis_only`;
- `analysis_only_control = true`;
- `stem_waveform_used = false`.

The guidance models may locate short de-essing and masking events. Phrase-level
vocal rides are disabled in Tier 2 because separated-stem amplitude is not
trusted strongly enough for autonomous gain automation.

Actual Tier 2 stereo controls are deliberately small:

- de-essing operates on the mid channel in the 4.8–11 kHz region;
- masking pockets operate on short side-channel events in the four learned
  vocal-overlap bands;
- de-essing evidence is scaled by 0.40 and capped at 0.35 dB;
- masking evidence is scaled by 0.55 and capped at 0.35 dB per event;
- simultaneous masking is capped at 0.45 dB;
- the total simultaneous nominal edit is capped at 0.55 dB.

### 6.3 Tier 3 — rejected

Tier 3 is used for unsafe archives, unsupported/incomplete audio, CRC faults,
global polarity risk, insecure local timing, material duration problems,
missing or ambiguous lead identity, or no locally trustworthy evidence. The
five-strategy stereo competition continues automatically.

## 7. Vocal and masking evidence

The lead role must be explicit and unique, with a filename-role margin above
the runner-up. Backing, harmony, choir and instrumental roles cannot silently
become the lead.

Vocal analysis learns phrase levels and sibilance from the current song. Tier
1 can use smooth sub-dB rides and event-controlled de-essing through a centered
processed-minus-original lead delta. Tier 2 retains only the de-essing event
locations and strengths as measurement controls on the stereo mix. Neither
tier retunes, time-warps, regenerates, denoises or hard-gates the performance.

Masking analysis requires the validated lead plus explicitly named, locally
secure competing instruments. Eligible roles include guitar, piano,
keys/organ, synth/pad, strings and brass. Vocals, drums, percussion, bass/sub,
clicks and ambiguous names remain excluded.

The model learns robust overlap behavior in:

- 250–700 Hz;
- 700–1600 Hz;
- 1600–3200 Hz;
- 3200–6500 Hz.

Broad recurring overlap remains diagnostic only; only short events are
eligible.

## 8. Protected regions and render gates

Every Tier 1 or Tier 2 correction is hard-zeroed in:

- the first 2 seconds; and
- the final `max(8 seconds, 4% of song duration)`.

The app verifies unchanged sample rate, stereo channels, 24-bit output, sample
count and duration; protected boundaries within the floating audit tolerance;
source correlation of at least 0.995; no introduced exact-zero interruption;
no non-finite or clipped samples; safe global/low-band correlation; and
protected LRA.

## 9. Autonomous promotion and fallback

Every candidate first passes format, duration, −14 LUFS/priority, −1 dBTP,
clipping, continuity, phase, mono and dynamics gates.

Tier 1 can replace the guarded stereo winner only when:

- score margin is at least 0.06; and
- measured global objective gain is at least 0.01, or bounded targeted-event
  evidence bonus is at least 0.04.

Tier 2 deliberately has no evidence-only promotion route. It must achieve:

- score margin of at least 0.08; and
- measured global objective gain of at least 0.015.

Analysis-only candidates also receive a reduced evidence weighting and an
extra processing-cost penalty. Admission therefore means only “safe to test,”
not “safe to select.” If the candidate does not prove the stronger result, the
stereo winner remains selected automatically.

## 10. Loudness, peak and codec policy

- Loudness goal: exactly −14.00 LUFS-I after verification.
- True-peak ceiling: no higher than −1.00 dBTP.
- The ceiling is not a peak target.
- Transparent gain is tested first.
- Minimum guarded limiting is used only when necessary.
- If exact loudness would exceed the song-derived limiter allowance, dynamics
  are preserved and the honest shortfall is reported.

Final Android QC tests the selected strategy with actual AAC 256 encode/decode,
a 328 kbps SBC stress proxy and response-profile translation simulation. A
codec-safety rerender repeats the selected source strategy. If a candidate
cannot pass codec QC, ranking falls through to the next eligible candidate.

## 11. Reporting

The QC JSON records:

- app/build identity and evidence labels;
- authoritative source properties and SHA-256;
- section map and diagnostics;
- stem archive hash, inventory and acceptance status;
- reconstruction and every local alignment probe;
- trust tier and classifier reasons;
- actual vocal, de-essing and masking evidence;
- every candidate plan, render, score, cost and rejection;
- control mode and stem-waveform-use proof;
- protected-boundary and source-correlation measurements;
- promotion/fallback decision;
- PCM, decoded AAC, SBC proxy and translation measurements;
- technical verdict, warnings and limitations.

Measurements, track-trained inferences, response simulations and supplied
human observations remain distinctly labelled. The app never claims physical
playback by itself.

## 12. Validation completed

- Clean source compiled with Android 34/build-tools 34.0.0.
- Package/version/API identity and empty permission list verified.
- Zip alignment, APK Signature Scheme v3 and retained certificate verified.
- No-stem Never Be Fancy winner and hash remained identical to v0.3.
- Already-compliant no-render/hash-preservation regression passed.
- Controlled Tier 1 reconstruction, local alignment, candidate rendering and
  valid stem promotion passed.
- Controlled poor reconstruction entered Tier 2 rather than Tier 1.
- Tier 2 phrase rides were disabled; de-essing/masking guidance remained.
- Tier 2 output passed protected boundaries, source correlation, phase,
  duration, peak and sample-integrity gates.
- Machine-checked QC JSON proved no stem waveform use.
- Tier 2 candidate was safely retained for comparison but not promoted when it
  failed to prove the stricter measured benefit.
- A deliberately 20 ms delayed guitar and ZIP traversal path were rejected.
- Full AI package listing and checksum content were verified.

See `TEST_RESULTS.md` for exact controlled measurements and hashes.

## 13. Physical S23 Ultra work still required

No Android device was connected to this build environment. Dennis should test:

1. install v0.4.0 directly over v0.3.0;
2. app-data retention and launch;
3. WAV and optional ZIP document pickers;
4. Tier 1, Tier 2 and Tier 3 wording on representative archives;
5. safe stereo fallback with no ZIP and a rejected ZIP;
6. long-run foreground behaviour and cancellation;
7. saved WAV/AAC/report/package access;
8. actual Android AAC encode/decode and SBC proxy completion;
9. playback and package opening; and
10. level-matched listening against v0.3 and Dennis Mastering Studio on the
    S23 Ultra, speakers, headphones and other relevant systems.

These pending tests prevent a claim of physical listening or phone-level final
certification, but do not invalidate the completed engineering gates.

## 14. Latest-source instruction

Treat this v0.4.0 source archive as the latest Dennis AI Mastering Lab
implementation. Do not resume AI Lab development from v0.3.0 or from Dennis
Mastering Studio source. Dennis Mastering Studio v0.5.0 remains the latest
implementation of the separate deterministic app.
