extends Control
## Fully native Android/Godot user interface. No embedded web page.
const RULES = preload("res://scripts/game_state.gd")
const ACTOR = preload("res://scripts/actor.gd")
const ROUTE = preload("res://scripts/route_board.gd")
const COMBAT_BOARD = preload("res://scripts/combat_board.gd")
const RUNE = preload("res://scripts/rune_art.gd")
const INK = Color("#10151f")
const PANEL = Color("#242938")
const PANEL_ALT = Color("#302d3b")
const GOLD = Color("#e8c48e")
const TEXT = Color("#f4e4d3")
const QUIET = Color("#a9adbb")
const BLUE = Color("#527fa1")
const RED = Color("#8f4147")
var game = RULES.new()
var root: VBoxContainer
var page: VBoxContainer
var scroll: ScrollContainer
var selected_card: int = -1
var enemy_controls: Array = []
var dragging: int = -1
var touch_start: Vector2 = Vector2.ZERO
var fingerprint: String = ""
var collection_visible: bool = false

func _ready() -> void:
 set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
 get_window().content_scale_factor = 1.0
 render_ui()

func panel_style(bg:Color,edge:Color=Color("#8b7058"),radius:int=15) -> StyleBoxFlat:
 var st=StyleBoxFlat.new()
 st.bg_color=bg
 st.border_color=edge
 st.set_border_width_all(2)
 st.set_corner_radius_all(radius)
 st.set_content_margin_all(9)
 return st

func make_text(value:String, font_size:int=19, color:Color=TEXT) -> Label:
 var label=Label.new()
 label.text=value
 label.add_theme_font_size_override("font_size",font_size)
 label.add_theme_color_override("font_color",color)
 label.autowrap_mode=TextServer.AUTOWRAP_WORD_SMART
 label.size_flags_horizontal=Control.SIZE_EXPAND_FILL
 return label

func title(value:String) -> Label:
 var l=make_text(value,31,GOLD)
 l.horizontal_alignment=HORIZONTAL_ALIGNMENT_CENTER
 return l

func make_button(text:String,callback:Callable,secondary:bool=false) -> Button:
 var b=Button.new()
 b.text=text
 b.custom_minimum_size=Vector2(0,60)
 b.size_flags_horizontal=Control.SIZE_EXPAND_FILL
 b.add_theme_font_size_override("font_size",17)
 b.add_theme_color_override("font_color",TEXT)
 b.add_theme_stylebox_override("normal",panel_style(PANEL_ALT if secondary else Color("#624638"),GOLD if not secondary else QUIET,9))
 b.add_theme_stylebox_override("hover",panel_style(Color("#796049"),GOLD,9))
 b.add_theme_stylebox_override("pressed",panel_style(RED,GOLD,9))
 b.add_theme_stylebox_override("focus",StyleBoxEmpty.new())
 b.pressed.connect(callback)
 return b

func framed(vertical:bool=true,shade:Color=PANEL) -> Container:
 var p=PanelContainer.new()
 p.add_theme_stylebox_override("panel",panel_style(shade))
 p.size_flags_horizontal=Control.SIZE_EXPAND_FILL
 var c=VBoxContainer.new() if vertical else HBoxContainer.new()
 c.add_theme_constant_override("separation",9)
 p.add_child(c)
 page.add_child(p)
 return c

func render_ui() -> void:
 game.save_game()
 selected_card=-1
 enemy_controls.clear()
 for child in get_children():
  remove_child(child)
  child.queue_free()
 var bg=ColorRect.new()
 bg.color=INK
 bg.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
 bg.mouse_filter=Control.MOUSE_FILTER_IGNORE
 add_child(bg)
 root=VBoxContainer.new()
 root.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
 root.add_theme_constant_override("separation",0)
 add_child(root)
 var top=PanelContainer.new()
 top.add_theme_stylebox_override("panel",panel_style(Color("#151e2b"),Color("#b38e62"),0))
 root.add_child(top)
 var top_row=HBoxContainer.new()
 top.add_child(top_row)
 var brand=make_text("✦ ПЕПЕЛ И КАРТЫ",21,GOLD)
 top_row.add_child(brand)
 var version=make_text("GODOT  •  ALPHA 0.9",11,QUIET)
 version.horizontal_alignment=HORIZONTAL_ALIGNMENT_RIGHT
 top_row.add_child(version)
 if game.screen=="fight":
  var battle=COMBAT_BOARD.new()
  battle.game=game
  battle.transition_requested.connect(_combat_updated)
  root.add_child(battle)
  return
 scroll=ScrollContainer.new()
 scroll.size_flags_vertical=Control.SIZE_EXPAND_FILL
 scroll.horizontal_scroll_mode=ScrollContainer.SCROLL_MODE_DISABLED
 root.add_child(scroll)
 var margin=MarginContainer.new()
 margin.add_theme_constant_override("margin_left",13)
 margin.add_theme_constant_override("margin_right",13)
 margin.add_theme_constant_override("margin_top",15)
 margin.add_theme_constant_override("margin_bottom",25)
 margin.size_flags_horizontal=Control.SIZE_EXPAND_FILL
 scroll.add_child(margin)
 page=VBoxContainer.new()
 page.add_theme_constant_override("separation",14)
 page.size_flags_horizontal=Control.SIZE_EXPAND_FILL
 margin.add_child(page)
 match game.screen:
  "lobby": show_lobby()
  "class": show_class()
  "map": show_map()
  "fight": show_fight()
  "reward": show_rewards()
  "upgrade": show_upgrade()
  "remove": show_remove()
  "shop": show_shop()
  "rest": show_rest()
  "treasure": show_treasure()
  "event": show_event()
  "final": show_final()
  "lost": show_lost()
  _: show_lobby()

func _combat_updated()->void:
 game.save_game()
 render_ui()

func show_lobby() -> void:
 page.add_child(title("ПЕПЕЛ И КАРТЫ"))
 page.add_child(make_text("Нативный карточный рогалик на Godot 4",18,QUIET))
 var intro=framed(true,Color("#25212c"))
 intro.add_child(make_text("15 этажей • разветвлённая карта • 161 карта • 136 реликвий",19,GOLD))
 intro.add_child(make_text("Три класса, комбинации эффектов, отряды врагов, магазины и выбор наград. Касайся карт и выбирай цели.",16))
 intro.add_child(make_button("Начать одиночное приключение",_open_class))
 if game.has_save():
  intro.add_child(make_button("▶ Продолжить сохранённый забег",_resume_run,true))

func _open_class()->void:
 game.screen="class"
 render_ui()

func _resume_run()->void:
 if game.load_game():render_ui()
 var notice=framed()
 notice.add_child(make_text("Переход на новый движок",20,GOLD))
 notice.add_child(make_text("Это первый нативный порт. Финальный PvP по интернету и AV-контрприёмы пока остаются в старой Android-версии. В этой APK проверяется новый бой и интерфейс.",15,QUIET))

func show_class() -> void:
 page.add_child(title("ВЫБЕРИ ПУТЬ"))
 var c=game.data.get("classes",{})
 for id in ["guard","oracle","hunter"]:
  var entry=c.get(id,{})
  var wrap=framed()
  var line=HBoxContainer.new()
  wrap.add_child(line)
  var sprite=ACTOR.new()
  sprite.kind=id
  sprite.custom_minimum_size=Vector2(115,145)
  line.add_child(sprite)
  var right=VBoxContainer.new()
  right.size_flags_horizontal=Control.SIZE_EXPAND_FILL
  line.add_child(right)
  right.add_child(make_text(str(entry.get("name","")),24,GOLD))
  right.add_child(make_text("❤ %d HP" % int(entry.get("hp",0)),16,QUIET))
  right.add_child(make_text(str(entry.get("desc","")),14,QUIET))
  wrap.add_child(make_button("Выбрать %s" % entry.get("name",""),_choose_class.bind(id)))
func _choose_class(id:String)->void:
 game.new_run(id)
 render_ui()

func stats()->void:
 var row=framed(false,Color("#191f2b"))
 row.add_child(make_text("❤ %d/%d" % [game.hp,game.max_hp],16,Color("#ffb2a9")))
 row.add_child(make_text("◈ %d" % game.gold,16,GOLD))
 row.add_child(make_text("🃏 %d" % game.deck.size(),16,QUIET))
 row.add_child(make_text("✧ %d" % game.relics.size(),16,QUIET))

func show_map() -> void:
 stats()
 if collection_visible:
  show_deck_list()
  return
 page.add_child(title("ДРЕВО СУДЕБ"))
 page.add_child(make_text("Этаж %d из 15. Ты можешь перейти только по соединённой ветви." % (game.floor+1),16,QUIET))
 var allowed=game.reachable()
 var row=framed()
 row.add_child(make_text("ДОСТУПНЫЕ ВСТРЕЧИ",18,GOLD))
 for id in allowed:
  var typ=str(game.map_rows[game.floor][id]["type"])
  row.add_child(make_button(symbol(typ)+"  "+type_name(typ),_choose_node.bind(id)))
 # A real Godot canvas with curved, connected paths and touchable nodes.
 page.add_child(make_button("КОЛОДА · "+str(game.deck.size())+" карт",_toggle_deck,true))
 var route_scroll=ScrollContainer.new()
 route_scroll.custom_minimum_size=Vector2(0,535)
 route_scroll.size_flags_horizontal=Control.SIZE_EXPAND_FILL
 route_scroll.horizontal_scroll_mode=ScrollContainer.SCROLL_MODE_DISABLED
 page.add_child(route_scroll)
 var route_view=ROUTE.new()
 route_view.setup(game)
 route_view.node_chosen.connect(_choose_node)
 route_scroll.add_child(route_view)
 # Pan the graph to the player's current floor; choices above remain fixed.
 route_scroll.call_deferred("set", "scroll_vertical", maxi(0,int(route_view.y_for(game.floor)-250.0)))

func _toggle_deck()->void:
 collection_visible=not collection_visible
 render_ui()

func show_deck_list()->void:
 page.add_child(title("ТВОЯ КОЛОДА"))
 page.add_child(make_button("Вернуться к маршруту",_toggle_deck))
 var grid=GridContainer.new()
 grid.columns=3
 grid.add_theme_constant_override("h_separation",6)
 grid.add_theme_constant_override("v_separation",9)
 page.add_child(grid)
 for i in range(game.deck.size()):
  grid.add_child(build_card(game.deck[i],i,"inspect"))

func _choose_node(i:int)->void:
 if game.select_node(i):render_ui()

func symbol(t:String)->String:
 return {"fight":"⚔","elite":"☠","event":"✧","shop":"◆","rest":"♨","treasure":"◇"}.get(t,"✦")
func type_name(t:String)->String:
 return {"fight":"Бой","elite":"Элита","event":"Событие","shop":"Магазин","rest":"Привал","treasure":"Сокровище"}.get(t,"Путь")

func show_fight() -> void:
 var f=game.fight
 if f.is_empty():
  return
 stats()
 var resources=framed(false,Color("#1b2837"))
 resources.add_child(make_text("⚡ %d/3" % f["energy"],22,Color("#a7deff")))
 resources.add_child(make_text("🛡 %d" % f["block"],19))
 resources.add_child(make_text("☠ %d" % f["poison"],15,Color("#a2dc9e")))
 resources.add_child(make_text("Ход %d" % f["round"],16,GOLD))
 var arena=framed()
 arena.add_child(make_text("Пепельная арена",20,GOLD))
 var fighters=HBoxContainer.new()
 fighters.add_theme_constant_override("separation",6)
 arena.add_child(fighters)
 var own=VBoxContainer.new()
 own.size_flags_horizontal=Control.SIZE_EXPAND_FILL
 fighters.add_child(own)
 var actor=ACTOR.new()
 actor.kind=game.hero
 actor.custom_minimum_size=Vector2(140,166)
 own.add_child(actor)
 own.add_child(make_text("ГЕРОЙ",16,GOLD))
 own.add_child(make_text("❤ %d/%d" % [game.hp,game.max_hp],15))
 var foes=VBoxContainer.new()
 foes.size_flags_horizontal=Control.SIZE_EXPAND_FILL
 fighters.add_child(foes)
 for i in range(f["enemies"].size()):
  var enemy=f["enemies"][i]
  if enemy["hp"]<=0:continue
  var preview=game.enemy_plan(enemy)
  var target=make_button("%s\n❤ %d/%d  🛡 %d\n%s %s" % [enemy["name"],enemy["hp"],enemy["max_hp"],enemy["block"],preview["icon"],preview["label"]],_select_enemy.bind(i),true)
  target.custom_minimum_size=Vector2(0,85)
  foes.add_child(target)
  enemy_controls.append({"node":target,"index":i})
 var potion_hud=framed(false)
 potion_hud.add_child(make_text("Зелья",15,QUIET))
 for i in range(game.potions.size()):
  var id=str(game.potions[i])
  var names={"salve":"❤","steel":"🛡","cleanser":"✧","aether":"⚡","ember":"🔥"}
  potion_hud.add_child(make_button(names.get(id,"✦"),_use_potion.bind(i),true))
 var hand=framed()
 hand.add_child(make_text("РУКА  •  выбери карту, затем цель",17,GOLD))
 var scroll_hand=ScrollContainer.new()
 scroll_hand.custom_minimum_size=Vector2(0,255)
 scroll_hand.horizontal_scroll_mode=ScrollContainer.SCROLL_MODE_AUTO
 scroll_hand.vertical_scroll_mode=ScrollContainer.SCROLL_MODE_DISABLED
 hand.add_child(scroll_hand)
 var hand_row=HBoxContainer.new()
 hand_row.add_theme_constant_override("separation",8)
 scroll_hand.add_child(hand_row)
 for i in range(f["hand"].size()):
  var holder=build_card(f["hand"][i],i,"combat")
  hand_row.add_child(holder)
 hand.add_child(make_text("В руке: %d · Колода: %d · Сброс: %d · Изгнание: %d" % [f["hand"].size(),f["library"].size(),f["discard"].size(),f["exhaust"].size()],14,QUIET))
 var turn=make_button("ЗАВЕРШИТЬ ХОД",_end_turn)
 turn.custom_minimum_size=Vector2(0,76)
 page.add_child(turn)
 for msg in game.logs.slice(maxi(0,game.logs.size()-3),game.logs.size()):
  page.add_child(make_text(str(msg),13,QUIET))

func _use_potion(index:int)->void:
 if game.use_potion(index):render_ui()
func _tap_card(index:int)->void:
 if game.fight.is_empty() or index>=game.fight["hand"].size():return
 selected_card=index
 # Skills can be played with one touch; attacks require target confirmation.
 var desc=game.card(str(game.fight["hand"][index]["id"]))
 if desc.get("t","skill")!="attack":
  if game.play_card(index,first_living()):render_ui()
 else:
  for item in enemy_controls:
   var n=item["node"] as Button
   n.add_theme_color_override("font_color",GOLD)
func _select_enemy(index:int)->void:
 if selected_card<0:
  game.fight["target"]=index
  return
 if game.play_card(selected_card,index):
  render_ui()
func first_living()->int:
 for i in range(game.fight["enemies"].size()):
  if game.fight["enemies"][i]["hp"]>0:return i
 return 0
func _end_turn()->void:
 game.end_turn()
 render_ui()

func build_card(entry:Dictionary,index:int,mode:String)->PanelContainer:
 var c=game.card(str(entry["id"]))
 var tier=str(c.get("rarity","common"))
 var kind=str(c.get("t","skill"))
 var color=Color("#693b39") if kind=="attack" else Color("#31536c") if kind=="skill" else Color("#5c4677")
 var outline=GOLD if tier=="rare" else Color("#85bad7") if tier=="uncommon" else Color("#94816a")
 var wrap=PanelContainer.new()
 wrap.custom_minimum_size=Vector2(196,300)
 wrap.add_theme_stylebox_override("panel",panel_style(color,outline,12))
 var stack=VBoxContainer.new()
 stack.add_theme_constant_override("separation",5)
 wrap.add_child(stack)
 var cost=make_text("ЭНЕРГИЯ %d  ·  %s" % [int(c.get("c",0)),tier.to_upper()],13,outline)
 stack.add_child(cost)
 stack.add_child(make_text(str(c.get("n","")),17,TEXT))
 var art=PanelContainer.new()
 art.custom_minimum_size=Vector2(0,105)
 art.add_theme_stylebox_override("panel",panel_style(Color("#1a1f2d"),outline.darkened(.3),3))
 var rune=RUNE.new()
 rune.code=str(entry["id"])
 rune.school=str(c.get("school",""))
 rune.mode=kind
 art.add_child(rune)
 stack.add_child(art)
 var description=str(c.get("d",""))
 if description.is_empty() and c.has("effects"):
  var elements=[]
  for fx in c["effects"]:
   elements.append(str(fx["key"])+" "+str(fx["value"]))
  description=" · ".join(elements)
 stack.add_child(make_text(description.substr(0,135),12,TEXT))
 if int(entry.get("up",0))>0:
  stack.add_child(make_text("✦ УЛУЧШЕНО",12,GOLD))
 var action: Button
 if mode=="reward":
  action=make_button("Добавить в колоду",_reward.bind(index),true)
 elif mode=="shop":
  action=make_button("Купить · %d золота" % game.shop_card_price(index),_buy_card.bind(index),true)
  action.disabled=game.gold<game.shop_card_price(index)
 elif mode=="upgrade":
  action=make_button("Улучшить",_upgrade_card.bind(index),true)
  action.disabled=int(entry.get("up",0))>0
 elif mode=="remove":
  action=make_button("Удалить за 75 золота",_remove_card.bind(index),true)
  action.disabled=game.gold<75 or game.shop_removed or game.deck.size()<=8
 elif mode=="inspect":
  action=make_button("Просмотр",func():pass,true)
  action.disabled=true
 else:
  action=make_button("Выбрать",_tap_card.bind(index),true)
  if mode=="combat" and int(c.get("c",99))>int(game.fight["energy"]):
   action.disabled=true
 action.custom_minimum_size=Vector2(0,48)
 action.add_theme_font_size_override("font_size",13)
 stack.add_spacer(false)
 stack.add_child(action)
 return wrap

func _card_grid(source:Array,mode:String)->void:
 var grid=GridContainer.new()
 grid.columns=3
 grid.add_theme_constant_override("h_separation",8)
 grid.add_theme_constant_override("v_separation",12)
 page.add_child(grid)
 for i in range(source.size()):
  grid.add_child(build_card(source[i],i,mode))

func show_rewards()->void:
 stats()
 page.add_child(title("СРАЖЕНИЕ ОКОНЧЕНО"))
 page.add_child(make_text("Выбери одну из трёх карт для колоды. Отказ тоже допустим.",17,QUIET))
 var cards=[]
 for id in game.reward_cards:cards.append(game.make_card(str(id)))
 _card_grid(cards,"reward")
 page.add_child(make_button("Не брать карту",_reward.bind(-1),true))

func _reward(index:int)->void:
 game.pick_reward(index)
 render_ui()

func show_shop()->void:
 stats()
 page.add_child(title("РЫНОК ПОД ПЕПЛОМ"))
 page.add_child(make_text("Цена учитывает редкость. Удаление слабых карт повышает стабильность колоды.",16,QUIET))
 var cards=[]
 for id in game.shop_cards:
  if str(id)!="":cards.append(game.make_card(str(id)))
 # Show actual original offer indexes; empty sold slots are skipped.
 for i in range(game.shop_cards.size()):
  if str(game.shop_cards[i])=="":continue
  page.add_child(build_card(game.make_card(str(game.shop_cards[i])),i,"shop"))
 for i in range(game.shop_relics.size()):
  var id=str(game.shop_relics[i])
  if id=="":continue
  var r=game.relic(id)
  page.add_child(make_button("✦ %s\n%s\n◈ 115" % [r.get("n",""),r.get("d","")],_buy_relic.bind(i),true))
 var options=framed()
 options.add_child(make_text("УСЛУГИ И ЗЕЛЬЯ",18,GOLD))
 var prices={"salve":35,"cleanser":40,"steel":42,"aether":58,"ember":48}
 if not game.shop_potion_sold:
  options.add_child(make_button("Купить зелье «%s» · %d золота" % [game.shop_potion,prices.get(game.shop_potion,50)],_buy_potion,true))
 if not game.shop_removed:
  options.add_child(make_button("Удалить карту из колоды · 75 золота",_open_remove,true))
 page.add_child(make_button("Уйти с рынка",_leave_shop))

func _buy_card(index:int)->void:
 game.buy_card(index)
 render_ui()
func _buy_relic(index:int)->void:
 game.buy_relic(index)
 render_ui()

func _buy_potion()->void:
 game.buy_potion()
 render_ui()
func _leave_shop()->void:
 game.next_floor()
 render_ui()
func _open_remove()->void:
 game.screen="remove"
 render_ui()
func _remove_card(index:int)->void:
 if game.remove_card(index):
  game.screen="shop"
 render_ui()
func _upgrade_card(index:int)->void:
 game.upgrade_card(index)
 render_ui()
func show_upgrade()->void:
 stats()
 page.add_child(title("КУЗНИЦА"))
 page.add_child(make_text("Выбери одну карту. Изменение сохраняется до конца забега.",17,QUIET))
 _card_grid(game.deck,"upgrade")
func show_remove()->void:
 stats()
 page.add_child(title("ОЧИЩЕНИЕ КОЛОДЫ"))
 page.add_child(make_text("Избавься от слабой карты, если у тебя достаточно золота.",17,QUIET))
 _card_grid(game.deck,"remove")
 page.add_child(make_button("Вернуться в магазин",_back_shop,true))
func _back_shop()->void:
 game.screen="shop"
 render_ui()
func show_rest()->void:
 stats()
 page.add_child(title("НОЧНОЙ ПРИВАЛ"))
 page.add_child(make_text("Восстановить силы сейчас или усилить карту на все будущие бои?",18,QUIET))
 page.add_child(make_button("❤ Восстановить 30% здоровья",_rest.bind(true)))
 page.add_child(make_button("⚒ Выбрать карту для улучшения",_rest.bind(false),true))
func _rest(heal_choice:bool)->void:
 game.rest(heal_choice)
 render_ui()
func show_treasure()->void:
 stats()
 page.add_child(title("ЗАБЫТЫЙ ТАЙНИК"))
 for i in range(game.shop_relics.size()):
  var id=str(game.shop_relics[i]);var r=game.relic(id)
  page.add_child(make_button("%s  %s\n%s" % [r.get("e","✦"),r.get("n",""),r.get("d","")],_treasure.bind(id),true))
func _treasure(id:String)->void:
 game.gain_relic(id)
 game.next_floor()
 render_ui()
func show_event()->void:
 stats()
 var ev=game.active_event
 page.add_child(title(str(ev.get("n","Неизвестность"))))
 page.add_child(make_text(str(ev.get("d","")),20,QUIET))
 for i in range(ev.get("options",[]).size()):
  page.add_child(make_button(str(ev["options"][i][0]),_choose_event.bind(i),true))
func _choose_event(i:int)->void:
 game.event_option(i)
 render_ui()

func show_final()->void:
 page.add_child(title("ПУТЬ ПРОЙДЕН"))
 var text="Колода: %d карт, реликвий: %d. Сетевой PvP будет перенесён после проверки этого нативного игрового клиента." % [game.deck.size(),game.relics.size()]
 page.add_child(make_text(text,19))
 page.add_child(make_button("Новый забег",_restart))
func show_lost()->void:
 page.add_child(title("ТЫ ПАЛ"))
 page.add_child(make_text("Герой не добрался до конца пути.",20))
 page.add_child(make_button("Попробовать снова",_restart))
func _restart()->void:
 game.delete_save()
 game.screen="lobby"
 render_ui()
