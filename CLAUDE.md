# Verdis — Mindustry planet mod (Java)

Planet between Serpulo and Erekir. Separate from the Unit Transfer mod (F8 + Unit Launch Pad), which lives in the game's `saves/mods`.

## Build
- `.\build.ps1` → `build\verdis.jar`; `.\build.ps1 -Install` also copies it to `C:\Program Files (x86)\Steam\steamapps\common\Mindustry\saves\mods\verdis.jar`.
- No Gradle: javac from JDK 25 (`%LOCALAPPDATA%\Programs\Eclipse Adoptium\jdk-25.0.4.101-hotspot`) with `--release 17`, classpath = the game's own `jre\desktop.jar` (Mindustry 160.7). `java` on PATH is Java 8 — never use it.
- Check API with `javap -cp <desktop.jar> <class>` instead of guessing.
- Game log: `...\Mindustry\saves\last_log.txt`. Mod logs are prefixed `[Verdis]`.

## Map scenes
- Landing Glade map = the author's `saves/maps/1.msav`, copied to `assets/maps/landing-glade.msav`. After he edits it: copy again, then re-export the crash scene (crash site + two ship pieces):
  `py -I tools/export_scene.py assets/maps/landing-glade.msav 98,202,128,232 95,140,258,305 12,52,200,256 assets/scenes/crash-site.txt`
- If the debris moves, update the boxes above and `IntroCrash.SITE_X/Y` and `PIECES`.
- Root Maze map = `saves/maps/root-maze.msav` (made by `..\maze-map\gen_maze_map.py`, moss/ore by `add_moss_ore.py`, debris drawn by the author), copied to `assets/maps/root-maze.msav`. Its debris scene is the difference from the clean generated maze `..\maze-map\root-maze.msav`, so walls under the debris come back hidden and break when pieces land:
  `py -I tools/diff_scene.py ../maze-map/root-maze.msav assets/maps/root-maze.msav assets/scenes/maze-debris.txt`
  It prints debris groups (center, radius); put them into `MazeIntro.PIECES` (the last one is the generator).
- Scene block lines can carry a hidden block: `B x y <real> <hidden>`; real `-` = tile covered by a building placed at its center.

## Sectors
- Ship Deck (space, start of the campaign, `ShipDeck`: intro, HUD "wave X/10", on wave 10 the reactor is hit and the game moves to Landing Glade) → Landing Glade (`IntroCrash`, `Meteors`, `Finale`: Nucleoid on wave 15, core takes off to Root Maze) → Root Maze (land neighbour of the start, `MazeIntro`, capture on wave 25). Story: the ship broke apart; pieces are the generators needed for the launch of the most powerful one.
- Space: each space sector is a small body on the orbit of Verdis with one sector (`VerdisSpace.body(...)`, like the game's asteroids). The ship is `accessible` (campaign start); later space sectors stay `accessible = false`, so they are reached only by a rocket launch from Verdis. Planned: a couple of space sectors mid-campaign and the final launch back to space.
- Ship Deck map: `assets/maps/ship-deck.msav`, built from the author's outline `saves/maps/корабль.msav` (ship shape drawn with metal floor on plain floor; metal-tiles-3/4 = engine nozzles):
  `py -I tools/ship_from_outline.py "<saves>/maps/корабль.msav" assets/maps/ship-deck.msav --preview deck.png`
  It makes space outside, the hull, bulkheads with doors, engine glow, cargo ore, core and spawn (spawn is an overlay, not a block). `--mirror bottom` copies the bottom half onto the top (the drawn outline is not symmetric; the author chose the bottom). After that the author decorated `saves/maps/ship-deck.msav` by hand (doors, walls, New Horizon floor at the nozzles): do not regenerate over it, patch only the changed tiles. `tools/gen_ship_deck.py` is the old generated template.
- Ship defense and fittings (turrets from New Horizon / Exogenesis, generators, storage, hangar) are placed at runtime once per save by `ShipFitting` from `assets/scenes/ship-deck-fit.txt` (zones, power nodes, a kept-free reactor hall in the middle for the author). Modded buildings cannot be written into a .msav by a script (each block has its own entity format and a bad chunk breaks the map load).
- Engine flames are drawn by `ShipDeck.drawFlames()` (campaign and custom game on this map); nozzle positions are `ShipDeck.ENGINE_X` / `ENGINES`.

## Layout
- `src/verdis/VerdisMod.java` — mod entry.
- `src/verdis/content/` — planets, blocks, units, tech tree.
- `src/verdis/gen/` — planet terrain generator.
- `assets/` — `mod.hjson`, `bundles/` (en + ru), later `sprites/`, `maps/`.
- Content names get the mod prefix: planet `verdis` → `verdis-verdis` (bundle keys use the full name).

## Rules
- Tunable numbers go into named `public static final` constants at the top of a class — the author tunes them himself.
- No new hand-drawn art: sprites are recolors of vanilla ones.
- Keep Exogenesis compatibility in mind.
- Code, comments, bundles: plain and neutral. Bundles in English and Russian.
