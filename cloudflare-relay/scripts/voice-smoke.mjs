import { createHash } from "node:crypto";
import { readFile } from "node:fs/promises";
import assert from "node:assert/strict";

const ROOT="https://ehtid-chat-relay.craftkot404.workers.dev";
const room="EHTIDTESTROOM88Z99"; // public synthetic test room, no user audio
const hash=s=>createHash("sha256").update(s,"utf8").digest("hex");
const bearer=hash("EHTID-RELAY-ACCESS-V1:"+room);
const topic="ehtidp2p-"+hash(bearer).slice(0,40);
const audio=await readFile(process.argv[2]||"/tmp/ehtid-test-voice.m4a");
assert.ok(audio.length>=100&&audio.length<2*1024*1024);
const r=await fetch(ROOT+"/"+topic+"/transcribe",{
 method:"POST",headers:{"Authorization":"Bearer "+bearer,"Content-Type":"audio/mp4"},
 body:audio,signal:AbortSignal.timeout(100000)
});
const result=await r.json();
if(r.status!==200 || !result.ok || !result.text || result.text.length<3){
  console.error("Whisper sample failed:",r.status,result.error||"(empty transcript)");
  process.exitCode=1;
}else{
  console.log("Workers AI Whisper sample recognized:",result.text.slice(0,160));
  console.log("EHTID voice transcription live smoke: PASS");
}
