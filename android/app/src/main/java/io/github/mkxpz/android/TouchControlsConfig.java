package io.github.mkxpz.android;

import android.util.Log;
import android.view.KeyEvent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Layout of the on-screen pad.
 *
 * <p>A game may ship its own layout in {@code <gameDir>/android_controls.json}
 * (see {@code android/README.md}). When the file is missing or invalid the
 * built-in layout is used.
 *
 * <p>Positions are fractions of the screen: {@code x} of the width, {@code y}
 * of the height, {@code size} is the diameter as a fraction of the height.
 */
public final class TouchControlsConfig {

    private static final String TAG = "TouchControls";

    /** File read from the game folder. */
    public static final String FILE_NAME = "android_controls.json";

    /** One control of the pad. */
    public static final class Spec {
        /** Text drawn in the middle of the control. */
        public final String label;
        /** Android key code sent to the engine. */
        public final int keyCode;
        /** Column of the grid, or the center of the control. */
        public final float x;
        /** Row of the grid, or the center of the control. */
        public final float y;
        /** Diameter, as a fraction of the screen height. */
        public final float size;
        /** True when the control belongs to the group the toggle hides. */
        public final boolean secondary;
        /**
         * True when {@link #x} and {@link #y} only say in which cell of an
         * automatic grid the control goes, and not where it is drawn.
         */
        public final boolean grid;

        Spec(String label, int keyCode, float x, float y, float size, boolean secondary,
                boolean grid) {
            this.label = label;
            this.keyCode = keyCode;
            this.x = x;
            this.y = y;
            this.size = size;
            this.secondary = secondary;
            this.grid = grid;
        }
    }

    /** Buttons of the pad; the first ones are drawn on top of the others. */
    public final List<Spec> buttons = new ArrayList<>();
    /** The d-pad, or null when the game hides it. */
    public Spec dpad;
    /** Button that shows/hides the secondary buttons, or null. */
    public Spec toggle;

    /**
     * Reads the layout of a game.
     *
     * @param gameDir folder of the game (the one exported as MKXPZ_GAME_DIR)
     * @return the layout of the game, or the default one when it cannot be read
     */
    public static TouchControlsConfig load(File gameDir) {
        File file = new File(gameDir, FILE_NAME);
        if (!file.isFile()) {
            return defaults();
        }
        try {
            TouchControlsConfig config = parse(new JSONObject(readFile(file)));
            if (config == null) {
                return defaults();
            }
            return config;
        } catch (Exception e) {
            Log.w(TAG, "Ignoring " + file + ": " + e);
            return defaults();
        }
    }

    /**
     * The layout used when the game does not provide one.
     *
     * <p>The buttons are laid out like the keys of a keyboard:
     *
     * <pre>
     *   Q W
     *  A S D
     *  Z X C Alt
     * </pre>
     *
     * <p>The x and y values only say in which cell of the grid every button
     * goes; the view places them so that they fit the free space.
     */
    public static TouchControlsConfig defaults() {
        TouchControlsConfig config = new TouchControlsConfig();

        config.buttons.add(spec("Q", "Q", 0f, 0f, 0.130f));
        config.buttons.add(spec("W", "W", 1f, 0f, 0.130f));
        config.buttons.add(spec("A", "A", 0f, 1f, 0.130f));
        config.buttons.add(spec("S", "S", 1f, 1f, 0.130f));
        config.buttons.add(spec("D", "D", 2f, 1f, 0.130f));
        config.buttons.add(spec("Z", "Z", 0f, 2f, 0.130f));
        config.buttons.add(spec("X", "X", 1f, 2f, 0.130f));
        config.buttons.add(spec("C", "C", 2f, 2f, 0.130f));
        config.buttons.add(spec("Alt", "ALT_LEFT", 3f, 2f, 0.130f));

        /* Left hand: the d-pad, in the other side bar. */
        config.dpad = new Spec("", 0, 0f, 0f, 0.300f, false, false);
        return config;
    }

    private static TouchControlsConfig parse(JSONObject root) {
        JSONArray array = root.optJSONArray("buttons");
        if (array == null) {
            Log.w(TAG, "No \"buttons\" array; using the default layout");
            return null;
        }

        TouchControlsConfig config = new TouchControlsConfig();
        for (int i = 0; i < array.length(); i++) {
            JSONObject entry = array.optJSONObject(i);
            if (entry == null) {
                Log.w(TAG, "Button " + i + " is not an object; ignored");
                continue;
            }
            String label = entry.optString("label", "?");
            String key = entry.optString("key", "");
            int keyCode = keyCode(key);
            if (keyCode == KeyEvent.KEYCODE_UNKNOWN) {
                Log.w(TAG, "Unknown key \"" + key + "\" for button \"" + label + "\"; ignored");
                continue;
            }
            float x = (float) entry.optDouble("x", -1.0);
            float y = (float) entry.optDouble("y", -1.0);
            float size = (float) entry.optDouble("size", 0.0);
            if (x < 0.0f || x > 1.0f || y < 0.0f || y > 1.0f || size <= 0.0f) {
                Log.w(TAG, "Bad position for button \"" + label + "\"; ignored");
                continue;
            }
            config.buttons.add(new Spec(label, keyCode, x, y, size,
                    entry.optBoolean("secondary", false), false));
        }
        if (config.buttons.isEmpty()) {
            Log.w(TAG, "No usable button; using the default layout");
            return null;
        }

        Object dpad = root.opt("dpad");
        if (Boolean.FALSE.equals(dpad)) {
            config.dpad = null;
        } else if (dpad instanceof JSONObject) {
            config.dpad = spec((JSONObject) dpad, "");
        } else {
            config.dpad = defaults().dpad;
        }

        Object toggle = root.opt("toggle");
        if (toggle instanceof JSONObject) {
            config.toggle = spec((JSONObject) toggle, "≡");
        } else {
            config.toggle = null;
        }
        return config;
    }

    private static Spec spec(JSONObject entry, String defaultLabel) {
        String label = entry.optString("label", defaultLabel);
        float x = (float) entry.optDouble("x", -1.0);
        float y = (float) entry.optDouble("y", -1.0);
        float size = (float) entry.optDouble("size", 0.0);
        if (x < 0.0f || x > 1.0f || y < 0.0f || y > 1.0f || size <= 0.0f) {
            Log.w(TAG, "Bad position for \"" + label + "\"; ignored");
            return null;
        }
        return new Spec(label, 0, x, y, size, false, false);
    }

    private static Spec spec(String label, String key, float x, float y, float size) {
        return new Spec(label, keyCode(key), x, y, size, false, true);
    }

    /**
     * Resolves a key name such as {@code "Z"} or {@code "DPAD_UP"}.
     *
     * @return the key code, or {@link KeyEvent#KEYCODE_UNKNOWN} when unknown
     */
    public static int keyCode(String name) {
        if (name == null || name.isEmpty()) {
            return KeyEvent.KEYCODE_UNKNOWN;
        }
        String upper = name.toUpperCase(Locale.US);
        if (!upper.startsWith("KEYCODE_")) {
            upper = "KEYCODE_" + upper;
        }
        return KeyEvent.keyCodeFromString(upper);
    }

    private static String readFile(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            return out.toString("UTF-8");
        }
    }
}
