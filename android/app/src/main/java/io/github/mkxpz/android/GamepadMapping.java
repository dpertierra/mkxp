package io.github.mkxpz.android;

import android.content.Context;
import android.util.AtomicFile;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;

import org.json.JSONObject;
import org.libsdl.app.SDLActivity;
import org.libsdl.app.SDLControllerManager;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** One saved button map for all controllers, with per-device held-key tracking. */
final class GamepadMapping {
    private static final String TAG = "GamepadMapping";
    private static final int SDL_BUTTON = -1;
    // RGSS1 keyboard equivalents, intercepted even when no overrides are saved.
    private static final Map<Integer, Integer> DEFAULTS;
    static {
        Map<Integer, Integer> defaults = new LinkedHashMap<>();
        defaults.put(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_C);
        defaults.put(KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_X);
        defaults.put(KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_Z);
        defaults.put(KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_A);
        defaults.put(KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_Q);
        defaults.put(KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_W);
        defaults.put(KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_UNKNOWN);
        defaults.put(KeyEvent.KEYCODE_BUTTON_R2, KeyEvent.KEYCODE_UNKNOWN);
        defaults.put(KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_S);
        defaults.put(KeyEvent.KEYCODE_BUTTON_THUMBR, KeyEvent.KEYCODE_D);
        defaults.put(KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_ESCAPE);
        defaults.put(KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_UNKNOWN);
        defaults.put(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_UP);
        defaults.put(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_DOWN);
        defaults.put(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_LEFT);
        defaults.put(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_RIGHT);
        DEFAULTS = Collections.unmodifiableMap(defaults);
    }
    private final AtomicFile file;
    private final Map<Integer, Integer> bindings = new TreeMap<>();
    // SDL_BUTTON means the original down was forwarded to SDL's controller path.
    private final Map<Long, Integer> pressed = new HashMap<>();
    private final Set<Long> suppressed = new HashSet<>();

    GamepadMapping(Context context) {
        file = new AtomicFile(new File(context.getFilesDir(), "gamepad_map.json"));
        if (!file.getBaseFile().exists() && !new File(file.getBaseFile() + ".bak").exists()) return;
        try {
            JSONObject json = new JSONObject(new String(file.readFully(), StandardCharsets.UTF_8));
            Iterator<String> names = json.keys();
            while (names.hasNext()) {
                String name = names.next();
                int button = TouchControlsConfig.keyCode(name);
                Object value = json.get(name);
                int key = value instanceof String ? TouchControlsConfig.keyCode((String) value)
                        : KeyEvent.KEYCODE_UNKNOWN;
                boolean unassigned = value == JSONObject.NULL || "NONE".equalsIgnoreCase(String.valueOf(value));
                if (isButton(button) && (unassigned || key != KeyEvent.KEYCODE_UNKNOWN)) {
                    bindings.put(button, key);
                } else {
                    Log.w(TAG, "Ignoring invalid mapping for " + name);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not read gamepad map", e);
        }
    }

    Map<Integer, Integer> copyBindings() {
        Map<Integer, Integer> result = new LinkedHashMap<>(DEFAULTS);
        result.putAll(bindings);
        return result;
    }

    static Map<Integer, Integer> defaultBindings() {
        return DEFAULTS;
    }

    boolean save(Map<Integer, Integer> draft) {
        FileOutputStream out = null;
        try {
            JSONObject json = new JSONObject();
            Map<Integer, Integer> overrides = new TreeMap<>();
            for (Map.Entry<Integer, Integer> entry : draft.entrySet()) {
                int key = entry.getValue() == null ? KeyEvent.KEYCODE_UNKNOWN : entry.getValue();
                if (!isButton(entry.getKey()) || key < KeyEvent.KEYCODE_UNKNOWN
                        || key > KeyEvent.getMaxKeyCode()) {
                    throw new IllegalArgumentException("Invalid gamepad mapping");
                }
                if (Integer.valueOf(key).equals(DEFAULTS.get(entry.getKey()))) continue;
                overrides.put(entry.getKey(), key);
                json.put(keyName(entry.getKey()), key == KeyEvent.KEYCODE_UNKNOWN ? "NONE" : keyName(key));
            }
            out = file.startWrite();
            out.write(json.toString(2).getBytes(StandardCharsets.UTF_8));
            file.finishWrite(out);
            bindings.clear();
            bindings.putAll(overrides);
            return true;
        } catch (Exception e) {
            if (out != null) file.failWrite(out);
            Log.e(TAG, "Could not save gamepad map", e);
            return false;
        }
    }

    static String keyName(int code) {
        String name = KeyEvent.keyCodeToString(code);
        return name.startsWith("KEYCODE_") ? name.substring("KEYCODE_".length()) : name;
    }

    static boolean isButton(int code) {
        return KeyEvent.isGamepadButton(code)
                || (code >= KeyEvent.KEYCODE_DPAD_UP && code <= KeyEvent.KEYCODE_DPAD_CENTER)
                || code == KeyEvent.KEYCODE_DPAD_UP_LEFT || code == KeyEvent.KEYCODE_DPAD_UP_RIGHT
                || code == KeyEvent.KEYCODE_DPAD_DOWN_LEFT || code == KeyEvent.KEYCODE_DPAD_DOWN_RIGHT
                || code == KeyEvent.KEYCODE_BACK;
    }

    static boolean isGamepadEvent(KeyEvent event) {
        return event.isFromSource(InputDevice.SOURCE_GAMEPAD)
                || event.isFromSource(InputDevice.SOURCE_JOYSTICK)
                || event.isFromSource(InputDevice.SOURCE_DPAD)
                // SDL also recognises controllers whose buttons report SOURCE_KEYBOARD.
                || SDLControllerManager.isDeviceSDLJoystick(event.getDeviceId());
    }

    /** Called before SDLActivity can route a button to onNativePadDown/Up. */
    boolean dispatchKeyEvent(KeyEvent event, boolean blocked) {
        long id = buttonId(event);
        Integer held = pressed.get(id);
        if (held == null && !suppressed.contains(id)
                && (!isGamepadEvent(event) || !isButton(event.getKeyCode()))) return false;
        // Android may drop an up while paused; a fresh down starts a new press.
        if (!blocked && event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            suppressed.remove(id);
        }
        if (event.getAction() == KeyEvent.ACTION_UP) {
            if (suppressed.remove(id)) return true;
            if (held != null) {
                pressed.remove(id);
                if (held == SDL_BUTTON) return false;
                releaseKey(held);
                return true;
            }
            return blocked || bindings.containsKey(event.getKeyCode()) || DEFAULTS.containsKey(event.getKeyCode());
        }
        if (blocked) {
            if (held != null) {
                pressed.remove(id);
                releaseButton(id, held);
            }
            suppressed.add(id);
            return true;
        }
        if (suppressed.contains(id)) return true;
        if (held != null) return held != SDL_BUTTON;
        Integer key = bindings.getOrDefault(event.getKeyCode(), DEFAULTS.get(event.getKeyCode()));
        if (event.getAction() != KeyEvent.ACTION_DOWN) return key != null;
        // Ignore orphan repeats after a pause, disconnect or panel interaction.
        if (event.getRepeatCount() > 0 && key != null) return true;
        if (key != null && key != KeyEvent.KEYCODE_UNKNOWN && !pressed.containsValue(key)) {
            SDLActivity.onNativeKeyDown(key);
        }
        pressed.put(id, key == null ? SDL_BUTTON : key);
        return key != null;
    }

    void releaseAll() {
        for (long id : new ArrayList<>(pressed.keySet())) release(id);
    }

    void releaseDevice(int deviceId) {
        for (long id : new ArrayList<>(pressed.keySet())) {
            if ((int) (id >> 32) == deviceId) release(id);
        }
        suppressed.removeIf(id -> (int) (id >> 32) == deviceId);
    }

    private void release(long id) {
        int key = pressed.remove(id);
        suppressed.add(id);
        releaseButton(id, key);
    }

    private void releaseButton(long id, int key) {
        if (key == SDL_BUTTON) {
            if (SDLControllerManager.onNativePadUp((int) (id >> 32), (int) id) != 0) {
                SDLActivity.onNativeKeyUp((int) id);
            }
        } else {
            releaseKey(key);
        }
    }

    private void releaseKey(int key) {
        // ponytail: scan held buttons; use key reference counts if controller counts grow.
        if (key != KeyEvent.KEYCODE_UNKNOWN && !pressed.containsValue(key)) SDLActivity.onNativeKeyUp(key);
    }

    private static long buttonId(KeyEvent event) {
        return ((long) event.getDeviceId() << 32) | (event.getKeyCode() & 0xFFFFFFFFL);
    }
}
