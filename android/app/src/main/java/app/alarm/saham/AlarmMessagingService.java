package app.alarm.saham;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class AlarmMessagingService extends FirebaseMessagingService {

    public static final String CHANNEL_ID = "price_alerts_v4";
    public static final int NOTIF_ID = 9001;
    private static final long RING_MS = 60_000;

    private static MediaPlayer player = null;
    private static Vibrator vibrator = null;
    private static PowerManager.WakeLock ringWakeLock = null;
    private static AlarmMessagingService instance = null;
    private static int generation = 0;

    @Override
    public void onCreate() { super.onCreate(); instance = this; }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        instance = this;

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pricealarm:msg");
        wl.acquire(RING_MS + 10_000);

        Map<String, String> data = remoteMessage.getData();
        String title = data.containsKey("title") ? data.get("title") : "🚨 هشدار قیمت";
        String body  = data.containsKey("body")  ? data.get("body")  : "";

        ensureChannel(this);
        showFullAlarm(title, body, data);
        startAlarm();
    }

    public static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) ctx.getSystemService(NOTIFICATION_SERVICE);
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return;

        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "هشدار قیمت (آلارم)", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("هشدار قیمت با صدای آلارم، ویبره و نمایش تمام‌صفحه");
        ch.setBypassDnd(true);
        ch.enableVibration(true);
        ch.setVibrationPattern(new long[]{0, 900, 300, 900, 300, 900});
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

        AudioAttributes aa = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        int resId = ctx.getResources().getIdentifier("alarm", "raw", ctx.getPackageName());
        Uri sound = (resId != 0)
                ? Uri.parse("android.resource://" + ctx.getPackageName() + "/" + resId)
                : RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        ch.setSound(sound, aa);
        nm.createNotificationChannel(ch);
    }

    private void showFullAlarm(String title, String body, Map<String, String> data) {
        StringBuilder qs = new StringBuilder("alarm.html?");
        for (String k : data.keySet()) qs.append(k).append("=").append(data.get(k)).append("&");

        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("alarm_url", qs.toString());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent fullScreenPending = PendingIntent.getActivity(this, 1001, intent, flags);

        Intent stopIntent = new Intent(this, StopAlarmReceiver.class);
        PendingIntent stopPending = PendingIntent.getBroadcast(this, 1002, stopIntent, flags);

        int icon = getApplicationInfo().icon;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(icon)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(fullScreenPending, true)
                .setContentIntent(fullScreenPending)
                .addAction(icon, "✓ متوجه شدم", stopPending)
                .setOngoing(true)
                .setAutoCancel(false)
                .setTimeoutAfter(RING_MS);

        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIF_ID, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIF_ID, builder.build());
            }
        } catch (Exception e) {
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(NOTIF_ID, builder.build());
        }
    }

    // ========== شروع صدا + ویبره ==========
    private void startAlarm() {
        stopSoundVibrationOnly();
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            ringWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pricealarm:ring");
            ringWakeLock.acquire(RING_MS + 5000);

            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            am.setStreamVolume(AudioManager.STREAM_ALARM,
                    am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);

            int resId = getResources().getIdentifier("alarm", "raw", getPackageName());
            player = (resId != 0)
                    ? MediaPlayer.create(this, resId)
                    : MediaPlayer.create(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
            if (player != null) {
                player.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build());
                player.setLooping(true);
                player.setOnErrorListener((mp, w, x) -> { stopSoundVibrationOnly(); return true; });
                player.start();
            }
        } catch (Exception ignored) {}

        // ⭐ ویبره پیوسته تا قطع
        try {
            vibrator = (Vibrator) getSystemService(VIBRATE_SERVICE);
            if (vibrator != null) {
                long[] pattern = {0, 900, 300, 900, 300, 900, 500};
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
                } else {
                    vibrator.vibrate(pattern, 0);
                }
            }
        } catch (Exception ignored) {}

        // ⭐ قطع خودکار بعد از ۶۰ ثانیه
        final int g = ++generation;
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override public void run() { if (g == generation) dismissAlarm(); }
        }, RING_MS);
    }

    private static void stopSoundVibrationOnly() {
        try { if (player != null) { player.stop(); player.release(); } } catch (Exception ignored) {}
        player = null;
        try { if (vibrator != null) vibrator.cancel(); } catch (Exception ignored) {}
        vibrator = null;
        if (ringWakeLock != null && ringWakeLock.isHeld()) ringWakeLock.release();
    }

    // ⭐ قطع کامل از همه جا: دکمه نوتیفیکیشن، پل JS، تایم‌اوت
    public static void dismissAlarm() {
        generation++;
        stopSoundVibrationOnly();
        if (instance != null) {
            try { instance.stopForeground(true); } catch (Exception ignored) {}
            NotificationManager nm = (NotificationManager) instance.getSystemService(NOTIFICATION_SERVICE);
            nm.cancel(NOTIF_ID);
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) { dismissAlarm(); }
}
