extends Control
## Pixel-drawn card illustrations, never resizing the source pixels with blending.
const PX=preload("res://scripts/pixel_forge.gd")
var code:String="strike"
var school:String=""
var mode:String="skill"
func _ready()->void:
 mouse_filter=Control.MOUSE_FILTER_IGNORE
 size_flags_horizontal=Control.SIZE_EXPAND_FILL
 custom_minimum_size=Vector2(0,108)
 texture_filter=CanvasItem.TEXTURE_FILTER_NEAREST
 queue_redraw()
func _draw()->void:
 if size.x<48 or size.y<32:return
 var zoom=maxi(1,mini(int(size.x/48.0),int(size.y/32.0)))
 var dims=Vector2(48*zoom,32*zoom)
 var pos=Vector2(floor((size.x-dims.x)*.5),floor((size.y-dims.y)*.5))
 draw_rect(Rect2(Vector2.ZERO,size),Color("#151b29"))
 draw_texture_rect(PX.card_art(code,school,mode),Rect2(pos,dims),false)
 draw_rect(Rect2(pos,dims),Color("#9b7b63"),false,2.0)
