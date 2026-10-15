# Four-stem baseline compatibility test — 15 October 2026

**Scope:** Source-level test against the recovered, already-built Dennis Stem Lab Modern v0.5.0 source project. **No Android APK was rebuilt, no mastering DSP changed and no 4-stem listening claim is made.**

## Purpose

Verify that existing `StemInventory`, `StemPremixBuilder` and `WavFile` safely handle 4 aligned stereo stems with the same nominal mix as 2 aligned stereo stems.

## Test method

Create 12-second, 48,000-Hz, 24-bit PCM stereo synthetic music-like WAV sources, with audio components quantized to signed 24-bit integers. Four individual stems: `Vocals.wav`, `Drums.wav`, `Bass.wav`, `Other.wav`. Two comparison stems: `Vocals.wav` and `Instrumental.wav`, with the instrumental's integer samples defined as the exact sum of drums, bass and other. Both ZIP archives contain the same musical content and matched frame counts.

Compile the **unmodified production source files** `WavFile.java`, `LittleEndian.java`, `StemInventory.java` and `StemPremixBuilder.java` with minimal host test stubs for `AudioAnalyzer` and `AnalysisResult`. Run `StemPremixBuilder.build()` on each archive. Compare stem counts, frame counts, role classification, premix file format, shared gain, and **full SHA-256** of each 32-bit float premix.

## Results

- **PASS:** Four WAVs accepted, with roles `[vocal, drums, bass, other]`.
- **PASS:** Two WAVs accepted, with roles `[vocal, other]`.
- **PASS:** Both premixes have 576,000 frames, stereo, 48 kHz, IEEE 32-bit float PCM.
- **PASS:** Shared gain on both fixtures was exactly 0 dB (no overload), with no per-stem normalization.
- **PASS:** Both premix files are **byte-for-byte identical**: SHA-256 `a2b7068d96a35e0d17ead246c3106ba735492c45be6dd6f023d4f22f1adc2a06`.
- **PASS:** Maximum absolute sample delta = 0; RMS delta = 0.

**Interpretation:** With genuinely identical, aligned input audio, changing from two to four stems introduces no intrinsic premix improvement or damage. Real-world benefits depend on separator quality and any separately justified stem-aware intervention.

## Limitations and next real-world gate

- This was not an Android-device test and did not invoke the full mastering engine.
- Current premix code accepts differently sized stems and zero-fills missing tails, so real separation output must be audited for timeline alignment, equal frame counts, silent gaps and leading offsets. Identical frame counts alone do not prove sample alignment.
- v0.5.0 uses conservative repair plus unity stem summing and shared headroom; it does **not** apply specialised independent auto-mixing to drums/bass.
- For a valid A/B comparison, separate **the same song** into two-stem and four-stem files using the selected separation models. Master both without changing settings and compare loudness-matched files and `Stem_Import_QC.json`, `Studio_QC.json` and actual output WAVs.
- Do **not** conclude that four separated stems are better simply because more categories were returned.
- No changes to Dennis AI Mastering Lab v0.15.7, to the separate stem extractor or to Dennis Stem Lab Modern v0.5.0 mastering DSP.
