# EHTID P2P Chat v0.2 — Internet

This is a **prototype** Android messenger for any pair of Internet-connected phones. It no longer requires the same Wi-Fi network.

## Usage

1. Install the same APK on both phones. Allow access to the Internet.
2. Phone A: open the app and send a message. It becomes **host**. Copy its 20-character room code and share it privately.
3. Phone B: open app, paste the code into "Вставь код собеседника" and tap "Войти".
4. The app automatically uses a hosted rendezvous service to find each other. It attempts an **encrypted direct WebRTC DataChannel** with public STUN servers.
5. If a carrier's CGNAT, firewall or router prevents direct P2P, **messages are sent as encrypted ciphertext via the ntfy.sh public relay**, so both can still chat with Internet access and the app open. The UI displays which transport is active.

## Architecture / limitations

- Real P2P: WebRTC DataChannel, DTLS encryption, ICE + STUN (Google/Cloudflare).
- Signaling/rendezvous: ntfy.sh topic named by SHA-256 of room code; no personal accounts.
- Fallback text relay: ntfy.sh (not P2P), AES-256-GCM with a key derived from the 20-character high-entropy room code.
- Every outgoing message has a random identifier, messages are deduplicated and last 100 are saved on-device per room.
- DataChannel messages use ACK+3-second relay fallback if direct delivery isn't confirmed.
- The host sends its recent history when the guest joins.
- Code must be sent once out-of-band. Devices cannot find the correct remote chat without any identifier.
- **No TURN server**. CGNAT/firewall combinations may prevent *direct* WebRTC; fallback requires ntfy.sh reachability and its external service availability, quotas and retention.
- Not designed as a secure production messenger: there is no forward secrecy in relay fallback, no independently verified peer identity, no code revocation, no background service, no push notifications, no guaranteed offline delivery and no formal audit. The room code is a shared secret; never post publicly.
- The relay is a third-party service; it can see communication metadata and ciphertext, but not decrypt it without the room code.
- Messages may be lost if offline longer than the relay's cache retention. This is a best-effort prototype.
- Android target SDK 32, min 24.

## Build

Use JDK 17, Android SDK 35, Gradle 8.9:

    cd p2p-chat
    gradle --no-daemon :app:assembleDebug

CI: branch feature/p2p-chat -> Actions -> Build P2P Chat APK -> artifact EHTID-P2P-Chat-debug-APK.
