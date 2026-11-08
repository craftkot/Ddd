"use strict";
/* ASHES v1.0 — deterministic Action Value sequencing (PvE) and five shared
   Skill Points. The battle has one playable deck, so a card is one action.
   Inspired by public turn-order conventions; not a copy of HSR art or code.
   Existing PvP AV, counter phases, and saved run mechanics remain compatible. */
(function(){
  const BASE_GAUGE=10000,MAX_SP=5,START_SP=3,MAX_ULT=100;
  const chargeOf=c=>bounded(Number(c?.av?.ultCharge)||0,0,MAX_ULT);
  const gainCharge=(c,delta)=>{
    if(!c?.av)return 0;
    c.av.ultCharge=bounded(chargeOf(c)+Math.max(0,Number(delta)||0),0,MAX_ULT);
    return c.av.ultCharge;
  };
  const oldCreate=createFight,oldPlay=playSolo,oldEnd=soloEnd,oldDispatch=dispatch;
  const bounded=(v,min,max)=>Math.max(min,Math.min(max,v));
  function living(c){return c.enemies?.some(e=>e.hp>0)&&c.player?.hp>0}
  function valid(c){
    return !!(c?.av?.actors && c.enemies && c.av.actors.length===c.enemies.length+1 &&
      c.av.actors.every((a,i)=>a.id==="hero"||a.id==="foe"+(i-1)))
  }
  function initialize(c){
    const hero=c.player;
    const count=c.enemies.length;
    const speed={guard:156,oracle:172,hunter:184};
    c.av={
      clock:0,sp:bounded(hero.energy||START_SP,0,MAX_SP),maxSp:MAX_SP,
      ultCharge:0,ultMax:MAX_ULT,
      current:null,heroActions:0,actions:0,
      actors:[{id:"hero",speed:speed[G.runner?.hero]||165,gauge:BASE_GAUGE},
        ...c.enemies.map((e,i)=>({
          id:"foe"+i,speed:bounded((e.role==="caster"?96:e.role==="boss"?92:e.role==="guard"?79:88)+((i*7)%14),70,118),
          gauge:BASE_GAUGE
        }))]
    };
    // One deck hero faces up to four foes. Distribute incoming burst
    // without erasing individual enemy telegraphs or role identity.
    const multiplier=count>=4?.43:count===3?.54:count===2?.72:1;
    for(const e of c.enemies){
      e.attack=Math.max(2,Math.round(e.attack*multiplier));
      ASH_ENCOUNTERS.announce(e);
    }
    hero.energy=c.av.sp;
    advance(c);
  }
  function nextReady(c){
    const s=c.av;
    return s.actors.filter(a=>a.id==="hero"?c.player.hp>0:c.enemies[Number(a.id.slice(3))]?.hp>0)
      .sort((a,b)=>{
        const delta=a.gauge/a.speed-b.gauge/b.speed;
        return Math.abs(delta)>.000001?delta:(a.id==="hero"?-1:b.id==="hero"?1:a.id.localeCompare(b.id));
      })[0];
  }
  function tickEnemy(c,a){
    const i=Number(a.id.slice(3)),e=c.enemies[i],p=c.player;
    if(!e||e.hp<=0)return;
    const hpBefore=p.hp;
    ASH_ENCOUNTERS.execute(e,p,c);
    if(p.hp<hpBefore)gainCharge(c,Math.min(18,5+Math.ceil((hpBefore-p.hp)/2)));
    const damage=ASH_DEBUG.ashTick(e,p);
    if(damage)logLine(e.name+": периодический урон "+damage);
    if(e.hp<=0&&!ash.killSet.has(e)){ash.killSet.add(e);ashHook("kill",p,e,{})}
    e.intent++;ASH_ENCOUNTERS.announce(e);
  }
  function advance(c){
    const s=c.av;
    s.current=null;
    // A short loop prevents a large batch of simultaneous enemies from
    // locking up WebView. No timers: all transitions are deterministic.
    for(let guard=0;guard<64;guard++){
      if(!living(c))return;
      const a=nextReady(c);if(!a)return;
      const wait=Math.max(0,a.gauge/a.speed);
      for(const other of s.actors)other.gauge=Math.max(0,other.gauge-wait*other.speed);
      s.clock=Number((s.clock+wait).toFixed(3));
      a.gauge=0;
      s.current=a.id;
      if(a.id==="hero"){
        c.player.energy=s.sp;
        if(c.enemies[c.target]?.hp<=0)c.target=Math.max(0,c.enemies.findIndex(e=>e.hp>0));
        return;
      }
      tickEnemy(c,a);
      s.actions++;
      a.gauge=BASE_GAUGE;
      s.current=null;
      if(c.player.hp<=0){die();return}
      if(c.enemies.every(e=>e.hp<=0)){winFight();return}
    }
    throw Error("Action Value queue exceeded 64 transitions");
  }
  function finishHeroAction(c){
    const s=c.av,p=c.player;
    s.heroActions++;s.actions++;
    // Effects resolve at the END of this hero action; block carries over
    // so a defensive card can protect against the next enemy attack.
    const dot=ASH_DEBUG.ashTick(p,null);
    if(dot)logLine("Статусы героя: -"+dot+" здоровья");
    p.weak=Math.max(0,p.weak-1);p.vuln=Math.max(0,p.vuln-1);
    if(s.heroActions%3===0){
      p.block=Math.ceil(p.block*.75);
      if(p.regen)ashHeal(p,p.regen);
      ashHook("turn",p,null,{});
    }
    p.hooks={};
    p.rounds=(p.rounds||0)+1;
    draw(p,1);
    if(p.hp<=0){die();return}
    if(c.enemies.every(e=>e.hp<=0)){winFight();return}
    const actor=s.actors[0];actor.gauge=BASE_GAUGE;
    s.current=null;
    advance(c);
    if(G.combat===c&&G.screen==="fight")render();
  }
  function basicAttack(){
    const c=G.combat;if(!valid(c)||c.av.current!=="hero"||!living(c))return false;
    const target=c.enemies[c.target]?.hp>0?c.enemies[c.target]:c.enemies.find(e=>e.hp>0);
    if(!target)return false;
    const dmg=hit(c.player,target,Math.max(4,6+Math.floor(G.stage/4)));
    gainCharge(c,20);
    c.av.sp=bounded(c.av.sp+1,0,MAX_SP);
    c.player.energy=c.av.sp;
    logLine("Базовая атака: "+dmg+" урона · ОН "+c.av.sp+"/"+MAX_SP);
    finishHeroAction(c);
    return true;
  }
  function useCard(index){
    const c=G.combat;if(!valid(c)||c.av.current!=="hero"||!living(c))return false;
    const p=c.player,card=p.hand[index];
    if(!card||!art(card.id)||art(card.id).t==="reaction")return false;
    const cost=art(card.id).c;
    if(!Number.isFinite(cost)||cost<0||cost>c.av.sp)return false;
    const aliveBefore=c.enemies.filter(x=>x.hp>0).length;
    const e=c.enemies[c.target]?.hp>0?c.enemies[c.target]:c.enemies.find(x=>x.hp>0);
    if(!e)return false;
    p.hand.splice(index,1);
    c.av.sp-=cost;p.energy=c.av.sp;
    if(art(card.id).t==="power")(p.exhaust||(p.exhaust=[])).push(card);
    else p.discard.push(card);
    logLine("Ты: "+apply(card,p,e));
    gainCharge(c,12+(art(card.id).c>=2?4:0));
    const killed=Math.max(0,aliveBefore-c.enemies.filter(x=>x.hp>0).length);
    if(killed)gainCharge(c,killed*15);
    // Some original card/relic effects generate energy: map it into shared SP.
    c.av.sp=bounded(p.energy,0,MAX_SP);p.energy=c.av.sp;
    if(c.enemies[c.target]?.hp<=0)c.target=Math.max(0,c.enemies.findIndex(x=>x.hp>0));
    finishHeroAction(c);
    return true;
  }
  function ultimate(){
    const c=G.combat;
    if(!valid(c)||c.av.current!=="hero"||!living(c)||chargeOf(c)<MAX_ULT)return false;
    c.av.ultCharge=0;
    let total=0,kills=0;
    for(const foe of c.enemies){
      if(foe.hp<=0)continue;
      const wasAlive=foe.hp>0;
      total+=hit(c.player,foe,18+Math.floor(G.stage/3));
      if(wasAlive&&foe.hp<=0)kills++;
    }
    // Ultimate is an action, not a free extra turn. SP remains unchanged.
    c.player.block=(c.player.block||0)+8;
    logLine("Пепельный разлом: "+total+" урона по врагам, +8 брони, "+kills+" добито");
    finishHeroAction(c);
    return true;
  }
  function preview(c,n=7){
    if(!valid(c))return [];
    const livingActors=c.av.actors.filter(a=>a.id==="hero"?c.player.hp>0:c.enemies[Number(a.id.slice(3))]?.hp>0);
    const actors=livingActors.map(a=>({...a}));
    const result=[];
    if(c.av.current==="hero"){
      result.push({id:"hero",name:"ТЫ",av:0,kind:"hero",active:true});
      actors.find(a=>a.id==="hero").gauge=BASE_GAUGE;
    }
    for(let k=result.length;k<n;k++){
      const next=actors.slice().sort((a,b)=>a.gauge/a.speed-b.gauge/b.speed||(a.id==="hero"?-1:b.id==="hero"?1:a.id.localeCompare(b.id)))[0];
      if(!next)break;
      const wait=Math.max(0,next.gauge/next.speed);
      for(const a of actors)a.gauge=Math.max(0,a.gauge-wait*a.speed);
      const idx=next.id.startsWith("foe")?Number(next.id.slice(3)):-1;
      result.push({id:next.id,name:idx<0?"ТЫ":c.enemies[idx].name,av:Math.ceil(wait),kind:idx<0?"hero":"enemy",active:false});
      next.gauge=BASE_GAUGE;
    }
    return result;
  }
  createFight=window.createFight=function(elite){
    oldCreate(elite);
    if(G.combat?.enemies){initialize(G.combat);if(G.screen==="fight")render()}
  };
  playSolo=window.playSolo=function(i){
    if(!valid(G.combat))return oldPlay(i);
    return useCard(i);
  };
  soloEnd=window.soloEnd=function(){
    if(!valid(G.combat))return oldEnd();
    return basicAttack();
  };
  dispatch=window.dispatch=async function(action,x){
    if(action==="duelBasic"&&G.screen==="duel"){commandDuel({action:"basic"});return}
    if(action==="ultimate"&&G.screen==="fight"){ultimate();return}
    if(action==="duelUltimate"&&G.screen==="duel"){commandDuel({action:"ult",revision:G.duel?.revision});return}
    if(action==="basicAttack"&&G.screen==="fight"){basicAttack();return}
    if(action==="endTurn"&&G.screen==="fight"&&valid(G.combat)){basicAttack();return}
    await oldDispatch(action,x);
    if(action==="usePotion"&&G.screen==="fight"&&valid(G.combat)){
      G.combat.av.sp=bounded(G.combat.player.energy,0,MAX_SP);
      G.combat.player.energy=G.combat.av.sp;render()
    }
  };
  // The duel already has its own speed-based Action Value and counterplay.
  // Make the same five-point resource persist across its normal turns.
  const oldPvMake=pvMakeFighter,oldPvNext=pvNext,oldPvAction=pvAction;
  pvMakeFighter=function(build){
    const f=oldPvMake(build);f.sp=START_SP;f.maxSp=MAX_SP;f.ultCharge=0;return f
  };
  pvNext=function(d){
    const taxes={host:d.fighters.host.nextTax||0,guest:d.fighters.guest.nextTax||0};
    const result=oldPvNext(d);
    if(d.turn&&d.fighters[d.turn]){
      const f=d.fighters[d.turn];
      f.sp=bounded(f.sp??START_SP,0,MAX_SP);
      f.energy=bounded(f.sp-(taxes[d.turn]||0),0,MAX_SP);
    }
    return result;
  };
  pvAction=function(cmd,by){
    const d=G.duel;if(!d?.fighters||!cmd)return oldPvAction(cmd,by);
    const f=d.fighters[by],e=d.fighters[by==="host"?"guest":"host"];
    if(!f)return oldPvAction(cmd,by);
    const pvpCharge=(x,n)=>{if(x)x.ultCharge=bounded((x.ultCharge||0)+n,0,MAX_ULT)};
    if(cmd.action==="ult"){
      if(d.turn!==by||d.phase!=="play"||d.winner||f.ultCharge<MAX_ULT||
          (Number.isInteger(cmd.revision)&&cmd.revision!==d.revision))return;
      f.ultCharge=0;
      const damage=hit(f,e,22);
      f.block=(f.block||0)+7;
      d.log.push(f.name+": Пепельный разлом — "+damage+" урона, +7 брони");
      return oldPvAction({...cmd,action:"end"},by);
    }
    if(cmd.action==="basic"){
      if(d.turn!==by||d.phase!=="play"||d.winner||Number.isInteger(cmd.revision)&&cmd.revision!==d.revision)return;
      hit(f,e,5);
      pvpCharge(f,20);
      pvpCharge(e,Math.min(18,5+Math.ceil(5/2)));
      f.sp=bounded((f.sp??START_SP)+1,0,MAX_SP);
      f.energy=f.sp;
      d.log.push(f.name+": базовая атака · +1 ОН");
      return oldPvAction({...cmd,action:"end"},by)
    }
    if(cmd.action==="play"&&d.turn===by&&d.phase==="play"){
      f.sp=bounded(f.sp??START_SP,0,MAX_SP);
      f.energy=f.sp;
    }
    const hpBeforeF=f.hp,hpBeforeE=e.hp;
    const revBefore=d.revision;
    const result=oldPvAction(cmd,by);
    if(d.revision!==revBefore){
      if(cmd.action==="play")pvpCharge(f,12);
      if(f.hp<hpBeforeF)pvpCharge(f,Math.min(18,5+Math.ceil((hpBeforeF-f.hp)/2)));
      if(e.hp<hpBeforeE)pvpCharge(e,Math.min(18,5+Math.ceil((hpBeforeE-e.hp)/2)));
    }
    if(cmd.action==="play"&&d.revision!==revBefore){
      f.sp=bounded(f.energy,0,MAX_SP);
      f.energy=f.sp;
    }
    // End is a voluntary pass, not a free SP generator; use Basic Attack.
    return result;
  };
  window.ASH_AV={BASE_GAUGE,MAX_SP,START_SP,MAX_ULT,valid,preview,advance,basicAttack,useCard,ultimate,gainCharge,chargeOf,initialize};
})();
