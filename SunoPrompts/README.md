# Suno Prompts Studio v0.5.1 — Imported Lyrics Offline Check

## Fix prompted by on-device testing
- The user's imported *Wild Boys / Count Us Through* draft previously showed 18/21 short chorus lines (median ~3 syllables) but incorrectly reported “No obvious text-density warning.” This happened because imported songs lacked a recorded production style and chant detection only ran for recognised dance-rock prompts.
- Now the **offline text analysis** reports a *possible chant-only chorus* when 75%+ of chorus lines are <=5 syllables with no extended melodic answer, **even if genre is absent**. It explains that repetitive shouts may be musically intentional in some genres and is advisory rather than a verdict. When a production profile says dance-rock, the warning becomes more specific.
- **Set musical reference for offline check**, available inside any lyric draft: enter a musical genre/era, reference recording, optional detailed style prompt, or select **any saved production prompt** from an existing artist folder. Saving updates the draft's songwriter brief, not its words. All of this works offline and uses no ChatGPT plan allowance.
- The checker also explicitly tells users when it lacks a musical profile, and suggests adding one.
- Regression tests verify (a) the old 18/21 short-line imported chorus is flagged without genre, (b) the AI-repaired chorus with short calls plus longer responses isn't falsely marked chant-only.

## Safe upgrading from Studio v0.5.0
GitHub still builds with an ephemeral debug signing certificate. Before downloading or uninstalling ANY Studio version, use **Back up my full library** on your current working version, save the JSON to Downloads and verify the file exists. If Android refuses the upgrade due to signing, and only after checking the backup, uninstall **Suno Prompts Studio**, install v0.5.1 and **Restore a saved library backup**. Do not uninstall the separate original Suno Prompts app. Your ChatGPT connection may require signing in again.

---
# Suno Prompts Studio v0.5.0 — Lyrics-first Production

## What changed
- **Stronger musicality in AI Lyrics Studio:** for aggressive dance-rock and similar genres, songwriting no longer equates short syllable counts with musical effectiveness. Choruses alternate short percussive calls, longer melodic answers, sustained vowels, backing-vocal responses and dynamic contrast. The AI silently reviews lyrical singability, chorus lift and narrative preservation before returning a draft.
- **Chant-only offline warning:** the syllable-based advisory detects a chorus where nearly every line is a 1–5 syllable shout with no melodic answer, as in the earlier Count Us Through example. It is a text heuristic, not a melody or audio measurement.
- **Lyrics → Studio Production:** open ANY saved lyric draft to generate an original Suno v6 production style from the actual text and the user's genre/era/vocal instructions. This creates a 1000-character style, Exclude list and arrangement notes. No lyric words are changed.
- **One-tap Generate Lyrics + Matching Suno Sound:** explicitly uses two ChatGPT-plan AI requests in sequence. If the second call fails, the finished lyric draft is still saved.
- Every new lyrics-first sound is saved as a version attached to its lyrics draft, not under a colliding artist/song production prompt. Edit/Copy/Share/Delete independently; warn if original lyrics changed afterward.
- SQLite upgrade from 2 → 3 only adds a `lyric_productions` table; existing artist folders, prompts and lyrics remain intact. Full-library JSON backup format 2 includes production versions, while Restore still accepts format 1 from Studio 0.4.2.
- App package remains `com.dennis.sunoprompts.studio`. No private account credentials are stored in library backups.

## Safe installation — READ THIS FIRST
Studio v0.4.2 was built with a temporary CI debug signing certificate. The v0.5.0 APK is likely signed differently and may trigger "App not installed" if installed over 0.4.2. **Do not uninstall before exporting your data.**

1. In your working v0.4.2 **Suno Prompts Studio** app, tap **Back up my full library** and save `SunoPromptsStudio-Library.json` in Downloads or Documents. Verify the JSON exists using the phone's Files app.
2. Attempt to install 0.5.0 over 0.4.2 if you want, but do not delete anything on failure.
3. If Android reports a conflicting signature, and the backup is safely saved, uninstall **Suno Prompts Studio** (the new Studio package only, not the separate original Suno Prompts app), then install v0.5.0.
4. Open Studio v0.5.0 → **Restore a saved library backup** → select your JSON → review counts → Import.
5. ChatGPT sign-in credentials do not transfer; reconnect using the app's account screen if necessary.

The long-term signing fix is a persistent private Android release signing key held in GitHub Actions encrypted secrets. Until configured, debug builds may continue to be incompatible for in-place upgrades. Never commit a private signing key to the repo.

## New feature test
1. Open a saved lyrics draft such as the original factory escape / Count Us Through song.
2. Tap **Check phrasing and hook density (offline)**. It should identify a flat chorus consisting almost entirely of very short shouts.
3. Tap **AI Rhythm + Hook Repair**, choosing aggressive 1984 dance-rock and emphasizing melodic phrase lift. The original lyrics must remain unchanged; the repaired version is a separate saved draft.
4. In the saved draft, tap **AI • Create production from these lyrics**. Supply genre, production era and instrument/vocal direction.
5. Review the linked 1000-character style, Exclude prompt and producer notes. Copy to Suno. Generate a new draft from Home using **Generate lyrics + matching Suno sound (2 AI requests)**.

Note: Live ChatGPT account connection and audio results must be tested on the user's phone. The successful CI tests cannot prove musical quality or sign-in account eligibility.

---

# Suno Prompts Studio v0.4.2 — Safe Side-by-Side Install

This release uses Android package **com.dennis.sunoprompts.studio**, independent from the old **com.dennis.sunoprompts**. Both can exist on the same Samsung phone simultaneously. This is a workaround for old debug builds signed with different temporary keys. The app data is NOT automatically shared across packages, because Android isolates apps' private files.

## Step-by-step transfer without a computer
1. Install the new Suno Prompts Studio APK. **Do not uninstall your previous Suno Prompts app.**
2. In the old Suno Prompts app, open an artist/song production prompt.
3. Tap **Share prompt text** and choose **Suno Prompts Studio** from the Android Share menu.
4. The Studio app previews the import and asks permission to save it under that artist's folder.
5. Repeat for other prompts. For old lyric drafts choose **Share lyrics**; these import into a separate **Imported Lyrics** folder. You can edit, copy and rename text manually, but lyric metadata (original artist folder) isn't available in the old shared text.

## Protect the Studio library against future debug-signature changes
- Studio home: **Back up my full library**. Choose a folder in Android Files and save the JSON.
- In a fresh installation: **Restore a saved library backup**; select the JSON. The preview shows item counts and asks before importing.
- Restore uses a SQLite transaction, skips production prompts already saved with the same artist/song, and avoids duplicate identical lyric drafts.
- The JSON contains only library content: prompts, lyrics, notes, reference links. It contains **no passwords, ChatGPT credentials or API tokens**.
- Keep the backup file before uninstalling either app.
- Old v0.4.1 and earlier versions have no full backup function; use Share as described above or ADB for expert database extraction.

## Features preserved
Song-specific production prompts, free curated fallback, ChatGPT account connection (subject to eligibility and network connectivity), AI Lyrics Studio, rhythm/hook repair and original Suno-ready song drafts.

## Build
GitHub workflow runs rhythm-coach/share-import JVM tests then builds the APK. It verifies the package is **com.dennis.sunoprompts.studio**, preventing accidental replacement of the original app. Output is `SunoPrompts-Studio-v0.4.2-debug.apk`.

## Future signing
A permanent signing key stored as GitHub Actions encrypted secrets is still needed for reliable in-place updates of the Studio package. Future builds using different temporary debug keys will be unable to update this APK, but its JSON library export/restore provides a supported recovery path. Do not put a private signing key in a public source repository.
---
# Suno Prompts v0.4.1 — Music-Matched Lyrics

This update improves the existing AI Lyrics Studio, keeping all existing production prompts, lyrics folders and draft versions.

## Production-specific lyrics
- Choose a reference song's **saved production DNA** even if your new original song has a different title.
- Set an independent reference recording name (for musical character only), vocal rhythmic attack and breathing instructions. The reference recording's existing hook or copyrighted lyrics must not be copied.
- AI songwriting directions vary with the musical production: tightly stressed, chant-like new-wave dance-rock; natural story-led country; groove-led dance-pop; expressive ballad phrasing.
- An optional **offline text rhythm check** flags high syllable density, verse/chorus imbalance and missing sections. This is a rough English lyric-density heuristic, **not real music/audio measurement**.
- **AI Rhythm + Hook Repair** revises an existing user-owned draft against a selected production style; it makes a NEW draft and never overwrites the previous version.
- Users must save current editor edits before requesting repair so nothing can be lost.
- A saved song prompt's **Write original lyrics for this sound** shortcut preloads its musical production description.

## Test case
A new original escape-and-loyalty story placed in an aggressive early-1980s dance-rock production should produce clipped verses, shorter urgent call/response chorus lines and a fresh hook, rather than generic country-narrative singalong lyrics. This is an arrangement-matching target; it is **not** a request to reproduce any Duran Duran song.

## Testing
The Android GitHub Actions workflow executes `testDebugUnitTest` for the rhythm coach and compiles the complete app before publishing its release APK.

## Installation and account access
Version 0.4.1 uses the same app ID and SQLite database (schema v2). ChatGPT account connection still depends on successful OpenAI OAuth sign-in and eligible plan usage. GitHub debug builds may use differing signatures; **do not uninstall a version with valuable saved prompts** unless you've copied them elsewhere first.

---

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
