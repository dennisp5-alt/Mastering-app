package com.dennis.aimasteringlab;
import java.io.File;
/** Host-only stub: Android MediaCodec is unavailable off-device. */
final class AndroidCodecEncoder {
    static final class Result { File file; String codec; int bitrate; boolean available; String encoderName; String error; }
    private AndroidCodecEncoder() {}
    static Result encodeAac(File wav, File output, int bitrate, AudioAnalyzer.Progress progress) { return na(output, "AAC", bitrate); }
    static Result encodeMp3(File wav, File output, int bitrate, AudioAnalyzer.Progress progress) { return na(output, "MP3", bitrate); }
    private static Result na(File o, String c, int b) { Result r = new Result(); r.file = o; r.codec = c; r.bitrate = b; r.available = false; r.error = "host stub"; return r; }
}
