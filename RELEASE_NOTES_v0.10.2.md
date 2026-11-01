# Dennis AI Mastering Lab v0.10.2

Install **DennisAiMasteringLab-v0.10.2.apk** over the existing AI Mastering Lab. The original package and signing identity are retained; no uninstall is needed. Requires Android 10 or later.

## What improves

- **Protection within the song:** candidates and the final master are checked for local losses in centre presence, transient impact, stereo width, section energy and ending tails. A track-wide average can no longer hide these measured local losses.
- **One gentler correction:** after an optional core correction wins, try it at 75% strength. Keep the gentler version only if it preserves measured benefit, reduces processing and passes all preservation and limiter gates. Otherwise retain the original winner. Technical repair maps and release safety stay at their verified strength.
- **Better listening comparison:** switch A/B at the same song position with matched loudness; pause, seek, listen in mono or use randomized blind labels. Tap a radio-warning timestamp to hear that passage with two seconds of lead-in. Listening settings do not change exported audio.

The natural v0.7 baseline, offline operation, approximately -14 LUFS-I target and at most -1 dBTP remain. Already-good masters can pass unchanged. No fixed catalogue EQ or automatic extra compression is added.

## Your supplied master

`LastRunDownTheRange_Master_24bit-48kHz.wav` passed the full computer pipeline unchanged, with an exact input/output hash match: **-14.0439 LUFS-I / -1.0933 dBTP**. The new passage checks passed too.

Its existing mono warnings remain around 3:54–3:55, 3:58–3:59 and 4:04–4:06, plus the ending transient near 4:29.7. These are listening prompts, not proof of audible damage. The radio report remains diagnostic and does not automatically change an intentional ending or certify every broadcaster's delivery requirements.

## First phone check

1. Install the APK over your current version and retry that same WAV.
2. Leave Preference Memory and Album Consistency off for neutral mastering.
3. When an alternative is available, switch A/B through a verse and chorus, then try mono and a warning timestamp.

The signed build and computer regressions passed, including real-file preservation, deliberate local-damage detection, aligned A/B, gentler-selection safeguards, limiting, repairs, cancellation and packaging. **Installation, native phone playback, Android AAC and screen-off operation still need your phone check.** AAC may require a small flat level reduction for decoded peak safety. These checks cannot certify human-perceived studio quality.

Outputs remain 24-bit/48 kHz WAV, reports, checksums and optional device AAC. MP3, 16-bit/44.1 kHz export, shortened radio arrangements and lyric edits are not added in this update.

The source backup includes the project, regression evidence and private signing key needed for compatible updates. Keep it private.
