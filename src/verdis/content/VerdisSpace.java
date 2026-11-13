package verdis.content;

import arc.graphics.Color;
import arc.math.Rand;
import arc.math.geom.Mat3D;
import arc.struct.Seq;
import arc.util.Tmp;
import mindustry.content.Blocks;
import mindustry.game.Team;
import mindustry.graphics.g3d.GenericMesh;
import mindustry.graphics.g3d.MatMesh;
import mindustry.graphics.g3d.MultiMesh;
import mindustry.graphics.g3d.NoiseMesh;
import mindustry.graphics.g3d.PlanetGrid;
import mindustry.maps.planet.AsteroidGenerator;
import mindustry.type.Planet;
import mindustry.type.Sector;

/**
 * Space bodies around Verdis. Each one is a small object on the orbit with a single hand-made sector,
 * like the game's own asteroids. The first one is the ship itself (start of the campaign);
 * later ones are reached only by a rocket (launch from Verdis).
 */
public class VerdisSpace{
    // ---- tunables ----
    /** size of the ship on the planet map (Serpulo's asteroids use 0.12) */
    public static final float SHIP_SIZE = 0.1f;
    /** distance of the ship's orbit from Verdis (planet radii) */
    public static final float SHIP_ORBIT = 1.9f;

    public static Planet ship;

    public static void load(){
        ship = body("verdis-ship", Color.valueOf("8a8f9e"), Color.valueOf("5b6070"), SHIP_SIZE, 11);
        ship.orbitRadius = VerdisPlanets.verdis.radius * SHIP_ORBIT;
        // the campaign starts here, so it must be selectable without a launch
        ship.accessible = true;
        ship.alwaysUnlocked = true;
        ship.launchCandidates.add(VerdisPlanets.verdis);
        ship.meshLoader = () -> shipMesh(ship);
    }

    /** colors of the ship model on the planet map */
    static final Color hull = Color.valueOf("9aa0ad"), hullDark = Color.valueOf("5d6372"),
        trim = Color.valueOf("c9cdd6"), engine = Color.valueOf("ffb35c"), engineDark = Color.valueOf("ff7a3c");

    /**
     * The ship on the planet map, put together from smooth stretched blobs:
     * long hull, pointed bow, bridge on top, side wings, two engines with a glow at the stern.
     * Sizes are parts of the body radius; x is along the ship (bow at +x).
     */
    static GenericMesh shipMesh(Planet p){
        float r = p.radius;
        return new MultiMesh(
            part(p, 1, r, 0f, 0f, 0f, 2.4f, 0.65f, 0.8f, hull, hullDark),                // hull
            part(p, 2, r * 0.7f, r * 2.1f, 0f, 0f, 1.7f, 0.45f, 0.55f, trim, hull),        // bow
            part(p, 3, r * 0.4f, -r * 0.5f, r * 0.55f, 0f, 1.5f, 0.7f, 0.8f, trim, hull),  // bridge
            part(p, 4, r * 0.5f, -r * 0.7f, 0f, r * 0.95f, 1.7f, 0.25f, 1.1f, hullDark, hull),   // wing
            part(p, 5, r * 0.5f, -r * 0.7f, 0f, -r * 0.95f, 1.7f, 0.25f, 1.1f, hullDark, hull),  // wing
            part(p, 6, r * 0.38f, -r * 2.2f, 0f, r * 0.5f, 1.4f, 0.8f, 0.8f, hullDark, hull),    // engine
            part(p, 7, r * 0.38f, -r * 2.2f, 0f, -r * 0.5f, 1.4f, 0.8f, 0.8f, hullDark, hull),   // engine
            part(p, 8, r * 0.26f, -r * 2.75f, 0f, r * 0.5f, 0.7f, 0.7f, 0.7f, engine, engineDark),  // glow
            part(p, 9, r * 0.26f, -r * 2.75f, 0f, -r * 0.5f, 0.7f, 0.7f, 0.7f, engine, engineDark)  // glow
        );
    }

    /** a smooth blob of the given radius, moved to (x, y, z) and stretched by (sx, sy, sz) */
    static GenericMesh part(Planet p, int seed, float radius, float x, float y, float z, float sx, float sy, float sz, Color a, Color b){
        return new MatMesh(
            new NoiseMesh(p, seed, 2, radius, 2, 0.5f, 0.5f, 1.5f, a, b, 2, 0.5f, 0.6f, 0.5f),
            new Mat3D().setToTranslationAndScaling(x, y, z, sx, sy, sz)
        );
    }

    /**
     * A small body on the orbit of Verdis with one sector (index 0) for a sector preset.
     * Not reachable from the planet list by itself: only by launching from Verdis (accessible = false).
     */
    public static Planet body(String name, Color light, Color dark, float size, int seed){
        Planet p = new Planet(name, VerdisPlanets.verdis, size){{
            hasAtmosphere = false;
            updateLighting = false;
            sectors.add(new Sector(this, PlanetGrid.Ptile.empty));
            camRadius = 0.68f * size / 0.12f;
            minZoom = 0.6f;
            drawOrbit = true;
            accessible = false;
            clipRadius = 2f;
            icon = "commandRally";
            iconColor = light;
            generator = new AsteroidGenerator();
            allowWaves = true;
            allowLaunchLoadout = true;
            allowLaunchSchematics = true;
            allowSectorInvasion = false;
            ruleSetter = r -> {
                r.waveTeam = Team.crux;
                r.placeRangeCheck = false;
            };
            meshLoader = () -> {
                Rand rand = new Rand(seed);
                Seq<GenericMesh> meshes = new Seq<>();
                meshes.add(new NoiseMesh(this, seed, 2, radius, 2, 0.55f, 0.45f, 14f, light, dark, 3, 0.6f, 0.38f, 0.5f));
                for(int i = 0; i < 6; i++){
                    meshes.add(new MatMesh(
                        new NoiseMesh(this, seed + i + 1, 1, 0.022f + rand.random(0.039f), 2, 0.6f, 0.38f, 20f, light, dark, 3, 0.6f, 0.38f, 0.5f),
                        new Mat3D().setToTranslation(Tmp.v31.setToRandomDirection(rand).setLength(rand.random(0.44f, 1.4f)))
                    ));
                }
                return new MultiMesh(meshes.toArray(GenericMesh.class));
            };
        }};
        p.launchCandidates.add(VerdisPlanets.verdis);
        VerdisPlanets.verdis.launchCandidates.add(p);
        p.defaultCore = Blocks.coreShard;
        return p;
    }
}
