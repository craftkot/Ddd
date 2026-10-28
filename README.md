# RelayBox v0.3 (Android)

RelayBox collects Avito, FunPay and Playerok Android notifications into a local inbox, opens official chat websites in an embedded WebView and offers sender-to-chat URL bookmarking.

Features:
- Android NotificationListenerService (permission required)
- Encrypted local message history (AES-256-GCM, Android Keystore), up to 1000 rows
- Inbox search and platform/unread filters, mark all read
- Account login on official WebView sites, optional mirrored notifications that open RelayBox
- Manual link to a particular conversation, file attachments in WebView

Limitations:
- Does not read server-side chat history or integrate with proprietary APIs.
- Clicking an original Avito notification opens Avito, not RelayBox. Mirrored notifications can open RelayBox.
- Browser push notifications must first be enabled on the respective sites.
- Bookmarking chat URLs requires the site to expose distinct URLs.
- WebView login, CAPTCHA, third-party SSO and website changes may prevent login.
- This is a debug build, not an official client of any marketplace.

Build with GitHub Actions: Actions > Build RelayBox Android APK > Run workflow.
Download artifact named RelayBox-v0.3-debug-apk and extract app-debug.apk.
Android Studio alternative: JDK 17, Android SDK 35, Gradle 8.11.1, AGP 8.9.2.
The repository omits gradle-wrapper.jar; Actions installs Gradle.

After installing: give RelayBox notification access through Android settings, enable its mirrored notifications if desired, sign into services via embedded WebView.
