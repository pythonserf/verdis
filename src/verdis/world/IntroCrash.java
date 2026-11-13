package verdis.world;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Font;
import arc.graphics.g2d.TextureRegion;
import arc.input.KeyCode;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.scene.Element;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.util.Align;
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
import mindustry.ui.Fonts;
import mindustry.world.Block;
import mindustry.world.Tile;
import verdis.content.VerdisSectors;

import static mindustry.Vars.*;

/**
 * Intro cutscene of Landing Glade.
 * A giant burning ship flies over the sector and breaks apart: pieces fall onto the glade
 * (the crash site and debris drawn in the editor appear where they hit), and the ship itself
 * goes over the horizon and crashes somewhere on another sector.
 * Click anywhere to skip.
 */
public class IntroCrash{
    // ---- tunables ----
    /** ship sprite: unit names tried in order (first found is used) */
    public static String[] shipUnits = {"new-horizon-collapser", "eclipse"};
    public static final float SHIP_SCALE = 1f;
    /** ship flight: from / to, relative to the main impact point (world units, 8 = one tile) */
    public static final float FROM_X = -2600f, FROM_Y = 1500f;
    public static final float TO_X = 3200f, TO_Y = -1300f;
    /** ship height over the ground at the start and at the end (shadow distance) */
    public static final float ALT_START = 420f, ALT_END = 120f;

    /** pieces: target tile x, y, size, scene radius to reveal, moment of breaking off (part of the flight 0..1) */
    public static final float[][] PIECES = {
        {27, 228, 1.2f, 30, 0.16f},   // left pile
        {122, 280, 1.2f, 30, 0.25f},  // top pile
        {140, 175, 2.4f, 0, 0.34f},   // main section with the core: reveals everything left (radius 0)
    };
    /** how long a piece falls */
    public static final float PIECE_FALL = 1.8f;

    // timeline, seconds from the start of the cutscene
    public static final float T_TEXT1 = 0.6f;
    public static final float T_FLY = 2f;
    public static final float FLY_SECONDS = 10f;
    static final float MAIN_BREAK = 0.34f;
    public static final float T_IMPACT = T_FLY + MAIN_BREAK * FLY_SECONDS + PIECE_FALL;
    public static final float T_TEXT2 = T_IMPACT + 1.2f;
    public static final float T_FAR = T_FLY + FLY_SECONDS + 0.6f;
    public static final float T_TEXT3 = T_FAR + 0.4f;
    public static final float T_TEXT4 = T_FAR + 4f;
    public static final float T_TO_CORE = T_FAR + 6f;
    public static final float T_END = T_FAR + 8.5f;

    static final String TAG_HIDDEN = "verdis-crash-hidden", TAG_DONE = "verdis-crash-done", TAG_IMPACT = "verdis-crash-impact";
    static final Color flame = Color.valueOf("ffb35c"), flameCore = Color.valueOf("fff1c2");

    static MapScene scene;
    static boolean pending, running;
    static boolean[] landed = new boolean[PIECES.length];
    static float time;

    public static void init(){
        scene = new MapScene("crash-site.txt");

        Events.on(WorldLoadEvent.class, e -> {
            stop();
            pending = active() && !state.rules.tags.getBool(TAG_DONE);
        });
        Events.run(Trigger.update, IntroCrash::update);
        Events.run(Trigger.draw, IntroCrash::draw);
    }

    static boolean active(){
        return state.isCampaign() && !net.client()
            && state.rules.sector != null && state.rules.sector.preset == VerdisSectors.landingGlade;
    }

    static float mainX(){ return PIECES[PIECES.length - 1][0] * tilesize; }
    static float mainY(){ return PIECES[PIECES.length - 1][1] * tilesize; }

    // ---------- logic ----------

    static void update(){
        if(pending && state.isPlaying()){
            pending = false;
            if(!state.rules.tags.getBool(TAG_HIDDEN)){
                scene.hide();
                state.rules.tags.put(TAG_HIDDEN, "true");
            }
            // saved after the main crash but before the cutscene ended: don't replay
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

        if(passed(before, T_TEXT1)) say("verdis.intro.1");

        // camera: follows the ship, then the main section, then stays on the crash site
        float mainBreak = T_FLY + MAIN_BREAK * FLY_SECONDS;
        if(time >= T_FLY && time < mainBreak){
            control.input.logicCamPan.set(shipPos(flyProgress()));
            control.input.logicCamSpeed = 0.08f;
        }else if(time >= mainBreak && time < T_IMPACT){
            control.input.logicCamPan.set(piecePos(PIECES.length - 1));
            control.input.logicCamSpeed = 0.12f;
        }

        // smoke behind the ship
        if(!headless && time >= T_FLY && time < T_FLY + FLY_SECONDS){
            Vec2 s = shipPos(flyProgress());
            for(int i = 0; i < 3; i++){
                if(Mathf.chanceDelta(0.9f)) Fx.fireSmoke.at(s.x + Mathf.range(60f), s.y + Mathf.range(60f));
            }
        }

        for(int i = 0; i < PIECES.length; i++){
            if(!landed[i] && time >= landTime(i)) land(i, true);
        }

        if(passed(before, T_TEXT2)) say("verdis.intro.2");
        if(passed(before, T_FAR)) farCrash();
        if(passed(before, T_TEXT3)) say("verdis.intro.3");
        if(passed(before, T_TEXT4)) say("verdis.intro.4");
        if(passed(before, T_TO_CORE)){
            Building core = state.rules.defaultTeam.core();
            if(core != null){
                control.input.logicCamPan.set(core.x, core.y);
                control.input.logicCamSpeed = 0.03f;
            }
        }
        if(time >= T_END) finish();
    }

    static boolean passed(float before, float mark){
        return before < mark && time >= mark;
    }

    static void start(){
        running = true;
        time = 0f;
        landed = new boolean[PIECES.length];
        scene.resetRevealed();
        Cinema.open(IntroCrash::skip);
        Cinema.camera(mainX() + FROM_X * 0.6f, mainY() + FROM_Y * 0.6f, 0.05f);
    }

    static void say(String key){
        Cinema.say(key);
    }

    /** a piece hits the ground: explosion, fire, its part of the scene appears */
    static void land(int i, boolean effects){
        landed[i] = true;
        float[] p = PIECES[i];
        int tx = (int)p[0], ty = (int)p[1], radius = (int)p[3];
        boolean main = i == PIECES.length - 1;

        if(main) scene.reveal();
        else scene.revealNear(tx, ty, radius);
        if(main) state.rules.tags.put(TAG_IMPACT, "true");

        for(int f = 0; f < (main ? 30 : 8); f++){
            Tile t = world.tile(tx + Mathf.range(main ? 14 : 6), ty + Mathf.range(main ? 14 : 6));
            if(t != null) Fires.create(t);
        }

        if(effects && !headless){
            float x = tx * tilesize, y = ty * tilesize;
            if(main){
                Fx.impactReactorExplosion.at(x, y);
                for(int k = 0; k < 10; k++) Fx.massiveExplosion.at(x + Mathf.range(90f), y + Mathf.range(90f));
                Effect.shake(22f, 120f, x, y);
                Sounds.explosionReactor.at(x, y, 0.8f, 2f);
            }else{
                for(int k = 0; k < 3; k++) Fx.massiveExplosion.at(x + Mathf.range(30f), y + Mathf.range(30f));
                Effect.shake(8f, 40f, x, y);
                Sounds.explosionTitan.at(x, y, 1f, 1.2f);
            }
        }
    }

    /** the ship goes down beyond the horizon, on another sector */
    static void farCrash(){
        if(headless) return;
        Vec2 end = shipPos(1f);
        Effect.shake(6f, 90f, end.x, end.y);
        Sounds.explosionReactor.at(Core.camera.position.x, Core.camera.position.y, 0.5f, 0.6f);
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
        return tmp.set(Mathf.lerp(mainX() + FROM_X, mainX() + TO_X, f), Mathf.lerp(mainY() + FROM_Y, mainY() + TO_Y, f));
    }

    static float breakTime(int i){
        return T_FLY + PIECES[i][4] * FLY_SECONDS;
    }

    static float landTime(int i){
        return breakTime(i) + PIECE_FALL;
    }

    /** piece position: breaks off the ship and falls to its target, keeping some of the ship's speed */
    static Vec2 piecePos(int i){
        float[] p = PIECES[i];
        Vec2 from = shipPos(p[4]);
        float fx = from.x, fy = from.y;
        float f = Mathf.clamp((time - breakTime(i)) / PIECE_FALL);
        float e = Interp.pow2In.apply(f);
        return tmp2.set(Mathf.lerp(fx, p[0] * tilesize, Interp.pow2Out.apply(f)), Mathf.lerp(fy, p[1] * tilesize, e));
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

        // pieces
        TextureRegion pr = pieceRegion();
        for(int i = 0; i < PIECES.length; i++){
            if(landed[i] || time < breakTime(i)) continue;
            Vec2 pos = piecePos(i);
            float f = Mathf.clamp((time - breakTime(i)) / PIECE_FALL);
            float size = PIECES[i][2] * pr.width * Draw.scl * Mathf.lerp(1.4f, 1f, f);
            float rot = time * 120f * (i % 2 == 0 ? 1 : -1);
            float alt = (1f - f) * 160f;

            Draw.color(0f, 0f, 0f, 0.3f * f);
            Draw.rect(pr, pos.x + alt, pos.y - alt, size, size, rot);
            fireBall(pos.x, pos.y, size * 0.45f, 1f);
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
            float scale = SHIP_SCALE * Mathf.lerp(0.85f, 1.05f, fly);
            float w = region.width * Draw.scl * scale, h = region.height * Draw.scl * scale;
            float alt = Mathf.lerp(ALT_START, ALT_END, fly);

            Draw.color(0f, 0f, 0f, 0.22f);
            Draw.rect(region, x + alt, y - alt, w, h, angle - 90f);

            // burning trail
            for(int i = 12; i >= 1; i--){
                Vec2 b = shipPos(Math.max(fly - i * 0.012f, 0f));
                float k = i / 12f;
                Draw.color(flame, Color.darkGray, k);
                Draw.alpha(0.6f * (1f - k));
                Fill.circle(b.x, b.y, w * 0.25f * (1f - k * 0.5f));
            }

            Draw.color(Color.white.cpy().lerp(Color.orange, 0.15f + Mathf.absin(Time.time, 3f, 0.1f)));
            Draw.rect(region, x, y, w, h, angle - 90f);

            // fires on the hull
            for(int i = 0; i < 7; i++){
                float ox = Mathf.randomSeedRange(i * 13L, w * 0.3f), oy = Mathf.randomSeedRange(i * 31L, h * 0.3f);
                float rx = Angles.trnsx(angle - 90f, ox, oy), ry = Angles.trnsy(angle - 90f, ox, oy);
                fireBall(x + rx, y + ry, w * 0.05f + Mathf.absin(Time.time + i * 9f, 4f, w * 0.03f), 0.85f);
            }
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
