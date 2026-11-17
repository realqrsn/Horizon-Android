package dev.horizon.android.controls;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.util.Log;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import dev.horizon.android.HorizonInput;
import java.util.List;

/**
 * Puts the touch controls and the floating cog in a transparent window above the engine's
 * NativeActivity window, and keeps them in step with the game (grab state, FPS).
 */
public final class HostControls {
    private static final String TAG = "HorizonControls";

    private final Activity activity;
    private final ControlsStore store;
    private final WindowManager windowManager;
    private List<ControlButton> buttons;
    private FrameLayout panel;
    private ControlsOverlay overlay;
    private CogView cog;
    private boolean destroyed;
    private int frame;

    private final Choreographer.FrameCallback poll = new Choreographer.FrameCallback() {
        @Override public void doFrame(long nanos) {
            if (destroyed || overlay == null) return;
            overlay.setGrabbed(HorizonInput.isGrabbed());
            if ((frame++ & 15) == 0 && cog != null) cog.setFps(HorizonInput.fps());
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    public HostControls(Activity activity) {
        this.activity = activity;
        this.store = new ControlsStore(activity);
        this.windowManager = activity.getWindowManager();
    }

    public void attach() {
        View decor = activity.getWindow().getDecorView();
        decor.post(() -> attachWhenReady(decor, 0));
    }

    private void attachWhenReady(View decor, int attempt) {
        if (destroyed || panel != null) return;
        if (decor.getWindowToken() == null) {
            if (attempt < 100) decor.postDelayed(() -> attachWhenReady(decor, attempt + 1), 50L);
            else Log.e(TAG, "Engine window never got a token; touch controls unavailable");
            return;
        }
        try {
            build(decor);
        } catch (Throwable t) {
            Log.e(TAG, "Could not add touch controls over Horizon", t);
            detach();
        }
    }

    private void build(View decor) {
        buttons = store.load();
        FrameLayout p = new FrameLayout(activity);
        p.setBackgroundColor(Color.TRANSPARENT);
        ViewGroup.LayoutParams match = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

        overlay = new ControlsOverlay(activity, buttons);
        overlay.setControlsEnabled(store.controlsEnabled());
        overlay.setListener(new ControlsOverlay.Listener() {
            @Override public void onMenuRequested() { showMenu(); }
            @Override public void onEditButton(ControlButton b) { editButton(b); }
            @Override public void onLayoutChanged() { store.save(buttons); }
            @Override public void onEditDone() {
                overlay.setEditMode(false);
                store.save(buttons);
            }
        });
        p.addView(overlay, match);

        cog = new CogView(activity);
        cog.setShowFps(store.showFps());
        cog.setListener(new CogView.Listener() {
            @Override public void onCogTapped() { showMenu(); }
            @Override public void onCogMoved(float fx, float fy) { store.setCogPosition(fx, fy); }
        });
        p.addView(cog, cogParams());
        p.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> positionCog());

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        params.token = decor.getWindowToken();
        params.setTitle("Horizon touch controls");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        windowManager.addView(p, params);
        panel = p;
        HorizonInput.setControlsAttached(true);
        Choreographer.getInstance().postFrameCallback(poll);
        Log.i(TAG, "Touch controls attached over Horizon");
    }

    private FrameLayout.LayoutParams cogParams() {
        int size = Ui.dp(activity, store.cogSizeDp());
        int height = store.showFps() ? size + Ui.dp(activity, 18) : size;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, height, Gravity.TOP | Gravity.START);
        return lp;
    }

    private void positionCog() {
        if (panel == null || cog == null || panel.getWidth() == 0) return;
        float cx = store.cogX() * panel.getWidth();
        float cy = store.cogY() * panel.getHeight();
        int badge = store.showFps() ? Ui.dp(activity, 18) : 0;
        float x = Math.max(0, Math.min(panel.getWidth() - cog.getWidth(), cx - cog.getWidth() / 2f));
        float y = Math.max(0, Math.min(panel.getHeight() - cog.getHeight(), cy - (cog.getHeight() - badge) / 2f - badge));
        cog.setX(x);
        cog.setY(y);
    }

    private void relayoutCog() {
        cog.setLayoutParams(cogParams());
        cog.setShowFps(store.showFps());
        panel.post(this::positionCog);
    }

    private void showMenu() {
        ControlsMenu.show(activity, new ControlsMenu.Host() {
            @Override public boolean isEditMode() { return overlay.isEditMode(); }
            @Override public void setEditMode(boolean on) {
                overlay.setEditMode(on);
                if (!on) store.save(buttons);
            }
            @Override public void addButton() { addNewButton(); }
            @Override public boolean controlsEnabled() { return overlay.isControlsEnabled(); }
            @Override public void setControlsEnabled(boolean on) {
                store.setControlsEnabled(on);
                overlay.setControlsEnabled(on);
            }
            @Override public boolean showFps() { return store.showFps(); }
            @Override public void setShowFps(boolean on) {
                store.setShowFps(on);
                relayoutCog();
            }
            @Override public int cogSizeDp() { return store.cogSizeDp(); }
            @Override public void setCogSizeDp(int dp) {
                store.setCogSizeDp(dp);
                relayoutCog();
            }
            @Override public void resetLayout() {
                store.reset();
                buttons = store.load();
                overlay.setButtons(buttons);
            }
        });
    }

    private void addNewButton() {
        ControlButton b = new ControlButton();
        b.id = ControlsStore.newId();
        b.label = "";
        b.x = 0.5f;
        b.y = 0.5f;
        buttons.add(b);
        overlay.setEditMode(true);
        overlay.invalidate();
        editButton(b);
    }

    private void editButton(ControlButton b) {
        ButtonSettingsDialog.show(activity, b, buttons, new ButtonSettingsDialog.Callback() {
            @Override public void onSave() {
                store.save(buttons);
                overlay.setButtons(buttons);
                overlay.setEditMode(true);
            }
            @Override public void onDelete(ControlButton button) {
                buttons.remove(button);
                if (button.is(ControlButton.DRAWER)) {
                    for (int i = buttons.size() - 1; i >= 0; i--) if (button.id.equals(buttons.get(i).parentId)) buttons.remove(i);
                }
                store.save(buttons);
                overlay.setButtons(buttons);
                overlay.setEditMode(true);
            }
            @Override public void onDuplicate(ControlButton button) {
                ControlButton c = button.copy();
                c.id = ControlsStore.newId();
                c.x = Math.min(0.95f, c.x + 0.05f);
                c.y = Math.min(0.95f, c.y + 0.05f);
                buttons.add(c);
                store.save(buttons);
                overlay.setButtons(buttons);
                overlay.setEditMode(true);
            }
        });
    }

    public void detach() {
        destroyed = true;
        Choreographer.getInstance().removeFrameCallback(poll);
        if (overlay != null) overlay.releaseAll();
        if (panel != null) {
            try {
                windowManager.removeViewImmediate(panel);
            } catch (RuntimeException ignored) {
            }
            panel = null;
        }
        HorizonInput.setControlsAttached(false);
    }
}
