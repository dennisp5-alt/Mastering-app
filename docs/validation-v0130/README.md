# v0.13 validation

All entries in RESULTS.json exited 0. HOST_GATE.log preserves the combined gate, including both recovery and cancellation invocations whose older per-test filenames overlap. The test runner now uses numbered log filenames and records arguments for future runs.

StudioV013Harness covers production editing/persistence/queue/delivery/album code. The low-end positive fixture selected 120 Hz: 0.990115 dB target/body-ratio improvement, −0.008381 dB body change. Steady bass and unchanged candidates are rejected. Exact identity/bypass, protected samples, mid/side preservation, sample-accurate trims, zero fade boundaries, metadata PCM preservation, custom −18 LUFS / −2 dBTP delivery and derivative ceilings passed.

StudioRealMusicHarness processes the actual supplied Pilot Light2 source, saves the base, reopens project state, confirms the valid base is reused, and completes Studio finishing and exports. PilotLight_Studio_QC.json contains the result. Independent_PilotLight_Formats.json contains separate FFmpeg decoded duration and loudness/peak checks.

Final later edits affect project display-name persistence, album list filtering, version text and foreground completion ordering. They do not change the tested DSP. The final Android build compiles all code and validates signing/manifest. No Android emulator or physical device was exercised in this environment; user bulk testing must cover installation, UI and native playback/background behavior. No human listening was performed by the assistant.
