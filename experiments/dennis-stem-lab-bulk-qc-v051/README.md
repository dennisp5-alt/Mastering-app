# Dennis Stem Lab Modern v0.5.1 bulk A/B experiment

This isolated GitHub branch builds a **separate Android trial application** from the exact SHA-256 pinned v0.15.7 engine source, the known-good v0.5.0 stem integration, and the optional v0.5.1 source patch.

- Ordinary full WAV imports and 2-/4-stem ZIP premixes use the original mastering engine.
- The new screen creates two separate low-impact post-master dynamic-EQ experiments from each completed and SHA-verified source project, without overwriting its master.
- Bulk QC CSV distinguishes FULL_WAV from STEM_PREMIX and gives measured loudness, true peak, LRA, crest factor, correlation, hashes and deltas.
- The independent package ID **com.dennis.stemlab.bulkqc** and launcher label **Dennis Stem Lab A-B DEV** let the test app coexist with the existing v0.5.0 and trusted v0.15.7 apps. Existing v0.5.0 internal projects are intentionally not migrated; import WAVs and stems into the trial app.
- Two trial versions are **experimental listening candidates**, not asserted enhancements. Listen level matched and reject any degradation.
- The Android APK must pass GitHub Actions before it is considered build-ready. No device listening claims are made.

Every code change is isolated to the test branch. The original engine source is preserved by the pinned checksum and unchanged-core Java SHA validation in the build workflow.
