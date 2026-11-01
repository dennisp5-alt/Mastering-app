# v0.8.0 scope and limits

Dennis AI Mastering Lab v0.8.0 adds four offline, per-song, authoritative-
stereo candidate families. They remain experimental and separate from Dennis
Mastering Studio's deterministic release path.

## 1. Stereo Depth and Reverb Intelligence

- Learns 100 ms mid/side vocal-band and upper-band behavior from the song.
- May attenuate a short 700–6,500 Hz masking side event or a short
  6,500–10,000 Hz unsafe-width event.
- Maximum event cut is 0.65 dB; total simultaneous v0.8 edit is 0.85 dB.
- It never increases side energy or automatically widens a flat/narrow mix.
- Broad evidence above the coverage gate is diagnostic-only.

## 2. Limiter and Crest Optimisation

- Runs only when flat target gain demonstrates limiter pressure within the
  song-derived reduction allowance.
- Renders clean-long (8 ms/220 ms), balanced (5 ms/150 ms) and transient-
  recovery (3 ms/90 ms) stereo-linked profiles.
- Measures actual output loudness, true peak, limiter reduction, crest loss
  and LRA loss; the lowest-cost safe render is retained.
- If an alternative does not show a material measured gain over balanced, it
  cannot clear the module-specific promotion gate.

## 3. Low-Frequency Phase Rescue

- Learns local L/R correlation independently in 25–60, 60–100 and
  100–160 Hz bands.
- Only the affected side-band component is attenuated; bass is never
  synthesized, replaced or globally monoed.
- Broad phase risk becomes review-only rather than a local repair.

## 4. Vocal Artifact Intelligence

- Uses centre-channel spectral evidence to infer short 6.5–10 kHz sibilance,
  25–160 Hz plosives and 1.6–3.2 kHz harsh-vowel events.
- A vocal is not separated or proven. Pitch, timing, breaths and the full mix
  remain intact.
- Cuts are event-specific and at most 0.65 dB. Broad high-frequency
  attenuation is not treated as de-essing.

## Shared safeguards

- Authoritative input must be 48 kHz stereo WAV; source bit depth is reported
  independently and is never inferred from sample rate.
- First 2 seconds and final `max(8 seconds, 4% of duration)` are protected.
- No automatic widening; no raw stem sum; no waveform generation.
- Float pre-master must retain frame count, correlation at least 0.9950, exact
  edges, LRA within 0.45 LU and no added full-scale/non-finite samples.
- Final 24-bit master requires zero clips/non-finite samples, true peak no
  higher than −1.00 dBTP and the normal loudness/dynamics hierarchy.
- A v0.8 candidate requires at least 0.050 score margin, 0.080 targeted benefit
  and module-specific measured benefit to clear promotion.
- Preference Memory and Album Consistency cannot override hard QC or module
  promotion gates.

## Corrected integrity semantics

- A source full-scale sample is inherited evidence, not an “introduced”
  candidate fault. Pre-master rejection occurs only if the count increases.
- Final outputs still require zero clips.
- Every internal source exact-zero region of 100 ms or longer is carried into
  final QC. Dither making samples nonzero is explicitly not repair.
- Any unresolved source long-zero region forces a CONDITIONAL technical
  verdict with exact time ranges.

## Required physical tests

Install directly over v0.7.0 on the Samsung Galaxy S23 Ultra without
uninstalling. Confirm app-data retention, Android AAC encode/decode, playback,
document-provider saves, cancellation, package opening and repeated bulk runs.
Perform level-matched listening separately. Response simulations are not
physical playback observations.
