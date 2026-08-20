package app.alarm.saham;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class AlarmMessagingService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Map<String, String> data = remoteMessage.getData();
        String title = data.containsKey("title") ? data.get("title") : "🚨 هشدار قیمت";
        String body = data.containsKey("body") ? data.get("body") : "";
        showFullAlarm(title, body, data);
    }

    private void showFullAlarm(String title, String body, Map<String, String> data) {
        String channelId = "price_alerts";
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // کانال با صدای آلارم و ویبره
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "هشدار قیمت", NotificationManager.IMPORTANCE_HIGH);
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 1000, 300, 1000, 300, 1000, 300, 1000});
            try {
                Uri sound = Uri.parse("android.resource://" + getPackageName() + "/raw/alarm");
                AudioAttributes aa = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build();
                channel.setSound(sound, aa);
            } catch (Exception ignored) {}
            manager.createNotificationChannel(channel);
        }

        // Intent باز کردن مستقیم صفحه آلارم
        StringBuilder qs = new StringBuilder("alarm.html?");
        if (data != null) {
            for (String k : data.keySet()) {
                qs.append(k).append("=").append(data.get(k)).append("&");
            }
        }

        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("alarm_url", qs.toString());

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent fullScreenPending = PendingIntent.getActivity(this, 1001, intent, flags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(fullScreenPending, true)   // ⭐ باز شدن خودکار صفحه
                .setContentIntent(fullScreenPending)
                .setVibrate(new long[]{0, 1000, 300, 1000, 300, 1000})
                .setAutoCancel(true);

        manager.notify((int) System.currentTimeMillis(), builder);
    }
}
