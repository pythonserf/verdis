package verdis.world;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.content.Fx;
import mindustry.entities.Damage;
import mindustry.entities.Effect;
import mindustry.entities.Fires;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.gen.Sounds;
import mindustry.graphics.Layer;
import mindustry.world.Tile;
import verdis.content.VerdisSectors;

import static mindustry.Vars.*;

/**
 * Landing Glade: after a while a meteorite falls on the moss pad and leaves a crater.
 * Also redraws the old crater of the map in the new crater style once.
 */
public class Meteors{
    // ---- tunables ----
    /** game time on the sector before the meteorite hits (the intro cutscene takes about 20 s of it) */
    public static final float DELAY_MINUTES = 1f;
    /** warning message this many seconds before the fall starts */
    public static final float WARNING_SECONDS = 20f;
    /** how long the meteorite is visible while falling */
    public static final float FALL_SECONDS = 4f;
    /** damage to everything near the impact (buildings and units of all teams) */
    public static final float IMPACT_DAMAGE = 2500f;

    /** impact point: the moss pad in the metal frame (map tiles) */
    public static final int PAD_X = 205, PAD_Y = 261, PAD_RADIUS = 24;
    /** old crater of the map, redrawn in the new style */
    public static final boolean REDRAW_OLD_CRATER = false; // the old crater was removed from the map
    public static final int OLD_X = 263, OLD_Y = 205, OLD_RADIUS = 34;

    static final String TAG_DONE = "verdis-meteor-done", TAG_OLD = "verdis-old-crater";
    static final Color hot = Color.valueOf("ffb35c"), core = Color.valueOf("fff1c2");

    static boolean warned, pendingOld;
    static float fallTime = -1f;
    static double warnTick;

    public static void init(){
        Events.on(WorldLoadEvent.class, e -> {
            warned = false;
            fallTime = -1f;
            pendingOld = active() && REDRAW_OLD_CRATER && !state.rules.tags.getBool(TAG_OLD);
        });
        Events.run(Trigger.update, Meteors::update);
        Events.run(Trigger.draw, Meteors::draw);
    }

    static boolean active(){
        return state.isCampaign() && !net.client()
            && state.rules.sector != null && state.rules.sector.preset == VerdisSectors.landingGlade;
    }

    static void update(){
        if(!active() || !state.isPlaying()) return;

        // first frame after loading: redraw the old crater once
        if(pendingOld){
            pendingOld = false;
            Crater.build(OLD_X, OLD_Y, OLD_RADIUS, 101, true);
            state.rules.tags.put(TAG_OLD, "true");
        }

        if(state.rules.tags.getBool(TAG_DONE)) return;

        // not during the intro cutscene; the warning always comes WARNING_SECONDS before the fall
        if(IntroCrash.running) return;
        double delay = DELAY_MINUTES * 60f * 60f;
        if(!warned && state.tick >= delay - WARNING_SECONDS * 60f){
            warned = true;
            warnTick = state.tick;
            if(!headless) ui.showInfoToast(Core.bundle.get("verdis.meteor.warning"), 8f);
        }
        if(fallTime < 0f && warned && state.tick >= delay && state.tick >= warnTick + WARNING_SECONDS * 60f){
            fallTime = 0f;
        }
        if(fallTime >= 0f){
            fallTime += Time.delta;
            if(fallTime >= FALL_SECONDS * 60f) impact();
        }
    }

    static void impact(){
        fallTime = -1f;
        state.rules.tags.put(TAG_DONE, "true");

        float wx = PAD_X * tilesize, wy = PAD_Y * tilesize;
        Damage.damage(wx, wy, PAD_RADIUS * 1.3f * tilesize, IMPACT_DAMAGE);
        Crater.build(PAD_X, PAD_Y, PAD_RADIUS, 202, true);

        // fires on the hot ring
        for(int i = 0; i < 40; i++){
            float a = Mathf.random(360f), r = Mathf.random(0.3f, 0.6f) * PAD_RADIUS;
            Tile t = world.tile(PAD_X + (int)(Mathf.cosDeg(a) * r), PAD_Y + (int)(Mathf.sinDeg(a) * r));
            if(t != null) Fires.create(t);
        }

        if(!headless){
            Fx.impactReactorExplosion.at(wx, wy);
            for(int i = 0; i < 12; i++){
                Fx.massiveExplosion.at(wx + Mathf.range(PAD_RADIUS * 5f), wy + Mathf.range(PAD_RADIUS * 5f));
            }
            Effect.shake(25f, 150f, wx, wy);
            Sounds.explosionReactor.at(wx, wy, 0.7f, 2f);
            ui.showInfoToast(Core.bundle.get("verdis.meteor.impact"), 6f);
        }
    }

    static void draw(){
        if(!active() || state.rules.tags.getBool(TAG_DONE) || !warned) return;

        float wx = PAD_X * tilesize, wy = PAD_Y * tilesize;
        Draw.z(Layer.overlayUI);

        // pulsing warning circle on the impact zone
        Lines.stroke(3f, Color.scarlet);
        Draw.alpha(0.5f + Mathf.absin(Time.time, 8f, 0.5f));
        Lines.circle(wx, wy, PAD_RADIUS * tilesize);
        Lines.circle(wx, wy, PAD_RADIUS * tilesize * 0.25f);

        if(fallTime >= 0f){
            float p = Mathf.clamp(fallTime / (FALL_SECONDS * 60f));
            float ease = p * p;

            // shadow grows while it gets closer
            Draw.color(Color.black, 0.15f + 0.45f * p);
            Fill.circle(wx, wy, 10f + p * PAD_RADIUS * tilesize * 0.6f);

            // meteorite flies in from the upper left
            float sx = wx - 900f, sy = wy + 1300f;
            float mx = Mathf.lerp(sx, wx, ease), my = Mathf.lerp(sy, wy, ease);
            float size = 10f + p * 26f;

            // trail
            for(int i = 8; i >= 1; i--){
                float back = Math.max(ease - i * 0.035f, 0f);
                float tx = Mathf.lerp(sx, wx, back), ty = Mathf.lerp(sy, wy, back);
                Draw.color(hot, Color.scarlet, i / 8f);
                Draw.alpha(0.7f * (1f - i / 9f));
                Fill.circle(tx, ty, size * (1f - i / 10f));
            }
            Draw.color(hot);
            Fill.circle(mx, my, size);
            Draw.color(core);
            Fill.circle(mx, my, size * 0.55f);
        }
        Draw.reset();
    }
}
