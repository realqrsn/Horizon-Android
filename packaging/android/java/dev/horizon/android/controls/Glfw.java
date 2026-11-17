package dev.horizon.android.controls;

import java.util.ArrayList;
import java.util.List;

/** GLFW key codes for the key picker, with the short labels shown on buttons. */
public final class Glfw {
    private Glfw() {}

    public static final List<String> NAMES = new ArrayList<>();
    public static final List<Integer> CODES = new ArrayList<>();
    private static final List<String> SHORT = new ArrayList<>();

    private static void add(int code, String name, String shortName) {
        CODES.add(code);
        NAMES.add(name);
        SHORT.add(shortName);
    }

    static {
        add(0, "None", "");
        for (int c = 'A'; c <= 'Z'; c++) add(c, String.valueOf((char) c), String.valueOf((char) c));
        for (int c = '0'; c <= '9'; c++) add(c, String.valueOf((char) c), String.valueOf((char) c));
        add(32, "Space", "Jump");
        add(340, "Left Shift", "Shift");
        add(341, "Left Ctrl", "Ctrl");
        add(342, "Left Alt", "Alt");
        add(258, "Tab", "Tab");
        add(257, "Enter", "Enter");
        add(256, "Escape", "Esc");
        add(259, "Backspace", "Back");
        add(261, "Delete", "Del");
        add(262, "Arrow Right", "\u2192");
        add(263, "Arrow Left", "\u2190");
        add(264, "Arrow Down", "\u2193");
        add(265, "Arrow Up", "\u2191");
        add(266, "Page Up", "PgUp");
        add(267, "Page Down", "PgDn");
        add(268, "Home", "Home");
        add(269, "End", "End");
        for (int i = 1; i <= 12; i++) add(289 + i, "F" + i, "F" + i);
        add(47, "Slash", "/");
        add(46, "Period", ".");
        add(44, "Comma", ",");
        add(45, "Minus", "-");
        add(61, "Equals", "=");
        add(96, "Grave", "`");
    }

    public static int indexOf(int code) {
        int i = CODES.indexOf(code);
        return i < 0 ? 0 : i;
    }

    public static String shortLabel(int code) {
        return SHORT.get(indexOf(code));
    }

    public static String[] names() {
        return NAMES.toArray(new String[0]);
    }
}
