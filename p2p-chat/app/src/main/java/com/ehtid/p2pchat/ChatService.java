package com.ehtid.p2pchat;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
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
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        refreshSettings();
        String requested = intent == null ? room : intent.getStringExtra(EXTRA_ROOM);
        if (requested != null && requested.matches("[A-Z2-9]{16,32}") &&
                (engine == null || !room.equals(requested))) {
            if (engine != null) engine.stop();
            room = requested;
            status = "Подключение…";
            messages = new ArrayList<>();
            members = new ArrayList<>();
            engine = new InternetEngine(getApplicationContext(), room,
                    intent.getBooleanExtra(EXTRA_JOIN, false), this);
            engine.setChatVisible(visible);
            engine.start();
            emit();
        }
        return START_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return binder; }

    public void setUiListener(InternetEngine.Listener listener) {
        this.ui = listener;
        if (listener != null) emit();
    }

    /** Foreground presence controls the roster only, not the socket/service. */
    public void setVisible(boolean on) {
        visible = on;
        if (engine != null) engine.setChatVisible(on);
    }
    public String currentRoom() { return room; }
    public String myId() { return engine == null ? "" : engine.myId(); }

    public void send(String text) {
        if (engine != null) engine.sendMessage(text);
    }
    public void changeNickname(String name) {
        if (engine != null) engine.setName(name);
    }
    /** Reconnect to the user-selected ntfy-compatible relay, retaining room/history. */
    public void restartRelay() {
        if (room.isEmpty()) return;
        if (engine != null) engine.stop();
        status = "Переподключаем интернет-реле…";
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

    @Override public void onIncoming(String nickname, String message) {
        if (ui != null) ui.onIncoming(nickname, message);
        SharedPreferences options = getSharedPreferences("ui_settings", MODE_PRIVATE);
        if (visible || !options.getBoolean("notifications", true)) return;
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            return;

        boolean preview = options.getBoolean("preview", true);
        Notification.Builder b = builder(options.getBoolean("sound", true) ? CHANNEL_MESSAGE : CHANNEL_SILENT)
                .setSmallIcon(R.drawable.ic_chat_notification)
                .setContentTitle(preview ? nickname : "Новое сообщение в EHTID")
                .setContentText(preview ? message : "Откройте приложение, чтобы прочитать")
                .setContentIntent(openIntent())
                .setAutoCancel(true);
        if (Build.VERSION.SDK_INT < 26) {
            if (options.getBoolean("sound", true)) b.setDefaults(Notification.DEFAULT_SOUND);
            b.setPriority(Notification.PRIORITY_HIGH);
        }
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(
                2000 + Math.abs((int)(System.nanoTime() % 50)), b.build());
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
