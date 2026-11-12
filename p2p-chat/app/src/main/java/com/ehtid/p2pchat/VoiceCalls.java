package com.ehtid.p2pchat;

import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

/** One-to-one encrypted signaling + WebRTC audio. No recordings or call history. */
public final class VoiceCalls {
    public interface Events {
        void send(JSONObject packet);
        void changed(String phase,String peerName);
    }
    private final Context context;
    private final Events events;
    private final Handler main=new Handler(Looper.getMainLooper());
    private PeerConnectionFactory factory;
    private PeerConnection connection;
    private AudioSource source;
    private AudioTrack microphone;
    private AudioManager audio;
    private int previousMode=AudioManager.MODE_NORMAL;
    private boolean previouslySpeaker;
    private String callId="",peerId="",peerName="";
    private String phase="idle";
    private final List<IceCandidate> queuedIce=new ArrayList<>();
    private boolean remoteSet;
    private int generation;

    public VoiceCalls(Context context,Events events) {
        this.context=context.getApplicationContext();
        this.events=events;
    }
    public boolean isActive(){return !"idle".equals(phase);}
    public boolean isRinging(){return "incoming".equals(phase);}
    public String phase(){return phase;}
    public String peerName(){return peerName;}
    public String peerId(){return peerId;}

    public void start(String id,String name) {
        if(isActive() || id==null || id.isEmpty() || id.length()>80)return;
        callId=UUID.randomUUID().toString();
        peerId=id; peerName=safeName(name);
        state("outgoing");
        send("invite",null);
        deadline();
    }
    public void accept() {
        if(!"incoming".equals(phase))return;
        if(!setup()) { end(); return; }
        send("accept",null);
        state("connecting");
    }
    public void decline() {
        if(!"incoming".equals(phase))return;
        send("decline",null);
        reset();
    }
    public void end() {
        if(!isActive())return;
        send("hangup",null);
        reset();
    }
    public void receive(JSONObject p) {
        String action=p.optString("action");
        String from=p.optString("from");
        String id=p.optString("callId");
        if(!id.matches("[a-f0-9-]{36}") || from.isEmpty() || from.length()>80)return;
        if("invite".equals(action)) {
            if(isActive()){
                JSONObject busy=new JSONObject();
                put(busy,"t","call");put(busy,"to",from);put(busy,"callId",id);
                put(busy,"action","busy");events.send(busy);
                return;
            }
            callId=id;peerId=from;peerName=safeName(p.optString("name"));
            state("incoming");deadline();
            return;
        }
        if(!id.equals(callId)||!from.equals(peerId)||!isActive())return;
        if("decline".equals(action)||"hangup".equals(action)||"busy".equals(action)){
            reset();
        } else if("accept".equals(action)&&"outgoing".equals(phase)) {
            if(!setup()){end();return;}
            state("connecting");
            createOffer();
        } else if("offer".equals(action) && "connecting".equals(phase)) {
            String sdp=p.optString("sdp");
            if(sdp.length()<10 || sdp.length()>15000)return;
            PeerConnection pc=connection;
            if(pc==null)return;
            pc.setRemoteDescription(new Observer() {
                @Override public void onSetSuccess() {
                    remoteSet=true;flushIce();
                    pc.createAnswer(new Observer() {
                        @Override public void onCreateSuccess(SessionDescription answer) {
                            setLocalAndSend(pc,answer,"answer");
                        }
                    },new MediaConstraints());
                }
            },new SessionDescription(SessionDescription.Type.OFFER,sdp));
        } else if("answer".equals(action)&&"connecting".equals(phase)) {
            String sdp=p.optString("sdp");
            if(sdp.length()<10||sdp.length()>15000)return;
            if(connection!=null)connection.setRemoteDescription(new Observer(){
                @Override public void onSetSuccess(){remoteSet=true;flushIce();}
            },new SessionDescription(SessionDescription.Type.ANSWER,sdp));
        } else if("ice".equals(action)&&"connecting".equals(phase) || "ice".equals(action)&&"connected".equals(phase)){
            String candidate=p.optString("candidate");
            if(candidate.length()<5||candidate.length()>4000)return;
            IceCandidate ice=new IceCandidate(p.optString("mid"),p.optInt("index"),candidate);
            if(remoteSet && connection!=null)connection.addIceCandidate(ice);
            else if(queuedIce.size()<64)queuedIce.add(ice);
        }
    }
    private void flushIce(){
        PeerConnection pc=connection;
        if(pc!=null)for(IceCandidate ice:queuedIce)pc.addIceCandidate(ice);
        queuedIce.clear();
    }
    private void createOffer(){
        PeerConnection pc=connection;
        if(pc==null)return;
        pc.createOffer(new Observer(){
            @Override public void onCreateSuccess(SessionDescription offer){
                setLocalAndSend(pc,offer,"offer");
            }
        },new MediaConstraints());
    }
    private void setLocalAndSend(PeerConnection pc,SessionDescription sdp,String action){
        pc.setLocalDescription(new Observer(){
            @Override public void onSetSuccess(){send(action,sdp.description);}
        },sdp);
    }
    private boolean setup(){
        try {
            PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions
                    .builder(context).createInitializationOptions());
            factory=PeerConnectionFactory.builder().createPeerConnectionFactory();
            source=factory.createAudioSource(new MediaConstraints());
            microphone=factory.createAudioTrack("EHTID_AUDIO",source);
            microphone.setEnabled(true);
            audio=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
            if(audio!=null){
                previousMode=audio.getMode();
                previouslySpeaker=audio.isSpeakerphoneOn();
                audio.setMode(AudioManager.MODE_IN_COMMUNICATION);
                audio.setSpeakerphoneOn(false);
            }
            List<PeerConnection.IceServer> stun=Arrays.asList(
                    PeerConnection.IceServer.builder("stun:stun.cloudflare.com:3478").createIceServer(),
                    PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
            PeerConnection.RTCConfiguration config=new PeerConnection.RTCConfiguration(stun);
            config.sdpSemantics=PeerConnection.SdpSemantics.UNIFIED_PLAN;
            String activeId=callId;
            connection=factory.createPeerConnection(config,new PeerConnection.Observer(){
                @Override public void onSignalingChange(PeerConnection.SignalingState s){}
                @Override public void onIceConnectionReceivingChange(boolean b){}
                @Override public void onIceGatheringChange(PeerConnection.IceGatheringState s){}
                @Override public void onIceCandidate(IceCandidate ice){
                    if(!activeId.equals(callId))return;
                    JSONObject p=packet("ice");
                    put(p,"mid",ice.sdpMid);put(p,"index",ice.sdpMLineIndex);
                    put(p,"candidate",ice.sdp);events.send(p);
                }
                @Override public void onIceCandidatesRemoved(IceCandidate[] ice){}
                @Override public void onIceConnectionChange(PeerConnection.IceConnectionState s){
                    main.post(()->{
                        if(!activeId.equals(callId))return;
                        if(s==PeerConnection.IceConnectionState.CONNECTED ||
                           s==PeerConnection.IceConnectionState.COMPLETED)state("connected");
                        else if(s==PeerConnection.IceConnectionState.FAILED ||
                           s==PeerConnection.IceConnectionState.CLOSED){end();}
                    });
                }
                @Override public void onAddStream(MediaStream stream){}
                @Override public void onRemoveStream(MediaStream stream){}
                @Override public void onDataChannel(org.webrtc.DataChannel dc){}
                @Override public void onRenegotiationNeeded(){}
                @Override public void onAddTrack(RtpReceiver receiver,MediaStream[] streams){
                    if(receiver.track() instanceof AudioTrack)
                        ((AudioTrack)receiver.track()).setEnabled(true);
                }
            });
            if(connection==null){reset();return false;}
            connection.addTrack(microphone,Collections.singletonList("ehtid-voice"));
            return true;
        } catch(Exception e){reset();return false;}
    }
    private void state(String next){
        phase=next;
        events.changed(phase,peerName);
    }
    private void send(String action,String sdp){
        JSONObject p=packet(action);
        if(sdp!=null)put(p,"sdp",sdp);
        events.send(p);
    }
    private JSONObject packet(String action){
        JSONObject p=new JSONObject();
        put(p,"t","call");put(p,"action",action);
        put(p,"to",peerId);put(p,"callId",callId);
        return p;
    }
    private static void put(JSONObject p,String key,Object value){
        try{p.put(key,value);}catch(Exception ignored){}
    }
    private static String safeName(String name) {
        if(name==null||name.trim().isEmpty())return "Участник";
        return name.length()>40?name.substring(0,40):name;
    }
    private void deadline(){
        int marker=++generation;
        main.postDelayed(()->{
            if(marker==generation && !"connected".equals(phase) && isActive())end();
        },45000L);
    }
    private void reset(){
        generation++;
        PeerConnection pc=connection;connection=null;
        if(pc!=null){try{pc.close();pc.dispose();}catch(Exception ignored){}}
        if(microphone!=null){microphone.dispose();microphone=null;}
        if(source!=null){source.dispose();source=null;}
        if(factory!=null){factory.dispose();factory=null;}
        if(audio!=null){
            audio.setSpeakerphoneOn(previouslySpeaker);
            audio.setMode(previousMode);
            audio=null;
        }
        queuedIce.clear();remoteSet=false;
        callId="";peerId="";
        state("idle");
    }
    public void close(){reset();}
    private static class Observer implements SdpObserver {
        @Override public void onCreateSuccess(SessionDescription sdp){}
        @Override public void onSetSuccess(){}
        @Override public void onCreateFailure(String s){}
        @Override public void onSetFailure(String s){}
    }
}
