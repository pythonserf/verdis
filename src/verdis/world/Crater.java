package verdis.world;

import arc.math.Angles;
import arc.math.Mathf;
import arc.util.noise.Simplex;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.environment.Floor;

/**
 * Draws an impact crater out of blocks, like a moon crater:
 * lava in the middle -> magma rock -> hot rock -> basalt -> wall rim -> thrown-out basalt around.
 * All sizes are parts of the radius (0 = center, 1 = rim).
 */
public class Crater{
    // ---- tunables (parts of the radius) ----
    public static final float LAVA = 0.18f;
    public static final float MAGMA = 0.36f;
    public static final float HOT = 0.56f;
    public static final float BASALT = 0.80f;
    /** rim is between BASALT and 1.0 */
    public static final float EJECTA = 1.30f;
    /** how uneven the edge of each ring is */
    public static final float WOBBLE = 0.10f;
    /** number of gaps in the rim, so units can walk inside */
    public static final int RIM_GAPS = 3;
    /** rim wall block: name of any block, also from other mods (e.g. a New Horizon or Exogenesis wall).
     *  If the block is not found, carbon wall is used. */
    public static String rimWall = "carbon-wall";

    static Block rimBlock(){
        Block b = Vars.content.block(rimWall);
        return b != null ? b : Blocks.carbonWall;
    }

    /**
     * @param live true while playing: kills buildings and refreshes rendering/pathfinding.
     */
    public static void build(int cx, int cy, int radius, int seed, boolean live){
        Block rim = rimBlock();
        float gapOffset = Mathf.randomSeed(seed, 360f);
        int outer = (int)Math.ceil(radius * EJECTA) + 2;

        for(int x = cx - outer; x <= cx + outer; x++){
            for(int y = cy - outer; y <= cy + outer; y++){
                Tile tile = Vars.world.tile(x, y);
                if(tile == null) continue;

                float angle = Angles.angle(cx, cy, x, y);
                float wob = (Simplex.noise2d(seed, 3, 0.5, 1f / 6f, x, y) - 0.5f) * 2f * WOBBLE;
                float d = Mathf.dst(cx, cy, x, y) / radius + wob;
                if(d > EJECTA) continue;

                Floor floor = null;
                Block block = null;

                if(d < LAVA){
                    floor = Blocks.slag.asFloor();
                    block = Blocks.air;
                }else if(d < MAGMA){
                    floor = Blocks.magmarock.asFloor();
                    block = Blocks.air;
                }else if(d < HOT){
                    floor = Blocks.hotrock.asFloor();
                    block = Blocks.air;
                }else if(d < BASALT){
                    floor = Blocks.basalt.asFloor();
                    block = Mathf.randomSeed(seed + x * 7919L + y) < 0.04f ? Blocks.basaltBoulder : Blocks.air;
                }else if(d < 1f){
                    floor = Blocks.basalt.asFloor();
                    boolean gap = false;
                    for(int g = 0; g < RIM_GAPS; g++){
                        if(Angles.within(angle, gapOffset + g * 360f / RIM_GAPS, 9f)) gap = true;
                    }
                    block = gap ? Blocks.air : rim;
                }else{
                    // thrown-out rock: basalt patches that thin out with distance
                    float chance = 1f - (d - 1f) / (EJECTA - 1f);
                    if(Simplex.noise2d(seed + 1, 2, 0.5, 1f / 4f, x, y) < chance * 0.75f){
                        floor = Blocks.basalt.asFloor();
                        // natural walls nearby get partly knocked down
                        if(tile.block().isStatic() && Mathf.randomSeed(seed + x * 31L + y * 17L) < 0.5f){
                            block = Blocks.air;
                        }
                    }
                }

                apply(tile, floor, block, live);
            }
        }

        if(live) refresh(cx, cy, outer);
    }

    static void apply(Tile tile, Floor floor, Block block, boolean live){
        if(block != null){
            if(tile.build != null){
                if(live) tile.build.kill();
                tile.remove();
            }
            if(tile.block() != block) tile.setBlock(block);
        }
        if(floor != null && tile.floor() != floor){
            tile.setFloor(floor);
            tile.setOverlay(Blocks.air);
        }
    }

    static void refresh(int cx, int cy, int outer){
        for(int x = cx - outer; x <= cx + outer; x++){
            for(int y = cy - outer; y <= cy + outer; y++){
                Tile tile = Vars.world.tile(x, y);
                if(tile != null) refreshTile(tile);
            }
        }
    }

    /** after changing a tile while playing: redraw it and update unit paths */
    public static void refreshTile(Tile tile){
        tile.recache();
        Vars.pathfinder.updateTile(tile);
        if(!Vars.headless){
            Vars.renderer.blocks.floor.recacheTile(tile);
            Vars.renderer.minimap.update(tile);
        }
    }
}
