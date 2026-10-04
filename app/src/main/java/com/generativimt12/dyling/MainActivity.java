package com.generativimt12.dyling;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

public class MainActivity extends Activity implements SurfaceHolder.Callback {
    private static final int PICK_VIDEO = 1001;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private SurfaceView surfaceView;
    private MediaPlayer audioPlayer;
    private MediaPlayer videoPlayer;
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

        TextView sub = label("Video sync for late Bluetooth audio", 14, muted);
        root.addView(sub, new LinearLayout.LayoutParams(-1, dp(28)));

        surfaceView = new SurfaceView(this);
        surfaceView.getHolder().addCallback(this);
        surfaceView.setBackgroundColor(Color.BLACK);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(-1, 0, 1f);
        vp.topMargin = dp(14);
        root.addView(surfaceView, vp);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(16), dp(14), dp(16), dp(12));
        controls.setBackground(round(card, dp(20)));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, dp(250));
        cp.topMargin = dp(12);
        root.addView(controls, cp);

        TextView d = label("Delay video relative to audio", 14, muted);
        controls.addView(d, new LinearLayout.LayoutParams(-1, dp(28)));

        delayValue = label("300 ms", 28, white);
        delayValue.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        delayValue.setGravity(Gravity.CENTER);
        controls.addView(delayValue, new LinearLayout.LayoutParams(-1, dp(42)));

        delayBar = new SeekBar(this);
        delayBar.setMax(2000);
        delayBar.setProgress(300);
        controls.addView(delayBar, new LinearLayout.LayoutParams(-1, dp(46)));
        delayBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int p, boolean fromUser) {
                delayValue.setText(p + " ms");
            }
            public void onStartTrackingTouch(SeekBar b) {}
            public void onStopTrackingTouch(SeekBar b) {
                if (fromPlaying()) restartSynced();
            }
        });

        positionBar = new SeekBar(this);
        positionBar.setMax(1000);
        controls.addView(positionBar, new LinearLayout.LayoutParams(-1, dp(40)));
        positionBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int p, boolean fromUser) {
                if (fromUser) userSeeking = true;
            }
            public void onStartTrackingTouch(SeekBar b) { userSeeking = true; }
            public void onStopTrackingTouch(SeekBar b) {
                if (audioPlayer != null && preparedAudio) {
                    int duration = audioPlayer.getDuration();
                    int pos = (int)((b.getProgress() / 1000f) * duration);
                    seekBoth(pos);
                }
                userSeeking = false;
            }
        });

        time = label("00:00 / 00:00", 12, muted);
        controls.addView(time, new LinearLayout.LayoutParams(-1, dp(24)));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        Button open = button("Choose video", accent, white);
        playPause = button("Play", Color.rgb(45, 52, 76), white);
        row.addView(open, new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(0, dp(52), 1);
        pp.leftMargin = dp(8);
        row.addView(playPause, pp);
        controls.addView(row);

        open.setOnClickListener(v -> pickVideo());
        playPause.setOnClickListener(v -> togglePlayback());

        status = label("Choose a video to begin", 12, muted);
        status.setPadding(dp(2), dp(8), dp(2), 0);
        root.addView(status, new LinearLayout.LayoutParams(-1, dp(30)));

        setContentView(root);
        updateProgressLoop();
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
        startActivityForResult(i, PICK_VIDEO);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == PICK_VIDEO && result == RESULT_OK && data != null && data.getData() != null) {
            try {
                getContentResolver().takePersistableUriPermission(
                    data.getData(),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {}
            openVideo(data.getData());
        }
    }

    private void openVideo(Uri uri) {
        releasePlayers();
        videoUri = uri;
        preparedAudio = false;
        preparedVideo = false;
        status.setText("Loading video…");
        playPause.setText("Loading…");

        try {
            audioPlayer = new MediaPlayer();
            audioPlayer.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build());
            audioPlayer.setDataSource(this, uri);
            audioPlayer.setOnPreparedListener(mp -> {
                preparedAudio = true;
                maybeReady();
            });
            audioPlayer.setOnCompletionListener(mp -> {
                if (videoPlayer != null) {
                    try { videoPlayer.pause(); } catch (Exception ignored) {}
                }
                playPause.setText("Play");
            });

            videoPlayer = new MediaPlayer();
            videoPlayer.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build());
            videoPlayer.setVolume(0f, 0f);
            if (surfaceView.getHolder().getSurface().isValid()) {
                videoPlayer.setDisplay(surfaceView.getHolder());
            }
            videoPlayer.setDataSource(this, uri);
            videoPlayer.setOnPreparedListener(mp -> {
                preparedVideo = true;
                maybeReady();
            });

            audioPlayer.prepareAsync();
            videoPlayer.prepareAsync();
        } catch (Exception e) {
            status.setText("Could not open this video");
            playPause.setText("Play");
        }
    }

    private void maybeReady() {
        if (!preparedAudio || !preparedVideo) return;
        int duration = audioPlayer.getDuration();
        positionBar.setProgress(0);
        time.setText(format(0) + " / " + format(duration));
        status.setText("Ready • video will start " + delayMs() + " ms after audio");
        playPause.setText("Play");
    }

    private boolean fromPlaying() {
        return audioPlayer != null && preparedAudio && audioPlayer.isPlaying();
    }

    private void togglePlayback() {
        if (!preparedAudio || !preparedVideo) return;
        if (audioPlayer.isPlaying()) {
            audioPlayer.pause();
            videoPlayer.pause();
            playPause.setText("Play");
            return;
        }
        restartSynced();
    }

    private void restartSynced() {
        if (!preparedAudio || !preparedVideo) return;
        final int pos = audioPlayer.getCurrentPosition();
        audioPlayer.start();
        videoPlayer.seekTo(Math.max(0, pos));
        handler.postDelayed(() -> {
            if (audioPlayer != null && audioPlayer.isPlaying() && videoPlayer != null) {
                try { videoPlayer.start(); } catch (Exception ignored) {}
            }
        }, delayMs());
        playPause.setText("Pause");
        status.setText("Playing • audio first, video delayed " + delayMs() + " ms");
    }

    private void seekBoth(int pos) {
        if (!preparedAudio || !preparedVideo) return;
        boolean wasPlaying = audioPlayer.isPlaying();
        audioPlayer.pause();
        videoPlayer.pause();
        audioPlayer.seekTo(pos);
        videoPlayer.seekTo(pos);
        if (wasPlaying) restartSynced();
    }

    private void updateProgressLoop() {
        if (audioPlayer != null && preparedAudio && !userSeeking) {
            try {
                int p = audioPlayer.getCurrentPosition();
                int d = Math.max(1, audioPlayer.getDuration());
                positionBar.setProgress((int)(p * 1000L / d));
                time.setText(format(p) + " / " + format(d));
            } catch (Exception ignored) {}
        }
        handler.postDelayed(this::updateProgressLoop, 250);
    }

    private String format(int ms) {
        int s = Math.max(0, ms / 1000);
        return String.format(java.util.Locale.US, "%02d:%02d", s / 60, s % 60);
    }

    private void releasePlayers() {
        handler.removeCallbacksAndMessages(null);
        if (audioPlayer != null) {
            try { audioPlayer.stop(); } catch (Exception ignored) {}
            audioPlayer.release();
            audioPlayer = null;
        }
        if (videoPlayer != null) {
            try { videoPlayer.stop(); } catch (Exception ignored) {}
            videoPlayer.release();
            videoPlayer = null;
        }
    }

    @Override public void surfaceCreated(SurfaceHolder holder) {
        if (videoPlayer != null) {
            try { videoPlayer.setDisplay(holder); } catch (Exception ignored) {}
        }
    }

    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        if (videoPlayer != null) {
            try { videoPlayer.setDisplay(null); } catch (Exception ignored) {}
        }
    }

    @Override protected void onDestroy() {
        releasePlayers();
        super.onDestroy();
    }
}
