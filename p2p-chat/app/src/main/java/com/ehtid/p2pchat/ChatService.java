package com.ehtid.p2pchat;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.RemoteInput;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import java.util.LinkedHashMap;
import java.util.Map;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

public final class ChatService extends Service implements InternetEngine.Listener {
    public static final String ACTION_STOP = "com.ehtid.p2pchat.STOP";
    public static final String ACTION_REPLY = "com.ehtid.p2pchat.REPLY";
    public static final String ACTION_READ = "com.ehtid.p2pchat.READ";
    private static final String EXTRA_REPLY_TEXT = "ehtid_reply_text";
    private static final String GROUP_ROOMS = "ehtid_rooms";
    public static final String EXTRA_ROOM = "room";
    public static final String EXTRA_JOIN = "join";
    private static final String CHANNEL_STATUS = "chat_connection";
    private static final String CHANNEL_MESSAGE = "chat_messages";
    private static final String CHANNEL_SILENT = "chat_messages_silent";
    private final IBinder binder = new LocalBinder();
    private InternetEngine engine;
    private InternetEngine.Listener ui;
    private String room = "";
    private String status = "Подключение…";
    private List<JSONObject> messages = new ArrayList<>();
    private List<InternetEngine.Member> members = new ArrayList<>();
    private boolean visible = false;
    private boolean foreground = false;
    private String lastManualCode="";
    private String lastManualKind="";
    private final Map<String,List<String>> notificationLines=new LinkedHashMap<>();

    public class LocalBinder extends Binder {
        public ChatService service() { return ChatService.this; }
    }

    @Override public void onCreate() {
        super.onCreate();
        createChannels();
    }

    public static Intent intent(android.content.Context context, String code, boolean join) {
        Intent i = new Intent(context, ChatService.class);
        i.putExtra(EXTRA_ROOM, code);
        i.putExtra(EXTRA_JOIN, join);
        return i;
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_REPLY.equals(intent.getAction())) {
            String target=intent.getStringExtra(EXTRA_ROOM);
            Bundle result=RemoteInput.getResultsFromIntent(intent);
            CharSequence answer=result==null?null:result.getCharSequence(EXTRA_REPLY_TEXT);
            if (target!=null && target.equals(room) && answer!=null &&
                    !answer.toString().trim().isEmpty() && answer.length()<=1000) {
                send(answer.toString());
                clearRoomNotifications(target);
            }
            return START_STICKY;
        }
        if (intent != null && ACTION_READ.equals(intent.getAction())) {
            String target=intent.getStringExtra(EXTRA_ROOM);
            if(target!=null && target.equals(room))clearRoomNotifications(target);
            return START_STICKY;
        }
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        refreshSettings();
        String requested = intent == null ? room : intent.getStringExtra(EXTRA_ROOM);
        switchRoom(requested,intent != null && intent.getBooleanExtra(EXTRA_JOIN,false));
        return START_STICKY;
    }

    /** Switches the single active room without destroying its local history.
     * Inactive rooms remain in SQLite; their cloud events are loaded on return. */
    public void switchRoom(String requested,boolean join) {
        if (requested == null || !requested.matches("[A-Z2-9]{16,32}") ||
                (engine != null && room.equals(requested)))return;
        InternetEngine old=engine;
        engine=null;
        if(old!=null)old.stop();
        room=requested;
        status="Подключение к комнате…";
        messages=new ArrayList<>();
        members=new ArrayList<>();
        lastManualCode="";
        lastManualKind="";
        emit(); // Clear the previous room immediately, before loading new data.
        engine=new InternetEngine(getApplicationContext(),room,join,this);
        engine.setChatVisible(visible);
        engine.start();
        emit();
    }

    @Override public IBinder onBind(Intent intent) { return binder; }

    public void setUiListener(InternetEngine.Listener listener) {
        this.ui = listener;
        if (listener != null) emit();
    }

    /** Foreground presence controls the roster only, not the socket/service. */
    public void setVisible(boolean on) {
        visible = on;
        if(on && !room.isEmpty())clearRoomNotifications(room);
        if (engine != null) engine.setChatVisible(on);
    }
    public String currentRoom() { return room; }
    public String myId() { return engine == null ? "" : engine.myId(); }

    public void send(String text) {
        if (engine != null) engine.sendMessage(text);
    }
    public void sendAttachment(org.json.JSONObject media) {
        if(engine!=null)engine.sendAttachment(media);
    }
    public boolean isPeerMesh() {
        return engine!=null && engine.isMeshMode();
    }
    public int directPeerCount() {
        return engine==null ? 0 : engine.meshPeerCount();
    }
    public void refreshPings() {
        if(engine!=null)engine.refreshPings();
    }
    public void createPeerInvitation() {
        if (engine!=null) engine.makeMeshOffer();
    }
    public void importPeerInvitation(String token) {
        if (engine!=null) engine.importMeshCode(token);
    }
    public void changeNickname(String name) {
        if (engine != null) engine.setName(name);
    }
    /** Reconnect to the user-selected ntfy-compatible relay, retaining room/history. */
    public void restartRelay() {
        if (room.isEmpty()) return;
        if (engine != null) engine.stop();
        status = "Перезапускаем сетевое соединение…";
        lastManualCode="";
        lastManualKind="";
        members = new ArrayList<>();
        engine = new InternetEngine(getApplicationContext(),room,false,this);
        engine.setChatVisible(visible);
        engine.start();
        emit();
    }
    public void refreshSettings() {
        SharedPreferences p = getSharedPreferences("ui_settings", MODE_PRIVATE);
        boolean background = p.getBoolean("background", true);
        if (background && !foreground) {
            try {
                startForeground(1100, buildOngoing());
                foreground = true;
            } catch (RuntimeException ignored) {
                // If background services are restricted, chat remains active while UI is open.
            }
        } else if (!background && foreground) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }
            foreground = false;
        }
    }

    private void emit() {
        InternetEngine.Listener l = ui;
        if (l == null) return;
        l.onStatus(status);
        l.onMessages(new ArrayList<>(messages));
        l.onParticipants(new ArrayList<>(members));
        if (!lastManualCode.isEmpty()) l.onManualSignal(lastManualCode,lastManualKind);
    }

    @Override public void onStatus(String s) {
        status = s;
        if (ui != null) ui.onStatus(s);
    }

    @Override public void onMessages(List<JSONObject> list) {
        messages = new ArrayList<>(list);
        if (ui != null) ui.onMessages(new ArrayList<>(list));
    }

    @Override public void onParticipants(List<InternetEngine.Member> list) {
        members = new ArrayList<>(list);
        if (ui != null) ui.onParticipants(new ArrayList<>(list));
    }

    @Override public void onManualSignal(String token,String kind) {
        lastManualCode=token;
        lastManualKind=kind;
        if (ui!=null) ui.onManualSignal(token,kind);
    }
    @Override public void onIncoming(String nickname, String message) {
        if (ui != null) ui.onIncoming(nickname, message);
        SharedPreferences options = getSharedPreferences("ui_settings", MODE_PRIVATE);
        if (visible || !options.getBoolean("notifications", true) || room.isEmpty()) return;
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            return;
        String current=room;
        boolean preview=options.getBoolean("preview",true);
        boolean sound=options.getBoolean("sound",true);
        boolean vibration=options.getBoolean("vibration",true);
        List<String> recent=notificationLines.get(current);
        if(recent==null) {
            recent=new ArrayList<>();
            notificationLines.put(current,recent);
        }
        String line=preview ? nickname+": "+message : "Новое сообщение";
        recent.add(line.length()>200?line.substring(0,200):line);
        while(recent.size()>5)recent.remove(0);
        Notification.InboxStyle style=new Notification.InboxStyle()
                .setBigContentTitle("EHTID • комната "+current.substring(0,Math.min(6,current.length()))+"…");
        for(String item:recent)style.addLine(item);
        int count=recent.size();
        Notification.Builder b=builder(sound?CHANNEL_MESSAGE:CHANNEL_SILENT)
                .setSmallIcon(R.drawable.ic_chat_notification)
                .setContentTitle(preview ? nickname : "EHTID • новое сообщение")
                .setContentText(preview ? message : "Откройте чат, чтобы прочитать")
                .setStyle(style).setGroup(GROUP_ROOMS)
                .setNumber(count).setOnlyAlertOnce(false)
                .setContentIntent(openIntent()).setAutoCancel(true);
        if (Build.VERSION.SDK_INT >= 24) {
            RemoteInput input=new RemoteInput.Builder(EXTRA_REPLY_TEXT)
                    .setLabel("Ответить в EHTID").build();
            b.addAction(new Notification.Action.Builder(
                    android.R.drawable.ic_menu_send,"Ответить",
                    serviceAction(ACTION_REPLY,current,true)).addRemoteInput(input).build());
        }
        b.addAction(new Notification.Action.Builder(
                android.R.drawable.ic_menu_close_clear_cancel,"Прочитано",
                serviceAction(ACTION_READ,current,false)).build());
        if(Build.VERSION.SDK_INT<26) {
            if(sound)b.setDefaults(Notification.DEFAULT_SOUND);
            if(vibration)b.setVibrate(new long[]{0,130,70,100});
            b.setPriority(Notification.PRIORITY_HIGH);
        }
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE))
                .notify(notificationId(current),b.build());
    }

    private int notificationId(String roomCode) {
        return 2000+(roomCode.hashCode() & 0x7fffffff)%100000;
    }
    private PendingIntent serviceAction(String action,String roomCode,boolean mutable) {
        Intent i=new Intent(this,ChatService.class).setAction(action)
                .putExtra(EXTRA_ROOM,roomCode);
        int flags=PendingIntent.FLAG_UPDATE_CURRENT;
        if(Build.VERSION.SDK_INT>=31) flags |= mutable ?
                PendingIntent.FLAG_MUTABLE:PendingIntent.FLAG_IMMUTABLE;
        else if(Build.VERSION.SDK_INT>=23 && !mutable)flags|=PendingIntent.FLAG_IMMUTABLE;
        int request=notificationId(roomCode)+(ACTION_REPLY.equals(action)?100000:200000);
        return PendingIntent.getService(this,request,i,flags);
    }
    private void clearRoomNotifications(String roomCode) {
        notificationLines.remove(roomCode);
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE))
                .cancel(notificationId(roomCode));
    }

    private Notification buildOngoing() {
        return builder(CHANNEL_STATUS)
                .setSmallIcon(R.drawable.ic_chat_notification)
                .setContentTitle("EHTID • чат активен")
                .setContentText("Соединение работает в фоне")
                .setOngoing(true)
                .setContentIntent(openIntent())
                .build();
    }

    private PendingIntent openIntent() {
        Intent intent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(this, 0, intent, flags);
    }

    private Notification.Builder builder(String channelId) {
        if (Build.VERSION.SDK_INT >= 26) return new Notification.Builder(this, channelId);
        return new Notification.Builder(this);
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ongoing = new NotificationChannel(CHANNEL_STATUS,
                "Подключение EHTID", NotificationManager.IMPORTANCE_LOW);
        ongoing.setDescription("Постоянное уведомление при работе чата в фоне");
        ongoing.setShowBadge(false);
        nm.createNotificationChannel(ongoing);

        NotificationChannel incoming = new NotificationChannel(CHANNEL_MESSAGE,
                "Сообщения EHTID", NotificationManager.IMPORTANCE_HIGH);
        incoming.setDescription("Уведомления о новых сообщениях");
        nm.createNotificationChannel(incoming);
        NotificationChannel silent = new NotificationChannel(CHANNEL_SILENT,
                "Сообщения без звука", NotificationManager.IMPORTANCE_HIGH);
        silent.setSound(null, null);
        silent.enableVibration(false);
        nm.createNotificationChannel(silent);
    }

    @Override public void onDestroy() {
        if (engine != null) engine.stop();
        engine = null;
        ui = null;
        super.onDestroy();
    }
}
