# v0.14.1 Candidate Pipeline Repair validation status

Static source validation passes. The modified candidate engine uses bounded family ranges, shortlists expensive candidates, reuses exact source-control features, and skips full feature learning for specialist renders that cannot reach their promotion floor. Studio uses PCM-only base QC and WAV-only finishing, with two user release files.

The SDK-less host javac sweep reports zero syntax/structure diagnostics. Android and `org.json` types are not installed on this host, so unresolved platform symbols are expected and are not a substitute for a fresh Android APK compile.

A fresh GitHub Actions Android build is the remaining compile gate before phone testing. See `RESULTS.json` for the machine-readable assertions.

## Executable host regression

`V0141RepairHarness` compiled the non-Android production source with host `org.json` and Android-codec stubs, then executed a 24-second stereo project through import, `StudioPipeline.master`, PCM-only base QC and `StudioPipeline.finish`. Both master and finishing progress were asserted never to decrease. The run produced a verified 24-bit/48 kHz master and 16-bit/44.1 kHz WAV, with no Studio MP3 or release-ZIP references, zero codec attempts, translation marked `NOT_RUN_STUDIO_PCM_ONLY`, and radio simulation marked `NOT_RUN_WAV_ONLY`.


## Real Ribbon of Black regression

The actual v0.14.0 `RibbonOfBlack_Master_24bit-48kHz.wav` was re-imported through the v0.14.1 Studio master path with a progress observer that aborts if the visible percentage decreases. The run completed monotonically from 0→100 in about 45.4 seconds on the host (phone timing is not implied), selected `vocal_artifact_intelligence`, and changed the audio hash while retaining the delivery metrics around −14.000 LUFS-I / −1.374 dBTP / 4.367 LU LRA. The selection comparison reported 5/5 eligible artifact events improved and none worsened. Studio codec/translation passes were not run.

The same project then completed WAV-only finishing in about 22.3 host seconds, producing the 24-bit/48 kHz master and 16-bit/44.1 kHz derivative only. No MP3 or per-track release ZIP project reference was created, radio/broadcaster simulation remained `NOT_RUN_WAV_ONLY`, and visible progress remained monotonic.

These host timings are regression measurements, not performance guarantees for the Galaxy phone.
