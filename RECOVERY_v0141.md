# v0.14.1 Candidate Pipeline Repair recovery checkpoint

Base: v0.14.0 Sonic Finish checkpoint.
Package: `com.dennis.aimasteringlab`.
Version code/name: 20 / 0.14.1.

## Changes

- Core candidate shortlist: transparent control, conservative corrective, Sonic Finish, plus album candidate only when relevant.
- Stereo and advanced families: at most two candidates each (combined + strongest evidence-specific module).
- Quality family retains limiter/crest when justified and both vocal-artifact strengths when vocal-artifact evidence exists; this avoids pruning the stronger path that won the v0.14 Ribbon of Black run.
- Specialist render promotion floors are checked before the expensive whole-song feature scan when the later promotion gate would be mathematically impossible to clear.
- Exact transparent source controls reuse the original source feature map.
- Automatic gentler post-winner re-render is disabled.
- Studio progress is range-mapped across high-level phases and candidate families. Candidate messages identify family/candidate/strategy/phase and no user-facing v0.7 progress wording remains.
- Studio base finalization uses `FullAiReleaseQc.runStudioPcmOnly`: selected PCM winner copy/measurement, passage preservation and final hash/format verification only. AAC/SBC codec fallback, translation-device composites and broadcaster/radio simulation remain available to Classic/AI Lab but are not run by Studio.
- Studio delivery creates only the 24-bit/48 kHz master and verified 16-bit/44.1 kHz derivative. MP3 and per-track release ZIP generation are bypassed in Studio.
- Studio finishing radio/broadcaster simulation is skipped in WAV-only delivery; A/B aligned source and album/reference profile remain available.
- QC/recipe/report data remain internal for diagnostics and project recovery.
- Delivery no-op wording now distinguishes final delivery gain/limiting from the upstream mastering decision.

## Validation performed in this checkpoint

- Full Java SDK-less parse sweep: zero syntax diagnostics (`; expected`, illegal start, reached-end, not-a-statement, parenthesis/class-structure failures all zero). Android and `org.json` unresolved symbols are expected without the Android SDK/runtime stubs.
- Modified core files `FullAiReleaseQc.java` and `FullAiMasteringEngine.java` produced zero javac diagnostics in the SDK-less sweep; Studio/Release UI diagnostics are limited to expected missing Android/`org.json` types.
- Static policy assertions cover versioning, candidate shortlisting, bounded specialist families, pre-scan promotion floors, exact-control feature reuse, range-mapped progress, Studio PCM-only base QC, two-WAV Studio UI, MP3/ZIP/QC-save removal, WAV-only derivative generation, skipped Studio broadcaster simulation, and clarified delivery wording.

## Build

Use the GitHub Actions workflow prepared for `DennisAiMasteringLab-v0.14.1-GitHubBuildSource.zip`. The standard GitHub connector available in chat is read-only for repository contents, so the sanitized source ZIP and workflow must be uploaded/committed by the repository owner before Actions can build the test APK.

The public build source excludes `test-signing.jks`. The test workflow uses a separate application ID, `com.dennis.aimasteringlab.test141`, and GitHub's normal debug signing. This avoids relying on the ephemeral debug certificate from the earlier v0.14 TEST build and allows v0.14.1 to install alongside it.


## Real-file regression checkpoint

The actual v0.14 Ribbon of Black 24/48 master completed the v0.14.1 Studio master path with monotonic progress. On the host it selected `vocal_artifact_intelligence` in about 45.4 seconds, changed the source PCM hash, retained approximately −14.000 LUFS-I / −1.374 dBTP / 4.367 LU LRA, and recorded 5/5 eligible artifact targets improved with none worsened. WAV-only finishing completed in about 22.3 host seconds and created only the 24/48 and 16/44.1 WAV outputs. These are host regression timings, not phone performance guarantees.
