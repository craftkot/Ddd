"use strict";
/* Room Connection HUD v0.6: real end-to-end ping/pong over existing encrypted
   WebRTC/relay channel. Neither P2P ICE probing nor ntfy availability is confused
   with successful contact with the other phone. No invented latency numbers. */
(function(){
 const net={peerSeen:0,hasPeer:false,samples:[],rtt:null,via:null,reason:"",
   pings:new Map(),lastPing:0,expanded:true,copyNotice:"",timer:0,updates:0};
 const ROOM_ALPHABET="ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
 const safe=s=>String(s).replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&lt;",'"':"&quot;","'":"&#39;"}[c]));
 const statusAge=now=>net.peerSeen?Math.floor((now-net.peerSeen)/1000):null;
 const isLive=()=>net.hasPeer&&Date.now()-net.peerSeen<24000;
 function transport(){
   if(net.via==="direct"&&channel?.readyState==="open")return "Прямое P2P";
   if(net.via==="relay")return "Интернет-релей";
   if(channel?.readyState==="open")return "P2P · проверяем";
   return "Релей · проверяем";
 }
 function state(){
   if(G.demo)return {self:"Локально",other:"Не требуется",connected:false,ping:null,transport:"Без сети"};
   if(!G.code)return {self:"Без комнаты",other:"Не подключён",connected:false,ping:null,transport:"—"};
   const live=isLive();
   const age=statusAge(Date.now());
   return {self:"В комнате",other:live?"В сети":net.hasPeer?"Связь потеряна":"Ожидаем игрока",
     connected:live,ping:live?net.rtt:null,age,transport:transport(),
     selfRole:G.role==="host"?"Хозяин":"Гость",otherRole:G.role==="host"?"Гость":"Хозяин",
     code:G.code};
 }
 function redraw(){
   const el=document.getElementById("roomPanel");if(!el)return;
   if(!G.code||G.demo){el.innerHTML="";el.hidden=true;return}
   el.hidden=false;
   const s=state(),rtt=s.ping===null?"—":Math.round(s.ping)+" мс";
   const latency=s.ping===null?"unknown":s.ping<180?"good":s.ping<450?"medium":"bad";
   const age=s.age===null?"не было ответа":s.age<2?"только что":s.age+" с назад";
   el.innerHTML='<div class="room-header">'+
     '<div class="room-code-wrap"><small>КОМНАТА</small><strong class="room-code">'+safe(G.code)+'</strong></div>'+
     '<button type="button" class="room-control copy" data-a="copyRoom">⧉ Копировать</button>'+
     '<button type="button" class="room-control expand" data-a="netToggle" aria-expanded="'+net.expanded+'">'+(net.expanded?"Свернуть ▴":"Соединение ▾")+'</button></div>'+
     (net.expanded?'<div class="room-details"><div class="room-player-grid">'+
     '<div class="room-player online"><span class="room-dot"></span><span><small>'+s.selfRole+'</small><strong>Ты · '+s.self+'</strong></span></div>'+
     '<div class="room-player '+(s.connected?"online":"offline")+'"><span class="room-dot"></span><span><small>'+s.otherRole+'</small><strong>'+s.other+'</strong></span></div></div>'+
     '<div class="room-metrics"><div>Задержка <strong class="latency-'+latency+'">'+rtt+'</strong></div>'+
     '<div>Маршрут <strong>'+s.transport+'</strong></div></div>'+
     '<p class="room-last">Последнее подтверждение от игрока: '+age+'</p>'+
     '<div class="room-footer"><button class="room-control" data-a="netRetry">↻ Проверить связь</button>'+
     '<span>'+safe(net.copyNotice||"Пинг измеряется обменом сообщениями, а не сигналом мобильной сети.")+'</span></div></div>':"");
   const small=document.getElementById("net");
   if(small)small.textContent=(s.connected?"● ":"◌ ")+s.other+(s.connected&&s.ping!==null?" · "+Math.round(s.ping)+" мс":"");
 }
 function markPeer(m){
   net.hasPeer=true;net.peerSeen=Date.now();
   if(m._via==="relay"||m._via==="direct")net.via=m._via;
   G.connected=true;
 }
 function nonce(){
   const bytes=new Uint8Array(8);crypto.getRandomValues(bytes);
   return Array.from(bytes,b=>b.toString(16).padStart(2,"0")).join("")
 }
 function tick(){
   if(!G.code||!G.key||G.demo)return;
   const now=Date.now();
   for(const [key,value] of net.pings)if(now-value>40000)net.pings.delete(key);
   if(!isLive())G.connected=false;
   if(now-net.lastPing>=4800&&net.pings.size<3){
     const key=nonce();
     net.pings.set(key,now);net.lastPing=now;
     send({type:"ping",token:key}).catch(()=>{net.pings.delete(key)});
   }
   if(!isLive()&&now-(net.lastHandshake||0)>=8500){
     net.lastHandshake=now;
     send({type:G.role==="host"?"hello":"join"}).catch(()=>{});
   }
   redraw();
 }
 async function copyCode(){
   if(!G.code)return false;
   let ok=false;
   try{if(typeof AndroidNet!=="undefined"&&typeof AndroidNet.copyText==="function")ok=AndroidNet.copyText(G.code)===true}catch(e){}
   if(!ok){
     try{if(typeof navigator!=="undefined"&&navigator.clipboard?.writeText){await navigator.clipboard.writeText(G.code);ok=true}}catch(e){}
   }
   net.copyNotice=ok?"Код "+G.code+" скопирован":'Код '+G.code+' — выдели его для отправки';
   redraw();
   return ok
 }
 async function retry(){
   net.lastPing=0;net.lastHandshake=0;net.copyNotice="Запрос связи отправлен";
   await send({type:G.role==="host"?"hello":"join"});
   tick()
 }
 // Message metadata is assigned by the receive() path only after successful
 // decryption, validation of the protocol and deduplication.
 const oldHandleMessage=handleMessage;
 handleMessage=window.handleMessage=function(m){
   if(m.type==="ping"){
     markPeer(m);
     send({type:"pong",token:m.token}).catch(()=>{});
     redraw();return
   }
   if(m.type==="pong"){
     const start=net.pings.get(m.token);
     if(start!==undefined){
       const elapsed=Date.now()-start;
       net.pings.delete(m.token);
       if(elapsed>=0&&elapsed<=40000){
         net.samples.push(elapsed);
         if(net.samples.length>5)net.samples.shift();
         let ordered=[...net.samples].sort((a,b)=>a-b);
         net.rtt=ordered[Math.floor(ordered.length/2)];
       }
     }
     markPeer(m);redraw();return
   }
   markPeer(m);
   oldHandleMessage(m);redraw()
 };
 const oldSetRoom=setRoom;
 setRoom=window.setRoom=async function(code,role){
   net.hasPeer=false;net.peerSeen=0;net.rtt=null;net.samples=[];net.pings.clear();net.via=null;
   net.copyNotice="";net.expanded=true;net.lastPing=0;net.lastHandshake=Date.now();
   await oldSetRoom(code,role);
   if(!net.timer)net.timer=setInterval(tick,2000);
   if(role==="host")await copyCode();
   tick();redraw()
 };
 const oldSetNet=setNet;
 setNet=window.setNet=function(){oldSetNet();redraw()};
 const oldDispatch=dispatch;
 dispatch=window.dispatch=async function(action,x){
   if(action==="copyRoom"){await copyCode();return}
   if(action==="netRetry"){await retry();return}
   if(action==="netToggle"){net.expanded=!net.expanded;redraw();return}
   return oldDispatch(action,x)
 };
 window.ASH_NET={state,redraw,markPeer,tick,copyCode,retry,nonce,ROOM_ALPHABET,net};
 redraw();
})();