package verdis.world;

import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.gen.Sounds;
import mindustry.graphics.Layer;
import arc.util.Time;
import mindustry.content.StatusEffects;
import mindustry.core.Logic;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WaveEvent;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import mindustry.gen.Unit;
import mindustry.type.Sector;
import mindustry.type.UnitType;
import mindustry.world.Tile;
import verdis.content.VerdisSectors;

import static mindustry.Vars.*;

/**
 * Landing Glade finale: on BOSS_WAVE a giant enemy Nucleoid arrives.
 * The base can't hold it, so the core makes an emergency take-off: the sector counts as captured
 * and the core flies on to Root Maze (FLY_TO_NEXT_SECTOR), or the planet map opens.
 */
public class Finale{
    // ---- tunables ----
    public static final int BOSS_WAVE = 15;
    /** boss unit: names tried in order (first found is used) */
    public static String[] bossUnits = {"new-horizon-nucleoid", "eclipse"};
    /** the core takes off this many seconds after the boss arrives... */
    public static final float EVAC_AFTER = 45f;
    /** ...or earlier, when the boss gets this close to the core (tiles) */
    public static final float EVAC_DISTANCE = 40f;
    /** ...or when the core health drops below this part */
    public static final float EVAC_CORE_HEALTH = 0.5f;
    /** after the take-off the core lands on Root Maze by itself; false = the planet map opens to choose */
    public static final boolean FLY_TO_NEXT_SECTOR = true;
    /** time between waves on this sector is multiplied by this (once, on the first visit) */
    public static final float WAVE_SPACING_MULT = 1.5f;

    static final String TAG_BOSS = "verdis-boss", TAG_DONE = "verdis-evac-done", TAG_SPACING = "verdis-spacing";

    enum Phase{ none, bossIntro, fight, evacIntro, launching }

    /** boss arrival: how long it flies in, from how far behind its spawn, from how high (shadow distance) */
    public static final float ARRIVE_SECONDS = 6.5f;
    public static final float ARRIVE_DISTANCE = 1800f;
    public static final float ARRIVE_ALT = 500f;
    static final Color engine = Color.valueOf("b28cff");

    static float fromX, fromY, toX, toY, arriveAngle;
    static boolean arrived;

    static Phase phase = Phase.none;
    static float time;
    static Unit boss;

    public static void init(){
        Events.on(WorldLoadEvent.class, e -> {
            if(phase != Phase.none) Cinema.closeNow();
            phase = Phase.none;
            boss = null;
            if(!active()) return;

            // the HUD shows "wave X/15"; the sector is won by the evacuation, not by reaching the wave,
            // so the target is dropped the moment the boss wave starts (see WaveEvent below)
            state.rules.winWave = state.wave < BOSS_WAVE ? BOSS_WAVE : 0;
            if(!state.rules.tags.getBool(TAG_SPACING)){
                state.rules.waveSpacing *= WAVE_SPACING_MULT;
                state.rules.tags.put(TAG_SPACING, "true");
            }
            // loaded a save made during the fight: the boss is already somewhere on the map
            if(state.rules.tags.getBool(TAG_BOSS) && !state.rules.tags.getBool(TAG_DONE)){
                boss = findBoss();
                phase = Phase.fight;
                time = 0f;
            }
        });
        // fired right after the wave number goes up, before the game checks winWave again
        Events.on(WaveEvent.class, e -> {
            if(active() && state.wave >= BOSS_WAVE) state.rules.winWave = 0;
        });
        Events.run(Trigger.update, Finale::update);
        Events.run(Trigger.draw, Finale::draw);
    }

    static boolean active(){
        return state.isCampaign() && !net.client()
            && state.rules.sector != null && state.rules.sector.preset == VerdisSectors.landingGlade;
    }

    static UnitType bossType(){
        for(String name : bossUnits){
            UnitType t = content.unit(name);
            if(t != null) return t;
        }
        return null;
    }

    static Unit findBoss(){
        UnitType t = bossType();
        if(t == null) return null;
        for(Unit u : state.rules.waveTeam.data().units){
            if(u.type == t) return u;
        }
        return null;
    }

    static void update(){
        // safety: never let the game end the sector by the wave (it is won by the evacuation)
        if(active() && state.wave >= BOSS_WAVE) state.rules.winWave = 0;
        if(!active() || !state.isPlaying() || state.rules.tags.getBool(TAG_DONE)) return;
        CoreBuild core = state.rules.defaultTeam.core();
        if(core == null) return;

        if(phase == Phase.none){
            if(state.wave >= BOSS_WAVE && !state.rules.tags.getBool(TAG_BOSS)) spawnBoss(core);
            return;
        }

        time += Time.delta / 60f;

        switch(phase){
            case bossIntro -> {
                float before = time - Time.delta / 60f;
                if(!arrived){
                    Vec2 p = arrivalPos(time / ARRIVE_SECONDS);
                    Cinema.camera(p.x, p.y, 0.1f);
                    if(!headless && Mathf.chanceDelta(0.15f)) Effect.shake(1f + 3f * time / ARRIVE_SECONDS, 10f, p.x, p.y);
                    if(time >= ARRIVE_SECONDS) arrive(true);
                }else if(boss != null){
                    Cinema.camera(boss.x, boss.y, 0.06f);
                }
                if(before < ARRIVE_SECONDS + 1.2f && time >= ARRIVE_SECONDS + 1.2f) Cinema.say("verdis.finale.2");
                if(time >= ARRIVE_SECONDS + 5f) startFight();
            }
            case fight -> {
                boolean close = boss != null && boss.isValid() && boss.within(core, EVAC_DISTANCE * tilesize);
                boolean hurt = core.health < core.maxHealth * EVAC_CORE_HEALTH;
                if(time >= EVAC_AFTER || close || hurt) startEvac(core);
            }
            case evacIntro -> {
                Cinema.camera(core.x, core.y, 0.08f);
                if(time >= 3f) launch(core);
            }
            case launching -> {
                if(time >= core.launchDuration() / 60f + 0.5f){
                    phase = Phase.none;
                    if(headless) break;
                    Sector next = VerdisSectors.rootMaze == null ? null : VerdisSectors.rootMaze.sector;
                    if(FLY_TO_NEXT_SECTOR && next != null && next != state.rules.sector){
                        // the core lands on the next sector by itself, like after a normal launch
                        control.playSector(state.rules.sector, next);
                    }else{
                        ui.planet.show();
                    }
                }
            }
            default -> {}
        }
    }

    /** the boss flies in from beyond the map edge, behind the enemy spawn */
    static void spawnBoss(CoreBuild core){
        Tile spawn = spawner.getFirstSpawn();
        toX = spawn != null ? spawn.worldx() : world.unitWidth() - core.x;
        toY = spawn != null ? spawn.worldy() : world.unitHeight() - core.y;
        arriveAngle = Angles.angle(toX, toY, core.x, core.y); // it comes from the far side, facing the core
        fromX = toX - Angles.trnsx(arriveAngle, ARRIVE_DISTANCE);
        fromY = toY - Angles.trnsy(arriveAngle, ARRIVE_DISTANCE);
        arrived = false;

        phase = Phase.bossIntro;
        time = 0f;
        Cinema.open(Finale::skipArrival);
        Cinema.camera(fromX, fromY, 0.05f);
        Cinema.say("verdis.finale.1");
    }

    static final Vec2 tmp = new Vec2();

    /** slows down while arriving, like braking */
    static Vec2 arrivalPos(float p){
        float e = Interp.pow2Out.apply(Mathf.clamp(p));
        return tmp.set(Mathf.lerp(fromX, toX, e), Mathf.lerp(fromY, toY, e));
    }

    /** the drawn ship becomes the real boss unit */
    static void arrive(boolean effects){
        if(arrived) return;
        arrived = true;
        state.rules.tags.put(TAG_BOSS, "true"); // set only now: a save made during the arrival replays it
        UnitType type = bossType();
        if(type != null){
            boss = type.spawn(state.rules.waveTeam, toX, toY);
            boss.rotation = arriveAngle;
            boss.apply(StatusEffects.boss);
        }
        if(effects && !headless){
            Fx.bigShockwave.at(toX, toY);
            Fx.spawnShockwave.at(toX, toY, 300f);
            Effect.shake(14f, 70f, toX, toY);
            Sounds.explosionTitan.at(toX, toY, 0.5f, 2f);
        }
    }

    static void skipArrival(){
        arrive(false);
        startFight();
    }

    static void draw(){
        if(phase != Phase.bossIntro || arrived || !active()) return;
        UnitType type = bossType();
        TextureRegion region = type != null ? type.fullIcon : arc.Core.atlas.find("error");

        float p = Mathf.clamp(time / ARRIVE_SECONDS);
        Vec2 pos = arrivalPos(p);
        float x = pos.x, y = pos.y;
        float scale = Mathf.lerp(1.6f, 1f, Interp.pow2Out.apply(p)); // high above = closer to the camera = bigger
        float w = region.width * Draw.scl * scale, h = region.height * Draw.scl * scale;
        float alt = Mathf.lerp(ARRIVE_ALT, 12f, Interp.pow2Out.apply(p));

        Draw.z(Layer.flyingUnit + 2f);
        Draw.color(0f, 0f, 0f, 0.25f);
        Draw.rect(region, x + alt, y - alt, w, h, arriveAngle - 90f);

        // engine glow behind it, fading while it brakes
        float glow = 1f - p * 0.7f;
        for(int i = 6; i >= 1; i--){
            float back = i * w * 0.12f;
            float k = i / 6f;
            Draw.color(engine, Color.white, 1f - k);
            Draw.alpha(0.55f * glow * (1f - k * 0.8f));
            Fill.circle(x - Angles.trnsx(arriveAngle, h * 0.4f + back), y - Angles.trnsy(arriveAngle, h * 0.4f + back), w * 0.12f * (1f - k * 0.5f));
        }

        Draw.color();
        Draw.rect(region, x, y, w, h, arriveAngle - 90f);
        Draw.reset();
    }

    static void startFight(){
        if(phase != Phase.bossIntro) return;
        phase = Phase.fight;
        time = 0f;
        Cinema.close();
    }

    static void startEvac(CoreBuild core){
        phase = Phase.evacIntro;
        time = 0f;
        Cinema.open(null);
        Cinema.camera(core.x, core.y, 0.05f);
        Cinema.say("verdis.finale.3");
    }

    static void launch(CoreBuild core){
        phase = Phase.launching;
        time = 0f;
        state.rules.tags.put(TAG_DONE, "true");
        Cinema.closeNow();
        Logic.sectorCapture();
        if(!headless) renderer.showLaunch(core);
    }
}
