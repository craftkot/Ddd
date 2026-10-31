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
check("G.runner=newRun('oracle');G.stage=12;createFight(false);");
assert.strictEqual(check("G.combat.enemies.length"),4,"4 targets at late-game stage");
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

/* PvP 0.3 rules: predictable AV, counters, simultaneous gambits, OTK guard. */
vm.runInContext(fs.readFileSync(base+"pvp-balance.js","utf8"),context,{timeout:5000});
const stats=check("window.EXPANSION_STATS");
assert.strictEqual(check("Object.keys(CARDS).length"),161);
assert.strictEqual(check("RELICS.length"),136);
assert.strictEqual(check("PVP_BALANCE_DEBUG.pvAV({gauge:10000,speed:125})"),80);
check([
'G.role="host";G.done=true;G.other={...newRun("oracle"),hp:73};',
'G.runner=newRun("guard");G.duel=null;pvStartDuel();',
'if(!["host","guest"].includes(G.duel.turn))throw Error("missing initial AV turn");',
'if(G.duel.fighters.host.focus<2||G.duel.fighters.guest.focus<2)throw Error("missing counter energy");',
'if(G.duel.phase!=="play")throw Error("wrong phase");',
'if(G.duel.fighters.host.speed!==100||G.duel.fighters.guest.speed!==100)throw Error("base speed");',
'if(G.duel.fighters.host.gauge<0||G.duel.fighters.guest.gauge<0)throw Error("negative gauge");',
'var later=G.duel.turn==="host"?"guest":"host";if(G.duel.fighters[later].ward<5)throw Error("second player has no opening ward");'
].join('\n'));
check([
'var duel=G.duel;var starter=duel.turn;var target=starter==="host"?"guest":"host";',
'duel.fighters[starter].hand=[inst("forge035")];duel.fighters[starter].energy=10;',
'pvAction({action:"play",index:0},starter);',
'if(duel.phase!=="reaction")throw Error("no counter window");',
'if(duel.pending.by!==starter)throw Error("bad pending attacker");',
'pvAction({action:"counter",kind:"guard"},target);',
'if(duel.phase!=="play"||duel.pending)throw Error("counter not resolved");',
'if(duel.fighters[target].focus>2)throw Error("counter was free");'
].join('\n'));
check([
'var duel=G.duel,starter=duel.turn,target=starter==="host"?"guest":"host";',
'duel.fighters[starter].hand=[inst("pvp003")];duel.fighters[starter].energy=10;',
'var startSpeed=duel.fighters[starter].speed;',
'pvAction({action:"play",index:0},starter);',
'if(duel.fighters[starter].speed!==startSpeed+10)throw Error("speed card failed");',
'if(!duel.fighters[starter].exhaust.some(c=>c.id==="pvp003"))throw Error("permanent powers must be exhausted");'
].join('\n'));
check([
'var duel=G.duel;duel.fighters.host.poison=0;duel.fighters.guest.poison=0;',
'for(let i=0;i<5;i++){',
' if(duel.phase!=="play")throw Error("unexpected pause during AV");',
' pvAction({action:"end"},duel.turn);',
'}',
'if(duel.phase!=="gambit"||duel.actions!==5||duel.offers.length!==3)throw Error("gambit not triggered on 5th action");',
'if(new Set(duel.offers.map(x=>x.type)).size!==3)throw Error("gambit must offer risk, buff, and counterplay");',
'pvAction({action:"pick",id:duel.offers[0].id},"host");',
'if(duel.phase!=="gambit")throw Error("must wait for both players");',
'pvAction({action:"pick",id:duel.offers[1].id},"guest");',
'if(duel.phase!=="play"||!duel.turn)throw Error("gambit failed to resume");'
].join('\n'));
check([
'var duel=G.duel; var actor=duel.turn;var enemy=actor==="host"?"guest":"host";',
'duel.fighters[actor].str=70;duel.fighters[actor].energy=30;',
'duel.fighters[actor].hand=Array.from({length:6},()=>inst("rift"));',
'duel.fighters[enemy].block=0;duel.fighters[enemy].ward=0;duel.fighters[enemy].focus=2;',
'var before=duel.fighters[enemy].hp,cap=Math.ceil(duel.fighters[enemy].maxHp*.58);',
'for(let i=0;i<6&&duel.fighters[actor].hand.length;i++){',
'pvAction({action:"play",index:0},actor);',
'if(duel.phase==="reaction")pvAction({action:"counter",kind:"pass"},enemy);',
'}',
'if(before-duel.fighters[enemy].hp>cap)throw Error("single-turn cap bypassed: "+(before-duel.fighters[enemy].hp)+" > "+cap);'
].join('\n'));
console.log("PASS: AV speed, role-independent initiative, counter windows, action-5 simultaneous gambit, one-shot protection.");

/* Stress test 810 bot-vs-bot duels (all 9 class matchups, varying draft archetypes).
   This is a stability / first-mover smoke test, not proof of competitive parity. */
const simulated=check([
'var reports={host:0,guest:0,draw:0,unresolved:0,firstWins:0,firstTotal:0,actions:[],matrix:{}};',
'var archetypes=["Яд","Огонь","Кровь","Оборона","Сила","Проклятия","Знание","Энергия","Шипы","Лёд","Гроза","Тени","Лечение","Охота","Кинжалы","Цена силы"];',
'function drafted(c,school){',
' const r=newRun(c),schoolPool=COMMON.filter(id=>art(id).school===school);',
' for(let i=0;i<8;i++)r.deck.push(inst(choose(schoolPool.length?schoolPool:COMMON)));',
' r.deck.push(inst(choose(["pvp001","pvp002","pvp003","pvp004","pvp005","pvp017","pvp018","pvp024"])));',
' r.relics=unique(RELICS.map(x=>x.id),4);',
' return r;',
'}',
'for(const c1 of ["guard","oracle","hunter"])for(const c2 of ["guard","oracle","hunter"]){',
' for(let game=0;game<90;game++){',
'  G.role="host";G.done=true;G.otherDead=false;G.runner=drafted(c1,choose(archetypes));G.other=drafted(c2,choose(archetypes));G.duel=null;',
'  pvStartDuel();const d=G.duel;const first=d.turn;let count=0;let plays=0;',
'  while(!d.winner&&count++<1000){',
'   if(d.phase==="gambit"){',
'    for(const who of ["host","guest"])if(!d.picks[who])pvAction({action:"pick",id:d.offers[(count+ (who==="host"?0:1))%3].id},who);',
'   }else if(d.phase==="reaction"){',
'    let who=d.pending.by==="host"?"guest":"host";const id=d.pending.card.id;',
'    let kind=(art(id).effects||[]).some(x=>x.key==="detonate"||x.key==="poison"||x.key==="burn")?"purge":"interrupt";',
'    pvAction({action:"counter",kind},who);',
'   }else if(d.phase==="play"){',
'    const who=d.turn,f=d.fighters[who];',
'    const legal=f.hand.map((c,i)=>({c,i})).filter(({c})=>art(c.id).c<=f.energy&&art(c.id).t!=="reaction");',
'    if(legal.length&&plays<7){',
'     const attacks=legal.filter(({c})=>art(c.id).t==="attack");',
'     let choice=choose(attacks.length&&plays>1?attacks:legal);',
'     pvAction({action:"play",index:choice.i},who);plays++;',
'    }else{pvAction({action:"end"},who);plays=0}',
'   }else throw Error("Unknown PvP phase "+d.phase);',
'  }',
'  if(count>=1000||!d.winner){reports.unresolved++;continue}',
'  reports[d.winner]++;reports.actions.push(d.actions);',
'  reports.firstTotal++;if(d.winner===first)reports.firstWins++;',
'  const key=c1+"-"+c2;reports.matrix[key]||(reports.matrix[key]={host:0,guest:0,draw:0});reports.matrix[key][d.winner]++;',
' }',
'}',
'reports.avgActions=reports.actions.reduce((a,b)=>a+b,0)/Math.max(1,reports.actions.length);',
'reports.firstMoverWin=reports.firstWins/Math.max(1,reports.firstTotal);',
'reports'
].join('\n'));
console.log("PvP automated combat matrix:",JSON.stringify(simulated));
assert.strictEqual(simulated.unresolved,0,"PvP must never softlock in 810 consecutive simulations");
assert(simulated.avgActions>3&&simulated.avgActions<65,"duel duration must be finite");

check([
'G.role="host";G.done=true;G.runner=newRun("guard");G.other=newRun("oracle");G.duel=null;',
'G.other.deck[0]={id:"madeup_untrusted_card",up:0};',
'pvStartDuel();if(G.duel!==null)throw Error("invalid opponent deck was accepted");',
'G.other=newRun("oracle");pvStartDuel();if(!G.duel)throw Error("valid opponent deck was rejected");',
'var dd=G.duel,side=dd.turn,actor=dd.fighters[side];',
'actor.hand=[inst("pvp003"),inst("pvp003")];actor.energy=12;',
'var rev=dd.revision,beforeSpeed=actor.speed;',
'pvAction({action:"play",index:0,revision:rev},side);',
'if(dd.revision!==rev+1||actor.speed!==beforeSpeed+10)throw Error("first command was not accepted");',
'pvAction({action:"play",index:0,revision:rev},side);',
'if(actor.speed!==beforeSpeed+10||actor.hand.length!==1)throw Error("duplicate delayed command replayed");'
].join('\n'));
console.log("PASS: invalid remote builds rejected; delayed duplicate commands do not consume extra cards.");
