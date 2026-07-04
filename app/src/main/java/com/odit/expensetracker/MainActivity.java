package com.odit.expensetracker;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.PhoneNumberUtils;
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
        webView.addJavascriptInterface(new SmsBridge(), "Android");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (checkSelfPermission(android.Manifest.permission.READ_SMS)
                        == PackageManager.PERMISSION_GRANTED) {
                    loadSms();
                }
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
                loadSms();
            } else {
                Toast.makeText(this, "SMS permission required", Toast.LENGTH_LONG).show();
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

    private void loadSms() {
        new Thread(() -> {
            try {
                Uri uri = Uri.parse("content://sms/inbox");
                String[] projection = {"_id", "address", "body", "date"};
                String selection = "address LIKE ? OR address = ? OR address LIKE ? OR address LIKE ?";
                String[] args = new String[]{"%telebirr%", "127", "%cbe%", "%dashen%"};
                List<JSONObject> allMessages = new ArrayList<>();

                try (Cursor c = getContentResolver().query(uri, projection,
                        selection, args, "date DESC")) {
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
                            allMessages.add(msg);
                        }
                    }
                }

                JSONArray arr = new JSONArray(allMessages);
                String json = arr.toString();
                mainHandler.post(() ->
                    webView.evaluateJavascript(
                        "window.renderSms(" + json + ")", null));
            } catch (Exception e) {
                mainHandler.post(() ->
                    webView.evaluateJavascript(
                        "window.showError('" + e.getMessage().replace("'", "\\'") + "')", null));
            }
        }).start();
    }

    private class SmsBridge {
        @JavascriptInterface
        public void refresh() {
            loadSms();
        }
    }
}
