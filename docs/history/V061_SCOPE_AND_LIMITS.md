# v0.6.1 scope and limits

## Scope

Version 0.6.1 is a reporting and translation-QC maintenance release for the
existing v0.6 autonomous mastering engine. It corrects seven observed audit
problems while intentionally leaving master-render DSP and learned safety gates
unchanged.

The maintenance surface is limited to:

- response-profile gain normalization and diagnostic labeling;
- translation verdict/warning propagation;
- Confidence Map aggregation and global/local change accounting;
- raw-score/promotion/final-selection terminology;
- no-repair applicability reporting; and
- run-start versus current profile-state labeling.

## Unchanged audio and decision safeguards

No v0.6.1 change was made to:

- −14.00 LUFS-I target or −1.00 dBTP ceiling;
- limiter, compressor, EQ, local-event or stereo-delta DSP bounds;
- no-change, stereo-intelligence or stem promotion margins;
- Tier 1, Tier 2 or Tier 3 stem trust rules;
- click/dropout detection, coverage or protected-edge gates;
- Preference Memory readiness, duplicate guard or ±0.035 score cap;
- Album Consistency plan bounds; or
- the authoritative-stereo-mix policy.

The exact retained Never Be Fancy regression output hash is documented in
`TEST_RESULTS.md`.

## Translation interpretation

Each response profile is first rendered at its raw filter gain. The analyzer
then requests a flat gain that would match the unfiltered composite RMS. That
gain is capped so the simulated profile cannot exceed the unfiltered
composite's sample peak. The report contains:

- raw response peak and RMS;
- requested and applied level-match gain;
- whether the peak cap constrained matching;
- level-matched peak and RMS;
- raw headroom stress above 0 dBFS; and
- actual review reasons used by the simulation verdict.

Raw headroom stress describes unnormalized filter boost. It is not evidence
that a physical device clipped. The profiles remain objective response proxies
and perceptual inferences, not physical listening tests.

## Confidence Map interpretation

For songs at least 60 seconds long, the report targets 8–15 macro sections.
Shorter fixtures may use fewer sections rather than inventing musical
structure. Macro roles remain learned candidates, not confirmed verse/chorus
labels.

The term silence is used only when the macro section measures at or below
−55 LUFS. Otherwise a low-confidence inactive learned state is labeled a
low-occupancy transition candidate. Global selected processing is listed in
every section it affects; section-specific events remain separately flagged.

## Decision interpretation

`raw_score_rank` is calculated before no-change, module-promotion and codec
gates. `promotion_blocked` and `decision_outcome` describe what happened after
ranking. `selected_after_promotion_gates` identifies the authoritative
candidate at the reporting stage. Codec QC may still move selection to the
highest-ranked promotable codec-safe fallback.

## Known limits

- The app is an offline analytical controller with deterministic DSP, not a
  pretrained neural waveform generator.
- Response simulation cannot prove physical-device translation.
- Host/JVM tests cannot execute the phone's real Android AAC codec,
  document-provider, MediaPlayer or in-place update behavior.
- Final music release decisions still require matched-level human listening.
- Production/A&R competitiveness is not assessed by the app.
- Uninstalling or clearing app data removes local Preference and Album profiles.

## Required phone checks

On the Samsung Galaxy S23 Ultra:

1. install v0.6.1 over v0.6.0 without uninstalling;
2. confirm Preference and Album profiles remain;
3. run at least one stereo-only song and one optional-stem song;
4. verify actual AAC encode/decode, save destinations and package opening;
5. confirm playback and cancellation behavior;
6. compare winner/runner-up at matched level; and
7. confirm a post-run preference/album action changes the current profile but
   does not change the completed run's QC snapshot.
