# DENNIS AI MASTERING LAB — v0.7.0 HANDOVER

Handover date: 2026-08-21  
Owner: Dennis

## Authoritative build

- App: Dennis AI Mastering Lab
- Package: `com.dennis.aimasteringlab`
- Version: `0.7.0`
- Version code: `8`
- Minimum Android: API 29
- Target/compile Android: API 34
- Permissions: none
- Internet permission: none
- APK Signature Scheme: v3 verified
- Signing-certificate SHA-256:
  `2d6822f2d6e3785a03bfe61d73d5139822c78c9b2dedbbc64aa0424ad44eff70`
- APK SHA-256:
  `62070f192c2b48d5b371f1223140c9adf1877474f8b26e3461550e0f05a52651`

Install directly over v0.6.1. Do not uninstall first. The signing identity,
package and private Preference/Album storage keys are unchanged.

Treat this v0.7.0 APK and source archive as the latest AI Mastering Lab
implementation. Do not resume development from an older source handoff except
for deliberate historical comparison.

## New autonomous modules

### 1. Section-Aware Mastering AI

The module uses the learned song map only above a 0.70 confidence floor. If
measured quiet-to-loud lift is below 1.75 LU, it may attenuate demonstrated
quieter sections by 0.10–0.24 dB. Otherwise, recurring high-energy sections
with more than 1.60 LU spread may receive up to ±0.12 dB contour correction.
Broad automation over 65% of the song is diagnostic-only.

### 2. AI-Generation Artifact Repair

This is a provenance-neutral repair system. It measures short, robustly
unusual fizz, metallic-band and phasey high-frequency side signatures. It does
not classify a source as AI-generated. Eligible edits are frequency-specific,
with 0.55 dB maximum combined artifact attenuation. Any artifact kind covering
more than 12% of the song is rejected from automatic processing.

### 3. Transient Intelligence

Transient control is unavailable unless the target-gain prediction produces
limiter pressure. Securely isolated stereo peaks may receive at most 0.30 dB
control. Natural low-frequency impacts and attacks not requiring peak control
are explicitly recorded as protected evidence. Diagnostic output is bounded
for long-song bulk testing.

### Combined candidate

When at least two modules are eligible, the app also renders
`v07_advanced_combined`. Section, artifact and transient processing remain
inside a 0.75 dB simultaneous nominal-edit cap.

## Candidate promotion and safety

A v0.7 candidate must:

- preserve duration, channel count and 48 kHz sample rate;
- keep protected intro and ending samples exact;
- introduce no clips, non-finite samples or exact-zero interruptions;
- retain source correlation of at least 0.9955;
- stay within stereo/low-band phase guards and 0.50 LU maximum LRA loss;
- pass final loudness/true-peak hierarchy checks;
- beat the prior guarded winner by at least 0.055 score;
- achieve at least 0.100 measured targeted benefit; and
- pass the relevant measured section/artifact/transient benefit threshold.

Candidates that fail promotion remain reported but cannot re-enter codec
fallback. Each independently promoted lower-processing stage remains available
as a legitimate fallback if a later winner fails actual codec QC.

## Retained operating contract

- The original stereo WAV remains authoritative.
- Target: exactly −14.00 LUFS-I where higher priorities permit.
- True-peak ceiling: −1.00 dBTP.
- Flat gain/minimum processing remains preferred.
- First 2 seconds and final `max(8 seconds, 4% of duration)` are protected from
  local edits.
- Tier 1 stems may use guarded deltas; Tier 2 is measurement-only; Tier 3 is
  rejected to stereo fallback.
- Raw stem sums never replace the mix.
- Preference/Album history cannot override hard safety or promotion gates.
- Click/dropout repair remains sample-proven and separately guarded.

## Objective validation headline

- All production sources and JVM harnesses compiled against Android API 34.
- Controlled v0.7 fixture activated all three modules and the combined mode.
- Every v0.7 render preserved exact edges, zero clips and correlation above
  0.99993.
- Existing stereo, click/dropout, Tier 1/Tier 2 stem, preference, album,
  translation, package, codec-safety PCM and no-change regressions passed.
- “Never Be Fancy Remix” retained exact prior output SHA-256
  `4517808696fca43c3116f474f1fb924e33a15371e4d6b0bc38465ae4fa704671`
  because no v0.7 candidate cleared promotion.
- Both retained real-song sources independently measured exactly −14.0000
  LUFS-I and passed 12/0 response simulations.

Full evidence and limitations are in `TEST_RESULTS.md`.

## Device boundary

No physical speaker/headphone listening was performed. Host tests cannot run
the S23 Ultra's Android AAC codec, MediaPlayer, document picker or install-over
behavior. Install/profile retention, actual AAC encode/decode, output saving,
package opening, cancellation and matched-level listening remain required on
Dennis's Samsung Galaxy S23 Ultra.

## Release filenames

- `DennisAiMasteringLab-v0.7.0.apk`
- `DennisAiMasteringLab-v0.7.0-Release.zip`
- `DennisAiMasteringLab-Source-v0.7.0.zip`
- `DennisAiMasteringLab-v0.7.0-CHECKSUMS.sha256`

The final ZIP and APK hashes are recorded in the external checksum manifest.
