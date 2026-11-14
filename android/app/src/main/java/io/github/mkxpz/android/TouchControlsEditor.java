package io.github.mkxpz.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

/** Inset-aware, collapsible editor over the pad; changes stay in memory until Save. */
final class TouchControlsEditor {

    interface Callback {
        void onSave(TouchControlsConfig config);
        void onCancel();
        void onRestoreDefaults();
        void onToggleFps();
    }

    private static final float MIN_SIZE = 0.04f;
    private static final float MAX_SIZE = 0.30f;
    private static final int KEY_COUNT = 1 + 26 + 10 + 12 + 11;
    private static final int INK = 0xFFF4F4F6;
    private static final int MUTED = 0xFFC6CBD5;
    private static final int ACCENT = 0xFFFFC850;
    private static final int SURFACE = 0xF5191D26;

    private final Activity activity;
    private final TouchControlsView view;
    private final Callback callback;
    private LinearLayout panel;
    private LinearLayout selectionActions;
    private LinearLayout footer;
    private ScrollView body;
    private TextView hint;
    private TextView selection;
    private TextView sizeLabel;
    private SeekBar sizeBar;
    private Button foldButton;
    private Button keyButton;
    private Button nameButton;
    private Button deleteButton;
    private CheckBox fpsToggle;
    private boolean fpsVisible;
    private AlertDialog dialog;
    private boolean waitingForKey;
    private TouchControlsConfig.Spec keyTarget;
    private int capturedKey = KeyEvent.KEYCODE_UNKNOWN;
    private boolean collapsed;
    private int dock;

    TouchControlsEditor(Activity activity, TouchControlsView view, Callback callback) {
        this.activity = activity;
        this.view = view;
        this.callback = callback;
        view.setEditorListener(this::refresh);
        view.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> positionPanel(true));
    }

    void show() {
        if (panel == null) {
            buildPanel();
            view.addView(panel, new FrameLayout.LayoutParams(1, 1));
        }
        collapsed = false;
        dock = 0;
        panel.setVisibility(View.VISIBLE);
        refresh();
        view.requestApplyInsets();
    }

    void hide() {
        waitingForKey = false;
        keyTarget = null;
        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }
        if (panel != null) {
            panel.setVisibility(View.GONE);
        }
    }

    void setFpsVisible(boolean visible) {
        fpsVisible = visible;
        if (fpsToggle != null) fpsToggle.setChecked(visible);
    }

    /** Consume both halves of a captured key, including repeats, before SDL sees it. */
    boolean captureKey(KeyEvent event) {
        if (capturedKey != KeyEvent.KEYCODE_UNKNOWN && event.getKeyCode() == capturedKey) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                capturedKey = KeyEvent.KEYCODE_UNKNOWN;
            }
            return true;
        }
        if (!waitingForKey || event.getAction() != KeyEvent.ACTION_DOWN
                || event.getKeyCode() == KeyEvent.KEYCODE_BACK
                || event.getKeyCode() == KeyEvent.KEYCODE_UNKNOWN) {
            return false;
        }
        capturedKey = event.getKeyCode();
        if (keyTarget == view.getSelectedSpec()) {
            view.setSelectedKey(capturedKey);
        }
        dialog.dismiss();
        return true;
    }

    private void buildPanel() {
        panel = column();
        panel.setPadding(dp(10), dp(6), dp(10), dp(10));
        panel.setBackground(background(SURFACE, dp(16)));
        panel.setElevation(dp(6));
        // Empty panel space must not become a drag or a touch on the game.
        panel.setClickable(true);

        LinearLayout header = row();
        TextView title = text(R.string.controls_editor_title, 16, INK);
        title.setTypeface(null, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button move = button(R.string.controls_editor_move, v -> {
            dock = (dock + 1) % 6;
            positionPanel(false);
        });
        LinearLayout.LayoutParams moveParams = new LinearLayout.LayoutParams(0, -2, 1);
        moveParams.setMargins(dp(6), 0, dp(6), 0);
        header.addView(move, moveParams);
        foldButton = button(R.string.controls_editor_collapse, v -> {
            collapsed = !collapsed;
            refresh();
        });
        header.addView(foldButton, new LinearLayout.LayoutParams(0, -2, 1));
        panel.addView(header);

        LinearLayout content = column();
        hint = text(R.string.controls_editor_hint, 14, MUTED);
        hint.setPadding(0, dp(8), 0, dp(12));
        content.addView(hint);
        selectionActions = column();
        selection = text(R.string.controls_editor_title, 15, ACCENT);
        selection.setTypeface(null, Typeface.BOLD);
        selection.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        selection.setPadding(0, dp(8), 0, dp(6));
        selectionActions.addView(selection);
        keyButton = button(R.string.controls_editor_key, v -> showKeyDialog());
        nameButton = button(R.string.controls_editor_name, v -> showLabelDialog());
        selectionActions.addView(pair(keyButton, nameButton));

        LinearLayout sizeRow = row();
        sizeLabel = text(R.string.controls_editor_size, 14, INK);
        sizeRow.addView(sizeLabel);
        sizeBar = new SeekBar(activity);
        sizeBar.setMax(100);
        sizeBar.setMinHeight(dp(48));
        sizeBar.setThumbTintList(ColorStateList.valueOf(ACCENT));
        sizeBar.setProgressTintList(ColorStateList.valueOf(ACCENT));
        sizeBar.setProgressBackgroundTintList(ColorStateList.valueOf(0xFF727B8C));
        sizeBar.setContentDescription(activity.getString(R.string.controls_editor_size));
        sizeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) {
                    view.setSelectedSize(MIN_SIZE + progress / 100f * (MAX_SIZE - MIN_SIZE));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        sizeRow.addView(sizeBar, new LinearLayout.LayoutParams(0, -2, 1));
        selectionActions.addView(sizeRow);
        deleteButton = button(R.string.controls_editor_delete, v -> view.deleteSelected());
        deleteButton.setTextColor(0xFFFFB5B0);
        selectionActions.addView(deleteButton, new LinearLayout.LayoutParams(-1, -2));
        content.addView(selectionActions);

        TextView layoutTitle = text(R.string.controls_editor_layout, 14, MUTED);
        layoutTitle.setPadding(0, dp(12), 0, dp(6));
        content.addView(layoutTitle);
        content.addView(pair(button(R.string.controls_editor_add, v -> view.addButton()),
                button(R.string.controls_editor_restore, v -> showRestoreDialog())));
        fpsToggle = new CheckBox(activity);
        fpsToggle.setText(R.string.controls_show_fps);
        fpsToggle.setTextColor(INK);
        fpsToggle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        fpsToggle.setButtonTintList(new ColorStateList(new int[][]{
                new int[]{android.R.attr.state_checked}, new int[]{}}, new int[]{ACCENT, MUTED}));
        fpsToggle.setMinHeight(dp(48));
        fpsToggle.setChecked(fpsVisible);
        fpsToggle.setOnClickListener(v -> {
            // The engine's next title, including physical F2 presses, sets the checked state.
            fpsToggle.setChecked(fpsVisible);
            callback.onToggleFps();
        });
        content.addView(fpsToggle);
        body = new ScrollView(activity);
        body.setFillViewport(false);
        body.addView(content);
        panel.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));

        Button save = button(R.string.controls_editor_save, v -> callback.onSave(view.getConfig()));
        save.setTextColor(0xFF191D26);
        save.setTypeface(null, Typeface.BOLD);
        save.setBackground(new RippleDrawable(ColorStateList.valueOf(0x55000000),
                background(ACCENT, dp(10)), null));
        footer = pair(button(R.string.controls_editor_cancel, v -> callback.onCancel()), save);
        footer.setPadding(0, dp(8), 0, 0);
        panel.addView(footer);
    }

    private void refresh() {
        if (panel == null || panel.getVisibility() != View.VISIBLE) {
            return;
        }
        TouchControlsConfig.Spec spec = view.getSelectedSpec();
        hint.setVisibility(spec == null ? View.VISIBLE : View.GONE);
        selectionActions.setVisibility(spec == null ? View.GONE : View.VISIBLE);
        if (spec != null) {
            boolean dpad = spec == view.getConfig().dpad;
            selection.setText(dpad ? activity.getString(R.string.controls_editor_dpad)
                    : activity.getString(R.string.controls_editor_selected, spec.label, spec.keyName()));
            keyButton.setVisibility(dpad ? View.GONE : View.VISIBLE);
            nameButton.setVisibility(dpad ? View.GONE : View.VISIBLE);
            deleteButton.setVisibility(dpad ? View.GONE : View.VISIBLE);
            sizeBar.setProgress(Math.round((spec.size - MIN_SIZE) / (MAX_SIZE - MIN_SIZE) * 100));
            sizeLabel.setText(activity.getString(R.string.controls_editor_size_value,
                    Math.round(spec.size * 100)));
        }
        body.setVisibility(collapsed ? View.GONE : View.VISIBLE);
        footer.setVisibility(collapsed ? View.GONE : View.VISIBLE);
        foldButton.setText(collapsed ? R.string.controls_editor_expand : R.string.controls_editor_collapse);
        positionPanel(true);
    }

    /** Prefer the upper letterbox in portrait and the upper centre in landscape. */
    private void positionPanel(boolean avoidControls) {
        if (panel == null || panel.getVisibility() != View.VISIBLE || view.getWidth() == 0) {
            return;
        }
        Rect safe = new Rect();
        view.getSafeBounds(safe);
        safe.inset(dp(8), dp(8));
        if (safe.width() <= 0 || safe.height() <= 0) {
            return;
        }
        int width = Math.min(dp(400), safe.width());
        int innerWidth = View.MeasureSpec.makeMeasureSpec(width - panel.getPaddingLeft()
                - panel.getPaddingRight(), View.MeasureSpec.EXACTLY);
        int unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        int desired = panel.getPaddingTop() + panel.getPaddingBottom();
        for (int i = 0; i < panel.getChildCount(); i++) {
            View child = panel.getChildAt(i);
            if (child.getVisibility() != View.GONE) {
                child.measure(innerWidth, unspecified);
                desired += child.getMeasuredHeight();
            }
        }
        int available = safe.height();
        if (view.getHeight() > view.getWidth() && (dock == 0 || dock == 2 || dock == 3)) {
            int gameTop = Math.round((view.getHeight() - view.getWidth() * 0.75f) / 2f);
            int upperBand = gameTop - safe.top - dp(8);
            if (upperBand >= dp(200)) {
                available = Math.min(available, upperBand);
            }
        }
        int height = Math.min(desired, available);
        Rect bounds = panelBounds(safe, width, height, dock);
        if (avoidControls) {
            // ponytail: six anchors cover phone layouts; free dragging can replace them if needed.
            float best = view.editorOverlap(bounds);
            for (int i = 0; i < 6 && best > 0; i++) {
                Rect candidate = panelBounds(safe, width, height, i);
                float score = view.editorOverlap(candidate);
                if (score < best) {
                    dock = i;
                    bounds = candidate;
                    best = score;
                }
            }
        }
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) panel.getLayoutParams();
        if (params.width != width || params.height != height || params.leftMargin != bounds.left
                || params.topMargin != bounds.top) {
            params.width = width;
            params.height = height;
            params.gravity = Gravity.TOP | Gravity.LEFT;
            params.leftMargin = bounds.left;
            params.topMargin = bounds.top;
            panel.setLayoutParams(params);
        }
    }

    private Rect panelBounds(Rect safe, int width, int height, int anchor) {
        int left = safe.left + (safe.width() - width) / 2;
        if (anchor == 2 || anchor == 4) left = safe.left;
        if (anchor == 3 || anchor == 5) left = safe.right - width;
        int top = anchor == 1 || anchor >= 4 ? safe.bottom - height : safe.top;
        return new Rect(left, top, left + width, top + height);
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private LinearLayout pair(View first, View second) {
        LinearLayout row = row();
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, -2, 1);
        a.rightMargin = dp(6);
        row.addView(first, a);
        row.addView(second, new LinearLayout.LayoutParams(0, -2, 1));
        return row;
    }

    private TextView text(int resource, int sp, int color) {
        TextView text = new TextView(activity);
        text.setText(resource);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        text.setTextColor(color);
        return text;
    }

    private Button button(int resource, View.OnClickListener listener) {
        Button button = new Button(activity);
        button.setText(resource);
        button.setAllCaps(false);
        button.setSingleLine(false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        button.setTextColor(new ColorStateList(new int[][]{
                new int[]{android.R.attr.state_focused}, new int[]{}}, new int[]{ACCENT, INK}));
        button.setMinWidth(dp(48));
        button.setMinimumWidth(dp(48));
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        button.setPadding(dp(10), dp(6), dp(10), dp(6));
        GradientDrawable face = background(0xFF303744, dp(10));
        face.setStroke(dp(1), 0xFF7D8798);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x55FFFFFF), face, null));
        button.setOnClickListener(listener);
        return button;
    }

    private GradientDrawable background(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private void showKeyDialog() {
        if (view.getSelectedSpec() == null) return;
        final int[] codes = keyCodes();
        dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.controls_editor_key)
                .setItems(keyNames(), (d, which) -> {
                    if (which == 0) {
                        showKeyCapture();
                    } else {
                        view.setSelectedKey(codes[which]);
                    }
                })
                .setNegativeButton(R.string.controls_editor_cancel, null)
                .show();
    }

    private void showKeyCapture() {
        keyTarget = view.getSelectedSpec();
        waitingForKey = true;
        dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.controls_editor_key)
                .setMessage(R.string.controls_editor_press_key)
                .setNegativeButton(R.string.controls_editor_cancel, null)
                .setOnKeyListener((d, code, event) -> captureKey(event))
                .show();
        dialog.setOnDismissListener(d -> {
            waitingForKey = false;
            keyTarget = null;
        });
    }

    private void showLabelDialog() {
        if (view.getSelectedSpec() == null) return;
        final EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(view.getSelectedSpec().label);
        input.setSelectAllOnFocus(true);
        input.setContentDescription(activity.getString(R.string.controls_editor_name));
        dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.controls_editor_name)
                .setView(input)
                .setPositiveButton(R.string.controls_editor_apply, (d, which) ->
                        view.setSelectedLabel(input.getText().toString()))
                .setNegativeButton(R.string.controls_editor_cancel, null)
                .show();
    }

    private void showRestoreDialog() {
        dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.controls_editor_restore_title)
                .setMessage(R.string.controls_editor_restore_message)
                .setPositiveButton(R.string.controls_editor_restore, (d, which) -> callback.onRestoreDefaults())
                .setNegativeButton(R.string.controls_editor_cancel, null)
                .show();
    }

    private String[] keyNames() {
        String[] names = new String[KEY_COUNT];
        int i = 0;
        names[i++] = activity.getString(R.string.controls_editor_capture_key);
        for (char c = 'A'; c <= 'Z'; c++) names[i++] = String.valueOf(c);
        for (char c = '0'; c <= '9'; c++) names[i++] = String.valueOf(c);
        for (int f = 1; f <= 12; f++) names[i++] = "F" + f;
        for (String name : activity.getResources().getStringArray(R.array.controls_editor_special_keys)) {
            names[i++] = name;
        }
        return names;
    }

    private static int[] keyCodes() {
        int[] codes = new int[KEY_COUNT];
        int i = 0;
        codes[i++] = 0;
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
