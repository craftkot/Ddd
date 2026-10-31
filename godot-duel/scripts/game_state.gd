extends RefCounted
## Offline rules for the native Godot migration. No HTML, DOM or WebView.
const DATA_PATH = "res://data/game.json"
const ARCHETYPES = [
 {"name":"Болотный налётчик","kind":"bandit","moves":["slash","lunge","guard"]},
 {"name":"Пепельный культист","kind":"cultist","moves":["hex","slash","enrage"]},
 {"name":"Скелет-дозорный","kind":"skeleton","moves":["guard","bash","slash"]},
 {"name":"Гнилая слизь","kind":"slime","moves":["venom","guard","sever"]},
 {"name":"Рыцарь пепельной клятвы","kind":"knight","moves":["charge","lunge","guard"]},
 {"name":"Ткач пустоты","kind":"mage","moves":["hex","drain","sever"]},
 {"name":"Пещерный волк","kind":"hound","moves":["bite","bite","howl"]}
]
var data: Dictionary = {}
var rng: RandomNumberGenerator = RandomNumberGenerator.new()
var screen: String = "lobby"
var hero: String = ""
var hp: int = 0
var max_hp: int = 0
var gold: int = 75
var deck: Array = []
var relics: Array = []
var potions: Array = ["salve"]
var floor: int = 0
var map_rows: Array = []
var map_edges: Array = []
var position: int = -1
var history: Array = []
var fight: Dictionary = {}
var reward_cards: Array = []
var shop_cards: Array = []
var shop_relics: Array = []
var active_event: Dictionary = {}
var logs: Array = []
var state: Dictionary = {}
var reason: String = ""

func _init() -> void:
 var f = FileAccess.open(DATA_PATH, FileAccess.READ)
 if f == null:
  push_error("game.json was not found")
  return
 var parsed = JSON.parse_string(f.get_as_text())
 if typeof(parsed) == TYPE_DICTIONARY:
  data = parsed
 rng.randomize()

func card(id: String) -> Dictionary:
 return data.get("cards", {}).get(id, {})
func relic(id: String) -> Dictionary:
 for r in data.get("relics", []):
  if r.get("id", "") == id:
   return r
 return {}
func make_card(id: String) -> Dictionary:
 return {"id": id, "up": 0}
func roll(array: Array):
 if array.is_empty():
  return null
 return array[rng.randi_range(0,array.size()-1)]
func take_distinct(source: Array, n: int) -> Array:
 var copy = source.duplicate()
 copy.shuffle()
 return copy.slice(0, min(n, copy.size()))
func clamp_health() -> void:
 hp = clampi(hp, 0, max_hp)

func new_run(id: String) -> void:
 if not data.get("classes", {}).has(id):
  return
 var cls: Dictionary = data["classes"][id]
 hero = id
 hp = int(cls["hp"])
 max_hp = hp
 gold = 75
 deck.clear()
 for c in cls["deck"]:
  deck.append(make_card(c))
 relics.clear()
 potions = ["salve"]
 floor = 0
 position = -1
 history.clear()
 logs = ["Путь начинается. Выбери первый узел."]
 screen = "map"
 rng.randomize()
 build_map()

func build_map() -> void:
 map_rows.clear()
 map_edges.clear()
 var types = ["fight","fight","event","shop","fight","elite","rest","treasure"]
 var count = data.get("stages", []).size()
 for y in range(count):
  var row = []
  var width = 3 if y==0 or y==count-1 else rng.randi_range(3,5)
  for x in range(width):
   var klass = str(roll(types))
   if x < 2 and y < count:
    klass = str(data["stages"][y][x])
   row.append({"type":klass,"x":(float(x+1)/float(width+1)),"floor":y,"index":x})
  map_rows.append(row)
 for y in range(map_rows.size()-1):
  var a: Array = map_rows[y]
  var b: Array = map_rows[y+1]
  for i in range(a.size()):
   var nearest = 0
   var d = 999.0
   for j in range(b.size()):
    var dd = absf(float(a[i]["x"])-float(b[j]["x"]))
    if dd < d:
     d = dd
     nearest = j
   map_edges.append([y,i,nearest])
   if nearest + 1 < b.size() and rng.randf() < .55:
    map_edges.append([y,i,nearest+1])
   elif nearest > 0 and rng.randf() < .55:
    map_edges.append([y,i,nearest-1])
  for j in range(b.size()):
   var linked = false
   for edge in map_edges:
    if int(edge[0])==y and int(edge[2])==j:
     linked = true
   if not linked:
    var from_index = 0
    var dd = 999.0
    for i in range(a.size()):
     var gap = absf(float(a[i]["x"])-float(b[j]["x"]))
     if gap < dd:
      dd = gap
      from_index = i
    map_edges.append([y,from_index,j])

func reachable() -> Array:
 if floor == 0:
  return range(map_rows[0].size())
 var allowed = []
 for e in map_edges:
  if int(e[0]) == floor-1 and int(e[1])==position and not allowed.has(int(e[2])):
   allowed.append(int(e[2]))
 return allowed

func select_node(index: int) -> bool:
 if screen != "map" or not reachable().has(index):
  return false
 var node = map_rows[floor][index]
 position = index
 history.append([floor,index])
 var kind: String = str(node["type"])
 match kind:
  "fight", "elite": start_fight(kind == "elite")
  "shop":
   setup_shop()
   screen = "shop"
  "event":
   active_event = roll(data["events"]).duplicate(true)
   screen = "event"
  "rest": screen = "rest"
  "treasure":
   shop_relics = offer_relics(3)
   screen = "treasure"
  _:
   screen = "event"
 return true

func next_floor() -> void:
 fight.clear()
 active_event.clear()
 reward_cards.clear()
 shop_cards.clear()
 floor += 1
 if floor >= map_rows.size():
  screen = "final"
 else:
  screen = "map"

func create_enemy(elite: bool) -> Dictionary:
 var def = ARCHETYPES[rng.randi_range(0,ARCHETYPES.size()-1)]
 var hp_max = 33 + floor*4
 if elite:
  hp_max += 35
 return {"name":("Судья из пепла" if elite else def["name"]),"kind":("boss" if elite else def["kind"]),
 "moves":(["charge","sever","hex","rampage"] if elite else def["moves"]),
 "hp":hp_max,"max_hp":hp_max,"block":0,"str":(2 if elite else 0),
 "poison":0,"bleed":0,"burn":0,"weak":0,"vuln":0,"mark":0,"thorns":0,"intent":0,"stun":0}

func enemy_plan(enemy: Dictionary) -> Dictionary:
 var moves: Array = enemy["moves"]
 var move = str(moves[int(enemy.get("intent",0))%moves.size()])
 var atk = 7 + floor + int(enemy.get("str",0))
 match move:
  "slash": return {"move":move,"damage":atk,"icon":"⚔","label":"Атака %d" % atk}
  "lunge": return {"move":move,"damage":atk+3,"icon":"⚔","label":"Выпад %d" % (atk+3)}
  "bash": return {"move":move,"damage":atk-1,"block":4,"icon":"🛡","label":"Щит и удар %d" % (atk-1)}
  "guard": return {"move":move,"block":6+floor/3,"damage":maxi(1,atk-4),"icon":"🛡","label":"Броня и удар"}
  "venom": return {"move":move,"damage":maxi(1,atk-5),"poison":3,"icon":"☠","label":"Яд +3"}
  "sever": return {"move":move,"damage":maxi(1,atk-2),"bleed":3,"icon":"🩸","label":"Кровь +3"}
  "hex": return {"move":move,"damage":maxi(1,atk-5),"weak":1,"vuln":1,"icon":"🕸","label":"Слабость и уязвимость"}
  "charge": return {"move":move,"strength":2,"block":9,"icon":"✦","label":"Подготовка"}
  "enrage","howl": return {"move":move,"strength":2,"icon":"✦","label":"Сила +2"}
  "drain": return {"move":move,"damage":maxi(1,atk-4),"weak":2,"heal":5,"icon":"🕸","label":"Поглощение"}
  "bite": return {"move":move,"damage":maxi(1,atk/2),"hits":2,"icon":"⚔","label":"Двойной укус"}
  "rampage": return {"move":move,"damage":maxi(1,atk/2),"hits":3,"icon":"⚔","label":"Шквал x3"}
 return {"move":"slash","damage":atk,"icon":"⚔","label":"Удар"}

func start_fight(elite: bool) -> void:
 var enemies = []
 var n = 1 if elite else clampi(1 + floor/3,1,4)
 for i in range(n):
  var e = create_enemy(elite)
  if n>1:
   e["hp"] = maxi(12,int(float(e["hp"])/float(n)*1.55))
   e["max_hp"] = e["hp"]
  enemies.append(e)
 fight = {"enemies":enemies,"elite":elite,"energy":3,"block":0,"str":0,"poison":0,"bleed":0,"burn":0,"weak":0,
 "vuln":0,"mark":0,"ward":0,"thorns":0,"regen":0,"speed":100,"focus":2,
 "library":deck.duplicate(true),"discard":[],"exhaust":[],"hand":[],"round":0,
 "target":0,"limit":0,"next_av":0}
 fight["library"].shuffle()
 for id in relics:
  var r = relic(str(id))
  if r.get("event","") == "start":
   relic_effect(r,0)
  match id:
   "sword":fight["str"]+=2
   "shield","chain":fight["block"]+=10
   "rose":fight["thorns"]+=2
   "spark":fight["energy"]+=1
 start_turn()
 screen = "fight"
 logs = ["Сражение началось. Врагов: %d" % n]

func draw_cards(n: int) -> void:
 for i in range(n):
  if fight["hand"].size() >= 10:
   break
  if fight["library"].is_empty():
   if fight["discard"].is_empty():break
   fight["library"] = fight["discard"].duplicate(true)
   fight["library"].shuffle()
   fight["discard"].clear()
  fight["hand"].append(fight["library"].pop_back())

func start_turn() -> void:
 fight["round"] += 1
 fight["block"] = 0 if int(fight["round"])>1 else int(fight["block"])
 fight["energy"] = 3 + (1 if int(fight["round"])==1 and relics.has("spark") else 0)
 if fight["regen"] > 0:
  hp = mini(max_hp,hp+int(fight["regen"]))
 draw_cards(5)
 for id in relics:
  var r=relic(str(id))
  if r.get("event","") == "turn":
   relic_effect(r,0)

func _damage_enemy(target: int, points: int, piercing: bool=false) -> int:
 var enemies: Array = fight["enemies"]
 if target<0 or target>=enemies.size():return 0
 var e: Dictionary = enemies[target]
 if e["hp"]<=0:return 0
 var d = maxi(0,points + int(fight["str"]))
 if fight["weak"]>0:d=int(float(d)*.75)
 if e["vuln"]>0:d=int(ceil(float(d)*1.5))
 d += int(e.get("mark",0))
 var armor = 0 if piercing else mini(int(e["block"]),d)
 e["block"] -= armor
 d -= armor
 e["hp"] = maxi(0,int(e["hp"])-d)
 if e.get("thorns",0)>0 and d>0:hp -= int(e["thorns"])
 for id in relics:
  var r=relic(str(id))
  if r.get("event","")=="attack":relic_effect(r,target)
 return d

func _true_damage_enemy(target: int,amount: int) -> void:
 var e: Dictionary = fight["enemies"][target]
 e["hp"]=maxi(0,int(e["hp"])-maxi(0,amount))

func effect(key:String, value:int, target:int, upgrade:int) -> void:
 var e:Dictionary = fight["enemies"][target]
 var multi_keys=["hit","block","heal","poison","burn","bleed","aoe","aoepoison","aoeburn","aoebleed","lifesteal","multihit","pierce"]
 var v=value+(2 if upgrade>0 and multi_keys.has(key) else 0)
 var alive = []
 for j in range(fight["enemies"].size()):
  if fight["enemies"][j]["hp"]>0:alive.append(j)
 match key:
  "hit":_damage_enemy(target,v)
  "aoe":for_each_damage(alive,v)
  "multihit":
   for i in range(4 if upgrade>0 else 3):
    if e["hp"]>0:_damage_enemy(target,v)
  "pierce":_damage_enemy(target,v,true)
  "execute":
   if e["hp"]<float(e["max_hp"])*.5:_damage_enemy(target,10)
  "lifesteal":
   var h=_damage_enemy(target,v);hp=mini(max_hp,hp+int(ceil(float(h)*.5)))
  "shieldhit":_damage_enemy(target,int(fight["block"])*v)
  "poison":e["poison"]+=v
  "burn":e["burn"]+=v
  "bleed":e["bleed"]+=v
  "aoepoison","aoeburn","aoebleed":
   var field=key.substr(3)
   for i in alive:fight["enemies"][i][field]+=v
  "block":fight["block"]+=v
  "heal":hp=mini(max_hp,hp+v)
  "draw":draw_cards(v)
  "energy":fight["energy"]=mini(20,int(fight["energy"])+v)
  "str":fight["str"]+=v
  "weak","vuln","mark":e[key]+=v
  "aoeweak","aoevuln":
   var field=key.substr(3)
   for i in alive:fight["enemies"][i][field]+=v
  "regen","thorns","charge","armorcarry":fight[key]=int(fight.get(key,0))+v
  "dodge":fight["dodge"]=int(fight.get("dodge",0))+v
  "detonate":
   var affected=alive if v>=3 else [target]
   for i in affected:
    var enemy:Dictionary=fight["enemies"][i]
    var burst=(int(enemy["poison"])+2*int(enemy["burn"])+int(enemy["bleed"]))*v
    _true_damage_enemy(i,burst)
    enemy["poison"]=0;enemy["burn"]=0;enemy["bleed"]=0
  "spread":
   for i in alive:
    if i!=target:
     var other:Dictionary=fight["enemies"][i]
     for field in ["poison","burn","bleed"]:other[field]+=int(ceil(float(e[field])*.6))
  "risky":hp=maxi(1,hp-v)
  "siphon":fight["energy"]+=mini(v,int(e["poison"])+int(e["burn"])+int(e["bleed"]))
  "duplicate":
   if fight["hand"].size()>0 and fight["hand"].size()<10:
    fight["hand"].append(fight["hand"][0].duplicate(true))
  "stun":e["stun"]+=v
  "counter":fight["thorns"]+=v
  "consume":fight["block"]=0
  "ward":fight["ward"]+=v
  "purify":
   for field in ["poison","bleed","burn"]:fight[field]=int(float(fight[field])*(100-v)/100.0)
  "speed":fight["speed"]=clampi(int(fight["speed"])+v,75,155)
  "slow":e["weak"]+=1
  "delay":e["stun"]+=1
  "advance","retard","tax":fight["next_av"]=int(fight.get("next_av",0))+v
  _:
   if key.begins_with("counter"):fight["block"]+=7

func for_each_damage(indices:Array,v:int)->void:
 for i in indices:_damage_enemy(int(i),v)

func play_card(index:int,target:int) -> bool:
 if screen!="fight" or index<0 or index>=fight["hand"].size():return false
 var c:Dictionary=fight["hand"][index]
 var cd:Dictionary=card(str(c["id"]))
 if cd.is_empty() or int(cd.get("c",99))>int(fight["energy"]):return false
 if fight["enemies"].is_empty():return false
 if target<0 or target>=fight["enemies"].size() or fight["enemies"][target]["hp"]<=0:return false
 fight["energy"]-=int(cd["c"])
 fight["hand"].remove_at(index)
 var up=int(c.get("up",0))
 if cd.has("effects"):
  for fx in cd["effects"]:
   effect(str(fx["key"]),int(fx["value"]),target,up)
 else:
  match str(cd.get("k","")):
   "hit":_damage_enemy(target,int(cd.get("v",5))+up*3)
   "block":fight["block"]+=int(cd.get("v",5))+up*3
   "dash":_damage_enemy(target,int(cd.get("v",5))+up*3);draw_cards(1)
   "fire","bleed":_damage_enemy(target,int(cd.get("v",5))+up*3);fight["enemies"][target]["poison"]+=2
   "spikes":fight["block"]+=5;fight["thorns"]+=2
   "str":fight["str"]+=2+up
   "heal":hp=mini(max_hp,hp+int(cd.get("v",5))+up*3)
   "hex":fight["enemies"][target]["weak"]+=1;fight["enemies"][target]["vuln"]+=1
   "twin":_damage_enemy(target,4+up*3);_damage_enemy(target,4+up*3)
   "breaker":_damage_enemy(target,12+up*3);fight["block"]+=8
   "draw":draw_cards(2+up)
   "oath":fight["block"]+=8;fight["str"]+=1+up
   "leech":_damage_enemy(target,12+up*3);hp=mini(max_hp,hp+4)
   "frost":fight["block"]+=10;fight["enemies"][target]["weak"]+=1
   "ambush":_damage_enemy(target,4+(8 if fight["enemies"][target]["poison"]>0 else 0))
   "poison":fight["enemies"][target]["poison"]+=3+up
   "seal":fight["block"]+=6;draw_cards(1)
   "bargain":hp=maxi(1,hp-2);fight["energy"]+=2;draw_cards(1)
   "execute":_damage_enemy(target,10+(10 if fight["enemies"][target]["hp"]<fight["enemies"][target]["max_hp"]/2 else 0))
   "meditate":hp=mini(max_hp,hp+4);draw_cards(1)
 if str(cd.get("t",""))=="power" or str(cd.get("t",""))=="reaction":
  fight["exhaust"].append(c)
 else:
  fight["discard"].append(c)
 logs.append("%s разыграна" % cd["n"])
 if fight["enemies"].all(func(enemy):return int(enemy["hp"])<=0):
  win_fight()
 elif hp<=0:screen="lost"
 return true

func _tick_status(x:Dictionary)->void:
 var amount=int(x.get("poison",0))+int(x.get("bleed",0))+2*int(x.get("burn",0))
 x["hp"]=maxi(0,int(x["hp"])-amount)
 x["poison"]=maxi(0,int(x.get("poison",0))-1)
 x["bleed"]=maxi(0,int(x.get("bleed",0))-2)
 x["burn"]=maxi(0,int(x.get("burn",0))-1)

func end_turn() -> void:
 if screen!="fight":return
 var status={"hp":hp,"poison":fight["poison"],"burn":fight["burn"],"bleed":fight["bleed"]}
 _tick_status(status)
 hp=int(status["hp"])
 for k in ["poison","bleed","burn"]:fight[k]=int(status[k])
 if hp<=0:screen="lost";return
 fight["discard"].append_array(fight["hand"])
 fight["hand"].clear()
 for e in fight["enemies"]:
  if e["hp"]<=0:continue
  var p=enemy_plan(e)
  e["block"]=0
  if e["stun"]>0:e["stun"]-=1
  else:
   if p.has("block"):e["block"]+=int(p["block"])
   if p.has("strength"):e["str"]+=int(p["strength"])
   for key in ["poison","bleed","weak","vuln"]:
    if p.has(key):fight[key]=int(fight.get(key,0))+int(p[key])
   if p.has("heal"):e["hp"]=mini(int(e["max_hp"]),int(e["hp"])+int(p["heal"]))
   for j in range(int(p.get("hits",1))):
    var dmg=int(p.get("damage",0))
    if dmg>0 and hp>0:
     var blocked=mini(int(fight["block"]),dmg)
     fight["block"]-=blocked
     dmg-=blocked
     if fight["vuln"]>0:dmg=int(ceil(dmg*1.5))
     hp=maxi(0,hp-dmg)
     if dmg>0 and fight["thorns"]>0:e["hp"]=maxi(0,int(e["hp"])-int(fight["thorns"]))
  e["intent"]+=1
  _tick_status(e)
  if hp<=0:screen="lost";return
 if fight["enemies"].all(func(enemy):return int(enemy["hp"])<=0):win_fight();return
 fight["weak"]=maxi(0,int(fight["weak"])-1)
 fight["vuln"]=maxi(0,int(fight["vuln"])-1)
 start_turn()

func relic_effect(r:Dictionary,target:int)->void:
 var key=str(r.get("power",""))
 var v=int(r.get("value",0))
 match key:
  "strength":fight["str"]+=v
  "energy":fight["energy"]+=v
  "block":fight["block"]+=v
  "thorns":fight["thorns"]+=v
  "regen":fight["regen"]+=v
  "heal":hp=mini(max_hp,hp+v)
  "draw":draw_cards(v)
  "poisonall","burnall","bleedall","weakall","vulnall":
   var name=key.trim_suffix("all")
   for e in fight["enemies"]:
    if e["hp"]>0:e[name]+=v
  "poison","burn","bleed","weak","mark":
   if target<fight["enemies"].size():fight["enemies"][target][key]+=v
  "gold":gold+=v

func offer_cards(elite:bool=false)->Array:
 var pool=data["cards"].keys().filter(func(id):return not str(id).begins_with("pvp"))
 var offers=[]
 var tier=["common","uncommon","rare"]
 for i in range(3):
  var odds=rng.randf()
  var target="rare" if odds<(0.19 if elite else .07) else ("uncommon" if odds<(0.55 if elite else .35) else "common")
  var options=pool.filter(func(id):return card(str(id)).get("rarity","common")==target and not offers.has(id))
  if options.is_empty():options=pool.filter(func(id):return not offers.has(id))
  offers.append(str(roll(options)))
 return offers

func offer_relics(n:int)->Array:
 var available=[]
 for r in data["relics"]:
  if not relics.has(r["id"]):available.append(str(r["id"]))
 return take_distinct(available,n)

func gain_relic(id:String)->void:
 if relics.has(id):
  gold+=55;return
 relics.append(id)
 var r=relic(id)
 match id:
  "heart":max_hp+=12;hp+=12
  "gem":max_hp+=6;hp+=6
  "coin":gold+=65
  "scroll":
   if deck.size()>0:deck[rng.randi_range(0,deck.size()-1)]["up"]+=1
 if r.get("event","")=="acquire":
  max_hp+=int(r.get("value",0));hp+=int(r.get("value",0))

func win_fight()->void:
 if fight.is_empty():return
 var elite=bool(fight["elite"])
 gold+=75 if elite else 30+floor*6
 hp=mini(max_hp,hp+(7 if relics.has("sun") else 0)+(12 if relics.has("flask") else 0))
 if elite:
  var opts=offer_relics(1)
  if opts.size()>0:gain_relic(str(opts[0]))
 reward_cards=offer_cards(elite)
 fight.clear()
 screen="reward"

func pick_reward(index:int)->void:
 if screen!="reward":return
 if index>=0 and index<reward_cards.size():deck.append(make_card(str(reward_cards[index])))
 next_floor()

func setup_shop()->void:
 shop_cards=offer_cards()
 shop_relics=offer_relics(2)

func shop_card_price(index:int)->int:
 if index<0 or index>=shop_cards.size():return 9999
 var rarity=str(card(str(shop_cards[index])).get("rarity","common"))
 return {"common":47,"uncommon":74,"rare":115}.get(rarity,47)
func buy_card(index:int)->bool:
 if screen!="shop" or index<0 or index>=shop_cards.size():return false
 var id=str(shop_cards[index])
 var price=shop_card_price(index)
 if id=="" or gold<price:return false
 gold-=price;deck.append(make_card(id));shop_cards[index]="";return true
func buy_relic(index:int)->bool:
 if screen!="shop" or index<0 or index>=shop_relics.size() or gold<115:return false
 var id=str(shop_relics[index]);if id=="":return false
 gold-=115;gain_relic(id);shop_relics[index]="";return true

func rest(heal_choice:bool)->void:
 if screen!="rest":return
 if heal_choice:hp=mini(max_hp,hp+int(ceil(max_hp*.3)))
 elif deck.size()>0:deck[rng.randi_range(0,deck.size()-1)]["up"]+=1
 next_floor()

func event_option(index:int)->void:
 if screen!="event" or index<0 or index>=active_event.get("options",[]).size():return
 var kind=str(active_event["options"][index][1])
 match kind:
  "blood":hp=maxi(1,hp-9)
  "gold":gold+=45
  "heal":hp=mini(max_hp,hp+20)
  "upgrade":rest_upgrade()
  "relic":
   hp=maxi(1,hp-10)
   var opts=offer_relics(1);if opts.size()>0:gain_relic(str(opts[0]))
  "card":deck.append(make_card(str(roll(data["cards"].keys()))));gold+=15
  "risk":hp=maxi(1,hp-6);gold+=70
  "goldclock":gold+=60
  "vials":potions.append_array(["salve","cleanser"]);potions=potions.slice(0,3)
  "goldgrow":gold+=75
  "purge":
   if deck.size()>7:
    for i in range(deck.size()):
     if deck[i]["id"]=="strike":deck.remove_at(i);break
   gold+=25
  "rarecard":hp=maxi(1,hp-10);deck.append(make_card(str(roll(offer_cards()))))
  "fortify":max_hp+=7;hp+=7
  "riskyupgrade":
   hp=maxi(1,hp-8);rest_upgrade();rest_upgrade()
  "vial":
   if potions.size()<3:potions.append("steel")
  "goldalchemy":gold+=55
 next_floor()
func rest_upgrade()->void:
 if deck.size()>0:deck[rng.randi_range(0,deck.size()-1)]["up"]+=1

func use_potion(index:int)->bool:
 if screen!="fight" or index<0 or index>=potions.size():return false
 var id=str(potions[index])
 match id:
  "salve":hp=mini(max_hp,hp+17)
  "cleanser":
   for s in ["poison","burn","bleed"]:fight[s]=0
  "steel":fight["block"]+=16
  "aether":fight["energy"]+=2;draw_cards(1)
  "ember":
   if fight["enemies"].size()>0:
    var i=clampi(int(fight["target"]),0,fight["enemies"].size()-1)
    fight["enemies"][i]["burn"]+=7
 potions.remove_at(index)
 return true

const SAVE_PATH = "user://ashes_godot_solo_v08.json"

func has_save()->bool:
 return FileAccess.file_exists(SAVE_PATH)

func delete_save()->void:
 if FileAccess.file_exists(SAVE_PATH):
  DirAccess.remove_absolute(ProjectSettings.globalize_path(SAVE_PATH))

func save_game()->bool:
 if hero=="" or screen in ["lobby","class","lost","final"]:
  return false
 var content = {
  "version":8,
  "hero":hero,"hp":hp,"max_hp":max_hp,"gold":gold,
  "deck":deck,"relics":relics,"potions":potions,"floor":floor,
  "map_rows":map_rows,"map_edges":map_edges,"position":position,
  "history":history,"fight":fight,"reward_cards":reward_cards,
  "shop_cards":shop_cards,"shop_relics":shop_relics,
  "active_event":active_event,"logs":logs,"screen":screen,
  "random_state":rng.state
 }
 var file=FileAccess.open(SAVE_PATH,FileAccess.WRITE)
 if file==null:return false
 file.store_string(JSON.stringify(content))
 return true

func load_game()->bool:
 if not has_save():return false
 var raw=FileAccess.get_file_as_string(SAVE_PATH)
 var doc=JSON.parse_string(raw)
 if typeof(doc)!=TYPE_DICTIONARY or int(doc.get("version",0))!=8:return false
 if not data["classes"].has(doc.get("hero","")):return false
 if not (doc.get("map_rows",[]) is Array) or doc["map_rows"].size()!=15:return false
 if str(doc.get("screen","")) not in ["map","fight","reward","shop","event","rest","treasure"]:return false
 hero=str(doc["hero"]);hp=int(doc.get("hp",0));max_hp=int(doc.get("max_hp",0));gold=int(doc.get("gold",0))
 deck=doc.get("deck",[]);relics=doc.get("relics",[]);potions=doc.get("potions",[])
 floor=int(doc.get("floor",0));map_rows=doc["map_rows"];map_edges=doc.get("map_edges",[])
 position=int(doc.get("position",-1));history=doc.get("history",[])
 fight=doc.get("fight",{});reward_cards=doc.get("reward_cards",[])
 shop_cards=doc.get("shop_cards",[]);shop_relics=doc.get("shop_relics",[])
 active_event=doc.get("active_event",{});logs=doc.get("logs",[])
 screen=str(doc["screen"])
 rng.state=int(doc.get("random_state",rng.state))
 return true
