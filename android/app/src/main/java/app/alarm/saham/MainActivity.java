package app.alarm.saham;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.RingtoneManager;
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
    private static final int REQ_SOUND = 777;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
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

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        if ("1".equals(intent.getStringExtra("stop_alarm"))) {
            intent.removeExtra("stop_alarm");
            AlarmMessagingService.dismissAlarm();
            finish();
            return;
        }
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

    // ===== تزریق پل JS (همان پل قابل‌اعتماد) =====
    private void attachJsInterface() {
        final Handler h = new Handler(Looper.getMainLooper());
        final int[] tries = {0};
        final Runnable r = new Runnable() {
            @Override public void run() {
                try {
                    if (getBridge() != null && getBridge().getWebView() != null) {
                        getBridge().getWebView().addJavascriptInterface(new AlarmBridge(MainActivity.this), "AndroidAlarm");
                        return;
                    }
                } catch (Exception ignored) {}
                if (tries[0]++ < 5) h.postDelayed(this, 700);
            }
        };
        h.post(r);
    }

    // ===== ⭐ باز کردن انتخاب‌گر آهنگ سیستم =====
    public void startSoundPicker() {
        runOnUiThread(() -> {
            try {
                Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM | RingtoneManager.TYPE_RINGTONE);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "انتخاب آهنگ هشدار");
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
                Uri cur = AlarmMessagingService.getSavedSoundUri(this);
                if (cur != null) intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, cur);
                startActivityForResult(intent, REQ_SOUND);
            } catch (Exception ignored) {}
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SOUND) {
            String js;
            if (resultCode == RESULT_OK && data != null) {
                Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                if (uri != null) {
                    AlarmMessagingService.setSound(this, uri.toString());
                    js = "if(window.onSoundPicked)window.onSoundPicked('" + uri.toString().replace("'", "\\'") + "')";
                } else {
                    AlarmMessagingService.setSound(this, null);
                    js = "if(window.onSoundPicked)window.onSoundPicked('')";
                }
            } else {
                js = "if(window.onSoundCancelled)window.onSoundCancelled()";
            }
            if (getBridge() != null) getBridge().eval(js, null);
        }
    }

    // ===== ⭐ پل JS با متدهای صدا =====
    public static class AlarmBridge {
        private final MainActivity activity;
        public AlarmBridge(MainActivity activity) { this.activity = activity; }

        @JavascriptInterface
        public void stopAlarm() { AlarmMessagingService.dismissAlarm(); }

        @JavascriptInterface
        public String getSound() {
            Uri u = AlarmMessagingService.getSavedSoundUri(activity);
            return u == null ? "" : u.toString();
        }

        @JavascriptInterface
        public void resetSound() { AlarmMessagingService.setSound(activity, null); }

        @JavascriptInterface
        public void pickSound() { activity.startSoundPicker(); }
    }
}
