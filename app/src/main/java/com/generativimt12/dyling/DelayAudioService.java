package com.generativimt12.dyling;

import android.app.*;
import android.content.*;
import android.media.*;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.*;

public class DelayAudioService extends Service {
    private static final int SAMPLE_RATE = 48000;
    private static final int CHANNELS = AudioFormat.CHANNEL_IN_STEREO;
    private static final int MAX_DELAY_MS = 2000;

    private volatile boolean running;
    private Thread worker;
    private MediaProjection projection;
    private AudioRecord record;
    private AudioTrack track;

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        createChannel();
        startForeground(7, notification("Dyling audio processing is running"));

        if (worker != null) return START_STICKY;

        int resultCode = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED);
        Intent data;
        if (Build.VERSION.SDK_INT >= 33)
            data = intent.getParcelableExtra("data", Intent.class);
        else
            data = intent.getParcelableExtra("data");

        MediaProjectionManager mgr =
            (MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
        projection = mgr.getMediaProjection(resultCode, data);
        if (projection == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        startAudio();
        return START_STICKY;
    }

    private void startAudio() {
        int min = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, CHANNELS, AudioFormat.ENCODING_PCM_16BIT);
        int bufferBytes = Math.max(min * 2, 19200);

        AudioFormat captureFormat = new AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(CHANNELS)
            .build();

        AudioPlaybackCaptureConfiguration config =
            new AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .addMatchingUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .addMatchingUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .build();

        record = new AudioRecord.Builder()
            .setAudioFormat(captureFormat)
            .setBufferSizeInBytes(bufferBytes)
            .setAudioPlaybackCaptureConfig(config)
            .build();

        track = new AudioTrack.Builder()
            .setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build())
            .setAudioFormat(new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build())
            .setBufferSizeInBytes(bufferBytes * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build();

        running = true;
        worker = new Thread(() -> process(bufferBytes), "DylingAudio");
        worker.start();
    }

    private void process(int chunk) {
        final int bytesPerFrame = 4;
        final int maxDelayBytes = SAMPLE_RATE * bytesPerFrame * MAX_DELAY_MS / 1000;
        byte[] ring = new byte[maxDelayBytes + chunk * 2];
        byte[] in = new byte[chunk];
        byte[] out = new byte[chunk];
        int write = 0;

        record.startRecording();
        track.play();

        while (running) {
            int n = record.read(in, 0, in.length, AudioRecord.READ_BLOCKING);
            if (n <= 0) continue;

            int delayMs = getSharedPreferences("dyling", MODE_PRIVATE)
                .getInt("delay", 0);
            int delayBytes = Math.min(
                maxDelayBytes - chunk,
                Math.max(0, delayMs * SAMPLE_RATE * bytesPerFrame / 1000));
            delayBytes -= delayBytes % bytesPerFrame;

            for (int i = 0; i < n; i++) {
                ring[write] = in[i];
                int read = write - delayBytes;
                if (read < 0) read += ring.length;
                out[i] = ring[read];

                write++;
                if (write >= ring.length) write = 0;
            }

            track.write(out, 0, n);
        }

        try { record.stop(); } catch (Exception ignored) {}
        try { track.stop(); } catch (Exception ignored) {}
        record.release();
        track.release();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(
                "dyling", "Dyling", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(c);
        }
    }

    private Notification notification(String text) {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(this, "dyling")
            : new Notification.Builder(this);

        return b.setContentTitle("Dyling")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build();
    }

    @Override public void onDestroy() {
        running = false;
        if (projection != null) projection.stop();

        if (worker != null) {
            try { worker.join(800); }
            catch (InterruptedException ignored) {}
        }

        worker = null;
        super.onDestroy();
    }

    @Override public android.os.IBinder onBind(Intent intent) {
        return null;
    }
}
