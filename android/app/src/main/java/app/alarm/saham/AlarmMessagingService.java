package app.alarm.saham;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
    public static final int NOTIF_ID = 9001;
    private static final long RING_MS = 60_000;

    public static final String PREFS = "alarm_prefs";
    public static final String KEY_SOUND_URI = "alarm_sound_uri";
    public static final String KEY_CH_VER = "channel_version";

    private static final String POWER_SERVICE = "power";
    private static final String AUDIO_SERVICE = "audio";
    private static final String VIBRATOR_SERVICE = "vibrator";
    private static final String NOTIFICATION_SERVICE = "notification";

    private static MediaPlayer player = null;
    private static Vibrator vibrator = null;
    private static PowerManager.WakeLock ringWakeLock = null;
    private static AlarmMessagingService instance = null;
    private static int generation = 0;
    private static boolean ringing = false;

    @Override public void onCreate() { super.onCreate(); instance = this; }
    public static boolean isRinging() { return ringing; }

    // ===== مدیریت آهنگ داینامیک =====
    public static String getChannelId(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return "price_alerts_v" + sp.getInt(KEY_CH_VER, 7);
    }
    public static Uri getSavedSoundUri(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String s = sp.getString(KEY_SOUND_URI, "");
        return (s == null || s.isEmpty()) ? null : Uri.parse(s);
    }
    public static void setSound(Context ctx, String uriString) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int ver = sp.getInt(KEY_CH_VER, 7);
        sp.edit().putString(KEY_SOUND_URI, uriString == null ? "" : uriString).putInt(KEY_CH_VER, ver + 1).apply();
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        instance = this;
        Map<String, String> data = remoteMessage.getData();
        String title = data.containsKey("title") ? data.get("title") : "🚨 هشدار قیمت";
        String body  = data.containsKey("body")  ? data.get("body")  : "";

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pricealarm:msg").acquire(RING_MS + 10_000);

        ensureChannel(this);
        showFullAlarm(title, body, data);
        startAlarm();
    }

    public static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) ctx.getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        String chId = getChannelId(ctx);
        if (nm.getNotificationChannel(chId) != null) return;

        NotificationChannel ch = new NotificationChannel(chId, "هشدار قیمت (آلارم)", NotificationManager.IMPORTANCE_HIGH);
        ch.setBypassDnd(true); ch.enableVibration(true);
        ch.setVibrationPattern(new long[]{0,900,300,900,300,900});
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        AudioAttributes aa = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        Uri custom = getSavedSoundUri(ctx);
        int resId = ctx.getResources().getIdentifier("alarm", "raw", ctx.getPackageName());
        Uri sound = (custom != null) ? custom : (resId != 0 ? Uri.parse("android.resource://" + ctx.getPackageName() + "/" + resId) : RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
        ch.setSound(sound, aa);
        nm.createChannel(ch);
    }

    private void showFullAlarm(String title, String body, Map<String, String> data) {
        StringBuilder qs = new StringBuilder("alarm.html?");
        if (data != null) for (String k : data.keySet()) qs.append(k).append("=").append(data.get(k)).append("&");

        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("alarm_url", qs.toString());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent openPi = PendingIntent.getActivity(this, 1001, intent, flags);

        // دکمه «متوجه شدم» و Swipe → MainActivity با stop_alarm
        Intent stopIntent = new Intent(this, MainActivity.class);
        stopIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        stopIntent.putExtra("stop_alarm", "1");
        PendingIntent stopPi = PendingIntent.getActivity(this, 1002, stopIntent, flags);

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, getChannelId(this))
                .setSmallIcon(getApplicationInfo().icon)
                .setContentTitle(title).setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(openPi, true)
                .setContentIntent(openPi)
                .addAction(getApplicationInfo().icon, "✓ متوجه شدم", stopPi)
                .setDeleteIntent(stopPi)
                .setAutoCancel(false).setOngoing(false)
                .setTimeoutAfter(RING_MS);

        // 🏅 Badge روی آیکون
        int badge = 0;
        try { if (data != null && data.containsKey("badge")) badge = Integer.parseInt(data.get("badge")); } catch (Exception e) {}
        if (badge > 0) b.setNumber(badge);

        Notification notif = b.build();
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            else startForeground(NOTIF_ID, notif);
        } catch (Exception e) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTIF_ID, notif);
        }
    }

    private void startAlarm() {
        stopSoundVibrationOnly();
        ringing = true;
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null) { ringWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pricealarm:ring"); ringWakeLock.acquire(RING_MS + 5000); }
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (am != null) am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);

            Uri custom = getSavedSoundUri(this);
            int resId = getResources().getIdentifier("alarm", "raw", getPackageName());
            player = (custom != null) ? MediaPlayer.create(this, custom)
                   : (resId != 0 ? MediaPlayer.create(this, resId)
                                 : MediaPlayer.create(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)));
            if (player != null) {
                player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
                player.setLooping(true);
                player.setOnErrorListener((mp, w, x) -> { stopSoundVibrationOnly(); return true; });
                player.start();
            }
        } catch (Exception ignored) {}

        try {
            vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                long[] pattern = {0,900,300,900,300,900,500};
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
                else vibrator.vibrate(pattern, 0);
            }
        } catch (Exception ignored) {}

        final int g = ++generation;
        new Handler(Looper.getMainLooper()).postDelayed(() -> { if (g == generation) dismissAlarm(); }, RING_MS);
    }

    private static void stopSoundVibrationOnly() {
        ringing = false;
        try { if (player != null) { if (player.isPlaying()) player.stop(); player.release(); } } catch (Exception ignored) {}
        player = null;
        try { if (vibrator != null) vibrator.cancel(); } catch (Exception ignored) {}
        vibrator = null;
        if (ringWakeLock != null && ringWakeLock.isHeld()) { try { ringWakeLock.release(); } catch (Exception ignored) {} }
    }

    public static void dismissAlarm() {
        generation++;
        stopSoundVibrationOnly();
        if (instance != null) {
            try { instance.stopForeground(true); } catch (Exception ignored) {}
            NotificationManager nm = (NotificationManager) instance.getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(NOTIF_ID);
        }
    }

    @Override public void onTaskRemoved(Intent rootIntent) { dismissAlarm(); super.onTaskRemoved(rootIntent); }
}
