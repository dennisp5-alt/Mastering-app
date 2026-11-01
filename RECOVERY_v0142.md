# v0.14.2 Meaningful Mastering Gate recovery checkpoint

Base: v0.14.1 Candidate Pipeline Repair.
Package: `com.dennis.aimasteringlab`.
Version code/name: **21 / 0.14.2**.

## Purpose

v0.14.1 fixed repeated-looking candidate passes and bounded the competition, but The Old Girl test showed that `transient_intelligence` could still win even though the finished master was extremely close to the aligned source. v0.14.2 adds a final PCM-evidence gate so near-copy peak touches are not presented as a substantive mastering decision.

## Key implementation

- New `MasteringDeltaEvidence.java` computes level-matched whole-song covariance/gain, correlation and residual energy from aligned PCM.
- Broad-mastering floor: **-46.0 dB relative residual** after level matching.
- Narrow detection floor: **-56.0 dB** for reporting only; narrow candidates still require their existing independent target-specific evidence to bypass the broad floor.
- Final non-transparent winner is compared against the actual transparent candidate PCM.
- If the broad floor fails and no qualified narrow-repair evidence exists, selection rolls back to transparent and records the rejected strategy/reason.
- Transparent candidate audio is retained until final selection.
- Transient module-specific peak benefit threshold increased from 0.04 dB to 0.10 dB.
- Studio project/final QC persists meaningful-change status and final-gate evidence.
- User-facing Studio completion text explicitly states whether a sonic mastering change was verified.

## Validation

- `V0142MeaningfulDeltaHarness`: PASS.
- Actual The Old Girl pair: `-50.83 dB` residual relative, `0.99999587` correlation, `+0.0026 dB` gain match → correctly not broad-meaningful.
- Synthetic spectral-tilt case: `-30.47 dB` residual relative → correctly broad-meaningful.
- `FullAiMasteringEngine.java` + `MasteringDeltaEvidence.java` host core compilation: PASS.
- SDK-less Studio/report syntax sweep: no syntax/structure diagnostics in the changed files; unresolved Android media symbols are expected without Android SDK.

## Build

Create the public source archive without any signing key. GitHub Actions should build a side-by-side debug/test package `com.dennis.aimasteringlab.test142` using Android platform/build-tools 34 and Gradle 8.6. Do not publish any private keystore in the public repository.
