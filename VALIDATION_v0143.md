# v0.14.3 Validation

## Host compile / decision tests

- PASS — `V0143BroadMasteringNeedHarness`
  - strong reliable broad plan => `BROAD_MASTERING_REQUIRED`
  - tiny plan => broad mastering not required
  - previous-master guard => broad mastering blocked
  - low-reliability plan => broad mastering not forced
- PASS — `V0143BroadAnchorHarness`
  - higher-score near-copy does not qualify as broad anchor
  - lower-score broad spectral master is selected as the broad anchor
  - measured broad residual approximately -30.51 dB relative
- PASS — `V0143BroadRetentionHarness`
  - transparent control retained
  - raw-score family finalist retained
  - separate broad `sonic_finish` anchor retained through candidate-cache cleanup
  - broad residual approximately -30.98 dB relative
- PASS — real raw-material demand classification on an excerpt of the earlier aligned source
  - reliability 1.000
  - broad EQ demand 0.467 dB
  - predicted objective gain 1.680
  - verdict `BROAD_MASTERING_REQUIRED`
- PASS — `FullAiMasteringEngine.java` + new broad-mastering classifier/test dependencies compile under host Java 21.

## Android gate

The full Android APK compile is intentionally performed by the existing GitHub Actions Android 34 workflow. No private signing material is included in the public build-source ZIP.
