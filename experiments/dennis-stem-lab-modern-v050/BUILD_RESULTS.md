# Dennis Stem Lab Modern v0.5.0 DEV — Android Build Verified

Date: 15 October 2026

- Result: **SUCCESS** (GitHub Actions)
- Run: https://github.com/dennisp5-alt/Mastering-app/actions/runs/38682038516
- APK Actions artifact: `11948003341` / `DennisStemLab-Modern-v0.5.0-DEV-APK`
- Complete assembled source artifact: `11948083331` / `DennisStemLab-Modern-v0.5.0-Source`
- APK filename: `DennisStemLab-Modern-v0.5.0-DEV.apk`
- APK SHA-256: `4305ee2b094850322ca5730d2304cf14f44a6ab75d37a5d4afd1a2b3a6523080`
- Android application ID: `com.dennis.stemlab.modern`
- Version code/name: `5` / `0.5.0-dev`
- The APK passed ZIP integrity and its Actions-generated checksum was independently checked from downloaded artifacts. The source archive checksum was also independently verified.
- Verified source restore from v0.15.7 Actions artifact run `37899462151`, source hash `0bc7390fba6db3fa746d3a554555bbba9e7745068276929b529924426080eede`.
- Verified integration patch SHA-256 `4f3a83cc2ce4c45daf4d2adde4b07d7ea1eb18afd81f41d1cf768e86c519b8fd`.
- Strict patch application succeeded; original Java files unchanged except the intended StudioActivity UI.
- Two-stem positive import and unsafe-ZIP rejection regression passed on GitHub Actions (18-second deterministic 48-kHz stereo fixture).
- `gradle --no-daemon :app:assembleDebug` succeeded using Android SDK 34 and Java 17.
- **NOT YET DEVICE TESTED, NOT YET LISTENING-TESTED, NOT A PRODUCTION RELEASE.** No real separation-derived stems have been mastered on phone by this DEV APK yet.
- The original v0.15.7 app and its performance experiment were NOT modified. The `main` GitHub branch was NOT modified.
- Future work: S23 Ultra install, import a real vocals/instrumental WAV ZIP, finish mastering and confirm two WAV formats, verify stem import QC and final QC, listening A/B. Investigate any overlapping stem preflight/modern master repair before accepting.
- This branch holds an isolated, reproducible patch and workflow. Do not merge to main unless user explicitly approves.
