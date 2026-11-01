# Dennis AI Mastering Lab v0.12.0

Install **DennisAiMasteringLab-v0.12.0.apk** over your existing AI Mastering Lab. Android 10 or later; original package and update-signing identity retained. No uninstall is needed.

All four additions are included:

- **Stronger musical-context checks** help reject ambiguous repeated drum/instrument events before targeted vocal cleanup.
- **Frequency-tracking dynamic EQ** follows eligible local excesses, with small cut-only edits, gentle/full comparison and passage protection.
- **Four-times oversampled limiting** competes with the existing limiter profiles. It is selected only when the measured comparison and preservation gates permit it.
- **Three release formats:** 24-bit/48 kHz WAV, 16-bit/44.1 kHz WAV and offline 320 kbps CBR MP3, with individual save buttons and a combined ZIP. MP3 is decoded and checked before delivery. Any extra codec headroom uses flat gain on the derivative; it cannot alter the 24-bit master.

Targets remain approximately **−14 LUFS-I and at most −1 dBTP**. Codec-safe derivatives may be slightly quieter; the report gives their actual measurements. Already-good masters can pass unchanged. The v0.7 result remains available for comparison without permanent priority.

For bulk testing, process songs one at a time with Preference Memory and Album Consistency off, compare at matched loudness, and save each release package. This version does not add a multi-file queue, lyric censorship or shortened arrangements. Existing timestamped radio/mono/ending checks remain.

The engineering tests check measured improvement, false-positive rejection, alignment, dynamics, source preservation, encoded formats and failure recovery. They do not establish listening quality. Installation, native phone playback, Android AAC and screen-off operation still need a phone check.

The full computer regression suite passed. Your Claude master remained byte-for-byte unchanged; its MP3 needed 0.33 dB extra headroom. The Corner Stage completed with all three formats; its master reached −14.00 LUFS-I / −3.368 dBTP, and all four selected cleanup events improved under the measured comparison.

See TEST_RESULTS.md in the source backup for the exact validation results. The backup includes complete sources, tests, LGPL codec sources/license and the private key needed for future compatible updates; keep it private.
