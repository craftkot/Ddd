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
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
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
    private volatile String since = "1m";
    private volatile String activeBearer = "";
    private static final String RELAY = "https://ehtid-chat-relay.craftkot404.workers.dev";

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
                    if (!filename.matches("index\\.html|expansion-data\\.js|expansion-relics\\.js|expansion-combat\\.js|expansion-visual\\.js|pvp-balance\\.js|atelier\\.js|connection-ui\\.js|run-save\\.js|run-economy\\.js|turn-sequence\\.js|encounter-director\\.js|route-tree\\.js|atelier\\.css|connection-ui\\.css|expansion\\.css|artbook\\.css|fate-combat\\.css|character-redesign\\.css|art/arena\\.webp|art/atlas\\.webp|art/characters/(guard|oracle|hunter|bandit|cultist|skeleton|slime|hound|boss)\\.webp|art/kenney/(ui|icons|scene|heroes)/[a-z_-]+\\.png")) return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
                    String mime = filename.endsWith(".js") ? "application/javascript" : filename.endsWith(".css") ? "text/css" : filename.endsWith(".png") ? "image/png" : filename.endsWith(".webp") ? "image/webp" : "text/html";
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
        @JavascriptInterface public boolean copyText(String code) {
            if (code == null || !code.matches("[A-HJ-NP-Z2-9]{4}")) return false;
            try {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard == null) return false;
                clipboard.setPrimaryClip(ClipData.newPlainText("Код комнаты", code));
                return true;
            } catch (Exception ex) { return false; }
        }

        @JavascriptInterface public void listen(String topic, String bearer) {
            if (topic == null || !topic.matches("ehtidp2p-[a-f0-9]{40}")) return;
            if (bearer == null || !bearer.matches("[a-f0-9]{64}")) return;
            if (topic.equals(activeTopic) && bearer.equals(activeBearer)) return;
            activeTopic = topic;
            activeBearer = bearer;
            since = "1m";
            pool.execute(() -> {
                while (alive && topic.equals(activeTopic)) {
                    try {
                        URL u = new URL(RELAY + "/" + topic + "/json?since=" + URLEncoder.encode(since, "UTF-8"));
                        HttpURLConnection c = (HttpURLConnection) u.openConnection();
                        c.setConnectTimeout(8000);
                        c.setReadTimeout(10000);
                        c.setRequestProperty("Authorization", "Bearer " + bearer);
                        c.setRequestProperty("Cache-Control", "no-store");
                        if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
                        try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = br.readLine()) != null) {
                                JSONObject ev = new JSONObject(line);
                                String event = ev.optString("event");
                                String id = ev.optString("id");
                                if (("message".equals(event) || "cursor".equals(event)) && id.matches("e[0-9]{1,18}")) since = id;
                                if ("message".equals(event)) {
                                    String data = ev.optString("message");
                                    if (!data.isEmpty()) callback("onWire", data);
                                }
                            }
                        } finally { c.disconnect(); }
                    } catch (Exception ex) {
                        callback("transportWarning", "Связь с сервером сообщений недоступна. Повторяем подключение.");
                    }
                    try { Thread.sleep(4000); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); return; }
                }
            });
        }

        @JavascriptInterface public void post(String topic, String message) {
            if (!topic.equals(activeTopic) || message == null || message.length() > 11850) return;
            if (!(message.startsWith("v1.") || message.matches("frag1:[0-9a-f]{32}:[0-9]{1,2}:[0-9]{1,2}:[A-Za-z0-9_.-]{1,2100}"))) return;
            final String bearer = activeBearer;
            pool.execute(() -> {
                HttpURLConnection c = null;
                try {
                    c = (HttpURLConnection) new URL(RELAY + "/" + topic).openConnection();
                    c.setRequestMethod("POST");
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(8000);
                    c.setDoOutput(true);
                    c.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
                    c.setRequestProperty("Cache-Control", "no-store");
                    c.setRequestProperty("Authorization", "Bearer " + bearer);
                    c.setRequestProperty("Cache", "no");
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
        activeBearer = "";
        pool.shutdownNow();
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }
}
