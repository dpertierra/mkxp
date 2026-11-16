package io.github.mkxpz.android;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Copies the game data out of the APK and then hands over to
 * {@link MkxpActivity}.
 *
 * <p>The copy runs on a worker thread so the UI stays responsive (no ANR),
 * and the progress is reported back to the main thread.
 */
public final class SplashActivity extends Activity {

    private final Handler ui = new Handler(Looper.getMainLooper());
    private ExecutorService worker;
    private TextView status;
    private ProgressBar progress;
    private Button retry;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        status = findViewById(R.id.splash_status);
        progress = findViewById(R.id.splash_progress);
        retry = findViewById(R.id.splash_retry);
        retry.setOnClickListener(v -> startInstall());

        worker = Executors.newSingleThreadExecutor();
        startInstall();
    }

    private void startInstall() {
        retry.setVisibility(View.GONE);
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(false);

        GameDataInstaller installer = new GameDataInstaller(this);
        if (!installer.needsInstall()) {
            launchGame();
            return;
        }

        status.setText(R.string.splash_installing);
        worker.execute(() -> {
            try {
                installer.install((copied, total) ->
                        ui.post(() -> showProgress(copied, total)));
                ui.post(this::launchGame);
            } catch (IOException e) {
                ui.post(() -> showError(e.getMessage()));
            }
        });
    }

    private void showProgress(int copied, int total) {
        if (total <= 0) {
            progress.setIndeterminate(true);
            return;
        }
        progress.setMax(total);
        progress.setProgress(copied);
        status.setText(getString(R.string.splash_installing_progress, copied, total));
    }

    private void showError(String message) {
        status.setText(getString(R.string.splash_error, message));
        progress.setVisibility(View.GONE);
        retry.setVisibility(View.VISIBLE);
    }

    private void launchGame() {
        Intent game = new Intent(this, MkxpActivity.class);
        LauncherSession.forward(getIntent(), game);
        startActivity(game);
        finish();
    }

    @Override
    protected void onDestroy() {
        if (worker != null) {
            worker.shutdownNow();
        }
        super.onDestroy();
    }
}
