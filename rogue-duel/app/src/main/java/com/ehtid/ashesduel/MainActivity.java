package com.ehtid.ashesduel;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.view.View;
import android.net.Uri;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private WebView web;
    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private volatile boolean alive = true;
    private volatile String activeTopic = "";
    private volatile String since = "10s";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(5894 | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        web = new WebView(this);
        web.setBackgroundColor(0xFF0C1019);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setMediaPlaybackRequiresUserGesture(false);
        web.addJavascriptInterface(new NetworkBridge(), "AndroidNet");
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if ("game.ehtid".equals(u.getHost()) && "https".equals(u.getScheme())) {
                    String filename = u.getPath().replaceFirst("^/", "");
                    if (filename.isEmpty()) filename = "index.html";
                    if (!filename.matches("index\\.html|expansion-data\\.js|expansion-relics\\.js|expansion-combat\\.js|expansion-visual\\.js|pvp-balance\\.js|expansion\\.css")) return null;
                    String mime = filename.endsWith(".js") ? "application/javascript" : filename.endsWith(".css") ? "text/css" : "text/html";
                    try {
                        return new WebResourceResponse(mime, "UTF-8", getAssets().open(filename));
                    } catch (Exception ex) {
                        return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
                    }
                }
                return null;
            }
        });
        setContentView(web);
        web.loadUrl("https://game.ehtid/index.html");
    }

    private void callback(String function, String content) {
        if (!alive) return;
        runOnUiThread(() -> {
            if (web != null) web.evaluateJavascript("window." + function + "(" + JSONObject.quote(content) + ")", null);
        });
    }

    public class NetworkBridge {
        @JavascriptInterface public void listen(String topic) {
            if (!topic.matches("[a-z0-9-]{10,90}")) return;
            if (topic.equals(activeTopic)) return;
            activeTopic = topic;
            since = "10s";
            pool.execute(() -> {
                while (alive && topic.equals(activeTopic)) {
                    try {
                        URL u = new URL("https://ntfy.sh/" + topic + "/json?poll=1&since=" + URLEncoder.encode(since, "UTF-8"));
                        HttpURLConnection c = (HttpURLConnection) u.openConnection();
                        c.setConnectTimeout(8000);
                        c.setReadTimeout(10000);
                        try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = br.readLine()) != null) {
                                JSONObject ev = new JSONObject(line);
                                if ("message".equals(ev.optString("event"))) {
                                    String id = ev.optString("id");
                                    String data = ev.optString("message");
                                    if (!id.isEmpty()) since = id;
                                    if (!data.isEmpty()) callback("onWire", data);
                                }
                            }
                        } finally { c.disconnect(); }
                    } catch (Exception ex) {
                        callback("transportWarning", "Связь с сервером сообщений недоступна. Повторяем подключение.");
                    }
                    try { Thread.sleep(1800); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); return; }
                }
            });
        }

        @JavascriptInterface public void post(String topic, String message) {
            if (!topic.equals(activeTopic) || message.length() > 30000) return;
            pool.execute(() -> {
                HttpURLConnection c = null;
                try {
                    c = (HttpURLConnection) new URL("https://ntfy.sh/" + topic).openConnection();
                    c.setRequestMethod("POST");
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(8000);
                    c.setDoOutput(true);
                    c.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
                    c.setRequestProperty("Cache-Control", "no-store");
                    byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
                    try (OutputStream out = c.getOutputStream()) { out.write(bytes); }
                    int response = c.getResponseCode();
                    if (response < 200 || response >= 300) throw new IOException("HTTP " + response);
                } catch (Exception ex) {
                    callback("transportWarning", "Не удалось отправить данные. Проверь интернет.");
                } finally {
                    if (c != null) c.disconnect();
                }
            });
        }
    }

    @Override public void onBackPressed() {
        web.evaluateJavascript("window.goBack && window.goBack()", null);
    }
    @Override protected void onDestroy() {
        alive = false;
        activeTopic = "";
        pool.shutdownNow();
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }
}
