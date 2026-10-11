# Suno Prompts v0.3.1 — ChatGPT Plan Edition

Standalone Android application (package `com.dennis.sunoprompts`, Android API 26+). This open-source app's code lives in the SunoPrompts directory. See LICENSE in this directory. The rest of the repository is unrelated.

## ChatGPT Plus / Pro login, no API key
Optional **Continue with ChatGPT** uses the official Sign in with ChatGPT open-source dynamic OAuth registration documented at https://developers.openai.com/siwc/token-sharing-open-source/sign-in . It runs a local 127.0.0.1 server, uses browser OAuth PKCE S256, checks state/nonce/issuer/audience/JWKS signature and granted ChatGPT plan permission, and stores credentials with Android Keystore AES-GCM. It discovers eligible models from the plan-scoped model catalog, then sends `store:false`/`stream:true` requests to the PUBLIC `/v1/responses` API. Refresh tokens are rotated and stored encrypted. Users can disconnect locally or revoke app access in ChatGPT Settings.

No OpenAI API key, separately purchased credits, ChatGPT session scraping, or private ChatGPT website endpoints are used. **Plan usage depends on eligibility, user consent, and remaining shared limits.** New sign-in is not verified on a physical Android device yet; test with an eligible account.

## Features
- Automatic artist folders in SQLite, one saved prompt per artist+song.
- ChatGPT generation: optional source-informed, original, highly song-specific Suno v6 style (<=1000 characters) and Exclude prompt; model list auto-selected or chosen by user.
- Free curated reference library for known songs and artists (continues to work without login); metadata search with MusicBrainz and Wikipedia.
- Editable song prompts, Share, Copy, Save, Delete and Replace with latest free reference.
- ChatGPT errors do not replace saved prompts. The latest version keeps the same application ID and existing database as v0.1/v0.2.
- No Spotify integration or automatic audio-file analysis.

## How to test
1. Install this APK **over** v0.2.0 without uninstalling it. Existing folders should remain.
2. Open the ChatGPT button on the home screen, tap **Continue with ChatGPT** and authorise plan use in your browser.
3. After the callback, switch to Suno Prompts if the browser stays open. Account should show Connected.
4. Search AC/DC / Thunderstruck or Lee Kernaghan / The Outback Club. Tap **Generate with ChatGPT plan**.
5. View and edit saved original production prompts, compare with free profile.
6. Test declined consent, disconnected state, expired access and plan usage limit. No saved prompt should be changed on errors.

## Security and caveats
- Only the authenticated loopback callback host 127.0.0.1 receives the authorisation code; never use a shared external callback.
- The app does not read ChatGPT chat history, and cannot claim to have listened to a referenced track without an imported recording.
- MusicBrainz and Wikipedia are limited sources; the model is instructed to separate facts from informed producer interpretation.
- Credentials remain on-device and are not logged. Use ChatGPT Settings to revoke the registration remotely.
- Currently supports one active ChatGPT account profile on the device. Multiple-account selection may come later.
- Secure provider identity is validated with JWKS; the system browser handles sign-in.
- Build validation is automatic on GitHub; OAuth still requires real-device confirmation.

## Local build
JDK 17, Gradle 8.11.1, Android SDK 35: `gradle :app:assembleDebug`. Workflow: `.github/workflows/suno-prompts.yml`. Release: `SunoPrompts-v0.3.1-debug.apk`.

## v0.3.1 Android sign-in diagnostics
The browser completing the local 127.0.0.1 callback does not mean the OAuth token exchange finished. When Android DNS cannot resolve the official auth.openai.com hostname, the app now retries lookups at 3s and 8s intervals (DNS only; OAuth POST socket timeouts are NOT blindly replayed). It retains the issued client registration ID without treating the user as logged in; only verified OIDC tokens authorise plan usage. The identity JWKS GET also retries lookup failure.

Use ChatGPT Plan → **Test OpenAI connection** to test active network (Wi-Fi/mobile/VPN), custom Private DNS state, Android DNS for auth.openai.com and TLS connectivity to official OpenAI OIDC discovery. No tokens, passwords or artist data leave the device during the diagnostic. If DNS fails, change Wi-Fi/mobile network or temporarily review VPN, ad blocker or custom Private DNS. Do not override the official auth.openai.com hostname or disable certificate verification.

The app's debug APK is signed by the build runner's ephemeral debug key. Android may therefore block in-place upgrades from prior builds with INSTALL_FAILED_UPDATE_INCOMPATIBLE. **Do not uninstall before copying any valuable saved prompts** until a stable signing strategy and backup/restore are implemented.
