# v0.6 scope and limits

## Authoritative policy

The original stereo WAV remains the musical authority. Click/dropout repair
may create a verified, same-duration continuity-corrected working source; all
mastering candidates then operate on that source so an accepted micro-repair
cannot disappear when another candidate wins. Optional stem trust remains
separate: Tier 1 permits guarded deltas, Tier 2 supplies analytical guidance
only, and Tier 3 is rejected.

## Dennis Preference Memory

Preference learning is deliberately explicit and small:

- only the final winner and a technically eligible runner-up are offered;
- playback outputs are already mastered to the same loudness target;
- the comparison identity is derived from the two rendered SHA-256 hashes;
- the same exact pair cannot be counted twice;
- at least three comparisons are needed before any influence;
- a strategy needs at least two observations and a ≥0.20 directional
  preference; and
- the score contribution is capped to ±0.035.

Preference Memory ranks only candidates that already passed the hard render
checks. It cannot create a processing plan, resurrect a rejected candidate,
override a promotion block, relax loudness/peak/continuity/phase checks or
replace human matched-level listening.

## Album Consistency Mode

The album profile is a rolling average of verified winners' low, low-mid,
presence, harsh, air, side, LRA and crest measurements. Duplicate output
hashes are ignored. The profile is optional and song-group-specific; starting
a different release requires a new/cleared profile.

The album-guided candidate is bounded to:

- low shelf: ±0.45 dB;
- low-mid bell: ±0.42 dB;
- presence bell: ±0.40 dB;
- high shelf: ±0.35 dB;
- side scale: reduction only, no more than 2.5%;
- bus compression: bypassed; and
- loudness: the normal −14.00 LUFS-I target, never an album-specific increase.

Sub-0.05 dB tonal proposals are bypassed. An album-guided core candidate can
clear the no-change gate only when its measured album distance improves by
more than 0.10, its technical score is no worse by more than 0.025 and its
processing cost is at most 0.10. Hard safety remains dominant. This is tonal
and dynamics-character matching, not track-order loudness sequencing or human
album mastering.

## Click/Dropout Repair

Automatic repair is limited to two provable sample patterns:

1. An isolated single-sample prediction failure that strongly dominates both
   neighbours and overlaps a track-state `TRANSIENT_DISCONTINUITY` event with
   confidence at least 0.97.
2. A stereo-synchronous exact-zero run from 2 through 960 frames (up to 20 ms)
   bracketed by proven active audio.

The first 2 seconds and final `max(8 seconds, 4% of duration)` are excluded.
At most 96 events and no more than `min(4800 frames, max(96 frames, 0.025% of
the song))` can be accepted. Long silence, fades, ambiguous discontinuities
and normal musical transients remain untouched.

The repaired float source must retain sample rate, channel count, frame count,
duration and exact protected edges; produce no non-finite sample, extra clip or
new internal exact-zero run; and retain source correlation of at least 0.997.
Failure rejects the repair rather than weakening the gate. Linear/smooth
interpolation cannot reconstruct information that was never present.

## Master Confidence Map

The map uses learned arrangement segments as candidates and reports, for each
segment:

- actual stages/events present in the selected render;
- detected evidence retained as rejected or review-only; and
- protected or otherwise untouched waveform areas.

Global loudness, EQ, width, compression, limiting and final measurements are
reported separately. If arrangement learning is unavailable, a whole-song
fallback is used. Section roles are not confirmed composer intent, and the map
is an engineering audit rather than an audibility or artistic-quality claim.

## Retained v0.5 bounds

Stereo Event Repair, Low-End Intelligence, Stereo Vocal Focus and their
combined candidate retain their v0.5 event, coverage, source-correlation,
protected-edge and promotion gates. Tier 1/Tier 2/Tier 3 stem behavior is
unchanged. Full details remain in `docs/history/V050_SCOPE_AND_LIMITS.md` and
the earlier history files.

## Mastering hierarchy

- Target: exactly −14.00 LUFS-I after verification.
- True-peak ceiling: no higher than −1.00 dBTP.
- The ceiling is not a peak target.
- Transparent flat gain remains preferred when it is strongest.
- Dynamics win when exact loudness would require excessive peak control.
- Actual PCM, Android AAC decode, SBC proxy and translations still need to
  pass the final release-QC path.

## What remains unverified here

The build environment did not install the APK on a Samsung Galaxy S23 Ultra or
perform physical speaker/headphone listening. Android AAC availability,
document-provider behavior, long foreground runs, cancellation, playback,
package opening, profile persistence and matched-level listening must be
exercised on the phone.
