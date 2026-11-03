package com.ehtid.p2pchat;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class InternetEngine {
    public interface Listener {
        void onStatus(String text);
        void onMessages(List<JSONObject> messages);
        void onParticipants(List<Member> members);
        void onIncoming(String nickname, String message);
    }
    private static final int MAX_HISTORY = 100;
    private static final long HISTORY_COOLDOWN_MS = RoomPolicy.HISTORY_COOLDOWN_MS;
    private static final String DEFAULT_RELAY = "https://ntfy.sh";
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newCachedThreadPool();
    private final ExecutorService sender = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService clock = Executors.newSingleThreadScheduledExecutor();
    private final SharedPreferences prefs;
    private final Listener listener;
    private final String room, id, topic, relay;
    private final Map<String, JSONObject> pending = new LinkedHashMap<>();
    private volatile long nextRelaySendAt;
    private volatile int sendFailures;
    private final SecretKeySpec aes;
    private final SecureRandom random = new SecureRandom();
    private final List<JSONObject> messages = new ArrayList<>();
    private final Set<String> seen = Collections.synchronizedSet(new HashSet<>());
    private final Map<String, JSONObject> waitingAcks = new ConcurrentHashMap<>();
    private final Map<String, Member> members = new ConcurrentHashMap<>();
    private final Map<String, Long> pingStart = new ConcurrentHashMap<>();
    private final Map<String, String> pingOwner = new ConcurrentHashMap<>();
    private final Map<String, Long> lastGuestHistory = new ConcurrentHashMap<>();
    private volatile boolean chatVisible = false;
    private volatile long lastOfferAt = 0L;
    private volatile long lastPresenceSent = 0L;
    private volatile int pingRoundRobin = 0;
    private volatile String myName;
    private volatile boolean showPeerPings = true;
    public static final class Member {
        public final String id, name;
        public final long lastSeen;
        public final int pingMs;
        public final boolean isMe, online;
        public Member(String id, String name, long seen, int ping, boolean me, boolean online) {
            this.id = id; this.name = name; this.lastSeen = seen;
            this.pingMs = ping; this.isMe = me; this.online = online;
        }
    }
    private final Map<String, Fragments> pieces = new HashMap<>();
    private final List<IceCandidate> queuedIce = new ArrayList<>();
    private final Map<String, List<IceCandidate>> earlyIce = new HashMap<>();
    private volatile boolean stopped, relayOnline, hostMode, guestMode;
    private volatile String remoteId, lastRelayId;
    private volatile PeerConnectionFactory factory;
    private volatile PeerConnection rtc;
    private volatile DataChannel data;
    private volatile boolean remoteDescription;
    private volatile HttpURLConnection streamConnection;

    public InternetEngine(Context context, String code, boolean join, Listener callbacks) {
        this.listener = callbacks;
        room = normalize(code);
        appContext = context.getApplicationContext();
        prefs = appContext.getSharedPreferences("internet_p2p", Context.MODE_PRIVATE);
        relay = getRelayServer(appContext);
        nextRelaySendAt = prefs.getLong("retry_until_" + relay, 0L);
        String savedId = prefs.getString("device_id", null);
        if (savedId == null) {
            savedId = UUID.randomUUID().toString();
            prefs.edit().putString("device_id", savedId).apply();
        }
        id = savedId;
        myName = prefs.getString("nick", "Пользователь");
        myName = cleanName(myName);
        members.put(id, new Member(id, myName, System.currentTimeMillis(), 0, true, false));
        if (join) prefs.edit().putString("role_" + room, "guest").apply();
        String role = prefs.getString("role_" + room, "");
        hostMode = "host".equals(role);
        guestMode = "guest".equals(role);
        byte[] digest = sha(room.getBytes(StandardCharsets.UTF_8));
        aes = new SecretKeySpec(digest, "AES");
        StringBuilder t = new StringBuilder("ehtidp2p-");
        for (int i = 0; i < 20; i++) t.append(String.format(java.util.Locale.US, "%02x", digest[i] & 0xff));
        topic = t.toString();
        try {
            JSONArray old = new JSONArray(prefs.getString("msgs_" + room, "[]"));
            for (int i = 0; i < old.length(); i++) {
                JSONObject item = old.optJSONObject(i);
                if (validMessage(item)) messages.add(item);
            }
        } catch (Exception ignored) {}
        try {
            JSONArray oldPending = new JSONArray(prefs.getString("pending_" + room, "[]"));
            for (int i = 0; i < oldPending.length() && i < 100; i++) {
                JSONObject msg = oldPending.optJSONObject(i);
                if (validMessage(msg) && id.equals(msg.optString("from"))) {
                    put(msg, "delivery", "pending");
                    pending.put(msg.optString("id"), msg);
                }
            }
        } catch (Exception ignored) {}
    }

    public static String getRelayServer(Context context) {
        String raw = context.getSharedPreferences("ui_settings",Context.MODE_PRIVATE)
                .getString("relay_url",DEFAULT_RELAY);
        if (raw == null) return DEFAULT_RELAY;
        raw = raw.trim().replaceAll("/+$","");
        try {
            java.net.URI uri = new java.net.URI(raw);
            if (!"https".equalsIgnoreCase(uri.getScheme()) ||
                    uri.getHost() == null || uri.getHost().isEmpty() ||
                    uri.getRawUserInfo() != null || uri.getQuery() != null ||
                    uri.getFragment() != null || uri.getPath().length() > 0)
                return DEFAULT_RELAY;
            return raw;
        } catch (Exception invalid) {
            return DEFAULT_RELAY;
        }
    }

    public static String normalize(String raw) {
        return raw.toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z2-9]", "");
    }

    public String myId() { return id; }
    public String myName() { return myName; }
    public void setName(String name) {
        myName = cleanName(name);
        prefs.edit().putString("nick", myName).apply();
        members.put(id, new Member(id, myName, System.currentTimeMillis(), 0, true, chatVisible));
        publishMembers();
        announcePresence();
    }
    /** Presence is just a roster view; never use it to close network connections. */
    public List<Member> snapshotMembers() {
        long now = System.currentTimeMillis();
        List<Member> list = new ArrayList<>();
        for (Member m : members.values()) {
            if (!m.isMe && !RoomPolicy.isPresent(now,m.lastSeen,m.online)) continue;
            list.add(new Member(m.id,m.name,m.lastSeen,m.pingMs,m.isMe,
                    m.isMe ? (chatVisible && relayOnline && now >= nextRelaySendAt) : (m.online && relayOnline)));
        }
        list.sort((a,b) -> a.isMe ? -1 : b.isMe ? 1 :
                a.online != b.online ? Boolean.compare(b.online,a.online) :
                a.name.compareToIgnoreCase(b.name));
        return list;
    }
    /** The user is online only while the chat screen is actually open. */
    public void setChatVisible(boolean visible) {
        chatVisible = visible;
        members.put(id,new Member(id,myName,System.currentTimeMillis(),0,true,visible));
        publishMembers();
        announcePresence();
    }
    private static String cleanName(String s) {
        if (s == null) return "Пользователь";
        s = s.trim().replaceAll("[\\p{Cntrl}]", "");
        if (s.length() > 24) s = s.substring(0,24);
        return s.isEmpty() ? "Пользователь" : s;
    }
    private void publishMembers() {
        List<Member> list = snapshotMembers();
        ui.post(() -> { if (!stopped) listener.onParticipants(list); });
    }
    private void markMember(String peer, String nickname) {
        markMember(peer,nickname,null);
    }
    /** Presence changes only membership data and never touches the WebRTC link. */
    private void markMember(String peer, String nickname, Boolean visible) {
        if (peer == null || peer.isEmpty() || peer.length() > 80 || peer.equals(id)) return;
        long now = System.currentTimeMillis();
        for (Map.Entry<String,Member> entry : members.entrySet())
            if (!entry.getKey().equals(id) && now-entry.getValue().lastSeen > 180000L)
                members.remove(entry.getKey(),entry.getValue());
        if (members.size() >= 64 && !members.containsKey(peer)) return;
        Member prev = members.get(peer);
        String n = nickname == null || nickname.trim().isEmpty()
                ? (prev == null ? "Гость" : prev.name) : cleanName(nickname);
        boolean wasVisible = prev != null && prev.online;
        boolean nowVisible = visible == null ? wasVisible : visible;
        members.put(peer,new Member(peer,n,now,prev==null?-1:prev.pingMs,false,nowVisible));
        publishMembers();
        // Reply to a new peer (or a returning one) immediately. Subsequent
        // heartbeats do not trigger replies, avoiding an infinite ping-pong.
        if (visible != null && RoomPolicy.shouldReplyPresence(prev!=null,wasVisible,nowVisible)) {
            announcePresence();
            if (chatVisible) pingPeer(peer);
        }
    }
    private void announcePresence() {
        if (stopped || !relayOnline) return;
        lastPresenceSent = System.currentTimeMillis();
        JSONObject p = obj("t","presence");
        put(p,"name",myName);
        put(p,"active",chatVisible);
        signal(p);
    }
    private void pingPeer(String peer) {
        if (stopped || !relayOnline || !showPeerPings) return;
        if (pingStart.size() > 12) return;
        String nonce = UUID.randomUUID().toString();
        pingStart.put(nonce,System.currentTimeMillis());
        pingOwner.put(nonce,peer);
        JSONObject p = obj("t","ping");
        put(p,"to",peer);
        put(p,"nonce",nonce);
        signal(p);
    }
    private void sendPings() {
        if (!showPeerPings || !relayOnline || !chatVisible) return;
        long now = System.currentTimeMillis();
        List<Member> available = snapshotMembers();
        available.removeIf(m -> m.isMe || !RoomPolicy.isPresent(now,m.lastSeen,m.online));
        // One rotating RTT sample instead of pinging all peers: O(1) requests
        // and no participant evictions when a public relay is rate-limited.
        if (!available.isEmpty()) {
            Member next = available.get(Math.floorMod(pingRoundRobin++,available.size()));
            pingPeer(next.id);
        }
        for (String nonce : new ArrayList<>(pingStart.keySet())) {
            Long sent = pingStart.get(nonce);
            if (sent != null && now-sent > 40000) {
                pingStart.remove(nonce); pingOwner.remove(nonce);
            }
        }
        publishMembers();
    }

    public List<JSONObject> snapshot() {
        synchronized (messages) { return new ArrayList<>(messages); }
    }

    public void start() {
        try {
            PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions
                .builder(uiContext()).createInitializationOptions());
            factory = PeerConnectionFactory.builder().createPeerConnectionFactory();
        } catch (Exception error) {
            status("WebRTC недоступен: будет использоваться интернет-реле");
        }
        io.execute(this::readRelay);
        publishMembers();
        sender.execute(this::flushPending);
        clock.scheduleAtFixedRate(() -> {
            if (!stopped) sender.execute(this::flushPending);
        }, 20, 20, TimeUnit.SECONDS);
        // ntfy.sh has a small daily publishing quota. Heartbeats are slow;
        // explicit enter/exit status is sent immediately by setChatVisible().
        clock.scheduleAtFixedRate(() -> {
            if (stopped || !relayOnline) return;
            long interval = chatVisible ? 5*60*1000L : 20*60*1000L;
            if (System.currentTimeMillis()-lastPresenceSent >= interval) announcePresence();
            publishMembers();
        }, 45, 45, TimeUnit.SECONDS);
        clock.scheduleAtFixedRate(() -> {
            if (!stopped) sendPings();
        }, 30, 15*60, TimeUnit.SECONDS);
        clock.scheduleAtFixedRate(() -> {
            if (stopped || !relayOnline) return;
            if (guestMode && !direct()) signal(obj("t", "hello"));
        }, 2, 15*60, TimeUnit.SECONDS);
    }

    // WebRTC factory needs a Context. Stored exclusively as application context.
    private Context appContext;
    private Context uiContext() {
        return appContext;
    }

    public void sendMessage(String text) {
        if (stopped || text == null || text.trim().isEmpty() || text.length() > 1000) return;
        if (queuedCount() >= 100) {
            status("Очередь переполнена; повтори отправку после восстановления связи");
            return;
        }
        if (!hostMode && !guestMode) {
            hostMode = true;
            prefs.edit().putString("role_" + room, "host").apply();
            status("Ты хост. Ожидаем второй телефон по коду комнаты…");
        }
        JSONObject msg = obj("id", UUID.randomUUID().toString());
        put(msg, "from", id);
        put(msg, "name", myName);
        put(msg, "text", text.trim());
        put(msg, "at", System.currentTimeMillis());
        put(msg, "delivery", "pending");
        append(msg);
        sendUserData(obj("t", "msg"), msg);
    }

    private void sendUserData(JSONObject packet, JSONObject msg) {
        put(packet,"m",msg);
        // Direct path reaches at most one peer. Queue relay publication so it
        // cannot vanish on 429, network loss, app restart or process recreation.
        synchronized (pending) {
            if (pending.size() >= 100) {
                status("Очередь заполнена (100 сообщений); отправка остановлена");
                return;
            }
            pending.put(msg.optString("id"), msg);
            persistPending();
        }
        sendDirect(packet);
        sender.execute(this::flushPending);
    }

    private void persistPending() {
        JSONArray all = new JSONArray();
        for (JSONObject m : pending.values()) all.put(m);
        prefs.edit().putString("pending_" + room,all.toString()).commit();
    }

    public int queuedCount() {
        synchronized (pending) { return pending.size(); }
    }

    private void flushPending() {
        if (stopped || System.currentTimeMillis() < nextRelaySendAt) return;
        List<JSONObject> snapshot;
        synchronized (pending) { snapshot = new ArrayList<>(pending.values()); }
        for (JSONObject item : snapshot) {
            if (stopped || System.currentTimeMillis() < nextRelaySendAt) break;
            JSONObject packet = obj("t","msg");
            // No shared mutation: publish() attaches fresh transport fields.
            put(packet,"m",item);
            if (!publish(packet)) break;
            synchronized (pending) {
                pending.remove(item.optString("id"));
                persistPending();
            }
            updateDelivery(item.optString("id"),"relay");
        }
    }

    private void updateDelivery(String messageId, String state) {
        synchronized (messages) {
            for (JSONObject item : messages) {
                if (messageId.equals(item.optString("id"))) {
                    put(item,"delivery",state);
                    break;
                }
            }
            JSONArray store = new JSONArray();
            for (JSONObject item : messages) store.put(item);
            prefs.edit().putString("msgs_" + room,store.toString()).apply();
        }
        List<JSONObject> list = snapshot();
        ui.post(() -> { if (!stopped) listener.onMessages(list); });
    }

    private void readRelay() {
        int failures = 0;
        while (!stopped) {
            HttpURLConnection conn = null;
            try {
                String suffix = lastRelayId == null ? "30m" : lastRelayId;
                String endpoint = relay + "/" + topic + "/json?since=" +
                        java.net.URLEncoder.encode(suffix, "UTF-8");
                conn = (HttpURLConnection) new URL(endpoint).openConnection();
                streamConnection = conn;
                conn.setConnectTimeout(12000);
                conn.setReadTimeout(75000);
                conn.setRequestProperty("Accept", "application/x-ndjson");
                int http = conn.getResponseCode();
                if (http != 200) throw new java.io.IOException("HTTP " + http);
                relayOnline = true;
                failures = 0;
                publishMembers();
                if (System.currentTimeMillis() < nextRelaySendAt) {
                    status("Реле ограничило отправку: " + queuedCount() +
                            " сообщений ожидают повтора");
                } else {
                    status(direct() ? "P2P напрямую • шифрование включено" :
                            "Интернет-реле подключено • сообщения шифруются");
                }
                if (guestMode) signal(obj("t", "hello"));
                announcePresence();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(
                        conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while (!stopped && (line = br.readLine()) != null) {
                        if (line.length() > 18000) continue;
                        JSONObject event;
                        try { event = new JSONObject(line); } catch (Exception ignored) { continue; }
                        if (!"message".equals(event.optString("event"))) continue;
                        String eventId = event.optString("id");
                        if (eventId.length() > 0) lastRelayId = eventId;
                        receiveFragment(event.optString("message"));
                    }
                }
            } catch (Exception error) {
                if (!stopped) {
                    failures++;
                    relayOnline = false;
                    publishMembers();
                    status("Нет интернет-реле: " + error.getMessage() +
                            " • повторное подключение");
                }
            } finally {
                if (conn != null) conn.disconnect();
                if (streamConnection == conn) streamConnection = null;
            }
            if (stopped) break;
            try { Thread.sleep(Math.min(12000, 1000L + failures * 1800L)); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
    }

    private void receiveFragment(String wire) {
        if (wire == null || wire.isEmpty() || wire.length() > 12000) return;
        if (wire.startsWith("frag1:")) {
            String[] tokens = wire.split(":", 5);
            if (tokens.length != 5 || tokens[1].length() > 40) return;
            int index, total;
            try {
                index = Integer.parseInt(tokens[2]);
                total = Integer.parseInt(tokens[3]);
            } catch (Exception e) { return; }
            if (total < 2 || total > 24 || index < 0 || index >= total) return;
            String collected = null;
            synchronized (pieces) {
                if (pieces.size() > 40) pieces.clear();
                Fragments f = pieces.get(tokens[1]);
                if (f == null || f.parts.length != total) {
                    f = new Fragments(total);
                    pieces.put(tokens[1], f);
                }
                f.parts[index] = tokens[4];
                if (f.complete()) {
                    StringBuilder buffer = new StringBuilder();
                    for (String part : f.parts) buffer.append(part);
                    pieces.remove(tokens[1]);
                    collected = buffer.toString();
                }
            }
            if (collected != null) decodeEnvelope(collected);
        } else decodeEnvelope(wire);
    }

    private void decodeEnvelope(String wire) {
        try {
            String[] split = wire.split("\\.", 3);
            if (split.length != 3 || !"v1".equals(split[0])) return;
            byte[] iv = b64decode(split[1]);
            if (iv.length != 12) return;
            byte[] encrypted = b64decode(split[2]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, aes, new GCMParameterSpec(128, iv));
            byte[] plain = cipher.doFinal(encrypted);
            if (plain.length > 32000) return;
            JSONObject packet = new JSONObject(new String(plain, StandardCharsets.UTF_8));
            String pid = packet.optString("id");
            String from = packet.optString("from");
            if (pid.length() > 80 || pid.isEmpty() || from.isEmpty() || from.equals(id)) return;
            if (!seen.add(pid)) return;
            if (seen.size() > 2000) seen.clear();
            String type = packet.optString("t");
            long ts = packet.optLong("ts");
            if (!"msg".equals(type) && !"sync".equals(type)) {
                long maxAge = "presence".equals(type) ? 120000L : 180000L;
                if (Math.abs(System.currentTimeMillis() - ts) > maxAge) return;
            }
            String to = packet.optString("to");
            if (!to.isEmpty() && !to.equals(id)) return;
            if ("ping".equals(type) || "pong".equals(type) || "hello".equals(type))
                markMember(from, packet.optString("name"));
            if ("msg".equals(type) && Math.abs(System.currentTimeMillis()-ts)<120000L) {
                // Messages act as presence while chatting, reducing heartbeat posts.
                Boolean senderVisible = packet.has("active") ? packet.optBoolean("active", false) : null;
                markMember(from,packet.optString("name"),senderVisible);
            }
            switch (type) {
                case "presence":
                    markMember(from,packet.optString("name"),packet.optBoolean("active",false));
                    break;
                case "ping":
                    JSONObject pong = obj("t", "pong");
                    put(pong,"to",from);
                    put(pong,"nonce",packet.optString("nonce"));
                    signal(pong);
                    break;
                case "pong":
                    String nonce = packet.optString("nonce");
                    Long sent = pingStart.remove(nonce);
                    String who = pingOwner.remove(nonce);
                    if (sent != null && from.equals(who)) {
                        int rtt = (int)Math.max(0,Math.min(60000,System.currentTimeMillis()-sent));
                        Member old = members.get(from);
                        if (old != null) {
                            members.put(from,new Member(from,old.name,System.currentTimeMillis(),rtt,false,old.online));
                            publishMembers();
                        }
                    }
                    break;
                case "hello":
                    if (hostMode) onGuestHello(from);
                    break;
                case "offer":
                    if (!hostMode) acceptOffer(from, packet.optString("sdp"));
                    break;
                case "answer":
                    if (hostMode && from.equals(remoteId))
                        setAnswer(packet.optString("sdp"));
                    break;
                case "ice":
                    addIce(packet, from);
                    break;
                case "msg":
                    JSONObject incoming = packet.optJSONObject("m");
                    if (append(incoming)) incomingAlert(incoming);
                    break;
                case "sync":
                    JSONArray data = packet.optJSONArray("data");
                    if (data != null) for (int i = 0; i < data.length(); i++)
                        append(data.optJSONObject(i));
                    break;
                default: break;
            }
        } catch (Exception ignored) {
            // Malformed or unauthenticated packets are silently discarded.
        }
    }

    private void onGuestHello(String guest) {
        if (!hostMode || stopped) return;
        long now = System.currentTimeMillis();
        Long previous = lastGuestHistory.get(guest);
        if (RoomPolicy.shouldSyncHistory(previous,now)) {
            lastGuestHistory.put(guest,now);
            relayHistory(guest);
        }
        // The room is multi-user, while this WebRTC implementation is 1:1.
        // Keep the first active direct connection; additional guests stay on
        // the room-wide encrypted relay instead of evicting that participant.
        if (factory == null) return;
        if (!RoomPolicy.mayNegotiate(rtc != null,direct(),now-lastOfferAt)) return;
        startOffer(guest);
    }

    private void relayHistory(String guest) {
        List<JSONObject> history = snapshot();
        if (history.isEmpty()) return;
        io.execute(() -> {
            JSONArray batch = new JSONArray();
            int size = 0;
            int start = Math.max(0, history.size() - 40);
            for (int i = start; i < history.size() && !stopped; i++) {
                JSONObject m = history.get(i);
                int len = m.toString().length();
                if (batch.length() > 0 && size + len > 1800) {
                    sendHistoryBatch(guest, batch);
                    batch = new JSONArray();
                    size = 0;
                    try { Thread.sleep(150); } catch (InterruptedException ignored) {}
                }
                batch.put(m);
                size += len;
            }
            if (batch.length() > 0) sendHistoryBatch(guest, batch);
        });
    }

    private void sendHistoryBatch(String guest, JSONArray batch) {
        JSONObject signal = obj("t", "sync");
        put(signal, "to", guest);
        put(signal, "data", batch);
        signal(signal);
    }

    private void signal(JSONObject packet) {
        if (stopped) return;
        sender.execute(() -> publish(packet));
    }

    private boolean publish(JSONObject packet) {
        if (stopped || System.currentTimeMillis() < nextRelaySendAt) return false;
        put(packet, "id", UUID.randomUUID().toString());
        put(packet, "from", id);
        put(packet, "name", myName);
        put(packet, "active", chatVisible);
        put(packet, "ts", System.currentTimeMillis());
        String wire;
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aes, new GCMParameterSpec(128, iv));
            byte[] bytes = cipher.doFinal(packet.toString().getBytes(StandardCharsets.UTF_8));
            wire = "v1." + b64(iv) + "." + b64(bytes);
        } catch (Exception e) {
            status("Ошибка шифрования: сообщение сохранено в очереди");
            return false;
        }
        boolean isMessage = "msg".equals(packet.optString("t"));
        if (wire.length() <= 2350) return post(wire, isMessage);
        String token = UUID.randomUUID().toString().replace("-", "");
        int count = (wire.length() + 2099) / 2100;
        if (count > 24) {
            status("Слишком большой пакет: сообщение оставлено в очереди");
            return false;
        }
        for (int i = 0; i < count && !stopped; i++) {
            int begin = i * 2100;
            String frame = "frag1:" + token + ":" + i + ":" + count + ":" +
                    wire.substring(begin, Math.min(begin + 2100, wire.length()));
            if (!post(frame, isMessage)) return false;
        }
        return !stopped;
    }

    private boolean post(String text, boolean cache) {
        if (stopped || System.currentTimeMillis() < nextRelaySendAt) return false;
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(relay + "/" + topic).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
            conn.setRequestProperty("Cache", cache ? "yes" : "no");
            conn.setRequestProperty("Priority", "1");
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = conn.getOutputStream()) { out.write(bytes); }
            int response = conn.getResponseCode();
            if (response >= 200 && response < 300) {
                sendFailures = 0;
                return true;
            }

            String retryAfter = conn.getHeaderField("Retry-After");
            long delay = RelayRetryPolicy.retryDelayMs(response,retryAfter,sendFailures++);
            nextRelaySendAt = System.currentTimeMillis() + delay;
            prefs.edit().putLong("retry_until_" + relay,nextRelaySendAt).apply();
            publishMembers();
            if (response == 429) {
                long minutes = (delay + 59999L) / 60000L;
                status("HTTP 429: лимит публичного реле. Повтор через ~" +
                        Math.max(1,minutes) + " мин; " + queuedCount() + " в очереди");
            } else {
                status("Реле HTTP " + response + ": повторная отправка позже, очередь " +
                        queuedCount());
            }
            return false;
        } catch (Exception e) {
            long delay = RelayRetryPolicy.retryDelayMs(503,null,sendFailures++);
            nextRelaySendAt = System.currentTimeMillis() + delay;
            prefs.edit().putLong("retry_until_" + relay,nextRelaySendAt).apply();
            if (!stopped) status("Нет связи с реле: " + queuedCount() +
                    " в очереди, повтор через " + Math.max(1,(delay+999L)/1000L) + " с");
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private synchronized void startOffer(String guest) {
        if (stopped || !hostMode || factory == null) return;
        if (direct()) return;
        resetRtc();
        lastOfferAt = System.currentTimeMillis();
        remoteId = guest;
        PeerConnection pc = newPeer();
        if (pc == null) return;
        rtc = pc;
        DataChannel.Init init = new DataChannel.Init();
        init.ordered = true;
        DataChannel local = pc.createDataChannel("ehtid-chat", init);
        attach(local);
        pc.createOffer(new SdpObserver() {
            @Override public void onCreateSuccess(SessionDescription description) {
                pc.setLocalDescription(new BasicSdpObserver() {
                    @Override public void onSetSuccess() {
                        JSONObject packet = obj("t", "offer");
                        put(packet, "to", guest);
                        put(packet, "sdp", description.description);
                        signal(packet);
                    }
                }, description);
            }
            @Override public void onSetSuccess() {}
            @Override public void onCreateFailure(String s) { status("Ошибка WebRTC offer: " + s); }
            @Override public void onSetFailure(String s) {}
        }, new MediaConstraints());
        status("Устанавливаем прямое P2P-соединение…");
    }

    private synchronized void acceptOffer(String from, String sdp) {
        if (stopped || factory == null || sdp.isEmpty()) return;
        // Another participant entering the room must never replace this call.
        if (!RoomPolicy.mayNegotiate(rtc != null,direct(),System.currentTimeMillis()-lastOfferAt)) return;
        resetRtc();
        lastOfferAt = System.currentTimeMillis();
        remoteId = from;
        synchronized (queuedIce) {
            List<IceCandidate> early = earlyIce.remove(from);
            if (early != null) queuedIce.addAll(early);
        }
        PeerConnection pc = newPeer();
        if (pc == null) return;
        rtc = pc;
        pc.setRemoteDescription(new BasicSdpObserver() {
            @Override public void onSetSuccess() {
                remoteDescription = true;
                flushIce();
                pc.createAnswer(new SdpObserver() {
                    @Override public void onCreateSuccess(SessionDescription answer) {
                        pc.setLocalDescription(new BasicSdpObserver() {
                            @Override public void onSetSuccess() {
                                JSONObject packet = obj("t", "answer");
                                put(packet, "to", from);
                                put(packet, "sdp", answer.description);
                                signal(packet);
                            }
                        }, answer);
                    }
                    @Override public void onCreateFailure(String s) { status("Ошибка WebRTC answer: " + s); }
                    @Override public void onSetSuccess() {}
                    @Override public void onSetFailure(String s) {}
                }, new MediaConstraints());
            }
            @Override public void onSetFailure(String s) {
                status("Ошибка SDP: " + s + " • остаётся интернет-реле");
            }
        }, new SessionDescription(SessionDescription.Type.OFFER, sdp));
        status("Соединяем телефоны напрямую…");
    }

    private void setAnswer(String sdp) {
        PeerConnection pc = rtc;
        if (pc == null || sdp.isEmpty()) return;
        pc.setRemoteDescription(new BasicSdpObserver() {
            @Override public void onSetSuccess() {
                remoteDescription = true;
                flushIce();
            }
        }, new SessionDescription(SessionDescription.Type.ANSWER, sdp));
    }

    private PeerConnection newPeer() {
        List<PeerConnection.IceServer> servers = Arrays.asList(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun.cloudflare.com:3478").createIceServer()
        );
        PeerConnection.RTCConfiguration config = new PeerConnection.RTCConfiguration(servers);
        config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        PeerConnection result = factory.createPeerConnection(config, new PeerConnection.Observer() {
            @Override public void onSignalingChange(PeerConnection.SignalingState state) {}
            @Override public void onIceConnectionChange(PeerConnection.IceConnectionState state) {
                if (state == PeerConnection.IceConnectionState.FAILED ||
                        state == PeerConnection.IceConnectionState.DISCONNECTED) {
                    if (!direct()) status("Прямой канал недоступен • работает шифрованное реле");
                    // Do not reset peers asynchronously when other guests join.
                    // A failed attempt can be replaced after its offer timeout.
                }
            }
            @Override public void onIceConnectionReceivingChange(boolean state) {}
            @Override public void onIceGatheringChange(PeerConnection.IceGatheringState state) {}
            @Override public void onIceCandidate(IceCandidate candidate) {
                if (stopped || remoteId == null) return;
                JSONObject ice = obj("t", "ice");
                put(ice, "to", remoteId);
                put(ice, "sdpMid", candidate.sdpMid);
                put(ice, "index", candidate.sdpMLineIndex);
                put(ice, "candidate", candidate.sdp);
                signal(ice);
            }
            @Override public void onIceCandidatesRemoved(IceCandidate[] candidates) {}
            @Override public void onAddStream(MediaStream mediaStream) {}
            @Override public void onRemoveStream(MediaStream mediaStream) {}
            @Override public void onDataChannel(DataChannel dc) { attach(dc); }
            @Override public void onRenegotiationNeeded() {}
            @Override public void onAddTrack(RtpReceiver receiver, MediaStream[] streams) {}
        });
        if (result == null) status("WebRTC не поддерживается • работает реле");
        return result;
    }

    private void addIce(JSONObject packet, String from) {
        IceCandidate candidate = new IceCandidate(packet.optString("sdpMid"),
                packet.optInt("index"), packet.optString("candidate"));
        if (candidate.sdp.isEmpty()) return;
        synchronized (queuedIce) {
            PeerConnection peer = rtc;
            if (peer == null || !from.equals(remoteId)) {
                // ICE candidates may arrive before SDP due to asynchronous HTTP signaling.
                if (!hostMode) {
                    List<IceCandidate> batch = earlyIce.get(from);
                    if (batch == null) {
                        batch = new ArrayList<>();
                        if (earlyIce.size() > 8) earlyIce.clear();
                        earlyIce.put(from, batch);
                    }
                    if (batch.size() < 64) batch.add(candidate);
                }
                return;
            }
            if (remoteDescription) peer.addIceCandidate(candidate);
            else if (queuedIce.size() < 64) queuedIce.add(candidate);
        }
    }

    private void flushIce() {
        synchronized (queuedIce) {
            PeerConnection peer = rtc;
            if (peer == null) return;
            for (IceCandidate candidate : queuedIce) peer.addIceCandidate(candidate);
            queuedIce.clear();
        }
    }

    private void attach(DataChannel dc) {
        if (dc == null) return;
        data = dc;
        dc.registerObserver(new DataChannel.Observer() {
            @Override public void onBufferedAmountChange(long previousAmount) {}
            @Override public void onStateChange() {
                if (dc.state() == DataChannel.State.OPEN) {
                    status("P2P напрямую • WebRTC DTLS шифрование");
                    if (hostMode) directHistory();
                } else if (dc.state() == DataChannel.State.CLOSED) {
                    if (!stopped) status("Резервное шифрованное интернет-реле");
                }
            }
            @Override public void onMessage(DataChannel.Buffer buffer) {
                try {
                    ByteBuffer src = buffer.data.slice();
                    if (src.remaining() > 10000) return;
                    byte[] bytes = new byte[src.remaining()];
                    src.get(bytes);
                    JSONObject packet = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
                    String type = packet.optString("t");
                    if ("msg".equals(type)) {
                        JSONObject msg = packet.optJSONObject("m");
                        if (validMessage(msg)) {
                            if (append(msg)) incomingAlert(msg);
                            JSONObject ack = obj("t", "ack");
                            put(ack, "messageId", msg.optString("id"));
                            sendDirect(ack);
                        }
                    } else if ("ack".equals(type)) {
                        waitingAcks.remove(packet.optString("messageId"));
                    } else if ("sync".equals(type)) {
                        JSONArray batch = packet.optJSONArray("data");
                        if (batch != null) for (int i = 0; i < batch.length(); i++)
                            append(batch.optJSONObject(i));
                    }
                } catch (Exception ignored) {}
            }
        });
    }

    private void directHistory() {
        List<JSONObject> all = snapshot();
        JSONArray batch = new JSONArray();
        int chars = 0;
        for (JSONObject item : all) {
            int n = item.toString().length();
            if (batch.length() > 0 && chars + n > 5000) {
                JSONObject packet = obj("t", "sync");
                put(packet, "data", batch);
                sendDirect(packet);
                batch = new JSONArray();
                chars = 0;
            }
            batch.put(item);
            chars += n;
        }
        if (batch.length() > 0) {
            JSONObject packet = obj("t", "sync");
            put(packet, "data", batch);
            sendDirect(packet);
        }
    }

    private boolean sendDirect(JSONObject packet) {
        DataChannel channel = data;
        if (channel == null || channel.state() != DataChannel.State.OPEN) return false;
        try {
            return channel.send(new DataChannel.Buffer(ByteBuffer.wrap(
                    packet.toString().getBytes(StandardCharsets.UTF_8)), false));
        } catch (Exception e) { return false; }
    }

    private boolean direct() {
        DataChannel channel = data;
        return channel != null && channel.state() == DataChannel.State.OPEN;
    }

    private synchronized void resetRtc() {
        synchronized (queuedIce) {
            queuedIce.clear();
            remoteDescription = false;
        }
        DataChannel oldData = data;
        data = null;
        if (oldData != null) {
            try { oldData.close(); oldData.dispose(); } catch (Exception ignored) {}
        }
        PeerConnection oldPeer = rtc;
        rtc = null;
        if (oldPeer != null) {
            try { oldPeer.close(); oldPeer.dispose(); } catch (Exception ignored) {}
        }
        remoteId = null;
    }

    private boolean append(JSONObject item) {
        if (!validMessage(item) || stopped) return false;
        synchronized (messages) {
            for (JSONObject old : messages)
                if (old.optString("id").equals(item.optString("id"))) return false;
            messages.add(item);
            while (messages.size() > MAX_HISTORY) messages.remove(0);
            JSONArray store = new JSONArray();
            for (JSONObject m : messages) store.put(m);
            prefs.edit().putString("msgs_" + room, store.toString()).apply();
        }
        List<JSONObject> list = snapshot();
        ui.post(() -> { if (!stopped) listener.onMessages(list); });
        return true;
    }
    private void incomingAlert(JSONObject m) {
        if (m == null || m.optString("from").equals(id)) return;
        String nickname = cleanName(m.optString("name", "Гость"));
        if (Math.abs(System.currentTimeMillis() - m.optLong("at",0)) > 120000) return;
        markMember(m.optString("from"), nickname);
        String message = m.optString("text");
        ui.post(() -> { if (!stopped) listener.onIncoming(nickname, message); });
    }

    private static boolean validMessage(JSONObject item) {
        return item != null &&
                item.optString("id").length() > 0 && item.optString("id").length() <= 80 &&
                item.optString("from").length() > 0 && item.optString("from").length() <= 80 &&
                item.optString("text").length() > 0 && item.optString("text").length() <= 1000;
    }
    private static JSONObject obj(String key, Object value) {
        JSONObject j = new JSONObject();
        put(j, key, value);
        return j;
    }
    private static void put(JSONObject j, String key, Object value) {
        try { j.put(key, value); } catch (Exception ignored) {}
    }
    private static byte[] sha(byte[] value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }
    private static String b64(byte[] value) {
        return Base64.encodeToString(value, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    }
    private static byte[] b64decode(String value) {
        return Base64.decode(value, Base64.URL_SAFE | Base64.NO_WRAP);
    }
    private void status(String value) {
        ui.post(() -> { if (!stopped) listener.onStatus(value); });
    }
    public void stop() {
        stopped = true;
        relayOnline = false;
        if (streamConnection != null) streamConnection.disconnect();
        resetRtc();
        if (factory != null) {
            try { factory.dispose(); } catch (Exception ignored) {}
            factory = null;
        }
        io.shutdownNow();
        sender.shutdownNow();
        clock.shutdownNow();
    }
    private static class Fragments {
        final String[] parts;
        Fragments(int count) { parts = new String[count]; }
        boolean complete() {
            for (String x : parts) if (x == null) return false;
            return true;
        }
    }
    private static class BasicSdpObserver implements SdpObserver {
        @Override public void onCreateSuccess(SessionDescription sdp) {}
        @Override public void onSetSuccess() {}
        @Override public void onCreateFailure(String error) {}
        @Override public void onSetFailure(String error) {}
    }
}
