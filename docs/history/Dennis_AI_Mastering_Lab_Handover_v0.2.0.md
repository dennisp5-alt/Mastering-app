# DENNIS AI MASTERING LAB — v0.2.0 HANDOVER

Handover date: 2026-08-09  
Owner: Dennis

## Authoritative build identity

- App: Dennis AI Mastering Lab
- Package: `com.dennis.aimasteringlab`
- Version name/code: `0.2.0` / `2`
- Minimum/target Android: API 29 / 34
- Requested permissions: none
- Internet permission: none
- APK Signature Scheme v3: verified
- Signing-certificate SHA-256:
  `2d6822f2d6e3785a03bfe61d73d5139822c78c9b2dedbbc64aa0424ad44eff70`
- APK SHA-256:
  `62f2c9fd370d7a8e52626e561be207b617755bf8e3706f91d4ca347d41c1d792`

Install v0.2.0 directly over v0.1.0. Do not uninstall first if retaining app
data matters. The signing identity is unchanged. The build script now refuses
to generate a replacement key when the retained keystore is missing.

This is a separate secondary app. It does not replace, update or share a
package with Dennis Mastering Studio v0.5.0. DMS v0.5.0 remains the current
authoritative deterministic mastering implementation.

## v0.2 operating model

The user supplies one authoritative 48 kHz stereo WAV and requests one Full AI
master. There is no manual Reference/Conservative/Preferred selection screen.

The app internally considers:

1. `transparent_flat_gain`
2. `conservative_corrective`
3. `tonal_corrective`
4. `dynamic_transient`
5. `full_combined`

Audio-identical plans are not redundantly rendered. Every unique strategy is
rendered and reopened. The selection layer uses actual output measurements,
not only the controller's predicted objective.

Hard rejection covers:

- sample rate, channel, bit-depth, sample-count or duration changes;
- true peak above -1.00 dBTP;
- unprotected failure to reach -14.00 LUFS-I;
- clips or non-finite samples;
- introduced exact-zero interruptions;
- excessive global or below-160 Hz correlation loss;
- more than 1.25 LU LRA loss;
- any existing pre-master guardrail failure.

Eligible candidates are scored using measured spectral-corridor improvement,
phase/mono preservation, LRA preservation and the amount of EQ, event control,
stereo processing, compression and limiting actually used. A minimal-processing
tie break and explicit no-change margin can force transparent rollback.

When the source is already a compliant 24-bit/48 kHz master and transparent
wins, the source is copied byte-for-byte. No redundant dither pass is made.

## Codec-aware autonomous fallback

The current leading candidate is encoded on the phone to 256 kbps AAC-LC,
decoded and measured. A 328 kbps SBC/Bluetooth stress proxy is also measured.

If AAC exceeds -1.00 dBTP, the app estimates the actual codec overshoot and
derives a lower PCM ceiling with an additional 0.08 dB guard. It then rerenders
the same processing plan from the original source with forced stereo-linked
limiting, reconverges toward -14.00 LUFS-I and re-encodes. The rerender is
rejected if it exceeds the song-derived limiter allowance or any safety gate.
The next ranked candidate is tried when required.

If no candidate passes every codec gate, the highest-scoring PCM-safe candidate
is retained with a `CONDITIONAL` technical verdict. Codec failure is never
silently ignored.

## Outputs

- `_FullAI24.wav`: the single autonomously selected 24-bit/48 kHz master.
- `_FullAI256.m4a`: actual device AAC only when encode/decode QC passes.
- `_FullAI_QC.json`: complete features, plans, scores, rejections, processing,
  measurements, codec attempts, simulations, limitations and hashes.
- `_FullAI_TR.wav`: documented translation composite.
- `_FullAI_SHA256.txt`: self-contained payload checksum manifest.
- `_FullAI_Package.zip`: verified compact Full AI handoff package.

Reproducible losing candidates are deleted only after the final package exists.
The selected master, AAC, report and package stay in the private cache while
the activity remains available so Dennis can save them through Android's file
picker.

## Key implementation files

- `AutonomousMasteringBrain.java`: track learning and five internal plans.
- `AutonomousMasteringRenderer.java`: deterministic protected float processing.
- `FullAiMasteringEngine.java`: rendering, hard gates, measured scoring,
  deduplication, transparent preservation and autonomous PCM selection.
- `FullAiReleaseQc.java`: AAC/SBC verification, codec-safe rerender and fallback.
- `FullAiReportWriter.java`: machine-readable Full AI evidence.
- `FullAiPackageBuilder.java`: self-contained manifest and verified ZIP.
- `AiLabActivity.java`: one-input, one-winner Android workflow.
- `MasterRenderer.java`: target convergence, true-peak control, limiting and
  24-bit dither.

The older v0.1 A/B classes and harness remain in source only for regression and
historical comparison. They are not exposed by the v0.2 launcher interface.

## Validation highlights

- Never Be Fancy: autonomously selected the tonal candidate that matches the
  substantive direction Dennis previously preferred by ear.
- Selected real-song test render: -14.0000 LUFS-I, -3.8260 dBTP, 6.6078 LU LRA,
  zero clips.
- Transparent real-song candidate retained the exact input SHA-256.
- Never Give Up Standard-core regression reproduced the prior authoritative
  SHA-256 exactly:
  `ef5e4e28571639e97f596832e651a04ddd82c468028729c869ce88f3c1a789ed`.
- Peak/dynamics fixtures exercised transparent rollback.
- Forced codec-headroom fixture reached -14.0065 LUFS-I and -1.5500 dBTP with
  reduction inside its learned allowance.

See `TEST_RESULTS.md` for full measurements and evidence labels.

## Remaining physical validation

The build environment had no connected Android phone. On the Samsung Galaxy
S23 Ultra, verify:

1. v0.2.0 installs directly over v0.1.0.
2. File selection and destination creation work with Dennis's normal provider.
3. The screen remains active through a full-length multi-candidate run.
4. Cancellation leaves no misleading finished output.
5. The phone's actual AAC encoder/decoder completes and reports its component
   names.
6. The saved WAV, AAC, report and ZIP reopen correctly.
7. Physical mono, phone speaker, Bluetooth, buds/headphones, home system and car
   checks are performed where available.
8. The app-selected master is compared level-matched against the professional
   mastering result.

Do not describe response simulations as physical listening. Do not claim
professional A&R approval or major-label certification from the app alone.
