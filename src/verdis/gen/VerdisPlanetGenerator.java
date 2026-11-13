package verdis.gen;

import arc.graphics.Color;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.Rand;
import arc.math.geom.Vec3;
import arc.struct.Seq;
import arc.util.noise.Ridged;
import arc.util.noise.Simplex;
import mindustry.ai.Astar;
import mindustry.content.Blocks;
import mindustry.game.Schematics;
import mindustry.game.Waves;
import mindustry.maps.generators.PlanetGenerator;
import mindustry.type.Sector;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.TileGen;

import static mindustry.Vars.state;

/**
 * Terrain for every Verdis sector.
 * Step 1: basic living-planet terrain (moss, trees, lakes, hot basalt spots),
 * core in the middle, enemy spawn on the edge, guaranteed path between them.
 */
public class VerdisPlanetGenerator extends PlanetGenerator{
    // ---- tunables ----
    /** height below which there is water */
    public static final float WATER = 0.15f;
    /** walls disappear where ridged noise is above this; higher = more walls/trees */
    public static final float OPEN_THRESHOLD = 0.31f;
    /** how far the enemy spawn is from the core, as part of the map size */
    public static final float SPAWN_DISTANCE = 0.38f;

    @Override
    public void generateSector(Sector sector){
        super.generateSector(sector);
        // threat grows with distance from the start sector, with some noise
        Sector start = sector.planet.sectors.get(sector.planet.startSector);
        float dst = sector.tile.v.dst(start.tile.v) / 2f; // 0 = start, 1 = opposite side
        float noise = Simplex.noise3d(seed + 11, 2, 0.5, 2.0, sector.tile.v.x, sector.tile.v.y, sector.tile.v.z);
        sector.threat = Mathf.clamp(dst * 1.15f + (noise - 0.5f) * 0.3f);
    }

    public float rawHeight(Vec3 p){
        float n = Simplex.noise3d(seed, 7, 0.5, 1f / 3f, p.x, p.y, p.z);
        return (Mathf.pow(n, 2.3f) + 0.05f) / 1.05f;
    }

    @Override
    public float getHeight(Vec3 position){
        return Math.max(rawHeight(position), WATER);
    }

    @Override
    public void getColor(Vec3 position, Color out){
        Block block = getBlock(position);
        out.set(block.mapColor).a(1f - block.albedo);
    }

    Block getBlock(Vec3 p){
        float h = rawHeight(p);
        if(h < WATER - 0.04f) return Blocks.deepwater;
        if(h < WATER) return Blocks.water;
        if(h < WATER + 0.015f) return Blocks.sand;

        float heat = Simplex.noise3d(seed + 5, 2, 0.5, 0.8, p.x, p.y, p.z);
        if(heat > 0.78f && h > 0.25f) return Blocks.hotrock;
        if(heat > 0.72f && h > 0.25f) return Blocks.basalt;

        if(h > 0.42f) return Blocks.stone;

        float moist = Simplex.noise3d(seed + 3, 3, 0.5, 1.2, p.x, p.y, p.z);
        if(moist > 0.60f) return Blocks.sporeMoss;
        if(moist > 0.45f) return Blocks.moss;
        if(moist > 0.32f) return Blocks.grass;
        return Blocks.dirt;
    }

    /** the wall/tree that grows on a floor */
    static Block wallFor(Block floor){
        if(floor == Blocks.moss || floor == Blocks.grass) return Blocks.pine;
        if(floor == Blocks.sporeMoss) return Blocks.sporePine;
        if(floor == Blocks.sand) return Blocks.sandWall;
        if(floor == Blocks.dirt) return Blocks.dirtWall;
        if(floor == Blocks.stone || floor == Blocks.basalt || floor == Blocks.hotrock) return Blocks.stoneWall;
        return Blocks.air;
    }

    @Override
    public void genTile(Vec3 position, TileGen tile){
        tile.floor = getBlock(position);
        tile.block = wallFor(tile.floor);
        if(Ridged.noise3d(seed + 1, position.x, position.y, position.z, 2, 22f) > OPEN_THRESHOLD){
            tile.block = Blocks.air;
        }
    }

    @Override
    protected void generate(){
        cells(4);
        distort(10f, 12f);

        int cx = width / 2, cy = height / 2;
        float angle = rand.random(360f);
        int ex = Mathf.clamp((int)(cx + Angles.trnsx(angle, width * SPAWN_DISTANCE)), 12, width - 13);
        int ey = Mathf.clamp((int)(cy + Angles.trnsy(angle, height * SPAWN_DISTANCE)), 12, height - 13);

        // guaranteed ground path from the enemy spawn to the core
        Seq<Tile> path = pathfind(cx, cy, ex, ey,
            t -> (t.solid() ? 60f : 0f) + (t.floor().isDeep() ? 40f : 0f),
            Astar.manhattan);
        brush(path, 6);
        for(Tile t : path){
            for(int dx = -4; dx <= 4; dx++){
                for(int dy = -4; dy <= 4; dy++){
                    Tile o = tiles.get(t.x + dx, t.y + dy);
                    if(o != null && o.floor().isDeep()) o.setFloor(Blocks.water.asFloor());
                }
            }
        }

        erase(cx, cy, 15);
        erase(ex, ey, 10);
        dryLand(cx, cy, 14);
        dryLand(ex, ey, 8);

        ores(Seq.with(Blocks.oreCopper, Blocks.oreLead, Blocks.oreCoal, Blocks.oreTitanium));
        trimDark();
        decoration(0.017f);

        tiles.getn(ex, ey).setOverlay(Blocks.spawn);
        Schematics.placeLaunchLoadout(cx, cy);

        float difficulty = sector.threat;
        state.rules.waves = true;
        state.rules.spawns = Waves.generate(difficulty, new Rand(sector.id), false);
        state.rules.winWave = sector.info.winWave = 10 + 5 * (int)Math.max(difficulty * 10, 1);
        state.rules.waveSpacing = Mathf.lerp(60f * 65f * 2f, 60f * 60f, difficulty);
    }

    /** turns water into moss around a point, so buildings can stand there */
    void dryLand(int x, int y, int radius){
        for(int dx = -radius; dx <= radius; dx++){
            for(int dy = -radius; dy <= radius; dy++){
                if(dx * dx + dy * dy > radius * radius) continue;
                Tile t = tiles.get(x + dx, y + dy);
                if(t != null && t.floor().isLiquid) t.setFloor(Blocks.moss.asFloor());
            }
        }
    }
}
