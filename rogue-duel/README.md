# Пепел и карты — 2-player card roguelike (alpha v0.3)

Android 8.0+, Russian interface. Self-contained WebView gameplay, Android-native HTTP messaging.
Two independent fifteen-stage branching runs with enemies, elites, shops, random events, rests, relics, rewards and deck upgrades. Surviving players enter a shared turn-based PvP duel.

To play: player A creates a room and sends the generated 20-character code to player B. Player B joins with the code. Both choose classes and proceed independently. When both finish, the host synchronizes the final duel. PvP is host-authoritative and best-effort; it is not cheat-resistant.

Transport: encrypted relay through ntfy.sh, with an experimental direct WebRTC DataChannel when supported by the Android WebView, signaling through the relay. Room code never sent to relay. AES-GCM with PBKDF2 room-secret key; relay metadata visible to ntfy. If direct WebRTC fails due to NAT or WebView limitations, relay is used. No TURN, guaranteed delivery, account system, reconnect resume or third-party service SLA.

This version contains 161 cards, 136 relics, 15 map nodes, 16 card schools, 1–4 enemies and in-app-generated 2D transparent PNG sprites. Each player has distinct encounters and a shared final duel. PvP v0.3: action gauge AV=10000/SPD, speed 75–155, alternating AV queue with no more than two consecutive actions, reaction counter windows before hostile cards, focus currency, DoT ward, simultaneous buff/risk/control choices every five completed actions, 58% HP per-actor-turn incoming damage cap, permanent powers exhausted instead of shuffled back, sudden death after 48 actions, 24 AV/counter cards and 12 AV/counter relics. PvP host is authoritative; relay transport is not formally verified on two physical phones. The artwork, rules text, card names, enemies and game data are original. Inspired by deckbuilding roguelike mechanics, not an asset copy.

Build: JDK 17, Android SDK 35, Gradle 8.9+, `gradle :app:assembleDebug` from this directory.
The CI workflow uploads a debuggable APK. Build success is separate from real two-phone network validation.
