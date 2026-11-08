"use strict";
/* Ashes 0.14 — deterministic status / rupture / stance-break rules.
   Installs after PvP cap / ward wrappers and before AV, UI and run-save.
   This module deliberately retains the previous status fields for old saves
   and network messages. No timers, RNG or new secrets in game state. */
(function(){
  const DOTS=["poison","burn","bleed"];
  const LIMITS={pve:{poison:24,burn:16,bleed:22},pvp:{poison:16,burn:12,bleed:16}};
  const safe=(n)=>Math.max(0,Number.isFinite(Number(n))?Math.floor(Number(n)):0);
  const clamp=(v,a,b)=>Math.max(a,Math.min(b,v));
  const inPvp=f=>!!(G.duel?.fighters&&(G.duel.fighters.host===f||G.duel.fighters.guest===f));
  function profile(f){return inPvp(f)?"pvp":"pve"}
  function capStacks(f){
    if(!f)return f;
    const limits=LIMITS[profile(f)];
    for(const k of DOTS)f[k]=clamp(safe(f[k]),0,limits[k]);
    f.mark=clamp(safe(f.mark),0,12);
    f.weak=clamp(safe(f.weak),0,3);
    f.vuln=clamp(safe(f.vuln),0,3);
    f.stagger=clamp(safe(f.stagger),0,160);
    f.staggerLock=clamp(safe(f.staggerLock),0,3);
    f.ruptureLock=clamp(safe(f.ruptureLock),0,1);
    if(Number.isFinite(f.ward))f.ward=clamp(safe(f.ward),0,80);
    return f;
  }
  function threshold(f){return (f?.role==="boss"||f?.kind==="boss")?125:100}
  function breakdown(f){
    const p=clamp(safe(f?.poison),0,LIMITS[profile(f)].poison);
    const b=clamp(safe(f?.burn),0,LIMITS[profile(f)].burn);
    const l=clamp(safe(f?.bleed),0,LIMITS[profile(f)].bleed);
    const base=p+b*2+l;
    // Only the strongest synergy fires on one activation. This prevents
    // three-status builds from gaining exponential multi-proc damage.
    const variants=[
      p>=4&&b>=3?{label:"Воспламенение",extra:Math.min(6,Math.min(p,b))}:null,
      p>=3&&l>=4?{label:"Заражённая рана",extra:Math.min(4,Math.floor(l/2))}:null,
      b>=3&&l>=3?{label:"Прижигание",extra:2}:null
    ].filter(Boolean).sort((a,b)=>b.extra-a.extra);
    const combo=variants[0]||null;
    const raw=base+(combo?.extra||0);
    const maxPerTick=profile(f)==="pvp"?Math.min(28,Math.ceil(safe(f?.maxHp)*.16)):
      Math.min(42,Math.max(8,Math.ceil(safe(f?.maxHp)*.38)));
    const total=Math.min(raw,maxPerTick);
    const ward=Math.max(0,safe(f?.ward));
    return {poison:p,burn:b,bleed:l,base,combo,total,ward,
      hpDamage:Math.max(0,total-ward),limit:maxPerTick,
      nextPoison:Math.max(0,p-1),nextBurn:Math.max(0,b-1),nextBleed:Math.max(0,l-2)};
  }
  const oldHit=hit,oldEffect=ashEffect,oldApply=apply,oldTick=ashTick;
  function stagger(target,amount,source){
    if(!target||target.hp<=0||!Number.isFinite(amount)||amount<=0)return false;
    // In PvE only monsters can be stance-broken; a solo hero cannot lose a
    // whole action from an unavoidable hit. In PvP both players have answers.
    const enemy=Array.isArray(G.combat?.enemies)&&G.combat.enemies.includes(target);
    if(!enemy&&!inPvp(target))return false;
    capStacks(target);
    if(target.staggerLock>0)return false;
    target.stagger=clamp(target.stagger+Math.min(45,Math.round(amount)),0,160);
    const bar=threshold(target);
    if(target.stagger<bar)return false;
    target.stagger=0;
    target.staggerLock=2;
    target.vuln=Math.min(3,(target.vuln||0)+1);
    if(inPvp(target)){
      target.gauge=Math.min(18000,safe(target.gauge)+700);
      if(G.duel?.log)G.duel.log.push(target.name+": стойкость сломана — задержка AV, +1 уязвимость");
    }else{
      target.stun=Math.max(1,safe(target.stun));
      if(typeof logLine==="function")logLine(target.name+": пробита стойкость — потеряет действие");
    }
    return true;
  }
  hit=window.hit=function(a,b,n){
    if(!b)return oldHit(a,b,n);
    capStacks(b);
    const rupture=b.bleed>=4&&b.ruptureLock===0?Math.min(4,Math.floor(b.bleed/3)):0;
    const dealt=oldHit(a,b,n+rupture);
    if(dealt>0){
      if(rupture){b.bleed=Math.max(0,b.bleed-1);b.ruptureLock=1;}
      stagger(b,Math.max(4,Math.min(27,Math.floor(dealt*1.25))),a);
    }
    return dealt;
  };
  function tick(f,attacker){
    if(!f||f.hp<=0)return 0;
    capStacks(f);
    const info=breakdown(f);
    const hpBefore=f.hp;
    if(info.total){
      // PvP ashTrueDamage already consumes ward and respects its rules;
      // in PvE we apply the same ward interception here.
      const isDuel=inPvp(f);
      if(!isDuel&&f.ward>0){
        const absorb=Math.min(info.total,f.ward);
        f.ward-=absorb;
        ashTrueDamage(f,info.total-absorb);
      }else ashTrueDamage(f,info.total);
    }
    const dealt=hpBefore-f.hp;
    if(dealt>0&&attacker)ashHook("dot",attacker,f,{damage:dealt});
    f.poison=Math.max(0,f.poison-1);
    f.burn=Math.max(0,f.burn-1);
    f.bleed=Math.max(0,f.bleed-2);
    f.mark=Math.max(0,f.mark-1);
    f.ruptureLock=0;
    f.staggerLock=Math.max(0,f.staggerLock-1);
    if(info.combo&&typeof logLine==="function"&&G.screen==="fight")
      logLine(f.name+": "+info.combo.label+" (+"+info.combo.extra+" ДоТ)");
    if(f.hp<=0&&attacker&&!ash.killSet.has(f)){
      ash.killSet.add(f);ashHook("kill",attacker,f,{source:"dot"});
    }
    return dealt;
  }
  ashTick=window.ashTick=tick;
  ASH_DEBUG.ashTick=tick;
  // Keep ashDot's public debugger consistent with the new projection; the
  // old global const is intentionally unchanged for archived callers.
  ASH_DEBUG.ashDot=f=>breakdown(f).total;
  ashEffect=window.ashEffect=function(effect,a,b,card,pvp){
    const t=b?.hp>0?b:null;
    if(effect.key==="stagger"){
      if(t)stagger(t,effect.value+(card?.up?8:0),a);
      return 0;
    }
    if(effect.key==="detonate"){
      const v=safe(effect.value);
      const foes=(v>=3&&Array.isArray(G.combat?.enemies)&&G.combat.player===a)?
        G.combat.enemies.filter(x=>x.hp>0):[t].filter(Boolean);
      let total=0;
      for(const f of foes){
        capStacks(f);
        const info=breakdown(f);
        // Explosion is one strategic nuke, not an uncapped 100% HP delete.
        // It consumes all three DoTs, so timing matters.
        const limit=inPvp(f)?Math.max(9,Math.ceil(f.maxHp*.22)):
          Math.max(12,Math.min(64,Math.ceil(f.maxHp*.55)));
        const raw=Math.min(limit,info.total*Math.max(1,v));
        const before=f.hp;
        ashTrueDamage(f,raw);
        total+=before-f.hp;
        f.poison=0;f.burn=0;f.bleed=0;f.ruptureLock=0;
        if(raw>0)ashHook("detonate",a,f,{damage:before-f.hp});
        if(f.hp<=0&&!ash.killSet.has(f)){ash.killSet.add(f);ashHook("kill",a,f,{source:"detonate"})}
      }
      return total;
    }
    const damage=oldEffect(effect,a,b,card,pvp);
    capStacks(a);if(t)capStacks(t);
    // AoE may affect opponents other than the selected primary target.
    if(Array.isArray(G.combat?.enemies))G.combat.enemies.forEach(capStacks);
    return damage;
  };
  apply=window.apply=function(card,a,b){
    const dealt=oldApply(card,a,b);
    capStacks(a);capStacks(b);
    if(Array.isArray(G.combat?.enemies))G.combat.enemies.forEach(capStacks);
    return dealt;
  };
  function explain(f){
    const s=breakdown(f);
    const fields=[
      s.poison?"☠ Яд "+s.poison+": "+s.poison+" урона, −1 заряд/ход":null,
      s.burn?"🔥 Горение "+s.burn+": "+(s.burn*2)+" урона, −1 заряд/ход":null,
      s.bleed?"🩸 Кровь "+s.bleed+": "+s.bleed+" урона, −2 заряда/ход":null,
      s.combo?"✦ "+s.combo.label+": +"+s.combo.extra+" урона":null,
      f?.mark?"🎯 Метка "+f.mark+": усиление атак, −1/ход":null,
      f?.stagger>=0?"⚒ Стойкость "+safe(f.stagger)+"/"+threshold(f):null
    ].filter(Boolean);
    return fields.join(" · ")||"Нет отрицательных статусов";
  }
  window.ASH_STATUSES={LIMITS,capStacks,breakdown,stagger,threshold,explain,tick};
})();
