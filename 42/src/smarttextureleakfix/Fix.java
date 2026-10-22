package smarttextureleakfix;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.core.textures.SmartTexture;
import zombie.core.textures.Texture;
import zombie.core.textures.TextureCombiner;

/**
 * Vanilla SmartTexture.calculate() does {@code result = TextureCombiner.instance.combine(commands)} without
 * giving the previous result back. A character's clothing/body texture (ModelInstance.tex is a
 * CharacterSmartTexture / ItemSmartTexture that is cleared and recalculated in place on every model reset:
 * blood, dirt, holes, hue changes) therefore leaks one GPU texture per recomposite, sized like the source
 * textures (up to 2048x2048 = 16 MB for a full-size mod texture). Only destroy() ever released it.
 *
 * Fix: before calculate() runs, hand the old result back to the combiner exactly as destroy() does
 * (its per-size pool, from which the combine that follows takes its target). Callers that take a temporary
 * result for themselves (ModelInstanceTextureCreator, ItemModelRenderer, WorldItemAtlas, Viewpoint's
 * ItemTextures) null the field first, so nothing still in use is released. Renderers read result through
 * the SmartTexture at draw time, so they pick up the new one. All of this runs on the render thread, where
 * ModelInstance.reset() also queues destroy() after nulling ModelInstance.tex, so a destroyed SmartTexture
 * (whose stale result destroy() leaves set) is never recalculated. Checked against 42.21 classes.
 */
public final class Fix {
    private Fix() {}

    private static long released;
    private static long lastLog;

    @Patch(className = "zombie.core.textures.SmartTexture", methodName = "calculate")
    public static final class Calculate {
        @Patch.OnEnter
        public static void enter(@Patch.This Object self) {
            beforeCalculate(self);
        }
    }

    public static void beforeCalculate(Object self) {
        try {
            SmartTexture st = (SmartTexture) self;
            Texture old = st.result;
            if (old == null) return;
            st.result = null;
            TextureCombiner.instance.releaseTexture(old);
            released++;
            long now = System.currentTimeMillis();
            if (now - lastLog > 60_000L) {
                lastLog = now;
                System.out.println("[SmartTextureLeakFix] stale combined textures returned so far: " + released);
            }
        } catch (Throwable t) {
            System.out.println("[SmartTextureLeakFix] ERROR " + t);
        }
    }

    public static long released() {
        return released;
    }
}
