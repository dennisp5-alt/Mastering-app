# v0.14.2 Meaningful Mastering Gate validation

- `V0142MeaningfulDeltaHarness`: PASS.
- The Old Girl aligned source/master regression: -50.83 dB level-matched residual, -67.33 dBFS residual, correlation 0.99999587, gain match +0.0026 dB; correctly classified `NARROW_OR_SPARSE_DELTA_ONLY`.
- Synthetic broad spectral tilt: -30.47 dB level-matched residual; correctly classified `MEANINGFUL_BROAD_SONIC_DELTA`.
- Core engine compile (`FullAiMasteringEngine` + `MasteringDeltaEvidence` and dependencies): PASS.
- Studio/report SDK-less parse attempt produced no syntax/structure diagnostics in the modified files; remaining diagnostics are Android media classes unavailable in this container.
- Full Android APK compile/device test: pending GitHub Actions.

---

# v0.14.1 Candidate Pipeline Repair validation

Static host validation passed for the modified v0.14.1 branch: zero Java syntax/structure diagnostics in the SDK-less parse sweep; no diagnostics in the two modified core engine/QC classes; and 20/20 source-policy assertions passed for versioning, candidate bounding, pre-scan promotion floors, source-control feature reuse, forward range-mapped Studio progress, PCM-only Studio base QC, two-WAV output, skipped Studio broadcaster simulation, and clarified delivery wording. Android/`org.json` unresolved symbols are expected on this host because the Android SDK is not installed. A fresh APK compile remains the GitHub Actions gate before phone testing.

The v0.14 Ribbon of Black run that triggered this repair completed at approximately −14.000 LUFS-I / −1.375 dBTP / 4.367 LU LRA, but its UI exposed repeated-looking whole-song candidate passes. v0.14.1 addresses the redundant pass budget and progress presentation before broader bulk testing.

Executable host Studio regression also passed: a 24-second synthetic 48 kHz stereo project completed import → candidate mastering → PCM-only final QC → WAV-only finishing with monotonic progress, a −14.000000 LUFS-I 24/48 master, a −14.000251 LUFS-I 16/44.1 derivative, zero codec attempts, and no Studio MP3/release-ZIP project references.


Real-file regression also passed on the actual v0.14 Ribbon of Black master. v0.14.1 progressed monotonically 0→100, selected `vocal_artifact_intelligence`, changed the PCM hash, retained approximately −14.000 LUFS-I / −1.374 dBTP / 4.367 LU LRA, and reported 5/5 eligible target events improved with none worsened. Host mastering time was about 45.4 seconds. WAV-only finishing then completed in about 22.3 host seconds with 24/48 and 16/44.1 WAV outputs only; no MP3/release-ZIP reference was created. Host timing is not a phone performance guarantee.

---

# v0.13.1 import recovery validation

All three focused host harness invocations passed, including before/after path regression, four failed-project recoveries, corruption/cancellation/path guards, full real Pilot Light recovery through mastering and all exports, plus inherited Studio integration. See docs/validation-v0131. Physical phone tests remain outstanding.

---

# v0.13.0 validation

Completed all seven studio additions. See README.md, RECOVERY_v0130.md and docs/validation-v0130 for implementation, test results and current limits. The full host gate and independent real-song format verification passed. Final APK uses the retained update certificate.

---

# v0.12.0 validation — 2026-09-30

The full host regression runner completed with V0120_HOST_GATE_PASS. After the final bounded-export-retry change, focused export tests and the complete package harness passed again. Current logs and independent measurements are in docs/validation-v0120; prior-version logs are historical.

## New audio checks

| Check | Result |
|---|---|
| 48→44.1 kHz resampling | Exact rounded frame count and impulse timing; linked stereo/phase retained |
| Resampler frequency response | 18 kHz: +0.0001 dB; 23.5 kHz alias rejection: 96.70 dB |
| Moving-frequency fixture | Tracked 7300–9000 Hz; target reduction 1.0784 dB; 1 kHz body change −0.00544 dB; protected intro/ending exact |
| Musical counterexamples | Repeated cymbal-like and kick-like bursts vetoed |
| Oversampled final render | −14.002054 LUFS-I, −1.237143 dBTP, complete 288000-frame output; stereo and short-stream alignment pass |
| Full cleanup competition | Both gentle/full strengths passed actual finished-audio checks; 3/3 events improved, none worsened. Full strength selected at −14.004349 LUFS-I / −1.194098 dBTP |
| False improvement controls | Unchanged audio, uniform gain, worsened target, steady brightness and side-only bursts rejected |

## Exports and recovery

The bundled MP3 and independent FFmpeg decoder agree on complete decoded duration. WAV16 is stereo PCM16/44100; MP3 is stereo CBR320/44100 with a title and gapless metadata. Full ZIP payload, reports and SHA manifest passed; the host fixture package has eight entries because Android AAC is unavailable.

The final retry test deliberately truncates an export temporary file during the first checksum pass. It requires a complete independently measured recreation on attempt two and records the first failure. Persistent invalid destinations stop after two attempts; no partial derivative is exposed. Cancellation propagates; verified-master hashes stay unchanged. Truncated MP3 frame listings are rejected.

An initial real Corner Stage run caught a short temporary 16-bit WAV and retained the verified master. This prompted the bounded retry and its fault-injection test. No partial export was accepted.

## Previously failing Claude master

LastRunDownTheRange completed the entire mastering, QC and packaging path. Its 24/48 master remained byte-for-byte unchanged: SHA256 f78a0f49a215f07a00c0d0ff524a0fc9e6f6af731f716046fb9486ea0834676d, −14.04386137 LUFS-I, −1.09327863 dBTP.

| Output | App LUFS-I | App dBTP | Independent FFmpeg LUFS-I / dBTP |
|---|---:|---:|---:|
| 16-bit/44.1 WAV | −14.043714 | −1.069412 | −14.05 / −1.09 |
| MP3 320 | −14.375166 | −1.197433 | −14.38 / −1.21 |

Both derivatives decode to 11895247 frames. The MP3 needed two encoding passes and −0.332184 dB flat codec headroom. The master and 16-bit derivative were not attenuated for MP3. Its inherited mono and late-ending warnings remain; they are listening prompts.

## Retained gates

Passage-damage detection, matched-level aligned A/B, gentler correction policy, sparse limiter burden, isolated declipping, dense/rounded-peak negatives, click/dropout repair, source provenance, protected regions, hum repair, corrupt-source rejection, optional candidate recovery, cancellation, no-change idempotency and background-job host callbacks passed.

## APK and limits

Signed package com.dennis.aimasteringlab, version 0.12.0/code 16, minAPI29/target34. Original signing certificate retained; APK entries/CRC and bundled LGPL license verified. No Internet permission.

Host Android AAC is unavailable and correctly reported CONDITIONAL. Actual installation, phone UI/playback, AAC and screen-off operation remain device checks. No human listening comparison was performed. Synthetic proxies and a small real-song set cannot establish perceived studio quality or general accuracy across every genre.

## The Corner Stage final release run

The complete final-code run passed, selecting vocal_artifact_intelligence at −14.000000 LUFS-I / −3.367751 dBTP without limiting. Both strengths improved all four eligible events; full-strength mean target reduction was 0.275 dB, with no worsened event. Master SHA256 326c5be6815b3458fc105d193c241d614d46d8bde7f7990910559609e7887054.

| Export | LUFS-I | dBTP | Decoded frames |
|---|---:|---:|---:|
| wav16 | -13.999946 | -3.371458 | 16532208 |
| mp3 | -13.998858 | -3.410352 | 16532208 |

Export attempts: 1; MP3 encoding passes: 1. The final package contains the master, both new derivatives, reports and checksums.

Independent FFmpeg also verified The Corner Stage derivatives: both −13.99 LUFS-I; WAV16 −3.37 dBTP, MP3 −3.41 dBTP; both exactly 16532208 decoded frames. Small loudness-meter differences reflect their measurement implementations.
## v0.14.4 focused tests

- `V0144AdaptiveLadderHarness`: PASS
- `V0144AdaptiveSelectionHarness`: PASS
- inherited v0.14.3 broad need/anchor/retention harnesses: PASS

Observed dynamics-led test ladder: gentle 1.26:1 / 1.03 dB max GR -> firm 1.38:1 / 1.54 dB max GR, with restrained EQ and unchanged preservation policy.


## v0.14.5 focused regressions
PASS: V0145DynamicsPreservationHarness
PASS: V0145PassageEnvelopeHarness
PASS: V0145AdaptiveAnchorHarness
PASS: inherited v0.14.4 adaptive ladder/selection and v0.14.3 broad need/anchor/retention tests
