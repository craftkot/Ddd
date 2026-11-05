# EHTID Cloudflare Relay v1.0 — join by room code only

Cloudflare Workers + D1 backend for Android EHTID Chat v1.0.
The public infrastructure URL is baked into the Android APK:
`https://ehtid-chat-relay.craftkot404.workers.dev`.

**A new participant needs only the APK and a 20-character room code.**
They do **not** need a Cloudflare account, GitHub account, relay URL or
owner-wide server token. Cloudflare is operated and billed by the account owner.

## Security model (no secrets baked into the APK)

Every client independently calculates:

```
room = 20-character random private room code
aes_key = SHA256(room)                 # Unchanged from v0.9
token = lowercase_hex(SHA256("EHTID-RELAY-ACCESS-V1:" + room))
topic = "ehtidp2p-" + lowercase_hex(SHA256(token))[0:40]
```

Each read and write uses `Authorization: Bearer <token>`. The Worker
recomputes SHA256 of the supplied token and compares its first 20 bytes
(40 lowercase hex digits) to the topic in the URL. The server does not
receive the original room code or `aes_key`. Those are intentionally
different from the domain-separated room access capability.

This is capability-based access, not individual account authentication:
anyone who knows a room code can read that room's *encrypted* events and
send messages. Traffic is encrypted with AES-GCM in the client, and HTTPS
to Cloudflare. End-to-end identity verification and key rotation are not
implemented, and the protocol has not received a security audit.

The former `EHTID_RELAY_TOKEN` is **not used** by v1.0 and **must not be
compiled into the APK**. The `CLOUDFLARE_API_TOKEN` also belongs only in
GitHub Actions secrets; never put it in Android.

## API

- `GET /health`: no authentication, returns `{ok:true, version:"1.0"}`
  when the D1 database is bound.
- `GET /auth?topic=ehtidp2p-...`: validate the room-derived bearer
  without publishing a message.
- `POST /ehtidp2p-{40-hex}`: publish one opaque `v1.` or `frag1:` packet.
- `GET /ehtidp2p-{40-hex}/json?since=7d|e{seq}`: retrieve up to 150
  encrypted events as JSON Lines with stable cursors.
- 7-day normal message lifetime; 12-minute short-lived presence.
  Six-hour cron removes expired D1 events.

## Deployment (the owner's Cloudflare account)

The Cloudflare account, D1 `ehtid-chat`, and GitHub connection have
already been configured for this EHTID server. Run:

[GitHub Actions → Deploy EHTID Relay to Cloudflare](https://github.com/craftkot/Ddd/actions/workflows/deploy-cloudflare-relay.yml)

Use the **main** branch. The action checks out `feature/p2p-chat`,
runs `node --test`, installs Wrangler, initializes D1 idempotently,
deploys the Worker and verifies `/health` and room authentication.
Existing GitHub secrets `CLOUDFLARE_API_TOKEN` and
`CLOUDFLARE_ACCOUNT_ID` are used ONLY by the deploy job. No additional
secrets need to be shared with room participants.

The D1 binding is `DB` and configuration is in `wrangler.toml`.
Existing D1 tables stay intact. The Worker is intended to run on
`workers.dev` for the owner's account.

### Migration from v0.9

v1.0 uses a different *relay topic* because credentials can now be
verified per room without revealing the AES key. As a result:

- v0.9 and v1.0 cannot exchange live messages in the same room;
  upgrade every participant to v1.0.
- Previously stored cloud messages under the old topic do not appear
  in v1.0 rooms. Existing on-device chat history is preserved because
  room codes, AES encryption key and Android package name are unchanged.
- The old shared `EHTID_RELAY_TOKEN` secret may be rotated or deleted
  after migration. The v1.0 Worker never accesses it.

### Quotas and abuse

Cloudflare Free Workers / D1 quotas remain finite. 24/7 foreground polling
every 10 seconds consumes approximately 8,640 requests/day **per phone**.
Room proofs do not prevent outsiders from creating new rooms on the
public Worker; malicious request floods can still exhaust the owner's
free quotas. Use Cloudflare abuse/rate limiting controls as needed and
monitor dashboard usage. Client-side encryption is not audited.

To run backend tests without Cloudflare:

```sh
cd cloudflare-relay
node --test
```
