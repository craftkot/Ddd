# EHTID Chat v1.3 — фиолетовый интерфейс и голосовые

Public free relay: `https://ehtid-chat-relay.craftkot404.workers.dev`.
The URL is already integrated into the Android app; participants never
enter separate Cloudflare settings or server access tokens. Share the
private 20-character code, install the same Android APK, and join.

## v1.3 — обновлённый интерфейс и расшифровка

- Тёмно-фиолетовая палитра EHTID, контрастные пузыри сообщений и единые векторные иконки.
- Код комнаты и подключение свёрнуты в заголовок (нажмите на заголовок EHTID), участники доступны в отдельном экране.
- Компактная панель: вложение, текст, микрофон, отправка; микрофон меняется на кнопку остановки во время записи.
- Перед расшифровкой пользователь явно подтверждает отправку аудио без E2EE и выбирает Авто, Русский или English.
- Расшифровка показывает прогресс и объясняет ошибки; результат сохраняется для исходной комнаты, даже если переключиться во время распознавания.
- Backend: Whisper VAD для тишины и явный параметр языка. Для включения серверных улучшений требуется отдельно развернуть Cloudflare Worker; до этого сервер игнорирует новый языковой заголовок.
- Обновление поверх прежнего APK: старые комнаты и SQLite сохраняются. Имя пакета и постоянная подпись не меняются.

## v1.2 — автообновление и несколько комнат

**Комнаты**: кнопка «Комнаты» в верхней части чата открывает
сохранённые комнаты. Можно переключаться между ними, переименовывать
закладки и видеть число сохранённых сообщений. При переключении активен
только один сетевой канал, но локальная история всех комнат остаётся.
Когда комната снова открыта, клиент подгружает доступные события Cloudflare
(сервер хранит их до 7 дней). Закладку можно убрать без удаления истории.

**История**: теперь сохраняется в SQLite `ehtid_history.db`, разделённой
по коду комнаты. При первом открытии v1.2 существующая локальная переписка
переносится из SharedPreferences в SQLite **без удаления исходных записей**.
Также восстанавливается список старых комнат по сохранённым ключам
`msgs_`, `pending_` и `role_`. После обновлений APK база остаётся;
**удаление приложения** или «Очистить хранилище» Android всё равно
удаляет её. Обновляйте поверх существующего приложения, не удаляя APK.

**Автообновление**: первый переход **v1.1 → v1.2** надо установить вручную.
Начиная с v1.2 приложение само проверяет опубликованные EHTID Chat
Releases (включая beta), при обнаружении более новой версии
скачивает APK из официального GitHub. Проверяется SHA-256, имя
пакета `com.ehtid.p2pchat`, более высокий `versionCode`, семантическая
версия и постоянный сертификат EHTID. Неподходящий файл не предлагается
для установки. Проверка проводится при открытии приложения и через
Android WorkManager примерно раз в 12 часов, но Android может задержать
выполнение задачи из-за Doze и экономии батареи. При скачивании новой
версии показывается уведомление или окно установки.

**Android не разрешает обычным приложениям устанавливать APK незаметно**:
потребуется подтвердить установку в системном интерфейсе и, возможно,
один раз разрешить «Установку из этого источника» для EHTID. В настройках
можно вручную проверить GitHub или отключить автоматическую загрузку
(она может потратить примерно 50 МБ мобильного интернета).

Важно: подписание APK выполняется тем же постоянным сертификатом, что и
у v1.0/v1.1. Не публикуйте обновление с другим сертификатом.

## Что уже было в v1.1

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
  v1.2 installs over v1.1 without deleting app data.
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
