# Dennis AI Mastering Lab v0.13.0 — ready for bulk testing

Install this APK over v0.12.0. It keeps the existing package, signing identity and app data. Android 10 or later; offline; no uninstall required.

All seven additions are included:

- Master review with aligned, level-matched A/B, loops, mono, difference audition, markers and individual edit bypass.
- Smooth passage gain/EQ edits, editable timing and recipe undo.
- Selective bass/low-mid dynamic corrections with measured acceptance checks.
- Multiple local reference tracks, separately positioned passage comparisons and optional bypassed EQ suggestions.
- Explicit intro/outro trims, editable fades and conditional boundary-click finishing.
- Saved projects and a sequential multi-WAV queue with pause, retry and recovery from verified stages.
- Album comparison/order/spacing, continuous album sequence export, metadata and selectable delivery targets with verified WAV/MP3 formats.

To test: **Import WAVs → Master queued / resume → open a project → Review / looped A–B → Save verified release ZIP.** Inputs remain 48 kHz stereo WAV. Up to 40 songs can be selected in one import. Classic mastering and optional stems are still available.

The full computer regression suite and a real Pilot Light2 run passed. That song remained 202.000 seconds and delivered −14.00 LUFS / −1.72 dBTP. No bass correction was selected; the flagged ending received a 10 ms fade. Independent FFmpeg checks verified WAV and MP3 loudness, peaks, rates and decoded duration.

Phone installation, navigation, playback and background/pause behavior need your device testing. This is a bulk-test build, not a listening certification. If a song fails, completed songs remain saved; export the affected Studio QC JSON or note its project error.

Three development checkpoints and the complete final source were saved. Keep the source backup private: it includes the signing key required for compatible future updates.
