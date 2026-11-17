package dev.horizon.android.controls;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Saved layout and the cog's settings. */
public final class ControlsStore {
    private static final String PREFS = "horizon_host_controls";
    private static final String LAYOUT = "layout_v1";
    private final SharedPreferences prefs;

    public ControlsStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean controlsEnabled() { return prefs.getBoolean("enabled", true); }
    public void setControlsEnabled(boolean v) { prefs.edit().putBoolean("enabled", v).apply(); }
    public boolean showFps() { return prefs.getBoolean("fps", false); }
    public void setShowFps(boolean v) { prefs.edit().putBoolean("fps", v).apply(); }
    public int cogSizeDp() { return prefs.getInt("cogSize", 48); }
    public void setCogSizeDp(int v) { prefs.edit().putInt("cogSize", v).apply(); }
    public float cogX() { return prefs.getFloat("cogX", 0.97f); }
    public float cogY() { return prefs.getFloat("cogY", 0.12f); }
    public void setCogPosition(float x, float y) {
        prefs.edit().putFloat("cogX", x).putFloat("cogY", y).apply();
    }

    public List<ControlButton> load() {
        List<ControlButton> out = new ArrayList<>();
        String json = prefs.getString(LAYOUT, null);
        if (json != null) {
            try {
                JSONArray a = new JSONArray(json);
                for (int i = 0; i < a.length(); i++) out.add(ControlButton.fromJson(a.getJSONObject(i)));
                if (!out.isEmpty()) return out;
            } catch (Exception ignored) {
                out.clear();
            }
        }
        return defaults();
    }

    public void save(List<ControlButton> buttons) {
        try {
            JSONArray a = new JSONArray();
            for (ControlButton b : buttons) a.put(b.toJson());
            prefs.edit().putString(LAYOUT, a.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public void reset() {
        prefs.edit().remove(LAYOUT).apply();
    }

    public static String newId() {
        return "b" + Long.toString(System.nanoTime(), 36);
    }

    private static ControlButton key(String id, String label, int code, float x, float y) {
        ControlButton b = new ControlButton();
        b.id = id;
        b.label = label;
        b.keys[0] = code;
        b.x = x;
        b.y = y;
        return b;
    }

    private static ControlButton mouse(String id, String label, int button, float x, float y, int size) {
        ControlButton b = new ControlButton();
        b.id = id;
        b.label = label;
        b.action = ControlButton.MOUSE;
        b.mouseButton = button;
        b.x = x;
        b.y = y;
        b.sizePercent = size;
        b.mousePassthrough = true;
        return b;
    }

    /** A Minecraft layout: stick on the left, actions on the right. */
    public static List<ControlButton> defaults() {
        List<ControlButton> l = new ArrayList<>();
        ControlButton stick = new ControlButton();
        stick.id = "stick";
        stick.label = "";
        stick.action = ControlButton.JOYSTICK;
        stick.keys = new int[] {'W', 'A', 'S', 'D'};
        stick.x = 0.13f;
        stick.y = 0.74f;
        stick.sizePercent = 100;
        l.add(stick);
        ControlButton jump = key("jump", "Jump", 32, 0.90f, 0.62f);
        jump.sizePercent = 110;
        l.add(jump);
        ControlButton sneak = key("sneak", "Sneak", 340, 0.79f, 0.84f);
        sneak.toggle = true;
        l.add(sneak);
        l.add(mouse("attack", "Attack", 0, 0.80f, 0.60f, 110));
        l.add(mouse("place", "Place", 1, 0.90f, 0.82f, 110));
        l.add(key("inventory", "Inv", 'E', 0.60f, 0.90f));
        l.add(key("drop", "Drop", 'Q', 0.52f, 0.90f));
        l.add(key("chat", "Chat", 'T', 0.04f, 0.28f));
        l.add(key("view", "F5", 295, 0.04f, 0.42f));
        ControlButton prev = new ControlButton();
        prev.id = "prev";
        prev.label = "\u25C0";
        prev.action = ControlButton.SCROLL;
        prev.scrollDirection = 1;
        prev.repeatOnHold = true;
        prev.x = 0.36f;
        prev.y = 0.92f;
        prev.sizePercent = 80;
        l.add(prev);
        ControlButton next = new ControlButton();
        next.id = "next";
        next.label = "\u25B6";
        next.action = ControlButton.SCROLL;
        next.scrollDirection = -1;
        next.repeatOnHold = true;
        next.x = 0.44f;
        next.y = 0.92f;
        next.sizePercent = 80;
        l.add(next);
        ControlButton pause = key("pause", "Esc", 256, 0.04f, 0.12f);
        pause.visibility = ControlButton.VIS_ALWAYS;
        l.add(pause);
        return l;
    }
}
