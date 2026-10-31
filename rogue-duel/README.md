# Пепел и карты — 2-player card roguelike (alpha)

Android 8.0+, Russian interface. Self-contained WebView gameplay, Android-native HTTP messaging.
Two independent fifteen-stage branching runs with enemies, elites, shops, random events, rests, relics, rewards and deck upgrades. Surviving players enter a shared turn-based PvP duel.

To play: player A creates a room and sends the generated 20-character code to player B. Player B joins with the code. Both choose classes and proceed independently. When both finish, the host synchronizes the final duel. PvP is host-authoritative and best-effort; it is not cheat-resistant.

Transport: encrypted relay through ntfy.sh, with an experimental direct WebRTC DataChannel when supported by the Android WebView, signaling through the relay. Room code never sent to relay. AES-GCM with PBKDF2 room-secret key; relay metadata visible to ntfy. If direct WebRTC fails due to NAT or WebView limitations, relay is used. No TURN, guaranteed delivery, account system, reconnect resume or third-party service SLA.

This version contains 137 cards, 124 relics, 15 map nodes, 16 card schools, 1–4 enemies and in-app-generated 2D transparent PNG sprites. Each player has distinct encounters and a shared final duel. The artwork, rules text, card names, enemies and game data are original. Inspired by deckbuilding roguelike mechanics, not an asset copy.

Build: JDK 17, Android SDK 35, Gradle 8.9+, `gradle :app:assembleDebug` from this directory.
The CI workflow uploads a debuggable APK. Build success is separate from real two-phone network validation.
