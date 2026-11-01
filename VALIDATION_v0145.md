# v0.14.5 Validation

## Focused host tests
- PASS — `V0145DynamicsPreservationHarness`
  - dynamics-led demand activates bounded envelope
  - moderate LRA/crest movement can pass
  - excessive LRA/crest/limiter movement remains rejected
  - tonal-led material does not activate the envelope
  - intended LRA technical penalty is partially recovered only in the dynamics-led gate
- PASS — `V0145PassageEnvelopeHarness`
  - standard -0.70 dB transient crest guard remains unchanged
  - dynamics-led adaptive path may accept a bounded ~-0.8 dB one-second crest move
  - stronger ~-1.3 dB transient shaving remains rejected
- PASS — `V0145AdaptiveAnchorHarness`
  - a qualified dynamics-led adaptive candidate survives final broad-anchor selection
- PASS — inherited `V0144AdaptiveLadderHarness`
- PASS — inherited `V0144AdaptiveSelectionHarness`
- PASS — inherited `V0143BroadMasteringNeedHarness`
- PASS — inherited `V0143BroadAnchorHarness`
- PASS — inherited `V0143BroadRetentionHarness`

## Safety
Hard true-peak, clipping, non-finite, duration, interruption, global stereo correlation, low-band correlation and absolute 1.80 LU LRA-loss gates remain unchanged. Normal candidate preservation is unchanged.

## QC diagnostics
Studio QC now carries `base_adaptive_broad_diagnostics`, one string per adaptive rung, with exact passage/preservation/technical/objective/broad-delta decisions.

## Android build
GitHub Actions performs the Android 34 build from the public v0.14.4 source ZIP plus the reviewed v0.14.5 patch. No private signing material is committed.
