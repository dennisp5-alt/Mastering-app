# v0.14.0 Sonic Finish recovery checkpoint

Base: `DennisAiMasteringLab-Source-v0.13.1.zip`.
Package: `com.dennis.aimasteringlab`.
Version code/name: 19 / 0.14.0.
Signing identity: unchanged; `test-signing.jks` retained. Expected certificate SHA-256 remains `2d6822f2d6e3785a03bfe61d73d5139822c78c9b2dedbbc64aa0424ad44eff70`.

Implemented: compliance/mastering separation, source-history-only second-pass block, stronger Sonic Finish core candidate, broader conditional bus compression, larger bounded dynamic-EQ headroom, lower processing bias in competition, revised promotion gates, wider non-destructive musical margins, and removal of gentler-by-default final preference.

Validation completed in this session: source diff reviewed against v0.13.1; all non-Android production Java files were passed through `javac` parsing with expanded diagnostics and no parse/syntax diagnostic was emitted for the changed Sonic Finish classes. Full Android compilation and the host gate remain pending solely because Android platform 34/build-tools 34.0.0 are unavailable in the current execution container. Do not represent this checkpoint as a signed APK until `tools/build_apk.sh` and the host regression gate complete.

Resume procedure: materialize this checkpoint, run `DAML_ANDROID_SDK=/path/to/sdk bash tools/build_apk.sh`, verify the retained certificate, then run the focused/full v0.13.1 host regression plus bulk real-song A/B testing. If an early listening failure appears, adjust Sonic Finish policy from this checkpoint rather than reverting project/import recovery work.
