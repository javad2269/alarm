package app.alarm.saham;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

public class PriceWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager mgr, int[] ids) {
        super.onUpdate(context, mgr, ids);
        refresh(context);
    }

    public static void refresh(final Context context) {
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    URL url = new URL("https://alarm.bi-kalam.ir/api/api.php");
                    HttpURLConnection c = (HttpURLConnection) url.openConnection();
                    c.setConnectTimeout(8000); c.setReadTimeout(8000);
                    BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder(); String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                    r.close();

                    JSONObject j = new JSONObject(sb.toString());
                    JSONObject prices = j.optJSONObject("data").optJSONObject("prices");
                    String usd   = fmt(prices.optJSONObject("usd"),   "💵 دلار: ");
                    String ounce = fmt(prices.optJSONObject("ounce"), "🌍 طلا: ");
                    String coin  = fmt(prices.optJSONObject("coin"),  "🏅 سکه: ");

                    AppWidgetManager mgr = AppWidgetManager.getInstance(context);
                    int[] ids = mgr.getAppWidgetIds(new ComponentName(context, PriceWidgetProvider.class));
                    for (int id : ids) {
                        RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget_price);
                        v.setTextViewText(R.id.w_usd, usd);
                        v.setTextViewText(R.id.w_ounce, ounce);
                        v.setTextViewText(R.id.w_coin, coin);
                        Intent in = new Intent(context, MainActivity.class);
                        PendingIntent pi = PendingIntent.getActivity(context, 0, in, PendingIntent.FLAG_IMMUTABLE);
                        v.setOnClickPendingIntent(R.id.w_root, pi);
                        mgr.updateAppWidget(id, v);
                    }
                } catch (Exception ignored) {}
            }
        }).start();
    }

    private static String fmt(JSONObject o, String prefix) {
        if (o == null) return prefix + "-";
        double p = o.optDouble("price", 0);
        if (p <= 0) return prefix + "-";
        return prefix + String.format(Locale.getDefault(), "%,.0f", p);
    }
}
