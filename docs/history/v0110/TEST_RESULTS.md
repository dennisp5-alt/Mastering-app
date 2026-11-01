# v0.11.0 validation — 2026-09-29

The full host suite and two complete real-song release runs passed after restoration of the saved checkpoint. The final APK was rebuilt after report/UI wording corrections, and the final package-schema integration passed. No DSP or acceptance threshold changed after the full audio run.

Package: `com.dennis.aimasteringlab`; version 0.11.0/code 15; minimum API 29, target API 34. Original certificate SHA-256: `2d6822f2d6e3785a03bfe61d73d5139822c78c9b2dedbbc64aa0424ad44eff70`. Signature and alignment evidence accompanies the release. APK hash is recorded in `INSTALL_FIRST.txt`.

## Finished-audio cleanup competition

The production engine processed the 42-second professional fixture, comparing the v0.7 result with two rendered cleanup strengths:

| Candidate | Improved target events | Mean target reduction | Outside-event difference | Result |
| --- | ---: | ---: | ---: | --- |
| Gentle cleanup | 3/3 | 0.277 dB | -46.3 dB relative to signal | Eligible |
| Full cleanup | 3/3 | 0.429 dB | -45.1 dB relative to signal | Selected |

The selected full candidate ranked fifth on raw score, while the gentle candidate ranked first. Independent target benefit and preservation therefore determined the winner. The retained comparison was `ai_artifact_repair`; final loudness was -14.004828 LUFS-I and peak -1.194098 dBTP. The reference remains available for comparison. All selected-chain integrity and passage gates passed.

Negative controls passed: steady bright centre audio, side-only high-frequency bursts, flat-gain/no-change candidates, deliberately worsened target energy and cancellation. These tests establish bounded signal behavior, not recognition of a singer or a guarantee that every detected event is an audible flaw.

## Limiter

On the deterministic multi-peak stress test, maximum gain step changed from 2.779726 to 0.020833 dB per output sample. Earlier smaller peaks remain protected when a later larger peak enters lookahead. Stereo linking, frame count, first/tail alignment, short streams and all four profile boundaries passed.

The standalone limiter candidate improved engineering cost by 0.0183 on the professional fixture, below the 0.05 promotion requirement, and was correctly not promoted on that basis. Cleanup candidates used their own verified limiter competition where required. The original sparse fixture still achieved -14.0001 LUFS-I / -1.2000 dBTP with 0.03198 dB average reduction.

## Real music

| Input | Selected strategy | Final LUFS-I | Final dBTP | Independent FFmpeg LUFS / dBTP |
| --- | --- | ---: | ---: | --- |
| LastRunDownTheRange Claude master | Transparent, byte-exact | -14.043861 | -1.093279 | Not rerun in this release |
| T-Bar Girls | Tonal corrective | -14.000000 | -3.069693 | -14.0 / -3.0 |
| The Corner Stage (Remix) | Targeted cleanup | -14.000000 | -3.367810 | -14.0 / -3.4 |

Both original-song masters bypassed limiting. All source frames remained present, no clipped/non-finite output samples were found, final passage checks passed, original input hashes stayed unchanged, and output hashes matched final QC. FFmpeg `ebur128=peak=true` independently checked the two rendered originals. Its displayed peaks are rounded and use its own true-peak estimator.

On The Corner Stage, 11/11 detected target events improved against the transparent comparison, none worsened, mean target-band reduction was 0.165 dB and outside-event difference was -85.3 dB relative to signal. The gentle alternative also passed with 0.102 dB reduction. T-Bar Girls retained its established tonal result because no later candidate justified replacement. These are measured comparisons, not listening verdicts.

The Claude master retained SHA-256 `f78a0f49a215f07a00c0d0ff524a0fc9e6f6af731f716046fb9486ea0834676d`, including all 12,947,208 frames. Four inherited mono warnings and one ending finding remain. The separate flat-gain boundary regression passed at -14.000000 LUFS-I / -1.04941743 dBTP, without limiting.

## Retained regression coverage

The suite completed with `V0110_HOST_GATE_PASS` and the overall real-song run with `COMPLETE_V0110_RELEASE_GATE_PASS`:

- Transparent/compliant source boundaries, no-change remastering and flat codec attenuation.
- Local centre/width/transient/tail damage detection, gain-invariant passage comparison, aligned A/B, seeking, mono, blind mapping and cancellation.
- Gentler core rendering and promotion safeguards; selected-winner codec order.
- Isolated and overlapping per-channel declipping, dense clipping and rounded-peak negative controls.
- Optional candidate EOF recovery; damaged source/staging rejection.
- Click/dropout repair, inherited source defects, preserved zero regions and original provenance.
- All four retained v0.7 advanced render modes; measured hum repair and diagnostic-only renderer guards.
- Release ZIP contents, QC schema and background job callbacks.

The final editorial cleanup corrected stale v0.7-authority descriptions in the UI, controller, QC JSON, Confidence Map and radio report. After those string/schema edits, all sources compiled again and `FullAiPackageHarness` verified the final report and ZIP. The complete real-song evidence is preserved unedited from before that editorial cleanup; its old policy label is superseded by the final package fixture and current code.

## Limits

No phone/emulator installation, native AudioTrack playback, Android MediaCodec encoding, screen-off Android lifecycle or human listening comparison was performed. Host release verdicts correctly remain CONDITIONAL because Android AAC is unavailable. A small flat level reduction may be needed for codec safety on the phone. No universal broadcast or studio-quality certification is implied.
