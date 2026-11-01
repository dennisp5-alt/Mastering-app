# DENNIS AI MASTERING LAB HANDOVER

Version: 0.1.0  
Handover date: 2026-08-09  
Owner: Dennis

## Authority and separation

Dennis AI Mastering Lab is a secondary offline Android experiment. It is not an
update to Dennis Mastering Studio and must not replace its deterministic release
path.

- App name: Dennis AI Mastering Lab
- Package: `com.dennis.aimasteringlab`
- Version code/name: 1 / 0.1.0
- Minimum/target Android: API 29 / API 34
- Primary device: Samsung Galaxy S23 Ultra
- Permissions: none
- Internet permission: none
- APK Signature Scheme v3: verified
- Signing-certificate SHA-256:
  `2d6822f2d6e3785a03bfe61d73d5139822c78c9b2dedbbc64aa0424ad44eff70`

Install it normally beside Dennis Mastering Studio. The package and signing
identity are deliberately independent.

## v0.1 operating model

The app accepts a 48 kHz stereo WAV and creates three separate, level-matched
files:

1. Transparent Reference.
2. AI Conservative.
3. AI Preferred.

The AI controller learns multiband, stereo, transient and arrangement features
from the current song. It selects bounded minimum-phase EQ, confidence-gated
local event attenuation, optional side reduction and optional stereo-linked
compression. Deterministic DSP performs the render. A separate safety layer
rejects a candidate that violates duration, continuity, phase, LRA, processing
caps, true peak or sample integrity.

The controller is analytical track-trained ML, not a pretrained neural network.
Do not describe it as neural, generative or NPU accelerated. It never replaces,
regenerates, source-separates or uploads the waveform.

## Output hierarchy

1. Preserve duration, intro, ending, dynamics and performance.
2. Verified true peak no higher than −1.00 dBTP.
3. Exactly −14.00 LUFS-I when safely achievable.
4. Preserve dynamics and report an honest shortfall when the required peak
   control exceeds the song-derived allowance.
5. AI candidates remain experimental until Dennis selects one by level-matched
   human listening.

No AI file is automatically packaged or promoted as a release master.

## New source components

- `AutonomousMasteringBrain.java`: learned features and bounded plans.
- `AutonomousMasteringRenderer.java`: hybrid AI/DSP pre-master and guardrails.
- `AiLabActivity.java`: dedicated file, render, cancellation and A/B interface.
- `AiLabReportWriter.java`: machine-readable evidence and output report.
- `AiLabHarness.java`: real-song and controlled-fixture regression harness.

The inherited mastering core came from
`DennisMasteringStudio-Source-v0.4.0.zip`, verified SHA-256:

`286141292d13561c9f2d3c0bd841adfc7a6b94d0a006d44774d4e6bf29be516f`

## Verified regression results

- Full-song inherited Standard Master remained byte-identical:
  `ef5e4e28571639e97f596832e651a04ddd82c468028729c869ce88f3c1a789ed`.
- Real-song 60-second Conservative/Preferred candidates both measured exactly
  −14.0000 LUFS-I, below −1.83 dBTP, with zero clips and delayed reopen/hash
  verification.
- A high-crest fixture exercised the optional AI compressor and safety limiter;
  both results stayed below −1.048 dBTP with zero clips.
- Dynamics-protection and already-compliant no-op branches passed.

See `TEST_RESULTS.md` for the full measurements and hashes.

## Known limits and next work

The physical Samsung Galaxy S23 Ultra test remains outstanding. Dennis should
test selection, analysis, cancel, backgrounding, saving, A/B playback and JSON
opening before this build is treated as device-validated.

Recommended later phases, only after v0.1 listening results are collected:

1. v0.2: supplied-stem and Vocal Humanity candidate.
2. v0.3: local preference ranking from Dennis's explicit A/B choices.
3. v0.4: optional compact neural controller with LiteRT/QNN acceleration,
   retaining the same deterministic safety supervisor and CPU fallback.

Do not add reference-style transfer, source separation or generative restoration
until each can produce reversible A/B files and pass the existing safety gates.

## Signing-key warning

The source archive contains the private signing keystore so future versions can
install over v0.1.0. Keep the source archive private. Do not distribute it or
the keystore. The APK and Release ZIP do not contain the signing key.

