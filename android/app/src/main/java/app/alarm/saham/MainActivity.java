package app.alarm.saham;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                "price_alerts", 
                "هشدار قیمت", 
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("هشدار رسیدن قیمت به حد تعیین شده");
            channel.enableLights(true);
            channel.enableVibration(true);
            channel.setShowBadge(true);
            
            // ویبره طولانی مثل ساعت زنگ‌دار
            channel.setVibrationPattern(new long[]{
                0, 1000, 300, 1000, 300, 1000, 300, 1000, 300, 1000
            });
            
            // صدای آلارم سفارشی (اگر وجود دارد) یا صدای پیش‌فرض آلارم گوشی
            try {
                Uri soundUri = Uri.parse("android.resource://" + getPackageName() + "/raw/alarm");
                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
                channel.setSound(soundUri, audioAttributes);
            } catch (Exception e) {
                Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
                channel.setSound(alarmSound, audioAttributes);
            }
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}
