package com.ehtid.p2pchat;

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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * No message broker. Every connected phone forwards room events over its
 * WebRTC DataChannels. Initial session offer/answer is exchanged out-of-band
 * (clipboard, SMS, another chat, etc). STUN is only used for ICE discovery.
 *
 * WARNING: Without an existing route and without TURN, restrictive carrier
 * NATs cannot always connect. This is a best-effort experimental mesh.
 */
public final class PeerMesh {
    public interface Callbacks {
        void status(String message);
        void signalReady(String token, String type);
        void incomingMessage(JSONObject message);
        void peerPresence(String peerId, String name, boolean visible, int pingMs);
        void peerPing(String peerId, int pingMs);
        void peerDisconnected(String peerId);
        void peerConnected();
        List<JSONObject> history();
    }

    private static final String PREFIX = "EHTIDM1:";
    private static final int MAX_TOKEN_BYTES = 60000;
    private static final int MAX_LINKS = 16;
    private static final int MAX_HOPS = 8;
    private final PeerConnectionFactory factory;
    private final String roomFingerprint, selfId;
    private final Callbacks cb;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Map<String, Link> links = new HashMap<>();
    private final Set<String> recentIds = Collections.synchronizedSet(new HashSet<>());
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor();
    private volatile String myName;
    private volatile boolean visible;
    private volatile boolean closed;

    private static final class Link {
        final String session;
        final PeerConnection pc;
        volatile DataChannel channel;
        volatile boolean active;
        volatile boolean emitted;
        volatile String remoteId, remoteName;
        volatile long pingSent;
        final List<IceCandidate> gathered = new ArrayList<>();

        Link(String session, PeerConnection peer) {
            this.session = session;
            pc = peer;
        }
    }

    public PeerMesh(PeerConnectionFactory factory, String room, String id,
                    String name, boolean active, Callbacks callbacks) {
        this.factory = factory;
        selfId = id;
        myName = name;
        visible = active;
        cb = callbacks;
        roomFingerprint = hex(sha(room.getBytes(StandardCharsets.UTF_8))).substring(0,32);
    }

    public void start() {
        heartbeat.scheduleAtFixedRate(() -> {
            if (closed) return;
            announce();
            sendPings();
        }, 30, 30, TimeUnit.SECONDS);
        status("P2P без сервера • обменяйтесь приглашением и ответом");
    }

    public void setVisible(boolean isVisible) {
        visible = isVisible;
        announce();
    }

    public void rename(String nickname) {
        myName = nickname;
        announce();
    }

    public int connectedCount() {
        synchronized (links) {
            int n = 0;
            for (Link l : links.values()) if (isOpen(l)) n++;
            return n;
        }
    }

    public void createOffer() {
        if (closed) return;
        Link link = newLink(UUID.randomUUID().toString());
        if (link == null) return;
        DataChannel.Init config = new DataChannel.Init();
        config.ordered = true;
        attach(link,link.pc.createDataChannel("ehtid-mesh",config));
        link.pc.createOffer(new SdpObserver() {
            @Override public void onCreateSuccess(SessionDescription sdp) {
                if (closed) return;
                link.pc.setLocalDescription(new SimpleSdp() {
                    @Override public void onSetSuccess() {
                        status("Собираем сетевые адреса для приглашения…");
                        maybeEmit(link,"offer");
                    }
                },sdp);
            }
            @Override public void onCreateFailure(String error) { status("Ошибка создания приглашения: "+error); }
            @Override public void onSetSuccess() {}
            @Override public void onSetFailure(String error) { status("Ошибка SDP: "+error); }
        },new MediaConstraints());
    }

    /** Accepts an offer or answer string, and emits the answer when ICE gathering completes. */
    public void importSignal(String text) {
        if (closed) return;
        final JSONObject token;
        try { token=decode(text); }
        catch (Exception problem) { status("Код подключения повреждён или слишком большой"); return; }
        if (!roomFingerprint.equals(token.optString("room"))) {
            status("Этот код от другой комнаты. Сначала войдите по коду комнаты.");
            return;
        }
        final String session=token.optString("session");
        final String type=token.optString("type");
        final String remote=token.optString("from");
        final String sdp=token.optString("sdp");
        if (session.length()<8 || session.length()>80 || remote.isEmpty() ||
                remote.equals(selfId) || sdp.length()<20 || sdp.length()>45000) {
            status("Неверное приглашение P2P"); return;
        }
        if ("offer".equals(type)) {
            Link link=newLink(session);
            if (link==null) return;
            link.remoteId=remote;
            link.remoteName=token.optString("name","Участник");
            link.pc.setRemoteDescription(new SimpleSdp() {
                @Override public void onSetSuccess() {
                    addRemoteIce(link,token);
                    link.pc.createAnswer(new SdpObserver() {
                        @Override public void onCreateSuccess(SessionDescription answer) {
                            link.pc.setLocalDescription(new SimpleSdp() {
                                @Override public void onSetSuccess() {
                                    maybeEmit(link,"answer");
                                    status("Ответ готов. Отправьте его создателю приглашения.");
                                }
                            },answer);
                        }
                        @Override public void onCreateFailure(String err) { status("Ошибка P2P-ответа: "+err); }
                        @Override public void onSetSuccess() {}
                        @Override public void onSetFailure(String err) { status("Ошибка SDP: "+err); }
                    },new MediaConstraints());
                }
                @Override public void onSetFailure(String err) { status("Ошибка приглашения: "+err); }
            },new SessionDescription(SessionDescription.Type.OFFER,sdp));
        } else if ("answer".equals(type)) {
            Link link;
            synchronized (links) { link=links.get(session); }
            if (link==null) {
                status("Не найдено исходное приглашение. Создайте новое и повторите обмен.");
                return;
            }
            if (link.remoteId != null && !link.remoteId.equals(remote)) {
                status("Ответ получен от другого участника"); return;
            }
            link.remoteId=remote;
            link.remoteName=token.optString("name","Участник");
            link.pc.setRemoteDescription(new SimpleSdp() {
                @Override public void onSetSuccess() {
                    addRemoteIce(link,token);
                    status("Ответ принят, устанавливается прямое соединение…");
                }
                @Override public void onSetFailure(String err) { status("Не удалось применить ответ P2P: "+err); }
            },new SessionDescription(SessionDescription.Type.ANSWER,sdp));
        } else {
            status("Неизвестный тип кода подключения");
        }
    }

    /** Each phone can create multiple Link objects; any phone can relay to others. */
    private Link newLink(String session) {
        synchronized (links) {
            if (links.containsKey(session)) {
                status("Это приглашение уже используется");
                return null;
            }
            if (links.size()>=MAX_LINKS) {
                status("Максимум "+MAX_LINKS+" прямых соединений на устройство");
                return null;
            }
        }
        List<PeerConnection.IceServer> servers=Arrays.asList(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun.cloudflare.com:3478").createIceServer());
        PeerConnection.RTCConfiguration config=new PeerConnection.RTCConfiguration(servers);
        config.sdpSemantics=PeerConnection.SdpSemantics.UNIFIED_PLAN;
        final Link[] capture=new Link[1];
        PeerConnection pc=factory.createPeerConnection(config,new PeerConnection.Observer() {
            @Override public void onSignalingChange(PeerConnection.SignalingState s) {}
            @Override public void onIceConnectionReceivingChange(boolean s) {}
            @Override public void onIceGatheringChange(PeerConnection.IceGatheringState s) {
                if (s==PeerConnection.IceGatheringState.COMPLETE) {
                    Link l=capture[0];
                    if (l!=null) {
                        SessionDescription local=l.pc.getLocalDescription();
                        if (local!=null)
                            maybeEmit(l,local.type==SessionDescription.Type.OFFER?"offer":"answer");
                    }
                }
            }
            @Override public void onIceConnectionChange(PeerConnection.IceConnectionState s) {
                Link l=capture[0];
                if (l==null) return;
                if (s==PeerConnection.IceConnectionState.FAILED) {
                    status("Прямое соединение не удалось: возможно, мобильный CGNAT. Нужен доступный P2P-узел или TURN.");
                } else if (s==PeerConnection.IceConnectionState.DISCONNECTED ||
                        s==PeerConnection.IceConnectionState.CLOSED) {
                    peerLeft(l);
                }
            }
            @Override public void onIceCandidate(IceCandidate candidate) {
                Link l=capture[0];
                if (l!=null) synchronized (l.gathered) {
                    if (l.gathered.size()<128) l.gathered.add(candidate);
                }
            }
            @Override public void onIceCandidatesRemoved(IceCandidate[] candidates) {}
            @Override public void onAddStream(MediaStream stream) {}
            @Override public void onRemoveStream(MediaStream stream) {}
            @Override public void onDataChannel(DataChannel channel) {
                Link l=capture[0];
                if (l!=null) attach(l,channel);
            }
            @Override public void onRenegotiationNeeded() {}
            @Override public void onAddTrack(RtpReceiver receiver,MediaStream[] streams) {}
        });
        if (pc==null) {
            status("WebRTC недоступен на устройстве");
            return null;
        }
        Link link=new Link(session,pc);
        capture[0]=link;
        synchronized (links) { links.put(session,link); }
        return link;
    }

    private void maybeEmit(Link l,String type) {
        if (closed || l.emitted) return;
        SessionDescription local=l.pc.getLocalDescription();
        if (local==null || local.type==null) return;
        if (l.pc.iceGatheringState()!=PeerConnection.IceGatheringState.COMPLETE) return;
        l.emitted=true;
        try {
            JSONObject token=new JSONObject();
            token.put("v",1);
            token.put("type",type);
            token.put("session",l.session);
            token.put("room",roomFingerprint);
            token.put("from",selfId);
            token.put("name",myName);
            token.put("sdp",local.description);
            JSONArray ice=new JSONArray();
            synchronized (l.gathered) {
                for (IceCandidate c:l.gathered) {
                    JSONObject i=new JSONObject();
                    i.put("mid",c.sdpMid);
                    i.put("idx",c.sdpMLineIndex);
                    i.put("sdp",c.sdp);
                    ice.put(i);
                }
            }
            token.put("ice",ice);
            String code=encode(token);
            if (code.length()>MAX_TOKEN_BYTES) {
                status("Приглашение слишком длинное, попробуйте другую сеть");
                return;
            }
            onUi(()->cb.signalReady(code,type));
        } catch (Exception e) {
            l.emitted=false;
            status("Ошибка подготовки P2P-кода: "+e.getMessage());
        }
    }

    private void addRemoteIce(Link l,JSONObject packet) {
        JSONArray array=packet.optJSONArray("ice");
        if (array==null) return;
        for (int i=0;i<array.length() && i<128;i++) {
            JSONObject it=array.optJSONObject(i);
            if (it==null) continue;
            String sdp=it.optString("sdp");
            if (sdp.length()>1000 || sdp.isEmpty()) continue;
            l.pc.addIceCandidate(new IceCandidate(it.optString("mid"),
                    it.optInt("idx"),sdp));
        }
    }

    private void attach(Link l,DataChannel dc) {
        if (dc==null) return;
        l.channel=dc;
        dc.registerObserver(new DataChannel.Observer() {
            @Override public void onBufferedAmountChange(long previousAmount) {}
            @Override public void onStateChange() {
                if (dc.state()==DataChannel.State.OPEN && !l.active) {
                    l.active=true;
                    status("P2P подключён: "+connectedCount()+" прямых узлов. Без ntfy.");
                    announce();
                    sendHistory(l);
                    onUi(cb::peerConnected);
                } else if (dc.state()==DataChannel.State.CLOSED) {
                    peerLeft(l);
                }
            }
            @Override public void onMessage(DataChannel.Buffer buffer) {
                if (closed || buffer.binary) return;
                ByteBuffer src=buffer.data.slice();
                if (src.remaining()>20000 || src.remaining()<2) return;
                byte[] payload=new byte[src.remaining()];
                src.get(payload);
                try { receive(l,new JSONObject(new String(payload,StandardCharsets.UTF_8))); }
                catch (Exception ignored) {}
            }
        });
    }

    public boolean sendMessage(JSONObject msg) {
        if (closed) return false;
        String eventId=msg.optString("id");
        if (eventId.isEmpty()) return false;
        recentIds.add("m:"+eventId);
        JSONObject envelope=new JSONObject();
        put(envelope,"kind","msg");
        put(envelope,"event",eventId);
        put(envelope,"hop",0);
        put(envelope,"body",msg);
        return forward(envelope,null)>0;
    }

    private void sendHistory(Link recipient) {
        List<JSONObject> history=cb.history();
        if (history==null) return;
        int n=0;
        for (JSONObject msg:history) {
            if (n++>=100) break;
            JSONObject env=new JSONObject();
            put(env,"kind","msg");
            put(env,"event",msg.optString("id"));
            put(env,"hop",0);
            put(env,"body",msg);
            send(recipient,env);
        }
        JSONObject own=presencePacket();
        send(recipient,own);
    }

    public void announce() {
        if (closed) return;
        JSONObject packet=presencePacket();
        recentIds.add("p:"+packet.optString("event"));
        forward(packet,null);
    }

    private JSONObject presencePacket() {
        JSONObject p=new JSONObject();
        put(p,"kind","presence");
        put(p,"event",UUID.randomUUID().toString());
        put(p,"hop",0);
        put(p,"id",selfId);
        put(p,"name",myName);
        put(p,"active",visible);
        return p;
    }

    private void sendPings() {
        List<Link> copy;
        synchronized (links) { copy=new ArrayList<>(links.values()); }
        for (Link l:copy) if (isOpen(l)) {
            l.pingSent=System.currentTimeMillis();
            JSONObject p=new JSONObject();
            put(p,"kind","ping");
            put(p,"nonce",l.pingSent);
            send(l,p);
        }
    }

    private void receive(Link incoming,JSONObject p) {
        String kind=p.optString("kind");
        if ("ping".equals(kind)) {
            JSONObject response=new JSONObject();
            put(response,"kind","pong");
            put(response,"nonce",p.optLong("nonce"));
            send(incoming,response);
            return;
        }
        if ("pong".equals(kind)) {
            long start=p.optLong("nonce");
            long diff=System.currentTimeMillis()-start;
            if (start>0 && diff>=0 && diff<60000 && start==incoming.pingSent &&
                    incoming.remoteId!=null) {
                final String id=incoming.remoteId;
                onUi(()->cb.peerPing(id,(int)diff));
            }
            return;
        }
        String event=p.optString("event");
        int hop=p.optInt("hop");
        if (event.isEmpty() || event.length()>80 || hop<0 || hop>=MAX_HOPS) return;
        String key=kind+":"+event;
        if (!recentIds.add(key)) return;
        if (recentIds.size()>8000) recentIds.clear();
        if ("presence".equals(kind)) {
            String id=p.optString("id");
            String name=p.optString("name");
            if (id.isEmpty() || id.length()>80 || id.equals(selfId)) return;
            // A forwarded presence belongs to a distant node, not the immediate
            // WebRTC neighbor. Never overwrite the neighbor's identity.
            if (incoming.remoteId==null) {
                incoming.remoteId=id;
                incoming.remoteName=name;
            }
            boolean isVisible=p.optBoolean("active",false);
            onUi(()->cb.peerPresence(id,name,isVisible,-1));
        } else if ("msg".equals(kind)) {
            JSONObject body=p.optJSONObject("body");
            if (body==null || body.optString("id").isEmpty() ||
                    body.optString("text").length()>1000) return;
            onUi(()->cb.incomingMessage(body));
        } else return;
        put(p,"hop",hop+1);
        forward(p,incoming);
    }

    private int forward(JSONObject packet,Link source) {
        List<Link> all;
        synchronized (links) { all=new ArrayList<>(links.values()); }
        int sent=0;
        for (Link l:all)
            if (l!=source && isOpen(l) && send(l,packet)) sent++;
        return sent;
    }

    private boolean send(Link link,JSONObject packet) {
        DataChannel dc=link.channel;
        if (dc==null || dc.state()!=DataChannel.State.OPEN) return false;
        byte[] bytes=packet.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length>20000) return false;
        try { return dc.send(new DataChannel.Buffer(ByteBuffer.wrap(bytes),false)); }
        catch (Exception ignore) { return false; }
    }

    private boolean isOpen(Link l) {
        DataChannel dc=l.channel;
        return dc!=null && dc.state()==DataChannel.State.OPEN;
    }

    private void peerLeft(Link l) {
        if (!l.active) return;
        l.active=false;
        String remote=l.remoteId;
        if (remote!=null) onUi(()->cb.peerDisconnected(remote));
        status("P2P-узел отключился. Осталось прямых соединений: "+connectedCount());
    }

    public void close() {
        closed=true;
        heartbeat.shutdownNow();
        List<Link> all;
        synchronized (links) {
            all=new ArrayList<>(links.values());
            links.clear();
        }
        for (Link l:all) {
            try { if (l.channel!=null) { l.channel.close(); l.channel.dispose(); } }
            catch (Exception ignored) {}
            try { l.pc.close(); l.pc.dispose(); }
            catch (Exception ignored) {}
        }
    }

    private void status(String text) {
        onUi(()->cb.status(text));
    }
    private void onUi(Runnable task) {
        if (!closed) ui.post(()->{ if (!closed) task.run(); });
    }
    private static void put(JSONObject target,String k,Object v) {
        try { target.put(k,v); } catch (Exception ignored) {}
    }

    private static String encode(JSONObject packet) throws Exception {
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        try (GZIPOutputStream zip=new GZIPOutputStream(output)) {
            zip.write(packet.toString().getBytes(StandardCharsets.UTF_8));
        }
        return PREFIX+Base64.encodeToString(output.toByteArray(),
                Base64.NO_WRAP|Base64.URL_SAFE|Base64.NO_PADDING);
    }

    private static JSONObject decode(String raw) throws Exception {
        if (raw==null) throw new Exception("null token");
        String trimmed=raw.trim();
        if (!trimmed.startsWith(PREFIX) || trimmed.length()>MAX_TOKEN_BYTES)
            throw new Exception("Invalid token");
        byte[] zipped=Base64.decode(trimmed.substring(PREFIX.length()),Base64.URL_SAFE|Base64.NO_PADDING);
        if (zipped.length>45000) throw new Exception("Compressed token too large");
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try (GZIPInputStream input=new GZIPInputStream(new ByteArrayInputStream(zipped))) {
            byte[] buf=new byte[2048];
            int n;
            while ((n=input.read(buf))!=-1) {
                if (out.size()+n>80000) throw new Exception("Token too large");
                out.write(buf,0,n);
            }
        }
        JSONObject result=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));
        if (result.optInt("v")!=1) throw new Exception("Wrong version");
        return result;
    }
    private static byte[] sha(byte[] data) {
        try { return MessageDigest.getInstance("SHA-256").digest(data); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    private static String hex(byte[] bytes) {
        StringBuilder out=new StringBuilder();
        for (byte b:bytes) out.append(String.format(java.util.Locale.US,"%02x",b&0xff));
        return out.toString();
    }
    private static class SimpleSdp implements SdpObserver {
        @Override public void onCreateSuccess(SessionDescription ignored) {}
        @Override public void onSetSuccess() {}
        @Override public void onCreateFailure(String error) {}
        @Override public void onSetFailure(String error) {}
    }
}
