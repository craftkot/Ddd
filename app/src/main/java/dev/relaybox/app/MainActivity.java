package dev.relaybox.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int BG=0xFF101827, CARD=0xFF1D293D, TEXT=0xFFEAF0FC, MUTED=0xFFA5B4CB, ACCENT=0xFF7896FF;
    private LinearLayout page;
    private int tab=0;
    private Platform filter=null;
    private String query="";
    private boolean onlyUnread=false;
    private boolean registered=false;
    private final BroadcastReceiver receiver=new BroadcastReceiver() {
        @Override public void onReceive(Context context,Intent intent) {
            if(CaptureService.ACTION_NEW.equals(intent.getAction()) && tab==0) render();
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        page=new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(BG);
        setContentView(page);
        NavigationBars.apply(this);
        render();
    }
    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override protected void onStart() {
        super.onStart();
        IntentFilter f=new IntentFilter(CaptureService.ACTION_NEW);
        if(Build.VERSION.SDK_INT>=33) registerReceiver(receiver,f,Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver,f);
        registered=true;
    }
    @Override protected void onStop() {
        if(registered) { unregisterReceiver(receiver); registered=false; }
        super.onStop();
    }
    @Override protected void onResume() {
        super.onResume();
        NavigationBars.apply(this);
        if(page!=null) render();
    }
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) NavigationBars.apply(this);
    }

    private int dp(int size) { return (int)(size*getResources().getDisplayMetrics().density+0.5f); }
    private GradientDrawable shape(int color,int radius) {
        GradientDrawable d=new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private TextView text(String value,int size,int color,boolean bold) {
        TextView t=new TextView(this);
        t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if(bold)t.setTypeface(null,Typeface.BOLD);
        return t;
    }
    private LinearLayout group() {
        LinearLayout g=new LinearLayout(this);
        g.setOrientation(LinearLayout.VERTICAL);
        g.setBackground(shape(CARD,16));
        g.setPadding(dp(15),dp(14),dp(15),dp(14));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin=dp(12);
        g.setLayoutParams(lp);
        return g;
    }
    private void add(LinearLayout parent,View view) {
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin=dp(9); parent.addView(view,lp);
    }
    private TextView action(LinearLayout parent,String caption,Runnable callback) {
        TextView b=text(caption,14,TEXT,true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(12),dp(13),dp(12),dp(13));
        b.setBackground(shape(0xFF334668,12));
        b.setOnClickListener(v -> callback.run());
        add(parent,b); return b;
    }
    private void heading(LinearLayout parent,String title,String subtitle) {
        add(parent,text(title,24,TEXT,true));
        add(parent,text(subtitle,12,MUTED,false));
    }
    private void render() {
        page.removeAllViews();
        LinearLayout top=new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(20),dp(18),dp(20),dp(15));
        ImageView logo=new ImageView(this);
        logo.setImageResource(R.drawable.relaybox_icon);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        logo.setBackground(shape(0xFF101827,12));
        logo.setClipToOutline(true);
        top.addView(logo,new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout heading=new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(13),0,0,0);
        heading.addView(text("RelayBox",22,TEXT,true));
        heading.addView(text("Все площадки · один экран",11,MUTED,false));
        top.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        TextView status=text(listenerAccess()?"● ON":"● Доступ",12,listenerAccess()?0xFF7DE3BD:0xFFFFA893,true);
        status.setOnClickListener(v -> openAccess());
        top.addView(status);
        page.addView(top);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18),dp(10),dp(18),dp(22));
        scroll.addView(body);
        page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(tab==0) inbox(body);
        if(tab==1) accounts(body);
        if(tab==2) settings(body);
        LinearLayout nav=new LinearLayout(this);
        nav.setPadding(dp(5),dp(7),dp(5),dp(12));
        String[] names={"Входящие","Сервисы","Настройки"};
        for(int i=0;i<3;i++) {
            final int next=i;
            TextView item=text(names[i],14,i==tab?ACCENT:MUTED,i==tab);
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(4),dp(14),dp(4),dp(14));
            item.setOnClickListener(v -> { tab=next;render(); });
            nav.addView(item,new LinearLayout.LayoutParams(0,-2,1));
        }
        page.addView(nav);
    }
    private boolean listenerAccess() {
        ComponentName target = new ComponentName(this, CaptureService.class);
        if (Build.VERSION.SDK_INT >= 27) {
            NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            return manager!=null && manager.isNotificationListenerAccessGranted(target);
        }
        String enabled = Settings.Secure.getString(
                getContentResolver(), "enabled_notification_listeners");
        if (enabled == null) return false;
        for (String item : enabled.split(":")) {
            ComponentName component = ComponentName.unflattenFromString(item);
            if (target.equals(component)) return true;
        }
        return false;
    }
    private void openAccess() {
        try { startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }
        catch(Exception e) { new AlertDialog.Builder(this).setMessage("Откройте настройки Android → Доступ к уведомлениям").setPositiveButton("ОК",null).show(); }
    }
    private void inbox(LinearLayout body) {
        heading(body,"Входящие","Локальная история новых сообщений");
        if(!listenerAccess()) {
            LinearLayout box=group();
            add(box,text("Чтобы получать сообщения, разрешите RelayBox доступ к уведомлениям Android.",14,MUTED,false));
            action(box,"Выдать разрешение",this::openAccess);
            body.addView(box);
        }
        LinearLayout controls=group();
        LinearLayout chips=new LinearLayout(this);
        String[] names={"Все","Авито","FunPay","Playerok"};
        for(int i=0;i<names.length;i++) {
            final Platform f=i==0?null:Platform.values()[i-1];
            TextView chip=text(names[i],12,f==filter?ACCENT:MUTED,f==filter);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(3),dp(12),dp(3),dp(12));
            chip.setOnClickListener(v -> { filter=f;render(); });
            chips.addView(chip,new LinearLayout.LayoutParams(0,-2,1));
        }
        add(controls,chips);
        EditText search=new EditText(this);
        search.setSingleLine(true); search.setTextColor(TEXT);search.setHintTextColor(MUTED);
        search.setText(query);search.setHint("Отправитель или текст");
        search.setTextSize(13);
        add(controls,search);
        action(controls,"Применить поиск",() -> { query=search.getText().toString().toLowerCase(Locale.ROOT); render(); });
        action(controls,onlyUnread?"✓ Только непрочитанные":"Показывать только непрочитанные",
                () -> { onlyUnread=!onlyUnread;render(); });
        body.addView(controls);
        List<MessageStore.Entry> entries;
        try(MessageStore db=new MessageStore(this)) { entries=db.recent(); }
        if(entries.isEmpty()) add(body,text("Сообщений пока нет. Разрешите доступ и получите сообщение из площадки.",14,MUTED,false));
        int shown=0;
        for(MessageStore.Entry item:entries) {
            if(filter!=null&&item.platform!=filter)continue;
            if(onlyUnread&&item.seen)continue;
            if(!query.isEmpty() && !item.sender.toLowerCase(Locale.ROOT).contains(query)
                    && !item.message.toLowerCase(Locale.ROOT).contains(query))continue;
            LinearLayout card=group();
            String time=new SimpleDateFormat("dd.MM HH:mm",Locale.getDefault()).format(new Date(item.time));
            add(card,text(item.platform.label+" · "+time+(item.seen?"":"   ●"),12,ACCENT,!item.seen));
            add(card,text(item.sender,16,TEXT,true));
            TextView message=text(item.message,13,MUTED,false);
            message.setMaxLines(3);
            message.setEllipsize(TextUtils.TruncateAt.END);
            add(card,message);
            card.setOnClickListener(v -> {
                try(MessageStore db=new MessageStore(this)) { db.markRead(item.id); }
                Intent intent=new Intent(this,ChatActivity.class);
                intent.putExtra("platform",item.platform.id);
                intent.putExtra("row",item.id);
                startActivity(intent);
            });
            body.addView(card);shown++;
        }
        if(shown==0 && !entries.isEmpty()) add(body,text("По выбранным фильтрам ничего нет.",13,MUTED,false));
        action(body,"Отметить всё прочитанным",() -> {
            try(MessageStore db=new MessageStore(this)) { db.allRead(); }
            render();
        });
    }
    private void accounts(LinearLayout body) {
        heading(body,"Сервисы","Вход в аккаунты выполняется на официальных сайтах");
        for(Platform p:Platform.values()) {
            LinearLayout card=group();
            add(card,text(p.label,19,TEXT,true));
            add(card,text("Уведомления: "+(Platform.allowed(this,p)?"включены":"выключены"),12,MUTED,false));
            Switch toggle=new Switch(this);
            toggle.setText("Слушать "+p.label);
            toggle.setTextColor(TEXT);
            toggle.setChecked(Platform.allowed(this,p));
            toggle.setOnCheckedChangeListener((button,checked)-> {
                getSharedPreferences("relaybox",0).edit().putBoolean("platform_"+p.id,checked).apply();
            });
            add(card,toggle);
            action(card,"Открыть чат / войти",() -> openChat(p));
            body.addView(card);
        }
        add(body,text("Веб-сессия хранится в WebView. Если сайт запрещает вход через встроенный браузер, потребуется обычное приложение или внешний браузер.",12,MUTED,false));
    }
    private void openChat(Platform p) {
        Intent intent=new Intent(this,ChatActivity.class);
        intent.putExtra("platform",p.id);
        startActivity(intent);
    }
    private void settingSwitch(LinearLayout body,String title,String pref,boolean initial) {
        LinearLayout card=group();
        Switch sw=new Switch(this);
        sw.setText(title); sw.setTextColor(TEXT);
        sw.setChecked(getSharedPreferences("relaybox",0).getBoolean(pref,initial));
        sw.setOnCheckedChangeListener((v,checked)-> {
            getSharedPreferences("relaybox",0).edit().putBoolean(pref,checked).apply();
            if(pref.equals("hide_system_navigation")) NavigationBars.apply(this);
            if(pref.equals("mirror")&&checked) requestPostPermission();
        });
        card.addView(sw); body.addView(card);
    }
    private void requestPostPermission() {
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},73);
    }
    private void settings(LinearLayout body) {
        heading(body,"Настройки","Разрешения, уведомления и конфиденциальность");
        action(body,listenerAccess()?"Доступ к уведомлениям: разрешён":"Разрешить чтение уведомлений",this::openAccess);
        settingSwitch(body,"Скрывать системные кнопки Android","hide_system_navigation",true);
        settingSwitch(body,"Уведомления браузеров","browsers",true);
        settingSwitch(body,"Расширенное распознавание (менее точно)","loose",false);
        settingSwitch(body,"Дублировать уведомления в шторке","mirror",false);
        action(body,"Тестовые сообщения (без реального push)",() -> {
            try(MessageStore db=new MessageStore(this)) {
                for(Platform p:Platform.values()) {
                    db.add("test_"+p.id+"_"+System.nanoTime(),p,"Тестовый покупатель","Проверка отображения RelayBox",System.currentTimeMillis());
                }
            }catch(Exception ignored){}
            tab=0;render();
        });
        action(body,"Удалить историю сообщений",() -> new AlertDialog.Builder(this)
                .setTitle("Удалить локальную историю?")
                .setNegativeButton("Отмена",null)
                .setPositiveButton("Удалить",(d,w)-> {
                    try(MessageStore db=new MessageStore(this)) { db.clearMessages(); }
                    render();
                }).show());
        action(body,"Выйти из всех веб-аккаунтов",() -> new AlertDialog.Builder(this)
                .setTitle("Очистить WebView-сессии?")
                .setMessage("Cookies и данные сайтов будут удалены. Понадобится входить заново.")
                .setNegativeButton("Отмена",null)
                .setPositiveButton("Очистить",(d,w)-> {
                    CookieManager.getInstance().removeAllCookies(null);
                    CookieManager.getInstance().flush();
                    WebStorage.getInstance().deleteAllData();
                    render();
                }).show());
        add(body,text("При нажатии на исходное уведомление Авито откроется Авито. Чтобы открывать RelayBox, включите дублирование уведомлений.",12,MUTED,false));
        add(body,text("Текущая версия не получает историю переписок через API площадок. Чат показывается внутри WebView.",12,MUTED,false));
    }
}
