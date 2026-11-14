package io.github.mkxpz.android;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.HashMap;
import java.util.LinkedHashMap;
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
    private final Map<Integer, View> buttonRows = new HashMap<>();
    private boolean waitingForKey;
    private int selectedButton = KeyEvent.KEYCODE_UNKNOWN;
    private int identifiedButton = KeyEvent.KEYCODE_UNKNOWN;
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
            panel = scrollPanel();
            content = column();
            content.setPadding(dp(8), dp(4), dp(8), dp(8));
            panel.addView(content, new ScrollView.LayoutParams(-1, -2));
            overlay.addView(panel, new FrameLayout.LayoutParams(1, 1));
            view.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        }
        draft = mapping.copyBindings();
        identifiedButton = KeyEvent.KEYCODE_UNKNOWN;
        overlay.setVisibility(View.VISIBLE);
        showBindings();
        overlay.requestFocus();
        view.requestApplyInsets();
    }

    void hide() {
        boolean wasShowing = isShowing();
        if (overlay != null) overlay.setVisibility(View.GONE);
        draft = null;
        waitingForKey = false;
        selectedButton = KeyEvent.KEYCODE_UNKNOWN;
        buttonRows.clear();
        if (wasShowing) onClose.run();
    }

    void onBackPressed() {
        if (selectedButton != KeyEvent.KEYCODE_UNKNOWN || buttonRows.isEmpty()) showBindings();
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
            if (down && GamepadMapping.defaultBindings().containsKey(code)) {
                identifiedButton = code;
                if (buttonRows.isEmpty()) showBindings();
                else identifyButton();
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
        buttonRows.clear();
        content.removeAllViews();
        TextView heading = text(title, 16, INK);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setPadding(dp(4), dp(8), dp(4), dp(8));
        content.addView(heading);
        panel.scrollTo(0, 0);
    }

    private void showBindings() {
        waitingForKey = false;
        selectedButton = KeyEvent.KEYCODE_UNKNOWN;
        heading(R.string.gamepad_title);
        TextView hint = text(R.string.gamepad_hint, 14, MUTED);
        hint.setSingleLine(true);
        hint.setEllipsize(TextUtils.TruncateAt.END);
        hint.setTooltipText(hint.getText());
        content.addView(hint);
        for (Map.Entry<Integer, Integer> entry : GamepadMapping.defaultBindings().entrySet()) {
            int buttonCode = entry.getKey();
            int key = draft.get(buttonCode);
            boolean changed = key != entry.getValue();
            LinearLayout row = row();
            row.setMinimumHeight(dp(48));
            row.setPadding(dp(8), dp(6), dp(8), dp(6));
            row.setFocusable(true);
            GradientDrawable normal = background(0xFF303744, dp(10));
            GradientDrawable highlighted = background(0xFF303744, dp(10));
            highlighted.setStroke(dp(2), ACCENT);
            StateListDrawable states = new StateListDrawable();
            states.addState(new int[]{android.R.attr.state_activated}, highlighted);
            states.addState(new int[]{android.R.attr.state_focused}, highlighted);
            states.addState(new int[]{}, normal);
            row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x55FFFFFF), states, null));
            TextView label = text(R.string.gamepad_title, 14, changed ? ACCENT : INK);
            label.setText(changed ? "• " + buttonName(buttonCode) : buttonName(buttonCode));
            row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            TextView assignment = text(R.string.gamepad_title, 14, INK);
            assignment.setText(keyLabel(key));
            assignment.setGravity(Gravity.RIGHT);
            row.addView(assignment, new LinearLayout.LayoutParams(0, -2, 2));
            String description = activity.getString(R.string.gamepad_binding,
                    buttonName(buttonCode), key == KeyEvent.KEYCODE_UNKNOWN
                            ? activity.getString(R.string.gamepad_unassigned) : keyLabel(key));
            if (changed) description += ", " + activity.getString(R.string.gamepad_changed);
            row.setContentDescription(description);
            row.setOnClickListener(v -> {
                selectedButton = buttonCode;
                showKeyPicker();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = dp(4);
            content.addView(row, params);
            buttonRows.put(buttonCode, row);
        }
        LinearLayout actions = row();
        addAction(actions, button(R.string.gamepad_restore_all, v -> showRestoreConfirmation()));
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
        identifyButton();
    }

    private void showRestoreConfirmation() {
        waitingForKey = false;
        selectedButton = KeyEvent.KEYCODE_UNKNOWN;
        heading(R.string.gamepad_restore_title);
        content.addView(text(R.string.gamepad_restore_message, 14, MUTED));
        LinearLayout actions = row();
        addAction(actions, button(R.string.controls_editor_cancel, v -> showBindings()));
        addAction(actions, primaryButton(R.string.gamepad_restore_all, v -> {
            draft = new LinkedHashMap<>(GamepadMapping.defaultBindings());
            showBindings();
        }));
        content.addView(actions);
        positionPanel();
    }

    private void showKeyPicker() {
        waitingForKey = false;
        heading(R.string.controls_editor_key);
        TextView hint = text(R.string.gamepad_choose_key, 14, MUTED);
        hint.setText(activity.getString(R.string.gamepad_choose_key, buttonName(selectedButton)));
        content.addView(hint);
        LinearLayout actions = row();
        addAction(actions, button(R.string.controls_editor_cancel, v -> showBindings()));
        addAction(actions, button(R.string.controls_editor_physical_key, v -> showKeyCapture()));
        content.addView(actions);
        LinearLayout defaults = row();
        addAction(defaults, button(R.string.gamepad_unassigned, v -> chooseKey(KeyEvent.KEYCODE_UNKNOWN)));
        addAction(defaults, button(R.string.gamepad_default, v ->
                chooseKey(GamepadMapping.defaultBindings().get(selectedButton))));
        content.addView(defaults);
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
        switch (code) {
            case KeyEvent.KEYCODE_BUTTON_THUMBL: return "L3";
            case KeyEvent.KEYCODE_BUTTON_THUMBR: return "R3";
            case KeyEvent.KEYCODE_BUTTON_START: return "Start";
            case KeyEvent.KEYCODE_BUTTON_SELECT: return "Select";
            case KeyEvent.KEYCODE_DPAD_UP: return "D-pad ↑";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "D-pad ↓";
            case KeyEvent.KEYCODE_DPAD_LEFT: return "D-pad ←";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "D-pad →";
        }
        String name = GamepadMapping.keyName(code);
        return name.startsWith("BUTTON_") ? name.substring("BUTTON_".length()) : name;
    }

    private String keyLabel(int key) {
        switch (key) {
            case KeyEvent.KEYCODE_UNKNOWN: return "—";
            case KeyEvent.KEYCODE_C: return activity.getString(R.string.gamepad_key_c);
            case KeyEvent.KEYCODE_X: return activity.getString(R.string.gamepad_key_x);
            case KeyEvent.KEYCODE_Z: return activity.getString(R.string.gamepad_key_z);
            case KeyEvent.KEYCODE_ESCAPE: return activity.getString(R.string.gamepad_key_escape);
            case KeyEvent.KEYCODE_DPAD_UP: return "↑";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "↓";
            case KeyEvent.KEYCODE_DPAD_LEFT: return "←";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "→";
            default: return GamepadMapping.keyName(key);
        }
    }

    private void identifyButton() {
        for (Map.Entry<Integer, View> entry : buttonRows.entrySet()) {
            entry.getValue().setActivated(entry.getKey() == identifiedButton);
        }
        View row = buttonRows.get(identifiedButton);
        if (row == null) return;
        panel.post(() -> {
            if (!isShowing() || buttonRows.get(identifiedButton) != row) return;
            panel.smoothScrollTo(0, row.getTop());
        });
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
        view.constrainPanelToUpperBand(safe);
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
