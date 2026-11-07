package com.ehtid.ashesduel;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebSettings;
import android.net.Uri;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import org.json.JSONObject;

/*
 * Android startup diagnostics.
 *
 * Use a known local HTTPS origin and load the main document directly from
 * APK bytes. The old v0.11 relied on an unregistered https://game.ehtid host
 * for the first navigation, with no visible fallback if WebView failed.
 *
 * The native screen stays visible until JavaScript explicitly confirms that
 * the whole game has rendered. This also detects blank screens and WebView
 * renderer crashes. External resources are NEVER loaded into this JS bridge.
 */
public class MainActivity extends Activity {
    private static final String TAG = "AshesStartup";
    private static final String LOCAL_HOST = "appassets.androidplatform.net";
    private static final String BASE_URL = "https://" + LOCAL_HOST + "/assets/";
    private static final String RELAY = "https://ehtid-chat-relay.craftkot404.workers.dev";
    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private FrameLayout root;
    private LinearLayout overlay;
    private TextView status;
    private Button retry;
    private WebView web;
    private volatile boolean alive = true;
    private volatile String activeTopic = "";
    private volatile String since = "1m";
    private volatile String activeBearer = "";
    private volatile boolean ready = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(12, 16, 25));
        setContentView(root);
        createStartupOverlay();
        try {
            // Avoid manufacturer-specific fullscreen/WindowInsets calls in
            // onCreate; immersive mode is not required for a playable game.
            web = new WebView(this);
            web.setBackgroundColor(0xFF0C1019);
            root.addView(web, 0, new FrameLayout.LayoutParams(-1, -1));
            WebSettings settings = web.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setAllowFileAccess(false);
            settings.setAllowContentAccess(false);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            settings.setJavaScriptCanOpenWindowsAutomatically(false);
            settings.setSupportMultipleWindows(false);
            settings.setMediaPlaybackRequiresUserGesture(false);
            // Safe Browsing stays enabled (WebView default); never disable it.
            web.addJavascriptInterface(new NetworkBridge(), "AndroidNet");
            web.setWebChromeClient(new WebChromeClient() {
                @Override public boolean onConsoleMessage(ConsoleMessage m) {
                    if (m.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                        Log.e(TAG, "JS: " + m.message() + " @" + m.lineNumber());
                    }
                    return true;
                }
            });
            web.setWebViewClient(new WebViewClient() {
                @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                    Uri u = req.getUrl();
                    return !("https".equals(u.getScheme()) && LOCAL_HOST.equals(u.getHost())
                            && u.getPath() != null && u.getPath().startsWith("/assets/"));
                }
                @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                    Uri u = req.getUrl();
                    if (!"https".equals(u.getScheme()) || !LOCAL_HOST.equals(u.getHost())
                            || u.getPath() == null || !u.getPath().startsWith("/assets/")) {
                        return blocked();
                    }
                    String filename = u.getPath().substring("/assets/".length());
                    // Only APK-owned files. No file://, remote HTTP, or traversal.
                    if (filename.isEmpty() || filename.startsWith("/") || filename.contains("..")
                            || !filename.matches("[A-Za-z0-9_./-]+")) return blocked();
                    String mime = filename.endsWith(".js") ? "application/javascript"
                            : filename.endsWith(".css") ? "text/css"
                            : filename.endsWith(".png") ? "image/png"
                            : filename.endsWith(".webp") ? "image/webp"
                            : filename.endsWith(".svg") ? "image/svg+xml"
                            : filename.endsWith(".json") ? "application/json" : "text/html";
                    try {
                        return new WebResourceResponse(mime, "UTF-8", getAssets().open(filename));
                    } catch (IOException ex) {
                        Log.e(TAG, "Asset missing: " + filename, ex);
                        return blocked();
                    }
                }
                @Override public void onReceivedError(WebView view, WebResourceRequest request,
                                                      WebResourceError error) {
                    if (request.isForMainFrame()) {
                        showStartupError("Не удалось загрузить страницу: " + error.getErrorCode());
                    }
                }
                @Override public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                    Log.e(TAG, "WebView renderer exited, crashed=" + detail.didCrash());
                    if (web != null) {
                        root.removeView(web);
                        web.destroy();
                        web = null;
                    }
                    showStartupError("Сбой системного Android WebView. Проверь обновление WebView в Google Play.");
                    return true;
                }
            });
            // The game HTML is read straight from its APK; it does not rely
            // on DNS/certificates/online connectivity during startup.
            String html = readGameHtml();
            Log.i(TAG, "Loading bundled game HTML: " + html.length() + " characters");
            web.loadDataWithBaseURL(BASE_URL, html, "text/html", "UTF-8", BASE_URL);
            handler.postDelayed(() -> {
                if (!ready && alive) {
                    showStartupError("Игра не подтвердила запуск за 12 секунд. Проверь обновление Android System WebView.");
                }
            }, 12000);
        } catch (Throwable error) {
            Log.e(TAG, "Native startup failed", error);
            showStartupError("Ошибка запуска Android: " + error.getClass().getSimpleName());
        }
    }

    private static WebResourceResponse blocked() {
        return new WebResourceResponse("text/plain", "UTF-8",
                new ByteArrayInputStream(new byte[0]));
    }

    private String readGameHtml() throws IOException {
        StringBuilder content = new StringBuilder();
        try (InputStream in = getAssets().open("index.html");
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            char[] buffer = new char[4096];
            int n;
            while ((n = reader.read(buffer)) != -1) content.append(buffer, 0, n);
        }
        if (content.length() < 500 || !content.toString().contains("</html>"))
            throw new IOException("APK contains incomplete HTML");
        return content.toString();
    }

    private void createStartupOverlay() {
        overlay = new LinearLayout(this);
        overlay.setGravity(Gravity.CENTER);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setPadding(30, 25, 30, 25);
        overlay.setBackgroundColor(Color.rgb(12, 16, 25));
        status = new TextView(this);
        status.setText("ПЕПЕЛ И КАРТЫ\n\nЗапуск игры…");
        status.setTextColor(Color.rgb(237, 218, 184));
        status.setTextSize(18);
        status.setGravity(Gravity.CENTER);
        overlay.addView(status, new LinearLayout.LayoutParams(-1, -2));
        retry = new Button(this);
        retry.setText("Повторить запуск");
        retry.setVisibility(View.GONE);
        retry.setOnClickListener(v -> recreate());
        LinearLayout.LayoutParams button = new LinearLayout.LayoutParams(-2, -2);
        button.topMargin = 26;
        overlay.addView(retry, button);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
    }

    private void showStartupError(String message) {
        Log.e(TAG, "STARTUP_ERROR: " + message);
        handler.post(() -> {
            if (ready || overlay == null || status == null || !alive) return;
            overlay.setVisibility(View.VISIBLE);
            status.setText("ПЕПЕЛ И КАРТЫ\n\n" + message + "\n\nВерсия 0.12 — диагностика запуска");
            if (retry != null) retry.setVisibility(View.VISIBLE);
        });
    }

    private void callback(String function, String content) {
        if (!alive) return;
        runOnUiThread(() -> {
            if (web != null) web.evaluateJavascript("window." + function + "(" + JSONObject.quote(content) + ")", null);
        });
    }

    public class NetworkBridge {
        @JavascriptInterface public void ready() {
            Log.i(TAG, "ASHES_READY: JavaScript game initialized");
            runOnUiThread(() -> {
                ready = true;
                if (overlay != null) overlay.setVisibility(View.GONE);
            });
        }
        @JavascriptInterface public void error(String message) {
            runOnUiThread(() -> showStartupError("Ошибка игрового скрипта: " + String.valueOf(message)));
        }

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
        if (web != null && ready) {
            web.evaluateJavascript("window.goBack && window.goBack()", null);
        } else {
            finish();
        }
    }

    @Override protected void onDestroy() {
        alive = false;
        activeTopic = "";
        activeBearer = "";
        handler.removeCallbacksAndMessages(null);
        pool.shutdownNow();
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
