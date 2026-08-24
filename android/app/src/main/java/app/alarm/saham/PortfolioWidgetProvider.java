package app.alarm.saham;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

public class PortfolioWidgetProvider extends AppWidgetProvider {

    public static final String PREFS = "portfolio_widget_prefs";
    public static final String ACTION_REFRESH = "app.alarm.saham.REFRESH_WIDGET";

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) updateWidget(ctx, mgr, id);
    }

    @Override
    public void onDeleted(Context ctx, int[] ids) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor e = sp.edit();
        for (int id : ids) {
            e.remove("asset_" + id).remove("label_" + id).remove("type_" + id);
        }
        e.apply();
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        if (ACTION_REFRESH.equals(intent.getAction())) {
            AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
            int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, PortfolioWidgetProvider.class));
            for (int id : ids) updateWidget(ctx, mgr, id);
        }
    }

    public static void updateWidget(final Context ctx, final AppWidgetManager mgr, final int id) {
        final SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        final String asset  = sp.getString("asset_" + id, "");
        final String label  = sp.getString("label_" + id, "نامشخص");
        final String type   = sp.getString("type_"  + id, "");

        final RemoteViews views = new RemoteViews(ctx.getPackageName(), R.layout.widget_portfolio);
        views.setTextViewText(R.id.w_name, label);
        views.setTextViewText(R.id.w_price, "⏳ ...");
        views.setTextViewText(R.id.w_pct, "");

        // Tap → باز کردن اپ
        Intent open = new Intent(ctx, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, id, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.w_root, pi);

        // Refresh button
        Intent rf = new Intent(ctx, PortfolioWidgetProvider.class);
        rf.setAction(ACTION_REFRESH);
        PendingIntent rpi = PendingIntent.getBroadcast(ctx, 9000 + id, rf,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.w_refresh, rpi);

        mgr.updateAppWidget(id, views);

        if (asset.isEmpty()) return;

        // Fetch قیمت در thread
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    URL url = new URL("https://alarm.bi-kalam.ir/api/widget_price.php?asset=" + asset);
                    HttpURLConnection c = (HttpURLConnection) url.openConnection();
                    c.setConnectTimeout(8000); c.setReadTimeout(8000);
                    BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder(); String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();

                    JSONObject j = new JSONObject(sb.toString());
                    double price = j.optDouble("price", 0);
                    double pct   = j.optDouble("pct", 0);

                    RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_portfolio);
                    v.setTextViewText(R.id.w_name, label);
                    v.setTextViewText(R.id.w_price, price > 0 ?
                            String.format(Locale.getDefault(), "%,.0f", price) : "-");
                    v.setTextViewText(R.id.w_pct, (pct >= 0 ? "▲ " : "▼ ") +
                            String.format(Locale.getDefault(), "%.2f", Math.abs(pct)) + "%");
                    v.setInt(R.id.w_pct, "setTextColor",
                            ctx.getResources().getColor(pct >= 0 ? android.R.color.holo_green_light : android.R.color.holo_red_light, null));
                    v.setOnClickPendingIntent(R.id.w_root, pi);
                    v.setOnClickPendingIntent(R.id.w_refresh, rpi);

                    mgr.updateAppWidget(id, v);
                } catch (Exception ignored) {}
            }
        }).start();
    }

    // Called from Activity after user picks an asset
    public static void saveAndRefresh(Context ctx, int id, String asset, String label, String type) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit().putString("asset_" + id, asset)
                 .putString("label_" + id, label)
                 .putString("type_"  + id, type).apply();
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        updateWidget(ctx, mgr, id);
    }
}
