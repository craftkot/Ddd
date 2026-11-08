#!/usr/bin/env bash
# Android startup smoke. One file is essential because android-emulator-runner
# executes script entries separately via /bin/sh -c, not as a multiline shell.
set -euo pipefail
APK="$GITHUB_WORKSPACE/rogue-duel/app/build/outputs/apk/debug/app-debug.apk"
PKG="${ASHES_PACKAGE:-com.ehtid.ashesduel.trial}"
ACT="com.ehtid.ashesduel.MainActivity"

adb install "$APK"
adb logcat -c
adb shell am start -W -n "$PKG/$ACT"

for attempt in $(seq 1 36); do
  LOGS="$(adb logcat -d -s AshesStartup:V AndroidRuntime:E '*:S' || true)"
  if printf '%s\n' "$LOGS" | grep -q "ASHES_READY"; then
    echo "PASS: APK installed, Android Activity opened, final JS game rendered."
    printf '%s\n' "$LOGS" | grep "ASHES_READY"
    exit 0
  fi
  if printf '%s\n' "$LOGS" | grep -q "STARTUP_ERROR"; then
    echo "FAIL: native startup error"
    printf '%s\n' "$LOGS" | tail -150
    exit 1
  fi
  if printf '%s\n' "$LOGS" | grep -q "FATAL EXCEPTION"; then
    echo "FAIL: Android process crashed"
    printf '%s\n' "$LOGS" | tail -150
    exit 1
  fi
  sleep 2
done
echo "FAIL: game did not produce ASHES_READY; WebView may be blank."
adb logcat -d -s AshesStartup:V AndroidRuntime:E chromium:E '*:S' | tail -170
exit 1
