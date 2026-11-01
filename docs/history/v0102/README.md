# Dennis AI Mastering Lab 0.10.2

Offline Android mastering with the natural v0.7 sonic baseline, measured technical repairs, guarded peak limiting and final release preparation.

## Installation and use

- Package `com.dennis.aimasteringlab`; version 0.10.2, code 14.
- Android 10 or later; compile/target API 34; no Internet permission.
- Install over the existing AI Mastering Lab without uninstalling. The original update certificate is retained. Other Dennis apps are separate.
- Select a 48 kHz stereo WAV and choose **Create Full AI Master**. Leave Preference Memory and Album Consistency off for neutral operation.
- Outputs: 24-bit/48 kHz WAV, QC JSON, Confidence Map, Radio Preparation report, checksums and optional Android AAC, packaged in a release ZIP. MP3 and 16-bit/44.1 kHz export are not part of this release.

## v0.10.2 improvements

**Passage preservation:** Each candidate is checked against the verified transparent render using one-second windows and a single whole-file loudness match. The checks look for sustained loss of centre presence, stereo width or section energy, reduced transient crest, and diminished ending tails. The final released WAV is checked again after any codec safety attenuation. Results and rejection timestamps are recorded in QC JSON. These are conservative signal measurements, not isolated-vocal analysis or proof of perceptual quality.

**One gentler challenger:** After a core correction wins, render one version at 75% of its optional EQ, local-event, compression and stereo-width adjustment amounts. Select it only when measured benefit remains within 0.025 score points, processing cost is lower, limiter burden does not increase, and local and whole-song preservation gates pass. Targeted repair maps, sparse limiting safety, v0.7 advanced renderers and protected/no-change masters are not weakened. If the challenger fails, retain the verified original winner. Release QC checks the selected winner before raw-score alternatives.

**Aligned A/B listening:** One sample timeline and Android output queue play both verified files. Switching uses a 5 ms transition without restarting the song. Integrated-loudness matching attenuates the louder file. Add pause, exact shared seeking, mono listening and optional randomized blind A/B labels; preference votes map to the actual version. Radio warning buttons start playback two seconds before the marked event. Preview gain and mono do not alter exports.

## Retained sound and repair policy

The complete guarded v0.7 decision path remains the sonic reference. Later depth/reverb/vocal shaping, reference matching and Translation Consensus remain diagnostic. Device simulations assess final translation only. Corrections need measured evidence and preservation checks; no fixed catalogue EQ, compulsory high-pass/fade or LRA-only compression has been added.

Targets remain approximately -14 LUFS-I and at most -1 dBTP. A verified compliant 24-bit/48 kHz master within 0.10 LU of the target can pass byte-for-byte unchanged. Safe flat gain respects the actual requested ceiling. Sparse limiting and measured click/pop, short-dropout, isolated per-channel clipping and hum/whine repairs retain their existing limits. Actual Android AAC overshoot may require flat attenuation, not an added limiter.

Radio preparation reports mono-loss passages, opening/ending boundaries and unusual late transients with input-relative provenance. These remain audition requests rather than automatic edits or universal broadcast certification.

## Build and test

Keep `test-signing.jks` private. Compatible updates require this original key; the build refuses to substitute a new identity.

Set `DAML_ANDROID_SDK` to an Android 34 SDK with build-tools 34.0.0. With Java 17, Python 3 and NumPy:

```sh
bash tools/build_apk.sh
python3 tools/run_v0102_tests.py
# Optional real-file preservation regression:
python3 tools/run_v0102_tests.py /path/to/LastRunDownTheRange_Master_24bit-48kHz.wav
```

The runner compiles all production sources and harnesses together using local JSON test doubles; the APK uses Android JSON. Host codec unavailability stays CONDITIONAL. Avoid concurrent writes to test fixtures.

See `RELEASE_NOTES_v0.10.2.md`, `TEST_RESULTS.md`, `SOURCE_PROVENANCE.md` and `docs/validation-v0102/`. Prior release records remain under `docs/history/`. Native phone playback, installation, Android AAC and screen-off mastering require handset checks; host DSP tests do not certify listening quality.
