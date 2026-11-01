# Dennis AI Mastering Lab 0.14.3 — Broad Mastering Requirement bulk-test branch

Offline Android mastering and review workspace. Package `com.dennis.aimasteringlab`, version code 22, Android 10+, target API 34. The branch retains the existing update certificate and app data and adds no Internet permission. v0.13.1 remains the rollback/source reference.

**Broad Mastering Requirement fixes the raw-track hierarchy:** delivery compliance near −14 LUFS still does not mean mastering is complete. When reliable whole-song analysis finds tonal/dynamic/stereo mastering demand, the engine must establish and retain a safe broad master before narrow vocal/artifact/technical repairs are allowed to compete for the final result. A tiny local repair cannot stand in for mastering the whole raw song. High-confidence prior Dennis masters still receive second-pass protection, and Studio still publishes only the two requested WAV formats. See `RELEASE_NOTES_v0.14.3.md` and `RECOVERY_v0143.md`.

## Start a bulk test

1. Open **Import WAVs — multi-select**. Select up to 40 stereo 48 kHz WAVs per import. The app copies and verifies each source into its own saved project.
2. Tap **Master queued / resume**. Songs run sequentially. A failed song does not prevent later songs from completing.
3. Open a completed project and **Review / looped A–B**. A is the original, aligned to the edited duration; B is the finished master. Playback is loudness-matched. Mono, delta audition, loops and problem/edit markers are available.
4. Save the two release WAVs: **24-bit/48 kHz WAV** and **16-bit/44.1 kHz WAV**. Studio no longer generates a per-track MP3 or release ZIP.
5. QC and recipe data remain stored inside the project for diagnostics/recovery. When useful, send the original/master audio and internal QC from a project backup for review. Changes made after the last render are marked pending; render again to publish them.

The previous single-song/stem workflow remains available through **Classic mastering + optional stems**. Existing preference and album data remain in that screen. The new queue starts from neutral mastering; it does not silently apply learned preferences.

## All seven additions

1. **Mastering review:** aligned level-matched original/master comparison, sample-aligned seeking, click-softened preview loops, mono, half-level signed difference audition, timestamped warnings and edit markers. Individual active passage edits can be compared with an otherwise equivalent render in which that edit is bypassed. Preview controls never alter exports.
2. **Passage edits:** editable start/end times, ±3 dB linked gain rides, optional stereo/Mid/Side EQ and cut-only dynamic EQ. Smooth local envelopes, continuous filter state, 1.5 dB dynamic-cut cap and shared nominal 4 dB overlapping EQ budget. Each render starts from the saved base, never the preceding edited output. Twenty recipe undo states are retained.
3. **Selective bass/low-mid control:** local 70/120/220/360 Hz evidence, duration/repetition/side-energy checks and bounded centre-band dynamic correction. Every automatic event requires independently measured local benefit and body preservation. Failed automatic corrections are disabled while manual edits remain. These are stereo signal heuristics, not isolated kick/bass/vocal stems.
4. **Reference comparison:** up to four local 48 kHz stereo WAVs; independent song/reference start positions; 5–60 second passage comparisons at matched passage loudness. Broad stereo energy includes side information. Small reference EQ suggestions begin bypassed; enable only those useful to the song. References do not force a catalogue curve.
5. **Finishing and radio edits:** sample-counted explicit intro/outro trims, editable fades, optional conditional 5 ms opening / 10 ms ending boundary repair. End 0 retains the full song. No automatic lyric censorship, internal arrangement splicing or arbitrary song shortening. Fades preserve the chosen duration.
6. **Saved projects and queue:** source copies, verified base masters, recipes, outputs, hashes and stage state on disk. Atomic JSON writes with previous-state recovery. Completed songs remain complete; paused work resumes from verified base/export boundaries. An interrupted stage is rendered again. Foreground notifications, pause, retry failed items and project backup ZIPs are available. Backup ZIPs are recovery archives; in-app archive import is not included.
7. **Album and delivery:** actual finished-song loudness/broad-tone comparison, selectable membership, track order and 0–10 s inter-song spacing. Export a continuous 24/48 listening sequence and report while retaining individual PCM. Per-song title/artist/album/track/ISRC and WAV INFO metadata. Editable −30 to −10 LUFS targets and −6 to −1 dBTP ceilings; presets provide starting points. Every derivative honors the chosen peak ceiling. A requested louder target may be missed when bounded limiting would harm dynamics; the report states this.

## Storage and recovery

Sources and finished work are private app files until exported. Keep sufficient phone storage; the initial mastering stage reserves an estimate of 24 source-file sizes plus 100 MB, and a finishing rerender reserves 8 sizes plus 100 MB. The core uses streamed audio. Completed base-stage temporary candidates are removed. Saved projects and prior render folders remain until the user deletes the project. Delete only after exporting anything required.

A process interruption does not erase completed songs. Reopen the app, tap Refresh, then Master queued / resume. Android force-stop does not automatically restart a job. Source import interrupted before its copy completes may require re-importing that file. Source/base hash mismatches stop affected work.

Project backup ZIPs contain the copied source, references, recipes and saved output files. They are separate from this development source backup.

## Validation and limits

v0.14.1 successfully compiled through GitHub Actions and was phone-tested; v0.13.1 remains the last retained rollback/source baseline. v0.14.3 Broad Mastering Requirement host validation is recorded in `RECOVERY_v0143.md`; the fresh Android APK compile remains the next GitHub Actions gate. The v0.13.x DSP results below are retained as historical validation.

The host suite passed editing, null/bypass, exact trims, fade boundaries, mid/side preservation, positive/negative bass controls, JSON recovery, source corruption rejection, queue failure isolation/pause/resume, references, metadata, custom target/peak delivery, decoded MP3, packages and albums. Existing v0.12 audio/export, optional-file recovery, cancellation and quality-counterexample tests passed. See `docs/validation-v0130`.

Pilot Light2 completed the full mastering/project/reopen/resume/finishing path: 202.000 s, −14.000000 LUFS-I, −1.719918 dBTP. No automatic bass edit was selected. Its inherited nonzero ending received a 10 ms fade. Independent FFmpeg checks verified both release derivatives: 8,908,200 decoded frames, approximately −14.01 LUFS and −1.73 dBTP.

Phone installation, touch navigation, AudioTrack playback, notification/background behavior and human listening still need device testing. The host tests do not certify subjective studio quality. Final Studio PCM QC is current; codec checks in retained historical/base reports are separate from the two-WAV Studio delivery path. Device simulations remain diagnostic. The album sequence is not a DDP image. Broadcast presets do not certify a particular station's specification.

## Rebuild

Keep `test-signing.jks` private; it is the update-signing key. Complete LGPL codec source/license and build instructions are included in this archive; vendor codec sources remain unchanged.

With Java 17, Python 3/NumPy, Android platform 34 and build-tools 34.0.0:

```
DAML_ANDROID_SDK=/path/to/sdk bash tools/build_apk.sh
DAML_ANDROID_SDK=/path/to/sdk python tools/run_v0131_tests.py /path/to/PilotLight2.wav
```

Omit the WAV for fixtures only. `tools/checkpoint.py /absolute/path/to/source.zip` produces a full source/recovery archive and checksum manifest, excluding SDKs, build products and song audio. v0.14.2 branch status is in `RECOVERY_v0142.md`.
## v0.14.5 Adaptive Broad Mastering

When reliable whole-song analysis says a raw source still needs broad mastering but the normal bounded candidates cannot safely establish a broad anchor, v0.14.5 tries exactly three source-shaped fallback rungs. Dynamics-led material gets progressively stronger but transient-safe bus control with restrained EQ; tonal-led material gets progressively stronger broad EQ with conservative dynamics; mixed material scales both. Hard safety and normal-track preservation gates remain unchanged. Only unresolved dynamics-led adaptive fallback may use the bounded demand-aware preservation envelope; transparent remains the final fallback if no rung proves a safe whole-song improvement.



### v0.14.5 Adaptive Dynamics Preservation
Only unresolved dynamics-led adaptive fallback receives a bounded demand-aware preservation envelope. Normal mastering candidates and all hard safety gates remain unchanged.
