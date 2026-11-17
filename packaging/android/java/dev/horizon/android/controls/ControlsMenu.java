package dev.horizon.android.controls;

import android.app.Activity;
import android.app.Dialog;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/** The menu behind the floating cog. */
final class ControlsMenu {
    interface Host {
        boolean isEditMode();
        void setEditMode(boolean on);
        boolean controlsEnabled();
        void setControlsEnabled(boolean on);
        boolean showFps();
        void setShowFps(boolean on);
        int cogSizeDp();
        void setCogSizeDp(int dp);
        void resetLayout();
    }

    private ControlsMenu() {}

    static void show(Activity a, Host host) {
        LinearLayout list = Ui.column(a);
        Dialog[] dialog = new Dialog[1];
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(a, 46));
        lp.setMargins(0, Ui.dp(a, 4), 0, Ui.dp(a, 4));

        list.addView(Ui.title(a, "Touch controls"));
        list.addView(Ui.button(a, "Resume", true, v -> dialog[0].dismiss()), lp);
        list.addView(Ui.button(a, host.isEditMode() ? "Finish editing layout" : "Edit layout", false, v -> {
            dialog[0].dismiss();
            host.setEditMode(!host.isEditMode());
        }), lp);
        list.addView(Ui.button(a, "Controls: " + (host.controlsEnabled() ? "ON" : "OFF"), false, v -> {
            dialog[0].dismiss();
            host.setControlsEnabled(!host.controlsEnabled());
        }), lp);
        list.addView(Ui.button(a, "FPS badge: " + (host.showFps() ? "ON" : "OFF"), false, v -> {
            dialog[0].dismiss();
            host.setShowFps(!host.showFps());
        }), lp);
        int size = host.cogSizeDp();
        String sizeName = size <= 36 ? "Small" : size >= 64 ? "Large" : "Medium";
        list.addView(Ui.button(a, "Cog size: " + sizeName, false, v -> {
            dialog[0].dismiss();
            host.setCogSizeDp(size <= 36 ? 48 : size >= 64 ? 36 : 64);
        }), lp);
        list.addView(Ui.button(a, "Reset layout", false, v -> {
            dialog[0].dismiss();
            confirmReset(a, host);
        }), lp);

        ScrollView scroll = new ScrollView(a);
        scroll.addView(list);
        LinearLayout root = Ui.column(a);
        root.setBackground(Ui.round(Ui.BG, 20, a));
        root.setPadding(Ui.dp(a, 18), Ui.dp(a, 16), Ui.dp(a, 18), Ui.dp(a, 14));
        root.addView(scroll);
        dialog[0] = Ui.dialog(a, root, 380);
        dialog[0].show();
    }

    private static void confirmReset(Activity a, Host host) {
        LinearLayout root = Ui.column(a);
        root.setBackground(Ui.round(Ui.BG, 20, a));
        root.setPadding(Ui.dp(a, 18), Ui.dp(a, 16), Ui.dp(a, 18), Ui.dp(a, 14));
        root.addView(Ui.title(a, "Reset layout?"));
        root.addView(Ui.label(a, "Every button goes back to the default Minecraft layout."));
        Dialog[] d = new Dialog[1];
        LinearLayout row = new LinearLayout(a);
        row.setPadding(0, Ui.dp(a, 12), 0, 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(a, 44), 1f);
        lp.setMargins(Ui.dp(a, 3), 0, Ui.dp(a, 3), 0);
        row.addView(Ui.button(a, "Cancel", false, v -> d[0].dismiss()), lp);
        row.addView(Ui.button(a, "Reset", true, v -> {
            d[0].dismiss();
            host.resetLayout();
        }), lp);
        root.addView(row);
        d[0] = Ui.dialog(a, root, 380);
        d[0].show();
    }
}
