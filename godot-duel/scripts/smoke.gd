extends SceneTree
## Native headless gameplay validation. Run with: godot --headless --path . --script res://scripts/smoke.gd
const RULES = preload("res://scripts/game_state.gd")
func _initialize() -> void:
 var g = RULES.new()
 if g.data.is_empty():_fail("JSON load");return
 if g.data["cards"].size()!=161:_fail("161 cards");return
 if g.data["relics"].size()!=136:_fail("136 relics");return
 for cls in ["guard","oracle","hunter"]:
  g.new_run(cls)
  if g.deck.size()<10:_fail("starting deck "+cls);return
  if g.map_rows.size()!=15:_fail("route floors");return
  if g.reachable().size()<2:_fail("reachable starting routes");return
  if not g.select_node(0):_fail("first encounter");return
  if g.screen=="fight":
   if g.fight["hand"].size()!=5:_fail("starting hand");return
   if not g.play_card(0,0):_fail("first card");return
   if g.screen=="fight":
    g.end_turn()
   # Reset run before other class.
 g.new_run("oracle")
 g.start_fight(false)
 var dead=0
 var guard=0
 for card_id in g.data["cards"].keys():
  var c=g.card(str(card_id))
  if c.is_empty():_fail("missing card");return
  if not c.has("n") or not c.has("t"):_fail("card schema");return
  if c.get("t","")=="power":guard+=1
  if c.get("rarity","")=="rare":dead+=1
 if guard<8 or dead<8:_fail("rarity and powers");return
 g.hp=mini(g.max_hp,50)
 g.fight["hand"]=[g.make_card("guard")]
 g.fight["energy"]=3
 if not g.play_card(0,0):_fail("blocking card");return
 if g.fight["block"]<6:_fail("block value");return
 g.fight["enemies"][0]["hp"]=1
 g.fight["hand"]=[g.make_card("strike")]
 g.fight["energy"]=3
 if not g.play_card(0,0):_fail("lethal card");return
 if g.screen!="reward":_fail("reward transition");return
 g.pick_reward(0)
 if g.screen!="map" or g.floor!=1:_fail("map advancement");return
 g.screen="map"
 g.floor=0
 g.position=-1
 if not g.reachable().has(0):_fail("new fork");return
 g.new_run("guard")
 g.screen="rest"
 g.rest(false)
 if g.screen!="upgrade":_fail("upgrade choice not offered");return
 if not g.upgrade_card(0):_fail("selected upgrade not applied");return
 if int(g.deck[0]["up"])<1 or g.floor!=1:_fail("campfire upgrade failed");return
 g.screen="shop"
 g.gold=700
 g.setup_shop()
 var before_potions=g.potions.size()
 if not g.buy_potion():_fail("potion purchase");return
 if g.potions.size()!=before_potions+1:_fail("potion inventory");return
 g.screen="remove"
 var before_deck=g.deck.size()
 if not g.remove_card(1):_fail("card removal");return
 if g.deck.size()!=before_deck-1 or g.gold>=700:_fail("shop removal economics");return
 g.new_run("guard")
 g.hp=42
 if not g.save_game():_fail("Godot save path");return
 g.hp=3
 if not g.load_game():_fail("Godot load");return
 if g.hp!=42:_fail("save did not restore HP");return
 g.delete_save()
 print("PASS: Godot game.json 161 cards, 136 relics, three classes, 15 floors, cards, enemies, rewards.")
 quit(0)
func _fail(error:String)->void:
 push_error("FAIL: "+error)
 quit(1)
