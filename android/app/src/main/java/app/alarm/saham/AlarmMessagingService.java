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

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class AlarmMessagingService extends FirebaseMessagingService {

    // ⭐ اگر صدا/تنظیمات را تغییر دادید، نسخه را بالا ببرید (کانال قدیمی غیرقابل ویرایش است)
    public static final String CHANNEL_ID = "price_alerts_v2";
    public static final int NOTIF_ID = 9001;
    private static final long RING_MS = 60_000;

    private static MediaPlayer player = null;
    private static PowerManager.WakeLock ringWakeLock = null;
    private static AlarmMessagingService instance = null;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        instance = this;

        // ⭐ اصلاح باگ ۳: WakeLock تا CPU هنگام خواب گوشی خاموش نشود
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pricealarm:msg");
        wl.acquire(RING_MS + 10_000);

        Map<String, String> data = remoteMessage.getData();
        String title = data.containsKey("title") ? data.get("title") : "🚨 هشدار قیمت";
        String body = data.containsKey("body") ? data.get("body") : "";

        ensureChannel(this);
        showFullAlarm(title, body, data);
        startLoopSound();
    }

    // ========== کانال با صدای معتبر (پخش توسط خود سیستم) ==========
    public static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) ctx.getSystemService(NOTIFICATION_SERVICE);
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return;

        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "هشدار قیمت (آلارم)", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("هشدار قیمت با صدای آلارم و نمایش تمام‌صفحه");
        ch.setBypassDnd(true);                    // ⭐ اصلاح باگ ۴: پخش در حالت مزاحم نشود
        ch.enableVibration(true);
        ch.setVibrationPattern(new long[]{0, 1000, 300, 1000, 300, 1000});
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

        AudioAttributes aa = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        // ⭐ اصلاح باگ ۱: صدای سفارشی فقط اگر فایل وجود داشت، وگرنه آلارم پیش‌فرض سیستم
        int resId = ctx.getResources().getIdentifier("alarm", "raw", ctx.getPackageName());
        Uri sound = (resId != 0)
                ? Uri.parse("android.resource://" + ctx.getPackageName() + "/" + resId)
                : RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        ch.setSound(sound, aa);
        nm.createNotificationChannel(ch);
    }

    // ========== نوتیفیکیشن تمام‌صفحه ==========
    private void showFullAlarm(String title, String body, Map<String, String> data) {
        StringBuilder qs = new StringBuilder("alarm.html?");
        for (String k : data.keySet()) {
            qs.append(k).append("=").append(data.get(k)).append("&");
        }

        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("alarm_url", qs.toString());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent fullScreenPending = PendingIntent.getActivity(this, 1001, intent, flags);

        // دکمه «متوجه شدم» روی نوتیفیکیشن → قطع صدا
        Intent stopIntent = new Intent(this, StopAlarmReceiver.class);
        PendingIntent stopPending = PendingIntent.getBroadcast(this, 1002, stopIntent, flags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(getApplicationInfo().icon)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(fullScreenPending, true)
                .setContentIntent(fullScreenPending)
                .addAction(0, "✓ متوجه شدم", stopPending)
                .setVibrate(new long[]{0, 1000, 300, 1000, 300, 1000})
                .setOngoing(true)
                .setAutoCancel(false)
                .setTimeoutAfter(RING_MS);

        Notification notif = builder.build();

        // Foreground تا سیستم پروسس را وسط پخش صدا نکشد
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIF_ID, notif);
            }
        } catch (Exception e) {
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(NOTIF_ID, notif);
        }
    }

    // ========== صدای حلقه‌ای تا تأیید کاربر یا ۶۰ ثانیه ==========
    private void startLoopSound() {
        stopLoopSound();
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            ringWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pricealarm:ring");
            ringWakeLock.acquire(RING_MS + 5000);

            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            int max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM);
            am.setStreamVolume(AudioManager.STREAM_ALARM, max, 0);

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
                player.start();
                new Handler(Looper.getMainLooper()).postDelayed(
                        AlarmMessagingService::stopLoopSound, RING_MS);
            }
        } catch (Exception ignored) {}
    }

    public static void stopLoopSound() {
        try { if (player != null) { player.stop(); player.release(); } } catch (Exception ignored) {}
        player = null;
        if (ringWakeLock != null && ringWakeLock.isHeld()) ringWakeLock.release();
        if (instance != null) {
            try { instance.stopForeground(false); } catch (Exception ignored) {}
            NotificationManager nm = (NotificationManager) instance.getSystemService(NOTIFICATION_SERVICE);
            nm.cancel(NOTIF_ID);
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) { stopLoopSound(); }
}
