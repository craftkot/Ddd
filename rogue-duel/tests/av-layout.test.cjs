"use strict";
/* v0.10 regression tests: AV scheduling, five SP, 4 visible enemies and
   complete card text. Run with node tests/av-layout.test.cjs from rogue-duel/. */
const fs=require("fs"),vm=require("vm"),assert=require("node:assert/strict"),crypto=require("crypto");
const base="app/src/main/assets/";
const html=fs.readFileSync(base+"index.html","utf8");
const inline=html.match(/<script>([\s\S]*?)<\/script>/)[1];
const scripts=[...html.matchAll(/<script src="([^"]+\.js)"><\/script>/g)].map(m=>m[1]);
assert(scripts.includes("turn-sequence.js"),"AV module is not included in APK HTML");
assert(scripts.indexOf("turn-sequence.js")>scripts.indexOf("encounter-director.js"),"AV must run after director");
assert(scripts.indexOf("turn-sequence.js")<scripts.indexOf("atelier.js"),"AV must run before UI");
const store=new Map(),app={innerHTML:"",insertAdjacentHTML(_,s){this.innerHTML+=s}};
const doc={
 getElementById:id=>id==="app"?app:{textContent:"",innerHTML:""},
 addEventListener(){},querySelector(){return null},querySelectorAll(){return []},
 head:{appendChild(){}},createElement:()=>({getContext:()=>({
  imageSmoothingEnabled:false,fillRect(){},clearRect(){},beginPath(){},ellipse(){},
  fill(){},moveTo(){},lineTo(){},stroke(){},closePath(){} }),toDataURL:()=>"data:image/png;base64,bW9jaw==",
  classList:{add(){},remove(){}}})
};
const ctx={console,document:doc,crypto:crypto.webcrypto,TextEncoder,TextDecoder,Date,Math,URL,btoa,atob,
 setInterval(){return 1},clearInterval(){},setTimeout(){return 1},clearTimeout(){},
 localStorage:{getItem:k=>store.get(k)||null,setItem:(k,v)=>store.set(k,v),removeItem:k=>store.delete(k)},
 alert(s){throw Error(String(s))},location:{reload(){}},AndroidNet:{listen(){},post(){},copyText(){return true}}};
ctx.window=ctx;
const context=vm.createContext(ctx);
vm.runInContext(inline,context,{filename:"index-inline",timeout:8000});
for(const src of scripts)vm.runInContext(fs.readFileSync(base+src,"utf8"),context,{filename:src,timeout:8000});
const run=s=>vm.runInContext(s,context,{timeout:8000});
const numCards=run("Object.keys(CARDS).length");
assert(numCards>=161,"Old card library missing");
assert.equal(run("ASH_AV.MAX_SP"),5);
assert.equal(run("ASH_AV.BASE_GAUGE"),10000);
run("G.demo=true;G.role='host';G.runner=newRun('guard');makeMap();G.stage=12;createFight(false)");
assert.equal(run("G.combat.enemies.length"),4);
assert.equal(run("G.combat.av.actors.length"),5);
assert.equal(run("G.combat.av.sp"),3);
assert.equal(run("G.combat.av.current"),"hero");
assert(app.innerHTML.includes("fate-stage count-4"),"four-person formation missing");
assert(app.innerHTML.includes("fate-av-bar"),"AV strip missing");
assert(app.innerHTML.includes("fate-sp-points"),"5 SP slots missing");
assert(app.innerHTML.includes("fate-basic"),"basic attack missing");
for(let i=0;i<4;i++)assert(app.innerHTML.includes('data-foe-index="'+i+'"'),"target "+i+" is not visible");
assert.equal(run("ASH_AV.preview(G.combat,7).length"),7,"AV forecast must have 7 actions");
run("G.combat.player.hp=500;G.combat.player.maxHp=500");
assert(run("ASH_AV.basicAttack()"),"Basic Attack must resolve");
assert.equal(run("G.combat.av.sp"),4,"Basic Attack must restore one SP");
assert(run("G.combat.av.clock")>0,"AV clock never advanced");
run("G.combat.player.hand=[inst('strike')];G.combat.av.sp=4;G.combat.player.energy=4");
assert(run("playSolo(0)"),"one-cost card should resolve");
assert.equal(run("G.combat.av.sp"),3,"one-cost card must spend one SP");
run("G.combat.player.hand=[inst('strike')];G.combat.av.sp=0;G.combat.player.energy=0");
assert.equal(run("playSolo(0)"),false,"can't play unaffordable card");
assert.equal(run("G.combat.player.hand.length"),1,"rejected action removed card");
run("G.combat.av.sp=5;G.combat.player.energy=5");
assert(run("ASH_AV.basicAttack()"),"basic at full SP must remain legal");
assert.equal(run("G.combat.av.sp"),5,"five SP cap violated");
const ccss=fs.readFileSync(base+"fate-combat.css","utf8");
assert(ccss.includes("grid-template-columns:repeat(2,minmax(0,1fr))"),"enemy grid not defined");
assert(ccss.includes("max-height:none!important"),"card description clipping still enabled");
assert(ccss.includes("overflow-wrap:anywhere"),"long words must wrap");
assert(ccss.includes("min-height:530px"),"four-enemy stage is too short");
const allCards=run("Object.keys(CARDS).map(id=>({id,html:playcard(inst(id),'reward',0),desc:esc(desc(inst(id)))}))");
for(const {id,html:markup,desc} of allCards){
 assert(markup.includes('class="card-desc"'),"description container missing for "+id);
 assert(markup.includes(desc),"FULL description absent/truncated on "+id);
 assert(markup.includes('class="card-title"'),"visible card title missing for "+id);
 assert(markup.includes('class="card-art"'),"card illustration missing for "+id);
}
const lengths=run("Object.keys(CARDS).map(id=>desc(inst(id)).length)");
assert(Math.max(...lengths)<500,"new oversized text needs extra card-size adaptation");
assert.equal(run("G.combat.av.maxSp"),5);
run("G.combat=null;G.screen='lobby';G.demo=false;G.done=true;G.role='host';G.other=newRun('hunter');G.runner=newRun('guard');G.duel=null;pvStartDuel()");
assert.equal(run("G.duel.fighters.host.sp"),3,"PVP host SP not initialized");
assert.equal(run("G.duel.fighters.guest.sp"),3,"PVP guest SP not initialized");
const after=run("(()=>{const d=G.duel;const by=d.turn;d.fighters[by].hp=160;d.fighters[by].maxHp=160;pvAction({action:'basic'},by);return {sp:d.fighters[by].sp,actions:d.actions}})()");
assert(after.sp>=3&&after.sp<=5,"PVP basic attack must respect 5 SP cap");
assert(after.actions>=1,"PVP basic action did not advance AV");
console.log("PASS AV/Fate: 1-4 opponents, 7-entry AV queue, 5 SP, full texts across "+numCards+" cards; PvP basic.");
