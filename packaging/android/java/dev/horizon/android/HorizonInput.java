package dev.horizon.android;

/**
 * Host-app controls bridge. A Java overlay sends GLFW-style input to the Horizon engine
 * through these natives (see crates/horizon-client/src/android/host_input_jni.rs).
 * Every call is safe before the engine library is loaded.
 */
public final class HorizonInput {
    private HorizonInput() {}

    public static void key(int glfwKey, boolean pressed) {
        try { nativeKey(glfwKey, pressed); } catch (UnsatisfiedLinkError ignored) {}
    }
    public static void text(String text) {
        try { nativeText(text); } catch (UnsatisfiedLinkError ignored) {}
    }
    public static void cursor(float x, float y) {
        try { nativeCursor(x, y); } catch (UnsatisfiedLinkError ignored) {}
    }
    public static void cursorSilently(float x, float y) {
        try { nativeCursorSilently(x, y); } catch (UnsatisfiedLinkError ignored) {}
    }
    public static void mouseButton(int glfwButton, boolean pressed) {
        try { nativeMouseButton(glfwButton, pressed); } catch (UnsatisfiedLinkError ignored) {}
    }
    public static void scroll(float x, float y) {
        try { nativeScroll(x, y); } catch (UnsatisfiedLinkError ignored) {}
    }
    public static boolean isGrabbed() {
        try { return nativeIsGrabbed(); } catch (UnsatisfiedLinkError e) { return false; }
    }
    public static void setImeViewportBottomInset(int px) {
        try { nativeSetImeViewportBottomInset(px); } catch (UnsatisfiedLinkError ignored) {}
    }
    public static int fps() {
        try { return nativeFps(); } catch (UnsatisfiedLinkError e) { return 0; }
    }
    /** Tells the engine the Java overlay is drawing the controls, so it hides its own. */
    public static void setControlsAttached(boolean attached) {
        try { nativeSetControlsAttached(attached); } catch (UnsatisfiedLinkError ignored) {}
    }
    /** True while a world is open; false on the launcher, title and loading screens. */
    public static boolean isInGame() {
        try { return nativeIsInGame(); } catch (UnsatisfiedLinkError e) { return false; }
    }
    /** Asks the engine to open its on-screen keyboard (chat). */
    public static void requestKeyboard() {
        try { nativeRequestKeyboard(); } catch (UnsatisfiedLinkError ignored) {}
    }
    public static int cursorShape() {
        try { return nativeCursorShape(); } catch (UnsatisfiedLinkError e) { return 0; }
    }

    private static native void nativeKey(int glfwKey, boolean pressed);
    private static native void nativeText(String text);
    private static native void nativeCursor(float x, float y);
    private static native void nativeCursorSilently(float x, float y);
    private static native void nativeMouseButton(int glfwButton, boolean pressed);
    private static native void nativeScroll(float x, float y);
    private static native boolean nativeIsGrabbed();
    private static native void nativeSetImeViewportBottomInset(int bottomInsetPx);
    private static native int nativeFps();
    private static native int nativeCursorShape();
    private static native boolean nativeIsInGame();
    private static native void nativeRequestKeyboard();
    private static native void nativeSetControlsAttached(boolean attached);
}
