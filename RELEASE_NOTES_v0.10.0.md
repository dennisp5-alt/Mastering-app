# Dennis AI Mastering Lab v0.10.0 — Natural Studio

Install **DennisAiMasteringLab-v0.10.0.apk** over your existing AI Mastering Lab v0.9.0 or v0.9.1. No uninstall is needed. The package and original signing certificate are retained. Dennis Mastering Studio and Stem Lab are separate apps.

Select the original **48 kHz stereo WAV**, leave the optional Preference Memory and Album Consistency controls off for the neutral baseline, and create the master. Processing stays offline. Android 10 or later is required.

What changed:

- The complete guarded v0.7 decision remains the natural reference. Transparent/no-change is rendered first.
- Later depth/reverb/vocal shaping, reference matching and Translation Consensus remain diagnostic-only. Device simulations only check the result.
- Sparse peak limiting can reach approximately −14 LUFS without accepting sustained heavy gain reduction. Sources that need excessive processing retain an honestly reported loudness shortfall.
- Clicks, short stereo dropouts and strictly isolated per-channel digital clip plateaux receive bounded repair. Stable hum/whine candidates need measured before/after benefit. Ambiguous panning and dense clipping stay diagnostic.
- AAC headroom protection uses flat attenuation, without adding another limiter pass. A small codec-safe loudness margin is labelled explicitly.
- An optional candidate read failure rejects that candidate and records the reason while retaining a verified baseline. Source, baseline and final-file integrity failures still stop the job.
- Repaired sources are re-measured, original provenance is preserved, and known source defects remain visible in QC.

Validation completed:

- Signed APK, original certificate, package ID and version code verified; no Internet permission.
- Sparse-peak fixture: **−14.0001 LUFS-I / −1.2000 dBTP**, 0.032 dB average limiter reduction. FFmpeg independently measured −14.0 LUFS / −1.2 dBTP.
- All four v0.7 advanced fixture modes passed duration, peak, continuity and protected-region checks.
- Optional-read failure recovery, truncated-source rejection, cancellation, click/dropout repair, isolated clipping with overlapping channels, dense/rounded-peak negative controls, measured hum reduction and output-package verification passed.
- Reprocessing both a compliant synthetic master and the full “Never Be Fancy” master preserved every byte.

**Still to test on your phone:** installation, playback, screen-off/background operation and Android AAC encoding. The host cannot validate those device operations or replace a matched-level listening comparison. Severe baked-in clipping, reverb or mix-balance problems remain outside safe automatic repair.

The source backup contains the complete project, regression tests, validation logs and retained private update-signing key. Keep it private for future compatible builds.
