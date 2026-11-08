"use strict";
/* v0.13 regression: fixed battle UX, completely separate charge meter and SP.
   Uses the same official game bundle, not a reimplementation of its rules. */
const assert=require("node:assert/strict");
const fs=require("node:fs");
const vm=require("node:vm");
const crypto=require("node:crypto");
const base="app/src/main/assets/";
const html=fs.readFileSync(base+"index.html","utf8");
const scripts=[...html.matchAll(/<script src="([^"]+\.js)"><\/script>/g)].map(m=>m[1]);
assert(scripts.indexOf("combat-dock.js")>scripts.indexOf("run-save.js"),"Dock must wrap final game render after run-save");
assert(scripts.indexOf("battle-fx.js")>scripts.indexOf("combat-dock.js"),"Effects must wrap dock render after layout");
assert(html.includes('href="combat-dock.css"'),"Missing combat layout stylesheet");
assert(scripts.indexOf("turn-sequence.js")<scripts.indexOf("atelier.js"));
const styles=fs.readFileSync(base+"combat-dock.css","utf8");
assert(styles.includes("position:fixed")&&styles.includes("overflow-x:auto"),"Battle/hand must remain on screen");
assert(styles.includes(".unit-portrait-frame"),"Shared hero/enemy art frames missing");
assert(styles.includes("dock-potions"),"Consumables must remain accessible");
const dmock={
  innerHTML:"",
  insertAdjacentHTML(_where,t){this.innerHTML+=t;}
};
const document={
  getElementById:id=>id==="app"?dmock:{textContent:"",innerHTML:""},
  addEventListener(){},querySelector(){return null},querySelectorAll(){return []},
  head:{appendChild(){}},
  createElement:()=>({getContext:()=>({imageSmoothingEnabled:false,fillRect(){},clearRect(){},beginPath(){},ellipse(){},fill(){},moveTo(){},lineTo(){},stroke(){},closePath(){}}),
    toDataURL:()=>"data:image/png;base64,bW9jaw==",classList:{add(){},remove(){}}})
};
const memory=new Map();
const ctx={window:null,document,console,crypto:crypto.webcrypto,TextEncoder,TextDecoder,Date,Math,URL,btoa,atob,
  setInterval(){return 1},clearInterval(){},setTimeout(){return 1},clearTimeout(){},
  localStorage:{getItem:x=>memory.get(x)||null,setItem:(x,v)=>memory.set(x,v),removeItem:x=>memory.delete(x)},
  alert(x){throw new Error(String(x))},location:{reload(){}},
  AndroidNet:{listen(){},post(){},copyText(){return true}}
};
ctx.window=ctx;
const context=vm.createContext(ctx);
vm.runInContext(html.match(/<script>([\s\S]*?)<\/script>/)[1],context,{filename:"inline",timeout:15000});
for(const path of scripts){
  vm.runInContext(fs.readFileSync(base+path,"utf8"),context,{filename:path,timeout:15000});
}
const run=code=>vm.runInContext(code,context,{timeout:15000});
run("G.demo=true;G.role='host';G.runner=newRun('guard');makeMap();G.stage=12;createFight(false)");
assert.equal(run("G.combat.av.sp"),3);
assert.equal(run("G.combat.av.ultCharge"),0);
assert.equal(run("ASH_AV.MAX_ULT"),100);
const countBefore=run("G.combat.av.heroActions");
run("G.combat.player.hp=1000;G.combat.player.maxHp=1000");
assert.equal(run("ASH_AV.basicAttack()"),true);
assert.equal(run("G.combat.av.sp"),4,"basic attack must restore exactly one SP");
assert(run("G.combat.av.ultCharge")>=20,"basic attack must provide ultimate energy");
assert.equal(run("G.combat.av.heroActions"),countBefore+1);
run("G.combat.player.hand=[inst('strike')];G.combat.av.sp=4;G.combat.player.energy=4");
const beforeCard=run("G.combat.av.ultCharge");
assert.equal(run("ASH_AV.useCard(0)"),true);
assert(run("G.combat.av.sp")<=3,"playing card must consume SP");
assert(run("G.combat.av.ultCharge")>=beforeCard,"playing card must generate charge");
run("G.combat.av.ultCharge=100;G.combat.av.sp=3;G.combat.player.energy=3");
const hpBefore=run("G.combat.enemies.reduce((n,e)=>n+e.hp,0)");
const actionsBefore=run("G.combat.av.heroActions");
assert.equal(run("ASH_AV.ultimate()"),true,"charged ultimate should activate");
assert(run("G.combat.av.ultCharge")<100,"ultimate must consume 100 charge (enemies may recharge it during the same AV transition)");
assert.equal(run("G.combat.av.sp"),3,"ultimate may not consume SP");
assert.equal(run("G.combat.av.heroActions"),actionsBefore+1,"ultimate must consume an AV action");
assert(run("G.combat.enemies.reduce((n,e)=>n+e.hp,0)")<hpBefore,"ultimate must deal AoE damage");
assert.equal(run("ASH_AV.ultimate()"),false,"cannot activate twice with empty charge");
run("G.combat.av.ultCharge=96;ASH_AV.gainCharge(G.combat,32)");
assert.equal(run("G.combat.av.ultCharge"),100,"ultimate charge capped at 100");
const pvp=run("(()=>{G.combat=null;G.screen='lobby';G.demo=false;G.done=true;G.role='host';G.other=newRun('hunter');G.runner=newRun('guard');G.duel=null;pvStartDuel();const d=G.duel;const by=d.turn;const foe=by==='host'?'guest':'host';d.fighters[by].ultCharge=100;d.fighters[by].hp=300;d.fighters[by].maxHp=300;d.fighters[foe].hp=300;d.fighters[foe].maxHp=300;const hp=d.fighters[foe].hp,actions=d.actions;pvAction({action:'ult',revision:d.revision},by);return {charge:d.fighters[by].ultCharge,hpBefore:hp,hpAfter:d.fighters[foe].hp,actionsBefore:actions,actionsAfter:d.actions}})()");
assert.equal(pvp.charge,0,"PVP ultimate charge must reset");
assert(pvp.hpAfter<pvp.hpBefore,"PVP ultimate must deal damage");
assert(pvp.actionsAfter>pvp.actionsBefore,"PVP ultimate must advance AV");
console.log("PASS v0.13: fixed dock, unified figures, potions, SP and energy are separate; PvE/PvP ultimates, caps and AV.");
