# v0.12.0 recovery checkpoint — 2026-09-30
All four additions are connected: 25 ms musical context checks; moving-frequency cut-only dynamic EQ; 4x oversampled limiter candidate; 16/44.1 and offline CBR320 MP3 release exports, individual save controls, report and ZIP integration.

Focused Java host tests V012ExportsHarness and V012AudioHarness pass. MP3 decoded duration independently matches ffmpeg (352800 frames); aligned floating decode residual RMS 8.2e-8 after 0.00027 dB decoder gain difference. Export cancellation and failure isolation pass. Fixed upstream Jump3r gapless-header ID3 offset bug in the wrapper without modifying vendor sources. Actual oversampled final render: -14.002054 LUFS / -1.237143 dBTP. No device/listening verification yet.

Pending: full regression gate including QualityFirst positive candidate promotion, Claude file byte preservation, Corner Stage real-song run, independent output checks; final signed APK/notes/source delivery. Do not treat this checkpoint as released.

Build: DAML_ANDROID_SDK=<sdk> bash tools/build_apk.sh
Host regression: DAML_ANDROID_SDK=<sdk> python3 tools/run_v0120_tests.py [Claude-WAV]
Retained update signing key is included; keep private. Source v0.11 provenance remains authoritative; v0.12 docs not finalized.
