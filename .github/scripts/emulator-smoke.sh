#!/usr/bin/env bash
set -euo pipefail
mkdir -p qa_artifacts
PKG="com.signallab.wifi"
adb wait-for-device
adb shell pm list packages | grep -q "$PKG" || adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -c
adb shell am force-stop "$PKG" || true
adb shell am start -W -n "$PKG/.MainActivity" | tee qa_artifacts/launch.txt
sleep 2

dump_ui() {
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1
  adb exec-out cat /sdcard/window.xml > qa_artifacts/window.xml
}
tap_text() {
  dump_ui
  local points
  points="$(python3 - "$1" <<'PY'
import sys, xml.etree.ElementTree as ET, re
query=sys.argv[1]
root=ET.parse('qa_artifacts/window.xml').getroot()
for n in root.iter('node'):
  if n.get('text')==query:
    m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',n.get('bounds',''))
    if m:
      x1,y1,x2,y2=map(int,m.groups())
      print((x1+x2)//2,(y1+y2)//2)
      break
else:
  sys.exit('Missing UI element: '+query)
PY
)"
  adb shell input tap $points
  sleep 2
}
assert_text() {
  dump_ui
  python3 - "$1" <<'PY'
import sys, xml.etree.ElementTree as ET
term=sys.argv[1]
root=ET.parse('qa_artifacts/window.xml').getroot()
texts=[n.get('text','') for n in root.iter('node')]
if not any(term in t for t in texts):
  raise SystemExit('Text not present: '+term)
print('PASSED:',term)
PY
}
capture() {
  adb exec-out screencap -p > "qa_artifacts/$1.png"
  cp qa_artifacts/window.xml "qa_artifacts/$1.xml"
}
assert_text "Состояние подключения"
capture overview
tap_text "Обновить"
assert_text "Состояние подключения"
tap_text "ЭФИР"
assert_text "Анализ радиоэфира"
capture radio
adb shell pm grant "$PKG" android.permission.ACCESS_FINE_LOCATION || true
adb shell pm grant "$PKG" android.permission.NEARBY_WIFI_DEVICES || true
adb shell cmd location set-location-enabled true || true
tap_text "Найти точки Wi-Fi"
assert_text "Анализ радиоэфира"
capture radio_scanned
tap_text "ТЕСТЫ"
assert_text "Диагностика сети"
capture diagnostics
tap_text "Начать проверку"
sleep 35
assert_text "Диагностика сети"
capture diagnostics_after_test
tap_text "РОУТЕР"
assert_text "Настройка роутера"
capture router
tap_text "ОБЗОР"
assert_text "Состояние подключения"
capture return
adb logcat -d -v time > qa_artifacts/logcat.txt || true
if grep -E 'FATAL EXCEPTION|Process: com\.signallab\.wifi' qa_artifacts/logcat.txt; then
  echo "FAIL: crash markers in logcat"
  exit 2
fi
adb shell dumpsys activity activities > qa_artifacts/dumpsys.txt
echo "PASS: launch, 4 tabs, Wi-Fi scan requested, diagnostics started, back to overview, no logged FATAL EXCEPTION" | tee qa_artifacts/summary.txt
