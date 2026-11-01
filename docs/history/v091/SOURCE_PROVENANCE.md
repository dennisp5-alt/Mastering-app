# Source provenance

This v0.9.0 source continues from the authoritative Dennis AI Mastering Lab
v0.8.0 source and its unchanged private update-signing identity.

The main v0.9 production additions are:

- `TranslationConsensusModel.java`
- `PersistentToneModel.java`
- `ChannelIntegrityModel.java`
- `SourceGuardModel.java`
- `ReferenceCalibrationModel.java`
- `MasteringForegroundService.java`
- `MasteringJobControl.java`

Integration changes cover candidate generation, second-pass restrictions,
rendered promotion, response-profile evidence, background UI reattachment,
QC JSON, Confidence Map, version metadata and deterministic regression fixtures.
All analysis and rendering are local Java code; no network AI or third-party
mastering service is used.

The private `test-signing.jks` is included only so Dennis can continue signing
compatible private updates. It must not be published or replaced. The build
script verifies certificate SHA-256
`2d6822f2d6e3785a03bfe61d73d5139822c78c9b2dedbbc64aa0424ad44eff70`
and refuses to generate another key.

`SOURCE_CHECKSUMS.sha256` inventories the clean source handoff, excluding build
outputs, packaged archives and the checksum file itself.
