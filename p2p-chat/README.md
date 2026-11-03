# EHTID P2P Chat v0.4 — Internet room beta

An experimental Android chat application for two or more users on different networks.

## Usage

1. Install on Android 7+. Choose a nickname once; it is saved in app storage.
2. Send a message to host a room, then privately share its 20-character room code.
3. Each visitor enters the room code and presses **Войти**. Existing users must remain in the room; joining does not kick previous visitors.
4. **УЧАСТНИКИ** panel displays people currently seen on the network: **В сети** means the chat is open and reachable; **В фоне** means the messaging service is connected while the app is minimized. Relay RTT is approximate, not direct P2P latency.
5. Switch background messages/notifications/preview in settings. The operating system may still suspend services.

## How multiple visitors work

- Text messages are **always published to the encrypted room relay** so every subscriber can receive them, even if the host already has an active WebRTC connection to one guest.
- The existing direct WebRTC connection is preserved when further visitors join; *only one* direct WebRTC channel is implemented. Other visitors communicate via the encrypted relay.
- Participants announce their presence on entry, exit into background, and on a slow cadence. Presence is a best-effort estimate. A crashed phone can appear online for up to 8 minutes (background up to 25 minutes), because only the relay can verify recent activity.
- A new member gets a one-time presence response. Per-guest history sync is rate-limited independently; rooms are not cleared when one visitor leaves.
- Ping uses a rotating peer sample, measured through the relay; it is not comparable to raw ICMP or ICE RTT.

## Important service limits

The public ntfy.sh server has [rate limits](https://docs.ntfy.sh/publish/#limitations) including a daily limit for unauthenticated messages (typically **250 per visitor**). Repeated presence and ping packets were reduced, but continuous all-day group chat **cannot be guaranteed** without a dedicated relay server. A server may respond HTTP 429 and Android may stop background tasks. Treat it as a prototype, not a production messenger.

No TOR, account or phone number is required. Topic names are SHA-256 derived from the room code. Messages are encrypted with AES-GCM using a key derived from the room code; WebRTC DataChannel traffic uses DTLS. Relay transport metadata is visible to its operator. No authentication beyond possession of the room code, no forward secrecy for the relay path, no audited cryptographic protocol, and no reliable queued offline delivery. **Do not use this beta for sensitive communications.**

## Build and tests

JDK 17; Android SDK 35, Gradle 8.9:

```sh
cd p2p-chat
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

Automated unit tests verify: active RTC link is not replaced; independent per-visitor history cooldown; presence response without loops; foreground/background expiry rules.

Continuous integration: GitHub Actions on `feature/p2p-chat`. Installable signed debug APK is uploaded as an Actions artifact and released as **ehtid-p2p-chat-v0.4**. Android emulator tests are best effort; successful compilation does not guarantee message delivery on real devices.
