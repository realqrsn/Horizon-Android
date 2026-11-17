package dev.horizon.android.controls;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * "Edit touch button": Undo / Redo / Copy on top, the settings in the middle, Delete / Cancel /
 * OK at the bottom. Changes show on the canvas live; Cancel puts the button back as it was.
 */
public final class ButtonSettingsDialog {
    public interface Callback {
        /** The button changed while the dialog is open; redraw. */
        void onPreview();
        void onSave();
        void onCancel();
        void onDelete(ControlButton button);
        void onCopy(ControlButton button);
    }

    private static final int[] SWATCHES = {
        0xFF25D380, 0xFF16884E, 0xFF246BCE, 0xFFBA1A1A, 0xFFFFD54F, 0xFFD95700, 0xFFFFFFFF, 0xFF9E9E9E
    };

    private ButtonSettingsDialog() {}

    public static void show(Activity a, ControlButton target, List<ControlButton> all, Callback cb) {
        final ControlButton original = target.copy();
        final ControlButton[] w = {target.copy()};
        final ArrayDeque<String> undo = new ArrayDeque<>();
        final ArrayDeque<String> redo = new ArrayDeque<>();
        final String[] last = {json(w[0])};
        final long[] lastAt = {0};
        final ScrollView scroll = new ScrollView(a);
        final Runnable[] rebuild = new Runnable[1];

        final Runnable changed = () -> {
            long now = System.currentTimeMillis();
            if (now - lastAt[0] > 600) {
                undo.push(last[0]);
                redo.clear();
            }
            lastAt[0] = now;
            last[0] = json(w[0]);
            target.set(w[0]);
            cb.onPreview();
        };
        rebuild[0] = () -> {
            scroll.removeAllViews();
            scroll.addView(body(a, w[0], target, all, changed, rebuild[0]),
                    new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        };
        rebuild[0].run();

        LinearLayout root = Ui.column(a);
        root.setBackground(Ui.round(Ui.BG, 22, a));
        root.setPadding(Ui.dp(a, 12), Ui.dp(a, 14), Ui.dp(a, 12), Ui.dp(a, 12));
        TextView title = Ui.title(a, "Edit touch button");
        title.setPadding(Ui.dp(a, 8), 0, 0, Ui.dp(a, 8));
        root.addView(title);

        Dialog d = Ui.dialog(a, root, 420, true);
        LinearLayout.LayoutParams third = new LinearLayout.LayoutParams(0, Ui.dp(a, 44), 1f);
        third.setMargins(Ui.dp(a, 3), 0, Ui.dp(a, 3), 0);

        LinearLayout top = new LinearLayout(a);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.addView(Ui.button(a, "UNDO", Ui.TEXT, v -> {
            if (undo.isEmpty()) return;
            redo.push(json(w[0]));
            w[0] = parse(undo.pop(), w[0]);
            last[0] = json(w[0]);
            lastAt[0] = 0;
            target.set(w[0]);
            cb.onPreview();
            rebuild[0].run();
        }), third);
        top.addView(Ui.button(a, "REDO", Ui.TEXT, v -> {
            if (redo.isEmpty()) return;
            undo.push(json(w[0]));
            w[0] = parse(redo.pop(), w[0]);
            last[0] = json(w[0]);
            lastAt[0] = 0;
            target.set(w[0]);
            cb.onPreview();
            rebuild[0].run();
        }), third);
        top.addView(Ui.button(a, "COPY", Ui.TEXT, v -> {
            target.set(w[0]);
            d.dismiss();
            cb.onCopy(target);
        }), third);
        root.addView(top);

        LinearLayout.LayoutParams grow = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        grow.setMargins(0, Ui.dp(a, 8), 0, Ui.dp(a, 8));
        root.addView(scroll, grow);

        LinearLayout bottom = new LinearLayout(a);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.addView(Ui.button(a, "DELETE", Ui.PINK, v -> {
            d.dismiss();
            cb.onDelete(target);
        }), third);
        bottom.addView(Ui.button(a, "CANCEL", Ui.ACCENT, v -> {
            target.set(original);
            d.dismiss();
            cb.onCancel();
        }), third);
        bottom.addView(Ui.button(a, "OK", Ui.ACCENT, v -> {
            target.set(w[0]);
            d.dismiss();
            cb.onSave();
        }), third);
        root.addView(bottom);

        d.setCanceledOnTouchOutside(false);
        d.setOnCancelListener(x -> {
            target.set(original);
            cb.onCancel();
        });
        Window win = d.getWindow();
        if (win != null) {
            win.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            win.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        d.show();
    }

    private static String json(ControlButton b) {
        try {
            return b.toJson().toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    private static ControlButton parse(String json, ControlButton fallback) {
        try {
            return ControlButton.fromJson(new org.json.JSONObject(json));
        } catch (Exception e) {
            return fallback;
        }
    }

    private static View body(Activity a, ControlButton w, ControlButton target, List<ControlButton> all,
                             Runnable changed, Runnable rebuild) {
        LinearLayout body = Ui.column(a);
        body.setPadding(Ui.dp(a, 6), 0, Ui.dp(a, 6), Ui.dp(a, 8));

        body.addView(Ui.label(a, "Label (empty = automatic)"));
        EditText label = new EditText(a);
        label.setText(w.label);
        label.setTextColor(Ui.TEXT);
        label.setSingleLine(true);
        label.setInputType(InputType.TYPE_CLASS_TEXT);
        label.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int c, int n) {}
            @Override public void onTextChanged(CharSequence s, int i, int b, int c) {
                if (!s.toString().equals(w.label)) {
                    w.label = s.toString();
                    changed.run();
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        body.addView(label);

        body.addView(Ui.label(a, "Action"));
        int actionIndex = java.util.Arrays.asList(ControlButton.ACTIONS).indexOf(w.action);
        body.addView(Ui.spinner(a, ControlButton.ACTION_NAMES, Math.max(0, actionIndex), pos -> {
            w.action = ControlButton.ACTIONS[pos];
            if (w.is(ControlButton.JOYSTICK) && w.keys[0] == 0) w.keys = new int[] {'W', 'A', 'S', 'D'};
            if (w.is(ControlButton.JOYSTICK)) w.heightDp = w.widthDp = Math.max(w.widthDp, 120);
            changed.run();
            rebuild.run();
        }));

        // What it does
        if (w.is(ControlButton.KEY)) {
            for (int i = 0; i < 4; i++) addKeySpinner(a, body, "Key " + (i + 1), w, i, changed);
        } else if (w.is(ControlButton.MOUSE)) {
            body.addView(Ui.label(a, "Mouse button"));
            body.addView(Ui.spinner(a, new String[] {"Left", "Right", "Middle"}, w.mouseButton, p -> {
                w.mouseButton = p;
                changed.run();
            }));
        } else if (w.is(ControlButton.SCROLL)) {
            body.addView(Ui.label(a, "Direction"));
            body.addView(Ui.spinner(a, new String[] {"Up", "Down"}, w.scrollDirection > 0 ? 0 : 1, p -> {
                w.scrollDirection = p == 0 ? 1 : -1;
                changed.run();
            }));
        } else if (w.is(ControlButton.JOYSTICK)) {
            String[] dirs = {"Up", "Left", "Down", "Right"};
            for (int i = 0; i < 4; i++) addKeySpinner(a, body, dirs[i] + " key", w, i, changed);
            body.addView(Ui.label(a, "Sprint key (forward lock)"));
            body.addView(Ui.spinner(a, Glfw.names(), Glfw.indexOf(w.sprintKey), p -> {
                w.sprintKey = Glfw.CODES.get(p);
                changed.run();
            }));
        } else if (w.is(ControlButton.DRAWER)) {
            body.addView(Ui.label(a, "Opens towards"));
            body.addView(Ui.spinner(a, new String[] {"Sideways", "Up / down"}, w.drawerHorizontal ? 0 : 1, p -> {
                w.drawerHorizontal = p == 0;
                changed.run();
            }));
            body.addView(Ui.check(a, "Open by default", w.drawerOpenByDefault, v -> {
                w.drawerOpenByDefault = v;
                changed.run();
            }));
        }

        // Size
        if (w.is(ControlButton.JOYSTICK)) {
            body.addView(Ui.slider(a, "Size", " dp", 60, 300, Math.round(w.widthDp), v -> {
                w.widthDp = w.heightDp = v;
                changed.run();
            }));
        } else {
            body.addView(Ui.slider(a, "Width", " dp", 14, 300, Math.round(w.widthDp), v -> {
                w.widthDp = v;
                changed.run();
            }));
            body.addView(Ui.slider(a, "Height", " dp", 14, 300, Math.round(w.heightDp), v -> {
                w.heightDp = v;
                changed.run();
            }));
        }

        // Droid's checkbox list
        if (w.is(ControlButton.KEY) || w.is(ControlButton.MOUSE)) {
            body.addView(Ui.check(a, "Toggle button", w.toggle, v -> {
                w.toggle = v;
                changed.run();
            }));
        }
        if (!w.is(ControlButton.DRAWER)) {
            Button color = new Button(a);
            color.setAllCaps(false);
            styleColorButton(color, w.toggleColor);
            color.setOnClickListener(v -> pickColor(a, w.toggleColor, c -> {
                w.toggleColor = c;
                styleColorButton(color, c);
                changed.run();
            }));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(a, 48));
            clp.setMargins(0, Ui.dp(a, 4), 0, Ui.dp(a, 4));
            body.addView(color, clp);
        }
        if (w.is(ControlButton.KEY) || w.is(ControlButton.SCROLL)) {
            body.addView(Ui.check(a, "Repeat while held (long press)", w.repeatOnHold, v -> {
                w.repeatOnHold = v;
                changed.run();
            }));
        }
        body.addView(Ui.check(a, "Visible in game", w.visibleInGame, v -> {
            w.visibleInGame = v;
            changed.run();
        }));
        body.addView(Ui.check(a, "Visible in menu", w.visibleInMenu, v -> {
            w.visibleInMenu = v;
            changed.run();
        }));
        body.addView(Ui.check(a, "Stay visible when touch controls are hidden", w.stayVisible, v -> {
            w.stayVisible = v;
            changed.run();
        }));

        if (w.is(ControlButton.JOYSTICK)) {
            body.addView(Ui.slider(a, "Deadzone", "%", 0, 60, w.deadzonePercent, v -> {
                w.deadzonePercent = v;
                changed.run();
            }));
            body.addView(Ui.check(a, "Floating (appears where you touch)", w.joystickFloating, v -> {
                w.joystickFloating = v;
                changed.run();
            }));
            body.addView(Ui.check(a, "Forward lock (push past the ring to sprint)", w.forwardLock, v -> {
                w.forwardLock = v;
                changed.run();
            }));
        } else if (!w.is(ControlButton.DRAWER)) {
            body.addView(Ui.check(a, "Mouse pass through", w.mousePassthrough, v -> {
                w.mousePassthrough = v;
                changed.run();
            }));
            TextView note = Ui.label(a, "When enabled, dragging on this button also moves the in-game camera while the button stays pressed. Useful for Jump, Sneak, Sprint, or Use.");
            note.setTextSize(17);
            body.addView(note);
            body.addView(Ui.check(a, "Swipeable gesture", w.swipeable, v -> {
                w.swipeable = v;
                changed.run();
            }));
        }

        // Parent drawer
        if (!w.is(ControlButton.DRAWER) && !w.is(ControlButton.JOYSTICK)) {
            List<ControlButton> drawers = new ArrayList<>();
            for (ControlButton b : all) if (b != target && b.is(ControlButton.DRAWER) && b.parentId.isEmpty()) drawers.add(b);
            if (!drawers.isEmpty()) {
                String[] names = new String[drawers.size() + 1];
                names[0] = "None";
                int sel = 0;
                for (int i = 0; i < drawers.size(); i++) {
                    ControlButton d = drawers.get(i);
                    names[i + 1] = d.label.isEmpty() ? "Drawer " + (i + 1) : d.label;
                    if (d.id.equals(w.parentId)) sel = i + 1;
                }
                body.addView(Ui.label(a, "Inside drawer"));
                body.addView(Ui.spinner(a, names, sel, p -> {
                    w.parentId = p == 0 ? "" : drawers.get(p - 1).id;
                    changed.run();
                }));
            }
        }
        return body;
    }

    private static void addKeySpinner(Activity a, LinearLayout into, String name, ControlButton w, int index, Runnable changed) {
        into.addView(Ui.label(a, name));
        into.addView(Ui.spinner(a, Glfw.names(), Glfw.indexOf(w.keys[index]), p -> {
            w.keys[index] = Glfw.CODES.get(p);
            changed.run();
        }));
    }

    private static void styleColorButton(Button b, int color) {
        b.setText(String.format("Toggle ON colour: #%08X", color));
        b.setBackground(Ui.round(color, 12, b.getContext()));
        double lum = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0;
        b.setTextColor(lum > 0.5 ? Color.BLACK : Color.WHITE);
    }

    /** A small colour chooser: preset swatches and a #AARRGGBB field. */
    static void pickColor(Activity a, int current, java.util.function.IntConsumer cb) {
        final int[] chosen = {current};
        LinearLayout root = Ui.column(a);
        root.setBackground(Ui.round(Ui.BG, 20, a));
        root.setPadding(Ui.dp(a, 18), Ui.dp(a, 16), Ui.dp(a, 18), Ui.dp(a, 14));
        root.addView(Ui.title(a, "Colour"));
        EditText hex = new EditText(a);
        hex.setText(String.format("#%08X", current));
        hex.setTextColor(Ui.TEXT);
        hex.setSingleLine(true);
        HorizontalScrollView hs = new HorizontalScrollView(a);
        LinearLayout row = new LinearLayout(a);
        for (int c : SWATCHES) {
            View v = new View(a);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(a, 38), Ui.dp(a, 38));
            lp.setMargins(0, Ui.dp(a, 2), Ui.dp(a, 8), Ui.dp(a, 2));
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            g.setColor(c);
            g.setStroke(Ui.dp(a, 1), 0x99FFFFFF);
            v.setBackground(g);
            v.setOnClickListener(x -> {
                chosen[0] = c;
                hex.setText(String.format("#%08X", c));
            });
            row.addView(v, lp);
        }
        hs.addView(row);
        root.addView(hs);
        root.addView(Ui.label(a, "Hex (#AARRGGBB)"));
        root.addView(hex);
        Dialog d = Ui.dialog(a, root, 380);
        LinearLayout buttons = new LinearLayout(a);
        buttons.setPadding(0, Ui.dp(a, 12), 0, 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(a, 44), 1f);
        lp.setMargins(Ui.dp(a, 3), 0, Ui.dp(a, 3), 0);
        buttons.addView(Ui.button(a, "CANCEL", Ui.ACCENT, v -> d.dismiss()), lp);
        buttons.addView(Ui.button(a, "OK", Ui.ACCENT, v -> {
            try {
                String s = hex.getText().toString().trim();
                if (!s.startsWith("#")) s = "#" + s;
                chosen[0] = Color.parseColor(s);
            } catch (IllegalArgumentException ignored) {
            }
            d.dismiss();
            cb.accept(chosen[0]);
        }), lp);
        root.addView(buttons);
        d.show();
    }
}
