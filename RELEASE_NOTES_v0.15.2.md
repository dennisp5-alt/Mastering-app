# Dennis AI Mastering Lab v0.15.2 — Version Truth + No-Event Core Rendering

- Studio workspace and classic mastering UI read `BuildConfig.VERSION_NAME` instead of stale hard-coded strings. The Studio QC `app_version` and radio status reason use the same value. Core engine label also follows the installed version.
- Audio policy, master selection, limiter, true peak, source alignment and QC safety gates remain unchanged.
- Core AI renders with **zero measured local correction events** skip unused local high/harsh/mud filter calculations. Any render with correction events follows the old full path.
- `core_no_event_filter_fast_paths` is published in Studio QC to tell whether the shortcut executed; it is not a guarantee of a given speedup.
- The no-event rendering fast path is checked against the v0.15.1 source implementation by comparing rendered float WAV bytes across representative finite audio and core plans.
- No GPU code introduced: no evidence GPU benefits these sequential DSP paths without energy/accuracy regressions.
- Built as a separate TEST package by GitHub Actions, leaving previous test versions available.
