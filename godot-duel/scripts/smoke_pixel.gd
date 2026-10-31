extends SceneTree
## Real pixel texture and gameplay invariants, including actual combat transitions.
const RULES=preload("res://scripts/game_state.gd")
const PX=preload("res://scripts/pixel_forge.gd")
func _initialize()->void:
 call_deferred("_run")
func _fail(why:String)->void:
 push_error("PIXEL/GAMEPLAY REGRESSION: "+why)
 quit(1)
func _run()->void:
 var world=PX.scene()
 if world.get_width()!=192 or world.get_height()!=128:_fail("arena must be authored at 192x128");return
 for hero in ["guard","oracle","hunter","slime","skeleton","hound","boss"]:
  var sprite=PX.actor(hero)
  if sprite.get_width()!=32 or sprite.get_height()!=48:_fail("sprite "+hero+" dimensions");return
 for name in ["strike","forge003","forge014","forge055","guard"]:
  var art=PX.card_art(name,"Сила","attack")
  if art.get_width()!=48 or art.get_height()!=32:_fail("card source resolution");return
 var img=PX.actor("guard").get_image()
 if img.get_pixel(0,0).a!=0.0:_fail("transparent padding must survive");return
 if img.get_pixel(15,8).a<.98:_fail("fully opaque authored pixels missing");return
 var g=RULES.new()
 g.new_run("guard")
 if g.map_rows.size()!=15:_fail("fifteen floors");return
 for n in g.map_rows[14]:
  if n["type"]!="elite":_fail("final floor must be boss");return
 g.start_fight(false)
 g.fight["enemies"]=[g.create_enemy(false)]
 g.fight["enemies"][0]["hp"]=900
 g.fight["enemies"][0]["max_hp"]=900
 g.fight["energy"]=3
 g.fight["hand"]=[g.make_card("strike"),g.make_card("strike"),g.make_card("strike")]
 for i in range(3):
  if not g.play_card(0,0):_fail("combo card cannot be played");return
 if not g.fight["combo_used"]:_fail("3-card combo must trigger");return
 if int(g.fight["energy"])!=1:_fail("combo grants exactly one energy");return
 if g.fight["hand"].size()!=1:_fail("combo must draw one card");return
 if g.card_description(g.make_card("forge015")).contains("hit7"):_fail("raw developer effects shown to players");return
 # Ward consumes damage before HP.
 g.hp=50
 g.fight["poison"]=3
 g.fight["bleed"]=0
 g.fight["burn"]=0
 g.fight["ward"]=5
 g.fight["enemies"][0]["intent"]=2 # non-damaging shield move
 g.fight["enemies"][0]["moves"]=["guard","lunge","enrage"]
 g.end_turn()
 if g.hp!=50:_fail("ward or non-damaging intent incorrect");return
 # Lethal poison resolves before the enemy's attack, not after it.
 g.new_run("guard")
 g.start_fight(false)
 g.hp=50
 g.fight["enemies"]=[g.create_enemy(false)]
 g.fight["enemies"][0]["hp"]=2
 g.fight["enemies"][0]["poison"]=3
 g.fight["enemies"][0]["moves"]=["lunge"]
 g.end_turn()
 if g.hp!=50 or g.screen!="reward":_fail("poisoned enemy attacked after dying");return
 # Upgrades must actually change previewed card numbers.
 var normal=g.card_description(g.make_card("strike"))
 var upgraded={"id":"strike","up":1}
 if normal==g.card_description(upgraded):_fail("upgraded card reads the same");return
 g.new_run("oracle")
 g.floor=14
 var boss=g.create_enemy(true)
 if int(boss["max_hp"])<100:_fail("last-floor boss health missing");return
 print("PASS PIXEL: native 32x48, 48x32, 192x128 pixel assets, combo, boss, readable cards, ward and lethal DoT.")
 quit(0)
