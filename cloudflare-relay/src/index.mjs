/**
 * EHTID Cloudflare Relay v0.9
 * Compatible with the Android client: POST /ehtidp2p-{40hex}
 * and GET /ehtidp2p-{40hex}/json?since=30m|7d|e{seq}.
 *
 * The server never decrypts payloads; clients encrypt messages with AES-GCM.
 * Per-room capabilities derived from a private room code prevent arbitrary
 * access to known rooms. No owner-wide credentials are distributed in APKs.
 */
const TOPIC = /^ehtidp2p-[a-f0-9]{40}$/;
const MAX_WIRE_BYTES = 12000;
const MAX_BATCH = 150;
const MESSAGE_TTL = 7 * 24 * 60 * 60 * 1000;
const EPHEMERAL_TTL = 12 * 60 * 1000;
const POLL_SECONDS = 10;
// Limit plaintext source attachments to 10 MiB. AES-GCM adds 28 bytes
// (12 byte nonce and 16 byte tag), then base64url uses about 4/3 storage.
const MAX_FILE_BYTES = 10 * 1024 * 1024;
const MAX_TRANSCRIBE_BYTES = 2 * 1024 * 1024;
const MAX_TRANSCRIBE_DAILY = 80;
const MAX_ENCODED_BYTES = Math.ceil((MAX_FILE_BYTES + 28) * 4 / 3) + 3;
const MEDIA_CHUNK_BYTES = 196608;
const MAX_MEDIA_PARTS = Math.ceil(MAX_ENCODED_BYTES / MEDIA_CHUNK_BYTES);
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

/**
 * A phone derives its capability from its private 20-character room code:
 * token = hex(SHA-256("EHTID-RELAY-ACCESS-V1:" + roomCode))
 * topic = "ehtidp2p-" + hex(SHA-256(token))[0..40].
 *
 * The access token is NOT the AES encryption key, and the server never
 * receives the room code. A static global API key is NOT embedded in APKs.
 * Any party knowing a room capability can read ciphertext, but cannot infer
 * the AES key just from this proof.
 */
async function authorizedRoom(request, topic) {
  const authorization=request.headers.get("Authorization") || "";
  if (!/^Bearer [0-9a-f]{64}$/.test(authorization)) return false;
  const proof=authorization.slice(7);
  const digest=new Uint8Array(await crypto.subtle.digest(
      "SHA-256",new TextEncoder().encode(proof)));
  const hex=Array.from(digest,b=>b.toString(16).padStart(2,"0")).join("");
  return topic==="ehtidp2p-"+hex.slice(0,40);
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

async function readBody(request, limit = MAX_WIRE_BYTES) {
  if (!request.body) return "";
  const size = Number(request.headers.get("Content-Length"));
  if (Number.isFinite(size) && size > limit) return null;
  const reader = request.body.getReader();
  const chunks = [];
  let total = 0;
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    total += value.byteLength;
    if (total > limit) {
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


async function transcribeVoice(request,env) {
  if(!env.AI || typeof env.AI.run!=="function")
    return responseJson({ok:false,error:"Workers AI not enabled"},503);
  const ct=(request.headers.get("Content-Type")||"").toLowerCase();
  if(!/^audio\/(mp4|mpeg|m4a|ogg|webm|wav|x-wav)(;|$)/.test(ct))
    return responseJson({ok:false,error:"unsupported audio content type"},415);
  const len=Number(request.headers.get("Content-Length"));
  if(Number.isFinite(len)&&len>MAX_TRANSCRIBE_BYTES)
    return responseJson({ok:false,error:"voice note too large for transcription (2 MiB)"},413);
  const reader=request.body?.getReader();
  if(!reader)return responseJson({ok:false,error:"missing audio"},400);
  const chunks=[];
  let total=0;
  while(true){
    const {done,value}=await reader.read();
    if(done)break;
    total+=value.byteLength;
    if(total>MAX_TRANSCRIBE_BYTES){
      await reader.cancel();
      return responseJson({ok:false,error:"voice note too large for transcription (2 MiB)"},413);
    }
    chunks.push(value);
  }
  if(total<100)return responseJson({ok:false,error:"empty voice note"},400);
  const day=new Date().toISOString().slice(0,10);
  const limit=await env.DB.prepare(
    "INSERT INTO ai_usage(day,requests) VALUES(?,1) " +
    "ON CONFLICT(day) DO UPDATE SET requests=requests+1 WHERE requests<? " +
    "RETURNING requests"
  ).bind(day,MAX_TRANSCRIBE_DAILY).first();
  if(!limit)return responseJson({ok:false,error:"daily voice recognition limit"},429,{"Retry-After":"3600"});

  const raw=new Uint8Array(total);
  let ptr=0;
  for(const chunk of chunks){raw.set(chunk,ptr);ptr+=chunk.length;}
  // Workers AI accepts base64 audio. Copy in bounded slices to avoid
  // argument-limit errors or huge intermediate function calls.
  let binary="";
  for(let i=0;i<raw.length;i+=8192)
    binary+=String.fromCharCode(...raw.subarray(i,i+8192));
  try {
    const result=await env.AI.run("@cf/openai/whisper",{audio:btoa(binary)});
    const transcript=typeof result?.text==="string"?result.text.trim():"";
    if(!transcript)return responseJson({ok:false,error:"no speech detected"},422);
    return responseJson({ok:true,text:transcript.slice(0,3000)});
  } catch(error) {
    console.error("Whisper unavailable:",String(error?.message||error).slice(0,150));
    return responseJson({ok:false,error:"voice recognition temporarily unavailable"},503);
  }
}

const FILE_ID = /^[a-f0-9]{32}$/;

async function mediaRoute(request, env, topic, parts) {
  if (parts.length !== 4 || parts[1] !== "files" || !FILE_ID.test(parts[2]))
    return responseJson({ok:false,error:"invalid attachment path"},404);
  const fileId=parts[2], operation=parts[3];
  const now=Date.now(), expires=now+MESSAGE_TTL;
  if (operation==="complete" && request.method==="POST") {
    const raw=await readBody(request,512);
    if (!raw) return responseJson({ok:false,error:"invalid finalize request"},400);
    let manifest;
    try { manifest=JSON.parse(raw); } catch { return responseJson({ok:false,error:"invalid JSON"},400); }
    const count=manifest?.parts, encoded=manifest?.encodedBytes;
    if (!Number.isInteger(count) || count<1 || count>MAX_MEDIA_PARTS ||
        !Number.isInteger(encoded) || encoded<20 || encoded>MAX_ENCODED_BYTES)
      return responseJson({ok:false,error:"invalid attachment size"},413);

    const found=await env.DB.prepare(
      "SELECT COUNT(*) AS pieces, MIN(part_index) AS first_idx, " +
      "MAX(part_index) AS last_idx, SUM(LENGTH(payload)) AS bytes " +
      "FROM media_parts WHERE topic=? AND file_id=? AND expires_at>?"
    ).bind(topic,fileId,now).first();
    if (Number(found?.pieces)!==count || Number(found?.first_idx)!==0 ||
        Number(found?.last_idx)!==count-1 || Number(found?.bytes)!==encoded)
      return responseJson({ok:false,error:"missing or corrupt attachment chunks"},409);
    await env.DB.prepare(
      "INSERT OR REPLACE INTO media_files (topic,file_id,chunk_count,encoded_bytes,created_at,expires_at) " +
      "VALUES (?,?,?,?,?,?)"
    ).bind(topic,fileId,count,encoded,now,expires).run();
    return responseJson({ok:true,complete:true,parts:count},201);
  }
  if (operation==="meta" && request.method==="GET") {
    const row=await env.DB.prepare(
      "SELECT chunk_count,encoded_bytes FROM media_files " +
      "WHERE topic=? AND file_id=? AND expires_at>?"
    ).bind(topic,fileId,now).first();
    return row ? responseJson({ok:true,parts:row.chunk_count,encodedBytes:row.encoded_bytes}) :
      responseJson({ok:false,error:"attachment not found"},404);
  }
  if (!/^\d{1,2}$/.test(operation)) return responseJson({ok:false,error:"not found"},404);
  const index=Number(operation);
  if (index>=MAX_MEDIA_PARTS) return responseJson({ok:false,error:"chunk out of range"},400);
  if (request.method==="POST") {
    const stored=await env.DB.prepare(
      "SELECT chunk_count FROM media_files WHERE topic=? AND file_id=? AND expires_at>?"
    ).bind(topic,fileId,now).first();
    if (stored) return responseJson({ok:false,error:"attachment already finalized"},409);
    const chunk=await readBody(request,MEDIA_CHUNK_BYTES);
    if (!chunk || chunk.length>MEDIA_CHUNK_BYTES || !/^[A-Za-z0-9_-]+$/.test(chunk))
      return responseJson({ok:false,error:"invalid encrypted attachment chunk"},413);
    await env.DB.prepare(
      "INSERT OR REPLACE INTO media_parts (topic,file_id,part_index,payload,created_at,expires_at) " +
      "VALUES (?,?,?,?,?,?)"
    ).bind(topic,fileId,index,chunk,now,expires).run();
    return responseJson({ok:true,index},201);
  }
  if (request.method==="GET") {
    const meta=await env.DB.prepare(
      "SELECT chunk_count FROM media_files WHERE topic=? AND file_id=? AND expires_at>?"
    ).bind(topic,fileId,now).first();
    if (!meta || index>=meta.chunk_count)
      return responseJson({ok:false,error:"attachment unavailable"},404);
    const found=await env.DB.prepare(
      "SELECT payload FROM media_parts WHERE topic=? AND file_id=? AND part_index=? AND expires_at>?"
    ).bind(topic,fileId,index,now).first();
    if (!found) return responseJson({ok:false,error:"chunk expired"},404);
    return new Response(found.payload,{
      status:200,headers:{...HEADERS,"Content-Type":"text/plain; charset=utf-8"}
    });
  }
  return responseJson({ok:false,error:"method not allowed"},405);
}

export default {
  async fetch(request, env) {
    try {
      const url = new URL(request.url);
      if (url.pathname === "/" || url.pathname === "/health") {
        if (request.method !== "GET") return responseJson({ error: "method not allowed" }, 405);
        return responseJson({
          ok: Boolean(env.DB),
          service: "EHTID Cloudflare Relay",
          storage: "D1",
          version: "1.1",
          authentication: "per-room-capability",
          features: ["media-chunks-v1", ...(env.AI ? ["voice-transcription-opt-in"] : [])],
          publicAccess: false,
        }, env.DB ? 200 : 503);
      }

      if (url.pathname === "/auth") {
        if (request.method !== "GET")
          return responseJson({ ok: false, error: "method not allowed" }, 405);
        if (!env.DB) return responseJson({ ok: false, error: "relay unavailable" }, 503);
        const topic=url.searchParams.get("topic") || "";
        if (!TOPIC.test(topic) || !(await authorizedRoom(request,topic)))
          return responseJson({ ok: false, error: "room access denied" }, 401);
        return responseJson({ ok: true, authenticated: true, topic });
      }
      const parts = url.pathname.split("/").filter(Boolean);
      const topic = parts[0];
      if (!topic || !TOPIC.test(topic) || parts.length>4 ||
          (parts.length===2 && parts[1]!=="json" && parts[1]!=="transcribe") ||
          (parts.length>2 && parts[1]!=="files")) {
        return responseJson({ ok: false, error: "not found" }, 404);
      }
      if (!env.DB) {
        return responseJson({ ok: false, error: "relay database unavailable" }, 503);
      }
      if (!(await authorizedRoom(request, topic))) {
        return responseJson({ ok: false, error: "room access denied" }, 401);
      }
      if (parts.length===2 && parts[1]==="transcribe" && request.method==="POST")
        return await transcribeVoice(request,env);
      if (parts.length===4 && parts[1]==="files")
        return await mediaRoute(request,env,topic,parts);
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
    ctx.waitUntil(env.DB.prepare(
      "DELETE FROM media_parts WHERE expires_at <= ?"
    ).bind(Date.now()).run());
    ctx.waitUntil(env.DB.prepare(
      "DELETE FROM media_files WHERE expires_at <= ?"
    ).bind(Date.now()).run());
    ctx.waitUntil(env.DB.prepare(
      "DELETE FROM ai_usage WHERE day < ?"
    ).bind(new Date(Date.now()-3*86400000).toISOString().slice(0,10)).run());
  },
};
