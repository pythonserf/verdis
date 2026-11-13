package verdis.world;

import arc.files.Fi;
import arc.struct.Seq;
import arc.util.Log;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.game.Team;
import mindustry.world.Block;
import mindustry.world.Tile;

/**
 * A hand-made part of a map that can be hidden at the start and revealed later.
 * The file is made by tools/export_scene.py from the map:
 *   F x y <real floor> <hidden floor>
 *   B x y <block>
 */
public class MapScene{
    static class Entry{
        boolean isFloor;
        int x, y;
        String real, hidden;
        boolean revealed;
    }

    final Seq<Entry> entries = new Seq<>();
    public final String name;

    public MapScene(String file){
        name = file;
        Fi fi = Vars.mods.getMod("verdis").root.child("scenes").child(file);
        if(!fi.exists()){
            Log.err("[Verdis] scene file not found: @", file);
            return;
        }
        for(String line : fi.readString().split("\n")){
            String[] p = line.trim().split(" ");
            if(p.length < 4) continue;
            Entry e = new Entry();
            e.isFloor = p[0].equals("F");
            e.x = Integer.parseInt(p[1]);
            e.y = Integer.parseInt(p[2]);
            e.real = p[3];
            e.hidden = p.length > 4 ? p[4] : null;
            entries.add(e);
        }
        Log.info("[Verdis] scene @: @ entries", file, entries.size);
    }

    /**
     * Removes the scene: blocks become their hidden block (air if none), special floors become the hidden floor.
     * Block lines may carry a hidden block (e.g. a root wall the debris will break) and a real block "-"
     * (a tile covered by a building that is placed at its center).
     */
    public void hide(){
        for(Entry e : entries){
            e.revealed = false;
            Tile tile = Vars.world.tile(e.x, e.y);
            if(tile == null) continue;
            if(e.isFloor){
                Block f = Vars.content.block(e.hidden);
                if(f != null && f.isFloor()) tile.setFloor(f.asFloor());
                continue;
            }
            if(tile.build != null) tile.build.tile.remove();
            Block hidden = e.hidden == null ? Blocks.air : Vars.content.block(e.hidden);
            if(hidden == null || hidden.hasBuilding()) hidden = Blocks.air;
            if(tile.block() != hidden) tile.setBlock(hidden);
        }
        refresh();
    }

    /** forget which parts were revealed (called when a cutscene starts again after loading) */
    public void resetRevealed(){
        for(Entry e : entries) e.revealed = false;
    }

    /** puts the scene back exactly as it was drawn in the editor; buildings belong to the derelict team */
    public void reveal(){
        revealNear(0, 0, Integer.MAX_VALUE);
    }

    /** reveals only the part of the scene within a radius (tiles) of a point */
    public void revealNear(int cx, int cy, int radius){
        Seq<Entry> part = entries.select(e -> !e.revealed && (radius == Integer.MAX_VALUE || arc.math.Mathf.within(e.x, e.y, cx, cy, radius)));
        // clear hidden blocks first, so buildings placed below find their tiles free
        for(Entry e : part){
            if(e.isFloor || e.hidden == null) continue;
            Tile tile = Vars.world.tile(e.x, e.y);
            if(tile != null && tile.build == null && tile.block() != Blocks.air) tile.setBlock(Blocks.air);
        }
        for(Entry e : part){
            e.revealed = true;
            Tile tile = Vars.world.tile(e.x, e.y);
            if(tile == null) continue;
            Block b = Vars.content.block(e.real);
            if(b == null) continue; // block from a mod that is not installed
            if(e.isFloor){
                if(b.isFloor()) tile.setFloor(b.asFloor());
            }else{
                if(tile.build != null) tile.build.kill();
                if(b.hasBuilding()) tile.setBlock(b, Team.derelict, 0);
                else tile.setBlock(b);
            }
        }
        refresh(part);
    }

    void refresh(){
        refresh(entries);
    }

    void refresh(Seq<Entry> list){
        for(Entry e : list){
            Tile tile = Vars.world.tile(e.x, e.y);
            if(tile != null) Crater.refreshTile(tile);
        }
    }
}
