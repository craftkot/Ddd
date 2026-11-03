package com.ehtid.p2pchat;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONObject;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(11, 17, 29);
    private static final int SURFACE = Color.rgb(24, 33, 51);
    private static final int PANEL = Color.rgb(34, 45, 67);
    private static final int ACCENT = Color.rgb(66, 151, 255);
    private static final int WHITE = Color.rgb(241, 246, 255);
    private static final int MUTED = Color.rgb(157, 172, 196);
    private static final int ONLINE = Color.rgb(88, 223, 174);
    private EditText input, joinInput;
    private TextView status, codeLabel, profileLabel, membersLabel;
    private LinearLayout feed, membersPanel;
    private ScrollView scroll, membersScroll;
    private ChatService service;
    private boolean bound;
    private boolean bindRequested;
    private boolean active;
    private String code, myId = "";
    private final List<InternetEngine.Member> currentMembers = new ArrayList<>();
    private final SharedPreferences.OnSharedPreferenceChangeListener unused = null;
    private final InternetEngine.Listener callbacks = new InternetEngine.Listener() {
        @Override public void onStatus(String s) {
            status.setText("● " + s);
        }
        @Override public void onMessages(List<JSONObject> messages) { draw(messages); }
        @Override public void onParticipants(List<InternetEngine.Member> members) {
            currentMembers.clear();
            currentMembers.addAll(members);
            drawMembers();
        }
        @Override public void onIncoming(String nick, String text) {}
    };
    private final ServiceConnection conn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((ChatService.LocalBinder)binder).service();
            bound = true;
            myId = service.myId();
            service.setVisible(true);
            service.setUiListener(callbacks);
            status.setText("● Служба чата подключена");
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            service = null;
            bound = false;
            status.setText("● Служба чата отключена");
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        code = getPreferences(MODE_PRIVATE).getString("room", "");
        if (!code.matches("[A-Z2-9]{16,32}")) code = newCode();
        buildUI();
        if (profile().getString("nick", "").trim().isEmpty()) {
            askName(true);
        } else {
            launchChat(code,false);
            maybeAskNotification();
        }
    }

    private SharedPreferences profile() {
        return getSharedPreferences("internet_p2p",MODE_PRIVATE);
    }
    private SharedPreferences options() {
        return getSharedPreferences("ui_settings",MODE_PRIVATE);
    }

    private void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(13), dp(14), dp(11));
        root.setBackgroundColor(BG);

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_chat_logo);
        heading.addView(logo,new LinearLayout.LayoutParams(dp(54), dp(54)));
        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0,-2,1);
        bp.leftMargin = dp(8);
        heading.addView(brand,bp);
        TextView title = text("EHTID  CHAT",22,WHITE,true);
        title.setLetterSpacing(0.07f);
        brand.addView(title);
        profileLabel = text(profile().getString("nick",""),12,MUTED,false);
        brand.addView(profileLabel);
        TextView settings = button("⚙",PANEL,21);
        heading.addView(settings,new LinearLayout.LayoutParams(dp(48),dp(45)));
        settings.setOnClickListener(v -> settingsDialog());
        root.addView(heading);

        LinearLayout roomBox = new LinearLayout(this);
        roomBox.setOrientation(LinearLayout.VERTICAL);
        roomBox.setPadding(dp(12),dp(10),dp(12),dp(12));
        roomBox.setBackground(round(SURFACE,16));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1,-2);
        rp.topMargin = dp(13);
        root.addView(roomBox,rp);

        TextView hint = text("КОМНАТА  ·  КОД ДОСТУПА",11,MUTED,true);
        hint.setLetterSpacing(0.06f);
        roomBox.addView(hint);
        codeLabel = text(code,18,WHITE,true);
        codeLabel.setLetterSpacing(0.04f);
        codeLabel.setTextIsSelectable(true);
        codeLabel.setPadding(0,dp(5),0,dp(9));
        roomBox.addView(codeLabel);

        LinearLayout actions = new LinearLayout(this);
        roomBox.addView(actions);
        TextView copy = button("Копировать",ACCENT,13);
        actions.addView(copy);
        copy.setOnClickListener(v -> {
            ((ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE))
                    .setPrimaryClip(ClipData.newPlainText("EHTID room",code));
            status.setText("● Код скопирован");
        });
        TextView newRoom = button("Новая комната",PANEL,13);
        LinearLayout.LayoutParams nr = new LinearLayout.LayoutParams(-2,-2);
        nr.leftMargin = dp(8);
        actions.addView(newRoom,nr);
        newRoom.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Новая комната")
                .setMessage("Переключение отключит текущую комнату на этом телефоне. Продолжить?")
                .setNegativeButton("Отмена",null)
                .setPositiveButton("Создать",(d,w) -> launchChat(newCode(),false))
                .show());

        LinearLayout join = new LinearLayout(this);
        join.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams jp = new LinearLayout.LayoutParams(-1,-2);
        jp.topMargin = dp(10);
        roomBox.addView(join,jp);
        joinInput = new EditText(this);
        joinInput.setTextSize(14);
        joinInput.setTextColor(WHITE);
        joinInput.setHintTextColor(MUTED);
        joinInput.setHint("Код другого пользователя");
        joinInput.setSingleLine(true);
        joinInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        joinInput.setPadding(dp(8),dp(4),dp(8),dp(4));
        joinInput.setBackground(round(PANEL,11));
        join.addView(joinInput,new LinearLayout.LayoutParams(0,dp(45),1));
        TextView joinButton = button("Войти",ACCENT,13);
        LinearLayout.LayoutParams jb = new LinearLayout.LayoutParams(-2,dp(45));
        jb.leftMargin = dp(7);
        join.addView(joinButton,jb);
        joinButton.setOnClickListener(v -> {
            String target = InternetEngine.normalize(joinInput.getText().toString());
            if (!target.matches("[A-Z2-9]{16,32}")) {
                status.setText("● Код: 16–32 символа латиницы и цифры 2–9");
                return;
            }
            if (target.equals(code)) return;
            joinInput.setText("");
            launchChat(target,true);
        });

        LinearLayout memberCard = new LinearLayout(this);
        memberCard.setOrientation(LinearLayout.VERTICAL);
        memberCard.setBackground(round(SURFACE,14));
        memberCard.setPadding(dp(12),dp(10),dp(12),dp(9));
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1,-2);
        mp.topMargin = dp(10);
        root.addView(memberCard,mp);
        membersLabel = text("УЧАСТНИКИ · 0 В СЕТИ",11,MUTED,true);
        membersLabel.setLetterSpacing(0.05f);
        memberCard.addView(membersLabel);
        membersPanel = new LinearLayout(this);
        membersPanel.setOrientation(LinearLayout.VERTICAL);
        membersScroll = new ScrollView(this);
        membersScroll.setFillViewport(false);
        membersScroll.setVerticalScrollBarEnabled(true);
        membersScroll.addView(membersPanel,new ScrollView.LayoutParams(-1,-2));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1,dp(35));
        pp.topMargin=dp(5);
        memberCard.addView(membersScroll,pp);

        status = text("● Ожидание подключения…",12,ONLINE,false);
        status.setPadding(dp(10),dp(10),dp(10),dp(10));
        status.setBackground(round(SURFACE,12));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1,-2);
        sp.topMargin = dp(9);
        root.addView(status,sp);

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        feed = new LinearLayout(this);
        feed.setOrientation(LinearLayout.VERTICAL);
        feed.setPadding(0,dp(11),0,dp(11));
        scroll.addView(feed,new ScrollView.LayoutParams(-1,-2));
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));

        LinearLayout composer = new LinearLayout(this);
        composer.setGravity(Gravity.CENTER_VERTICAL);
        composer.setPadding(dp(6),dp(5),dp(5),dp(5));
        composer.setBackground(round(SURFACE,17));
        input = new EditText(this);
        input.setTextColor(WHITE);
        input.setHintTextColor(MUTED);
        input.setTextSize(15);
        input.setHint("Написать сообщение…");
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setPadding(dp(7),dp(4),dp(7),dp(4));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMaxLines(4);
        composer.addView(input,new LinearLayout.LayoutParams(0,-2,1f));
        TextView send = button("➤",ACCENT,22);
        composer.addView(send,new LinearLayout.LayoutParams(dp(51),dp(47)));
        send.setOnClickListener(v -> {
            String message = input.getText().toString().trim();
            if (message.isEmpty()) return;
            if (message.length() > 1000) {
                status.setText("● Максимум 1000 символов");
                return;
            }
            if (service == null) {
                status.setText("● Нет подключения к службе. Повтори отправку.");
                return;
            }
            service.send(message);
            input.setText("");
        });
        root.addView(composer);

        setContentView(root);
        draw(new ArrayList<>());
        drawMembers();
    }

    private void drawMembers() {
        if (membersPanel == null || membersScroll == null) return;
        membersPanel.removeAllViews();
        int count = currentMembers.size();
        int onlineCount = 0;
        for (InternetEngine.Member member : currentMembers) {
            if (member.online) onlineCount++;
        }
        membersLabel.setText("УЧАСТНИКИ · " + onlineCount + " В СЕТИ / " + count + " ПОДКЛЮЧЕНО");
        int shown = Math.min(count, 50);
        LinearLayout.LayoutParams viewport = (LinearLayout.LayoutParams) membersScroll.getLayoutParams();
        viewport.height = dp(Math.min(175, Math.max(33, shown * 34)));
        membersScroll.setLayoutParams(viewport);

        if (count == 0) {
            membersPanel.addView(text("Ищем участников…",13,MUTED,false));
            return;
        }
        for (int i=0;i<shown;i++) {
            InternetEngine.Member member = currentMembers.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0,dp(6),0,dp(6));
            membersPanel.addView(row,new LinearLayout.LayoutParams(-1,dp(34)));
            row.addView(text("●",13,member.online?ONLINE:MUTED,true));
            String name = member.name+(member.isMe?"  (вы)":"");
            TextView nick = text(name,13,WHITE,true);
            nick.setSingleLine(true);
            nick.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,-2,1);
            lp.leftMargin = dp(8);
            row.addView(nick,lp);
            String presence = member.online ? "В сети" : "В фоне";
            String ping = member.isMe ? "" :
                (member.pingMs < 0 ? " · пинг —" : " · " + member.pingMs + " мс");
            row.addView(text(presence+ping,11,
                    member.online?ONLINE:MUTED,false));
        }
        if (count > shown)
            membersPanel.addView(text("Ещё "+(count-shown)+" участников",12,MUTED,false));
    }

    private void draw(List<JSONObject> messages) {
        if (feed == null) return;
        feed.removeAllViews();
        if (messages.isEmpty()) {
            TextView welcome = text(
                    "Начните разговор\n\n" +
                    "Отправьте первое сообщение и передайте код комнаты. " +
                    "Другой человек вставит код, и чат соединится через интернет.",
                    15,MUTED,false);
            welcome.setGravity(Gravity.CENTER);
            welcome.setPadding(dp(16),dp(50),dp(16),dp(20));
            feed.addView(welcome);
        } else {
            for (JSONObject m : messages) {
                boolean own = myId.equals(m.optString("from")) ||
                        (myId.isEmpty() && m.optString("name").equals(profile().getString("nick","")));
                LinearLayout row = new LinearLayout(this);
                row.setGravity(own ? Gravity.RIGHT : Gravity.LEFT);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1,-2);
                rp.bottomMargin=dp(10);
                feed.addView(row,rp);
                LinearLayout bubble = new LinearLayout(this);
                bubble.setOrientation(LinearLayout.VERTICAL);
                bubble.setPadding(dp(12),dp(9),dp(12),dp(8));
                bubble.setBackground(round(own?Color.rgb(34,97,184):SURFACE,16));
                row.addView(bubble);
                String author = m.optString("name");
                if (author.isEmpty()) author = own ? profile().getString("nick","Вы") : "Собеседник";
                TextView nick = text(author,11,own?Color.rgb(194,225,255):ONLINE,true);
                bubble.addView(nick);
                TextView body = text(m.optString("text"),15,WHITE,false);
                body.setMaxWidth(dp(287));
                body.setTextIsSelectable(true);
                LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2,-2);
                tp.topMargin=dp(3);
                bubble.addView(body,tp);
                TextView time = text(new SimpleDateFormat("HH:mm",Locale.getDefault())
                        .format(new Date(m.optLong("at",System.currentTimeMillis()))),
                        10,MUTED,false);
                time.setGravity(Gravity.RIGHT);
                bubble.addView(time);
            }
        }
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private void launchChat(String room, boolean joining) {
        if (profile().getString("nick","").trim().isEmpty()) {
            askName(true);
            return;
        }
        code = room;
        codeLabel.setText(code);
        getPreferences(MODE_PRIVATE).edit().putString("room",room).apply();
        currentMembers.clear();
        drawMembers();
        status.setText("● Подключение…");
        Intent intent = ChatService.intent(this,room,joining);
        try {
            if (options().getBoolean("background",true) && Build.VERSION.SDK_INT >= 26)
                startForegroundService(intent);
            else startService(intent);
        } catch (RuntimeException e) {
            status.setText("● Не удалось запустить службу: " + e.getMessage());
        }
        if (!bindRequested) {
            try {
                bindRequested = bindService(new Intent(this,ChatService.class),conn,Context.BIND_AUTO_CREATE);
            } catch (RuntimeException e) {
                status.setText("● Ошибка привязки: " + e.getMessage());
            }
        } else if (service != null) {
            myId=service.myId();
            service.setUiListener(callbacks);
        }
    }

    private void askName(boolean first) {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setText(profile().getString("nick",""));
        field.setHint("Имя или никнейм");
        field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(24)});
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setPadding(dp(20),dp(10),dp(20),dp(4));
        wrapper.addView(field,new LinearLayout.LayoutParams(-1,-2));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(first?"Придумай имя":"Изменить имя")
                .setMessage(first?"Это имя увидят другие участники чата. Оно сохранится на устройстве.":
                        "Новое имя появится у собеседников при следующем обмене статусами.")
                .setView(wrapper)
                .setNegativeButton(first?null:"Отмена",(d,w)->{})
                .setPositiveButton("Сохранить",null)
                .create();
        dialog.setCancelable(!first);
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x -> {
            String nickname=field.getText().toString().trim().replaceAll("[\\p{Cntrl}]","");
            if (nickname.length()<2) {
                field.setError("От 2 до 24 символов");
                return;
            }
            profile().edit().putString("nick",nickname).apply();
            profileLabel.setText(nickname);
            if (service!=null) service.changeNickname(nickname);
            dialog.dismiss();
            if (first) {
                launchChat(code,false);
                maybeAskNotification();
            }
        }));
        dialog.show();
    }

    private void maybeAskNotification() {
        if (Build.VERSION.SDK_INT>=33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
                !options().getBoolean("asked_permission",false)) {
            options().edit().putBoolean("asked_permission",true).apply();
            getWindow().getDecorView().postDelayed(() -> requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},101),500);
        }
    }

    private void settingsDialog() {
        String[] labels = {
                "Уведомления о сообщениях",
                "Работа чата в фоне",
                "Показывать текст сообщений",
                "Звук уведомлений"
        };
        String[] keys={"notifications","background","preview","sound"};
        boolean[] checked = new boolean[keys.length];
        for(int i=0;i<keys.length;i++) checked[i]=options().getBoolean(keys[i],true);
        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle("Настройки")
                .setMultiChoiceItems(labels,checked,(d,which,enabled)-> {
                    options().edit().putBoolean(keys[which],enabled).apply();
                    if (which==1 && service!=null) service.refreshSettings();
                    if (which==0 && enabled) maybeAskNotification();
                })
                .setNeutralButton("Изменить имя",(d,w)->askName(false))
                .setPositiveButton("Готово",(d,w)->{})
                .create();
        dialog.show();
    }

    private TextView button(String label,int color,int sp) {
        TextView v = text(label,sp,WHITE,true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(12),dp(9),dp(12),dp(9));
        v.setBackground(round(color,12));
        return v;
    }
    private TextView text(String content,int sp,int color,boolean bold) {
        TextView v=new TextView(this);
        v.setText(content);
        v.setTextSize(sp);
        v.setTextColor(color);
        if(bold) v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return v;
    }
    private GradientDrawable round(int color,int radius) {
        GradientDrawable d=new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }
    private int dp(int px) {
        return (int)(getResources().getDisplayMetrics().density*px+0.5f);
    }
    private static String newCode() {
        String alphabet="ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        SecureRandom rng=new SecureRandom();
        StringBuilder b=new StringBuilder();
        for(int i=0;i<20;i++) b.append(alphabet.charAt(rng.nextInt(alphabet.length())));
        return b.toString();
    }

    @Override protected void onStart() {
        super.onStart();
        active=true;
        if (profile().getString("nick","").trim().length()>0 && !bindRequested) launchChat(code,false);
        if(service!=null) service.setVisible(true);
    }
    @Override protected void onStop() {
        active=false;
        if(service!=null) {
            service.setVisible(false);
            service.setUiListener(null);
        }
        if (bindRequested) {
            unbindService(conn);
            bindRequested=false;
            bound=false;
            service=null;
        }
        if (!options().getBoolean("background",true)) {
            stopService(new Intent(this,ChatService.class));
        }
        super.onStop();
    }
}
