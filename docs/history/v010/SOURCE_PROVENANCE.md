# v0.10.0 source provenance and continuation

This build was completed on 2026-09-15 from recovered, verified source archives:

- DennisAiMasteringLab-Source-v0.9.1.zip: integration base, app lifecycle, guarded v0.7 reference, private update identity.
- DennisAiMasteringLab-Source-v0.7.0.zip: historical sonic-reference source.
- DennisStemLab-Source-v0.4.0.zip: activity-measured sparse limiter policy, flat AAC headroom policy, source-relative translation accounting and bounded per-channel clip-run approach.

The earlier “Dennis Mastering Workflow” session stalled before releasing v0.10. No recovered v0.10 APK or finished source was assumed. v0.10 changes in this archive were implemented and verified during the recovery continuation. A separate source checkpoint was saved before final testing.

Main changes: FullAiMasteringEngine, MasterRenderer, LookaheadLimiter, AudioAnalyzer, AdvancedStereoRenderer, ClickDropoutRepair, IsolatedClipDetector, TechnicalSourcePreparation, NextGenerationMasteringRenderer/Model, ChannelIntegrityModel, TranslationAnalyzer/Composite, FullAiReleaseQc, FullAiReportWriter, AiLabActivity and codec cancellation checks. Later diagnostic models remain in the project. Regression fixtures and harnesses are included under tests/.

The private signing keystore is included only for Dennis's compatible private updates. Keep it private. Expected certificate: `2d6822f2d6e3785a03bfe61d73d5139822c78c9b2dedbbc64aa0424ad44eff70`.

SOURCE_CHECKSUMS.sha256 inventories the source handoff excluding generated build outputs, APKs, source ZIPs, incidental caches and itself. Build and validation logs live in docs/validation-v010/. The Android SDK, input song and test output WAVs are reproducible inputs/outputs and are excluded from this small source archive.

Current task status: source policy implemented; signed APK built; host checks passed. The next task is Dennis's phone installation and listening test. Do not repeat recovery or revive disabled later shaping automatically. If a phone run fails, inspect its QC, exact error, input format and selected strategy, then patch this v0.10 base with the same signing key.
