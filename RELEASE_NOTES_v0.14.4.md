# Dennis AI Mastering Lab v0.14.4 — Adaptive Broad Mastering

## Purpose

v0.14.3 correctly identified raw tracks that required broad mastering and prevented narrow repairs from standing in for a whole-song master. One raw test, **How I Am**, exposed the remaining case: broad mastering demand was measured, but every normal broad candidate failed either the broad-delta or preservation gate, so transparent fallback was correctly retained.

v0.14.4 adds a bounded fallback rather than weakening those gates.

## New behavior

1. Run the normal bounded mastering candidate families.
2. If broad mastering is not required, preserve the existing policy.
3. If broad mastering is required and a normal broad anchor exists, preserve it; no adaptive ladder is rendered.
4. If broad mastering is still unresolved, generate exactly three source-shaped adaptive broad plans.
5. Classify the measured need as dynamics-led, tonal-led or mixed and scale only the justified whole-song controls.
6. Every adaptive candidate must still pass format, true-peak, clipping, stereo/phase, LRA, passage-preservation, technical-score, objective and level-matched broad-delta gates.
7. Among qualifying adaptive candidates, retain the lowest-processing rung within 0.030 technical-score points of the best rung.
8. If no rung qualifies, transparent fallback remains authoritative.

## Diagnostics added

- `base_adaptive_broad_attempted`
- `base_adaptive_broad_eligible`
- `base_adaptive_broad_decision`
- `base_adaptive_broad_anchor`

The full AI report also records adaptive candidate counts, decision and selected adaptive anchor.

## Delivery

Studio still publishes only:

- 24-bit / 48 kHz WAV
- 16-bit / 44.1 kHz WAV

QC/recipe data remain inside the saved project.
