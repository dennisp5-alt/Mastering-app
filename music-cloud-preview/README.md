# Dennis AI Music Studio — Cloud XL preview v0.3

**Scope:** a separately installed Android client to test **real ACE-Step 1.5 XL cloud music generation** on a Galaxy S23 Ultra. It leaves the offline v0.1/v0.2 experiments untouched.

## Status and limits

- Implements the official ACE-Step /health, /release_task, /query_result and /v1/audio endpoints.
- Original 3-minute Australian country-rock prompt and structured song lyrics are prefilled; you may replace them.
- Supports XL Turbo, XL SFT and standard Turbo choices, subject to models actually installed on the server.
- Full 3/4/5-minute songs with vocals, WAV retrieval, local playback and storage.
- **Does NOT include a GPU instance, GPU account, model weights, built-in demo credentials or active endpoint. No real AI-generated audio has been obtained yet.**
- **Does NOT upload recordings or train a LoRA yet**. Personalisation is a future phase only after assessing the base model's quality.
- Does not store the secret API key; user enters it each session, HTTPS required.

## GPU deployment (user-controlled, may incur charges)

1. Obtain a GPU hosting account with a working NVIDIA GPU, preferably 24 GB+ VRAM for XL. Provision an isolated pod with sufficient disk storage for model files.
2. In the pod, set `ACESTEP_API_KEY` as a long secret; do not paste it into GitHub or public chat. Install Python 3.11/3.12 and `uv`. Run `bash cloud_gpu/start_gpu.sh` from this project's root (or manually clone official ACE-Step and run `uv sync && uv run acestep-api` with the matching environment).
3. Publish the ACE-Step API only through a trusted **HTTPS reverse proxy**; protect it using the configured ACESTEP_API_KEY. Never expose an unauthenticated public API to the internet. Check /health without sharing the secret.
4. Open the Android app, enter the HTTPS API base origin (no /path), enter the server API key in the password field, press TEST SECURE GPU CONNECTION. Then select XL Turbo and GENERATE. The app shows status and downloads a WAV after the API returns a successful task.
5. Stop the GPU instance after the test to avoid idle charges. Preserve any generated files and future LoRA checkpoints before terminating instances with ephemeral storage.

Official API: https://github.com/ace-step/ACE-Step-1.5/blob/main/docs/en/API.md
Official install: https://github.com/ace-step/ACE-Step-1.5/blob/main/docs/en/INSTALL.md
Official LoRA tutorial: https://ace-step.github.io/ACE-Step-1.5/en/LoRA_Training_Tutorial

## Quality gate

Listen to actual generated 3–5-minute vocal songs before spending GPU time fine-tuning. Compare vocal intelligibility, naturalness, arrangement continuity, real-world instruments, artefacts, and the ability to re-render predictable versions. The project's quality is not established by the client compiling.

## Security

Never put cloud keys in version control or screenshots. Generate uses an explicit confirmation because cloud GPU charges may apply. This app downloads only relative /v1/audio?path=... URLs from the same configured HTTPS origin, refuses HTTP, and verifies output has a RIFF WAVE header.

## Build

`gradle -p music-cloud-preview :app:testDebugUnitTest :app:assembleDebug`. GitHub Actions builds this app independently of Dennis Mastering Studio and offline Music Studio. Debug APK signing may vary per CI run; avoid uninstalling an app that contains files you still need.
