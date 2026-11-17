package dev.horizon.android.controls;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * The "Touch editor" panel on the right: settings for every button at once, plus Add Key,
 * Add Mouse, Add Stick, Add Drawer, Snap, Save, Undo, Redo and Close Editor.
 */
final class EditorPanel extends LinearLayout {
    interface Host {
        void onGlobalsChanged();
        void addKey();
        void addMouse();
        void addStick();
        void addDrawer();
        void save();
        void undo();
        void redo();
        void closeEditor();
        void hidePanel();
    }

    EditorPanel(Context c, ControlsStore.Globals g, Host host) {
        super(c);
        setOrientation(VERTICAL);
        setBackground(Ui.round(Ui.PANEL, 28, c));
        setPadding(Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14));
        // Taps on the panel never reach the buttons underneath.
        setClickable(true);

        LinearLayout header = new LinearLayout(c);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(c);
        title.setText("Touch editor");
        title.setTextColor(Ui.TEXT);
        title.setTextSize(24);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(Ui.button(c, "Hide panel", Ui.TEXT, v -> host.hidePanel()),
                new LinearLayout.LayoutParams(Ui.dp(c, 130), Ui.dp(c, 46)));
        addView(header);

        LinearLayout body = Ui.column(c);
        body.setPadding(Ui.dp(c, 6), 0, Ui.dp(c, 6), Ui.dp(c, 6));
        body.addView(Ui.slider(c, "Opacity", "All button opacity", "%", 0, 100, g.opacity, true, v -> {
            g.opacity = v;
            host.onGlobalsChanged();
        }));
        body.addView(Ui.slider(c, "Scale", "All button scale", "%", 30, 200, g.scale, true, v -> {
            g.scale = v;
            host.onGlobalsChanged();
        }));
        body.addView(Ui.slider(c, "Padding", "Screen edge padding", " dp", 0, 40, g.paddingDp, true, v -> {
            g.paddingDp = v;
            host.onGlobalsChanged();
        }));
        body.addView(Ui.slider(c, "Radius", "All button radius", " dp", 0, 30, g.radiusDp, true, v -> {
            g.radiusDp = v;
            host.onGlobalsChanged();
        }));
        body.addView(Ui.slider(c, "Stroke", "All button stroke", " dp", 0, 6, g.strokeDp, true, v -> {
            g.strokeDp = v;
            host.onGlobalsChanged();
        }));

        TextView textTitle = new TextView(c);
        textTitle.setText("All button text");
        textTitle.setTextColor(Ui.TEXT);
        textTitle.setTextSize(19);
        textTitle.setGravity(Gravity.CENTER_HORIZONTAL);
        textTitle.setPadding(0, Ui.dp(c, 16), 0, Ui.dp(c, 4));
        body.addView(textTitle);
        RadioGroup group = new RadioGroup(c);
        String[] names = {"Keep text as typed", "Uppercase all text", "Lowercase all text"};
        int[] map = {0, 1, 2};
        for (int i = 0; i < names.length; i++) {
            RadioButton rb = new RadioButton(c);
            rb.setId(View.generateViewId());
            rb.setText(names[i]);
            rb.setTextColor(Ui.TEXT);
            rb.setTextSize(18);
            rb.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
            rb.setPadding(Ui.dp(c, 6), Ui.dp(c, 8), 0, Ui.dp(c, 8));
            group.addView(rb);
            if (g.textCase == map[i]) rb.setChecked(true);
            final int mode = map[i];
            rb.setOnCheckedChangeListener((v, checked) -> {
                if (checked) {
                    g.textCase = mode;
                    host.onGlobalsChanged();
                }
            });
        }
        body.addView(group);

        Button snap = Ui.button(c, g.snap ? "Snap: ON" : "Snap: OFF", Ui.TEXT, null);
        snap.setOnClickListener(v -> {
            g.snap = !g.snap;
            snap.setText(g.snap ? "Snap: ON" : "Snap: OFF");
            host.onGlobalsChanged();
        });
        body.addView(row(c, Ui.button(c, "Add Key", Ui.TEXT, v -> host.addKey()),
                Ui.button(c, "Add Mouse", Ui.TEXT, v -> host.addMouse()),
                Ui.button(c, "Add Stick", Ui.TEXT, v -> host.addStick())));
        body.addView(row(c, snap, Ui.button(c, "Save", Ui.TEXT, v -> host.save()), null));
        body.addView(row(c, Ui.button(c, "Undo", Ui.TEXT, v -> host.undo()),
                Ui.button(c, "Redo", Ui.TEXT, v -> host.redo()), null));
        body.addView(row(c, Ui.button(c, "Add Drawer", Ui.TEXT, v -> host.addDrawer()),
                Ui.button(c, "Close Editor", Ui.TEXT, v -> host.closeEditor()), null));

        ScrollView scroll = new ScrollView(c);
        scroll.addView(body, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    /** Three equal columns; a null cell stays empty. */
    private static View row(Context c, View a, View b, View d) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(HORIZONTAL);
        r.setPadding(0, Ui.dp(c, 6), 0, 0);
        View[] cells = {a, b, d};
        for (View v : cells) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(c, 50), 1f);
            lp.setMargins(Ui.dp(c, 4), 0, Ui.dp(c, 4), 0);
            if (v == null) {
                r.addView(new View(c), lp);
            } else {
                r.addView(v, lp);
            }
        }
        return r;
    }

    static FrameLayout.LayoutParams params(Context c, int screenWidthPx) {
        int width = Math.min((int) (screenWidthPx * 0.40f), Ui.dp(c, 460));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.END | Gravity.TOP);
        lp.setMargins(0, Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 8));
        return lp;
    }
}
