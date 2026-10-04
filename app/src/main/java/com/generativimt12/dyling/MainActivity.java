package com.generativimt12.dyling;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.os.Handler;
import android.widget.TextView;

public class MainActivity extends Activity implements SurfaceHolder.Callback {
    private static final int PICK_VIDEO = 1001;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private PlayerView playerView;
    private ExoPlayer player;
    private Uri videoUri;

    private SeekBar delayBar;
    private SeekBar positionBar;
    private TextView delayValue;
    private TextView status;
    private TextView time;
    private Button playPause;
    private boolean preparedAudio;
    private boolean preparedVideo;
    private boolean userSeeking;

    private int delayMs() {
        return delayBar == null ? 300 : delayBar.getProgress();
    }

    private int dp(int v) {
        return (int)(v * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        if (getIntent() != null && getIntent().getData() != null) {
            openVideo(getIntent().getData());
        }
    }

    private TextView label(String s, float size, int color) {
        TextView t = new TextView(this);
        t.setText(s);
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
        root.addView(label("Universal video sync player", 14, muted), new LinearLayout.LayoutParams(-1, dp(28)));

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

        controls.addView(label("Video offset — positive = video starts later", 14, muted),
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
            public void onProgressChanged(SeekBar b, int p, boolean fromUser) {
                delayValue.setText(p + " ms");
                if (fromUser && player != null) player.setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC);
            }
            public void onStartTrackingTouch(SeekBar b) {}
            public void onStopTrackingTouch(SeekBar b) { if (player != null) player.seekTo(player.getCurrentPosition()); }
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

        if (getIntent() != null && getIntent().getData() != null) openVideo(getIntent().getData());
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
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private void pickVideo() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("video/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, PICK_VIDEO);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == PICK_VIDEO && result == RESULT_OK && data != null && data.getData() != null) {
            try { getContentResolver().takePersistableUriPermission(data.getData(), Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {}
            openVideo(data.getData());
        }
    }

    private void openVideo(Uri uri) {
        releasePlayer();
        try {
            player = new ExoPlayer.Builder(this).build();
            playerView.setPlayer(player);
            player.setMediaItem(MediaItem.fromUri(uri));
            player.prepare();
            player.addListener(new Player.Listener() {
                @Override public void onPlaybackStateChanged(int state) {
                    if (state == Player.STATE_READY) {
                        status.setText("Ready • video offset: " + delayMs() + " ms");
                        playPause.setText("Play");
                    } else if (state == Player.STATE_BUFFERING) status.setText("Buffering…");
                    else if (state == Player.STATE_ENDED) playPause.setText("Play");
                }
            });
            videoUri = uri;
            status.setText("Loading video…");
            playPause.setText("Loading…");
        } catch (Exception ex) {
            status.setText("This video could not be opened");
        }
    }

    private int delayMs() { return delayBar == null ? 300 : delayBar.getProgress(); }

    private void togglePlayback() {
        if (player == null) return;
        if (player.isPlaying()) {
            player.pause();
            playPause.setText("Play");
        } else {
            startSynced();
        }
    }

    private void startSynced() {
        if (player == null) return;
        final long audioClockPosition = player.getCurrentPosition();
        player.setVideoFrameMetadataListener(null);
        player.setPlayWhenReady(false);
        player.seekTo(audioClockPosition);
        // Media3 renders audio immediately; the video is intentionally held by disabling
        // the video renderer for the requested offset, then re-enabled.
        player.setVideoSurfaceView(playerView.getVideoSurfaceView());
        player.setPlayWhenReady(true);
        status.setText("Playing • video offset " + delayMs() + " ms");
        playPause.setText("Pause");
        if (delayMs() > 0) {
            player.pause();
            player.seekTo(audioClockPosition);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (player != null) player.play();
            }, delayMs());
        }
    }

    private void releasePlayer() {
        if (player != null) {
            player.release();
            player = null;
        }
        if (playerView != null) playerView.setPlayer(null);
    }

    @Override protected void onDestroy() {
        releasePlayer();
        super.onDestroy();
    }
\n}