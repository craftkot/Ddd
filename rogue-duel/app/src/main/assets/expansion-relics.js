"use strict";
/* 112 earned/purchased relics: 14 artifact disciplines x 8 hooks. */
const ASH_RELIC_GROUPS=[
["Чумная коллекция","🐍","poison",["Изумрудная железа","Сосуд чёрной росы","Слеза василиска","Перо гадюки","Проклятая пробирка","Сухой корень","Кисть некроманта","Змеиный янтарь"]],
["Огненные тайны","🔥","burn",["Негасимый фитиль","Сердце угольщика","Обожжённый колокол","Огненный палец","Лавовый талисман","Язык саламандры","Искра кузнеца","Корона пепла"]],
["Кровавые дары","🩸","bleed",["Треснувшая артерия","Красная нить судьбы","Сердце палача","Осколок пурпура","Окровавленный гвоздь","Перстень мясника","Ржавый распылитель","Тонкий скальпель"]],
["Доспехи древних","🛡️","guard",["Кожа титана","Руна панциря","Живой нагрудник","Оловянная корона","Последний барбакан","Кирпич защитника","Железный парус","Герб бастиона"]],
["Терновые кости","🌵","thorn",["Сердце ежевики","Венец тёрна","Шипастая монета","Семя дикого леса","Лоза возмездия","Кольцо гнева","Камень дикобраза","Клык терновника"]],
["Дары берсерка","🪓","might",["Печать ярости","Клык огра","Барабан войны","Разбитая цепь","Великанов топор","Красное знамя","Кубок победителя","Наконечник титана"]],
["Машины эфира","💠","energy",["Колесо заряда","Нулевой аккумулятор","Динамо-бабочка","Расколотый конденсатор","Серебряная катушка","Ядро бури","Дырявый реактор","Шестерня пустоты"]],
["Запретные знания","📜","draw",["Очки пророка","Зеркало знаний","Перо стенографа","Чернильный глаз","Незакрытая книга","Узел памяти","Часы без стрелок","Третья рукопись"]],
["Чудеса жизни","🌿","heal",["Колба росы","Лунный цветок","Семя регенерации","Красная трава","Эхо целителя","Бессмертный мох","Костяное яблоко","Медовый янтарь"]],
["Знаки страха","👁️","curse",["Слепая маска","Монета кошмара","Серебряный глаз","Зеркало страха","Чёрный колокольчик","Ониксовый крест","Звезда забвения","Печать пустоты"]],
["Искусство клинка","🗡️","blade",["Пружинный нож","Второе лезвие","Тайные ножны","Сердце дуэлянта","Белый стилет","Сломанные ножницы","Серебряный капкан","Перчатка убийцы"]],
["Кошельки странников","🪙","economy",["Кошелёк стража","Третий глаз купца","Двойной жетон","Золотая чешуйка","Перо ростовщика","Сундук смельчака","Золотая печать","Карта торговца"]],
["Хрупкие чудеса","💎","vital",["Багровый кристалл","Неприкосновенная кость","Звезда рождения","Тёплый обломок","Сердечный циферблат","Дыхание камня","Осколок бессмертия","Резной сосуд"]],
["Парадокс судьбы","🌀","chaos",["Зеркало повторения","Серебряный кубик","Тройной узел","Сосуд комбо","Медаль отголоска","Кольцо сдвига","Печать синергии","Око равновесия"]]
];
const ASH_HOOKS={
poison:[["start","poisonall",3],["turn","poisonall",1],["attack","poison",1],["dot","heal",2],["kill","poisonall",4],["detonate","energy",1],["skill","poison",1],["win","heal",6]],
burn:[["start","burnall",2],["turn","burnall",1],["attack","burn",1],["dot","block",2],["kill","burnall",4],["detonate","strength",1],["skill","burn",2],["win","gold",12]],
bleed:[["start","bleedall",2],["turn","bleedall",1],["attack","bleed",1],["dot","draw",1],["kill","bleedall",4],["detonate","heal",4],["skill","bleed",2],["win","heal",5]],
guard:[["start","block",10],["turn","block",3],["block","strength",1],["attack","block",2],["end","block",4],["kill","block",8],["skill","block",3],["win","gold",10]],
thorn:[["start","thorns",3],["turn","thorns",1],["block","thorns",1],["hit_taken","thorns",2],["kill","thorns",3],["end","block",3],["skill","thorns",1],["win","heal",4]],
might:[["start","strength",2],["turn","strength",1],["attack","strength",1],["kill","strength",2],["end","block",3],["skill","strength",1],["hit_taken","strength",1],["win","heal",3]],
energy:[["start","energy",1],["turn","energy",1],["attack","energy",1],["kill","energy",1],["dot","energy",1],["skill","energy",1],["end","block",4],["win","gold",15]],
draw:[["start","draw",1],["turn","draw",1],["attack","draw",1],["kill","draw",1],["dot","draw",1],["skill","draw",1],["end","block",4],["win","gold",12]],
heal:[["start","heal",7],["turn","regen",2],["attack","heal",1],["kill","heal",5],["dot","heal",1],["skill","regen",1],["hit_taken","heal",2],["win","heal",7]],
curse:[["start","weakall",1],["turn","vulnall",1],["attack","mark",1],["kill","weakall",1],["dot","mark",1],["skill","weak",1],["end","weakall",1],["win","gold",9]],
blade:[["start","charge",3],["turn","charge",1],["attack","damage",1],["kill","energy",1],["dot","strength",1],["skill","charge",1],["end","block",4],["win","gold",15]],
economy:[["start","gold",8],["turn","gold",1],["attack","gold",1],["kill","gold",12],["dot","gold",1],["skill","gold",2],["end","gold",2],["win","gold",30]],
vital:[["acquire","maxhp",8],["start","block",6],["turn","heal",2],["attack","heal",1],["kill","heal",6],["dot","heal",2],["end","regen",2],["win","maxhp",1]],
chaos:[["start","draw",2],["turn","energy",1],["attack","poison",1],["kill","strength",1],["dot","block",4],["skill","draw",1],["detonate","draw",2],["win","gold",20]]
};
const ASH_EXTRA_RELICS={};
const ASH_EVENT_NAMES={start:"Начало боя",turn:"Каждый ход",attack:"После атаки",dot:"При срабатывании ДоТ",kill:"После убийства",detonate:"После взрыва ДоТ",skill:"После навыка",win:"После победы",block:"При получении брони",end:"В конце хода",hit_taken:"Когда получен урон",card:"После карты",acquire:"При получении"};
const ASH_POWER_NAMES={poisonall:"яда всем",burnall:"горения всем",bleedall:"кровотечения всем",weakall:"слабости всем",vulnall:"уязвимости всем",maxhp:"максимального здоровья",gold:"золота",damage:"урона",strength:"силы",energy:"энергии",block:"брони",heal:"здоровья",draw:"карта",poison:"яда",burn:"горения",bleed:"кровотечения",thorns:"шипов",regen:"регенерации",charge:"заряда",weak:"слабости",mark:"метки"};
let ashRelicId=0;
for(const [group,emoji,type,names] of ASH_RELIC_GROUPS){
 names.forEach((name,i)=>{
  const id="artifact"+String(++ashRelicId).padStart(3,"0"),[event,power,value]=ASH_HOOKS[type][i];
  const r={id,n:name,e:emoji,d:ASH_EVENT_NAMES[event]+": +"+value+" "+ASH_POWER_NAMES[power]+".",event,power,value,group};
  ASH_EXTRA_RELICS[id]=r;RELICS.push(r);
 });
}
window.EXPANSION_STATS={cards:Object.keys(CARDS).length,relics:RELICS.length,newCards:ASH_CARD_IDS.length,newRelics:Object.keys(ASH_EXTRA_RELICS).length};
