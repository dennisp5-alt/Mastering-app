# Dennis AI Mastering Lab v0.14.9 — Verified Candidate Efficiency

- Optimised stereo PCM24 WAV readFrame decoding in the shared WAV reader. Exact PCM-to-float conversion is retained; 16/32-bit, floating-point and other layouts still use the unchanged decoder.
- Shared the limiter-engagement predicate between the mastering renderer and the limiter profile audition. When limiting is mathematically bypassed, a single BALANCED render replaces five identical flat-gain profile renders. All active-limiter cases still audition every profile.
- Corrected QC language to disclose how many limiter profiles were actually rendered.
- No changes to candidate scoring thresholds, corrective EQ, compressor processing, limiter algorithms, dithering, mastering quality selection, or hard-safety gates.

Scope: first-pass deterministic optimisation. Device runtime and battery gains require fresh measurements; no percentage speed improvement is promised.
