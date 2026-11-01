# v0.10.1 validation — 2026-09-25

Signed production APK built and verified: package com.dennis.aimasteringlab, version 0.10.1/code 13, minimum API 29 and target API 34. Signature v3 verifies with the original certificate. No Internet permission.

APK SHA-256: a7292e9363504366903eb375ad4c476019ccb557c9b26d3f2cb32e97dc1e05ad.

## Reported real-file failure

Input LastRunDownTheRange_Master_24bit-48kHz.wav: 77,689,058 bytes, 12,947,208 stereo frames, 269.7335 seconds. SHA-256 f78a0f49a215f07a00c0d0ff524a0fc9e6f6af731f716046fb9486ea0834676d.

The old renderer rejected approved gain +0.04386137 dB predicting -1.04941726 dBTP. The new end-to-end host run followed the phone's analysis, source guard, learned sections, diagnostics, repair detection, next-generation model, brain, competition, release QC and package path, without optional reference/stems/profiles.

- Transparent winner: zero gain, zero render passes, no limiter or repair.
- Final source and master SHA-256 match exactly.
- App meter: -14.04386137 LUFS-I, -1.09327863 dBTP.
- COMPLIANT_SOURCE_PRESERVED; exact-target flag correctly false.
- Four source-inherited mono warnings: 234–235, 238–239, 244–245 and 245–246 seconds.
- Inherited ending boundary and late transient near 269.7 seconds. These are audition requests, not proof of an audible defect or of its cause.
- Preparation text, QC JSON and ZIP inclusion passed.
- Android AAC unavailable on host, honestly CONDITIONAL. A real phone's codec may require small flat attenuation.

Separate reproduction scaled the same audio down 1 dB, so preservation could not mask the renderer bug. Flat normalization delivered -14.00000000 LUFS-I and -1.04941743 dBTP without limiting and preserved sample count. Excess gain and a stricter -1.2 ceiling correctly failed.

## Other completed checks

- Compliant bounds/format/finite values/peak/hash/frames/gain/render/limiter safeguards: PASS.
- Radio clean source, uncorrelated-width negative control, local phase, inherited versus introduced classification, ending transient and cancellation: PASS.
- All three limiter profiles preserve boundary positions and frame count, including streams shorter than lookahead: PASS.
- Sparse fixture: -14.0001 LUFS-I/-1.2000 dBTP; max reduction 4.7058 dB, average 0.03198 dB, 1.0354% above 1 dB, 0.2945% above 3 dB: PASS.
- Flat codec attenuation: maximum sample error 0.000000177, original limiter metadata retained: PASS.
- Isolated/overlapping per-channel clip repair and dense/rounded negative controls: PASS.
- Optional EOF recovery, invalid-source/staging rejection and cancellation: PASS.
- Exact-target synthetic master reprocessing preserves every byte: PASS.
- Click/dropout, source-resolution, inherited clipping and zero-region provenance: PASS.
- All four v0.7 advanced candidates: PASS, about -14.0006 LUFS-I/-1.1969 dBTP, protected regions exact, correlation at least 0.99993474.
- Measured hum repair and diagnostic-only renderer guards: PASS.
- Full package including Radio Preparation: PASS (six host entries without AAC).
- Background job callback lifecycle harness: PASS.

The initial combined run encountered an unexpectedly truncated advanced test WAV: 7,340,032 bytes instead of 10,944,044 declared by its header. That fixture was regenerated; its complete length and SHA-256 were verified before and after a successful isolated rerun. Remaining gates passed. The initial log is retained; the scratch truncation's cause was not established. No application guard was relaxed.

No handset/emulator, live MediaCodec, speaker audition or studio-quality certification was performed. The background harness checks internal callbacks, not Android screen-off behavior.
