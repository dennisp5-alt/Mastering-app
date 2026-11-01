# v0.14.4 Validation

## Host compile / decision tests

- PASS — `V0144AdaptiveLadderHarness`
  - dynamics-led broad demand generates exactly three source-shaped rungs
  - compressor intensity rises across rungs
  - EQ remains restrained on a dynamics-led source
  - gentler rung uses slower attack to protect transients
  - compressor maximum reduction remains capped at 2.20 dB
- PASS — `V0144AdaptiveSelectionHarness`
  - materially stronger technical result can win
  - when candidates sit within 0.030 technical-score points, the lower-processing rung wins
- PASS — inherited `V0143BroadMasteringNeedHarness`
- PASS — inherited `V0143BroadAnchorHarness`
- PASS — inherited `V0143BroadRetentionHarness`
- PASS — `FullAiMasteringEngine.java`, `AdaptiveBroadMastering.java` and recursive host dependencies compiled under host Java.

## Safety policy

Adaptive fallback does not change the existing true-peak, clipping, stereo/phase, LRA, passage-preservation, technical-score, objective or level-matched broad-delta thresholds. It only creates additional bounded candidate plans after normal broad mastering remains unresolved.

## Android gate

The full Android 34 APK compile is performed by the existing GitHub Actions workflow. No private signing material is included in the public build-source ZIP.
