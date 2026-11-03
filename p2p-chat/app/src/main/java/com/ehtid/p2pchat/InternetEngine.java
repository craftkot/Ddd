        try {
            PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions
                .builder(uiContext()).createInitializationOptions());
            factory = PeerConnectionFactory.builder().createPeerConnectionFactory();
        } catch (Exception error) {
            status(meshMode ? "WebRTC недоступен: P2P не запустился" :
                    "WebRTC недоступен: будет использоваться интернет-реле");
        }
        if (!meshMode && relay.isEmpty()) {
            status("Укажи HTTPS-адрес домашнего сервера через кнопку «Сервер ПК»");
            publishMembers();
            return;
        }
        if (meshMode) {
            try {
                PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions
                    .builder(uiContext()).createInitializationOptions());
                factory = PeerConnectionFactory.builder().createPeerConnectionFactory();
            } catch (Exception error) {
                status("WebRTC недоступен: P2P не запустился");
            }
        }
        if (meshMode) {
            if (factory==null) return;
            PeerMesh p = new PeerMesh(factory,room,id,myName,chatVisible,
                    new PeerMesh.Callbacks() {
                        @Override public void status(String value) {
                            InternetEngine.this.status(value);
                        }
                        @Override public void signalReady(String code,String type) {
                            ui.post(()->{ if(!stopped) listener.onManualSignal(code,type); });
                        }
                        @Override public void incomingMessage(JSONObject item) {
                            if (append(item)) incomingAlert(item);
                        }
                        @Override public void peerPresence(String peer,String name,boolean active,int ping) {
                            markMember(peer,name,active);
                        }
                        @Override public void peerPing(String peer,int ping) {
                            Member old=members.get(peer);
                            if (old!=null) {
                                members.put(peer,new Member(peer,old.name,System.currentTimeMillis(),
                                        ping,false,old.online));
                                publishMembers();
                            }
                        }
                        @Override public void peerDisconnected(String peer) {
                            Member old=members.get(peer);
                            if (old!=null) {
                                members.put(peer,new Member(peer,old.name,System.currentTimeMillis(),
                                        -1,false,false));
                                publishMembers();
                            }
                        }
                        @Override public void peerConnected(List<String> forwardedHistoryIds) {
                            if (forwardedHistoryIds!=null) {
                                for (String msgId:forwardedHistoryIds) {
                                    synchronized (messages) {
                                        for (JSONObject m:messages) {
                                            if (msgId.equals(m.optString("id")) &&
                                                    id.equals(m.optString("from")) &&
                                                    "pending".equals(m.optString("delivery"))) {
                                                updateDelivery(msgId,"p2p");
                                                break;
                                            }
                                        }
                                    }
                                    synchronized (pending) {
                                        if (pending.remove(msgId)!=null) persistPending();
                                    }
                                }
                            }
                            publishMembers();
                        }
                        @Override public List<JSONObject> history() { return snapshot(); }
                    });
            mesh=p;
            p.start();
            publishMembers();
            return;
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
            long interval = chatVisible ? 30*60*1000L : 90*60*1000L;
            // No heartbeat publications for an empty room.
            if (snapshotMembers().size() > 1 &&
                    System.currentTimeMillis()-lastPresenceSent >= interval)
                announcePresence();
            publishMembers();
        }, 45, 45, TimeUnit.SECONDS);
        clock.scheduleAtFixedRate(() -> {
            if (!stopped) sendPings();
        }, 30, 60*60, TimeUnit.SECONDS);
        clock.scheduleAtFixedRate(() -> {
            if (stopped || !relayOnline) return;
            if (guestMode && !direct() && snapshotMembers().size() > 1 &&
                    System.currentTimeMillis() - lastHelloSent > 4*60*60*1000L)
                sendHello();
        }, 2, 4*60*60, TimeUnit.SECONDS);
    }

    public boolean isMeshMode() { return meshMode; }
    public int meshPeerCount() {
        PeerMesh p=mesh;
        return p==null ? 0 : p.connectedCount();
    }
    public void makeMeshOffer() {
        PeerMesh p=mesh;
        if (p!=null) p.createOffer();
        else status("P2P не запущен: выбери режим P2P в настройках");
    }
    public void importMeshCode(String code) {
        PeerMesh p=mesh;
        if (p!=null) p.importSignal(code);
        else status("P2P не запущен");
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
        if (meshMode) {
            PeerMesh p=mesh;
            if (p!=null && p.sendMessage(msg)) updateDelivery(msg.optString("id"),"p2p");
            else status("P2P: сообщение сохранено. Подключите хотя бы один телефон.");
        } else sendUserData(obj("t","msg"),msg);
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
                conn.setReadTimeout(180000);
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
                            "Сервер чата подключён • сообщения шифруются");
                }
                // The HTTP stream reconnects after idle timeouts. Do not send
                // presence/hello every time: that rapidly exhausts ntfy quota.
                if (guestMode && System.currentTimeMillis()-lastHelloSent > 4*60*60*1000L)
                    sendHello();
                if (System.currentTimeMillis()-lastPresenceSent > 30*60*1000L)
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
                    status("Нет связи с сервером чата: " + error.getMessage() +
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
        if (!id.equals(item.optString("from"))) item.remove("delivery");
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
        PeerMesh current=mesh;
        mesh=null;
        if (current!=null) current.close();
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
