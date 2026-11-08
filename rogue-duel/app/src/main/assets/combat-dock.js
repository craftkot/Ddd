"use strict";
/* Ashes 0.13 — one-screen combat UX.
 * Turns the existing, fully accessible battle DOM into a fixed battle +
 * action dock. No card/gameplay data are duplicated or replaced.
 * Target chips relay the same event as tapping a real enemy.
 */
(function(){
  function E(tag,cls,text){
    const el=document.createElement(tag);
    if(cls)el.className=cls;
    if(text!==undefined)el.textContent=text;
    return el;
  }
  function combatMode(){
    return G.screen==="fight"||(G.screen==="duel"&&G.duel?.phase==="play");
  }
  function labelOf(e){
    return e?.name?.replace(/\s+/g," ").trim()||"Цель";
  }
  function targets(strip){
    if(!strip||G.screen!=="fight")return;
    const foes=G.combat?.enemies||[];
    const selected=window.ATELIER_TEST?.UI?.select;
    strip.innerHTML="";
    const hint=E("span","dock-target-title",selected?"КАРТА → ЦЕЛЬ":"ЦЕЛЬ");
    strip.appendChild(hint);
    for(let i=0;i<foes.length;i++){
      const f=foes[i],button=E("button","dock-target"+(G.combat.target===i?" active":"")+(f.hp<=0?" dead":""));
      button.type="button";button.dataset.combatTarget=String(i);button.disabled=f.hp<=0;
      const title=E("strong",null,(i+1)+". "+labelOf(f));
      const hp=E("small",null,Math.max(0,f.hp)+"/"+f.maxHp+" HP");
      button.append(title,hp);strip.appendChild(button);
    }
  }
  function energyTray(){
    const c=G.combat,active=G.screen==="fight"?c?.av:null;
    const duel=G.screen==="duel"?G.duel?.fighters?.[G.role]:null;
    const charge=active?Math.max(0,active.ultCharge||0):Math.max(0,duel?.ultCharge||0);
    const max=100,ready=G.screen==="fight"?active?.current==="hero":G.duel?.phase==="play"&&G.duel?.turn===G.role;
    const wrapper=E("div","dock-ult-wrap");
    const progress=E("div","dock-ult-meter");
    const fill=E("span","dock-ult-fill");
    fill.style.height=Math.min(100,charge)+"%";
    progress.appendChild(fill);
    const button=E("button","dock-ultimate"+(charge>=max?" charged":""));
    button.type="button";
    button.dataset.a=G.screen==="fight"?"ultimate":"duelUltimate";
    button.disabled=!(ready&&charge>=max);
    button.setAttribute("aria-label","Пепельный разлом: "+charge+" из 100 энергии");
    const info=E("span",null,"✦ УЛЬТА");
    const points=E("b",null,charge+"/100");
    button.append(info,points);
    wrapper.append(progress,button);
    return wrapper;
  }
  function move(parent,selector,from){
    const el=from.querySelector(selector);if(el)parent.appendChild(el);return el;
  }
  function organize(){
    if(!combatMode())return;
    const app=document.getElementById("app");
    if(!app||typeof app.querySelector!=="function"||typeof app.replaceChildren!=="function")return;
    const stage=app.querySelector(".fate-stage");
    const hand=app.querySelector(".etch-hand-shell");
    if(!stage||!hand)return;
    const view=E("section","combat-viewport");
    view.setAttribute("aria-label","Игровой экран боя");
    const top=E("div","combat-topbar");
    const arena=E("div","combat-arena");
    const dock=E("div","combat-dock");
    const order=E("div","dock-turn-order");
    const health=move(top,".etch-battle-hud",app);
    if(health)health.classList.add("dock-health");
    const av=move(order,".fate-av-bar",app)||move(order,".etch-timeline",app);
    if(av)top.appendChild(order);
    move(arena,".fate-stage",app);
    const topOfDock=E("div","dock-topline");
    const sp=move(topOfDock,".fate-sp-tray",app);
    if(sp)sp.classList.add("dock-sp");
    topOfDock.appendChild(energyTray());
    dock.appendChild(topOfDock);
    if(G.screen==="fight"){
      const strip=E("div","dock-target-strip");strip.id="dock-target-strip";
      dock.appendChild(strip);targets(strip);
    }
    const controls=move(dock,".fate-combat-controls",app)||move(dock,".etch-actions",app);
    if(controls)controls.classList.add("dock-actions");
    const handLabel=move(dock,".fate-hand-label",app);
    if(handLabel)handLabel.classList.add("dock-cards-title");
    const holder=E("div","dock-scroll");
    const resource=move(holder,".etch-resourcebar",app);
    if(resource)resource.classList.add("dock-hidden-metrics");
    holder.appendChild(hand);
    dock.appendChild(holder);
    const potion=move(topOfDock,".potion-hud",app);
    if(potion){
      const details=E("details","dock-potions");
      const summary=E("summary",null,"⚗");
      summary.title="Зелья и расходники";
      summary.setAttribute("aria-label","Зелья и расходники");
      details.append(summary,potion);topOfDock.appendChild(details);
    }
    // Nonessential combat log remains accessible, but never hides the arena.
    const log=move(dock,".logs",app);
    if(log){
      const details=E("details","dock-log");
      details.append(E("summary",null,"Последние события"),log);
      dock.appendChild(details);
    }
    view.append(top,arena,dock);
    app.replaceChildren(view);
    document.documentElement.classList.add("combat-fullscreen");
  }
  function syncTargets(){
    const strip=document.getElementById("dock-target-strip");
    if(strip)targets(strip);
  }
  document.addEventListener("click",e=>{
    const target=e.target.closest?.("button[data-combat-target]");
    if(!target||target.disabled)return;
    e.preventDefault();e.stopImmediatePropagation();
    const idx=target.dataset.combatTarget;
    const enemy=document.querySelector('.fate-stage .etch-person.enemy[data-foe-index="'+idx+'"]');
    if(enemy&&typeof enemy.click==="function")enemy.click();
  },true);
  // The atelier selection routine modifies state without re-render.
  document.addEventListener("pointerup",()=>{
    if(typeof requestAnimationFrame==="function")requestAnimationFrame(syncTargets);
  });
  const renderBefore=render;
  render=window.render=function(){
    // Clear the layout flag on screen changes before normal app rendering.
    if(!combatMode())document.documentElement?.classList?.remove?.("combat-fullscreen");
    renderBefore();
    organize();
  };
  window.ASH_COMBAT_DOCK={organize,syncTargets,energyTray,targets};
  render();
})();
