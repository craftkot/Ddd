"use strict";
/* 0.3: AV combat, reaction counterplay, synchronized 5-action gambits.
   Original rules inspired by speed / action-gauge mechanics of turn based RPGs. */
const PVP_ACTION_CARDS=[
["pvp001","Срыв ритма","⏳",1,"skill","Задержать действие врага на 20% AV.","delay20"],
["pvp002","Перехват времени","⚡",1,"skill","Продвинуть следующее своё действие на 20%. Получить 5 брони.","advance20|block5"],
["pvp003","Лёгкая поступь","👟",1,"power","Получить 10 скорости до конца боя.","speed10"],
["pvp004","Связанные гири","⛓️",2,"skill","Замедлить врага на 12 скорости и наложить слабость.","slow12|weak1"],
["pvp005","Ускоренный разрез","🗡️",1,"attack","Удар 8. Продвинуть следующее действие на 10%.","hit8|advance10"],
["pvp006","Петля","🌀",2,"power","Ускориться на 12, добрать карту.","speed12|draw1"],
["pvp007","Нарушить очередь","🕰️",2,"attack","Удар 12. Отбросить AV врага на 25%.","hit12|delay25"],
["pvp008","Часовой якорь","⚓",1,"skill","Броня 12, скорость −6. Следующий ход на 12% раньше.","block12|speed-6|advance12"],
["pvp009","Расчёт импульса","🔷",0,"skill","Взять карту. Продвинуть следующий ход на 8%.","draw1|advance8"],
["pvp010","Тяжёлые шаги","🥾",1,"attack","Удар 15. Следующее действие на 12% позже.","hit15|retard12"],
["pvp011","Синхронный клинок","🔆",2,"attack","Дважды ударить по 5. Скорость +5.","multihit5|speed5"],
["pvp012","Замедляющая печать","🕸️",1,"skill","Замедлить врага на 8. 5 брони.","slow8|block5"],
["pvp013","Чистая реакция","🛡️",1,"reaction","Контркарта: 20 брони и щит от ДоТ 10.","counterguard20"],
["pvp014","Затвор крови","🧪",1,"reaction","Контркарта: очистить 80% ДоТ и сопротивляться взрыву.","counterclean80"],
["pvp015","Разрыв заклятия","✋",1,"reaction","Контркарта: уменьшить урон и наложение статусов на 55%.","counterbreak55"],
["pvp016","Зеркальный шрам","🪞",1,"reaction","Контркарта: отразить 10 урона и получить защиту.","counterreflect10"],
["pvp017","Сброс угрозы","🧿",1,"skill","Очистить половину своего ДоТ, +8 защиты.","purify50|block8"],
["pvp018","Тихий эликсир","🌿",1,"skill","Очистить 80% ДоТ, вылечить 6.","purify80|heal6"],
["pvp019","Непробиваемая вера","💎",2,"power","Получить щит от прямого ДоТ 12 и регенерацию 2.","ward12|regen2"],
["pvp020","Заслон призрака","🌫️",1,"skill","Щит от взрывов и ДоТ 10, 6 брони.","ward10|block6"],
["pvp021","Энергетический налог","💸",2,"skill","Отнять 1 энергию у врага в следующем ходе, продвинуть AV на 8%.","tax1|advance8"],
["pvp022","Гарантированное окно","⌛",2,"attack","10 урона. Следующее действие на 16% раньше.","hit10|advance16"],
["pvp023","Прерывающий импульс","⚔️",0,"reaction","Контркарта: защита от 35% входящего урона.","counterbreak35"],
["pvp024","Покой в движении","🪷",1,"skill","Броня 8, щит от ДоТ 6, скорость +3.","block8|ward6|speed3"]
];
const PVP_RELICS=[
["pvp_relic01","Песочные часы","⏳","Стартовая скорость +8.","speed",8],
["pvp_relic02","Ртутный колокол","🔔","Стартовое продвижение AV на 12%.","advance",12],
["pvp_relic03","Тормозное колесо","⚙️","В начале PvP задержать врага на 8% AV.","delay",8],
["pvp_relic04","Малый циферблат","🕰️","После каждого своего хода продвинуть AV на 5%.","eachAdvance",5],
["pvp_relic05","Нить секунды","🧵","Каждый пятый общий ход: +6 скорости.","gambitSpeed",6],
["pvp_relic06","Стальной оберег","🛡️","В начале PvP +16 брони.","block",16],
["pvp_relic07","Антидотная цепь","🧪","В начале PvP +18 защиты от ДоТ.","ward",18],
["pvp_relic08","Колесо реакции","🔄","В начале PvP +1 контрресурс.","focus",1],
["pvp_relic09","Тихое зеркало","🪞","Каждый пятый общий ход +8 защиты от ДоТ.","gambitWard",8],
["pvp_relic10","Бронзовый хронометр","🧭","После каждого хода противника ускоряет твой AV на 3%.","enemyAdvance",3],
["pvp_relic11","Два отражения","🎭","При начале PvP +1 контрресурс, но −6 скорости.","tradeFocus",1],
["pvp_relic12","Алый противовес","❤️","Стартовое здоровье +10 (в рамках максимума) и −5 скорости.","vitalSlow",10]
];
for(const [id,n,e,c,t,d,fx] of PVP_ACTION_CARDS){
 let effects=ashParseFX(fx);
 CARDS[id]={n,e,t,c,d,v:effects[0].value,k:"combo",effects,school:t==="reaction"?"Контрприёмы":"Хронология"};
 COMMON.push(id);
}
for(const [id,n,e,d,power,value] of PVP_RELICS)RELICS.push({id,n,e,d,pvpPower:power,pvpValue:value,group:"Хронология"});
CLASSES.guard.deck.push("pvp013","pvp017");
CLASSES.oracle.deck.push("pvp014","pvp009");
CLASSES.hunter.deck.push("pvp015","pvp005");
const PVP_CHOICES=[
 {id:"edge",name:"Алый натиск",description:"+3 силы, но получить 2 уязвимости.",type:"buff"},
 {id:"rampart",name:"Последний бастион",description:"+15 брони и +10 щита ДоТ, но −7 скорости.",type:"buff"},
 {id:"haste",name:"Рывок часов",description:"+14 скорости и −9 здоровья.",type:"buff"},
 {id:"plague",name:"Ядовитый договор",description:"Врагу +5 яда, себе +2 яда.",type:"risk"},
 {id:"purify",name:"Очищение",description:"Очистить ДоТ и восстановить 11 здоровья.",type:"buff"},
 {id:"tempo",name:"Ускорение судьбы",description:"Твоё AV вперёд на 22%, врага назад на 8%.",type:"control"},
 {id:"mirror",name:"Отражённый ответ",description:"+1 контрресурс и +10 щита ДоТ.",type:"control"},
 {id:"sand",name:"Песок в шестернях",description:"Задержать противника на 18% AV. Ему +5 брони.",type:"risk"},
 {id:"berserk",name:"Предел плоти",description:"+4 силы, потерять 11 здоровья.",type:"risk"},
 {id:"blessing",name:"Тихое благословение",description:"+3 регенерации, но замедлиться на 7.",type:"buff"},
 {id:"exhaust",name:"Великий сброс",description:"Добрать до 7 карт, потерять 5 здоровья.",type:"control"},
 {id:"barrier",name:"Зеркальная крепость",description:"+15 щита ДоТ. Следующий ход позже на 12% AV.",type:"buff"}
];
const PV={gauge:10000,minSpeed:75,maxSpeed:155,turnLimit:48,scriptVersion:3};
const pvClamp=(v,a,b)=>Math.max(a,Math.min(b,v));
function pvRelic(f,kind,enemy=null){
 for(const id of f.relics||[]){
  const r=RELICS.find(x=>x.id===id);
  if(!r||!r.pvpPower)continue;
  const v=r.pvpValue;
  if(kind==="start"){
   if(r.pvpPower==="speed")f.speed=pvClamp(f.speed+v,PV.minSpeed,PV.maxSpeed);
   if(r.pvpPower==="advance")f.gauge=pvClamp(f.gauge-100*v,1500,15000);
   if(r.pvpPower==="delay"&&enemy)enemy.gauge=pvClamp(enemy.gauge+100*v,1500,18000);
   if(r.pvpPower==="block")f.block+=v;
   if(r.pvpPower==="ward")f.ward+=v;
   if(r.pvpPower==="focus")f.focus=pvClamp(f.focus+v,0,4);
   if(r.pvpPower==="tradeFocus"){f.focus=pvClamp(f.focus+v,0,4);f.speed=Math.max(PV.minSpeed,f.speed-6)}
   if(r.pvpPower==="vitalSlow"){f.hp=Math.min(f.maxHp,f.hp+v);f.speed=Math.max(PV.minSpeed,f.speed-5)}
  }else if(kind==="end"){
   if(r.pvpPower==="eachAdvance")f.nextAdvance=(f.nextAdvance||0)+100*v;
   if(r.pvpPower==="enemyAdvance"&&enemy)enemy.gauge=Math.max(0,enemy.gauge-100*v)
  }else if(kind==="gambit"){
   if(r.pvpPower==="gambitSpeed")f.speed=pvClamp(f.speed+v,PV.minSpeed,PV.maxSpeed);
   if(r.pvpPower==="gambitWard")f.ward=pvClamp(f.ward+v,0,70)
  }
 }
}
function pvMakeFighter(build){
 const f=makeFighter({...build,hp:build.maxHp});ashExtra(f);
 f.speed=100;f.gauge=PV.gauge;f.ward=0;f.focus=2;f.maxFocus=4;f.nextAdvance=0;
 f.nextTax=0;f.exhaust=[];f.damageTurn=0;f.healTurn=0;f.dotTurn=0;
 f.block=0;f.hooks={};f.combo=0;f.rounds=0;f.energy=0;f.hand=[];return f
}
function pvAV(f){return Math.max(0,Math.ceil((f.gauge||0)/(f.speed||100)))}
function pvPreview(d,steps=3){
 const x={host:{g:d.fighters.host.gauge,s:d.fighters.host.speed},guest:{g:d.fighters.guest.gauge,s:d.fighters.guest.speed}};
 const arr=[];
 for(let n=0;n<steps;n++){
  const a=x.host.g/x.host.s,b=x.guest.g/x.guest.s;
  let side=Math.abs(a-b)<.001?(d.tieNext||"host"):(a<b?"host":"guest");
  const wait=x[side].g/x[side].s;
  x.host.g=Math.max(0,x.host.g-wait*x.host.s);
  x.guest.g=Math.max(0,x.guest.g-wait*x.guest.s);
  x[side].g=PV.gauge;
  arr.push(side)
 }
 return arr
}
function pvNext(d){
 if(d.winner||d.phase!=="play")return;
 const host=d.fighters.host,guest=d.fighters.guest;
 const a=host.gauge/host.speed,b=guest.gauge/guest.speed;
 const side=Math.abs(a-b)<.001?(d.tieNext||"host"):(a<b?"host":"guest");
 const dt=Math.min(a,b);
 host.gauge=Math.max(0,host.gauge-dt*host.speed);
 guest.gauge=Math.max(0,guest.gauge-dt*guest.speed);
 d.clock=Number(((d.clock||0)+dt).toFixed(2));
 d.turn=side;
 // One player cannot monopolize turns with AV tricks.
 if(d.previousTurn===side&&d.streak>=2){
  const other=side==="host"?"guest":"host";d.turn=other;
  d.fighters[other].gauge=0;
  d.streak=0
 }else d.streak=d.previousTurn===d.turn?d.streak+1:1;
 d.previousTurn=d.turn;
 const f=d.fighters[d.turn];f.gauge=0;f.damageTurn=0;f.healTurn=0;f.dotTurn=0;
 startTurn(f);f.energy=Math.max(1,f.energy-(f.nextTax||0));f.nextTax=0;
 if(d.actions>=PV.turnLimit){d.sudden=(d.sudden||0)+2;const n=d.sudden+4;f.hp=Math.max(0,f.hp-n);d.log.push("Пепельное сжатие: "+n+" чистого урона.");}
 if(f.hp<=0){d.winner=d.turn==="host"?"guest":"host"}
 d.log.push("Очередь: "+f.name+", AV "+pvAV(f)+", скорость "+f.speed)
}
function pvGambitApply(choice,own,foe){
 const p=own;
 switch(choice){
 case"edge":p.str+=3;p.vuln+=2;break;
 case"rampart":p.block+=15;p.ward+=10;p.speed=Math.max(PV.minSpeed,p.speed-7);break;
 case"haste":p.speed=Math.min(PV.maxSpeed,p.speed+14);p.hp=Math.max(1,p.hp-9);break;
 case"plague":foe.poison+=5;p.poison+=2;break;
 case"purify":p.poison=0;p.burn=0;p.bleed=0;ashHeal(p,11);break;
 case"tempo":p.gauge=Math.max(0,p.gauge-2200);foe.gauge=Math.min(18000,foe.gauge+800);break;
 case"mirror":p.focus=Math.min(4,p.focus+1);p.ward+=10;break;
 case"sand":foe.gauge=Math.min(18000,foe.gauge+1800);foe.block+=5;break;
 case"berserk":p.str+=4;p.hp=Math.max(1,p.hp-11);break;
 case"blessing":p.regen+=3;p.speed=Math.max(PV.minSpeed,p.speed-7);break;
 case"exhaust":draw(p,Math.max(0,Math.min(7-p.hand.length,7)));p.hp=Math.max(1,p.hp-5);break;
 case"barrier":p.ward+=15;p.gauge=Math.min(18000,p.gauge+1200);break;
 }
}
function pvBeginGambit(d){
 d.phase="gambit";const cycle=Math.floor(d.actions/5)-1;
 d.offers=["buff","risk","control"].map((type,i)=>{
   const pool=PVP_CHOICES.filter(x=>x.type===type);
   return pool[(cycle+i)%pool.length]
 });
 d.picks={host:null,guest:null};
 for(const side of ["host","guest"])pvRelic(d.fighters[side],"gambit")
 d.log.push("⚖ Перелом судьбы: оба игрока выбирают модификатор.")
}
function pvFinishGambit(d){
 if(!d.picks.host||!d.picks.guest)return;
 const host=d.fighters.host,guest=d.fighters.guest;
 const h=d.picks.host,g=d.picks.guest;
 pvGambitApply(h,host,guest);pvGambitApply(g,guest,host);
 d.log.push("Пять действий: "+host.name+" → "+PVP_CHOICES.find(x=>x.id===h).name+
 " · "+guest.name+" → "+PVP_CHOICES.find(x=>x.id===g).name);
 d.offers=[];d.phase="play";
 pvNext(d)
}
function pvCheckDeaths(d){
 const h=d.fighters.host.hp,g=d.fighters.guest.hp;
 if(h<=0||g<=0){
   if(h<=0&&g<=0)d.winner="draw";
   else d.winner=h>0?"host":"guest";
   d.pending=null;d.phase="finished";
   d.log.push(d.winner==="draw"?"Ничья.":("Победа: "+d.fighters[d.winner].name));
   G.screen="duelEnd";
   return true
 }return false
}
const pvBaseHit=hit,pvBaseTrueDamage=ashTrueDamage,pvBaseEffect=ashEffect;
let pvContext=null;
ashTrueDamage=function(f,n){
 if(!pvContext||pvContext.target!==f){
   if(G.duel&&G.duel.fighters&&(f===G.duel.fighters.host||f===G.duel.fighters.guest)){
     const absorbed=Math.min(n,f.ward||0);f.ward=Math.max(0,(f.ward||0)-absorbed);
     return pvBaseTrueDamage(f,Math.max(0,n-absorbed))
   }
   return pvBaseTrueDamage(f,n)
 }
 const block=Math.min(f.ward||0,n);f.ward=Math.max(0,(f.ward||0)-block);
 let remaining=Math.max(0,n-block);
 if(pvContext.reduce)remaining=Math.ceil(remaining*pvContext.reduce);
 remaining=Math.min(remaining,Math.max(0,pvContext.limit-pvContext.damage));
 pvContext.damage+=remaining;
 return pvBaseTrueDamage(f,remaining)
};
hit=window.hit=function(a,b,n){
 if(!pvContext||pvContext.attacker!==a||pvContext.target!==b)return pvBaseHit(a,b,n);
 const scale=pvContext.reduce||1;
 const adjusted=Math.max(0,Math.round(n*scale));
 let predicted=Math.max(0,adjusted+a.str+a.charge+a.bonusDamage);
 if(a.weak)predicted=Math.floor(predicted*.75);
 if(b.vuln)predicted=Math.ceil(predicted*1.5);
 predicted+=b.mark||0;
 const oldBlock=b.block;
 const blockUsed=Math.min(oldBlock,predicted);
 const allowed=Math.max(0,pvContext.limit-pvContext.damage);
 const extraBlock=Math.max(0,predicted-blockUsed-allowed);
 b.block+=extraBlock;
 const dealt=pvBaseHit(a,b,adjusted);
 pvContext.damage+=dealt;
 return dealt
};
ashEffect=function(e,a,b,card,pvp){
 if(e.key==="advance"){if(a.speed)a.nextAdvance=(a.nextAdvance||0)+100*e.value;else if(e.value>=16)draw(a,1);return 0}
 if(e.key==="retard"){a.nextAdvance=(a.nextAdvance||0)-100*e.value;return 0}
 if(e.key==="delay"){if(b&&Number.isFinite(b.gauge))b.gauge=Math.min(18000,b.gauge+100*e.value);else if(b)b.weak=(b.weak||0)+1;return 0}
 if(e.key==="speed"){if(a.speed)a.speed=pvClamp(a.speed+e.value,PV.minSpeed,PV.maxSpeed);else if(e.value>0)a.dodge=(a.dodge||0)+1;return 0}
 if(e.key==="slow"){if(b&&b.speed)b.speed=pvClamp(b.speed-e.value,PV.minSpeed,PV.maxSpeed);else if(b)b.weak=(b.weak||0)+1;return 0}
 if(e.key==="ward"){a.ward=pvClamp((a.ward||0)+e.value,0,80);return 0}
 if(e.key==="purify"){for(const field of ["poison","burn","bleed"])a[field]=Math.floor((a[field]||0)*(100-e.value)/100);return 0}
 if(e.key==="tax"){if(b)b.nextTax=Math.min(2,(b.nextTax||0)+e.value);return 0}
 if(e.key.startsWith("counter")){a.block+=e.key==="counterguard"?20:8;if(e.key==="counterclean")for(const k of ["poison","burn","bleed"])a[k]=Math.floor((a[k]||0)*.2);a.ward=(a.ward||0)+8;return 0} // usable in PvE too, dedicated PvP counter logic below
 return pvBaseEffect(e,a,b,card,pvp)
};
function pvResolveCard(d){
 const pending=d.pending;if(!pending)return;
 const f=d.fighters[pending.by],def=d.fighters[pending.by==="host"?"guest":"host"];
 const c=pending.card;
 const oldHp=def.hp,oldStatus={poison:def.poison,burn:def.burn,bleed:def.bleed};
 if(pending.counter==="guard"){def.block+=16;def.ward=Math.min(80,def.ward+10)}
 if(pending.counter==="purge"){for(const k of ["poison","burn","bleed"])def[k]=Math.floor(def[k]*.3);def.ward=Math.min(80,def.ward+7)}
 if(pending.counter==="interrupt"){def.block+=5;}
 if(pending.counter==="mirror"){def.ward+=7;f.hp=Math.max(1,f.hp-10)}
 if(pending.counter==="pvp013"){def.block+=20;def.ward+=10}
 if(pending.counter==="pvp014"){for(const k of ["poison","burn","bleed"])def[k]=Math.floor(def[k]*.2);def.ward+=12}
 if(pending.counter==="pvp015"||pending.counter==="pvp023")def.block+=7;
 if(pending.counter==="pvp016"){def.block+=7;def.ward+=5;f.hp=Math.max(1,f.hp-10)}
 const reduction=(pending.counter==="interrupt"||pending.counter==="pvp015")?.45:pending.counter==="pvp023"?.65:1;
 pvContext={attacker:f,target:def,damage:f.damageTurn||0,limit:Math.ceil(def.maxHp*.58),reduce:reduction};
 const hpF=f.hp;
 let message=apply(c,f,def);
 f.damageTurn=pvContext.damage;
 pvContext=null;
 // DoT buildup limited per attack: upgraded combos remain powerful but counterable.
 for(const k of ["poison","burn","bleed"]){
  const created=def[k]-oldStatus[k];
  const limit=pending.counter==="purge"||pending.counter==="pvp014"?3:10;
  def[k]=pvClamp(def[k],0,Math.max(oldStatus[k],0)+limit);
 }
 if(f.hp<hpF&&f.hp<=0)f.hp=0;
 if(art(c.id).t==="power"||art(c.id).t==="reaction")f.exhaust.push(c);
 else f.discard.push(c);
 d.log.push(f.name+": "+message+(pending.counter?", ответ: "+pending.counter:""));
 d.pending=null;d.phase="play";pvCheckDeaths(d)
}
function pvOfferReaction(d,by,c){
 const def=d.fighters[by==="host"?"guest":"host"];
 const effect=art(c.id).effects||[],harmful=art(c.id).t==="attack"||effect.some(x=>["poison","burn","bleed","detonate","weak","vuln","mark","tax","delay","slow","aoe","aoepoison","aoeburn","aoebleed"].includes(x.key));
 d.pending={by,card:c,counter:null};
 if(harmful&&def.focus>0){d.phase="reaction";return}
 pvResolveCard(d)
}
function pvAction(cmd,by){
 const d=G.duel;if(!d||d.winner||!d.fighters||!cmd)return;
 if(d.phase==="gambit"){
  if(cmd.action!=="pick"||d.picks[by]||!d.offers.some(x=>x.id===cmd.id))return;
  d.picks[by]=cmd.id;pvFinishGambit(d);broadcastDuel();return
 }
 if(d.phase==="reaction"){
  const defender=d.pending.by==="host"?"guest":"host";
  if(by!==defender||cmd.action!=="counter")return;
  const f=d.fighters[by];let reaction=String(cmd.kind);
  if(reaction!=="pass"){
   if(f.focus<1)return;
   if(!["guard","purge","interrupt","mirror"].includes(reaction)){
    const i=Number(cmd.cardIndex),c=f.hand[i];
    if(!Number.isInteger(i)||i<0||!c||c.id!==reaction||art(c.id).t!=="reaction")return;
    f.hand.splice(i,1);f.exhaust.push(c)
   }
   f.focus--;d.pending.counter=reaction
  }
  pvResolveCard(d);broadcastDuel();return
 }
 if(d.phase!=="play"||d.turn!==by)return;
 const f=d.fighters[by],foe=d.fighters[by==="host"?"guest":"host"];
 if(cmd.action==="play"){
  const i=Number(cmd.index);if(!Number.isInteger(i)||i<0||i>=f.hand.length)return;
  const c=f.hand[i];if(art(c.id).c>f.energy||art(c.id).t==="reaction")return;
  f.hand.splice(i,1);f.energy-=art(c.id).c;
  pvOfferReaction(d,by,c);
 }else if(cmd.action==="end"){
  endTurnStatuses(f,foe);
  pvRelic(f,"end",foe);pvRelic(foe,"end",f);
  f.gauge=pvClamp(PV.gauge-(f.nextAdvance||0),1800,18000);f.nextAdvance=0;
  d.actions++;d.log.push(f.name+" завершил действие №"+d.actions);
  if(pvCheckDeaths(d)){broadcastDuel();return}
  if(d.actions%5===0)pvBeginGambit(d);
  else pvNext(d);
 }else return;
 pvCheckDeaths(d);broadcastDuel()
}
function pvStartDuel(){
 if(G.role!=="host"||!G.done||!G.other||G.duel)return;
 const own=runnerSummary();
 const fighters={host:pvMakeFighter(own),guest:pvMakeFighter(G.other)};
 const first=Math.floor(Math.random()*2)?"host":"guest";
 const d={fighters,turn:null,phase:"play",clock:0,actions:0,round:1,tieNext:first,
  previousTurn:null,streak:0,log:["Последняя дуэль началась. Начальный ход определяется AV."],
  winner:"",pending:null,offers:[],picks:{host:null,guest:null},sudden:0};
 G.duel=d;
 for(const s of ["host","guest"]){const f=fighters[s],e=fighters[s==="host"?"guest":"host"];beginEffects(f,e)}
 for(const s of ["host","guest"])pvRelic(fighters[s],"start",fighters[s==="host"?"guest":"host"]);
 const actor=pvPreview(d,1)[0],later=actor==="host"?"guest":"host";
 // Fairness: reactive shield for player who acts later, not locked to role.
 fighters[later].block+=8;fighters[later].ward+=10;fighters[later].focus=Math.min(4,fighters[later].focus+1);
 pvNext(d);G.screen="duel";broadcastDuel()
}
checkDuel=window.checkDuel=pvStartDuel;
applyDuel=window.applyDuel=function(cmd,by){pvAction(cmd,by)};
function pvCardCost(c){return art(c.id).c}
function pvStatus(f){
 const current=f.speed||100,av=pvAV(f);
 return '<div class="pvp-tempo"><span>⚡ Скорость '+current+'</span><span>⏳ AV '+av+'</span><span>🛡️ Контрресурс '+(f.focus||0)+'</span><span>🔮 Щит ДоТ '+(f.ward||0)+'</span></div>'
}
const pvOldRenderDuel=renderDuel;
renderDuel=window.renderDuel=function(){
 const d=G.duel;if(!d||!d.fighters)return pvOldRenderDuel();
 const me=d.fighters[G.role],foe=d.fighters[G.role==="host"?"guest":"host"];
 const mine=d.turn===G.role;
 let body='<div class="eyebrow">PvP · AV-БОЙ · ДЕЙСТВИЕ '+d.actions+' · ПОРОГ '+(5-d.actions%5)+'</div>';
 body+=ashArena({...me,hero:G.runner?.hero||me.hero},[{...foe,kind:foe.hero||"boss",intentText:"AV "+pvAV(foe)}],-1,false);
 body+='<div class="panel pvp-meter">'+pvStatus(me)+'<div class="pvp-order">Очередь: '+pvPreview(d,3).map(s=>'<span class="pill">'+(s===G.role?"ТЫ":"СОПЕРНИК")+'</span>').join(" → ")+'</div><p class="small">Соперник: скорость '+foe.speed+' · AV '+pvAV(foe)+' · защита ДоТ '+foe.ward+'</p></div>';
 body+='<div class="logs">'+d.log.slice(-5).map(esc).join("\n")+'</div>';
 if(d.phase==="gambit"){
  body+='<h2>⚖ Перелом судьбы</h2><p class="sub">Каждые 5 действий оба игрока одновременно выбирают усиление или риск. Выбор скрыт до решения второго игрока.</p>';
  if(d.picks[G.role])body+='<div class="panel gold">Выбор принят. Ожидаем решение противника.</div>';
  else body+='<div class="grid one">'+d.offers.map(x=>'<button class="node" data-a="pvpPick" data-x="'+x.id+'"><span class="eyebrow">'+(x.type==="risk"?"РИСК":x.type==="buff"?"УСИЛЕНИЕ":"КОНТРОЛЬ")+'</span><strong>'+esc(x.name)+'</strong><span class="small">'+esc(x.description)+'</span></button>').join("")+'</div>';
  return body
 }
 if(d.phase==="reaction"){
  const defending=d.pending.by!==G.role;
  const p=d.pending;
  body+='<h2>🛑 Окно контрприёма</h2><p class="sub">'+esc(d.fighters[p.by].name)+' разыгрывает «'+esc(art(p.card.id).n)+'». '+(defending?"Можно остановить комбинацию, потратив контрресурс.":"Ожидаем ответ противника.")+'</p>';
  if(defending){
   body+='<div class="grid">'+[
    ["guard","Заслон","+16 брони и +10 защиты ДоТ"],
    ["purge","Антидот","Снять 70% ДоТ и +7 защиты от взрыва"],
    ["interrupt","Прерывание","Сократить входящий урон на 55%"],
    ["mirror","Отражение","10 урона атакующему, +7 защиты ДоТ"]
   ].map(([id,label,info])=>'<button class="node" data-a="pvpCounter" data-x="'+id+'" '+(me.focus<1?"disabled":"")+'><strong>'+label+'</strong><span class="small">'+info+'</span></button>').join("")+'</div>';
   const reactions=me.hand.map((c,i)=>({c,i})).filter(x=>art(x.c.id).t==="reaction");
   if(reactions.length)body+='<div class="eyebrow">КАРТЫ-КОНТРПРИЁМЫ</div><div class="scrollline">'+reactions.map(x=>card(x.c,"pvpCounterCard",String(x.i),me.focus<1)).join("")+'</div>';
   body+='<div class="actions">'+btn("Пропустить ответ","pvpCounter","pass","full")+'</div>';
  }else body+='<p class="message">Противник выбирает ответную защиту.</p>';
  return body
 }
 body+='<div class="panel resource-line"><span>⚡ '+me.energy+' энергии</span><span>'+(mine?"Твой ход":"Ход соперника")+'</span><span>🃏 '+me.hand.length+' карт</span></div>';
 body+='<div class="eyebrow">РУКА</div><div class="scrollline">'+me.hand.map((c,i)=>card(c,"duelPlay",i,!mine||art(c.id).c>me.energy||art(c.id).t==="reaction")).join("")+'</div>';
 body+=btn("Завершить действие","duelEndTurn","","full",!mine);
 body+='<p class="small center" style="margin-top:8px">Копи скорость и сдвигай AV. Усиления уходят в изгнание до конца дуэли. 1 контрресурс блокирует один опасный розыгрыш.</p>';
 return body
};
const pvOldDispatch=dispatch;
dispatch=window.dispatch=async function(action,x){
 if(action==="pvpPick"){commandDuel({action:"pick",id:x});return}
 if(action==="pvpCounter"){commandDuel({action:"counter",kind:x});return}
 if(action==="pvpCounterCard"){const i=Number(x);const c=G.duel?.fighters?.[G.role]?.hand?.[i];if(c&&art(c.id).t==="reaction")commandDuel({action:"counter",kind:c.id,cardIndex:i});return}
 await pvOldDispatch(action,x)
};
window.PVP_BALANCE_DEBUG={pvStartDuel,pvAction,pvNext,pvAV,pvPreview,pvGambitApply,pvResolveCard,pvCheckDeaths,PV};
render();
