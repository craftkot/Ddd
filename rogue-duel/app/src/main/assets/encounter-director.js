"use strict";
/* v0.7 Encounter director: differentiated enemy behaviour and truthful telegraphs.
   Inspired by genre architecture research; all rules and characters original.
   Plan is computed before the player acts and executed without re-rolling. */
(function(){
const oldMake=createMonster,oldCreate=createFight;
const MOBS=[
 {kind:"bandit",name:"Болотный налётчик",role:"fighter",moves:["slash","lunge","guard"]},
 {kind:"cultist",name:"Пепельный культист",role:"caster",moves:["hex","slash","enrage"]},
 {kind:"skeleton",name:"Скелет-дозорный",role:"guard",moves:["guard","bash","slash"]},
 {kind:"slime",name:"Гнилая слизь",role:"poison",moves:["venom","guard","sever"]},
 {kind:"knight",name:"Рыцарь пепельной клятвы",role:"fighter",moves:["charge","lunge","guard"]},
 {kind:"mage",name:"Ткач пустоты",role:"caster",moves:["hex","drain","sever"]},
 {kind:"hound",name:"Пещерный волк",role:"fighter",moves:["bite","bite","howl"]},
 {kind:"boss",name:"Судья без лица",role:"boss",moves:["charge","sever","hex","rampage"]}
];
const MOVE_NAME={
 slash:"Прямой удар",lunge:"Быстрый выпад",guard:"Стойка за щитом",
 hex:"Проклятие",enrage:"Взгляд безумия",bash:"Щитовой удар",
 venom:"Ядовитые брызги",sever:"Рассекающий удар",charge:"Подготовка натиска",
 drain:"Похищение сил",bite:"Два укуса",howl:"Звериный вой",rampage:"Разрушительный шквал"
};
function modeFor(e){
 const list=e.moves||["slash","guard","venom"];
 return list[(e.intent||0)%list.length]
}
function preview(e){
 const move=modeFor(e),attack=Math.max(1,e.attack||7);
 const scaled=e.role==="boss"?3:0;
 const actions={
 slash:{damage:attack,description:"⚔ "+attack},
 lunge:{damage:attack+3,description:"⚔ "+(attack+3)},
 guard:{damage:Math.max(1,attack-4),block:6+Math.floor(G.stage/2),description:"🛡 "+(6+Math.floor(G.stage/2))+" · ⚔ "+Math.max(1,attack-4)},
 hex:{damage:Math.max(1,attack-5),vuln:1,weak:1,description:"🕸 Слабость/уязвимость · ⚔ "+Math.max(1,attack-5)},
 enrage:{strength:2+scaled,block:4,description:"⚔ Сила +"+(2+scaled)+" · 🛡 4"},
 bash:{damage:attack-1,block:4,description:"🛡 4 · ⚔ "+(attack-1)},
 venom:{damage:Math.max(1,attack-5),poison:3+Math.floor(G.stage/5),description:"☠ Яд +"+(3+Math.floor(G.stage/5))+" · ⚔ "+Math.max(1,attack-5)},
 sever:{damage:Math.max(1,attack-2),bleed:3,description:"🩸 Кровь +3 · ⚔ "+Math.max(1,attack-2)},
 charge:{block:9+Math.floor(G.stage/2),strength:1,description:"🛡 "+(9+Math.floor(G.stage/2))+" · ⚔ Сила +1"},
 drain:{damage:Math.max(1,attack-4),weak:2,heal:5,description:"🕸 Слабость +2 · ⚔ "+Math.max(1,attack-4)+" · ❤ 5"},
 bite:{damage:Math.max(1,Math.floor(attack*.65)),hits:2,description:"⚔ "+Math.max(1,Math.floor(attack*.65))+" ×2"},
 howl:{strength:2,description:"⚔ Сила +2"},
 rampage:{damage:Math.max(1,Math.floor(attack*.55)),hits:3,description:"⚔ "+Math.max(1,Math.floor(attack*.55))+" ×3"}
 };
 const plan={...actions[move],move,label:MOVE_NAME[move]};return plan
}
function announce(e){e.plan=preview(e);e.intentText=e.plan.description;return e.plan}
function make(elite){
 const source=oldMake(elite);
 const def=MOBS.find(m=>m.kind===source.kind)||MOBS[3];
 Object.assign(source,{name:def.name,role:def.role,moves:[...def.moves]});
 if(elite){source.kind="boss";source.name="Судья из пепла";source.role="boss";source.moves=[...MOBS[7].moves]}
 announce(source);return source
}
createMonster=window.createMonster=make;
createFight=window.createFight=function(elite){
 oldCreate(elite);
 if(G.combat?.enemies){
  for(const e of G.combat.enemies)announce(e);
  render()
 }
};
function execute(e,p,c){
 const cmd=e.plan||announce(e);
 if(e.hp<=0)return;
 // Enemy block expires at the start of its own next activation.
 e.block=0;
 if(e.stun>0){e.stun--;logLine(e.name+" оглушён и пропускает ход");return}
 if(cmd.block)e.block+=cmd.block;
 if(cmd.strength)e.str+=cmd.strength;
 if(cmd.weak)p.weak+=cmd.weak;
 if(cmd.vuln)p.vuln+=cmd.vuln;
 if(cmd.poison)p.poison+=cmd.poison;
 if(cmd.bleed)p.bleed+=cmd.bleed;
 if(cmd.heal)ashHeal(e,cmd.heal);
 let total=0;
 for(let i=0;i<(cmd.hits||1)&&p.hp>0;i++)if(cmd.damage)total+=hit(e,p,cmd.damage);
 logLine(e.name+": "+cmd.label+(total?" · "+total+" урона":"")+
    (cmd.poison?" · яд +"+cmd.poison:"")+(cmd.bleed?" · кровотечение +"+cmd.bleed:""))
}
soloEnd=window.soloEnd=function(){
 const c=G.combat;if(!c||!c.enemies)return;
 const p=c.player;endTurnStatuses(p,null);
 if(p.hp<=0){die();return}
 for(const e of c.enemies){
  if(e.hp<=0)continue;
  execute(e,p,c);
  const dmg=ashTick(e,p);
  if(dmg)logLine(e.name+": урон от статусов "+dmg);
  if(e.hp<=0&&!ash.killSet.has(e)){ash.killSet.add(e);ashHook("kill",p,e,{})}
  if(p.hp<=0){die();return}
  e.intent++;
  announce(e);
 }
 if(c.enemies.every(e=>e.hp<=0)){winFight();return}
 c.round++;
 startTurn(p);render()
};
window.ASH_ENCOUNTERS={MOBS,MOVE_NAME,preview,announce,execute};
})();