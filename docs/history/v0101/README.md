# Dennis AI Mastering Lab 0.10.1

Private offline Android mastering with v0.7 as the sonic baseline, measured technical repair, guarded peak limiting and final radio/release preparation checks.

## Installation and use

- Package com.dennis.aimasteringlab; version 0.10.1, code 13.
- Android 10 or later; compile/target API 34. No Internet permission or online mastering service.
- Original signing certificate SHA-256: 2d6822f2d6e3785a03bfe61d73d5139822c78c9b2dedbbc64aa0424ad44eff70.
- Install over AI Mastering Lab v0.9.x or v0.10.0 without uninstalling. Other Dennis apps are separate.
- Select a 48 kHz stereo WAV and choose Create Full AI Master. Leave Preference Memory and Album Consistency off for neutral operation.
- Output: 24-bit/48 kHz WAV, QC JSON, Confidence Map, Radio Preparation report and optional device AAC. The release ZIP includes reports and checksums.

## v0.10.1

The reported failure was reproduced on the exact LastRunDownTheRange master. The analyzer approved a flat gain predicting -1.0494 dBTP, while the renderer incorrectly required -1.1 dBTP. The renderer now respects the requested final ceiling and independently verifies the result. Unsafe gain and stricter ceiling requests still fail.

A verified 24-bit/48 kHz stereo source within 0.10 LU of -14 and at or below -1 dBTP can pass unchanged. Measurements, sample count, SHA-256, zero gain and zero render passes must support preservation. COMPLIANT_SOURCE_PRESERVED is distinct from exact-target status. Proven technical repairs or actual AAC headroom needs can still justify a changed result.

Radio preparation scans the complete final song for active anticorrelated mono-loss sections, nonzero opening/ending boundaries and a brief late transient rising above the preceding tail. Findings include timestamps and source-relative provenance. Uncorrelated stereo width alone is not a phase defect. These diagnostics ask for audition; they do not automatically fade, narrow, EQ or compress the track, or certify a station's delivery requirements.

## Retained audio policy

The complete guarded v0.7 result remains the natural reference. Later depth, reverb, vocal shaping, reference matching and Translation Consensus stay diagnostic. Narrow technical candidates need measured benefit and preservation checks. Device simulations only assess the output.

Sparse limiting allows up to 5.5 dB maximum reduction only with average reduction at most 0.9 dB, at most 20% of frames above 1 dB and at most 5% above 3 dB. Sustained excess backs off gain and reports a dynamic-master shortfall. Actual AAC overshoot can trigger flat attenuation without another limiter.

Bounded click, short-dropout, isolated per-channel clipping and stable hum/whine repairs remain. Repaired float sources are re-metered, and original provenance stays in QC. Fixed catalogue EQ, compulsory filtering, LRA-only compression and repeated decimal-target limiting were not imported from the Claude recipe.

## Build and test

Keep test-signing.jks private; compatible updates require it. The build refuses to invent a replacement key.

Set DAML_ANDROID_SDK to an SDK containing Android 34/build-tools 34.0.0, then run:
    bash tools/build_apk.sh
    python3 tools/run_v0101_tests.py

Optional exact real-file reproduction:
    python3 tools/run_v0101_tests.py /path/to/LastRunDownTheRange_Master_24bit-48kHz.wav

Use Java 17, Python 3 and NumPy. Host tests compile production sources with local JSON test doubles; the APK uses Android JSON. Host codec unavailability remains CONDITIONAL. Avoid concurrent writes to the test work directory.

See RELEASE_NOTES_v0.10.1.md, TEST_RESULTS.md, SOURCE_PROVENANCE.md and docs/validation-v0101/. Historical records remain under docs/history. Phone installation, Android AAC, playback and screen-off operation still need device validation; the host tests do not substitute for listening.
