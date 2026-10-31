"use strict";
/* Rules: 1–4 monsters, 3 independent DoTs, explosions, armor, turn timing,
   proc-capped relic hooks, authoritative PvP with the same card semantics. */
const ashOld={makeFighter,apply,winFight,addRelic,beginEffects,renderDuel,dispatch,renderFight};
const ash=window.ASH={battle:null,triggering:false,depth:0,killSet:new WeakSet()};
function ashExtra(f){
 if(!f)return null;
 for(const k of ["burn","bleed","mark","regen","dodge","charge","carry","armorcarry","stun","bonusDamage","combo"])if(!Number.isFinite(f[k]))f[k]=0;
 if(!f.hooks)f.hooks={};return f
}
const ashEnemyList=(a,b)=>{
 if(G.combat&&Array.isArray(G.combat.enemies)&&a===G.combat.player)return G.combat.enemies.filter(x=>x.hp>0);
 return b&&b.hp>0?[b]:[]
};
const ashTarget=(a,b)=>b&&b.hp>0?b:ashEnemyList(a,b)[0]||null;
const ashDot=f=>(f.poison||0)+2*(f.burn||0)+(f.bleed||0);
function ashHeal(f,n){f.hp=Math.min(f.maxHp,f.hp+Math.max(0,n))}
function ashTrueDamage(f,n){f.hp=Math.max(0,f.hp-Math.max(0,n))}
function ashHook(event,owner,target,ctx={}){
 if(ash.triggering||!owner||!owner.relics)return;
 ashExtra(owner);ash.triggering=true;
 try{
  for(const id of owner.relics){
   const r=ASH_EXTRA_RELICS[id];if(!r||r.event!==event)continue;
   const capped=["attack","dot","skill","block","hit_taken","detonate"].includes(event);
   if(capped&&(owner.hooks[id]||0)>=3)continue;
   if(capped)owner.hooks[id]=(owner.hooks[id]||0)+1;
   const v=r.value,key=r.power,enemies=ashEnemyList(owner,target),t=ashTarget(owner,target);
   if(key==="poisonall")enemies.forEach(e=>e.poison+=v);
   else if(key==="burnall")enemies.forEach(e=>e.burn+=v);
   else if(key==="bleedall")enemies.forEach(e=>e.bleed+=v);
   else if(key==="weakall")enemies.forEach(e=>e.weak+=v);
   else if(key==="vulnall")enemies.forEach(e=>e.vuln+=v);
   else if(key==="poison"&&t)t.poison+=v;
   else if(key==="burn"&&t)t.burn+=v;
   else if(key==="bleed"&&t)t.bleed+=v;
   else if(key==="weak"&&t)t.weak+=v;
   else if(key==="mark"&&t)t.mark+=v;
   else if(key==="energy")owner.energy=Math.min(20,owner.energy+v);
   else if(key==="strength")owner.str+=v;
   else if(key==="block")owner.block+=v;
   else if(key==="thorns")owner.thorns+=v;
   else if(key==="charge")owner.charge+=v;
   else if(key==="regen")owner.regen+=v;
   else if(key==="draw")draw(owner,v);
   else if(key==="heal")ashHeal(owner,v);
   else if(key==="damage")owner.bonusDamage+=v;
   else if(key==="gold"&&G.combat&&owner===G.combat.player)G.runner.gold+=v;
   else if(key==="gold"&&event==="win"&&G.runner)G.runner.gold+=v;
   else if(key==="maxhp"&&event==="win"&&G.runner){G.runner.maxHp+=v;G.runner.hp+=v}
  }
 }finally{ash.triggering=false}
}
makeFighter=window.makeFighter=function(r){const f=ashExtra(ashOld.makeFighter(r));f.hero=r.hero;return f};
startTurn=window.startTurn=function(f){
 ashExtra(f);
 const previous=f.rounds||0;
 f.block=previous===0?f.block:f.carry;
 f.carry=0;f.rounds=previous+1;f.energy=3+(previous===0&&f.relics.includes("spark")?1:0)+(previous===0?(f.ashStartupEnergy||0):0);
 if(previous===0&&f.relics.includes("chain"))f.block+=10;
 if(f.regen)ashHeal(f,f.regen);
 f.hooks={};
 draw(f,5);
 ashHook("turn",f,null,{});
};
beginEffects=window.beginEffects=function(f,enemy){
 ashExtra(f);if(enemy)ashExtra(enemy);
 ashOld.beginEffects(f,enemy);
 ashHook("start",f,enemy,{});f.ashStartupEnergy=Math.max(0,f.energy||0);
};
endTurnStatuses=window.endTurnStatuses=function(f,attacker){
 ashExtra(f);
 if(f.armorcarry)f.carry=Math.floor(f.block*.6);
 ashHook("end",f,attacker,{});
 ashTick(f,attacker);
 f.weak=Math.max(0,f.weak-1);f.vuln=Math.max(0,f.vuln-1);
 f.discard.push(...f.hand);f.hand=[];
};
function ashTick(f,attacker){
 ashExtra(f);let n=ashDot(f);if(n)ashTrueDamage(f,n);
 if(n&&attacker)ashHook("dot",attacker,f,{damage:n});
 f.poison=Math.max(0,f.poison-1);
 f.bleed=Math.max(0,f.bleed-2);
 f.burn=Math.max(0,f.burn-1);
 return n
}
hit=window.hit=function(a,b,base){
 ashExtra(a);ashExtra(b);
 let n=Math.max(0,base+a.str+a.charge+a.bonusDamage);
 if(a.weak)n=Math.floor(n*.75);
 if(b.vuln)n=Math.ceil(n*1.5);
 n+=b.mark;
 a.charge=Math.max(0,a.charge-1);
 if(b.dodge){b.dodge--;return 0}
 const absorbed=Math.min(b.block,n);b.block-=absorbed;
 n=Math.max(0,n-absorbed);b.hp=Math.max(0,b.hp-n);
 if(n&&b.thorns)a.hp=Math.max(0,a.hp-b.thorns);
 if(n){ashHook("attack",a,b,{damage:n});ashHook("hit_taken",b,a,{damage:n})}
 if(b.hp<=0&&!ash.killSet.has(b)){ash.killSet.add(b);ashHook("kill",a,b,{})}
 return n
};
function ashEffect(e,a,b,card,pvp){
 let v=e.value+(card.up&&["hit","aoe","block","heal","poison","burn","bleed","aoepoison","aoeburn","aoebleed","multihit","lifesteal","pierce"].includes(e.key)?2:0);
 const all=ashEnemyList(a,b),t=ashTarget(a,b);let dealt=0;
 const hurt=(f,n)=>{if(f)dealt+=hit(a,f,n)};
 switch(e.key){
 case"hit":hurt(t,v);break;
 case"aoe":all.forEach(f=>hurt(f,v));break;
 case"multihit":for(let i=0;i<(card.up?4:3)&&t&&t.hp>0;i++)hurt(t,v);break;
 case"pierce":if(t){let armor=t.block;t.block=0;hurt(t,v);t.block=armor}break;
 case"execute":if(t&&t.hp<t.maxHp*.5)hurt(t,10);break;
 case"lifesteal":hurt(t,v);ashHeal(a,Math.ceil(dealt*.5));break;
 case"shieldhit":hurt(t,Math.ceil(a.block*v));break;
 case"poison":if(t)t.poison+=v;break;
 case"aoepoison":all.forEach(f=>f.poison+=v);break;
 case"burn":if(t)t.burn+=v;break;
 case"aoeburn":all.forEach(f=>f.burn+=v);break;
 case"bleed":if(t)t.bleed+=v;break;
 case"aoebleed":all.forEach(f=>f.bleed+=v);break;
 case"detonate":{
  const targets=v>=3?all:[t];
  targets.filter(Boolean).forEach(f=>{
   let n=ashDot(f)*v;
   ashTrueDamage(f,n);dealt+=n;
   if(n)ashHook("detonate",a,f,{damage:n});
   f.poison=0;f.burn=0;f.bleed=0;
   if(f.hp<=0&&!ash.killSet.has(f)){ash.killSet.add(f);ashHook("kill",a,f,{})}
  });break
 }
 case"spread":if(t){all.filter(f=>f!==t).forEach(f=>{f.poison+=Math.ceil(t.poison*.6);f.burn+=Math.ceil(t.burn*.6);f.bleed+=Math.ceil(t.bleed*.6)})}break;
 case"block":a.block+=v;ashHook("block",a,t,{amount:v});break;
 case"heal":ashHeal(a,v);break;
 case"draw":draw(a,v);break;
 case"energy":a.energy=Math.min(20,a.energy+v);break;
 case"str":a.str+=v;break;
 case"weak":if(t)t.weak+=v;break;
 case"aoeweak":all.forEach(f=>f.weak+=v);break;
 case"vuln":if(t)t.vuln+=v;break;
 case"aoevuln":all.forEach(f=>f.vuln+=v);break;
 case"mark":if(t)t.mark+=v;break;
 case"thorns":a.thorns+=v;break;
 case"regen":a.regen+=v;break;
 case"dodge":a.dodge+=v;break;
 case"charge":a.charge+=v;break;
 case"armorcarry":a.armorcarry+=v;break;
 case"counter":a.thorns+=v;break;
 case"risky":a.hp=Math.max(1,a.hp-v);break;
 case"siphon":a.energy=Math.min(20,a.energy+Math.min(v,ashDot(t||a)));break;
 case"duplicate":{let cp=a.hand.find(x=>art(x.id).t==="attack")||a.hand[0];if(cp&&a.hand.length<10)a.hand.push({...cp});break}
 case"stun":if(t){if(pvp)t.weak+=2;else t.stun+=v}break;
 case"consume":a.block=0;break;
 default:throw Error("Missing effect: "+e.key)
 }
 return dealt
}
apply=window.apply=function(card,a,b){
 ashExtra(a);if(b)ashExtra(b);
 if(!art(card.id).effects)return ashOld.apply(card,a,b);
 let dealt=0;
 for(const effect of art(card.id).effects)dealt+=ashEffect(effect,a,b,card,!(G.combat&&G.combat.player===a));
 if(art(card.id).t==="skill")ashHook("skill",a,b,{card});
 a.combo++;
 return art(card.id).n+(dealt?" → "+dealt+" урона":"")
};
addRelic=window.addRelic=function(id){
 const was=G.runner.relics.includes(id);ashOld.addRelic(id);
 if(!was&&ASH_EXTRA_RELICS[id]&&ASH_EXTRA_RELICS[id].event==="acquire"){
   G.runner.maxHp+=ASH_EXTRA_RELICS[id].value;G.runner.hp+=ASH_EXTRA_RELICS[id].value
 }
};
const ASH_MONSTERS=[
["Болотный налётчик","bandit","fighter"],["Пепельный культист","cultist","caster"],
["Скелет-дозорный","skeleton","guard"],["Зелёная слизь","slime","poison"],
["Рыцарь из праха","knight","fighter"],["Ткач пустоты","mage","caster"],
["Пещерный волк","hound","fighter"],["Судья без лица","boss","boss"]
];
createMonster=window.createMonster=function(elite){
 const [name,kind,role]=choose(ASH_MONSTERS.slice(0,elite?8:7));
 const hp=(elite?65:31)+Math.floor(G.stage*2.8);
 return ashExtra({name,kind:elite?"boss":kind,role,hp,maxHp:hp,attack:7+Math.floor(G.stage*.85),block:0,energy:0,str:elite?2:0,poison:0,weak:0,vuln:0,thorns:0,rounds:0,intent:0,deck:[],discard:[],hand:[],relics:[]})
};
createFight=window.createFight=function(elite){
 const qty=elite?(G.stage>=5?2:1):Math.min(4,1+(G.stage>=2?1:0)+(G.stage>=5?1:0)+(G.stage>=10?1:0));
 const p=makeFighter(G.runner),enemies=Array.from({length:qty},()=>createMonster(elite));
 if(qty>1)enemies.forEach(e=>{e.maxHp=Math.round(e.maxHp/(.65+qty*.18));e.hp=e.maxHp});
 G.combat={player:p,enemies,target:0,elite,round:1};
 G.log=["Вражеский отряд: "+enemies.map(x=>x.name).join(", ")+"."];ash.killSet=new WeakSet();
 beginEffects(p,enemies[0]);startTurn(p);G.screen="fight";render()
};
playSolo=window.playSolo=function(index){
 const c=G.combat;if(!c||!c.enemies)return;
 const p=c.player,e=c.enemies[c.target],k=p.hand[index];
 if(!k||art(k.id).c>p.energy)return;
 p.energy-=art(k.id).c;p.hand.splice(index,1);p.discard.push(k);
 logLine("Ты: "+apply(k,p,e));
 if(p.hp<=0){die();return}
 if(c.enemies.every(x=>x.hp<=0)){winFight();return}
 if(!c.enemies[c.target]||c.enemies[c.target].hp<=0)c.target=Math.max(0,c.enemies.findIndex(x=>x.hp>0));
 render()
};
winFight=window.winFight=function(){
 const c=G.combat;if(!c||!c.enemies)return ashOld.winFight();
 const p=c.player;ashHook("win",p,null,{});
 G.runner.hp=Math.max(1,p.hp);
 if(p.relics.includes("sun"))ashHeal(G.runner,7);
 if(p.relics.includes("flask"))ashHeal(G.runner,12);
 const gold=c.elite?75:30+G.stage*6;G.runner.gold+=gold;
 const options=RELICS.filter(x=>!G.runner.relics.includes(x.id));
 const relic=c.elite&&options.length?choose(options).id:null;
 if(relic)addRelic(relic);
 G.reward={title:"Отряд уничтожен",desc:(relic?"Реликвия: "+RELICS.find(x=>x.id===relic).n+". ":"")+"Выбери одну карту в колоду.",gold,choices:ashAffinityCards(G.runner,3)};
 G.combat=null;G.screen="reward";render()
};
soloEnd=window.soloEnd=function(){
 const c=G.combat;if(!c||!c.enemies)return;
 const p=c.player;endTurnStatuses(p,null);
 if(p.hp<=0){die();return}
 for(const e of c.enemies){
  if(e.hp<=0)continue;
  if(e.stun){e.stun--;logLine(e.name+" пропускает ход.");}
  else {
   const mode=e.intent%4;e.block=0;let dmg=0;
   if(mode===0)dmg=hit(e,p,e.attack);
   if(mode===1){e.block+=7+G.stage;dmg=hit(e,p,Math.max(2,e.attack-3))}
   if(mode===2){p.poison+=2;dmg=hit(e,p,Math.max(2,e.attack-4))}
   if(mode===3){p.bleed+=2;dmg=hit(e,p,Math.max(2,e.attack-2))}
   logLine(e.name+": "+dmg+" урона"+(mode===2?", яд +2":mode===3?", кровь +2":""))
  }
  e.intent++;const dot=ashTick(e,p);if(dot)logLine(e.name+": ДоТ "+dot);
  if(e.hp<=0&&!ash.killSet.has(e)){ash.killSet.add(e);ashHook("kill",p,e,{})}
  if(p.hp<=0){die();return}
 }
 if(c.enemies.every(x=>x.hp<=0)){winFight();return}
 c.round++;startTurn(p);render()
};
applyDuel=window.applyDuel=function(cmd,by){
 const d=G.duel;if(!d||d.winner||d.turn!==by)return;
 const f=d.fighters[by],foe=d.fighters[by==="host"?"guest":"host"];
 ashExtra(f);ashExtra(foe);
 if(cmd.action==="play"){
  const i=Number(cmd.index);if(!Number.isInteger(i)||i<0||i>=f.hand.length)return;
  const c=f.hand[i];if(art(c.id).c>f.energy)return;
  f.energy-=art(c.id).c;f.hand.splice(i,1);f.discard.push(c);
  d.log.push(f.name+": "+apply(c,f,foe));
 }else if(cmd.action==="end"){
  endTurnStatuses(f,foe);d.log.push(f.name+" завершил ход.");
  if(f.hp>0&&foe.hp>0){d.turn=by==="host"?"guest":"host";if(d.turn==="host")d.round++;startTurn(foe)}
 }else return;
 if(f.hp<=0||foe.hp<=0){
  d.winner=f.hp>0?by:(by==="host"?"guest":"host");
  d.log.push("Победа: "+d.fighters[d.winner].name);G.screen="duelEnd"
 }
 broadcastDuel()
};
window.ASH_DEBUG={ashExtra,ashDot,ashTick,ashEffect,ashHook,monsters:ASH_MONSTERS};
