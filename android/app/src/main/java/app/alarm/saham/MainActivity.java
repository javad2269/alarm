package app.alarm.saham;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AlarmMessagingService.ensureChannel(this);
        requestNotifPermission();
        attachJsInterface();
        handleAlarmIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleAlarmIntent(intent);
    }

    // ⭐ درخواست مجوز نوتیفیکیشن (اندروید ۱۳+) — بدون آن نوتیفیکیشن نمایش داده نمی‌شود!
    private void requestNotifPermission() {
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                if (checkSelfPermission("android.permission.POST_NOTIFICATIONS") != 0) {
                    requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 100);
                }
            }
        } catch (Exception ignored) {}
    }

    // ⭐ پل JS→Native: دکمه «متوجه شدم» در alarm.html صدا را قطع می‌کند
    private void attachJsInterface() {
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    if (getBridge() != null && getBridge().getWebView() != null) {
                        getBridge().getWebView().addJavascriptInterface(new AlarmBridge(), "AndroidAlarm");
                    }
                } catch (Exception ignored) {}
            }
        }, 2500);
    }

    public static class AlarmBridge {
        @JavascriptInterface
        public void stopAlarm() {
            AlarmMessagingService.dismissAlarm();
        }
    }

    private void handleAlarmIntent(Intent intent) {
        if (intent == null) return;
        String url = intent.getStringExtra("alarm_url");
        if (url == null) return;
        intent.removeExtra("alarm_url");
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if (getBridge() != null) {
                    String js = "window.location.href='" + url + "';";
                    getBridge().eval(js, new ValueCallback<String>() {
                        @Override
                        public void onReceiveValue(String value) {}
                    });
                }
            }
        }, 1500);
    }
}
