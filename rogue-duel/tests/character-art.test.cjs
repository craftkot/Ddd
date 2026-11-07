"use strict";
/* WebView art-regression test: every game character is physically bundled, transparent
   and mapped to the UI. No network image fetch and no old sprite atlas portraits. */
const fs=require("node:fs");
const path=require("node:path");
const assert=require("node:assert/strict");
const root=path.join(__dirname,"..","app","src","main","assets");
const characters=["guard","oracle","hunter","bandit","cultist","skeleton","slime","hound","boss"];
const main=fs.readFileSync(path.join(root,"index.html"),"utf8");
const atelier=fs.readFileSync(path.join(root,"atelier.js"),"utf8");
const css=fs.readFileSync(path.join(root,"character-redesign.css"),"utf8");
const java=fs.readFileSync(path.join(__dirname,"..","app","src","main","java","com","ehtid","ashesduel","MainActivity.java"),"utf8");
assert(main.includes('href="character-redesign.css"'),"Character stylesheet must load in the game");
assert(css.includes(".hero-portrait-wrap"),"Hero artwork layout missing");
assert(css.includes(".etch-fighters .etch-person"),"Battle artwork layout missing");
assert(atelier.includes("function paintedPortrait"),"Sprite renderer missing");
assert(atelier.includes('src="art/characters/'),"Renderer must load new offline art");
assert(!atelier.includes("portrait-x:"),"Old compressed character atlas still used");
assert(java.includes("art/characters/(guard|oracle|hunter|bandit|cultist|skeleton|slime|hound|boss)"),"Android WebView must intercept all character assets");
for(const id of characters){
 const file=path.join(root,"art","characters",id+".webp");
 assert(fs.existsSync(file),id+": missing bundled WebP");
 const data=fs.readFileSync(file);
 assert(data.length>12_000,id+": unexpectedly small/placeholder artwork");
 assert.equal(data.toString("ascii",0,4),"RIFF",id+": not RIFF");
 assert.equal(data.toString("ascii",8,12),"WEBP",id+": not WebP");
 assert.equal(data.toString("ascii",12,16),"VP8X",id+": expected extended WebP with alpha");
 assert((data[20] & 0x10)!==0,id+": no alpha channel");
 const width=data.readUIntLE(24,3)+1,height=data.readUIntLE(27,3)+1;
 assert(width>=320&&height>=320,id+": too small to remain sharp on mobile");
}
console.log("PASS art v0.9: nine transparent RGBA character illustrations, mobile UI renderer, secure offline Android asset routes.");
