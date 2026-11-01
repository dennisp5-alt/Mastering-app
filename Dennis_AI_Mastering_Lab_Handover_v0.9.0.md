# Dennis AI Mastering Lab — v0.9.0 handover

Handover date: 2026-08-26  
Owner: Dennis

## Authoritative build

- App: Dennis AI Mastering Lab v0.9.0
- Package: `com.dennis.aimasteringlab`
- Version code: `10`
- Minimum/target Android: API 29 / API 34
- Signing-certificate SHA-256:
  `2d6822f2d6e3785a03bfe61d73d5139822c78c9b2dedbbc64aa0424ad44eff70`
- Internet access: none
- Install directly over v0.8.0; do not uninstall first

Treat the v0.9.0 source archive as the latest implementation. Do not resume
development from the older v0.8.0 archive.

## New systems

1. Translation Consensus Correction uses the existing 12 device-response
   simulations and explicit family/device evidence. Corrections are cautious,
   short and attenuation-only.
2. Hum/Whine/Persistent Tone Repair detects stable tone families and offers
   narrow-notch candidates only when harmonic/non-musical evidence is strong.
3. Channel Integrity/Stereo-Balance Repair corrects only a small stable level
   fault supported across time and frequency. Ambiguous polarity, delay, swap,
   centre drift and musical panning are reported without destructive repair.
4. Already-Mastered/Lossy-Source Guard detects prior-master, limiter,
   bandwidth/pre-echo and effective-resolution evidence. It prevents broad
   second-pass mastering and blocks unsafe HF compensation.
5. Optional Reference-Aware Calibration accepts one reference WAV, level-
   matches it and learns bounded broad relationships only.
6. Background mastering uses a foreground data-sync service with progress,
   cancellation, Activity reattachment and a bounded partial wake lock.

## Retained authority and targets

The original stereo WAV remains authoritative. Stems are optional and retain
the three-tier trust policy. Raw stem sums never replace the mix. The verified
goal remains exactly −14.00 LUFS-I, subject to preserving the song and
dynamics, with true peak no higher than −1.00 dBTP. The ceiling is not a peak
target, and no-change remains an acceptable winner.

## Validation status

Controlled host regressions cover v0.9 source guards, lossy inference, actual
translation-profile consensus, hum/buzz harmonic families, musical-tone
negative control, polarity/delay evidence, channel correction, reference
calibration, every v0.9 renderer mode, engine/report integration, background
state handoff, legacy stereo/advanced/professional/source-integrity/
click-dropout behavior and Full AI package generation.

The signed APK is separately checked for package/version/SDK identity, ZIP
alignment, archive integrity, v3 signature, certificate and requested
permissions. Exact release hashes are authoritative in
`DennisAiMasteringLab-v0.9.0-CHECKSUMS.sha256`.

## Remaining physical validation

- Install v0.9.0 over v0.8.0 on the Samsung Galaxy S23 Ultra and verify profile
  retention.
- Run background jobs through Home, task switching and screen-off; test the
  notification Cancel action and Android battery-management behavior.
- Verify real device document providers, playback, AAC MediaCodec output and
  package opening.
- Perform level-matched human listening on representative releases.

Background continuation is process-local. Do not force-stop or reboot during a
job; v0.9 cannot resume a partially rendered song after process death.
