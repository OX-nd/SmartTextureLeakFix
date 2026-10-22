import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import zombie.core.textures.SmartTexture;
import zombie.core.textures.Texture;
import zombie.core.textures.TextureCombiner;
import zombie.core.textures.TextureFBO;

/**
 * Applies SmartTextureLeakFix with the real ZombieBuddy agent and calls the real (patched)
 * SmartTexture.calculate() on a GL context: the previous result must land in the combiner's pool for
 * its size instead of being dropped, and the field must be cleared before the vanilla code runs.
 * args: <SmartTextureLeakFix.jar>
 */
public class ApplyCheck {
    static int fails;
    static sun.misc.Unsafe U;

    public static void main(String[] args) throws Exception {
        File jar = new File(args[0]);
        me.zed_0xff.zombie_buddy.PatchEngine.applyPatches("smarttextureleakfix",
            new URLClassLoader(new URL[] { jar.toURI().toURL() }, ClassLoader.getSystemClassLoader()));
        Field uf = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        uf.setAccessible(true);
        U = (sun.misc.Unsafe) uf.get(null);

        if (!GLFW.glfwInit()) throw new RuntimeException("glfwInit");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        long w = GLFW.glfwCreateWindow(16, 16, "t", 0, 0);
        GLFW.glfwMakeContextCurrent(w);
        GL.createCapabilities();

        // a pool entry for 2048x2048, as TextureCombiner.createTexture would have made
        Texture poolTex = fakeTexture(2048, 2048);
        TextureFBO fbo = (TextureFBO) U.allocateInstance(TextureFBO.class);
        set(TextureFBO.class, fbo, "texture", poolTex);
        set(TextureFBO.class, fbo, "width", 2048);
        set(TextureFBO.class, fbo, "height", 2048);
        Class<?> cfCls = Class.forName("zombie.core.textures.TextureCombiner$CombinerFBO");
        Constructor<?> cc = cfCls.getDeclaredConstructor();
        cc.setAccessible(true);
        Object cf = cc.newInstance();
        set(cfCls, cf, "fbo", fbo);
        @SuppressWarnings("unchecked")
        ArrayList<Object> pool = (ArrayList<Object>) get(TextureCombiner.class, TextureCombiner.instance, "fboPool");
        pool.add(cf);
        @SuppressWarnings("unchecked")
        ArrayDeque<Texture> deque = (ArrayDeque<Texture>) get(cfCls, cf, "textures");
        System.out.println("pool fbo size = " + fbo.getWidth() + "x" + fbo.getHeight());

        SmartTexture st = new SmartTexture();
        Texture old = fakeTexture(2048, 2048);
        st.result = old;
        try {
            st.calculate();          // vanilla part has no commands to combine; may log/throw — irrelevant here
        } catch (Throwable t) {
            System.out.println("(vanilla calculate after the fix threw " + t + ")");
        }
        check("old result returned to the 2048 pool", deque.contains(old) ? 1 : 0, 1);
        check("result no longer the old texture", st.result == old ? 0 : 1, 1);
        check("released counter", (int) smarttextureleakfix.Fix.released(), 1);

        // no previous result: nothing released
        SmartTexture st2 = new SmartTexture();
        try { st2.calculate(); } catch (Throwable t) { }
        check("empty result untouched", (int) smarttextureleakfix.Fix.released(), 1);

        GLFW.glfwDestroyWindow(w);
        GLFW.glfwTerminate();
        System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }

    static Texture fakeTexture(int w, int h) throws Exception {
        Texture t = (Texture) U.allocateInstance(Texture.class);
        set(Texture.class, t, "width", w);
        set(Texture.class, t, "height", h);
        Class<?> pc = Class.forName("zombie.asset.Asset$PRIVATE");
        Object priv = U.allocateInstance(pc);
        set(pc, priv, "currentState", zombie.asset.Asset.State.READY);
        set(zombie.asset.Asset.class, t, "priv", priv);
        return t;
    }

    static void set(Class<?> c, Object o, String n, Object v) throws Exception {
        Field f = c.getDeclaredField(n);
        f.setAccessible(true);
        f.set(o, v);
    }

    static Object get(Class<?> c, Object o, String n) throws Exception {
        Field f = c.getDeclaredField(n);
        f.setAccessible(true);
        return f.get(o);
    }

    static void check(String what, int got, int want) {
        boolean ok = got == want;
        if (!ok) fails++;
        System.out.println((ok ? "ok   " : "FAIL ") + what + " = " + got + (ok ? "" : " (want " + want + ")"));
    }
}
