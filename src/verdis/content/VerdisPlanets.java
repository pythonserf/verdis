package verdis.content;

import arc.graphics.Color;
import arc.math.Mathf;
import arc.util.Log;
import mindustry.content.Blocks;
import mindustry.content.Planets;
import mindustry.game.Team;
import mindustry.graphics.g3d.HexMesh;
import mindustry.graphics.g3d.HexSkyMesh;
import mindustry.graphics.g3d.MultiMesh;
import mindustry.graphics.g3d.PlanetGrid;
import mindustry.type.Planet;
import mindustry.type.Sector;
import verdis.gen.VerdisPlanetGenerator;

public class VerdisPlanets{
    // ---- tunables ----
    /** planet size; Serpulo is 1.0 */
    public static final float RADIUS = 1.1f;
    /** sector grid subdivisions; Serpulo uses 3 */
    public static final int SECTOR_SIZE = 3;
    /** where the orbit lies between Serpulo (0) and Erekir (1) */
    public static final float ORBIT_POSITION = 0.35f;
    /** the start sector is searched near this one: the closest land sector that touches water */
    public static final int START_SECTOR_HINT = 10;
    /** how high above the water the start sector's middle must be (bigger = farther from water) */
    public static final float START_LAND_MARGIN = 0.03f;

    public static Planet verdis;

    public static void load(){
        Planet serpulo = Planets.serpulo, erekir = Planets.erekir;

        verdis = new Planet("verdis", Planets.sun, RADIUS, SECTOR_SIZE){{
            generator = new VerdisPlanetGenerator();
            meshLoader = () -> new HexMesh(this, 6);
            cloudMeshLoader = () -> new MultiMesh(
                new HexSkyMesh(this, 4, 0.15f, 0.13f, 5, Color.valueOf("ffffff").a(0.75f), 2, 0.45f, 0.9f, 0.40f),
                new HexSkyMesh(this, 9, 0.60f, 0.16f, 5, Color.valueOf("eef2ff").a(0.75f), 2, 0.45f, 1.0f, 0.42f)
            );

            atmosphereColor = Color.valueOf("7a6cf0");
            atmosphereRadIn = 0.02f;
            atmosphereRadOut = 0.3f;
            iconColor = Color.valueOf("8fd0ff");
            landCloudColor = Color.white.cpy().a(0.6f);

            orbitRadius = Mathf.lerp(serpulo.orbitRadius, erekir.orbitRadius, ORBIT_POSITION);
            orbitTime = Mathf.lerp(serpulo.orbitTime, erekir.orbitTime, ORBIT_POSITION);

            startSector = START_SECTOR_HINT;
            sectorSeed = 7;
            alwaysUnlocked = true;
            defaultCore = Blocks.coreShard;
            launchCapacityMultiplier = 0.5f;
            allowWaves = true;
            allowLaunchLoadout = true;
            allowLaunchSchematics = true;
            allowSelfSectorLaunch = true;
            allowSectorInvasion = false;
            ruleSetter = r -> {
                r.waveTeam = Team.crux;
                r.placeRangeCheck = false;
            };
        }};
        verdis.parent.updateTotalRadius();
        verdis.startSector = findStartSector(verdis);

        // temporary: Serpulo research tree until Verdis gets its own
        verdis.techTree = serpulo.techTree;

        // launching between planets
        serpulo.launchCandidates.add(verdis);
        erekir.launchCandidates.add(verdis);
        verdis.launchCandidates.addAll(serpulo, erekir);

        Log.info("[Verdis] orbit: serpulo=@ verdis=@ erekir=@", serpulo.orbitRadius, verdis.orbitRadius, erekir.orbitRadius);
    }

    /** a land neighbour of a sector, the one farthest from water (inland); used for the second hand-made sector */
    public static int nextSector(Planet planet, int from){
        if(planet.sectors.isEmpty() || !(planet.generator instanceof VerdisPlanetGenerator g)) return from + 1;
        Sector start = planet.sectors.get(from);
        Sector best = null;
        float bestHeight = -Float.MAX_VALUE;
        for(PlanetGrid.Ptile n : start.tile.tiles){
            Sector s = planet.sectors.get(n.id);
            float height = g.rawHeight(s.tile.v);
            if(height > bestHeight){
                bestHeight = height;
                best = s;
            }
        }
        int id = best == null ? from + 1 : best.id;
        Log.info("[Verdis] root maze sector: @ (next to @)", id, from);
        return id;
    }

    /** the land sector closest to the hint whose neighbours include water (a coast) */
    static int findStartSector(Planet planet){
        if(planet.sectors.isEmpty() || !(planet.generator instanceof VerdisPlanetGenerator g)) return START_SECTOR_HINT;
        float water = VerdisPlanetGenerator.WATER;
        Sector hint = planet.sectors.get(Math.min(START_SECTOR_HINT, planet.sectors.size - 1));
        Sector best = null;
        float bestDst = Float.MAX_VALUE;
        for(Sector s : planet.sectors){
            if(g.rawHeight(s.tile.v) < water + START_LAND_MARGIN) continue;
            boolean coast = false;
            for(PlanetGrid.Ptile n : s.tile.tiles){
                if(g.rawHeight(n.v) < water) coast = true;
            }
            if(!coast) continue;
            float dst = s.tile.v.dst(hint.tile.v);
            if(dst < bestDst){
                bestDst = dst;
                best = s;
            }
        }
        int id = best == null ? START_SECTOR_HINT : best.id;
        Log.info("[Verdis] start sector: @ (hint @)", id, START_SECTOR_HINT);
        return id;
    }
}
