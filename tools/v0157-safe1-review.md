# Dennis AI Mastering Lab — v0.15.7 safe1 candidate (15 October 2026)

STATUS: Source-only checkpoint. **Not deployed, not accepted, no Android APK built.**

Base ref: `experiment/performance-profiling-v0157` commit `33be0b34642f3e1e79a1b972afcf642dcbd0a4f5`.
Branch: `experiment/performance-safe1-v0157`.

## Change

`tools/v0157-safe1-inactive-professional-side.patch` modifies only `ProfessionalMasteringRenderer.java` when explicitly applied to the existing assembled perf1 source. Neither v0.15.7 nor perf1 is edited. The existing perf1 build workflow does NOT apply this patch.

When `isVocal(mode)`, five side event controls are provably empty by `ProfessionalMasteringRenderer.kindInMode`. The rendered side-output filter contributions are therefore structurally inactive, yet baseline executes five stateful four-stage filters per sample. Safe1 bypasses those filters for vocal-only modes and retains all downstream sample equations, evidence gates, event maps, and mastering renderers.

## Local verification

Using original perf1 GitHub Actions source artifact 11788063766:
- Verified source ZIP SHA-256 from Actions-provided manifest.
- Patch applied with `patch --batch --fuzz=0 -p1`, byte-for-byte against independently edited source.
- JDK 21 synthetic 18-second 48-kHz stereo float fixture: original versus changed intermediate float WAV **byte-identical in all five non-limiter professional modes**; complete renderer Stats values also equal. The unchanged `LIMITER_CREST` mode does not enter `renderFloat`.
- Additional 20 preexisting host regression harnesses completed successfully, including v0.15.1 exact DSP, v0.15.2 bit-identical PCM, and v0.15.6 sparse peak.
- A repeatable exact renderer regression harness is saved in the conversation source checkpoint ZIP as `V0157ProfessionalInactiveSideHarness.java`, together with this patch. Generate legacy class before patch by replacing `ProfessionalMasteringRenderer` with `LegacyProfessionalMasteringRenderer` in original source.

## Not demonstrated

No reliable *whole song* speedup; no actual S23 Ultra benchmark of safe1; no bytewise independent comparison of exported final WAVs; no changed candidate-selection measurements. Do not claim app speed improvement. Do not replace current main mastering engine without those checks.

## Next steps

When additional QC arrives, inspect per-candidate statuses, rejection reasons and audio hashes for Off to the Show; establish if advanced failures are from premaster verification. After that, wire this separate patch into a dedicated safe1 GitHub Actions workflow with the exact regression harness, produce a side-by-side TEST APK, then alternate baseline and safe1 runs on S23 Ultra STANDARD profile. Require unchanged candidate eligibility, winner, final SHA-256, all QC and genuine repeatable runtime benefit. Roll back if any differences.
