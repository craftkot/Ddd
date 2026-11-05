import test from "node:test";
import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import worker from "../src/index.mjs";

const ROOM = "EHTIDTESTROOM88Z99";
const hash = input => createHash("sha256").update(input,"utf8").digest("hex");
const SECRET = hash("EHTID-RELAY-ACCESS-V1:" + ROOM);
const TOPIC = "ehtidp2p-" + hash(SECRET).slice(0,40);
const URL = "https://ehtid-chat-relay.example.workers.dev";
const WIRE = "v1." + "A".repeat(16) + "." + "B".repeat(64);

class FakeD1 {
  rows = [];
  index = 0;
  mediaParts = [];
  mediaFiles = [];
  aiUsage = new Map();
  prepare(sql) {
    return {
      bind: (...args) => ({
        run: async () => {
          if (sql.startsWith("INSERT OR REPLACE INTO media_parts")) {
            const [topic,file_id,part_index,payload,created_at,expires_at]=args;
            this.mediaParts=this.mediaParts.filter(x=>!(x.topic===topic&&x.file_id===file_id&&x.part_index===part_index));
            this.mediaParts.push({topic,file_id,part_index,payload,created_at,expires_at});
            return {success:true};
          }
          if (sql.startsWith("INSERT OR REPLACE INTO media_files")) {
            const [topic,file_id,chunk_count,encoded_bytes,created_at,expires_at]=args;
            this.mediaFiles=this.mediaFiles.filter(x=>!(x.topic===topic&&x.file_id===file_id));
            this.mediaFiles.push({topic,file_id,chunk_count,encoded_bytes,created_at,expires_at});
            return {success:true};
          }
          if (sql.startsWith("INSERT")) {
            const [topic,payload,created_at,expires_at] = args;
            this.rows.push({ seq: ++this.index, topic, payload, created_at, expires_at });
            return { success:true };
          }
          if (sql.startsWith("DELETE")) {
            if(sql.includes("media_parts")) this.mediaParts=this.mediaParts.filter(row=>row.expires_at>args[0]);
            else if(sql.includes("media_files")) this.mediaFiles=this.mediaFiles.filter(row=>row.expires_at>args[0]);
            else this.rows=this.rows.filter(row=>row.expires_at>args[0]);
            return { success:true };
          }
          throw new Error("Unhandled run query: " + sql);
        },
        first: async () => {
          if(sql.includes("INSERT INTO ai_usage")) {
            const [day,max]=args;
            const n=this.aiUsage.get(day)||0;
            if(n>=max)return null;
            this.aiUsage.set(day,n+1);
            return {requests:n+1};
          }
          if (sql.includes("COUNT(*) AS pieces")) {
            const [topic,file_id,now]=args;
            const rows=this.mediaParts.filter(x=>x.topic===topic&&x.file_id===file_id&&x.expires_at>now);
            return {pieces:rows.length,first_idx:rows.length?Math.min(...rows.map(x=>x.part_index)):null,
              last_idx:rows.length?Math.max(...rows.map(x=>x.part_index)):null,
              bytes:rows.reduce((acc,x)=>acc+x.payload.length,0)};
          }
          if (sql.includes("FROM media_files")) {
            const [topic,file_id,now]=args;
            const item=this.mediaFiles.find(x=>x.topic===topic&&x.file_id===file_id&&x.expires_at>now);
            return item?{chunk_count:item.chunk_count,encoded_bytes:item.encoded_bytes}:null;
          }
          if (sql.includes("FROM media_parts")) {
            const [topic,file_id,index,now]=args;
            const item=this.mediaParts.find(x=>x.topic===topic&&x.file_id===file_id&&x.part_index===index&&x.expires_at>now);
            return item?{payload:item.payload}:null;
          }
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
function environment() { return {DB:new FakeD1()}; }
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
  assert.equal(data.version,"1.1");
  assert.deepEqual(data.features,["media-chunks-v1"]);
  assert.equal(JSON.stringify(data).includes(SECRET),false);
  assert.equal(data.authentication,"per-room-capability");
  const unconfigured=await worker.fetch(new Request(URL+"/health"),{});
  assert.equal(unconfigured.status,503);
});

test("phone can authenticate with a room-specific proof without exposing encryption keys",async()=>{
  const e=environment();
  assert.equal(SECRET,"9dc9cb2b91ec0307ddbee78dabaf0b167226b8da8d8fc8d2c5901f5bee25fe3a");
  assert.equal(TOPIC,"ehtidp2p-d09b81a540e20abb3df12bfc5f50f852d99e6912");
  assert.notEqual(SECRET,hash(ROOM)); // AES remains private, token is domain separated
  const ok=await worker.fetch(req("GET","/auth?topic="+TOPIC),e);
  assert.equal(ok.status,200);
  assert.equal((await ok.json()).authenticated,true);
  const bad=await worker.fetch(new Request(URL+"/auth?topic="+TOPIC,{
    headers:{ Authorization:"Bearer "+hash("EHTID-RELAY-ACCESS-V1:OTHERROOM") }
  }),e);
  assert.equal(bad.status,401);
  assert.equal(e.DB.rows.length,0);
});

test("all room operations require their own per-room capability",async()=>{
  const e=environment();
  for(const method of ["POST","GET"]) {
    const path=method==="POST"?"/"+TOPIC:"/"+TOPIC+"/json?since=30m";
    const r=await worker.fetch(new Request(URL+path,{
      method, ...(method==="POST"?{body:WIRE}:{})
    }),e);
    assert.equal(r.status,401);
  }
  assert.equal(e.DB.rows.length,0);
  const wrongRoom=hash("EHTID-RELAY-ACCESS-V1:OTHERROOM");
  const unauthorized=await worker.fetch(new Request(URL+"/"+TOPIC+"/json?since=30m",{
    headers:{Authorization:"Bearer "+wrongRoom}
  }),e);
  assert.equal(unauthorized.status,401);
  // Knowing the public topic is not sufficient to read another room.
  assert.notEqual("ehtidp2p-"+hash(wrongRoom).slice(0,40),TOPIC);
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

test("media chunks enforce per-room authorization and finalize complete sets",async()=>{
  const e=environment();
  const id="f".repeat(32);
  const first="ABcd".repeat(2000);
  const second="Efgh".repeat(1000);
  const base="/"+TOPIC+"/files/"+id;
  const anonymous=await worker.fetch(new Request(URL+base+"/0",{method:"POST",body:first}),e);
  assert.equal(anonymous.status,401);
  assert.equal((await worker.fetch(req("POST",base+"/0",first),e)).status,201);
  assert.equal((await worker.fetch(req("POST",base+"/1",second),e)).status,201);
  const badManifest=await worker.fetch(req("POST",base+"/complete",
    JSON.stringify({parts:3,encodedBytes:first.length+second.length})),e);
  assert.equal(badManifest.status,409);
  const good=await worker.fetch(req("POST",base+"/complete",
    JSON.stringify({parts:2,encodedBytes:first.length+second.length})),e);
  assert.equal(good.status,201);
  const meta=await worker.fetch(req("GET",base+"/meta"),e);
  assert.deepEqual(await meta.json(),{ok:true,parts:2,encodedBytes:first.length+second.length});
  assert.equal(await (await worker.fetch(req("GET",base+"/0"),e)).text(),first);
  assert.equal(await (await worker.fetch(req("GET",base+"/1"),e)).text(),second);
  assert.equal((await worker.fetch(req("POST",base+"/0",first),e)).status,409);
  const wrong=await worker.fetch(new Request(URL+base+"/meta",{
    headers:{Authorization:"Bearer "+"0".repeat(64)}
  }),e);
  assert.equal(wrong.status,401);
});
test("media rejects attachments larger than 10 MiB, invalid ids and oversized chunks",async()=>{
  const e=environment(),id="a".repeat(32),base="/"+TOPIC+"/files/"+id;
  assert.equal((await worker.fetch(req("POST",base+"/complete",
    JSON.stringify({parts:73,encodedBytes:20000})),e)).status,413);
  assert.equal((await worker.fetch(req("POST",base+"/complete",
    JSON.stringify({parts:1,encodedBytes:20000000})),e)).status,413);
  assert.equal((await worker.fetch(req("POST",base+"/0","ABC".repeat(70000)),e)).status,413);
  assert.equal((await worker.fetch(req("POST","/"+TOPIC+"/files/invalid/0","AAAA"),e)).status,404);
});
test("opt-in voice transcription requires room authorization, media type and limits",async()=>{
  const e=environment();
  e.AI={run:async(model,params)=>{
    assert.equal(model,"@cf/openai/whisper-large-v3-turbo");
    assert.ok(params.audio.length>50);
    return {text:"Привет, это тестовая расшифровка"};
  }};
  const path="/"+TOPIC+"/transcribe";
  const audio="A".repeat(1000);
  const without=await worker.fetch(new Request(URL+path,{
    method:"POST",headers:{"Content-Type":"audio/mp4"},body:audio
  }),e);
  assert.equal(without.status,401);
  const badFormat=await worker.fetch(req("POST",path,audio,{"Content-Type":"application/json"}),e);
  assert.equal(badFormat.status,415);
  const tooLarge=await worker.fetch(req("POST",path,"x".repeat(2100000),{"Content-Type":"audio/mp4"}),e);
  assert.equal(tooLarge.status,413);
  const accepted=await worker.fetch(req("POST",path,audio,{"Content-Type":"audio/mp4"}),e);
  assert.equal(accepted.status,200);
  assert.equal((await accepted.json()).text,"Привет, это тестовая расшифровка");
  assert.equal([...e.DB.aiUsage.values()][0],1);
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
