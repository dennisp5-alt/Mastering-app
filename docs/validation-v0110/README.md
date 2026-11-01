# v0.11.0 release evidence

`full-release-gate.log` records the complete regression suite and two real-song exports. `release-validation.json` holds real-song hashes and independent FFmpeg readings. The real-song QC/preparation files are original run outputs, preserved without editing.

`final-package-build.log` records the rebuild and package integration after report/UI wording corrections. `final-package/` shows the current report policy. No DSP or acceptance threshold changed between the full audio gate and that final rebuild. Earlier raw reports contain a stale v0.7 policy description; the final report and current source supersede that label.

Audio and the Android SDK are excluded. Actual phone playback/AAC and human listening were not tested. Host codec verdicts are CONDITIONAL by design.
