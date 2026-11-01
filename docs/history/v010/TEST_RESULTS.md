# v0.10.0 validation — 2026-09-15

Engineering validation only. No physical Android installation, live Android AAC encode/decode or human listening was performed here.

## Build

APK version 0.10.0/code 12, package com.dennis.aimasteringlab, min/target 29/34. APK v3 signature verified with the retained certificate `2d6822f2d6e3785a03bfe61d73d5139822c78c9b2dedbbc64aa0424ad44eff70`. Permissions remain FOREGROUND_SERVICE, FOREGROUND_SERVICE_DATA_SYNC, POST_NOTIFICATIONS and WAKE_LOCK. No Internet permission.

APK SHA-256: `ed9e687e52e275204830e5cfb2bab5197f8f610e10fcbccf65646f71957635af`.

## Executed checks

| Check | Result |
|---|---|
| NaturalStudioPolicyHarness | PASS: sparse/dense limiter policy, flat codec margin, source-relative translation semantics |
| Sparse high-crest render | PASS: input −18.3939; output −14.0001 LUFS, −1.2000 dBTP; max GR 4.7058 dB, average 0.03198 dB, >1 dB 1.0354%, >3 dB 0.2945% |
| Independent FFmpeg ebur128 | −14.0 LUFS integrated, −1.2 dBTP |
| Flat codec attenuation | PASS: −0.22 dB, output −14.2201 LUFS; maximum sample deviation from pure gain 1.77e−7, consistent with 24-bit quantisation/dither; prior limiter metadata retained |
| AdvancedIntelligenceHarness | PASS: all four v0.7 modes eligible; each −14.0006 LUFS / −1.1969 dBTP; source correlations ≥0.99993474 and exact protected premaster regions |
| Optional EOF injection | PASS: section-aware source stream failed; transparent verified winner retained, one recorded integrity warning |
| Truncated source and staged WAV | PASS: both rejected; incomplete destination not published |
| Cancellation during optional candidate | PASS: interrupted job returns no master |
| Isolated clip repair | PASS: three per-channel events, including overlapping L/R spans; only the 12 intended samples changed, every other sample remained exact |
| Dense/rounded clipping controls | PASS: no isolated-declip events accepted |
| ClickDropoutRepairHarness | PASS: one click and one 96-frame stereo dropout, protected boundaries preserved; repaired-source reanalysis and QC schema verified |
| SourceIntegrityHarness | PASS: 16-bit source identity, inherited full-scale sample and long-zero provenance retained; source gap 6.000000–6.125000 s not hidden by dither |
| Measured persistent-tone repair | PASS: three proven tones; measured reduction 0.76214 dB, source correlation 0.99979632, exact protected premaster regions |
| Diagnostic-only API guard | PASS: reference, translation and combined later modes refused audio rendering |
| Synthetic-master second pass | PASS: transparent byte-exact no-render result |
| Real-song second pass | PASS: full 216.44 s Never_Be_Fancy_Remix_AI_Preferred.wav preserved byte-for-byte; −14.0000 LUFS, −3.8260 dBTP |
| FullAiPackageHarness | PASS: five checked ZIP entries, QC/Confidence Map/checksums, 12/0 source-relative translation; missing host AAC honestly CONDITIONAL |
| BackgroundJobHarness | PASS: progress, completion and cancellation state; seven callbacks |

Real-song unchanged SHA-256: `d71c7da171c4c58a9cde5e6d39a59251c1270a2a8d255c0e4a1f5328c871caa5`.

The recovered “Built on a Prayer” v0.9.1 QC was independently inspected: input −10.9929, output −14.0000 LUFS, winner tonal_corrective. Its old report is historical evidence; this run did not create a new master of that song.

## Remaining limits

Phone installation, Android Storage Access Framework saves, playback, live AAC codec behavior, notification cancellation and screen-off operation need device testing. Host job-state checks do not prove Android lifecycle behavior. Objective proxies cannot guarantee perceived vocal, ambience or transient quality on all mixes. Hum tests validate the bounded detector/render path, not universal removal; technical channel candidates remain subject to conservative refusal. Severe/widespread defects remain diagnostic.
