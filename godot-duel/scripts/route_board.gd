extends Control
const PX=preload("res://scripts/pixel_forge.gd")
## Native Godot interactive route graph: a real directed tree/DAG.
signal node_chosen(index: int)
var state: RefCounted
var canvas_height: float = 1450.0
var node_buttons: Array = []
var active: Array = []
const GOLD = Color("#edc58b")
const PATH = Color("#8b7b6d")
const GREEN = Color("#91c7a6")
const DARK = Color("#28303d")
const EMOJI = {"fight":"⚔","elite":"☠","event":"✧","shop":"◆","rest":"♨","treasure":"◇"}

func setup(game:RefCounted)->void:
 state=game
 active=state.reachable()
 custom_minimum_size=Vector2(0,canvas_height)
 size_flags_horizontal=Control.SIZE_EXPAND_FILL
 if is_inside_tree():
  _build_nodes()
  queue_redraw()

func _ready()->void:
 _build_nodes()
 queue_redraw()

func _notification(what:int)->void:
 if what==NOTIFICATION_RESIZED and state!=null:
  _reposition()
  queue_redraw()

func y_for(row:int)->float:
 return canvas_height-75.0-row*89.0

func x_for(node:Dictionary)->float:
 return 45.0+float(node["x"])*maxf(140.0,size.x-90.0)

func _build_nodes()->void:
 if state==null:return
 for button in node_buttons:
  if is_instance_valid(button):
   button.queue_free()
 node_buttons.clear()
 for row in range(state.map_rows.size()):
  for item in state.map_rows[row]:
   var button=Button.new()
   var index=int(item["index"])
   var available=row==state.floor and active.has(index)
   var visited=state.history.has([row,index])
   button.text=EMOJI.get(str(item["type"]),"✦")
   button.tooltip_text=str(item["type"])+" · "+str(row+1)+" этаж"
   button.custom_minimum_size=Vector2(61,53)
   button.add_theme_font_size_override("font_size",23)
   button.add_theme_color_override("font_color",GOLD if available else GREEN if visited else PATH)
   button.disabled=not available
   var style=StyleBoxFlat.new()
   style.bg_color=Color("#6c4234") if available else Color("#25443b") if visited else DARK
   style.border_color=GOLD if available else GREEN if visited else Color("#53606c")
   style.set_border_width_all(2)
   style.set_corner_radius_all(0)
   button.add_theme_stylebox_override("normal",style)
   button.add_theme_stylebox_override("disabled",style)
   if available:button.pressed.connect(_emit_choice.bind(index))
   add_child(button)
   node_buttons.append(button)
 _reposition()

func _emit_choice(index:int)->void:
 node_chosen.emit(index)

func _reposition()->void:
 if state==null or node_buttons.is_empty():return
 var idx=0
 for row in range(state.map_rows.size()):
  for item in state.map_rows[row]:
   var button:Button=node_buttons[idx]
   var center=Vector2(x_for(item),y_for(row))
   button.position=center-Vector2(30,26)
   button.size=Vector2(61,53)
   idx+=1

func _draw()->void:
 if state==null:return
 draw_rect(Rect2(0,0,size.x,canvas_height),Color("#211f2b"))
 draw_texture_rect(PX.scene(),Rect2(0,0,size.x,canvas_height),false,Color(1,1,1,.09))
 # Terrain parchment grid.
 for y in range(0,int(canvas_height),36):
  draw_line(Vector2(0,y),Vector2(size.x,y),Color("#5b5360",.09),1.0)
 for edge in state.map_edges:
  var row=int(edge[0]);var source=int(edge[1]);var target=int(edge[2])
  var from_node:Dictionary=state.map_rows[row][source]
  var to_node:Dictionary=state.map_rows[row+1][target]
  var p1=Vector2(x_for(from_node),y_for(row))
  var p2=Vector2(x_for(to_node),y_for(row+1))
  var points=PackedVector2Array()
  for j in range(13):
   var t=float(j)/12.0
   var x=lerpf(p1.x,p2.x,t)
   var y=lerpf(p1.y,p2.y,t)
   # Light curved links, visually distinguish forks and convergences.
   x+=sin(t*PI)*((p1.x-p2.x)*.13)
   points.append(Vector2(x,y))
  var chosen=false
  for i in range(1,state.history.size()):
   var a=state.history[i-1];var b=state.history[i]
   if a[0]==row and a[1]==source and b[0]==row+1 and b[1]==target:
    chosen=true
  var reachable=row==state.floor-1 and state.position==source and active.has(target)
  draw_polyline(points,GREEN if chosen else GOLD if reachable else PATH,5.0 if chosen else 3.0,false)
