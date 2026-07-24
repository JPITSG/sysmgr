#!/usr/bin/env bash
#
# Builds the System Manager debug APK without Gradle, driving the Android SDK
# build tools directly: aapt2 -> javac -> d8 -> (inject dex + native lib) ->
# zipalign -> apksigner. Output: build/outputs/apk/SystemManager-debug.apk
#
# Prerequisites:
#   - Android SDK with build-tools 36.0.0 and platform android-36
#   - A JDK (javac, keytool)
#   - zip; and, for the embedded OpenVPN binary, the Android NDK r27
#
# Configuration (environment variables, with defaults):
#   ANDROID_HOME  Android SDK location   (default: /opt/android-sdk)
#   BUILD_TOOLS   build-tools version    (default: 36.0.0)
#   PLATFORM      compile platform       (default: android-36)
#   NDK_DIR       NDK location           (see native/build-openvpn.sh)
#   SKIP_NATIVE   if set (any value), skip building/bundling the OpenVPN binary;
#                 produces a working APK, but the in-app VPN is unavailable.
#
# A local debug keystore is generated automatically on first run if missing.
#
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
BUILD_TOOLS="${BUILD_TOOLS:-36.0.0}"
PLATFORM="${PLATFORM:-android-36}"

AAPT2="$ANDROID_HOME/build-tools/$BUILD_TOOLS/aapt2"
D8="$ANDROID_HOME/build-tools/$BUILD_TOOLS/d8"
ZIPALIGN="$ANDROID_HOME/build-tools/$BUILD_TOOLS/zipalign"
APKSIGNER="$ANDROID_HOME/build-tools/$BUILD_TOOLS/apksigner"
ANDROID_JAR="$ANDROID_HOME/platforms/$PLATFORM/android.jar"

OUT_DIR="$ROOT_DIR/build"
RES_COMPILED="$OUT_DIR/compiled-res"
GEN_DIR="$OUT_DIR/generated"
CLASS_DIR="$OUT_DIR/classes"
DEX_DIR="$OUT_DIR/dex"
INTERMEDIATE_DIR="$OUT_DIR/intermediates"
APK_DIR="$OUT_DIR/outputs/apk"
KEYSTORE="$ROOT_DIR/keystore/debug.keystore"

rm -rf "$RES_COMPILED" "$GEN_DIR" "$CLASS_DIR" "$DEX_DIR" "$INTERMEDIATE_DIR"
mkdir -p "$RES_COMPILED" "$GEN_DIR" "$CLASS_DIR" "$DEX_DIR" "$INTERMEDIATE_DIR" "$APK_DIR" "$ROOT_DIR/keystore"

"$AAPT2" compile --dir "$ROOT_DIR/app/src/main/res" -o "$RES_COMPILED"

"$AAPT2" link \
  -I "$ANDROID_JAR" \
  --manifest "$ROOT_DIR/app/src/main/AndroidManifest.xml" \
  --java "$GEN_DIR" \
  --auto-add-overlay \
  --min-sdk-version 26 \
  --target-sdk-version 36 \
  -o "$INTERMEDIATE_DIR/SystemManager-unsigned.apk" \
  "$RES_COMPILED"/*.flat

find "$ROOT_DIR/app/src/main/java" "$GEN_DIR" -name '*.java' | sort > "$OUT_DIR/sources.list"

javac \
  -source 8 \
  -target 8 \
  -bootclasspath "$ANDROID_JAR" \
  -classpath "$ANDROID_JAR" \
  -d "$CLASS_DIR" \
  @"$OUT_DIR/sources.list"

find "$CLASS_DIR" -name '*.class' | sort > "$OUT_DIR/classes.list"
"$D8" --release --min-api 26 --classpath "$ANDROID_JAR" --output "$DEX_DIR" @"$OUT_DIR/classes.list"

cp "$INTERMEDIATE_DIR/SystemManager-unsigned.apk" "$INTERMEDIATE_DIR/SystemManager-unsigned-dex.apk"
(cd "$DEX_DIR" && zip -q "$INTERMEDIATE_DIR/SystemManager-unsigned-dex.apk" classes.dex)

# Embedded OpenVPN binary: built (or stamp-skipped) by the native script, then
# injected as lib/arm64-v8a/libopenvpn.so before zipalign/signing. Requires
# android:extractNativeLibs="true" so the installer extracts it for exec().
# Set SKIP_NATIVE=1 to build without it (the in-app VPN is then unavailable,
# but every other feature works).
if [[ -z "${SKIP_NATIVE:-}" ]]; then
  "$ROOT_DIR/native/build-openvpn.sh"
  NATIVE_STAGE="$INTERMEDIATE_DIR/native-stage"
  rm -rf "$NATIVE_STAGE"
  mkdir -p "$NATIVE_STAGE/lib/arm64-v8a"
  cp "$ROOT_DIR/app/src/main/jniLibs/arm64-v8a/libopenvpn.so" "$NATIVE_STAGE/lib/arm64-v8a/libopenvpn.so"
  (cd "$NATIVE_STAGE" && zip -q -r "$INTERMEDIATE_DIR/SystemManager-unsigned-dex.apk" lib)
else
  echo "SKIP_NATIVE set: building without the embedded OpenVPN binary (in-app VPN unavailable)."
fi

"$ZIPALIGN" -p -f 4 "$INTERMEDIATE_DIR/SystemManager-unsigned-dex.apk" "$INTERMEDIATE_DIR/SystemManager-aligned.apk"

if [[ ! -f "$KEYSTORE" ]]; then
  keytool -genkeypair \
    -keystore "$KEYSTORE" \
    -storepass android \
    -keypass android \
    -alias androiddebugkey \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US" >/dev/null
fi

"$APKSIGNER" sign \
  --ks "$KEYSTORE" \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out "$APK_DIR/SystemManager-debug.apk" \
  "$INTERMEDIATE_DIR/SystemManager-aligned.apk"

"$APKSIGNER" verify --verbose "$APK_DIR/SystemManager-debug.apk"
echo "Built: $APK_DIR/SystemManager-debug.apk"
