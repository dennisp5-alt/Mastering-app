# v0.10.1 source provenance and continuation

Completed for Dennis's request to resume the APK and add radio/release preparation. Base: released DennisAiMasteringLab-Source-v0.10.0.zip, SHA-256 f722f30c8c2ef44ccb146776fa1e4b5a0d5db5c12f94e400a26724178b1be8cd. Earlier v0.9.1/v0.7 and Stem Lab recovery records remain in docs/history/v010/SOURCE_PROVENANCE.md.

The failure was reproduced using the exact uploaded Claude-mastered WAV. The gain/ceiling mismatch and narrow no-change tolerance were fixed. Radio preparation adds full-song mono and boundary/late-transient diagnostics with timestamps, input provenance and report/ZIP integration. It does not force the pasted catalogue EQ curve onto the audio.

Main changes: AudioAnalyzer, AnalysisResult, SourceGuardModel, MasterRenderer, FullAiMasteringEngine, FullAiReleaseQc, FullAiReportWriter, FullAiPackageBuilder, ReleasePackageBuilder, ReportWriter, AiLabActivity and new RadioPreparation. MasterConfidenceMap and build configuration carry the new version. LookaheadLimiter DSP and v0.7 shaping algorithms are unchanged in this patch.

New regression harnesses: CompliantMasterRecoveryHarness, CompliantMasterPolicyHarness, RadioPreparationHarness. AdvancedIntelligenceHarness now prints rejection causes. Runner: tools/run_v0101_tests.py. Evidence: TEST_RESULTS.md and docs/validation-v0101/.

Private original update identity is retained. Certificate SHA-256: 2d6822f2d6e3785a03bfe61d73d5139822c78c9b2dedbbc64aa0424ad44eff70. The included test-signing.jks is for compatible private updates. Keep the source backup private.

SOURCE_CHECKSUMS.sha256 inventories the source and validation files, excluding generated builds, APKs, source ZIPs, caches and itself. SDK, source song and generated audio are excluded; exact reproduction needs Dennis's original input separately.

Status: changes implemented, signed APK built, supplied song processed and focused/retained host checks passed. Next useful validation is Dennis's phone installation, listening, AAC and background run. Preserve the app identity and v0.7 policy for future updates.
