#!/usr/bin/env python3
"""Pin the debug APK signing configuration to the restored fixed keystore."""
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: apply_stable_signing.py <project>")
build_file = Path(sys.argv[1]) / "app/build.gradle"
source = build_file.read_text()
if source.count("android {") != 1 or source.count("    buildTypes {") != 1:
    raise RuntimeError("Ambiguous Android Gradle configuration")
if "signingConfigs {" in source:
    raise RuntimeError("Unexpected pre-existing signing settings")

source = source.replace("android {", """android {
    signingConfigs {
        debug {
            storeFile file(System.getenv("HOME") + "/.android/debug.keystore")
            storePassword "android"
            keyAlias "androiddebugkey"
            keyPassword "android"
        }
    }
""", 1)
source = source.replace("    buildTypes {", """    buildTypes {
        debug {
            signingConfig signingConfigs.debug
        }
""", 1)
build_file.write_text(source)
print("Pinned Photo Master AI signing source to existing GitHub signing key")
