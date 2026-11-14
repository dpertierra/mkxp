package io.github.mkxpz.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The editor of the on-screen pad.
 *
 * <p>It shows a bar with the actions (add, delete, key, text, size, restore
 * the defaults, cancel and done) over the pad, which can be dragged while the
 * editor is open.
 */
final class TouchControlsEditor {

    /** What the editor asks the activity to do. */
    interface Callback {
        /** The layout is ready: write it down. */
        void onSave(TouchControlsConfig config);

        /** The changes are not wanted: go back to the layout on disk. */
        void onCancel();

        /** The layout of the user must be forgotten. */
        void onRestoreDefaults();
    }

    /** Smallest and biggest size of a control, as a fraction of the screen. */
    private static final float MIN_SIZE = 0.04f;
    private static final float MAX_SIZE = 0.30f;

    /** One entry to press a key, the letters, the digits, F1-F12 and the rest. */
    private static final int KEY_COUNT = 1 + 26 + 10 + 12 + 11;

    private final Activity activity;
    private final TouchControlsView view;
    private final Callback callback;

    private View toolbar;
    private AlertDialog dialog;
    private boolean waitingForKey;

    TouchControlsEditor(Activity activity, TouchControlsView view, Callback callback) {
        this.activity = activity;
        this.view = view;
        this.callback = callback;
    }

    /** Shows the bar of the editor. */
    void show() {
        if (toolbar == null) {
            toolbar = buildToolbar();
        }
        if (toolbar.getParent() == null) {
            view.addView(toolbar, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP));
        }
        toolbar.setVisibility(View.VISIBLE);
    }

    /** Hides the bar of the editor. */
    void hide() {
        waitingForKey = false;
        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }
        if (toolbar != null) {
            toolbar.setVisibility(View.GONE);
        }
    }

    /** Hides the bar of the editor, without leaving the editor. */
    void hideToolbar() {
        if (toolbar != null) {
            toolbar.setVisibility(View.GONE);
        }
    }

    /**
     * Takes a key that the player pressed.
     *
     * @return true when the key was used to assign a button
     */
    boolean captureKey(KeyEvent event) {
        if (!waitingForKey || event.getAction() != KeyEvent.ACTION_DOWN) {
            return false;
        }
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_DEL
                || code == KeyEvent.KEYCODE_BACK) {
            /* Those keys are not useful for a button. */
            return false;
        }
        waitingForKey = false;
        view.setSelectedKey(code);
        toast("Key: " + KeyEvent.keyCodeToString(code));
        return true;
    }

    /* ----------------------------------------------------------- toolbar */

    private View buildToolbar() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(0xCC101018);

        row.addView(button("Hide", v -> hideToolbar()));
        row.addView(button("+", v -> {
            view.addButton();
            toast("Button added");
        }));
        row.addView(button("-", v -> {
            if (view.getSelectedSpec() == null) {
                toast("Select a button first");
            } else {
                view.deleteSelected();
            }
        }));
        row.addView(button("Key", v -> showKeyDialog()));
        row.addView(button("Text", v -> showLabelDialog()));
        row.addView(button("Size", v -> showSizeDialog()));
        row.addView(button("Def", v -> showRestoreDialog()));
        row.addView(button("Cancel", v -> callback.onCancel()));
        row.addView(button("Done", v -> callback.onSave(view.getConfig())));

        HorizontalScrollView scroll = new HorizontalScrollView(activity);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.addView(row);
        return scroll;
    }

    private Button button(String text, View.OnClickListener listener) {
        Button button = new Button(activity);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        button.setOnClickListener(listener);
        button.setHapticFeedbackEnabled(true);
        return button;
    }

    /* ----------------------------------------------------------- dialogs */

    private void showKeyDialog() {
        if (view.getSelectedSpec() == null) {
            toast("Select a button first");
            return;
        }
        final String[] names = keyNames();
        final int[] codes = keyCodes();
        dialog = new AlertDialog.Builder(activity)
                .setTitle("Key")
                .setItems(names, (dialog, which) -> {
                    if (which == 0) {
                        /* The first entry waits for a key. */
                        waitingForKey = true;
                        toast("Press a key");
                    } else {
                        view.setSelectedKey(codes[which]);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showLabelDialog() {
        if (view.getSelectedSpec() == null) {
            toast("Select a button first");
            return;
        }
        final EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(view.getSelectedSpec().label);
        dialog = new AlertDialog.Builder(activity)
                .setTitle("Text")
                .setView(input)
                .setPositiveButton("OK", (dialog, which) ->
                        view.setSelectedLabel(input.getText().toString()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showSizeDialog() {
        if (view.getSelectedSpec() == null) {
            toast("Select a button first");
            return;
        }
        final SeekBar bar = new SeekBar(activity);
        bar.setMax(100);
        float size = view.getSelectedSpec().size;
        bar.setProgress(Math.round((size - MIN_SIZE) / (MAX_SIZE - MIN_SIZE) * 100f));
        dialog = new AlertDialog.Builder(activity)
                .setTitle("Size")
                .setView(bar)
                .setPositiveButton("OK", (dialog, which) ->
                        view.setSelectedSize(MIN_SIZE + bar.getProgress() / 100f
                                * (MAX_SIZE - MIN_SIZE)))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showRestoreDialog() {
        dialog = new AlertDialog.Builder(activity)
                .setTitle("Restore defaults")
                .setMessage("The user layout for the current orientation is deleted.")
                .setPositiveButton("Restore", (dialog, which) -> callback.onRestoreDefaults())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void toast(String message) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }

    /* --------------------------------------------------------- key table */

    /** Names of the keys the editor offers, the first one waits for a key. */
    private static String[] keyNames() {
        String[] names = new String[KEY_COUNT];
        int i = 0;
        names[i++] = "Press a key...";
        for (char c = 'A'; c <= 'Z'; c++) {
            names[i++] = String.valueOf(c);
        }
        for (char c = '0'; c <= '9'; c++) {
            names[i++] = String.valueOf(c);
        }
        for (int f = 1; f <= 12; f++) {
            names[i++] = "F" + f;
        }
        names[i++] = "Enter";
        names[i++] = "Esc";
        names[i++] = "Space";
        names[i++] = "Shift";
        names[i++] = "Ctrl";
        names[i++] = "Alt";
        names[i++] = "Tab";
        names[i++] = "Up";
        names[i++] = "Down";
        names[i++] = "Left";
        names[i++] = "Right";
        return names;
    }

    /** Key codes of the names of {@link #keyNames()}, in the same order. */
    private static int[] keyCodes() {
        int[] codes = new int[KEY_COUNT];
        int i = 0;
        codes[i++] = 0;
        for (int c = 0; c < 26; c++) {
            codes[i++] = KeyEvent.KEYCODE_A + c;
        }
        for (int c = 0; c < 10; c++) {
            codes[i++] = KeyEvent.KEYCODE_0 + c;
        }
        for (int f = 1; f <= 12; f++) {
            codes[i++] = KeyEvent.KEYCODE_F1 + f - 1;
        }
        codes[i++] = KeyEvent.KEYCODE_ENTER;
        codes[i++] = KeyEvent.KEYCODE_ESCAPE;
        codes[i++] = KeyEvent.KEYCODE_SPACE;
        codes[i++] = KeyEvent.KEYCODE_SHIFT_LEFT;
        codes[i++] = KeyEvent.KEYCODE_CTRL_LEFT;
        codes[i++] = KeyEvent.KEYCODE_ALT_LEFT;
        codes[i++] = KeyEvent.KEYCODE_TAB;
        codes[i++] = KeyEvent.KEYCODE_DPAD_UP;
        codes[i++] = KeyEvent.KEYCODE_DPAD_DOWN;
        codes[i++] = KeyEvent.KEYCODE_DPAD_LEFT;
        codes[i++] = KeyEvent.KEYCODE_DPAD_RIGHT;
        return codes;
    }
}
