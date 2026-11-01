# Dennis AI Mastering Lab v0.15.1 — Bit-Exact DSP Loop Optimisation

## Goal
Reduce repeated CPU work in full-track mastering and QC on Android without changing audio results, candidate selection, source-aligned A/B or safety gates.

## Changes
- Four-times oversampled `TruePeakMeter`: replace twelve-tap per-phase ring-buffer modulus with a sequential, conditional wrap, preserving coefficient order, exact IEEE-754 double multiply/add sequence, and padding behavior.
- `LookaheadLimiter`: replace per-input/per-output long-integer ring modulo with incremental position cursors. Ring storage/read order is unchanged.
- For ramped-attack profiles, skip a logarithm where measured peak <= positive limiter ceiling: the original maximum constraint is mathematically exactly zero.
- Where limiter gain is exactly unity, skip `Math.log10(1.0)` when calculating reduction statistics; still records zero, with all other branches unchanged.

No changes to DSP coefficients, mastering profiles, candidate generation, score thresholds, delivered format, dither sequence, EQ or compressor settings, true-peak interpolation or alignment. The v0.15.0 gated vocal-audition behavior is retained.

## Verification
`V0151ExactHotLoopHarness` compares original v0.15.0 class implementations (renamed under tests) against the optimized versions. It verifies bit-for-bit peak readings after long sequences and bit-for-bit output PCM and all limiter statistics across zero signal, sparse impulse, transient spikes and dense audio, at 48 and 192 kHz including the ramped attack variant. Host master regressions and Android Gradle build run on GitHub Actions.

## Caveat
These changes remove redundant arithmetic but do not eliminate repeated full-track candidate renders; on-device speedup is unproven until QC performance JSON from a completed v0.15.1 test is available. Comparisons across different songs can mislead. For highest confidence, benchmark v0.15.0 and v0.15.1 against *the same original file* (new import each time), keeping the original as the A/B reference.
