# v0.10.2 source provenance and continuation

Authorized by Dennis: improve mastering judgment and listening review without materially changing the natural v0.7 character. Continued after the work limit; completed 2026-09-26.

Base: `DennisAiMasteringLab-Source-v0.10.1.zip`, SHA-256 `a88ad697a1f2d171031e4e73cf6a66ffd981ffb3ccc1699e04f1fd90a1ccf34c`. The complete project was extracted before edits. Earlier source/signing recovery records remain in `docs/history/v0101/`, `docs/history/v010/` and `docs/history/v091/`.

Added production classes: `PassagePreservation`, `ComparisonAudioStream`, `ComparisonPlayer`. Modified competition, release QC, report/UI integration and WAV reader seeking. The final stage renders one 75%-strength challenger only for optional core plans, requires equivalent measured benefit and preserves local and whole-song dynamics. Final codec ordering honors the selected candidate. Existing DSP repair and v0.7 advanced algorithms are retained.

New harnesses: `PassageAndComparisonHarness` and `GentlerRenderHarness`. Runner: `tools/run_v0102_tests.py`. Evidence and measured limits are in `TEST_RESULTS.md` and `docs/validation-v0102/`. The already-mastered supplied WAV remains byte-exact on the host. This backup contains no source song, generated audio, SDK, build cache or APK.

Version 0.10.2/code 14, same application ID and original private update signing key. Certificate SHA-256: `2d6822f2d6e3785a03bfe61d73d5139822c78c9b2dedbbc64aa0424ad44eff70`. The included `test-signing.jks` must remain private for future compatible builds.

`SOURCE_CHECKSUMS.sha256` inventories all source and validation files, excluding generated builds, APKs, source ZIPs, caches and the checksum inventory itself. Reproduction of the real-song regression needs Dennis's original WAV separately.

Status: production APK built and verified, full host suite plus final focused tests passed. Handset installation, native comparison playback, AAC and screen-off mastering remain device checks. Do not infer auditory/studio certification from engineering tests.
