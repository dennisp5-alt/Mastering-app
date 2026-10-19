#!/usr/bin/env python3
"""v0.4.39: opt-in native Display P3 neural delta, independent APK.

Runs immediately after v0.4.38 experimental source patch, without
altering baseline AI MAX, approved AutoPhoto Polished, or production v0.4.37.
"""
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: apply_v0439_p3_neural_lab.py <project>")
project=Path(sys.argv[1])
gradle=project/"app/build.gradle"
build=gradle.read_text()
expected=(
    "versionCode 47",
    "versionName '0.4.38-neural-lab'",
    "applicationId 'com.dennis.photomasterai.neurallab'",
)
for text in expected:
    if build.count(text)!=1:
        raise RuntimeError("Neural Lab baseline missing or ambiguous: "+text)
build=build.replace("versionCode 47","versionCode 48",1)
build=build.replace("versionName '0.4.38-neural-lab'",
                    "versionName '0.4.39-p3-neural-lab'",1)
# A new package avoids breaking the separately signed v0.4.38 app.
build=build.replace(
    "applicationId 'com.dennis.photomasterai.neurallab'",
    "applicationId 'com.dennis.photomasterai.neurallab.p3'",1)
gradle.write_text(build)

manifest=project/"app/src/main/AndroidManifest.xml"
manifest_s=manifest.read_text()
if 'android:label="Photo Master AI Neural Lab"' not in manifest_s:
    raise RuntimeError("Old lab manifest label not found")
manifest.write_text(manifest_s.replace(
    'android:label="Photo Master AI Neural Lab"',
    'android:label="Photo Master AI P3 Neural Lab"',1))

root=project/"app/src/main/java/com/dennis/photomasterai"
java=root/"AdaptiveNeuralColour.java"
source=java.read_text()

def once(a,b,why):
    global source
    if source.count(a)!=1:
        raise RuntimeError(f"Ambiguous neural engine {why}: {source.count(a)}")
    source=source.replace(a,b,1)

once(
    '''        // The pretrained classifier and LUTs were trained in sRGB. Do not
        // retag P3 bitmaps, or erase source wide-gamut image information.
        ColorSpace a = original.getColorSpace();
        ColorSpace b = mastered.getColorSpace();
        if (a == null || b == null || !a.isSrgb() || !b.isSrgb())
            return withReport(baseline, "WITHHELD — only verified sRGB input is enabled in this experiment");''',
    '''        // Training is sRGB; inference is always colour-managed sRGB.
        // P3 source and master qualify only with true native P3 protection,
        // not a colour-space retag or a clipped sRGB re-encode.
        ColorSpace a = original.getColorSpace();
        ColorSpace b = mastered.getColorSpace();
        ColorSpace p3 = ColorSpace.get(ColorSpace.Named.DISPLAY_P3);
        boolean srgbPath = a != null && b != null
                && a.isSrgb() && b.isSrgb();
        boolean p3Path = a != null && b != null
                && p3.equals(a) && p3.equals(b)
                && mastered.getConfig() == Bitmap.Config.ARGB_8888
                && (long) mastered.getWidth() * mastered.getHeight() <= 12_600_000L
                && mastered.getRowBytes() == (long) mastered.getWidth() * 4;
        if (!srgbPath && !p3Path)
            return withReport(baseline, "WITHHELD — unsupported source/master profile "
                    + "or P3 pixel storage; original AI MAX retained");
        if (p3Path && !verifyP3JpegEncoder())
            return withReport(baseline, "WITHHELD — native Display P3 JPEG "
                    + "ICC export not verified on this device");''',
    "profile qualification"
)
once('Correction correction = render(original, mastered, lut, strength);',
     '''Correction correction = p3Path
                        ? renderP3(original, mastered, lut, strength)
                        : render(original, mastered, lut, strength);''',
     "candidate mode choice")
once('"ACCEPTED • ONNX Image-Adaptive 3D LUT • strength %.2f"',
     '"ACCEPTED • ONNX Image-Adaptive 3D LUT • strength %.2f"',
     "accept report marker")
once('''                            + "\\n• AI MAX baseline preserved as rollback candidate",''',
     '''                            + "\\n• AI MAX baseline preserved as rollback candidate"
                            + "\\n• Colour pipeline: " + (p3Path
                                ? "Display P3 native chroma + colour-managed sRGB neural delta"
                                : "sRGB neural LUT path"),''',
     "report accepted profile")
# Bundle new P3 renderer only; the original sRGB method is unchanged.
block=Path("tools/neural_colour/DisplayP3RenderMethod.txt").read_text()
once('    private static int clampedDiff(float requested) {',
     block+'''
    private static boolean verifyP3JpegEncoder() {
        final ColorSpace p3=ColorSpace.get(ColorSpace.Named.DISPLAY_P3);
        Bitmap probe=null;
        try {
            probe=Bitmap.createBitmap(4,4,Bitmap.Config.ARGB_8888,true,p3);
            ByteBuffer pixels=ByteBuffer.allocateDirect(probe.getByteCount());
            for(int i=0;i<16;i++)
                pixels.put((byte)90).put((byte)160).put((byte)210).put((byte)255);
            pixels.rewind();
            probe.copyPixelsFromBuffer(pixels);
            java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream();
            if(!probe.compress(Bitmap.CompressFormat.JPEG,98,output)) return false;
            byte[] jpeg=output.toByteArray();
            android.graphics.BitmapFactory.Options opts=new android.graphics.BitmapFactory.Options();
            opts.inJustDecodeBounds=true;
            android.graphics.BitmapFactory.decodeByteArray(jpeg,0,jpeg.length,opts);
            return p3.equals(opts.outColorSpace);
        } catch (Throwable issue) {
            return false;
        } finally {
            if(probe!=null&&!probe.isRecycled())probe.recycle();
        }
    }

    private static int clampedDiff(float requested) {''',
     "preserving raw P3 gamut")
for text in (
    "renderP3(original, mastered, lut, strength)",
    "copyPixelsToBuffer(nativePixels)",
    "copyPixelsFromBuffer(output)",
    "verifyP3JpegEncoder()",
    "MAX_CHANNEL_EDIT = 12",
    "MIN_DETAIL_RETAINED = .985",
    "MAX_RING_INCREASE = .006",
    "MAX_MEAN_CHANNEL_MOVE = .007",
    "MAX_CLIP_INCREASE = .002",
    'ColorSpace.get(ColorSpace.Named.DISPLAY_P3)',
):
    if text not in source:
        raise RuntimeError("Protection invariant missing: "+text)
java.write_text(source)

main_file=root/"MainActivity.java"
main=main_file.read_text()
if main.count('text("PHOTO MASTER AI • NEURAL LAB", 24, TEXT, true)')!=1:
    raise RuntimeError("Lab UI title anchor changed")
main=main.replace('text("PHOTO MASTER AI • NEURAL LAB", 24, TEXT, true)',
                  'text("PHOTO MASTER AI • P3 NEURAL LAB", 23, TEXT, true)',1)
if 'AI MAX + LUT' not in main:
    raise RuntimeError("Opt-in AI MAX + LUT mode missing")
main_file.write_text(main)
print("v0.4.39 P3 Neural Lab: independent P3-native neural delta, "
      "ICC probe, unchanged sRGB & AI MAX baselines, original safety QC")
