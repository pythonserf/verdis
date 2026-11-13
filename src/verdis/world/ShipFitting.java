package verdis.world;

import arc.Events;
import arc.files.Fi;
import arc.struct.Seq;
import arc.util.Log;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Items;
import mindustry.game.EventType.Trigger;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.type.Item;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.blocks.power.Battery;
import mindustry.world.blocks.power.PowerNode;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.blocks.storage.StorageBlock;

import static mindustry.Vars.*;

/**
 * Ship Deck: puts the ship's own defense and fittings (turrets, generators, storage, hangar) on the map
 * once, on the first visit. The layout is assets/scenes/ship-deck-fit.txt (format in its header).
 * Done at runtime because modded turrets cannot be written into a map file by a script.
 */
public class ShipFitting{
    // ---- tunables ----
    /** place the fittings on the first visit (off: the automatic layout looked bad, it is being redone) */
    public static final boolean ENABLED = false;
    /** remove what an earlier version placed, once per save */
    public static final boolean CLEAN_UP = true;
    public static final String LAYOUT = "ship-deck-fit.txt";
    /** default power node for "node" lines */
    public static final String NODE = "power-node-large";
    /** free tiles kept between placed buildings */
    public static final int GAP = 1;
    /** items put into every storage block (vault, container) */
    public static final Item[] STORAGE_ITEMS = {Items.copper, Items.lead, Items.graphite, Items.silicon, Items.metaglass};
    public static final int[] STORAGE_AMOUNTS = {300, 300, 150, 150, 80};
    /** fuel for item-burning generators */
    public static final Item FUEL = Items.coal;

    static final String TAG = "verdis-deck-fitted", TAG_CLEAN = "verdis-deck-unfitted";

    public static void init(){
        Events.run(Trigger.update, () -> {
            if(!state.isGame() || net.client() || !ShipDeck.shipMap()) return;
            if(CLEAN_UP && state.rules.tags.getBool(TAG) && !state.rules.tags.getBool(TAG_CLEAN)){
                state.rules.tags.put(TAG_CLEAN, "true");
                try{
                    cleanUp();
                }catch(Throwable e){
                    Log.err("[Verdis] ship fitting clean-up failed", e);
                }
                return;
            }
            if(!ENABLED || state.rules.tags.getBool(TAG)) return;
            state.rules.tags.put(TAG, "true");
            try{
                apply();
            }catch(Throwable e){
                Log.err("[Verdis] ship fitting failed", e);
            }
        });
    }

    static class Box{
        int x1, y1, x2, y2;
        Box(String[] p, int from){
            int a = Integer.parseInt(p[from]), b = Integer.parseInt(p[from + 1]), c = Integer.parseInt(p[from + 2]), d = Integer.parseInt(p[from + 3]);
            x1 = Math.min(a, c); x2 = Math.max(a, c); y1 = Math.min(b, d); y2 = Math.max(b, d);
        }
        boolean has(int x, int y){ return x >= x1 && x <= x2 && y >= y1 && y <= y2; }
    }

    static final Seq<Box> keep = new Seq<>();
    static int placed, skipped;

    static void apply(){
        Fi fi = Vars.mods.getMod("verdis").root.child("scenes").child(LAYOUT);
        if(!fi.exists()){
            Log.warn("[Verdis] no @", LAYOUT);
            return;
        }
        Team team = state.rules.defaultTeam;
        keep.clear();
        placed = skipped = 0;
        Seq<String[]> zones = new Seq<>();
        Seq<Building> nodes = new Seq<>();

        // keep boxes first, then the nodes at their exact places, then the zones
        for(String raw : fi.readString().split("\n")){
            String line = raw.trim();
            if(line.isEmpty() || line.startsWith("#")) continue;
            String[] p = line.split("\\s+");
            switch(p[0]){
                case "keep" -> keep.add(new Box(p, 1));
                case "zone" -> zones.add(p);
                case "node" -> {}
                default -> Log.warn("[Verdis] @: unknown line '@'", LAYOUT, line);
            }
        }
        for(String raw : fi.readString().split("\n")){
            String[] p = raw.trim().split("\\s+");
            if(!p[0].equals("node")) continue;
            Block block = find(p.length > 3 ? p[3] : NODE);
            int x = Integer.parseInt(p[1]), y = Integer.parseInt(p[2]);
            if(block != null && fits(block, x, y, 0, false)){
                Building b = place(block, team, x, y);
                if(b != null) nodes.add(b);
            }else{
                skipped++;
            }
        }
        for(String[] p : zones) fillZone(p, team);

        // power: every node links to what is in its range (other nodes first), like when a player builds it
        // (Building.placed() of a node does nothing once the node has a link, so the links are made here:
        // other nodes first, then the nearest buildings, never two in the same power graph)
        Seq<Building> all = new Seq<>();
        for(Building b : team.data().buildings) if(b.power != null) all.add(b);
        for(Building node : nodes){
            if(!node.isValid() || !(node.block instanceof PowerNode pn)) continue;
            all.sort(o -> (o.block instanceof PowerNode ? 0f : 1e9f) + o.dst2(node));
            for(Building other : all){
                if(other == node || !other.isValid() || other.power.graph == node.power.graph) continue;
                if(!pn.linkValid(node, other, true)) continue;
                if(other.block instanceof PowerNode on && !on.linkValid(other, node, true)) continue;
                node.configureAny(other.pos());
            }
        }
        Log.info("[Verdis] ship fitting: @ placed, @ skipped, @ power nodes", placed, skipped, nodes.size);
    }

    /** removes the buildings of the blocks named in the layout (the author's own blocks are not in it) */
    static void cleanUp(){
        Fi fi = Vars.mods.getMod("verdis").root.child("scenes").child(LAYOUT);
        Seq<Block> blocks = new Seq<>();
        blocks.add(find(NODE));
        for(String raw : fi.readString().split("\n")){
            String[] p = raw.trim().split("\\s+");
            if(!p[0].equals("zone")) continue;
            for(int i = 6; i < p.length; i++){
                Block b = find(p[i]);
                if(b != null && !blocks.contains(b)) blocks.add(b);
            }
        }
        Seq<Building> remove = new Seq<>();
        for(Building b : state.rules.defaultTeam.data().buildings) if(blocks.contains(b.block)) remove.add(b);
        for(Building b : remove) if(b.isValid()) b.tile.remove();
        Log.info("[Verdis] ship fitting clean-up: @ buildings removed", remove.size);
    }

    static void fillZone(String[] p, Team team){
        Box box = new Box(p, 1);
        int max = Integer.parseInt(p[5]);
        Seq<Block> blocks = new Seq<>();
        for(int i = 6; i < p.length; i++){
            Block b = find(p[i]);
            if(b != null) blocks.add(b);
            else Log.warn("[Verdis] @: no block '@'", LAYOUT, p[i]);
        }
        if(blocks.isEmpty()) return;
        int count = 0, turn = 0, misses = 0;
        while(count < max && misses < blocks.size){
            Block block = blocks.get(turn % blocks.size);
            turn++;
            if(placeIn(box, block, team)){
                count++;
                misses = 0;
            }else{
                misses++;
                skipped++;
            }
        }
    }

    /** first free spot in the box, from the bow side (high x) backwards */
    static boolean placeIn(Box box, Block block, Team team){
        int off = (block.size - 1) / 2;
        for(int x = box.x2 - block.size + 1 + off; x >= box.x1 + off; x--){
            for(int y = box.y1 + off; y <= box.y2 - block.size + 1 + off; y++){
                if(fits(block, x, y, GAP, true)){
                    return place(block, team, x, y) != null;
                }
            }
        }
        return false;
    }

    /** the block's tiles are free floor; with a gap, no building around it either */
    static boolean fits(Block block, int x, int y, int gap, boolean checkKeep){
        int off = (block.size - 1) / 2;
        for(int dx = -gap; dx < block.size + gap; dx++){
            for(int dy = -gap; dy < block.size + gap; dy++){
                int tx = x - off + dx, ty = y - off + dy;
                Tile t = world.tile(tx, ty);
                boolean inner = dx >= 0 && dy >= 0 && dx < block.size && dy < block.size;
                if(inner){
                    if(t == null || t.block() != Blocks.air || t.floor().isDeep() || !t.floor().placeableOn || t.floor().solid) return false;
                    if(checkKeep && keep.contains(k -> k.has(tx, ty))) return false;
                }else if(t != null && t.build != null){
                    return false;
                }
            }
        }
        return true;
    }

    static Building place(Block block, Team team, int x, int y){
        Tile tile = world.tile(x, y);
        tile.setBlock(block, team, 0);
        Building b = tile.build;
        if(b == null) return null;
        placed++;
        stock(b);
        return b;
    }

    /** loaded turrets, fueled generators, charged batteries, filled storage */
    static void stock(Building b){
        Block block = b.block;
        if(block instanceof ItemTurret turret){
            for(Item item : turret.ammoTypes.keys()){
                int n = b.acceptStack(item, 10000, null);
                if(n > 0){
                    b.handleStack(item, n, null);
                    break;
                }
            }
        }else if(block instanceof Battery){
            if(b.power != null) b.power.status = 1f;
        }else if(block instanceof StorageBlock && !(block instanceof CoreBlock)){
            for(int i = 0; i < STORAGE_ITEMS.length; i++){
                b.items.add(STORAGE_ITEMS[i], Math.min(STORAGE_AMOUNTS[i], block.itemCapacity));
            }
        }else if(block.hasItems && !block.consumesPower && block.outputsPower && b.acceptItem(b, FUEL)){
            int n = b.acceptStack(FUEL, 10000, null);
            if(n > 0) b.handleStack(FUEL, n, null);
            else b.items.add(FUEL, block.itemCapacity);
        }
    }

    /** "a|b|c": the first block that exists; a short name also finds a modded block ("w-comet" -> "<mod>-w-comet") */
    static Block find(String names){
        for(String n : names.split("\\|")){
            Block b = content.block(n);
            if(b == null){
                for(Block c : content.blocks()){
                    if(c.minfo.mod != null && c.name.endsWith("-" + n)){
                        b = c;
                        break;
                    }
                }
            }
            if(b != null) return b;
        }
        return null;
    }
}
