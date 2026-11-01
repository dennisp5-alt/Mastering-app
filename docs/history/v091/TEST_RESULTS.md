# Dennis AI Mastering Lab 0.9.1 validation record

Test date: 2026-08-27

These are objective engineering regressions and response simulations. Physical
S23 Ultra installation, Android MediaCodec behavior and matched-level human
listening remain device tests.

## Signed APK

- Package/version: `com.dennis.aimasteringlab` / `0.9.1` (`11`)
- Minimum/target API: 29 / 34
- APK Signature Scheme v3: verified; one signer
- Signing certificate:
  `2d6822f2d6e3785a03bfe61d73d5139822c78c9b2dedbbc64aa0424ad44eff70`
- Permissions: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`,
  `POST_NOTIFICATIONS`, `WAKE_LOCK`
- No Internet, microphone or broad storage/media permission
- APK SHA-256:
  `39d4ef780d7171f643f1000e4686fe86566d932dbac424f8459cc4f789b4bcbc`

## v0.7 sonic-reference regressions

Four clean-state runs were remastered from the same original WAVs used in the
version comparison. v0.9.1 selected the same strategy and produced the exact
v0.7 waveform hash in every case:

| Song | Selected strategy | Exact SHA-256 |
| --- | --- | --- |
| Never Give Up | `transparent_flat_gain` | `3497d7bd48ab608895ddf04cbb6b5b5308312c52acdff562edc337354429f0ab` |
| Never Be Fancy | `tonal_corrective` | `d71c7da171c4c58a9cde5e6d39a59251c1270a2a8d255c0e4a1f5328c871caa5` |
| T-Bar Girls | `transient_intelligence` | `8abddd0dfd113a2985617ffbc85765aaa2893dc1a6e02aeaa1ebb161bddd7cc1` |
| The Corner Stage | `transparent_flat_gain` | `235d52fecda0c821368b81ce522b669deb41156021977410525fa23fd8b4e02c` |

The Corner Stage master also passed the already-mastered no-op guard at
24-bit/48 kHz, −14.0000 LUFS-I and −3.3683 dBTP. Its 12 playback-response
simulations returned 12 PASS / 0 REVIEW without generating processing.

## Controlled regressions

- v0.9 hostile fixture: PASS for lossy/artificial-resolution/prior-master
  guards, hum/buzz, musical single-tone negative control, polarity/delay,
  stable channel repair, optional reference and diagnostic translation.
- v0.8 professional fixture: PASS for all five render modes, no widening,
  protected edges, target hierarchy, clips and non-finite samples.
- v0.7 advanced fixture: PASS for section, artifact, transient and combined
  renders, including natural-transient protection.
- Stereo Intelligence: PASS for all three modules plus combined.
- Source Integrity: PASS for inherited clipping and unresolved-zero provenance.
- Click/Dropout Repair: PASS.
- Tier 1 stem audio-delta and Tier 2 analysis-only stems: PASS.
- Preference/Album model boundaries: PASS.
- Foreground job progress/completion/cancellation state: PASS.

The render-integrity stress test reproduced a shortened premaster and verified
that v0.9.1 selected no winner. After isolating the test storage, the identical
run completed and produced the expected v0.7 hash. This proves incomplete
candidates cannot silently change the competition.

## Physical validation still required

- Install over v0.9.0 on the S23 Ultra and confirm both learned-profile
  controls initially show OFF while stored history remains.
- Test Home/task switching, screen-off continuation and notification Cancel.
- Verify Android document saving/playback, AAC encode/decode and package open.
- Compare v0.7 and v0.9.1 at matched level on the phone.
