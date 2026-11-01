# Stem Lab v0.5.2 — One Path TEST

Side-by-side test package `com.dennis.stemlab.onepath052` ("Stem Lab 0.5.2 One Path TEST").
Based on Stem Lab v0.5.1 Trial B Bulk (v0.15.7 engine).

## One mastering path
- `MasteringCore` is the single analysis → event repair → technical analysis → mastering brain →
  candidate engine sequence. Studio projects, the queue, stem-only imports and Classic mastering all use it.
  Before, Studio and Classic each had their own copy, and the copies had drifted: Studio failed a whole song
  when section mapping, local diagnostics or technical analysis threw; Classic continued. Now all paths continue.
- When every step succeeds the base master is bit-identical to v0.5.1 (checked on host in CI).
- Removed the unreachable old Classic engine (not in the manifest): MainActivity, AiGuidedRenderer, MasteringPlan,
  ReleasePackageBuilder, ReleaseReportWriter, ReportWriter, FormalAssessment, ReleaseArtifacts, AiLabReportWriter.

## Trial B loudness
- Imported-master Trial B now finishes through the same delivery stage as every Studio master
  (`StudioAudio.deliver`), at a −1.0 dBTP ceiling (was −1.1 with a further 0.06 dB margin and no limiter).
  Target = the imported master's own loudness. If flat gain is not enough, the verified transparent peak limiter
  may be used (≤3.1 dB max, ≤0.2 dB mean, ≤4% of frames above 1 dB, LRA within 0.7 LU, crest loss ≤1.5 dB).
- The result is stated in one sentence on the project screen, in the QC JSON and in the bulk CSV
  (new columns `limiter_used`, `limiter_max_db`; imported masters are their own baseline).
- A master whose own true peak is already above −1.0 dBTP may still come out quieter; the app now says so and why.

## Delivery fix (affects all Studio masters)
- The 4× limiter's down-sampling filter could put the final true peak ~0.3 dB above the ceiling; delivery then
  fell back to flat gain and delivered quieter. It now lowers the limiter ceiling by the measured overshoot and
  re-checks (up to 3 times). Same transparency limits. QC field `limiter_peak_corrections`.

Projects from v0.5.1 stay in that app (separate package); re-import songs to test them here.
