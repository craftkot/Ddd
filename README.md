# RelayBox Android v0.4

Unified Android notification inbox for Avito, FunPay, and Playerok, with official sites inside WebView.

## What's new in 0.4
- Custom launcher icon (Avito color dots + gold crown + green gamepad + central conversation bubble).
- Automated CI quality gate: JUnit URL validation, Android Lint, APK archive + signature + icon verification, emulator installation and UI smoke test.
- Android emulator screenshots and test reports attached to GitHub Actions runs.
- The verified APK artifact is only uploaded if all checks pass.

## Building & checking
Push to main or open **Actions → RelayBox Android • Build + automated tests → Run workflow**.
Successful build contains:
- `RelayBox-v0.4-verified-apk` — Android installable debug APK.
- `RelayBox-v0.4-qa-reports` — JUnit, Android Lint, emulator results, screenshot.

## Limitations
NotificationListenerService can observe allowed system notifications after the user grants access; this is not equivalent to API access to marketplace chat histories. WebView login, SSO and the exact chat links can fail if marketplace websites change; synthetic emulator tests cannot verify real credentials, incoming marketplace push, or external service availability. Original marketplace notifications still open their original app; optional RelayBox mirror notifications open RelayBox.

App requires Android 8.0+. WebView cookies remain on device; encrypted message data is stored locally. No remote backend.
