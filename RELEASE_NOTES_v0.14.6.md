# Dennis AI Mastering Lab — v0.14.6 Demand-Aware Objective Gate

* DSP and mastering plans unchanged from v0.14.5.
* Applies only when normal broad mastering failed and an adaptive candidate is used for a verified dynamics-led unresolved source.
* Retains exact existing hard safety, true peak, clipping, channel, phase, passage, and demand-aware dynamics LRA/crest/limiter checks.
* Separates physical dynamics preservation from the legacy generic objective diagnostic: a preserved candidate may now pass with objective delta >= -0.050 and **raw** technical-score delta >= -0.300, both against the established v0.14.5 references.
* Both tolerance limits are independent, strict, finite-value checks and are never applied to normal broad, tonal, already mastered, or repair candidates.
* When multiple adaptive dynamics rungs pass, the gentle rung wins, then balanced, then firm. The engine keeps the transparent control if none pass.
* Final broad-anchor selection rechecks the exact same gate, preventing the prior final-selection veto or gate bypass.
* Adds focused host regression harness for objective and technical boundaries, scope rejection, and weakest-valid-rung selection.

## Installation

The v0.14.6 TEST package uses a distinct test application ID while persistent private signing is still unconfigured. Stable update-over installation requires a protected, reusable signing keystore and the stable TEST application ID, not only a renamed APK.
