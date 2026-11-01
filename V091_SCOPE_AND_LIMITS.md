# Dennis AI Mastering Lab v0.9.1 — scope and limits

## Governing policy

The authoritative original stereo WAV remains the musical truth. The complete
v0.7 winner is the natural reference. A newer module is not valuable merely
because it detects something or changes a metric; it must prove that the
result is a better professional master while preserving performance, dynamics,
transients, depth, duration, intro and ending.

| System | v0.9.1 role |
| --- | --- |
| v0.7 core/stereo/advanced path | Frozen natural reference and valid winner |
| v0.8 professional modules | Individually measured; strict technical and musical-preservation promotion |
| Translation profiles | QC simulation only; no candidate generation |
| Tone/channel repair | Narrow technical repair only when strongly proven |
| Optional reference | Bounded broad calibration; never curve/loudness copying |
| Learned profiles | Stored locally, OFF by default and explicitly opt-in |

## Integrity boundary

Required candidate files are complete transactions: staging render, flush,
filesystem sync, frame/length/header validation and publish. Candidate-family
audio is pruned only after its measurements are complete and its best possible
finalist is known. Any shortened or unreadable required candidate stops the run
with no winner.

## Source and second-pass boundary

Prior Full AI/DMS naming, prior-master evidence, or an already-compliant
24-bit/48 kHz release master invokes the second-pass guard. Broad mastering,
stems and reference processing are blocked. Only a confidently demonstrated
narrow technical fault may remain eligible. Remastering should start from the
original WAV.

## Claims boundary

Device-response profiles are simulations, not physical devices. Host tests do
not establish S23 Ultra playback, Android MediaCodec behavior or human
preference. Background continuation remains process-local and cannot survive a
force-stop, reboot or Android process death.
