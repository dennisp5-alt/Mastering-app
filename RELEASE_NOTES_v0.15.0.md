# Dennis AI Mastering Lab v0.15.0 — Guarded Sparse Vocal Limiter Auditions

- Directly targets the v0.14.9 Ribbon of Black measured bottleneck (professional candidate processing: approximately 19.1 minutes).
- Only in professional VOCAL_ARTIFACT and VOCAL_ARTIFACT_GENTLE candidates, BALANCED is rendered and independently QC-verified first. If maximum reduction is <=0.25 dB, average <=0.0015 dB, maximum step <=0.025 dB, crest/LRA loss <=0.08 each, LUFS is within 0.02 of -14, true peak stays below requested ceiling, and there are zero clips/nonfinite samples, further limiter time-constant trials are not justified for that sparse, bounded application. All other cases run all five profiles.
- Global LIMITER_CREST candidate always keeps the full limiter-profile competition. No EQ, dynamics/limiter DSP algorithms, mastering acceptance or safety thresholds are modified.
- QC performance now counts vocal limiter profiles actually rendered and sparse-audition early exits. Labels never claim unrendered profiles were verified.
- The original source-aligned A/B reference, source retention, two release WAVs and original full safety validation are unchanged.

Note: the optional sparse shortcut can change which limiter time-constant is chosen in an otherwise extremely lightly limited vocal candidate; it is bounded by measurements and does NOT prove bit-identical output to all 5-profile v0.14.9 results. Listen to and compare the completed phone master, and retain v0.14.9 as rollback. No whole-job speed improvement is promised without on-device evidence.
