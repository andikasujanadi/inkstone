#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
bash scripts/build-native.sh
for abi in arm64-v8a x86_64; do
  test -s "android/app/src/main/jniLibs/$abi/libinkstone.so" || { echo "Missing native library for $abi" >&2; exit 1; }
done
export ANDROID_USER_HOME="${ANDROID_USER_HOME:-$ROOT/.toolchain/android-user}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$ROOT/.toolchain/gradle}"
./android/gradlew -p android assembleDebug --no-daemon
mkdir -p releases
cp android/app/build/outputs/apk/debug/app-debug.apk releases/inkstone-0.1.9-preview7-debug.apk
echo "APK: $ROOT/releases/inkstone-0.1.9-preview7-debug.apk"
