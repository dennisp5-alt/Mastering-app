# Dennis AI Mastering Lab v0.14.1 — Candidate Pipeline Repair

- Bounds expensive core mastering competition to transparent, conservative, Sonic Finish, plus an album candidate when relevant.
- Bounds stereo and advanced specialist families to at most two candidates each: combined plus the strongest evidence-specific module when justified.
- Keeps quality-first limiter/crest and both vocal-artifact strengths when evidence supports them, so runtime reduction does not discard the stronger vocal-artifact path that won the v0.14 Ribbon of Black test.
- Keeps narrow technical tone/channel repair candidates when evidence exists.
- Removes the automatic post-selection gentler re-render to avoid a redundant full-song pass.
- Rejects specialist candidates before expensive whole-song feature learning when their measured targeted benefit cannot mathematically clear the later promotion floor.
- Reuses the source feature map for an exact transparent source-control render instead of learning the same whole-song features again.
- Studio progress is now range-mapped from source analysis through candidate mastering and final PCM QC. Candidate family, number, strategy and render/verification phase are explicit, so the percentage advances forward rather than repeatedly restarting at the same-looking stage.
- Removes user-facing v0.7 terminology from advanced mastering progress while retaining compatible legacy DSP/report identifiers internally.
- Studio base-master QC is PCM-only in this WAV-only workflow. AAC/SBC codec fallback, device-translation composites and radio/broadcaster simulations are skipped in Studio instead of adding full-song passes that cannot affect the two requested WAV outputs. Classic/AI Lab keep their broader release-QC path.
- Studio delivery exports only the requested 24-bit/48 kHz WAV and 16-bit/44.1 kHz WAV. MP3, per-track release ZIP and QC-report save buttons are removed from Studio output; QC remains internal for diagnostics/recovery.
- Studio finishing also skips radio/broadcaster simulation and verifies the two WAV files directly.
- Clarifies the no-op delivery message: an already-selected mastered candidate may need no additional delivery gain/limiting without implying that mastering itself was bypassed.


## Focused regression evidence

The repair path was exercised on both a synthetic 48 kHz stereo fixture and the actual v0.14 Ribbon of Black master. Progress stayed monotonic, the real track selected a changed `vocal_artifact_intelligence` result rather than simply terminating at delivery compliance, and Studio finishing produced only the two requested WAV deliverables. A fresh Android/GitHub Actions compile is still required before phone testing.
