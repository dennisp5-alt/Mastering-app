# v0.5 scope and limits

## Authoritative policy

The original stereo WAV remains the musical authority. v0.5's new modules
operate only on that stereo waveform. Optional stem trust remains separate:
Tier 1 may use processed-minus-original deltas, Tier 2 supplies measurement
guidance only, and Tier 3 is rejected.

## Stereo Event Repair AI

The module reuses song-learned 125 ms anomaly evidence for short:

- high-frequency emphasis;
- upper-mid harshness; and
- low-mid masking.

Only attenuation events that clear confidence, section-relative and coverage
gates are eligible. Each source event is capped at 0.60 dB; simultaneous
spectral attenuation is capped at 0.80 dB. Widespread tonal conditions remain
diagnostic-only. This is not broadband denoising or automatic generic EQ.

## Low-End Intelligence

Low-End Intelligence learns low-band share, sub-within-low share, crest,
derivative energy and below-160 Hz correlation from the song. It may create:

- short sustained sub-bloom attenuation;
- short sustained bass-bloom attenuation; or
- low-side attenuation during a local phase-risk interval.

Short high-crest or high-derivative windows are protected as kick-like
transients. Bloom events shorter than 0.245 seconds remain diagnostic-only.
Automatic low-end coverage may not exceed 16% of the track per event kind, and
simultaneous nominal low attenuation is capped at 0.65 dB. The module does not
replace a bass or kick and does not force the low end mono globally.

## Stereo Vocal Focus AI

This module does not claim vocal separation. It looks for short,
non-transient, centre-dominant 700–4200 Hz evidence whose centre/side ratio and
spectral shape remain inside conservative physical bounds. Eligible events use:

- a 0.14–0.30 dB mid vocal-band lift; and
- a simultaneous 0.08–0.20 dB side vocal-band pocket.

Events shorter than 0.34 seconds are diagnostic-only; one segment may not
exceed 2.25 seconds; automatic coverage may not exceed 38% of the song. The
module cannot identify lyrics, pitch-correct, retime, regenerate, denoise or
guarantee that a centre source is the lead vocal.

## Combined mode and protected regions

The combined candidate exists only when at least two modules are available.
Its simultaneous nominal edit is capped at 0.90 dB after the spectral and
low-end sub-caps. All module envelopes are hard-zero in:

- the first 2 seconds; and
- the final `max(8 seconds, 4% of duration)`.

The float premaster must preserve sample rate, channels, frame count, duration,
continuity and protected regions; introduce no clips/non-finite samples or
exact-zero interruption; retain source correlation of at least 0.995; avoid
worsening global/low-band correlation; and lose no more than 0.65 LU of LRA.

## Autonomous selection

The five core stereo strategies are evaluated first and retain their
transparent no-change guard. Stereo-intelligence candidates then compete
against the guarded core winner. Promotion requires both:

- score margin at least 0.060; and
- measured targeted benefit at least 0.100.

Model confidence or safe rendering alone cannot promote a candidate.
Non-promoted candidates are explicitly blocked from codec fallback. A promoted
mode that needs a codec-safety ceiling rerender is reconstructed through the
same stereo-intelligence renderer and mode.

Optional stem candidates are evaluated afterward under the existing Tier 1 or
Tier 2 promotion rules. The final candidate still must pass PCM, actual Android
AAC decode, SBC proxy and translation gates.

## Mastering hierarchy

- Target: exactly −14.00 LUFS-I after verification.
- True-peak ceiling: no higher than −1.00 dBTP.
- The ceiling is not a peak target.
- Transparent flat gain remains preferred when it is strongest.
- If exact loudness requires more peak control than the song-derived allowance,
  dynamics win and the honest shortfall is reported.

## What remains unverified here

The build environment did not run the APK on a Samsung Galaxy S23 Ultra or
perform physical speaker/headphone listening. Android codec availability,
document-provider behavior, long foreground runs, cancellation, playback,
package opening and matched-level listening must be exercised on the phone.
