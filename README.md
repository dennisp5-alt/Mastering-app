# Dennis AI Music Studio — Android-only experimental prototype (v0.2.1)

An **experimental, entirely offline** Android app designed for Samsung Galaxy S23 Ultra. It imports **PCM WAV** music that you have training rights to, extracts approximate dominant pitch-class patterns, trains a small neural next-note predictor **on the phone**, and synthesises a 3–5 minute instrumental WAV using a learned pitch tendency and repeating, section-aware melodic phrases.

**It does not yet generate sung vocals, reuse the timbre or voice from training audio, or rival Suno's studio-quality output.** Rendered instruments are intentionally simple synthesised sounds. It does not use ACE-Step or download models. The core goal is to validate *local learning*, *A/B evaluation* and *long-form generation*, rather than pretend a foundation model has been built.

## How to use
1. Install experimental APK, open it, choose **Import & learn from WAV** and select a PCM WAV music recording using the system file picker. Supports 16/24/32-bit integer PCM and 32-bit IEEE float PCM with standard RIFF WAVE headers. If importing fails, first convert compressed audio to 16/24-bit PCM WAV.
2. Add more songs one by one (6–75 representative recordings is an experimental target, not a hard requirement). The small neural model is retrained from the extracted features, with imported sequences kept privately in the app's local storage.
3. Select a style and duration, then **Compose 3–5 min WAV**. Keep the seed fixed and generate with the **Use learned music model** checkbox enabled, save the result, then disable it and generate/save the untrained control. Press **NEW COMPOSITION SEED** for another pair. Wait for completion and use **Play** or **Save WAV**.
4. Compare learned and untrained tracks using the same composition seed (different output filenames), and rate whether musical changes are useful. This is a small symbolic learning test, not production audio AI.
5. Use **BACK UP MY MUSIC DNA** to export your training features to a .dms file. Keep backups outside the app; **RESTORE MUSIC DNA BACKUP** can transfer them to later experimental installs. Restore replaces the experimental app's feature library, not the source WAVs.

### Installing alongside v0.1
The v0.1 APK was built with a temporary debug signing key and has no library export. Because CI debug signing keys can change, v0.2.1 deliberately uses the separate Android application ID `com.dennis.aimusic.experimental`; both apps can coexist. Your v0.1 10-track library stays in v0.1, but you will need to re-import those same source WAVs into v0.2.1. **Do not uninstall v0.1 to install this version.** The new backup facility prevents repeating the issue for future experimental models.

## Limitations
- Input features are coarse; pitch classes from mixed mastered audio may be dominated by bass/harmony rather than the sung melody. Does not isolate vocals or infer full harmonic structures. Tempo is set in the interface rather than estimated from audio.
- A tiny network learns statistical *pitch-class transitions*, **not waveform generation** or comprehensive musical style. Key estimation and chord progressions are rough heuristics.
- The app does not claim to clone a performer, generate lyrics, create professional vocals, or achieve studio-ready sound.
- Compositions are generated using custom Java synthesis, with standard-length exports that can take time. Output is 22.05 kHz 16-bit stereo WAV to reduce CPU, memory and storage load during the initial experiment.
- Never import works without explicit rights for AI training. Files and trained data stay offline. Uninstalling the app deletes locally stored profiles.

## Architecture
Pure Java analysis/training/rendering: `WavAnalyzer`, `TinyMusicBrain`, `SongComposer`, `SongRenderer`; Android `MainActivity` and `LibraryStore`. No Internet permission, tracking, remote service or external training library required. Existing v0.1 music_dna_v1.bin features are automatically reused. Training and WAV render are off the UI thread; exported files are transferred using Android's document picker.

## Build
Android Gradle Plugin 8.9.2, Gradle 8.11.1, JDK 17, compile SDK 35, min SDK 29, target SDK 34. GitHub Actions tests code and builds a debug APK under Actions artifacts on branch `dennis-ai-music-v0`.