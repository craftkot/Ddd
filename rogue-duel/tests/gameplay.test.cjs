"use strict";
const fs=require("fs"),vm=require("vm"),assert=require("assert"),crypto=require("crypto");
const base="app/src/main/assets/";
const main=fs.readFileSync(base+"index.html","utf8").match(/<script>([\s\S]*?)<\/script>/)[1];
const elements={app:{innerHTML:"",insertAdjacentHTML(){},classList:{add(){},remove(){}}},net:{textContent:""}};
const doc={
 getElementById:(id)=>elements[id]||(elements[id]={innerHTML:"",textContent:""}),
 addEventListener(){},
 querySelector(){return null},
 head:{appendChild(){}},
 createElement(type){
  if(type==="canvas")return {
   width:0,height:0,getContext:()=>({fillStyle:"",lineWidth:0,imageSmoothingEnabled:false,fillRect(){},clearRect(){},beginPath(){},ellipse(){},fill(){},moveTo(){},lineTo(){},stroke(){}}),
   toDataURL:()=>"data:image/png;base64,aGVsbG8="
  };
  return {textContent:"",classList:{add(){},remove(){}}}
 }
};
const sandbox={document:doc,console,crypto:crypto.webcrypto,TextEncoder,TextDecoder,setInterval(){return 1},clearInterval(){},setTimeout(){return 1},clearTimeout(){},URL,Math,alert(){},location:{reload(){}},btoa,atob};
sandbox.window=sandbox;
const context=vm.createContext(sandbox);
for(const s of [main,"expansion-data.js","expansion-relics.js","expansion-combat.js","expansion-visual.js"].map((v,i)=>i===0?v:fs.readFileSync(base+v,"utf8")))vm.runInContext(s,context,{timeout:2000});
function check(src){return vm.runInContext(src,context,{timeout:5000})}
const count=check("window.EXPANSION_STATS");
console.log("CONTENT COUNTS",count);
assert(count.cards>=100,"100+ cards");assert(count.relics>=100,"100+ relics");
assert.strictEqual(count.newCards,112);assert.strictEqual(count.newRelics,112);
check("G.runner=newRun('oracle');G.stage=5;createFight(false);");
assert.strictEqual(check("G.combat.enemies.length"),4,"4 targets at stage 6");
check("G.combat=null;");
check([
'var testHero=makeFighter({...newRun("oracle"),hp:500,maxHp:500});',
'var testEnemy=makeFighter({...newRun("guard"),hp:500,maxHp:500});',
'for(let id of ASH_CARD_IDS){',
' testHero.hp=500;testHero.maxHp=500;testHero.energy=20;testHero.str=0;testHero.block=0;testHero.poison=0;',
' testHero.burn=0;testHero.bleed=0;testHero.hand=[inst("strike")];testHero.deck=[inst("strike")];',
' testEnemy.hp=500;testEnemy.maxHp=500;testEnemy.block=0;testEnemy.poison=0;testEnemy.burn=0;testEnemy.bleed=0;testEnemy.weak=0;testEnemy.vuln=0;testEnemy.dodge=0;',
' apply(inst(id),testHero,testEnemy);',
'}'
].join('\n'));
check([
'var hero=makeFighter({...newRun("oracle"),hp:200,maxHp:200});',
'var enemy=makeFighter({...newRun("guard"),hp:200,maxHp:200});',
'apply(inst("forge003"),hero,enemy);',
'if(enemy.poison!==5)throw Error("poison must stack, got "+enemy.poison);',
'var before=enemy.hp;apply(inst("forge005"),hero,enemy);',
'if(before-enemy.hp<10)throw Error("poison explosion did not hit");',
'if(enemy.poison!==0)throw Error("poison explosion must consume DoT");'
].join('\n'));
check([
'var h2=makeFighter({...newRun("hunter"),hp:150,maxHp:150});',
'var enemies=Array.from({length:4},()=>makeFighter({...newRun("guard"),hp:150,maxHp:150}));',
'G.combat={player:h2,enemies,round:1,target:0};',
'apply(inst("forge010"),h2,enemies[0]);',
'if(enemies.some(e=>e.hp===150||e.burn===0))throw Error("AOE missed an enemy");',
'G.combat=null;'
].join('\n'));
check([
'var h3=makeFighter({...newRun("oracle"),hp:150,maxHp:150});h3.relics=["artifact001"];',
'var squad=Array.from({length:4},()=>makeFighter({...newRun("guard"),hp:150,maxHp:150}));',
'G.combat={player:h3,enemies:squad};',
'ASH_DEBUG.ashHook("start",h3,squad[0]);',
'if(squad.some(e=>e.poison!==3))throw Error("relic did not target whole squad");',
'G.combat=null;'
].join('\n'));
check([
'var f=makeFighter({...newRun("guard"),hp:150,maxHp:150});',
'var foe=makeFighter({...newRun("hunter"),hp:150,maxHp:150});',
'f.poison=3;f.burn=2;f.bleed=4;',
'var hp=f.hp;ASH_DEBUG.ashTick(f,foe);',
'if(hp-f.hp!==11)throw Error("dot tick mismatch: "+(hp-f.hp));'
].join('\n'));
console.log("PASS: 112 cards apply; 4 targets; DoT explosion; relic events; status tick.");
