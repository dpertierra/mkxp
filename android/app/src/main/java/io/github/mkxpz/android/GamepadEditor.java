package io.github.mkxpz.android;

import android.app.Activity;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

final class GamepadEditor extends ControlsEditorUi {
    private static final int[][] GROUPS = {
            {KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B,
                    KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y},
            {KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1,
                    KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2},
            {KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_BUTTON_THUMBR},
            {KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT},
            {KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT}
    };
    private static final int[] TITLES = {R.string.gamepad_section_buttons,
            R.string.gamepad_section_triggers, R.string.gamepad_section_sticks,
            R.string.gamepad_section_menu, R.string.gamepad_section_dpad};
    private final TouchControlsView view;
    private final GamepadMapping mapping;
    private final Runnable onClose;
    private ControlsModalSheet sheet;
    private ControlsKeyPicker keyPicker;
    private Map<Integer, Integer> draft;
    private final Map<Integer, View> buttonTiles = new HashMap<>();
    private int identifiedButton = KeyEvent.KEYCODE_UNKNOWN;
    private boolean confirmingRestore;
    private boolean saveFailed;

    GamepadEditor(Activity activity, TouchControlsView view, GamepadMapping mapping, Runnable onClose) {
        super(activity);
        this.view = view;
        this.mapping = mapping;
        this.onClose = onClose;
    }

    boolean isShowing() {
        return sheet != null && sheet.isShowing();
    }

    void show() {
        mapping.releaseAll();
        view.releaseAllKeys();
        view.dismissQuickMenu();
        if (sheet == null) {
            sheet = new ControlsModalSheet(activity, view);
            keyPicker = new ControlsKeyPicker(activity, view, false);
        }
        draft = mapping.copyBindings();
        identifiedButton = KeyEvent.KEYCODE_UNKNOWN;
        confirmingRestore = false;
        saveFailed = false;
        sheet.show(this::showBindings);
    }

    void hide() {
        boolean wasShowing = isShowing();
        if (sheet != null) {
            keyPicker.hide();
            sheet.hide();
        }
        draft = null;
        buttonTiles.clear();
        if (wasShowing) onClose.run();
    }

    void onBackPressed() {
        if (keyPicker.isShowing()) keyPicker.cancel();
        else if (confirmingRestore) {
            confirmingRestore = false;
            showBindings();
        } else hide();
    }

    boolean dispatchKeyEvent(KeyEvent event) {
        boolean releasedAfterClose = sheet != null && sheet.consumeAfterClose(event);
        boolean picked = keyPicker != null && keyPicker.dispatchKeyEvent(event);
        if (releasedAfterClose || picked) return true;
        if (sheet == null) return false;
        if (!isShowing()) return false;
        int code = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0;
        if (GamepadMapping.isGamepadEvent(event) && GamepadMapping.isButton(code)) {
            if (down && GamepadMapping.defaultBindings().containsKey(code)) {
                keyPicker.hide();
                sheet.overlay.requestFocus();
                identifiedButton = code;
                if (confirmingRestore) {
                    confirmingRestore = false;
                    showBindings();
                }
                identifyButton();
            }
            return true;
        }
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (down) onBackPressed();
            return true;
        }
        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN) return false;
        if (sheet.overlay.hasFocus()) activity.getWindow().superDispatchKeyEvent(event);
        return true;
    }

    private void showBindings() {
        if (!isShowing()) return;
        if (confirmingRestore) {
            showRestoreConfirmation();
            return;
        }
        sheet.title.setText(R.string.gamepad_title);
        sheet.hint.setText(saveFailed ? R.string.gamepad_save_error : R.string.gamepad_hint);
        sheet.hint.setAccessibilityLiveRegion(saveFailed ? View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE
                : View.ACCESSIBILITY_LIVE_REGION_NONE);
        sheet.content.removeAllViews();
        sheet.footer.removeAllViews();
        buttonTiles.clear();
        int columns = sheet.isLandscape() ? Math.max(4, sheet.contentWidth() / dp(80))
                : sheet.contentWidth() >= dp(336) ? 4 : 2;
        int used = columns;
        LinearLayout band = null;
        for (int group = 0; group < GROUPS.length; group++) {
            int span = Math.min(columns, GROUPS[group].length);
            if (used + span > columns) {
                band = row();
                band.setGravity(Gravity.TOP);
                band.setPadding(0, 0, 0, 0);
                sheet.content.addView(band);
                used = 0;
            }
            LinearLayout section = column();
            section.addView(sectionTitle(TITLES[group]));
            for (int offset = 0; offset < GROUPS[group].length; offset += span) {
                LinearLayout tiles = row();
                tiles.setPadding(0, 0, 0, 0);
                for (int slot = offset; slot < Math.min(offset + span, GROUPS[group].length); slot++) {
                    int code = GROUPS[group][slot];
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
                    params.setMargins(dp(2), dp(2), dp(2), dp(2));
                    tiles.addView(bindingTile(code), params);
                }
                section.addView(tiles);
            }
            band.addView(section, new LinearLayout.LayoutParams(0, -2, span));
            used += span;
        }
        LinearLayout actions = row();
        addAction(actions, button(R.string.gamepad_restore_all, v -> showRestoreConfirmation()));
        addAction(actions, button(R.string.controls_editor_cancel, v -> hide()));
        addAction(actions, primaryButton(R.string.controls_editor_save, v -> {
            if (mapping.save(draft)) hide();
            else {
                saveFailed = true;
                showBindings();
            }
        }));
        sheet.footer.addView(actions);
        identifyButton();
    }

    private View bindingTile(int code) {
        int key = draft.get(code);
        boolean changed = key != GamepadMapping.defaultBindings().get(code);
        LinearLayout tile = column();
        tile.setGravity(Gravity.CENTER);
        tile.setMinimumHeight(dp(sheet.isLandscape() ? 64 : 80));
        tile.setPadding(dp(4), dp(6), dp(4), dp(6));
        tile.setFocusable(true);
        tileBackground(tile);
        TextView label = text(R.string.gamepad_title, 19, changed ? ACCENT : INK);
        label.setText(changed ? "• " + buttonName(code) : buttonName(code));
        label.setTypeface(null, Typeface.BOLD);
        label.setGravity(Gravity.CENTER);
        tile.addView(label, new LinearLayout.LayoutParams(-1, -2));
        TextView assignment = text(R.string.gamepad_title, 12, INK);
        assignment.setText(keyLabel(key));
        assignment.setGravity(Gravity.CENTER);
        tile.addView(assignment, new LinearLayout.LayoutParams(-1, -2));
        String description = activity.getString(R.string.gamepad_binding, buttonName(code),
                key == KeyEvent.KEYCODE_UNKNOWN ? activity.getString(R.string.gamepad_unassigned) : keyLabel(key));
        if (changed) description += ", " + activity.getString(R.string.gamepad_changed);
        tile.setContentDescription(description);
        tile.setOnClickListener(v -> keyPicker.show(
                activity.getString(R.string.controls_keys_for, buttonName(code)),
                GamepadMapping.defaultBindings().get(code), chosen -> {
                    draft.put(code, chosen);
                    showBindings();
                    sheet.overlay.requestFocus();
                }, () -> sheet.overlay.requestFocus()));
        buttonTiles.put(code, tile);
        return tile;
    }

    private void showRestoreConfirmation() {
        confirmingRestore = true;
        sheet.title.setText(R.string.gamepad_restore_title);
        sheet.hint.setText(R.string.gamepad_restore_message);
        sheet.content.removeAllViews();
        sheet.footer.removeAllViews();
        buttonTiles.clear();
        LinearLayout actions = row();
        addAction(actions, button(R.string.controls_editor_cancel, v -> {
            confirmingRestore = false;
            showBindings();
        }));
        addAction(actions, primaryButton(R.string.gamepad_restore_all, v -> {
            draft = new LinkedHashMap<>(GamepadMapping.defaultBindings());
            confirmingRestore = false;
            showBindings();
        }));
        sheet.footer.addView(actions);
    }

    private String buttonName(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_BUTTON_THUMBL: return "L3";
            case KeyEvent.KEYCODE_BUTTON_THUMBR: return "R3";
            case KeyEvent.KEYCODE_BUTTON_START: return "Start";
            case KeyEvent.KEYCODE_BUTTON_SELECT: return "Select";
            case KeyEvent.KEYCODE_DPAD_UP: return "↑";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "↓";
            case KeyEvent.KEYCODE_DPAD_LEFT: return "←";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "→";
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
        for (Map.Entry<Integer, View> entry : buttonTiles.entrySet()) {
            entry.getValue().setActivated(entry.getKey() == identifiedButton);
        }
        View tile = buttonTiles.get(identifiedButton);
        if (tile == null) return;
        tile.post(() -> {
            if (!isShowing() || buttonTiles.get(identifiedButton) != tile) return;
            tile.requestRectangleOnScreen(new Rect(0, 0, tile.getWidth(), tile.getHeight()), false);
        });
    }
}
