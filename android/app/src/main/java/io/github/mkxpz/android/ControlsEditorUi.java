package io.github.mkxpz.android;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.function.IntConsumer;

/** Shared native components for the touch and gamepad editors. */
class ControlsEditorUi {
    static final int INK = 0xFFF4F4F6;
    static final int MUTED = 0xFFC6CBD5;
    static final int ACCENT = 0xFFFFC850;
    static final int SURFACE = 0xF5191D26;
    private static final int KEY_COUNT = 26 + 10 + 12 + 11;

    final Activity activity;

    ControlsEditorUi(Activity activity) {
        this.activity = activity;
    }

    LinearLayout column() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    LinearLayout row() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    TextView text(int resource, int sp, int color) {
        TextView text = new TextView(activity);
        text.setText(resource);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        text.setTextColor(color);
        return text;
    }

    Button button(int resource, View.OnClickListener listener) {
        Button button = new Button(activity);
        button.setText(resource);
        button.setAllCaps(false);
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        button.setTextColor(new ColorStateList(new int[][]{
                new int[]{android.R.attr.state_focused}, new int[]{}}, new int[]{ACCENT, INK}));
        button.setMinWidth(dp(40));
        button.setMinimumWidth(dp(40));
        button.setMinHeight(dp(40));
        button.setMinimumHeight(dp(40));
        button.setPadding(dp(8), dp(4), dp(8), dp(4));
        button.setBackgroundTintList(null);
        button.setStateListAnimator(null);
        GradientDrawable face = background(0xFF303744, dp(10));
        face.setStroke(dp(1), 0xFF7D8798);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x55FFFFFF), face, null));
        button.setOnClickListener(listener);
        return button;
    }

    Button primaryButton(int resource, View.OnClickListener listener) {
        Button button = button(resource, listener);
        button.setTextColor(0xFF191D26);
        button.setTypeface(null, Typeface.BOLD);
        GradientDrawable face = background(ACCENT, dp(10));
        GradientDrawable focused = background(ACCENT, dp(10));
        focused.setStroke(dp(2), INK);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{}, face);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x55000000), states, null));
        return button;
    }

    ImageButton iconButton(int icon, int description, View.OnClickListener listener) {
        ImageButton button = new ImageButton(activity);
        button.setImageResource(icon);
        button.setImageTintList(new ColorStateList(new int[][]{
                new int[]{android.R.attr.state_focused}, new int[]{}}, new int[]{ACCENT, INK}));
        button.setContentDescription(activity.getString(description));
        button.setTooltipText(activity.getString(description));
        button.setPadding(dp(10), dp(10), dp(10), dp(10));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x55FFFFFF),
                background(SURFACE, dp(8)), null));
        button.setOnClickListener(listener);
        return button;
    }

    GradientDrawable background(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    LinearLayout keyGrid(int width, IntConsumer select) {
        LinearLayout grid = column();
        int[] codes = keyCodes();
        String[] names = new String[KEY_COUNT];
        int index = 0;
        for (char c = 'A'; c <= 'Z'; c++) names[index++] = String.valueOf(c);
        for (char c = '0'; c <= '9'; c++) names[index++] = String.valueOf(c);
        for (int f = 1; f <= 12; f++) names[index++] = "F" + f;
        for (String name : activity.getResources().getStringArray(R.array.controls_editor_special_keys)) {
            names[index++] = name;
        }
        int columns = Math.max(1, (width - dp(16)) / dp(88));
        for (int i = 0; i < codes.length; i += columns) {
            LinearLayout keys = row();
            for (int j = i; j < Math.min(i + columns, codes.length); j++) {
                final int code = codes[j];
                Button key = button(R.string.controls_editor_key, v -> select.accept(code));
                key.setText(names[j]);
                key.setTooltipText(names[j]);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
                params.setMargins(dp(2), dp(2), dp(2), dp(2));
                keys.addView(key, params);
            }
            grid.addView(keys);
        }
        return grid;
    }

    private static int[] keyCodes() {
        int[] codes = new int[KEY_COUNT];
        int i = 0;
        for (int c = 0; c < 26; c++) codes[i++] = KeyEvent.KEYCODE_A + c;
        for (int c = 0; c < 10; c++) codes[i++] = KeyEvent.KEYCODE_0 + c;
        for (int f = 1; f <= 12; f++) codes[i++] = KeyEvent.KEYCODE_F1 + f - 1;
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
