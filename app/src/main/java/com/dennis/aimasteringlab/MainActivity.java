package com.dennis.aimasteringlab;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int PICK_WAV = 101;
    private static final int PICK_STEMS = 102;
    private static final int PICK_PLAN = 103;
    private static final int SAVE_REPORT = 104;
    private static final int SAVE_MASTER = 105;
    private static final int SAVE_RELEASE = 106;
    private static final int SAVE_AI_MASTER = 107;
    private static final int SAVE_VOCAL_MASTER = 108;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Uri wavUri;
    private Uri stemsUri;
    private AnalysisResult analysis;
    private MasterRenderer.Result renderResult;
    private MasteringPlan importedPlan;
    private String wavName = "No stereo WAV selected";
    private String stemsName = "No stem ZIP selected (optional)";

    private TextView wavLabel;
    private TextView stemsLabel;
    private TextView status;
    private TextView results;
    private ProgressBar progress;
    private Button analyzeButton;
    private Button aiRenderButton;
    private Button vocalRenderButton;
    private Button importPlanButton;
    private Button renderButton;
    private Button exportButton;
    private Button releaseButton;
    private int lastProgress = Integer.MIN_VALUE;
    private String lastStage = "";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(buildUi());
    }

    private View buildUi() {
        int pad = dp(18);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.parseColor("#101216"));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(18), pad, dp(36));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = text("DENNIS MASTERING STUDIO", 25, Color.parseColor("#F2F4F7"));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setLetterSpacing(0.06f);
        root.addView(title);
        TextView version = text("Offline adaptive release engineering beta • v0.4.0", 13,
                Color.parseColor("#E7B64A"));
        version.setPadding(0, dp(3), 0, dp(14));
        root.addView(version);

        TextView intro = text("Large audio stays on this phone. v0.4.0 adds learned song-section mapping "
                        + "and a stem-aware Vocal Humanity A/B editor while retaining the per-track diagnostic model, "
                        + "strictly capped correction tests, guarded mastering, "
                        + "stem reconstruction evidence, 24-bit WAV and consumer deliveries, decoded-codec QC, "
                        + "translation simulations and complete release packaging.",
                15, Color.parseColor("#AAB2BF"));
        intro.setLineSpacing(0, 1.15f);
        root.addView(panel(intro));

        root.addView(section("1  SOURCE"));
        Button chooseWav = button("SELECT AUTHORITATIVE STEREO WAV", true);
        chooseWav.setOnClickListener(v -> openWavPicker());
        root.addView(chooseWav);
        wavLabel = fileLabel(wavName);
        root.addView(wavLabel);

        Button chooseStems = button("SELECT STEM ZIP (OPTIONAL)", false);
        chooseStems.setOnClickListener(v -> openStemPicker());
        root.addView(chooseStems);
        stemsLabel = fileLabel(stemsName);
        root.addView(stemsLabel);

        root.addView(section("2  ANALYSE"));
        analyzeButton = button("RUN TECHNICAL + AI DIAGNOSTIC", true);
        analyzeButton.setOnClickListener(v -> startAnalysis());
        root.addView(analyzeButton);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(0);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(8));
        pp.setMargins(0, dp(12), 0, dp(8));
        root.addView(progress, pp);

        status = text("Ready", 14, Color.parseColor("#AAB2BF"));
        root.addView(status);
        results = text("No measurements yet.", 15, Color.parseColor("#F2F4F7"));
        results.setLineSpacing(dp(3), 1.08f);
        root.addView(panel(results));

        root.addView(section("3  AI DIAGNOSTIC / A-B TEST"));
        TextView aiBridge = text("The diagnostic and section-learning passes make no audio changes. The mix test applies only "
                        + "event-controlled attenuation. When a reliable lead stem is aligned, the Vocal Humanity test adds only "
                        + "a centered processed-minus-original stem delta with sub-dB phrase rides and event de-essing. Both are loudness-matched A/B tests.",
                14, Color.parseColor("#AAB2BF"));
        aiBridge.setLineSpacing(0, 1.12f);
        root.addView(aiBridge);
        aiRenderButton = button("RENDER AI-GUIDED TEST MASTER", false);
        aiRenderButton.setEnabled(false);
        aiRenderButton.setOnClickListener(v -> requestAiMasterDestination());
        root.addView(aiRenderButton);
        vocalRenderButton = button("RENDER VOCAL HUMANITY TEST MASTER", false);
        vocalRenderButton.setEnabled(false);
        vocalRenderButton.setOnClickListener(v -> requestVocalMasterDestination());
        root.addView(vocalRenderButton);

        root.addView(section("4  OPTIONAL CHAT-ASSISTED PLAN"));
        TextView bridge = text("Export the compact JSON report to this chat when engineering judgment "
                        + "is needed. A checksum-bound flat-gain plan can then be imported without uploading the full mix.",
                14, Color.parseColor("#AAB2BF"));
        bridge.setLineSpacing(0, 1.12f);
        root.addView(bridge);
        importPlanButton = button("IMPORT MASTERING PLAN JSON", false);
        importPlanButton.setEnabled(false);
        importPlanButton.setOnClickListener(v -> openPlanPicker());
        root.addView(importPlanButton);

        root.addView(section("5  MASTER, REPORT AND PACKAGE"));
        renderButton = button("RENDER VERIFIED 24-BIT MASTER", true);
        renderButton.setEnabled(false);
        renderButton.setOnClickListener(v -> requestMasterDestination());
        root.addView(renderButton);
        exportButton = button("EXPORT QC / CHAT HANDOFF JSON", false);
        exportButton.setEnabled(false);
        exportButton.setOnClickListener(v -> requestReportDestination());
        root.addView(exportButton);
        releaseButton = button("BUILD COMPLETE RELEASE ZIP", true);
        releaseButton.setEnabled(false);
        releaseButton.setOnClickListener(v -> requestReleaseDestination());
        root.addView(releaseButton);

        TextView warning = text("True peak and playback devices are measured offline using validated meters "
                        + "and response simulations; no claim is made that physical speakers were heard. "
                        + "Production/A&R scoring is advisory and always requires human artistic review.",
                13, Color.parseColor("#FFB0A9"));
        warning.setPadding(0, dp(18), 0, 0);
        warning.setLineSpacing(0, 1.12f);
        root.addView(warning);
        return scroll;
    }

    private void openWavPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"audio/wav", "audio/x-wav", "audio/wave", "application/octet-stream"});
        startActivityForResult(intent, PICK_WAV);
    }

    private void openStemPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        startActivityForResult(intent, PICK_STEMS);
    }

    private void openPlanPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        startActivityForResult(intent, PICK_PLAN);
    }

    private void requestReportDestination() {
        if (analysis == null) return;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, shortBaseName(wavName) + "_DMS_QC.json");
        startActivityForResult(intent, SAVE_REPORT);
    }

    private void requestMasterDestination() {
        if (analysis == null) return;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/wav");
        intent.putExtra(Intent.EXTRA_TITLE, shortBaseName(wavName) + "_DMS24.wav");
        startActivityForResult(intent, SAVE_MASTER);
    }

    private void requestAiMasterDestination() {
        if (!canAiRender()) return;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/wav");
        intent.putExtra(Intent.EXTRA_TITLE, shortBaseName(wavName) + "_AI24_Test.wav");
        startActivityForResult(intent, SAVE_AI_MASTER);
    }

    private void requestVocalMasterDestination() {
        if (!canVocalRender()) return;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/wav");
        intent.putExtra(Intent.EXTRA_TITLE, shortBaseName(wavName) + "_VocalAI24_Test.wav");
        startActivityForResult(intent, SAVE_VOCAL_MASTER);
    }

    private void requestReleaseDestination() {
        if (analysis == null) return;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_TITLE, shortBaseName(wavName) + "_Complete.zip");
        startActivityForResult(intent, SAVE_RELEASE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == PICK_WAV) {
            keepPermission(uri, data.getFlags());
            wavUri = uri;
            wavName = displayName(uri, "Selected WAV");
            wavLabel.setText(wavName);
            clearAnalysis("New source selected. Run analysis.");
        } else if (requestCode == PICK_STEMS) {
            keepPermission(uri, data.getFlags());
            stemsUri = uri;
            stemsName = displayName(uri, "Selected stems.zip");
            stemsLabel.setText(stemsName);
            clearAnalysis("Stem archive changed. Run analysis again.");
        } else if (requestCode == PICK_PLAN) {
            importPlan(uri);
        } else if (requestCode == SAVE_REPORT) {
            saveReport(uri);
        } else if (requestCode == SAVE_MASTER) {
            startRender(uri);
        } else if (requestCode == SAVE_AI_MASTER) {
            startAiRender(uri);
        } else if (requestCode == SAVE_VOCAL_MASTER) {
            startVocalRender(uri);
        } else if (requestCode == SAVE_RELEASE) {
            startReleaseBuild(uri);
        }
    }

    private void startAnalysis() {
        if (wavUri == null) {
            status.setText("Select the authoritative stereo WAV first.");
            return;
        }
        setBusy(true, "Starting analysis", 0);
        final Uri source = wavUri;
        final Uri stems = stemsUri;
        final String sourceName = wavName;
        worker.submit(() -> {
            try {
                AnalysisResult measured = AudioAnalyzer.analyze(sourceName,
                        () -> requiredInput(source), this::postProgress);
                try {
                    measured.sections = LearnedSectionMapper.analyze(
                            () -> requiredInput(source), measured, this::postProgress);
                } catch (Exception sectionError) {
                    // AudioAnalyzer's energy-derived candidates remain the safe fallback.
                }
                if (stems != null) {
                    postProgress("Preparing stem archive", -1);
                    long stemBytes = displaySize(stems);
                    long stemSpace = stemBytes > 0 ? stemBytes + 64L * 1024L * 1024L : 0;
                    if (stemSpace > 0 && workingCacheDir().getUsableSpace() < stemSpace) {
                        throw new IOException("Not enough temporary phone storage for the stem archive; need about "
                                + humanBytes(stemSpace));
                    }
                    File stemCache = File.createTempFile("dms-stems-", ".zip", workingCacheDir());
                    try {
                        try (InputStream in = requiredInput(stems);
                             OutputStream out = new FileOutputStream(stemCache)) {
                            copy(in, out);
                        }
                        measured.stems.addAll(StemInventory.inspect(stemCache, this::postProgress));
                        StemInventory.compareAgainstMix(measured);
                        measured.stemReconstruction = StemReconstruction.analyze(
                                () -> requiredInput(source), measured, stemCache, this::postProgress);
                        try {
                            measured.vocalHumanity = VocalHumanityModel.analyze(
                                    stemCache, measured, this::postProgress);
                        } catch (Exception vocalError) {
                            measured.vocalHumanity = new VocalHumanityModel.Result();
                            measured.vocalHumanity.status = "Vocal analysis did not complete: "
                                    + cleanError(vocalError);
                            measured.vocalHumanity.safetyNotes.add(
                                    "No vocal edit was enabled after the analysis error.");
                        }
                    } finally {
                        if (stemCache.exists() && !stemCache.delete()) stemCache.deleteOnExit();
                    }
                }
                try {
                    measured.aiDiagnostics = AdaptiveDiagnosticModel.analyze(
                            () -> requiredInput(source), measured, this::postProgress);
                } catch (Exception aiError) {
                    measured.aiDiagnostics = AdaptiveDiagnosticModel.unavailable(
                            "AI diagnostic did not complete: " + cleanError(aiError), measured);
                }
                analysis = measured;
                renderResult = null;
                importedPlan = null;
                runOnUiThread(() -> {
                    setBusy(false, "Analysis complete", 100);
                    results.setText(summary(measured));
                    if (measured.noRenderNeeded) {
                        renderButton.setText("NO RENDER NEEDED — KEEP SOURCE");
                        status.setText(measured.noRenderReason + ". Source retained unchanged.");
                    } else {
                        renderButton.setText("RENDER VERIFIED 24-BIT MASTER");
                    }
                    renderButton.setEnabled(canRender());
                    aiRenderButton.setEnabled(canAiRender());
                    vocalRenderButton.setEnabled(canVocalRender());
                    importPlanButton.setEnabled(true);
                    exportButton.setEnabled(true);
                    releaseButton.setEnabled(canBuildRelease());
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setBusy(false, "Analysis failed", 0);
                    status.setText("Analysis failed: " + cleanError(e));
                });
            }
        });
    }

    private void importPlan(Uri uri) {
        if (analysis == null) return;
        setBusy(true, "Reading mastering plan", -1);
        worker.submit(() -> {
            try {
                String json = readText(requiredInput(uri), 1024 * 1024);
                MasteringPlan plan = MasteringPlan.parse(json);
                if (!analysis.sourceSha256.equalsIgnoreCase(plan.sourceSha256)) {
                    throw new IOException("The plan checksum does not match the selected source WAV");
                }
                if (analysis.truePeakEstimateDbtp + plan.gainDb > -1.20 + 0.001) {
                    throw new IOException("The plan requests gain above the true-peak safety margin");
                }
                importedPlan = plan;
                runOnUiThread(() -> {
                    setBusy(false, "Plan imported", 100);
                    renderButton.setText(canRender()
                            ? "RENDER VERIFIED 24-BIT MASTER" : "NO RENDER NEEDED — KEEP SOURCE");
                    renderButton.setEnabled(canRender());
                    status.setText(String.format(Locale.US,
                            "Plan ready: %.3f dB flat gain • %s", plan.gainDb, plan.note));
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setBusy(false, "Plan rejected", 0);
                    status.setText("Plan rejected: " + cleanError(e));
                });
            }
        });
    }

    private void startRender(Uri destination) {
        if (analysis == null || wavUri == null) return;
        if (!canRender()) {
            status.setText("No render needed. " + analysis.noRenderReason + ".");
            return;
        }
        double gainDb = importedPlan != null ? importedPlan.gainDb : analysis.appliedSafeGainDb;
        File workDir = new File(workingCacheDir(), "render-cache");
        if (!workDir.exists() && !workDir.mkdirs()) {
            status.setText("Could not create the private render cache.");
            return;
        }
        long estimated = analysis.wav.frames * analysis.wav.channels * 3L + 128L * 1024L * 1024L;
        if (workDir.getUsableSpace() < estimated) {
            status.setText("Not enough free phone storage. Approximately "
                    + humanBytes(estimated) + " is required.");
            return;
        }
        File temp = new File(workDir, "verified-master-" + System.currentTimeMillis() + ".wav");
        setBusy(true, "Rendering safe master", 0);
        final Uri sourceUri = wavUri;
        worker.submit(() -> {
            try {
                MasterRenderer.Result made = MasterRenderer.render(
                        () -> requiredInput(sourceUri), analysis, gainDb, temp, this::postProgress);
                postProgress("Copying verified master to selected location", 99);
                try (InputStream in = new FileInputStream(temp);
                     OutputStream out = requiredOutput(destination)) {
                    copy(in, out);
                }
                AnalysisResult destinationCheck = AudioAnalyzer.analyze(
                        displayName(destination, temp.getName()),
                        () -> requiredInput(destination), this::postProgress);
                if (!made.verification.sourceSha256.equals(destinationCheck.sourceSha256)) {
                    throw new IOException("The saved copy checksum did not match the verified render");
                }
                made.verification = destinationCheck;
                made.savedFileName = destinationCheck.sourceName;
                renderResult = made;
                if (!temp.delete()) temp.deleteOnExit();
                runOnUiThread(() -> {
                    setBusy(false, "Verified master saved", 100);
                    results.setText(summary(analysis) + "\n\nRENDERED OUTPUT\n" + renderSummary(made));
                    exportButton.setEnabled(true);
                    status.setText("Verified 24-bit WAV saved. Export the QC JSON next.");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setBusy(false, "Render failed", 0);
                    status.setText("Render failed: " + cleanError(e));
                });
            }
        });
    }

    private void startAiRender(Uri destination) {
        if (analysis == null || wavUri == null || !canAiRender()) {
            status.setText("No local AI finding passed the strict correction gate. Keep the diagnostic-only result.");
            return;
        }
        File workDir = new File(workingCacheDir(), "ai-render-cache");
        if (!workDir.exists() && !workDir.mkdirs()) {
            status.setText("Could not create the private AI render cache.");
            return;
        }
        long estimated = analysis.wav.frames * analysis.wav.channels * 7L
                + 160L * 1024L * 1024L;
        if (workDir.getUsableSpace() < estimated) {
            status.setText("Not enough free phone storage for the temporary 32-bit AI test pass. Approximately "
                    + humanBytes(estimated) + " is required.");
            return;
        }
        File temp = new File(workDir, "verified-ai-master-" + System.currentTimeMillis() + ".wav");
        setBusy(true, "Rendering AI-guided A/B test master", 0);
        final Uri sourceUri = wavUri;
        worker.submit(() -> {
            try {
                MasterRenderer.Result made = AiGuidedRenderer.render(
                        () -> requiredInput(sourceUri), analysis, temp, workDir, this::postProgress);
                postProgress("Copying verified AI test master", 99);
                try (InputStream in = new FileInputStream(temp);
                     OutputStream out = requiredOutput(destination)) {
                    copy(in, out);
                }
                AnalysisResult destinationCheck = AudioAnalyzer.analyze(
                        displayName(destination, temp.getName()),
                        () -> requiredInput(destination), this::postProgress);
                if (!made.verification.sourceSha256.equals(destinationCheck.sourceSha256)) {
                    throw new IOException("The saved AI test copy checksum did not match the verified render");
                }
                made.verification = destinationCheck;
                made.savedFileName = destinationCheck.sourceName;
                renderResult = made;
                if (!temp.delete()) temp.deleteOnExit();
                runOnUiThread(() -> {
                    setBusy(false, "Verified AI test master saved", 100);
                    results.setText(summary(analysis) + "\n\nAI-GUIDED A/B OUTPUT\n" + renderSummary(made));
                    exportButton.setEnabled(true);
                    status.setText("AI-guided test WAV saved. Compare it level-matched with the standard master; it is not automatically promoted to release master.");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setBusy(false, "AI-guided test failed", 0);
                    status.setText("AI-guided test failed: " + cleanError(e));
                });
            } finally {
                if (temp.exists() && !temp.delete()) temp.deleteOnExit();
            }
        });
    }

    private void startVocalRender(Uri destination) {
        if (analysis == null || wavUri == null || stemsUri == null || !canVocalRender()) {
            status.setText("No reliable aligned lead-vocal edit passed every safety gate.");
            return;
        }
        File workDir = new File(workingCacheDir(), "vocal-render-cache");
        if (!workDir.exists() && !workDir.mkdirs()) {
            status.setText("Could not create the private vocal render cache.");
            return;
        }
        long stemBytes = displaySize(stemsUri);
        long estimated = analysis.wav.frames * analysis.wav.channels * 7L
                + Math.max(0L, stemBytes) + 192L * 1024L * 1024L;
        if (workDir.getUsableSpace() < estimated) {
            status.setText("Not enough free phone storage for the stem-delta A/B pass. Approximately "
                    + humanBytes(estimated) + " is required.");
            return;
        }
        File stemCache = new File(workDir, "vocal-stems-" + System.currentTimeMillis() + ".zip");
        File temp = new File(workDir, "verified-vocal-master-" + System.currentTimeMillis() + ".wav");
        setBusy(true, "Rendering Vocal Humanity A/B test master", 0);
        final Uri sourceUri = wavUri;
        final Uri sourceStemsUri = stemsUri;
        worker.submit(() -> {
            try {
                postProgress("Preparing validated stem archive for vocal delta", -1);
                try (InputStream input = requiredInput(sourceStemsUri);
                     OutputStream output = new FileOutputStream(stemCache)) {
                    copy(input, output);
                }
                MasterRenderer.Result made = VocalGuidedRenderer.render(
                        () -> requiredInput(sourceUri), analysis, stemCache,
                        temp, workDir, this::postProgress);
                postProgress("Copying verified Vocal Humanity test master", 99);
                try (InputStream input = new FileInputStream(temp);
                     OutputStream output = requiredOutput(destination)) {
                    copy(input, output);
                }
                AnalysisResult destinationCheck = AudioAnalyzer.analyze(
                        displayName(destination, temp.getName()),
                        () -> requiredInput(destination), this::postProgress);
                if (!made.verification.sourceSha256.equals(destinationCheck.sourceSha256)) {
                    throw new IOException("The saved vocal test copy checksum did not match the verified render");
                }
                made.verification = destinationCheck;
                made.savedFileName = destinationCheck.sourceName;
                renderResult = made;
                runOnUiThread(() -> {
                    setBusy(false, "Verified Vocal Humanity test saved", 100);
                    results.setText(summary(analysis) + "\n\nVOCAL HUMANITY A/B OUTPUT\n"
                            + renderSummary(made));
                    exportButton.setEnabled(true);
                    status.setText("Vocal Humanity test WAV saved. Compare it level-matched with the standard master; it is not automatically promoted.");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setBusy(false, "Vocal Humanity test failed", 0);
                    status.setText("Vocal Humanity test failed: " + cleanError(e));
                });
            } finally {
                if (temp.exists() && !temp.delete()) temp.deleteOnExit();
                if (stemCache.exists() && !stemCache.delete()) stemCache.deleteOnExit();
            }
        });
    }

    private void startReleaseBuild(Uri destination) {
        if (analysis == null || wavUri == null) return;
        if (!canBuildRelease()) {
            status.setText("Complete packaging currently requires a verified 48 kHz stereo source.");
            return;
        }
        long sourceBytes = displaySize(wavUri);
        long estimated = sourceBytes > 0
                ? Math.min(Long.MAX_VALUE / 2, sourceBytes * 5L + 384L * 1024L * 1024L)
                : 2L * 1024L * 1024L * 1024L;
        File cache = workingCacheDir();
        if (cache.getUsableSpace() < estimated) {
            status.setText("Not enough temporary phone storage for complete packaging. Approximately "
                    + humanBytes(estimated) + " is required; no files were changed.");
            return;
        }
        File work = new File(cache, "release-v040-" + System.currentTimeMillis());
        if (!work.mkdirs()) {
            status.setText("Could not create the private release work directory.");
            return;
        }
        final Uri sourceUri = wavUri;
        final AnalysisResult sourceAnalysis = analysis;
        final String base = shortBaseName(wavName);
        setBusy(true, "Preparing complete release package", 0);
        worker.submit(() -> {
            try {
                File localSource = new File(work, "authoritative-source.wav");
                try (InputStream input = requiredInput(sourceUri);
                     OutputStream output = new FileOutputStream(localSource)) {
                    copy(input, output);
                }
                String localHash = Hashing.sha256(localSource);
                if (!localHash.equalsIgnoreCase(sourceAnalysis.sourceSha256)) {
                    throw new IOException("The locally prepared source did not match the analysed WAV checksum");
                }
                ReleaseArtifacts built = ReleasePackageBuilder.build(localSource,
                        sourceAnalysis, work, base, this::postProgress);
                postProgress("Copying verified complete ZIP to selected location", 99);
                try (InputStream input = new FileInputStream(built.completeZip);
                     OutputStream output = requiredOutput(destination)) {
                    copy(input, output);
                }
                String savedHash = Hashing.sha256(requiredInput(destination));
                String builtHash = Hashing.sha256(built.completeZip);
                if (!builtHash.equals(savedHash)) {
                    throw new IOException("The saved complete ZIP checksum did not match the verified package");
                }
                String savedName = displayName(destination, built.completeZip.getName());
                String codecStatus = "MP3 " + (built.mp3.available ? "included" : "unavailable")
                        + " • AAC " + (built.aac.available ? "included" : "unavailable");
                String finalText = String.format(Locale.US,
                        "%s\n\nComplete release ZIP saved and checksum-verified.\n"
                                + "Technical gate: %s (%d/100)\n"
                                + "Production/A&R proxy: %s (%d/100; human review required)\n"
                                + "%s\nSHA-256: %s",
                        savedName, built.assessment.technicalVerdict,
                        built.assessment.technicalScore,
                        built.assessment.productionArBand,
                        built.assessment.productionArScore,
                        codecStatus, savedHash);
                runOnUiThread(() -> {
                    setBusy(false, "Complete release package saved", 100);
                    status.setText("Complete release ZIP saved and verified.");
                    results.setText(summary(sourceAnalysis) + "\n\nRELEASE PACKAGE\n" + finalText);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setBusy(false, "Release packaging failed", 0);
                    status.setText("Release packaging failed: " + cleanError(e));
                });
            } finally {
                deleteTree(work);
            }
        });
    }

    private void saveReport(Uri destination) {
        if (analysis == null) return;
        setBusy(true, "Writing QC report", -1);
        worker.submit(() -> {
            try {
                byte[] data = ReportWriter.create(analysis, renderResult)
                        .getBytes(StandardCharsets.UTF_8);
                try (OutputStream out = requiredOutput(destination)) {
                    out.write(data);
                }
                runOnUiThread(() -> {
                    setBusy(false, "QC report saved", 100);
                    status.setText("QC / chat handoff JSON saved.");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setBusy(false, "Report failed", 0);
                    status.setText("Report failed: " + cleanError(e));
                });
            }
        });
    }

    private InputStream requiredInput(Uri uri) throws IOException {
        InputStream in = getContentResolver().openInputStream(uri);
        if (in == null) throw new IOException("Android could not open the selected file");
        return in;
    }

    private OutputStream requiredOutput(Uri uri) throws IOException {
        OutputStream out = getContentResolver().openOutputStream(uri, "w");
        if (out == null) throw new IOException("Android could not create the selected file");
        return out;
    }

    private void postProgress(String stage, int percent) {
        synchronized (this) {
            if (stage.equals(lastStage) && percent == lastProgress) return;
            lastStage = stage;
            lastProgress = percent;
        }
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

    private void setBusy(boolean busy, String stage, int percent) {
        analyzeButton.setEnabled(!busy);
        renderButton.setEnabled(!busy && canRender());
        aiRenderButton.setEnabled(!busy && canAiRender());
        vocalRenderButton.setEnabled(!busy && canVocalRender());
        exportButton.setEnabled(!busy && analysis != null);
        importPlanButton.setEnabled(!busy && analysis != null);
        releaseButton.setEnabled(!busy && canBuildRelease());
        status.setText(stage);
        if (percent >= 0) {
            progress.setIndeterminate(false);
            progress.setProgress(percent);
        } else {
            progress.setIndeterminate(true);
        }
    }

    private void clearAnalysis(String message) {
        analysis = null;
        renderResult = null;
        importedPlan = null;
        renderButton.setText("RENDER VERIFIED 24-BIT MASTER");
        renderButton.setEnabled(false);
        aiRenderButton.setEnabled(false);
        vocalRenderButton.setEnabled(false);
        importPlanButton.setEnabled(false);
        exportButton.setEnabled(false);
        releaseButton.setEnabled(false);
        progress.setProgress(0);
        results.setText("No measurements yet.");
        status.setText(message);
    }

    private String summary(AnalysisResult r) {
        int wavStems = 0;
        int stemErrors = 0;
        for (StemInventory.Item item : r.stems) {
            if (item.wav) wavStems++;
            if (item.error != null) stemErrors++;
        }
        String path = r.noRenderNeeded
                ? r.noRenderReason + ": no render and no additional dither required."
                : r.limiterRequired && r.limiterAllowed
                ? String.format(Locale.US,
                "Exact target requires guarded limiting: estimated %.2f dB reduction within the derived %.2f dB dynamics allowance.",
                r.estimatedLimiterReductionDb, r.limiterReductionAllowanceDb)
                : r.ceilingLimited
                ? String.format(Locale.US,
                "Exact -14.00 would require excessive limiting. Dynamics-protected result: %.2f LUFS-I / %.2f dBTP.",
                r.predictedOutputLufs, r.predictedOutputTruePeakDbtp)
                : String.format(Locale.US,
                "Transparent gain path available: %+.3f dB -> %.2f LUFS-I / %.2f dBTP estimated.",
                r.appliedSafeGainDb, r.predictedOutputLufs, r.predictedOutputTruePeakDbtp);
        String stemLine;
        if (stemsUri == null) {
            stemLine = "Stems: not supplied";
        } else if (r.commonStemTailShortfallSeconds > 0.0) {
            stemLine = String.format(Locale.US,
                    "Stem ZIP: %d entries • %d WAV • %d CRC verified • stems end %.3f s before mix; preserve mix tail\nReconstruction: %s",
                    r.stems.size(), wavStems, r.crcVerifiedStemCount,
                    r.commonStemTailShortfallSeconds,
                    StemReconstruction.compact(r.stemReconstruction));
        } else {
            stemLine = String.format(Locale.US,
                    "Stem ZIP: %d entries • %d WAV • %d CRC verified • %d duration mismatches • %d header errors\nReconstruction: %s",
                    r.stems.size(), wavStems, r.crcVerifiedStemCount,
                    r.stemDurationMismatchCount, stemErrors,
                    StemReconstruction.compact(r.stemReconstruction));
        }
        String aiLine = r.aiDiagnostics == null
                ? "AI diagnostic: not run"
                : "AI diagnostic: " + r.aiDiagnostics.compactSummary()
                + "\nStem trust: " + r.aiDiagnostics.stemTrustAssessment;
        String sectionLine = r.sections == null
                ? "Section map: unavailable"
                : String.format(Locale.US,
                "Section map: %s • %d learned states • %d boundary candidates • confidence %.2f",
                r.sections.learnedMapping ? "learned" : "energy fallback",
                r.sections.learnedStates, r.sections.boundaryCandidates,
                r.sections.mappingConfidence);
        String vocalLine = r.vocalHumanity == null
                ? "Vocal Humanity: stems not supplied or analysis unavailable"
                : "Vocal Humanity: " + r.vocalHumanity.compactSummary();
        return String.format(Locale.US,
                "%s\n%d Hz • %d-bit %s • %d ch • %s\n\n"
                        + "LUFS-I  %.2f\nLRA  %.2f LU\nSample peak  %.2f dBFS\n"
                        + "True peak estimate  %.2f dBTP\nStereo correlation  %.3f\n"
                        + "Below 160 Hz correlation  %.3f\nClipped samples  %d\n"
                        + "Internal zero interruptions  %d\n\n%s\n%s\n\n%s\n\n"
                        + "%s\n%s\n\nA formal technical gate and advisory production/A&R proxy are created during complete packaging.",
                r.sourceName, r.wav.sampleRate, r.wav.bitsPerSample, r.wav.formatName(),
                r.wav.channels, duration(r.wav.durationSeconds), r.integratedLufs, r.lraLu,
                r.samplePeakDbfs, r.truePeakEstimateDbtp, r.stereoCorrelation,
                r.lowBandCorrelation, r.clippedSamples, r.internalZeroRuns, path, stemLine, aiLine,
                sectionLine, vocalLine);
    }

    private String renderSummary(MasterRenderer.Result r) {
        AnalysisResult v = r.verification;
        String ai = r.aiGuided
                ? String.format(Locale.US,
                "\nAI-guided test  YES • %d events • max cut %.2f dB\n%s\nCorrected pre-master  %.2f LUFS-I / %.2f dBTP",
                r.aiCorrectionEvents, r.maximumAiCorrectionDb, r.aiCorrectionSummary,
                r.aiCorrectedPremasterLufs, r.aiCorrectedPremasterTruePeakDbtp)
                : r.vocalHumanityGuided
                ? String.format(Locale.US,
                "\nVocal Humanity test  YES • %s\n%s\nCorrected pre-master  %.2f LUFS-I / %.2f dBTP",
                r.vocalStemPath, r.vocalCorrectionSummary,
                r.aiCorrectedPremasterLufs, r.aiCorrectedPremasterTruePeakDbtp)
                : "\nAI-guided test  NO (standard mastering path)";
        return String.format(Locale.US,
                "24-bit PCM • %d Hz • %s\nGain  %+.3f dB\nLimiter  %s • max reduction %.3f dB\n"
                        + "Verified LUFS-I  %.2f\nVerified TP estimate  %.2f dBTP\n"
                        + "LRA  %.2f LU\nQuantizer clips  %d\nSHA-256  %s%s",
                v.wav.sampleRate, duration(v.wav.durationSeconds), r.gainDb,
                r.limiterUsed ? "used" : "bypassed", r.maximumLimiterReductionDb,
                v.integratedLufs, v.truePeakEstimateDbtp, v.lraLu,
                r.quantizerClips, v.sourceSha256, ai);
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

    private long displaySize(Uri uri) {
        try (android.database.Cursor cursor = getContentResolver().query(
                uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (index >= 0 && !cursor.isNull(index)) return cursor.getLong(index);
            }
        } catch (Exception ignored) {}
        return -1L;
    }

    private static String readText(InputStream source, int limit) throws IOException {
        try (InputStream in = source; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int n;
            while ((n = in.read(buffer)) >= 0) {
                total += n;
                if (total > limit) throw new IOException("The JSON file is unexpectedly large");
                out.write(buffer, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[256 * 1024];
        int n;
        while ((n = in.read(buffer)) >= 0) if (n > 0) out.write(buffer, 0, n);
        out.flush();
    }

    private TextView section(String value) {
        TextView view = text(value, 13, Color.parseColor("#E7B64A"));
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setLetterSpacing(0.08f);
        view.setPadding(0, dp(24), 0, dp(9));
        return view;
    }

    private TextView fileLabel(String value) {
        TextView view = text(value, 13, Color.parseColor("#AAB2BF"));
        view.setPadding(dp(2), dp(6), dp(2), dp(4));
        return view;
    }

    private TextView text(String value, int sp, int colour) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(colour);
        view.setGravity(Gravity.START);
        view.setTextIsSelectable(true);
        return view;
    }

    private Button button(String value, boolean primary) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(13);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setAllCaps(false);
        button.setTextColor(primary ? Color.parseColor("#101216") : Color.parseColor("#F2F4F7"));
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(10));
        background.setColor(primary ? Color.parseColor("#E7B64A") : Color.parseColor("#222834"));
        background.setStroke(dp(1), primary ? Color.parseColor("#E7B64A") : Color.parseColor("#3B4555"));
        button.setBackground(background);
        button.setMinHeight(dp(48));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(5), 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    private View panel(TextView content) {
        LinearLayout holder = new LinearLayout(this);
        holder.setPadding(dp(14), dp(13), dp(14), dp(13));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.parseColor("#191D24"));
        background.setCornerRadius(dp(12));
        background.setStroke(dp(1), Color.parseColor("#2A313E"));
        holder.setBackground(background);
        holder.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(5), 0, dp(2));
        holder.setLayoutParams(params);
        return holder;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private File workingCacheDir() {
        File external = getExternalCacheDir();
        return external != null ? external : getCacheDir();
    }

    private boolean canRender() {
        if (analysis == null || analysis.wav.channels != 2) return false;
        if (!analysis.noRenderNeeded) return true;
        return importedPlan != null && Math.abs(importedPlan.gainDb) > 0.0005;
    }

    private boolean canAiRender() {
        return analysis != null && analysis.wav.channels == 2
                && analysis.aiDiagnostics != null
                && analysis.aiDiagnostics.hasAutoCorrections();
    }

    private boolean canVocalRender() {
        return analysis != null && stemsUri != null && analysis.wav.channels == 2
                && analysis.vocalHumanity != null
                && analysis.vocalHumanity.available
                && analysis.vocalHumanity.hasCorrections();
    }

    private boolean canBuildRelease() {
        return analysis != null && analysis.wav.channels == 2
                && analysis.wav.sampleRate == 48000;
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        if (!file.delete()) file.deleteOnExit();
    }

    private static String duration(double seconds) {
        long ms = Math.round(seconds * 1000.0);
        long minutes = ms / 60000;
        long sec = (ms / 1000) % 60;
        long rem = ms % 1000;
        return String.format(Locale.US, "%d:%02d.%03d", minutes, sec, rem);
    }

    private static String humanBytes(long bytes) {
        double mb = bytes / (1024.0 * 1024.0);
        return mb >= 1024.0 ? String.format(Locale.US, "%.2f GB", mb / 1024.0)
                : String.format(Locale.US, "%.0f MB", mb);
    }

    private static String shortBaseName(String name) {
        String base = name == null ? "Song" : name.replaceFirst("(?i)\\.wav$", "");
        base = base.replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (base.isEmpty()) base = "Song";
        if (base.length() > 28) base = base.substring(0, 28).replaceAll("_+$", "");
        return base;
    }

    private static String cleanError(Exception e) {
        String message = e.getMessage();
        return message == null || message.trim().isEmpty() ? e.getClass().getSimpleName() : message;
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) worker.shutdownNow();
    }
}
