package io.github.mkxpz.android;

import android.content.Intent;
import android.content.res.Configuration;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.RelativeLayout;

import org.libsdl.app.SDLActivity;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hosts the MKXP-Z engine inside SDL's Android glue.
 *
 * <p>SDL's Java sources are not vendored in this repository: they are copied
 * from the pinned SDL2 release by {@code fetch-sdl-java.sh}.
 */
public final class MkxpActivity extends SDLActivity implements InputManager.InputDeviceListener {

    private static final String TAG = "MkxpActivity";

    /** How long a synthetic key is held, so the engine sees it. */
    private static final long KEY_HOLD_MS = 60;
    private static final Pattern FPS_TITLE = Pattern.compile("(?:^| - )([0-9]+(?:\\.[0-9]+)? FPS)$");

    /** How often the state of the on-screen keyboard is checked. */
    private static final long IME_POLL_MS = 100;

    /**
     * Environment variable holding the absolute path of the installed game
     * data ({@code <filesDir>/game}).
     *
     * <p>The engine reads it and chdir()s into that directory before loading
     * {@code Data/}. See {@code android/README.md}.
     */
    public static final String ENV_GAME_DIR = "MKXPZ_GAME_DIR";

    /**
     * SDL hint (read from the environment) that makes the enter key of the
     * on-screen keyboard close it instead of typing a new line.
     */
    private static final String ENV_SDL_HIDE_IME = "SDL_RETURN_KEY_HIDES_IME";

    /** Live instances; the save provider refuses imports while one exists. */
    private static volatile int instances;

    /** True while a game activity exists (contract field {@code gameRunning}). */
    static boolean isRunning() {
        return instances > 0;
    }

    private final Handler handler = new Handler(Looper.getMainLooper());

    /** Launcher play session, or null when started without the launcher extras. */
    private LauncherSession session;
    /** True once game_started was sent for {@link #session}. */
    private boolean sessionStarted;

    /** On-screen pad drawn over the SDL surface. */
    private TouchControlsView touchControls;

    /** Editor of the on-screen pad. */
    private TouchControlsEditor controlsEditor;
    private GamepadEditor gamepadEditor;
    private GamepadMapping gamepadMapping;
    private InputManager inputManager;

    private int controlsOrientation;

    /** Folder with the installed game data. */
    private File gameDir;

    /** True while the on-screen keyboard is on the screen. */
    private boolean imeShown;
    /** True when the keyboard was closed by a key and not by the enter key. */
    private boolean imeClosedByKey;

    /** Watches the on-screen keyboard to know when the enter key was used. */
    private final Runnable imeWatcher = new Runnable() {
        @Override
        public void run() {
            watchIme();
            handler.postDelayed(this, IME_POLL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        instances++;
        session = LauncherSession.from(getIntent());
        // The engine pauses its render loop when the display goes to sleep.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Set before SDL starts: the native side reads it in main().
        gameDir = new GameDataInstaller(this).gameDir();
        try {
            Os.setenv(ENV_GAME_DIR, gameDir.getAbsolutePath(), true);
            Os.setenv("MKXPZ_APK_PATH", getApplicationInfo().sourceDir, true);
            // The enter key of the on-screen keyboard closes the keyboard
            // instead of writing a new line in the text field of the game.
            Os.setenv(ENV_SDL_HIDE_IME, "1", true);
        } catch (ErrnoException e) {
            Log.e(TAG, "Could not set " + ENV_GAME_DIR, e);
        }

        super.onCreate(savedInstanceState);

        // mLayout is null when SDL could not start (broken libraries).
        if (mLayout != null) {
            controlsOrientation = getResources().getConfiguration().orientation;
            // The layout of the editor wins over the one of the game, which
            // wins over the built-in one.
            TouchControlsConfig config = TouchControlsConfig.load(this, gameDir);
            touchControls = new TouchControlsView(this, config);
            touchControls.setFpsToggleListener(this::toggleFps);
            touchControls.setResetListener(this::resetGame);
            controlsEditor = new TouchControlsEditor(this, touchControls, editorCallback);
            gamepadMapping = new GamepadMapping(this);
            gamepadEditor = new GamepadEditor(this, touchControls, gamepadMapping, () -> {
                if (mSurface != null) mSurface.requestFocus();
            });
            touchControls.setGamepadListener(gamepadEditor::show);
            inputManager = (InputManager) getSystemService(INPUT_SERVICE);
            inputManager.registerInputDeviceListener(this, handler);
            touchControls.setEditListener(editing -> {
                if (editing) {
                    gamepadMapping.releaseAll();
                    controlsEditor.show();
                } else {
                    controlsEditor.hide();
                }
            });
            mLayout.addView(touchControls, new RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            updateFpsTitle(getTitle());
        }
        handler.post(imeWatcher);
    }

    /** What the editor of the pad asks the activity to do. */
    private final TouchControlsEditor.Callback editorCallback = new TouchControlsEditor.Callback() {
        @Override
        public void onSave(TouchControlsConfig config) {
            config.save(MkxpActivity.this);
            touchControls.endEditing();
        }

        @Override
        public void onCancel() {
            touchControls.setConfig(TouchControlsConfig.load(MkxpActivity.this, gameDir));
            touchControls.endEditing();
        }

        @Override
        public void onRestoreDefaults() {
            touchControls.setConfig(TouchControlsConfig.loadGameLayout(MkxpActivity.this, gameDir));
            touchControls.beginEditing();
        }
    };

    private void toggleFps() {
        SDLActivity.onNativeKeyDown(KeyEvent.KEYCODE_F2);
        handler.postDelayed(() -> SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_F2), KEY_HOLD_MS);
    }

    /** F12 is the engine's soft reset back to the title screen. */
    private void resetGame() {
        SDLActivity.onNativeKeyDown(KeyEvent.KEYCODE_F12);
        handler.postDelayed(() -> SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_F12), KEY_HOLD_MS);
    }

    @Override
    protected void onTitleChanged(CharSequence title, int color) {
        super.onTitleChanged(title, color);
        updateFpsTitle(title);
    }

    private void updateFpsTitle(CharSequence title) {
        if (touchControls == null) return;
        String text = title == null ? "" : title.toString();
        boolean visible = text.endsWith(" FPS");
        Matcher match = FPS_TITLE.matcher(text);
        touchControls.setFpsText(visible ? (match.find() ? match.group(1) : text) : null);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (touchControls != null && controlsOrientation != newConfig.orientation) {
            controlsOrientation = newConfig.orientation;
            // ponytail: cancel edits on rotation; preserving them would need per-orientation drafts.
            touchControls.endEditing();
            touchControls.setConfig(TouchControlsConfig.load(this, gameDir));
            gamepadEditor.hide();
        }
    }

    @Override
    protected void onPause() {
        // No key may stay pressed while the engine is not running.
        if (touchControls != null) {
            touchControls.dismissQuickMenu();
            touchControls.releaseAllKeys();
        }
        if (controlsEditor != null) controlsEditor.cancelInteraction();
        if (gamepadEditor != null) gamepadEditor.hide();
        if (gamepadMapping != null) gamepadMapping.releaseAll();
        handler.removeCallbacks(imeWatcher);
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(imeWatcher);
        handler.post(imeWatcher);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        boolean gamepad = GamepadMapping.isGamepadEvent(event);
        if (touchControls != null && (gamepad || isPhysicalInput(event.getSource()))
                && !isOnScreenKeyboard(event) && !touchControls.isEditing()
                && (gamepadEditor == null || !gamepadEditor.isShowing())) {
            // A keyboard or a pad is in use: the on-screen pad is not needed.
            touchControls.setControlsVisible(false);
        }
        boolean handled = gamepadEditor != null && gamepadEditor.dispatchKeyEvent(event);
        if (!handled && controlsEditor != null) handled = controlsEditor.dispatchKeyEvent(event);
        // Intercept before SDL's surface listener routes joystick buttons to onNativePadDown/Up.
        if (gamepadMapping != null && gamepadMapping.dispatchKeyEvent(event, handled)) return true;
        if (handled) return true;
        int keyCode = event.getKeyCode();
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_BACK) {
                // The engine gets these keys already, no need to fake them.
                imeClosedByKey = true;
            } else if (keyCode == KeyEvent.KEYCODE_DEL && SDLActivity.isScreenKeyboardShown()) {
                // The erase key of the on-screen keyboard. SDL sends the
                // backspace itself, but too fast for the engine to see it, so
                // the key is sent again a moment later.
                handler.postDelayed(this::pressBackspace, KEY_HOLD_MS);
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (gamepadEditor != null && gamepadEditor.isShowing()) {
            gamepadEditor.onBackPressed();
            return;
        }
        if (touchControls != null && touchControls.isEditing()) {
            controlsEditor.onBackPressed();
            return;
        }
        super.onBackPressed();
    }

    /**
     * Sends the backspace key to the engine.
     *
     * <p>The key is released a moment later: the engine only sees a key that
     * is still pressed in the frame it polls the input.
     */
    private void pressBackspace() {
        SDLActivity.onNativeKeyDown(KeyEvent.KEYCODE_DEL);
        handler.postDelayed(() -> SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_DEL),
                KEY_HOLD_MS);
    }

    /**
     * Checks whether the on-screen keyboard was closed with its enter key.
     *
     * <p>That key does not reach the engine as a key event, so the return
     * key is sent when the keyboard closes.
     */
    private void watchIme() {
        watchSession();
        boolean shown = SDLActivity.isScreenKeyboardShown();
        if (imeShown && !shown) {
            if (!imeClosedByKey && (touchControls == null || !touchControls.isEditing())
                    && (gamepadEditor == null || !gamepadEditor.isShowing())) {
                pressReturn();
            }
            imeClosedByKey = false;
            if (touchControls != null) {
                touchControls.setControlsVisible(true);
            }
        }
        imeShown = shown;
    }

    /** Reports game_started to the launcher once SDL has started the engine thread. */
    private void watchSession() {
        if (session != null && !sessionStarted && mSDLThread != null) {
            sessionStarted = true;
            session.send(this, LauncherSession.GAME_STARTED, false);
        }
    }

    /** A new launcher session for a game that is already running (singleTask). */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        LauncherSession next = LauncherSession.from(intent);
        if (next == null || next.sameAs(session)) return;
        closeSession();
        session = next;
    }

    private void closeSession() {
        if (session != null && sessionStarted) session.send(this, LauncherSession.GAME_CLOSED, true);
        sessionStarted = false;
    }

    /** Sends the return key to the engine, the way a game expects it. */
    private void pressReturn() {
        SDLActivity.onNativeKeyDown(KeyEvent.KEYCODE_ENTER);
        handler.postDelayed(() -> SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_ENTER),
                KEY_HOLD_MS);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (touchControls != null && isPhysicalInput(event.getSource())
                && !touchControls.isEditing()
                && (gamepadEditor == null || !gamepadEditor.isShowing())) {
            touchControls.setControlsVisible(false);
        }
        return super.onGenericMotionEvent(event);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (!hasFocus && gamepadMapping != null) gamepadMapping.releaseAll();
        super.onWindowFocusChanged(hasFocus);
    }

    @Override
    public void onInputDeviceAdded(int deviceId) { }

    @Override
    public void onInputDeviceChanged(int deviceId) {
        if (gamepadMapping != null) gamepadMapping.releaseDevice(deviceId);
    }

    @Override
    public void onInputDeviceRemoved(int deviceId) {
        if (gamepadMapping != null) gamepadMapping.releaseDevice(deviceId);
    }

    @Override
    protected void onDestroy() {
        if (inputManager != null) inputManager.unregisterInputDeviceListener(this);
        handler.removeCallbacks(imeWatcher);
        try {
            // Joins the engine thread: saves are complete when this returns.
            super.onDestroy();
        } finally {
            instances--;
        }
        closeSession();
    }

    /** True for keys typed on the on-screen keyboard, which is not a physical device. */
    private static boolean isOnScreenKeyboard(KeyEvent event) {
        InputDevice device = event.getDevice();
        return device == null || device.isVirtual();
    }

    /** True for keyboards, game pads and joysticks (not for the screen). */
    private static boolean isPhysicalInput(int source) {
        int physical = InputDevice.SOURCE_KEYBOARD | InputDevice.SOURCE_JOYSTICK
                | InputDevice.SOURCE_GAMEPAD | InputDevice.SOURCE_DPAD;
        return (source & physical) != 0;
    }

    @Override
    protected String[] getLibraries() {
        // libSDL2.so provides the JNI entry points and must come first.
        // libmain.so is the MKXP-Z engine, built by Meson.
        return new String[] { "SDL2", "main" };
    }
}
