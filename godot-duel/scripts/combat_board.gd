extends Control
## Native touch battle with original generated 2D painting atlases.
const PX = preload("res://scripts/pixel_forge.gd")
signal transition_requested
var game: RefCounted
var chosen: int = -1
var held: int = -1
var pressed_at: Vector2 = Vector2.ZERO
var finger: Vector2 = Vector2.ZERO
var moving: bool = false
var hit_areas: Array = []
var hand_areas: Array = []
var finish_area: Rect2 = Rect2()
var potion_areas: Array = []
var hit_flash: float = 0.0
var last_damage: int = 0
var target_marker: int = -1
var tick: float = 0.0
const FONT_SIZE = 19
const GOLD = Color("#eac69a")
const WHITE = Color("#f8ecde")
const MUTED = Color("#c0afab")
const SKY = Color("#95c5e7")
const RED = Color("#d87467")
const GREEN = Color("#9bca99")
const INK = Color("#0d111b")
const EDGE = Color("#72635d")

func _ready() -> void:
 mouse_filter = Control.MOUSE_FILTER_STOP
 texture_filter=CanvasItem.TEXTURE_FILTER_NEAREST
 size_flags_vertical = Control.SIZE_EXPAND_FILL
 size_flags_horizontal = Control.SIZE_EXPAND_FILL
 set_process(true)

func _process(delta: float) -> void:
 tick += delta
 if hit_flash > 0.0:
  hit_flash = maxf(0.0,hit_flash-delta)
 queue_redraw()

func _font():
 return ThemeDB.fallback_font

func word_line(value: String, width: float, font_size: int, max_rows: int=4) -> Array:
 var words = value.replace("\n"," ").split(" ",false)
 var output: Array = []
 var row = ""
 for word in words:
  var probe = (row+" "+word).strip_edges()
  if _font().get_string_size(probe,HORIZONTAL_ALIGNMENT_LEFT,-1,font_size).x > width and row != "":
   output.append(row)
   row = word
   if output.size() >= max_rows:
    break
  else:
   row=probe
 if output.size()<max_rows and row!="":
  output.append(row)
 return output

func label(text: String, pos: Vector2, size_px: int=16, color: Color=WHITE, alignment: HorizontalAlignment=HORIZONTAL_ALIGNMENT_LEFT, width: float=-1.0) -> void:
 draw_string(_font(),pos,text,alignment,width,size_px,color)

func plate(r: Rect2, background: Color, line: Color, radius: float=12.0) -> void:
 draw_rect(r, background,true)
 draw_rect(r,line,false,2.0)

func _draw() -> void:
 if game == null or game.fight.is_empty():return
 var w=size.x
 var h=size.y
 if w<320 or h<460:return
 hit_areas.clear()
 hand_areas.clear()
 potion_areas.clear()
 var dusk=Color("#101522")
 draw_rect(Rect2(Vector2.ZERO,size),dusk)
 # 192x128 texel authored scene, nearest-neighbour magnification.
 var scene_rect=Rect2(0,h*.078,w,h*.49)
 draw_texture_rect(PX.scene(),scene_rect,false,Color(1,1,1,.98))
 draw_rect(Rect2(0,h*.53,w,h*.053),Color("#111624",.55))
 for i in range(10):
  var xx=fmod(float(i*81)+tick*float(i%3+1)*6.0,w)
  var yy=h*.19+fmod(float(i*37)-tick*8.0,h*.31)
  draw_rect(Rect2(roundi(xx),roundi(yy),3,3),Color("#b47759",.45))
 # Status and resources.
 plate(Rect2(9,9,w-18,69),Color("#1b2131",.98),Color("#c6a178"))
 label("ЭТАЖ %d  ·  ХОД %d" % [game.floor+1,game.fight["round"]],Vector2(22,32),15,GOLD)
 label("ЗДОРОВЬЕ %d/%d" % [game.hp,game.max_hp],Vector2(22,57),19,WHITE)
 label("ЭНЕРГИЯ %d" % game.fight["energy"],Vector2(w-185,35),19,SKY)
 label("БРОНЯ %d" % game.fight["block"],Vector2(w-185,58),17,GREEN)
 label("ЦЕПЬ %d/3" % mini(3,int(game.fight.get("combo",0))),Vector2(w*.47,57),13,GOLD)
 # Battles must show predicted intent before choice.
 var foe_count=0
 var foes=game.fight["enemies"]
 for e in foes:
  if int(e["hp"])>0:foe_count+=1
 var cursor=0
 var y=h*.27
 var range_w=w*.77
 var e_gap=minf(132.0,range_w/maxf(1,foe_count))
 var x0=w*.60-(foe_count-1)*e_gap*.25
 for i in range(foes.size()):
  var e:Dictionary=foes[i]
  if int(e["hp"])<=0:continue
  var ix=cursor
  cursor+=1
  var x=x0+(float(ix)-(foe_count-1)*.5)*e_gap
  var r=Rect2(x-58,y-65,116,207)
  hit_areas.append({"index":i,"rect":r})
  var focused=(chosen>=0 or held>=0) and (target_marker<0 or target_marker==i)
  if focused:
   plate(r.grow(3),Color("#b96c4e",.12),GOLD)
  _draw_figure(Vector2(x,y+12),e.get("kind","bandit"),.88)
  var plan:Dictionary=game.enemy_plan(e)
  plate(Rect2(x-70,y-95,140,45),Color("#171823",.98),RED if int(plan.get("damage",0))>0 else SKY)
  label(str(plan.get("icon","!"))+" "+str(plan.get("label","")),Vector2(x-64,y-68),13,GOLD)
  var name=word_line(str(e["name"]),130,13,2)
  for j in range(name.size()):
   label(str(name[j]),Vector2(x-66,y+76+j*15),13,WHITE)
  var frac=clampf(float(e["hp"])/maxf(1,int(e["max_hp"])),0,1)
  draw_rect(Rect2(x-64,y+102,128,11),Color("#481f2b"))
  draw_rect(Rect2(x-64,y+102,128*frac,11),RED)
  label("%d/%d  [Б %d]" % [int(e["hp"]),int(e["max_hp"]),int(e["block"])],Vector2(x-60,y+128),13,WHITE)
  var dots=""
  for key in ["poison","bleed","burn","weak","vuln"]:
   if int(e.get(key,0))>0:
    dots+=str(key).substr(0,1).to_upper()+str(e[key])+" "
  if dots!="":label(dots,Vector2(x-65,y+146),12,GOLD)
 _draw_figure(Vector2(w*.23,h*.43),game.hero,1.16)
 label("ТЫ  •  "+game.data["classes"][game.hero]["name"],Vector2(16,h*.54),15,WHITE)
 label("ЯД %d  КРОВЬ %d  ГОРЕНИЕ %d" % [game.fight["poison"],game.fight["bleed"],game.fight["burn"]],Vector2(16,h*.565),13,MUTED)
 # Dividing ironwork and compact potions, with large tappable hitboxes.
 draw_line(Vector2(14,h*.585),Vector2(w-14,h*.585),EDGE,2)
 var potx=18.0
 for i in range(game.potions.size()):
  var p=str(game.potions[i])
  var rx=Rect2(potx+i*92,h*.592,85,48)
  potion_areas.append({"index":i,"rect":rx})
  plate(rx,Color("#303c45"),SKY)
  label({"salve":"ЛЕЧ","cleanser":"ОЧИСТ","steel":"ЩИТ","aether":"ЭФИР","ember":"ОГОНЬ"}.get(p,"ЗЕЛЬЕ"),Vector2(rx.position.x+7,rx.position.y+29),13,GOLD)
 # Hand occupies the bottom third and remains onscreen, regardless of actions.
 var cards=game.fight["hand"]
 var count=cards.size()
 var card_w=minf(188.0,maxf(118.0,w*.254))
 var card_h=minf(268.0,maxf(184.0,h*.260))
 var overlap=minf(card_w*.72,(w-card_w-30.0)/maxf(1,count-1))
 var center=(count-1)*.5
 var baseline=h-82.0-card_h
 for i in range(count):
  var shift=(i-center)
  var x=w*.5-card_w*.5+shift*overlap
  var yy=baseline+absf(shift)*4.0
  if chosen==i:yy-=38.0
  var cardrect=Rect2(roundi(x),roundi(yy),roundi(card_w),roundi(card_h))
  hand_areas.append(cardrect)
 # Draw selected card LAST, so it is never hidden under the rest of the fan.
 for i in range(count):
  if i==held or i==chosen:continue
  _card(game.fight["hand"][i],hand_areas[i],false,false)
 if chosen>=0 and chosen<count and chosen!=held:
  _card(game.fight["hand"][chosen],hand_areas[chosen],true,false)
 if held>=0 and held<count:
  var held_rect=Rect2(finger.x-card_w*.5,finger.y-card_h*.86,card_w,card_h)
  _card(game.fight["hand"][held],held_rect,true,true)
  if _needs_target(game.fight["hand"][held]):
   _aim(pressed_at,finger)
 elif chosen>=0 and chosen<count and _needs_target(game.fight["hand"][chosen]):
  label("ВЫБЕРИ ВРАГА ДЛЯ АТАКИ",Vector2(w*.5-135,h*.62),16,GOLD)
 # Visible active pile counts and always-available end-turn button.
 var footer=Rect2(0,h-75,w,75)
 plate(footer,Color("#121927"),GOLD)
 label("КОЛОДА %d  ·  СБРОС %d  ·  ИЗГНАНО %d" % [game.fight["library"].size(),game.fight["discard"].size(),game.fight["exhaust"].size()],Vector2(12,h-52),13,MUTED)
 label("ПЕРЕТАЩИ НА ВРАГА",Vector2(12,h-24),12,GOLD)
 finish_area=Rect2(w-190,h-70,183,62)
 plate(finish_area,Color("#884034"),GOLD)
 label("КОНЕЦ ХОДА",Vector2(w-179,h-32),17,WHITE)
 if hit_flash>0:
  draw_rect(Rect2(Vector2.ZERO,size),Color("#ffdeb2",hit_flash*.18),true)
  label("−"+str(last_damage),Vector2(w*.58,h*.18-hit_flash*22),29,RED)

func _draw_figure(center:Vector2,kind:String,scale:float)->void:
 var bob=roundi(sin(tick*1.65+center.x*.01)*2.0)
 var factor=maxi(2,int(round(scale*4.0)))
 var dims=Vector2(32*factor,48*factor)
 var destination=Rect2(roundi(center.x-dims.x*.5),roundi(center.y-dims.y*.62)+bob,dims.x,dims.y)
 draw_rect(Rect2(center.x-dims.x*.35,center.y+dims.y*.37,dims.x*.7,4),Color("#0b1018",.6))
 draw_texture_rect(PX.actor(kind),destination,false)
 if kind=="boss":
  draw_rect(Rect2(destination.position.x,destination.position.y-6,dims.x,3),Color("#e5a278",.75))

func _card(entry:Dictionary,r:Rect2,selected:bool=false,floating:bool=false)->void:
 var definition:Dictionary=game.card(str(entry["id"]))
 var kind=str(definition.get("t","skill"))
 var rarity=str(definition.get("rarity","common"))
 var primary=RED if kind=="attack" else SKY if kind=="skill" else Color("#a48ed2")
 var frame=GOLD if rarity=="rare" else Color("#8cc4df") if rarity=="uncommon" else Color("#a89c91")
 var valid=int(definition.get("c",0))<=int(game.fight["energy"])
 plate(r.grow(3 if selected else 0),Color("#27222c"),frame)
 plate(Rect2(r.position+Vector2(5,5),r.size-Vector2(10,10)),primary.darkened(.64),frame.darkened(.3))
 var cost_at=r.position+Vector2(24,22)
 draw_rect(Rect2(cost_at.x-18,cost_at.y-18,36,36),Color("#27547b"))
 draw_rect(Rect2(cost_at.x-18,cost_at.y-18,36,36),SKY,false,3)
 label(str(definition.get("c",0)),cost_at+Vector2(-7,8),21,WHITE)
 var cname=str(definition.get("n","Карта"))
 var cap=word_line(cname,r.size.x-58,15,2)
 for i in range(cap.size()):
  label(str(cap[i]),r.position+Vector2(46,18+i*19),15,WHITE)
 var art=Rect2(r.position+Vector2(9,58),Vector2(r.size.x-18,r.size.y*.36))
 plate(art,Color("#1d2331"),frame.darkened(.35))
 # Each source card is authored at 48x32 logical pixels and scaled in whole pixels.
 var pixel=PX.card_art(str(entry["id"]),str(definition.get("school","")),kind)
 var zoom=maxi(1,mini(int((art.size.x-4.0)/48.0),int((art.size.y-4.0)/32.0)))
 var dimensions=Vector2(48*zoom,32*zoom)
 var artwork=Rect2(roundi(art.position.x+(art.size.x-dimensions.x)*.5),
  roundi(art.position.y+(art.size.y-dimensions.y)*.5),dimensions.x,dimensions.y)
 draw_texture_rect(pixel,artwork,false)
 draw_rect(artwork,frame,false,1.0)
 var body=Rect2(r.position.x+9,art.end.y+6,r.size.x-18,r.end.y-art.end.y-29)
 plate(body,Color("#171b26"),frame.darkened(.25))
 var desc=game.card_description(entry)
 var row=word_line(desc,body.size.x-9,11,4)
 for i in range(row.size()):
  label(str(row[i]),body.position+Vector2(6,14+i*15),11,WHITE)
 label(kind.to_upper(),r.position+Vector2(11,r.size.y-9),11,frame)
 if not valid:
  draw_rect(r,Color(0,0,0,.37),true)
 if floating:
  draw_rect(r.grow(4),Color(1,.78,.43,.07),false,3)

func _needs_target(entry:Dictionary)->bool:
 var definition:Dictionary=game.card(str(entry["id"]))
 if definition.get("t","")=="attack":return true
 if definition.has("effects"):
  for fx in definition["effects"]:
   if str(fx["key"]) in ["poison","burn","bleed","weak","vuln","mark","detonate","slow","delay","stun","spread","pierce","shieldhit","lifesteal","execute"]:
    return true
 return false

func _aim(start:Vector2,endpoint:Vector2)->void:
 var points=PackedVector2Array()
 for j in range(16):
  var t=float(j)/15.0
  var p=start.lerp(endpoint,t)
  p.y-=sin(t*PI)*55.0
  points.append(p)
 draw_polyline(points,Color("#d86a50",.45),12,true)
 draw_polyline(points,GOLD,3,true)
 draw_arc(endpoint,15,0,TAU,26,GOLD,2)

func _pick_card(pos:Vector2)->int:
 for i in range(hand_areas.size()-1,-1,-1):
  if hand_areas[i].has_point(pos):return i
 return -1
func _pick_enemy(pos:Vector2)->int:
 for item in hit_areas:
  if item["rect"].has_point(pos):return int(item["index"])
 return -1

func _press(pos:Vector2)->void:
 pressed_at=pos
 finger=pos
 held=_pick_card(pos)
 moving=false
func _move(pos:Vector2)->void:
 if held<0:return
 finger=pos
 if pos.distance_to(pressed_at)>18:moving=true
 queue_redraw()
func _release(pos:Vector2)->void:
 if game==null or game.screen!="fight":return
 if held>=0:
  var index=held
  held=-1
  var chosen_enemy=_pick_enemy(pos)
  if moving:
   if chosen_enemy>=0:
    _play(index,chosen_enemy)
   elif not _needs_target(game.fight["hand"][index]) and pos.y<pressed_at.y-55:
    _play(index,_living_target())
   else:chosen=index
  else:
   if _needs_target(game.fight["hand"][index]):
    chosen=-1 if chosen==index else index
   else:_play(index,_living_target())
  queue_redraw()
  return
 if finish_area.has_point(pos):
  game.end_turn()
  chosen=-1
  transition_requested.emit()
  return
 for item in potion_areas:
  if item["rect"].has_point(pos):
   game.use_potion(int(item["index"]))
   transition_requested.emit()
   return
 var foe=_pick_enemy(pos)
 if foe>=0:
  target_marker=foe
  game.fight["target"]=foe
  if chosen>=0:_play(chosen,foe)
  queue_redraw()

func _living_target()->int:
 for i in range(game.fight["enemies"].size()):
  if game.fight["enemies"][i]["hp"]>0:return i
 return 0

func _play(index:int,target:int)->void:
 if index<0 or index>=game.fight["hand"].size():return
 var before=0
 if target>=0 and target<game.fight["enemies"].size():
  before=int(game.fight["enemies"][target]["hp"])
 if game.play_card(index,target):
  var after=0
  if game.screen=="fight" and target<game.fight["enemies"].size():
   after=int(game.fight["enemies"][target]["hp"])
  last_damage=maxi(0,before-after)
  hit_flash=.30
  chosen=-1
  transition_requested.emit()

func _gui_input(event:InputEvent)->void:
 if event is InputEventScreenTouch:
  if event.pressed:_press(event.position)
  else:_release(event.position)
  accept_event()
 elif event is InputEventScreenDrag:
  _move(event.position)
  accept_event()
 elif event is InputEventMouseButton and event.button_index==MOUSE_BUTTON_LEFT:
  if event.pressed:_press(event.position)
  else:_release(event.position)
  accept_event()
 elif event is InputEventMouseMotion and held>=0:
  _move(event.position)
  accept_event()
