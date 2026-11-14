package io.github.mkxpz.android;

import android.app.Activity;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Map;

/** Inline gamepad remapping; only Save changes the active map. */
final class GamepadEditor extends ControlsEditorUi {
    private final TouchControlsView view;
    private final GamepadMapping mapping;
    private final Runnable onClose;
    private FrameLayout overlay;
    private ScrollView panel;
    private LinearLayout content;
    private Map<Integer, Integer> draft;
    private boolean waitingForButton;
    private boolean waitingForKey;
    private int selectedButton = KeyEvent.KEYCODE_UNKNOWN;
    private int capturedKey = KeyEvent.KEYCODE_UNKNOWN;

    GamepadEditor(Activity activity, TouchControlsView view, GamepadMapping mapping, Runnable onClose) {
        super(activity);
        this.view = view;
        this.mapping = mapping;
        this.onClose = onClose;
        view.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> positionPanel());
        view.getViewTreeObserver().addOnGlobalLayoutListener(this::positionPanel);
    }

    boolean isShowing() {
        return overlay != null && overlay.getVisibility() == View.VISIBLE;
    }

    void show() {
        mapping.releaseAll();
        view.releaseAllKeys();
        view.dismissQuickMenu();
        if (overlay == null) {
            overlay = new FrameLayout(activity);
            // Block touches outside the panel from reaching the pad or the game.
            overlay.setClickable(true);
            overlay.setFocusableInTouchMode(true);
            panel = new ScrollView(activity);
            panel.setBackground(background(SURFACE, dp(16)));
            panel.setElevation(dp(6));
            panel.setClickable(true);
            content = column();
            content.setPadding(dp(8), dp(4), dp(8), dp(8));
            panel.addView(content, new ScrollView.LayoutParams(-1, -2));
            overlay.addView(panel, new FrameLayout.LayoutParams(1, 1));
            view.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        }
        draft = mapping.copyBindings();
        overlay.setVisibility(View.VISIBLE);
        showBindings();
        overlay.requestFocus();
        view.requestApplyInsets();
    }

    void hide() {
        boolean wasShowing = isShowing();
        if (overlay != null) overlay.setVisibility(View.GONE);
        draft = null;
        waitingForButton = false;
        waitingForKey = false;
        selectedButton = KeyEvent.KEYCODE_UNKNOWN;
        if (wasShowing) onClose.run();
    }

    void onBackPressed() {
        if (waitingForButton || selectedButton != KeyEvent.KEYCODE_UNKNOWN) showBindings();
        else hide();
    }

    boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (capturedKey != KeyEvent.KEYCODE_UNKNOWN && capturedKey == code) {
            if (event.getAction() == KeyEvent.ACTION_UP) capturedKey = KeyEvent.KEYCODE_UNKNOWN;
            return true;
        }
        if (!isShowing()) return false;
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0;
        if (GamepadMapping.isGamepadEvent(event) && GamepadMapping.isButton(code)) {
            if (down && waitingForButton) {
                selectedButton = code;
                showKeyPicker();
            }
            return true;
        }
        if (code == KeyEvent.KEYCODE_BACK
                || (code == KeyEvent.KEYCODE_ESCAPE && !waitingForKey)) {
            if (down) {
                capturedKey = code;
                onBackPressed();
            }
            return true;
        }
        if (down && waitingForKey
                && !GamepadMapping.isGamepadEvent(event) && code != KeyEvent.KEYCODE_UNKNOWN
                && code != KeyEvent.KEYCODE_VOLUME_UP && code != KeyEvent.KEYCODE_VOLUME_DOWN) {
            capturedKey = code;
            chooseKey(code);
            return true;
        }
        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN) return false;
        if (overlay.hasFocus()) activity.getWindow().superDispatchKeyEvent(event);
        return true;
    }

    private void heading(int title) {
        content.removeAllViews();
        TextView heading = text(title, 16, INK);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setPadding(dp(4), dp(8), dp(4), dp(8));
        content.addView(heading);
        panel.scrollTo(0, 0);
    }

    private void showBindings() {
        waitingForButton = false;
        waitingForKey = false;
        selectedButton = KeyEvent.KEYCODE_UNKNOWN;
        heading(R.string.gamepad_title);
        if (draft.isEmpty()) content.addView(text(R.string.gamepad_empty, 14, MUTED));
        for (Map.Entry<Integer, Integer> entry : draft.entrySet()) {
            int buttonCode = entry.getKey();
            LinearLayout row = row();
            TextView label = text(R.string.gamepad_binding, 14, INK);
            label.setText(activity.getString(R.string.gamepad_binding,
                    buttonName(buttonCode), GamepadMapping.keyName(entry.getValue())));
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            Button change = button(R.string.controls_editor_key, v -> {
                selectedButton = buttonCode;
                showKeyPicker();
            });
            change.setContentDescription(activity.getString(R.string.gamepad_change, buttonName(buttonCode)));
            row.addView(change);
            Button delete = button(R.string.controls_editor_delete, v -> {
                draft.remove(buttonCode);
                showBindings();
            });
            delete.setTextColor(0xFFFFB5B0);
            delete.setContentDescription(activity.getString(R.string.gamepad_delete, buttonName(buttonCode)));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
            params.leftMargin = dp(4);
            row.addView(delete, params);
            content.addView(row);
        }
        LinearLayout actions = row();
        addAction(actions, button(R.string.controls_editor_add, v -> showButtonCapture()));
        addAction(actions, button(R.string.controls_editor_restore, v -> {
            draft.clear();
            showBindings();
        }));
        content.addView(actions);
        LinearLayout footer = row();
        addAction(footer, button(R.string.controls_editor_cancel, v -> hide()));
        addAction(footer, primaryButton(R.string.controls_editor_save, v -> {
            if (mapping.save(draft)) hide();
            else {
                TextView error = text(R.string.gamepad_save_error, 14, INK);
                error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE);
                showBindings();
                content.addView(error, 1);
                positionPanel();
            }
        }));
        content.addView(footer);
        positionPanel();
    }

    private void showButtonCapture() {
        waitingForButton = true;
        selectedButton = KeyEvent.KEYCODE_UNKNOWN;
        heading(R.string.gamepad_capture_button);
        content.addView(text(R.string.gamepad_capture_hint, 14, MUTED));
        content.addView(button(R.string.controls_editor_cancel, v -> showBindings()));
        positionPanel();
    }

    private void showKeyPicker() {
        waitingForButton = false;
        waitingForKey = false;
        heading(R.string.controls_editor_key);
        TextView hint = text(R.string.gamepad_choose_key, 14, MUTED);
        hint.setText(activity.getString(R.string.gamepad_choose_key, buttonName(selectedButton)));
        content.addView(hint);
        LinearLayout actions = row();
        addAction(actions, button(R.string.controls_editor_cancel, v -> showBindings()));
        addAction(actions, button(R.string.controls_editor_physical_key, v -> showKeyCapture()));
        content.addView(actions);
        content.addView(keyGrid(panelWidth(), this::chooseKey));
        positionPanel();
    }

    private void showKeyCapture() {
        waitingForKey = true;
        heading(R.string.controls_editor_capture_key);
        content.addView(text(R.string.gamepad_press_key, 14, MUTED));
        LinearLayout actions = row();
        addAction(actions, button(R.string.controls_editor_cancel, v -> showBindings()));
        addAction(actions, button(R.string.controls_editor_key_list, v -> showKeyPicker()));
        content.addView(actions);
        positionPanel();
    }

    private void chooseKey(int key) {
        draft.put(selectedButton, key);
        showBindings();
    }

    private String buttonName(int code) {
        String name = GamepadMapping.keyName(code);
        return name.startsWith("BUTTON_")
                ? activity.getString(R.string.gamepad_button_name, name.substring("BUTTON_".length())) : name;
    }

    private void addAction(LinearLayout row, Button button) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.setMargins(dp(2), dp(4), dp(2), 0);
        row.addView(button, params);
    }

    private int panelWidth() {
        Rect safe = new Rect();
        view.getSafeBounds(safe);
        return Math.max(1, Math.min(dp(400), safe.width() - dp(16)));
    }

    private void positionPanel() {
        if (!isShowing() || view.getWidth() == 0) return;
        Rect safe = new Rect();
        view.getSafeBounds(safe);
        safe.inset(dp(8), dp(8));
        if (safe.width() <= 0 || safe.height() <= 0) return;
        int width = Math.min(dp(400), safe.width());
        panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(safe.height(), View.MeasureSpec.AT_MOST));
        int height = panel.getMeasuredHeight();
        int left = safe.left + (safe.width() - width) / 2;
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) panel.getLayoutParams();
        if (params.width == width && params.height == height && params.leftMargin == left
                && params.topMargin == safe.top) return;
        params.width = width;
        params.height = height;
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.leftMargin = left;
        params.topMargin = safe.top;
        panel.setLayoutParams(params);
    }
}
