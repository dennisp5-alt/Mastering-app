# Dennis AI Mastering Lab v0.15.3 — Verified peak-safety recovery

## Trigger
Studio 0.15.2 could reject the entire project during initial transparent baseline rendering when actual remeasured true peak exceeded the requested ceiling, even when safe flat-gain attenuation could recover it. The user reported this for “Last Run Down the Range”; the Classic engine had previously mastered the track through a separate path.

## Change
- Peak overshoot with no limiter: re-render with less flat gain, verify loudness, sample integrity, true peak and file duration; up to three bounded retry attempts.
- If the standard limiter exhausts its four passes and is not force-required, attempt independently verified flat-gain headroom fallback. Forced codec-safety limiter failures still fail closed.
- No ceiling relaxation, no extra compression, no EQ alterations, no silent -14 LUFS normalization claim.
- Store transparent baseline peak-safety recovery counts, applied attenuation and verified loudness difference in Studio QC; warn clearly when recovery used. Additional metadata in Full AI report.
- Normal passes and DSP processing preserved, including exact-output regression.

## Validation
- Synthetic true-peak prediction-error reproduction recovered at -1.22 dBTP under a -1.1 dBTP test ceiling; source unchanged; exact-loudness shortfall exposed.
- Normal-render result bit-identical to direct reference render; unsafe preflight gain still rejected.
- Seventeen local regression harnesses passed. Android GitHub build required before field deployment.

## Known limits
This change targets the independently measured peak-overshoot failure. Other unsafe conditions remain rejection cases. The user's particular failed WAV has not yet been directly reproduced without its original source and failure diagnostics. On-device mastering performance and success require bulk testing.
