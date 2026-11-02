# SignalLab v1.0.4
Android Wi-Fi analyzer with RSSI, channel scan, Wi-Fi-specific DNS/TCP and HTTP download diagnostics, gateway TCP latency, router page, Huawei HiLink signal probe, optional OpenWrt ubus channel management.
Android location access is required for SSID/BSSID and scans. The Wi-Fi permission prompt is on the overview screen. No coordinates are stored.
5 MB speed test is a rough HTTP download estimate, NOT a guaranteed WAN/Wi-Fi speed measurement. The DNS query result may change with resolver and time.
QA: Android 15 emulator start, four tabs, permission prompt, scan, diagnostics and logcat.
OpenWrt/Huawei router-specific actions require testing against real devices and administrator authorization. OpenWrt uses cleartext HTTP, only on a trusted LAN.
Build: GitHub Actions -> SignalLab APK -> artifact. Development is isolated to branch build/signallab-apk-20261102; main contains a different app.
