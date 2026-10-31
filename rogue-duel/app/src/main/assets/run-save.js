"use strict";
/* v0.7 offline-only run checkpoints. Never persist short room codes, keys,
   PvP snapshots or other players' data. Full battle state can be resumed. */
(function(){
 const KEY="ashes_duel_solo_v07",VERSION=7;
 let snapshot=null;
 function storage(){try{return typeof localStorage!=="undefined"?localStorage:null}catch(e){return null}}
 function read(){
  try{
   const raw=storage()?.getItem(KEY);if(!raw)return null;
   const doc=JSON.parse(raw);
   if(doc.version!==VERSION||!doc.state||!doc.state.runner||!Array.isArray(doc.state.map))return null;
   if(!["map","fight","reward","event","shop","treasure","rest","upgrade","wait"].includes(doc.state.screen))return null;
   return doc
  }catch(e){return null}
 }
 function clear(){try{storage()?.removeItem(KEY)}catch(e){} snapshot=null}
 function capture(){
  if(!G.demo||!G.runner||!G.routeTree)return false;
  if(["done","lost","duelEnd"].includes(G.screen)){clear();return false}
  const fields=["runner","hero","stage","map","routeTree","screen","combat","encounter","shop","reward","log","done","rngState"];
  const state={};
  for(const key of fields)state[key]=G[key];
  const doc={version:VERSION,at:Date.now(),state};
  try{storage()?.setItem(KEY,JSON.stringify(doc));snapshot=doc;return true}catch(e){return false}
 }
 function restore(){
  const doc=read();if(!doc)return false;
  for(const [key,value]of Object.entries(doc.state))G[key]=value;
  G.demo=true;G.role="host";G.code="";G.topic="";G.key=null;G.connected=false;G.duel=null;G.other=null;
  if(!G.runner.potions)G.runner.potions=[];
  if(G.combat?.player){ashExtra(G.combat.player);ash.killSet=new WeakSet();for(const e of G.combat.enemies||[])ashExtra(e)}
  if(G.screen==="fight"&&!G.combat)G.screen="map";
  render();return true
 }
 const oldDispatch=dispatch;
 dispatch=window.dispatch=async function(action,x){
  if(action==="resumeSolo"){if(!restore()){clear();render()}return}
  if(action==="eraseSolo"){clear();render();return}
  if(action==="demo"){clear()}
  const answer=await oldDispatch(action,x);
  capture();
  return answer
 };
 const oldRender=render;
 render=window.render=function(){
  oldRender();
  if(G.screen==="lobby"){
   const existing=read();
   if(existing&&document.getElementById("app")){
    let hour=new Date(existing.at).toLocaleString("ru-RU",{day:"numeric",month:"short",hour:"2-digit",minute:"2-digit"});
    document.getElementById("app").insertAdjacentHTML("beforeend",
      '<div class="etch-panel solo-save"><div class="eyebrow">СОХРАНЁННЫЙ ЗАБЕГ</div><p class="sub">Последнее прохождение: '+hour+' · '+(existing.state.stage+1)+' этаж.</p>'+
      '<div class="etch-actions"><button class="etch-btn cool" data-a="resumeSolo">Продолжить одиночный забег</button>'+
      '<button class="etch-btn plain" data-a="eraseSolo">Удалить сохранение</button></div></div>');
   }
  }
 };
 if(typeof document!=="undefined"&&document.addEventListener){
  document.addEventListener("visibilitychange",()=>{if(document.visibilityState==="hidden")capture()});
 }
 if(typeof window!=="undefined"&&typeof window.addEventListener==="function")window.addEventListener("pagehide",capture);
 window.ASH_SAVES={KEY,VERSION,read,capture,restore,clear};
 render();
})();