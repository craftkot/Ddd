"use strict";
/* THE ASHEN ATELIER — playable mobile interface, original vector artwork.
   UI remains a skin over existing authoritative battle and PvP rules.
   Works with taps and pointer dragging; contains no copyrighted game assets. */
(() => {
const UI={select:null,drag:null,skipTap:false,history:[],lastScreen:null};
const $app=()=>document.getElementById("app");
const trim=(n)=>Math.max(0,Math.floor(n||0));
const pct=(n,max)=>Math.max(0,Math.min(100,100*n/Math.max(1,max)));
function emblem(t){return ({fight:"⚔",elite:"☠",event:"✧",rest:"♨",shop:"◆",treasure:"◇",boss:"♛"})[t]||"✦"}
function longType(t){return ({attack:"АТАКА",skill:"НАВЫК",power:"УСИЛЕНИЕ",reaction:"КОНТРПРИЁМ"})[t]||"КАРТА"}
function toneOf(a){if(a.school==="Яд"||a.school==="Лечение")return"poison";return a.t}
function isHostile(a){
 if(a.t==="attack")return true;
 return (a.effects||[]).some(x=>["hit","aoe","multihit","pierce","execute","lifesteal","poison","aoepoison","burn","aoeburn","bleed","aoebleed","detonate","spread","weak","aoeweak","vuln","aoevuln","mark","stun","delay","slow","tax"].includes(x.key))
}
function colors(kind){
 const palette={
 guard:["#445a6e","#7cabc3","#d5c29b","#e7dbb7"],
 oracle:["#4b365e","#b08cda","#bfa0dc","#d0c1f6"],
 hunter:["#305547","#7b9f71","#bec595","#8cbb84"],
 bandit:["#482c36","#a25e67","#d1a482","#dbb7a3"],
 cultist:["#542739","#d35a59","#cc9b83","#f4b075"],
 skeleton:["#4a4851","#c8bfb2","#e8d9bb","#c2c5bb"],
 slime:["#29483d","#58ac88","#c0e6a0","#b3efca"],
 knight:["#464451","#938da0","#d1b2a1","#e2c6a2"],
 mage:["#403252","#9577bb","#d5b4b8","#b8b5fa"],
 hound:["#4b3931","#a08064","#d5a77e","#a6967f"],
 boss:["#512834","#d46556","#dbb2a0","#f4a86a"]
 };
 return palette[kind]||palette.bandit
}
function figure(kind){
 let [shadow,coat,skin,glow]=colors(kind),animal=kind==="hound",slime=kind==="slime";
 if(slime)return '<svg class="figure" viewBox="0 0 150 210" role="img" aria-label="Слизень"><defs><radialGradient id="g-slime"><stop stop-color="#b2f4a9"/><stop offset="1" stop-color="'+shadow+'"/></radialGradient></defs><ellipse cx="75" cy="193" rx="54" ry="8" fill="#0008"/><path d="M21 153 Q23 101 49 75 Q54 38 80 46 Q114 51 121 102 Q140 165 113 179 Q72 192 32 179Z" stroke="#b0e2ba" stroke-width="4" fill="url(#g-slime)"/><ellipse cx="55" cy="121" rx="8" ry="12" fill="#172a29"/><ellipse cx="92" cy="121" rx="8" ry="12" fill="#172a29"/><path d="M58 155 Q74 166 96 155" stroke="#21463d" stroke-width="4" fill="none"/></svg>';
 if(animal)return '<svg class="figure" viewBox="0 0 150 210" role="img" aria-label="Волк"><ellipse cx="75" cy="197" rx="57" ry="8" fill="#0008"/><path d="M23 149 Q20 104 55 92 L84 96 Q106 80 115 103 L132 123 L110 143 L112 191 L99 191 L89 146 L51 149 L45 191 L29 191Z" stroke="'+shadow+'" stroke-width="6" fill="'+coat+'"/><path d="M57 94 L58 56 L74 75 L95 54 L111 94 Q110 120 94 136 L73 130 L56 118Z" fill="'+shadow+'" stroke="'+glow+'" stroke-width="3"/><path d="M65 100 L79 100 M94 100 L103 100" stroke="#f4a964" stroke-width="5"/><path d="M78 119 L89 120 L86 125" fill="#1b1014"/></svg>';
 const mage=(kind==="oracle"||kind==="mage"||kind==="cultist");
 const shield=(kind==="guard"||kind==="knight"||kind==="boss");
 const helmet=kind==="skeleton";
 return '<svg class="figure" viewBox="0 0 160 230" role="img" aria-label="Персонаж">'+
 '<defs><linearGradient id="t-'+kind+'" x1="0" y1="0" x2=".9" y2="1"><stop stop-color="'+coat+'"/><stop offset=".64" stop-color="'+shadow+'"/><stop offset="1" stop-color="#080d15"/></linearGradient><radialGradient id="h-'+kind+'"><stop stop-color="'+glow+'" stop-opacity=".9"/><stop offset="1" stop-color="'+glow+'" stop-opacity="0"/></radialGradient></defs>'+
 '<ellipse cx="82" cy="215" rx="64" ry="8" fill="#03070d88"/>'+
 '<path d="M57 125 L36 210 L70 211 L85 147 L93 213 L129 209 L108 122Z" fill="'+shadow+'" stroke="'+coat+'" stroke-width="4"/>'+
 '<path d="M46 74 Q20 118 27 192 L60 180 L76 96 L112 191 L138 182 Q124 87 103 72Z" fill="url(#t-'+kind+')" stroke="'+shadow+'" stroke-width="3"/>'+
 '<path d="M51 79 L44 111 L52 159 L80 170 L112 154 L117 114 L107 77 L83 60Z" fill="url(#t-'+kind+')" stroke="'+glow+'" stroke-width="3"/>'+
 '<path d="M48 85 L20 138 L40 149 L62 114 M109 84 L135 131 L120 145 L95 112" fill="'+coat+'" stroke="'+shadow+'" stroke-width="7"/>'+
 '<path d="M64 48 L65 70 L98 70 L100 44 Q92 31 77 33 Q65 34 64 48Z" fill="'+skin+'" stroke="'+shadow+'" stroke-width="3"/>'+
 '<path d="M49 51 Q58 9 79 16 Q108 12 117 52 L105 83 L98 58 L65 58 L57 84Z" fill="'+shadow+'" stroke="'+coat+'" stroke-width="3"/>'+
 '<path d="M68 53 L76 53 M88 53 L96 53" stroke="'+(kind==="oracle"?"#beefff":"#ffd4a4")+'" stroke-width="3"/>'+
 '<path d="M53 91 Q82 107 110 91" stroke="'+glow+'" opacity=".75" stroke-width="3" fill="none"/>'+
 (shield?'<path d="M18 110 Q43 91 62 118 L58 168 L36 190 L14 168Z" fill="'+shadow+'" stroke="'+glow+'" stroke-width="5"/><path d="M37 105 L37 181" stroke="'+glow+'" stroke-width="3"/>':'')+
 (mage?'<path d="M132 76 L143 193" stroke="#bfb7a7" stroke-width="7"/><circle cx="131" cy="67" r="28" fill="url(#h-'+kind+')"/><circle cx="131" cy="67" r="11" fill="'+glow+'" stroke="#f5e1d2" stroke-width="2"/>':
 '<path d="M124 109 L139 19 L146 9 L151 24 L137 111 L126 189" stroke="#b4aab1" stroke-width="7" fill="none"/><path d="M116 102 L149 111" stroke="'+glow+'" stroke-width="5"/>')+
 (helmet?'<path d="M62 48 L99 48 L107 39 L94 26 L71 27 L61 39Z" fill="#c7c4b3"/>':'')+
 '<path d="M42 205 L74 209 M91 209 L123 205" stroke="'+glow+'" opacity=".5" stroke-width="4"/></svg>'
}
function cardVisual(c){
 const a=art(c.id),seed=(c.id.charCodeAt(0)||45)+[...a.n].reduce((s,ch)=>s+ch.charCodeAt(0),0);
 const t=a.t;
 const palette=t==="attack"?["#eb7867","#773039","#271925"]:t==="skill"?["#a3d8fa","#2f6e95","#17283b"]:t==="power"?["#d2a5f8","#704d8d","#241932"]:["#97e4bd","#327867","#142b2a"];
 if(a.school==="Яд"||a.school==="Лечение"){palette[0]="#b5d989";palette[1]="#56723c"}
 const choice=seed%5,base='<defs><radialGradient id="a'+seed+'"><stop stop-color="'+palette[1]+'"/><stop offset="1" stop-color="'+palette[2]+'"/></radialGradient><linearGradient id="b'+seed+'" x2=".8" y2="1"><stop stop-color="'+palette[0]+'"/><stop offset="1" stop-color="'+palette[1]+'"/></linearGradient></defs>'+
 '<rect width="150" height="115" fill="url(#a'+seed+')"/><circle cx="115" cy="28" r="24" fill="'+palette[0]+'" opacity=".12"/><path d="M0 97 L30 63 L58 82 L83 57 L130 94 L150 69 L150 115 L0 115Z" fill="#090e19" opacity=".7"/>';
 const forms=[
 '<path d="M78 14 L91 66 L117 88 L98 98 L81 77 L62 96 L44 88 L70 67Z" fill="url(#b'+seed+')" stroke="#fff3d0" stroke-width="2"/><path d="M75 18 L75 81" stroke="#ffe8b7" stroke-width="2"/>',
 '<path d="M70 20 Q107 42 105 69 Q98 89 74 104 Q47 88 43 65 Q49 35 70 20Z" fill="url(#b'+seed+')" stroke="#fff2c9" stroke-width="3"/><path d="M49 66 L69 84 L97 45" stroke="#f2f8ee" stroke-width="5" fill="none"/>',
 '<path d="M73 15 Q48 34 63 53 Q77 71 54 85 Q89 101 109 77 Q124 45 89 37 Q99 60 79 60 Q60 52 73 15Z" fill="url(#b'+seed+')" stroke="#f6dec2" stroke-width="3"/><path d="M75 68 Q87 77 78 91" stroke="#f9c48b" stroke-width="3" fill="none"/>',
 '<circle cx="76" cy="64" r="35" fill="none" stroke="'+palette[0]+'" stroke-width="6"/><path d="M74 16 L87 47 L121 60 L91 78 L83 107 L67 78 L34 61 L65 48Z" fill="url(#b'+seed+')" stroke="#fff6d9" stroke-width="2"/>',
 '<path d="M75 20 L92 42 L75 66 L55 43Z" fill="url(#b'+seed+')" stroke="#fff6d9" stroke-width="2"/><path d="M37 79 L58 53 L84 68 L112 49 L127 88 L85 105Z" fill="'+palette[1]+'" stroke="'+palette[0]+'" stroke-width="4"/><circle cx="75" cy="65" r="7" fill="#fce4bc"/>'
 ];
 const rays=Array.from({length:7},(_,i)=>{let angle=(seed*13+i*57)*Math.PI/180,x=76+Math.cos(angle)*59,y=64+Math.sin(angle)*42;return '<path d="M76 64 L'+x.toFixed(1)+' '+y.toFixed(1)+'" stroke="'+palette[0]+'" opacity=".35" stroke-width="2"/>'}).join("");
 return '<svg viewBox="0 0 150 115" preserveAspectRatio="xMidYMid slice" aria-hidden="true">'+base+rays+forms[choice]+'</svg>'
}
function playcard(c,act,i,disabled=false){
 const a=art(c.id),t=toneOf(a),rarity=(a.c>=3||a.t==="power")?"rare":a.c===2?"uncommon":"common";
 return '<button type="button" style="--deal:'+i+'" class="etch-card '+t+' '+rarity+'" data-cast-action="'+act+'" data-cast-index="'+i+'" aria-label="'+esc(a.n)+'"'+(disabled?" disabled":"")+'><span class="card-etched"></span><span class="cost">'+a.c+'</span><div class="card-title">'+esc(a.n)+upgraded(c)+'</div><div class="card-art">'+cardVisual(c)+'</div><div class="card-desc">'+esc(desc(c))+'</div><div class="card-type">'+longType(a.t)+'</div></button>'
}
function eventHead(title,subtitle){return '<div class="etch-head"><p class="eyebrow">✦ ПЕПЕЛ И КАРТЫ ✦</p><h2>'+esc(title)+'</h2><p class="sub">'+esc(subtitle||"")+'</p></div>'}
function health(f){return '<span class="fig-hp">❤ '+trim(f.hp)+'/'+trim(f.maxHp)+'</span><span class="fig-track"><span style="width:'+pct(f.hp,f.maxHp)+'%"></span></span>'}
function stateIcons(f){
 let out=[];const statuses=[["🛡",f.block],["☠",f.poison],["🩸",f.bleed],["🔥",f.burn],["⚔",f.str],["⬟",f.ward],["⏳",f.weak],["✧",f.regen],["✦",f.thorns]];
 for(let [sym,val] of statuses)if(val>0)out.push('<small>'+sym+' '+trim(val)+'</small>');
 return '<div class="fig-status">'+out.join("")+'</div>'
}
function intentOf(f){
 if(f.hp<=0)return"ПОВЕРЖЕН";
 if(f.intentText)return f.intentText;
 const mode=(f.intent||0)%4;
 return mode===0?"⚔ "+(f.attack||9):mode===1?"🛡 Броня":mode===2?"☠ Яд":"🩸 Кровь"
}
function unit(f,kind,i,isEnemy,clickable){
 return '<button type="button" '+(clickable?'data-foe-index="'+i+'" ':"")+'class="etch-person '+(isEnemy?"enemy":"hero")+' '+(f.hp<=0?"dead":"")+' '+(isEnemy&&G.combat?.target===i?"is-target":"")+'"'+(isEnemy&&f.hp<=0?" disabled":"")+'>'
 +(isEnemy?'<div class="etch-intent">'+esc(intentOf(f))+'</div>':"")+
 figure(kind)+'<div class="fig-name">'+esc(f.name)+'</div>'+health(f)+stateIcons(f)+'</button>'
}
function stage(hero,foes,target,clickable){
 return '<div class="etch-stage"><div class="etch-moon"></div><div class="etch-spires"></div><div class="etch-fighters">'+unit(hero,hero.hero||G.runner?.hero||"guard",-1,false,false)+
 '<div class="etch-team count-'+foes.length+'">'+foes.map((f,i)=>unit(f,f.kind||f.hero||"boss",i,true,clickable)).join("")+'</div></div><div class="etch-groundline"></div></div>'
}
function hud(hero,other=""){
 return '<div class="etch-battle-hud"><span>❤ <strong class="hp">'+trim(hero.hp)+' / '+trim(hero.maxHp)+'</strong></span><span>'+esc(hero.name)+'</span>'+other+'</div>'
}
function hand(f,action,disabled){
 const cards=f.hand||[];
 return '<div class="etch-resourcebar"><div class="etch-energy">'+trim(f.energy)+'/3</div><div class="etch-metrics"><span>▣ Колода '+trim(f.deck?.length)+'</span><span>↺ Сброс '+trim(f.discard?.length)+'</span><span>В руке '+cards.length+'</span></div></div>'+
 '<div class="etch-hand-shell"><div class="etch-hand count-'+Math.min(10,cards.length)+'">'+cards.map((c,i)=>'<div style="--fan:'+(i-(cards.length-1)/2)+';display:contents">'+playcard(c,action,i,disabled||art(c.id).c>f.energy||art(c.id).t==="reaction")+'</div>').join("")+'</div></div>'
}
function fightView(){
 const c=G.combat,p=c.player;
 return hud(p,'<span class="coin">◈ '+G.runner.gold+'</span>')+
 stage({...p,hero:G.runner.hero},c.enemies,c.target,true)+
 '<div class="etch-hint">Перетащи атакующую карту на врага или нажми карту и выбери цель.</div>'+
 hand(p,"play",false)+
 '<div class="etch-actions"><button class="etch-btn cta" data-a="endTurn">Завершить ход</button></div>'+
 '<div class="logs">'+G.log.slice(-4).map(esc).join("\n")+'</div>'
}
function timeline(d){
 let forecast=[];try{forecast=pvPreview(d,5)}catch(e){}
 return '<div class="etch-timeline"><small>AV · СКОРОСТЬ</small>'+forecast.map((s,i)=>'<span class="next '+(i===0?"active":"")+'">'+(s===G.role?"ТЫ":"СОПЕРНИК")+'</span>').join("")+
 '<small>Твоя: '+d.fighters[G.role].speed+' · Врага: '+d.fighters[G.role==="host"?"guest":"host"].speed+'</small></div>'
}
function duelView(){
 const d=G.duel;if(!d||!d.fighters)return '<div class="etch-panel">Синхронизация дуэли…</div>';
 const m=d.fighters[G.role],foe=d.fighters[G.role==="host"?"guest":"host"];
 if(!m||!foe)return'<div class="etch-panel">Синхронизация состояния…</div>';
 const mine=d.turn===G.role;
 let out=hud(m,'<span>AV '+pvAV(m)+' · Щит ДоТ '+(m.ward||0)+' · Контрресурс '+(m.focus||0)+'</span>')+
 timeline(d)+stage({...m,hero:G.runner?.hero||m.hero},[{...foe,kind:foe.hero||"boss",intentText:"AV "+pvAV(foe)}],0,true);
 if(d.phase==="gambit"){
  out+=eventHead("Перелом судьбы","Каждые пять действий игроки независимо выбирают модификатор. Решение раскрывается, когда оба выбрали.");
  out+=d.picks[G.role]?'<div class="etch-panel">Решение принято. Ожидаем второго игрока.</div>':
   '<div class="etch-catalog">'+d.offers.map(o=>'<div class="etch-offer"><strong>'+esc(o.name)+'</strong><p>'+esc(o.description)+'</p><button data-a="pvpPick" data-x="'+esc(o.id)+'" class="etch-btn">Выбрать</button></div>').join("")+'</div>';
  return out
 }
 if(d.phase==="reaction"){
  const pending=d.pending,defending=pending&&pending.by!==G.role;
  out+=eventHead("Окно контрприёма",pending?'Враг разыгрывает «'+art(pending.card.id).n+'».':'');
  if(defending){
   let opts=[["guard","🛡 Заслон","+16 брони и +10 щита ДоТ"],["purge","☠ Антидот","Снять 70% ДоТ, +7 щита"],["interrupt","✋ Прерывание","Снизить урон на 55%"],["mirror","🪞 Отражение","10 урона в ответ, +7 щита"]];
   out+='<div class="etch-catalog">'+opts.map(([id,n,x])=>'<div class="etch-offer"><strong>'+n+'</strong><p>'+x+'</p><button class="etch-btn cool" data-a="pvpCounter" data-x="'+id+'" '+(m.focus<1?"disabled":"")+'>Контрприём</button></div>').join("")+'</div>';
   const rs=m.hand.map((c,i)=>({c,i})).filter(x=>art(x.c.id).t==="reaction");
   if(rs.length)out+='<div class="etch-catalog">'+rs.map(x=>'<div>'+playcard(x.c,"pvpCounterCard",x.i,m.focus<1)+'</div>').join("")+'</div>';
   out+='<div class="etch-actions"><button data-a="pvpCounter" data-x="pass" class="etch-btn plain">Пропустить ответ</button></div>'
  }else out+='<p class="etch-hint">Противник выбирает контрприём. Ожидание не меняет твою руку.</p>';
  return out
 }
 out+='<div class="etch-hint">'+(mine?"ТВОЁ ДЕЙСТВИЕ":"ХОД ПРОТИВНИКА")+' · Число завершённых действий '+d.actions+' · До следующего события '+(5-d.actions%5)+'</div>'+
 hand(m,"duelPlay",!mine)+
 '<div class="etch-actions"><button data-a="duelEndTurn" class="etch-btn cta" '+(!mine?"disabled":"")+'>Завершить действие</button></div>'+
 '<div class="logs">'+d.log.slice(-5).map(esc).join("\n")+'</div>';
 return out
}
function mapView(){
 const r=G.runner,tree=G.routeTree;
 if(!tree)return '<div class="etch-story"><h2>Маршрут не создан</h2><button class="etch-btn" data-a="rebuildRoute">Восстановить маршрут</button></div>';
 const floor=G.stage,n=tree.floors.length,h=90*n+45;
 const active=ASH_TREE.reachable(floor),visited=tree.visited;
 const selected=new Set(visited.map(p=>p.stage+"-"+p.index));
 const pastEdges=new Set();
 for(let i=1;i<visited.length;i++){const a=visited[i-1],b=visited[i];if(b.stage===a.stage+1)pastEdges.add(a.stage+"-"+a.index+"-"+b.index)}
 const yOf=i=>h-64-i*90;
 const paths=tree.edges.map(([stage,source,target])=>{
  const from=tree.floors[stage][source],to=tree.floors[stage+1][target];
  const y1=yOf(stage),y2=yOf(stage+1),mx=(y1+y2)*.5;
  const travelled=pastEdges.has(stage+"-"+source+"-"+target);
  const lit=stage===floor-1&&tree.position?.stage===stage&&tree.position.index===source&&active.includes(target);
  return '<path class="'+(travelled?"travelled":lit?"reachable":"untravelled")+'" d="M'+(from.x*10)+' '+y1+' C'+(from.x*10)+' '+mx+' '+(to.x*10)+' '+mx+' '+(to.x*10)+' '+y2+'"/>';
 }).join("");
 const nodes=tree.floors.map((items,i)=>items.map((node,j)=>{
  const done=selected.has(i+"-"+j),can=i===floor&&active.includes(j);
  const cl=done?"travelled":can?"reachable":"untravelled";
  return '<button type="button" data-map-stage="'+i+'" class="etch-route-node '+cl+'" style="left:'+node.x+'%;top:'+yOf(i)+'px" title="'+esc(LABEL[node.type][1])+'" aria-label="Этаж '+(i+1)+': '+esc(LABEL[node.type][1])+(can?', доступен':'')+'"'+(can?' data-a="node" data-x="'+j+'"':' disabled')+'>'+
    '<span class="route-symbol">'+emblem(node.type)+'</span><span class="route-kind">'+esc(LABEL[node.type][1])+'</span></button>'
 }).join("")).join("");
 const choices=tree.floors[floor].filter(node=>active.includes(node.index));
 return '<div class="etch-map-caption"><div class="eyebrow">КАРТА ПУТИ · '+(G.role==="guest"?"ГОСТЬ":G.demo?"ОДИНОЧНАЯ ИГРА":"ХОЗЯИН")+'</div><div class="etch-title">Этаж '+(floor+1)+' / '+n+'</div>'+
 '<p class="small">❤ '+r.hp+'/'+r.maxHp+' · ◈ '+r.gold+' · '+r.deck.length+' карт · '+r.relics.length+' реликвий</p></div>'+
 '<div class="etch-next-panel"><div class="eyebrow">ДОСТУПНЫЕ ПУТИ</div><p class="small">Можно выбрать только узел, соединённый с твоим последним шагом. У каждого игрока своё дерево.</p>'+
 '<div class="etch-next-choices">'+choices.map(node=>'<button type="button" class="etch-btn etch-next-button" data-a="node" data-x="'+node.index+'">'+emblem(node.type)+' '+esc(LABEL[node.type][1])+'</button>').join("")+'</div>'+
 (G.lastRouteWarning?'<p class="dangertext">'+esc(G.lastRouteWarning)+'</p>':'')+'</div>'+
 '<div class="etch-worldmap etch-tree-map" style="height:'+h+'px"><svg class="etch-tree-links" viewBox="0 0 1000 '+h+'" preserveAspectRatio="none" aria-hidden="true">'+paths+'</svg>'+nodes+'</div>'+
 '<p class="etch-hint">Дерево строится снизу вверх. Золотые узлы доступны. Зелёные — пройденный путь. Серые — ещё закрыты.</p>'+
 '<div class="etch-map-legend">⚔ Бой · ☠ Элита · ◆ Магазин · ◇ Сокровище · ✧ Событие · ♨ Привал</div>'+
 '<div class="etch-actions"><button data-a="inventory" class="etch-btn plain">Посмотреть свою сборку</button></div>'
}
function rewardView(){
 return eventHead(G.reward.title,G.reward.desc)+'<div class="etch-story"><div class="story-symbol">🏆</div><div class="center money">◈ Получено '+G.reward.gold+' золота</div><div class="etch-catalog">'+G.reward.choices.map((c,i)=>'<div>'+playcard(c,"reward",i)+'</div>').join("")+'</div><div class="etch-actions"><button class="etch-btn plain" data-a="skipReward">Пропустить награду</button></div></div>'
}
function shopView(){
 let s=G.shop;
 return eventHead("Лавка под пеплом","Торговец принимает золото. Купленные карты остаются в колоде.")+
 '<div class="etch-catalog">'+s.cards.map((c,i)=>c?'<div>'+playcard(c,"buyCard",i,G.runner.gold<55)+'<div class="center money">◈ 55</div></div>':'<div class="etch-offer">Продано</div>').join("")+'</div>'+
 '<div class="etch-catalog">'+s.relics.map(id=>{let r=RELICS.find(x=>x.id===id);return'<div class="etch-offer"><span class="offer-art">'+r.e+'</span><strong>'+esc(r.n)+'</strong><p>'+esc(r.d)+'</p><button data-a="buyRelic" data-x="'+id+'" class="etch-btn" '+(G.runner.gold<115?'disabled':'')+'>◈ 115 · Купить</button></div>'}).join("")+'</div>'+
 '<div class="etch-actions"><button data-a="buyHeal" class="etch-btn cool" '+(G.runner.gold<40?'disabled':'')+'>Лечение +20 · ◈ 40</button><button data-a="buyRemove" class="etch-btn" '+(G.runner.gold<75?'disabled':'')+'>Удалить карту · ◈ 75</button></div>'+
 '<div class="etch-actions"><button data-a="next" class="etch-btn cta">Покинуть магазин</button></div>'
}
function eventView(){
 let e=G.encounter.event;
 return '<div class="etch-story"><div class="eyebrow center">НЕИЗВЕДАННАЯ ТРОПА</div><div class="story-symbol">'+e.e+'</div><h2>'+esc(e.n)+'</h2><p class="sub">'+esc(e.d)+'</p>'+
 e.options.map((x,i)=>'<button class="etch-decision" data-a="event" data-x="'+i+'"><b>'+String.fromCharCode(8544+i)+'</b> · '+esc(x[0])+'</button>').join("")+'</div>'
}
function restView(){
 return '<div class="etch-story"><div class="eyebrow center">ТИШИНА МЕЖДУ БИТВАМИ</div><div class="story-symbol">🔥</div><h2>Ночной привал</h2><p class="sub">Пламя не отвечает на вопросы. Можно вернуть силы или закалить карту.</p>'+
 '<button data-a="restHeal" class="etch-decision">❤ Отдохнуть · восстановить '+Math.ceil(G.runner.maxHp*.3)+' здоровья</button>'+
 '<button data-a="restUpgrade" class="etch-decision">⚒ Закалить · улучшить карту</button></div>'
}
function treasureView(){
 return eventHead("Забытый тайник","Одну из трёх реликвий можно взять без платы.")+
 '<div class="etch-catalog">'+G.encounter.choices.map(id=>{let r=RELICS.find(x=>x.id===id);return '<div class="etch-offer"><span class="offer-art">'+r.e+'</span><strong>'+esc(r.n)+'</strong><p>'+esc(r.d)+'</p><button data-a="takeRelic" data-x="'+r.id+'" class="etch-btn cool">Взять реликвию</button></div>'}).join("")+'</div>'
}
function heroView(){
 let intro='<div class="etch-head"><p class="eyebrow">ИЗБЕРИ ПУТЬ</p><h2>Три судьбы</h2><p class="sub">'+(G.demo?"Одиночный тест маршрута":"Код комнаты: "+G.code)+"</p></div>";
 return intro+'<div class="etch-class-grid">'+Object.entries(CLASSES).map(([id,c])=>'<button class="etch-class" data-a="hero" data-x="'+id+'">'+figure(id)+'<h3>'+c.name+'</h3><p>❤ '+c.hp+' здоровья</p><p>'+esc(c.desc)+'</p></button>').join("")+'</div>'
}
function lobbyView(){
 return '<div class="etch-lobby"><div class="hero-crest">⚔</div><div class="eyebrow">ДВА ПУТИ · ОДНА ДУЭЛЬ</div><h1>Пепел и карты</h1><p class="lead">Собери колоду, пройди разветвлённый путь и сразись с другим игроком. Действия определяются скоростью и контрприёмами.</p>'+
 '<div class="etch-panel"><h3>Создать приключение</h3><p class="sub">Первый игрок создаёт комнату, второй подключается по коду.</p><button data-a="create" class="etch-btn cta etch-wide">Создать комнату</button></div>'+
 '<div class="etch-panel"><h3>Присоединиться к другу</h3><input class="input" id="codeInput" maxlength="25" placeholder="КОД КОМНАТЫ"><button data-a="join" class="etch-btn cool etch-wide">Войти к другу</button></div>'+
 '<div class="etch-actions"><button class="etch-btn plain" data-a="demo">Одиночное прохождение</button><button data-a="catalog" class="etch-btn plain">Коллекция карт</button></div></div>'
}
function upgradeView(){
 return eventHead("Кузница","Выбери карту, чтобы улучшить её эффект.")+
 '<div class="etch-catalog">'+G.runner.deck.map((c,i)=>'<div>'+playcard(c,"upgrade",i)+'</div>').join("")+'</div>'
}
function roomWaitView(){
 const r=G.runner;
 return eventHead("У порога дуэли","Твой путь окончен. Противник завершает собственный маршрут.")+
 '<div class="etch-story"><div class="story-symbol">⚔</div><h2>Колода собрана</h2><p class="sub">Карт '+r.deck.length+' · Реликвий '+r.relics.length+' · Максимум здоровья '+r.maxHp+'</p>'+
 '<button class="etch-decision" data-a="resend">Отправить готовность повторно</button></div>'
}
function simpleView(){
 const isWin=G.screen==="duelEnd"&&G.duel?.winner===G.role;
 const title=G.screen==="duelEnd"?(isWin?"Победа в дуэли":"Дуэль окончена"):G.screen==="lost"?"Путь оборвался":"Путь завершён";
 return '<div class="etch-story"><div class="story-symbol">'+(isWin?"🏆":G.screen==="lost"?"💀":"⚔")+'</div><h2>'+title+'</h2><p class="sub">Один забег закончился. В следующем пути выпадут другие встречи и комбинации.</p><button data-a="restart" class="etch-btn cta etch-wide">Новый забег</button></div>'
}
const oldRender=render;
render=window.render=function(){
 let view=null;
 switch(G.screen){
 case"lobby":view=lobbyView();break;
 case"hero":view=heroView();break;
 case"map":view=mapView();break;
 case"fight":if(G.combat?.enemies)view=fightView();break;
 case"duel":if(G.duel?.fighters)view=duelView();break;
 case"reward":view=rewardView();break;
 case"shop":view=shopView();break;
 case"event":view=eventView();break;
 case"rest":view=restView();break;
 case"treasure":view=treasureView();break;
 case"upgrade":view=upgradeView();break;
 case"wait":view=roomWaitView();break;
 case"lost":case"done":case"duelEnd":view=simpleView();break;
 }
 if(view===null)oldRender();
 else{$app().innerHTML=view;setNet()}
 if(UI.lastScreen!==G.screen){UI.select=null;UI.lastScreen=G.screen}
 /* Route choices are placed above the tree: never scroll the user to a disabled floor on Android. */
};
const oldOpenNode=openNode;
openNode=window.openNode=function(i){if(G.map?.[G.stage])UI.history[G.stage]=G.map[G.stage][i];return oldOpenNode(i)};
function playable(c,action){
 if(!c||art(c.id).t==="reaction"&&action==="duelPlay")return false;
 if(action==="play"){return G.screen==="fight"&&G.combat&&art(c.id).c<=G.combat.player.energy}
 if(action==="duelPlay"){return G.screen==="duel"&&G.duel?.phase==="play"&&G.duel.turn===G.role&&art(c.id).c<=G.duel.fighters[G.role].energy}
 return true
}
function indexCard(action,i){
 if(action==="play")return G.combat?.player?.hand?.[i];
 if(action==="duelPlay")return G.duel?.fighters?.[G.role]?.hand?.[i];
 if(action==="reward")return G.reward?.choices?.[i];
 if(action==="buyCard")return G.shop?.cards?.[i];
 if(action==="upgrade")return G.runner?.deck?.[i];
 if(action==="pvpCounterCard")return G.duel?.fighters?.[G.role]?.hand?.[i];
 return null
}
function selectCard(node){
 document.querySelectorAll(".etch-card.selected").forEach(el=>el.classList.remove("selected"));
 node?.classList.add("selected");
}
function doCast(action,i,enemy=null){
 let c=indexCard(action,i);
 if(!playable(c,action))return;
 if(action==="play"&&enemy!==null&&G.combat?.enemies?.[enemy]?.hp>0)G.combat.target=enemy;
 UI.select=null;clearLine();
 Promise.resolve(dispatch(action,String(i))).catch(e=>{console.error(e);alert("Не удалось разыграть карту: "+e.message)})
}
function isPlayedAction(action){return action==="play"||action==="duelPlay"}
function clickCard(node){
 const action=node.getAttribute("data-cast-action"),i=Number(node.getAttribute("data-cast-index"));
 const c=indexCard(action,i);if(!c||node.disabled)return;
 if(!isPlayedAction(action)){doCast(action,i);return}
 if(isHostile(art(c.id))){
  if(UI.select&&UI.select.action===action&&UI.select.i===i){UI.select=null;selectCard(null)}
  else{UI.select={action,i};selectCard(node)}
 }else doCast(action,i)
}
function clearLine(){
 let x=document.getElementById("etch-aim-line");if(x?.remove)x.remove();
 document.querySelectorAll(".etch-person.aimed").forEach(x=>x.classList.remove("aimed"))
}
function drawLine(x0,y0,x1,y1){
 let overlay=document.getElementById("etch-aim-line");
 if(!overlay){overlay=document.createElementNS("http://www.w3.org/2000/svg","svg");overlay.id="etch-aim-line";overlay.setAttribute("class","etch-target-line");overlay.setAttribute("viewBox","0 0 "+window.innerWidth+" "+window.innerHeight);document.body.appendChild(overlay)}
 const mx=(x0+x1)/2,my=(y0+y1)/2-28;
 overlay.innerHTML='<defs><filter id="etch-glow"><feGaussianBlur stdDeviation="5"/></filter></defs>'+
 '<path d="M '+x0+' '+y0+' Q '+mx+' '+my+' '+x1+' '+y1+'" stroke="#f26a4f" stroke-width="12" opacity=".48" filter="url(#etch-glow)" fill="none"/>'+
 '<path d="M '+x0+' '+y0+' Q '+mx+' '+my+' '+x1+' '+y1+'" stroke="#ffd0a1" stroke-width="3" fill="none"/>'+
 '<circle cx="'+x1+'" cy="'+y1+'" r="10" stroke="#ffe2a1" stroke-width="3" fill="none"/>'
}
function pointerDown(e){
 if(e.button!==0&&e.pointerType==="mouse")return;
 const node=e.target.closest?.(".etch-card[data-cast-action]");if(!node||node.disabled)return;
 const action=node.getAttribute("data-cast-action"),i=Number(node.getAttribute("data-cast-index"));
 if(!isPlayedAction(action))return;
 const r=node.getBoundingClientRect();
 UI.drag={action,i,node,startX:e.clientX,startY:e.clientY,origX:r.x+r.width/2,origY:r.y+r.height/2,moved:false,ghost:null};
}
function pointerMove(e){
 const d=UI.drag;if(!d)return;
 const dx=e.clientX-d.startX,dy=e.clientY-d.startY;
 if(!d.moved&&Math.hypot(dx,dy)<12)return;
 if(!d.moved){
  d.moved=true;d.ghost=d.node.cloneNode(true);d.ghost.classList.add("held");d.ghost.disabled=true;
  d.ghost.removeAttribute("data-cast-action");document.body.appendChild(d.ghost);
 }
 if(d.ghost){d.ghost.style.left=e.clientX+"px";d.ghost.style.top=e.clientY+"px";d.ghost.style.width=Math.min(175,d.node.getBoundingClientRect().width*1.12)+"px"}
 const card=indexCard(d.action,d.i),targetted=card&&isHostile(art(card.id));
 if(targetted){
  drawLine(d.origX,d.origY,e.clientX,e.clientY);
  const el=document.elementFromPoint?.(e.clientX,e.clientY),unit=el?.closest?.(".etch-person.enemy");
  document.querySelectorAll(".etch-person.aimed").forEach(x=>x.classList.remove("aimed"));
  if(unit)unit.classList.add("aimed")
 }else clearLine()
 e.preventDefault()
}
function pointerUp(e){
 const d=UI.drag;if(!d)return;
 UI.drag=null;
 if(d.ghost?.remove)d.ghost.remove();
 clearLine();
 if(!d.moved)return;
 UI.skipTap=true;UI.skipTapAt=Date.now();
 let node=document.elementFromPoint?.(e.clientX,e.clientY);
 const unit=node?.closest?.(".etch-person.enemy");
 const card=indexCard(d.action,d.i);
 if(!card||!playable(card,d.action))return;
 if(isHostile(art(card.id))){
  if(unit){
    let index=unit.getAttribute("data-foe-index");
    doCast(d.action,d.i,index===null?null:Number(index))
  }else{UI.select={action:d.action,i:d.i};selectCard(d.node)}
 }else if(e.clientY<d.startY-50)doCast(d.action,d.i)
}
document.addEventListener("pointerdown",pointerDown,{capture:true,passive:true});
document.addEventListener("pointermove",pointerMove,{capture:true,passive:false});
document.addEventListener("pointerup",pointerUp,{capture:true,passive:true});
document.addEventListener("pointercancel",()=>{UI.drag?.ghost?.remove();UI.drag=null;clearLine()},{capture:true});
document.addEventListener("click",e=>{
 const card=e.target.closest?.(".etch-card[data-cast-action]");
 if(card){
  e.preventDefault();e.stopImmediatePropagation();
  if(UI.skipTap&&Date.now()-UI.skipTapAt<450){UI.skipTap=false;return}
  UI.skipTap=false;
  clickCard(card);return
 }
 const foe=e.target.closest?.(".etch-person.enemy[data-foe-index]");
 if(foe){
  e.preventDefault();e.stopImmediatePropagation();
  let enemy=Number(foe.getAttribute("data-foe-index"));
  if(UI.select&&isPlayedAction(UI.select.action)){doCast(UI.select.action,UI.select.i,enemy)}
  else if(G.combat?.enemies?.[enemy]?.hp>0){G.combat.target=enemy;render()}
 }
},true);
window.ATELIER_TEST={isHostile,playcard,stage,mapView,figure,cardVisual,UI};
render();
})();