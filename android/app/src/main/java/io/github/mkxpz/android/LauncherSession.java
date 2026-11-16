package io.github.mkxpz.android;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

/**
 * Play session handed over by the Fangame Launcher (contract section A/B).
 *
 * <p>The launcher starts the game with two string extras. When both are
 * present, {@link #send} reports {@code game_started} / {@code game_closed}
 * to the launcher's provider. Every failure is ignored: the game never depends
 * on the launcher.
 */
final class LauncherSession {

    static final String LAUNCHER_PACKAGE = "com.fangamelauncher.app";
    static final String EXTRA_GAME_ID = "com.fangamelauncher.extra.GAME_ID";
    static final String EXTRA_SESSION_ID = "com.fangamelauncher.extra.SESSION_ID";

    static final String GAME_STARTED = "game_started";
    static final String GAME_CLOSED = "game_closed";

    private static final String TAG = "LauncherSession";
    private static final Uri PROVIDER = Uri.parse("content://" + LAUNCHER_PACKAGE + ".naviaxp");
    private static final int MAX_ID_LENGTH = 128;
    /** Longest the main thread waits for the launcher to answer a notification. */
    private static final long WAIT_MS = 1500;

    final String gameId;
    final String sessionId;

    private LauncherSession(String gameId, String sessionId) {
        this.gameId = gameId;
        this.sessionId = sessionId;
    }

    /** The session carried by {@code intent}, or null when an extra is missing. */
    static LauncherSession from(Intent intent) {
        if (intent == null) return null;
        String gameId = intent.getStringExtra(EXTRA_GAME_ID);
        String sessionId = intent.getStringExtra(EXTRA_SESSION_ID);
        if (!valid(gameId) || !valid(sessionId)) return null;
        return new LauncherSession(gameId, sessionId);
    }

    /** Copies the launcher extras from one intent to the next. */
    static void forward(Intent from, Intent to) {
        LauncherSession session = from(from);
        if (session == null) return;
        to.putExtra(EXTRA_GAME_ID, session.gameId);
        to.putExtra(EXTRA_SESSION_ID, session.sessionId);
    }

    private static boolean valid(String value) {
        return value != null && !value.isEmpty() && value.length() <= MAX_ID_LENGTH;
    }

    boolean sameAs(LauncherSession other) {
        return other != null && gameId.equals(other.gameId) && sessionId.equals(other.sessionId);
    }

    /**
     * Sends {@code method} to the launcher on a worker thread.
     *
     * @param wait keep the caller blocked (briefly) so the call is not lost
     *             when the process is about to end.
     */
    void send(Context context, String method, boolean wait) {
        Context app = context.getApplicationContext();
        Thread thread = new Thread(() -> {
            Bundle extras = new Bundle();
            extras.putString("gameId", gameId);
            extras.putString("sessionId", sessionId);
            try {
                ContentResolver resolver = app.getContentResolver();
                Bundle result = resolver.call(PROVIDER, method, null, extras);
                Log.i(TAG, method + " -> " + result);
            } catch (RuntimeException e) {
                // Launcher missing, not allowed or busy: the game keeps running.
                Log.i(TAG, method + " not delivered: " + e);
            }
        }, "launcher-" + method);
        thread.start();
        if (wait) {
            try {
                thread.join(WAIT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
