package io.github.mkxpz.android;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

/** Inset-aware, collapsible editor over the pad; changes stay in memory until Save. */
final class TouchControlsEditor extends ControlsEditorUi {

    interface Callback {
        void onSave(TouchControlsConfig config);
        void onCancel();
        void onRestoreDefaults();
    }

    private static final float MIN_SIZE = 0.04f;
    private static final float MAX_SIZE = 0.30f;
    private final TouchControlsView view;
    private final Callback callback;
    private ScrollView panel;
    private LinearLayout selectionActions;
    private LinearLayout footer;
    private LinearLayout body;
    private LinearLayout contextActions;
    private TextView hint;
    private TextView selection;
    private TextView sizeLabel;
    private SeekBar sizeBar;
    private ImageButton foldButton;
    private ImageButton moveButton;
    private Button keyButton;
    private Button nameButton;
    private Button deleteButton;
    private EditText labelInput;
    private TouchControlsConfig.Spec contextTarget;
    private int previousSoftInputMode;
    private boolean waitingForKey;
    private int capturedKey = KeyEvent.KEYCODE_UNKNOWN;
    private boolean collapsed;
    private int dock;

    TouchControlsEditor(Activity activity, TouchControlsView view, Callback callback) {
        super(activity);
        this.view = view;
        this.callback = callback;
        view.setEditorListener(this::refresh);
        view.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> positionPanel(true));
        view.getViewTreeObserver().addOnGlobalLayoutListener(() -> positionPanel(false));
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
        clearInteraction();
        if (panel != null) {
            panel.setVisibility(View.GONE);
        }
    }

    /** Keep editor input in Android views, including both halves of captured keys. */
    boolean dispatchKeyEvent(KeyEvent event) {
        if (capturedKey != KeyEvent.KEYCODE_UNKNOWN && event.getKeyCode() == capturedKey) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                capturedKey = KeyEvent.KEYCODE_UNKNOWN;
            }
            return true;
        }
        if (panel == null || panel.getVisibility() != View.VISIBLE) return false;
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK || (!waitingForKey && code == KeyEvent.KEYCODE_ESCAPE)) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                capturedKey = code;
                onBackPressed();
            }
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN && code != KeyEvent.KEYCODE_UNKNOWN) {
            if (waitingForKey) {
                capturedKey = code;
                if (contextTarget == view.getSelectedSpec()) view.setSelectedKey(code);
                finishInteraction();
                return true;
            }
            if (labelInput != null && labelInput.hasFocus() && (code == KeyEvent.KEYCODE_ENTER
                    || code == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                capturedKey = code;
                applyLabel();
                return true;
            }
        }
        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN) return false;
        // Dispatch only when focus belongs to the panel; SDL's surface also has a key listener.
        if (panel.hasFocus()) activity.getWindow().superDispatchKeyEvent(event);
        return true;
    }

    void onBackPressed() {
        if (!cancelInteraction()) callback.onCancel();
    }

    boolean cancelInteraction() {
        if (contextActions == null || contextActions.getVisibility() != View.VISIBLE) return false;
        finishInteraction();
        return true;
    }

    private void buildPanel() {
        panel = new ScrollView(activity);
        panel.setFillViewport(false);
        panel.setBackground(background(SURFACE, dp(16)));
        panel.setElevation(dp(6));
        // Empty panel space must not become a drag or a touch on the game.
        panel.setClickable(true);
        LinearLayout content = column();
        content.setPadding(dp(8), dp(4), dp(8), dp(8));
        panel.addView(content, new ScrollView.LayoutParams(-1, -2));

        LinearLayout header = row();
        TextView title = text(R.string.controls_editor_title, 16, INK);
        title.setTypeface(null, Typeface.BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        header.addView(iconButton(R.drawable.ic_controls_restore, R.string.controls_editor_restore,
                v -> showRestoreConfirmation()), new LinearLayout.LayoutParams(dp(44), dp(44)));
        foldButton = iconButton(R.drawable.ic_controls_collapse, R.string.controls_editor_collapse, v -> {
            clearInteraction();
            collapsed = !collapsed;
            refresh();
        });
        header.addView(foldButton, new LinearLayout.LayoutParams(dp(44), dp(44)));
        moveButton = iconButton(R.drawable.ic_controls_move, R.string.controls_editor_move, v -> {
            dock = (dock + 1) % 6;
            positionPanel(false);
        });
        header.addView(moveButton, new LinearLayout.LayoutParams(dp(44), dp(44)));
        content.addView(header);

        body = column();
        hint = text(R.string.controls_editor_hint, 14, MUTED);
        hint.setSingleLine(true);
        hint.setEllipsize(TextUtils.TruncateAt.END);
        hint.setGravity(Gravity.CENTER_VERTICAL);
        hint.setMinHeight(dp(40));
        body.addView(hint);
        selectionActions = column();
        LinearLayout actions = row();
        selection = text(R.string.controls_editor_title, 14, INK);
        selection.setTypeface(null, Typeface.BOLD);
        selection.setSingleLine(true);
        selection.setEllipsize(TextUtils.TruncateAt.END);
        selection.setGravity(Gravity.CENTER_VERTICAL);
        selection.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        selection.setPadding(dp(8), dp(4), dp(8), dp(4));
        selection.setBackground(background(0xFF303744, dp(6)));
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(0, -2, 1);
        chipParams.rightMargin = dp(4);
        actions.addView(selection, chipParams);
        keyButton = button(R.string.controls_editor_key, v -> showKeyCapture());
        nameButton = button(R.string.controls_editor_name, v -> showLabelEditor());
        deleteButton = button(R.string.controls_editor_delete, v -> showDeleteConfirmation());
        deleteButton.setTextColor(0xFFFFB5B0);
        for (Button action : new Button[]{keyButton, nameButton, deleteButton}) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
            params.leftMargin = dp(4);
            actions.addView(action, params);
        }
        selectionActions.addView(actions);

        LinearLayout sizeRow = row();
        sizeLabel = text(R.string.controls_editor_size, 14, INK);
        sizeRow.addView(sizeLabel);
        sizeBar = new SeekBar(activity);
        sizeBar.setMax(100);
        sizeBar.setMinHeight(dp(40));
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
        body.addView(selectionActions);
        contextActions = column();
        contextActions.setVisibility(View.GONE);
        body.addView(contextActions);
        content.addView(body);

        Button save = primaryButton(R.string.controls_editor_save, v -> callback.onSave(view.getConfig()));
        footer = row();
        footer.setPadding(0, dp(4), 0, 0);
        Button add = button(R.string.controls_editor_add, v -> view.addButton());
        Button cancel = button(R.string.controls_editor_cancel, v -> callback.onCancel());
        for (Button action : new Button[]{add, cancel, save}) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
            if (action != save) params.rightMargin = dp(4);
            footer.addView(action, params);
        }
        content.addView(footer);
    }

    private void refresh() {
        if (panel == null || panel.getVisibility() != View.VISIBLE) {
            return;
        }
        TouchControlsConfig.Spec spec = view.getSelectedSpec();
        if (contextActions.getVisibility() == View.VISIBLE && contextTarget != spec) clearInteraction();
        boolean interacting = contextActions.getVisibility() == View.VISIBLE;
        hint.setVisibility(!interacting && spec == null ? View.VISIBLE : View.GONE);
        selectionActions.setVisibility(!interacting && spec != null ? View.VISIBLE : View.GONE);
        if (spec != null) {
            boolean dpad = spec == view.getConfig().dpad;
            selection.setText(dpad ? activity.getString(R.string.controls_editor_dpad)
                    : spec.label);
            selection.setContentDescription(dpad ? activity.getString(R.string.controls_editor_dpad)
                    : activity.getString(R.string.controls_editor_selected, spec.label, spec.keyName()));
            keyButton.setContentDescription(activity.getString(R.string.controls_editor_key)
                    + ": " + spec.keyName());
            keyButton.setVisibility(dpad ? View.GONE : View.VISIBLE);
            nameButton.setVisibility(dpad ? View.GONE : View.VISIBLE);
            deleteButton.setVisibility(dpad ? View.GONE : View.VISIBLE);
            sizeBar.setProgress(Math.round((spec.size - MIN_SIZE) / (MAX_SIZE - MIN_SIZE) * 100));
            sizeLabel.setText(activity.getString(R.string.controls_editor_size_value,
                    Math.round(spec.size * 100)));
        }
        body.setVisibility(collapsed ? View.GONE : View.VISIBLE);
        footer.setVisibility(collapsed || interacting ? View.GONE : View.VISIBLE);
        foldButton.setImageResource(collapsed ? R.drawable.ic_controls_expand : R.drawable.ic_controls_collapse);
        String foldDescription = activity.getString(collapsed ? R.string.controls_editor_expand
                : R.string.controls_editor_collapse);
        foldButton.setContentDescription(foldDescription);
        foldButton.setTooltipText(foldDescription);
        positionPanel(true);
    }

    /** Prefer the upper letterbox in portrait and the upper centre in landscape. */
    private void positionPanel(boolean avoidControls) {
        if (panel == null || panel.getVisibility() != View.VISIBLE || view.getWidth() == 0) {
            return;
        }
        Rect safe = new Rect();
        view.getSafeBounds(safe);
        if (labelInput != null) {
            // Fullscreen SDL windows do not reliably resize for the IME; constrain only the panel.
            WindowInsets insets = view.getRootWindowInsets();
            if (Build.VERSION.SDK_INT >= 30 && insets != null) {
                int[] location = new int[2];
                view.getLocationInWindow(location);
                safe.bottom = Math.min(safe.bottom, view.getRootView().getHeight()
                        - insets.getInsets(WindowInsets.Type.ime()).bottom - location[1]);
            } else {
                Rect visible = new Rect();
                view.getWindowVisibleDisplayFrame(visible);
                int[] location = new int[2];
                view.getLocationOnScreen(location);
                safe.bottom = Math.min(safe.bottom, visible.bottom - location[1]);
            }
        }
        safe.inset(dp(8), dp(8));
        if (safe.width() <= 0 || safe.height() <= 0) {
            return;
        }
        int width = Math.min(dp(400), safe.width());
        boolean portrait = view.getHeight() > view.getWidth();
        moveButton.setVisibility(portrait ? View.GONE : View.VISIBLE);
        if (portrait) dock = 0;
        int available = safe.height();
        if (portrait && labelInput == null) {
            int gameTop = Math.round((view.getHeight() - view.getWidth() * 0.75f) / 2f);
            int upperBand = gameTop - safe.top - dp(8);
            if (upperBand >= dp(100)) {
                available = Math.min(available, upperBand);
            }
        }
        // Measure the actual scroll content; weighted body heights used to squeeze out its actions.
        panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(available, View.MeasureSpec.AT_MOST));
        int height = panel.getMeasuredHeight();
        Rect bounds = panelBounds(safe, width, height, dock);
        if (avoidControls && !portrait && labelInput == null) {
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

    private void startInteraction(int title) {
        clearInteraction();
        collapsed = false;
        contextTarget = view.getSelectedSpec();
        contextActions.setVisibility(View.VISIBLE);
        TextView heading = text(title, 14, INK);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setPadding(0, dp(4), 0, dp(4));
        contextActions.addView(heading);
        panel.scrollTo(0, 0);
    }

    private void clearInteraction() {
        waitingForKey = false;
        contextTarget = null;
        if (labelInput != null) {
            InputMethodManager ime = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            ime.hideSoftInputFromWindow(labelInput.getWindowToken(), 0);
            labelInput.clearFocus();
            labelInput = null;
            activity.getWindow().setSoftInputMode(previousSoftInputMode);
        }
        if (contextActions != null) {
            contextActions.removeAllViews();
            contextActions.setVisibility(View.GONE);
        }
    }

    private void finishInteraction() {
        clearInteraction();
        refresh();
        panel.scrollTo(0, 0);
    }

    private void addInteractionButtons(int apply, Runnable action) {
        LinearLayout actions = row();
        actions.setPadding(0, dp(4), 0, 0);
        Button cancel = button(R.string.controls_editor_cancel, v -> finishInteraction());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.rightMargin = dp(4);
        actions.addView(cancel, params);
        actions.addView(primaryButton(apply, v -> action.run()), new LinearLayout.LayoutParams(0, -2, 1));
        contextActions.addView(actions);
    }

    private void showKeyCapture() {
        if (view.getSelectedSpec() == null) return;
        startInteraction(R.string.controls_editor_capture_key);
        waitingForKey = true;
        contextActions.addView(text(R.string.controls_editor_press_key, 14, MUTED));
        addInteractionButtons(R.string.controls_editor_key_list, this::showKeyList);
        refresh();
    }

    private void showKeyList() {
        if (view.getSelectedSpec() == null) return;
        startInteraction(R.string.controls_editor_key);
        addInteractionButtons(R.string.controls_editor_physical_key, this::showKeyCapture);
        contextActions.addView(keyGrid(panel.getLayoutParams().width, code -> {
            if (contextTarget == view.getSelectedSpec()) view.setSelectedKey(code);
            finishInteraction();
        }));
        refresh();
    }

    private void showLabelEditor() {
        if (view.getSelectedSpec() == null) return;
        startInteraction(R.string.controls_editor_name);
        final EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI
                | EditorInfo.IME_FLAG_NO_FULLSCREEN);
        input.setText(contextTarget.label);
        input.setSelectAllOnFocus(true);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        input.setTextColor(INK);
        input.setHintTextColor(MUTED);
        input.setHighlightColor(0x66FFC850);
        input.setMinHeight(dp(40));
        input.setPadding(dp(10), dp(6), dp(10), dp(6));
        input.setBackgroundTintList(null);
        StateListDrawable states = new StateListDrawable();
        GradientDrawable focused = background(0xFF303744, dp(10));
        focused.setStroke(dp(2), ACCENT);
        GradientDrawable normal = background(0xFF303744, dp(10));
        normal.setStroke(dp(1), 0xFF7D8798);
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{}, normal);
        input.setBackground(states);
        if (Build.VERSION.SDK_INT >= 29) {
            GradientDrawable cursor = background(ACCENT, 0);
            cursor.setSize(dp(2), dp(20));
            input.setTextCursorDrawable(cursor);
        }
        input.setContentDescription(activity.getString(R.string.controls_editor_name));
        input.setOnEditorActionListener((v, action, event) -> {
            if (action != EditorInfo.IME_ACTION_DONE) return false;
            applyLabel();
            return true;
        });
        labelInput = input;
        previousSoftInputMode = activity.getWindow().getAttributes().softInputMode;
        activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        contextActions.addView(input, new LinearLayout.LayoutParams(-1, -2));
        addInteractionButtons(R.string.controls_editor_apply, this::applyLabel);
        refresh();
        input.requestFocus();
        input.post(() -> {
            if (labelInput != input || !input.hasFocus()) return;
            InputMethodManager ime = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            ime.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private void applyLabel() {
        if (labelInput != null && contextTarget == view.getSelectedSpec()) {
            view.setSelectedLabel(labelInput.getText().toString());
        }
        finishInteraction();
    }

    private void showRestoreConfirmation() {
        startInteraction(R.string.controls_editor_restore_title);
        contextActions.addView(text(R.string.controls_editor_restore_message, 14, MUTED));
        addInteractionButtons(R.string.controls_editor_restore, () -> {
            finishInteraction();
            callback.onRestoreDefaults();
        });
        refresh();
    }

    private void showDeleteConfirmation() {
        if (view.getSelectedSpec() == null) return;
        startInteraction(R.string.controls_editor_delete_title);
        TextView message = text(R.string.controls_editor_delete_title, 14, MUTED);
        message.setText(activity.getString(R.string.controls_editor_delete_message, contextTarget.label));
        contextActions.addView(message);
        addInteractionButtons(R.string.controls_editor_delete, () -> {
            if (contextTarget == view.getSelectedSpec()) view.deleteSelected();
            finishInteraction();
        });
        refresh();
    }

}
