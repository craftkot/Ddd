extends Control
## Painted portrait from original image-generated atlas; animated on the GPU.
const ART = preload("res://scripts/painted_assets.gd")
var kind: String = "guard"
var seed: float = 0.0
var hp_fraction: float = 1.0
var walking: bool = true

func _ready() -> void:
 custom_minimum_size = Vector2(130,168)
 mouse_filter=Control.MOUSE_FILTER_IGNORE
 set_process(true)
 queue_redraw()

func _process(delta: float) -> void:
 if walking:
  seed+=delta
  queue_redraw()

func _draw() -> void:
 var w=size.x
 var h=size.y
 if w<4 or h<4:return
 var sx=w*.5
 var sw=minf(w*.91,h*.92)
 var sh=minf(h*.93,w*1.51)
 var bob=sin(seed*1.55)*2.5
 var center=Vector2(sx,h*.54+bob)
 draw_circle(center+Vector2(0,h*.04),minf(w*.30,h*.17),Color("#d77e5c",.07))
 draw_arc(center+Vector2(0,-h*.14),minf(w*.35,h*.23),0,TAU,70,Color("#b78b63",.42),1.4)
 draw_rect(Rect2(sx-w*.31,h*.91,w*.62,7),Color(0,0,0,.47),true)
 draw_texture_rect_region(ART.ATLAS,Rect2(sx-sw*.5,h*.48-sh*.5+bob,sw,sh),ART.character_region(kind),Color.WHITE)
 if hp_fraction<.55:
  draw_arc(center,minf(w,h)*.46,PI*.1,PI*.91,22,Color("#a84848",.62),2.0)
