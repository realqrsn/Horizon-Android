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
import android.widget.Toast;
import dev.horizon.android.HorizonInput;
import java.util.ArrayDeque;
import java.util.List;

/**
 * Puts the touch controls and the floating cog in a transparent window above the engine's
 * NativeActivity window. The window only takes touches while a world is open; on the
 * launcher and title screens it is invisible and lets every touch through.
 */
public final class HostControls {
    private static final String TAG = "HorizonControls";
    private static final int HISTORY = 60;

    private final Activity activity;
    private final ControlsStore store;
    private final WindowManager windowManager;
    private final ArrayDeque<String> undoStack = new ArrayDeque<>();
    private final ArrayDeque<String> redoStack = new ArrayDeque<>();
    private List<ControlButton> buttons;
    private ControlsStore.Globals globals;
    private FrameLayout panel;
    private WindowManager.LayoutParams panelParams;
    private ControlsOverlay overlay;
    private CogView cog;
    private EditorPanel editorPanel;
    private boolean active;
    private boolean destroyed;
    private int frame;

    private final Choreographer.FrameCallback poll = new Choreographer.FrameCallback() {
        @Override public void doFrame(long nanos) {
            if (destroyed || overlay == null) return;
            boolean inGame = HorizonInput.isInGame();
            if (inGame != active) setActive(inGame);
            if (active) {
                overlay.setGrabbed(HorizonInput.isGrabbed());
                if ((frame++ & 15) == 0 && cog != null) cog.setFps(HorizonInput.fps());
            }
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
        globals = store.globals();
        FrameLayout p = new FrameLayout(activity);
        p.setBackgroundColor(Color.TRANSPARENT);
        ViewGroup.LayoutParams match = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

        overlay = new ControlsOverlay(activity, buttons);
        overlay.setGlobals(globals);
        overlay.setControlsEnabled(store.controlsEnabled());
        overlay.setListener(new ControlsOverlay.Listener() {
            @Override public void onMenuRequested() { showMenu(); }
            @Override public void onEditButton(ControlButton b) { editButton(b, true); }
            @Override public void onBeforeChange() { pushUndo(); }
            @Override public void onLayoutChanged() { store.save(buttons); }
        });
        p.addView(overlay, match);

        cog = new CogView(activity);
        cog.setShowFps(store.showFps());
        cog.setListener(new CogView.Listener() {
            @Override public void onCogTapped() {
                if (overlay.isEditMode()) toggleEditorPanel(); else showMenu();
            }
            @Override public void onCogMoved(float fx, float fy) { store.setCogPosition(fx, fy); }
        });
        p.addView(cog, cogParams());
        p.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> positionCog());

        panelParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        panelParams.token = decor.getWindowToken();
        panelParams.setTitle("Horizon touch controls");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            panelParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        // Starts hidden and untouchable: the launcher and title screens must stay usable.
        p.setVisibility(View.GONE);
        windowManager.addView(p, panelParams);
        panel = p;
        HorizonInput.setControlsAttached(true);
        Choreographer.getInstance().postFrameCallback(poll);
        Log.i(TAG, "Touch controls attached over Horizon");
    }

    /** Takes touches and shows the controls only while a world is open. */
    private void setActive(boolean on) {
        active = on;
        if (panel == null) return;
        if (on) {
            panelParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            panel.setVisibility(View.VISIBLE);
        } else {
            overlay.releaseAll();
            closeEditorUi();
            panelParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            panel.setVisibility(View.GONE);
        }
        try {
            windowManager.updateViewLayout(panel, panelParams);
        } catch (RuntimeException ignored) {
        }
    }

    // ---------------------------------------------------------------- cog

    private FrameLayout.LayoutParams cogParams() {
        int size = Ui.dp(activity, store.cogSizeDp());
        int height = store.showFps() ? size + Ui.dp(activity, 18) : size;
        return new FrameLayout.LayoutParams(size, height, Gravity.TOP | Gravity.START);
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
                if (on) enterEditor(); else closeEditor();
            }
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
                pushUndo();
                store.reset();
                buttons = store.load();
                overlay.setButtons(buttons);
            }
        });
    }

    // ---------------------------------------------------------------- editor

    private void enterEditor() {
        overlay.setEditMode(true);
        cog.setEditing(true);
        showEditorPanel();
    }

    private void closeEditor() {
        store.save(buttons);
        store.saveGlobals(globals);
        closeEditorUi();
    }

    private void closeEditorUi() {
        if (overlay != null) overlay.setEditMode(false);
        if (cog != null) cog.setEditing(false);
        hideEditorPanel();
    }

    private void toggleEditorPanel() {
        if (editorPanel != null) hideEditorPanel(); else showEditorPanel();
    }

    private void showEditorPanel() {
        if (editorPanel != null || panel == null) return;
        editorPanel = new EditorPanel(activity, globals, new EditorPanel.Host() {
            @Override public void onGlobalsChanged() {
                overlay.setGlobals(globals);
                store.saveGlobals(globals);
            }
            @Override public void addKey() { addNew(ControlButton.KEY); }
            @Override public void addMouse() { addNew(ControlButton.MOUSE); }
            @Override public void addStick() { addNew(ControlButton.JOYSTICK); }
            @Override public void addDrawer() { addNew(ControlButton.DRAWER); }
            @Override public void save() {
                store.save(buttons);
                store.saveGlobals(globals);
                Toast.makeText(activity, "Layout saved", Toast.LENGTH_SHORT).show();
            }
            @Override public void undo() { undoLayout(); }
            @Override public void redo() { redoLayout(); }
            @Override public void closeEditor() { HostControls.this.closeEditor(); }
            @Override public void hidePanel() { hideEditorPanel(); }
        });
        panel.addView(editorPanel, EditorPanel.params(activity, Math.max(panel.getWidth(), activity.getResources().getDisplayMetrics().widthPixels)));
        cog.bringToFront();
    }

    private void hideEditorPanel() {
        if (editorPanel == null) return;
        if (panel != null) panel.removeView(editorPanel);
        editorPanel = null;
    }

    private void addNew(String action) {
        pushUndo();
        ControlButton b = new ControlButton();
        b.id = ControlsStore.newId();
        b.action = action;
        b.x = 0.5f;
        b.y = 0.5f;
        switch (action) {
            case ControlButton.KEY:
                b.label = "Key";
                break;
            case ControlButton.MOUSE:
                b.label = "Mouse";
                b.mouseButton = 0;
                break;
            case ControlButton.JOYSTICK:
                b.keys = new int[] {'W', 'A', 'S', 'D'};
                b.widthDp = b.heightDp = 150;
                break;
            case ControlButton.DRAWER:
                b.label = "\u25B6";
                b.widthDp = b.heightDp = 40;
                break;
            default:
                break;
        }
        buttons.add(b);
        overlay.setSelected(b.id);
        overlay.invalidate();
        editButton(b, false);
    }

    private void editButton(ControlButton b, boolean snapshot) {
        if (snapshot) pushUndo();
        overlay.setSelected(b.id);
        ButtonSettingsDialog.show(activity, b, buttons, new ButtonSettingsDialog.Callback() {
            @Override public void onPreview() { overlay.invalidate(); }
            @Override public void onSave() {
                store.save(buttons);
                overlay.setButtons(buttons);
                overlay.setSelected(b.id);
            }
            @Override public void onCancel() {
                if (snapshot && !undoStack.isEmpty()) undoStack.pop();
                overlay.invalidate();
            }
            @Override public void onDelete(ControlButton button) {
                buttons.remove(button);
                if (button.is(ControlButton.DRAWER)) {
                    for (int i = buttons.size() - 1; i >= 0; i--) if (button.id.equals(buttons.get(i).parentId)) buttons.remove(i);
                }
                store.save(buttons);
                overlay.setButtons(buttons);
                overlay.setSelected("");
            }
            @Override public void onCopy(ControlButton button) {
                ControlButton c = button.copy();
                c.id = ControlsStore.newId();
                c.x = Math.min(0.95f, c.x + 0.04f);
                c.y = Math.min(0.95f, c.y + 0.07f);
                buttons.add(c);
                store.save(buttons);
                overlay.setButtons(buttons);
                overlay.setSelected(c.id);
            }
        });
    }

    // ---------------------------------------------------------------- undo / redo

    private void pushUndo() {
        undoStack.push(ControlsStore.serialize(buttons));
        redoStack.clear();
        while (undoStack.size() > HISTORY) undoStack.removeLast();
    }

    private void restore(String json) {
        try {
            buttons = ControlsStore.parse(json);
            overlay.setButtons(buttons);
            overlay.setSelected("");
            store.save(buttons);
        } catch (Exception e) {
            Log.w(TAG, "Could not restore layout", e);
        }
    }

    private void undoLayout() {
        if (undoStack.isEmpty()) return;
        redoStack.push(ControlsStore.serialize(buttons));
        restore(undoStack.pop());
    }

    private void redoLayout() {
        if (redoStack.isEmpty()) return;
        undoStack.push(ControlsStore.serialize(buttons));
        restore(redoStack.pop());
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
