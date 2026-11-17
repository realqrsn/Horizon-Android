package dev.horizon.android.controls;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** One on-screen control: what it does and how big it is. Saved as JSON. */
public final class ControlButton {
    public static final String KEY = "key";
    public static final String MOUSE = "mouse";
    public static final String SCROLL = "scroll";
    public static final String MENU = "menu";
    public static final String KEYBOARD = "keyboard";
    public static final String TOGGLE_CONTROLS = "toggle_controls";
    public static final String JOYSTICK = "joystick";
    public static final String DRAWER = "drawer";

    public static final String[] ACTIONS = {KEY, MOUSE, SCROLL, MENU, KEYBOARD, TOGGLE_CONTROLS, JOYSTICK, DRAWER};
    public static final String[] ACTION_NAMES = {
        "Key", "Mouse button", "Scroll wheel", "Open menu", "Keyboard", "Toggle controls", "Joystick", "Drawer"
    };

    public String id = "";
    public String label = "";
    public String action = KEY;
    /** Key slots (GLFW codes, 0 = empty). Joystick: up, left, down, right. */
    public int[] keys = new int[4];
    /** Joystick: key held while the stick is pushed past the lock ring. */
    public int sprintKey = 341;
    public int mouseButton = 0;
    public int scrollDirection = 1;
    /** Centre of the button as a fraction of the screen. */
    public float x = 0.5f;
    public float y = 0.5f;
    public float widthDp = 59;
    public float heightDp = 59;
    public boolean toggle = false;
    public int toggleColor = 0xFF25D380;
    public boolean repeatOnHold = false;
    public boolean visibleInGame = true;
    public boolean visibleInMenu = true;
    public boolean stayVisible = false;
    public boolean mousePassthrough = false;
    public boolean swipeable = false;
    public int deadzonePercent = 15;
    public boolean joystickFloating = false;
    public boolean forwardLock = true;
    public String parentId = "";
    public boolean drawerHorizontal = true;
    public boolean drawerOpenByDefault = false;

    public boolean is(String a) {
        return action.equals(a);
    }

    public ControlButton copy() {
        try {
            return fromJson(toJson());
        } catch (JSONException e) {
            return this;
        }
    }

    /** Copies every setting of {@code o} into this button, keeping this object's identity. */
    public void set(ControlButton o) {
        try {
            ControlButton c = fromJson(o.toJson());
            id = c.id; label = c.label; action = c.action; keys = c.keys; sprintKey = c.sprintKey;
            mouseButton = c.mouseButton; scrollDirection = c.scrollDirection; x = c.x; y = c.y;
            widthDp = c.widthDp; heightDp = c.heightDp; toggle = c.toggle; toggleColor = c.toggleColor;
            repeatOnHold = c.repeatOnHold; visibleInGame = c.visibleInGame; visibleInMenu = c.visibleInMenu;
            stayVisible = c.stayVisible; mousePassthrough = c.mousePassthrough; swipeable = c.swipeable;
            deadzonePercent = c.deadzonePercent; joystickFloating = c.joystickFloating;
            forwardLock = c.forwardLock; parentId = c.parentId; drawerHorizontal = c.drawerHorizontal;
            drawerOpenByDefault = c.drawerOpenByDefault;
        } catch (JSONException ignored) {
        }
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("label", label);
        o.put("action", action);
        JSONArray k = new JSONArray();
        for (int v : keys) k.put(v);
        o.put("keys", k);
        o.put("sprintKey", sprintKey);
        o.put("mouseButton", mouseButton);
        o.put("scrollDirection", scrollDirection);
        o.put("x", x);
        o.put("y", y);
        o.put("widthDp", widthDp);
        o.put("heightDp", heightDp);
        o.put("toggle", toggle);
        o.put("toggleColor", toggleColor);
        o.put("repeatOnHold", repeatOnHold);
        o.put("visibleInGame", visibleInGame);
        o.put("visibleInMenu", visibleInMenu);
        o.put("stayVisible", stayVisible);
        o.put("mousePassthrough", mousePassthrough);
        o.put("swipeable", swipeable);
        o.put("deadzonePercent", deadzonePercent);
        o.put("joystickFloating", joystickFloating);
        o.put("forwardLock", forwardLock);
        o.put("parentId", parentId);
        o.put("drawerHorizontal", drawerHorizontal);
        o.put("drawerOpenByDefault", drawerOpenByDefault);
        return o;
    }

    public static ControlButton fromJson(JSONObject o) throws JSONException {
        ControlButton b = new ControlButton();
        b.id = o.optString("id", "");
        b.label = o.optString("label", "");
        b.action = o.optString("action", KEY);
        JSONArray k = o.optJSONArray("keys");
        for (int i = 0; k != null && i < 4 && i < k.length(); i++) b.keys[i] = k.optInt(i, 0);
        b.sprintKey = o.optInt("sprintKey", 341);
        b.mouseButton = o.optInt("mouseButton", 0);
        b.scrollDirection = o.optInt("scrollDirection", 1);
        b.x = (float) o.optDouble("x", 0.5);
        b.y = (float) o.optDouble("y", 0.5);
        b.widthDp = (float) o.optDouble("widthDp", 59);
        b.heightDp = (float) o.optDouble("heightDp", 59);
        b.toggle = o.optBoolean("toggle", false);
        b.toggleColor = o.optInt("toggleColor", 0xFF25D380);
        b.repeatOnHold = o.optBoolean("repeatOnHold", false);
        b.visibleInGame = o.optBoolean("visibleInGame", true);
        b.visibleInMenu = o.optBoolean("visibleInMenu", true);
        b.stayVisible = o.optBoolean("stayVisible", false);
        b.mousePassthrough = o.optBoolean("mousePassthrough", false);
        b.swipeable = o.optBoolean("swipeable", false);
        b.deadzonePercent = o.optInt("deadzonePercent", 15);
        b.joystickFloating = o.optBoolean("joystickFloating", false);
        b.forwardLock = o.optBoolean("forwardLock", true);
        b.parentId = o.optString("parentId", "");
        b.drawerHorizontal = o.optBoolean("drawerHorizontal", true);
        b.drawerOpenByDefault = o.optBoolean("drawerOpenByDefault", false);
        return b;
    }
}
