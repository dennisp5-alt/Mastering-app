package com.dennis.aimasteringlab;
import java.io.*;
import org.json.*;

/** Masters one WAV through the full Studio path and prints the base and delivered hashes. */
public final class StudioPathRegression {
    public static void main(String[] args) throws Exception {
        File input = new File(args[0]), root = new File(args[1]);
        root.mkdirs();
        AudioAnalyzer.Progress quiet = (s, x) -> {};
        StudioProject p = StudioProject.create(root, input.getName());
        File copied = p.child("source.wav");
        StudioProject.copy(new FileInputStream(input), copied);
        AnalysisResult source = StudioAudio.measure(copied, quiet);
        p.file("source", copied);
        p.data.put("source_hash", source.sourceSha256).put("duration", source.wav.durationSeconds)
            .put("source_meters", StudioAudio.meters(source));
        p.state("QUEUED", "ready");
        StudioPipeline.master(p, quiet);
        StudioPipeline.finish(p, quiet);
        p = StudioProject.load(p.dir);
        System.out.println("STUDIO " + input.getName() + " state=" + p.state()
            + " winner=" + p.data.optString("base_winner")
            + " rendered=" + p.data.optLong("base_candidates_rendered", -1)
            + " base=" + p.data.getString("base_hash") + " master=" + p.data.getString("master_hash"));
    }
}
