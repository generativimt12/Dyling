package com.generativimt12.dyling;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.ui.PlayerView;

public class MainActivity extends Activity {
    private static final int PICK_VIDEO = 1001;
    private static final long SYNC_INTERVAL_MS = 50L;
    private static final long HARD_RESYNC_MS = 120L;
    private static final float MAX_FINE_SPEED = 0.025f;

    private final Handler syncHandler = new Handler(Looper.getMainLooper());
    private final Runnable syncRunnable = new Runnable() {
        @Override public void run() {
            synchronizeVideo();
            if (audioPlayer != null && audioPlayer.isPlaying()) {
                syncHandler.postDelayed(this, SYNC_INTERVAL_MS);
            }
        }
    };

    private PlayerView playerView;
    private ExoPlayer audioPlayer;
    private ExoPlayer videoPlayer;
    private SeekBar delayBar;
    private TextView delayValue;
    private TextView status;
    private Button playPause;
    private boolean correcting;
    private boolean userSeeking;

    private int delayMs() {
        return delayBar == null ? 300 : delayBar.getProgress();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();

        Intent intent = getIntent();
        if (intent != null && intent.getData() != null) {
            openVideo(intent.getData());
        }
    }

    private TextView label(String text, float size, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private void buildUi() {
        int bg = Color.rgb(8, 11, 22);
        int card = Color.rgb(19, 25, 43);
        int white = Color.WHITE;
        int muted = Color.rgb(165, 175, 199);
        int accent = Color.rgb(113, 91, 255);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(16), dp(18), dp(16));
        root.setBackgroundColor(bg);

        TextView title = label("Dyling", 30, white);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(42)));
        root.addView(label("Universal video sync player", 14, muted),
                new LinearLayout.LayoutParams(-1, dp(28)));

        playerView = new PlayerView(this);
        playerView.setUseController(true);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(-1, 0, 1f);
        vp.topMargin = dp(14);
        root.addView(playerView, vp);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(16), dp(14), dp(16), dp(12));
        controls.setBackground(round(card, dp(20)));
        root.addView(controls, new LinearLayout.LayoutParams(-1, dp(190)));

        controls.addView(label("Video offset — video starts later than audio", 14, muted),
                new LinearLayout.LayoutParams(-1, dp(28)));

        delayValue = label("300 ms", 28, white);
        delayValue.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        delayValue.setGravity(Gravity.CENTER);
        controls.addView(delayValue, new LinearLayout.LayoutParams(-1, dp(42)));

        delayBar = new SeekBar(this);
        delayBar.setMax(3000);
        delayBar.setProgress(300);
        controls.addView(delayBar, new LinearLayout.LayoutParams(-1, dp(48)));
        delayBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int p, boolean fromUser) {
                delayValue.setText(p + " ms");
            }

            @Override public void onStartTrackingTouch(SeekBar b) {
                userSeeking = true;
            }

            @Override public void onStopTrackingTouch(SeekBar b) {
                userSeeking = false;
                resyncNow();
            }
        });

        LinearLayout row = new LinearLayout(this);
        Button open = button("Choose video", accent, white);
        playPause = button("Play", Color.rgb(45, 52, 76), white);
        row.addView(open, new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(0, dp(52), 1);
        pp.leftMargin = dp(8);
        row.addView(playPause, pp);
        controls.addView(row);

        open.setOnClickListener(v -> pickVideo());
        playPause.setOnClickListener(v -> togglePlayback());

        status = label("Choose any video file from the device", 12, muted);
        status.setPadding(dp(2), dp(8), dp(2), 0);
        root.addView(status, new LinearLayout.LayoutParams(-1, dp(30)));

        setContentView(root);
    }

    private Button button(String text, int bg, int fg) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(fg);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setBackground(round(bg, dp(16)));
        return b;
    }

    private android.graphics.drawable.GradientDrawable round(int color, int radius) {
        android.graphics.drawable.GradientDrawable d =
                new android.graphics.drawable.GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private void pickVideo() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("video/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, PICK_VIDEO);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == PICK_VIDEO && result == RESULT_OK &&
                data != null && data.getData() != null) {
            try {
                getContentResolver().takePersistableUriPermission(
                        data.getData(), Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
            }
            openVideo(data.getData());
        }
    }

    private void openVideo(Uri uri) {
        releasePlayers();

        try {
            audioPlayer = new ExoPlayer.Builder(this).build();
            videoPlayer = new ExoPlayer.Builder(this).build();

            audioPlayer.setSeekParameters(SeekParameters.CLOSEST_SYNC);
            videoPlayer.setSeekParameters(SeekParameters.CLOSEST_SYNC);

            playerView.setPlayer(videoPlayer);
            videoPlayer.setVolume(0f);

            MediaItem item = MediaItem.fromUri(uri);
            audioPlayer.setMediaItem(item);
            videoPlayer.setMediaItem(item);

            Player.Listener listener = new Player.Listener() {
                @Override public void onPlaybackStateChanged(int state) {
                    if (state == Player.STATE_READY) {
                        status.setText("Ready • audio is master clock");
                        playPause.setText("Play");
                    } else if (state == Player.STATE_BUFFERING) {
                        status.setText("Buffering…");
                    } else if (state == Player.STATE_ENDED) {
                        syncHandler.removeCallbacks(syncRunnable);
                        playPause.setText("Play");
                        status.setText("Finished");
                    }
                }
            };
            audioPlayer.addListener(listener);

            audioPlayer.prepare();
            videoPlayer.prepare();
            videoPlayer.pause();
            status.setText("Loading video…");
            playPause.setText("Loading…");
        } catch (Exception ex) {
            status.setText("This video could not be opened");
            releasePlayers();
        }
    }

    private void togglePlayback() {
        if (audioPlayer == null || videoPlayer == null) return;

        if (audioPlayer.isPlaying()) {
            audioPlayer.pause();
            videoPlayer.pause();
            videoPlayer.setPlaybackSpeed(1f);
            syncHandler.removeCallbacks(syncRunnable);
            playPause.setText("Play");
            status.setText("Paused");
        } else {
            startSynced();
        }
    }

    private void startSynced() {
        if (audioPlayer == null || videoPlayer == null) return;

        syncHandler.removeCallbacks(syncRunnable);
        long audioPos = Math.max(0L, audioPlayer.getCurrentPosition());
        long videoStart = Math.max(0L, audioPos - delayMs());

        audioPlayer.seekTo(audioPos);
        videoPlayer.seekTo(videoStart);
        videoPlayer.setPlaybackSpeed(1f);
        videoPlayer.pause();

        audioPlayer.play();
        syncHandler.postDelayed(() -> {
            if (audioPlayer != null && videoPlayer != null && audioPlayer.isPlaying()) {
                videoPlayer.play();
                syncHandler.post(syncRunnable);
            }
        }, delayMs());

        status.setText("Playing • continuously synchronized");
        playPause.setText("Pause");
    }

    private void synchronizeVideo() {
        if (audioPlayer == null || videoPlayer == null ||
                !audioPlayer.isPlaying() || userSeeking || correcting) {
            return;
        }

        long audioPosition = audioPlayer.getCurrentPosition();
        long targetVideoPosition = Math.max(0L, audioPosition - delayMs());
        long actualVideoPosition = videoPlayer.getCurrentPosition();
        long error = targetVideoPosition - actualVideoPosition;

        if (Math.abs(error) > HARD_RESYNC_MS) {
            correcting = true;
            videoPlayer.setPlaybackSpeed(1f);
            videoPlayer.seekTo(targetVideoPosition);
            correcting = false;
            return;
        }

        // Fine correction: keep audio untouched and gently steer video.
        // Positive error means video is behind; speed it up slightly.
        float correction = Math.max(-MAX_FINE_SPEED,
                Math.min(MAX_FINE_SPEED, error / 4000f));
        videoPlayer.setPlaybackSpeed(1f + correction);
    }

    private void resyncNow() {
        if (audioPlayer == null || videoPlayer == null) return;

        long audioPosition = Math.max(0L, audioPlayer.getCurrentPosition());
        long target = Math.max(0L, audioPosition - delayMs());

        correcting = true;
        videoPlayer.setPlaybackSpeed(1f);
        videoPlayer.seekTo(target);
        correcting = false;

        if (audioPlayer.isPlaying()) {
            videoPlayer.play();
            syncHandler.removeCallbacks(syncRunnable);
            syncHandler.post(syncRunnable);
        }
        status.setText("Sync target: " + delayMs() + " ms");
    }

    private void releasePlayers() {
        syncHandler.removeCallbacks(syncRunnable);
        correcting = false;

        if (audioPlayer != null) {
            audioPlayer.release();
            audioPlayer = null;
        }
        if (videoPlayer != null) {
            videoPlayer.release();
            videoPlayer = null;
        }
        if (playerView != null) {
            playerView.setPlayer(null);
        }
    }

    @Override protected void onDestroy() {
        releasePlayers();
        super.onDestroy();
    }
}
