extends Control
## Original generated artwork for card/relic vignettes, with an etched Art Nouveau frame.
const ART = preload("res://scripts/painted_assets.gd")
var code: String = "forge001"
var school: String = ""
var mode: String = "skill"
var phase: float = 0.0
func _ready()->void:
 mouse_filter=Control.MOUSE_FILTER_IGNORE
 size_flags_horizontal=Control.SIZE_EXPAND_FILL
 custom_minimum_size=Vector2(0,108)
 queue_redraw()
func _draw()->void:
 if size.x<2 or size.y<2:return
 var r=Rect2(Vector2.ZERO,size)
 var tint=Color("#8bc7e5") if mode=="skill" else Color("#d88b74") if mode=="attack" else Color("#c9a1df")
 draw_rect(r,Color("#0f1520"))
 var idx=ART.card_index(code,school,mode)
 draw_texture_rect_region(ART.ATLAS,r,ART.card_region(idx),Color.WHITE)
 draw_rect(r.grow(-1),Color("#d7ba8c",.60),false,2)
 draw_line(Vector2(5,5),Vector2(21,5),tint,2)
 draw_line(Vector2(5,5),Vector2(5,20),tint,2)
 draw_line(Vector2(size.x-5,5),Vector2(size.x-21,5),tint,2)
 draw_line(Vector2(size.x-5,5),Vector2(size.x-5,20),tint,2)
 draw_line(Vector2(5,size.y-5),Vector2(21,size.y-5),tint,2)
 draw_line(Vector2(size.x-5,size.y-5),Vector2(size.x-21,size.y-5),tint,2)
