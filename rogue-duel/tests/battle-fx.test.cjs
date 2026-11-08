"use strict";
/* v0.15: all animation/audio code must load from APK with no network access,
 * must preserve game math when DOM and WebAudio are unavailable, and must
 * install after the one-screen dock to animate the resulting real nodes. */
const fs=require("node:fs"),assert=require("node:assert/strict"),vm=require("node:vm"),crypto=require("node:crypto");
const base="app/src/main/assets/";
const html=fs.readFileSync(base+"index.html","utf8");
const scripts=[...html.matchAll(/<script src="([^"]+\.js)"><\/script>/g)].map(x=>x[1]);
assert.equal(scripts.at(-1),"battle-fx.js","FX must wrap final battle dock render");
assert(html.includes('href="battle-fx.css"'),"FX CSS not included");
const css=fs.readFileSync(base+"battle-fx.css","utf8");
assert(css.includes("prefers-reduced-motion")&&css.includes("will-change"),"Accessible GPU animation missing");
const script=fs.readFileSync(base+"battle-fx.js","utf8");
for(const forbidden of ["fetch(","XMLHttpRequest","new WebSocket","navigator.vibrate","webkitAudioContext:"])assert(!script.includes(forbidden),"FX must not use forbidden network/permissions: "+forbidden);
assert(script.includes("aria-label")&&script.includes("ash-audio-settings"));
assert(script.includes("oldHit(a,b,base)"),"Damage must come from real combat engine");
assert(script.includes("localStorage.setItem(K"),"Settings must persist");
const fakeApp={innerHTML:"",insertAdjacentHTML(_where,s){this.innerHTML+=s}};
const doc={
 getElementById(id){return id==="app"?fakeApp:{innerHTML:"",textContent:""}},
 addEventListener(){},querySelector(){return null},querySelectorAll(){return []},
 head:{appendChild(){}},
 createElement(){return {getContext(){return {fillRect(){},clearRect(){},beginPath(){},ellipse(){},fill(){},moveTo(){},lineTo(){},stroke(){}}},toDataURL(){return "data:image/png;base64,bW9jaw=="},classList:{add(){},remove(){}}}}
};
const storage=new Map(),window={};
const sandbox={console,document:doc,Date,Math,URL,btoa,atob,crypto:crypto.webcrypto,TextEncoder,TextDecoder,
 setInterval(){return 1},clearInterval(){},setTimeout(){return 1},clearTimeout(){},
 localStorage:{getItem:k=>storage.get(k)||null,setItem:(k,v)=>storage.set(k,v),removeItem:k=>storage.delete(k)},
 location:{reload(){}},alert(x){throw Error(String(x))},AndroidNet:{listen(){},post(){},copyText(){return true}}
};
sandbox.window=sandbox;
vm.createContext(sandbox);
vm.runInContext(html.match(/<script>([\s\S]*?)<\/script>/)[1],sandbox,{filename:"inline",timeout:15000});
for(const scriptName of scripts)vm.runInContext(fs.readFileSync(base+scriptName,"utf8"),sandbox,{filename:scriptName,timeout:15000});
const evalCode=code=>vm.runInContext(code,sandbox,{timeout:15000});
assert.equal(evalCode("typeof ASH_FX.sound"),"function");
assert.equal(evalCode("ASH_FX.sound('impact')"),false,"No audio context => silent fallback");
assert.equal(evalCode("ASH_FX.reduced()"),false);
assert.equal(evalCode("ASH_FX.config.enabled"),true);
evalCode("ASH_FX.set('enabled',false);ASH_FX.set('master',.43);ASH_FX.set('reduce',true)");
assert(storage.has("ashes_av_sound_v015"),"Local preferences missing");
assert.equal(JSON.parse(storage.get("ashes_av_sound_v015")).master,.43);
assert.equal(evalCode("ASH_FX.reduced()"),true);
evalCode("G.demo=true;G.role='host';G.runner=newRun('guard');makeMap();G.stage=10;createFight(false)");
const before=evalCode("G.combat.enemies[G.combat.target].hp");
evalCode("hit(G.combat.player,G.combat.enemies[G.combat.target],11)");
const after=evalCode("G.combat.enemies[G.combat.target].hp");
assert(after<before,"Audio/FX hook must preserve real physical damage");
assert(evalCode("ASH_FX.snapshot().items.length")>=4);
assert.equal(evalCode("ASH_FX.sound('ultimate')"),false,"Mute must suppress sound");
assert.equal(evalCode("ASH_FX.sound('unknown')"),false);
evalCode("ASH_FX.set('shake',false);ASH_FX.set('particles',false)");
assert.equal(evalCode("ASH_FX.config.particles"),false);
console.log("PASS v0.15: offline FX, persistent sound/reduced-motion settings, no-DOM graceful fallback, original combat damage.");
