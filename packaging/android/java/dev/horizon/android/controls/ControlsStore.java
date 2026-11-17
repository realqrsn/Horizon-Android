package dev.horizon.android.controls;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;

/** Saved layout, the "all button" settings, and the cog's settings. */
public final class ControlsStore {
    private static final String PREFS = "horizon_host_controls";
    private static final String LAYOUT = "layout_v2";
    private final SharedPreferences prefs;

    /** The "all button" settings from the touch editor panel. */
    public static final class Globals {
        public int opacity = 100;
        public int scale = 100;
        public int paddingDp = 8;
        public int radiusDp = 8;
        public int strokeDp = 2;
        /** 0 = as typed, 1 = uppercase, 2 = lowercase. */
        public int textCase = 0;
        public boolean snap = true;
    }

    public ControlsStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean controlsEnabled() { return prefs.getBoolean("enabled", true); }
    public void setControlsEnabled(boolean v) { prefs.edit().putBoolean("enabled", v).apply(); }
    public boolean showFps() { return prefs.getBoolean("fps", false); }
    public void setShowFps(boolean v) { prefs.edit().putBoolean("fps", v).apply(); }
    public int cogSizeDp() { return prefs.getInt("cogSize", 48); }
    public void setCogSizeDp(int v) { prefs.edit().putInt("cogSize", v).apply(); }
    public float cogX() { return prefs.getFloat("cogX", 0.493f); }
    public float cogY() { return prefs.getFloat("cogY", 0.075f); }
    public void setCogPosition(float x, float y) {
        prefs.edit().putFloat("cogX", x).putFloat("cogY", y).apply();
    }

    public Globals globals() {
        Globals g = new Globals();
        g.opacity = prefs.getInt("g_opacity", 100);
        g.scale = prefs.getInt("g_scale", 100);
        g.paddingDp = prefs.getInt("g_padding", 8);
        g.radiusDp = prefs.getInt("g_radius", 8);
        g.strokeDp = prefs.getInt("g_stroke", 2);
        g.textCase = prefs.getInt("g_text", 0);
        g.snap = prefs.getBoolean("g_snap", true);
        return g;
    }

    public void saveGlobals(Globals g) {
        prefs.edit().putInt("g_opacity", g.opacity).putInt("g_scale", g.scale)
                .putInt("g_padding", g.paddingDp).putInt("g_radius", g.radiusDp)
                .putInt("g_stroke", g.strokeDp).putInt("g_text", g.textCase)
                .putBoolean("g_snap", g.snap).apply();
    }

    public List<ControlButton> load() {
        List<ControlButton> out = new ArrayList<>();
        String json = prefs.getString(LAYOUT, null);
        if (json != null) {
            try {
                out = parse(json);
                if (!out.isEmpty()) return out;
            } catch (Exception ignored) {
                out.clear();
            }
        }
        return defaults();
    }

    public static List<ControlButton> parse(String json) throws Exception {
        List<ControlButton> out = new ArrayList<>();
        JSONArray a = new JSONArray(json);
        for (int i = 0; i < a.length(); i++) out.add(ControlButton.fromJson(a.getJSONObject(i)));
        return out;
    }

    public static String serialize(List<ControlButton> buttons) {
        try {
            JSONArray a = new JSONArray();
            for (ControlButton b : buttons) a.put(b.toJson());
            return a.toString();
        } catch (Exception e) {
            return "[]";
        }
    }

    public void save(List<ControlButton> buttons) {
        prefs.edit().putString(LAYOUT, serialize(buttons)).apply();
    }

    public void reset() {
        prefs.edit().remove(LAYOUT).apply();
    }

    public static String newId() {
        return "b" + Long.toString(System.nanoTime(), 36);
    }

    private static ControlButton make(String id, String label, String action, float x, float y, float w, float h) {
        ControlButton b = new ControlButton();
        b.id = id;
        b.label = label;
        b.action = action;
        b.x = x;
        b.y = y;
        b.widthDp = w;
        b.heightDp = h;
        return b;
    }

    private static ControlButton key(String id, String label, int code, float x, float y, float w, float h) {
        ControlButton b = make(id, label, ControlButton.KEY, x, y, w, h);
        b.keys[0] = code;
        return b;
    }

    /** Laid out like DroidBridge's default: text pills along the top, pads at the sides. */
    public static List<ControlButton> defaults() {
        List<ControlButton> l = new ArrayList<>();
        ControlButton stick = make("stick", "", ControlButton.JOYSTICK, 0.22f, 0.69f, 152, 152);
        stick.keys = new int[] {'W', 'A', 'S', 'D'};
        stick.visibleInMenu = false;
        l.add(stick);

        l.add(key("esc", "Esc", 256, 0.035f, 0.05f, 44, 22));
        l.add(key("chat", "Chat", 'T', 0.09f, 0.05f, 44, 22));
        ControlButton kb = make("keyboard", "Keyboard", ControlButton.KEYBOARD, 0.155f, 0.05f, 62, 22);
        l.add(kb);
        l.add(key("tab", "Tab", 258, 0.22f, 0.05f, 44, 22));
        ControlButton third = key("third", "3Rd", 294, 0.035f, 0.115f, 44, 22);
        third.visibleInMenu = false;
        l.add(third);
        ControlButton debug = key("debug", "Debug", 292, 0.09f, 0.115f, 44, 22);
        debug.visibleInMenu = false;
        l.add(debug);

        ControlButton gui = key("gui", "Gui", 290, 0.0275f, 0.76f, 32, 32);
        gui.visibleInMenu = false;
        l.add(gui);
        ControlButton sneak = key("sneak", "Sneak", 340, 0.0675f, 0.76f, 32, 32);
        sneak.visibleInMenu = false;
        l.add(sneak);
        l.add(key("up", "\u25B2", 265, 0.0675f, 0.85f, 32, 32));
        l.add(key("left", "\u25C0", 263, 0.0275f, 0.94f, 32, 32));
        l.add(key("down", "\u25BC", 264, 0.0675f, 0.94f, 32, 32));
        l.add(key("right", "\u25B6", 262, 0.1075f, 0.94f, 32, 32));

        for (int dir = 1; dir >= -1; dir -= 2) {
            ControlButton s = make(dir > 0 ? "scroll_up" : "scroll_down", dir > 0 ? "\u25B2" : "\u25BC",
                    ControlButton.SCROLL, 0.974f, dir > 0 ? 0.13f : 0.21f, 30, 30);
            s.scrollDirection = dir;
            s.repeatOnHold = true;
            l.add(s);
        }

        ControlButton attack = make("attack", "Attack", ControlButton.MOUSE, 0.8825f, 0.76f, 34, 34);
        attack.mouseButton = 0;
        attack.mousePassthrough = true;
        attack.visibleInMenu = false;
        l.add(attack);
        ControlButton use = make("use", "Use", ControlButton.MOUSE, 0.9225f, 0.76f, 34, 34);
        use.mouseButton = 1;
        use.mousePassthrough = true;
        use.visibleInMenu = false;
        l.add(use);
        l.add(key("inv", "Inv", 'E', 0.9225f, 0.85f, 34, 34));
        ControlButton drop = key("drop", "Drop", 'Q', 0.8825f, 0.85f, 34, 34);
        drop.visibleInMenu = false;
        l.add(drop);
        ControlButton jump = key("jump", "Jump", 32, 0.967f, 0.844f, 38, 38);
        jump.mousePassthrough = true;
        jump.visibleInMenu = false;
        l.add(jump);
        return l;
    }
}
