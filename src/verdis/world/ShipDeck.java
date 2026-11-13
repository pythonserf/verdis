package verdis.world;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.content.Fx;
import mindustry.core.Logic;
import mindustry.entities.Effect;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WaveEvent;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.gen.Building;
import mindustry.gen.Sounds;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.type.Sector;
import verdis.content.VerdisSectors;

import static mindustry.Vars.*;

/**
 * Ship Deck, the first sector: a battle on board the ship in orbit of Verdis.
 * Short intro at the start; the HUD shows "wave X/FINAL_WAVE". When the final wave comes, the reactor is hit,
 * explosions run along the hull, the screen burns white and the game moves to Landing Glade,
 * where the ship enters the atmosphere and breaks apart (IntroCrash).
 */
public class ShipDeck{
    // ---- tunables ----
    /** the ship falls when this wave starts */
    public static final int FINAL_WAVE = 10;
    /** intro: second caption, end */
    public static final float INTRO_TEXT2 = 4f, INTRO_END = 8.5f;
    /** finale timeline, seconds: second caption, white-out start, move to Landing Glade */
    public static final float FIN_TEXT2 = 4.5f, FIN_WHITE = 8f, FIN_END = 10.5f;
    /** explosions per second along the hull during the finale */
    public static final float FIN_BLASTS = 6f;
    /** after the finale the game goes to Landing Glade by itself; false = the planet map opens */
    public static final boolean FALL_TO_GLADE = true;
    /** engine flames: x of the nozzle edge (tiles, flames go to -x) and the engines as {bottom y, top y} (tiles) */
    public static final float ENGINE_X = 21f;
    public static final int[][] ENGINES = {{39, 48}, {50, 63}, {65, 78}, {80, 89}};
    /** flame length (tiles), how much it flickers (part of the length), and its width (part of the engine height) */
    /** name of the map in the custom maps list (the flames burn there too) */
    public static final String MAP_NAME = "Палуба корабля";
    public static final float FLAME_LENGTH = 14f, FLAME_FLICKER = 0.1f, FLAME_WIDTH = 0.8f;
    /** flames per engine */
    public static final int FLAME_TONGUES = 1;

    static final String TAG_INTRO = "verdis-deck-intro", TAG_DONE = "verdis-deck-done";
    static final Color alarm = Color.valueOf("ff3b3b"), white = Color.valueOf("fff4dc");

    enum Phase{ none, intro, finale }

    static Phase phase = Phase.none;
    static float time;

    public static void init(){
        Events.on(WorldLoadEvent.class, e -> {
            if(phase != Phase.none) Cinema.closeNow();
            phase = Phase.none;
            if(!active()) return;
            // HUD shows "wave X/FINAL_WAVE"; the target is dropped when the final wave starts (WaveEvent),
            // so the game itself never ends the sector by the wave
            state.rules.winWave = state.wave < FINAL_WAVE ? FINAL_WAVE : 0;
        });
        Events.on(WaveEvent.class, e -> {
            if(active() && state.wave >= FINAL_WAVE) state.rules.winWave = 0;
        });
        Events.run(Trigger.update, ShipDeck::update);
        Events.run(Trigger.draw, ShipDeck::draw);
    }

    static boolean active(){
        return state.isCampaign() && !net.client()
            && state.rules.sector != null && state.rules.sector.preset == VerdisSectors.shipDeck;
    }

    static void update(){
        if(!active() || !state.isPlaying()) return;
        // safety: never let the game end the sector by the wave (the finale does)
        if(state.wave >= FINAL_WAVE) state.rules.winWave = 0;

        if(phase == Phase.none){
            if(!state.rules.tags.getBool(TAG_INTRO)){
                state.rules.tags.put(TAG_INTRO, "true");
                startIntro();
            }else if(state.wave >= FINAL_WAVE && !state.rules.tags.getBool(TAG_DONE)){
                startFinale();
            }
            return;
        }

        float before = time;
        time += Time.delta / 60f;

        if(phase == Phase.intro){
            if(passed(before, INTRO_TEXT2)) Cinema.say("verdis.deck.2");
            if(time >= INTRO_END) endIntro();
            return;
        }

        // finale
        Building core = state.rules.defaultTeam.core();
        if(core != null && time < FIN_WHITE) Cinema.camera(core.x, core.y, 0.04f);
        if(!headless && time < FIN_WHITE && Mathf.chanceDelta(FIN_BLASTS / 60f)) blast();
        if(passed(before, FIN_TEXT2)) Cinema.say("verdis.deck.4");
        if(passed(before, FIN_WHITE) && !headless) Sounds.explosionReactor.at(Core.camera.position.x, Core.camera.position.y, 0.6f, 2f);
        if(time >= FIN_END) fall();
    }

    static boolean passed(float before, float mark){
        return before < mark && time >= mark;
    }

    static void startIntro(){
        phase = Phase.intro;
        time = 0f;
        Cinema.open(ShipDeck::endIntro);
        Building core = state.rules.defaultTeam.core();
        if(core != null) Cinema.camera(core.x, core.y, 0.05f);
        Cinema.say("verdis.deck.1");
    }

    static void endIntro(){
        if(phase != Phase.intro) return;
        phase = Phase.none;
        Cinema.close();
    }

    static void startFinale(){
        phase = Phase.finale;
        time = 0f;
        Cinema.open(null);
        Cinema.say("verdis.deck.3");
        if(!headless){
            Effect.shake(18f, 90f, Core.camera.position.x, Core.camera.position.y);
            Sounds.explosionTitan.at(Core.camera.position.x, Core.camera.position.y, 0.7f, 2f);
        }
    }

    /** an explosion somewhere on the hull */
    static void blast(){
        for(int tries = 0; tries < 20; tries++){
            int x = Mathf.random(world.width() - 1), y = Mathf.random(world.height() - 1);
            var tile = world.tile(x, y);
            if(tile == null || tile.floor().isDeep() || tile.floor().solid) continue;
            float wx = x * tilesize, wy = y * tilesize;
            Fx.massiveExplosion.at(wx, wy);
            Effect.shake(5f, 25f, wx, wy);
            if(Mathf.chance(0.3)) Sounds.explosion.at(wx, wy, Mathf.random(0.7f, 1f), 0.8f);
            return;
        }
    }

    /** the ship leaves the orbit: sector done, the game goes down to Landing Glade */
    static void fall(){
        phase = Phase.none;
        state.rules.tags.put(TAG_DONE, "true");
        Cinema.closeNow();
        Logic.sectorCapture();
        if(headless) return;
        Sector glade = VerdisSectors.landingGlade == null ? null : VerdisSectors.landingGlade.sector;
        if(FALL_TO_GLADE && glade != null){
            control.playSector(state.rules.sector, glade);
        }else{
            ui.planet.show();
        }
    }

    static final Color flameOuter = Color.valueOf("ff7a3c"), flameMid = Color.valueOf("ffb35c"), flameCore = Color.valueOf("fff4dc");

    /** steady engine flames behind the stern */
    static void drawFlames(){
        Draw.z(Layer.effect);
        float x = ENGINE_X * tilesize - tilesize / 2f;
        for(int i = 0; i < ENGINES.length; i++){
            float y0 = ENGINES[i][0] * tilesize - tilesize / 2f, y1 = ENGINES[i][1] * tilesize + tilesize / 2f;
            float cy = (y0 + y1) / 2f, half = (y1 - y0) / 2f * FLAME_WIDTH;
            // one big flame per engine (FLAME_TONGUES > 1 splits it into smaller ones side by side)
            int tongues = FLAME_TONGUES;
            for(int t = 0; t < tongues; t++){
                float ty = y0 + (y1 - y0) * (t + 0.5f) / tongues, th = (y1 - y0) / tongues / 2f * (tongues == 1 ? FLAME_WIDTH : 1.1f);
                float len = FLAME_LENGTH * tilesize * (1f - FLAME_FLICKER + Mathf.absin(Time.time + i * 31f + t * 17f, 4f + t, FLAME_FLICKER * 2f));
                flame(x, ty, th, len, flameOuter, 0.85f);
                flame(x, ty, th * 0.65f, len * 0.7f, flameMid, 0.95f);
                flame(x, ty, th * 0.3f, len * 0.4f, flameCore, 1f);
            }
            Drawf.light(x - FLAME_LENGTH * tilesize * 0.4f, cy, half * 3f + FLAME_LENGTH * tilesize, flameOuter, 0.8f);
        }
        Draw.reset();
    }

    static void flame(float x, float y, float halfWidth, float length, Color color, float alpha){
        Draw.color(color, alpha);
        Fill.tri(x, y - halfWidth, x, y + halfWidth, x - length, y);
    }

    /** the flames also burn when the map is played as a custom game (file ship-deck.msav or the same map name) */
    static boolean shipMap(){
        if(state.rules.sector != null) return state.rules.sector.preset == VerdisSectors.shipDeck;
        if(state.map == null) return false;
        return (state.map.file != null && state.map.file.nameWithoutExtension().equals("ship-deck"))
            || MAP_NAME.equals(state.map.name());
    }

    static void draw(){
        if(state.isGame() && shipMap()) drawFlames();
        if(phase != Phase.finale || !active()) return;
        float cx = Core.camera.position.x, cy = Core.camera.position.y, w = Core.camera.width * 1.2f, h = Core.camera.height * 1.2f;
        Draw.z(Layer.overlayUI - 1f);
        // red alarm light, pulsing
        if(time < FIN_WHITE){
            Draw.color(alarm, 0.12f + Mathf.absin(Time.time, 8f, 0.12f));
            Fill.rect(cx, cy, w, h);
        }
        // burning white towards the end
        if(time >= FIN_WHITE){
            Draw.color(white, Mathf.clamp((time - FIN_WHITE) / (FIN_END - FIN_WHITE - 0.3f)));
            Fill.rect(cx, cy, w, h);
        }
        Draw.reset();
    }
}
