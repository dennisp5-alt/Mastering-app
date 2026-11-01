# Privacy

Dennis AI Mastering Lab 0.9.0 is fully offline and declares no Internet,
microphone, location, account or broad media/storage permission. Source audio,
optional stems, the optional reference, analysis, candidate renders, Preference
Memory, Album Consistency and reports remain on the device. Files are chosen
and saved explicitly through Android's system document picker.

The app requests only Android foreground-service/data-sync, notification and
wake-lock capabilities so a user-started mastering job can continue visibly
while minimized or the screen is off. The wake lock is bounded to the active
job and released on completion, cancellation or failure.

Preference and album profiles are stored in private app data. They survive an
in-place signed update but are removed if the app is uninstalled or its data is
cleared. There is no telemetry, upload or remote AI service.
