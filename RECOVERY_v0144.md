# Recovery checkpoint — v0.14.4 Adaptive Broad Mastering

Baseline: v0.14.3 Broad Mastering Requirement.

Primary defect addressed: a raw source could correctly be classified as needing broad mastering while every normal broad candidate failed preservation; the engine then had no safe route other than transparent fallback.

Current policy:

1. Preserve v0.14.3 broad-mastering classification and meaningful-delta gates.
2. Run normal candidate families first.
3. Only when reliable broad demand remains unresolved, render three adaptive broad fallback rungs.
4. Shape the ladder from measured EQ/dynamics demand rather than generic intensity presets.
5. Keep all existing preservation gates unchanged.
6. Select the safest qualifying rung within 0.030 technical-score points of the best adaptive result.
7. If none qualifies, keep transparent and report the unresolved broad need honestly.
8. Prior-master second-pass protection remains authoritative.
9. Studio delivery remains two PCM WAVs.

New source:
- `app/src/main/java/com/dennis/aimasteringlab/AdaptiveBroadMastering.java`

New tests:
- `tests/V0144AdaptiveLadderHarness.java`
- `tests/V0144AdaptiveSelectionHarness.java`
