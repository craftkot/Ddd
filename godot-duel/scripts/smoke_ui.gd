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
 var hand_before=app.game.fight["hand"].size()
 battle._press(Vector2(310,1020))
 battle._release(Vector2(310,1020))
 if app.game.fight["hand"].size()>hand_before:_fail("touch interaction produced cards");return
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
