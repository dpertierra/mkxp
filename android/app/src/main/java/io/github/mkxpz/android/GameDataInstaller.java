package io.github.mkxpz.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Installs the game tree that ships inside the APK ({@code assets/game})
 * into the app's private storage ({@code <filesDir>/game}).
 *
 * <p>The copy happens on the first launch and is repeated whenever the
 * marker file {@code assets/game.version} differs from the marker stored on
 * the device. Bump that marker whenever the APK is updated with new game
 * data, otherwise the old copy is kept.
 *
 * <p>The tree is first written to a temporary directory and moved into place
 * afterwards, so an interrupted install never leaves a half-copied game.
 */
final class GameDataInstaller {

    /** Marker bundled in the APK; its contents identify the installed game build. */
    private static final String VERSION_ASSET = "game.version";
    /** Asset sub-tree holding the game files. */
    private static final String GAME_ASSET_DIR = "game";
    /** Directory name under the app's private storage. */
    private static final String GAME_DIR_NAME = "game";
    /** Staging directory used while copying. */
    private static final String TMP_DIR_NAME = ".game-install";
    /** Where the installed marker is kept. */
    private static final String PREFS_NAME = "mkxpz";
    private static final String KEY_INSTALLED_VERSION = "installedGameVersion";

    /** Progress callback; invoked on the worker thread. */
    interface ProgressListener {
        void onProgress(int copiedFiles, int totalFiles);
    }

    /** Running count of copied files, shared by the recursive copy. */
    private static final class Counter {
        int copied;
    }

    private final Context context;

    GameDataInstaller(Context context) {
        this.context = context.getApplicationContext();
    }

    /** Directory that holds the installed game data. */
    File gameDir() {
        return new File(context.getFilesDir(), GAME_DIR_NAME);
    }

    /** Version marker bundled in the APK ("" when the APK ships no marker). */
    String assetVersion() {
        try (InputStream in = context.getAssets().open(VERSION_ASSET)) {
            return readString(in).trim();
        } catch (IOException e) {
            return "";
        }
    }

    /** True when the game data is missing or outdated. */
    boolean needsInstall() {
        if (!gameDir().isDirectory()) {
            return true;
        }
        return !assetVersion().equals(installedVersion());
    }

    /**
     * Copies {@code assets/game} into {@link #gameDir()}.
     *
     * @throws IOException when the APK ships no game data or the copy fails.
     */
    void install(ProgressListener listener) throws IOException {
        AssetManager assets = context.getAssets();
        String[] entries = assets.list(GAME_ASSET_DIR);
        if (entries == null || entries.length == 0) {
            throw new IOException("The APK ships no game data (assets/" + GAME_ASSET_DIR + " is empty).");
        }

        File target = gameDir();
        File staging = new File(context.getFilesDir(), TMP_DIR_NAME);
        deleteRecursively(staging);
        deleteRecursively(target);
        if (!staging.mkdirs()) {
            throw new IOException("Could not create " + staging.getAbsolutePath());
        }

        int total = countEntries(assets, GAME_ASSET_DIR);
        copyTree(assets, GAME_ASSET_DIR, staging, total, listener, new Counter());

        if (!staging.renameTo(target)) {
            deleteRecursively(staging);
            throw new IOException("Could not install the game data into " + target.getAbsolutePath());
        }

        prefs().edit().putString(KEY_INSTALLED_VERSION, assetVersion()).apply();
    }

    private void copyTree(AssetManager assets, String assetPath, File target, int total,
                          ProgressListener listener, Counter counter) throws IOException {
        String[] names = assets.list(assetPath);
        if (names == null) {
            return;
        }
        for (String name : names) {
            String childAsset = assetPath + "/" + name;
            File childTarget = new File(target, name);
            String[] children = assets.list(childAsset);
            if (children != null && children.length > 0) {
                if (!childTarget.isDirectory() && !childTarget.mkdirs()) {
                    throw new IOException("Could not create directory " + childTarget.getAbsolutePath());
                }
                copyTree(assets, childAsset, childTarget, total, listener, counter);
            } else if (copyFile(assets, childAsset, childTarget)) {
                counter.copied++;
                if (listener != null) {
                    listener.onProgress(counter.copied, total);
                }
            }
        }
    }

    /** @return true when the asset was a regular file, false for empty directories. */
    private boolean copyFile(AssetManager assets, String assetPath, File target) throws IOException {
        try (InputStream in = assets.open(assetPath)) {
            try (OutputStream out = new FileOutputStream(target)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                }
            }
            return true;
        } catch (FileNotFoundException e) {
            // Empty directory in the APK.
            if (!target.mkdirs()) {
                throw new IOException("Could not create directory " + target.getAbsolutePath());
            }
            return false;
        }
    }

    private int countEntries(AssetManager assets, String assetPath) throws IOException {
        String[] names = assets.list(assetPath);
        if (names == null) {
            return 0;
        }
        int total = 0;
        for (String name : names) {
            String childAsset = assetPath + "/" + name;
            String[] children = assets.list(childAsset);
            if (children != null && children.length > 0) {
                total += countEntries(assets, childAsset);
            } else {
                total++;
            }
        }
        return total;
    }

    private String installedVersion() {
        return prefs().getString(KEY_INSTALLED_VERSION, "");
    }

    private SharedPreferences prefs() {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static void deleteRecursively(File file) {
        if (!file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        // Best effort: a leftover file is overwritten by the next install.
        file.delete();
    }

    private static String readString(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) > 0) {
            out.write(buffer, 0, read);
        }
        return out.toString("UTF-8");
    }
}
