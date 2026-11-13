# Verdis

A Mindustry mod (v8, build 160): a living planet between Serpulo and Erekir — moss, glowing lakes and lava rifts — with a small story campaign.

**Campaign so far**
1. **Ship Deck** — a battle on board the ship in orbit of Verdis. On the last wave the reactor is hit and the ship falls.
2. **Landing Glade** — the crash site. Meteors, waves, a boss on wave 15; then the core takes off.
3. **Root Maze** — a maze of roots where pieces of the ship (one of the launch generators) broke through the walls.

**Recommended mods:** the Ship Deck map uses blocks from Exogenesis, New Horizon, BetaMindy and Upgraded Tiers.

## Install
Download `verdis.jar` from [Releases](../../releases) and put it into the game's `mods` folder (or use *Import mod* in the game).

## Build
JDK 17+, no Gradle: `build.ps1` compiles against the game's own `jre/desktop.jar` (edit the paths at the top). `.\build.ps1 -Install` also copies the jar into the game's mods folder.
`tools/` holds Python scripts for the hand-made maps and cutscene scenes (see `CLAUDE.md`).

---

# Вердис

Мод для Mindustry (v8, сборка 160): живая планета между Серпуло и Эрекиром (мох, светящиеся озёра, лавовые разломы) с небольшой сюжетной кампанией.

**Кампания сейчас**
1. **Палуба корабля** — бой на борту корабля на орбите Вердиса. На последней волне попадают в реактор, и корабль падает.
2. **Поляна посадки** — место крушения. Метеориты, волны, босс на 15-й волне, потом ядро взлетает.
3. **Корневой лабиринт** — лабиринт из корней, сквозь стены которого пробились обломки корабля (один из генераторов для запуска).

**Рекомендуемые моды:** на карте «Палуба корабля» стоят блоки из Exogenesis, New Horizon, BetaMindy и Upgraded Tiers.

## Установка
Скачай `verdis.jar` в [Releases](../../releases) и положи в папку `mods` игры (или «Импорт мода» в самой игре).

## Сборка
JDK 17+, без Gradle: `build.ps1` собирает мод с игровым `jre/desktop.jar` (пути в начале скрипта). `.\build.ps1 -Install` ещё и копирует jar в папку модов.
В `tools/` лежат Python-скрипты для карт и сцен катсцен (подробнее в `CLAUDE.md`).
