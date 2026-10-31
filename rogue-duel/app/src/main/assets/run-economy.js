"use strict";
/* v0.7 Run economy: intentionally varied card rarities, themed mixed rewards,
   consumable potions with capacity and prices, shop inventory and new events. */
(function(){
 const oldNew=newRun,oldWin=winFight,oldOpen=openNode,oldEvent=doEvent;
 const POTIONS=[
  {id:"salve",name:"Тёплая смола",e:"❤",desc:"Восстановить 17 здоровья",price:35},
  {id:"cleanser",name:"Белые травы",e:"✧",desc:"Снять весь яд, кровь и горение",price:40},
  {id:"steel",name:"Стеклянная броня",e:"🛡",desc:"Получить 16 брони",price:42},
  {id:"aether",name:"Склянка эфира",e:"⚡",desc:"Получить 2 энергии и 1 карту",price:58},
  {id:"ember",name:"Угли последнего дня",e:"🔥",desc:"Наложить 7 горения выбранному врагу",price:48}
 ];
 const RARITIES=["common","uncommon","rare"];
 const RARITY_LABEL={common:"Обычная",uncommon:"Необычная",rare:"Редкая"};
 for(const [id,c]of Object.entries(CARDS)){
  if(c.rarity)continue;
  if(c.effects){
   const hasBigCombo=c.effects.some(x=>["detonate","armorcarry","multihit","duplicate","aoe","aoepoison","aoeburn","aoebleed","energy"].includes(x.key));
   c.rarity=c.c>=3||c.t==="power"&&c.c>=2?"rare":c.c>=2||hasBigCombo||c.t==="reaction"?"uncommon":"common";
  }else c.rarity=c.c>=3||["storm","rift","execute"].includes(id)?"rare":c.c>=2||c.t==="power"?"uncommon":"common";
 }
 function randomCard(tier,avoid=[]){
  const pool=COMMON.filter(id=>CARDS[id]&&CARDS[id].rarity===tier&&!avoid.includes(id));
  const fallback=COMMON.filter(id=>CARDS[id]&&!avoid.includes(id));
  return choose(pool.length?pool:fallback)
 }
 function rollTier(elite=false,shop=false){
  let p=(rnd(1000000)/1000000);
  if(elite)return p<.19?"rare":p<.55?"uncommon":"common";
  if(shop)return p<.12?"rare":p<.51?"uncommon":"common";
  return p<.07?"rare":p<.35?"uncommon":"common"
 }
 function rewards(r,elite=false){
  const own={};
  for(const c of r.deck||[]){const s=CARDS[c.id]?.school;if(s)own[s]=(own[s]||0)+1}
  const primary=Object.entries(own).sort((a,b)=>b[1]-a[1])[0]?.[0];
  const ids=[];
  // One themed card makes a build coherent, two wildcard offers preserve agency.
  for(let i=0;i<3;i++){
   const rarity=rollTier(elite,false);
   let pool=COMMON.filter(id=>CARDS[id]?.rarity===rarity&&!ids.includes(id));
   if(i===0&&primary&&(rnd(1000000)/1000000)<.72){
    const affinity=pool.filter(id=>CARDS[id].school===primary);
    if(affinity.length)pool=affinity
   }
   const id=pool.length?choose(pool):randomCard(rarity,ids);
   ids.push(id)
  }
  return ids.map(inst)
 }
 function potion(id){return POTIONS.find(x=>x.id===id)}
 function addPotion(id){
  const r=G.runner;
  if(!r||!potion(id)||(r.potions||[]).length>=3)return false;
  (r.potions||(r.potions=[])).push(id);return true
 }
 newRun=window.newRun=function(id){
  const r=oldNew(id);r.potions=["salve"];return r
 };
 winFight=window.winFight=function(){
  const elite=!!G.combat?.elite;
  oldWin();
  if(G.reward&&G.runner&&G.screen==="reward"){
   G.reward.choices=rewards(G.runner,elite);
   if((rnd(1000000)/1000000)<(elite?.48:.25)&&(G.runner.potions||[]).length<3){
    const p=choose(POTIONS);addPotion(p.id);
    G.reward.desc+=" Найден расходуемый предмет: "+p.name+"."
   }
   render()
  }
 };
 function arrangeShop(){
  const shop=G.shop;if(!shop||shop.prices)return;
  shop.cards=rewards(G.runner,false);
  shop.prices=shop.cards.map(c=>({common:47,uncommon:74,rare:115})[CARDS[c.id]?.rarity||"common"]);
  shop.potion=choose(POTIONS).id;
  shop.potionBought=false
 }
 openNode=window.openNode=function(i){
  const result=oldOpen(i);
  if(G.screen==="shop"){arrangeShop();render()}
  return result
 };
 const startEventCount=EVENTS.length;
 EVENTS.push(
 {n:"Счётчик времени",e:"⏳",d:"Внутри механизма застряла ещё живая секунда.",options:[
  ["Забрать реликвию, потеряв 11 здоровья","relic"],["Вынуть осколок и получить 60 золота","goldclock"]]},
 {n:"Теплица без неба",e:"🌿",d:"Алые растения выделяют прозрачную смолу.",options:[
  ["Взять два лечебных снадобья","vials"],["Сжечь посадки ради 75 золота","goldgrow"]]},
 {n:"Голос под мостом",e:"🕯",d:"Невидимый торговец просит одну карту в оплату.",options:[
  ["Удалить слабейший удар и получить 25 золота","purge"],["Получить случайную редкую карту ценой 10 здоровья","rarecard"]]},
 {n:"Треснувший обелиск",e:"⚒",d:"Гравировка предлагает выбор между безопасностью и силой.",options:[
  ["Укрепить здоровье: максимум +7","fortify"],["Усилить две случайные карты, но потерять 8 HP","riskyupgrade"]]},
 {n:"Ящик алхимика",e:"🧪",d:"Три тёмных флакона не имеют подписей.",options:[
  ["Взять случайное зелье","vial"],["Разбить склянки и получить 55 золота","goldalchemy"]]}
 );
 doEvent=window.doEvent=function(i){
  const option=G.encounter?.event?.options?.[i],type=option?.[1],r=G.runner;
  if(!option)return;
  if(type==="vials"){addPotion("salve");addPotion("cleanser")}
  else if(type==="vial")addPotion(choose(POTIONS).id);
  else if(type==="goldclock")r.gold+=60;
  else if(type==="goldgrow")r.gold+=75;
  else if(type==="goldalchemy")r.gold+=55;
  else if(type==="purge"){let x=r.deck.findIndex(c=>c.id==="strike"||c.id==="guard");if(x>=0&&r.deck.length>7)r.deck.splice(x,1);r.gold+=25}
  else if(type==="rarecard"){r.hp=Math.max(1,r.hp-10);r.deck.push(inst(randomCard("rare")))}
  else if(type==="fortify"){r.maxHp+=7;r.hp+=7}
  else if(type==="riskyupgrade"){r.hp=Math.max(1,r.hp-8);for(const c of shuffle(r.deck).slice(0,2))c.up++}
  else {oldEvent(i);return}
  nextStage()
 };
 const oldDispatch=dispatch;
 dispatch=window.dispatch=async function(action,x){
  if(action==="usePotion"){
   if(G.screen!=="fight"||!G.combat)return;
   const pos=Number(x),r=G.runner,id=r.potions?.[pos],p=potion(id),f=G.combat.player;
   if(!p||!Number.isInteger(pos))return;
   if(id==="salve")ashHeal(f,17);
   if(id==="cleanser"){f.poison=0;f.bleed=0;f.burn=0}
   if(id==="steel")f.block+=16;
   if(id==="aether"){f.energy=Math.min(20,f.energy+2);draw(f,1)}
   if(id==="ember"){let e=G.combat.enemies[G.combat.target]||G.combat.enemies.find(e=>e.hp>0);if(e)e.burn+=7}
   r.potions.splice(pos,1);logLine("Использовано: "+p.name);render();return
  }
  if(action==="buyPotion"){
   if(G.screen!=="shop"||!G.shop||G.shop.potionBought)return;
   const p=potion(G.shop.potion);if(!p||G.runner.gold<p.price||G.runner.potions.length>=3)return;
   G.runner.gold-=p.price;addPotion(p.id);G.shop.potionBought=true;render();return
  }
  return oldDispatch(action,x)
 };
 function potionBar(){
  const r=G.runner;if(!r)return"";
  const inv=r.potions||[];
  return '<div class="potion-hud"><small>РАСХОДНИКИ '+inv.length+'/3</small>'+
   (inv.length?inv.map((id,i)=>{const p=potion(id);return p?'<button class="potion-btn" data-a="usePotion" data-x="'+i+'" title="'+esc(p.desc)+'"><span>'+p.e+'</span><b>'+esc(p.name)+'</b><small>'+esc(p.desc)+'</small></button>':""}).join(""):'<span class="small">Нет зелий</span>')+'</div>'
 }
 function shopPotion(){
  if(!G.shop)return"";
  const p=potion(G.shop.potion);
  if(!p)return"";
  return '<div class="etch-offer"><span class="offer-art">'+p.e+'</span><strong>'+esc(p.name)+'</strong><p>'+esc(p.desc)+'</p>'+
    '<button class="etch-btn cool" data-a="buyPotion" '+(G.shop.potionBought||G.runner.gold<p.price||G.runner.potions.length>=3?'disabled':'')+'>'+
    (G.shop.potionBought?"Продано":"◈ "+p.price+" · Купить")+'</button></div>'
 }
 // Allow randomization to be reproduced from a saved run (separate map RNG remains unchanged).
 const oldMakeMap=makeMap,oldRnd=rnd;
 rnd=window.rnd=function(n){
  if(!Number.isInteger(n)||n<=0)return 0;
  if(!G.rngState)return oldRnd(n);
  let s=G.rngState>>>0;
  s^=s<<13;s^=s>>>17;s^=s<<5;
  G.rngState=s>>>0;
  return (G.rngState%n)
 };
 makeMap=window.makeMap=function(){
  const result=oldMakeMap();
  G.rngState=(G.routeTree?.seed||1)^0x6d2b79f5;
  if(G.rngState===0)G.rngState=0x743ab13;
  return result
 };
 window.ASH_RUN={POTIONS,RARITY_LABEL,potion,rewards,randomCard,rollTier,arrangeShop,addPotion,potionBar,shopPotion,eventsAdded:EVENTS.length-startEventCount};
})();