package com.odit.expensetracker;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final int SMS_PERMISSION_CODE = 1;
    private WebView webView;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.addJavascriptInterface(new SmsBridge(), "Android");
        setStatusBarColor("#f5f3ef");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                webView.evaluateJavascript("window.pageReady()", null);
            }
        });
        webView.loadUrl("file:///android_asset/index.html");

        if (checkSelfPermission(android.Manifest.permission.READ_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.READ_SMS},
                    SMS_PERMISSION_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                            int[] grantResults) {
        if (requestCode == SMS_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                webView.evaluateJavascript("window.smsGranted()", null);
            } else {
                Toast.makeText(this, "SMS permission required", Toast.LENGTH_LONG).show();
                webView.evaluateJavascript("window.smsDenied()", null);
            }
        }
    }

    private String detectBank(String address, String body) {
        String addr = (address != null) ? address.toLowerCase() : "";
        String text = (body != null) ? body.toLowerCase() : "";
        if (addr.equals("127") || addr.contains("telebirr") || text.contains("telebirr")) return "telebirr";
        if (addr.contains("cbe") || text.contains("cbe birr") || text.contains("cbebirr")) return "cbe";
        if (addr.contains("dashen") || text.contains("dashen")) return "dashen";
        return "other";
    }

    private void sendProgress(int count) {
        mainHandler.post(() ->
            webView.evaluateJavascript("window.readProgress(" + count + ")", null));
    }

    private void sendBatch(JSONArray batch, boolean last) {
        String json = batch.toString();
        mainHandler.post(() ->
            webView.evaluateJavascript("window.addBatch(" + json + "," + last + ")", null));
    }

    private void loadSms() {
        new Thread(() -> {
            try {
                Uri uri = Uri.parse("content://sms/inbox");
                String[] projection = {"_id", "address", "body", "date"};
                String selection = buildSelection();
                List<JSONObject> batch = new ArrayList<>();
                int count = 0;

                try (Cursor c = getContentResolver().query(uri, projection,
                        selection, filterArgs, "date DESC")) {
                    if (c != null) {
                        while (c.moveToNext()) {
                            JSONObject msg = new JSONObject();
                            String address = c.getString(1);
                            String body = c.getString(2);
                            msg.put("id", c.getLong(0));
                            msg.put("address", address);
                            msg.put("body", body);
                            msg.put("date", c.getLong(3));
                            msg.put("bank", detectBank(address, body));
                            batch.add(msg);
                            count++;

                            if (batch.size() >= 100) {
                                sendBatch(new JSONArray(batch), false);
                                batch.clear();
                                sendProgress(count);
                            }
                        }
                    }
                }

                if (!batch.isEmpty()) {
                    sendBatch(new JSONArray(batch), false);
                }
                sendProgress(count);
                mainHandler.post(() ->
                    webView.evaluateJavascript("window.finalize()", null));
            } catch (Exception e) {
                mainHandler.post(() ->
                    webView.evaluateJavascript(
                        "window.showError('" + e.getMessage().replace("'", "\\'") + "')", null));
            }
        }).start();
    }

    private String[] filterArgs = new String[]{"%telebirr%", "127", "%cbe%", "%dashen%"};

    private void setStatusBarColor(String color) {
        runOnUiThread(() -> {
            try {
                getWindow().setStatusBarColor(Color.parseColor(color));
                boolean light = color.equals("#f5f3ef") || color.equals("#ffffff");
                if (Build.VERSION.SDK_INT >= 23) {
                    int flags = getWindow().getDecorView().getSystemUiVisibility();
                    if (light) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                    else flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                    getWindow().getDecorView().setSystemUiVisibility(flags);
                }
            } catch (Exception ignored) {}
        });
    }

    private class SmsBridge {
        @JavascriptInterface
        public void statusBarColor(String color) {
            setStatusBarColor(color);
        }

        @JavascriptInterface
        public void setFilters(String filters) {
            if (filters.isEmpty()) {
                filterArgs = new String[]{"%telebirr%", "127", "%cbe%", "%dashen%"};
            } else {
                String[] parts = filters.split(",");
                filterArgs = new String[parts.length];
                for (int i = 0; i < parts.length; i++) {
                    String p = parts[i].trim();
                    filterArgs[i] = p.matches("\\d+") ? p : "%" + p + "%";
                }
            }
        }

        @JavascriptInterface
        public void refresh() {
            loadSms();
        }
    }

    private String buildSelection() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < filterArgs.length; i++) {
            if (i > 0) sb.append(" OR ");
            sb.append("address LIKE ?");
        }
        return sb.toString();
    }
}
