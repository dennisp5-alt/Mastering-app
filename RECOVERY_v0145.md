# v0.14.5 Recovery Checkpoint

Baseline: v0.14.4 Adaptive Broad Mastering.

Primary change: demand-aware preservation for unresolved dynamics-led adaptive fallback only.

Key files:
- `app/src/main/java/com/dennis/aimasteringlab/AdaptiveDynamicsPreservation.java`
- `app/src/main/java/com/dennis/aimasteringlab/AdaptiveBroadMastering.java`
- `app/src/main/java/com/dennis/aimasteringlab/FullAiMasteringEngine.java`
- `app/src/main/java/com/dennis/aimasteringlab/PassagePreservation.java`
- `app/src/main/java/com/dennis/aimasteringlab/StudioPipeline.java`
- `app/src/main/java/com/dennis/aimasteringlab/FullAiReportWriter.java`

Do not remove the transparent fallback. Do not apply the demand-aware envelope to normal candidates, tonal-led fallback, prior-master guarded material, clipping/phase/continuity safety, or ordinary passage presence/width/level/tail checks.
