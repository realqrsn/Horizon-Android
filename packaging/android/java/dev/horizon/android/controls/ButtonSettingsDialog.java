package dev.horizon.android.controls;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.text.Editable;
import android.text.TextWatcher;
import java.util.ArrayList;
import java.util.List;

/** The settings panel that opens when a button is tapped in edit mode. */
public final class ButtonSettingsDialog {
    public interface Callback {
        void onSave();
        void onDelete(ControlButton button);
        void onDuplicate(ControlButton button);
    }

    private static final int[] SWATCHES = {
        0xFFFFFFFF, 0xFF000000, 0xFFD95700, 0xFF16884E, 0xFF246BCE, 0xFFBA1A1A, 0xFFFFD54F, 0xFF9E9E9E
    };

    private ButtonSettingsDialog() {}

    public static void show(Activity a, ControlButton target, List<ControlButton> all, Callback cb) {
        final ControlButton w = target.copy();
        final LinearLayout keySec = Ui.column(a);
        final LinearLayout mouseSec = Ui.column(a);
        final LinearLayout scrollSec = Ui.column(a);
        final LinearLayout joySec = Ui.column(a);
        final LinearLayout drawerSec = Ui.column(a);
        final LinearLayout parentSec = Ui.column(a);
        final Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            keySec.setVisibility(w.is(ControlButton.KEY) ? View.VISIBLE : View.GONE);
            mouseSec.setVisibility(w.is(ControlButton.MOUSE) ? View.VISIBLE : View.GONE);
            scrollSec.setVisibility(w.is(ControlButton.SCROLL) ? View.VISIBLE : View.GONE);
            joySec.setVisibility(w.is(ControlButton.JOYSTICK) ? View.VISIBLE : View.GONE);
            drawerSec.setVisibility(w.is(ControlButton.DRAWER) ? View.VISIBLE : View.GONE);
            parentSec.setVisibility(w.is(ControlButton.DRAWER) || w.is(ControlButton.JOYSTICK) ? View.GONE : View.VISIBLE);
        };

        LinearLayout body = Ui.column(a);
        body.setPadding(Ui.dp(a, 4), 0, Ui.dp(a, 4), Ui.dp(a, 8));

        // What it does
        body.addView(Ui.section(a, "Button"));
        body.addView(Ui.label(a, "Label (empty = automatic)"));
        EditText label = new EditText(a);
        label.setText(w.label);
        label.setTextColor(Ui.TEXT);
        label.setSingleLine(true);
        label.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int c, int n) {}
            @Override public void onTextChanged(CharSequence s, int i, int b, int c) { w.label = s.toString(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        body.addView(label);
        body.addView(Ui.label(a, "Action"));
        int actionIndex = java.util.Arrays.asList(ControlButton.ACTIONS).indexOf(w.action);
        body.addView(Ui.spinner(a, ControlButton.ACTION_NAMES, Math.max(0, actionIndex), pos -> {
            w.action = ControlButton.ACTIONS[pos];
            refresh[0].run();
        }));

        // KEY
        for (int i = 0; i < 4; i++) addKeySpinner(a, keySec, "Key " + (i + 1), w, i);
        keySec.addView(Ui.toggle(a, "Toggle (tap on, tap off)", w.toggle, v -> w.toggle = v));
        keySec.addView(Ui.toggle(a, "Repeat while held", w.repeatOnHold, v -> w.repeatOnHold = v));
        body.addView(keySec);

        // MOUSE
        mouseSec.addView(Ui.label(a, "Mouse button"));
        mouseSec.addView(Ui.spinner(a, new String[] {"Left", "Right", "Middle"}, w.mouseButton, p -> w.mouseButton = p));
        mouseSec.addView(Ui.toggle(a, "Toggle (tap on, tap off)", w.toggle, v -> w.toggle = v));
        body.addView(mouseSec);

        // SCROLL
        scrollSec.addView(Ui.label(a, "Direction"));
        scrollSec.addView(Ui.spinner(a, new String[] {"Up", "Down"}, w.scrollDirection > 0 ? 0 : 1, p -> w.scrollDirection = p == 0 ? 1 : -1));
        scrollSec.addView(Ui.toggle(a, "Repeat while held", w.repeatOnHold, v -> w.repeatOnHold = v));
        body.addView(scrollSec);

        // JOYSTICK
        String[] dirs = {"Up", "Left", "Down", "Right"};
        for (int i = 0; i < 4; i++) addKeySpinner(a, joySec, dirs[i] + " key", w, i);
        joySec.addView(Ui.label(a, "Sprint key (forward lock)"));
        joySec.addView(Ui.spinner(a, Glfw.names(), Glfw.indexOf(w.sprintKey), p -> w.sprintKey = Glfw.CODES.get(p)));
        joySec.addView(Ui.slider(a, "Dead zone", "%", 0, 60, w.deadzonePercent, v -> w.deadzonePercent = v));
        joySec.addView(Ui.toggle(a, "Floating (appears where you touch)", w.joystickFloating, v -> w.joystickFloating = v));
        joySec.addView(Ui.toggle(a, "Forward lock (push past the ring to sprint)", w.forwardLock, v -> w.forwardLock = v));
        body.addView(joySec);

        // DRAWER
        drawerSec.addView(Ui.label(a, "Opens towards"));
        drawerSec.addView(Ui.spinner(a, new String[] {"Sideways", "Up / down"}, w.drawerHorizontal ? 0 : 1, p -> w.drawerHorizontal = p == 0));
        drawerSec.addView(Ui.toggle(a, "Open by default", w.drawerOpenByDefault, v -> w.drawerOpenByDefault = v));
        body.addView(drawerSec);

        // Parent drawer
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
            parentSec.addView(Ui.label(a, "Inside drawer"));
            parentSec.addView(Ui.spinner(a, names, sel, p -> w.parentId = p == 0 ? "" : drawers.get(p - 1).id));
        }
        body.addView(parentSec);

        // Look
        body.addView(Ui.section(a, "Look"));
        body.addView(Ui.slider(a, "Size", "%", 40, 250, w.sizePercent, v -> w.sizePercent = v));
        body.addView(Ui.slider(a, "Opacity", "%", 10, 100, w.opacityPercent, v -> w.opacityPercent = v));
        body.addView(Ui.slider(a, "Corner radius", "%", 0, 50, w.cornerPercent, v -> w.cornerPercent = v));
        body.addView(Ui.slider(a, "Stroke width", " dp", 0, 8, w.strokeDp, v -> w.strokeDp = v));
        addColorRow(a, body, "Background colour", w.backgroundColor, c -> w.backgroundColor = c);
        addColorRow(a, body, "Stroke colour", w.strokeColor, c -> w.strokeColor = c);
        addColorRow(a, body, "Active / pressed colour", w.activeColor, c -> w.activeColor = c);

        // Behaviour
        body.addView(Ui.section(a, "Behaviour"));
        body.addView(Ui.label(a, "Visible"));
        body.addView(Ui.spinner(a, ControlButton.VIS_NAMES, w.visibility, p -> w.visibility = p));
        body.addView(Ui.toggle(a, "Finger can also look around while held", w.mousePassthrough, v -> w.mousePassthrough = v));

        refresh[0].run();

        ScrollView scroll = new ScrollView(a);
        scroll.addView(body, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout root = Ui.column(a);
        root.setBackground(Ui.round(Ui.BG, 20, a));
        root.setPadding(Ui.dp(a, 18), Ui.dp(a, 16), Ui.dp(a, 18), Ui.dp(a, 14));
        root.addView(Ui.title(a, "Button settings"));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Dialog d = Ui.dialog(a, root, 520, true);
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, Ui.dp(a, 10), 0, 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(a, 44), 1f);
        lp.setMargins(Ui.dp(a, 3), 0, Ui.dp(a, 3), 0);
        row.addView(Ui.button(a, "Delete", false, v -> { d.dismiss(); cb.onDelete(target); }), lp);
        row.addView(Ui.button(a, "Duplicate", false, v -> { d.dismiss(); cb.onDuplicate(target); }), lp);
        row.addView(Ui.button(a, "Cancel", false, v -> d.dismiss()), lp);
        row.addView(Ui.button(a, "Save", true, v -> { target.set(w); d.dismiss(); cb.onSave(); }), lp);
        root.addView(row);
        d.show();
    }

    private static void addKeySpinner(Activity a, LinearLayout into, String name, ControlButton w, int index) {
        into.addView(Ui.label(a, name));
        Spinner s = Ui.spinner(a, Glfw.names(), Glfw.indexOf(w.keys[index]), p -> w.keys[index] = Glfw.CODES.get(p));
        into.addView(s);
    }

    private static void addColorRow(Activity a, LinearLayout into, String name, int current, java.util.function.IntConsumer cb) {
        into.addView(Ui.label(a, name));
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        final View[] swatchViews = new View[SWATCHES.length];
        for (int i = 0; i < SWATCHES.length; i++) {
            final int color = SWATCHES[i];
            View v = new View(a);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(a, 34), Ui.dp(a, 34));
            lp.setMargins(0, Ui.dp(a, 2), Ui.dp(a, 8), Ui.dp(a, 2));
            v.setBackground(ring(a, color, color == current));
            final int index = i;
            v.setOnClickListener(x -> {
                cb.accept(color);
                for (int j = 0; j < SWATCHES.length; j++) swatchViews[j].setBackground(ring(a, SWATCHES[j], j == index));
            });
            swatchViews[i] = v;
            row.addView(v, lp);
        }
        hs.addView(row);
        into.addView(hs);
    }

    private static android.graphics.drawable.GradientDrawable ring(Activity a, int color, boolean selected) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        g.setColor(color);
        g.setStroke(Ui.dp(a, selected ? 3 : 1), selected ? Ui.ACCENT : Color.argb(120, 255, 255, 255));
        return g;
    }
}
