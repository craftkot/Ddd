/**
 * EHTID Cloudflare Relay v0.9
 * Compatible with the Android client: POST /ehtidp2p-{40hex}
 * and GET /ehtidp2p-{40hex}/json?since=30m|7d|e{seq}.
 *
 * The server never decrypts payloads; clients encrypt messages with AES-GCM.
 * A private, server-wide bearer token prevents anonymous writes/reads.
 */
const TOPIC = /^ehtidp2p-[a-f0-9]{40}$/;
const MAX_WIRE_BYTES = 12000;
const MAX_BATCH = 150;
const MESSAGE_TTL = 7 * 24 * 60 * 60 * 1000;
const EPHEMERAL_TTL = 12 * 60 * 1000;
const POLL_SECONDS = 10;
const HEADERS = {
  "Cache-Control": "no-store",
  "X-Content-Type-Options": "nosniff",
  "X-EHTID-Relay": "cloudflare-d1",
  "X-EHTID-Poll-Seconds": String(POLL_SECONDS),
};

function responseJson(data, status = 200, extra = {}) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { ...HEADERS, "Content-Type": "application/json; charset=utf-8", ...extra },
  });
}

function allowedToken(header, configured) {
  if (typeof configured !== "string" || configured.length < 24) return false;
  if (typeof header !== "string" || !header.startsWith("Bearer ")) return false;
  const value = header.slice(7);
  if (value.length !== configured.length) return false;
  // Avoid a variable-time early exit on the first mismatching character.
  let mismatch = 0;
  for (let i = 0; i < value.length; i++) mismatch |= value.charCodeAt(i) ^ configured.charCodeAt(i);
  return mismatch === 0;
}

function validateWire(value) {
  if (value.startsWith("v1.")) {
    return /^v1\.[A-Za-z0-9_-]{12,32}\.[A-Za-z0-9_-]{20,11800}$/.test(value);
  }
  if (value.startsWith("frag1:")) {
    const parts = value.split(":", 5);
    if (parts.length !== 5) return false;
    const [, token, idx, total, chunk] = parts;
    if (!/^[0-9a-f]{32}$/.test(token)) return false;
    if (!/^\d{1,2}$/.test(idx) || !/^\d{1,2}$/.test(total)) return false;
    const n = Number(total), i = Number(idx);
    return n >= 2 && n <= 24 && i >= 0 && i < n &&
      /^[A-Za-z0-9_.-]{1,2100}$/.test(chunk);
  }
  return false;
}

async function readBody(request) {
  if (!request.body) return "";
  const size = Number(request.headers.get("Content-Length"));
  if (Number.isFinite(size) && size > MAX_WIRE_BYTES) return null;
  const reader = request.body.getReader();
  const chunks = [];
  let total = 0;
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    total += value.byteLength;
    if (total > MAX_WIRE_BYTES) {
      await reader.cancel();
      return null;
    }
    chunks.push(value);
  }
  const bytes = new Uint8Array(total);
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.length;
  }
  try { return new TextDecoder("utf-8", { fatal: true }).decode(bytes); }
  catch { return null; }
}

function formatId(seq) {
  return "e" + String(seq).padStart(16, "0");
}

function parseSince(raw) {
  if (!raw || raw === "30m") return { type: "period", ms: 30 * 60_000 };
  if (/^e\d{1,18}$/.test(raw)) {
    const seq = Number(raw.slice(1));
    if (Number.isSafeInteger(seq) && seq >= 0) return { type: "cursor", seq };
  }
  const match = /^([1-7])d$|^([1-9]|[12][0-9]|30)h$/.exec(raw);
  // Whitespace is not allowed in a client cursor.
  if (match && match[1]) return { type: "period", ms: Number(match[1]) * 86_400_000 };
  if (match && match[2]) return { type: "period", ms: Number(match[2]) * 3_600_000 };
  const min = /^([1-9]|[1-5]\d)m$/.exec(raw);
  if (min) return { type: "period", ms: Number(min[1]) * 60_000 };
  return null;
}

async function publish(request, env, topic) {
  const payload = await readBody(request);
  if (payload === null) return responseJson({ ok: false, error: "packet too large or invalid UTF-8" }, 413);
  if (!validateWire(payload)) return responseJson({ ok: false, error: "invalid encrypted packet" }, 400);
  const now = Date.now();
  const persistent = request.headers.get("Cache") !== "no";
  const expiresAt = now + (persistent ? MESSAGE_TTL : EPHEMERAL_TTL);
  await env.DB.prepare(
    "INSERT INTO chat_events (topic, payload, created_at, expires_at) VALUES (?, ?, ?, ?)"
  ).bind(topic, payload, now, expiresAt).run();
  return responseJson({ ok: true, accepted: true }, 201);
}

async function subscribe(request, env, topic, url) {
  const since = parseSince(url.searchParams.get("since") || "30m");
  if (!since) return responseJson({ ok: false, error: "invalid since cursor" }, 400);
  const now = Date.now();
  // Upper bound is captured before the page query. Never skip messages written
  // between the initial query and the response when emitting an empty cursor.
  const max = await env.DB.prepare(
    "SELECT COALESCE(MAX(seq), 0) AS upper_seq FROM chat_events WHERE topic = ?"
  ).bind(topic).first();
  const upper = Math.max(0, Number(max?.upper_seq || 0));
  let rows = [];
  if (since.type === "cursor") {
    if (since.seq < upper) {
      const result = await env.DB.prepare(
        "SELECT seq, payload, created_at FROM chat_events " +
        "WHERE topic = ? AND seq > ? AND seq <= ? AND expires_at > ? " +
        "ORDER BY seq ASC LIMIT ?"
      ).bind(topic, since.seq, upper, now, MAX_BATCH).all();
      rows = result.results || [];
    }
  } else {
    const result = await env.DB.prepare(
      "SELECT seq, payload, created_at FROM chat_events " +
      "WHERE topic = ? AND seq <= ? AND created_at >= ? AND expires_at > ? " +
      "ORDER BY seq DESC LIMIT ?"
    ).bind(topic, upper, now - since.ms, now, MAX_BATCH).all();
    rows = (result.results || []).reverse();
  }
  const output = rows.map(row => JSON.stringify({
    event: "message",
    id: formatId(row.seq),
    topic,
    message: row.payload,
    time: Math.floor(row.created_at / 1000),
  }));
  if (rows.length === 0) {
    output.push(JSON.stringify({ event: "cursor", id: formatId(upper), topic }));
  }
  return new Response(output.join("\n") + "\n", {
    status: 200,
    headers: { ...HEADERS, "Content-Type": "application/x-ndjson; charset=utf-8" },
  });
}

export default {
  async fetch(request, env) {
    try {
      const url = new URL(request.url);
      if (url.pathname === "/" || url.pathname === "/health") {
        if (request.method !== "GET") return responseJson({ error: "method not allowed" }, 405);
        return responseJson({
          ok: Boolean(env.DB && env.RELAY_ACCESS_TOKEN?.length >= 24),
          service: "EHTID Cloudflare Relay",
          storage: "D1",
          version: "0.9",
          publicAccess: false,
        }, env.DB && env.RELAY_ACCESS_TOKEN?.length >= 24 ? 200 : 503);
      }

      if (url.pathname === "/auth") {
        if (request.method !== "GET")
          return responseJson({ ok: false, error: "method not allowed" }, 405);
        if (!env.DB || !env.RELAY_ACCESS_TOKEN || env.RELAY_ACCESS_TOKEN.length < 24)
          return responseJson({ ok: false, error: "relay not configured" }, 503);
        if (!allowedToken(request.headers.get("Authorization"), env.RELAY_ACCESS_TOKEN))
          return responseJson({ ok: false, error: "unauthorized" }, 401);
        return responseJson({ ok: true, authenticated: true });
      }
      const parts = url.pathname.split("/").filter(Boolean);
      const topic = parts[0];
      if (!topic || !TOPIC.test(topic) || parts.length > 2 ||
          (parts.length === 2 && parts[1] !== "json")) {
        return responseJson({ ok: false, error: "not found" }, 404);
      }
      if (!env.DB || !env.RELAY_ACCESS_TOKEN || env.RELAY_ACCESS_TOKEN.length < 24) {
        return responseJson({ ok: false, error: "relay not configured" }, 503);
      }
      if (!allowedToken(request.headers.get("Authorization"), env.RELAY_ACCESS_TOKEN)) {
        return responseJson({ ok: false, error: "unauthorized" }, 401);
      }
      if (parts.length === 1 && request.method === "POST") return await publish(request, env, topic);
      if (parts.length === 2 && request.method === "GET") return await subscribe(request, env, topic, url);
      return responseJson({ ok: false, error: "method not allowed" }, 405);
    } catch (problem) {
      console.error("EHTID relay error:", String(problem?.message || problem).slice(0, 200));
      return responseJson({ ok: false, error: "relay unavailable" }, 503,
                          { "Retry-After": "30" });
    }
  },
  async scheduled(_event, env, ctx) {
    ctx.waitUntil(env.DB.prepare(
      "DELETE FROM chat_events WHERE expires_at <= ?"
    ).bind(Date.now()).run());
  },
};
