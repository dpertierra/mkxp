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
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Shared native components for the touch and gamepad editors. */
class ControlsEditorUi {
    static final int INK = 0xFFF4F4F6;
    static final int MUTED = 0xFFC6CBD5;
    static final int ACCENT = 0xFFFFC850;
    static final int SURFACE = 0xF5191D26;

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
        // Mixed text/button baselines can move a button below the row's measured bounds.
        layout.setBaselineAligned(false);
        layout.setPadding(0, dp(2), 0, dp(2));
        return layout;
    }

    ScrollView scrollPanel() {
        ScrollView panel = new ScrollView(activity);
        panel.setFillViewport(false);
        panel.setPadding(0, dp(4), 0, dp(4));
        panel.setClipToPadding(false);
        panel.setBackground(background(SURFACE, dp(16)));
        panel.setElevation(dp(6));
        panel.setClickable(true);
        return panel;
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
        button.setBackgroundTintList(null);
        button.setStateListAnimator(null);
        GradientDrawable face = background(0xFF303744, dp(10));
        face.setStroke(dp(1), 0xFF7D8798);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x55FFFFFF), face, null));
        fitButton(button);
        button.setOnClickListener(listener);
        return button;
    }

    static void fitButton(Button button) {
        float density = button.getResources().getDisplayMetrics().density;
        int height = Math.round(48 * density);
        int width = Math.round(40 * density);
        button.setMinWidth(width);
        button.setMinimumWidth(width);
        button.setMinHeight(height);
        button.setMinimumHeight(height);
        button.setIncludeFontPadding(true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(Math.round(8 * density), Math.round(6 * density),
                Math.round(8 * density), Math.round(6 * density));
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

    void addAction(LinearLayout row, Button button) {
        button.setSingleLine(false);
        button.setMaxLines(2);
        button.setEllipsize(null);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.setMargins(dp(2), dp(2), dp(2), dp(2));
        row.addView(button, params);
    }

    TextView sectionTitle(int resource) {
        TextView title = text(resource, 12, MUTED);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(dp(2), dp(4), dp(2), dp(4));
        return title;
    }

    void tileBackground(View tile) {
        GradientDrawable normal = background(0xFF303744, dp(10));
        GradientDrawable highlighted = background(0xFF303744, dp(10));
        highlighted.setStroke(dp(2), ACCENT);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_activated}, highlighted);
        states.addState(new int[]{android.R.attr.state_focused}, highlighted);
        states.addState(new int[]{}, normal);
        tile.setBackground(new RippleDrawable(ColorStateList.valueOf(0x55FFFFFF), states, null));
    }
}
