package com.generativimt12.dyling;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int REQ_CAPTURE = 42;
    private static final int REQ_MIC = 43;

    private SharedPreferences prefs;
    private TextView value;
    private TextView status;
    private Button power;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("dyling", MODE_PRIVATE);
        buildUi();
    }

    private int dp(float v) {
        return (int)(v * getResources().getDisplayMetrics().density + .5f);
    }

    private TextView text(String s, float size, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private void buildUi() {
        int bg = Color.rgb(9, 13, 26);
        int card = Color.rgb(20, 27, 48);
        int white = Color.WHITE;
        int muted = Color.rgb(164, 175, 202);
        int accent = Color.rgb(124, 92, 255);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(20), dp(22), dp(20));
        root.setBackgroundColor(bg);

        TextView title = text("Dyling", 32, white);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(46)));

        TextView sub = text("Bluetooth audio • video sync", 15, muted);
        root.addView(sub, new LinearLayout.LayoutParams(-1, dp(30)));

        LinearLayout cardView = new LinearLayout(this);
        cardView.setOrientation(LinearLayout.VERTICAL);
        cardView.setPadding(dp(20), dp(18), dp(20), dp(18));
        cardView.setBackground(round(card, dp(22)));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, dp(285));
        cp.topMargin = dp(24);
        root.addView(cardView, cp);

        TextView heading = text("Audio correction", 16, muted);
        cardView.addView(heading, new LinearLayout.LayoutParams(-1, dp(28)));

        value = text("", 42, white);
        value.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        value.setGravity(Gravity.CENTER);
        cardView.addView(value, new LinearLayout.LayoutParams(-1, dp(72)));

        SeekBar seek = new SeekBar(this);
        seek.setMax(2000);
        seek.setProgress(prefs.getInt("delay", 0));
        cardView.addView(seek, new LinearLayout.LayoutParams(-1, dp(52)));
        updateValue(seek.getProgress());

        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int p, boolean fromUser) {
                updateValue(p);
            }
            public void onStartTrackingTouch(SeekBar b) {}
            public void onStopTrackingTouch(SeekBar b) {}
        });

        LinearLayout bounds = new LinearLayout(this);
        TextView zero = text("0 ms", 12, muted);
        TextView max = text("2000 ms", 12, muted);
        bounds.addView(zero, new LinearLayout.LayoutParams(0, dp(24), 1));
        max.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        bounds.addView(max, new LinearLayout.LayoutParams(0, dp(24), 1));
        cardView.addView(bounds);

        status = text("Ready", 14, muted);
        status.setPadding(dp(2), dp(18), dp(2), 0);
        root.addView(status, new LinearLayout.LayoutParams(-1, dp(48)));

        power = new Button(this);
        power.setText("Start capture");
        power.setTextSize(16);
        power.setAllCaps(false);
        power.setTextColor(white);
        power.setBackground(round(accent, dp(18)));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(58));
        bp.topMargin = dp(8);
        root.addView(power, bp);
        power.setOnClickListener(v -> startCapture());

        TextView info = text(
            "Important: if Bluetooth audio is already LATE compared with the video, adding audio delay makes it worse. A normal APK cannot make source audio play before it arrives. In that case the correct compensation is to delay the VIDEO by the measured amount inside the player.",
            13, muted);
        info.setGravity(Gravity.TOP);
        info.setPadding(dp(4), dp(18), dp(4), 0);
        root.addView(info, new LinearLayout.LayoutParams(-1, dp(125)));

        setContentView(root);
    }

    private android.graphics.drawable.GradientDrawable round(int color, int radius) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private void updateValue(int ms) {
        prefs.edit().putInt("delay", ms).apply();
        if (value != null) value.setText(ms + " ms");
    }

    private void startCapture() {
        if (android.os.Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        MediaProjectionManager mgr =
            (MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mgr.createScreenCaptureIntent(), REQ_CAPTURE);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == REQ_CAPTURE && result == RESULT_OK && data != null) {
            Intent s = new Intent(this, DelayAudioService.class);
            s.putExtra("resultCode", result);
            s.putExtra("data", data);
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(s);
            else startService(s);

            power.setText("Stop capture");
            status.setText("Running • audio correction active");
            power.setOnClickListener(v -> stopDelay());
        }
    }

    private void stopDelay() {
        stopService(new Intent(this, DelayAudioService.class));
        power.setText("Start capture");
        status.setText("Ready");
        power.setOnClickListener(v -> startCapture());
    }
}
