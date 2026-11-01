# Dennis AI Mastering Lab v0.14.5 — Adaptive Dynamics Preservation

## Purpose
v0.14.5 addresses dynamics-led raw tracks where v0.14.4 correctly detected that broad mastering was required but all three adaptive broad candidates were rejected by generic preservation/technical scoring.

## What changed
- Hard safety remains unchanged: no clipping/non-finite samples, true peak <= -1.0 dBTP, continuity and stereo/low-band correlation guards remain authoritative.
- Normal tracks keep the existing preservation gates.
- Only unresolved, dynamics-led adaptive fallback may use a bounded demand-aware preservation envelope.
- The envelope scales modestly with measured dynamics demand and remains inside the engine's absolute LRA guardrail.
- A dynamics-led adaptive candidate must prove actual bus-compression activity plus a measurable, bounded crest or LRA change.
- The tonal objective is allowed to stay effectively flat for dynamics-led candidates, but may not materially regress.
- The generic LRA-loss technical penalty is partially recovered only for this dynamics-led adaptive gate, preventing the same intended dynamics move from being penalized twice.
- One-second transient preservation may move from the normal -0.70 dB crest floor toward, but never beyond, -1.05 dB according to measured dynamics demand. Centre presence, stereo width, passage level and tail protection are unchanged.
- Each adaptive rung now records its exact broad-delta, passage, preservation, technical and objective gate decision in QC.

## Why
The v0.14.4 How I Am test measured broad mastering required (dynamics-led) but 0/3 adaptive rungs were eligible. The problem was not source analysis or candidate generation; the generic preservation/technical policy could reject the very dynamics movement the source analysis requested.

## Test package
The GitHub Actions test build uses a side-by-side application ID so it can coexist with earlier test versions.
