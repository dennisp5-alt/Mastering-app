# DENNIS AI MASTERING LAB — v0.8.0 HANDOVER

Handover date: 2026-08-25  
Owner: Dennis

## Build identity

- App: Dennis AI Mastering Lab
- Package: `com.dennis.aimasteringlab`
- Version: `0.8.0`
- Version code: `9`
- Minimum Android: API 29
- Target/compile Android: API 34
- Requested permissions: none
- Internet permission/access: none
- APK Signature Scheme: v3 verified
- Signing-certificate SHA-256:
  `2d6822f2d6e3785a03bfe61d73d5139822c78c9b2dedbbc64aa0424ad44eff70`
- APK SHA-256:
  `03883f3f944c8e870b39a83748fca6c0a3260743f0bcd84ca078e2e550410e09`
- Release/source archive SHA-256 values: see the external
  `DennisAiMasteringLab-v0.8.0-CHECKSUMS.sha256` manifest generated after
  packaging (an archive cannot truthfully contain its own final hash).

Install v0.8.0 directly over v0.7.0. Do not uninstall first. Package, signing
certificate and private profile keys are unchanged, so Dennis Preference
Memory and Album Consistency data are retained.

## Authoritative behavior

The original stereo WAV remains the musical authority. The app is fully
offline and autonomously renders, measures, ranks and promotion-gates bounded
candidates. Exactly one verified winner is saved. This app remains separate
from Dennis Mastering Studio's deterministic release path.

The target is −14.00 LUFS-I after render verification, subject to dynamics
protection, with true peak no higher than −1.00 dBTP. Lower peaks are welcome.

## v0.8 corrections

1. Pre-master full-scale validation now compares candidate count with source
   count. Inherited samples are reported accurately; added samples fail.
2. Source exact-zero regions of 100 ms or longer persist into final QC even
   when dither changes the output zero count. Unresolved regions force a
   CONDITIONAL verdict and are never called repaired.
3. Stereo Event Repair diagnostics now distinguish non-finite samples,
   additional full-scale samples and inherited source samples.

## v0.8 additions

1. Stereo Depth/Reverb Intelligence — attenuation-only short side-band repair;
   no automatic widening.
2. Limiter/Crest Optimisation — three actual rendered time-constant profiles,
   verified and scored for peak control and dynamics preservation.
3. Low-Frequency Phase Rescue — local 25–60, 60–100 and 100–160 Hz side-band
   attenuation only.
4. Vocal Artifact Intelligence — centre-inferred sibilance, plosive and harsh-
   vowel micro-corrections without vocal separation or pitch/timing changes.

A combined v0.8 candidate is generated only when at least two modules are
eligible. New processing cannot win without hard QC, score margin, targeted
benefit and module-specific benefit.

## Controlled verification

- Integrity fixture SHA-256:
  `3054428ec859b17687b5fca620efef2dd3be7a87a79e633443e2d3c52d376e6b`
  — 48 kHz/16-bit identity, one inherited full-scale sample, exact source gap
  6.000000–6.125000 s, transparent and Stereo Event Repair final clips zero.
- Professional fixture SHA-256:
  `d832a4b33cc135525e456131fd999e0b9c2ea89889592aa2dcb88a0c5c262ec1`
  — all four modules, all three vocal-artifact families, all three LF bands,
  all five render modes, exact protected edges, no widening and zero clips.
- All application and harness sources compiled against Android 34.
- Full candidate-engine integration rendered all five new v0.8 candidates and
  correctly blocked the raw professional score leader at its promotion gate.

See `TEST_RESULTS.md` and `V080_SCOPE_AND_LIMITS.md` for measurements and
limitations.

## Physical/device validation still required

On Dennis's Samsung Galaxy S23 Ultra:

- install over v0.7.0 and confirm profile retention;
- verify Android AAC encode/decode and codec fallback;
- verify playback, document-provider saves, packages and cancellation;
- run repeated stereo-only and optional-stem bulk tests; and
- perform level-matched physical listening.

No physical listening was performed by the app or this build environment.
Device profiles are simulations and perceptual inferences only.

## Release filenames

- `DennisAiMasteringLab-v0.8.0.apk`
- `DennisAiMasteringLab-v0.8.0-Release.zip`
- `DennisAiMasteringLab-Source-v0.8.0.zip`
- `DennisAiMasteringLab-v0.8.0-CHECKSUMS.sha256`

Treat this v0.8.0 APK and source archive as the latest AI Mastering Lab
implementation. Do not resume development from v0.7.0 or an older source.
