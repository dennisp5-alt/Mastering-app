# Dennis AI Mastering Lab v0.14.0 — Sonic Finish

## Purpose

This is the bulk-test branch requested after v0.13.1 produced technically compliant masters that could remain too close to the source. The objective is a more complete automatic mastering decision, not preservation of the v0.7 sound or a preference for minimal change.

## Changed mastering policy

- **Delivery-ready is not mastering-complete.** Being near −14 LUFS-I and peak-safe no longer activates the previous-master block.
- **Transparent is the control.** It is rendered/measured first and can still win, but it receives no automatic sonic priority.
- **Prior-master protection is source-history based.** Positive Dennis master naming/history still activates second-pass protection. Strong limiter occupancy alone remains evidence, not proof.
- **Sonic Finish candidate added.** The preferred plan gets a bounded stronger challenger with up to roughly 2 dB global EQ movement, up to 1.65 dB simultaneous local dynamic cut, and up to 2 dB stereo-linked compressor reduction when evidence enables the compressor.
- **Compression is easier to justify but still conditional.** Crest factor, LRA and predicted peak pressure determine whether it participates. It is not inserted unconditionally.
- **Candidate scoring no longer rewards minimal processing as strongly.** EQ/compression/local-cut cost penalties and legacy LRA penalties are reduced; hard damage gates remain.
- **Specialist modules have measured promotion gates rather than a frozen v0.7 sound target.**
- **Gentler challenger no longer wins by being gentler.** It can replace the selected master only if its measured objective, technical score and total score are effectively equal, while retaining the limiter/passage safeguards.

## Safety retained

The branch still fails or rejects candidates for file/render integrity faults, non-finite samples, added clipping/full-scale damage, unsafe true peak, destructive LRA/crest loss, serious phase/correlation degradation, failed passage preservation, unresolved codec/source limitations and other established hard QC failures. Sparse-peak limiting remains bounded rather than forcing −14 LUFS at any cost.

## Bulk-test comparison

For useful evaluation, compare the same source as: **Original → v0.13.1 → v0.14.0 Sonic Finish → external/Claude reference**. Listen level-matched and retain the QC JSON for each v0.14 result. The first test goal is not that v0.14 always changes more; it is that it should make a meaningful mastering move when the measurements support one and remain conservative only when the source already earns it.

## Build status

The v0.14.0 source branch and recovery checkpoint are complete. In the current execution environment, the Android SDK platform/build-tools directory used for v0.13.1 is not mounted, so a signed APK has **not** been claimed from this session. `tools/build_apk.sh` is updated for version code 19/name 0.14.0 and still requires the retained signing certificate before emitting the installable APK.
