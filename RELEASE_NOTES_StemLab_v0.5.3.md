# Stem Lab v0.5.3 — Chain + Loud TEST

Side-by-side package `com.dennis.stemlab.chain053` ("Stem Lab 0.5.3 Chain TEST"). Built on v0.5.2.

## Fixes stack instead of competing
Mastering is now a chain (`MasteringCore`, `FullAiMasteringEngine.Scope`):
1. **Tone** — the core EQ/dynamics plans compete as before (plus the adaptive broad ladder and
   sparse-peak audition). No repair families here.
2. **Repairs** (up to two stages) — the tone winner becomes the input; the repair families
   (stereo intelligence, advanced/artifact, vocal artifact, technical repair, stems) compete
   against the tone winner itself, with every existing gate. A family that wins is stacked and
   not offered again. Stops when no repair proves a benefit.
3. A stacked repair is rejected if, against the tone master, it costs more than 1 dB of crest,
   changes LRA by more than 1 LU, or drops stereo correlation by more than 0.08.
The project screen shows the chain ("tone: sonic_finish → repair 1 (stereo): …").
With the chain switched off (`-Dstemlab.chained=false`, host tests only) the base master is
bit-identical to v0.5.2.

## Fewer wasted versions
Repair stages render one version per family (its combined or gentlest mode) and skip the
limiter-profile auditions (limiting is the delivery stage's job). On host test songs the busiest
case went from 36 s to 18 s; simple songs take a few seconds longer (extra analysis of the tone
master). Phone timing still to be measured.

## Loud masters
- Home: **LOUDNESS FOR NEW SONGS** — Streaming −14 (default), Balanced −12, Loud −11
  (country/pop), Louder −10 (rock/pop), Very loud −9. Per song in Finishing and delivery too.
- Targets above −12.5 LUFS use `LoudDelivery`: glue compressor (1.6:1, 6 dB knee, 30/250 ms,
  threshold at the loudest fifth) then the 4× limiter to −1.0 dBTP. Limits: limiter ≤ 6 dB peak,
  ≤ 1.2 dB average, < 35% of frames over 1 dB, < 6% over 3 dB, LRA within 2.5 LU, crest within
  4.5 dB. If the target fails, it steps down 0.5 LU at a time (max 2 LU) and the result says
  "asked −10.0: limiter peaks 6.1 dB there"; if nothing passes, streaming delivery is used and says so.
- Trial B keeps the streaming delivery (it only restores the EQ's loudness).

# v0.5.4 (after Dennis's first two 0.5.3 songs)
- One test app from now on: package `com.dennis.stemlab.test`, "Stem Lab TEST", signed with the
  test key in `signing/` so each new test build installs over the last and keeps its projects.
  (0.5.3 and earlier stay as separate apps.)
- Louder presets (−12, −11, −10, −9) use a −2.0 dBTP ceiling, as Spotify asks for masters louder
  than −14 LUFS; "LOUDNESS FOR NEW SONGS" applies it too.
- Studio QC now records the chain (`base_chain`, `base_repairs`), the tone stage's winner and
  decision, and per-stage timing (`base_stage_ms`). Broad-mastering fields come from the tone
  stage (a repair stage re-measuring the tone master reported a misleading "unresolved").
