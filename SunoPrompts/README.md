# Suno Prompts — Android v0.1.0

Independent offline-library Android application for researching artists and recordings, then writing original Suno music-production prompts.

## Working features
- Artist/song lookup via MusicBrainz and English Wikipedia, with linked references.
- Local SQLite folders by artist, one saved prompt per song.
- Editable style prompt (1,000-character cap), Exclude prompt and research/production notes.
- Save, copy, share and delete with a dark native interface.
- Built-in style-aware starter templates when no AI account is configured.
- Optional AI generation via a user-provided OpenAI API key, encrypted by Android Keystore AES/GCM.
- Spotify not included.

## Usage
1. Open Suno Prompts on Android and tap New song prompt.
2. Enter the artist, song, optional creative production direction.
3. Tap Research + create starter prompt; or optionally visit AI Settings, enter your API key, then Generate AI Prompt.
4. Open artist folders to edit or copy prompts into Suno.

OpenAI API usage is separately billed to the user's API account. ChatGPT Plus does not include developer API credits. No shared API key is bundled.

## Limits
Research retrieves textual background, not actual audio; the app cannot verify exact BPM, key, mic chains, stereo channels, or playing techniques from an audio master. It flags missing sources instead of pretending to have heard a recording. Output is original production guidance, not a recreation of melodies or lyrics. Built-in templates are visibly labelled Starter. Saved prompts work offline; research/AI require internet.

One saved prompt per artist/song. Android application ID: com.dennis.sunoprompts; minSdk 26; targetSdk 34.

## Build
Run 'gradle :app:assembleDebug' inside SunoPrompts with JDK17, Gradle 8.11.1, SDK35; or use the independent GitHub Actions workflow suno-prompts.yml. It publishes the raw SunoPrompts-v0.1.0-debug.apk to GitHub Releases.
