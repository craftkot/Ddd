# EHTID Chat v1.1 — Cloudflare Workers + D1 + Workers AI

The Android client connects automatically to
`https://ehtid-chat-relay.craftkot404.workers.dev` — users only share their
random room code. Cloudflare dashboard setup is required only once for the
server's owner. **Never put an API token or shared relay key in the APK.**

## Encryption and access

A 20-character room code controls both access and E2E encryption via
separate, domain-separated SHA-256 derivations:

```
aes_key = SHA256(roomCode)
token = hex(SHA256("EHTID-RELAY-ACCESS-V1:" + roomCode))
topic = "ehtidp2p-" + hex(SHA256(token))[0:40]
```

All room reads/writes require `Authorization: Bearer <token>`.
Messages and attachments are AES-GCM encrypted on phones; Cloudflare only
stores ciphertext. The room code and `aes_key` are not sent to Cloudflare.
The access token is a room capability, **not verified individual identity**.
Anyone given the code can join the room and send messages.

## File, image and voice attachments

- Max plaintext attachment: **10 MiB**; each is encrypted with a fresh GCM
  nonce and split into 192 KiB base64url parts.
- `POST /{topic}/files/{id}/{index}`: upload encrypted part. Up to 72 parts.
- `POST /{topic}/files/{id}/complete`: finalize with
  `{parts:int, encodedBytes:int}`. The server checks all parts and size.
- `GET /{topic}/files/{id}/meta`: read finalized part count and size.
- `GET /{topic}/files/{id}/{index}`: download ciphertext part.
- The chat's encrypted message contains the file ID, filename, MIME, byte
  length, part count and duration for voice notes.
- Uploaded-but-unfinalized parts are inaccessible to recipients.
- Stored media parts and ordinary messages expire after **7 days**. A
  six-hour Worker cron deletes expired rows.
- The Android app decrypts and caches files privately per room, shows images
  in a viewer, plays AAC/M4A voice notes, and uses Android FileProvider to
  open other files via installed apps.

The D1 **Free plan currently has a 500 MB per-database limit and a 2 MB
row-size limit**. We chunk files to stay under the row limit, but even one
10 MiB file occupies around 14 MiB of encoded ciphertext in the DB. This
is sufficient for small private groups, not a large media archive. R2 is
more appropriate for long-term high-volume file storage, but requires
activation of an R2 subscription in Cloudflare.

## Optional voice transcription — real privacy boundary

`POST /{topic}/transcribe` accepts up to 2 MiB of **unencrypted**
audio/mp4, audio/mpeg, audio/ogg, audio/webm or audio/wav, authenticates the
room capability and passes the audio to
`@cf/openai/whisper` through Workers AI binding `AI`. It returns recognized
text (up to 3,000 characters). No plaintext audio or transcript is stored
in D1 by this endpoint. The UI requires explicit user confirmation before
sending unencrypted audio for AI transcription.

**Audio transcription is NOT end-to-end private**, unlike file sharing.
A user may refuse transcription and still play the original encrypted
voice note. Each client's transcript is cached locally, not broadcast to
every participant. Accuracy and language detection depend on the model.
The request cap is **80 transcription calls per UTC day for the Worker**,
implemented by `ai_usage`; the Cloudflare Workers AI Free quota is finite
(10,000 Neurons per day across models). Unsupported format, over-quota or
service errors must be shown, not represented as successful transcription.

## Deployment

The owner's Cloudflare API and D1 account are already integrated with
GitHub Actions. Deployment action:
[Deploy EHTID Relay to Cloudflare](https://github.com/craftkot/Ddd/actions/workflows/deploy-cloudflare-relay.yml)
on `main` checks out the source from `feature/p2p-chat`, runs
`node --test`, initializes schema idempotently and runs
`node scripts/live-smoke.mjs` against the production Worker.

`wrangler.toml` includes `DB` (D1 database `ehtid-chat`) and
`AI` (Workers AI binding); deployment requires GitHub repository secrets
`CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID`.
The obsolete `EHTID_RELAY_TOKEN` is not used by v1.1.

### Tests

```sh
cd cloudflare-relay
node --test
node scripts/live-smoke.mjs
```

Cloudflare's real-Worker smoke script validates:
- public health and runtime feature flags,
- per-room authorization and rejection of anonymous/wrong-room access,
- encrypted message write/read from actual D1,
- encrypted media chunk upload/finalize/download from actual D1.

A separate optional [Whisper smoke workflow](https://github.com/craftkot/Ddd/actions/workflows/test-ehtid-whisper.yml)
synthesizes non-user speech, sends it to Cloudflare Workers AI and checks
that actual transcription returns text.

### Operational caveats

- No independent security audit or verified identities.
- Server quota abuse remains possible through guessed/random rooms because
  anybody can create a new room; D1 quotas and bot/DoS protection matter.
- Media uploads are not durably queued offline; text messages have local
  retry support. An HTTP 201 confirms server storage, not the recipient
  opening or receiving an attachment.
- The client must keep its original random room code secret. Room-code
  compromise requires creating another room.
- The previous v0.9 global-token Worker protocol is incompatible with
  room-specific capabilities (introduced in v1.0); clients should be v1.1.
