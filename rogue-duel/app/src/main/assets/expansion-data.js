"use strict";
/* 112 original cards: 16 archetypes x 7 carefully defined combinations. */
const ASH_FAMILIES=[
["Яд","🦂",[
["Жало из тумана",1,"attack","hit5|poison3"],["Змеиная клятва",1,"power","str1|poison2"],["Ядовитая ловушка",1,"skill","poison5|weak1"],["Чума стай",2,"skill","aoepoison4"],["Разрыв пузырей",2,"attack","hit8|detonate2"],["Заражение крови",1,"skill","poison4|spread1"],["Токсичный голод",2,"attack","lifesteal11|poison3"]]],
["Огонь","🔥",[
["Искра",0,"attack","hit3|burn1"],["Горящий след",1,"attack","hit6|burn3"],["Пепельный дождь",2,"attack","aoe7|aoeburn2"],["Пламенный оберег",1,"skill","block8|burn2"],["Топка",2,"skill","burn6|draw1"],["Солнечная вспышка",2,"attack","hit14|detonate1"],["Жар изнутри",1,"power","str2|risky2"]]],
["Кровь","🩸",[
["Зазубренный порез",1,"attack","hit7|bleed3"],["Красная нить",0,"skill","bleed2|draw1"],["Струя багрянца",2,"attack","aoe8|aoebleed3"],["Открытая рана",1,"skill","bleed6|vuln1"],["Гемолиз",2,"attack","detonate3"],["Тёмное причастие",1,"skill","risky3|str3|draw1"],["Кровавый трон",2,"power","regen3|thorns3"]]],
["Оборона","🛡️",[
["Гранитная стойка",1,"skill","block12"],["Стальная дисциплина",1,"skill","block7|draw1"],["Бастион",2,"skill","block20|armorcarry1"],["Выпад из-за щита",1,"attack","block7|shieldhit1"],["Укреплённый шлем",1,"power","armorcarry1|block4"],["Крушитель заслона",2,"attack","shieldhit2|consume1"],["Оборонительная ярость",2,"attack","block10|aoe9"]]],
["Сила","🪓",[
["Боевой рык",1,"skill","str2"],["Яростный удар",1,"attack","hit9"],["Серия рассечений",2,"attack","multihit5"],["Железная воля",1,"power","str2|block4"],["Безжалостная хватка",2,"attack","hit17|weak1"],["Всплеск адреналина",0,"skill","risky3|energy2"],["Окончательная казнь",3,"attack","hit31"]]],
["Проклятия","👁️",[
["Ложный шаг",0,"skill","weak1"],["Сломанная печать",1,"skill","vuln2"],["Чёрная метка",1,"attack","hit5|mark3"],["Узы тоски",2,"skill","aoeweak2|block7"],["Знак обречённого",1,"skill","weak1|vuln1|draw1"],["Магическое истощение",2,"attack","hit14|weak2"],["Прикосновение тьмы",2,"attack","aoe9|aoevuln1"]]],
["Знание","📜",[
["Искра мысли",0,"skill","draw1"],["Круг планирования",1,"skill","draw3"],["Быстрое решение",1,"attack","hit4|draw2"],["Внутренний порядок",1,"skill","block6|draw2"],["Дальновидность",2,"skill","draw4|energy1"],["Бесконечный расчёт",2,"power","draw2|str1"],["Прошитая реальность",1,"skill","duplicate1|draw1"]]],
["Энергия","💠",[
["Эфирная искра",0,"skill","energy1"],["Разряд силы",1,"attack","hit6|energy1"],["Медный генератор",1,"power","charge3"],["Поглощённый эфир",1,"skill","siphon2"],["Тройной импульс",0,"skill","risky4|energy3"],["Заводная спираль",2,"skill","block12|energy2"],["Бездонная батарея",2,"power","energy2|draw1"]]],
["Шипы","🌵",[
["Ежевичная броня",1,"skill","block7|thorns3"],["Гибкая игла",1,"attack","hit5|thorns2"],["Ответный страх",1,"power","thorns4"],["Терновый венец",2,"skill","block14|thorns3"],["Колючий взрыв",2,"attack","aoe8|thorns2"],["Непроходимый лес",1,"skill","block8|counter2"],["Костяной панцирь",2,"power","armorcarry1|thorns5"]]],
["Лёд","❄️",[
["Холодное касание",1,"attack","hit6|weak1"],["Осколки льда",2,"attack","aoe7|aoeweak1"],["Ледяная кладка",1,"skill","block11|weak1"],["Замедление времени",2,"skill","weak2|draw2"],["Хрустальный наконечник",2,"attack","hit16|vuln1"],["Ледниковый гроб",2,"skill","block19|stun1"],["Вечная мерзлота",2,"power","armorcarry1|weak2"]]],
["Гроза","⚡",[
["Сухая молния",0,"attack","hit4"],["Разветвлённая дуга",1,"attack","aoe5|draw1"],["Проводящий клинок",1,"attack","hit7|mark2"],["Шквал зарядов",2,"attack","multihit6"],["Громовая печать",1,"skill","charge4|block6"],["Ослепляющий разряд",2,"attack","hit13|weak2"],["Магнитная буря",3,"attack","aoe17|energy1"]]],
["Тени","🌑",[
["Теневой укол",0,"attack","hit5"],["Печать забвения",1,"skill","weak1|poison2"],["Серп мрака",2,"attack","lifesteal13"],["Призрачный плащ",1,"skill","dodge1|block4"],["Скрытый охотник",1,"power","mark3|str1"],["Выпущенная тень",2,"attack","aoe10|bleed3"],["Смертная тишина",3,"attack","hit22|vuln2"]]],
["Лечение","🌿",[
["Перевязка",1,"skill","heal7"],["Живая вода",0,"skill","regen2"],["Цветок на углях",1,"skill","heal5|block5"],["Восстановление плоти",2,"skill","heal14|draw1"],["Горькая настойка",1,"skill","heal8|risky2"],["Священный договор",2,"skill","regen5|block9"],["Завет бессмертия",3,"power","heal16|regen5"]]],
["Охота","🏹",[
["Точный прицел",1,"skill","mark4"],["Пробивающий выстрел",1,"attack","pierce9"],["Нить охотника",0,"skill","mark2|draw1"],["Карающий болт",2,"attack","hit14|mark3"],["Осколочная стрела",2,"attack","aoe12"],["Прицельное добивание",2,"attack","hit19|execute1"],["Кровавая награда",1,"skill","mark3|energy1"]]],
["Кинжалы","🗡️",[
["Скрытый нож",0,"attack","multihit3"],["Танец лезвий",1,"attack","multihit4"],["Поток стилетов",2,"attack","multihit6"],["Ловкая рука",0,"skill","draw2|risky1"],["Нож в рукаве",1,"skill","duplicate1|block6"],["Последний клинок",2,"attack","multihit7|bleed2"],["Стая острых мыслей",1,"power","str1|draw1"]]],
["Цена силы","🕯️",[
["Договор боли",0,"skill","risky3|draw2"],["Опасная сделка",0,"skill","risky5|energy3"],["Отчаянный выпад",1,"attack","risky3|hit17"],["Ритуал души",1,"power","risky6|str4"],["Тропа жертвы",2,"attack","risky5|aoe19"],["Последняя капля",2,"skill","risky8|heal20|draw2"],["Кровавое озарение",3,"attack","risky6|multihit12"]]]
];
const ASH_EFFECT_NAMES={hit:"урона",aoe:"урона всем",block:"брони",heal:"здоровья",poison:"яда",burn:"горения",bleed:"кровотечения",aoepoison:"яда всем",aoeburn:"горения всем",aoebleed:"кровотечения всем",detonate:"взрыв всех ДоТ ×",spread:"распространение ДоТ",draw:"добор карт",energy:"энергии",str:"силы",weak:"слабости",aoeweak:"слабости всем",vuln:"уязвимости",aoevuln:"уязвимости всем",mark:"метки",thorns:"шипов",regen:"регенерации",dodge:"уклонения",charge:"заряда",multihit:"урона трижды",shieldhit:"удар от брони ×",consume:"тратит броню",lifesteal:"урона с лечением",risky:"потерять здоровье",stun:"оглушение",armorcarry:"сохранение брони",counter:"ответные шипы",siphon:"энергии за ДоТ",duplicate:"дублировать карту",pierce:"пробивающего урона",execute:"бонусного урона раненым"};
function ashParseFX(str){return str.split("|").map(part=>{const m=part.match(/^([a-z]+)(-?\d+)$/);if(!m)throw Error("Некорректный эффект: "+part);return {key:m[1],value:Number(m[2])}})}
const ASH_CARD_IDS=[];
let ashId=0;
for(const [school,emoji,defs] of ASH_FAMILIES)for(const [name,cost,type,fx] of defs){
 const id="forge"+String(++ashId).padStart(3,"0"),effects=ashParseFX(fx);
 const d=effects.map(z=>z.value+" "+(ASH_EFFECT_NAMES[z.key]||z.key)).join(" · ")+".";
 CARDS[id]={n:name,e:emoji,t:type,c:cost,d,v:effects[0].value,k:"combo",effects,school};
 ASH_CARD_IDS.push(id);COMMON.push(id);
}
CLASSES.guard.deck.splice(-2,0,"forge022","forge023");
CLASSES.oracle.deck.splice(-2,0,"forge003","forge004");
CLASSES.hunter.deck.splice(-2,0,"forge095","forge096");
