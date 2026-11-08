"use strict";
/* Ashes 0.14 — DoT / rupture / ward / stance-break / 12 new cards.
   Evaluates real browser modules with the CI Node VM shim. */
const assert=require("node:assert/strict");
const vm=require("node:vm");
const fs=require("node:fs");
const crypto=require("node:crypto");
const root="app/src/main/assets/";
const html=fs.readFileSync(root+"index.html","utf8");
const src=[...html.matchAll(/<script src="([^"]+\.js)"><\/script>/g)].map(m=>m[1]);
assert(src.includes("status-engine.js"));
assert(src.indexOf("status-engine.js")>src.indexOf("pvp-balance.js"));
assert(src.indexOf("status-engine.js")<src.indexOf("turn-sequence.js"));
assert(html.includes('href="status-engine.css"'));
const fakeApp={innerHTML:"",insertAdjacentHTML(_where,s){this.innerHTML+=s}};
const document={
 getElementById:id=>id==="app"?fakeApp:{textContent:"",innerHTML:""},
 addEventListener(){},querySelector(){return null},querySelectorAll(){return []},
 head:{appendChild(){}},
 createElement:()=>({getContext:()=>({imageSmoothingEnabled:false,fillRect(){},clearRect(){},beginPath(){},ellipse(){},fill(){},moveTo(){},lineTo(){},stroke(){},closePath(){}}),toDataURL:()=>"data:image/png;base64,bW9jaw==",classList:{add(){},remove(){}}})
};
const storage=new Map();
const sandbox={console,document,crypto:crypto.webcrypto,TextEncoder,TextDecoder,URL,btoa,atob,Date,Math,
 localStorage:{getItem:k=>storage.get(k)||null,setItem:(k,v)=>storage.set(k,v),removeItem:k=>storage.delete(k)},
 setInterval(){return 1},clearInterval(){},setTimeout(){return 1},clearTimeout(){},
 alert(e){throw Error(String(e))},location:{reload(){}},
 AndroidNet:{listen(){},post(){},copyText(){return true}}
};
sandbox.window=sandbox;
const ctx=vm.createContext(sandbox);
vm.runInContext(html.match(/<script>([\s\S]*?)<\/script>/)[1],ctx,{filename:"inline",timeout:12000});
for(const path of src)vm.runInContext(fs.readFileSync(root+path,"utf8"),ctx,{filename:path,timeout:12000});
const run=s=>vm.runInContext(s,ctx,{timeout:12000});
assert.equal(run("ASH_STATUS_CARD_IDS.length"),12);
assert.equal(run("Object.keys(CARDS).length"),173);
run("G.demo=true;G.role='host';G.runner=newRun('oracle');makeMap();G.stage=12;createFight(false)");
assert.equal(run("G.combat.enemies.length"),4);
const check=(js)=>run(js);
check("var e=G.combat.enemies[0];e.hp=e.maxHp=400;e.poison=4;e.burn=3;e.bleed=0;e.block=0;e.ward=0;");
assert.equal(check("ASH_STATUSES.breakdown(e).total"),13,"ignition preview");
assert.equal(check("ASH_DEBUG.ashTick(e,G.combat.player)"),13,"ignition tick");
assert.equal(check("e.poison"),3);
assert.equal(check("e.burn"),2);
check("e.poison=3;e.burn=0;e.bleed=4;");
assert.equal(check("ASH_STATUSES.breakdown(e).total"),9,"infected wound");
check("e.poison=0;e.burn=3;e.bleed=3;");
assert.equal(check("ASH_STATUSES.breakdown(e).total"),11,"searing wound");
check("e.poison=99;e.burn=99;e.bleed=99;");
check("ASH_STATUSES.capStacks(e)");
assert.equal(check("e.poison"),24,"pve poison max");
assert.equal(check("e.burn"),16,"pve burn max");
assert.equal(check("e.bleed"),22,"pve bleed max");
assert.equal(check("ASH_STATUSES.breakdown(e).total"),42,"pve per activation damage cap");
check("e.poison=3;e.burn=0;e.bleed=4;e.mark=4;e.ward=8;e.hp=100;");
const projected=check("ASH_STATUSES.breakdown(e)");
assert.equal(projected.hpDamage,1,"ward should absorb first 8 of 9");
assert.equal(check("ASH_DEBUG.ashTick(e,G.combat.player)"),1,"ward should protect hp");
assert.equal(check("e.mark"),3,"mark decays once each activation");
check("e.hp=400;e.poison=e.burn=0;e.bleed=6;e.ruptureLock=0;e.stagger=0;e.staggerLock=0;G.combat.player.str=0;");
const first=check("hit(G.combat.player,e,5)");
const second=check("hit(G.combat.player,e,5)");
assert(first>=second+2,"rupture should empower first hit only");
assert.equal(check("e.bleed"),5,"rupture consumes one bleed");
check("e.stagger=97;e.staggerLock=0;e.stun=0;hit(G.combat.player,e,7);");
assert.equal(check("e.stun"),1,"stance-break should interrupt PvE enemy");
assert.equal(check("e.staggerLock"),2);
check("e.hp=e.maxHp=400;e.poison=20;e.burn=14;e.bleed=17;e.staggerLock=0;e.ward=0");
const before=check("e.hp");
check("ashEffect({key:'detonate',value:2},G.combat.player,e,inst('strike'),false)");
assert(before-check("e.hp")<=64,"DoT detonation cap should prevent instant deletion");
assert.equal(check("e.poison+e.burn+e.bleed"),0,"detonation consumes all DoTs");
check("G.combat.player.poison=10;G.combat.player.bleed=8;ASH_STATUSES.capStacks(G.combat.player);ashEffect({key:'purify',value:80},G.combat.player,e,inst('strike'),false)");
assert.equal(check("G.combat.player.poison"),2);
assert.equal(check("G.combat.player.bleed"),1);
const cardHtml=check("ASH_STATUS_CARD_IDS.map(id=>ASH_UI_DEBUG.playcard(inst(id),'reward',0)).join('')");
assert(cardHtml.includes("Пепельный фильтр")&&cardHtml.includes("Разрыв сухожилий"),"new cards not rendered");
assert(cardHtml.includes("очищения ДоТ")&&cardHtml.includes("стойкости"),"new descriptions absent");
check("G.combat.enemies[0].poison=3;G.combat.enemies[0].stagger=44;");
const unitHtml=check("ATELIER_TEST.stage({...G.combat.player,hero:'oracle'},G.combat.enemies,0,true)");
assert(unitHtml.includes("ash-status-forecast")&&unitHtml.includes("ash-break-next"),"enemy telegraph missing");
run("G.combat=null;G.screen='lobby';G.demo=false;G.done=true;G.role='host';G.other=newRun('hunter');G.runner=newRun('guard');G.duel=null;pvStartDuel()");
check("var duel=G.duel;var d=duel.fighters.host;var opp=duel.fighters.guest;d.hp=d.maxHp=200;d.poison=14;d.burn=12;d.bleed=12;d.ward=10;");
const pvp=check("ASH_STATUSES.breakdown(d)");
assert(pvp.total<=28,"PVP tick cap");
assert.equal(check("ASH_DEBUG.ashTick(d,opp)"),Math.max(0,pvp.total-10),"ward mitigation in PvP");
assert.equal(check("d.ward"),0);
check("d.hp=200;d.stagger=95;d.staggerLock=0;d.vuln=0;d.gauge=1000;");
assert.equal(check("ASH_STATUSES.stagger(d,35,opp)"),true,"PvP stance-break");
assert.equal(check("d.stun||0"),0,"PvP break must not hard-stun");
assert(check("d.gauge")>=1700,"PVP break must delay AV");
assert.equal(check("d.vuln"),1);
console.log("PASS status v0.14: 173 cards, 12 new synergies, DoT caps/decay, ward, rupture, cleansing, detonation and PvP break");
