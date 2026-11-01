# Dennis AI Mastering Lab v0.14.2 — Meaningful Mastering Gate

v0.14.2 is a focused sonic-decision repair built on v0.14.1. It does not add another broad DSP stack. It prevents a candidate from being presented as the mastering winner when its finished PCM is effectively a level-matched near-copy of the transparent control.

## What changed

- Added `MasteringDeltaEvidence`, a whole-song level-matched PCM comparison against the transparent delivery control.
- A non-transparent final winner now needs either:
  - a meaningful broad sonic delta (`>= -46.0 dB` residual relative to the level-matched transparent control), or
  - independent target-specific proof for a narrow repair already protected by its own promotion gate.
- Near-copy candidates are rolled back to the transparent control instead of being labelled as a sonic master.
- Narrow technical repairs are not blocked merely because they affect little audio: proven vocal-artifact repair, artifact repair, persistent-tone/channel repair and qualified stem controls can still promote when their dedicated evidence passes.
- Transient Intelligence now needs at least `0.10 dB` measured transient-peak reduction for its module benefit gate, raised from `0.04 dB`.
- The transparent candidate audio is retained through final selection so the last comparison uses actual finished PCM rather than plan assumptions.
- Candidate QC records the level-matched residual, correlation, gain match, gate verdict and final meaningful-mastering decision.
- Studio final QC now surfaces whether the base master contained a verified sonic change, which candidate was tested by the final gate and the measured delta verdict.
- The completed-project status distinguishes `Sonic mastering change verified` from `Transparent mastering control retained; no justified sonic change`.
- Studio delivery remains the requested two-WAV workflow only: 24-bit/48 kHz and 16-bit/44.1 kHz WAV. Internal QC/recipe data remain available in the project.

## Regression evidence

The uploaded v0.14.1 **The Old Girl** aligned source/master pair measured:

- level-matched residual: `-50.83 dB` relative to the source,
- residual level: `-67.33 dBFS`,
- correlation: `0.99999587`,
- gain match: `+0.0026 dB`.

That result is classified `NARROW_OR_SPARSE_DELTA_ONLY`, so it would no longer qualify as a broad mastering winner without separate target-specific evidence.

A synthetic broad spectral-tilt regression measured `-30.47 dB` level-matched residual and correctly passed as `MEANINGFUL_BROAD_SONIC_DELTA`.

`FullAiMasteringEngine.java` plus the new delta class compile cleanly in the host core check. The full Android APK compile remains the GitHub Actions gate because the local container does not contain Android platform 34.

Version: **0.14.2** / versionCode **21**.
