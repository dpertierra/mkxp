package io.github.mkxpz.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.SparseArray;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import org.libsdl.app.SDLActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Virtual game pad drawn on top of the SDL surface.
 *
 * <p>The layout comes from {@link TouchControlsConfig}: either the one the
 * game ships ({@code <gameDir>/android_controls.json}) or the default one.
 *
 * <p>Touches are turned into SDL keyboard events, so the engine sees them as
 * ordinary keyboard input. SDL translates the Android key codes to scancodes
 * and {@code src/input/keybindings.cpp} maps those to RGSS buttons. The
 * mapping depends on the RGSS version the engine detected:
 *
 * <pre>
 *                 RGSS 1        RGSS 2 and up
 *   Enter         Input::C      Input::C
 *   Space         Input::C      Input::C
 *   C             Input::C      -
 *   Z             Input::A      Input::C
 *   X             Input::B      Input::B
 *   Esc           Input::B      Input::B
 *   Left Shift    Input::A      Input::A
 *   A             Input::X      Input::X
 *   S             Input::Y      Input::Y
 *   D             Input::Z      Input::Z
 *   Q             Input::L      Input::L
 *   W             Input::R      Input::R
 *   Alt           Input::Alt    Input::Alt
 *   Arrows        Input::Up/Down/Left/Right
 * </pre>
 *
 * <p>The default layout only uses keys that mean the same thing in every
 * version (Enter confirms, X cancels, Shift runs).
 *
 * <p>Touches that do not land on a control are not consumed, so the SDL
 * surface below keeps receiving them (the game uses them as mouse input).
 */
public final class TouchControlsView extends View {

    /* Key codes injected into SDL (see the class comment for the mapping). */
    private static final int DPAD_UP = KeyEvent.KEYCODE_DPAD_UP;
    private static final int DPAD_DOWN = KeyEvent.KEYCODE_DPAD_DOWN;
    private static final int DPAD_LEFT = KeyEvent.KEYCODE_DPAD_LEFT;
    private static final int DPAD_RIGHT = KeyEvent.KEYCODE_DPAD_RIGHT;

    /* Direction bits of the d-pad. */
    private static final int DIR_UP = 1;
    private static final int DIR_DOWN = 2;
    private static final int DIR_LEFT = 4;
    private static final int DIR_RIGHT = 8;

    /* Hit codes returned by {@link #hitTest}. */
    private static final int HIT_NONE = -1;
    private static final int HIT_DPAD = -2;
    private static final int HIT_TOGGLE = -3;

    /** The game keeps this aspect ratio, the rest of the screen is free. */
    private static final float GAME_ASPECT = 4f / 3f;

    /** A control of the pad, with its position in pixels. */
    private static final class Control {
        final TouchControlsConfig.Spec spec;
        float cx;
        float cy;
        float r;

        Control(TouchControlsConfig.Spec spec) {
            this.spec = spec;
        }
    }

    /** One finger. {@code control} is a button index or a hit code. */
    private static final class Touch {
        final int control;
        int dirMask;
        final Set<Integer> keys = new HashSet<>();

        Touch(int control) {
            this.control = control;
        }
    }

    private final List<Control> controls = new ArrayList<>();
    private final SparseArray<Touch> touches = new SparseArray<>();
    /** How many fingers hold each key, so a key is released only once. */
    private final Map<Integer, Integer> keyRefs = new HashMap<>();

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Control dpad;
    private Control toggle;
    private int dpDirMask;
    private boolean secondaryVisible;
    private boolean controlsVisible = true;

    public TouchControlsView(Context context, TouchControlsConfig config) {
        super(context);
        for (TouchControlsConfig.Spec spec : config.buttons) {
            controls.add(new Control(spec));
        }
        if (config.dpad != null) {
            dpad = new Control(config.dpad);
        }
        if (config.toggle != null) {
            toggle = new Control(config.toggle);
        }
        setFocusable(false);
        setFocusableInTouchMode(false);
        setHapticFeedbackEnabled(true);
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    /* ------------------------------------------------------------ layout */

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) {
            return;
        }
        layout(w, h);
    }

    /**
     * Places every control.
     *
     * <p>The game keeps its 4:3 ratio, so in landscape there is a bar on each
     * side and in portrait a band below the game. The d-pad goes to the
     * bottom left and the buttons to the bottom right, so they never cover
     * the game.
     */
    private void layout(int w, int h) {
        float unit = Math.min(w, h);
        float gap = 0.016f * unit;
        float margin = 0.030f * unit;

        float gameW = Math.min(w, h * GAME_ASPECT);
        float gameH = Math.min(h, w / GAME_ASPECT);
        float barX = (w - gameW) / 2f;
        float barY = (h - gameH) / 2f;
        boolean portrait = barX <= 0f;

        /* The d-pad: bottom left corner. */
        if (dpad != null) {
            float size = Math.min(dpad.spec.size * unit, Math.max(barX, barY) * 0.95f);
            dpad.r = size / 2f;
            dpad.cx = barX > 0f ? barX / 2f : margin + dpad.r;
            dpad.cy = portrait ? h - barY + barY / 2f : h - margin - dpad.r;
        }

        /* The buttons: the area next to the d-pad. */
        float left;
        float areaW;
        float areaTop;
        float areaH;
        if (portrait) {
            /* Portrait: the band below the game. */
            left = (dpad != null ? dpad.r * 2f : 0f) + gap + margin;
            areaW = w - left;
            areaTop = h - barY;
            areaH = barY;
        } else {
            /* Landscape: the right side bar. */
            left = w - barX;
            areaW = barX;
            areaTop = 0f;
            areaH = h;
        }

        List<Control> visible = new ArrayList<>();
        float maxSize = 0f;
        boolean grid = false;
        for (Control control : controls) {
            if (control.spec.secondary && !secondaryVisible) {
                continue;
            }
            visible.add(control);
            maxSize = Math.max(maxSize, control.spec.size);
            grid |= control.spec.grid;
        }
        int count = visible.size();
        if (count == 0) {
            return;
        }

        if (!grid) {
            /* The layout of the game says where every button goes. */
            for (Control control : visible) {
                place(control, w, h);
            }
            if (toggle != null) {
                place(toggle, w, h);
            }
            return;
        }

        /* Otherwise the buttons are arranged in a grid, as big as they fit. */
        int cols = 1;
        int rows = 1;
        for (Control control : visible) {
            cols = Math.max(cols, (int) control.spec.x + 1);
            rows = Math.max(rows, (int) control.spec.y + 1);
        }
        if (toggle != null) {
            /* The toggle goes to the top of the area, the grid below it. */
            toggle.r = toggle.spec.size * unit / 2f;
            toggle.cx = left + areaW - toggle.r - margin;
            toggle.cy = areaTop + toggle.r + margin;
            float space = toggle.r * 2f + gap;
            areaTop += space;
            areaH -= space;
        }
        float size = maxSize * unit;
        size = Math.min(size, (areaW - (cols - 1) * gap) / cols);
        size = Math.min(size, (areaH - (rows - 1) * gap) / rows);

        float gridW = cols * size + (cols - 1) * gap;
        float gridH = rows * size + (rows - 1) * gap;
        float x0 = left + areaW - gridW - margin;
        /* In portrait the buttons sit in the middle of the band, in landscape
         * they are kept at the bottom, where the thumb reaches best. */
        float y0 = portrait ? areaTop + (areaH - gridH) / 2f
                : areaTop + areaH - gridH - margin;

        for (Control control : visible) {
            control.r = size / 2f;
            control.cx = x0 + control.spec.x * (size + gap) + size / 2f;
            control.cy = y0 + control.spec.y * (size + gap) + size / 2f;
        }
    }

    /**
     * Places a control at the position asked by the layout.
     *
     * <p>Sizes are relative to the shortest side, so the pad keeps the same
     * physical size in landscape and in portrait. Controls are kept inside
     * the screen, even when the layout asks for more.
     */
    private static void place(Control control, int w, int h) {
        float unit = Math.min(w, h);
        float r = control.spec.size * unit / 2f;
        float cx = control.spec.x * w;
        float cy = control.spec.y * h;
        if (cx < r) {
            cx = r;
        } else if (cx > w - r) {
            cx = w - r;
        }
        if (cy < r) {
            cy = r;
        } else if (cy > h - r) {
            cy = h - r;
        }
        control.cx = cx;
        control.cy = cy;
        control.r = r;
    }

    /* ------------------------------------------------------------- touch */

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int index = event.getActionIndex();
                float x = event.getX(index);
                float y = event.getY(index);

                /* Any touch brings the pad back if a keyboard or a pad hid it. */
                setControlsVisible(true);

                int hit = hitTest(x, y);
                if (hit == HIT_NONE) {
                    /* Not a control: let the SDL surface have the gesture. */
                    return false;
                }
                if (hit == HIT_TOGGLE) {
                    secondaryVisible = !secondaryVisible;
                    layout(getWidth(), getHeight());
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    invalidate();
                    return true;
                }

                Touch touch = new Touch(hit);
                touches.put(event.getPointerId(index), touch);
                if (hit == HIT_DPAD) {
                    applyDpad(touch, dpadMaskAt(x, y));
                } else {
                    int key = controls.get(hit).spec.keyCode;
                    pressKey(key);
                    touch.keys.add(key);
                }
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                boolean changed = false;
                for (int i = 0; i < touches.size(); i++) {
                    Touch touch = touches.valueAt(i);
                    if (touch.control != HIT_DPAD) {
                        continue;
                    }
                    int index = event.findPointerIndex(touches.keyAt(i));
                    if (index < 0) {
                        continue;
                    }
                    changed |= applyDpad(touch, dpadMaskAt(event.getX(index), event.getY(index)));
                }
                if (changed) {
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                int id = event.getPointerId(event.getActionIndex());
                Touch touch = touches.get(id);
                if (touch != null) {
                    releaseTouch(touch);
                    touches.remove(id);
                }
                if (action == MotionEvent.ACTION_UP) {
                    /* Last finger up: nothing else can be held. */
                    releaseAllKeys();
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_CANCEL: {
                releaseAllKeys();
                invalidate();
                return true;
            }
            default:
                break;
        }
        return super.onTouchEvent(event);
    }

    /**
     * Finds the control under a point.
     *
     * @return the index of a button, {@link #HIT_DPAD}, {@link #HIT_TOGGLE}
     *         or {@link #HIT_NONE}
     */
    private int hitTest(float x, float y) {
        if (toggle != null && hit(toggle, x, y)) {
            return HIT_TOGGLE;
        }
        for (int i = 0; i < controls.size(); i++) {
            Control control = controls.get(i);
            if (control.spec.secondary && !secondaryVisible) {
                continue;
            }
            if (hit(control, x, y)) {
                return i;
            }
        }
        if (dpad != null && hit(dpad, x, y)) {
            return HIT_DPAD;
        }
        return HIT_NONE;
    }

    private static boolean hit(Control control, float x, float y) {
        /* A little bigger than the drawing, but never so big that two
         * controls fight over the same touch. */
        float r = control.r * 1.12f;
        float dx = x - control.cx;
        float dy = y - control.cy;
        return dx * dx + dy * dy <= r * r;
    }

    /** Directions of the d-pad under the given point. */
    private int dpadMaskAt(float x, float y) {
        if (distance(x, y, dpad.cx, dpad.cy) < dpad.r * 0.30f) {
            return 0; /* dead zone */
        }
        double angle = Math.toDegrees(Math.atan2(y - dpad.cy, x - dpad.cx));
        if (angle < 0) {
            angle += 360.0;
        }
        if (angle < 22.5) {
            return DIR_RIGHT;
        } else if (angle < 67.5) {
            return DIR_RIGHT | DIR_DOWN;
        } else if (angle < 112.5) {
            return DIR_DOWN;
        } else if (angle < 157.5) {
            return DIR_DOWN | DIR_LEFT;
        } else if (angle < 202.5) {
            return DIR_LEFT;
        } else if (angle < 247.5) {
            return DIR_LEFT | DIR_UP;
        } else if (angle < 292.5) {
            return DIR_UP;
        } else if (angle < 337.5) {
            return DIR_UP | DIR_RIGHT;
        }
        return DIR_RIGHT;
    }

    /** Applies a new d-pad direction, pressing and releasing what changed. */
    private boolean applyDpad(Touch touch, int mask) {
        if (mask == touch.dirMask) {
            return false;
        }
        touch.dirMask = mask;
        dpDirMask = mask;

        if ((touch.dirMask & DIR_UP) == 0 && touch.keys.remove(DPAD_UP)) {
            releaseKey(DPAD_UP);
        }
        if ((touch.dirMask & DIR_DOWN) == 0 && touch.keys.remove(DPAD_DOWN)) {
            releaseKey(DPAD_DOWN);
        }
        if ((touch.dirMask & DIR_LEFT) == 0 && touch.keys.remove(DPAD_LEFT)) {
            releaseKey(DPAD_LEFT);
        }
        if ((touch.dirMask & DIR_RIGHT) == 0 && touch.keys.remove(DPAD_RIGHT)) {
            releaseKey(DPAD_RIGHT);
        }

        if ((touch.dirMask & DIR_UP) != 0 && touch.keys.add(DPAD_UP)) {
            pressKey(DPAD_UP);
        }
        if ((touch.dirMask & DIR_DOWN) != 0 && touch.keys.add(DPAD_DOWN)) {
            pressKey(DPAD_DOWN);
        }
        if ((touch.dirMask & DIR_LEFT) != 0 && touch.keys.add(DPAD_LEFT)) {
            pressKey(DPAD_LEFT);
        }
        if ((touch.dirMask & DIR_RIGHT) != 0 && touch.keys.add(DPAD_RIGHT)) {
            pressKey(DPAD_RIGHT);
        }
        return true;
    }

    private void releaseTouch(Touch touch) {
        for (Integer key : new ArrayList<>(touch.keys)) {
            releaseKey(key);
        }
        touch.keys.clear();
        touch.dirMask = 0;
        if (touch.control == HIT_DPAD) {
            dpDirMask = 0;
        }
    }

    private static float distance(float x, float y, float cx, float cy) {
        float dx = x - cx;
        float dy = y - cy;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /* -------------------------------------------------------------- keys */

    private void pressKey(int key) {
        Integer refs = keyRefs.get(key);
        if (refs == null) {
            keyRefs.put(key, 1);
            SDLActivity.onNativeKeyDown(key);
        } else {
            keyRefs.put(key, refs + 1);
        }
    }

    private void releaseKey(int key) {
        Integer refs = keyRefs.get(key);
        if (refs == null) {
            return;
        }
        if (refs <= 1) {
            keyRefs.remove(key);
            SDLActivity.onNativeKeyUp(key);
        } else {
            keyRefs.put(key, refs - 1);
        }
    }

    /** Releases everything; used when the app is paused or the gesture ends. */
    public void releaseAllKeys() {
        List<Integer> keys = new ArrayList<>(keyRefs.keySet());
        for (Integer key : keys) {
            SDLActivity.onNativeKeyUp(key);
        }
        keyRefs.clear();
        touches.clear();
        dpDirMask = 0;
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        /* The view goes away (a rotation, for example): no key may stay
         * pressed in the engine. */
        releaseAllKeys();
        super.onDetachedFromWindow();
    }

    /* ----------------------------------------------------------- drawing */

    public void setControlsVisible(boolean visible) {
        if (controlsVisible != visible) {
            controlsVisible = visible;
            invalidate();
        }
    }

    public boolean areControlsVisible() {
        return controlsVisible;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!controlsVisible) {
            return;
        }
        float stroke = Math.max(2f, 0.0035f * Math.min(getWidth(), getHeight()));

        if (dpad != null) {
            drawDpad(canvas, stroke);
        }
        for (Control control : controls) {
            if (control.spec.secondary && !secondaryVisible) {
                continue;
            }
            drawButton(canvas, control, stroke);
        }
        if (toggle != null) {
            drawToggle(canvas, stroke);
        }
    }

    private void drawDpad(Canvas canvas, float stroke) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(48, 255, 255, 255));
        canvas.drawCircle(dpad.cx, dpad.cy, dpad.r, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        paint.setColor(Color.argb(96, 255, 255, 255));
        canvas.drawCircle(dpad.cx, dpad.cy, dpad.r, paint);

        float arrow = dpad.r * 0.26f;
        float offset = dpad.r * 0.52f;
        drawArrow(canvas, dpad.cx + offset, dpad.cy, arrow, 0, (dpDirMask & DIR_RIGHT) != 0);
        drawArrow(canvas, dpad.cx - offset, dpad.cy, arrow, 2, (dpDirMask & DIR_LEFT) != 0);
        drawArrow(canvas, dpad.cx, dpad.cy + offset, arrow, 1, (dpDirMask & DIR_DOWN) != 0);
        drawArrow(canvas, dpad.cx, dpad.cy - offset, arrow, 3, (dpDirMask & DIR_UP) != 0);
    }

    /** Arrow pointing outwards; 0 = right, 1 = down, 2 = left, 3 = up. */
    private void drawArrow(Canvas canvas, float cx, float cy, float size, int dir, boolean active) {
        float dx = 0f;
        float dy = 0f;
        switch (dir) {
            case 0:
                dx = 1f;
                break;
            case 1:
                dy = 1f;
                break;
            case 2:
                dx = -1f;
                break;
            default:
                dy = -1f;
                break;
        }
        float px = -dy;
        float py = dx;

        Path path = new Path();
        path.moveTo(cx + dx * size, cy + dy * size);
        path.lineTo(cx - dx * size * 0.6f + px * size * 0.8f,
                cy - dy * size * 0.6f + py * size * 0.8f);
        path.lineTo(cx - dx * size * 0.6f - px * size * 0.8f,
                cy - dy * size * 0.6f - py * size * 0.8f);
        path.close();

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(active ? Color.argb(230, 255, 255, 255) : Color.argb(140, 255, 255, 255));
        canvas.drawPath(path, paint);
    }

    private void drawButton(Canvas canvas, Control control, float stroke) {
        boolean active = keyRefs.containsKey(control.spec.keyCode);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(active ? Color.argb(170, 255, 255, 255) : Color.argb(64, 255, 255, 255));
        canvas.drawCircle(control.cx, control.cy, control.r, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        paint.setColor(Color.argb(120, 255, 255, 255));
        canvas.drawCircle(control.cx, control.cy, control.r, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(control.r * (control.spec.label.length() > 1 ? 0.55f : 1.05f));
        paint.setColor(Color.argb(220, 255, 255, 255));
        Paint.FontMetrics fm = paint.getFontMetrics();
        canvas.drawText(control.spec.label, control.cx, control.cy - (fm.ascent + fm.descent) / 2f, paint);
    }

    private void drawToggle(Canvas canvas, float stroke) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(secondaryVisible ? Color.argb(64, 255, 255, 255) : Color.argb(120, 255, 255, 255));
        canvas.drawCircle(toggle.cx, toggle.cy, toggle.r, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        paint.setColor(Color.argb(120, 255, 255, 255));
        canvas.drawCircle(toggle.cx, toggle.cy, toggle.r, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(toggle.r * 1.05f);
        paint.setColor(Color.argb(220, 255, 255, 255));
        Paint.FontMetrics fm = paint.getFontMetrics();
        canvas.drawText(toggle.spec.label, toggle.cx, toggle.cy - (fm.ascent + fm.descent) / 2f, paint);
    }
}
