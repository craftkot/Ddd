extends Control
## Authored pixel-by-pixel 32x48 bitmap sprites, enlarged only by integer scales.
const PX = preload("res://scripts/pixel_forge.gd")
var kind:String="guard"
var seed:float=0.0
var hp_fraction:float=1.0
var walking:bool=true

func _ready()->void:
 custom_minimum_size=Vector2(132,192)
 mouse_filter=Control.MOUSE_FILTER_IGNORE
 texture_filter=CanvasItem.TEXTURE_FILTER_NEAREST
 set_process(true)

func _process(delta:float)->void:
 if walking:
  seed+=delta
  queue_redraw()

func _draw()->void:
 if size.x<2 or size.y<2:return
 var scale_factor=maxi(1,mini(int(size.x/32.0),int(size.y/48.0)))
 var dims=Vector2(32*scale_factor,48*scale_factor)
 var shift=roundi(sin(seed*2.0)*2.0)
 var origin=Vector2(floor((size.x-dims.x)*.5),floor((size.y-dims.y)*.5)+shift)
 draw_rect(Rect2(origin.x+scale_factor*7,origin.y+dims.y-4,scale_factor*18,3),Color("#0b1019",.7))
 draw_texture_rect(PX.actor(kind),Rect2(origin,dims),false,Color.WHITE)
 if hp_fraction<.3:
  draw_rect(Rect2(origin.x,origin.y+3,dims.x,2),Color("#e16f6c",.67))
