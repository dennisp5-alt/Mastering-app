# v0.10.2 validation — 2026-09-26

Production APK: package `com.dennis.aimasteringlab`, version 0.10.2/code 14, minimum API 29, target API 34. Final build, v3 signature verification and zip alignment passed. No Internet permission.

APK SHA-256: `62253b0956a89bceece1b354a390d72d5b6a103e66d61d5ac632ccf21ee03b4e`.

Original signing certificate SHA-256: `2d6822f2d6e3785a03bfe61d73d5139822c78c9b2dedbbc64aa0424ad44eff70`.

## New passage and comparison checks

The actual production passage scanner and playback mixer were tested on deterministic PCM fixtures:

- Unchanged audio and a uniform +3 dB version both pass after fixed whole-file loudness matching.
- Local centre-presence reduction at 5–7 seconds is rejected: -5.621 dB measured change.
- Local stereo-width reduction at 5–7 seconds is rejected: -6.021 dB.
- Isolated transient crest loss at 10–11 seconds is rejected: -8.638 dB.
- Ending-tail reduction at 17–18 seconds is rejected: -8.111 dB.
- Cancellation interrupts the scanner; timestamps stay within the source duration.
- A/B switching remains on the same sample timeline. Maximum sample error against the unchanged reference is 0.000000104 when matching a +3 dB alternative.
- Shared seeking, mono arithmetic, the last seven frames, EOF, out-of-range seeking, mismatched-pair rejection, blind preference mapping and unchanged input hash pass.

These establish signal-level behavior and bounded protection. They do not prove that every musical change is beneficial or that all audible defects are detected.

## Gentler challenger

Policy tests pass for immutable parent parameters, 75% correction scaling, equivalent-benefit promotion, rejection of lost benefit, non-finite benefit, whole-song dynamics loss, increased limiter burden and passage failures. Release codec order preserves the selected candidate first even when another candidate ranks above it by raw score.

A full production competition rendered a transparent baseline, a controlled core correction and exactly one gentler challenger. Both corrected WAVs passed safety and passage checks. The original won because reducing the correction lost more than the allowed benefit:

| Measurement | Original correction | Gentler challenger |
| --- | ---: | ---: |
| Measured objective | 97.538675 | 97.410782 |
| Processing cost | 0.048000 | 0.036000 |

The retained winner measured -14.000000 LUFS-I and -7.571883 dBTP, with all frames preserved. This is a synthetic branch/guard regression, not an assessment of music quality. Automatic gentler promotion is exercised by policy tests; this rendered fixture deliberately retains the original correction.

## Supplied real master

Input: `LastRunDownTheRange_Master_24bit-48kHz.wav`, 77,689,058 bytes, 12,947,208 stereo frames, 269.7335 seconds.

SHA-256: `f78a0f49a215f07a00c0d0ff524a0fc9e6f6af731f716046fb9486ea0834676d`.

- Full analysis, competition, final QC and package run passes with the transparent winner and identical input/output SHA-256.
- No gain, repair, limiter, EQ, compression or additional dither. Compliant source preservation is reported separately from exact target achievement.
- Measured -14.04386137 LUFS-I / -1.09327863 dBTP. Final passage result: MEASURED_PASS.
- Gentler rendering is correctly skipped for the protected transparent winner.
- Four source-inherited mono warnings remain at 234–235, 238–239, 244–245 and 245–246 seconds; an inherited final boundary/late transient remains near 269.7 seconds.
- Independent -1 dB input variant normalizes with flat gain to -14.00000000 LUFS-I / -1.04941743 dBTP, without limiting. Unsafe gain and a stricter requested ceiling still fail.

## Retained regression suite

The uninterrupted full host suite completed with `V0102_HOST_GATE_PASS`:

- Natural policy, compliant-source boundaries and radio diagnostics/negative controls.
- All three limiter profiles: frame count and boundary timing, including short streams.
- Sparse limiting: -14.0001 LUFS-I / -1.2000 dBTP, maximum reduction 4.7058 dB and average 0.03198 dB.
- Flat codec attenuation: maximum sample error 0.000000177; limiter metadata retained.
- Isolated/overlapping per-channel clipping repair; dense/rounded negative controls.
- Optional candidate EOF recovery, corrupt source/staging rejection and cancellation.
- Byte-exact remastering of an already compliant synthetic master.
- Click/dropout and inherited source-resolution/zero-region accounting.
- All four v0.7 advanced renderers remain at approximately -14.0006 LUFS-I / -1.1969 dBTP; protected regions exact and correlations at least 0.99993474.
- Measured hum repair and diagnostic-only renderer guards.
- Six-entry host release package and background job callback lifecycle.

After that suite, the final small changes added the global dynamics gate to the gentler comparison and updated the model label. All sources were recompiled; passage/comparison/gentler policy and the full gentler-render integration were rerun successfully, followed by the final signed APK build. The real-file and retained DSP algorithms were not changed by those final edits.

## Limits and evidence

Evidence is saved in `docs/validation-v0102/`: full suite, final focused tests, gentler rendering, signed build and the real-file QC/preparation reports. The first draft of the new integration fixture omitted its required plan mode; that test-fixture error was corrected before the successful logged run, without changing production acceptance thresholds.

No handset/emulator installation, native AudioTrack playback, live Android MediaCodec, screen-off Android behavior or listening-quality certification was performed. The mixer tests execute production PCM comparison code but do not replace a phone playback check. Android AAC is honestly CONDITIONAL on the host and may require flat attenuation on a phone.
