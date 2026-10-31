"use strict";
const fs=require("fs"),vm=require("vm"),assert=require("assert"),crypto=require("crypto");
const root="app/src/main/assets/";
const main=fs.readFileSync(root+"index.html","utf8").match(/<script>([\s\S]*?)<\/script>/)[1];
const els={app:{innerHTML:"",insertAdjacentHTML(){},classList:{add(){},remove(){}}},net:{textContent:""},codeInput:{value:"ABCDEFGHJKLMNPQRSTUV"}};
const events={};
const doc={
 getElementById:id=>els[id]||(els[id]={innerHTML:"",textContent:""}),
 querySelector(){return null},
 addEventListener(t,handler){(events[t]||(events[t]=[])).push(handler)},
 head:{appendChild(){}},
 createElement(type){
  if(type==="canvas")return {getContext:()=>({fillRect(){},clearRect(){},beginPath(){},ellipse(){},fill(){},moveTo(){},lineTo(){},stroke(){}}),toDataURL:()=>"data:image/png;base64,aGVsbG8="};
  return {textContent:"",classList:{add(){},remove(){}}}
 }
};
const broadcasts=[];
const sandbox={document:doc,console,crypto:crypto.webcrypto,TextEncoder,TextDecoder,Math,URL,Date,alert(x){throw Error(String(x))},location:{reload(){}},setInterval(){return 1},clearInterval(){},setTimeout(){return 1},clearTimeout(){},btoa,atob,
 AndroidNet:{listen(topic){broadcasts.push({listen:topic})},post(topic,text){broadcasts.push({post:topic})}}};
sandbox.window=sandbox;
const context=vm.createContext(sandbox);
const scripts=[main,"expansion-data.js","expansion-relics.js","expansion-combat.js","expansion-visual.js","pvp-balance.js","route-tree.js","atelier.js"];
for(const [i,s] of scripts.entries())vm.runInContext(i===0?s:fs.readFileSync(root+s,"utf8"),context,{timeout:4000,filename:i===0?"inline.js":s});
const run=s=>vm.runInContext(s,context,{timeout:5000});
async function check(){
 const result=run("(function(){let tested=0,edges=0;for(let seed=1;seed<=180;seed++){let tree=ASH_TREE.createTree(seed);ASH_TREE.validate(tree);tested++;edges+=tree.edges.length;for(let row=0;row<tree.floors.length-1;row++)for(let item of tree.floors[row])if(!tree.edges.some(e=>e[0]===row&&e[1]===item.index))throw Error('Stranded node '+item.id)}return {tested,edges}})()");
 assert.strictEqual(result.tested,180);
 await run("dispatch('join','')");
 assert.strictEqual(run("G.role"),"guest");
 assert.strictEqual(run("G.screen"),"hero");
 assert(broadcasts.some(e=>e.listen),"guest must listen to room transport");
 await run("dispatch('hero','oracle')");
 assert.strictEqual(run("G.screen"),"map");
 assert.strictEqual(run("G.routeTree.floors.length"),15);
 let html=els.app.innerHTML;
 assert(html.includes("etch-tree-links"),"SVG connections not rendered");
 assert(html.includes("etch-next-choices"),"guest has no always-visible accessible buttons");
 assert(html.includes('data-a="node" data-x="0"'),"guest starting route not clickable");
 assert(html.includes('data-map-stage="0"'),"first stage missing");
 let guestMap=run("G.map.map(x=>x.join(':')).join('|')");
 await run("dispatch('node','0')");
 console.log("GUEST ROUTE DIAG",JSON.stringify(run("({role:G.role,stage:G.stage,screen:G.screen,warning:G.lastRouteWarning,position:G.routeTree.position,reachable:ASH_TREE.reachable(0),first:G.map[0],visited:G.routeTree.visited})")));
 assert.strictEqual(run("G.screen"),"fight","guest cannot enter combat");
 assert.strictEqual(run("G.routeTree.visited.length"),1);
 assert.strictEqual(run("G.routeTree.position.index"),0);
 for(let f=1;f<15;f++){
   run("nextStage()");
   assert.strictEqual(run("G.stage"),f,"stage advancement");
   assert.strictEqual(run("G.screen"),"map");
   const valid=run("ASH_TREE.reachable(G.stage)");
   assert(valid.length>0,"guest stranded on floor "+f);
   assert(els.app.innerHTML.includes('data-a="node"'),"next floor has no clickable node");
   await run("dispatch('node','"+valid[0]+"')");
   assert.notStrictEqual(run("G.screen"),"map","guest click did not enter encounter "+f);
 }
 run("nextStage()");
 assert.strictEqual(run("G.screen"),"wait","guest should reach PvP gate");
 assert.strictEqual(run("G.done"),true);
 run("G.role='host';G.demo=false;G.done=false;G.runner=newRun('guard');makeMap();G.screen='map';render()");
 assert.strictEqual(run("G.routeTree.floors.length"),15);
 assert.notStrictEqual(run("G.map.map(x=>x.join(':')).join('|')"),guestMap,"routes must differ");
 await run("dispatch('node','1')");
 assert.notStrictEqual(run("G.screen"),"map","host map selection broken");
 console.log("PASS route-tree: 180 DAGs with "+result.edges+" edges; guest join, class, 15 levels, final gate, independent host route.");
}
check().catch(e=>{console.error(e);process.exitCode=1});
