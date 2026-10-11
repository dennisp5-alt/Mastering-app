# Suno Prompts v0.2.0 — Free Production Reference Library

Android package `com.dennis.sunoprompts`; targetSdk 34; minimum Android 8.0.

## No credits required
**This build does not make OpenAI API requests and does not include API-key settings.** Legacy API-key storage from v0.1 is cleared on launch. No Spotify integration. Saved prompts are stored locally in SQLite and remain available offline.

## Features
- Artist folders, recording titles, editable style prompt (max 1,000 characters), separate Exclude, save/share/copy/delete.
- Curated, reviewed production briefs for **Lee Kernaghan / Boys from the Bush**, selected AC/DC, Slim Dusty, Dire Straits and Duran Duran tracks.
- Curated artist-level production directions for a growing set of musicians (Lee Kernaghan, Slim Dusty, Alan Jackson, AC/DC and others).
- Free online fallback using MusicBrainz and Wikipedia metadata when the artist isn't in the curated library. Genre-based guesses are explicitly labelled, not falsely passed off as song-specific audio analysis.
- Unprofiled artists receive an explicit warning rather than a fabricated musical description.
- One-tap **Replace with latest FREE production prompt** to update an old generic saved result while keeping its artist folder.

## Audio limitations
Neither MusicBrainz nor Wikipedia verifies each song's exact playing technique, tempo, key, equipment or stereo arrangement. Curated briefs are professional **interpretations for original songs**, not actual transcriptions of the reference audio. To automatically recognise an arbitrary track's sound, a trained model or a supported audio-analysis engine would be required; this is not included.

## Build
JDK17, Gradle 8.11.1, Android SDK 35. Run `gradle :app:assembleDebug` in this folder or use `.github/workflows/suno-prompts.yml`. The workflow validates the APK and publishes **SunoPrompts-v0.2.0-debug.apk** as a raw GitHub release asset.

## Testing
1. Install over v0.1.0 so previously saved artist folders are preserved.
2. Open the existing Lee Kernaghan / Boys from the Bush song, tap **Replace with latest FREE production prompt** and confirm.
3. Check that the prompt starts with **Early-1990s Australian country-rock bush anthem**, not **Song-inspired original production brief**.
4. Check AC/DC / Back in Black and another unprofiled artist to verify clear difference between curated and broader guidance.
