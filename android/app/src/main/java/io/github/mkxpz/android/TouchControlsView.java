package io.github.mkxpz.android;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.util.SparseArray;
import android.view.HapticFeedbackConstants;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

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
 * <p>The layout comes from {@link TouchControlsConfig}: the one the editor
 * wrote, the one the game ships ({@code <gameDir>/android_controls.json}) or
 * the built-in one.
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
public final class TouchControlsView extends FrameLayout {

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

    /** Told when the editor is opened or closed. */
    public interface EditListener {
        void onEditModeChanged(boolean editing);
    }

    private final List<Control> controls = new ArrayList<>();
    private final SparseArray<Touch> touches = new SparseArray<>();
    /** How many fingers hold each key, so a key is released only once. */
    private final Map<Integer, Integer> keyRefs = new HashMap<>();

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private TouchControlsConfig config;
    private Control dpad;
    private Control toggle;
    private int dpDirMask;
    private boolean secondaryVisible;
    private boolean controlsVisible = true;

    /* Editor state. */
    private EditListener editListener;
    private Runnable editorListener;
    private boolean editing;
    private Control selected;
    private Control dragged;
    private int dragPointer;
    private float dragOffsetX;
    private float dragOffsetY;
    private float gearX;
    private float gearY;
    private float gearR;
    private View gearAnchor;
    private PopupWindow quickMenu;
    private CheckBox fpsToggle;
    private Runnable toggleFps;
    private Runnable openGamepad;
    private TextView fpsLabel;
    private String fpsText;

    public TouchControlsView(Context context, TouchControlsConfig config) {
        super(context);
        /* The pad draws itself; the bar of the editor is a child. */
        setWillNotDraw(false);
        setConfig(config);
        setFocusable(false);
        setFocusableInTouchMode(false);
        setHapticFeedbackEnabled(true);
        paint.setStrokeCap(Paint.Cap.ROUND);
        gearAnchor = new View(context);
        gearAnchor.setContentDescription(context.getString(R.string.controls_menu_title));
        gearAnchor.setFocusable(true);
        gearAnchor.setBackground(new RippleDrawable(ColorStateList.valueOf(0x55FFFFFF),
                null, menuBackground(0xFFFFFFFF)));
        gearAnchor.setOnClickListener(v -> openGear());
        addView(gearAnchor, new FrameLayout.LayoutParams(1, 1));
        fpsLabel = new TextView(context);
        fpsLabel.setTextSize(12);
        fpsLabel.setTextColor(0xFFF4F4F6);
        fpsLabel.setBackgroundColor(0xE6191D26);
        int padding = Math.round(4 * getResources().getDisplayMetrics().density);
        fpsLabel.setPadding(padding * 2, padding, padding * 2, padding);
        fpsLabel.setVisibility(View.INVISIBLE);
        addView(fpsLabel, new FrameLayout.LayoutParams(-2, -2));
    }

    void setFpsText(String text) {
        fpsText = text;
        if (fpsToggle != null) fpsToggle.setChecked(text != null);
        fpsLabel.setText(text);
        fpsLabel.setVisibility(text == null ? View.INVISIBLE : View.VISIBLE);
        invalidate();
    }

    void setFpsToggleListener(Runnable listener) {
        toggleFps = listener;
    }

    void setGamepadListener(Runnable listener) {
        openGamepad = listener;
    }

    private void openGear() {
        if (editing) {
            if (editListener != null) editListener.onEditModeChanged(true);
            return;
        }
        if (quickMenu != null) return;
        releaseAllKeys();
        Rect safe = new Rect();
        getSafeBounds(safe);
        safe.inset(dp(8), dp(8));
        constrainPanelToUpperBand(safe);
        if (safe.width() <= 0 || safe.height() <= 0) return;

        LinearLayout items = new LinearLayout(getContext());
        items.setOrientation(LinearLayout.VERTICAL);
        items.setPadding(dp(8), dp(4), dp(8), dp(4));
        items.addView(menuButton(R.string.controls_editor_title, v -> {
            dismissQuickMenu();
            beginEditing();
        }), new LinearLayout.LayoutParams(-1, -2));
        items.addView(menuButton(R.string.gamepad_title, v -> {
            dismissQuickMenu();
            if (openGamepad != null) openGamepad.run();
        }), new LinearLayout.LayoutParams(-1, -2));
        fpsToggle = new CheckBox(getContext());
        fpsToggle.setText(R.string.controls_show_fps);
        fpsToggle.setTextColor(0xFFF4F4F6);
        fpsToggle.setTextSize(14);
        fpsToggle.setMinHeight(dp(48));
        fpsToggle.setPadding(dp(8), 0, dp(8), 0);
        fpsToggle.setBackground(new RippleDrawable(ColorStateList.valueOf(0x55FFFFFF),
                menuBackground(0xF5191D26), null));
        fpsToggle.setButtonTintList(new ColorStateList(new int[][]{
                new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{0xFFFFC850, 0xFFC6CBD5}));
        fpsToggle.setChecked(fpsText != null);
        fpsToggle.setOnClickListener(v -> {
            // The engine's next title, including physical F2 presses, sets the checked state.
            fpsToggle.setChecked(fpsText != null);
            if (toggleFps != null) toggleFps.run();
        });
        items.addView(fpsToggle, new LinearLayout.LayoutParams(-1, -2));
        items.addView(menuButton(R.string.controls_hide, v -> setControlsVisible(false)),
                new LinearLayout.LayoutParams(-1, -2));
        for (int i = 0; i < items.getChildCount() - 1; i++) {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) items.getChildAt(i).getLayoutParams();
            params.bottomMargin = dp(4);
        }

        ScrollView content = new ScrollView(getContext());
        content.setPadding(0, dp(4), 0, dp(4));
        content.setClipToPadding(false);
        content.addView(items, new ScrollView.LayoutParams(-1, -2));
        int width = Math.min(dp(232), safe.width());
        content.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(safe.height(), MeasureSpec.AT_MOST));
        int height = content.getMeasuredHeight();
        quickMenu = new PopupWindow(content, width, height, true);
        quickMenu.setBackgroundDrawable(menuBackground(0xF5191D26));
        quickMenu.setElevation(dp(6));
        quickMenu.setOutsideTouchable(true);
        quickMenu.setOnDismissListener(() -> {
            quickMenu = null;
            fpsToggle = null;
        });
        int left = Math.max(safe.left, Math.min(Math.round(gearX - gearR), safe.right - width));
        int top = Math.round(gearY + gearR) + dp(4);
        if (top + height > safe.bottom) top = Math.round(gearY - gearR) - dp(4) - height;
        top = Math.max(safe.top, Math.min(top, safe.bottom - height));
        int[] location = new int[2];
        gearAnchor.getLocationInWindow(location);
        int[] origin = new int[2];
        getLocationInWindow(origin);
        quickMenu.showAsDropDown(gearAnchor, origin[0] + left - location[0],
                origin[1] + top - location[1] - gearAnchor.getHeight(), Gravity.LEFT);
    }

    void dismissQuickMenu() {
        if (quickMenu != null) quickMenu.dismiss();
    }

    private Button menuButton(int text, View.OnClickListener listener) {
        Button button = new Button(getContext());
        button.setText(text);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setTextColor(new ColorStateList(new int[][]{
                new int[]{android.R.attr.state_focused}, new int[]{}},
                new int[]{0xFFFFC850, 0xFFF4F4F6}));
        button.setBackgroundTintList(null);
        button.setStateListAnimator(null);
        GradientDrawable face = menuBackground(0xFF303744);
        face.setStroke(dp(1), 0xFF7D8798);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x55FFFFFF),
                face, null));
        ControlsEditorUi.fitButton(button);
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        button.setPadding(dp(12), dp(6), dp(12), dp(6));
        button.setOnClickListener(listener);
        return button;
    }

    private GradientDrawable menuBackground(int color) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(12));
        return background;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void positionFpsLabel() {
        if (fpsLabel == null || fpsText == null) return;
        Rect safe = new Rect();
        getSafeBounds(safe);
        int gap = Math.round(8 * getResources().getDisplayMetrics().density);
        safe.inset(gap, gap);
        if (safe.width() <= 0 || safe.height() <= 0) {
            fpsLabel.setVisibility(View.INVISIBLE);
            return;
        }
        fpsLabel.measure(MeasureSpec.makeMeasureSpec(safe.width(), MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(safe.height(), MeasureSpec.AT_MOST));
        int width = fpsLabel.getMeasuredWidth();
        int height = fpsLabel.getMeasuredHeight();
        Rect gear = new Rect((int) (gearX - gearR), (int) (gearY - gearR),
                (int) Math.ceil(gearX + gearR), (int) Math.ceil(gearY + gearR));
        // ponytail: search the edges; hide on crowded layouts until a free-position HUD is needed.
        for (int row = 0; row <= (safe.height() - height) / (height + gap); row++) {
            for (int corner = 0; corner < 4; corner++) {
                int left = corner % 2 == 0 ? safe.right - width : safe.left;
                int top = corner < 2 ? safe.top + row * (height + gap)
                        : safe.bottom - height - row * (height + gap);
                Rect bounds = new Rect(left, top, left + width, top + height);
                if (controlsVisible && (Rect.intersects(bounds, gear) || editorOverlap(bounds) > 0)) {
                    continue;
                }
                fpsLabel.layout(bounds.left, bounds.top, bounds.right, bounds.bottom);
                fpsLabel.setVisibility(View.VISIBLE);
                return;
            }
        }
        fpsLabel.setVisibility(View.INVISIBLE);
    }

    /** Replaces the layout of the pad. */
    public void setConfig(TouchControlsConfig config) {
        releaseAllKeys();
        this.config = config;
        controls.clear();
        for (TouchControlsConfig.Spec spec : config.buttons) {
            controls.add(new Control(spec));
        }
        dpad = config.dpad != null ? new Control(config.dpad) : null;
        toggle = config.toggle != null ? new Control(config.toggle) : null;
        selected = null;
        dragged = null;
        layout(getWidth(), getHeight());
        invalidate();
        notifyEditor();
    }

    /** The layout of the pad, as it is now (the editor changes it). */
    public TouchControlsConfig getConfig() {
        return config;
    }

    public void setEditListener(EditListener listener) {
        editListener = listener;
    }

    public boolean isEditing() {
        return editing;
    }

    void setEditorListener(Runnable listener) {
        editorListener = listener;
    }

    private void notifyEditor() {
        if (editorListener != null && editing) {
            editorListener.run();
        }
    }

    /** Insets are in window coordinates; SDL may already offset this view. */
    void getSafeBounds(Rect bounds) {
        bounds.set(0, 0, getWidth(), getHeight());
        WindowInsets insets = getRootWindowInsets();
        if (insets == null) return;
        int left;
        int top;
        int right;
        int bottom;
        if (Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets bars = insets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            left = bars.left;
            top = bars.top;
            right = bars.right;
            bottom = bars.bottom;
        } else {
            left = insets.getStableInsetLeft();
            top = insets.getStableInsetTop();
            right = insets.getStableInsetRight();
            bottom = insets.getStableInsetBottom();
            if (Build.VERSION.SDK_INT >= 28 && insets.getDisplayCutout() != null) {
                left = Math.max(left, insets.getDisplayCutout().getSafeInsetLeft());
                top = Math.max(top, insets.getDisplayCutout().getSafeInsetTop());
                right = Math.max(right, insets.getDisplayCutout().getSafeInsetRight());
                bottom = Math.max(bottom, insets.getDisplayCutout().getSafeInsetBottom());
            }
        }
        int[] location = new int[2];
        getLocationInWindow(location);
        bounds.intersect(Math.max(0, left - location[0]), Math.max(0, top - location[1]),
                getRootView().getWidth() - right - location[0],
                getRootView().getHeight() - bottom - location[1]);
    }

    /** Keep portrait panels above the picture; their ScrollView handles taller content. */
    void constrainPanelToUpperBand(Rect bounds) {
        if (getHeight() <= getWidth()) return;
        // ponytail: use the existing 4:3 game estimate; use native viewport bounds if exposed later.
        int gameTop = (int) Math.floor((getHeight() - getWidth() * 0.75f) / 2f);
        int bottom = Math.min(bounds.bottom, gameTop - dp(8));
        if (bottom - bounds.top >= dp(100)) bounds.bottom = bottom;
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        dismissQuickMenu();
        WindowInsets result = super.onApplyWindowInsets(insets);
        layout(getWidth(), getHeight());
        notifyEditor();
        invalidate();
        return result;
    }

    /** Choose a panel anchor away from controls, especially the selection. */
    float editorOverlap(Rect panel) {
        float overlap = 0;
        for (Control control : controls) {
            if (!control.spec.secondary || secondaryVisible) overlap += overlap(control, panel);
        }
        if (dpad != null) overlap += overlap(dpad, panel);
        if (toggle != null) overlap += overlap(toggle, panel);
        return overlap;
    }

    private float overlap(Control control, Rect panel) {
        float r = control.r * 1.12f;
        float width = Math.max(0, Math.min(control.cx + r, panel.right)
                - Math.max(control.cx - r, panel.left));
        float height = Math.max(0, Math.min(control.cy + r, panel.bottom)
                - Math.max(control.cy - r, panel.top));
        return width * height * (control == selected ? 100 : 1);
    }

    /* ------------------------------------------------------------ layout */

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        dismissQuickMenu();
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

        Rect safe = new Rect();
        getSafeBounds(safe);
        gearR = Math.max(0.025f * unit, 16f * getResources().getDisplayMetrics().density);
        gearX = safe.left + margin + gearR;
        gearY = safe.top + margin + gearR;
        if (gearAnchor != null) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) gearAnchor.getLayoutParams();
            int diameter = Math.round(gearR * 2);
            int left = Math.round(gearX - gearR);
            int top = Math.round(gearY - gearR);
            if (params.width != diameter || params.height != diameter
                    || params.leftMargin != left || params.topMargin != top) {
                params.width = diameter;
                params.height = diameter;
                params.gravity = Gravity.TOP | Gravity.LEFT;
                params.leftMargin = left;
                params.topMargin = top;
                gearAnchor.setLayoutParams(params);
            }
        }

        if (isAbsolute()) {
            /* Every control says where it goes. */
            for (Control control : controls) {
                place(control, w, h);
            }
            if (dpad != null) {
                place(dpad, w, h);
            }
            if (toggle != null) {
                place(toggle, w, h);
            }
            return;
        }

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
        for (Control control : controls) {
            if (control.spec.secondary && !secondaryVisible) {
                continue;
            }
            visible.add(control);
            maxSize = Math.max(maxSize, control.spec.size);
        }
        int count = visible.size();
        if (count == 0) {
            return;
        }

        /* The buttons are arranged in a grid, as big as they fit. */
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

    /** True when every control says where it goes. */
    private boolean isAbsolute() {
        for (Control control : controls) {
            if (control.spec.grid) {
                return false;
            }
        }
        if (dpad != null && dpad.spec.grid) {
            return false;
        }
        return toggle == null || !toggle.spec.grid;
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
        if (editing) {
            return onEditTouch(event, action);
        }
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int index = event.getActionIndex();
                float x = event.getX(index);
                float y = event.getY(index);

                /* Any touch brings the pad back if a keyboard or a pad hid it. */
                setControlsVisible(true);

                if (hitGear(x, y)) {
                    openGear();
                    return true;
                }

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
                    applyDpad(touch, dpadMaskAt(x, y, touch.dirMask));
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
                    changed |= applyDpad(touch, dpadMaskAt(event.getX(index), event.getY(index), touch.dirMask));
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
     * Handles the touches while the editor is open.
     *
     * <p>No key is sent to the engine while editing.
     */
    private boolean onEditTouch(MotionEvent event, int action) {
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int index = event.getActionIndex();
                float x = event.getX(index);
                float y = event.getY(index);
                if (hitGear(x, y)) {
                    /* The bar of the editor was hidden: bring it back. */
                    if (editListener != null) {
                        editListener.onEditModeChanged(true);
                    }
                    return true;
                }
                int hit = hitTest(x, y);
                if (hit == HIT_NONE || hit == HIT_TOGGLE) {
                    selected = null;
                    invalidate();
                    notifyEditor();
                    return true;
                }
                selected = hit == HIT_DPAD ? dpad : controls.get(hit);
                if (selected == null) {
                    return true;
                }
                dragged = selected;
                dragPointer = event.getPointerId(index);
                dragOffsetX = x - selected.cx;
                dragOffsetY = y - selected.cy;
                invalidate();
                notifyEditor();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (dragged == null) {
                    return true;
                }
                int index = event.findPointerIndex(dragPointer);
                if (index < 0) {
                    return true;
                }
                moveTo(dragged, event.getX(index) - dragOffsetX, event.getY(index) - dragOffsetY);
                invalidate();
                notifyEditor();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL: {
                dragged = null;
                invalidate();
                return true;
            }
            default:
                break;
        }
        return true;
    }

    /** Moves a control and keeps the layout in sync with it. */
    private void moveTo(Control control, float x, float y) {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        keepGridSize(control, w, h);
        control.spec.x = clamp(x / w);
        control.spec.y = clamp(y / h);
        place(control, (int) w, (int) h);
    }

    /**
     * Leaves the automatic grid. The grid shrinks buttons to fit the free
     * space, so the size drawn is written too, or the buttons would grow back
     * to their nominal size and overlap.
     */
    private static void keepGridSize(Control control, float w, float h) {
        if (control.spec.grid && control.r > 0f) {
            control.spec.size = control.r * 2f / Math.min(w, h);
        }
        control.spec.grid = false;
    }

    private static float clamp(float value) {
        return value < 0f ? 0f : (value > 1f ? 1f : value);
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

    private boolean hitGear(float x, float y) {
        float dx = x - gearX;
        float dy = y - gearY;
        /* Easier to hit than it looks, but at least a 48dp target. */
        float r = Math.max(gearR * 1.25f, 24f * getResources().getDisplayMetrics().density);
        return dx * dx + dy * dy <= r * r;
    }

    private static boolean hit(Control control, float x, float y) {
        /* A little bigger than the drawing, but never so big that two
         * controls fight over the same touch. */
        float r = control.r * 1.12f;
        float dx = x - control.cx;
        float dy = y - control.cy;
        return dx * dx + dy * dy <= r * r;
    }

    /**
     * Direction of the d-pad under the given point. Four-way only: RPG Maker
     * moves in four directions, and a diagonal sends two arrows at once, so
     * aiming down slightly to the right could walk right. The current
     * direction is kept until the finger is clearly in another quadrant, so a
     * thumb resting near a border does not flicker between two directions.
     */
    private int dpadMaskAt(float x, float y, int current) {
        if (distance(x, y, dpad.cx, dpad.cy) < dpad.r * 0.30f) {
            return 0; /* dead zone */
        }
        double angle = Math.toDegrees(Math.atan2(y - dpad.cy, x - dpad.cx));
        if (angle < 0) {
            angle += 360.0;
        }
        double held = current == DIR_RIGHT ? 0.0 : current == DIR_DOWN ? 90.0
                : current == DIR_LEFT ? 180.0 : current == DIR_UP ? 270.0 : -1.0;
        if (held >= 0.0) {
            double off = Math.abs(angle - held);
            if (Math.min(off, 360.0 - off) < 55.0) {
                return current;
            }
        }
        if (angle < 45.0 || angle >= 315.0) {
            return DIR_RIGHT;
        } else if (angle < 135.0) {
            return DIR_DOWN;
        } else if (angle < 225.0) {
            return DIR_LEFT;
        }
        return DIR_UP;
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
        dismissQuickMenu();
        super.onDetachedFromWindow();
    }

    /* ------------------------------------------------------------ editor */

    /**
     * Opens the editor.
     *
     * <p>No key is sent to the engine while the editor is open, and the
     * controls keep the position they have now so that they can be moved.
     */
    public void beginEditing() {
        dismissQuickMenu();
        releaseAllKeys();
        setControlsVisible(true);
        float w = getWidth();
        float h = getHeight();
        for (Control control : controls) {
            freeze(control, w, h);
        }
        if (dpad != null) {
            freeze(dpad, w, h);
        }
        editing = true;
        selected = null;
        dragged = null;
        layout(getWidth(), getHeight());
        if (editListener != null) {
            editListener.onEditModeChanged(true);
        }
        invalidate();
    }

    /** Closes the editor, keeping the changes. */
    public void endEditing() {
        editing = false;
        selected = null;
        dragged = null;
        if (editListener != null) {
            editListener.onEditModeChanged(false);
        }
        invalidate();
    }

    /** Writes the position of a control in the layout, as a screen fraction. */
    private void freeze(Control control, float w, float h) {
        if (w <= 0 || h <= 0) {
            return;
        }
        keepGridSize(control, w, h);
        control.spec.x = clamp(control.cx / w);
        control.spec.y = clamp(control.cy / h);
    }

    /** The control the player has selected, or null. */
    public TouchControlsConfig.Spec getSelectedSpec() {
        return selected != null ? selected.spec : null;
    }

    /** Adds a button and selects it. */
    public void addButton() {
        float w = getWidth();
        float h = getHeight();
        float unit = Math.min(w, h);
        /* Same size as the buttons already there, so it fits in with them. */
        float size = controls.isEmpty() || unit <= 0f ? 0.130f
                : controls.get(controls.size() - 1).r * 2f / unit;
        float[] spot = freeSpot(w, h, size * unit / 2f);
        TouchControlsConfig.Spec spec = new TouchControlsConfig.Spec(
                "?", KeyEvent.KEYCODE_ENTER, spot[0], spot[1], size, false, false);
        config.buttons.add(spec);
        Control control = new Control(spec);
        controls.add(control);
        layout(getWidth(), getHeight());
        selected = control;
        invalidate();
        notifyEditor();
    }

    /**
     * Finds where a new button of radius {@code r} can go: outside the game
     * picture and away from the other controls, starting at the bottom right,
     * where the other buttons are. Falls back to the middle of the screen.
     *
     * @return x and y as screen fractions
     */
    private float[] freeSpot(float w, float h, float r) {
        if (w <= 0f || h <= 0f || r <= 0f) {
            return new float[] {0.5f, 0.5f};
        }
        float gameW = Math.min(w, h * GAME_ASPECT);
        float gameH = Math.min(h, w / GAME_ASPECT);
        float gameL = (w - gameW) / 2f;
        float gameT = (h - gameH) / 2f;
        float step = r * 2f + 0.016f * Math.min(w, h);
        for (float y = h - step / 2f; y >= step / 2f; y -= step / 2f) {
            for (float x = w - step / 2f; x >= step / 2f; x -= step / 2f) {
                boolean onGame = x + r > gameL && x - r < gameL + gameW
                        && y + r > gameT && y - r < gameT + gameH;
                if (!onGame && isFree(x, y, step / 2f)) {
                    return new float[] {x / w, y / h};
                }
            }
        }
        return new float[] {0.5f, 0.5f};
    }

    /** True when a circle of radius {@code r} touches no control nor the gear. */
    private boolean isFree(float x, float y, float r) {
        List<Control> all = new ArrayList<>(controls);
        if (dpad != null) all.add(dpad);
        if (toggle != null) all.add(toggle);
        for (Control control : all) {
            float dx = x - control.cx;
            float dy = y - control.cy;
            float min = r + control.r;
            if (dx * dx + dy * dy < min * min) {
                return false;
            }
        }
        float dx = x - gearX;
        float dy = y - gearY;
        float min = r + gearR;
        return dx * dx + dy * dy >= min * min;
    }

    /** Removes the selected button. */
    public void deleteSelected() {
        if (selected == null || selected == dpad) {
            return;
        }
        controls.remove(selected);
        config.buttons.remove(selected.spec);
        selected = null;
        invalidate();
        notifyEditor();
    }

    /** Gives the selected control a new key. */
    public void setSelectedKey(int keyCode) {
        if (selected == null) {
            return;
        }
        selected.spec.keyCode = keyCode;
        notifyEditor();
    }

    /** Gives the selected control a new label. */
    public void setSelectedLabel(String label) {
        if (selected == null || label == null) {
            return;
        }
        selected.spec.label = label;
        invalidate();
        notifyEditor();
    }

    /** Gives the selected control a new size. */
    public void setSelectedSize(float size) {
        if (selected == null) {
            return;
        }
        selected.spec.size = size;
        layout(getWidth(), getHeight());
        invalidate();
        notifyEditor();
    }

    /* ----------------------------------------------------------- drawing */

    public void setControlsVisible(boolean visible) {
        if (controlsVisible != visible) {
            controlsVisible = visible;
            gearAnchor.setVisibility(visible ? View.VISIBLE : View.GONE);
            if (!visible) dismissQuickMenu();
            invalidate();
        }
    }

    public boolean areControlsVisible() {
        return controlsVisible;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        positionFpsLabel();
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
        if (editing && selected != null) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(0xFF191D26);
            paint.setStrokeWidth(stroke * 5);
            canvas.drawCircle(selected.cx, selected.cy, selected.r + stroke * 2, paint);
            paint.setColor(0xFFFFC850);
            paint.setStrokeWidth(stroke * 2);
            canvas.drawCircle(selected.cx, selected.cy, selected.r + stroke * 2, paint);
        }
        drawGear(canvas);
    }

    /** The little gear that opens the quick menu, or restores the editor panel. */
    private void drawGear(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(editing ? Color.argb(60, 255, 255, 255) : Color.argb(110, 255, 255, 255));
        canvas.save();
        canvas.translate(gearX, gearY);
        for (int i = 0; i < 8; i++) {
            canvas.rotate(45f);
            canvas.drawRect(-gearR * 0.18f, -gearR, gearR * 0.18f, -gearR * 0.68f, paint);
        }
        canvas.drawCircle(0f, 0f, gearR * 0.62f, paint);
        canvas.restore();

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, gearR * 0.12f));
        paint.setColor(Color.argb(150, 255, 255, 255));
        canvas.drawCircle(gearX, gearY, gearR * 0.62f, paint);
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
        paint.setColor(control == selected ? Color.argb(255, 255, 200, 80)
                : Color.argb(120, 255, 255, 255));
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
