# Dennis AI Mastering Lab v0.10.1

Install **DennisAiMasteringLab-v0.10.1.apk** over the existing AI Mastering Lab. The package and signing certificate are retained; no uninstall is needed. Requires Android 10 or later.

This release fixes the failure on your Claude-mastered WAV and adds radio/release preparation while retaining the natural v0.7 sound.

## Changes

- A peak-safe 24-bit/48 kHz stereo master within 0.10 LU of -14 can pass unchanged, with original bytes verified. The report identifies preservation instead of claiming exact -14.000 loudness.
- Safe flat gain between the former hidden -1.1 ceiling and the actual -1 dBTP delivery ceiling now works. Actual output peaks remain independently checked.
- Whole-song final checks identify mono-loss sections, opening/ending boundaries and unusual late transients, with timestamps and input provenance.
- The app shows the preparation result and includes a readable Radio Preparation report in the release ZIP alongside QC and checksums.
- A genuine transparent-baseline failure now includes the underlying reason.

## Radio preparation and sound

Existing measured tonal corrections, event repairs, dynamics control and sparse limiting remain, targeting approximately -14 LUFS-I and at most -1 dBTP. Each correction must justify changing the v0.7 reference. New final checks identify where listening is needed; they do not automatically alter an intentional ending or narrow an arrangement.

The fixed catalogue EQ, compulsory filtering and LRA-only compression from the Claude recipe were not adopted. Station specifications differ; this prepares a master for release and audition rather than certifying every broadcast format.

## Your WAV

The full LastRunDownTheRange master completed the host pipeline with identical input/output SHA-256: **no gain, EQ, compression or extra dither**. App measurements: -14.0439 LUFS-I and -1.0933 dBTP.

Audition mono at 3:54–3:55, 3:58–3:59 and 4:04–4:06, and check the final transient near 4:29.7. These findings were already in the input; no cause or audible defect is assumed.

Safe-gain reproduction, preservation safeguards, all limiter timing profiles, sparse limiting, repair, invalid-source rejection, optional-candidate recovery, cancellation, all four v0.7 advanced modes, hum repair and packaging passed host checks.

**Phone checks remain:** installation, playback, screen-off/background behavior and Android AAC. AAC may need small flat attenuation for decoded peak safety. Host tests do not certify perceptual mastering quality.

Use a 48 kHz stereo WAV and Create Full AI Master. Leave Preference Memory and Album Consistency off for neutral operation. Outputs remain 24-bit/48 kHz WAV, reports and optional device AAC; MP3 and 16-bit/44.1 kHz export are not added in this patch.

The source backup includes the project, tests, validation evidence and private update-signing key. Keep that backup private for future compatible updates.
