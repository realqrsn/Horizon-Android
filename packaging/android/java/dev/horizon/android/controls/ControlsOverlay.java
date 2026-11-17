package dev.horizon.android.controls;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import dev.horizon.android.HorizonInput;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Draws every control and turns touches into HorizonInput calls. One view, one touch handler,
 * so fingers on different buttons work together. Touches that miss every button drive the
 * camera (grabbed) or act as a mouse (menus).
 */
public final class ControlsOverlay extends View {
    public interface Listener {
        void onMenuRequested();
        void onEditButton(ControlButton button);
        void onLayoutChanged();
        void onEditDone();
    }

    private static final int CLAIM_BUTTON = 0;
    private static final int CLAIM_CAMERA = 1;
    private static final int CLAIM_EDIT = 2;
    private static final int CLAIM_DONE = 3;

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
        float startFx, startFy;
        // Joystick
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
    private final RectF tmp = new RectF();
    private final RectF doneRect = new RectF();
    private final float density;
    private final float slop;

    private List<ControlButton> buttons;
    private Listener listener;
    private boolean editMode;
    private boolean grabbed;
    private boolean controlsEnabled = true;

    public ControlsOverlay(Context context, List<ControlButton> buttons) {
        super(context);
        this.buttons = buttons;
        density = context.getResources().getDisplayMetrics().density;
        slop = 8f * density;
        stroke.setStyle(Paint.Style.STROKE);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
        resetDrawers();
        setWillNotDraw(false);
    }

    public void setListener(Listener l) { listener = l; }
    public boolean isEditMode() { return editMode; }
    public boolean isControlsEnabled() { return controlsEnabled; }

    public void setButtons(List<ControlButton> list) {
        releaseAll();
        buttons = list;
        resetDrawers();
        invalidate();
    }

    public void setEditMode(boolean on) {
        if (editMode == on) return;
        releaseAll();
        editMode = on;
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

    private float sizeOf(ControlButton b) {
        return dp(b.is(ControlButton.JOYSTICK) ? 150 : 56) * b.sizePercent / 100f;
    }

    private RectF baseRect(ControlButton b) {
        float s = sizeOf(b);
        float cx = Math.max(s / 2, Math.min(getWidth() - s / 2, b.x * getWidth()));
        float cy = Math.max(s / 2, Math.min(getHeight() - s / 2, b.y * getHeight()));
        return new RectF(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2);
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
            float cs = sizeOf(c);
            if (c == b) {
                if (horizontal) {
                    float left = dir > 0 ? pr.right + offset : pr.left - offset - cs;
                    return new RectF(left, pr.centerY() - cs / 2, left + cs, pr.centerY() + cs / 2);
                }
                float top = dir > 0 ? pr.bottom + offset : pr.top - offset - cs;
                return new RectF(pr.centerX() - cs / 2, top, pr.centerX() + cs / 2, top + cs);
            }
            offset += cs + gap;
        }
        return pr;
    }

    private boolean visible(ControlButton b) {
        if (editMode) return true;
        if (!controlsEnabled && !b.is(ControlButton.TOGGLE_CONTROLS)) return false;
        if (b.visibility == ControlButton.VIS_IN_GAME && !grabbed) return false;
        if (b.visibility == ControlButton.VIS_IN_MENU && grabbed) return false;
        ControlButton p = parentDrawer(b);
        return p == null || (visible(p) && openDrawers.contains(p.id));
    }

    private ControlButton hit(float x, float y) {
        for (int i = buttons.size() - 1; i >= 0; i--) {
            ControlButton b = buttons.get(i);
            if (!visible(b)) continue;
            RectF r = new RectF(rectOf(b));
            if (b.is(ControlButton.JOYSTICK) && b.joystickFloating) r.inset(-sizeOf(b) * 0.4f, -sizeOf(b) * 0.4f);
            if (r.contains(x, y)) return b;
        }
        return null;
    }

    // ---------------------------------------------------------------- drawing

    private static int withAlpha(int color, float alpha) {
        int a = Math.round(((color >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, alpha)));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private String labelOf(ControlButton b) {
        if (!b.label.isEmpty()) return b.label;
        if (b.is(ControlButton.KEY)) return Glfw.shortLabel(b.keys[0]);
        if (b.is(ControlButton.MOUSE)) return b.mouseButton == 0 ? "LMB" : b.mouseButton == 1 ? "RMB" : "MMB";
        if (b.is(ControlButton.SCROLL)) return b.scrollDirection > 0 ? "\u25B2" : "\u25BC";
        if (b.is(ControlButton.MENU)) return "Menu";
        if (b.is(ControlButton.TOGGLE_CONTROLS)) return "Hide";
        if (b.is(ControlButton.DRAWER)) return openDrawers.contains(b.id) ? "\u25C0" : "\u25B6";
        return "";
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth();
        for (ControlButton b : buttons) {
            if (!visible(b)) continue;
            Claim joy = joystickClaim(b);
            RectF r = rectOf(b);
            float alpha = b.opacityPercent / 100f;
            boolean pressed = pressedIds.contains(b.id);
            boolean active = toggled.contains(b.id);
            if (b.is(ControlButton.JOYSTICK)) {
                drawJoystick(c, b, r, joy, alpha);
            } else {
                float rx = Math.min(r.width(), r.height()) * Math.max(0, Math.min(50, b.cornerPercent)) / 100f;
                int base = active ? b.activeColor : b.backgroundColor;
                fill.setStyle(Paint.Style.FILL);
                fill.setColor(withAlpha(pressed ? b.activeColor : base, pressed ? Math.min(1f, alpha + 0.25f) : alpha * (active ? 1f : 0.7f)));
                c.drawRoundRect(r, rx, rx, fill);
                if (b.strokeDp > 0) {
                    stroke.setStrokeWidth(dp(b.strokeDp));
                    stroke.setColor(withAlpha(b.strokeColor, alpha));
                    tmp.set(r);
                    tmp.inset(dp(b.strokeDp) / 2, dp(b.strokeDp) / 2);
                    c.drawRoundRect(tmp, rx, rx, stroke);
                }
                drawLabel(c, labelOf(b), r, alpha);
            }
            if (editMode) {
                stroke.setStrokeWidth(dp(1.5f));
                stroke.setColor(0xFFFFD54F);
                stroke.setPathEffect(new DashPathEffect(new float[] {dp(5), dp(4)}, 0));
                tmp.set(r);
                tmp.inset(-dp(3), -dp(3));
                c.drawRoundRect(tmp, dp(8), dp(8), stroke);
                stroke.setPathEffect(null);
            }
        }
        if (editMode) {
            float pw = dp(132), ph = dp(38);
            doneRect.set(w / 2 - pw / 2, dp(10), w / 2 + pw / 2, dp(10) + ph);
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(0xFFD95700);
            c.drawRoundRect(doneRect, ph / 2, ph / 2, fill);
            text.setColor(0xFFFFFFFF);
            text.setTextSize(dp(14));
            c.drawText("Done editing", doneRect.centerX(), doneRect.centerY() + dp(5), text);
            text.setTextSize(dp(11));
            text.setColor(0xCCFFFFFF);
            c.drawText("Drag to move \u2022 tap a button for settings", w / 2, doneRect.bottom + dp(16), text);
        }
    }

    private void drawLabel(Canvas c, String label, RectF r, float alpha) {
        if (label.isEmpty()) return;
        float size = r.height() * 0.3f;
        text.setTextSize(size);
        float maxW = r.width() * 0.84f;
        float measured = text.measureText(label);
        if (measured > maxW) {
            size *= maxW / measured;
            text.setTextSize(size);
        }
        text.setColor(withAlpha(0xFFFFFFFF, Math.min(1f, alpha + 0.3f)));
        c.drawText(label, r.centerX(), r.centerY() + size * 0.35f, text);
    }

    private void drawJoystick(Canvas c, ControlButton b, RectF r, Claim claim, float alpha) {
        float cx = claim != null ? claim.centerX : r.centerX();
        float cy = claim != null ? claim.centerY : r.centerY();
        float radius = r.width() / 2f;
        if (b.joystickFloating && claim == null && !editMode) return;
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(withAlpha(b.backgroundColor, alpha * 0.45f));
        c.drawCircle(cx, cy, radius, fill);
        if (b.strokeDp > 0) {
            stroke.setStrokeWidth(dp(b.strokeDp));
            stroke.setColor(withAlpha(b.strokeColor, alpha));
            c.drawCircle(cx, cy, radius - dp(b.strokeDp) / 2, stroke);
        }
        float knobR = radius * 0.42f;
        float kx = claim != null ? claim.knobX : cx;
        float ky = claim != null ? claim.knobY : cy;
        fill.setColor(withAlpha(claim != null && claim.sprint ? b.activeColor : b.strokeColor, Math.min(1f, alpha + 0.1f) * 0.8f));
        c.drawCircle(kx, ky, knobR, fill);
        if (b.forwardLock) {
            text.setColor(withAlpha(0xFFFFFFFF, alpha));
            text.setTextSize(radius * 0.22f);
            c.drawText("\u25B2", cx, cy - radius + radius * 0.3f, text);
        }
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
            if (doneRect.contains(x, y)) {
                cl.type = CLAIM_DONE;
            } else {
                ControlButton b = hit(x, y);
                if (b == null) return;
                cl.type = CLAIM_EDIT;
                cl.button = b;
                cl.startFx = b.x;
                cl.startFy = b.y;
            }
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

    private void move(int id, float x, float y) {
        Claim cl = claims.get(id);
        if (cl == null) return;
        if (!cl.moved && Math.hypot(x - cl.downX, y - cl.downY) > slop) cl.moved = true;
        switch (cl.type) {
            case CLAIM_CAMERA:
                if (!grabbed || cl.moved) HorizonInput.cursor(x, y);
                break;
            case CLAIM_EDIT:
                if (cl.moved && parentDrawer(cl.button) == null) {
                    cl.button.x = Math.max(0.02f, Math.min(0.98f, cl.startFx + (x - cl.downX) / getWidth()));
                    cl.button.y = Math.max(0.02f, Math.min(0.98f, cl.startFy + (y - cl.downY) / getHeight()));
                    invalidate();
                }
                break;
            case CLAIM_BUTTON:
                if (cl.button.is(ControlButton.JOYSTICK)) {
                    moveJoystick(cl, x, y);
                } else if (cl.button.mousePassthrough && grabbed) {
                    if (!cl.cameraPrimed) {
                        cl.cameraPrimed = true;
                        HorizonInput.cursorSilently(cl.lastX, cl.lastY);
                    }
                    HorizonInput.cursor(x, y);
                }
                break;
            default:
                break;
        }
        cl.lastX = x;
        cl.lastY = y;
    }

    private void up(int id, float x, float y, boolean cancelled) {
        Claim cl = claims.remove(id);
        if (cl == null) return;
        finish(cl, x, y, cancelled);
        invalidate();
    }

    private void finish(Claim cl, float x, float y, boolean cancelled) {
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
            case CLAIM_EDIT:
                if (cl.moved) {
                    if (listener != null) listener.onLayoutChanged();
                } else if (!cancelled && listener != null) {
                    listener.onEditButton(cl.button);
                }
                break;
            case CLAIM_DONE:
                if (!cancelled && doneRect.contains(x, y) && listener != null) listener.onEditDone();
                break;
            default:
                break;
        }
    }

    public void releaseAll() {
        for (Claim cl : new HashMap<>(claims).values()) finish(cl, cl.lastX, cl.lastY, true);
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
        if (cl.repeat != null) handler.removeCallbacks(cl.repeat);
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
            case ControlButton.TOGGLE_CONTROLS:
                if (!cancelled) setControlsEnabled(!controlsEnabled);
                break;
            case ControlButton.DRAWER:
                if (!cancelled) {
                    if (!openDrawers.remove(b.id)) openDrawers.add(b.id);
                }
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
        float radius = sizeOf(b) / 2f;
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
