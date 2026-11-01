# v0.13.1 validation

The final focused release gate passed all three host harness invocations; see RESULTS.json and HOST_GATE.log.

- StudioImportPathHarness: the unchanged v0.13.0 classes fail with Invalid project file path under an Android-style /data/user/0 -> /data/data symlink. v0.13.1 saves source.wav as a project-relative path and verifies it after reload. Before-fix trace is saved separately.
- StudioImportRecoveryHarness: new imports hash the selected stream and completed copy; four legacy failed projects recover; known-checksum path recovery passes; changed/missing/partial/truncated copies, untrusted post-render sources, cancelled copies and path escapes are rejected. Interrupted analysis resumes. Both new import and old-project recovery use the real Pilot Light2 WAV through the same path alias.
- The recovered real song completed the production StudioQueue, full mastering, final WAV24/WAV16/MP3, QC and release ZIP; hashes, project reload and verified-base resume passed.
- StudioV013Harness: inherited Studio editing/bypass/protected regions, exact trim/fades, mid/side behavior, bass benefit/false-positive gates, persistence/undo/corruption recovery, queue failure isolation/pause/resume, reference null, custom delivery/metadata/export, package/album and stale-render checks all pass.

Recovered Pilot Light2: 202.000 seconds / 9,696,000 frames, -14.000000000054792 LUFS-I, -1.7199175452479238 dBTP. Source SHA256 dbc52c7c69b310b48cc19df1de72306251412458e482ce5b49ecb6b01e0a9d22 remains unchanged. Measured derivatives and recovery provenance are in Recovered_PilotLight_QC.json.

An initial integration attempt used the 20-second import fixture and was rejected by the existing section mapper minimum length. The final integration input was replaced with the actual full-length song. No mastering algorithm was changed to make the short fixture pass.

The APK compiled and verified with version code 18 and the retained signing certificate. No Internet permission was introduced. Source code was not changed after the final passing host gate began, apart from documentation.

These are host tests with a real filesystem alias and the production Java import/queue/DSP paths. No physical Android/emulator instrumentation or human listening test was performed. Dennis's existing phone projects have not been inspected directly; completed retained copies should recover, but absent or damaged copies still require re-import.
