package app.alarm.saham;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class WidgetConfigActivity extends Activity {

    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private LinearLayout list;
    private ProgressBar loader;
    private TextView empty;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setResult(RESULT_CANCELED);
        setContentView(R.layout.widget_config);

        list = findViewById(R.id.cfg_list);
        loader = findViewById(R.id.cfg_loader);
        empty = findViewById(R.id.cfg_empty);

        Intent i = getIntent();
        Bundle extras = i.getExtras();
        if (extras != null) appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return; }

        loadAssets();
    }

    private void loadAssets() {
        loader.setVisibility(View.VISIBLE);
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    SharedPreferences sp = getSharedPreferences("auth_prefs", MODE_PRIVATE);
                    String token = sp.getString("api_token", "");
                    URL url = new URL("https://alarm.bi-kalam.ir/api/widget_assets.php");
                    HttpURLConnection c = (HttpURLConnection) url.openConnection();
                    c.setRequestProperty("Authorization", "Bearer " + token);
                    c.setConnectTimeout(10000); c.setReadTimeout(10000);
                    BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder(); String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();

                    JSONObject j = new JSONObject(sb.toString());
                    final JSONArray arr = j.optJSONObject("data").optJSONArray("assets");
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            loader.setVisibility(View.GONE);
                            if (arr == null || arr.length() == 0) {
                                empty.setVisibility(View.VISIBLE);
                                return;
                            }
                            empty.setVisibility(View.GONE);
                            for (int k = 0; k < arr.length(); k++) {
                                try {
                                    final JSONObject o = arr.getJSONObject(k);
                                    addRow(o.optString("asset"), o.optString("asset_label"), o.optString("asset_type"));
                                } catch (Exception ignored) {}
                            }
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            loader.setVisibility(View.GONE);
                            empty.setText("خطا: " + e.getMessage());
                            empty.setVisibility(View.VISIBLE);
                        }
                    });
                }
            }
        }).start();
    }

    private void addRow(String asset, String label, String type) {
        TextView tv = new TextView(this);
        tv.setText(label + "  •  " + asset);
        tv.setTextSize(16);
        tv.setPadding(30, 30, 30, 30);
        tv.setBackgroundResource(android.R.drawable.list_selector_background);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 2);
        tv.setLayoutParams(lp);
        tv.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pick(asset, label, type); }
        });
        list.addView(tv);
    }

    private void pick(String asset, String label, String type) {
        PortfolioWidgetProvider.saveAndRefresh(this, appWidgetId, asset, label, type);
        Intent result = new Intent();
        result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        setResult(RESULT_OK, result);
        finish();
    }
}
