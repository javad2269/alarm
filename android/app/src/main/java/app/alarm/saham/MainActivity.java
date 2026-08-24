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

        // ⭐ گیت مجوز اعلان: بدون مجوز برنامه اجرا نمی‌شود
        if (!hasNotificationPermission()) {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
            } else {
                showBlocked();
            }
            return;
        }

        AlarmMessagingService.ensureChannel(this);
        setVolumeControlStream(AudioManager.STREAM_ALARM);
        attachJsInterface();
        handleAlarmIntent(getIntent());
    }

    private boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            return checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        // ⭐ اصلاح خطای کامپایل: from(this) اضافه شد
        return NotificationManagerCompat.from(this).areNotificationsEnabled();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] perms, int[] grants) {
        super.onRequestPermissionsResult(requestCode, perms, grants);
        if (requestCode == REQ_NOTIF) {
            if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) {
                // مجوز داده شد → ادامه
                AlarmMessagingService.ensureChannel(this);
                setVolumeControlStream(AudioManager.STREAM_ALARM);
                attachJsInterface();
                handleAlarmIntent(getIntent());
            } else {
                showBlocked();
            }
        }
    }

    // ⭐ مسدودسازی کامل + پیام مجوز و اینترنت
    private void showBlocked() {
        new AlertDialog.Builder(this)
            .setTitle("⚠️ مجوز اعلان لازم است")
            .setMessage("بدون مجوز اعلان، برنامه قابل استفاده نیست.\n\n" +
                        "همچنین برای دریافت آلارم دقیق و به‌موقع، به اتصال اینترنت نیاز دارید.")
            .setCancelable(false)
            .setPositiveButton("باشه، خروج", (d, w) -> finish())
            .setNegativeButton("رفتن به تنظیمات", (d, w) -> {
                Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
                finish();
            })
            .show();
    }

    // ===== پل JS (دکمه متوجه شدم در alarm.html) =====
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

    private void handleAlarmIntent(Intent intent) {
        if (intent == null) return;
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
}
