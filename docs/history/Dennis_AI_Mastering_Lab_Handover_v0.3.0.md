# DENNIS AI MASTERING LAB — v0.3.0 HANDOVER

Handover date: 2026-08-09  
Owner: Dennis  
Status: engineering build complete; physical S23 Ultra validation pending

## 1. Build identity

- App: Dennis AI Mastering Lab
- Package: `com.dennis.aimasteringlab`
- Version: `0.3.0`
- Version code: `3`
- Minimum Android: API 29
- Target Android: API 34
- Requested permissions: none
- Internet permission: none
- APK Signature Scheme: v3 verified
- Signing-certificate SHA-256:
  `2d6822f2d6e3785a03bfe61d73d5139822c78c9b2dedbbc64aa0424ad44eff70`
- APK SHA-256:
  `5896ec83eaf0a0e6a054a8116088dc808bb205e7aef33b3ec3271e7d195acafa`

Install v0.3.0 directly over v0.2.0. Do not uninstall first. The package and
signing identity are unchanged, so Android app data can be retained.

## 2. Purpose and separation from Dennis Mastering Studio

This is the secondary Full AI mastering experiment. It autonomously renders,
measures, rejects and selects one output. It remains separate from Dennis
Mastering Studio v0.5.0, whose deterministic Standard Master remains the
authoritative release path in that app.

AI Lab outputs do not automatically modify or replace a Dennis Mastering Studio
release. The AI Lab performs engineering inference, not record-label approval.

## 3. v0.3 user flow

1. Select one authoritative 48 kHz stereo WAV.
2. Optionally select a ZIP of stems.
3. Choose the destination for the Full AI 24-bit WAV.
4. The app analyses and makes every decision autonomously.
5. It saves one verified winner and makes the verified AAC, QC JSON and compact
   Full AI package available when Android codec QC succeeds.

No manual A/B choice is required. If the ZIP or every stem candidate fails,
the app completes through the five-strategy stereo path.

## 4. Guaranteed stereo baseline

The complete v0.2 five-strategy controller is retained:

- transparent flat gain;
- conservative corrective;
- tonal corrective;
- dynamic/transient;
- full combined.

Audio-identical plans are deduplicated. Each actual render is reopened and
measured. Hard safety rejection, score ranking and transparent rollback remain
active. No-stem behaviour was regression-tested against Never Be Fancy Remix
and retained the v0.2 `tonal_corrective` winner and measurements.

## 5. Optional Full AI Stem Assist

After safe admission, the app may add three internal candidates:

- `stem_vocal_humanity`;
- `stem_masking_pockets`;
- `stem_combined`.

The original stereo mix remains authoritative for all three. The only allowed
construction is:

`output = authoritative mix + scale × (processed stem − original stem)`

The app never uses a raw stem sum as the finished mix.

### 5.1 Archive and source admission

The ZIP reader rejects unsafe/traversal paths, absolute paths, backslashes,
drive paths, duplicates, more than 512 entries, more than 64 WAV stems, more
than 4 GB declared for one entry, more than 8 GB total declared expansion and
suspicious large-entry compression ratios. It fully reads entries to verify
CRC and fully decodes every WAV used in inventory.

The app records format, sample rate, valid bits, channel count, sample count,
duration, levels, clips and CRC state. Stems must be 48 kHz mono or stereo.

### 5.2 Reconstruction and local timing

The stem sum is built only for analysis. The report records waveform
correlation, least-squares gain, residual SNR, start/middle/end lag, drift,
polarity risk and reconstruction classification.

Each eligible stem is then checked locally near the start, 25%, midpoint, 75%
and ending. The corrected v0.3 probe combines envelope and signed-waveform
correlation. A stem needs at least three informative probes, at least two
positive polarity probes, no polarity-risk probe and effectively zero lag and
drift at 5 ms resolution.

Ambiguous, separation-derived, polarity-risk, drifted and materially truncated
material is excluded from automatic deltas.

### 5.3 Vocal Humanity

The lead role must be explicit and unique, with a filename-role margin above
the runner-up. The lead must also pass local timing. The model learns phrase
levels and sibilance from the song. It permits only smooth sub-dB phrase rides
and short event de-essing. It does not retune, time-warp, regenerate, denoise or
hard-gate the vocal.

The renderer adds a centered, scaled lead processed-minus-original delta.
Attack and release envelopes cannot cross the protected file boundaries.

### 5.4 Masking Pockets

Masking analysis requires the validated lead plus explicitly named and locally
secure competing instrument stems. Eligible roles include guitar, piano,
keys/organ, synth/pad, strings and brass. Vocals, drums, percussion, bass/sub,
clicks and ambiguous names such as `instrumental`, `music` or `other` cannot be
selected automatically.

The model learns robust vocal/competitor ratios in:

- 250–700 Hz;
- 700–1600 Hz;
- 1600–3200 Hz;
- 3200–6500 Hz.

Only short attenuation events are allowed. Maximum nominal cut is 0.60 dB per
event/stem and 0.75 dB combined. Broad recurring overlap is diagnostic only.
The processed-minus-original delta retains the selected stem's channel
position.

### 5.5 Protected regions and render verification

Stem delta is hard-zeroed in:

- the first 2 seconds; and
- the final `max(8 seconds, 4% of song duration)`.

Every stem pre-master is compared sample by sample with the authoritative mix.
The app verifies unchanged frame count, exact protected boundaries within a
`1e-7` floating tolerance, measurable but conservative delta, source
correlation of at least 0.995, no added exact-zero interruption, no non-finite
or over-range samples, safe stereo/low-band correlation and protected LRA.

## 6. Autonomous promotion and fallback

Stem candidates receive the same final format, duration, −14 LUFS/priority,
−1 dBTP, clipping, continuity, phase and LRA gates as stereo candidates.

The highest stem candidate can replace the guarded stereo winner only if:

- score margin is at least `0.06`; and
- measured global objective gain is at least `0.01`, or bounded targeted-event
  evidence bonus is at least `0.04`.

If it misses either condition, the report records the margin and the stereo
winner remains selected. The controlled regression exercised a valid stem win;
Never Be Fancy exercised unchanged stereo-only behaviour.

## 7. Loudness, peak and codec policy

- Loudness goal: exactly −14.00 LUFS-I after verification.
- True-peak ceiling: no higher than −1.00 dBTP.
- The ceiling is not a peak target.
- Transparent gain is tested first.
- Minimum guarded limiting is used only when necessary.
- If exact loudness would exceed the song-derived limiter allowance, dynamics
  are preserved and the honest shortfall is reported.

Final Android QC tests the selected strategy with actual AAC 256 encode/decode,
a 328 kbps SBC stress proxy and response-profile translation simulation. A
codec-safety rerender of a stem winner repeats the same stem mode from the
original mix; it cannot silently become a stereo-only render. If a candidate
cannot pass codec QC, ranking falls through to the next eligible candidate.

## 8. Reporting

The QC JSON records:

- app/build identity and evidence labels;
- authoritative source properties and SHA-256;
- section map and local diagnostics;
- optional stem archive name/hash, inventory and acceptance status;
- reconstruction and every local alignment probe;
- actual vocal rides and de-essing events;
- actual masking events, roles, bands, cuts and evidence;
- every candidate plan, render, score, cost and rejection;
- stem delta and protected-boundary measurements;
- promotion/fallback decision;
- PCM, decoded AAC, SBC proxy and translation measurements;
- technical verdict, warnings and limitations.

Measurements, track-trained inferences, response simulations and any supplied
human listening observations remain distinctly labelled. The app never claims
physical playback by itself.

## 9. Validation completed

- Clean flattened source compiled with Android 34/build-tools 34.0.0.
- Package/version/API identity verified.
- Requested permission list verified empty.
- Zip alignment verified.
- APK Signature Scheme v3 and retained certificate verified.
- No-stem Never Be Fancy autonomous regression passed.
- Already-compliant no-render/hash-preservation regression passed.
- Controlled exact production-stem reconstruction and five-position alignment
  passed for lead and guitar.
- Controlled Vocal Humanity, de-essing and Masking Pockets candidates rendered.
- Protected intro/ending, source correlation, phase, duration, loudness
  hierarchy, true peak and clip checks passed.
- Controlled combined stem candidate cleared the promotion gate.
- Deliberately 20 ms delayed guitar was rejected by local timing.
- ZIP traversal path was rejected.
- Source and release ZIP integrity will be recorded in the external checksum
  manifest shipped beside this handover.

See `TEST_RESULTS.md` for exact controlled measurements and hashes.

## 10. Physical S23 Ultra work still required

No Android device was connected to this build environment. Dennis should test:

1. install v0.3.0 directly over v0.2.0;
2. app-data retention and launch;
3. WAV and optional ZIP document pickers;
4. safe fallback with no ZIP and a rejected ZIP;
5. one valid stem run;
6. long-run foreground behaviour and cancellation;
7. saved WAV/AAC/report/package access;
8. actual Android AAC encode/decode and SBC proxy completion;
9. playback and package opening;
10. level-matched listening against v0.2 and Dennis Mastering Studio on the
    S23 Ultra, speakers, headphones and other relevant systems.

These pending tests prevent a claim of physical listening or phone-level final
certification, but do not invalidate the completed desktop engineering gates.

## 11. Latest-source instruction

Treat this v0.3.0 source archive as the latest Dennis AI Mastering Lab
implementation. Do not resume AI Lab development from v0.2.0 or from Dennis
Mastering Studio source. Dennis Mastering Studio v0.5.0 remains the latest
implementation of the separate deterministic app.
