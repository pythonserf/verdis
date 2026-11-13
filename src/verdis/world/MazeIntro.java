package verdis.world;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.util.Time;
import mindustry.content.Blocks;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.entities.Fires;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.gen.Building;
import mindustry.gen.Sounds;
import mindustry.graphics.Layer;
import mindustry.type.UnitType;
import mindustry.world.Block;
import mindustry.world.Tile;
import verdis.content.VerdisSectors;

import static mindustry.Vars.*;

/**
 * Intro cutscene of Root Maze (ship log record).
 * The burning ship passes over the maze and goes beyond the horizon; far away it blows up with a huge
 * explosion, and debris rains down onto the maze, breaking the root walls. The last and biggest piece is
 * one of the generators needed for the launch. The debris drawn in the editor appears where pieces hit.
 * Click anywhere to skip.
 */
public class MazeIntro{
    // ---- tunables ----
    /** ship sprite: unit names tried in order (first found is used) */
    public static String[] shipUnits = {"new-horizon-collapser", "eclipse"};
    public static final float SHIP_SCALE = 0.9f;
    /** ship flight in world units (8 = one tile): from below-left of the map to far beyond its top-right corner */
    public static final float FROM_X = -400f, FROM_Y = -250f;
    public static final float TO_X = 2600f, TO_Y = 2300f;
    /** ship height over the ground at the start and at the end (shadow distance) */
    public static final float ALT_START = 300f, ALT_END = 200f;
    /** part of the flight after which the camera stops following the ship and waits at the map edge */
    public static final float CAMERA_LEAVE = 0.62f;

    /** pieces: target tile x, y, size, scene radius to reveal (0 = everything left), delay after the rain starts (s).
     *  The last one is the generator. Centers and radii come from tools/diff_scene.py. */
    public static final float[][] PIECES = {
        {27, 7, 0.9f, 10, 0.0f},
        {8, 24, 0.9f, 11, 0.6f},
        {52, 45, 1.1f, 13, 1.3f},
        {133, 59, 1.2f, 14, 2.0f},
        {170, 39, 1.7f, 30, 2.8f},
        {68, 93, 2.6f, 0, 3.9f},
    };
    /** how long a piece falls, and from how far away (towards the explosion) it starts */
    public static final float PIECE_FALL = 1.9f, PIECE_FROM = 900f;

    // timeline, seconds from the start of the cutscene
    public static final float T_TEXT1 = 0.6f;
    public static final float T_FLY = 2f;
    public static final float FLY_SECONDS = 8f;
    public static final float T_BOOM = T_FLY + FLY_SECONDS + 0.8f;
    public static final float T_TEXT2 = T_BOOM + 0.5f;
    public static final float T_RAIN = T_BOOM + 2.2f;
    /** explosion flash: how long it fades, and how bright it starts (0..1) */
    public static final float FLASH_SECONDS = 2.2f, FLASH_ALPHA = 0.85f;

    static final String TAG_HIDDEN = "verdis-maze-hidden", TAG_DONE = "verdis-maze-done", TAG_IMPACT = "verdis-maze-impact";
    static final Color flame = Color.valueOf("ffb35c"), flameCore = Color.valueOf("fff1c2"), flash = Color.valueOf("fff4dc");

    static MapScene scene;
    static boolean pending, running;
    static boolean[] landed = new boolean[PIECES.length];
    static float time;

    public static void init(){
        scene = new MapScene("maze-debris.txt");

        Events.on(WorldLoadEvent.class, e -> {
            stop();
            pending = active() && !state.rules.tags.getBool(TAG_DONE);
        });
        Events.run(Trigger.update, MazeIntro::update);
        Events.run(Trigger.draw, MazeIntro::draw);
    }

    static boolean active(){
        return state.isCampaign() && !net.client()
            && state.rules.sector != null && state.rules.sector.preset == VerdisSectors.rootMaze;
    }

    static int last(){ return PIECES.length - 1; }

    static float lastLand(){ return landTime(last()); }
    static float tText3(){ return lastLand() + 1.4f; }
    static float tToCore(){ return lastLand() + 5f; }
    static float tText4(){ return lastLand() + 5.4f; }
    static float tEnd(){ return lastLand() + 8.5f; }

    // ---------- logic ----------

    static void update(){
        if(pending && state.isPlaying()){
            pending = false;
            if(!state.rules.tags.getBool(TAG_HIDDEN)){
                scene.hide();
                state.rules.tags.put(TAG_HIDDEN, "true");
            }
            // saved after the generator fell but before the cutscene ended: don't replay
            if(state.rules.tags.getBool(TAG_IMPACT)){
                scene.reveal();
                state.rules.tags.put(TAG_DONE, "true");
                return;
            }
            start();
        }
        if(!running) return;
        if(!active()){ stop(); return; }

        float before = time;
        time += Time.delta / 60f;

        if(passed(before, T_TEXT1)) Cinema.say("verdis.maze.1");

        // camera: follows the ship until it leaves the map, waits at the edge, then follows each falling piece
        float fly = flyProgress();
        if(time >= T_FLY && fly < CAMERA_LEAVE){
            Cinema.camera(shipPos(fly).x, shipPos(fly).y, 0.08f);
        }else if(time >= T_FLY && time < T_RAIN){
            Vec2 edge = shipPos(CAMERA_LEAVE);
            Cinema.camera(edge.x, edge.y, 0.05f);
        }else if(time >= T_RAIN && time < lastLand()){
            int i = falling();
            if(i >= 0){
                Vec2 p = piecePos(i);
                Cinema.camera(p.x, p.y, 0.1f);
            }
        }

        // smoke behind the ship
        if(!headless && time >= T_FLY && fly < 1f){
            Vec2 s = shipPos(fly);
            for(int i = 0; i < 3; i++){
                if(Mathf.chanceDelta(0.9f)) Fx.fireSmoke.at(s.x + Mathf.range(50f), s.y + Mathf.range(50f));
            }
        }

        if(passed(before, T_BOOM)) farExplosion();
        if(passed(before, T_TEXT2)) Cinema.say("verdis.maze.2");

        for(int i = 0; i < PIECES.length; i++){
            if(!landed[i] && time >= landTime(i)) land(i, true);
        }

        if(passed(before, tText3())) Cinema.say("verdis.maze.3");
        if(passed(before, tToCore())){
            Building core = state.rules.defaultTeam.core();
            if(core != null) Cinema.camera(core.x, core.y, 0.03f);
        }
        if(passed(before, tText4())) Cinema.say("verdis.maze.4");
        if(time >= tEnd()) finish();
    }

    static boolean passed(float before, float mark){
        return before < mark && time >= mark;
    }

    /** the piece the camera should watch: the latest one that started falling and has not landed */
    static int falling(){
        int found = -1;
        for(int i = 0; i < PIECES.length; i++){
            if(time >= breakTime(i) && !landed[i]) found = i;
        }
        return found;
    }

    static void start(){
        running = true;
        time = 0f;
        landed = new boolean[PIECES.length];
        scene.resetRevealed();
        Cinema.open(MazeIntro::skip);
        Vec2 s = shipPos(0.15f);
        Cinema.camera(s.x, s.y, 0.05f);
    }

    /** the ship blows up beyond the horizon: flash, long shake, low boom */
    static void farExplosion(){
        if(headless) return;
        float x = Core.camera.position.x, y = Core.camera.position.y;
        Effect.shake(26f, 150f, x, y);
        Sounds.explosionReactor.at(x, y, 0.45f, 2f);
        Sounds.explosionTitan.at(x, y, 0.6f, 1.5f);
    }

    /** a piece hits the maze: explosion, fire, broken roots, its part of the scene appears */
    static void land(int i, boolean effects){
        landed[i] = true;
        float[] p = PIECES[i];
        int tx = (int)p[0], ty = (int)p[1], radius = (int)p[3];
        boolean generator = i == last();

        if(generator || radius <= 0){
            scene.reveal();
            state.rules.tags.put(TAG_IMPACT, "true");
        }else{
            scene.revealNear(tx, ty, radius);
        }

        int spread = (int)(4 + p[2] * 4);
        for(int f = 0; f < (int)(p[2] * 6); f++){
            Tile t = world.tile(tx + Mathf.range(spread), ty + Mathf.range(spread));
            if(t != null) Fires.create(t);
        }

        if(effects && !headless){
            float x = tx * tilesize, y = ty * tilesize;
            int blasts = (int)(p[2] * 3);
            for(int k = 0; k < blasts; k++) Fx.massiveExplosion.at(x + Mathf.range(spread * 6f), y + Mathf.range(spread * 6f));
            // shattered roots
            for(int k = 0; k < (int)(p[2] * 10); k++) Fx.breakBlock.at(x + Mathf.range(spread * 8f), y + Mathf.range(spread * 8f), 2f);
            if(generator){
                Fx.impactReactorExplosion.at(x, y);
                Effect.shake(22f, 110f, x, y);
                Sounds.explosionReactor.at(x, y, 0.8f, 2f);
            }else{
                Effect.shake(6f + p[2] * 4f, 40f, x, y);
                Sounds.explosionTitan.at(x, y, 1f, 1.1f);
            }
        }
    }

    /** click: skip the rest */
    static void skip(){
        if(!running) return;
        for(int i = 0; i < PIECES.length; i++){
            if(!landed[i]) land(i, false);
        }
        finish();
    }

    static void finish(){
        state.rules.tags.put(TAG_DONE, "true");
        running = false;
        Cinema.close();
    }

    /** world changed: drop everything at once */
    static void stop(){
        if(running) Cinema.closeNow();
        running = false;
    }

    // ---------- motion ----------

    static float flyProgress(){
        return Mathf.clamp((time - T_FLY) / FLY_SECONDS);
    }

    static final Vec2 tmp = new Vec2(), tmp2 = new Vec2();

    static Vec2 shipPos(float f){
        return tmp.set(Mathf.lerp(FROM_X, TO_X, f), Mathf.lerp(FROM_Y, TO_Y, f));
    }

    static float breakTime(int i){
        return T_RAIN + PIECES[i][4];
    }

    static float landTime(int i){
        return breakTime(i) + PIECE_FALL;
    }

    /** piece position: comes from the explosion side and drops onto its target */
    static Vec2 piecePos(int i){
        float[] p = PIECES[i];
        float tx = p[0] * tilesize, ty = p[1] * tilesize;
        float angle = Angles.angle(tx, ty, TO_X, TO_Y);
        float fx = tx + Angles.trnsx(angle, PIECE_FROM), fy = ty + Angles.trnsy(angle, PIECE_FROM);
        float f = Mathf.clamp((time - breakTime(i)) / PIECE_FALL);
        return tmp2.set(Mathf.lerp(fx, tx, Interp.pow2Out.apply(f)), Mathf.lerp(fy, ty, Interp.pow2In.apply(f)));
    }

    // ---------- drawing ----------

    static TextureRegion shipRegion(){
        for(String name : shipUnits){
            UnitType type = content.unit(name);
            if(type != null) return type.fullIcon;
        }
        return Core.atlas.find("error");
    }

    static TextureRegion pieceRegion(){
        Block b = content.block("scrap-wall-huge");
        return (b != null ? b : Blocks.scrapWall).fullIcon;
    }

    static void draw(){
        if(!running) return;
        Draw.z(Layer.flyingUnit + 2f);

        // falling pieces
        TextureRegion pr = pieceRegion();
        for(int i = 0; i < PIECES.length; i++){
            if(landed[i] || time < breakTime(i)) continue;
            Vec2 pos = piecePos(i);
            float f = Mathf.clamp((time - breakTime(i)) / PIECE_FALL);
            float size = PIECES[i][2] * pr.width * Draw.scl * Mathf.lerp(1.5f, 1f, f);
            float rot = time * 140f * (i % 2 == 0 ? 1 : -1);
            float alt = (1f - f) * 150f;

            Draw.color(0f, 0f, 0f, 0.3f * f);
            Draw.rect(pr, pos.x + alt, pos.y - alt, size, size, rot);
            fireBall(pos.x, pos.y, size * 0.5f, 1f);
            Draw.color(Color.lightGray);
            Draw.rect(pr, pos.x, pos.y, size, size, rot);
        }

        // the ship
        float fly = flyProgress();
        if(time >= T_FLY && fly < 1f){
            TextureRegion region = shipRegion();
            Vec2 s = shipPos(fly);
            float x = s.x, y = s.y;
            float angle = Angles.angle(FROM_X, FROM_Y, TO_X, TO_Y);
            float w = region.width * Draw.scl * SHIP_SCALE, h = region.height * Draw.scl * SHIP_SCALE;
            float alt = Mathf.lerp(ALT_START, ALT_END, fly);

            Draw.color(0f, 0f, 0f, 0.22f);
            Draw.rect(region, x + alt, y - alt, w, h, angle - 90f);

            for(int i = 12; i >= 1; i--){
                Vec2 b = shipPos(Math.max(fly - i * 0.012f, 0f));
                float k = i / 12f;
                Draw.color(flame, Color.darkGray, k);
                Draw.alpha(0.6f * (1f - k));
                Fill.circle(b.x, b.y, w * 0.25f * (1f - k * 0.5f));
            }

            Draw.color(Color.white.cpy().lerp(Color.orange, 0.15f + Mathf.absin(Time.time, 3f, 0.1f)));
            Draw.rect(region, x, y, w, h, angle - 90f);
            for(int i = 0; i < 7; i++){
                float ox = Mathf.randomSeedRange(i * 13L, w * 0.3f), oy = Mathf.randomSeedRange(i * 31L, h * 0.3f);
                float rx = Angles.trnsx(angle - 90f, ox, oy), ry = Angles.trnsy(angle - 90f, ox, oy);
                fireBall(x + rx, y + ry, w * 0.05f + Mathf.absin(Time.time + i * 9f, 4f, w * 0.03f), 0.85f);
            }
        }

        // flash of the far explosion over the whole view
        if(time >= T_BOOM && time < T_BOOM + FLASH_SECONDS){
            float a = FLASH_ALPHA * (1f - Interp.pow2Out.apply((time - T_BOOM) / FLASH_SECONDS));
            Draw.z(Layer.overlayUI - 1f);
            Draw.color(flash, a);
            Fill.rect(Core.camera.position.x, Core.camera.position.y, Core.camera.width * 1.2f, Core.camera.height * 1.2f);
        }
        Draw.reset();
    }

    static void fireBall(float x, float y, float r, float alpha){
        Draw.color(flame, alpha);
        Fill.circle(x, y, r);
        Draw.color(flameCore, alpha);
        Fill.circle(x, y, r * 0.5f);
    }
}
