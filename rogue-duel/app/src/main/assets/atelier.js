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
/* Real offline card illustrations: 12 original painted atlas crops, plus Kenney CC0 item icons.
   This function only changes artwork; effects, cost and card IDs remain untouched. */
function paintedPortrait(kind){
 const aliases={guard:"guard",oracle:"oracle",hunter:"hunter",bandit:"bandit",cultist:"cultist",skeleton:"skeleton",slime:"slime",hound:"hound",boss:"boss",knight:"skeleton",mage:"cultist"};
 const imageKind=aliases[kind]||"bandit";
 const labels={guard:"Страж — бронированный рыцарь",oracle:"Оракул — повелительница эфира",hunter:"Ловчий — лесной следопыт",bandit:"Разбойник",cultist:"Культист",skeleton:"Скелет-воин",slime:"Болотная тварь",hound:"Пепельная гончая",boss:"Король мёртвых"};
 return '<img class="figure artbook-figure character-'+imageKind+'" src="art/characters/'+imageKind+'.webp" alt="'+labels[imageKind]+'" draggable="false" loading="eager" decoding="async">';
}
function cardVisual(c){
 const a=art(c.id),school=String(a.school||""),seed=[...String(c.id)].reduce((s,ch)=>(s*37+ch.charCodeAt(0))>>>0,17);
 const isFire=/огонь|fire|плам|жар/i.test(school+" "+a.n);
 const isPoison=/яд|лечен|природ|venom|poison/i.test(school+" "+a.n);
 const isArcane=a.t==="power"||/маг|астр|ритуал|эфир|темн|хрон|time/i.test(school+" "+a.n);
 const isGuard=/щит|оборон|защит|block/i.test(school+" "+a.n)||a.k==="block";
 const tone=isFire?"ember":isPoison?"moss":isArcane?"violet":isGuard?"steel":a.t==="attack"?"scarlet":"azure";
 const palettes={ember:["#e6aa68","#923f38","#2a1623"],moss:["#bbcf8e","#49644d","#15272c"],violet:["#d3a5f7","#684881","#211b40"],steel:["#c5dfef","#5483a1","#172c45"],scarlet:["#f0a286","#8b3d4c","#241929"],azure:["#9fd3e8","#336d89","#172f42"]};
 const colors=palettes[tone],id="fate"+seed;
 const stars=Array.from({length:8},(_,i)=>{
   const x=13+(seed+i*71)%210,y=12+((seed>>>i)+i*29)%110;
   return '<circle cx="'+x+'" cy="'+y+'" r="'+(i%3===0?1.4:.8)+'" fill="'+colors[0]+'" opacity=".7"/>';
 }).join("");
 const effects=[String(a.k||""),...(a.effects||[]).map(e=>e.key)].join(" ").toLowerCase();
 const category= /heal|regen|meditate|лечен|восстан/i.test(effects+" "+a.n)?"heal":
   /poison|venom|aoepoison|яд|токс/i.test(effects+" "+a.n)?"venom":
   /burn|fire|ember|огонь|жар|плам/i.test(effects+" "+a.n)?"flame":
   /block|shield|guard|ward|armor|щита|брон/i.test(effects+" "+a.n)?"shield":
   /speed|delay|advance|slow|retard|хрон|время|ав/i.test(effects+" "+school+" "+a.n)?"time":
   /bleed|blood|leech|кров|клин/i.test(effects+" "+a.n)?"blade":
   /draw|flow|ritual|energy|str|маг|эфир/i.test(effects+" "+school+" "+a.n)?"arcane":
   /ambush|bow|ranged|hunter|стрел|лук/i.test(effects+" "+a.n)?"bow":
   a.t==="attack"?"sword":a.t==="power"?"arcane":"crystal";
 const metal="url(#"+id+"metal)";
 const ornament={
  sword:'<path d="M107 91L137 32 147 21 153 37 121 104Z" fill="'+metal+'" stroke="'+colors[0]+'" stroke-width="3"/><path d="M98 91L133 113 M113 103L102 119" stroke="'+colors[0]+'" stroke-width="8" stroke-linecap="round"/><path d="M125 39L116 85" stroke="#fff5d7" stroke-width="2" opacity=".9"/>',
  blade:'<path d="M86 108Q113 77 126 28Q148 43 136 74Q122 95 86 108Z" fill="'+metal+'" stroke="'+colors[0]+'" stroke-width="4"/><path d="M102 89L140 123 M104 107L145 89" fill="none" stroke="'+colors[0]+'" stroke-width="7"/>',
  shield:'<path d="M120 26Q144 43 162 43L156 82Q146 105 120 119Q94 105 84 82L78 43Q105 39 120 26Z" fill="'+metal+'" stroke="'+colors[0]+'" stroke-width="5"/><path d="M120 43V105 M95 71H145" stroke="#fff5da" stroke-width="4" opacity=".75"/>',
  flame:'<path d="M122 17Q103 51 119 69Q139 57 141 38Q177 98 132 122Q90 136 79 98Q72 66 103 47Q99 80 120 86Q111 63 122 17Z" fill="'+metal+'" stroke="'+colors[0]+'" stroke-width="5"/><path d="M122 83Q105 102 123 120Q144 104 122 83Z" fill="#fff1bc" opacity=".9"/>',
  venom:'<path d="M92 31Q162 30 150 69Q146 84 119 91Q88 100 99 121Q73 104 90 87Q116 66 129 69Q149 58 129 48Q110 42 92 53Z" fill="'+metal+'" stroke="'+colors[0]+'" stroke-width="4"/><circle cx="140" cy="57" r="4" fill="#f7f0be"/><path d="M111 96L105 110 M121 100L120 115 M130 97L136 109" stroke="'+colors[0]+'" stroke-width="3"/>',
  heal:'<path d="M120 111Q72 82 80 51Q89 22 120 53Q147 21 161 48Q173 76 120 111Z" fill="'+metal+'" stroke="'+colors[0]+'" stroke-width="5"/><path d="M120 53V101 M96 76H144" stroke="#fff8d9" stroke-width="7" stroke-linecap="round"/>',
  time:'<path d="M89 29H151M91 117H149M94 34L144 34Q141 56 126 68Q140 82 146 114H94Q101 82 114 68Q97 52 94 34Z" fill="'+metal+'" stroke="'+colors[0]+'" stroke-width="4"/><path d="M105 42H136L120 67L107 103H134" fill="none" stroke="#fff6cc" stroke-width="3"/>',
  arcane:'<path d="M120 21L135 56L170 70L135 84L120 119L105 84L70 70L105 56Z" fill="'+metal+'" stroke="'+colors[0]+'" stroke-width="4"/><circle cx="120" cy="70" r="20" fill="#131c30" stroke="#fff1c7" stroke-width="3"/><path d="M120 48V92M98 70H142" stroke="'+colors[0]+'" stroke-width="2"/>',
  bow:'<path d="M101 29Q161 69 99 118" fill="none" stroke="'+colors[0]+'" stroke-width="8"/><path d="M101 29L99 118M76 69H169M169 69L156 58M169 69L156 80" stroke="#fff0cf" stroke-width="3" fill="none"/>',
  crystal:'<path d="M119 19L160 61L134 114H101L79 61Z" fill="'+metal+'" stroke="'+colors[0]+'" stroke-width="4"/><path d="M119 19L120 114M79 61H160M119 19L101 61L120 114L134 61Z" fill="none" stroke="#fff5d7" stroke-width="2" opacity=".9"/>'
 };
 const glyph='<g transform="rotate('+((seed%17)-8)+' 120 70)">'+ornament[category]+'</g>';
 const base='<svg class="fate-art-svg" viewBox="0 0 240 140" aria-hidden="true" preserveAspectRatio="xMidYMid slice"><defs><linearGradient id="'+id+'" x1="0" y1="0" x2="1" y2="1"><stop stop-color="'+colors[1]+'"/><stop offset="1" stop-color="'+colors[2]+'"/></linearGradient><radialGradient id="'+id+'halo"><stop stop-color="'+colors[0]+'" stop-opacity=".5"/><stop offset="1" stop-color="'+colors[0]+'" stop-opacity="0"/></radialGradient><linearGradient id="'+id+'metal" x1="0" y1="0" x2="1" y2="1"><stop stop-color="'+colors[0]+'"/><stop offset=".55" stop-color="'+colors[1]+'"/><stop offset="1" stop-color="'+colors[2]+'"/></linearGradient></defs><rect width="240" height="140" fill="url(#'+id+')"/><circle cx="119" cy="70" r="80" fill="url(#'+id+'halo)"/><path d="M-10 135L38 94 73 117 101 76 149 114 182 83 248 138Z" fill="#0b1627" opacity=".69"/><circle cx="120" cy="72" r="53" fill="none" stroke="'+colors[0]+'" opacity=".38"/><circle cx="120" cy="72" r="46" fill="none" stroke="'+colors[0]+'" opacity=".2" stroke-dasharray="6 11"/>'+stars+glyph+'</svg>';
 return '<div class="fate-illustration tone-'+tone+'">'+base+'<span class="fate-art-flourish">✦</span></div>'
}

function relicPainting(r){
 const id=String(r?.id||"");
 const icons={sword:"sword",shield:"shield",chain:"shield",spark:"staff",rose:"staff",scroll:"staff",gem:"staff",heart:"shield",coin:"axe",sun:"hammer",venom:"dagger",flask:"staff"};
 const icon=icons[id]||(["sword","shield","bow","staff","axe","dagger","hammer"][id.length%7]);
 return '<img class="kenney-relic" src="art/kenney/icons/'+icon+'.png" alt="" draggable="false">';
}
function paintedScene(name,label){
 return '<img class="artbook-scene" src="art/kenney/scene/'+name+'.png" alt="'+esc(label)+'" draggable="false">';
}
function playcard(c,act,i,disabled=false){
 const a=art(c.id),tone=toneOf(a),rarity=a.rarity||"common";
 const description=String(desc(c));
 const textClass=description.length>165?" copy-very-long":description.length>98?" copy-long":"";
 const label=a.n+" — "+description+". Стоимость "+a.c+" ОН";
 return '<button type="button" style="--deal:'+i+'" class="etch-card fate-card '+tone+' '+rarity+textClass+'" data-cast-action="'+act+'" data-cast-index="'+i+'" aria-label="'+esc(label)+'"'+(disabled?" disabled":"")+'>'+
  '<span class="card-etched"></span><div class="fate-card-top"><span class="cost" aria-hidden="true">'+a.c+'</span><span class="card-title">'+esc(a.n)+upgraded(c)+'</span></div>'+
  '<div class="card-art">'+cardVisual(c)+'</div><div class="card-desc">'+esc(description)+'</div>'+
  '<div class="card-type"><span>'+longType(a.t)+'</span><span>'+(rarity==="rare"?"◆ РЕДКАЯ":rarity==="uncommon"?"◇ НЕОБЫЧНАЯ":"✧ ОБЫЧНАЯ")+'</span></div></button>'
}

window.ASH_UI_DEBUG={playcard,cardVisual,stage,hand,fightView};
function eventHead(title,subtitle){return '<div class="etch-head"><p class="eyebrow">✦ ПЕПЕЛ И КАРТЫ ✦</p><h2>'+esc(title)+'</h2><p class="sub">'+esc(subtitle||"")+'</p></div>'}
function health(f){return '<span class="fig-hp">❤ '+trim(f.hp)+'/'+trim(f.maxHp)+'</span><span class="fig-track"><span style="width:'+pct(f.hp,f.maxHp)+'%"></span></span>'}
function stateIcons(f){
 const labels={block:"Броня",poison:"Яд: чистый урон в свой ход",burn:"Горение: двойной урон в свой ход",
  bleed:"Кровотечение: усиливает прямые удары и ДоТ",mark:"Метка: усиливает атаки",
  str:"Сила",ward:"Щит от прямого урона ДоТ",weak:"Слабость: меньше урона",vuln:"Уязвимость: больше получаемого урона",
  regen:"Регенерация",thorns:"Шипы"};
 const statuses=[["🛡",f.block,"block"],["☠",f.poison,"poison"],["🔥",f.burn,"burn"],
   ["🩸",f.bleed,"bleed"],["🎯",f.mark,"mark"],["⚔",f.str,"str"],
   ["⬟",f.ward,"ward"],["⏳",f.weak,"weak"],["⚡",f.vuln,"vuln"],
   ["✧",f.regen,"regen"],["✦",f.thorns,"thorns"]];
 const out=[];
 for(const [sym,val,key] of statuses)if(val>0)
   out.push('<small class="status-'+key+'" title="'+esc(labels[key]+": "+trim(val))+'">'+sym+' '+trim(val)+'</small>');
 return '<div class="fig-status">'+out.join("")+'</div>'
}
function statusForecast(f,isEnemy){
 if(!window.ASH_STATUSES||!f||f.hp<=0)return"";
 const info=ASH_STATUSES.breakdown(f),stagger=Math.max(0,f.stagger||0);
 const significant=info.total>0||stagger>0;
 if(!significant)return"";
 const combo=info.combo?' · '+info.combo.label:"";
 const label=esc(ASH_STATUSES.explain(f));
 return '<div class="ash-status-forecast" title="'+label+'" aria-label="'+label+'">'+
  (info.total>0?'<span class="ash-dot-next">ДоТ −'+info.hpDamage+(info.ward>0?" (щит)":"")+'</span>':"")+
  (stagger>0?'<span class="ash-break-next">⚒ '+stagger+'/'+ASH_STATUSES.threshold(f)+'</span>':"")+
  (info.combo?'<span class="ash-combo-next" title="'+esc(info.combo.label)+'">✦</span>':"")+'</div>'
}
function intentOf(f){
 if(f.hp<=0)return"ПОВЕРЖЕН";
 if(f.intentText)return f.intentText;
 const mode=(f.intent||0)%4;
 return mode===0?"⚔ "+(f.attack||9):mode===1?"🛡 Броня":mode===2?"☠ Яд":"🩸 Кровь"
}
function unit(f,kind,i,isEnemy,clickable){
 const alive=f.hp>0,chosen=isEnemy&&G.combat?.target===i;
 const intent=isEnemy&&alive?'<div class="etch-intent" title="'+esc(intentOf(f))+'">'+esc(intentOf(f))+'</div>':"";
 return '<button type="button" '+(clickable?'data-foe-index="'+i+'" ':"")+
  'class="etch-person '+(isEnemy?"enemy":"hero")+' '+(alive?"":"dead")+' '+(chosen?"is-target":"")+'"'+(isEnemy&&!alive?' disabled':"")+'>'+
  '<span class="fate-ground-shadow"></span><span class="unit-portrait-frame kind-'+esc(String(kind||"unknown").replace(/[^a-z0-9_-]/gi,""))+'">'+paintedPortrait(kind)+'</span>'+
  '<div class="fig-name">'+esc(f.name)+'</div>'+health(f)+statusForecast(f,isEnemy)+stateIcons(f)+intent+'</button>'
}

function stage(hero,foes,target,clickable){
 const num=foes.length;
 return '<div class="etch-stage fate-stage count-'+num+'"><div class="etch-moon"></div><div class="etch-spires"></div>'+
  (num>=3?'<div class="fate-battle-risers" aria-hidden="true"><div class="fate-riser rear"></div><div class="fate-riser front"></div></div>':'')+
  '<div class="etch-fighters"><div class="fate-player-platform">'+unit(hero,hero.hero||G.runner?.hero||"guard",-1,false,false)+'</div>'+
  '<div class="etch-team count-'+num+'">'+foes.map((f,i)=>unit(f,f.kind||f.hero||"boss",i,true,clickable)).join("")+'</div></div>'+
  '<div class="etch-groundline"></div></div>'
}

function hud(hero,other=""){
 return '<div class="etch-battle-hud"><span>❤ <strong class="hp">'+trim(hero.hp)+' / '+trim(hero.maxHp)+'</strong></span><span>'+esc(hero.name)+'</span>'+other+'</div>'
}
function hand(f,action,disabled){
 const cards=f.hand||[],c=G.combat,s=action==="play"&&c?.av&&window.ASH_AV?.valid(c)?c.av:null;
 const energy=s?s.sp:f.energy,limit=s||action==="duelPlay"?5:3;
 return '<div class="etch-resourcebar"><div class="etch-energy">'+trim(energy)+'/'+limit+'</div><div class="etch-metrics">'+
  '<span>▣ Колода '+trim(f.deck?.length)+'</span><span>↺ Сброс '+trim(f.discard?.length)+'</span><span>В руке '+cards.length+'</span></div></div>'+
  '<div class="etch-hand-shell"><div class="etch-hand count-'+Math.min(10,cards.length)+'">'+
  cards.map((c,i)=>playcard(c,action,i,disabled||art(c.id).c>energy||art(c.id).t==="reaction")).join("")+'</div></div>'
}

function fightView(){
 const c=G.combat,p=c.player,av=c.av&&window.ASH_AV?.valid(c)?c.av:null;
 const ready=!av||av.current==="hero";
 const turns=av?ASH_AV.preview(c,7):[];
 const order=av?'<div class="fate-av-bar" aria-label="Очередь действий"><div class="fate-av-label">ОЧЕРЕДЬ · ACTION VALUE</div>'+
  '<div class="fate-av-scroll">'+turns.map((a,i)=>'<span class="fate-av-token '+(a.kind==="hero"?"ally":"hostile")+' '+(a.active?"current":"")+'" title="'+esc(a.name)+'">'+
   '<strong>'+(a.kind==="hero"?"ТЫ":esc(a.name))+'</strong><small>AV '+a.av+'</small></span>').join("")+'</div></div>':"";
 const points=av?'<div class="fate-sp-tray" aria-label="Очки навыков"><strong>ОЧКИ НАВЫКОВ</strong><div class="fate-sp-points">'+
  Array.from({length:5},(_,i)=>'<span class="'+(i<av.sp?"filled":"empty")+'"></span>').join("")+'</div><b>'+av.sp+' / 5</b></div>':"";
 const stageHeader='<div class="fate-battle-caption"><strong>БОЙ · ЭТАП '+(G.stage+1)+'</strong><small>Выбери цель · ходы определяет скорость</small></div>';
 return stageHeader+hud(p,'<span class="coin">◈ '+G.runner.gold+'</span>')+order+
  stage({...p,hero:G.runner.hero},c.enemies,c.target,true)+points+ASH_RUN.potionBar()+
  '<div class="fate-combat-controls"><button type="button" class="etch-btn cool fate-basic" data-a="basicAttack" '+(ready?"":"disabled")+'>'+
  '<strong>⚔ Базовая атака</strong><span>+1 ОН · 6 урона · передать ход</span></button>'+
  '<span class="etch-hint">'+(ready?"Выбери одну карту: после её применения очередь AV продвинется.":"Враг действует.")+'</span></div>'+
  '<div class="fate-hand-label">ТВОЯ РУКА <small>Все эффекты написаны на картах</small></div>'+
  hand(p,"play",!ready)+
  '<div class="logs">'+G.log.slice(-5).map(esc).join("\\n")+'</div>'
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
 '<div class="fate-sp-tray" aria-label="Очки навыков"><strong>ОЧКИ НАВЫКОВ</strong><div class="fate-sp-points">'+Array.from({length:5},(_,i)=>'<span class="'+(i<(m.sp??m.energy)?"filled":"empty")+'"></span>').join("")+'</div><b>'+(m.sp??m.energy)+' / 5</b></div>'+
 hand(m,"duelPlay",!mine)+
 '<div class="etch-actions"><button data-a="duelBasic" class="etch-btn cool fate-basic" '+(!mine?"disabled":"")+'><strong>⚔ Базовая атака</strong><span>5 урона · +1 ОН</span></button><button data-a="duelEndTurn" class="etch-btn cta" '+(!mine?"disabled":"")+'>Передать ход</button></div>'+
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
 '<p class="small">❤ '+r.hp+'/'+r.maxHp+' · ◈ '+r.gold+' · '+r.deck.length+' карт · '+r.relics.length+' реликвий · 🧪 '+(r.potions||[]).length+'/3</p></div>'+
 '<div class="etch-next-panel"><div class="eyebrow">ДОСТУПНЫЕ ПУТИ</div><p class="small">Можно выбрать только узел, соединённый с твоим последним шагом. У каждого игрока своё дерево.</p>'+
 '<div class="etch-next-choices">'+choices.map(node=>'<button type="button" class="etch-btn etch-next-button" data-a="node" data-x="'+node.index+'">'+emblem(node.type)+' '+esc(LABEL[node.type][1])+'</button>').join("")+'</div>'+
 (G.lastRouteWarning?'<p class="dangertext">'+esc(G.lastRouteWarning)+'</p>':'')+'</div>'+
 '<div class="etch-tree-viewport"><div class="etch-worldmap etch-tree-map" style="height:'+h+'px"><svg class="etch-tree-links" viewBox="0 0 1000 '+h+'" preserveAspectRatio="none" aria-hidden="true">'+paths+'</svg>'+nodes+'</div></div>'+
 '<p class="etch-hint">Дерево строится снизу вверх. Золотые узлы доступны. Зелёные — пройденный путь. Серые — ещё закрыты.</p>'+
 '<div class="etch-map-legend">⚔ Бой · ☠ Элита · ◆ Магазин · ◇ Сокровище · ✧ Событие · ♨ Привал</div>'+
 '<div class="etch-actions"><button data-a="inventory" class="etch-btn plain">Посмотреть свою сборку</button></div>'
}
function rewardView(){
 return eventHead(G.reward.title,G.reward.desc)+'<div class="etch-story"><div class="story-symbol">'+paintedScene("chest","Сундук с наградой")+'</div><div class="center money">◈ Получено '+G.reward.gold+' золота</div><div class="etch-catalog">'+G.reward.choices.map((c,i)=>'<div>'+playcard(c,"reward",i)+'</div>').join("")+'</div><div class="etch-actions"><button class="etch-btn plain" data-a="skipReward">Пропустить награду</button></div></div>'
}
function shopView(){
 let s=G.shop;
 return eventHead("Лавка под пеплом","Торговец принимает золото. Купленные карты остаются в колоде.")+
 '<div class="etch-catalog">'+s.cards.map((c,i)=>c?'<div>'+playcard(c,"buyCard",i,G.runner.gold<(s.prices?.[i]??55))+'<div class="center money">'+ASH_RUN.RARITY_LABEL[art(c.id).rarity]+' · ◈ '+(s.prices?.[i]??55)+'</div></div>':'<div class="etch-offer">Продано</div>').join("")+'</div>'+
 '<div class="etch-catalog">'+s.relics.map(id=>{let r=RELICS.find(x=>x.id===id);return'<div class="etch-offer"><span class="offer-art">'+relicPainting(r)+'</span><strong>'+esc(r.n)+'</strong><p>'+esc(r.d)+'</p><button data-a="buyRelic" data-x="'+id+'" class="etch-btn" '+(G.runner.gold<115?'disabled':'')+'>◈ 115 · Купить</button></div>'}).join("")+'</div>'+
 '<div class="etch-catalog">'+ASH_RUN.shopPotion()+'</div>'+ '<div class="etch-actions"><button data-a="buyHeal" class="etch-btn cool" '+(G.runner.gold<40?'disabled':'')+'>Лечение +20 · ◈ 40</button><button data-a="buyRemove" class="etch-btn" '+(G.runner.gold<75?'disabled':'')+'>Удалить карту · ◈ 75</button></div>'+
 '<div class="etch-actions"><button data-a="next" class="etch-btn cta">Покинуть магазин</button></div>'
}
function eventView(){
 let e=G.encounter.event;
 return '<div class="etch-story"><div class="eyebrow center">НЕИЗВЕДАННАЯ ТРОПА</div><div class="story-symbol">'+e.e+'</div><h2>'+esc(e.n)+'</h2><p class="sub">'+esc(e.d)+'</p>'+
 e.options.map((x,i)=>'<button class="etch-decision" data-a="event" data-x="'+i+'"><b>'+String.fromCharCode(8544+i)+'</b> · '+esc(x[0])+'</button>').join("")+'</div>'
}
function restView(){
 return '<div class="etch-story"><div class="eyebrow center">ТИШИНА МЕЖДУ БИТВАМИ</div><div class="story-symbol">'+paintedScene("campfire","Ночной привал")+'</div><h2>Ночной привал</h2><p class="sub">Пламя не отвечает на вопросы. Можно вернуть силы или закалить карту.</p>'+
 '<button data-a="restHeal" class="etch-decision">❤ Отдохнуть · восстановить '+Math.ceil(G.runner.maxHp*.3)+' здоровья</button>'+
 '<button data-a="restUpgrade" class="etch-decision">⚒ Закалить · улучшить карту</button></div>'
}
function treasureView(){
 return eventHead("Забытый тайник","Одну из трёх реликвий можно взять без платы.")+
 '<div class="etch-catalog">'+G.encounter.choices.map(id=>{let r=RELICS.find(x=>x.id===id);return '<div class="etch-offer"><span class="offer-art">'+relicPainting(r)+'</span><strong>'+esc(r.n)+'</strong><p>'+esc(r.d)+'</p><button data-a="takeRelic" data-x="'+r.id+'" class="etch-btn cool">Взять реликвию</button></div>'}).join("")+'</div>'
}
function heroView(){
 let intro='<div class="etch-head"><p class="eyebrow">ИЗБЕРИ ПУТЬ</p><h2>Три судьбы</h2><p class="sub">'+(G.demo?"Одиночный тест маршрута":"Код комнаты: "+G.code)+"</p></div>";
 return intro+'<div class="etch-class-grid">'+Object.entries(CLASSES).map(([id,c])=>'<button class="etch-class hero-choice hero-'+id+'" data-a="hero" data-x="'+id+'"><span class="hero-portrait-wrap">'+paintedPortrait(id)+'</span><span class="hero-choice-info"><span class="hero-role">КЛАСС ГЕРОЯ</span><h3>'+c.name+'</h3><span class="hero-hp">❤ '+c.hp+' здоровья</span><p>'+esc(c.desc)+'</p><span class="hero-select">ВЫБРАТЬ ПЕРСОНАЖА ›</span></span></button>').join("")+'</div>'
}
function lobbyView(){
 return '<div class="etch-lobby"><div class="hero-crest">⚔</div><div class="eyebrow">ДВА ПУТИ · ОДНА ДУЭЛЬ</div><h1>Пепел и карты</h1><p class="lead">Собери колоду, пройди разветвлённый путь и сразись с другим игроком. Действия определяются скоростью и контрприёмами.</p>'+
 '<div class="etch-panel"><h3>Создать приключение</h3><p class="sub">Первый игрок создаёт комнату, второй подключается по коду.</p><button data-a="create" class="etch-btn cta etch-wide">Создать комнату</button></div>'+
 '<div class="etch-panel"><h3>Присоединиться к другу</h3><input class="input" id="codeInput" maxlength="4" placeholder="КОД ИЗ 4 СИМВОЛОВ" autocapitalize="characters" autocomplete="off" spellcheck="false"><button data-a="join" class="etch-btn cool etch-wide">Войти к другу</button></div>'+
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
 if(G.screen==="map"){const viewport=document.querySelector(".etch-tree-viewport");const first=viewport?.querySelector(".etch-route-node.reachable");if(viewport&&first)viewport.scrollTop=Math.max(0,first.offsetTop-viewport.clientHeight*.56)}
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
 // In the final duel there is only one possible enemy; casting should
 // never require a second tap on a tiny portrait.
 if(action==="duelPlay"){doCast(action,i);return}
 if(isHostile(art(c.id))){
  if(UI.select&&UI.select.action===action&&UI.select.i===i){UI.select=null;selectCard(null)}
  else{UI.select={action,i};selectCard(node)}
 }else doCast(action,i)
 window.ASH_COMBAT_DOCK?.syncTargets?.();
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
 // Horizontal swipes navigate the fixed hand, not the card-drag targeting
 // gesture. Without this guard pointermove.preventDefault blocked scrolling.
 if(!d.moved&&d.node.closest?.(".combat-dock")&&
    Math.abs(dx)>14&&Math.abs(dx)>Math.abs(dy)*1.2){
   UI.drag=null;return;
 }
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