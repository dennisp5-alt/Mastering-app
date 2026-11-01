# Dennis AI Mastering Lab v0.14.3 — Broad Mastering Requirement

## Purpose

v0.14.2 proved that the meaningful-delta gate could detect a near-copy, but a raw-track test (`Whispers by the River`) still allowed a tiny `vocal_artifact_intelligence` correction to become the whole mastering winner. The QC measured only -53.88 dB of level-matched residual while the source was confirmed raw. v0.14.3 fixes the decision hierarchy rather than simply increasing processing strength.

## What changed

- Added `BroadMasteringNeed`: reliable whole-song analysis decides whether broad mastering is required before final candidate promotion.
- Demand uses measured broad EQ, bus-compression and stereo-plan evidence plus feature reliability and predicted objective gain. Local event correction by itself does not trigger broad mastering.
- Loudness compliance is explicitly excluded as a mastering-completion test.
- The candidate cache now retains the best verified broad core candidate even when a transparent/near-copy candidate has a slightly higher generic score.
- A broad anchor must clear hard technical QC, natural dynamics/limiter preservation and the existing level-matched broad-delta floor.
- When broad mastering is required, a narrow-only repair cannot replace that anchor. The narrow candidate is rejected as the whole-song winner and the verified broad anchor remains final.
- If no safe broad anchor exists, QC marks broad mastering as unresolved rather than silently claiming a local repair completed the master.
- Previous-master second-pass protection remains unchanged and can block broad processing entirely.
- Studio QC now records broad-mastering requirement, demand summary, anchor, anchor delta, anchor use and unresolved status.

## Validation completed before Android build

- `V0143BroadMasteringNeedHarness`: PASS.
- `V0143BroadAnchorHarness`: PASS. A meaningful broad spectral candidate beat the broad-change gate despite a lower generic score than a near-copy.
- `V0143BroadRetentionHarness`: PASS. Candidate-cache cleanup retained the broad anchor alongside the score-leading near-copy.
- Real raw-material classifier regression using an excerpt of the earlier aligned source: `BROAD_MASTERING_REQUIRED`, reliability 1.000, broad EQ demand 0.467 dB, predicted objective gain 1.680.
- Core v0.14.3 engine Java compiled successfully under host `javac 21`.

## Still intentionally conservative

This build does not force an EQ/compressor move merely to make the result sound different. If whole-song evidence is weak, transparent mastering remains valid. If broad mastering demand is strong but no candidate survives safety/preservation gates, the result is reported as unresolved rather than forcing a damaging master.

## Delivery

Studio remains WAV-only: 24-bit/48 kHz and 16-bit/44.1 kHz. Internal QC/recipe data remains available for diagnosis.
