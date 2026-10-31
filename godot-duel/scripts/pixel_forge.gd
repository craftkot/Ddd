extends RefCounted
## TRUE source-resolution pixel art. Every sprite is authored from exact integer-pixel
## rectangles at 32x48 / 48x32. No post-process pixelation, no smooth filtering.
const OUTLINE=Color("#101620")
const PARCHMENT=Color("#e8d0a3")
static var _cache:Dictionary={}

static func _rect(img:Image,x:int,y:int,w:int,h:int,col:Color)->void:
 for yy in range(maxi(y,0),mini(y+h,img.get_height())):
  for xx in range(maxi(x,0),mini(x+w,img.get_width())):
   img.set_pixel(xx,yy,col)

static func _dot(img:Image,x:int,y:int,c:Color)->void:
 if x>=0 and y>=0 and x<img.get_width() and y<img.get_height():
  img.set_pixel(x,y,c)

static func _line(img:Image,x0:int,y0:int,x1:int,y1:int,col:Color,thick:int=1)->void:
 var dx=absi(x1-x0)
 var dy=-absi(y1-y0)
 var step_x=1 if x0<x1 else -1
 var step_y=1 if y0<y1 else -1
 var err=dx+dy
 while true:
  _rect(img,x0,y0,thick,thick,col)
  if x0==x1 and y0==y1:break
  var next=2*err
  if next>=dy:
   err+=dy
   x0+=step_x
  if next<=dx:
   err+=dx
   y0+=step_y

static func _texture(img:Image)->ImageTexture:
 return ImageTexture.create_from_image(img)

static func _char_palette(kind:String)->Array:
 match kind:
  "guard","knight":return [Color("#35566a"),Color("#668ba0"),Color("#a8bac0"),Color("#f0c78e")]
  "oracle","mage","cultist":return [Color("#443a67"),Color("#755e95"),Color("#aa89a7"),Color("#e7bcba")]
  "hunter","hound":return [Color("#294a40"),Color("#4d8265"),Color("#8db48b"),Color("#f1ce93")]
  "slime":return [Color("#224c4a"),Color("#498b82"),Color("#8dcc9d"),Color("#ebffce")]
  "skeleton":return [Color("#46535a"),Color("#899592"),Color("#c8ccae"),Color("#ffe0a0")]
  "bandit","boss":return [Color("#552f3d"),Color("#9a4b53"),Color("#d58067"),Color("#ffce97")]
 return [Color("#34516a"),Color("#718ba0"),Color("#a6a6a0"),Color("#f7cd93")]

static func actor(kind:String)->Texture2D:
 var key="actor_"+kind
 if _cache.has(key):return _cache[key]
 var im=Image.create_empty(32,48,false,Image.FORMAT_RGBA8)
 im.fill(Color(0,0,0,0))
 var p=_char_palette(kind)
 var base:Color=p[0];var mid:Color=p[1];var light:Color=p[2];var gold:Color=p[3]
 # Tiny shadow below the boots, never antialiased.
 _rect(im,8,44,18,2,Color("#171822",.5))
 # Cloak silhouette / feet / shoulders: one-to-one grid pixels.
 _rect(im,10,37,5,7,OUTLINE);_rect(im,18,37,5,7,OUTLINE)
 _rect(im,11,38,3,5,base);_rect(im,19,38,3,5,base)
 _rect(im,7,17,19,25,OUTLINE)
 _rect(im,9,19,15,20,base)
 _rect(im,8,25,16,9,mid)
 _rect(im,10,19,4,17,light)
 _rect(im,21,20,2,18,Color("#273741"))
 # Sleeves pixel clusters.
 _rect(im,4,20,5,16,OUTLINE);_rect(im,5,22,3,12,mid)
 _rect(im,24,20,5,16,OUTLINE);_rect(im,25,21,3,12,base)
 # Dark hood and visible face mask.
 _rect(im,10,4,13,15,OUTLINE);_rect(im,8,8,17,8,OUTLINE)
 _rect(im,11,8,11,9,base)
 _rect(im,12,12,9,5,gold)
 _rect(im,12,12,9,2,OUTLINE)
 _rect(im,13,14,2,1,Color("#fff0ba"));_rect(im,19,14,2,1,Color("#fff0ba"))
 _rect(im,15,18,3,3,light)
 _rect(im,12,5,10,3,mid)
 if kind in ["guard","knight","skeleton"]:
  # Helm / visor / kite shield, bounded by deliberate hard pixel borders.
  _rect(im,9,7,15,3,light);_rect(im,10,11,13,2,mid)
  _rect(im,3,24,10,15,OUTLINE)
  _rect(im,4,25,8,10,light)
  _rect(im,5,26,6,7,base)
  _rect(im,7,25,2,12,gold)
  _rect(im,4,28,8,2,light)
  _line(im,27,18,30,3,Color("#a2b4c2"),2)
  _rect(im,26,17,6,2,gold)
 elif kind in ["oracle","cultist","mage"]:
  # Oracle's crescent staff and single-pixel glowing crystal.
  _line(im,27,40,28,8,gold,2)
  _rect(im,25,6,6,5,OUTLINE);_rect(im,27,5,2,5,Color("#e5b4fd"))
  _rect(im,26,3,4,2,Color("#9fbcde"))
  _rect(im,11,30,12,2,light)
  _rect(im,15,32,4,2,gold)
 elif kind in ["hunter","bandit"]:
  # Bow and sharp arrow; thin 1-pixel limbs remain legible when scaled 4x.
  _line(im,27,14,31,23,gold)
  _line(im,31,23,27,37,gold)
  _line(im,27,14,27,37,light)
  _line(im,19,26,31,24,Color("#c9bf93"))
  _rect(im,28,23,3,3,Color("#efe1b5"))
  _rect(im,13,6,7,3,light)
 elif kind=="slime":
  # Separate blob silhouette over shared body.
  _rect(im,4,23,24,18,OUTLINE)
  _rect(im,6,23,20,16,mid)
  _rect(im,8,20,16,7,light)
  _rect(im,10,18,12,5,mid)
  _rect(im,11,30,4,3,OUTLINE);_rect(im,20,30,4,3,OUTLINE)
  _rect(im,13,29,1,2,gold);_rect(im,22,29,1,2,gold)
 elif kind=="hound":
  _rect(im,4,6,9,7,OUTLINE);_rect(im,22,6,8,7,OUTLINE)
  _rect(im,6,9,6,7,mid);_rect(im,23,8,5,8,mid)
  _rect(im,13,14,7,5,gold)
  _rect(im,15,18,5,3,OUTLINE)
 elif kind=="boss":
  _rect(im,7,4,5,7,Color("#8e4945"))
  _rect(im,22,4,5,7,Color("#8e4945"))
  _rect(im,12,2,10,2,gold)
  _rect(im,14,24,4,15,light)
 _cache[key]=_texture(im)
 return _cache[key]

static func card_art(code:String,school:String="",kind:String="skill")->Texture2D:
 var style=school
 if style=="":style=kind
 var motif=posmod(int(hash(code)),12)
 var key="art_"+style+"_"+str(motif)
 if _cache.has(key):return _cache[key]
 var im=Image.create_empty(48,32,false,Image.FORMAT_RGBA8)
 im.fill(Color("#171d2b"))
 var accent=Color("#d88c64") if kind=="attack" else Color("#75b4a7") if kind=="skill" else Color("#b09acd")
 if school=="Огонь":accent=Color("#ee9e56")
 elif school=="Яд":accent=Color("#82b38b")
 elif school=="Кровь":accent=Color("#d4707b")
 elif school in ["Оборона","Защита"]:accent=Color("#8fb8d6")
 var lighter=accent.lightened(.35)
 # Pixel dither sky and horizon, exclusively integer coordinates.
 for y in range(2,22):
  for x in range(2,46):
   if (x*17+y*29+motif*7)%31<3:_dot(im,x,y,Color("#414257"))
 _rect(im,1,26,46,5,Color("#29293b"))
 _rect(im,2,27,44,1,Color("#6a5460"))
 for i in range(4):
  var x=5+i*11
  _rect(im,x,2,1,2,accent.darkened(.4))
 match motif:
  0,1:
   _line(im,14,25,31,5,OUTLINE,6)
   _line(im,15,24,30,6,lighter,3)
   _rect(im,11,18,13,4,Color("#b39b7b"))
   _rect(im,18,22,4,5,accent)
  2,3:
   _rect(im,17,5,15,20,OUTLINE)
   _rect(im,19,7,11,15,accent)
   _rect(im,21,9,7,11,accent.darkened(.45))
   _rect(im,23,9,3,12,lighter)
   _rect(im,16,15,17,3,Color("#d5bb90"))
  4,5:
   _rect(im,20,21,8,5,OUTLINE)
   _rect(im,21,13,6,10,accent)
   _rect(im,23,6,2,12,lighter)
   _rect(im,18,14,3,7,accent.darkened(.25))
   _rect(im,27,12,3,7,lighter)
   _rect(im,24,4,2,5,PARCHMENT)
  6,7:
   _rect(im,13,9,22,14,OUTLINE)
   _rect(im,15,11,18,10,accent.darkened(.35))
   _rect(im,18,7,13,3,accent)
   _rect(im,21,13,7,5,lighter)
   _rect(im,23,18,3,5,lighter)
  8,9:
   for i in range(4):
    _rect(im,10+i*6,17-i*3,5,5,accent)
    _rect(im,12+i*6,14-i*3,2,3,lighter)
   _rect(im,6,23,37,2,Color("#c99c78"))
  _:
   _rect(im,11,10,26,15,accent.darkened(.55))
   _rect(im,13,12,22,11,OUTLINE)
   _rect(im,19,14,10,8,accent)
   _rect(im,23,8,3,17,lighter)
   _rect(im,15,17,19,2,lighter)
 # Etched bevel around art canvas.
 _rect(im,0,0,48,1,Color("#93755d"))
 _rect(im,0,31,48,1,Color("#93755d"))
 _rect(im,0,0,1,32,Color("#93755d"))
 _rect(im,47,0,1,32,Color("#93755d"))
 _cache[key]=_texture(im)
 return _cache[key]

static func scene()->Texture2D:
 if _cache.has("scene"):return _cache["scene"]
 var im=Image.create_empty(192,128,false,Image.FORMAT_RGBA8)
 im.fill(Color("#121725"))
 # Genuine 192x128 hand-authored pixel environment: 1 pixel is one logical texel.
 for y in range(128):
  for x in range(192):
   var noisy=posmod(x*73+y*37+(x*y)%19,29)
   var base:Color
   if y<93:
    base=Color("#202a3a") if y<56 else Color("#303343")
    if noisy<4:base=base.lightened(.09)
   else:
    base=Color("#3e3941") if noisy<14 else Color("#41333e")
   _dot(im,x,y,base)
 # Blood moon with hard edge and sparse 1px dithering.
 for y in range(12,57):
  for x in range(119,164):
   var dx=x-141;var dy=y-34
   var radius=dx*dx+dy*dy
   if radius<19*19:
    _dot(im,x,y,Color("#bd6d65") if (x*11+y*17)%9>2 else Color("#d78f74"))
   elif radius<22*22 and (x+y)%3==0:
    _dot(im,x,y,Color("#a35b63"))
 # Ruined columns and buttresses. Sharp diagonal step edges.
 for i in range(5):
  var x=i*42-4
  _rect(im,x,29,11,65,Color("#131b2b"))
  _rect(im,x+2,31,3,57,Color("#333746"))
  _rect(im,x-3,28,17,3,Color("#54606b"))
  _rect(im,x-3,85,17,3,Color("#4b4e58"))
  _rect(im,x+5,55,1,25,Color("#91675d"))
 for i in range(4):
  var x=i*50+13
  _rect(im,x,61,24,4,Color("#202838"))
  _rect(im,x+6,61,12,2,Color("#4c5360"))
 # Pixellated stone stage, cracks and torch embers.
 _rect(im,0,91,192,4,Color("#191b2c"))
 for y in range(98,128,12):
  _rect(im,0,y,192,1,Color("#262632"))
  for x in range(0,192,31):
   _rect(im,x+((y//12)%2)*14,y,1,11,Color("#27252f"))
 for x in [27,90,161]:
  _rect(im,x,71,3,15,Color("#69463b"))
  _rect(im,x-2,67,7,8,Color("#d98a4e"))
  _rect(im,x,65,3,6,Color("#f2c07d"))
 for i in range(37):
  var x=posmod(i*53+7,192)
  var y=posmod(i*37+17,79)+8
  if (x+y)%3!=0:_rect(im,x,y,1,1,Color("#c79c76"))
 _cache["scene"]=_texture(im)
 return _cache["scene"]
