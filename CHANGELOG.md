# v0.14.4 — Adaptive Broad Mastering

- Added a bounded three-rung Adaptive Broad Mastering fallback for reliable raw-source broad demand that remains unresolved after normal candidate families.
- Ladder shape follows measured need: dynamics-led, tonal-led, or mixed.
- Adaptive candidates retain the existing clipping, true-peak, stereo/phase, LRA, passage-preservation, technical-score and objective guards.
- Selection chooses the lowest-processing rung within 0.030 technical-score points of the best qualifying adaptive result.
- Transparent fallback remains authoritative if all adaptive rungs fail.
- Added adaptive broad diagnostics to base/final Studio QC.
- Studio remains two-WAV delivery only.

# 0.14.3 — Broad Mastering Requirement

- Added a whole-song Broad Mastering Requirement so a raw source with measured tonal/dynamic demand must first establish a safe broad master before narrow repair candidates can become final.
- Broad demand is based on reliable whole-song EQ, bus-dynamics and stereo-plan evidence, not loudness compliance. A raw track near -14 LUFS can still require mastering.
- Added a retained broad-core anchor so candidate-cache cleanup cannot delete the only meaningful whole-song master merely because a near-copy has a slightly higher generic score.
- A narrow artifact/vocal/technical repair can no longer replace the verified broad anchor when its level-matched result is only narrow/sparse.
- Previous-master and lossy-source guardrails remain authoritative; broad remastering is never forced when source-history protection blocks it.
- Added Broad Mastering Requirement diagnostics to Base/Studio QC: required state, demand summary, anchor strategy, anchor delta, unresolved flag and whether the anchor was ultimately retained.
- Real raw-material regression: an excerpt from the earlier aligned source measured BROAD_MASTERING_REQUIRED with 1.000 analysis reliability, 0.467 dB broad EQ demand and 1.680 predicted objective gain.
- Synthetic retention regression proves a lower-score `sonic_finish` broad master is preserved beside a higher-score near-copy until final selection.
- Version code 22 / version 0.14.3.

---

# 0.14.2 — Meaningful Mastering Gate

- Added level-matched finished-PCM delta evidence against the transparent control.
- Non-transparent winners must show a meaningful broad sonic delta or independently proven narrow target-specific repair benefit.
- Near-copy candidates now roll back to transparent instead of being described as a sonic mastering winner.
- Increased Transient Intelligence module benefit threshold from 0.04 dB to 0.10 dB.
- Preserved transparent PCM until final selection so the gate compares actual rendered files.
- Added meaningful-change status/delta evidence to Base/Studio QC and clearer completed-project status text.
- Kept Studio delivery to the two requested WAV formats only.
- The Old Girl near-copy regression measures -50.83 dB level-matched residual and is blocked as a broad master; synthetic spectral tilt at -30.47 dB passes.
- Version code 21 / version 0.14.2.

---

# 0.14.1 — Candidate Pipeline Repair

- Bounded core competition to transparent, conservative, Sonic Finish, plus an album candidate only when relevant.
- Stereo and advanced specialist families now shortlist at most two candidates: combined plus the strongest evidence-specific module when multiple modules exist.
- Specialist renders that cannot meet their family promotion floor skip the expensive whole-song feature re-scan.
- Exact transparent source controls reuse the already-computed source feature map rather than learning the same features again.
- Removed the automatic post-selection gentler re-render.
- Studio mastering progress is range-mapped from source analysis through candidate competition and final PCM QC; candidate messages include family, number, strategy, and render/verification phase, and advanced progress no longer exposes v0.7 terminology.
- Studio output is simplified to two user-facing files: 24-bit/48 kHz WAV and 16-bit/44.1 kHz WAV. MP3, per-track release ZIP and QC-report export buttons are removed from Studio, while QC remains stored internally.
- Studio base finalization now uses PCM-only winner verification; AAC/SBC fallback, translation composites and broadcaster/radio simulations are skipped in the WAV-only Studio path, while Classic/AI Lab retain their broader QC path.
- Studio finishing skips broadcaster/radio simulation and directly verifies the two WAV outputs, aligned A/B source and album/reference profile.
- Clarified the delivery no-op message so it does not imply that mastering was bypassed.
- Version code 20 / version 0.14.1.

---

# 0.14.0 — Sonic Finish bulk-test branch

- Separated delivery compliance from mastering completion. A safe ~−14 LUFS source can still enter full sonic candidate analysis.
- Restricted the second-pass block to positive prior-Dennis source-history evidence; limiter density remains diagnostic rather than automatic proof of a previous Dennis master.
- Added a stronger `sonic_finish` candidate with bounded corrective EQ, targeted dynamic EQ and stereo-linked mastering compression.
- Broadened mastering-compression evidence and useful gain-reduction range while retaining hard peak, phase, LRA and passage-damage rejection.
- Reduced the historical scoring bias against EQ, compression and local dynamic correction; processing amount is no longer treated as a quality defect by itself.
- Reworked transparent rollback and specialist promotion thresholds around measured benefit rather than v0.7 character preservation.
- Relaxed only the soft musical-preservation margins; hard duration, non-finite sample, clipping, phase/correlation, true-peak and destructive-dynamics gates remain.
- Tightened the final gentler-challenger rule: a 75% version may replace the winner only when measured objective/technical/total score are effectively unchanged, rather than simply because it processes less.
- Version code 19 / version 0.14.0; package identity and retained update-signing certificate are unchanged.

---

# 0.13.1 — saved-source import repair

Fixed canonical versus aliased project paths. Added selected-stream/copy checksum comparison, persisted import identity before analysis, guarded recovery of completed old imports, original-error retention and specific source diagnostics. Existing mismatched checksums still fail. The seven features and DSP policies remain unchanged.

---

## 0.13.0 — Studio workspace and bulk testing

Completed all seven studio additions. See README.md, RECOVERY_v0130.md and docs/validation-v0130 for implementation, test results and current limits. The full host gate and independent real-song format verification passed. Final APK uses the retained update certificate.

---

# v0.12.0 — 2026-09-30

- Added independent 25 ms musical-context vetoes and per-event frequency tracks.
- Replaced eligible broad vocal cleanup with moving cut-only centre EQ; existing independent benefit and preservation gates remain.
- Added a four-times oversampled linked limiter competitor with aligned output and final PCM verification.
- Added verified 16/44.1 WAV and offline CBR320 MP3 derivatives, decoded MP3 metering, codec-only flat attenuation, individual save controls and ZIP/QC integration.
- One bounded retry recreates incomplete export staging; persistent export failures keep the verified master and visibly report incomplete delivery.
- Added timing/alias, moving-EQ, repeated-percussion, oversampled-render, format/cancellation and export-isolation regressions.

# Changelog

## 0.11.0 — 2026-09-29

- Removed permanent v0.7 sonic priority while retaining its verified comparison result and transparent-first path.
- Added independent finished-PCM target-event and outside-event evidence for gentle/full targeted cleanup.
- Tightened automatic event confidence/duration gates and second-pass intervention; retained musical preservation.
- Added a stereo-linked ramped limiter attack and four-profile render competition using measured stress and dynamics.
- Prevented later technical candidates from undoing accepted targeted cleanup.
- Updated quality decisions, limiter gain-step evidence, reports and A/B reference availability.
- Passed the complete host suite, unchanged Claude master and two real-song release runs with independent metering.
- Code 15 with the original update certificate. Device and human listening checks remain separate.

## 0.10.2 — 2026-09-26

- Added one-second passage preservation against the verified transparent baseline and final-file rechecking.
- Added one bounded 75%-strength challenger for winning optional core corrections, gated on equivalent benefit, lower cost, limiter burden and local/global preservation.
- Preserved the actual chosen candidate through final codec selection.
- Added shared-timeline, loudness-matched A/B preview with mono, seeking, blind labels and playable warning timestamps.
- Added production mixer, passage damage/negative controls and real-render challenger regressions.
- Code 14 with the original update identity; no extra compulsory processing.

## 0.10.1 — 2026-09-25

- Fixed the approved flat-gain versus hidden renderer ceiling mismatch.
- Added byte-exact preservation for compliant masters within 0.10 LU of target.
- Added full-song radio preparation diagnostics and source-relative warning timestamps.
- Reproduced and fixed the failure on LastRunDownTheRange; retained prior safety gates.
- Code 13, original signing identity.

## 0.10.0 — 2026-09-15

- Kept the complete v0.7 natural reference; disabled later broad audio shaping, reference matching and combined candidates. Channel level balance is diagnostic-only.
- Imported measured sparse-peak limiting, flat AAC safety attenuation and source-relative translation reporting from Stem Lab policy.
- Added sparse per-channel isolated clipping repair, overlapping-channel correctness and fresh analysis of repaired sources with original provenance retained.
- Isolated candidate staging and made optional candidate I/O failures recover to a verified baseline; kept source/baseline/final failures and cancellation fail-closed.
- Added actual tone-frequency before/after measurements for narrow repair promotion and explicit inherited clipping accounting.
- Added failure injection, repair/negative controls, limiter/codec policy and byte-exact reprocessing tests.
- Increased version code to 12 with the same private update certificate. Physical phone and listening tests remain outstanding.

## 0.9.1 — 2026-08-27

- Restored the complete v0.7 decision path as the frozen natural sonic
  reference. Later modules now require technical-score-only, mode-specific
  promotion margins plus explicit dynamics/crest/correlation preservation.
- Made all 12 Translation Consensus response profiles diagnostic-only. They
  continue to audit playback translation but cannot generate EQ candidates.
- Removed the stacked v0.9 combined candidate; retained only independently
  proven narrow tone, stable channel and optional-reference candidates.
- Added exact staging-file, frame, header, durable-length and atomic-publish
  verification. Any required incomplete candidate now fails the whole run
  closed instead of changing the candidate pool.
- Bounded cache use by retaining only the still-relevant finalist from each
  measured candidate family.
- Added an already-compliant release-master guard independent of filename.
- Changed Preference Memory and Album Consistency to explicit opt-in controls
  that start OFF after update while retaining stored histories.
- Added explicit v0.7 reference, technical-promotion, diagnostic-translation
  and render-integrity evidence to QC JSON and the Confidence Map.
- Reproduced exact v0.7 selected waveform hashes for Never Give Up, Never Be
  Fancy, T-Bar Girls and The Corner Stage.
- Version code increased from 10 to 11; package and signing identity remain
  unchanged for direct in-place installation over v0.9.0.

## 0.9.0 — 2026-08-26

- Added Translation Consensus Correction using the same 12 documented response
  profiles, explicit device-family evidence and cut-only candidates. Bass or
  harshness needs agreement from at least three independent families; phone-
  only bass evidence remains diagnostic.
- Added persistent-tone analysis and bounded narrow-notch repair. Stable hum,
  buzz and whine harmonic families may be treated; isolated musical-note-like
  evidence is not automatically notched.
- Added stable technical stereo-balance repair using time stability,
  three-band direction agreement, cross-band spread and correlation gates.
  Polarity, inter-channel delay, swap and centre drift remain diagnostic when
  intent cannot be proven.
- Added Already-Mastered/Lossy-Source Guard for prior Full AI/DMS names, strong
  limiter signatures, codec-like bandwidth walls/pre-echo and effective source
  resolution. Broad second-pass modules and unsafe HF boosts are blocked.
- Added optional, level-matched reference calibration using bounded broad
  characteristics only; it neither clones an EQ curve nor imports loudness.
- Added foreground-service mastering with progress notification, cancellation,
  bounded wake lock and Activity reattachment after task switching.
- Expanded QC JSON and Confidence Map evidence for source guard, translation
  devices/families, tone families, channel stability and reference decisions.
- Added deterministic v0.9 fixtures for clean, artificial-resolution, lossy,
  prior-master, hum, buzz, musical-tone, polarity, fixed-delay, translation,
  reference and renderer/integration cases.
- Version code increased from 9 to 10. Package and signing identity remain
  unchanged, so v0.9.0 installs directly over v0.8.0.

## 0.8.0 — 2026-08-25

- Corrected inherited full-scale sample provenance: pre-master candidates are
  rejected only when they add full-scale samples; the final 24-bit master
  still requires zero quantizer clips and zero measured clipped samples.
- Preserved every source exact-zero region of 100 ms or longer through final
  QC. Dither can no longer conceal an unresolved source gap or produce a false
  technical PASS; the result becomes CONDITIONAL with exact time ranges.
- Corrected Stereo Event Repair failure diagnostics so non-finite samples,
  additional full-scale samples and inherited source samples are reported as
  separate conditions.
- Added attenuation-only Stereo Depth and Reverb Intelligence. It reduces only
  short masking vocal-band or unsafe upper-band side events; flat depth remains
  diagnostic because automatic widening is prohibited.
- Added Limiter and Crest Optimisation using three actual rendered look-ahead/
  release profiles and final-file LUFS, true-peak, LRA, crest and gain-reduction
  scoring. It runs only when the song demonstrates limiter pressure.
- Added Low-Frequency Phase Rescue in independently learned 25–60, 60–100 and
  100–160 Hz bands, using side-band attenuation only.
- Added Vocal Artifact Intelligence for centre-inferred sibilance, plosive and
  harsh-vowel events. It does not separate, regenerate, retune or time-warp a
  vocal and never applies broad automatic de-essing.
- Added an optional combined v0.8 candidate when at least two professional
  modules are eligible, with a 0.85 dB simultaneous nominal-edit ceiling.
- Added v0.8 model evidence, rendered benefits, limiter trials, protected-edge
  proof, promotion decisions and no-widening evidence to QC JSON, UI and the
  Master Confidence Map.
- Added controlled 48 kHz/16-bit inherited-clip/dropout provenance regression
  and a four-module professional mastering fixture covering every event family.
- Version code increased from 8 to 9; package and signing identity are
  unchanged, so v0.8.0 installs directly over v0.7.0 without uninstalling.

## 0.7.0 — 2026-08-21

- Added offline Section-Aware Mastering AI using learned arrangement-map
  confidence, demonstrated contour need and bounded section automation.
- Added provenance-neutral AI-Generation Artifact Repair for short 9–15 kHz
  fizz, 3.2–7 kHz metallic and phasey high-frequency side signatures.
- Added limiter-pressure Transient Intelligence with isolated stereo-peak
  control and explicit protection for natural/kick-like attacks.
- Added a combined v0.7 candidate when at least two new modules are eligible.
- Protected the first 2 seconds and final `max(8 seconds, 4% of duration)`;
  added 0.75 dB total-edit, 0.9955 correlation, phase and 0.50 LU LRA guards.
- Added rendered module-benefit verification and a new promotion gate requiring
  0.055 score margin, 0.100 targeted benefit and module-specific evidence.
- Added v0.7 codec-safety rerender, QC JSON, UI and Confidence Map reporting.
- Bounded long-song protected-transient diagnostics to 24 representative
  events while preserving the complete untouched count.
- Added controlled module/negative fixtures and retained exact Never Be Fancy
  real-song output-hash regression.
- Version code increased from 7 to 8; package and signing identity unchanged.

## 0.6.1 — 2026-08-12

- Propagated translation review independently into UI/QC display verdicts and
  warnings.
- Replaced raw filter-gain clipping judgments with documented, peak-capped RMS
  response matching; retained raw boost as headroom-stress evidence.
- Consolidated learned microstates into 8–15 macro Confidence Map sections for
  normal song lengths and removed false silence/tail labels.
- Added selected global processing and separate global/local change counts to
  every relevant Confidence Map section.
- Separated raw score rank, promotion-gate outcome and final selection in JSON,
  text summaries and selection explanations.
- Reported protected-region verification as N/A/null when no repair occurred.
- Distinguished run-start profile snapshots from current device profiles used
  by the next run.
- Retained all v0.6 mastering thresholds, promotion margins, stem gates,
  continuity gates and Preference/Album limits.

## 0.6.0 — 2026-08-12

- Added offline Dennis Preference Memory learned only from explicit,
  level-matched winner/runner-up choices.
- Added exact hash-pair duplicate protection, three-comparison warm-up,
  per-strategy evidence thresholds and a hard ±0.035 score-bias cap.
- Kept preference influence downstream of hard safety and promotion gates; it
  cannot create processing or promote a rejected candidate.
- Added optional Album Consistency Mode with a persistent rolling profile of
  verified winners' tonal balance, side energy, LRA and crest factor.
- Added a bounded `album_consistency_guided` candidate: no EQ node exceeds
  0.45 dB, widening and compression are prohibited, and the normal −14 LUFS-I
  target remains unchanged.
- Added album duplicate-hash protection, explicit profile reset and a stricter
  album-specific no-change gate requiring measured distance improvement.
- Added sample-proven Click/Dropout Repair before candidate generation.
- Limited click correction to isolated one-sample prediction failures with
  overlapping ≥0.97-confidence discontinuity evidence.
- Limited dropout correction to active-audio-bracketed, stereo-synchronous
  exact-zero holes of 2–960 frames; long or ambiguous silence stays untouched.
- Added event-count, total-coverage, protected-edge, duration, correlation,
  clip, non-finite and new-zero-run repair rejection gates.
- Added a Master Confidence Map that audits selected changes, rejected/review-
  only evidence and untouched areas by learned song section.
- Added the map to QC JSON and as a separate text file in the Full AI package.
- Expanded the UI with winner/runner-up playback and preference recording,
  album controls, persistent profile summaries and Confidence Map saving.
- Added controlled preference/album, click/dropout and package regressions.
- Verified exact v0.5 output-hash preservation on Never Be Fancy when no
  continuity event cleared the new gate.
- Re-ran v0.5 stereo intelligence and all Tier 1/Tier 2 stem regressions.
- Version code increased from 5 to 6. Package and signing identity are
  unchanged.

## 0.5.0 — 2026-08-12

- Added offline Stereo Event Repair AI using the existing song-learned local
  HF, upper-mid and low-mid evidence, capped to 0.60 dB per source event and
  0.80 dB simultaneous spectral attenuation.
- Added Low-End Intelligence for sustained sub/bass bloom and below-160 Hz
  side-phase risk, with robust per-song thresholds, a 0.65 dB simultaneous
  cap and explicit kick-like transient protection.
- Added Stereo Vocal Focus AI using short centre-dominant vocal-band evidence,
  a 0.14–0.30 dB mid lift and 0.08–0.20 dB side pocket. It does not extract,
  regenerate, retune or time-warp the vocal.
- Added a bounded combined stereo-intelligence candidate when at least two
  modules are eligible; maximum simultaneous nominal edit is 0.90 dB.
- Preserved the first 2 seconds and final `max(8 seconds, 4% of duration)` and
  verified source correlation of at least 0.995 for every new candidate.
- Added per-module coverage rejection so broad tonal or centre-channel
  conditions remain diagnostic-only.
- Added module-specific promotion: minimum 0.060 score advantage and 0.100
  measured targeted benefit over the guarded core stereo winner.
- Prevented non-promoted core, stereo-intelligence and stem candidates from
  re-entering selection as codec fallbacks.
- Added correct codec-safety rerendering for a promoted stereo-intelligence
  mode rather than silently reverting to the transparent strategy.
- Expanded the UI and QC JSON with module availability, event evidence,
  edits, measured targeted benefit, protected-edge proof, source correlation,
  promotion blocks and final decision.
- Added deterministic stereo-only module and report regressions. All four new
  candidates reached −14.0000 LUFS-I with zero clips, exact protected edges
  and source correlation above 0.99993 on the controlled fixture.
- Verified that the real-song Never Be Fancy winner, measurements and SHA-256
  remain exactly identical to v0.4.0 when no new module clears promotion.
- Re-ran Tier 1, Tier 2, delayed-stem and unsafe-ZIP regressions.
- Version code increased from 4 to 5. Package and signing identity are
  unchanged.

## 0.4.0 — 2026-08-12

- Added an explicit three-tier stem-trust classifier.
- Preserved v0.3 production-stem processing as Tier 1.
- Added Tier 2 for locally timing-secure separation-derived or incomplete
  stems that remain useful as measurement evidence.
- Added independent per-stem analysis timing tolerance of ±5 ms and drift no
  greater than 5 ms, with the existing exact-zero gate retained for Tier 1.
- Added a dedicated authoritative-mix-only Tier 2 renderer. It never opens the
  stem ZIP while rendering and never injects stem waveform audio.
- Limited Tier 2 vocal use to learned de-essing events; phrase rides are
  intentionally disabled.
- Limited Tier 2 masking control to short frequency-specific side-channel
  attenuation inferred from securely identified eligible competitors.
- Capped Tier 2 de-essing at 0.35 dB, masking events at 0.35 dB, combined
  masking at 0.45 dB and total simultaneous nominal edit at 0.55 dB.
- Protected the first 2 seconds and final `max(8 seconds, 4% of duration)` in
  the Tier 2 renderer.
- Added a stricter Tier 2 promotion gate: 0.08 score margin and 0.015 measured
  objective gain. Analytical evidence cannot substitute for objective gain.
- Expanded UI and QC JSON to report trust tier, audio-delta eligibility,
  analysis-only eligibility, per-stem analysis security, control mode and
  whether any stem waveform entered the render.
- Retained Tier 3 stereo fallback and all existing archive, codec, loudness,
  peak, phase, continuity and dynamics safeguards.
- Added controlled Tier 2 regression fixtures and machine-checked QC JSON
  assertions proving no stem waveform use.
- Version code increased from 3 to 4. Signing identity is unchanged.

## 0.3.0 — 2026-08-09

- Added optional offline Full AI Stem Assist with guarded Vocal Humanity,
  Masking Pockets and combined production-stem candidates.
- Added five-position local alignment, archive hardening, protected boundaries,
  measured promotion and stem-aware codec-safety rerendering.

The complete v0.3 handover is retained in
`docs/history/Dennis_AI_Mastering_Lab_Handover_v0.3.0.md`.

## v0.14.5
- Added demand-aware preservation only for unresolved dynamics-led adaptive broad fallback.
- Kept all hard safety and normal-track preservation gates unchanged.
- Added adaptive-rung rejection/eligibility diagnostics to Studio QC.
- Prevented generic LRA scoring from double-penalizing a measured dynamics need inside the adaptive fallback.
