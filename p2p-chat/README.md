# EHTID Chat 0.7 — phone-relayed WebRTC mesh (experimental)

This Android app now defaults to a **phone-to-phone mesh transport**. Chat
messages are forwarded by participating phones over encrypted WebRTC
DataChannels. **The ntfy.sh relay is not contacted in mesh mode**; the older
ntfy transport remains an optional switch in Settings.

## Establish a connection WITHOUT a messaging relay

All participants must use the same 20-character room code.

1. Phone A: open a room and share its room code with phone B (copy/share).
2. Phone B: enter the same room code and tap **Войти**.
3. Phone A: **↔ Соединить телефоны P2P** -> **Создать приглашение**.
4. Phone A: copy or share the resulting **EHTIDM1:** token with phone B
   using any out-of-band mechanism (SMS, email, messenger, QR utility, etc).
5. Phone B: **↔ Соединить телефоны P2P** -> **Вставить код** -> paste
   invitation and tap **Применить**. The app generates an **answer**.
6. Phone B: send the full answer token back to phone A.
7. Phone A: **↔ Соединить телефоны P2P** -> **Вставить код** -> paste
   answer. The two phones should connect through ICE/STUN if their NATs permit.
8. Repeat for more phones: any connected participant can create another
   invitation and link a new phone. Example: A---B---C. B forwards messages
   A<->C, and all nodes deduplicate repeated events.

**Important limitations:**

- Manual signaling is required for each newly created WebRTC connection.
  A peer may need to repeat the handshake after both apps are restarted.
- CGNAT/symmetric NAT, VPN/firewalls and carrier mobile restrictions can
  prevent direct connectivity. Google/Cloudflare STUN is used only to discover
  ICE candidates; **without TURN there is no guaranteed internet connection**.
- Phones act as relays only while Android keeps their WebRTC sessions alive
  (foreground service / battery optimizations apply). If a bridging phone
  goes offline, network partitions can occur.
- No ntfy message quota in mesh mode, but all phones still have battery,
  bandwidth, and maximum-connection constraints (up to 16 direct links per
  phone, max 8 forwarding hops, last 100 messages synced on join).
- Group message forwarding is opportunistic and not yet audited for security
  or independently verified on real multi-phone mobile networks.
- The room code is the group secret for older AES-GCM fallback transport.
  Peer mesh uses WebRTC DTLS encryption hop-by-hop; participating relay
  phones can see group messages. It is not a private end-to-end-encrypted
  messenger against other participants.
- The old ntfy-based mode can still be enabled in Settings if desired;
  it remains subject to the ntfy.sh HTTP 429 quota unless pointed at a
  compatible independently hosted service.

## Settings

**P2P-режим без сервера** is enabled by default. Disabling it restarts the
transport, connects to the configured ntfy-compatible relay, and uses
the old room transport. Enabling it again disconnects the public relay.

## Build and CI

Android 7+, JDK 17, Gradle 8.9, SDK 35.
```bash
cd p2p-chat
gradle :app:testDebugUnitTest :app:assembleDebug
```

CI on `feature/p2p-chat` signs published APKs with the permanent key from
0.5 and above. The v0.7 update can install over v0.5/v0.6 without uninstall.
Android emulator smoke checks do not constitute a real multi-phone
connectivity test.
