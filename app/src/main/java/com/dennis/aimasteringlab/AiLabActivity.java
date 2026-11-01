package com.dennis.aimasteringlab;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.content.pm.PackageManager;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** One authoritative mix, v0.14.5 Sonic Finish competition and one verified winner. */
public final class AiLabActivity extends Activity {
    private static final int PICK_WAV = 201;
    private static final int PICK_STEMS = 206;
    private static final int SAVE_MASTER = 202;
    private static final int SAVE_WAV16 = 209, SAVE_MP3 = 210;
    private static final int SAVE_AAC = 203;
    private static final int SAVE_REPORT = 204;
    private static final int SAVE_PACKAGE = 205;
    private static final int SAVE_CONFIDENCE_MAP = 207;
    private static final int PICK_REFERENCE = 208;
    private static final int NOTIFICATION_PERMISSION = 209;
    private static final String USE_PREFERENCE_KEY =
            "use_preference_for_ranking_v091";
    private static final String USE_ALBUM_KEY =
            "use_album_for_ranking_v091";

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Future<?> currentTask;
    private Uri wavUri;
    private String wavName = "No stereo WAV selected";
    private Uri stemUri;
    private String stemName = "No optional stem ZIP selected — stereo fallback is ready";
    private Uri referenceUri;
    private String referenceName = "No optional professional reference selected";
    private Uri savedMasterUri;
    private AnalysisResult analysis;
    private AutonomousMasteringBrain.Result brain;
    private FullAiMasteringEngine.Result engine;
    private FullAiReleaseQc.Result qc;
    private FullAiPackageBuilder.Result releasePackage;
    private File sessionDirectory;
    private ComparisonPlayer player;
    private long playbackGeneration;
    private boolean blindSwap, seeking;
    private int playbackSlot;
    private CheckBox monoCheck, blindCheck;
    private SeekBar playbackSeek;
    private TextView playbackPosition;
    private Button pauseButton;
    private LinearLayout listeningMarkers;
    private FullAiMasteringEngine.Candidate comparisonCandidate;
    private String preferenceComparisonId;
    private DennisPreferenceMemory.Profile preferenceProfile;
    private AlbumConsistencyModel.Profile albumProfile;

    private TextView wavLabel;
    private TextView stemLabel;
    private TextView status;
    private TextView results;
    private ProgressBar progress;
    private Button chooseWavButton;
    private Button chooseStemButton;
    private Button createButton;
    private Button removeStemButton;
    private Button chooseReferenceButton;
    private Button removeReferenceButton;
    private TextView referenceLabel;
    private Button cancelButton;
    private Button playButton;
    private Button saveWav16Button, saveMp3Button;
    private Button saveAacButton;
    private Button saveReportButton;
    private Button savePackageButton;
    private Button compareButton;
    private Button preferWinnerButton;
    private Button preferRunnerButton;
    private Button resetPreferenceButton;
    private Button resetAlbumButton;
    private Button addAlbumButton;
    private Button saveConfidenceMapButton;
    private CheckBox albumModeCheck;
    private CheckBox preferenceModeCheck;
    private TextView preferenceStatus;
    private TextView albumStatus;
    private int lastProgress = Integer.MIN_VALUE;
    private String lastStage = "";
    private final MasteringJobControl.Listener jobListener =
            snapshot -> runOnUiThread(() -> applyJobSnapshot(snapshot));

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferenceProfile = loadPreferenceProfile();
        albumProfile = loadAlbumProfile();
        setContentView(buildUi());
        MasteringJobControl.attach(jobListener);
    }

    private void applyJobSnapshot(MasteringJobControl.Snapshot snapshot) {
        if (snapshot == null || status == null) return;
        if (snapshot.running) {
            setBusy(true, snapshot.stage, snapshot.percent);
        } else if (snapshot.finished && engine == null) {
            setBusy(false, snapshot.stage, snapshot.percent);
            status.setText(snapshot.success
                    ? "Background mastering completed. The verified master was saved to the destination selected when the job started."
                    : "Background mastering ended without a verified master: "
                    + snapshot.stage);
        }
    }

    private View buildUi() {
        int pad = dp(18);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.parseColor("#0B1118"));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(18), pad, dp(40));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = text("DENNIS AI MASTERING LAB", 24,
                Color.parseColor("#F5F7FA"));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setLetterSpacing(0.055f);
        root.addView(title);
        TextView version = text("Sonic Finish Mastering • offline • v" + StudioQcReporting.APP_VERSION, 13,
                Color.parseColor("#51D6C7"));
        version.setPadding(0, dp(3), 0, dp(14));
        root.addView(version);

        TextView intro = text("Select a 48 kHz stereo WAV. Master offline, aiming for −14 LUFS while protecting peaks, dynamics and musical detail. Measured cleanup must improve the rendered result. "
                        + "Proven clicks, short dropouts and isolated technical faults are repaired conservatively. Optional references are used for diagnosis only; device simulations check the finished master.",
                15, Color.parseColor("#B6C0CE"));
        intro.setLineSpacing(0, 1.15f);
        root.addView(panel(intro));

        root.addView(section("1  AUTHORITATIVE SOURCE"));
        chooseWavButton = button("SELECT STEREO WAV", false);
        chooseWavButton.setOnClickListener(view -> openWavPicker());
        root.addView(chooseWavButton);
        wavLabel = fileLabel(wavName);
        root.addView(wavLabel);

        chooseStemButton = button("SELECT OPTIONAL STEM ZIP", false);
        chooseStemButton.setOnClickListener(view -> openStemPicker());
        root.addView(chooseStemButton);
        stemLabel = fileLabel(stemName);
        root.addView(stemLabel);
        removeStemButton = button("REMOVE STEM ZIP — USE STEREO ONLY", false);
        removeStemButton.setEnabled(false);
        removeStemButton.setOnClickListener(view -> removeStemArchive());
        root.addView(removeStemButton);

        chooseReferenceButton = button("SELECT OPTIONAL PROFESSIONAL REFERENCE", false);
        chooseReferenceButton.setOnClickListener(view -> openReferencePicker());
        root.addView(chooseReferenceButton);
        referenceLabel = fileLabel(referenceName);
        root.addView(referenceLabel);
        removeReferenceButton = button("REMOVE REFERENCE", false);
        removeReferenceButton.setEnabled(false);
        removeReferenceButton.setOnClickListener(view -> removeReference());
        root.addView(removeReferenceButton);

        root.addView(section("2  OPTIONAL LEARNED MASTERING PROFILES"));
        preferenceModeCheck = new CheckBox(this);
        preferenceModeCheck.setText("Use Preference Memory as a safe tie-break for the next run");
        preferenceModeCheck.setTextSize(14);
        preferenceModeCheck.setTextColor(Color.parseColor("#F5F7FA"));
        preferenceModeCheck.setChecked(loadPreferenceEnabled());
        preferenceModeCheck.setPadding(dp(4), dp(10), dp(4), dp(4));
        preferenceModeCheck.setOnCheckedChangeListener((button, checked) -> {
            savePreferenceEnabled(checked);
            if (preferenceStatus != null) {
                preferenceStatus.setText(currentPreferenceText());
            }
        });
        root.addView(preferenceModeCheck);
        preferenceStatus = text(currentPreferenceText(), 14,
                Color.parseColor("#F5F7FA"));
        preferenceStatus.setLineSpacing(dp(2), 1.08f);
        root.addView(panel(preferenceStatus));
        resetPreferenceButton = button("CLEAR LEARNED PREFERENCE HISTORY", false);
        resetPreferenceButton.setOnClickListener(
                view -> confirmResetPreferenceProfile());
        root.addView(resetPreferenceButton);

        albumModeCheck = new CheckBox(this);
        albumModeCheck.setText("Use current Album Consistency profile for the next run");
        albumModeCheck.setTextSize(14);
        albumModeCheck.setTextColor(Color.parseColor("#F5F7FA"));
        // v0.14.5 deliberately starts neutral after an update. The learned
        // album data is retained, but it must be explicitly enabled again.
        albumModeCheck.setChecked(loadAlbumEnabled());
        albumModeCheck.setPadding(dp(4), dp(10), dp(4), dp(4));
        albumModeCheck.setOnCheckedChangeListener((button, checked) -> {
            albumProfile.active = checked;
            saveAlbumEnabled(checked);
            saveAlbumProfile();
            refreshAlbumStatus();
        });
        root.addView(albumModeCheck);
        albumStatus = text(currentAlbumText(), 14,
                Color.parseColor("#F5F7FA"));
        albumStatus.setLineSpacing(dp(2), 1.08f);
        root.addView(panel(albumStatus));
        resetAlbumButton = button("START NEW / CLEAR ALBUM PROFILE", false);
        resetAlbumButton.setOnClickListener(view -> resetAlbumProfile());
        root.addView(resetAlbumButton);

        root.addView(section("3  AUTONOMOUS MASTER"));
        createButton = button("CREATE FULL AI MASTER", true);
        createButton.setOnClickListener(view -> requestMasterDestination());
        root.addView(createButton);
        cancelButton = button("CANCEL CURRENT JOB", false);
        cancelButton.setEnabled(false);
        cancelButton.setOnClickListener(view -> cancelCurrentJob());
        root.addView(cancelButton);

        progress = new ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(8));
        progressParams.setMargins(0, dp(12), 0, dp(8));
        root.addView(progress, progressParams);
        status = text("Ready", 14, Color.parseColor("#B6C0CE"));
        root.addView(status);
        results = text("No Full AI run yet.", 14, Color.parseColor("#F5F7FA"));
        results.setLineSpacing(dp(3), 1.08f);
        root.addView(panel(results));

        root.addView(section("4  VERIFIED OUTPUTS + LEVEL-MATCHED A/B"));
        playButton = button("PLAY FULL AI MASTER", true);
        playButton.setEnabled(false);
        playButton.setOnClickListener(view -> playMaster());
        root.addView(playButton);
        compareButton = button("PLAY LEVEL-MATCHED RUNNER-UP", false);
        compareButton.setEnabled(false);
        compareButton.setOnClickListener(view -> playComparison());
        root.addView(compareButton);
        pauseButton = button("PAUSE", false);
        pauseButton.setEnabled(false);
        pauseButton.setOnClickListener(view -> { if (player != null) player.pause(); });
        root.addView(pauseButton);
        playbackPosition = text("0:00 / 0:00 • matched playback", 13, Color.parseColor("#B6C0CE"));
        root.addView(playbackPosition);
        playbackSeek = new SeekBar(this); playbackSeek.setMax(1000); playbackSeek.setEnabled(false);
        playbackSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStartTrackingTouch(SeekBar bar) { seeking = true; }
            public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {}
            public void onStopTrackingTouch(SeekBar bar) {
                seeking = false;
                if (qc != null && ensureComparisonPlayer()) player.seekSeconds(
                        qc.finalMasterVerification.wav.durationSeconds * bar.getProgress() / 1000.0);
            }
        });
        root.addView(playbackSeek);
        monoCheck = new CheckBox(this); monoCheck.setText("Listen in mono"); monoCheck.setTextColor(Color.WHITE);
        monoCheck.setOnCheckedChangeListener((button, checked) -> { if (player != null) player.mono(checked); });
        monoCheck.setEnabled(false); root.addView(monoCheck);
        blindCheck = new CheckBox(this); blindCheck.setText("Hide version names for a blind A/B comparison"); blindCheck.setTextColor(Color.WHITE);
        blindCheck.setOnCheckedChangeListener((button, checked) -> {
            if (player != null) player.pause();
            if (checked) blindSwap = new java.util.Random().nextBoolean();
            configurePreferenceButtons();
            status.setText(checked ? "Blind pairing ready. Choose A or B; names remain hidden until you turn this off." : "Version names revealed.");
        });
        blindCheck.setEnabled(false); root.addView(blindCheck);
        listeningMarkers = new LinearLayout(this); listeningMarkers.setOrientation(LinearLayout.VERTICAL);
        root.addView(listeningMarkers);
        preferWinnerButton = button("I PREFER THE FULL AI WINNER", false);
        preferWinnerButton.setEnabled(false);
        preferWinnerButton.setOnClickListener(view -> recordPreference(!alternativeForSlot(0)));
        root.addView(preferWinnerButton);
        preferRunnerButton = button("I PREFER THE RUNNER-UP", false);
        preferRunnerButton.setEnabled(false);
        preferRunnerButton.setOnClickListener(view -> recordPreference(!alternativeForSlot(1)));
        root.addView(preferRunnerButton);
        addAlbumButton = button("ADD VERIFIED WINNER TO ALBUM PROFILE", false);
        addAlbumButton.setEnabled(false);
        addAlbumButton.setOnClickListener(view -> addWinnerToAlbum());
        root.addView(addAlbumButton);
        saveWav16Button = button("SAVE VERIFIED 16-BIT / 44.1 kHz WAV", false);
        saveWav16Button.setEnabled(false);
        saveWav16Button.setOnClickListener(view -> requestExistingDestination(
                SAVE_WAV16, qc.releaseFormats.wav16.getName(), "audio/wav"));
        root.addView(saveWav16Button);
        saveMp3Button = button("SAVE VERIFIED MP3 320", false);
        saveMp3Button.setEnabled(false);
        saveMp3Button.setOnClickListener(view -> requestExistingDestination(
                SAVE_MP3, qc.releaseFormats.mp3.getName(), "audio/mpeg"));
        root.addView(saveMp3Button);
        saveAacButton = button("SAVE VERIFIED AAC 256", false);
        saveAacButton.setEnabled(false);
        saveAacButton.setOnClickListener(view -> requestExistingDestination(
                SAVE_AAC, qc.finalAac.getName(), "audio/mp4"));
        root.addView(saveAacButton);
        saveReportButton = button("SAVE FULL AI QC JSON", false);
        saveReportButton.setEnabled(false);
        saveReportButton.setOnClickListener(view -> requestExistingDestination(
                SAVE_REPORT, releasePackage.report.getName(), "application/json"));
        root.addView(saveReportButton);
        saveConfidenceMapButton = button("SAVE MASTER CONFIDENCE MAP", false);
        saveConfidenceMapButton.setEnabled(false);
        saveConfidenceMapButton.setOnClickListener(view -> requestExistingDestination(
                SAVE_CONFIDENCE_MAP, releasePackage.confidenceMap.getName(), "text/plain"));
        root.addView(saveConfidenceMapButton);
        savePackageButton = button("SAVE RELEASE PACKAGE — WAV + MP3", false);
        savePackageButton.setEnabled(false);
        savePackageButton.setOnClickListener(view -> requestExistingDestination(
                SAVE_PACKAGE, releasePackage.zip.getName(), "application/zip"));
        root.addView(savePackageButton);

        TextView warning = text("Experimental boundary: this app makes the selection autonomously, "
                        + "but it remains separate from the deterministic Dennis Mastering Studio release path. "
                        + "The original stereo mix always remains authoritative. Local preference and album history cannot override measured safety or quality gates. Tier 2 stems control measurements only, "
                        + "never contribute waveform audio, and unsafe Tier 3 stems fall back automatically. "
                        + "The 12 playback-device profiles are QC simulations only; they do not alter the master or claim physical listening or human A&R approval.",
                13, Color.parseColor("#FFB4AB"));
        warning.setPadding(0, dp(18), 0, 0);
        warning.setLineSpacing(0, 1.12f);
        root.addView(warning);
        return scroll;
    }

    private void openWavPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "audio/wav", "audio/x-wav", "audio/wave", "application/octet-stream"});
        startActivityForResult(intent, PICK_WAV);
    }

    private void openStemPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/zip", "application/x-zip-compressed", "application/octet-stream"});
        startActivityForResult(intent, PICK_STEMS);
    }

    private void openReferencePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "audio/wav", "audio/x-wav", "audio/wave",
                "application/octet-stream"});
        startActivityForResult(intent, PICK_REFERENCE);
    }

    private void removeReference() {
        referenceUri = null;
        referenceName = "No optional professional reference selected";
        referenceLabel.setText(referenceName);
        removeReferenceButton.setEnabled(false);
        clearRun("Reference removed. Source-aware stereo mastering is ready.");
    }

    private void removeStemArchive() {
        stopPlayback();
        cleanupSession();
        stemUri = null;
        stemName = "No optional stem ZIP selected — stereo fallback is ready";
        stemLabel.setText(stemName);
        removeStemButton.setEnabled(false);
        clearRun("Stem ZIP removed. Core plus stereo-intelligence Full AI testing is ready.");
    }

    private void requestMasterDestination() {
        if (wavUri == null) {
            status.setText("Select the authoritative stereo WAV first.");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/wav");
        intent.putExtra(Intent.EXTRA_TITLE,
                ReleaseFormats.camelBase(wavName) + "_Master_24bit-48kHz.wav");
        startActivityForResult(intent, SAVE_MASTER);
    }

    private boolean formatsReady() {
        return qc != null && qc.releaseFormats != null && qc.releaseFormats.complete();
    }

    private void requestExistingDestination(int requestCode, String filename,
                                            String mime) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mime);
        intent.putExtra(Intent.EXTRA_TITLE, filename);
        startActivityForResult(intent, requestCode);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode,
                                              Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        keepPermission(uri, data.getFlags());
        if (requestCode == PICK_WAV) {
            stopPlayback();
            cleanupSession();
            wavUri = uri;
            wavName = displayName(uri, "Selected WAV");
            wavLabel.setText(wavName);
            stemUri = null;
            stemName = "No optional stem ZIP selected — stereo fallback is ready";
            stemLabel.setText(stemName);
            removeStemButton.setEnabled(false);
            clearRun("New source selected. Create the Full AI master when ready.");
        } else if (requestCode == PICK_STEMS) {
            stopPlayback();
            cleanupSession();
            stemUri = uri;
            stemName = displayName(uri, "Selected stem ZIP");
            stemLabel.setText(stemName);
            removeStemButton.setEnabled(true);
            clearRun("Optional stems selected. Unsafe or ambiguous stems will fall back to stereo automatically.");
        } else if (requestCode == PICK_REFERENCE) {
            stopPlayback();
            cleanupSession();
            referenceUri = uri;
            referenceName = displayName(uri, "Selected professional reference");
            referenceLabel.setText(referenceName);
            removeReferenceButton.setEnabled(true);
            clearRun("Optional reference selected. It will be level-matched for broad calibration evidence only.");
        } else if (requestCode == SAVE_MASTER) {
            startFullAi(uri);
        } else if (requestCode == SAVE_WAV16 && formatsReady()) {
            saveExisting(qc.releaseFormats.wav16, uri, "16-bit WAV");
        } else if (requestCode == SAVE_MP3 && formatsReady()) {
            saveExisting(qc.releaseFormats.mp3, uri, "MP3 320");
        } else if (requestCode == SAVE_AAC && qc != null && qc.finalAac != null) {
            saveExisting(qc.finalAac, uri, "AAC");
        } else if (requestCode == SAVE_REPORT && releasePackage != null) {
            saveExisting(releasePackage.report, uri, "QC JSON");
        } else if (requestCode == SAVE_CONFIDENCE_MAP && releasePackage != null) {
            saveExisting(releasePackage.confidenceMap, uri, "Master Confidence Map");
        } else if (requestCode == SAVE_PACKAGE && releasePackage != null) {
            saveExisting(releasePackage.zip, uri, "Full AI package");
        }
    }

    private void startFullAi(Uri destination) {
        stopPlayback();
        File work = createSessionDirectory();
        if (work == null) return;
        final Uri source = wavUri;
        final String sourceName = wavName;
        final Uri selectedStems = stemUri;
        final String selectedStemName = stemName;
        final Uri selectedReference = referenceUri;
        final String selectedReferenceName = referenceName;
        final DennisPreferenceMemory.Profile preferenceSnapshot =
                preferenceModeCheck != null && preferenceModeCheck.isChecked()
                        ? DennisPreferenceMemory.Profile.fromJson(
                        preferenceProfile.toJson()) : null;
        final AlbumConsistencyModel.Profile albumSnapshot =
                AlbumConsistencyModel.Profile.fromJson(albumProfile.toJson());
        albumSnapshot.active = albumModeCheck != null
                && albumModeCheck.isChecked();
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION);
        }
        setBusy(true, "Starting autonomous Full AI mastering", 0);
        MasteringForegroundService.start(this, sourceName);
        currentTask = worker.submit(() -> {
            try {
                AnalysisResult measured = AudioAnalyzer.analyze(sourceName,
                        () -> requiredInput(source), this::postProgress);
                if (measured.wav.channels != 2 || measured.wav.sampleRate != 48000) {
                    throw new IOException("v0.14.5 requires a 48 kHz stereo WAV");
                }
                measured.sourceGuard = SourceGuardModel.analyze(
                        () -> requiredInput(source), measured,
                        this::postProgress);
                File stemArchive = prepareOptionalStems(selectedStems,
                        selectedStemName, measured, source, work);
                // v0.5.2: the same shared mastering path as Studio projects.
                final String referenceHash = selectedReference == null ? null
                        : sha256(requiredInput(selectedReference));
                MasteringCore.Options coreOptions = new MasteringCore.Options();
                coreOptions.stemArchive = stemArchive;
                coreOptions.reference = selectedReference == null ? null
                        : () -> requiredInput(selectedReference);
                coreOptions.referenceName = selectedReferenceName;
                coreOptions.referenceSha256 = referenceHash;
                coreOptions.preference = preferenceSnapshot;
                coreOptions.album = albumSnapshot;
                coreOptions.repairedFileName = "continuity-repaired-source.wav";
                MasteringCore.Result core = MasteringCore.master(
                        () -> requiredInput(source), measured, work, coreOptions,
                        (step, start, end) -> this::postProgress);
                final AudioAnalyzer.StreamProvider processingSource = core.processing;
                final AnalysisResult masteringAnalysis = core.analysis;
                AutonomousMasteringBrain.Result learned = core.brain;
                FullAiMasteringEngine.Result competition = core.engine;
                FullAiReleaseQc.Result verified = FullAiReleaseQc.run(
                        processingSource, masteringAnalysis, competition,
                        stemArchive, work, shortBaseName(sourceName), this::postProgress);
                FullAiPackageBuilder.Result packaged = FullAiPackageBuilder.build(
                        masteringAnalysis, learned, competition, verified, work,
                        shortBaseName(sourceName), this::postProgress);
                FullAiMasteringEngine.Candidate comparator = chooseComparisonCandidate(
                        competition, verified.finalCandidate);
                cleanupCandidateRenders(competition, verified.finalMaster,
                        comparator == null ? null : comparator.file);

                copyToUri(verified.finalMaster, destination);
                AnalysisResult saved = AudioAnalyzer.analyze(
                        displayName(destination, verified.finalMaster.getName()),
                        () -> requiredInput(destination), this::postProgress);
                if (!verified.finalMasterSha256.equalsIgnoreCase(saved.sourceSha256)) {
                    throw new IOException("Saved Full AI master checksum did not match the verified local winner");
                }
                analysis = masteringAnalysis;
                brain = learned;
                engine = competition;
                qc = verified;
                releasePackage = packaged;
                savedMasterUri = destination;
                comparisonCandidate = comparator;
                preferenceComparisonId = DennisPreferenceMemory.comparisonId(
                        verified.finalCandidate, comparator);
                runOnUiThread(() -> {
                    setBusy(false, "Full AI master saved and verified", 100);
                    results.setText(fullSummary());
                    playButton.setEnabled(true);
                    saveWav16Button.setEnabled(formatsReady());
                    saveMp3Button.setEnabled(formatsReady());
                    saveAacButton.setEnabled(qc.finalAac != null);
                    saveReportButton.setEnabled(true);
                    saveConfidenceMapButton.setEnabled(true);
                    savePackageButton.setEnabled(true);
                    configurePreferenceButtons();
                    configureListeningMarkers();
                    addAlbumButton.setEnabled(qc != null
                            && "PASS".equals(qc.technicalVerdict));
                    status.setText("Master saved. " + (qc.radioPreparation == null
                            ? "Review the QC report before release."
                            : qc.radioPreparation.compactSummary()));
                });
                MasteringForegroundService.finish(true,
                        "Master saved and verified");
            } catch (Exception error) {
                MasteringForegroundService.finish(false,
                        "Mastering failed: " + cleanError(error));
                runOnUiThread(() -> {
                    setBusy(false, "Full AI mastering failed", 0);
                    status.setText("Full AI mastering failed: " + cleanError(error));
                });
            } finally {
                MasteringJobControl.clear(currentTask);
            }
        });
        MasteringJobControl.register(currentTask);
    }

    private File prepareOptionalStems(Uri selectedStems, String selectedStemName,
                                      AnalysisResult measured,
                                      Uri mixUri, File work) {
        if (selectedStems == null) return null;
        measured.stemArchiveName = selectedStemName;
        File cached = new File(work, "optional-stems.zip");
        try {
            postProgress("Stem assist: caching and hashing optional archive", 0);
            try (InputStream input = requiredInput(selectedStems);
                 OutputStream output = new FileOutputStream(cached)) {
                copy(input, output);
            }
            measured.stemArchiveSha256 = Hashing.sha256(cached);
            measured.stems.clear();
            measured.stems.addAll(StemInventory.inspect(cached, this::postProgress));
            StemInventory.compareAgainstMix(measured);
            if (measured.wavStemCount < 1 || measured.wavStemCount > 64) {
                throw new IOException("Stem ZIP must contain 1–64 intact WAV stems");
            }
            for (StemInventory.Item item : measured.stems) {
                if (item.directory) continue;
                if (item.error != null || !item.crcVerified) {
                    throw new IOException("Stem archive entry failed decode/CRC validation: " + item.path);
                }
                if (item.wav && item.wavInfo != null
                        && (item.wavInfo.sampleRate != measured.wav.sampleRate
                        || (item.wavInfo.channels != 1 && item.wavInfo.channels != 2))) {
                    throw new IOException("Stem format is not 48 kHz mono/stereo: " + item.path);
                }
            }
            measured.stemReconstruction = StemReconstruction.analyze(
                    () -> requiredInput(mixUri), measured, cached, this::postProgress);
            if (measured.stemReconstruction.compatible
                    && !measured.stemReconstruction.globalPolarityRisk) {
                measured.stemAlignment = StemAlignmentValidator.analyze(
                        () -> requiredInput(mixUri), measured, cached, this::postProgress);
            }
            measured.stemTrust = StemTrustClassifier.classify(measured);
            measured.stemTrustTier = measured.stemTrust.code();
            measured.stemArchiveAccepted = measured.stemTrust.audioDeltaAllowed;
            measured.stemArchiveAnalysisOnlyAccepted = measured.stemTrust.analysisOnlyAllowed
                    && !measured.stemTrust.audioDeltaAllowed;

            if (measured.stemArchiveAccepted) {
                try {
                    measured.vocalHumanity = VocalHumanityModel.analyze(
                            cached, measured, this::postProgress);
                } catch (Exception vocalError) {
                    measured.vocalHumanity = new VocalHumanityModel.Result();
                    measured.vocalHumanity.status = "Vocal model rejected: " + cleanError(vocalError);
                }
                try {
                    measured.maskingPockets = MaskingPocketModel.analyze(
                            cached, measured, this::postProgress);
                } catch (Exception maskingError) {
                    measured.maskingPockets = new MaskingPocketModel.Result();
                    measured.maskingPockets.status = "Masking model rejected: " + cleanError(maskingError);
                }
            } else if (measured.stemArchiveAnalysisOnlyAccepted) {
                try {
                    measured.vocalHumanity = VocalHumanityModel.analyzeForGuidance(
                            cached, measured, this::postProgress);
                } catch (Exception vocalError) {
                    measured.vocalHumanity = new VocalHumanityModel.Result();
                    measured.vocalHumanity.analysisOnly = true;
                    measured.vocalHumanity.status = "Analysis-only vocal model rejected: "
                            + cleanError(vocalError);
                }
                try {
                    measured.maskingPockets = MaskingPocketModel.analyzeForGuidance(
                            cached, measured, this::postProgress);
                } catch (Exception maskingError) {
                    measured.maskingPockets = new MaskingPocketModel.Result();
                    measured.maskingPockets.analysisOnly = true;
                    measured.maskingPockets.status = "Analysis-only masking model rejected: "
                            + cleanError(maskingError);
                }
            }

            if (measured.stemTrust.tier == StemTrustClassifier.Tier.TIER_3_REJECTED) {
                measured.stemAssistStatus = "TIER 3 REJECTED — stereo fallback active: "
                        + measured.stemTrust.status;
            } else {
                int evaluated = measured.stemAlignment == null
                        ? 0 : measured.stemAlignment.stemsEvaluated;
                measured.stemAssistStatus = String.format(Locale.US,
                        "%s\n%d/%d stems locally secure; vocal %s; masking %s",
                        measured.stemTrust.status,
                        measured.stemTrust.secureStems, evaluated,
                        measured.vocalHumanity == null ? "unavailable" : measured.vocalHumanity.status,
                        measured.maskingPockets == null ? "unavailable" : measured.maskingPockets.status);
            }
            return cached;
        } catch (Exception error) {
            measured.stemArchiveAccepted = false;
            measured.stemArchiveAnalysisOnlyAccepted = false;
            measured.stemTrustTier = "TIER_3_REJECTED";
            measured.stemAssistStatus = "TIER 3 REJECTED — stereo fallback active: "
                    + cleanError(error);
            return cached.isFile() ? cached : null;
        }
    }

    private void saveExisting(File file, Uri destination, String label) {
        setBusy(true, "Saving " + label, -1);
        currentTask = worker.submit(() -> {
            try {
                String expected = Hashing.sha256(file);
                copyToUri(file, destination);
                String actual;
                try (InputStream input = requiredInput(destination)) {
                    actual = sha256(input);
                }
                if (!expected.equalsIgnoreCase(actual)) {
                    throw new IOException(label + " checksum verification failed after saving");
                }
                runOnUiThread(() -> {
                    setBusy(false, label + " saved and verified", 100);
                    status.setText(label + " saved and checksum-verified.");
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    setBusy(false, label + " save failed", 0);
                    status.setText(label + " save failed: " + cleanError(error));
                });
            }
        });
    }

    private String fullSummary() {
        int rendered = 0;
        int rejected = 0;
        int duplicates = 0;
        for (FullAiMasteringEngine.Candidate candidate : engine.candidates) {
            if (candidate.rendered) rendered++;
            if (candidate.rendered && !candidate.eligible) rejected++;
            if (candidate.duplicateOf != null) duplicates++;
        }
        int translationPass = qc.translation == null ? 0 : qc.translation.passCount;
        int translationReview = qc.translation == null ? 0 : qc.translation.reviewCount;
        String aac = qc.aacDecoded != null && qc.aacDecoded.verification != null
                ? String.format(Locale.US, "AAC decoded %.3f LUFS-I • %.3f dBTP",
                qc.aacDecoded.verification.integratedLufs,
                qc.aacDecoded.verification.truePeakEstimateDbtp)
                : "AAC unavailable or conditional";
        return String.format(Locale.US,
                "%s\n\nSource %.3f LUFS-I • %.3f dBTP • LRA %.3f LU\n"
                        + "%d strategies rendered • %d duplicates skipped • %d safety rejections\n\n"
                        + "CONTINUITY REPAIR\n%s\n\n"
                        + "STEREO INTELLIGENCE\n%s\n%s\n\n"
                        + "ADVANCED SECTION / ARTIFACT / TRANSIENT INTELLIGENCE\n%s\n%s\n\n"
                        + "ADVANCED COMPARISON REFERENCE\n%s\n\n"
                        + "TARGETED CLEANUP / SHAPING DIAGNOSTICS\n%s\n%s\n\n"
                        + "V0.9 SOURCE GUARD\n%s\n\n"
                        + "V0.10 TECHNICAL REPAIRS / TRANSLATION AND REFERENCE DIAGNOSTICS\n%s\n%s\n\n"
                        + "STEM ASSIST\n%s\n%s\n\n"
                        + "RUN-START PROFILE SNAPSHOT — USED FOR THIS MASTER\n%s\n%s\n\n"
                        + "FULL AI WINNER\n%s\n"
                        + "PCM %.4f LUFS-I • %.3f dBTP • LRA %.3f LU\n"
                        + "Limiter %s • %.3f dB max • clips %d\n%s\n"
                        + "Translation simulations %d PASS / %d REVIEW\n"
                        + "Technical verdict %s\nSHA-256  %s",
                wavName, analysis.integratedLufs, analysis.truePeakEstimateDbtp,
                analysis.lraLu, rendered, duplicates, rejected,
                analysis.clickDropoutRepair == null
                        ? "Unavailable — untouched source retained"
                        : analysis.clickDropoutRepair.compactSummary(),
                analysis.stereoIntelligence == null
                        ? "Unavailable — core stereo fallback active"
                        : analysis.stereoIntelligence.compactSummary(),
                engine.intelligenceDecision,
                analysis.advancedIntelligence == null
                        ? "Unavailable — established competition remained active"
                        : analysis.advancedIntelligence.compactSummary(),
                engine.advancedDecision,
                engine.naturalReference == null
                        ? "Unavailable — run failed before reference selection"
                        : engine.naturalReference.strategy
                        + " • cleanup and limiter changes require measured benefit and preservation",
                analysis.professionalIntelligence == null
                        ? "Unavailable — earlier verified competition remained active"
                        : analysis.professionalIntelligence.compactSummary(),
                engine.professionalDecision,
                analysis.sourceGuard == null
                        ? "Unavailable"
                        : analysis.sourceGuard.compactSummary(),
                analysis.nextGenerationIntelligence == null
                        ? "Unavailable — v0.8 competition remained active"
                        : analysis.nextGenerationIntelligence.compactSummary(),
                engine.nextGenerationDecision,
                analysis.stemAssistStatus, engine.stemDecision,
                engine.preferenceProfile == null
                        ? "Preference Memory OFF — professional-neutral ranking"
                        : engine.preferenceProfile.compactSummary(),
                engine.albumProfile == null
                        ? "Album Consistency unavailable"
                        : engine.albumProfile.compactSummary(),
                engine.selected.strategy,
                qc.finalMasterVerification.integratedLufs,
                qc.finalMasterVerification.truePeakEstimateDbtp,
                qc.finalMasterVerification.lraLu,
                qc.finalCandidate.render.master.limiterProfile,
                qc.finalCandidate.render.master.maximumLimiterReductionDb,
                qc.finalMasterVerification.clippedSamples, aac,
                translationPass, translationReview, qc.combinedVerdict(),
                qc.finalMasterSha256) + "\n\n" + (qc.radioPreparation == null
                ? "Radio preparation checks unavailable" : qc.radioPreparation.compactSummary())
                + "\n" + (qc.passageCheck == null ? "" : qc.passageCheck.summary())
                + "\n" + engine.gentlerDecision
                + "\n\n" + (qc.releaseFormats == null ? "Release formats unavailable" : qc.releaseFormats.summary());
    }

    private boolean alternativeForSlot(int slot) {
        return ComparisonAudioStream.alternativeForSlot(slot, blindCheck.isChecked(), blindSwap);
    }

    private void playMaster() { playSlot(0); }
    private void playComparison() { playSlot(1); }

    private void playSlot(int slot) {
        if (!ensureComparisonPlayer()) return;
        playbackSlot = slot;
        player.mono(monoCheck.isChecked());
        player.play(alternativeForSlot(slot));
        status.setText("Playing " + (slot == 0 ? "A" : "B")
                + " at matched loudness. Switching keeps the same song position.");
    }

    private boolean ensureComparisonPlayer() {
        if (player != null) return true;
        if (qc == null || qc.finalMaster == null || !qc.finalMaster.isFile()) return false;
        final long generation = ++playbackGeneration;
        File alternative = comparisonCandidate == null ? qc.finalMaster : comparisonCandidate.file;
        AnalysisResult alternativeAnalysis = comparisonCandidate == null
                ? qc.finalMasterVerification : comparisonCandidate.render.master.verification;
        if (alternative == null || !alternative.isFile()) {
            status.setText("The comparison cache is unavailable. Your saved master remains available."); return false;
        }
        player = new ComparisonPlayer(this, qc.finalMaster, qc.finalMasterVerification,
                alternative, alternativeAnalysis, new ComparisonPlayer.Listener() {
            public void position(double seconds, boolean playing) {
                runOnUiThread(() -> {
                    if (generation != playbackGeneration || qc == null) return;
                    double duration = qc.finalMasterVerification.wav.durationSeconds;
                    playbackPosition.setText(playbackTime(seconds) + " / " + playbackTime(duration)
                            + (playing ? " • playing" : " • paused")
                            + (monoCheck.isChecked() ? " • mono" : " • stereo"));
                    if (!seeking) playbackSeek.setProgress((int)Math.round(1000 * seconds / duration));
                });
            }
            public void failure(String message) {
                runOnUiThread(() -> {
                    if (generation != playbackGeneration) return;
                    stopPlayback(); status.setText("Comparison playback stopped: " + message);
                });
            }
        });
        return true;
    }

    private void configureListeningMarkers() {
        listeningMarkers.removeAllViews();
        if (qc == null || qc.radioPreparation == null) return;
        if (!qc.radioPreparation.issues.isEmpty()) listeningMarkers.addView(text(
                "LISTEN AT FLAGGED MOMENTS", 13, Color.parseColor("#51D6C8")));
        for (RadioPreparation.Issue issue : qc.radioPreparation.issues) {
            Button marker = button("LISTEN " + playbackTime(issue.startSeconds) + " — "
                    + ("MONO_SECTION".equals(issue.kind) ? "mono check"
                    : "ENDING_TRANSIENT".equals(issue.kind) ? "ending transient" : "boundary check"), false);
            marker.setOnClickListener(view -> {
                if (ensureComparisonPlayer()) {
                    player.seekSeconds(Math.max(0, issue.startSeconds - 2));
                    playSlot(playbackSlot);
                }
            });
            listeningMarkers.addView(marker);
        }
    }

    private static String playbackTime(double seconds) {
        int s = (int)Math.max(0, Math.floor(seconds));
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }

    private void configurePreferenceButtons() {
        boolean available = qc != null && qc.finalCandidate != null
                && comparisonCandidate != null && comparisonCandidate.file != null
                && comparisonCandidate.file.exists() && preferenceComparisonId != null;
        boolean alreadyRecorded = available
                && preferenceProfile.hasComparison(preferenceComparisonId);
        compareButton.setEnabled(available);
        preferWinnerButton.setEnabled(available && !alreadyRecorded);
        preferRunnerButton.setEnabled(available && !alreadyRecorded);
        if (available) {
            boolean blind = blindCheck.isChecked();
            playButton.setText(blind ? "PLAY A" : "PLAY A — MASTER");
            compareButton.setText(blind ? "PLAY B" : "PLAY B — ALTERNATIVE");
            preferWinnerButton.setText(blind ? "I PREFER A" : "I PREFER THE MASTER");
            preferRunnerButton.setText(blind ? "I PREFER B" : "I PREFER THE ALTERNATIVE");
        } else {
            playButton.setText("PLAY MASTER"); compareButton.setText("NO DIFFERENT VERIFIED ALTERNATIVE");
        }
        if (alreadyRecorded) {
            status.setText("This exact level-matched comparison is already recorded in Preference Memory.");
        }
    }

    private void recordPreference(boolean preferWinner) {
        if (qc == null || qc.finalCandidate == null || comparisonCandidate == null
                || preferenceComparisonId == null) return;
        FullAiMasteringEngine.Candidate preferred = preferWinner
                ? qc.finalCandidate : comparisonCandidate;
        FullAiMasteringEngine.Candidate rejected = preferWinner
                ? comparisonCandidate : qc.finalCandidate;
        if (preferenceProfile.record(preferenceComparisonId,
                preferred.strategy, rejected.strategy)) {
            savePreferenceProfile();
            preferenceStatus.setText(currentPreferenceText());
            preferWinnerButton.setEnabled(false);
            preferRunnerButton.setEnabled(false);
            status.setText(blindCheck.isChecked() ? "Blind preference recorded. Your saved master is unchanged."
                    : "Preference recorded: " + preferred.strategy + ". This saved master is unchanged.");
        } else {
            status.setText("That exact A/B preference was already recorded or was not a valid comparison.");
        }
    }

    private void resetAlbumProfile() {
        AlbumConsistencyModel.Profile replacement = new AlbumConsistencyModel.Profile();
        replacement.active = true;
        albumProfile = replacement;
        albumModeCheck.setChecked(true);
        saveAlbumProfile();
        refreshAlbumStatus();
        addAlbumButton.setEnabled(qc != null && "PASS".equals(qc.technicalVerdict));
        status.setText("New empty current album profile started for future runs. This completed master and its QC retain the run-start snapshot.");
    }

    private void addWinnerToAlbum() {
        if (qc == null || qc.finalCandidate == null
                || !"PASS".equals(qc.technicalVerdict)) return;
        if (albumProfile.add(qc.finalCandidate)) {
            albumProfile.active = true;
            albumModeCheck.setChecked(true);
            saveAlbumProfile();
            refreshAlbumStatus();
            addAlbumButton.setEnabled(false);
            status.setText("Verified winner added to the current album profile. Future album-mode runs will use it; this completed master and its QC retain the run-start snapshot.");
        } else {
            addAlbumButton.setEnabled(false);
            status.setText("This exact verified master is already in the album profile.");
        }
    }

    private DennisPreferenceMemory.Profile loadPreferenceProfile() {
        SharedPreferences preferences = getSharedPreferences(
                DennisPreferenceMemory.PREFERENCES_NAME, MODE_PRIVATE);
        return DennisPreferenceMemory.Profile.fromJson(
                preferences.getString(DennisPreferenceMemory.PROFILE_KEY, ""));
    }

    private void savePreferenceProfile() {
        getSharedPreferences(DennisPreferenceMemory.PREFERENCES_NAME, MODE_PRIVATE)
                .edit().putString(DennisPreferenceMemory.PROFILE_KEY,
                        preferenceProfile.toJson()).apply();
    }

    private boolean loadPreferenceEnabled() {
        return getSharedPreferences(DennisPreferenceMemory.PREFERENCES_NAME,
                MODE_PRIVATE).getBoolean(USE_PREFERENCE_KEY, false);
    }

    private void savePreferenceEnabled(boolean enabled) {
        getSharedPreferences(DennisPreferenceMemory.PREFERENCES_NAME, MODE_PRIVATE)
                .edit().putBoolean(USE_PREFERENCE_KEY, enabled).apply();
    }

    private void confirmResetPreferenceProfile() {
        new AlertDialog.Builder(this)
                .setTitle("Clear Preference Memory?")
                .setMessage("This permanently removes the level-matched choices stored on this device. It does not change saved masters or album profiles.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (dialog, which) -> {
                    preferenceProfile = new DennisPreferenceMemory.Profile();
                    savePreferenceProfile();
                    if (preferenceStatus != null) {
                        preferenceStatus.setText(currentPreferenceText());
                    }
                    status.setText("Preference Memory cleared on this device. Professional-neutral ranking remains active.");
                })
                .show();
    }

    private AlbumConsistencyModel.Profile loadAlbumProfile() {
        SharedPreferences preferences = getSharedPreferences(
                AlbumConsistencyModel.PREFERENCES_NAME, MODE_PRIVATE);
        return AlbumConsistencyModel.Profile.fromJson(
                preferences.getString(AlbumConsistencyModel.PROFILE_KEY, ""));
    }

    private void saveAlbumProfile() {
        getSharedPreferences(AlbumConsistencyModel.PREFERENCES_NAME, MODE_PRIVATE)
                .edit().putString(AlbumConsistencyModel.PROFILE_KEY,
                        albumProfile.toJson()).apply();
    }

    private boolean loadAlbumEnabled() {
        return getSharedPreferences(AlbumConsistencyModel.PREFERENCES_NAME,
                MODE_PRIVATE).getBoolean(USE_ALBUM_KEY, false);
    }

    private void saveAlbumEnabled(boolean enabled) {
        getSharedPreferences(AlbumConsistencyModel.PREFERENCES_NAME,
                MODE_PRIVATE).edit().putBoolean(USE_ALBUM_KEY, enabled).apply();
    }

    private void refreshAlbumStatus() {
        if (albumStatus != null) albumStatus.setText(currentAlbumText());
    }

    private String currentPreferenceText() {
        boolean enabled = preferenceModeCheck != null
                ? preferenceModeCheck.isChecked() : loadPreferenceEnabled();
        return "LOCAL PREFERENCE MEMORY — "
                + (enabled ? "ON for safe tie-breaks" : "OFF; professional-neutral ranking")
                + "\n" + preferenceProfile.compactSummary()
                + "\nIt cannot override measured safety or quality gates.";
    }

    private String currentAlbumText() {
        boolean enabled = albumModeCheck != null
                ? albumModeCheck.isChecked() : loadAlbumEnabled();
        AlbumConsistencyModel.Profile display =
                AlbumConsistencyModel.Profile.fromJson(albumProfile.toJson());
        display.active = enabled;
        return "LOCAL ALBUM PROFILE — "
                + (enabled ? "ON for the next run" : "OFF; professional-neutral ranking")
                + "\n" + display.compactSummary()
                + "\nStored album data is retained while this control is off.";
    }

    private static FullAiMasteringEngine.Candidate chooseComparisonCandidate(
            FullAiMasteringEngine.Result engine,
            FullAiMasteringEngine.Candidate selected) {
        if (engine == null) return null;
        FullAiMasteringEngine.Candidate[] preferred = {
                selected == engine.gentlerCandidate ? engine.gentlerParent : engine.gentlerCandidate,
                engine.naturalReference,
                engine.transparent};
        for (FullAiMasteringEngine.Candidate c : preferred)
            if (c != null && c != selected && c.eligible && c.file != null && c.file.isFile()
                    && !c.render.master.verification.sourceSha256.equals(selected.render.master.verification.sourceSha256)) return c;
        for (FullAiMasteringEngine.Candidate candidate : engine.rankedEligible) {
            if (candidate != selected && candidate.rendered && candidate.eligible
                    && candidate.file != null && candidate.file.exists()
                    && !candidate.render.master.verification.sourceSha256.equals(selected.render.master.verification.sourceSha256)) return candidate;
        }
        return null;
    }

    private void cancelCurrentJob() {
        boolean cancelled = MasteringJobControl.cancel();
        Future<?> task = currentTask;
        if (!cancelled && task != null && !task.isDone()) {
            cancelled = task.cancel(true);
        }
        if (cancelled) {
            status.setText("Cancellation requested. Finishing the current safe file boundary…");
        }
    }

    private File createSessionDirectory() {
        cleanupSession();
        File root = new File(getCacheDir(), "dennis-full-ai-v091");
        deleteTree(root);
        File next = new File(root, "run-" + System.currentTimeMillis());
        if (!next.mkdirs()) {
            status.setText("Could not create the private Full AI cache.");
            return null;
        }
        long expectedFrames = 48000L * 60L * 5L;
        if (analysis != null) expectedFrames = analysis.wav.frames;
        // Peak cache budget after per-family loser pruning: retained fallback
        // chain + one active 32-bit premaster + the largest candidate family.
        long estimated = expectedFrames * 72L + 256L * 1024L * 1024L;
        long sourceBytes = documentSize(wavUri);
        if (sourceBytes > 0 && sourceBytes < Long.MAX_VALUE / 12L) {
            estimated = Math.max(estimated,
                    sourceBytes * 14L + 256L * 1024L * 1024L);
        }
        long stemBytes = documentSize(stemUri);
        if (stemBytes > 0 && stemBytes < Long.MAX_VALUE / 5L) {
            estimated += stemBytes * 4L;
        }
        long referenceBytes = documentSize(referenceUri);
        if (referenceBytes > 0 && referenceBytes < Long.MAX_VALUE / 3L) {
            estimated += referenceBytes * 2L;
        }
        if (next.getUsableSpace() < estimated) {
            deleteTree(next);
            status.setText("Not enough free storage for autonomous candidate competition. Approximately "
                    + humanBytes(estimated) + " may be required.");
            return null;
        }
        sessionDirectory = next;
        return next;
    }

    private void cleanupSession() {
        if (sessionDirectory != null) {
            deleteTree(sessionDirectory);
            sessionDirectory = null;
        }
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteTree(child);
        }
        if (!file.delete()) file.deleteOnExit();
    }

    private static void cleanupCandidateRenders(FullAiMasteringEngine.Result result,
                                                File... retainedFiles) {
        for (FullAiMasteringEngine.Candidate candidate : result.candidates) {
            File file = candidate.file;
            boolean retained = false;
            if (retainedFiles != null) {
                for (File keep : retainedFiles) {
                    if (file != null && keep != null && file.equals(keep)) {
                        retained = true;
                        break;
                    }
                }
            }
            if (file != null && file.exists() && !retained && !file.delete()) {
                file.deleteOnExit();
            }
        }
    }

    private void copyToUri(File source, Uri destination) throws IOException {
        postProgress("Copying verified file to selected location", 99);
        try (InputStream input = new FileInputStream(source);
             OutputStream output = requiredOutput(destination)) {
            copy(input, output);
        }
    }

    private void setBusy(boolean busy, String stage, int percent) {
        chooseWavButton.setEnabled(!busy);
        chooseStemButton.setEnabled(!busy);
        removeStemButton.setEnabled(!busy && stemUri != null);
        chooseReferenceButton.setEnabled(!busy);
        removeReferenceButton.setEnabled(!busy && referenceUri != null);
        preferenceModeCheck.setEnabled(!busy);
        resetPreferenceButton.setEnabled(!busy);
        albumModeCheck.setEnabled(!busy);
        resetAlbumButton.setEnabled(!busy);
        createButton.setEnabled(!busy && wavUri != null);
        cancelButton.setEnabled(busy);
        playButton.setEnabled(!busy && savedMasterUri != null);
        pauseButton.setEnabled(!busy && qc != null);
        playbackSeek.setEnabled(!busy && qc != null);
        monoCheck.setEnabled(!busy && qc != null);
        blindCheck.setEnabled(!busy && comparisonCandidate != null);
        listeningMarkers.setVisibility(busy ? View.GONE : View.VISIBLE);
        saveWav16Button.setEnabled(!busy && formatsReady());
        saveMp3Button.setEnabled(!busy && formatsReady());
        saveAacButton.setEnabled(!busy && qc != null && qc.finalAac != null);
        saveReportButton.setEnabled(!busy && releasePackage != null);
        saveConfidenceMapButton.setEnabled(!busy && releasePackage != null);
        savePackageButton.setEnabled(!busy && releasePackage != null);
        compareButton.setEnabled(!busy && comparisonCandidate != null
                && comparisonCandidate.file != null && comparisonCandidate.file.exists());
        boolean comparisonOpen = !busy && preferenceComparisonId != null
                && !preferenceProfile.hasComparison(preferenceComparisonId);
        preferWinnerButton.setEnabled(comparisonOpen);
        preferRunnerButton.setEnabled(comparisonOpen);
        addAlbumButton.setEnabled(!busy && qc != null
                && "PASS".equals(qc.technicalVerdict));
        status.setText(stage);
        if (percent >= 0) {
            progress.setIndeterminate(false);
            progress.setProgress(percent);
        } else {
            progress.setIndeterminate(true);
        }
    }

    private void postProgress(String stage, int percent) {
        synchronized (this) {
            if (stage.equals(lastStage) && percent == lastProgress) return;
            lastStage = stage;
            lastProgress = percent;
        }
        MasteringForegroundService.update(stage, percent);
        runOnUiThread(() -> {
            status.setText(stage);
            if (percent >= 0) {
                progress.setIndeterminate(false);
                progress.setProgress(percent);
            } else {
                progress.setIndeterminate(true);
            }
        });
    }

    private void clearRun(String message) {
        stopPlayback();
        pauseButton.setEnabled(false); playbackSeek.setEnabled(false); playbackSeek.setProgress(0);
        monoCheck.setEnabled(false); blindCheck.setEnabled(false); listeningMarkers.removeAllViews();
        playbackPosition.setText("0:00 / 0:00 • matched playback");
        analysis = null;
        brain = null;
        engine = null;
        qc = null;
        releasePackage = null;
        savedMasterUri = null;
        comparisonCandidate = null;
        preferenceComparisonId = null;
        progress.setProgress(0);
        results.setText("No Full AI run yet.");
        createButton.setEnabled(wavUri != null);
        chooseWavButton.setEnabled(true);
        chooseStemButton.setEnabled(true);
        removeStemButton.setEnabled(stemUri != null);
        chooseReferenceButton.setEnabled(true);
        removeReferenceButton.setEnabled(referenceUri != null);
        playButton.setEnabled(false);
        saveWav16Button.setEnabled(false);
        saveMp3Button.setEnabled(false);
        saveAacButton.setEnabled(false);
        saveReportButton.setEnabled(false);
        saveConfidenceMapButton.setEnabled(false);
        savePackageButton.setEnabled(false);
        compareButton.setEnabled(false);
        preferWinnerButton.setEnabled(false);
        preferRunnerButton.setEnabled(false);
        addAlbumButton.setEnabled(false);
        playButton.setText("PLAY FULL AI MASTER");
        compareButton.setText("PLAY LEVEL-MATCHED RUNNER-UP");
        status.setText(message);
    }

    private void stopPlayback() {
        playbackGeneration++;
        ComparisonPlayer prior = player; player = null;
        if (prior != null) prior.close();
    }

    private InputStream requiredInput(Uri uri) throws IOException {
        InputStream input = getContentResolver().openInputStream(uri);
        if (input == null) throw new IOException("Android could not open the selected file");
        return input;
    }

    private OutputStream requiredOutput(Uri uri) throws IOException {
        OutputStream output = getContentResolver().openOutputStream(uri, "w");
        if (output == null) throw new IOException("Android could not create the selected file");
        return output;
    }

    private void keepPermission(Uri uri, int flags) {
        int grant = flags & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try { getContentResolver().takePersistableUriPermission(uri, grant); }
        catch (SecurityException ignored) {}
    }

    private String displayName(Uri uri, String fallback) {
        try (android.database.Cursor cursor = getContentResolver().query(
                uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String name = cursor.getString(index);
                    if (name != null && !name.trim().isEmpty()) return name;
                }
            }
        } catch (Exception ignored) {}
        return fallback;
    }

    private long documentSize(Uri uri) {
        if (uri == null) return -1L;
        try (android.database.Cursor cursor = getContentResolver().query(
                uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (index >= 0 && !cursor.isNull(index)) return cursor.getLong(index);
            }
        } catch (Exception ignored) {}
        return -1L;
    }

    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[256 * 1024];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read > 0) output.write(buffer, 0, read);
            if (Thread.currentThread().isInterrupted()) throw new IOException("Operation cancelled");
        }
        output.flush();
    }

    private static String sha256(InputStream raw) throws IOException {
        try (InputStream input = raw) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[128 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) digest.update(buffer, 0, read);
            }
            StringBuilder hex = new StringBuilder(64);
            for (byte value : digest.digest()) {
                hex.append(String.format(Locale.US, "%02x", value & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException(impossible);
        }
    }

    private TextView section(String value) {
        TextView view = text(value, 13, Color.parseColor("#51D6C7"));
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setLetterSpacing(0.08f);
        view.setPadding(0, dp(24), 0, dp(9));
        return view;
    }

    private TextView fileLabel(String value) {
        TextView view = text(value, 14, Color.parseColor("#F5F7FA"));
        view.setPadding(dp(2), dp(10), dp(2), 0);
        return view;
    }

    private TextView text(String value, int sp, int colour) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(colour);
        view.setFontFeatureSettings("kern");
        return view;
    }

    private Button button(String value, boolean primary) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setAllCaps(false);
        button.setTextColor(primary ? Color.parseColor("#081313")
                : Color.parseColor("#E7EDF5"));
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(12));
        background.setColor(primary ? Color.parseColor("#51D6C7")
                : Color.parseColor("#202A36"));
        background.setStroke(dp(1), primary ? Color.parseColor("#51D6C7")
                : Color.parseColor("#3A4756"));
        button.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        params.setMargins(0, dp(6), 0, dp(2));
        button.setLayoutParams(params);
        return button;
    }

    private View panel(TextView content) {
        LinearLayout panel = new LinearLayout(this);
        panel.setPadding(dp(14), dp(13), dp(14), dp(13));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.parseColor("#141C25"));
        background.setCornerRadius(dp(12));
        background.setStroke(dp(1), Color.parseColor("#263342"));
        panel.setBackground(background);
        panel.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(10), 0, 0);
        panel.setLayoutParams(params);
        return panel;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String shortBaseName(String name) {
        String base = name == null ? "Song" : name.replaceFirst("(?i)\\.wav$", "")
                .replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (base.isEmpty()) base = "Song";
        if (base.length() > 28) base = base.substring(0, 28).replaceAll("_+$", "");
        return base;
    }

    private static String humanBytes(long bytes) {
        double gib = bytes / (1024.0 * 1024.0 * 1024.0);
        if (gib >= 1.0) return String.format(Locale.US, "%.1f GB", gib);
        return String.format(Locale.US, "%.0f MB", bytes / (1024.0 * 1024.0));
    }

    private static String cleanError(Exception error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName() : message;
    }

    @Override protected void onDestroy() {
        stopPlayback();
        MasteringJobControl.detach(jobListener);
        if (MasteringJobControl.running()) {
            worker.shutdown();
        } else {
            worker.shutdownNow();
        }
        super.onDestroy();
    }
}
