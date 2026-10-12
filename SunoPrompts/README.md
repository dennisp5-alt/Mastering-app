# Suno Prompts v0.4.0 — Song Production & Lyrics Studio

Independent Android app, package `com.dennis.sunoprompts`, Android API 26+.

## New AI Lyrics Studio
- Home screen **AI Lyrics Studio** and **Write Original Lyrics for This Sound** inside any existing production prompt.
- Creates original lyrics for Suno v6 using the existing eligible Sign in with ChatGPT account connection. No separately purchased API credits.
- Songwriter controls: title, specific story and setting, desired chorus hook, genre/mood, vocal delivery, section structure, rhyme/syllabic pacing, unwanted themes and production-style prompt.
- Original finished song with [Verse 1], [Chorus], [Bridge], [Final Chorus], etc; exports via Copy/Share.
- Every generated version is saved as its own lyric draft. New generations never overwrite old lyrics.
- Lyrics are stored in a separate `lyrics` SQLite table, linked to a folder (reference artist or Original Songs); production prompt records are untouched.
- Edit/save a lyric draft, generate a new variation from the stored structured brief, or delete a single draft.
- Create and edit a manual blank draft without ChatGPT connectivity.
- Original songwriting rules forbid replicating existing songs, copying published lyrics or imitating an artist's unique lyric phrasing.

## Database upgrade (important)
SQLite schema updated from version 1 to 2. The onUpgrade migration adds `lyrics` and its index **without altering, dropping or deleting any existing production prompts**. Existing user data stays in the same app package.

## ChatGPT sign-in
Uses the official Sign in with ChatGPT local OAuth loopback + PKCE and public Responses API. Plan usage is subject to eligibility/limits. The previous network diagnosis and DNS recovery tools remain in place. This AI generation feature requires a successfully connected account; the APK build alone does not establish a live account connection.

## Build and install
JDK17, Gradle8.11.1, SDK35. Build: `gradle :app:assembleDebug`.
GitHub Actions workflow builds a raw `SunoPrompts-v0.4.0-debug.apk` file and publishes it as a GitHub release.

**Known install limitation:** GitHub's ephemeral debug signing keys can differ between builds. If Android refuses an in-place APK update, do not uninstall an existing app until important saved prompt text has been copied somewhere safe. There is no data-export feature in v0.3.1.

## Functional test
1. Confirm old production prompt folders still display after an in-place upgrade.
2. From Home → AI Lyrics Studio, input original title and a detailed story; generate with ChatGPT or start blank offline.
3. Verify section labels, finished lyrics, Copy Lyrics, Save Edits.
4. Generate another variation and verify both drafts remain under the same artist folder.
5. In any existing production prompt, tap **Write original lyrics for this sound** and check the style prompt is prefilled.
6. Test AI failure: existing production prompts and lyric drafts must remain unchanged.
