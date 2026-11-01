# Dennis AI Mastering Lab v0.13.1

This update fixes saved-source import failures in the Studio workspace.

## Install and recover your queue

1. Install v0.13.1 over v0.13.0. **Do not uninstall or clear app data**; the retained WAV copies and projects are needed for recovery.
2. Open Projects and tap **RETRY FAILED / INCOMPLETE EXPORTS**.
3. Completed WAV imports left behind by the old path error are rechecked and recovered. Each song then continues through mastering normally.
4. Open the finished project, review original/master playback, and save its verified release ZIP.

If a completed source copy is genuinely absent, truncated or no longer matches a previously recorded checksum, the app gives a more specific error and asks you to import that WAV again. It does not replace an existing checksum to accept changed audio.

## What changed

- Fixed project-relative paths when Android exposes the same private storage folder through different names. Previously the WAV could copy successfully but fail before its project file reference was saved.
- Imports now compare the selected stream's checksum with the completed copy, then save the copy's identity before analysis.
- Added recovery of completed v0.13.0 imports with missing initial metadata. Where the old failure prevented any checksum being saved, the app fully re-reads the retained WAV and records its first checksum. The project and QC report disclose this; review the source before release.
- Preserved original import errors and separated missing-copy, changed-checksum and incomplete-copy failures.
- Kept the seven Studio features, mastering processing, targets and update-signing identity from v0.13.0.

Package: `com.dennis.aimasteringlab`; version code 18. Fully offline; Android 10+. Input remains stereo 48 kHz WAV.

## Validation

All three host test harnesses passed, including four-project recovery and the full Pilot Light mastering/export run (-14.00 LUFS-I, -1.72 dBTP). See `docs/validation-v0131` and `TEST_RESULTS.md` for the completed release gate. The original path failure was reproduced against v0.13.0 before applying the fix. Physical-phone navigation and background behavior still require device testing.

Keep the source backup private: it includes the retained signing key for compatible future updates.
