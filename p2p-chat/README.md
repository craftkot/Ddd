# EHTID Chat v0.9 — Cloudflare Workers + D1

Android chat application; supports multiple users over the internet without
a Windows PC, ntfy.sh rate limits or manual WebRTC offer/answer exchange.

### Cloudflare setup

The server source, tests, D1 schema, deployment workflow and Cloudflare
account setup instructions are in
[`cloudflare-relay/README.md`](../../cloudflare-relay/README.md).

Once deployed, open EHTID Chat v0.9 on each phone and tap
**«Подключить Cloudflare»**. Enter:

1. Your Worker HTTPS URL (`https://...workers.dev`) from deployment logs.
2. The shared `EHTID_RELAY_TOKEN` value (24+ characters) you created.
3. The same 20-character chat room code as other participants.

Tap **«Проверить»**: the app calls `/health` and `/auth`. Then
**«Подключиться»** to save the settings. The server URL and token are not
hardcoded into the APK. The earlier public ntfy.sh relay is not automatically
used.

### Technical design

- Android-to-Worker: encrypted packet POST and bounded JSON Lines polling.
- Worker responses include a cursor to avoid rereading old history;
  ~10-second foreground polling, ~30-second background polling.
- Cloudflare D1 stores ciphertext for 7 days. Short-lived presence
  packets expire after 12 minutes.
- Group notifications require Android background permission and may be delayed
  or suspended by operating system battery management.
- Offline outgoing messages remain in the local outgoing queue and are retried.
- An accepted POST is **not** proof that the other device received a message.
- Android's local message history is preserved during in-place updates,
  provided the permanent signing key and package name are unchanged.
- Legacy manual phone-to-phone WebRTC mesh is available under Settings, but
  it is off by default.

### Build

Android 7+; JDK 17, Gradle 8.9, Android SDK 35:

```sh
cd p2p-chat
gradle :app:testDebugUnitTest :app:assembleDebug
```

GitHub Actions on `feature/p2p-chat` build and sign the APK with the same
permanent signing certificate used from v0.5 onwards.

### Limitations

Cloudflare Free is not unlimited (Workers and D1 quotas apply). Shared
server token is not per-user identity verification and should be kept private.
The cryptographic design is experimental and is not security-audited.
The source code can be built without a Cloudflare account, but a real online
Worker and D1 database cannot be provisioned without authorization from
the owner's Cloudflare account.
