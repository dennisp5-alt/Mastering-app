# Dennis AI Mastering Lab v0.13.0 — completed recovery record

Status: finished bulk-test build. All seven additions are implemented. APK built and signed; complete source and release files prepared. Do not restart the old v0.10/v0.12 recovery work.

Package: com.dennis.aimasteringlab
Version: 0.13.0, versionCode 17
Android: min29 / target34, no Internet permission
APK: DennisAiMasteringLab-v0.13.0.apk
APK SHA256: aac7d96e0dfb680d296902b34585143784b31874b984f3823bb4ad67c2dc418f
Signing certificate SHA256: 2d6822f2d6e3785a03bfe61d73d5139822c78c9b2dedbbc64aa0424ad44eff70

Final source archive: DennisAiMasteringLab-Source-v0.13.0.zip
Previous authoritative source: DennisAiMasteringLab-Source-v0.12.0.zip
Previous source SHA256: f033cdf0c2a71b8ca93b4adeed1b7976d92d12d974c72b8be2bded74167aa18f

Development checkpoints saved:
1. libfile_3defc5c40a448191ac348070d7ae8fab — recovered v0.12 and agreed scope.
2. libfile_1c977c980f1881919ae784ea415817d6 — first integrated build of all seven additions.
3. libfile_5960c60fcaf081918660823b7bd8bf6f — core/regression gates and implementation fixes.
Prefer the completed v0.13 source archive above for future work.

## Implementation
StudioActivity is the launcher. AiLabActivity remains the classic mastering/stems path.
StudioProject and StudioRecipe own persisted files/state/undo. StudioJobs owns the Android worker; StudioQueue is its platform-independent tested queue logic.
StudioAudio renders smooth local gain/EQ, trims/fades and guarded delivery; StudioLowEnd proposes and verifies limited automatic centre-bass corrections.
StudioReferences supplies independently positioned matched-passage comparisons and disabled optional broad EQ suggestions.
StudioPipeline runs the existing mastering engine, persists the verified base, applies saved finishing, creates measured formats/QC/ZIP and publishes project references only after completion.
StudioAlbum compares actual masters and builds ordered, spaced continuous PCM sequences.
StudioMetadata writes WAV INFO. Existing OfflineMp3/ReleaseFormats now support optional artist/album/track/ISRC and custom derivative ceilings. Existing ComparisonPlayer/ComparisonAudioStream support loops/delta.

## Validation
Twelve host harness invocations passed; RESULTS.json and HOST_GATE.log are in docs/validation-v0130. Covers null/bypass, protected samples, exact trim, boundary fades, centre/side preservation, bass true/false positives, candidate benefit rejection, saved-state recovery/corruption, queue pause/resume/failure isolation, references, metadata, delivery, packages and album rules, plus inherited DSP/export/recovery regressions.
Real Pilot Light2: 9,696,000 frames / 202.000s, −14.000000 LUFS-I, −1.719918 dBTP. No new bass correction. Conditional end fade 10ms. Base checkpoint reused after disk reload. Both derivatives independently verified by FFmpeg: 8,908,200 frames, about −14.01 LUFS / −1.73 dBTP.
Final later changes are documented in validation README; final APK compilation and signature/manifest checks passed.

No physical phone/emulator or human listening test was performed. Native UI/playback and Android background/pause behavior are user bulk-test items. This is not a claim of perceived studio certification. Per-app saved checkpoints resume verified stages, not an arbitrary sample in an interrupted render.

## Rebuild
Keep test-signing.jks private. It is required for compatible updates. All third-party LGPL codec source/license is retained, unmodified.
Install Java17, Python/NumPy, Android SDK platform34 and build-tools34.0.0.
Google archive SHA1s: platform-34-ext12_r01.zip ba80ccbcc29b29f25ac926a08c0b2777f0bce842; build-tools_r34-linux.zip d6d58e0c6925a9e4d9a541e84cd1f405c2f9d2a9.
Set DAML_ANDROID_SDK; run tools/build_apk.sh and tools/run_v0130_tests.py [optional real WAV].
See README.md and RELEASE_NOTES_v0.13.0.md for current behavior and limits. All v0.7 permanent-authority instructions were superseded by the user's explicit permission to prioritize quality; transparent rendering and conservative verification remain.
