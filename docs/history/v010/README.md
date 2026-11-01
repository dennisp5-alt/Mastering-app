# Dennis AI Mastering Lab 0.10.0

Private offline Android mastering with v0.7 as the sonic baseline, measured technical repair and activity-bounded release limiting. The source continues from the recovered v0.9.1 project; the interrupted chat did not deliver a v0.10 APK.

## Identity and installation

- Package: `com.dennis.aimasteringlab`; version `0.10.0`, code `12`.
- Minimum API 29; compile/target API 34. Java DSP, no network service or Internet permission.
- Original signing certificate SHA-256: `2d6822f2d6e3785a03bfe61d73d5139822c78c9b2dedbbc64aa0424ad44eff70`.
- Install the APK over a compatible v0.9.0/v0.9.1 build, without uninstalling.
- Source is a 48 kHz stereo WAV. Output is 24-bit/48 kHz stereo WAV plus QC, Confidence Map and optional device-encoded AAC.

## Processing policy

Transparent/no-change first. The complete guarded v0.7 core/stereo/advanced selection remains the natural reference. Later depth, reverb, vocal shaping, reference matching and Translation Consensus cannot enter the audio. Stable channel level imbalance is diagnostic because it may be intentional panning. Narrow proven tone and technical channel repairs must still clear individual render, technical-score and natural-preservation gates. No later combined candidate is generated.

The existing limiter envelope stays unchanged. Standard peak reduction is capped by the track allowance, at most 3 dB. Extension up to 5.5 dB requires average reduction ≤0.9 dB, ≤20% of frames above 1 dB and ≤5% above 3 dB. Otherwise the app backs off gain and reports a dynamic-master shortfall. Actual AAC overshoot can trigger flat attenuation; no second limiter is added.

Continuity repair covers high-confidence isolated impulses, stereo exact-zero holes up to 20 ms, and sparse per-channel clip plateaux of 2–32 frames. Intro/tail protection, source correlation, precise channel masks and sparse coverage limits apply. Rounded or dense clipping is not reconstructed. Verified repaired float sources are re-metered before mastering while original hashes, clipping and inherited-defect evidence are retained.

## Reliability and honest reporting

Each candidate has a separate staging directory. Advanced float intermediates and final PCM renders are synchronised and verified before publication. A failed optional candidate is excluded and reported; a verified baseline survives. A failed source, mandatory baseline or final copy still aborts. Cancellation propagates through render and codec stages.

Source-relative device simulations distinguish inherited weaknesses from processing-induced changes. Lower zero/clip counts after quantisation do not prove defect repair. Already-compliant mastered audio uses a verified byte-exact copy path when no technical repair is justified.

The existing foreground service, notifications, wake lock, local profiles and guarded optional-stem paths remain. Partial jobs cannot resume after process death or reboot. Phone installation, Android codec operation and listening remain device checks.

## Build and test

Keep `test-signing.jks` private. It is required for compatible updates; the build refuses to invent a replacement key.

```bash
export DAML_ANDROID_SDK=/path/to/android-sdk
bash tools/build_apk.sh
python3 tools/run_v010_tests.py
```

Use Java 17, Android platform 34/build-tools 34.0.0, Python 3 and NumPy for the host fixture generator. The host tests compile against local JSON test doubles; production APKs use Android's JSON implementation. Host codec unavailability must produce a conditional result.

See `RELEASE_NOTES_v0.10.0.md`, `TEST_RESULTS.md`, `SOURCE_PROVENANCE.md` and `docs/validation-v010/`. Older validation claims are retained under `docs/history/v091/`; they are historical, not new device-test claims.
