import test from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.mjs";

const SECRET = "stable-secret-".padEnd(40,"Z");
const TOPIC = "ehtidp2p-" + "a".repeat(40);
const URL = "https://ehtid-chat-relay.example.workers.dev";
const WIRE = "v1." + "A".repeat(16) + "." + "B".repeat(64);

class FakeD1 {
  rows = [];
  index = 0;
  prepare(sql) {
    return {
      bind: (...args) => ({
        run: async () => {
          if (sql.startsWith("INSERT")) {
            const [topic,payload,created_at,expires_at] = args;
            this.rows.push({ seq: ++this.index, topic, payload, created_at, expires_at });
            return { success:true };
          }
          if (sql.startsWith("DELETE")) {
            this.rows = this.rows.filter(row => row.expires_at > args[0]);
            return { success:true };
          }
          throw new Error("Unhandled run query: " + sql);
        },
        first: async () => {
          if (!sql.includes("MAX(seq)")) throw new Error("Unhandled first query");
          const matches = this.rows.filter(row => row.topic === args[0]);
          return { upper_seq: Math.max(0, ...matches.map(x => x.seq)) };
        },
        all: async () => {
          if (!sql.startsWith("SELECT seq")) throw new Error("Unhandled select query");
          let filtered;
          if (sql.includes("seq > ?")) {
            const [topic,seq,upper,now,limit] = args;
            filtered = this.rows.filter(row => row.topic === topic &&
              row.seq > seq && row.seq <= upper && row.expires_at > now)
              .sort((a,b) => a.seq-b.seq).slice(0,limit);
          } else {
            const [topic,upper,minTime,now,limit] = args;
            filtered = this.rows.filter(row => row.topic === topic &&
              row.seq <= upper && row.created_at >= minTime && row.expires_at > now)
              .sort((a,b) => b.seq-a.seq).slice(0,limit);
          }
          return { results: filtered };
        },
      }),
    };
  }
}
function environment() { return {DB:new FakeD1(),RELAY_ACCESS_TOKEN:SECRET}; }
function headers(extra = {}) { return { Authorization:"Bearer "+SECRET, ...extra }; }
function req(method,path,body,customHeaders = {}) {
  return new Request(URL+path,{method,
    headers:headers(customHeaders),
    ...(body===undefined ? {} : { body })});
}
function messages(text) { return text.trim().split("\n").map(line=>JSON.parse(line)); }

test("health is public but does not disclose token",async()=>{
  const e=environment();
  const r=await worker.fetch(new Request(URL+"/health"),e);
  assert.equal(r.status,200);
  const data=await r.json();
  assert.equal(data.ok,true);
  assert.equal(data.service,"EHTID Cloudflare Relay");
  assert.equal(JSON.stringify(data).includes(SECRET),false);
  const unconfigured=await worker.fetch(new Request(URL+"/health"),{DB:e.DB});
  assert.equal(unconfigured.status,503);
});

test("phone can check its shared token without reading a room",async()=>{
  const e=environment();
  const ok=await worker.fetch(req("GET","/auth"),e);
  assert.equal(ok.status,200);
  assert.equal((await ok.json()).authenticated,true);
  const bad=await worker.fetch(new Request(URL+"/auth",{
    headers:{ Authorization:"Bearer not-the-secret" }
  }),e);
  assert.equal(bad.status,401);
  assert.equal(e.DB.rows.length,0);
});

test("all room operations require the shared secret",async()=>{
  const e=environment();
  for(const method of ["POST","GET"]) {
    const path=method==="POST"?"/"+TOPIC:"/"+TOPIC+"/json?since=30m";
    const r=await worker.fetch(new Request(URL+path,{
      method, ...(method==="POST"?{body:WIRE}:{})
    }),e);
    assert.equal(r.status,401);
  }
  assert.equal(e.DB.rows.length,0);
});

test("two phones receive the same encrypted event with stable cursors",async()=>{
  const e=environment();
  const sent=await worker.fetch(req("POST","/"+TOPIC,WIRE,{"Cache":"yes"}),e);
  assert.equal(sent.status,201);
  assert.equal(e.DB.rows[0].payload,WIRE);
  assert.equal(e.DB.rows[0].payload.includes("Привет"),false);
  const a=await worker.fetch(req("GET","/"+TOPIC+"/json?since=7d"),e);
  const b=await worker.fetch(req("GET","/"+TOPIC+"/json?since=30m"),e);
  assert.equal(a.headers.get("X-EHTID-Relay"),"cloudflare-d1");
  assert.equal(a.headers.get("X-EHTID-Poll-Seconds"),"10");
  const received=messages(await a.text());
  assert.equal(received.length,1);
  assert.equal(received[0].event,"message");
  assert.equal(received[0].message,WIRE);
  assert.equal(received[0].id,"e0000000000000001");
  assert.deepEqual(received,messages(await b.text()));

  const cursor=received[0].id;
  const empty=await worker.fetch(req("GET","/"+TOPIC+"/json?since="+cursor),e);
  assert.deepEqual(messages(await empty.text()),[
    {event:"cursor",id:cursor,topic:TOPIC}
  ]);
  const second=await worker.fetch(req("POST","/"+TOPIC,WIRE,{"Cache":"yes"}),e);
  assert.equal(second.status,201);
  const after=await worker.fetch(req("GET","/"+TOPIC+"/json?since="+cursor),e);
  const event=messages(await after.text());
  assert.equal(event.length,1);
  assert.equal(event[0].id,"e0000000000000002");
});

test("opaque packets and invalid topic / cursors are rejected",async()=>{
  const e=environment();
  assert.equal((await worker.fetch(req("POST","/"+TOPIC,"plaintext"),e)).status,400);
  assert.equal((await worker.fetch(req("POST","/"+TOPIC,"x".repeat(13000)),e)).status,413);
  assert.equal((await worker.fetch(req("POST","/random-room",WIRE),e)).status,404);
  assert.equal((await worker.fetch(req("GET","/"+TOPIC+"/json?since=nonsense"),e)).status,400);
  assert.equal(e.DB.rows.length,0);
});

test("fragments and short-lived presence packets roundtrip",async()=>{
  const e=environment();
  const frag="frag1:"+"f".repeat(32)+":0:2:v1."+"A".repeat(16);
  const resp=await worker.fetch(req("POST","/"+TOPIC,frag,{"Cache":"no"}),e);
  assert.equal(resp.status,201);
  assert.equal(e.DB.rows.length,1);
  const delta=e.DB.rows[0].expires_at-Date.now();
  assert.ok(delta > 10*60*1000 && delta < 13*60*1000);
  assert.equal((messages(await (await worker.fetch(req("GET","/"+TOPIC+"/json?since=7d"),e)).text()))[0].message,frag);
  e.DB.rows[0].expires_at = Date.now()-1;
  await worker.scheduled({},e,{waitUntil: p=>p});
  assert.equal(e.DB.rows.length,0);
});

test("history is bounded and paginated without losing later events",async()=>{
  const e=environment();
  const now=Date.now();
  for(let i=0;i<170;i++) {
    e.DB.rows.push({seq:++e.DB.index,topic:TOPIC,payload:WIRE,
      created_at:now-500,expires_at:now+60000});
  }
  const first=messages(await (await worker.fetch(req("GET","/"+TOPIC+"/json?since=7d"),e)).text());
  assert.equal(first.length,150);
  assert.equal(first.at(0).id,"e0000000000000021");
  assert.equal(first.at(-1).id,"e0000000000000170");
  const after=messages(await (await worker.fetch(req("GET","/"+TOPIC+"/json?since=e0000000000000020"),e)).text());
  assert.equal(after.length,150);
  assert.equal(after.at(0).id,"e0000000000000021");
  const empty=messages(await (await worker.fetch(req("GET","/"+TOPIC+"/json?since=e0000000000000170"),e)).text());
  assert.equal(empty[0].event,"cursor");
});
