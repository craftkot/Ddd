extends RefCounted
## Optimized texture atlas cut from the original image-generated Ashes art sheets.
## 3 heroes, 6 enemies, 12 card/relic illustrations, moonlit arena.
## Atlas size 200x558; coordinates are calculated from the packed source grid.
const ATLAS: Texture2D = preload("res://assets/generated/ashes_painted_atlas.webp")
const BACKGROUND: Texture2D = preload("res://assets/generated/ashes_bloodmoon_arena.webp")
const SPRITE_SCALE = 200.0 / 510.0
const BACKGROUND_SCALE = 200.0 / 340.0

static func hero_region(index: int) -> Rect2:
 return Rect2(float(clampi(index,0,2) * 170)*SPRITE_SCALE,0,170*SPRITE_SCALE,208*SPRITE_SCALE)

static func enemy_region(index: int) -> Rect2:
 var n=clampi(index,0,5)
 return Rect2((n%3)*170*SPRITE_SCALE,(208+(n/3)*171)*SPRITE_SCALE,170*SPRITE_SCALE,171*SPRITE_SCALE)

static func card_region(index: int) -> Rect2:
 var n=posmod(index,12)
 return Rect2((n%6)*85*SPRITE_SCALE,(552+(n/6)*89)*SPRITE_SCALE,85*SPRITE_SCALE,89*SPRITE_SCALE)

static func arena_region() -> Rect2:
 return Rect2(0,493*BACKGROUND_SCALE,256*BACKGROUND_SCALE,455*BACKGROUND_SCALE)

static func hero_index(kind: String) -> int:
 match kind:
  "guard":return 0
  "oracle":return 1
  "hunter":return 2
 return -1

static func enemy_index(kind: String) -> int:
 match kind:
  "bandit":return 0
  "cultist":return 1
  "skeleton","knight","boss":return 2
  "slime":return 3
  "mage":return 4
  "hound":return 5
 return 0

static func character_region(kind: String) -> Rect2:
 var hero=hero_index(kind)
 if hero>=0:return hero_region(hero)
 return enemy_region(enemy_index(kind))

static func card_index(code: String, school: String="", mode: String="skill") -> int:
 var mapped:Dictionary={
  "attack":0,"skill":3,"power":9,"reaction":6
 }
 var n=int(abs(hash(code))) % 12
 if school=="Яд":return 1 if n%2==0 else 8
 if school=="Огонь":return 0 if n%2==0 else 9
 if school=="Лечение":return 6
 if school=="Кровь":return 2 if n%2==0 else 6
 if school=="Защита":return 4
 if school=="Тень":return 10
 if mode=="power":return 9 if n%2==0 else 11
 return n

static func atlas_region(source: Rect2) -> AtlasTexture:
 var t=AtlasTexture.new()
 t.atlas=ATLAS
 t.region=source
 return t
