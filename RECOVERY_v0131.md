# v0.13.1 — complete

Authoritative current source: DennisAiMasteringLab-Source-v0.13.1.zip.
Installable APK: DennisAiMasteringLab-v0.13.1.apk; version code 18; package com.dennis.aimasteringlab; same retained update certificate. Install over the current app without clearing data, then use RETRY FAILED / INCOMPLETE EXPORTS.

APK SHA256: 396faff6f9894c3c7c32b7df87c05e4c72e55bb15da02e595cac8bb575428821
Certificate SHA256: 2d6822f2d6e3785a03bfe61d73d5139822c78c9b2dedbbc64aa0424ad44eff70

## Fault and repair
Screenshot 40086.png showed four v0.13.0 source failures. A reproducible Android-style filesystem-alias bug computed relative paths from an aliased project root to a canonical file. The initial import failed before saving its source pointer/hash. Retry then reported a missing/changed source despite a completed local source.wav.

StudioProject now canonicalizes both paths. StudioImports is shared by the Android picker and tests, verifies the incoming stream against its saved copy, persists identity before measurement, and guards recovery. Completed early legacy imports without initial metadata can be re-read and given their first checksum; the project and QC disclose this limitation. Existing checksums are never silently replaced after mismatch. Partial/missing/truncated or untrusted post-render files fail with specific messages.

StudioActivity, StudioQueue and StudioPipeline use the corrected paths/import verification. All seven v0.13 features are retained. No DSP algorithm or mastering target was changed.

## Validation
Three final host harness invocations passed. Before-fix reproduction, after-fix regression, failed-project recovery, negative integrity/cancellation/path tests, complete real Pilot Light queue/master/WAV24/WAV16/MP3/QC/ZIP/reload/resume and inherited Studio integration all passed. See docs/validation-v0131/README.md, RESULTS.json and logs. Real song: 202.000 s, -14.00 LUFS-I, -1.72 dBTP.

The first short-fixture integration run hit the existing section mapper minimum length; the corrected integration used the real full-length song. No physical-phone/emulator or listening test was performed. The user's actual failed project contents are not accessible here. Recovery requires a complete retained source copy.

## Checkpoint and continuation
Checkpoint 1 was saved before final tests: DennisAiMasteringLab-Source-v0.13.1-CHECKPOINT-01.zip, Library ID libfile_f6c1649972f0819199796b8e1d607f56, SHA256 03eb7e1e3f87ba65341cc554e4b4c6d6b2cef2c1586d67e3abc7ca63d7bf53f2. This completed archive supersedes it and the previous v0.13.0 recovery record.

No remaining implementation step for this fix. Next is Dennis's on-device Retry failed test. Retain the update signing key privately. If re-building, use Java17, Android platform34/build-tools34, DAML_ANDROID_SDK, tools/build_apk.sh and tools/run_v0131_tests.py /path/to/a/full-length/48kHz-stereo.wav. tools/checkpoint.py creates the complete source archive with per-file hashes.
