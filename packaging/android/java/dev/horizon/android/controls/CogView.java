package dev.horizon.android.controls;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

/**
 * The floating cog: a dark translucent circle with a white gear. Drag it anywhere; tap it for
 * the touch-controls menu. An optional FPS badge sits above it.
 */
public final class CogView extends View {
    public interface Listener {
        void onCogTapped();
        /** Centre of the cog as a fraction of the screen, after a drag. */
        void onCogMoved(float fx, float fy);
    }

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF badge = new RectF();
    private final float density;
    private final float slop;
    private Drawable gear;
    private Listener listener;
    private boolean showFps;
    private boolean editing;
    private int fps;
    private float downRawX, downRawY, startX, startY;
    private boolean dragging;

    public CogView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        slop = 8f * density;
        fill.setColor(0xE6000000);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(density);
        stroke.setColor(0x66FFFFFF);
        text.setColor(0xFFFFFFFF);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
        int id = context.getResources().getIdentifier("horizon_ic_settings", "drawable", context.getPackageName());
        if (id != 0) gear = context.getDrawable(id);
        setContentDescription("Touch controls");
    }

    public void setListener(Listener l) { listener = l; }

    /** In the editor the cog turns brown, like DroidBridge's. */
    public void setEditing(boolean on) {
        editing = on;
        invalidate();
    }

    public void setShowFps(boolean on) {
        showFps = on;
        invalidate();
    }

    public void setFps(int value) {
        if (value != fps) {
            fps = value;
            if (showFps) invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth();
        float h = getHeight();
        float badgeH = showFps ? 18f * density : 0f;
        float d = Math.min(w, h - badgeH);
        float cx = w / 2f;
        float cy = badgeH + d / 2f;
        fill.setColor(editing ? 0xFF5A4636 : 0xE6000000);
        stroke.setColor(editing ? 0x33FFFFFF : 0x99FFFFFF);
        c.drawCircle(cx, cy, d / 2f - density, fill);
        c.drawCircle(cx, cy, d / 2f - density, stroke);
        if (gear != null) {
            int pad = (int) (d * 0.25f);
            gear.setBounds((int) (cx - d / 2f) + pad, (int) (cy - d / 2f) + pad, (int) (cx + d / 2f) - pad, (int) (cy + d / 2f) - pad);
            gear.setTint(editing ? 0xFFF3DED0 : 0xFFFFFFFF);
            gear.draw(c);
        } else {
            text.setTextSize(d * 0.45f);
            c.drawText("\u2699", cx, cy + d * 0.16f, text);
        }
        if (showFps) {
            String s = fps + " FPS";
            text.setTextSize(11f * density);
            float bw = text.measureText(s) + 14f * density;
            badge.set(cx - bw / 2f, 0, cx + bw / 2f, badgeH - 2f * density);
            c.drawRoundRect(badge, badgeH / 2f, badgeH / 2f, fill);
            c.drawText(s, cx, badge.centerY() + 4f * density, text);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        View parent = (View) getParent();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = e.getRawX();
                downRawY = e.getRawY();
                startX = getX();
                startY = getY();
                dragging = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!dragging && Math.hypot(e.getRawX() - downRawX, e.getRawY() - downRawY) > slop) dragging = true;
                if (dragging && parent != null) {
                    float nx = Math.max(0, Math.min(parent.getWidth() - getWidth(), startX + e.getRawX() - downRawX));
                    float ny = Math.max(0, Math.min(parent.getHeight() - getHeight(), startY + e.getRawY() - downRawY));
                    setX(nx);
                    setY(ny);
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (dragging && parent != null && listener != null) {
                    listener.onCogMoved((getX() + getWidth() / 2f) / parent.getWidth(), (getY() + getHeight() / 2f) / parent.getHeight());
                } else if (!dragging && listener != null) {
                    listener.onCogTapped();
                }
                dragging = false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                return true;
            default:
                return true;
        }
    }
}
