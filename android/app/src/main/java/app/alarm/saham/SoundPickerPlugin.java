package app.alarm.saham;

import android.app.Activity;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;

// ⭐ annotation حذف شد - Capacitor از نام کلاس استفاده می‌کند
public class SoundPickerPlugin extends Plugin {
    private static final int REQ_PICK = 9001;
    private PluginCall pendingCall = null;

    @PluginMethod
    public void pickSound(PluginCall call) {
        pendingCall = call;
        Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM | RingtoneManager.TYPE_RINGTONE);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "انتخاب آهنگ هشدار");
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
        getActivity().startActivityForResult(intent, REQ_PICK);
    }

    @PluginMethod
    public void getSound(PluginCall call) {
        Uri u = AlarmMessagingService.getSavedSoundUri(getContext());
        JSObject ret = new JSObject();
        ret.put("uri", u == null ? "" : u.toString());
        ret.put("isDefault", u == null);
        call.resolve(ret);
    }

    @PluginMethod
    public void resetSound(PluginCall call) {
        AlarmMessagingService.setSound(getContext(), null);
        JSObject ret = new JSObject();
        ret.put("uri", "");
        ret.put("isDefault", true);
        call.resolve(ret);
    }

    @Override
    protected void handleOnActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_PICK) {
            JSObject ret = new JSObject();
            if (resultCode == Activity.RESULT_OK && data != null) {
                Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                if (uri != null) {
                    AlarmMessagingService.setSound(getContext(), uri.toString());
                    ret.put("uri", uri.toString());
                    ret.put("isDefault", false);
                } else { ret.put("uri", ""); ret.put("isDefault", true); }
            } else { ret.put("uri", ""); ret.put("cancelled", true); }
            if (pendingCall != null) pendingCall.resolve(ret);
            pendingCall = null;
        } else {
            super.handleOnActivityResult(requestCode, resultCode, data);
        }
    }
}
