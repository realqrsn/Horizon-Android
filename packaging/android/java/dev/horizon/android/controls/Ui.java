package dev.horizon.android.controls;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.AdapterView;

/** Small helpers so the dialogs share one dark, orange-accent look. */
final class Ui {
    static final int BG = 0xFF1E1A18;
    static final int CARD = 0xFF2A2420;
    static final int ACCENT = 0xFFD95700;
    static final int TEXT = 0xFFFFF1E6;
    static final int MUTED = 0xFFC9B5A8;

    private Ui() {}

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable round(int color, float radiusDp, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    static Dialog dialog(Context c, View content, int maxWidthDp) {
        return dialog(c, content, maxWidthDp, false);
    }

    static Dialog dialog(Context c, View content, int maxWidthDp, boolean fullHeight) {
        Dialog d = new Dialog(c, android.R.style.Theme_Material_Dialog_NoActionBar);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setContentView(content);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int sw = c.getResources().getDisplayMetrics().widthPixels;
            int sh = c.getResources().getDisplayMetrics().heightPixels;
            w.setLayout(Math.min(dp(c, maxWidthDp), (int) (sw * 0.92f)), fullHeight ? (int) (sh * 0.92f) : ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.CENTER);
        }
        return d;
    }

    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static TextView title(Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(TEXT);
        t.setTextSize(18);
        t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setPadding(0, 0, 0, dp(c, 8));
        return t;
    }

    static TextView section(Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s.toUpperCase());
        t.setTextColor(ACCENT);
        t.setTextSize(12);
        t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setPadding(0, dp(c, 14), 0, dp(c, 4));
        return t;
    }

    static TextView label(Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(MUTED);
        t.setTextSize(13);
        t.setPadding(0, dp(c, 8), 0, dp(c, 2));
        return t;
    }

    static Button button(Context c, String s, boolean primary, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(primary ? Color.WHITE : TEXT);
        b.setBackground(round(primary ? ACCENT : CARD, 12, c));
        b.setOnClickListener(l);
        b.setPadding(dp(c, 14), 0, dp(c, 14), 0);
        return b;
    }

    static Switch toggle(Context c, String s, boolean on, java.util.function.Consumer<Boolean> cb) {
        Switch sw = new Switch(c);
        sw.setText(s);
        sw.setTextColor(TEXT);
        sw.setChecked(on);
        sw.setPadding(0, dp(c, 8), 0, dp(c, 8));
        sw.setOnCheckedChangeListener((v, checked) -> cb.accept(checked));
        return sw;
    }

    static Spinner spinner(Context c, String[] items, int selected, java.util.function.IntConsumer cb) {
        Spinner sp = new Spinner(c);
        ArrayAdapter<String> a = new ArrayAdapter<String>(c, android.R.layout.simple_spinner_item, items) {
            @Override public View getView(int pos, View v, ViewGroup parent) {
                TextView t = (TextView) super.getView(pos, v, parent);
                t.setTextColor(TEXT);
                return t;
            }
            @Override public View getDropDownView(int pos, View v, ViewGroup parent) {
                TextView t = (TextView) super.getDropDownView(pos, v, parent);
                t.setTextColor(TEXT);
                t.setBackgroundColor(CARD);
                t.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
                return t;
            }
        };
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(a);
        sp.setSelection(Math.max(0, Math.min(items.length - 1, selected)));
        sp.setBackground(round(CARD, 10, c));
        final boolean[] first = {true};
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (first[0]) { first[0] = false; return; }
                cb.accept(pos);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        return sp;
    }

    /** A labelled slider showing its value. */
    static LinearLayout slider(Context c, String name, String unit, int min, int max, int value, java.util.function.IntConsumer cb) {
        LinearLayout l = column(c);
        TextView t = label(c, name + ": " + value + unit);
        SeekBar s = new SeekBar(c);
        s.setMax(max - min);
        s.setProgress(value - min);
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean user) {
                t.setText(name + ": " + (p + min) + unit);
                if (user) cb.accept(p + min);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        l.addView(t);
        l.addView(s);
        return l;
    }
}
