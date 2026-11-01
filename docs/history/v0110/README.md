# Dennis AI Mastering Lab 0.11.0

Offline Android mastering with measured candidate comparisons, targeted cleanup, guarded limiting and release preparation. The v0.7 result remains available for comparison; it no longer has permanent sonic priority.

## Install and use

- Package `com.dennis.aimasteringlab`, version 0.11.0, code 15. Android 10 or later; target API 34; no Internet permission.
- Install the APK over the existing AI Mastering Lab. The original signing certificate is retained. Other Dennis apps are separate.
- Select a 48 kHz stereo WAV, leave Preference Memory and Album Consistency off for neutral operation, and choose **Create Full AI Master**.
- Compare the result using the aligned, loudness-matched A/B player. Mono listening, seeking, blind labels and radio-warning timestamps remain available.
- Outputs remain 24-bit/48 kHz WAV, QC JSON, Confidence Map, Radio Preparation report, checksums and optional Android AAC in a release ZIP. This update does not add MP3, 16-bit/44.1 kHz export, lyric censorship or arrangement shortening.

## Quality-first decisions

Transparent/no-change is rendered and verified first. The established guarded competition supplies a comparison result. New cleanup and limiter candidates may replace it when independent analysis of the finished audio shows a measured benefit and preservation checks pass. A lower raw tonal score cannot, by itself, veto that promotion.

Targeted cleanup uses high-confidence, short centre-channel sibilance, plosive and harshness proxies. It renders gentle and full-strength alternatives, then independently measures target-to-body energy changes at the detected events and waveform differences outside them. Controls reject unchanged audio, uniform gain and worsened target events. These are signal proxies, not voice isolation or proof of artistic intent.

A new ramped-attack limiter joins the three established profiles. When limiting is needed, actual rendered profiles compete on measured gain reduction, gain steps, crest/LRA preservation, loudness and peak safety. The engineering cost is not a perceptual listening score. The app does not add limiting when flat gain can meet the target safely.

Every promotion still requires source integrity, complete frames, finite audio, peak safety and passage preservation. Local centre presence, transients, stereo width, section energy and endings are checked. Later technical repairs cannot undo a previously verified targeted cleanup benefit.

## Retained safeguards

Targets are approximately -14 LUFS-I and at most -1 dBTP. A verified compliant 24-bit/48 kHz master within 0.10 LU of target can pass byte-for-byte unchanged. Sparse peak limiting, measured click/pop and short-dropout repairs, isolated per-channel declipping and narrow hum/whine repair retain their safeguards. Already-mastered source detection tightens later intervention.

Broad depth/reverb shaping, uncertain phase changes, catalogue/reference matching and device simulations remain diagnostic. No compulsory high-pass, fade, LRA-triggered compression or fixed catalogue EQ was introduced. Actual Android AAC overshoot is handled with flat attenuation, without another limiter pass.

Radio preparation checks source-relative mono loss and opening/ending events. It prompts listening and does not certify a station's specific delivery requirements.

## Build and test

Keep `test-signing.jks` private. The original key is required for compatible updates; the build refuses a different certificate.

Set `DAML_ANDROID_SDK` to an Android SDK containing platform 34 and build-tools 34.0.0. With Java 17, Python 3 and NumPy:

```sh
bash tools/build_apk.sh
python3 tools/run_v0110_tests.py /path/to/LastRunDownTheRange_Master_24bit-48kHz.wav
```

Omit the WAV argument for fixture-only tests. The runner compiles production code and all harnesses together with host JSON test doubles; the APK uses Android JSON. `RealMusicReleaseHarness` accepts an input WAV, empty work directory and title to exercise the no-stems/no-reference phone release path. Its classes are in the runner's printed build directory; include the Android platform JAR in the Java classpath. Real source songs are not included in this archive.

See `TEST_RESULTS.md`, `SOURCE_PROVENANCE.md`, `RELEASE_NOTES_v0.11.0.md` and `docs/validation-v0110/`. Older records are historical. Installation, native playback, Android AAC and screen-off operation still require a phone check. Host measurements do not certify human-perceived studio quality.
