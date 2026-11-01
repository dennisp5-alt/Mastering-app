# v0.2.0 scope and honest limits

This release deliberately tests how far a fully autonomous offline mastering
controller can go using the authoritative stereo mix.

Included:

- track-specific robust feature learning and section candidates;
- local anomaly inference;
- five hidden mastering strategies;
- deterministic floating-point processing;
- actual render measurement and autonomous winner selection;
- transparent no-change rollback;
- codec-aware fallback and safety rerendering;
- translation-response simulations;
- one 24-bit/48 kHz Full AI master, AAC, QC report and package.

Not included:

- a pretrained neural waveform model;
- cloud inference or model downloads;
- stem-aware vocal or masking processing;
- physical listening performed by the app;
- automatic artistic or A&R certification;
- automatic replacement of the Dennis Mastering Studio deterministic master.

The correct comparison is a level-matched physical listening test between the
app-selected Full AI master and the result of the complete professional
mastering workflow. Objective measurements alone cannot establish artistic
superiority.
