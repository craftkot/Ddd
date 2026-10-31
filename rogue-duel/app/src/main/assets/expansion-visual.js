"use strict";
/* Lightweight PNG sprites generated once using Canvas and rendered as <img>.
   Entirely original in-app 2D sprites; transparent background, no licensed assets. */
const ashSprites={};
function ashSprite(kind){
 if(ashSprites[kind])return ashSprites[kind];
 const canvas=document.createElement("canvas");canvas.width=64;canvas.height=82;
 const c=canvas.getContext("2d");c.imageSmoothingEnabled=false;
 const pal={
 guard:["#516b85","#d7b69e","#8fb6cc","#263448","#e3c47f"],
 oracle:["#7568a7","#d7bfae","#bba9ec","#29243c","#74b8fa"],
 hunter:["#56795c","#d9b69e","#9fcf8a","#293b31","#e4c87e"],
 bandit:["#75605c","#d7ab95","#a68363","#2e2932","#d8b978"],
 cultist:["#92464d","#dfbaa9","#d87063","#34202c","#edb34a"],
 skeleton:["#9d9e9c","#eadfca","#b6b6aa","#343441","#f6e4c9"],
 slime:["#26755f","#b1efc9","#69c89a","#16483c","#c4f5dc"],
 knight:["#756976","#c2aba6","#afa9b8","#33323e","#e77b67"],
 mage:["#7867a0","#d8c0b5","#bba2f0","#382b4b","#b3a0fe"],
 hound:["#6b5a4a","#d7b29b","#aa8a6b","#31292a","#efd19f"],
 boss:["#88464f","#dcb6aa","#b66c7a","#30212c","#f19b72"]
 }[kind]||["#686077","#d7bfae","#9f88af","#292d3e","#f7ce80"];
 const [coat,skin,light,dark,metal]=pal;
 const p=(x,y,w,h,color)=>{c.fillStyle=color;c.fillRect(x,y,w,h)};
 const line=(x,y,xx,yy,color,width=2)=>{c.beginPath();c.strokeStyle=color;c.lineWidth=width;c.moveTo(x,y);c.lineTo(xx,yy);c.stroke()};
 // shadow, boots, chest, pauldrons, face, helm, gloves, cape
 p(12,74,45,3,"#09101966");
 p(22,56,9,16,dark);p(35,56,9,16,dark);p(21,68,11,5,coat);p(34,68,12,5,coat);
 p(16,31,34,29,dark);p(19,34,28,26,coat);
 p(14,34,8,24,light);p(45,34,8,25,light);p(14,48,9,9,dark);p(45,48,9,9,dark);
 p(26,22,17,16,skin);p(23,15,22,15,dark);p(25,15,19,9,coat);
 p(24,30,6,3,metal);p(39,30,6,3,metal);p(29,26,3,2,"#1c2331");p(38,26,3,2,"#1c2331");
 p(25,39,16,5,light);p(22,48,24,4,dark);p(23,56,24,4,metal);
 p(19,32,5,27,coat);p(46,34,5,29,coat);
 if(kind==="guard"||kind==="knight"){p(50,18,3,43,metal);p(49,11,5,10,light);p(45,37,12,3,metal);p(8,36,13,20,metal);p(10,39,11,6,light);p(27,12,14,3,metal)}
 if(kind==="hunter"||kind==="bandit"){p(51,19,2,42,metal);p(48,19,6,3,light);p(10,16,3,40,dark);line(8,19,21,46,light,2);p(11,32,3,18,metal)}
 if(kind==="oracle"||kind==="mage"){p(52,21,3,43,metal);p(49,12,9,9,light);p(52,7,3,8,"#f1ecff");p(16,35,7,25,coat);p(27,14,13,8,light)}
 if(kind==="cultist"||kind==="boss"){p(10,21,6,35,metal);p(8,15,11,9,"#ffa666");p(11,11,4,6,"#ffec9e");p(47,45,8,14,metal)}
 if(kind==="skeleton"){p(29,24,3,3,dark);p(39,24,3,3,dark);p(31,32,12,3,dark);p(29,36,13,4,metal);p(27,48,5,8,light);p(38,48,5,8,light)}
 if(kind==="slime"){c.clearRect(0,0,64,82);c.fillStyle=dark;c.beginPath();c.ellipse(33,51,27,23,0,0,7);c.fill();c.fillStyle=light;c.beginPath();c.ellipse(31,48,23,20,0,0,7);c.fill();p(23,40,5,5,dark);p(39,40,5,5,dark);p(29,56,11,3,dark);p(15,43,6,4,"#d9ffecaa")}
 if(kind==="hound"){c.clearRect(0,0,64,82);p(12,39,41,20,dark);p(14,34,39,22,coat);p(25,26,24,21,skin);p(25,16,10,17,dark);p(42,16,10,17,dark);p(46,42,13,7,light);p(15,55,7,17,dark);p(45,55,7,17,dark);p(34,35,5,4,metal)}
 ashSprites[kind]=canvas.toDataURL("image/png");
 return ashSprites[kind]
}
function ashArtwork(kind,cls=""){return '<img class="unit-sprite '+cls+'" alt="" draggable="false" src="'+ashSprite(kind)+'">'}
function ashStatus(f){
 ashExtra(f);
 const stats=[["🛡",f.block],["☠",f.poison],["🩸",f.bleed],["🔥",f.burn],["⚔",f.str],["🎯",f.mark],["🌵",f.thorns],["💚",f.regen],["🕸",f.weak],["💥",f.vuln],["❄",f.stun],["🔋",f.charge]];
 return '<div class="pills compact">'+stats.filter(x=>x[1]>0).map(([ico,v])=>'<span class="pill">'+ico+' '+v+'</span>').join("")+'</div>'
}
function ashHealth(f){return '<div class="unit-health">❤️ '+f.hp+' / '+f.maxHp+'</div>'+hbar(Math.max(0,f.hp),Math.max(1,f.maxHp))}
function ashIntent(e){
 if(e.hp<=0)return "ПОВЕРЖЕН";
 if(e.intentText)return e.intentText;
 const k=e.intent%4;
 return (k===0?"⚔️ Удар "+e.attack:k===1?"🛡️ Защита + удар":k===2?"☠️ Яд + удар":"🩸 Кровь + удар")
}
function ashArena(p,enemies,selected,clickable){
 const ally='<div class="unit ally">'+ashArtwork(p.hero||G.runner.hero,"hero-image")+'<div class="unit-name">'+esc(p.name)+'</div>'+ashHealth(p)+ashStatus(p)+'</div>';
 const units=enemies.map((e,i)=>{
  let tag=clickable?"button":"div";
  return '<'+tag+' class="unit hostile '+(clickable&&selected===i?"selected":"")+' '+(e.hp<=0?"fallen":"")+'" '+(clickable&&e.hp>0?'data-a="target" data-x="'+i+'"':'')+'><div class="intent">'+ashIntent(e)+'</div>'+ashArtwork(e.kind||e.hero||"boss")+'<div class="unit-name">'+esc(e.name)+'</div>'+ashHealth(e)+ashStatus(e)+'</'+tag+'>'
 }).join("");
 return '<div class="battlefield"><div class="stage-fog"></div>'+ally+'<div class="enemy-party count-'+enemies.length+'">'+units+'</div></div>'
}
const ashOldRender=render;
function ashCatalog(){
 const isCard=G.catalogMode!=="relics";
 const t='<div class="eyebrow">БИБЛИОТЕКА ПЕПЛА</div><h2>Коллекция</h2><p class="sub">'+Object.keys(CARDS).length+' карт · '+RELICS.length+' реликвии. Каждая находка влияет на билд.</p>';
 const tabs='<div class="actions">'+btn("Карты ("+Object.keys(CARDS).length+")","catalogCards","",""+(isCard?"":"secondary"))+btn("Реликвии ("+RELICS.length+")","catalogRelics","",""+(!isCard?"":"secondary"))+'</div>';
 const filter='<div class="scrollline filterbar">'+["Все",...ASH_FAMILIES.map(x=>x[0])].map(x=>btn(x,"catalogFilter",x,"tiny "+(G.catalogFilter===x?"":"ghost"))).join("")+'</div>';
 let contents="";
 if(isCard){
  const entries=Object.entries(CARDS).filter(([id,c])=>G.catalogFilter==="Все"||c.school===G.catalogFilter);
  contents='<div class="catalog-grid">'+entries.map(([id,c])=>'<div class="panel catalog-card"><div class="flex"><strong>'+c.e+' '+esc(c.n)+'</strong><span class="pill">⚡'+c.c+'</span></div><p class="small">'+(c.t==="attack"?"Атака":c.t==="skill"?"Навык":"Усиление")+(c.school?" · "+c.school:" · Основа")+'</p><p class="sub">'+esc(c.d)+'</p></div>').join("")+'</div>'
 }else{
  contents='<div class="catalog-grid">'+RELICS.map(r=>'<div class="panel catalog-card"><h3>'+r.e+' '+esc(r.n)+'</h3><p class="sub">'+esc(r.d)+'</p></div>').join("")+'</div>'
 }
 return t+tabs+(isCard?filter:"")+contents+btn("Назад","catalogBack","","full")
}
function ashInventory(){
 if(!G.runner)return "";
 return topstats()+'<h2>Твоя сборка</h2><div class="eyebrow">КОЛОДА · '+G.runner.deck.length+' КАРТ</div>'+
 '<div class="catalog-grid">'+G.runner.deck.map(c=>'<div class="panel catalog-card"><strong>'+art(c.id).e+' '+esc(art(c.id).n)+upgraded(c)+'</strong><p class="sub">'+esc(desc(c))+'</p></div>').join("")+'</div>'+
 '<div class="eyebrow">РЕЛИКВИИ · '+G.runner.relics.length+'</div>'+
 '<div class="catalog-grid">'+G.runner.relics.map(id=>{let r=RELICS.find(x=>x.id===id);return r?'<div class="panel catalog-card"><strong>'+r.e+' '+esc(r.n)+'</strong><p class="sub">'+esc(r.d)+'</p></div>':""}).join("")+'</div>'+
 btn("Назад к маршруту","inventoryBack","","full")
}
render=window.render=function(){
 if(G.screen==="catalog"){app.innerHTML=ashCatalog();setNet();return}
 if(G.screen==="inventory"){app.innerHTML=ashInventory();setNet();return}
 ashOldRender();
 if(G.screen==="lobby"){
  app.insertAdjacentHTML("beforeend",'<p class="small center">✦ '+Object.keys(CARDS).length+' карт · '+RELICS.length+' реликвии · 1–4 противника</p>');
  app.insertAdjacentHTML("beforeend",'<div class="actions">'+btn("📚 Коллекция карт и реликвий","catalog","","ghost")+'</div>')
 }
 if(G.screen==="map")app.insertAdjacentHTML("beforeend",'<div class="actions">'+btn("🃏 Посмотреть свою сборку","inventory","","ghost")+'</div>')
};
renderFight=window.renderFight=function(){
 const c=G.combat;if(!c||!c.enemies)return ashOld.renderFight();
 const p=c.player;
 return pathdots()+'<div class="eyebrow">СРАЖЕНИЕ · ХОД '+c.round+' · ◈ '+G.runner.gold+'</div>'+
 ashArena({...p,hero:G.runner.hero},c.enemies,c.target,true)+
 '<div class="panel resource-line"><div>⚡ '+p.energy+' энергии</div><div>🃏 '+p.deck.length+' в колоде</div><div>↪ '+p.discard.length+' в сбросе</div></div>'+
 '<p class="small center">Выбери врага справа, затем разыграй карту. Массовые атаки поражают всех.</p>'+
 logs()+'<div class="eyebrow">РУКА · ВЫБЕРИ КАРТУ</div>'+
 '<div class="scrollline">'+p.hand.map((c,i)=>card(c,"play",i,art(c.id).c>p.energy)).join("")+'</div>'+
 btn("Завершить ход","endTurn","","full")
};
renderDuel=window.renderDuel=function(){
 const d=G.duel;if(!d)return ashOld.renderDuel();
 const me=d.fighters[G.role],them=d.fighters[G.role==="host"?"guest":"host"];
 if(!me||!them)return '<p class="sub">Синхронизация PvP...</p>';
 const mine=d.turn===G.role;
 return '<div class="eyebrow">ПОСЛЕДНЯЯ ДУЭЛЬ · ХОД '+d.round+'</div>'+
 ashArena({...me,hero:G.runner.hero},[{...them,kind:them.hero||"boss",intentText:mine?"Ожидает":"Атакует"}],-1,false)+
 '<div class="panel resource-line"><div>⚡ '+me.energy+' энергии</div><div>'+(mine?"Твой ход":"Ход противника")+'</div></div>'+
 '<div class="logs">'+d.log.slice(-6).map(esc).join("\n")+'</div>'+
 '<div class="eyebrow">РУКА · '+me.hand.length+' КАРТ</div><div class="scrollline">'+me.hand.map((c,i)=>card(c,"duelPlay",i,!mine||art(c.id).c>me.energy)).join("")+'</div>'+
 btn(mine?"Завершить ход":"Ход соперника","duelEndTurn","","full",!mine)
};
const ashOldOpenNode=openNode;
openNode=window.openNode=function(i){ashOldOpenNode(i);if(G.screen==="shop"&&G.shop){G.shop.cards=ashAffinityCards(G.runner,3);render()}};
const ashOldDispatch=dispatch;
dispatch=window.dispatch=async function(action,x){
 if(action==="catalog"){G.catalogMode="cards";G.catalogFilter="Все";G.catalogReturn=G.screen;G.screen="catalog";render();return}
 if(action==="catalogCards"){G.catalogMode="cards";G.catalogFilter="Все";render();return}
 if(action==="catalogRelics"){G.catalogMode="relics";render();return}
 if(action==="catalogFilter"){G.catalogFilter=x;render();return}
 if(action==="catalogBack"){G.screen=G.catalogReturn||"lobby";render();return}
 if(action==="inventory"){G.screen="inventory";render();return}
 if(action==="inventoryBack"){G.screen="map";render();return}
 if(action==="target"){if(G.combat&&G.combat.enemies[Number(x)]&&G.combat.enemies[Number(x)].hp>0)G.combat.target=Number(x);render();return}
 await ashOldDispatch(action,x);
 if(action==="play"||action==="duelPlay"){
  const hero=document.querySelector(".unit.ally"),enemy=document.querySelector(".unit.hostile.selected")||document.querySelector(".unit.hostile");
  if(hero){hero.classList.remove("striking");void hero.offsetWidth;hero.classList.add("striking")}
  if(enemy){enemy.classList.remove("impact");void enemy.offsetWidth;enemy.classList.add("impact")}
 }
};
window.ASH_SPRITES=ashSprites;
render();
