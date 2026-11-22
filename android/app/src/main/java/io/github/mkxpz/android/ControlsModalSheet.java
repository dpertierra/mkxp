package io.github.mkxpz.android;

import android.app.Activity;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.HashSet;
import java.util.Set;

final class ControlsModalSheet extends ControlsEditorUi {
    final FrameLayout overlay;
    final TextView title;
    final TextView hint;
    final LinearLayout content;
    final LinearLayout footer;
    private final TouchControlsView host;
    private final LinearLayout panel;
    private final Set<Long> heldKeys = new HashSet<>();
    private Runnable onResize;
    private int contentWidth;
    private int contentHeight;

    ControlsModalSheet(Activity activity, TouchControlsView host) {
        super(activity);
        this.host = host;
        overlay = new FrameLayout(activity);
        overlay.setBackgroundColor(0xB3000000);
        overlay.setClickable(true);
        overlay.setFocusableInTouchMode(true);
        overlay.setVisibility(View.GONE);
        panel = column();
        panel.setPadding(dp(10), dp(8), dp(10), dp(8));
        panel.setBackground(background(SURFACE, dp(16)));
        panel.setClickable(true);
        panel.setElevation(dp(6));
        title = text(R.string.controls_editor_key, 18, INK);
        title.setTypeface(null, Typeface.BOLD);
        panel.addView(title);
        hint = text(R.string.gamepad_hint, 13, MUTED);
        hint.setPadding(0, dp(4), 0, dp(6));
        panel.addView(hint);
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        content = column();
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        footer = column();
        panel.addView(footer);
        overlay.addView(panel, new FrameLayout.LayoutParams(1, 1));
        host.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        host.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> position());
        host.getViewTreeObserver().addOnGlobalLayoutListener(this::position);
    }

    void show(Runnable onResize) {
        this.onResize = onResize;
        overlay.setVisibility(View.VISIBLE);
        overlay.bringToFront();
        contentWidth = 0;
        position();
        overlay.requestFocus();
        host.requestApplyInsets();
    }

    void hide() {
        overlay.setVisibility(View.GONE);
    }

    boolean isShowing() {
        return overlay.getVisibility() == View.VISIBLE;
    }

    int contentWidth() {
        return contentWidth;
    }

    boolean isLandscape() {
        return contentWidth > contentHeight;
    }

    boolean consumeAfterClose(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP
                || event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN) return false;
        long token = ((long) event.getDeviceId() << 32) | (event.getKeyCode() & 0xFFFFFFFFL);
        boolean held = heldKeys.contains(token);
        if (event.getAction() == KeyEvent.ACTION_UP) heldKeys.remove(token);
        if (!isShowing()) return held;
        if (event.getAction() == KeyEvent.ACTION_DOWN) heldKeys.add(token);
        return false;
    }

    private void position() {
        if (!isShowing()) return;
        Rect safe = new Rect();
        host.getSafeBounds(safe);
        safe.inset(dp(8), dp(8));
        if (safe.width() <= dp(20) || safe.height() <= 0) return;
        int width = Math.min(dp(1100), safe.width());
        int availableWidth = width - panel.getPaddingLeft() - panel.getPaddingRight();
        boolean resized = availableWidth != contentWidth || safe.height() != contentHeight;
        contentWidth = availableWidth;
        contentHeight = safe.height();
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) panel.getLayoutParams();
        int left = safe.left + (safe.width() - width) / 2;
        if (params.width != width || params.height != safe.height()
                || params.leftMargin != left || params.topMargin != safe.top) {
            params.width = width;
            params.height = safe.height();
            params.gravity = Gravity.TOP | Gravity.LEFT;
            params.leftMargin = left;
            params.topMargin = safe.top;
            panel.setLayoutParams(params);
        }
        if (resized && onResize != null) onResize.run();
    }
}
