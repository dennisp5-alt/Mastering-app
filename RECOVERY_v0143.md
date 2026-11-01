# Recovery checkpoint — v0.14.3 Broad Mastering Requirement

Baseline: v0.14.2 Meaningful Mastering Gate.

Primary defect fixed: a raw source could produce a narrow, well-verified repair and allow that tiny repair to become the entire mastering winner even though the track still needed broad tonal/dynamic finishing.

Current policy:

1. Analyze source and source-history guard.
2. Build the bounded core mastering candidates.
3. Assess Broad Mastering Requirement from reliable whole-song plan evidence.
4. If broad mastering is required, retain at least one safe broad-delta core candidate through cache cleanup.
5. Specialist/narrow repairs may compete, but a narrow/sparse final delta cannot replace the verified broad anchor.
6. Existing hard true-peak, clipping, stereo/phase, LRA, passage-preservation and limiter guardrails still apply.
7. Prior-Dennis-master second-pass protection remains authoritative.
8. Studio delivery remains two WAV files.

New source files/tests:
- `app/src/main/java/com/dennis/aimasteringlab/BroadMasteringNeed.java`
- `tests/V0143BroadMasteringNeedHarness.java`
- `tests/V0143BroadAnchorHarness.java`
- `tests/V0143BroadRetentionHarness.java`
- `tests/V0143RealRawNeedHarness.java`

Important QC fields:
- `broad_mastering_required`
- `broad_mastering_need`
- `broad_mastering_decision`
- `broad_mastering_anchor`
- `broad_mastering_anchor_used`
- `broad_mastering_unresolved`
- `broad_mastering_anchor_delta`

Rollback reference remains v0.13.1; v0.14.2 is the immediate previous experimental branch.
