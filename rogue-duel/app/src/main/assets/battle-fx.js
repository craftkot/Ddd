"use strict";
/* Ashes Duel 0.15 — lightweight cinematic battle animation & procedural audio.
   Entirely offline: no sound libraries, CDNs, permissions, copyrighted samples,
   large assets or polling. The gameplay engine remains authoritative. */
(function(){
 const K="ashes_av_sound_v015", goodDOM=()=>typeof document!=="undefined"&&!!document.body&&typeof document.createElement==="function";
 const bounded=(v,min,max)=>Math.min(max,Math.max(min,Number(v)||0));
 const defaults={enabled:true,master:.64,effects:.9,ambience:.22,ambientOn:true,shake:true,particles:true,reduce:false};
 let config={...defaults};
 try{const raw=localStorage.getItem(K);if(raw){const stored=JSON.parse(raw);for(const key of Object.keys(defaults))if(typeof stored[key]===typeof defaults[key])config[key]=stored[key]}}catch(e){}
 const mediaReduce=()=>{try{return !!window.matchMedia?.("(prefers-reduced-motion: reduce)")?.matches}catch(e){return false}};
 const reduced=()=>config.reduce||mediaReduce();
 function persist(){try{localStorage.setItem(K,JSON.stringify(config))}catch(e){}}
 let ctx=null,master=null,ambient=null,ambientTones=[],ambientLfo=null,noise=null;
 let lastCueAt=0,lastHoverAt=0;
 const AC=()=>window.AudioContext||window.webkitAudioContext;
 function initAudio(){
  if(!config.enabled||!AC())return false;
  try{
   if(!ctx){
    ctx=new (AC())();
    master=ctx.createGain();
    master.gain.value=config.master;
    master.connect(ctx.destination);
    const seconds=.65;
    noise=ctx.createBuffer(1,Math.ceil(ctx.sampleRate*seconds),ctx.sampleRate);
    const data=noise.getChannelData(0);
    // No sample fetching, generated once. A subtle filtered noise bed is used
    // for swooshes, slashes, crackles and impact transients.
    let x=123456789;
    for(let i=0;i<data.length;i++){x^=x<<13;x^=x>>>17;x^=x<<5;data[i]=(x/2147483648)*.48;}
   }
   if(ctx.state==="suspended"){const p=ctx.resume();if(p?.catch)p.catch(()=>{})}
   updateMaster();updateAmbience();
   return true;
  }catch(e){return false}
 }
 function updateMaster(){if(ctx&&master)master.gain.setTargetAtTime(config.enabled?config.master:0,ctx.currentTime,.03)}
 function gainEnvelope(start,attack,hold,release,amplitude,output=master){
  const g=ctx.createGain(),t=ctx.currentTime+Math.max(0,start);
  g.gain.setValueAtTime(0,t);
  g.gain.linearRampToValueAtTime(amplitude,t+Math.max(.002,attack));
  g.gain.setValueAtTime(amplitude,t+attack+hold);
  g.gain.exponentialRampToValueAtTime(.0002,t+attack+hold+release);
  g.connect(output);
  return {g,start:t,end:t+attack+hold+release};
 }
 function oscillator(kind,f0,f1,duration,vol=.15,delay=0,filter=0){
  if(!ctx)return;
  const env=gainEnvelope(delay,.004,Math.max(.006,duration*.2),Math.max(.02,duration*.8-.004),vol*config.effects);
  let last=env.g;
  if(filter){const bi=ctx.createBiquadFilter();bi.type="lowpass";bi.frequency.value=filter;bi.connect(last);last=bi;}
  const o=ctx.createOscillator();o.type=kind;
  o.frequency.setValueAtTime(Math.max(25,f0),env.start);
  o.frequency.exponentialRampToValueAtTime(Math.max(25,f1),env.end);
  o.connect(last);o.start(env.start);o.stop(env.end+.025);
 }
 function whoosh(startFreq,endFreq,vol=.18,duration=.16,delay=0,filter=2200){
  if(!ctx||!noise)return;
  const {g,start,end}=gainEnvelope(delay,.008,duration*.15,duration*.85,vol*config.effects);
  const lp=ctx.createBiquadFilter();lp.type="bandpass";
  lp.frequency.setValueAtTime(startFreq,start);
  lp.frequency.exponentialRampToValueAtTime(Math.max(100,endFreq),end);
  lp.Q.value=.55;lp.connect(g);
  const s=ctx.createBufferSource();s.buffer=noise;s.connect(lp);
  s.start(start,Math.random()*.12);s.stop(Math.min(end+.02,start+.45));
 }
 const presets={
  select:()=>{oscillator("triangle",510,700,.08,.062);oscillator("sine",1050,1100,.07,.025,.03)},
  draw:()=>{whoosh(900,2600,.055,.15);oscillator("sine",740,990,.12,.045,.03)},
  attack:()=>{whoosh(650,1700,.20,.17);oscillator("triangle",230,85,.15,.15,.045);whoosh(2200,900,.11,.09,.06)},
  impact:()=>{whoosh(1450,240,.25,.16);oscillator("triangle",185,60,.20,.18);oscillator("sine",82,51,.18,.15,.02)},
  blocked:()=>{oscillator("triangle",430,215,.19,.16);oscillator("sine",980,690,.18,.045,.025);whoosh(1150,800,.055,.09)},
  poison:()=>{oscillator("sine",280,470,.23,.085);oscillator("sine",510,310,.22,.08,.075);whoosh(700,220,.055,.18)},
  fire:()=>{whoosh(2200,900,.13,.23);whoosh(1100,3200,.08,.20,.095);oscillator("sawtooth",120,62,.16,.038)},
  bleed:()=>{whoosh(2900,440,.17,.18);oscillator("triangle",205,105,.12,.10)},
  heal:()=>{oscillator("sine",395,590,.31,.095);oscillator("sine",590,880,.33,.07,.09);oscillator("sine",880,1180,.22,.05,.17)},
  magic:()=>{oscillator("triangle",210,630,.27,.11);oscillator("sine",460,920,.26,.095,.055);whoosh(800,2100,.055,.20)},
  break:()=>{whoosh(3600,270,.37,.30);oscillator("sawtooth",115,52,.35,.15);oscillator("triangle",540,205,.28,.085,.085)},
  ultimate:()=>{oscillator("sawtooth",60,205,.7,.16);whoosh(650,3900,.24,.58,.14);oscillator("sine",220,900,.52,.13,.18);oscillator("triangle",880,120,.54,.15,.42);whoosh(3900,180,.30,.35,.55)},
  victory:()=>{for(const [i,f] of [440,554,660,880].entries())oscillator("sine",f,f*1.004,.42,.08,i*.125)},
  defeat:()=>{for(const [i,f] of [330,260,170].entries())oscillator("triangle",f,f*.64,.55,.08,i*.18)},
  button:()=>oscillator("sine",420,560,.055,.045),
  card:()=>{whoosh(480,2200,.085,.16);oscillator("triangle",450,660,.11,.065,.035)}
 };
 function sound(name){
  if(!config.enabled||config.effects<=0||typeof presets[name]!=="function"||!initAudio())return false;
  try{
   // Avoid a wall of overlapping hits when enemies attack simultaneously.
   if(name==="impact"&&Date.now()-lastCueAt<63)return true;
   if(name==="impact")lastCueAt=Date.now();
   presets[name]();return true;
  }catch(e){return false}
 }
 function clearAmbient(){
  for(const o of ambientTones){try{o.stop()}catch(e){}}
  ambientTones=[];if(ambient){try{ambient.disconnect()}catch(e){}}
  ambient=null;if(ambientLfo){try{ambientLfo.stop()}catch(e){}ambientLfo=null}
 }
 function updateAmbience(){
  if(!ctx||!master)return;
  if(!config.enabled||!config.ambientOn||config.ambience<=0||config.effects<=0||document.visibilityState==="hidden"){
   clearAmbient();return;
  }
  if(ambient){ambient.gain.setTargetAtTime(config.ambience*config.effects*.13,ctx.currentTime,.15);return}
  ambient=ctx.createGain();ambient.gain.value=0;ambient.connect(master);
  ambient.gain.setTargetAtTime(config.ambience*config.effects*.13,ctx.currentTime,.4);
  for(const [freq,type,weight]of [[55,"sine",.55],[82.4,"triangle",.28],[110.2,"sine",.12]]){
   const o=ctx.createOscillator(),g=ctx.createGain();o.type=type;o.frequency.value=freq;g.gain.value=weight;
   o.connect(g);g.connect(ambient);o.start();ambientTones.push(o);
  }
  ambientLfo=ctx.createOscillator();const shape=ctx.createGain();ambientLfo.type="sine";
  ambientLfo.frequency.value=.23;shape.gain.value=.009*config.ambience;
  ambientLfo.connect(shape);shape.connect(ambient.gain);ambientLfo.start();
 }
 function set(key,value){
  if(!(key in defaults))return;
  config[key]=typeof defaults[key]==="boolean"?!!value:bounded(value,0,1);
  persist();if(ctx){updateMaster();updateAmbience()}
  updateControls();
 }
 const pending=[];
 let previous=null, rendering=false,lastVisualId=0;
 function snapshot(){
  const c=G.combat,duel=G.duel,items=[];
  if(G.screen==="fight"&&c?.player){
   items.push({id:"hero",ref:c.player,hp:c.player.hp,block:c.player.block,stagger:c.player.stagger||0});
   for(let i=0;i<(c.enemies||[]).length;i++){
    const f=c.enemies[i];items.push({id:"enemy"+i,ref:f,hp:f.hp,block:f.block,stagger:f.stagger||0});
   }
  }else if((G.screen==="duel"||G.screen==="duelEnd")&&duel?.fighters){
   const own=duel.fighters[G.role],other=duel.fighters[G.role==="host"?"guest":"host"];
   if(own)items.push({id:"hero",ref:own,hp:own.hp,block:own.block,stagger:own.stagger||0});
   if(other)items.push({id:"enemy0",ref:other,hp:other.hp,block:other.block,stagger:other.stagger||0});
  }
  return {screen:G.screen,items};
 }
 function idFor(f,s){
  if(!f)return null;
  const list=s?.items||snapshot().items;
  return list.find(i=>i.ref===f)?.id||null;
 }
 function record(type,target,attacker,amount=0,extra){
  if(!target||pending.length>35)return;
  pending.push({type,id:idFor(target),source:idFor(attacker),amount:Math.max(0,Math.round(amount||0)),extra});
 }
 const oldHit=hit;
 hit=window.hit=function(a,b,base){
  const beforeHp=b?.hp||0,beforeBlock=b?.block||0;
  const dealt=oldHit(a,b,base);
  if(b&&(dealt>0||b.block<beforeBlock))
   record(dealt>0?"impact":"blocked",b,a,dealt);
  return dealt;
 };
 // DoT events share the real engine's tick function; visual damage never
 // changes the HP or replay/relay state.
 const oldDot=window.ASH_DEBUG?.ashTick;
 if(typeof oldDot==="function"){
  const visualTick=function(f,a){
   const p=f?.poison||0,b=f?.burn||0,l=f?.bleed||0;
   const hp=f?.hp||0;const dealt=oldDot(f,a);
   if(f&&hp>f.hp)record(b>=p&&b>=l?"fire":l>p?"bleed":"poison",f,a,hp-f.hp);
   return dealt;
  };
  ASH_DEBUG.ashTick=visualTick;
  ashTick=window.ashTick=visualTick;
 }
 const oldStagger=window.ASH_STATUSES?.stagger;
 // Breaks are also detected by snapshot; no changes to the game engine.
 function findUnit(id){
  if(id==="hero")return document.querySelector(".combat-arena .etch-person.hero");
  if(/^enemy\d+$/.test(id||""))return document.querySelector('.combat-arena .etch-person.enemy[data-foe-index="'+id.slice(5)+'"]');
  return null;
 }
 function fxLayer(){
  const arena=document.querySelector(".combat-arena");
  if(!arena)return null;
  let layer=arena.querySelector(".ashfx-layer");
  if(!layer){
   layer=document.createElement("div");layer.className="ashfx-layer";
   layer.setAttribute("aria-hidden","true");arena.appendChild(layer);
  }
  return layer;
 }
 function center(el,layer){
  const a=el.getBoundingClientRect(),r=layer.getBoundingClientRect();
  return {x:a.left+a.width/2-r.left,y:a.top+a.height*.46-r.top};
 }
 function cleanupWhenDone(element,animation){
  if(!animation){element.remove();return;}
  animation.onfinish=()=>{try{element.remove()}catch(e){}};
  animation.oncancel=()=>{try{element.remove()}catch(e){}};
 }
 function animateUnit(node,role){
  if(!node||!node.animate||reduced()||!config.shake)return;
  const el=node.querySelector(".unit-portrait-frame")||node;
  const key=role==="impact"?
   [{transform:"translate3d(0,0,0)",filter:"brightness(1)"},{transform:"translate3d(-6px,-3px,0) rotate(-3deg)",filter:"brightness(1.9)",offset:.28},{transform:"translate3d(3px,0,0)",filter:"brightness(1.23)",offset:.62},{transform:"translate3d(0,0,0)",filter:"brightness(1)"}]:
   role==="attack"?
   [{transform:"translate3d(0,0,0)"},{transform:"translate3d(17px,-5px,0) rotate(3deg)",offset:.55},{transform:"translate3d(0,0,0)"}]:
   [{transform:"translate3d(0,0,0)"},{transform:"translate3d(-4px,0,0)",offset:.34},{transform:"translate3d(0,0,0)"}];
  el.animate(key,{duration:role==="impact"?360:265,easing:"cubic-bezier(.15,.7,.21,1)"});
 }
 function projectile(source,target,kind,layer){
  if(reduced()||!source||!target||!layer)return;
  const from=center(source,layer),to=center(target,layer),el=document.createElement("div");
  el.className="ashfx-projectile kind-"+kind;
  el.style.left=from.x+"px";el.style.top=from.y+"px";layer.appendChild(el);
  const dx=to.x-from.x,dy=to.y-from.y;
  const curve=-Math.min(85,Math.abs(dx)*.23+12);
  const animation=el.animate([
   {transform:"translate3d(0,0,0) scale(.3)",opacity:0},
   {transform:`translate3d(${dx*.45}px,${dy*.45+curve}px,0) scale(1.2)`,opacity:1,offset:.46},
   {transform:`translate3d(${dx}px,${dy}px,0) scale(.5)`,opacity:0}
  ],{duration:kind==="ultimate"?560:300,easing:"cubic-bezier(.23,.66,.29,1)"});
  cleanupWhenDone(el,animation);
 }
 const colors={impact:"#f8c99a",blocked:"#a1d7ea",poison:"#91de8e",fire:"#ffad69",
  bleed:"#ef7e96",break:"#fcdea0",ultimate:"#e8b8ff",heal:"#a6f6cc",magic:"#d5a8fc"};
 function burst(layer,x,y,kind,amount){
  if(!layer||reduced()||!config.particles)return;
  const color=colors[kind]||colors.impact;
  const low=typeof window.innerWidth==="number"&&window.innerWidth<400;
  const n=kind==="ultimate"?(low?16:23):kind==="break"?(low?11:17):kind==="blocked"?5:(low?8:12);
  for(let i=0;i<n;i++){
   const angle=i/n*Math.PI*2+(i%3)*.16,dist=18+(i%4)*11;
   const el=document.createElement("i");el.className="ashfx-spark";
   el.style.left=x+"px";el.style.top=y+"px";el.style.background=color;
   el.style.boxShadow="0 0 8px "+color;
   layer.appendChild(el);
   const anim=el.animate([
    {opacity:0,transform:"translate3d(0,0,0) scale(.4)"},
    {opacity:.98,transform:`translate3d(${Math.cos(angle)*dist*.37}px,${Math.sin(angle)*dist*.37}px,0) scale(1.1)`,offset:.25},
    {opacity:0,transform:`translate3d(${Math.cos(angle)*dist}px,${Math.sin(angle)*dist}px,0) scale(.24)`}
   ],{duration:kind==="ultimate"?820:490+100*(i%3),easing:"ease-out"});
   cleanupWhenDone(el,anim);
  }
 }
 function numberPopup(layer,x,y,kind,amount){
  if(!layer||!amount)return;
  const node=document.createElement("b");node.className="ashfx-number "+kind;
  node.textContent=(kind==="heal"?"+":"−")+amount;
  node.style.left=x+"px";node.style.top=y+"px";
  layer.appendChild(node);
  const anim=node.animate([
   {opacity:0,transform:"translate(-50%,-10px) scale(.65)"},
   {opacity:1,transform:"translate(-50%,-24px) scale(1.2)",offset:.25},
   {opacity:0,transform:"translate(-50%,-58px) scale(.88)"}
  ],{duration:820,easing:"cubic-bezier(.2,.65,.25,1)"});
  cleanupWhenDone(node,anim);
 }
 function ultimateWave(layer){
  if(!layer||reduced())return;
  const w=layer.clientWidth,h=layer.clientHeight;
  const center={x:w*.62,y:h*.46};
  const halo=document.createElement("span");
  halo.className="ashfx-ult-halo";
  halo.style.left=center.x+"px";halo.style.top=center.y+"px";
  layer.appendChild(halo);
  cleanupWhenDone(halo,halo.animate([
   {transform:"translate(-50%,-50%) scale(.08)",opacity:0,filter:"brightness(2)"},
   {transform:"translate(-50%,-50%) scale(.4)",opacity:.9,filter:"brightness(1.6)",offset:.22},
   {transform:"translate(-50%,-50%) scale(2.8)",opacity:0,filter:"brightness(.6)"}
  ],{duration:990,easing:"cubic-bezier(.14,.65,.2,1)"}));
  const flash=document.createElement("span");
  flash.className="ashfx-ult-flash";layer.appendChild(flash);
  cleanupWhenDone(flash,flash.animate([
   {opacity:0},{opacity:.47,offset:.23},{opacity:.06,offset:.55},{opacity:0}
  ],{duration:660,easing:"ease-out"}));
  const hero=findUnit("hero");
  if(hero?.animate)hero.animate([
   {filter:"brightness(1)",transform:"translateZ(0)"},
   {filter:"brightness(1.8) drop-shadow(0 0 15px #e9aeff)",transform:"translate3d(0,-11px,0)",offset:.35},
   {filter:"brightness(1)",transform:"translateZ(0)"}
  ],{duration:730,easing:"ease-in-out"});
 }
 function cardFlight(fromRect,school,layer){
  if(!layer||!fromRect||reduced())return;
  const r=layer.getBoundingClientRect();
  const pt={x:fromRect.left+fromRect.width/2,
     y:fromRect.top+fromRect.height/2};
  const node=document.createElement("span");
  node.className="ashfx-cardflight";
  if(school==="Огонь")node.classList.add("flame");
  else if(school==="Яд")node.classList.add("venom");
  else if(school==="Кровь")node.classList.add("blood");
  node.style.left=pt.x+"px";node.style.top=pt.y+"px";
  document.body.appendChild(node);
  // Fly above the fixed dock into the arena. The arena is overflow-clipped,
  // so this intentionally uses a short-lived fixed viewport overlay.
  const dx=r.left+r.width*.53-pt.x,dy=r.top+r.height*.45-pt.y;
  cleanupWhenDone(node,node.animate([
   {opacity:0,transform:"translate(-50%,-50%) scale(.46) rotate(-22deg)"},
   {opacity:1,transform:"translate(-50%,-50%) scale(1.15) rotate(-7deg)",offset:.2},
   {opacity:.8,transform:`translate(calc(-50% + ${dx*.45}px),calc(-50% + ${dy*.45}px)) scale(.9) rotate(9deg)`,offset:.5},
   {opacity:0,transform:`translate(calc(-50% + ${dx}px),calc(-50% + ${dy}px)) scale(.12) rotate(35deg)`}
  ],{duration:470,easing:"cubic-bezier(.24,.65,.26,1)"}));
 }
 function playEvent(event,layer){
  const unit=findUnit(event.id);if(!unit||!layer)return;
  const from=findUnit(event.source);
  const kind=event.type==="impact"?"impact":event.type;
  const pt=center(unit,layer);
  if(kind==="impact"||kind==="break")animateUnit(unit,"impact");
  if(kind==="blocked")animateUnit(unit,"blocked");
  if(from&&kind==="impact"){animateUnit(from,"attack");projectile(from,unit,kind,layer);}
  burst(layer,pt.x,pt.y,kind,event.amount);
  numberPopup(layer,pt.x,pt.y,kind,event.amount);
  sound(kind==="impact"?"impact":presets[kind]?kind:"magic");
 }
 let priorScreen=G.screen;
 function showScreenCue(prev,next){
  if(prev===next)return;
  if(next==="reward"||next==="duelEnd"&&G.duel?.winner===G.role)sound("victory");
  else if(next==="lost"||next==="duelEnd"&&G.duel?.winner!==G.role)sound("defeat");
  else if(next==="fight"||next==="duel")sound("draw");
 }
 function flush(){
  if(!goodDOM())return;
  const snap=snapshot(),layer=fxLayer();
  if(snap.screen!=="fight"&&snap.screen!=="duel"){pending.length=0;previous=snap;return;}
  if(!layer){pending.length=0;previous=snap;return;}
  if(previous&&previous.screen===snap.screen){
   for(const item of snap.items){
    const prev=previous.items.find(x=>x.id===item.id);
    if(!prev)continue;
    if(item.hp<prev.hp&&!pending.some(e=>e.id===item.id&&e.amount>0))
      pending.push({id:item.id,source:null,type:"impact",amount:prev.hp-item.hp});
    if((item.stagger||0)<(prev.stagger||0)&&prev.stagger>=75)
      pending.push({id:item.id,source:null,type:"break",amount:0});
   }
  }
  const list=pending.splice(0,12);
  for(const effect of list)playEvent(effect,layer);
  previous=snap;
 }
 function mountControls(){
  if(!goodDOM()||!document.querySelector||typeof document.body.appendChild!=="function")return;
  let opener=document.getElementById("ash-audio-settings");
  if(!opener){
   opener=document.createElement("button");opener.type="button";opener.id="ash-audio-settings";
   opener.setAttribute("aria-label","Настройки звука и анимаций");opener.title="Звук и анимации";
   opener.className="ashfx-settings-trigger";
   opener.addEventListener("click",()=>toggleSettings());
   document.body.appendChild(opener);
  }
  opener.textContent=config.enabled?"♫":"♩";
  opener.setAttribute("aria-pressed",String(config.enabled));
 }
 function settingsPanel(){
  if(!goodDOM())return null;
  let panel=document.getElementById("ashfx-settings-panel");if(panel)return panel;
  panel=document.createElement("section");panel.id="ashfx-settings-panel";
  panel.className="ashfx-settings-panel";panel.hidden=true;
  panel.setAttribute("role","dialog");panel.setAttribute("aria-modal","false");
  panel.setAttribute("aria-label","Настройки звука и эффектов");
  panel.innerHTML='<div class="ashfx-settings-head"><strong>ЗВУК И АНИМАЦИИ</strong><button type="button" data-ashfx-close aria-label="Закрыть">✕</button></div>'+
    '<label><input type="checkbox" data-ashfx-setting="enabled"> Звуковые эффекты</label>'+
    '<label>Общая громкость <input type="range" min="0" max="100" data-ashfx-setting="master"><output data-ashfx-value="master"></output></label>'+
    '<label>Звуки боя <input type="range" min="0" max="100" data-ashfx-setting="effects"><output data-ashfx-value="effects"></output></label>'+
    '<label><input type="checkbox" data-ashfx-setting="ambientOn"> Атмосфера / гул арены</label>'+
    '<label>Громкость атмосферы <input type="range" min="0" max="100" data-ashfx-setting="ambience"><output data-ashfx-value="ambience"></output></label>'+
    '<label><input type="checkbox" data-ashfx-setting="particles"> Искры, частицы и цифры урона</label>'+
    '<label><input type="checkbox" data-ashfx-setting="shake"> Реакция на попадания</label>'+
    '<label><input type="checkbox" data-ashfx-setting="reduce"> Минимум анимаций (экономия заряда)</label>'+
    '<div class="ashfx-settings-note">Звуки синтезируются внутри игры: работают офлайн. Системное снижение анимаций учитывается автоматически.</div>';
  panel.addEventListener("click",e=>{
   if(e.target.closest("[data-ashfx-close]")){panel.hidden=true;return}
  });
  panel.addEventListener("change",e=>{
   const input=e.target.closest("[data-ashfx-setting]");
   if(!input)return;
   const key=input.dataset.ashfxSetting;
   set(key,input.type==="checkbox"?input.checked:Number(input.value)/100);
   if(key==="enabled"&&config.enabled)initAudio();
   if(key==="effects"&&config.enabled)sound("select");
  });
  document.body.appendChild(panel);updateControls();return panel;
 }
 function updateControls(){
  if(!goodDOM())return;
  const btn=document.getElementById("ash-audio-settings");
  if(btn){btn.textContent=config.enabled?"♫":"♩";btn.setAttribute("aria-pressed",String(config.enabled))}
  const pane=document.getElementById("ashfx-settings-panel");
  if(!pane)return;
  for(const key of Object.keys(defaults)){
   const control=pane.querySelector('[data-ashfx-setting="'+key+'"]');
   if(control)control.type==="checkbox"?control.checked=config[key]:control.value=Math.round(config[key]*100);
   const label=pane.querySelector('[data-ashfx-value="'+key+'"]');
   if(label)label.textContent=Math.round(config[key]*100)+"%";
  }
 }
 function toggleSettings(){
  const panel=settingsPanel();if(!panel)return;
  panel.hidden=!panel.hidden;
  if(!panel.hidden)panel.querySelector("[data-ashfx-close]")?.focus();
  sound("button");
 }
 // Deliberately no global untrusted data is inserted into innerHTML.
 let flushFrame=null;
 function scheduleVisualFlush(){
  if(flushFrame!==null)return;
  if(typeof window.requestAnimationFrame==="function"){
   flushFrame=window.requestAnimationFrame(()=>{flushFrame=null;flush()});
  }else flush();
 }
 const originalRender=render;
 render=window.render=function(){
  const prev=priorScreen;
  originalRender();
  if(!goodDOM())return;
  const next=G.screen;showScreenCue(prev,next);priorScreen=next;
  mountControls();
  // Multiple synchronous AV actions may render before the browser paints.
  // Show the final impact on the last mounted stage, not a discarded stage.
  scheduleVisualFlush();
 };
 const originalDispatch=dispatch;
 dispatch=window.dispatch=async function(action,x){
  const beforeScreen=G.screen;
  let card;
  if(action==="play")card=G.combat?.player?.hand?.[Number(x)];
  if(action==="duelPlay")card=G.duel?.fighters?.[G.role]?.hand?.[Number(x)];
  const oldSP=G.combat?.av?.sp;
  const oldCharge=G.combat?.av?.ultCharge;
  let fromRect=null;
  if(card&&goodDOM()){
   const node=document.querySelector('.combat-dock .etch-card[data-cast-index="'+Number(x)+'"][data-cast-action="'+action+'"]');
   if(node){const r=node.getBoundingClientRect();fromRect={left:r.left,top:r.top,width:r.width,height:r.height};}
  }
  const result=await originalDispatch(action,x);
  if(!goodDOM())return result;
  if(card){
   const t=art(card.id),school=t?.school||"";
   cardFlight(fromRect,school,fxLayer());
   if(school==="Яд"||school==="Лечение")sound(school==="Лечение"?"heal":"poison");
   else if(school==="Огонь")sound("fire");
   else if(school==="Кровь")sound("bleed");
   else sound(t?.t==="attack"?"attack":t?.t==="power"?"magic":"card");
  }else if(action==="ultimate"&&oldCharge>=100&&beforeScreen==="fight"||
           action==="duelUltimate"&&beforeScreen==="duel"){
   sound("ultimate");
   const layer=fxLayer();if(layer){const rect=layer.getBoundingClientRect();ultimateWave(layer);burst(layer,rect.width*.62,rect.height*.42,"ultimate",0)}
  }else if(action==="basicAttack"||action==="duelBasic"){
   sound("attack");
  }else if(action==="usePotion"||action==="restHeal"){
   sound("heal");
  }else if(action==="reward"||action==="upgrade"||action==="buyRelic"){
   sound("card");
  }else if(action==="node"||action==="hero")sound("draw");
  else if(action==="create"||action==="join")sound("select");
  return result;
 };
 if(typeof document!=="undefined"&&document.addEventListener){
  document.addEventListener("pointerdown",()=>initAudio(),{passive:true});
  document.addEventListener("click",e=>{
   const button=e.target.closest?.("button");
   if(button&&config.enabled&&Date.now()-lastHoverAt>85){
    lastHoverAt=Date.now();
    if(button.matches(".etch-card")&&window.UI?.select)sound("select");
    else if(button.matches(".dock-target")||button.matches(".etch-person.enemy"))sound("select");
   }
  },{passive:true,capture:true});
  document.addEventListener("visibilitychange",()=>{
   if(document.visibilityState==="hidden"){clearAmbient();try{ctx?.suspend()}catch(e){}}
   else if(ctx&&config.enabled){try{ctx.resume().catch(()=>{})}catch(e){}updateAmbience()}
  });
  document.addEventListener("keydown",e=>{
   if(e.key==="Escape"){const pane=document.getElementById("ashfx-settings-panel");if(pane)pane.hidden=true}
  });
 }
 // In a browser with DOM the settings button appears on the very first screen.
 if(goodDOM())mountControls();
 window.ASH_FX={config,set,sound,record,flush,snapshot,reduced,initAudio,updateControls};
})();
