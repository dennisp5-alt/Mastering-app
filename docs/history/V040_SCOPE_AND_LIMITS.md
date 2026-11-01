# v0.4 scope and limits

## Authoritative policy

The original stereo WAV is always authoritative. A raw stem sum is never
promoted or substituted for the mix. The app distinguishes whether optional
stems are safe for audio deltas, safe only for measurement guidance, or unsafe
for all automatic use.

## Three-tier trust model

### Tier 1 — production-stem audio delta

Tier 1 requires compatible WAV stems, complete CRC/decode, safe reconstruction
without a separation-derived classification or global polarity risk, secure
start/middle/end reconstruction timing and at least one stem with exact-zero
five-position local lag/drift at 5 ms resolution.

The only permitted construction is:

`authoritative mix + scale × (processed stem − original stem)`

### Tier 2 — analysis-only stem control

Tier 2 is available when reconstruction is incomplete or separation-derived
but global polarity is safe, an unambiguous explicit lead vocal exists, and
each admitted evidence stem passes five-position local timing/polarity checks.
The analysis tolerance is no more than ±5 ms local lag and 5 ms drift.

Stems may identify short de-essing or masking events. The Tier 2 renderer opens
only the authoritative stereo WAV. It does not open the stem archive, sum
stems, copy stem samples or form a processed-minus-original stem delta.

Tier 2 restrictions:

- phrase rides are disabled;
- de-essing acts only on the stereo mid channel from 4.8–11 kHz;
- masking acts only on short side-channel band events;
- de-essing evidence scale is 0.40 and cut is capped at 0.35 dB;
- masking evidence scale is 0.55 and each event is capped at 0.35 dB;
- combined masking is capped at 0.45 dB;
- total simultaneous nominal edit is capped at 0.55 dB;
- first 2 seconds and final `max(8 seconds, 4% of duration)` are protected.

The Tier 2 candidate must clear a score margin of at least 0.08 and a measured
objective gain of at least 0.015. Analytical evidence does not provide an
alternate promotion route.

### Tier 3 — rejected

Tier 3 covers unsafe paths/CRC/decode, unsupported format, global polarity
risk, insecure timing, material duration faults, absent/ambiguous lead role or
no locally secure evidence stem. The full stereo competition continues.

## Archive and role limits

The app accepts at most 512 ZIP entries, 64 WAV stems, 4 GB per entry and 8 GB
declared expansion. It rejects traversal, absolute, backslash, drive-letter,
duplicate and suspicious high-ratio paths before stem use. Every non-directory
entry is fully read for CRC verification; every WAV is decoded and measured.

Masking competitors require explicit eligible roles such as guitar, piano,
keys/organ, synth/pad, strings or brass. Vocals, drums, percussion, bass/sub,
clicks and ambiguous names remain excluded.

## Mastering and verification hierarchy

The target is exactly −14.00 LUFS-I and the ceiling is −1.00 dBTP. The ceiling
is not a peak target. Transparent gain is preferred. If exact loudness would
require more peak control than the song-derived allowance, dynamics win and the
honest shortfall is reported.

Every candidate must preserve sample rate, stereo channels, 24-bit output,
sample count, duration, continuity, phase/mono safety and song dynamics. It
must have zero clips and non-finite samples and no introduced exact-zero
interruption. Tier 1 and Tier 2 candidates also require protected-boundary
proof and source correlation of at least 0.995.

## What remains unverified here

The build environment did not perform physical speaker/headphone listening or
run the APK on a Samsung Galaxy S23 Ultra. Android AAC encode/decode, SBC proxy,
document-provider behaviour, long foreground runs, cancellation, playback,
package opening and matched-level listening must be exercised on the phone.
