#!/usr/bin/env bash
set -euo pipefail
APK="p2p-chat/app/build/outputs/apk/debug/app-debug.apk"
OUT="p2p-chat/build/ci"
mkdir -p "$OUT"
adb install -r "$APK"
adb shell am start -W -n com.ehtid.p2pchat/.MainActivity | tee "$OUT/launch.txt"
sleep 8
PID="$(adb shell pidof com.ehtid.p2pchat | tr -d '\r' || true)"
test -n "$PID"
echo "App process alive: $PID"
adb logcat -d -v brief -s AndroidRuntime:E > "$OUT/android-runtime.txt"
if grep -Fq "FATAL EXCEPTION" "$OUT/android-runtime.txt"; then
  cat "$OUT/android-runtime.txt"
  exit 1
fi
adb shell screencap -p /sdcard/ehtid-p2p.png
adb pull /sdcard/ehtid-p2p.png "$OUT/screen.png"
echo "Emulator smoke test passed."
