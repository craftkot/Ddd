package com.ehtid.p2pchat;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class PeerEngine {
    public interface Listener {
        void onStatus(String text);
        void onMessages(List<JSONObject> messages);
    }
    private static final String SERVICE_TYPE = "_ehtidp2p._tcp.";
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService workers = Executors.newCachedThreadPool();
    private final ScheduledExecutorService beats = Executors.newSingleThreadScheduledExecutor();
    private final SharedPreferences prefs;
    private final NsdManager nsd;
    private final WifiManager.MulticastLock multicast;
    private final Listener listener;
    private final List<JSONObject> messages = new ArrayList<>();
    private final List<JSONObject> pending = new ArrayList<>();
    private final Set<Peer> guests = ConcurrentHashMap.newKeySet();
    private final String id;
    private final String serviceName;

    private volatile boolean stopped = false;
    private volatile boolean hosting = false;
    private volatile boolean discovering = false;
    private volatile boolean resolving = false;
    private volatile boolean synced = false;
    private volatile ServerSocket server;
    private volatile Peer host;
    private NsdManager.DiscoveryListener searchListener;
    private NsdManager.RegistrationListener registerListener;
    private volatile boolean registered;

    public PeerEngine(Context context, Listener listener) {
        this.listener = listener;
        prefs = context.getSharedPreferences("chat", Context.MODE_PRIVATE);
        String savedId = prefs.getString("id", null);
        if (savedId == null) {
            savedId = UUID.randomUUID().toString();
            prefs.edit().putString("id", savedId).apply();
        }
        id = savedId;
        serviceName = "EHTID-" + id.substring(0, 8);
        nsd = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        multicast = wifi != null ? wifi.createMulticastLock("EHTID-P2P-NSD") : null;
        if (multicast != null) multicast.setReferenceCounted(false);
        try {
            JSONArray old = new JSONArray(prefs.getString("messages", "[]"));
            for (int i = 0; i < old.length(); i++) {
                JSONObject m = old.optJSONObject(i);
                if (m != null && isValid(m)) messages.add(m);
            }
        } catch (Exception ignored) {}
    }

    public String myId() { return id; }
    public List<JSONObject> snapshot() {
        synchronized (messages) { return new ArrayList<>(messages); }
    }
    public void start() {
        if (multicast != null) {
            try { multicast.acquire(); } catch (Exception ignored) {}
        }
        state("Поиск собеседника в Wi-Fi…");
        startDiscovery();
        beats.scheduleAtFixedRate(() -> {
            if (stopped) return;
            JSONObject ping = packet("ping", null);
            Peer current = host;
            if (current != null && !current.send(ping)) current.close();
            for (Peer peer : guests) if (!peer.send(ping)) peer.close();
        }, 8, 8, TimeUnit.SECONDS);
    }

    public void send(String text) {
        if (stopped || text == null || text.trim().isEmpty()) return;
        text = text.trim();
        if (text.length() > 1200) return;
        JSONObject message = new JSONObject();
        try {
            message.put("id", UUID.randomUUID().toString());
            message.put("from", id);
            message.put("text", text);
            message.put("at", System.currentTimeMillis());
        } catch (Exception ignored) { return; }
        append(message);
        Peer peer = host;
        if (hosting) {
            broadcast(message, null);
        } else if (peer != null && synced) {
            if (!peer.send(packet("msg", message))) {
                synchronized (messages) { pending.add(message); }
                peer.close();
            }
        } else if (resolving || peer != null) {
            synchronized (messages) { pending.add(message); }
            state("Подключаемся, сообщение в очереди…");
        } else {
            becomeHost();
        }
    }

    private void becomeHost() {
        if (hosting || stopped) return;
        hosting = true;
        resolving = false;
        stopDiscovery();
        state("Создаём P2P-чат…");
        workers.execute(() -> {
            try {
                ServerSocket socket = new ServerSocket(0);
                server = socket;
                ui.post(() -> registerService(socket.getLocalPort()));
                state("Хост запущен. Ждём второй телефон…");
                while (!stopped && !socket.isClosed()) {
                    Socket incoming = socket.accept();
                    incoming.setKeepAlive(true);
                    incoming.setSoTimeout(30000);
                    Peer peer = new Peer(incoming);
                    guests.add(peer);
                    if (!peer.send(syncPacket())) {
                        guests.remove(peer);
                        peer.close();
                        continue;
                    }
                    state("Собеседник подключён напрямую");
                    workers.execute(() -> readLoop(peer, true));
                }
            } catch (IOException failure) {
                if (!stopped) state("Не удалось создать хост: " + failure.getMessage());
            }
        });
    }

    private void registerService(int port) {
        if (stopped || !hosting) return;
        NsdServiceInfo info = new NsdServiceInfo();
        info.setServiceName(serviceName);
        info.setServiceType(SERVICE_TYPE);
        info.setPort(port);
        registerListener = new NsdManager.RegistrationListener() {
            @Override public void onServiceRegistered(NsdServiceInfo s) { registered = true; }
            @Override public void onRegistrationFailed(NsdServiceInfo s, int code) {
                state("Хост открыт, но автообнаружение недоступно (" + code + ")");
            }
            @Override public void onServiceUnregistered(NsdServiceInfo s) { registered = false; }
            @Override public void onUnregistrationFailed(NsdServiceInfo s, int code) { registered = false; }
        };
        try {
            nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, registerListener);
        } catch (Exception e) {
            state("Ошибка обнаружения: " + e.getMessage());
        }
    }

    private void startDiscovery() {
        ui.post(() -> {
            if (stopped || hosting || host != null || discovering) return;
            discovering = true;
            searchListener = new NsdManager.DiscoveryListener() {
                @Override public void onDiscoveryStarted(String name) {}
                @Override public void onServiceFound(NsdServiceInfo info) {
                    if (stopped || hosting || host != null || resolving) return;
                    if (!info.getServiceType().startsWith("_ehtidp2p._tcp")) return;
                    if (serviceName.equals(info.getServiceName())) return;
                    resolving = true;
                    state("Найден чат. Подключение…");
                    try {
                        nsd.resolveService(info, new NsdManager.ResolveListener() {
                            @Override public void onResolveFailed(NsdServiceInfo s, int error) {
                                failConnect();
                            }
                            @Override public void onServiceResolved(NsdServiceInfo s) {
                                workers.execute(() -> connect(s));
                            }
                        });
                    } catch (Exception e) { failConnect(); }
                }
                @Override public void onServiceLost(NsdServiceInfo info) {}
                @Override public void onDiscoveryStopped(String name) { discovering = false; }
                @Override public void onStartDiscoveryFailed(String name, int error) {
                    discovering = false;
                    state("Не удалось найти сеть (" + error + ")");
                    retryDiscovery();
                }
                @Override public void onStopDiscoveryFailed(String name, int error) {
                    discovering = false;
                }
            };
            try {
                nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, searchListener);
            } catch (Exception e) {
                discovering = false;
                state("Проверьте Wi-Fi: " + e.getMessage());
                retryDiscovery();
            }
        });
    }

    private void connect(NsdServiceInfo info) {
        if (stopped || hosting) return;
        try {
            if (info.getHost() == null || info.getPort() <= 0) {
                failConnect(); return;
            }
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(info.getHost(), info.getPort()), 5000);
            socket.setKeepAlive(true);
            socket.setSoTimeout(30000);
            Peer peer = new Peer(socket);
            if (hosting || stopped) { peer.close(); return; }
            host = peer;
            synced = false;
            stopDiscovery();
            state("Соединение установлено. Получаем сообщения…");
            readLoop(peer, false);
        } catch (Exception e) {
            failConnect();
        }
    }

    private void readLoop(Peer peer, boolean serverSide) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    peer.socket.getInputStream(), StandardCharsets.UTF_8));
            String line;
            while (!stopped && (line = reader.readLine()) != null) {
                if (line.length() > 250000) break;
                JSONObject packet;
                try { packet = new JSONObject(line); } catch (Exception ignored) { continue; }
                String type = packet.optString("type");
                if ("ping".equals(type)) {
                    peer.send(packet("pong", null));
                } else if ("msg".equals(type)) {
                    JSONObject message = packet.optJSONObject("data");
                    if (isValid(message)) {
                        append(message);
                        if (serverSide) broadcast(message, peer);
                    }
                } else if ("sync".equals(type) && !serverSide) {
                    JSONArray received = packet.optJSONArray("data");
                    if (received != null) applySync(received, peer);
                }
            }
        } catch (IOException ignored) {
        } finally {
            peer.close();
            if (serverSide) {
                guests.remove(peer);
                if (guests.isEmpty() && hosting && !stopped)
                    state("Хост работает. Ждём собеседника…");
            } else if (host == peer) {
                host = null;
                synced = false;
                resolving = false;
                if (!stopped && !hosting) {
                    state("Связь потеряна. Повторный поиск…");
                    retryDiscovery();
                }
            }
        }
    }

    private void applySync(JSONArray array, Peer peer) {
        List<JSONObject> queued;
        synchronized (messages) {
            queued = new ArrayList<>(pending);
            messages.clear();
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (isValid(item)) addUniqueLocked(item);
            }
            for (JSONObject item : queued) addUniqueLocked(item);
            pending.clear();
            saveLocked();
        }
        synced = true;
        showMessages();
        state("Подключено напрямую • без сервера");
        for (JSONObject item : queued) {
            if (!peer.send(packet("msg", item))) {
                synchronized (messages) { pending.add(item); }
                peer.close();
                break;
            }
        }
    }

    private void failConnect() {
        resolving = false;
        if (!stopped && !hosting) {
            state("Соединение не удалось. Повторный поиск…");
            retryDiscovery();
        }
    }

    private void retryDiscovery() {
        ui.postDelayed(() -> {
            if (stopped || hosting || host != null) return;
            stopDiscovery();
            ui.postDelayed(this::startDiscovery, 650);
        }, 1800);
    }
    private void stopDiscovery() {
        ui.post(() -> {
            if (!discovering || searchListener == null) return;
            NsdManager.DiscoveryListener current = searchListener;
            searchListener = null;
            discovering = false;
            try { nsd.stopServiceDiscovery(current); } catch (Exception ignored) {}
        });
    }

    private JSONObject syncPacket() {
        JSONArray data = new JSONArray();
        synchronized (messages) {
            for (JSONObject message : messages) data.put(message);
        }
        return packet("sync", data);
    }
    private JSONObject packet(String type, Object data) {
        JSONObject result = new JSONObject();
        try {
            result.put("type", type);
            if (data != null) result.put("data", data);
        } catch (Exception ignored) {}
        return result;
    }
    private boolean isValid(JSONObject m) {
        if (m == null) return false;
        String text = m.optString("text");
        return !text.trim().isEmpty() && text.length() <= 1200 &&
                m.optString("id").length() <= 80 &&
                !m.optString("id").isEmpty() &&
                m.optString("from").length() <= 80 &&
                !m.optString("from").isEmpty();
    }
    private void append(JSONObject message) {
        if (!isValid(message)) return;
        synchronized (messages) {
            addUniqueLocked(message);
            saveLocked();
        }
        showMessages();
    }
    private void addUniqueLocked(JSONObject m) {
        String id = m.optString("id");
        for (JSONObject old : messages) {
            if (id.equals(old.optString("id"))) return;
        }
        messages.add(m);
        while (messages.size() > 100) messages.remove(0);
    }
    private void saveLocked() {
        JSONArray array = new JSONArray();
        for (JSONObject m : messages) array.put(m);
        prefs.edit().putString("messages", array.toString()).apply();
    }
    private void showMessages() {
        List<JSONObject> copy = snapshot();
        ui.post(() -> { if (!stopped) listener.onMessages(copy); });
    }
    private void state(String text) {
        ui.post(() -> { if (!stopped) listener.onStatus(text); });
    }
    private void broadcast(JSONObject msg, Peer exclude) {
        JSONObject wrapped = packet("msg", msg);
        for (Peer peer : guests) {
            if (peer == exclude) continue;
            if (!peer.send(wrapped)) peer.close();
        }
    }
    public void stop() {
        if (stopped) return;
        stopped = true;
        stopDiscovery();
        ui.post(() -> {
            if (registered && registerListener != null) {
                try { nsd.unregisterService(registerListener); } catch (Exception ignored) {}
            }
        });
        try { if (server != null) server.close(); } catch (IOException ignored) {}
        if (host != null) host.close();
        for (Peer p : guests) p.close();
        guests.clear();
        workers.shutdownNow();
        beats.shutdownNow();
        if (multicast != null && multicast.isHeld()) {
            try { multicast.release(); } catch (Exception ignored) {}
        }
    }

    private static final class Peer {
        final Socket socket;
        final BufferedWriter writer;
        Peer(Socket socket) throws IOException {
            this.socket = socket;
            this.writer = new BufferedWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.UTF_8));
        }
        boolean send(JSONObject data) {
            try {
                synchronized (writer) {
                    writer.write(data.toString());
                    writer.newLine();
                    writer.flush();
                }
                return true;
            } catch (IOException e) {
                return false;
            }
        }
        void close() {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }
}
