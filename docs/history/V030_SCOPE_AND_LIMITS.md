# v0.3 scope and limits

## Authoritative policy

The original stereo WAV is always authoritative. Stems are optional analytical
and delta-processing evidence. A raw stem sum is never promoted or substituted
for the mix.

## Stem admission

The app accepts at most 512 ZIP entries, 64 WAV stems, 4 GB per entry and 8 GB
declared expansion. It rejects traversal, absolute, backslash, drive-letter,
duplicate and suspicious high-ratio paths before stem use. Every non-directory
entry is fully read for CRC verification; every WAV is decoded and measured.

Automatic stem processing additionally requires:

- 48 kHz mono or stereo WAV stems;
- safe reconstruction evidence without global polarity risk or a
  separation-derived classification;
- five local timing probes near the start, 25%, midpoint, 75% and ending;
- effectively zero measured lag and drift at the validator's 5 ms resolution;
- sufficient positive signed-polarity evidence;
- a unique explicit lead-vocal role;
- explicit, eligible competing instrument roles for Masking Pockets.

An archive or individual stem that fails a gate cannot remove the stereo
fallback.

## Candidate processing

- Vocal rides: song-learned, smooth and sub-dB; pitch, timing, breaths,
  vibrato and grit are not regenerated.
- De-essing: only short learned sibilance outliers; widespread attenuation is
  rejected.
- Masking: 250–700, 700–1600, 1600–3200 and 3200–6500 Hz; attenuation only;
  maximum 0.60 dB per event/stem and 0.75 dB combined.
- Boundaries: stem delta is forced to zero in the first 2 seconds and final
  `max(8 seconds, 4% of song duration)`.
- Candidate construction: processed-minus-original stem deltas around the
  stereo mix only.

## Promotion rule

A stem-aware candidate first passes the same format, duration, loudness,
true-peak, clipping, non-finite, exact-zero interruption, correlation,
low-band correlation and LRA gates used for stereo strategies, plus delta and
protected-boundary verification.

It can replace the guarded stereo winner only when:

- its measured score margin is at least `0.06`; and
- either its measured global objective gain is at least `0.01`, or its bounded
  event-evidence bonus is at least `0.04`.

Otherwise the app records the reason and selects stereo automatically.

## Mastering hierarchy

The target is exactly −14.00 LUFS-I and the ceiling is −1.00 dBTP. The ceiling
is not a peak target. Transparent gain is preferred. If exact loudness would
require more peak control than the song-derived allowance, dynamics win and the
honest shortfall is reported.

## What remains unverified here

The build environment did not perform physical speaker/headphone listening or
run the APK on a Samsung Galaxy S23 Ultra. Android AAC encode/decode, SBC proxy,
document-provider behaviour, long foreground runs, cancellation, playback,
package opening and matched-level listening must be exercised on the phone.
