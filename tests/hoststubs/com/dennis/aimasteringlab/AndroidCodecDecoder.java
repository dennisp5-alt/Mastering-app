package com.dennis.aimasteringlab;
import java.io.File;
/** Host-only stub: Android MediaCodec is unavailable off-device. */
final class AndroidCodecDecoder {
    static final class Result { File decodedWav; String decoderName; String sourceMime; AnalysisResult verification; long decodedPcmBytes; String error; }
    private AndroidCodecDecoder() {}
    static Result decodeAndMeasure(File encoded, File decodedWav, AudioAnalyzer.Progress progress) { Result r = new Result(); r.decodedWav = decodedWav; r.error = "host stub"; return r; }
}
