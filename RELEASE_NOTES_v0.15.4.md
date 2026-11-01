# Dennis AI Mastering Lab v0.15.4 — Transient-Safe Tonal Audition (TEST)

This experimental update addresses songs that need significant tonal correction but whose ordinary adaptive broad candidates all fail measured one-second transient-crest preservation.

- Only after the original three fallback auditions yield **no eligible candidate**, and at least two fail the existing TRANSIENT_CREST_LOSS gate, a tonal-led source may request a fourth audition.
- That fourth audition uses the source-shaped EQ at 82% of its originally predicted corrective magnitude while **bypassing bus compression, local event shaping and stereo-width adjustments**. The mastering limiter still applies its original strict ceiling and the candidate must pass all existing transient, loudness, phase, technical, meaningful-broad-delta and objective gates. No gate is loosened.
- The new audition is skipped entirely when a normal candidate is eligible or the source is dynamics-led/mixed.
- The extra audition is identified as `adaptive_broad_tonal_transient_safe` in the JSON candidate and diagnostic output. If it fails verification, the transparent fallback remains authoritative.
- v0.15.3 peak-recovery and v0.15.2 performance and version-truth fixes are preserved. No GPU acceleration included.

Local host regressions: 18 suites including v0.15.4 eligibility/plan checks and prior 17 safety, DSP, reporting and candidate-selection checks.

**Limitations:** The user's original 'Last Run Down the Range' raw stereo WAV is not available, only the verified previous master and QC. This update has not yet been verified to produce a meaningful mastered output for that source. Some sources may remain peak-constrained at less than −14 LUFS. Instrumented phone tests and listening A/B remain required.
