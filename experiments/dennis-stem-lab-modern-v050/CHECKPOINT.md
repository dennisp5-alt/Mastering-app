# Dennis Stem Lab Modern v0.5.0 — isolated source-level integration

**15 October 2026 — NOT A BUILT APK / NOT DEVICE VERIFIED**

## Preservation

The trusted Dennis AI Mastering Lab v0.15.7 production source, application and `main` branch were not modified. The v0.15.7 performance experiments remain separate. This experiment is a different Android application: `com.dennis.stemlab.modern`, named **Dennis Stem Lab Modern DEV**.

Source checkpoints recovered independently:
- Modern stereo mastering v0.15.7: successful GitHub Actions run [37899462151](https://github.com/dennisp5-alt/Mastering-app/actions/runs/37899462151), source artifact 11602110293. Source ZIP SHA-256 `0bc7390fba6db3fa746d3a554555bbba9e7745068276929b529924426080eede`.
- Legacy Dennis Stem Lab v0.4.0: Library file `DennisStemLab-Source-v0.4.0.zip`, source SHA-256 `25b80c634d272f3b1542aa5236c097b53ab8f6c2087a78c9a63765c8920c96ae`.

## Exact integration boundary

1. Android document picker imports a stem ZIP. No original stereo WAV is required in Stems Only.
2. The original v0.4.0 `StemInventory` verifies entries, CRC, safety, and 1–32 48-kHz mono/stereo WAV inputs. Two stems **vocals + instrumental** are supported, independent of filename.
3. Original `StemStudioPreflight` executes conservative guarded repairs with a fallback to untouched stems if repair fails.
4. Original `StemPremixBuilder` sums stem samples at their preserved relative levels and creates an integer-safe-headroom, 32-bit float stereo premix.
5. New `StemOnlyImport` passes that premix to the untouched `StudioImports.source`, which records SHA-256, verifies the source, and queues a standard studio project.
6. The **unmodified v0.15.7 `StudioPipeline.master()` and `StudioPipeline.finish()`** perform the modern candidate selection, mastering, PCM verification and two-format WAV delivery.
7. Stem-specific diagnostics remain attached to the project as `Stem_Import_QC.json`.

The original-versus-stems trust/reconstruction mechanism is intentionally NOT used in this Stems Only path. Do not automatically normalise individual stem levels.

## Host tests passed (synthetic fixture)

- Java host compilation of adapter and legacy premix/preflight code against v0.15.7.
- `V050StemOnlyIntegrationHarness`: 18-second two-stem ZIP successfully imported, source verified, authority `STEMS_ONLY`, correct format, source CRC/ZIP guard, no original required; unsafe traversal ZIP refused.
- Original `StemLabPremixHarness`: mono/stereo, headroom, original correlation and unsafe ZIP checks passed against both old v0.4.0 and new v0.5.0 implementation.
- **Byte-identical intermediate stereo premix** produced by the old engine and the new test app (SHA-256 `6544264bc309be0d9a10dd68aa67abc2d309692c962781ed0dcd698eaeb9f75c`).
- No original `FullAiMasteringEngine`, `StudioPipeline`, `MasterRenderer`, `LoudnessMeter` or related DSP Java file was changed in the local staged source.
- Full source-only checkpoint ZIP SHA-256 `39036b90828cd2e40f0b6c2e80c8fb06706af3fee7bbc575382b16e19b302e11`. This is a conversation output artifact, not currently stored in this GitHub branch.

## Remaining work before a test APK

- Stage the sanitised source-only patch (the source checkpoint is available from conversation).
- Run Android Gradle compilation and the full modern mastering host regressions.
- Verify full candidate competition, final 24-bit/48-kHz and 16-bit/44.1-kHz WAV output/QC, checksum persistence and progress/cancellation.
- Run real 2-stem vocals/instrumental files from Dennis Stem Studio and listen on S23 Ultra/Philips NX4; later verify 4-stem input.
- Evaluate extra storage consumption and whether per-stem preflight followed by studio click repair performs duplicated work.

**No APK has been built or claimed as ready. No code was merged into the existing mastering app.**
