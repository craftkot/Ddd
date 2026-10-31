extends SceneTree
## Scene-level regression: scene render passes and native combat board interaction.
const APP = preload("res://scenes/main.tscn")
func _initialize() -> void:
 call_deferred("_check")
func _check() -> void:
 var app=APP.instantiate()
 root.add_child(app)
 await process_frame
 if app.game.screen!="lobby":_fail("lobby startup");return
 app.game.new_run("guard")
 app.render_ui()
 await process_frame
 if app.game.screen!="map":_fail("map state");return
 var route=app.find_child("BattleCanvas",true,false)
 if route!=null:_fail("battle canvas must not overlay map");return
 app.game.start_fight(false)
 app.render_ui()
 await process_frame
 var battle=app.find_child("BattleCanvas",true,false)
 if battle==null:_fail("native combat canvas missing");return
 battle.size=Vector2(720,1180)
 battle.queue_redraw()
 if not battle._needs_target(app.game.make_card("strike")):_fail("attack needs a target");return
 if battle._needs_target(app.game.make_card("guard")):_fail("block card should not require enemy");return
 app.game.fight["hand"]=[app.game.make_card("strike")]
 app.game.fight["energy"]=3
 app.game.fight["enemies"]=[app.game.create_enemy(false)]
 battle.hand_areas=[Rect2(240,790,165,240)]
 battle.hit_areas=[{"index":0,"rect":Rect2(410,210,120,170)}]
 var hp_before=int(app.game.fight["enemies"][0]["hp"])
 battle._press(Vector2(285,850))
 battle._move(Vector2(470,280))
 battle._release(Vector2(470,280))
 if int(app.game.fight["enemies"][0]["hp"])>=hp_before:_fail("dragging an attack to an enemy dealt no damage");return

 app.game.screen="reward"
 app.game.reward_cards=app.game.offer_cards()
 app.render_ui()
 await process_frame
 if app.game.screen!="reward":_fail("reward screen");return
 app.game.screen="shop"
 app.game.setup_shop()
 app.render_ui()
 await process_frame
 app.game.screen="rest"
 app.render_ui()
 await process_frame
 app.game.screen="upgrade"
 app.render_ui()
 await process_frame
 print("PASS: native Godot lobby, route, battle input canvas, card targeting, rewards, shop, rest, upgrade.")
 quit(0)
func _fail(detail:String)->void:
 push_error("UI SMOKE FAILED: "+detail)
 quit(1)
