extends Control
## Small original procedural vignette, generated consistently from card ID.
var code: String = "forge001"
var school: String = ""
var mode: String = "skill"
var phase: float = 0.0
func _ready()->void:
 mouse_filter=Control.MOUSE_FILTER_IGNORE
 size_flags_horizontal=Control.SIZE_EXPAND_FILL
 custom_minimum_size=Vector2(0,108)
func _draw()->void:
 var r=Rect2(Vector2.ZERO,size)
 var hue=Color("#526986") if mode=="skill" else Color("#a65a52") if mode=="attack" else Color("#76648c")
 if school=="Яд" or school=="Лечение":hue=Color("#668c63")
 if school=="Огонь":hue=Color("#ad754b")
 draw_rect(r,Color("#1c2030"))
 for i in range(13):
  var t=float(i)/13.0
  draw_rect(Rect2(0,size.y*t,size.x,size.y/13.0+1),Color("#161d2c").lerp(hue.darkened(.4),t*.75))
 var seed=int(abs(hash(code)))
 var center=size*.5
 draw_circle(center,size.y*.32,hue.darkened(.2))
 draw_arc(center,size.y*.33,0,TAU,48,Color("#d8bd8e"),2)
 var pattern=seed%5
 match pattern:
  0:
   draw_line(center+Vector2(-30,26),center+Vector2(24,-35),Color("#f0d29d"),8)
   draw_line(center+Vector2(7,-36),center+Vector2(37,-24),Color("#fff2d5"),3)
  1:
   draw_colored_polygon(PackedVector2Array([center+Vector2(0,-32),center+Vector2(29,-7),center+Vector2(12,28),center+Vector2(-23,28),center+Vector2(-29,-10)]),hue.lightened(.35))
   draw_line(center+Vector2(-13,1),center+Vector2(0,15),Color("#fff2d5"),4)
  2:
   for i in range(3):
    draw_arc(center,12+i*12,PI*.5,TAU,30,Color("#d9cba8"),3)
  3:
   draw_circle(center,14,Color("#f5d49c"))
   draw_line(center+Vector2(-37,0),center+Vector2(37,0),Color("#f1c784"),4)
   draw_line(center+Vector2(0,-37),center+Vector2(0,37),Color("#f1c784"),4)
  _:
   draw_colored_polygon(PackedVector2Array([center+Vector2(-25,-17),center+Vector2(0,-34),center+Vector2(25,-17),center+Vector2(20,16),center+Vector2(0,33),center+Vector2(-20,16)]),hue.lightened(.5))
 for i in range(5):
  var angle=float((seed>>i)%360)*PI/180.0
  var v=Vector2(cos(angle),sin(angle))*size.y*.48
  draw_circle(center+v,1.8,Color("#ffe4ba",.7))
