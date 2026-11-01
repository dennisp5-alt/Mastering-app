# DENNIS AI MASTERING LAB — v0.6.1 HANDOVER

Handover date: 2026-08-12  
Owner: Dennis

## Authoritative build

- App: Dennis AI Mastering Lab
- Package: `com.dennis.aimasteringlab`
- Version: `0.6.1`
- Version code: `7`
- Minimum Android: API 29
- Target/compile Android: API 34
- Permissions: none
- Internet permission: none
- APK Signature Scheme: v3 verified
- Signing-certificate SHA-256:
  `2d6822f2d6e3785a03bfe61d73d5139822c78c9b2dedbbc64aa0424ad44eff70`
- APK SHA-256:
  `e53ca4f47452a130c35752bfcbb3dcb52d4e0d38d321e6dcb5bf1fa59028e1f1`

Install directly over v0.6.0. Do not uninstall first. The unchanged package,
certificate and private SharedPreferences names retain Preference Memory and
Album Consistency data.

Treat the v0.6.1 APK and source archive as the latest AI Mastering Lab build.
Do not resume development from v0.6.0 unless deliberately performing a
historical comparison.

## Purpose of this release

Dennis supplied bulk-test results showing that mastering audio was working but
seven report/UI details required correction. Version 0.6.1 fixes those details
without retuning the mastering engine.

### 1. Translation verdict propagation

Technical PCM/codec status and device-response status are now separate.
Examples:

- `PASS` — technical and all translation simulations pass.
- `PASS • TRANSLATION REVIEW` — technical master passes but one or more
  response proxies need review.
- `CONDITIONAL • TRANSLATION REVIEW` — a technical condition and a translation
  review both remain.

QC JSON includes `technical_verdict`, `translation_verdict`, `display_verdict`
and warning text naming each review profile and reason.

### 2. Translation gain normalization

Raw response-profile filter gain is no longer treated as actual clipping. Each
profile requests RMS matching to the unfiltered composite and is peak-capped
to that composite. Raw profile overshoot remains visible as
`raw_headroom_stress_db`. It is diagnostic only.

### 3–4. Confidence Map corrections

Learned one-second/microstate runs are summarized into duration-scaled macro
sections. Normal songs target 8–15 sections. Low occupancy is not called
silence unless measured at or below −55 LUFS.

Selected global gain, EQ, side control, compression and limiting are listed in
every section where they apply. The map separately counts:

- sections with any selected change;
- sections with global processing; and
- sections with section-specific changes.

### 5. Candidate decision clarity

Reports now distinguish raw score rank from later decision gates. Every
candidate has:

- `raw_score_rank`;
- `promotion_blocked`;
- `selected_after_promotion_gates`; and
- `decision_outcome`.

The fallback selection sentence explicitly says when the raw leader failed a
promotion gate and identifies the selected candidate's raw rank.

### 6. No-repair applicability

When no click/dropout repair occurs, `protected_regions_exact` is JSON `null`
and `protected_regions_verification` is `NOT_APPLICABLE_NO_REPAIR`. Applied
repairs continue to report a real pass/fail boolean.

### 7. Profile-state labeling

QC Preference/Album objects are explicitly the run-start snapshots used for
the completed master. The app panels are explicitly current device profiles
for the next run. Post-run A/B or album actions do not rewrite the completed
master's QC history.

## Retained mastering contract

- The original stereo WAV remains authoritative.
- Full AI competition remains separate from Dennis Mastering Studio's
  deterministic release path.
- Target is exactly −14.00 LUFS-I where priorities permit.
- True peak must be no higher than −1.00 dBTP.
- Flat gain and minimum processing remain preferred.
- The first 2 seconds and final `max(8 seconds, 4% of duration)` remain
  protected from local AI edits.
- Tier 1 stems may use guarded processed-minus-original deltas.
- Tier 2 stems provide control measurements only.
- Tier 3 stems are rejected and stereo fallback remains active.
- Raw stem sums never replace the authoritative mix.
- Preference/Album history cannot override hard safety or promotion gates.

No master DSP threshold, processing bound, promotion margin, stem gate,
continuity-repair gate or personalization cap changed in v0.6.1.

## Verified regression headline

The After 2 AM v0.6.0 master was used as a real-song maintenance fixture:

- 12 translation profiles PASS / 0 REVIEW after normalization;
- raw filter headroom stress remained visible on two profiles;
- 48 learned microstates became 11 macro sections;
- all 11 sections correctly showed selected global processing;
- 0 section-specific changes remained correctly separate;
- no-repair protected status reported N/A/null; and
- package ZIP integrity passed.

The Never Be Fancy regression retained the exact v0.6 output SHA-256:

`4517808696fca43c3116f474f1fb924e33a15371e4d6b0bc38465ae4fa704671`

Controlled click/dropout, stereo intelligence, Tier 1 stems, Tier 2 stems,
Preference Memory, Album Consistency, no-op and codec-safety render regressions
also passed. Full evidence is in `TEST_RESULTS.md`.

## Important test boundary

No physical speaker/headphone listening was performed here. Host tests cannot
execute the S23 Ultra's actual Android AAC codec, MediaPlayer, document picker
or install-over/profile-retention behavior. These phone checks remain required
before treating v0.6.1 as device-validated.

## Library/release filenames

- `DennisAiMasteringLab-v0.6.1.apk`
- `DennisAiMasteringLab-v0.6.1-Release.zip`
- `DennisAiMasteringLab-Source-v0.6.1.zip`
- `DennisAiMasteringLab-v0.6.1-CHECKSUMS.sha256`

The release/source ZIP hashes are recorded in the external checksum manifest
created after final packaging.
