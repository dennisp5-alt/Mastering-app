# Dennis AI Mastering Lab v0.15.5 — Guarded Sparse Peak Mastering TEST

- Conditional fifth audition only when measured broad-mastering demand is unresolved, source peaks make the −14 LUFS target unattainable by flat gain, and two or more fallback candidates fail the unchanged transient crest gate.
- Separate, stereo-linked 4 ms look-ahead / 70 ms release limiter profile. No change to earlier candidates, existing winning masters, compressor profiles or global passage thresholds.
- Strict activity bounds: maximum gain reduction <=3.5 dB, mean <=0.10 dB, >1 dB activity <=1%, >3 dB <=0.15%. Exact target within 0.08 LU and independently verified <=−1.1 dBTP required.
- Candidate-specific transient verification permits at most 40 one-second windows (<=15% of duration) of crest reduction no more than 3.0 dB *only when actual limiter activity is exceptionally sparse*, while rejecting sustained presence/width/level loss and any other failure. The ordinary passage rule remains unchanged for all other candidates.
- Existing objective, broad meaningful-change, stereo and clipping verification remain enforced. Candidate-specific, bounded technical-score tolerance is -0.75 versus transparent to account for measured necessary peak-control cost; ordinary technical rules are unchanged. Fail-closed transparent master if unavailable or rejected.
- Adds QC diagnostics for sparse-peak candidacy, limiter activity, passage outcomes and selection.
- This is an experimental test build. The user's approved standalone master is a subjective reference, not a blind copy of DSP. A full-device run is required to verify improved output and performance.

Actual source trial (4:29.73 WAV SHA256 48bdcafd...): −14.004 LUFS, −1.150 dBTP, LRA 5.635 LU; maximum limiter 3.071 dB, average 0.029 dB, >1 dB activity 0.42%, >3 dB activity 0%; passage gate accepted 38/40 crest windows with independent unchanged centre/width/tail guards; source-relative objective delta −0.0147; verified level-matched broad residual −30.16 dB. These are offline Java host results, not measured phone timing.
