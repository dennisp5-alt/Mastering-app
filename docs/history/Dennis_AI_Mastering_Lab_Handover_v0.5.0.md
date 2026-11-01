# DENNIS AI MASTERING LAB — v0.5.0 HANDOVER

Handover date: 2026-08-12  
Owner: Dennis  
Status: engineering build complete; physical S23 Ultra validation pending

## 1. Build identity

- App: Dennis AI Mastering Lab
- Package: `com.dennis.aimasteringlab`
- Version: `0.5.0`
- Version code: `5`
- Minimum Android: API 29
- Compile/target Android: API 34
- Requested permissions: none
- Internet permission: none
- APK Signature Scheme: v3 verified
- Signing-certificate SHA-256:
  `2d6822f2d6e3785a03bfe61d73d5139822c78c9b2dedbbc64aa0424ad44eff70`
- APK SHA-256:
  `8b6a3015d346fc2db623af2fff09c8b4d9444e72705454e13e038182094a8441`

Install v0.5.0 directly over v0.4.0. Do not uninstall first. Package and
signing identity are unchanged.

## 2. Purpose and boundary

This is Dennis's secondary Full AI mastering experiment. It autonomously
renders, measures, rejects and selects one output. It remains separate from
Dennis Mastering Studio v0.5.0, whose deterministic Standard Master remains
that app's authoritative release path.

The original stereo WAV is always authoritative. AI Lab performs objective
measurement, track-trained inference and device-response simulation. It does
not claim physical listening, human A&R approval or label certification.

## 3. v0.5 additions

v0.5 adds three stereo-only modules so poor stems no longer prevent deeper AI
testing:

1. **Stereo Event Repair AI** — short frequency-specific attenuation of
   verified HF, upper-mid or low-mid events.
2. **Low-End Intelligence** — short sustained sub/bass bloom control and
   local low-side phase correction, with kick-like transient protection.
3. **Stereo Vocal Focus AI** — tiny short mid/side vocal-band focus from
   centre evidence without extracting or regenerating a vocal.

A fourth combined candidate is created only when at least two modules are
eligible. All use the authoritative stereo waveform only.

## 4. Candidate order and promotion

The app may test:

- five core stereo strategies;
- up to three individual stereo-intelligence strategies;
- one bounded combined stereo-intelligence strategy;
- up to three Tier 1 stem-delta strategies; or
- one Tier 2 analysis-only stem-control strategy.

Audio-identical core plans are deduplicated. Every actual render is reopened
and measured. The best new stereo candidate must exceed the guarded core stereo
winner by at least 0.060 score and achieve at least 0.100 measured targeted
benefit. Otherwise the established core result stays selected.

Candidates that fail a no-change, module or stem promotion gate are labelled
`promotion_blocked` and cannot return as codec fallbacks. A codec-safety
rerender repeats the selected processing mode.

## 5. DSP and safety bounds

- Event Repair source cut: no more than 0.60 dB per event.
- Simultaneous spectral cut: no more than 0.80 dB.
- Simultaneous low-end cut: no more than 0.65 dB.
- Vocal-focus mid lift: 0.14–0.30 dB.
- Vocal-focus side pocket: 0.08–0.20 dB.
- Combined simultaneous nominal edit: no more than 0.90 dB.
- Protected intro: first 2 seconds.
- Protected ending: final `max(8 seconds, 4% of duration)`.
- Required premaster source correlation: at least 0.995.
- Maximum stereo-intelligence LRA loss: 0.65 LU.

Broad low-end, tonal or centre-channel conditions are rejected as automatic
processing through coverage gates. No module retunes, time-warps, regenerates,
denoises or substitutes a stem sum.

## 6. Existing stem trust retained

- **Tier 1:** validated production stems may enter only as
  `mix + scale × (processed stem − original stem)`.
- **Tier 2:** securely aligned but poor/separation-derived stems may control
  short measurements; no stem waveform enters the render.
- **Tier 3:** unsafe stems are rejected and the stereo competition continues.

This means repeated low-quality stem rejection is no longer a blocker: the
three new modules still run when their stereo evidence is strong enough.

## 7. Loudness, peak and final QC

- Target: exactly −14.00 LUFS-I after verified render.
- Ceiling: no higher than −1.00 dBTP.
- The ceiling is not a peak target.
- Dynamics take priority when exact loudness would require excessive control.
- Final Android QC tests actual AAC 256 encode/decode, a 328 kbps SBC stress
  proxy and twelve response-profile simulations.

## 8. Reporting

The QC JSON now records:

- module availability and per-event evidence;
- automatic versus diagnostic-only status;
- actual edit ranges and correction count;
- source-only/no-stem-waveform identity;
- measured spectral, low-end and vocal-focus benefit;
- protected-edge deltas and source correlation;
- processing cost, score, rank and promotion block;
- intelligence promotion/fallback decision; and
- all retained v0.4 source, stem, PCM, codec and translation evidence.

## 9. Validation completed

- Full Android 34 source compilation passed.
- Package/version/API/empty-permission identity passed.
- Zip alignment, APK integrity, v3 signature and retained certificate passed.
- All four new candidates passed the controlled stereo fixture.
- Protected intro/tail remained exact; source correlation exceeded 0.99993.
- Every controlled candidate reached −14.0000 LUFS-I with zero clips and true
  peak below −3.12 dBTP.
- Two kick-like low windows were protected.
- QC JSON assertions for all new fields passed.
- Never Be Fancy retained the exact v0.4 winner hash
  `4517808696fca43c3116f474f1fb924e33a15371e4d6b0bc38465ae4fa704671`.
- Tier 1, Tier 2, delayed-stem and unsafe-path regressions passed.

See `TEST_RESULTS.md` for exact measurements.

## 10. Physical S23 Ultra work required

1. Install v0.5.0 over v0.4.0 and confirm app data retention.
2. Run representative songs with no stems, rejected stems, Tier 1 and Tier 2.
3. Confirm UI status, saved WAV/AAC/QC/package access and playback.
4. Confirm Android AAC encode/decode and SBC proxy completion.
5. Exercise cancellation and several long sequential jobs.
6. Compare level-matched v0.5 winners with v0.4 and Dennis Mastering Studio on
   the S23 Ultra, headphones, speakers and car system.

The build environment performed no physical listening.

## 11. Latest-source instruction

Treat `DennisAiMasteringLab-Source-v0.5.0.zip` as the latest Dennis AI
Mastering Lab source. Do not resume AI Lab development from v0.4.0 or from
Dennis Mastering Studio source. Dennis Mastering Studio v0.5.0 remains the
latest implementation of the separate deterministic app.
