# Пепел и карты v0.8 Beta — Android / интернет-PvP

**Рабочая версия для игры вдвоём** — Android/WebView (`rogue-duel/`).
Отдельный нативный Godot-порт пока предназначен для одиночного прохождения:
его PvP не завершён.

## Что изменилось в v0.8

- Сохранены существующие пути, коллекция **161 карты, 136 реликвий**,
  15 этапов, бой и финальная дуэль.
- **Создать комнату / войти по коду** без регистрации, токена и настройки
  Cloudflare на телефонах. Транспорт переключён с публичного ntfy.sh на уже
  используемый EHTID Chat Cloudflare Worker;
  WebRTC DataChannel используется при доступности прямого соединения.
- Реальный ping/pong с отображением последнего ответа, задержки,
  типа соединения, копированием кода и ручным повторным подключением.
- Сообщения шифруются AES-GCM, Android передаёт пакеты формата Worker v1,
  а крупные PvP-состояния разбиваются на ограниченные части `frag1`.
- **Реальные офлайн-изображения** Kenney CC0: фэнтези-панели, предметы,
  реликвии, оружие, сцены, а также оригинальный живописный атлас
  персонажей и карта локации из Godot-порта.
  Файлы физически находятся в `app/src/main/assets/art/` и показываются
  в бою, на картах, наградах и экранах выбора.
- Новая цветовая система `artbook.css`, обновлённые карточки и мобильные
  состояния интерфейса. Подробности лицензии: `ASSET_CREDITS_AND_NETWORK.md`.
- Android `versionName 0.8.0`, `versionCode 8`.
  GitHub Actions публикует тестовую APK в release `ashes-duel-v0.8.0-beta`.

## Ограничения, которые нельзя скрывать

- **4 символа** — удобный, но слабый секрет комнаты, около 1 млн
  комбинаций. Шифрование не защищает от того, кто узнает или переберёт код.
  Не использовать для конфиденциального общения.
- Обмен данными зависит от доступности Cloudflare Relay,
  а прямой WebRTC может не проходить через NAT; fallback работает
  через relay. Проверка на двух физических Android-устройствах
  пока требуется.
- Хост остаётся главным арбитром PvP; при его отключении автоматическая
  передача хостинга другому игроку **не реализована**.
- APK подписана debug-ключом CI: обновление поверх другой debug APK может
  вызвать ошибку несовместимой подписи. Перед удалением прежней версии
  следует сохранить доступные игровые данные.
- **12 уникальных художественных фрагментов** используются на 161 карту;
  это не 161 индивидуальная иллюстрация, а первый полноценный арт-проход.

---

## Историческое описание v0.4–v0.7


Android 8.0+, Russian interface. Self-contained WebView gameplay, Android-native HTTP messaging.
Two independent fifteen-stage branching runs with enemies, elites, shops, random events, rests, relics, rewards and deck upgrades. Surviving players enter a shared turn-based PvP duel.

To play: player A creates a room and sends the generated 20-character code to player B. Player B joins with the code. Both choose classes and proceed independently. When both finish, the host synchronizes the final duel. PvP is host-authoritative and best-effort; it is not cheat-resistant.

Transport: encrypted relay through ntfy.sh, with an experimental direct WebRTC DataChannel when supported by the Android WebView, signaling through the relay. Room code never sent to relay. AES-GCM with PBKDF2 room-secret key; relay metadata visible to ntfy. If direct WebRTC fails due to NAT or WebView limitations, relay is used. No TURN, guaranteed delivery, account system, reconnect resume or third-party service SLA.

This version contains 161 cards, 136 relics, 15 map nodes, 16 card schools, 1–4 enemies and in-app-generated 2D transparent PNG sprites. Each player has distinct encounters and a shared final duel. PvP v0.3: action gauge AV=10000/SPD, speed 75–155, alternating AV queue with no more than two consecutive actions, reaction counter windows before hostile cards, focus currency, DoT ward, simultaneous buff/risk/control choices every five completed actions, 58% HP per-actor-turn incoming damage cap, permanent powers exhausted instead of shuffled back, sudden death after 48 actions, 24 AV/counter cards and 12 AV/counter relics. PvP host is authoritative; relay transport is not formally verified on two physical phones. The artwork, rules text, card names, enemies and game data are original. Inspired by deckbuilding roguelike mechanics, not an asset copy.

Build: JDK 17, Android SDK 35, Gradle 8.9+, `gradle :app:assembleDebug` from this directory.
The CI workflow uploads a debuggable APK. Build success is separate from real two-phone network validation.

## Playable art and controls (v0.4)

The Android game now uses the Ashen Atelier interface, with custom inline-vector fantasy figures and independently authored card art. Combat is left-to-right; cards are presented in a fanned hand at the bottom. Tap an attack card and an enemy to target, or hold/drag it onto the target. Non-target support cards play on tap or when dragged up. The UI shows enemy intents, staggered card dealing, a 15-node vertical branching route, graphical card rewards, merchants, resting, relic chests, and story decisions. PvP retains the v0.3 AV, reaction and five-action dilemma rules and has its own targetable opponent.

Art is programmatically rendered and is NOT copied from Slay the Spire. This is a code-generated vector art pass, not a fully hand-painted asset pack. Runs and network interactions still require testing on real Android phones; unit tests and CI builds do not prove touch ergonomics or two-device reliability.
