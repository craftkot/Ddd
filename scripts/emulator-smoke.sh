#!/usr/bin/env bash
set -euo pipefail

# Android Gradle Test may uninstall the target app when instrumentation exits.
gradle --no-daemon :app:connectedDebugAndroidTest

mkdir -p build/ci
APK=app/build/outputs/apk/debug/app-debug.apk
test -s "$APK"
adb install -r "$APK"
adb shell am force-stop dev.relaybox.app || true
adb shell am start -W -n dev.relaybox.app/.MainActivity | tee build/ci/activity-launch.txt

visible=0
for attempt in 1 2 3 4 5; do
  sleep 3
  adb shell uiautomator dump /sdcard/relaybox-ui.xml >build/ci/uiautomator.log 2>&1 || true
  adb pull /sdcard/relaybox-ui.xml build/ci/relaybox-ui.xml >/dev/null 2>&1 || true
  if grep -Fq 'text="RelayBox"' build/ci/relaybox-ui.xml 2>/dev/null; then
    visible=1
    break
  fi
done
if test "$visible" -ne 1; then
  echo "FAIL: RelayBox was not displayed after installation and launch."
  cat build/ci/activity-launch.txt
  cat build/ci/uiautomator.log || true
  adb shell dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity' || true
  exit 1
fi

adb shell screencap -p /sdcard/relaybox-screen.png
adb pull /sdcard/relaybox-screen.png build/ci/relaybox-screen.png
test -s build/ci/relaybox-screen.png
echo "PASS: the installed APK displays RelayBox in the emulator."
