package com.dennis.aimasteringlab;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Aggregates the five guarded v0.9 source-aware systems. */
final class NextGenerationMasteringModel {
    static final class Result {
        String model = "DAML v0.10 measured technical repairs and diagnostics";
        String method = "diagnostic-only translation-family consensus + narrow persistent technical tones + stable channel integrity + source guard + diagnostic-only optional reference comparison";
        TranslationConsensusModel.Result translation;
        PersistentToneModel.Result tones;
        ChannelIntegrityModel.Result channel;
        ReferenceCalibrationModel.Result reference;
        final List<String> safetyNotes = new ArrayList<>();

        int availableModules() {
            int n=0;
            if(tones!=null&&tones.automaticCount()>0)n++;
            if(channel!=null&&channel.automaticCount()>0)n++;

            return n;
        }

        String compactSummary() {
            return String.format(Locale.US,
                    "Translation %d • tones %d • channel faults %d • reference %d",
                    translation==null?0:translation.automaticCount(),
                    tones==null?0:tones.automaticCount(),
                    channel==null?0:channel.automaticCount(),
                    reference==null?0:reference.automaticCount());
        }
    }

    private NextGenerationMasteringModel() {}

    static Result analyze(AudioAnalyzer.StreamProvider source,
                          AnalysisResult base,
                          AudioAnalyzer.StreamProvider reference,
                          String referenceName,
                          String referenceSha256,
                          AudioAnalyzer.Progress progress) throws IOException {
        Result out=new Result();
        out.translation=TranslationConsensusModel.analyze(source,base,progress);
        out.tones=PersistentToneModel.analyze(source,base,progress);
        out.channel=ChannelIntegrityModel.analyze(source,base,progress);
        out.reference=reference==null?ReferenceCalibrationModel.none()
                :ReferenceCalibrationModel.analyze(source,base,reference,
                referenceName,referenceSha256,progress);
        out.safetyNotes.add("Only proven persistent technical tones, polarity and fixed delay may generate later candidates. Translation, reference matching and level balance remain diagnostic-only.");
        out.safetyNotes.add("The authoritative stereo mix is never replaced by a generated or reference-derived waveform.");
        return out;
    }
}
