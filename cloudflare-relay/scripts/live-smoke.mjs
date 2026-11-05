import { createHash } from "node:crypto";
import assert from "node:assert/strict";

const ROOT = process.env.EHTID_RELAY_URL ||
  "https://ehtid-chat-relay.craftkot404.workers.dev";
const ROOM = "EHTIDTESTROOM88Z99"; // public, synthetic test fixture
const hash = text => createHash("sha256").update(text,"utf8").digest("hex");
const proof = hash("EHTID-RELAY-ACCESS-V1:" + ROOM);
const topic = "ehtidp2p-" + hash(proof).slice(0,40);
const auth = { Authorization: "Bearer " + proof };
const packet = "v1.AAAAAAAAAAAAAAAA.BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB";

async function request(path, options = {}) {
  return fetch(ROOT + path, { signal: AbortSignal.timeout(15000), ...options });
}
async function run() {
  const health = await request("/health");
  assert.equal(health.status,200,"Cloudflare health status");
  const info = await health.json();
  assert.equal(info.version,"1.0");
  assert.equal(info.ok,true);
  assert.equal(info.authentication,"per-room-capability");

  const noAccess = await request("/auth?topic=" + topic);
  assert.equal(noAccess.status,401,"anonymous room request must fail");
  const wrong = await request("/auth?topic=" + topic,{
    headers: {Authorization: "Bearer " + hash("EHTID-RELAY-ACCESS-V1:ANOTHERROOMCODE999")}
  });
  assert.equal(wrong.status,401,"another room's proof must fail");
  const authorized = await request("/auth?topic=" + topic,{headers:auth});
  assert.equal(authorized.status,200,"valid room must authenticate");
  assert.equal((await authorized.json()).authenticated,true);

  const posted = await request("/"+topic, {
    method:"POST", headers:{...auth,"Content-Type":"text/plain","Cache":"no"}, body:packet
  });
  assert.equal(posted.status,201,"encrypted packet must be stored");
  assert.equal((await posted.json()).accepted,true);

  const pulled = await request("/"+topic+"/json?since=30m",{headers:auth});
  assert.equal(pulled.status,200,"encrypted history must be readable");
  const lines=(await pulled.text()).trim().split("\n").map(line=>JSON.parse(line));
  assert.ok(lines.some(item=>item.event==="message" && item.message===packet),
    "same encrypted packet must roundtrip through the real D1 database");
  console.log("EHTID v1.0 live Worker /health, /auth, D1 write/read: PASS");
}
run().catch(error=>{console.error("EHTID v1.0 live smoke: FAILED", error.message);process.exitCode=1;});
