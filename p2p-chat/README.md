# EHTID Chat v1.1 — код комнаты, файлы, голосовые и оформление

Public free relay: `https://ehtid-chat-relay.craftkot404.workers.dev`.
The URL is already integrated into the Android app; participants never
enter separate Cloudflare settings or server access tokens. Share the
private 20-character code, install the same Android APK, and join.

## What changed

**Chat**
- Encrypted text and attachment manifests, room-local history and
  notifications (subject to Android background restrictions).
- Upload any file or picture up to **10 MiB** by tapping **＋**.
  Attachments are end-to-end AES-GCM encrypted, transmitted as D1 chunks,
  and downloaded on demand in chat.
- Tap **▣ Посмотреть фото** to view an image, **▶ Прослушать** for an
  AAC/M4A voice note or **⬇ Открыть файл** for other files.
  File open uses a secure Android FileProvider URI.
- Tap **🎤** to record a voice note, tap **■** to finish and send.
  Recording automatically stops after three minutes.
- Voice-note **📝 Расшифровать голосовое** is optional. The user must
  explicitly approve sending a plaintext copy of the selected audio
  to the Cloudflare Whisper model. The original chat attachment stays
  encrypted. Whisper transcript stays in local preferences and is
  displayed below that voice message, not silently shared with others.
  AI limits, models and accuracy vary.
- Cached decrypted media stays in the app's private files directory.

**Navigation**
- Swipe **right from chat** to open the participants panel; swipe left
  from participants to return to chat.
- Swipe **left from chat** to open profile/settings; swipe right to return.
- Participant details include nickname, online/offline state, device ID
  prefix, ping, last advertised login time and last received signal
  formatted down to seconds. Presence is periodic, not exact connection
  tracking: times may be stale, especially in the background.
- Profile includes nickname change, notification options, and personal
  wallpaper selection from any local image via Android Documents picker.
  Background images stay local to each user and can be reset to dark mode.

## Compatibility

- Android 7.0+ (API 24).
- Android runtime microphone permission required to record voice.
- Network needed to upload/download; **media uploads are not offline queued**.
- The cloud retains media and message ciphertext for up to seven days;
  local media remains cached until Android app storage is cleared or
  the app is uninstalled.
- Existing room code, chat history and permanent signing key remain.
  v1.1 installs over v1.0 without deleting app data.
- Cloudflare D1 Free 500 MB per database, Workers Free request quota and
  Workers AI Free daily quotas remain finite. The app is a beta and the
  cryptography has not been independently audited.

## Build

```sh
cd p2p-chat
gradle :app:testDebugUnitTest :app:assembleDebug
```

GitHub Actions on `feature/p2p-chat` produces a permanently signed APK.
Backend source, protocol and privacy documentation:
[cloudflare-relay](../cloudflare-relay/README.md).
