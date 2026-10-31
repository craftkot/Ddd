extends Control
## Minimal original vector actor, drawn by Godot instead of a browser canvas.
var kind: String = "guard"
var seed: float = 0.0
var hp_fraction: float = 1.0
var walking: bool = true
func _ready() -> void:
 custom_minimum_size = Vector2(130, 168)
 mouse_filter = Control.MOUSE_FILTER_IGNORE
func _process(delta:float) -> void:
 if walking:
  seed += delta
  queue_redraw()
func _draw() -> void:
 var w=size.x;var h=size.y
 var cx=w*.5
 var wave=sin(seed*2.0)*2.5
 var shadow=Color("#080e19")
 var trim=Color("#f3c28e")
 var cloth=Color("#647a96")
 if kind=="oracle" or kind=="mage" or kind=="cultist":cloth=Color("#8e65af");trim=Color("#d5adef")
 if kind=="hunter" or kind=="slime" or kind=="hound":cloth=Color("#598d76");trim=Color("#bfd5ac")
 if kind=="boss" or kind=="bandit":cloth=Color("#a65757");trim=Color("#f2a77c")
 draw_set_transform(Vector2.ZERO)
 draw_rect(Rect2(cx-w*.33,h*.93,w*.66,7),Color(0,0,0,.5),true)
 # Shrouded body, cape and shoulders.
 var y=wave
 draw_colored_polygon(PackedVector2Array([Vector2(cx-22,h*.30+y),Vector2(cx+21,h*.3+y),Vector2(cx+49,h*.87+y),Vector2(cx-44,h*.87+y)]),shadow)
 draw_colored_polygon(PackedVector2Array([Vector2(cx-18,h*.38+y),Vector2(cx+19,h*.38+y),Vector2(cx+34,h*.84+y),Vector2(cx-32,h*.84+y)]),cloth)
 draw_line(Vector2(cx,h*.43+y),Vector2(cx,h*.8+y),trim,3)
 draw_colored_polygon(PackedVector2Array([Vector2(cx-21,h*.37+y),Vector2(cx-39,h*.58+y),Vector2(cx-27,h*.62+y),Vector2(cx-6,h*.42+y)]),cloth.darkened(.2))
 draw_colored_polygon(PackedVector2Array([Vector2(cx+20,h*.38+y),Vector2(cx+40,h*.59+y),Vector2(cx+29,h*.64+y),Vector2(cx+6,h*.42+y)]),cloth.darkened(.2))
 # Face silhouette and glowing eyes.
 draw_circle(Vector2(cx,h*.275+y),h*.11,Color("#cda88e"))
 draw_colored_polygon(PackedVector2Array([Vector2(cx-25,h*.30+y),Vector2(cx-23,h*.10+y),Vector2(cx,h*.06+y),Vector2(cx+24,h*.11+y),Vector2(cx+26,h*.30+y),Vector2(cx+16,h*.25+y),Vector2(cx-14,h*.25+y)]),shadow)
 draw_line(Vector2(cx-12,h*.24+y),Vector2(cx-4,h*.24+y),trim,3)
 draw_line(Vector2(cx+6,h*.24+y),Vector2(cx+14,h*.24+y),trim,3)
 # Equipment changes by archetype.
 if kind=="guard" or kind=="knight" or kind=="boss":
  draw_colored_polygon(PackedVector2Array([Vector2(cx-29,h*.5+y),Vector2(cx-50,h*.46+y),Vector2(cx-51,h*.71+y),Vector2(cx-31,h*.82+y),Vector2(cx-15,h*.68+y)]),Color("#39475a"))
  draw_line(Vector2(cx-40,h*.51+y),Vector2(cx-38,h*.73+y),trim,3)
 else:
  draw_line(Vector2(cx+28,h*.2+y),Vector2(cx+49,h*.83+y),trim,5)
  draw_circle(Vector2(cx+28,h*.2+y),10,trim)
