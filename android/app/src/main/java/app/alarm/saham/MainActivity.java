package app.alarm.saham;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;

import androidx.core.app.NotificationManagerCompat;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    private static final int REQ_NOTIF = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        registerPlugin(SoundPickerPlugin.class);   // ⭐ ثبت پلاگین انتخاب آهنگ
        super.onCreate(savedInstanceState);

        if (!hasNotificationPermission()) {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
            } else {
                showBlocked();
            }
            return;
        }
        initApp();
    }

    private void initApp() {
        AlarmMessagingService.ensureChannel(this);
        setVolumeControlStream(AudioManager.STREAM_ALARM);
        attachJsInterface();
        handleIntent(getIntent());
    }

    private boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            return checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        return NotificationManagerCompat.from(this).areNotificationsEnabled();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] perms, int[] grants) {
        super.onRequestPermissionsResult(requestCode, perms, grants);
        if (requestCode == REQ_NOTIF) {
            if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) initApp();
            else showBlocked();
        }
    }

    private void showBlocked() {
        new AlertDialog.Builder(this)
            .setTitle("⚠️ مجوز اعلان لازم است")
            .setMessage("بدون مجوز اعلان، برنامه قابل استفاده نیست.\n\nهمچنین برای دریافت آلارم دقیق و به‌موقع، به اتصال اینترنت نیاز دارید.")
            .setCancelable(false)
            .setPositiveButton("باشه، خروج", (d, w) -> finish())
            .setNegativeButton("رفتن به تنظیمات", (d, w) -> {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
                finish();
            })
            .show();
    }

    // ⭐⭐ کلید حل مشکل: هم stop_alarm و هم alarm_url
    private void handleIntent(Intent intent) {
        if (intent == null) return;

        // دکمه «متوجه شدم» / Swipe → قطع صدا و بستن
        if ("1".equals(intent.getStringExtra("stop_alarm"))) {
            intent.removeExtra("stop_alarm");
            AlarmMessagingService.dismissAlarm();
            finish();
            return;
        }

        // باز کردن صفحه آلارم
        String url = intent.getStringExtra("alarm_url");
        if (url == null) return;
        intent.removeExtra("alarm_url");
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override public void run() {
                if (getBridge() != null) {
                    getBridge().eval("window.location.href='" + url + "';",
                        new ValueCallback<String>() { @Override public void onReceiveValue(String v) {} });
                }
            }
        }, 1500);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        attachJsInterface();
        handleIntent(intent);
    }

    private void attachJsInterface() {
        final Handler h = new Handler(Looper.getMainLooper());
        final int[] tries = {0};
        final Runnable r = new Runnable() {
            @Override public void run() {
                try {
                    if (getBridge() != null && getBridge().getWebView() != null) {
                        getBridge().getWebView().addJavascriptInterface(new AlarmBridge(), "AndroidAlarm");
                        return;
                    }
                } catch (Exception ignored) {}
                if (tries[0]++ < 5) h.postDelayed(this, 700);
            }
        };
        h.post(r);
    }

    public static class AlarmBridge {
        @JavascriptInterface
        public void stopAlarm() { AlarmMessagingService.dismissAlarm(); }
    }
}
