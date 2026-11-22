package io.github.mkxpz.android;

import android.app.Activity;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import java.util.function.IntConsumer;

final class ControlsKeyPicker extends ControlsEditorUi {
    private static final int[][] GROUPS = {
            sequence(KeyEvent.KEYCODE_A, 26), sequence(KeyEvent.KEYCODE_0, 10),
            {KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT},
            {KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_SPACE,
                    KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_CTRL_LEFT,
                    KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_TAB,
                    KeyEvent.KEYCODE_F1, KeyEvent.KEYCODE_F2, KeyEvent.KEYCODE_F3,
                    KeyEvent.KEYCODE_F4, KeyEvent.KEYCODE_F5, KeyEvent.KEYCODE_F6,
                    KeyEvent.KEYCODE_F7, KeyEvent.KEYCODE_F8, KeyEvent.KEYCODE_F9,
                    KeyEvent.KEYCODE_F10, KeyEvent.KEYCODE_F11, KeyEvent.KEYCODE_F12}
    };
    private static final int[] TITLES = {R.string.controls_keys_letters,
            R.string.controls_keys_numbers, R.string.controls_keys_arrows, R.string.controls_keys_special};
    private final ControlsModalSheet sheet;
    private final boolean allowGamepad;
    private IntConsumer select;
    private Runnable cancel;
    private Integer defaultKey;
    private boolean waitingForKey;

    ControlsKeyPicker(Activity activity, TouchControlsView host, boolean allowGamepad) {
        super(activity);
        this.allowGamepad = allowGamepad;
        sheet = new ControlsModalSheet(activity, host);
    }

    void show(String title, Integer defaultKey, IntConsumer select, Runnable cancel) {
        this.defaultKey = defaultKey;
        this.select = select;
        this.cancel = cancel;
        waitingForKey = false;
        sheet.title.setText(title);
        sheet.hint.setText(R.string.controls_keys_hint);
        sheet.show(this::rebuild);
    }

    boolean isShowing() {
        return sheet.isShowing();
    }

    void hide() {
        sheet.hide();
        waitingForKey = false;
    }

    void cancel() {
        hide();
        cancel.run();
    }

    boolean dispatchKeyEvent(KeyEvent event) {
        if (sheet.consumeAfterClose(event)) return true;
        if (!isShowing()) return false;
        int code = event.getKeyCode();
        if (GamepadMapping.isGamepadEvent(event) && !allowGamepad) return false;
        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN) return false;
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0;
        if (code == KeyEvent.KEYCODE_BACK || (code == KeyEvent.KEYCODE_ESCAPE && !waitingForKey)) {
            if (down) cancel();
            return true;
        }
        if (waitingForKey && down && code != KeyEvent.KEYCODE_UNKNOWN) {
            choose(code);
            return true;
        }
        if (sheet.overlay.hasFocus()) activity.getWindow().superDispatchKeyEvent(event);
        return true;
    }

    private void choose(int code) {
        hide();
        select.accept(code);
    }

    private void rebuild() {
        sheet.content.removeAllViews();
        sheet.footer.removeAllViews();
        int columns = Math.max(1, sheet.contentWidth() / dp(46));
        boolean wide = sheet.isLandscape() && columns >= 16;
        LinearLayout groups = wide ? row() : sheet.content;
        if (wide) {
            groups.setGravity(Gravity.TOP);
            groups.setPadding(0, 0, 0, 0);
            sheet.content.addView(groups);
        }
        int letterColumns = Math.max(7, (columns - 4) / 2);
        int[] spans = {letterColumns, 3, 1, columns - letterColumns - 4};
        for (int group = 0; group < GROUPS.length; group++) {
            LinearLayout section = column();
            section.addView(sectionTitle(TITLES[group]));
            int count = wide ? spans[group] : columns;
            for (int offset = 0; offset < GROUPS[group].length; offset += count) {
                LinearLayout keys = row();
                keys.setPadding(0, 0, 0, 0);
                for (int slot = 0; slot < count; slot++) {
                    int index = offset + slot;
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(44), 1);
                    params.setMargins(dp(1), dp(1), dp(1), dp(1));
                    if (index >= GROUPS[group].length) {
                        keys.addView(new View(activity), params);
                        continue;
                    }
                    int code = GROUPS[group][index];
                    Button key = button(R.string.controls_editor_key, v -> choose(code));
                    String label = keyName(code);
                    key.setText(label);
                    key.setContentDescription(label);
                    key.setTooltipText(label);
                    key.setMinWidth(0);
                    key.setMinimumWidth(0);
                    key.setMinHeight(0);
                    key.setMinimumHeight(0);
                    key.setPadding(dp(2), 0, dp(2), 0);
                    key.setAutoSizeTextTypeUniformWithConfiguration(9, 13, 1, TypedValue.COMPLEX_UNIT_SP);
                    tileBackground(key);
                    keys.addView(key, params);
                }
                section.addView(keys);
            }
            if (wide) {
                groups.addView(section, new LinearLayout.LayoutParams(0, -2, spans[group]));
            } else {
                sheet.content.addView(section);
            }
        }
        LinearLayout actions = row();
        if (defaultKey != null && !wide) {
            LinearLayout defaults = row();
            addDefaults(defaults);
            sheet.footer.addView(defaults);
        } else if (defaultKey != null) {
            addDefaults(actions);
        }
        Button capture = button(R.string.controls_keys_capture, v -> {
            waitingForKey = !waitingForKey;
            sheet.hint.setText(waitingForKey ? (allowGamepad ? R.string.controls_editor_press_key
                    : R.string.gamepad_press_key) : R.string.controls_keys_hint);
            rebuild();
        });
        tileBackground(capture);
        capture.setActivated(waitingForKey);
        addAction(actions, capture);
        addAction(actions, button(R.string.controls_editor_cancel, v -> cancel()));
        sheet.footer.addView(actions);
    }

    private void addDefaults(LinearLayout row) {
        addAction(row, button(R.string.gamepad_unassigned, v -> choose(KeyEvent.KEYCODE_UNKNOWN)));
        addAction(row, button(R.string.gamepad_default, v -> choose(defaultKey)));
    }

    private String keyName(int code) {
        int special = -1;
        switch (code) {
            case KeyEvent.KEYCODE_ENTER: special = 0; break;
            case KeyEvent.KEYCODE_ESCAPE: special = 1; break;
            case KeyEvent.KEYCODE_SPACE: special = 2; break;
            case KeyEvent.KEYCODE_SHIFT_LEFT: special = 3; break;
            case KeyEvent.KEYCODE_CTRL_LEFT: special = 4; break;
            case KeyEvent.KEYCODE_ALT_LEFT: special = 5; break;
            case KeyEvent.KEYCODE_TAB: return "Tab";
            case KeyEvent.KEYCODE_DPAD_UP: return "↑";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "↓";
            case KeyEvent.KEYCODE_DPAD_LEFT: return "←";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "→";
        }
        return special >= 0 ? activity.getResources().getStringArray(
                R.array.controls_editor_special_keys)[special] : GamepadMapping.keyName(code);
    }

    private static int[] sequence(int first, int count) {
        int[] codes = new int[count];
        for (int index = 0; index < count; index++) codes[index] = first + index;
        return codes;
    }
}
