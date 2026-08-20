package app.alarm.saham;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createNotificationChannel();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleAlarmIntent(intent);
    }

    @Override
    public void onResume() {
        super.onResume();
        handleAlarmIntent(getIntent());
    }

    // باز کردن خودکار صفحه آلارم در WebView
    private void handleAlarmIntent(Intent intent) {
        if (intent != null && intent.hasExtra("alarm_url")) {
            final String url = intent.getStringExtra("alarm_url");
            intent.removeExtra("alarm_url");
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (getBridge() != null) {
                        getBridge().eval("window.location.href='" + url + "';");
                    }
                }
            }, 2000);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                "price_alerts", "هشدار قیمت", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("هشدار رسیدن قیمت به حد تعیین شده");
            channel.enableLights(true);
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 1000, 300, 1000, 300, 1000, 300, 1000});
            try {
                Uri sound = Uri.parse("android.resource://" + getPackageName() + "/raw/alarm");
                AudioAttributes aa = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build();
                channel.setSound(sound, aa);
            } catch (Exception e) {
                Uri s = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM);
                AudioAttributes aa = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build();
                channel.setSound(s, aa);
            }
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }
}
