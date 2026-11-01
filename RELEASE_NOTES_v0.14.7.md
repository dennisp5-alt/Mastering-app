# Dennis AI Mastering Lab v0.14.7 — Honest QC Reporting

- No mastering DSP, audio acceptance gate, WAV format, or mastering target changes.
- Studio QC `app_version` reports `0.14.7`.
- Separately report the base mastering change and whether Studio applied additional edits.
- Deprecate ambiguous interpretation of `audio_unchanged_before_delivery_gain`: it now describes the whole pre-delivery processing chain. It is false if base mastering **or** Studio edits altered the audio.
- The old v0.14.5 tonal-objective threshold is labelled a *legacy diagnostic*, not a rejection verdict for an adaptive candidate accepted by the v0.14.6 scoped final gate.
- Update Studio and AI Lab version labels.
- For honest audible A/B verification, compare against the exact original source WAV at matched gain. No source was embedded in this build or inferred from the mastered output.

Test package remains side-by-side (`com.dennis.aimasteringlab.test147`) until a persistent, privately secured signing key is configured; changing only the package ID cannot ensure update installation.
