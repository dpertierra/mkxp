package io.github.mkxpz.android;

import android.os.Bundle;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;
import android.view.WindowManager;

import org.libsdl.app.SDLActivity;

import java.io.File;

/**
 * Hosts the MKXP-Z engine inside SDL's Android glue.
 *
 * <p>SDL's Java sources are not vendored in this repository: they are copied
 * from the pinned SDL2 release by {@code fetch-sdl-java.sh}.
 */
public final class MkxpActivity extends SDLActivity {

    private static final String TAG = "MkxpActivity";

    /**
     * Environment variable holding the absolute path of the installed game
     * data ({@code <filesDir>/game}).
     *
     * <p>The engine reads it and chdir()s into that directory before loading
     * {@code Data/}. See {@code android/README.md}.
     */
    public static final String ENV_GAME_DIR = "MKXPZ_GAME_DIR";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // The engine pauses its render loop when the display goes to sleep.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Set before SDL starts: the native side reads it in main().
        File gameDir = new GameDataInstaller(this).gameDir();
        try {
            Os.setenv(ENV_GAME_DIR, gameDir.getAbsolutePath(), true);
        } catch (ErrnoException e) {
            Log.e(TAG, "Could not set " + ENV_GAME_DIR, e);
        }

        super.onCreate(savedInstanceState);
    }

    @Override
    protected String[] getLibraries() {
        // libSDL2.so provides the JNI entry points and must come first.
        // libmain.so is the MKXP-Z engine, built by Meson.
        return new String[] { "SDL2", "main" };
    }
}
