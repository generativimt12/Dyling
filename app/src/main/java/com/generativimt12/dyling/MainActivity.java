package com.generativimt12.dyling;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQ_CAPTURE = 42;
    private static final int REQ_MIC = 43;
    private SharedPreferences prefs;
    private TextView delayValue;
    private TextView status;
    private Button power;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("dyling", MODE_PRIVATE);
        buildUi();
    }

    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }

    private TextView label(String text, float size, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private void buildUi() {
        int bg = Color.rgb(11,16,32), white = Color.WHITE, muted = Color.rgb(166,177,205);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(18), dp(22), dp(20));
        root.setBackgroundColor(bg);

        TextView title = label("Dyling", 32, white);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(48)));

        TextView sub = label("Audio delay • Bluetooth sync", 15, muted);
        root.addView(sub, new LinearLayout.LayoutParams(-1, dp(30)));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(18), dp(20), dp(18));
        card.setBackground(round(Color.rgb(22,29,51), dp(22)));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, dp(250));
        cp.topMargin = dp(28);
        root.addView(card, cp);

        TextView heading = label("Delay", 16, muted);
        card.addView(heading, new LinearLayout.LayoutParams(-1, dp(28)));

        delayValue = label("", 44, white);
        delayValue.setTypeface(null, android.graphics.Typeface.BOLD);
        delayValue.setGravity(Gravity.CENTER);
        card.addView(delayValue, new LinearLayout.LayoutParams(-1, dp(72)));

        SeekBar seek = new SeekBar(this);
        seek.setMax(2000);
        seek.setProgress(prefs.getInt("delay", 180));
        card.addView(seek, new LinearLayout.LayoutParams(-1, dp(52)));
        updateDelay(seek.getProgress());
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int p, boolean fromUser) { updateDelay(p); }
            public void onStartTrackingTouch(SeekBar b) {}
            public void onStopTrackingTouch(SeekBar b) {}
        });

        LinearLayout bounds = new LinearLayout(this);
        bounds.setGravity(Gravity.CENTER_VERTICAL);
        TextView zero = label("0 ms", 12, muted);
        TextView max = label("2000 ms", 12, muted);
        bounds.addView(zero, new LinearLayout.LayoutParams(0, dp(24), 1));
        max.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        bounds.addView(max, new LinearLayout.LayoutParams(0, dp(24), 1));
        card.addView(bounds);

        status = label("Ready — audio processing is off", 14, muted);
        status.setPadding(0, dp(20), 0, 0);
        root.addView(status, new LinearLayout.LayoutParams(-1, dp(55)));

        power = new Button(this);
        power.setText("Start audio delay");
        power.setTextSize(16);
        power.setAllCaps(false);
        power.setTextColor(white);
        power.setBackground(round(Color.rgb(124,92,255), dp(18)));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(58));
        bp.topMargin = dp(10);
        root.addView(power, bp);
        power.setOnClickListener(v -> startCapture());

        TextView info = label("Works with Android playback capture where the source app permits capture. Some protected/system audio cannot be intercepted by an ordinary APK.", 13, muted);
        info.setPadding(dp(4), dp(18), dp(4), 0);
        root.addView(info, new LinearLayout.LayoutParams(-1, dp(90)));

        setContentView(root);
    }

    private android.graphics.drawable.GradientDrawable round(int color, int radius) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(color); d.setCornerRadius(radius); return d;
    }

    private void updateDelay(int ms) {
        prefs.edit().putInt("delay", ms).apply();
        if (delayValue != null) delayValue.setText(ms + " ms");
    }

    private void startCapture() {
        if (android.os.Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        MediaProjectionManager mgr = (MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mgr.createScreenCaptureIntent(), REQ_CAPTURE);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == REQ_CAPTURE && result == RESULT_OK && data != null) {
            Intent s = new Intent(this, DelayAudioService.class);
            s.putExtra("resultCode", result);
            s.putExtra("data", data);
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(s); else startService(s);
            power.setText("Stop audio delay");
            status.setText("Running • delay is active");
            power.setOnClickListener(v -> stopDelay());
        }
    }

    private void stopDelay() {
        stopService(new Intent(this, DelayAudioService.class));
        power.setText("Start audio delay");
        status.setText("Ready — audio processing is off");
        power.setOnClickListener(v -> startCapture());
    }
}
