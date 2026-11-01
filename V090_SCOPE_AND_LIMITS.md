# Dennis AI Mastering Lab v0.9.0 — scope and limits

## Release scope

v0.9 adds five offline, per-song candidate systems and background execution.
The authoritative stereo WAV remains the source of musical truth. No module
may replace it with a raw stem sum or a reference-derived curve.

| System | May do | Must not do |
| --- | --- | --- |
| Translation consensus | Generate short cut-only corrections when independent device families agree | Compensate for a weakness found only on bandwidth-limited phones |
| Persistent tone repair | Apply bounded, extremely narrow notches to stable harmonic tone families | Notch an isolated or musically ambiguous note automatically |
| Channel integrity | Correct a small, stable, cross-band technical level imbalance | Rebalance normal arrangement panning or guess artistic channel intent |
| Source guard | Restrict second-pass/HF processing and report codec/resolution evidence | Claim definite encoder provenance from spectral inference alone |
| Reference calibration | Compare level-matched broad tone, vocal prominence, dynamics and width | Copy an EQ curve, loudness, timing or stereo image |

Every audio change is edge protected, bounded and separately rendered. A
candidate must pass the existing duration, non-finite, clip, true-peak,
loudness, continuity, LRA, phase, source-correlation and measured-benefit gates.
Diagnostic evidence is not treated as proof that a correction is safe.

## Source guard policy

- Filename/hash and signal evidence are reported separately.
- Prior Full AI/DMS naming or a strong prior-master signature invokes a
  conservative second-pass guard.
- Under that guard, broad stereo, advanced, professional, translation,
  reference and stem-remastering modules cannot compete. Only a demonstrated
  narrow technical tone or stereo-balance repair may do so.
- Artificial 24-bit resolution is reported; writing a 24-bit container does
  not restore lost source detail.
- Codec-like bandwidth and pre-echo are inferences. A detected bandwidth wall
  blocks automatic high-frequency boosting.

## Background-processing boundary

Mastering is a user-started foreground data-sync service. It owns the active
job, notification, cancel action and bounded partial wake lock. The Activity
can detach and reattach while the process remains alive. v0.9 does not persist
intermediate DSP state and cannot resume after force-stop, reboot or process
death. Android may still stop the process under exceptional system conditions.

## Claims boundary

Host tests establish deterministic model, renderer, report and package
behavior. Device-response profiles are simulations and conclusions drawn from
them are perceptual inferences. No physical Samsung Galaxy S23 Ultra playback,
speaker/headphone listening, Android MediaCodec verification or human A&R
approval is claimed until those tests are performed by a person on the device.
