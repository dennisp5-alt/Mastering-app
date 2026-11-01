#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SDK_DIR="${DAML_ANDROID_SDK:-/tmp/autoplus-android-sdk}"
BUILD_TOOLS="$SDK_DIR/build-tools/34.0.0"
ANDROID_JAR="$SDK_DIR/platforms/android-34/android.jar"
BUILD_ROOT="$PROJECT_DIR/build"
APP_DIR="$PROJECT_DIR/app/src/main"
KEYSTORE="$PROJECT_DIR/test-signing.jks"
STORE_PASS="dennis-ai-mastering"
KEY_ALIAS="daml-private"
EXPECTED_CERT="2d6822f2d6e3785a03bfe61d73d5139822c78c9b2dedbbc64aa0424ad44eff70"

for required in "$BUILD_TOOLS/aapt2" "$BUILD_TOOLS/d8" \
    "$BUILD_TOOLS/zipalign" "$BUILD_TOOLS/apksigner" "$BUILD_TOOLS/aapt" \
    "$ANDROID_JAR"; do
    if [[ ! -e "$required" ]]; then
        echo "Missing Android build component: $required" >&2
        exit 1
    fi
done
if [[ ! -f "$KEYSTORE" ]]; then
    echo "Missing retained update-signing keystore; refusing to create an incompatible update key" >&2
    exit 1
fi

mkdir -p "$BUILD_ROOT"
BUILD_DIR="$(mktemp -d "$BUILD_ROOT/manual-v0141-XXXXXX")"
mkdir -p "$BUILD_DIR/generated" "$BUILD_DIR/classes" "$BUILD_DIR/dex"
"$BUILD_TOOLS/aapt2" compile --dir "$APP_DIR/res" \
    -o "$BUILD_DIR/compiled-res.zip"
"$BUILD_TOOLS/aapt2" link \
    -o "$BUILD_DIR/base.apk" -I "$ANDROID_JAR" \
    --manifest "$APP_DIR/AndroidManifest.xml" -A "$APP_DIR/assets" \
    --min-sdk-version 29 --target-sdk-version 34 \
    --version-code 20 --version-name 0.14.1 \
    --java "$BUILD_DIR/generated" --auto-add-overlay \
    -R "$BUILD_DIR/compiled-res.zip"

mapfile -t JAVA_SOURCES < <(find "$APP_DIR/java" "$BUILD_DIR/generated" \
    -name '*.java' -type f | sort)
java com.sun.tools.javac.Main -encoding UTF-8 -source 8 -target 8 \
    -classpath "$ANDROID_JAR" -d "$BUILD_DIR/classes" "${JAVA_SOURCES[@]}"
mapfile -t CLASS_FILES < <(find "$BUILD_DIR/classes" -name '*.class' -type f | sort)
"$BUILD_TOOLS/d8" --lib "$ANDROID_JAR" --min-api 29 \
    --output "$BUILD_DIR/dex" "${CLASS_FILES[@]}"

cp "$BUILD_DIR/base.apk" "$BUILD_DIR/unsigned.apk"
(cd "$BUILD_DIR/dex" && zip -q -j "$BUILD_DIR/unsigned.apk" classes.dex)
"$BUILD_TOOLS/zipalign" -f -p 4 "$BUILD_DIR/unsigned.apk" "$BUILD_DIR/aligned.apk"
OUTPUT="$PROJECT_DIR/DennisAiMasteringLab-v0.14.1.apk"
"$BUILD_TOOLS/apksigner" sign --v3-signing-enabled true \
    --ks "$KEYSTORE" --ks-key-alias "$KEY_ALIAS" \
    --ks-pass "pass:$STORE_PASS" --key-pass "pass:$STORE_PASS" \
    --out "$OUTPUT" "$BUILD_DIR/aligned.apk"

VERIFY_OUTPUT="$("$BUILD_TOOLS/apksigner" verify --verbose --print-certs "$OUTPUT")"
printf '%s\n' "$VERIFY_OUTPUT"
ACTUAL_CERT="$(printf '%s\n' "$VERIFY_OUTPUT" | sed -n \
    's/^Signer #1 certificate SHA-256 digest: //p' | head -n 1 | tr 'A-F' 'a-f')"
if [[ "$ACTUAL_CERT" != "$EXPECTED_CERT" ]]; then
    echo "Signing certificate mismatch: $ACTUAL_CERT" >&2
    exit 1
fi
"$BUILD_TOOLS/aapt" dump badging "$OUTPUT" | sed -n '1,8p'
sha256sum "$OUTPUT"
