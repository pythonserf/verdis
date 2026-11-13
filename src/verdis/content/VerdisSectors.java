package verdis.content;

import mindustry.type.SectorPreset;
import verdis.world.Finale;
import verdis.world.ShipDeck;

/** Hand-made sectors of Verdis. Map files: assets/maps/<name>.msav */
public class VerdisSectors{
    // ---- tunables ----
    /** Root Maze: wave that captures the sector, and difficulty shown on the planet map (0-5) */
    public static final int ROOT_MAZE_CAPTURE_WAVE = 25;
    public static final float ROOT_MAZE_DIFFICULTY = 4;

    public static SectorPreset shipDeck, landingGlade, rootMaze;

    public static void load(){
        // 0. Ship Deck: the ship in orbit, start of the campaign; ends with the fall onto Landing Glade (see ShipDeck)
        shipDeck = new SectorPreset("ship-deck", VerdisSpace.ship, 0){{
            // a preset with captureWave 0 counts as an enemy base on the planet map; the real end is the
            // ShipDeck cutscene, which drops the win wave the moment it starts
            captureWave = ShipDeck.FINAL_WAVE;
            difficulty = 1;
            alwaysUnlocked = true;
            addStartingItems = true;
        }};

        // 1. Landing Glade: start sector, soft waves
        landingGlade = new SectorPreset("landing-glade", VerdisPlanets.verdis, VerdisPlanets.verdis.startSector){{
            // won by the core evacuation on wave 15 (see Finale); captureWave only keeps it from showing
            // as an enemy base, Finale drops the win wave the moment the boss wave starts
            captureWave = Finale.BOSS_WAVE;
            difficulty = 2;
            alwaysUnlocked = true;
            addStartingItems = true;
            startWaveTimeMultiplier = 2f;
        }};

        // 2. Root Maze: next to Landing Glade; ship debris (one of the launch generators) broke through the roots
        rootMaze = new SectorPreset("root-maze", VerdisPlanets.verdis, VerdisPlanets.nextSector(VerdisPlanets.verdis, VerdisPlanets.verdis.startSector)){{
            captureWave = ROOT_MAZE_CAPTURE_WAVE;
            difficulty = ROOT_MAZE_DIFFICULTY;
            alwaysUnlocked = true;
            addStartingItems = true;
        }};
    }
}
