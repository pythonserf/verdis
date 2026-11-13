package verdis.world;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Font;
import arc.input.KeyCode;
import arc.math.Interp;
import arc.math.Mathf;
import arc.scene.Element;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.util.Align;
import mindustry.ui.Fonts;

import static mindustry.Vars.*;

/** Cutscene helper: cinema bars, typed captions, camera control, click to skip. */
public class Cinema{
    /** letters per second in captions */
    public static final float TYPE_SPEED = 40f;

    static Overlay overlay;
    static String caption = "";
    static float captionAge;
    static boolean closing;
    static Runnable onSkip;

    /** starts a cutscene; onSkip runs when the player clicks (null = can't skip) */
    public static void open(Runnable skip){
        onSkip = skip;
        caption = "";
        closing = false;
        control.input.logicCutscene = true;
        control.input.logicHideHud = true;
        if(!headless && overlay == null){
            overlay = new Overlay();
            Core.scene.add(overlay);
        }
    }

    public static void say(String bundleKey){
        caption = Core.bundle.get(bundleKey);
        captionAge = 0f;
    }

    public static void camera(float x, float y, float speed){
        control.input.logicCamPan.set(x, y);
        control.input.logicCamSpeed = speed;
    }

    /** gives control back; the bars slide away */
    public static void close(){
        closing = true;
        onSkip = null;
        control.input.logicCutscene = false;
        control.input.logicHideHud = false;
    }

    /** removes everything at once (world change) */
    public static void closeNow(){
        close();
        if(overlay != null){
            overlay.remove();
            overlay = null;
        }
    }

    static class Overlay extends Element{
        float bars;

        Overlay(){
            addListener(new InputListener(){
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
                    if(onSkip == null) return false;
                    Runnable r = onSkip;
                    onSkip = null;
                    r.run();
                    return true;
                }
            });
        }

        @Override
        public void act(float delta){
            super.act(delta);
            setBounds(0, 0, Core.graphics.getWidth(), Core.graphics.getHeight());
            captionAge += delta;
            bars = Mathf.approachDelta(bars, closing ? 0f : 1f, 0.03f);
            if(closing && bars <= 0.001f){
                remove();
                overlay = null;
            }
        }

        @Override
        public arc.scene.Element hit(float x, float y, boolean touchable){
            // only catch clicks while skipping is possible
            return onSkip != null ? super.hit(x, y, touchable) : null;
        }

        @Override
        public void draw(){
            float bh = height * 0.13f * Interp.smooth.apply(bars);
            Draw.color(Color.black);
            Fill.crect(x, y, width, bh);
            Fill.crect(x, y + height - bh, width, bh);

            Font font = Fonts.def;
            if(!caption.isEmpty() && bars > 0.5f){
                int letters = Math.min(caption.length(), (int)(captionAge * TYPE_SPEED));
                font.setColor(Color.white);
                font.draw(caption.substring(0, letters), x + width / 2f, y + bh * 0.62f, 0f, Align.center, false);
            }
            if(bars > 0.5f && onSkip != null){
                font.setColor(Color.gray);
                font.draw(Core.bundle.get("verdis.intro.skip"), x + width - 20f, y + height - bh * 0.4f, 0f, Align.right, false);
            }
            font.setColor(Color.white);
            Draw.reset();
        }
    }
}
