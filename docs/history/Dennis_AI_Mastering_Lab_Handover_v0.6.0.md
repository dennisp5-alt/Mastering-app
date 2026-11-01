# DENNIS AI MASTERING LAB — v0.6.0 HANDOVER

Handover date: 2026-08-12  
Owner: Dennis  
Status: engineering build complete; physical S23 Ultra validation pending

## 1. Build identity

- App: Dennis AI Mastering Lab
- Package: `com.dennis.aimasteringlab`
- Version: `0.6.0`
- Version code: `6`
- Minimum Android: API 29
- Compile/target Android: API 34
- Requested permissions: none
- Internet permission: none
- APK Signature Scheme: v3 verified
- Signing-certificate SHA-256:
  `2d6822f2d6e3785a03bfe61d73d5139822c78c9b2dedbbc64aa0424ad44eff70`
- APK SHA-256:
  `2d3dff322df5a25de46a0535bec655b68affdd6c5c7ebbc4246ac48b3394cc31`

Install v0.6.0 directly over v0.5.0. Do not uninstall first. Package and
signing identity are unchanged, so local app data is retained.

## 2. Purpose and boundary

This is Dennis's secondary Full AI mastering experiment. It autonomously
analyses, renders, rejects and selects one verified output. It remains separate
from Dennis Mastering Studio v0.5.0, whose deterministic Standard Master is the
authoritative path in that app.

The original stereo WAV remains the musical authority. AI Lab performs
objective measurement, offline track-trained inference and device-response
simulation. It does not claim physical listening, human A&R approval or label
certification.

## 3. Completed module set

The v0.5 modules remain:

1. Stereo Event Repair AI.
2. Low-End Intelligence.
3. Stereo Vocal Focus AI.

Version 0.6 adds:

4. **Dennis Preference Memory.** The app retains one vote per exact,
   level-matched winner/runner-up hash pair. Learning begins after three valid
   comparisons and can add at most ±0.035 to an already safe candidate score.
5. **Album Consistency Mode.** Dennis can create an album profile, add
   technically passing winners and optionally test a tightly bounded
   album-guided candidate on later songs.
6. **Click/Dropout Repair.** Before mastering, the app may repair a proven
   isolated sample click or a 2–960-frame stereo exact-zero hole. Ambiguous
   findings remain untouched.
7. **Master Confidence Map.** Every run receives a section-by-section audit of
   selected changes, rejected/review-only evidence and untouched regions.

## 4. Preference Memory operation

After a successful run, the app retains the winner and highest-ranked eligible
runner-up for a level-matched A/B. Dennis may play either and record which one
he prefers. The exact candidate-hash pair is de-duplicated.

The persistent profile stores strategy wins/losses and comparison identifiers,
not audio. It needs three choices before influencing selection; individual
strategies need two observations. It cannot override a hard rejection,
promotion block or no-change safety rule by itself.

## 5. Album Consistency operation

Album mode is off unless explicitly enabled. The current album profile stores
rolling low, low-mid, presence, harsh, air, stereo-side, LRA and crest values
from winners Dennis adds after a technical PASS. Duplicate hashes are rejected.

The album-guided plan is limited to ±0.45/0.42/0.40/0.35 dB at the four global
EQ nodes, up to 2.5% side reduction, no widening and no compression. It still
targets −14.00 LUFS-I and remains subject to all existing hard QC. Use **Start
New / Clear Album Profile** before testing a different release.

## 6. Click/dropout operation

Click repair needs both sample-level isolation evidence and an overlapping
≥0.97-confidence learned discontinuity. Dropout repair needs an exact,
stereo-synchronous 2–960-frame zero run bracketed by active audio. A maximum of
96 events and a tiny total-coverage allowance applies.

The first 2 seconds and final `max(8 seconds, 4% of duration)` are excluded.
The working render must retain source correlation ≥0.997, unchanged frame count
and duration, exact protected edges, and no extra clip/non-finite/zero-run fault.
If no event clears every gate, the source is passed through unchanged.

## 7. Confidence Map operation

The map is stored inside `*_FullAI_QC.json` and separately as
`*_FullAI_Confidence_Map.txt`. It follows learned arrangement sections and
distinguishes selected changes from rejected evidence. Global chain choices,
protected edges and limitations are also explicit. Automatic section names are
candidate labels, not confirmed composer intent.

## 8. Candidate authority and order

The app can test:

- five core stereo strategies;
- one optional album-guided core strategy;
- up to three individual stereo-intelligence strategies;
- one bounded combined stereo-intelligence strategy;
- up to three Tier 1 stem-delta strategies; or
- one Tier 2 authoritative-mix-only stem-control strategy.

Continuity repair, when proven and verified, precedes all candidates. Audio-
identical plans are de-duplicated. Every actual render is reopened and measured.
Preference/album bonuses apply only after hard eligibility. Stereo and stem
modules retain their independent promotion gates, and non-promoted candidates
cannot return through codec fallback.

## 9. Loudness, peak and final QC

- Target: exactly −14.00 LUFS-I after verified render.
- Ceiling: no higher than −1.00 dBTP; it is not a peak target.
- Dynamics take priority when exact loudness would require excessive control.
- Final Android QC tests actual AAC 256 encode/decode, a 328 kbps SBC proxy and
  twelve response-profile simulations.

## 10. Reporting and package

QC JSON 0.6 adds top-level evidence for:

- `click_dropout_repair`;
- `dennis_preference_memory`;
- `album_consistency`; and
- `master_confidence_map`.

Candidate records separate technical score from preference and album bonuses.
The Full AI package contains the verified master, QC JSON, checksum file and
the separate Confidence Map. Existing PCM, codec, translation, stem, chain,
automation, hash and decision evidence remains.

## 11. Validation completed

- Full Android 34 source compilation passed.
- Package/version/API/empty-permission identity passed.
- Zip alignment, APK integrity, v3 signature and retained certificate passed.
- Preference persistence, duplicate rejection, three-choice activation and
  ±0.035 limit passed.
- Album persistence, duplicate rejection and all plan bounds passed.
- The continuity fixture repaired exactly one click plus one 96-frame dropout,
  retained 0.99996440 source correlation and left protected faults untouched.
- Confidence JSON and separate package file assertions passed.
- All four v0.5 stereo candidates passed unchanged regressions.
- Never Be Fancy retained its exact v0.5 winner hash
  `4517808696fca43c3116f474f1fb924e33a15371e4d6b0bc38465ae4fa704671`
  with no invented continuity repair.
- Tier 1, Tier 2, delayed-stem and unsafe-path regressions passed.

See `TEST_RESULTS.md` for exact measurements.

## 12. Physical S23 Ultra work required

1. Install v0.6.0 over v0.5.0 and confirm app/profile retention.
2. Run a bulk set with no stems, rejected stems, Tier 1 and Tier 2 stems.
3. Compare winner and runner-up at matched playback level before recording
   several preferences; confirm the profile survives app restart.
4. Build a small real album profile and confirm only intended songs are added.
5. Confirm saved WAV/AAC/QC/Confidence Map/package access and playback.
6. Confirm Android AAC encode/decode and SBC proxy completion.
7. Exercise cancellation and several long sequential jobs.
8. Compare selected masters with v0.5 and Dennis Mastering Studio on the S23
   Ultra, headphones, speakers and car system.

The build environment performed no physical listening.

## 13. Latest-source instruction

Treat `DennisAiMasteringLab-Source-v0.6.0.zip` as the latest Dennis AI
Mastering Lab source. Do not resume AI Lab development from v0.5.0 or from
Dennis Mastering Studio source. Dennis Mastering Studio v0.5.0 remains the
latest implementation of the separate deterministic app.
