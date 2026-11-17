package dev.horizon.android.controls;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import dev.horizon.android.HorizonInput;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Draws every control and turns touches into HorizonInput calls. One view, one touch handler,
 * so fingers on different buttons work together. Touches that miss every button drive the
 * camera (grabbed) or act as a mouse (menus). In edit mode buttons are dragged to move and
 * resized with the handle on the selected one.
 */
public final class ControlsOverlay extends View {
    public interface Listener {
        void onMenuRequested();
        void onEditButton(ControlButton button);
        /** A drag or resize is about to change the layout (take an undo snapshot). */
        void onBeforeChange();
        /** A drag or resize finished. */
        void onLayoutChanged();
    }

    private static final int CLAIM_BUTTON = 0;
    private static final int CLAIM_CAMERA = 1;
    private static final int CLAIM_MOVE = 2;
    private static final int CLAIM_RESIZE = 3;

    private static final class Claim {
        int type;
        ControlButton button;
        float downX, downY, lastX, lastY;
        long downTime;
        boolean moved;
        boolean leftHeld;
        boolean cameraPrimed;
        Runnable longPress;
        Runnable repeat;
        float startX, startY, startW, startH;
        float centerX, centerY, knobX, knobY;
        final boolean[] held = new boolean[4];
        boolean sprint;
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<Integer, Claim> claims = new HashMap<>();
    private final Set<String> pressedIds = new HashSet<>();
    private final Set<String> toggled = new HashSet<>();
    private final Set<String> openDrawers = new HashSet<>();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final float density;
    private final float slop;

    private List<ControlButton> buttons;
    private ControlsStore.Globals globals = new ControlsStore.Globals();
    private Listener listener;
    private boolean editMode;
    private boolean grabbed;
    private boolean controlsEnabled = true;
    private String selectedId = "";

    public ControlsOverlay(Context context, List<ControlButton> buttons) {
        super(context);
        this.buttons = buttons;
        density = context.getResources().getDisplayMetrics().density;
        slop = 8f * density;
        stroke.setStyle(Paint.Style.STROKE);
        text.setTextAlign(Paint.Align.CENTER);
        resetDrawers();
        setWillNotDraw(false);
    }

    public void setListener(Listener l) { listener = l; }
    public boolean isEditMode() { return editMode; }
    public boolean isControlsEnabled() { return controlsEnabled; }

    public void setGlobals(ControlsStore.Globals g) {
        globals = g;
        invalidate();
    }

    public void setButtons(List<ControlButton> list) {
        releaseAll();
        buttons = list;
        resetDrawers();
        invalidate();
    }

    public void setSelected(String id) {
        selectedId = id == null ? "" : id;
        invalidate();
    }

    public void setEditMode(boolean on) {
        if (editMode == on) return;
        releaseAll();
        editMode = on;
        if (!on) selectedId = "";
        invalidate();
    }

    public void setControlsEnabled(boolean on) {
        if (controlsEnabled == on) return;
        releaseAll();
        controlsEnabled = on;
        invalidate();
    }

    public void setGrabbed(boolean g) {
        if (grabbed == g) return;
        grabbed = g;
        releaseAll();
        invalidate();
    }

    private void resetDrawers() {
        openDrawers.clear();
        for (ControlButton b : buttons) if (b.is(ControlButton.DRAWER) && b.drawerOpenByDefault) openDrawers.add(b.id);
    }

    private float dp(float v) { return v * density; }

    private ControlButton find(String id) {
        if (id == null || id.isEmpty()) return null;
        for (ControlButton b : buttons) if (b.id.equals(id)) return b;
        return null;
    }

    private float scale() { return globals.scale / 100f; }

    private float widthOf(ControlButton b) {
        float w = dp(b.widthDp) * scale();
        return b.is(ControlButton.JOYSTICK) ? Math.min(w, dp(b.heightDp) * scale()) : w;
    }

    private float heightOf(ControlButton b) {
        float h = dp(b.heightDp) * scale();
        return b.is(ControlButton.JOYSTICK) ? Math.min(h, dp(b.widthDp) * scale()) : h;
    }

    private RectF baseRect(ControlButton b) {
        float w = widthOf(b), h = heightOf(b);
        float pad = dp(globals.paddingDp);
        float minX = pad + w / 2, maxX = Math.max(minX, getWidth() - pad - w / 2);
        float minY = pad + h / 2, maxY = Math.max(minY, getHeight() - pad - h / 2);
        float cx = Math.max(minX, Math.min(maxX, b.x * getWidth()));
        float cy = Math.max(minY, Math.min(maxY, b.y * getHeight()));
        return new RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
    }

    private ControlButton parentDrawer(ControlButton b) {
        ControlButton p = find(b.parentId);
        return p != null && p.is(ControlButton.DRAWER) && p != b ? p : null;
    }

    private RectF rectOf(ControlButton b) {
        ControlButton p = parentDrawer(b);
        if (p == null) return baseRect(b);
        RectF pr = baseRect(p);
        float gap = dp(6);
        boolean horizontal = p.drawerHorizontal;
        int dir = horizontal ? (pr.centerX() > getWidth() / 2f ? -1 : 1) : (pr.centerY() > getHeight() / 2f ? -1 : 1);
        float offset = gap;
        for (ControlButton c : buttons) {
            if (!p.id.equals(c.parentId)) continue;
            float cw = widthOf(c), ch = heightOf(c);
            if (c == b) {
                if (horizontal) {
                    float left = dir > 0 ? pr.right + offset : pr.left - offset - cw;
                    return new RectF(left, pr.centerY() - ch / 2, left + cw, pr.centerY() + ch / 2);
                }
                float top = dir > 0 ? pr.bottom + offset : pr.top - offset - ch;
                return new RectF(pr.centerX() - cw / 2, top, pr.centerX() + cw / 2, top + ch);
            }
            offset += (horizontal ? cw : ch) + gap;
        }
        return pr;
    }

    private boolean visible(ControlButton b) {
        if (editMode) return true;
        if (!controlsEnabled && !b.stayVisible && !b.is(ControlButton.TOGGLE_CONTROLS)) return false;
        if (grabbed ? !b.visibleInGame : !b.visibleInMenu) return false;
        ControlButton p = parentDrawer(b);
        return p == null || (visible(p) && openDrawers.contains(p.id));
    }

    private ControlButton hit(float x, float y) {
        for (int i = buttons.size() - 1; i >= 0; i--) {
            ControlButton b = buttons.get(i);
            if (!visible(b)) continue;
            RectF r = new RectF(rectOf(b));
            if (b.is(ControlButton.JOYSTICK) && b.joystickFloating) r.inset(-r.width() * 0.4f, -r.height() * 0.4f);
            if (r.contains(x, y)) return b;
        }
        return null;
    }

    private boolean onHandle(ControlButton b, float x, float y) {
        RectF r = rectOf(b);
        float cx = r.right - dp(8), cy = r.bottom - dp(8);
        return Math.hypot(x - cx, y - cy) <= dp(24);
    }

    // ---------------------------------------------------------------- drawing

    private static int withAlpha(int color, float alpha) {
        int a = Math.round(((color >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, alpha)));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private String labelOf(ControlButton b) {
        String s;
        if (!b.label.isEmpty()) s = b.label;
        else if (b.is(ControlButton.KEY)) s = Glfw.shortLabel(b.keys[0]);
        else if (b.is(ControlButton.MOUSE)) s = b.mouseButton == 0 ? "Attack" : b.mouseButton == 1 ? "Use" : "Pick";
        else if (b.is(ControlButton.SCROLL)) s = b.scrollDirection > 0 ? "\u25B2" : "\u25BC";
        else if (b.is(ControlButton.MENU)) s = "Menu";
        else if (b.is(ControlButton.KEYBOARD)) s = "Keyboard";
        else if (b.is(ControlButton.TOGGLE_CONTROLS)) s = "Hide";
        else if (b.is(ControlButton.DRAWER)) s = openDrawers.contains(b.id) ? "\u25C0" : "\u25B6";
        else s = "";
        if (globals.textCase == 1) return s.toUpperCase(Locale.getDefault());
        if (globals.textCase == 2) return s.toLowerCase(Locale.getDefault());
        return s;
    }

    @Override
    protected void onDraw(Canvas c) {
        float alpha = globals.opacity / 100f;
        for (ControlButton b : buttons) {
            if (!visible(b)) continue;
            RectF r = rectOf(b);
            if (b.is(ControlButton.JOYSTICK)) {
                drawJoystick(c, b, r, joystickClaim(b), alpha);
                continue;
            }
            boolean pressed = pressedIds.contains(b.id);
            boolean active = toggled.contains(b.id);
            float rx = Math.min(dp(globals.radiusDp), Math.min(r.width(), r.height()) / 2f);
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(withAlpha(active ? b.toggleColor : pressed ? 0xFF3A3A3A : 0xFF0A0A0A, alpha));
            c.drawRoundRect(r, rx, rx, fill);
            if (globals.strokeDp > 0) {
                float sw = dp(globals.strokeDp);
                stroke.setStrokeWidth(sw);
                stroke.setColor(withAlpha(0xFFE3D2C8, alpha));
                RectF in = new RectF(r.left + sw / 2, r.top + sw / 2, r.right - sw / 2, r.bottom - sw / 2);
                c.drawRoundRect(in, rx, rx, stroke);
            }
            drawLabel(c, labelOf(b), r, alpha, active ? 0xFF000000 : 0xFFF5EDE8);
            if (editMode && b.id.equals(selectedId)) drawHandle(c, r, rx);
        }
        if (editMode) {
            stroke.setStrokeWidth(dp(2));
            stroke.setColor(0xFFE8862A);
            c.drawRoundRect(new RectF(dp(4), dp(4), getWidth() - dp(4), getHeight() - dp(4)), dp(4), dp(4), stroke);
        }
    }

    private void drawLabel(Canvas c, String label, RectF r, float alpha, int color) {
        if (label.isEmpty()) return;
        float size = Math.min(r.height() * 0.5f, dp(15));
        text.setTextSize(size);
        float maxW = r.width() * 0.9f;
        float measured = text.measureText(label);
        if (measured > maxW) {
            size *= maxW / measured;
            text.setTextSize(size);
        }
        text.setColor(withAlpha(color, Math.min(1f, alpha + 0.2f)));
        c.drawText(label, r.centerX(), r.centerY() + size * 0.35f, text);
    }

    private void drawHandle(Canvas c, RectF r, float rx) {
        c.save();
        clip.reset();
        clip.addRoundRect(r, rx, rx, Path.Direction.CW);
        c.clipPath(clip);
        float cx = r.right - dp(8), cy = r.bottom - dp(8), rad = dp(18);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(0xFF1E7B55);
        c.drawCircle(cx, cy, rad, fill);
        stroke.setStrokeWidth(dp(1.5f));
        stroke.setColor(0x99FFFFFF);
        c.drawLine(cx - dp(2), cy + dp(10), cx + dp(10), cy - dp(2), stroke);
        c.drawLine(cx + dp(2), cy + dp(10), cx + dp(10), cy + dp(2), stroke);
        c.restore();
    }

    private void drawJoystick(Canvas c, ControlButton b, RectF r, Claim claim, float alpha) {
        float cx = claim != null ? claim.centerX : r.centerX();
        float cy = claim != null ? claim.centerY : r.centerY();
        float radius = r.width() / 2f;
        if (b.joystickFloating && claim == null && !editMode) return;
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(withAlpha(0xFF101010, alpha));
        c.drawCircle(cx, cy, radius, fill);
        stroke.setStrokeWidth(dp(1));
        stroke.setColor(withAlpha(0xFF6B6B6B, alpha));
        c.drawCircle(cx, cy, radius - dp(0.5f), stroke);
        stroke.setColor(withAlpha(0xFF2E2E2E, alpha));
        c.drawCircle(cx, cy, radius * 0.58f, stroke);
        float kx = claim != null ? claim.knobX : cx;
        float ky = claim != null ? claim.knobY : cy;
        fill.setColor(withAlpha(claim != null && claim.sprint ? b.toggleColor : 0xFF5F5F5F, alpha));
        c.drawCircle(kx, ky, radius * 0.38f, fill);
        if (editMode && b.id.equals(selectedId)) drawHandle(c, r, radius);
    }

    private Claim joystickClaim(ControlButton b) {
        for (Claim cl : claims.values()) if (cl.type == CLAIM_BUTTON && cl.button == b && b.is(ControlButton.JOYSTICK)) return cl;
        return null;
    }

    // ---------------------------------------------------------------- touch

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int i = e.getActionIndex();
                down(e.getPointerId(i), e.getX(i), e.getY(i));
                break;
            }
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < e.getPointerCount(); i++) move(e.getPointerId(i), e.getX(i), e.getY(i));
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                int i = e.getActionIndex();
                up(e.getPointerId(i), e.getX(i), e.getY(i), false);
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                releaseAll();
                break;
            default:
                break;
        }
        return true;
    }

    private boolean hasCameraClaim() {
        for (Claim c : claims.values()) if (c.type == CLAIM_CAMERA) return true;
        return false;
    }

    private void down(int id, float x, float y) {
        Claim cl = new Claim();
        cl.downX = cl.lastX = x;
        cl.downY = cl.lastY = y;
        cl.downTime = System.currentTimeMillis();
        if (editMode) {
            ControlButton sel = find(selectedId);
            if (sel != null && visible(sel) && onHandle(sel, x, y)) {
                cl.type = CLAIM_RESIZE;
                cl.button = sel;
                cl.startW = sel.widthDp;
                cl.startH = sel.heightDp;
                claims.put(id, cl);
                return;
            }
            ControlButton b = hit(x, y);
            if (b == null) {
                setSelected("");
                return;
            }
            cl.type = CLAIM_MOVE;
            cl.button = b;
            cl.startX = b.x;
            cl.startY = b.y;
            claims.put(id, cl);
            return;
        }
        ControlButton b = hit(x, y);
        if (b != null) {
            cl.type = CLAIM_BUTTON;
            cl.button = b;
            claims.put(id, cl);
            pressButton(cl, x, y);
            invalidate();
            return;
        }
        if (hasCameraClaim()) return;
        cl.type = CLAIM_CAMERA;
        claims.put(id, cl);
        if (grabbed) {
            HorizonInput.cursorSilently(x, y);
            cl.longPress = () -> {
                if (!cl.moved) {
                    cl.leftHeld = true;
                    HorizonInput.mouseButton(0, true);
                }
            };
            handler.postDelayed(cl.longPress, 350);
        } else {
            HorizonInput.cursor(x, y);
            HorizonInput.mouseButton(0, true);
            cl.leftHeld = true;
        }
    }

    private float snapTo(float v) {
        float grid = dp(8);
        return Math.round(v / grid) * grid;
    }

    private void move(int id, float x, float y) {
        Claim cl = claims.get(id);
        if (cl == null) return;
        boolean wasMoved = cl.moved;
        if (!cl.moved && Math.hypot(x - cl.downX, y - cl.downY) > slop) cl.moved = true;
        switch (cl.type) {
            case CLAIM_CAMERA:
                if (!grabbed || cl.moved) HorizonInput.cursor(x, y);
                break;
            case CLAIM_MOVE:
                if (cl.moved && parentDrawer(cl.button) == null) {
                    if (!wasMoved && listener != null) listener.onBeforeChange();
                    float w = widthOf(cl.button), h = heightOf(cl.button);
                    float cx = cl.startX * getWidth() + (x - cl.downX);
                    float cy = cl.startY * getHeight() + (y - cl.downY);
                    if (globals.snap) {
                        cx = snapTo(cx - w / 2) + w / 2;
                        cy = snapTo(cy - h / 2) + h / 2;
                    }
                    cl.button.x = Math.max(0.01f, Math.min(0.99f, cx / getWidth()));
                    cl.button.y = Math.max(0.01f, Math.min(0.99f, cy / getHeight()));
                    invalidate();
                }
                break;
            case CLAIM_RESIZE:
                if (cl.moved) {
                    if (!wasMoved && listener != null) listener.onBeforeChange();
                    float per = density * scale();
                    float nw = Math.max(14f, Math.min(500f, cl.startW + (x - cl.downX) / per));
                    float nh = Math.max(14f, Math.min(500f, cl.startH + (y - cl.downY) / per));
                    if (cl.button.is(ControlButton.JOYSTICK)) nw = nh = Math.max(nw, nh);
                    cl.button.widthDp = nw;
                    cl.button.heightDp = nh;
                    invalidate();
                }
                break;
            case CLAIM_BUTTON:
                if (cl.button.is(ControlButton.JOYSTICK)) {
                    moveJoystick(cl, x, y);
                } else {
                    swipeTo(cl, x, y);
                    if (cl.button.mousePassthrough && grabbed) {
                        if (!cl.cameraPrimed) {
                            cl.cameraPrimed = true;
                            HorizonInput.cursorSilently(cl.lastX, cl.lastY);
                        }
                        HorizonInput.cursor(x, y);
                    }
                }
                break;
            default:
                break;
        }
        cl.lastX = x;
        cl.lastY = y;
    }

    /** Sliding a held finger from one swipeable button onto another hands the press over. */
    private void swipeTo(Claim cl, float x, float y) {
        ControlButton from = cl.button;
        if (!from.swipeable || rectOf(from).contains(x, y)) return;
        ControlButton to = hit(x, y);
        if (to == null || to == from || !to.swipeable || to.is(ControlButton.JOYSTICK)) return;
        releaseButton(cl, true);
        cl.button = to;
        pressButton(cl, x, y);
        invalidate();
    }

    private void up(int id, float x, float y, boolean cancelled) {
        Claim cl = claims.remove(id);
        if (cl == null) return;
        finish(cl, cancelled);
        invalidate();
    }

    private void finish(Claim cl, boolean cancelled) {
        if (cl.longPress != null) handler.removeCallbacks(cl.longPress);
        switch (cl.type) {
            case CLAIM_CAMERA:
                if (cl.leftHeld) {
                    HorizonInput.mouseButton(0, false);
                } else if (grabbed && !cl.moved && !cancelled && System.currentTimeMillis() - cl.downTime < 350) {
                    HorizonInput.mouseButton(0, true);
                    handler.postDelayed(() -> HorizonInput.mouseButton(0, false), 70);
                }
                break;
            case CLAIM_BUTTON:
                releaseButton(cl, cancelled);
                break;
            case CLAIM_MOVE:
                if (cl.moved) {
                    if (listener != null) listener.onLayoutChanged();
                } else if (!cancelled) {
                    setSelected(cl.button.id);
                    if (listener != null) listener.onEditButton(cl.button);
                }
                break;
            case CLAIM_RESIZE:
                if (cl.moved && listener != null) listener.onLayoutChanged();
                break;
            default:
                break;
        }
    }

    public void releaseAll() {
        for (Claim cl : new HashMap<>(claims).values()) finish(cl, true);
        claims.clear();
        pressedIds.clear();
        invalidate();
    }

    // ---------------------------------------------------------------- button actions

    private void sendKeys(ControlButton b, boolean down) {
        for (int k : b.keys) if (k != 0) HorizonInput.key(k, down);
    }

    private void pressButton(Claim cl, float x, float y) {
        ControlButton b = cl.button;
        pressedIds.add(b.id);
        switch (b.action) {
            case ControlButton.KEY:
                if (b.toggle) {
                    boolean on = !toggled.contains(b.id);
                    if (on) toggled.add(b.id); else toggled.remove(b.id);
                    sendKeys(b, on);
                } else {
                    sendKeys(b, true);
                    if (b.repeatOnHold) startRepeat(cl, () -> {
                        sendKeys(b, false);
                        sendKeys(b, true);
                    });
                }
                break;
            case ControlButton.MOUSE:
                if (b.toggle) {
                    boolean on = !toggled.contains(b.id);
                    if (on) toggled.add(b.id); else toggled.remove(b.id);
                    HorizonInput.mouseButton(b.mouseButton, on);
                } else {
                    HorizonInput.mouseButton(b.mouseButton, true);
                }
                break;
            case ControlButton.SCROLL:
                HorizonInput.scroll(0f, b.scrollDirection);
                if (b.repeatOnHold) startRepeat(cl, () -> HorizonInput.scroll(0f, b.scrollDirection));
                break;
            case ControlButton.JOYSTICK: {
                RectF r = rectOf(b);
                cl.centerX = b.joystickFloating ? x : r.centerX();
                cl.centerY = b.joystickFloating ? y : r.centerY();
                cl.knobX = x;
                cl.knobY = y;
                moveJoystick(cl, x, y);
                break;
            }
            default:
                break;
        }
    }

    private void startRepeat(Claim cl, Runnable action) {
        cl.repeat = new Runnable() {
            @Override public void run() {
                action.run();
                handler.postDelayed(this, 90);
            }
        };
        handler.postDelayed(cl.repeat, 350);
    }

    private void releaseButton(Claim cl, boolean cancelled) {
        ControlButton b = cl.button;
        if (cl.repeat != null) {
            handler.removeCallbacks(cl.repeat);
            cl.repeat = null;
        }
        pressedIds.remove(b.id);
        switch (b.action) {
            case ControlButton.KEY:
                if (!b.toggle) sendKeys(b, false);
                break;
            case ControlButton.MOUSE:
                if (!b.toggle) HorizonInput.mouseButton(b.mouseButton, false);
                break;
            case ControlButton.MENU:
                if (!cancelled && listener != null) listener.onMenuRequested();
                break;
            case ControlButton.KEYBOARD:
                if (!cancelled) HorizonInput.requestKeyboard();
                break;
            case ControlButton.TOGGLE_CONTROLS:
                if (!cancelled) setControlsEnabled(!controlsEnabled);
                break;
            case ControlButton.DRAWER:
                if (!cancelled && !openDrawers.remove(b.id)) openDrawers.add(b.id);
                break;
            case ControlButton.JOYSTICK:
                for (int i = 0; i < 4; i++) if (cl.held[i] && b.keys[i] != 0) HorizonInput.key(b.keys[i], false);
                if (cl.sprint && b.sprintKey != 0) HorizonInput.key(b.sprintKey, false);
                java.util.Arrays.fill(cl.held, false);
                cl.sprint = false;
                break;
            default:
                break;
        }
    }

    private void moveJoystick(Claim cl, float x, float y) {
        ControlButton b = cl.button;
        float radius = widthOf(b) / 2f;
        float dx = x - cl.centerX;
        float dy = y - cl.centerY;
        float dist = (float) Math.hypot(dx, dy);
        float scale = dist > radius ? radius / dist : 1f;
        cl.knobX = cl.centerX + dx * scale;
        cl.knobY = cl.centerY + dy * scale;
        float dead = radius * b.deadzonePercent / 100f;
        float axis = 0.38f * dist;
        boolean[] want = {
            dy < -dead && Math.abs(dy) > axis,
            dx < -dead && Math.abs(dx) > axis,
            dy > dead && Math.abs(dy) > axis,
            dx > dead && Math.abs(dx) > axis,
        };
        for (int i = 0; i < 4; i++) {
            if (want[i] != cl.held[i]) {
                cl.held[i] = want[i];
                if (b.keys[i] != 0) HorizonInput.key(b.keys[i], want[i]);
            }
        }
        boolean sprint = b.forwardLock && want[0] && dist > radius * 1.35f;
        if (sprint != cl.sprint) {
            cl.sprint = sprint;
            if (b.sprintKey != 0) HorizonInput.key(b.sprintKey, sprint);
        }
        invalidate();
    }
}
