"use strict";
/* True branching route DAG for BOTH host and guest. Every node belongs to a
   floor and has explicit outgoing edges. Only connected future nodes are available.
   No map synchronization is required: each player has a distinct seeded route. */
(function(){
  const oldMakeMap=makeMap;
  const oldOpenNode=openNode;
  const TYPES=["fight","event","fight","shop","fight","rest","treasure","elite"];
  function seedOf(s){
    let h=2166136261;
    for(let i=0;i<s.length;i++){h^=s.charCodeAt(i);h=Math.imul(h,16777619)}
    return h>>>0
  }
  function randomGenerator(seed){
    let state=seed||0x9e3779b9;
    return function(){state^=state<<13;state^=state>>>17;state^=state<<5;return (state>>>0)/4294967296}
  }
  function nearest(floor,x){let idx=0,dist=Infinity;for(let i=0;i<floor.length;i++){const d=Math.abs(floor[i].x-x);if(d<dist){dist=d;idx=i}}return idx}
  function createTree(seed){
    const rand=randomGenerator(seed),floors=[],edges=[];
    for(let stage=0;stage<STAGES.length;stage++){
      const amount=stage===0?3:stage===STAGES.length-1?3:3+Math.floor(rand()*3);
      const pair=STAGES[stage]||["fight","event"];
      let floor=[];
      for(let j=0;j<amount;j++){
        let type=j<2?pair[j%pair.length]:TYPES[Math.floor(rand()*TYPES.length)];
        if(stage===0&&j===0)type="fight";
        if(stage===0&&j===1)type="event";
        let x=(j+1)*100/(amount+1)+(rand()-.5)*4;
        floor.push({id:"route-"+stage+"-"+j,stage,index:j,x:+x.toFixed(2),type});
      }
      floors.push(floor)
    }
    for(let stage=0;stage<floors.length-1;stage++){
      let here=floors[stage],next=floors[stage+1];
      for(let j=0;j<here.length;j++){
        const mid=nearest(next,here[j].x);
        edges.push([stage,j,mid]);
        // Distinct path forks, without teleporting across the whole floor.
        const alternative=mid+(rand()<.5?-1:1);
        if(alternative>=0&&alternative<next.length&&rand()<.86)edges.push([stage,j,alternative])
      }
      for(let k=0;k<next.length;k++){
        if(!edges.some(e=>e[0]===stage&&e[2]===k)){
          let origin=nearest(here,next[k].x);
          edges.push([stage,origin,k])
        }
      }
    }
    const result={floors,edges,visited:[],position:null,seed};
    validate(result);
    return result
  }
  function validate(t){
    if(!t||t.floors.length!==STAGES.length)throw Error("Некорректное количество этажей дерева");
    for(let floor=0;floor<t.floors.length;floor++){
      if(t.floors[floor].length<3||t.floors[floor].length>5)throw Error("Некорректное число узлов");
      if(floor===0)continue;
      for(const node of t.floors[floor]){
        if(!t.edges.some(e=>e[0]===floor-1&&e[2]===node.index))throw Error("Узел без входящего пути: "+node.id)
      }
    }
    return true
  }
  function reachable(stage){
    const t=G.routeTree;if(!t||!t.floors[stage])return [];
    if(stage===0)return t.floors[0].map(n=>n.index);
    if(!t.position||t.position.stage!==stage-1)return [];
    return t.edges.filter(e=>e[0]===stage-1&&e[1]===t.position.index).map(e=>e[2])
  }
  makeMap=window.makeMap=function(){
    const role=G.role==="guest"?"guest":G.demo?"demo":"host";
    const seed=seedOf((G.code||String(Math.random()))+"::"+role+"::tree-v05");
    const t=createTree(seed);
    G.routeTree=t;
    G.map=t.floors.map(floor=>floor.map(n=>n.type));
    G.stage=0;
    return G.map
  };
  openNode=window.openNode=function(index){
    const stage=G.stage,t=G.routeTree;
    if(!t){oldMakeMap();return oldOpenNode(index)}
    index=Number(index);
    if(!Number.isInteger(index)||!t.floors[stage]?.[index]||!reachable(stage).includes(index)){
      G.lastRouteWarning="Этот узел недоступен. Выбери один из подсвеченных переходов.";
      render();
      return false
    }
    t.position={stage,index};
    t.visited.push({stage,index});
    G.lastRouteWarning="";
    oldOpenNode(index);
    return true
  };
  window.ASH_TREE={createTree,validate,reachable,seedOf,randomGenerator};
})();
