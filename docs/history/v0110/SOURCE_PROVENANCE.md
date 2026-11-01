# v0.11.0 source provenance and continuation

Dennis authorized quality-first mastering improvements and explicitly allowed departure from the v0.7 character when necessary for cleaner release audio. This release implements independent finished-audio cleanup comparison, bounded targeted correction and smoother limiter competition. Completed 2026-09-29 after a work-limit interruption.

Base: `DennisAiMasteringLab-Source-v0.10.2.zip`, SHA-256 `b87f4826747110accd0cecd2f1707eac1bb7f4cf53b5b4a298edd49b5c688bb8`.

Recovery: `DennisAiMasteringLab-Source-v0.11.0-CHECKPOINT.zip`, 597,503 bytes, SHA-256 `3295e0a0e3bed1d340dc75b0873461ada0b8c41dab2e3bfe241ff4d9d3271953`. The checkpoint was restored and hash/ZIP-integrity verified after the temporary workspace was cleared. Its status note remains under `docs/history/v0110-checkpoint-status.md`; it describes the earlier unfinished state, not this completed release.

New production class: `TargetedQualityEvidence`. Main changes: `ProfessionalMasteringModel`, `ProfessionalMasteringRenderer`, `FullAiMasteringEngine`, `LookaheadLimiter`, `MasterRenderer`, `LimiterCrestOptimizer`, report writers and UI. A measured cleanup/limiter improvement can replace the v0.7 comparison; broad uncertain shaping remains diagnostic. Event confidence, bounded coverage, outside-event controls, source integrity and passage/global preservation remain required. Later technical candidates cannot undo accepted targeted benefit.

New harnesses: `QualityFirstHarness`, `QualityCounterexampleHarness`, `RealMusicReleaseHarness`. Current fixture/real-master runner: `tools/run_v0110_tests.py`. Full validation and final report-only rebuild evidence are in `docs/validation-v0110/`. See `TEST_RESULTS.md` for actual results and limitations.

Version 0.11.0/code 15 retains the original application ID and signing key. Certificate SHA-256: `2d6822f2d6e3785a03bfe61d73d5139822c78c9b2dedbbc64aa0424ad44eff70`. Keep the included `test-signing.jks` private. No external service is required for mastering.

This backup includes source, tests, docs, validation evidence and the private update key. It excludes the Android SDK, compiled classes, build caches, APKs, ZIP intermediates and source/rendered music. `SOURCE_CHECKSUMS.sha256` inventories the included files, excluding itself. The separately delivered APK and notes have release-level checksums.

Reproduction of real music checks requires Dennis's exact source WAVs. Input/output hashes, frame counts, selected candidates and independent FFmpeg readings are preserved in `docs/validation-v0110/release-validation.json`. No native phone or perceptual studio certification was performed.
