# EHTID Chat — Cloudflare Workers + D1

**This is the relay backend for EHTID Chat v0.9 (Android).**
It requires no Windows PC, no public IPv4, no Tailscale and no paid VPS.
The server is a small Worker exposing the existing encrypted-message protocol.

## How it works

- `POST /ehtidp2p-{40-hex}`: accept an encrypted `v1.` message or `frag1:` packet.
- `GET /ehtidp2p-{40-hex}/json?since=7d|e{seq}`: return up to 150
  recent events as JSON Lines (compatible with previous Android releases).
- `GET /health`: check service health (public, no secrets).
- `GET /auth`: validate a privately shared bearer key.
- Data is stored **only as ciphertext** in D1. Normal messages have a 7-day
  server retention; short-lived presence packets expire after 12 minutes.
  Expired entries are pruned on a six-hour cron.
- Clients poll no more often than every 10 seconds while the chat is visible,
  or 30 seconds in the background. Responses include stable cursors.
- The server expects a secret header `Authorization: Bearer <key>` on all
  room reads and writes. Anyone without this key receives HTTP 401.
- Every participant needs the same Worker URL, the same shared relay key and
  the same **room code**. The room code supplies the AES-GCM key to clients.
  Server operators and Cloudflare can see metadata and encrypted packets,
  but the Worker does not decrypt messages.
- No fallback to the public ntfy.sh service.

## Option 1: deploy from GitHub Actions (no local software)

**The Cloudflare account must be yours.** Merely having the source files in
GitHub does not create a Worker or D1 database.

1. Register or log into [Cloudflare](https://dash.cloudflare.com/).
   Activate Workers Free and a `workers.dev` subdomain if prompted.
2. Get your **Cloudflare Account ID** from the account overview.
3. Create a **Cloudflare API token**, scoped to that account, with
   Workers product **Admin** (to create the first Worker) and D1 **Admin**
   (to create the database and run its schema). Limit its lifetime, and
   later reduce permissions if desired.
   Documentation:
   https://developers.cloudflare.com/workers/ci-cd/external-cicd/github-actions/
4. In GitHub repository `craftkot/Ddd` open
   **Settings → Secrets and variables → Actions → New repository secret**.
   Add THREE secrets:
   - `CLOUDFLARE_API_TOKEN`: the Cloudflare deployment API token.
   - `CLOUDFLARE_ACCOUNT_ID`: the Cloudflare account ID.
   - `EHTID_RELAY_TOKEN`: a fresh random secret of at least 24 characters,
     *different from the Android signing password*. Keep your own copy:
     you must enter it on every phone. Never publish it in GitHub/chat.
5. Open **Actions → Deploy EHTID Relay to Cloudflare → Run workflow**
   and choose branch `feature/p2p-chat`.
   The workflow runs the tests, creates/reuses `ehtid-chat` D1, initializes
   `schema.sql`, deploys the Worker and uploads the secret.
6. In the completed log **Deploy authenticated Worker** copy its
   `https://...workers.dev` address. Open `ADDRESS/health` in a browser.
   It must return `"ok":true` and `"service":"EHTID Cloudflare Relay"`.
7. Install the Android APK **v0.9 or newer**, tap **Cloudflare**, paste the
   Worker URL and `EHTID_RELAY_TOKEN`, tap **Проверить**, then connect.
   All participants use the same URL and key but never have to use Windows.
   Share the room code privately to join the same room.

> Do not confuse `CLOUDFLARE_API_TOKEN` (used by GitHub to deploy your
> Worker) and `EHTID_RELAY_TOKEN` (used by phones to access your relay).
> Never put the Cloudflare deployment API token into the phone.

## Option 2: deploy via local Wrangler CLI

Requires Node.js 22+ and access to the Cloudflare account:

```powershell
cd cloudflare-relay
npm install
npx wrangler login
npx wrangler d1 create ehtid-chat --location weur
```

Copy the resulting D1 database UUID into `wrangler.toml` in place of the
all-zero placeholder. Then:

```powershell
npx wrangler d1 execute ehtid-chat --remote --file=./schema.sql --yes
npx wrangler deploy
npx wrangler secret put RELAY_ACCESS_TOKEN
```

Set the secret value to the same 24+ character string you will enter on the
phones. Wrangler prints the public URL after deployment. You can also use
`npx wrangler d1 list --json` to find an existing database UUID.

## Running tests without Cloudflare

```sh
cd cloudflare-relay
node --test
```

Tests cover authentication, opaque ciphertext, pagination, multi-client
reads, retention, invalid packets and HTTP responses. GitHub Actions runs
these on every change.

## Real limits and weaknesses

Cloudflare Workers Free: **100,000 requests/day**. D1 Free:
**5,000,000 rows read/day; 100,000 rows written/day; 5 GB storage**.
Limits are enforced, so *this is not an unlimited messenger*.
For example, a chat open 24/7 consumes approximately 8,640 requests/day
at 10-second polling (not counting sends), while an always-background phone
uses about 2,880 requests/day at 30-second polling.

One server-wide relay key is enough for this small private group, but it is
not individual user authentication; anyone with both the relay token and room
code can join that room. Leaked tokens can exhaust quotas. Rotation requires
changing the Worker secret and all Android clients.

No push notification service operates while Android is force-stopped,
and Worker polling may be delayed by Android background restrictions.
The server acknowledges ingestion, **not guaranteed recipient delivery**.
All cryptography in this prototype is unaudited; do not use for high-sensitivity
communications.
