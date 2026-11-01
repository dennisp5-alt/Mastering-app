# Dennis AI Mastering Lab v0.11.0

Install **DennisAiMasteringLab-v0.11.0.apk** over your existing AI Mastering Lab. The original package and signing identity are retained; no uninstall is needed. Requires Android 10 or later.

## What changes

- **Quality-first selection:** v0.7 remains an A/B comparison, but better measured cleanup or limiter results can replace it. A raw tonal score alone no longer blocks that choice.
- **Targeted cleanup:** gentle and full alternatives address short, high-confidence sibilance, plosive and harshness indications. Their finished audio must show a target improvement while preserving the surrounding music.
- **Smoother limiting:** a new ramped-attack profile competes with the existing profiles using measured peak control, dynamics and limiter burden. Limiting is bypassed when safe flat gain is sufficient.
- Existing transparent-first operation, local passage protection, technical repairs, release/radio checks and loudness-matched A/B remain.

Targets stay approximately **-14 LUFS-I and at most -1 dBTP**, fully offline. Already-good masters can still pass unchanged.

## Validation

The complete computer regression suite passed. Your Claude master passed byte-for-byte unchanged at -14.044 LUFS-I / -1.093 dBTP. T-Bar Girls and The Corner Stage both completed at -14.00 LUFS with safe peaks; independent FFmpeg checks agreed. The Corner Stage selected the new cleanup candidate after all 11 detected target events improved under the comparison test and preservation checks passed. T-Bar Girls retained its established tonal correction.

These are engineering measurements. They do not establish human-perceived studio quality or identify every artifact correctly.

## First phone check

1. Install the update without uninstalling the existing app.
2. Leave Preference Memory and Album Consistency off for neutral mastering.
3. Process a familiar 48 kHz stereo WAV, then compare a verse, chorus and ending with matched-level A/B and mono playback.

Installation, native playback, Android AAC and screen-off operation still need a phone check. AAC may require a small flat level reduction for decoded peak safety.

Outputs remain 24-bit/48 kHz WAV, QC/preparation reports, checksums and optional device AAC. MP3, 16-bit/44.1 kHz export, shortened radio arrangements and lyric edits are not added. Radio checks do not certify every station's delivery requirements.

The source backup includes tests and the private signing key for future compatible updates. Keep that backup private.
