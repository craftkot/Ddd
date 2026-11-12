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
import android.graphics.Rect;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.media.MediaRecorder;
import android.media.MediaPlayer;
import android.media.MediaMetadataRetriever;
import android.provider.MediaStore;
import android.widget.VideoView;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import org.json.JSONObject;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.io.File;
import java.io.InputStream;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;

public final class MainActivity extends Activity {
    // EHTID violet — high-contrast, deliberately restrained chat surfaces.
    private static final int BG = Color.rgb(16, 13, 27);
    private static final int SURFACE = Color.rgb(33, 26, 50);
    private static final int PANEL = Color.rgb(48, 37, 68);
    private static final int ACCENT = Color.rgb(139, 92, 246);
    private static final int WHITE = Color.rgb(245, 241, 255);
    private static final int MUTED = Color.rgb(173, 163, 195);
    private static final int ONLINE = Color.rgb(94, 216, 189);
    private EditText input, joinInput;
    private TextView status, codeLabel, codePeek, pingHeader, profileLabel, membersLabel, homeServerLabel;
    private final ImagePreviews imagePreviews=new ImagePreviews();
    private final List<PreviewSlot> previewSlots=new ArrayList<>();
    private boolean previewRefreshQueued;
    private final Set<String> pendingTranscriptions=new HashSet<>();
    private LinearLayout feed, membersPanel;
    private ScrollView scroll, membersScroll, peopleScroll, profileScroll, roomsScroll;
    private LinearLayout peopleContent, profileContent, roomsContent, composer;
    private ImageView wallpaper;
    private FrameLayout pages;
    private TextView micButton;
    private MediaRecorder recorder;
    private File recordingFile;
    private long recordStarted;
    private MediaPlayer voicePlayer;
    private AlertDialog incomingCallDialog;
    private String lastCallPhase="idle";
    private InternetEngine.Member pendingCallMember;
    private boolean pendingAnswerCall;
    private int page=0;
    private SavedRooms savedRooms;
    private ReleaseUpdater.Result readyUpdate;
    private String lastPromptedUpdate="";
    private int historyViewLimit=300;
    private boolean expandingHistory=false;
    private List<JSONObject> visibleSnapshot=new ArrayList<>();
    private static final int PICK_ATTACHMENT=201, PICK_WALLPAPER=202, RECORD_PERMISSION=203,
            PICK_SQUARE=204;
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
            String check = s.toLowerCase(Locale.ROOT);
            boolean warning = check.contains("429") || check.contains("очеред") ||
                    check.contains("не приняло") || check.contains("нет связи") ||
                    check.contains("ошибка") || check.contains("лимит") ||
                    check.contains("отключ");
            status.setTextColor(warning ? Color.rgb(255,179,107) : ONLINE);
        }
        @Override public void onMessages(List<JSONObject> messages) { draw(messages); }
        @Override public void onParticipants(List<InternetEngine.Member> members) {
            currentMembers.clear();
            currentMembers.addAll(members);
            drawMembers();
            refreshPeoplePage();
            refreshPingHeader();
        }
        @Override public void onIncoming(String nick, String text) {}
        @Override public void onManualSignal(String token, String kind) {}
        @Override public void onCallState(String phase,String nickname) {
            showCallStatus(phase,nickname);
        }
    };
    private final ServiceConnection conn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((ChatService.LocalBinder)binder).service();
            bound = true;
            if(!code.equals(service.currentRoom()))service.switchRoom(code,false);
            myId = service.myId();
            service.setVisible(active && page==0);
            service.setUiListener(callbacks);
            if(ChatService.ACTION_CALL_ACCEPT.equals(getIntent().getAction())) {
                getIntent().setAction(null);
                requestCallPermission(null,true);
            }
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
        savedRooms=new SavedRooms(this);
        savedRooms.migrateLegacy(profile());
        code = getPreferences(MODE_PRIVATE).getString("room", "");
        if (!code.matches("[A-Z2-9]{16,32}")) code = newCode();
        buildUI();
        UpdateCheckWorker.schedule(this);
        checkForUpdates(false);
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
        root.setPadding(dp(10), dp(9), dp(10), dp(8));
        root.setBackgroundColor(BG);

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_chat_logo);
        heading.addView(logo,new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0,-2,1);
        bp.leftMargin = dp(8);
        heading.addView(brand,bp);
        TextView title = text("EHTID",20,WHITE,true);
        title.setLetterSpacing(0.04f);
        brand.addView(title);
        profileLabel = text(profile().getString("nick",""),11,MUTED,false);
        brand.addView(profileLabel);
        codePeek = text("Код "+code.substring(0,6)+"… · открыть доступ",11,MUTED,false);
        codePeek.setSingleLine(true);
        brand.addView(codePeek);
        pingHeader=text("Cloudflare · измеряем задержку…",11,MUTED,false);
        brand.addView(pingHeader);
        TextView callButton=iconButton(R.drawable.ic_ehtid_call,PANEL,"Голосовой звонок");
        LinearLayout.LayoutParams callLp=new LinearLayout.LayoutParams(dp(46),dp(46));
        callLp.rightMargin=dp(5);
        heading.addView(callButton,callLp);
        callButton.setOnClickListener(v->openCallControls());
        TextView roomsButton=iconButton(R.drawable.ic_ehtid_rooms,PANEL,"Список комнат");
        LinearLayout.LayoutParams roomButtonLp=new LinearLayout.LayoutParams(dp(46),dp(46));
        roomButtonLp.rightMargin=dp(5);
        heading.addView(roomsButton,roomButtonLp);
        roomsButton.setOnClickListener(v->showPage(3));
        TextView settings = iconButton(R.drawable.ic_ehtid_settings,PANEL,"Профиль и настройки");
        heading.addView(settings,new LinearLayout.LayoutParams(dp(46),dp(46)));
        settings.setOnClickListener(v -> showPage(2));
        root.addView(heading);

        LinearLayout roomBox = new LinearLayout(this);
        roomBox.setOrientation(LinearLayout.VERTICAL);
        roomBox.setPadding(dp(12),dp(10),dp(12),dp(12));
        roomBox.setBackground(round(SURFACE,16));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1,-2);
        rp.topMargin = dp(13);
        root.addView(roomBox,rp);
        roomBox.setVisibility(View.GONE);
        brand.setOnClickListener(v->roomBox.setVisibility(roomBox.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));

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
        TextView copy = labeledButton("Копировать",R.drawable.ic_ehtid_copy,ACCENT,13);
        actions.addView(copy);
        copy.setOnClickListener(v -> {
            ((ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE))
                    .setPrimaryClip(ClipData.newPlainText("EHTID room",code));
            status.setText("● Код скопирован");
        });
        TextView newRoom = labeledButton("Новая комната",R.drawable.ic_ehtid_plus,PANEL,13);
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
        TextView joinButton = labeledButton("Войти",R.drawable.ic_ehtid_join,ACCENT,13);
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

        homeServerLabel=text("☁ Cloudflare • сервер встроен",12,ONLINE,false);
        homeServerLabel.setPadding(dp(3),dp(8),dp(3),dp(2));
        roomBox.addView(homeServerLabel);

        LinearLayout memberCard = new LinearLayout(this);
        memberCard.setOrientation(LinearLayout.VERTICAL);
        memberCard.setBackground(round(SURFACE,14));
        memberCard.setPadding(dp(12),dp(10),dp(12),dp(9));
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1,-2);
        mp.topMargin = dp(10);
        root.addView(memberCard,mp);
        // Participants have a dedicated full page; do not steal chat height.
        memberCard.setVisibility(View.GONE);
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
        status.setPadding(dp(5),dp(3),dp(5),dp(5));
        status.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1,-2);
        sp.topMargin = dp(2);
        root.addView(status,sp);

        pages = new SwipePages();
        LinearLayout.LayoutParams pageLp=new LinearLayout.LayoutParams(-1,0,1f);
        root.addView(pages,pageLp);

        wallpaper = new ImageView(this);
        wallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);
        pages.addView(wallpaper,new FrameLayout.LayoutParams(-1,-1));

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setBackgroundColor(Color.TRANSPARENT);
        feed = new LinearLayout(this);
        feed.setOrientation(LinearLayout.VERTICAL);
        feed.setPadding(0,dp(11),0,dp(11));
        scroll.addView(feed,new ScrollView.LayoutParams(-1,-2));
        pages.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        scroll.setOnScrollChangeListener((v,x,y,oldX,oldY)->schedulePreviewChecks());

        peopleScroll=new ScrollView(this);
        peopleScroll.setBackgroundColor(BG);
        peopleContent=new LinearLayout(this);
        peopleContent.setOrientation(LinearLayout.VERTICAL);
        peopleContent.setPadding(dp(10),dp(10),dp(10),dp(20));
        peopleScroll.addView(peopleContent,new ScrollView.LayoutParams(-1,-2));
        pages.addView(peopleScroll,new FrameLayout.LayoutParams(-1,-1));

        profileScroll=new ScrollView(this);
        profileScroll.setBackgroundColor(BG);
        profileContent=new LinearLayout(this);
        profileContent.setOrientation(LinearLayout.VERTICAL);
        profileContent.setPadding(dp(10),dp(10),dp(10),dp(20));
        profileScroll.addView(profileContent,new ScrollView.LayoutParams(-1,-2));
        pages.addView(profileScroll,new FrameLayout.LayoutParams(-1,-1));

        roomsScroll=new ScrollView(this);
        roomsScroll.setBackgroundColor(BG);
        roomsContent=new LinearLayout(this);
        roomsContent.setOrientation(LinearLayout.VERTICAL);
        roomsContent.setPadding(dp(10),dp(10),dp(10),dp(20));
        roomsScroll.addView(roomsContent,new ScrollView.LayoutParams(-1,-2));
        pages.addView(roomsScroll,new FrameLayout.LayoutParams(-1,-1));

        composer = new LinearLayout(this);
        composer.setGravity(Gravity.CENTER_VERTICAL);
        composer.setPadding(dp(5),dp(5),dp(5),dp(5));
        composer.setBackground(round(SURFACE,24));
        input = new EditText(this);
        input.setTextColor(WHITE);
        input.setHintTextColor(MUTED);
        input.setTextSize(15);
        input.setHint("Написать сообщение…");
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setPadding(dp(7),dp(4),dp(7),dp(4));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMaxLines(4);
        TextView attach=iconButton(R.drawable.ic_ehtid_attach,PANEL,"Добавить вложение");
        composer.addView(attach,new LinearLayout.LayoutParams(dp(43),dp(46)));
        attach.setOnClickListener(v->new AlertDialog.Builder(this)
                .setTitle("Добавить в EHTID")
                .setItems(new String[]{"Файл или фото","Квадратное видеосообщение"},(d,which)->{
                    if(which==0)chooseAttachment();else chooseSquare();
                }).show());
        composer.addView(input,new LinearLayout.LayoutParams(0,-2,1f));
        micButton = iconButton(R.drawable.ic_ehtid_mic,PANEL,"Записать голосовое сообщение");
        composer.addView(micButton,new LinearLayout.LayoutParams(dp(43),dp(46)));
        micButton.setOnClickListener(v->toggleRecording());
        micButton.setOnLongClickListener(v->{chooseSquare();return true;});
        TextView send = iconButton(R.drawable.ic_ehtid_send,ACCENT,"Отправить сообщение");
        LinearLayout.LayoutParams sendLp=new LinearLayout.LayoutParams(dp(47),dp(46));
        sendLp.leftMargin=dp(4);
        composer.addView(send,sendLp);
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
        showPage(0);
        applyWallpaper();
        refreshPeoplePage();
        refreshProfilePage();
        refreshRoomsPage();
        draw(new ArrayList<>());
        drawMembers();
    }

    private final class SwipePages extends FrameLayout {
        private float startX,startY;
        SwipePages(){super(MainActivity.this);}
        @Override public boolean onInterceptTouchEvent(MotionEvent event) {
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN) {
                startX=event.getX();startY=event.getY();
                return false;
            }
            if(event.getActionMasked()==MotionEvent.ACTION_MOVE) {
                float dx=event.getX()-startX,dy=event.getY()-startY;
                return Math.abs(dx)>dp(54)&&Math.abs(dx)>Math.abs(dy)*1.6f;
            }
            return false;
        }
        @Override public boolean onTouchEvent(MotionEvent event) {
            if(event.getActionMasked()==MotionEvent.ACTION_UP) {
                float dx=event.getX()-startX;
                if(dx>dp(65))showPage(page==2?0:1);
                else if(dx<-dp(65))showPage(page==1?0:2);
                return true;
            }
            return true;
        }
    }

    private void showPage(int next) {
        page=next;
        if(scroll==null||peopleScroll==null||profileScroll==null||
                roomsScroll==null||composer==null)return;
        scroll.setVisibility(page==0?View.VISIBLE:View.GONE);
        wallpaper.setVisibility(page==0?View.VISIBLE:View.GONE);
        peopleScroll.setVisibility(page==1?View.VISIBLE:View.GONE);
        profileScroll.setVisibility(page==2?View.VISIBLE:View.GONE);
        roomsScroll.setVisibility(page==3?View.VISIBLE:View.GONE);
        composer.setVisibility(page==0?View.VISIBLE:View.GONE);
        if(service!=null)service.setVisible(active && page==0);
        if(page==1)refreshPeoplePage();
        if(page==2)refreshProfilePage();
        if(page==3)refreshRoomsPage();
    }

    private String fullTime(long epoch) {
        if(epoch<=0)return "Нет данных";
        return new SimpleDateFormat("dd.MM.yyyy HH:mm:ss",Locale.getDefault()).format(new Date(epoch));
    }

    private void refreshPeoplePage() {
        if(peopleContent==null)return;
        peopleContent.removeAllViews();
        TextView title=text("Участники комнаты",21,WHITE,true);
        peopleContent.addView(title);
        TextView back=button("← Чат",PANEL,13);
        peopleContent.addView(back);
        back.setOnClickListener(v->showPage(0));
        TextView refresh=labeledButton("Обновить пинги",R.drawable.ic_ehtid_refresh,PANEL,13);
        LinearLayout.LayoutParams rf=new LinearLayout.LayoutParams(-1,dp(45));
        rf.topMargin=dp(9);
        peopleContent.addView(refresh,rf);
        refresh.setOnClickListener(v->{
            if(service==null){status.setText("● Сначала подключитесь к комнате");return;}
            service.refreshPings();
            status.setText("● Проверяем задержку сети и участников…");
        });
        for(InternetEngine.Member m:currentMembers) {
            LinearLayout card=new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(12),dp(12),dp(12),dp(12));
            card.setBackground(round(SURFACE,14));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
            p.topMargin=dp(12);
            peopleContent.addView(card,p);
            card.addView(text((m.online?"● ":"○ ")+m.name+(m.isMe?" (вы)":""),
                    16,m.online?ONLINE:WHITE,true));
            card.addView(text("Статус: "+(m.online?"в сети":"не в сети"),13,MUTED,false));
            card.addView(text("Последний вход: "+fullTime(m.loginAt),13,MUTED,false));
            card.addView(text("Последний сигнал: "+fullTime(m.lastSeen),13,MUTED,false));
            TextView latency=text(memberPingText(m),13,pingColor(m),true);
            latency.setPadding(0,dp(5),0,dp(3));
            card.addView(latency);
            String device=m.id.length()>12?m.id.substring(0,12)+"…":m.id;
            card.addView(text("Устройство: "+device,12,MUTED,false));
            if(!m.isMe && m.online){
                TextView call=labeledButton("Позвонить",R.drawable.ic_ehtid_call,ACCENT,13);
                LinearLayout.LayoutParams clp=new LinearLayout.LayoutParams(-1,dp(42));
                clp.topMargin=dp(7);
                card.addView(call,clp);
                call.setOnClickListener(v->{
                    requestCallPermission(m,false);
                });
            }
        }
        if(currentMembers.isEmpty())
            peopleContent.addView(text("Участники ещё не обнаружены",13,MUTED,false));
        peopleContent.addView(text(
                "Пинг Cloudflare = время HTTPS-запроса до сервера. "+
                "Пинг участников через реле включает передачу и ожидание опроса сервера; "+
                "это НЕ прямой пинг до телефона. Значения устаревают, если нет ответа. "+
                "Время присутствия зависит от фоновой работы Android.",
                12,MUTED,false));
    }

    private void refreshRoomsPage() {
        if(roomsContent==null||savedRooms==null)return;
        roomsContent.removeAllViews();
        roomsContent.addView(text("Мои комнаты",22,WHITE,true));
        roomsContent.addView(text(
                "Выбирай комнату — её история останется на телефоне. "+
                "Сейчас сообщения принимаются только в активной комнате; "+
                "после возвращения подгрузятся доступные события с сервера.",
                12,MUTED,false));
        TextView back=button("← Вернуться в чат",PANEL,14);
        LinearLayout.LayoutParams b=new LinearLayout.LayoutParams(-1,dp(43));
        b.topMargin=dp(12);
        roomsContent.addView(back,b);
        back.setOnClickListener(v->showPage(0));

        TextView create=button("＋ Создать комнату",ACCENT,14);
        LinearLayout.LayoutParams c=new LinearLayout.LayoutParams(-1,dp(46));
        c.topMargin=dp(9);
        roomsContent.addView(create,c);
        create.setOnClickListener(v->launchChat(newCode(),false));

        ChatHistory history=ChatHistory.get(this);
        List<SavedRooms.Room> rooms=savedRooms.list();
        for(SavedRooms.Room room:rooms) {
            history.migrateLegacy(profile(),room.code);
            LinearLayout card=new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(12),dp(11),dp(12),dp(11));
            card.setBackground(round(SURFACE,15));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
            lp.topMargin=dp(10);
            roomsContent.addView(card,lp);
            String title=room.title.isEmpty()?"Комната "+room.code.substring(0,6):room.title;
            card.addView(text(title+(room.code.equals(code)?"  ● Активна":""),16,
                    room.code.equals(code)?ONLINE:WHITE,true));
            card.addView(text(room.code,11,MUTED,false));
            card.addView(text(history.preview(room.code),13,WHITE,false));
            card.addView(text("Сообщений: "+history.count(room.code)+
                    "  ·  Последний вход: "+fullTime(room.lastOpened),11,MUTED,false));

            LinearLayout controls=new LinearLayout(this);
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(42));
            cp.topMargin=dp(8);
            card.addView(controls,cp);
            TextView open=button(room.code.equals(code)?"Открыть чат":"Перейти",ACCENT,12);
            controls.addView(open,new LinearLayout.LayoutParams(0,-1,1f));
            open.setOnClickListener(v->{
                if(room.code.equals(code))showPage(0);
                else launchChat(room.code,false);
            });
            TextView rename=button("Имя",PANEL,12);
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(dp(64),-1);
            rp.leftMargin=dp(5);
            controls.addView(rename,rp);
            rename.setOnClickListener(v->renameRoom(room));
            if(!room.code.equals(code)) {
                TextView hide=button("Скрыть",PANEL,12);
                LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(dp(67),-1);
                hp.leftMargin=dp(5);
                controls.addView(hide,hp);
                hide.setOnClickListener(v->new AlertDialog.Builder(this)
                        .setTitle("Убрать из списка?")
                        .setMessage("Комната пропадёт из списка, но сообщения и медиа "+
                                "останутся в памяти телефона. По коду можно вернуться.")
                        .setNegativeButton("Отмена",null)
                        .setPositiveButton("Скрыть",(d,w)->{
                            savedRooms.hide(room.code);
                            refreshRoomsPage();
                        }).show());
            }
        }
    }

    private void renameRoom(SavedRooms.Room room){
        EditText input=new EditText(this);
        input.setSingleLine(true);
        input.setText(room.title);
        input.setHint("Название комнаты");
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(36)});
        new AlertDialog.Builder(this)
                .setTitle("Переименовать комнату")
                .setMessage("Название видишь только ты. Код комнаты не меняется.")
                .setView(input)
                .setNegativeButton("Отмена",null)
                .setPositiveButton("Сохранить",(d,w)->{
                    savedRooms.rename(room.code,input.getText().toString());
                    refreshRoomsPage();
                }).show();
    }

    private void refreshProfilePage() {
        if(profileContent==null)return;
        profileContent.removeAllViews();
        profileContent.addView(text("Профиль и настройки",22,WHITE,true));
        TextView back=button("← Вернуться в чат",PANEL,14);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(43));
        bp.topMargin=dp(8);
        profileContent.addView(back,bp);
        back.setOnClickListener(v->showPage(0));
        profileContent.addView(text("Ник: "+profile().getString("nick","Пользователь"),
                17,WHITE,true));
        String device=profile().getString("device_id","—");
        profileContent.addView(text("ID устройства: "+device,11,MUTED,false));
        profileContent.addView(text("Cloudflare: сервер подключается автоматически",13,ONLINE,false));
        TextView rename=button("Изменить никнейм",PANEL,14);
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(46));
        rp.topMargin=dp(12);
        profileContent.addView(rename,rp);
        rename.setOnClickListener(v->askName(false));
        TextView wallpaperChoice=button("Выбрать собственный фон",ACCENT,14);
        LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(-1,dp(46));
        wp.topMargin=dp(10);
        profileContent.addView(wallpaperChoice,wp);
        wallpaperChoice.setOnClickListener(v->chooseWallpaper());
        TextView reset=button("Стандартный тёмный фон",PANEL,13);
        LinearLayout.LayoutParams rp2=new LinearLayout.LayoutParams(-1,dp(43));
        rp2.topMargin=dp(8);
        profileContent.addView(reset,rp2);
        reset.setOnClickListener(v->{
            options().edit().remove("wallpaper_uri").apply();
            applyWallpaper();
            refreshProfilePage();
        });

        profileContent.addView(text("Автообновление GitHub",16,WHITE,true));
        profileContent.addView(text("Версия: "+BuildConfig.VERSION_NAME+
                " · сохранённые комнаты и история останутся после обновления",
                12,MUTED,false));
        CheckBox updateMode=new CheckBox(this);
        updateMode.setText("Автоматически загружать APK (≈50 МБ, включая мобильную сеть)");
        updateMode.setTextColor(WHITE);
        updateMode.setChecked(getSharedPreferences("ehtid_updater",MODE_PRIVATE)
                .getBoolean("automatic",true));
        updateMode.setOnCheckedChangeListener((v,checked)->{
            getSharedPreferences("ehtid_updater",MODE_PRIVATE).edit()
                    .putBoolean("automatic",checked).apply();
            if(checked) {
                UpdateCheckWorker.schedule(this);
                checkForUpdates(false);
            }
        });
        profileContent.addView(updateMode);
        TextView updateButton=button("Проверить новые версии",PANEL,14);
        LinearLayout.LayoutParams updateLp=new LinearLayout.LayoutParams(-1,dp(44));
        updateLp.topMargin=dp(8);
        profileContent.addView(updateButton,updateLp);
        updateButton.setOnClickListener(v->checkForUpdates(true));
        if(readyUpdate!=null&&readyUpdate.ready()) {
            TextView install=button("Установить EHTID v"+readyUpdate.version,ACCENT,14);
            LinearLayout.LayoutParams installLp=new LinearLayout.LayoutParams(-1,dp(46));
            installLp.topMargin=dp(8);
            profileContent.addView(install,installLp);
            install.setOnClickListener(v->ReleaseUpdater.requestInstallation(this,readyUpdate));
        }
        profileContent.addView(text(
                "GitHub проверяется при запуске и примерно раз в 12 часов в фоне. "+
                "Android может отложить фоновую проверку. APK загружается автоматически, "+
                "установка требует разрешения Android. Удалять старое приложение нельзя.",
                12,MUTED,false));

        profileContent.addView(text("Параметры",16,WHITE,true));
        String[] labels={"Уведомления о сообщениях","Работа чата в фоне",
                "Показывать текст уведомлений","Звук уведомлений",
                "Уведомления о звонках","Вибрация сообщений"};
        String[] keys={"notifications","background","preview","sound",
                "calls_notifications","vibration"};
        for(int i=0;i<keys.length;i++) {
            final int which=i;
            CheckBox box=new CheckBox(this);
            box.setText(labels[i]);
            box.setTextColor(WHITE);
            box.setChecked(options().getBoolean(keys[i],true));
            box.setPadding(dp(4),dp(8),dp(4),dp(8));
            box.setOnCheckedChangeListener((v,checked)->{
                options().edit().putBoolean(keys[which],checked).apply();
                if(which==1&&service!=null)service.refreshSettings();
                if(which==0&&checked)maybeAskNotification();
            });
            profileContent.addView(box);
        }
        profileContent.addView(text(
                "Вправо от чата — участники. Влево — профиль. "+
                "Изображение фона хранится только на этом устройстве. "+
                "Голосовые расшифровываются через Cloudflare AI только с твоего согласия.",
                12,MUTED,false));
    }

    private void checkForUpdates(boolean manual) {
        if(manual&&status!=null)status.setText("● Проверяю обновления GitHub…");
        ReleaseUpdater.checkAsync(this,manual,result->{
            if(isFinishing()||isDestroyed())return;
            if(result.ready()){
                readyUpdate=result;
                if(status!=null)status.setText("● EHTID v"+result.version+
                        " загружен. Требуется подтверждение установки.");
                if(page==2)refreshProfilePage();
                if(active&&!result.version.equals(lastPromptedUpdate))
                    showUpdatePrompt(result);
            } else if(manual) {
                String message="current".equals(result.status)?"Уже установлена актуальная версия":
                        result.detail==null?result.status:result.detail;
                if(status!=null)status.setText("● "+message);
                new AlertDialog.Builder(this).setTitle("Обновления EHTID")
                        .setMessage(message)
                        .setPositiveButton("ОК",null).show();
            }
        });
    }

    private void showUpdatePrompt(ReleaseUpdater.Result ready) {
        if(ready==null||!ready.ready()||isFinishing()||isDestroyed())return;
        lastPromptedUpdate=ready.version;
        new AlertDialog.Builder(this)
                .setTitle("EHTID v"+ready.version+" готов")
                .setMessage("Обновление скачано с GitHub и проверено по SHA-256, "+
                        "пакету и постоянной подписи EHTID. Установка поверх "+
                        "текущей версии сохраняет чаты, список комнат и настройки. "+
                        "Android попросит подтвердить установку.")
                .setNegativeButton("Позже",null)
                .setPositiveButton("Установить",(dialog,which)->
                        ReleaseUpdater.requestInstallation(this,ready))
                .show();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if(intent!=null&&ChatService.ACTION_CALL_ACCEPT.equals(intent.getAction())){
            intent.setAction(null);
            requestCallPermission(null,true);
        }
        if(intent!=null&&intent.getBooleanExtra("ehtid_show_update",false)) {
            intent.removeExtra("ehtid_show_update");
            lastPromptedUpdate="";
            checkForUpdates(true);
        }
    }

    private void chooseWallpaper() {
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent,PICK_WALLPAPER);
    }

    private void applyWallpaper() {
        if(wallpaper==null)return;
        String uri=options().getString("wallpaper_uri","");
        if(uri.isEmpty()){
            wallpaper.setImageDrawable(null);
            wallpaper.setBackgroundColor(BG);
            return;
        }
        try {
            Uri source=Uri.parse(uri);
            BitmapFactory.Options info=new BitmapFactory.Options();
            info.inJustDecodeBounds=true;
            try(InputStream input=getContentResolver().openInputStream(source)){
                BitmapFactory.decodeStream(input,null,info);
            }
            int sample=1;
            while(info.outWidth/sample>1440||info.outHeight/sample>2560)sample*=2;
            BitmapFactory.Options opts=new BitmapFactory.Options();
            opts.inSampleSize=sample;
            Bitmap bitmap;
            try(InputStream input=getContentResolver().openInputStream(source)){
                bitmap=BitmapFactory.decodeStream(input,null,opts);
            }
            if(bitmap==null)throw new java.io.IOException("Невозможно открыть изображение");
            wallpaper.setImageBitmap(bitmap);
            wallpaper.setBackgroundColor(BG);
        }catch(Exception error){
            wallpaper.setImageDrawable(null);
            wallpaper.setBackgroundColor(BG);
            if(status!=null)status.setText("● Ошибка открытия фона: "+error.getClass().getSimpleName());
        }
    }

    private void chooseSquare() {
        Intent capture=new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        capture.putExtra(MediaStore.EXTRA_DURATION_LIMIT,60);
        capture.putExtra(MediaStore.EXTRA_SIZE_LIMIT,(long)MediaTransfer.MAX_FILE_BYTES);
        capture.putExtra(MediaStore.EXTRA_VIDEO_QUALITY,1);
        try{startActivityForResult(capture,PICK_SQUARE);}
        catch(Exception e){status.setText("● Камера недоступна");}
    }

    private void uploadSquare(Uri uri) {
        final String selectedRoom=code;
        status.setText("● Проверяем квадратное видео…");
        new Thread(()->{
            try {
                long duration=0;
                MediaMetadataRetriever metadata=new MediaMetadataRetriever();
                try{
                    metadata.setDataSource(this,uri);
                    String value=metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    if(value!=null)duration=Long.parseLong(value);
                }finally{metadata.release();}
                if(duration<1 || duration>60000L)
                    throw new IllegalArgumentException("Длительность должна быть 1–60 секунд");
                byte[] bytes=MediaTransfer.readLimited(getContentResolver().openInputStream(uri));
                sendFile(bytes,selectedRoom,"Квадрат.mp4","video/mp4","square",duration);
            }catch(Exception e){
                runOnUiThread(()->status.setText("● Квадрат: "+e.getMessage()));
            }
        },"EHTID-Square-Prepare").start();
    }

    private void chooseAttachment() {
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent,PICK_ATTACHMENT);
    }

    private String nameFor(Uri uri){
        String name="Вложение";
        try(Cursor cursor=getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)) {
            if(cursor!=null&&cursor.moveToFirst()){
                String found=cursor.getString(0);
                if(found!=null&&!found.trim().isEmpty())name=found;
            }
        }catch(Exception ignored){}
        return name;
    }

    private void uploadChosen(Uri uri) {
        final String selectedRoom=code;
        final String name=nameFor(uri);
        final String mime=getContentResolver().getType(uri);
        status.setText("● Подготовка файла…");
        new Thread(()->{
            try {
                byte[] bytes=MediaTransfer.readLimited(getContentResolver().openInputStream(uri));
                sendFile(bytes,selectedRoom,name,
                        mime==null?"application/octet-stream":mime,
                        mime!=null&&mime.startsWith("image/")?"image":"file",0);
            } catch(Exception error) {
                runOnUiThread(()->status.setText("● Вложение: "+error.getMessage()));
            }
        },"EHTID-Attachment-Prepare").start();
    }

    private void sendFile(byte[] bytes,String selectedRoom,String name,
                          String mime,String kind,long duration) throws Exception {
        JSONObject media=MediaTransfer.upload(bytes,selectedRoom,name,mime,kind,duration,
                (part,total)->runOnUiThread(()->{
                    if(selectedRoom.equals(code))
                        status.setText("● Загрузка "+part+"/"+total);
                }));
        MediaTransfer.saveLocal(this,selectedRoom,media,bytes);
        runOnUiThread(()->{
            if(!selectedRoom.equals(code)||service==null){
                status.setText("● Файл загружен, но комната сменилась. Отправка отменена.");
                return;
            }
            service.sendAttachment(media);
            status.setText("● Файл отправляется через Cloudflare");
        });
    }

    private void toggleRecording() {
        if(recorder!=null){stopRecording(true);return;}
        if(Build.VERSION.SDK_INT>=23 &&
                checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},RECORD_PERMISSION);
            return;
        }
        startRecording();
    }

    private void startRecording() {
        try {
            File dir=new File(getCacheDir(),"voice");
            if(!dir.exists()&&!dir.mkdirs())throw new java.io.IOException("Нет доступа к памяти");
            recordingFile=new File(dir,"recording-"+System.currentTimeMillis()+".m4a");
            recorder=Build.VERSION.SDK_INT>=31?new MediaRecorder(this):new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioChannels(1);
            recorder.setAudioSamplingRate(16000);
            recorder.setAudioEncodingBitRate(32000);
            recorder.setOutputFile(recordingFile.getAbsolutePath());
            recorder.setMaxDuration(3*60*1000);
            recorder.setMaxFileSize(2*1024*1024-8192);
            recorder.setOnInfoListener((mr,what,extra)->runOnUiThread(()->stopRecording(true)));
            recorder.prepare();
            recorder.start();
            recordStarted=System.currentTimeMillis();
            setIcon(micButton,R.drawable.ic_ehtid_stop,Color.rgb(255,195,205));
            micButton.setContentDescription("Завершить и отправить голосовое");
            micButton.setBackground(ripple(Color.rgb(106,45,78),14));
            status.setText("● Запись голосового… нажми ■ для отправки");
        } catch(Exception error) {
            if(recorder!=null){recorder.release();recorder=null;}
            status.setText("● Микрофон: "+error.getMessage());
        }
    }

    private void stopRecording(boolean send) {
        if(recorder==null)return;
        MediaRecorder current=recorder;
        recorder=null;
        long duration=System.currentTimeMillis()-recordStarted;
        File file=recordingFile;
        setIcon(micButton,R.drawable.ic_ehtid_mic,WHITE);
        micButton.setContentDescription("Записать голосовое сообщение");
        micButton.setBackground(ripple(PANEL,14));
        try{current.stop();}
        catch(Exception problem){send=false;}
        finally {current.reset();current.release();}
        if(!send||duration<400||file==null||!file.isFile()){
            if(file!=null)file.delete();
            status.setText("● Запись отменена");
            return;
        }
        final String selectedRoom=code;
        status.setText("● Отправляем голосовое…");
        new Thread(()->{
            try{
                byte[] audio;
                try(InputStream input=new FileInputStream(file)){
                    audio=MediaTransfer.readLimited(input);
                }
                sendFile(audio,selectedRoom,"Голосовое.m4a","audio/mp4","voice",duration);
            }catch(Exception error){
                runOnUiThread(()->status.setText("● Голосовое: "+error.getMessage()));
            }finally{file.delete();}
        },"EHTID-Voice-Upload").start();
    }

    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        if(request==PICK_WALLPAPER) {
            try {
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }catch(Exception ignored){}
            options().edit().putString("wallpaper_uri",uri.toString()).apply();
            applyWallpaper();
            refreshProfilePage();
        } else if(request==PICK_ATTACHMENT)uploadChosen(uri);
        else if(request==PICK_SQUARE)uploadSquare(uri);
    }

    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(request,permissions,grants);
        if(request==RECORD_PERMISSION&&grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)
            startRecording();
        else if(request==RECORD_PERMISSION)status.setText("● Нет разрешения на микрофон");
        if(request==205){
            if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)
                requestCallPermission(pendingCallMember,pendingAnswerCall);
            else status.setText("● Для звонков требуется разрешение микрофона");
            pendingCallMember=null;pendingAnswerCall=false;
        }
    }

    @Override public void onBackPressed(){
        if(page!=0){showPage(0);return;}
        super.onBackPressed();
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
        int shown = count;
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
            String presence = member.online ? "В сети" :
                    member.isMe ? "Подключение" : "В фоне";
            String ping = member.pingMs<0?" · —":(" · "+member.pingMs+" мс");
            row.addView(text(presence+ping,11,
                    member.pingMs<0?MUTED:pingColor(member),false));
        }
        if (count > shown)
            membersPanel.addView(text("Ещё "+(count-shown)+" участников",12,MUTED,false));
    }

    private void draw(List<JSONObject> latest) {
        if (feed == null) return;
        visibleSnapshot=new ArrayList<>(latest);
        final ChatHistory history=ChatHistory.get(this);
        final List<JSONObject> messages=historyViewLimit>300
                ? history.recent(code,historyViewLimit) : latest;
        final int allMessages=history.count(code);
        final boolean keepOffset=expandingHistory;
        final int oldFeedHeight=feed.getHeight(),oldY=scroll.getScrollY();
        expandingHistory=false;
        previewSlots.clear();
        feed.removeAllViews();
        if(allMessages>messages.size() && historyViewLimit<5000){
            TextView older=button("↑ Загрузить ранние сообщения ("+
                    (allMessages-messages.size())+")",PANEL,13);
            LinearLayout.LayoutParams olderLp=new LinearLayout.LayoutParams(-1,dp(43));
            olderLp.bottomMargin=dp(13);
            feed.addView(older,olderLp);
            older.setOnClickListener(v->{
                historyViewLimit=Math.min(historyViewLimit+200,5000);
                expandingHistory=true;
                draw(visibleSnapshot);
            });
        }
        if (messages.isEmpty()) {
            TextView welcome = text(
                    "Начните разговор\n\n" +
                    "Создай комнату или передай её код друзьям. " +
                    "Нажми ＋ для отправки фото или файла до 10 МБ, " +
                    "🎤 — для голосового. Свайп вправо — участники, влево — профиль.",
                    15,MUTED,false);
            welcome.setGravity(Gravity.CENTER);
            welcome.setPadding(dp(16),dp(50),dp(16),dp(20));
            feed.addView(welcome);
        } else {
            String lastDay=null;
            for (JSONObject m : messages) {
                long when=m.optLong("at",System.currentTimeMillis());
                String day=ChatTimeline.dayKey(when);
                if(!day.equals(lastDay)){
                    TextView divider=text(ChatTimeline.label(when,System.currentTimeMillis()),12,WHITE,true);
                    divider.setGravity(Gravity.CENTER);
                    divider.setPadding(dp(14),dp(6),dp(14),dp(6));
                    divider.setBackground(round(PANEL,16));
                    LinearLayout holder=new LinearLayout(this);
                    holder.setGravity(Gravity.CENTER);
                    LinearLayout.LayoutParams dateMargin=new LinearLayout.LayoutParams(-1,-2);
                    dateMargin.topMargin=dp(8);
                    dateMargin.bottomMargin=dp(14);
                    feed.addView(holder,dateMargin);
                    holder.addView(divider,new LinearLayout.LayoutParams(-2,-2));
                    lastDay=day;
                }
                boolean own = myId.equals(m.optString("from")) ||
                        (myId.isEmpty() && m.optString("name").equals(profile().getString("nick","")));
                LinearLayout row = new LinearLayout(this);
                row.setGravity(own ? Gravity.RIGHT : Gravity.LEFT);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1,-2);
                rp.bottomMargin=dp(10);
                feed.addView(row,rp);
                LinearLayout bubble = new LinearLayout(this);
                bubble.setOrientation(LinearLayout.VERTICAL);
                bubble.setMinimumWidth(dp(140));
                bubble.setPadding(dp(12),dp(9),dp(12),dp(8));
                bubble.setBackground(round(own?Color.rgb(77,55,123):SURFACE,18));
                row.addView(bubble);
                String author = m.optString("name");
                if (author.isEmpty()) author = own ? profile().getString("nick","Вы") : "Собеседник";
                TextView nick = text(author,11,own?Color.rgb(218,204,255):ONLINE,true);
                bubble.addView(nick);
                JSONObject media=m.optJSONObject("media");
                TextView body=text(m.optString("text"),15,WHITE,false);
                body.setMaxWidth(dp(290));
                body.setTextIsSelectable(true);
                LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-2,-2);
                tp.topMargin=dp(3);
                bubble.addView(body,tp);
                if(media!=null&&media.optString("id").matches("[a-f0-9]{32}")
                        && media.optInt("size")>0&&media.optInt("size")<=MediaTransfer.MAX_FILE_BYTES) {
                    String kind=media.optString("kind");
                    int size=media.optInt("size");
                    String info=String.format(Locale.getDefault(),"%.2f МБ",size/1048576.0);
                    if("voice".equals(kind)) {
                        long duration=media.optLong("duration",0)/1000;
                        info+=" • "+String.format(Locale.getDefault(),"%02d:%02d",duration/60,duration%60);
                    }
                    bubble.addView(text(info,11,MUTED,false));
                    if("image".equals(kind)){
                        ImageView preview=new ImageView(this);
                        preview.setBackground(round(PANEL,14));
                        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        preview.setImageResource(R.drawable.ic_ehtid_image);
                        preview.setPadding(dp(92),dp(55),dp(92),dp(55));
                        preview.setContentDescription("Фото: "+media.optString("name","изображение")+". Нажмите для просмотра");
                        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(260),dp(174));
                        ip.topMargin=dp(7);
                        bubble.addView(preview,ip);
                        TextView hint=text(size>3*1024*1024?
                                "Большое фото · нажмите для загрузки":
                                "Предпросмотр · защищённая загрузка",11,MUTED,false);
                        hint.setPadding(0,dp(4),0,dp(2));
                        bubble.addView(hint);
                        preview.setOnClickListener(v->openMedia(media));
                        if(size<=3*1024*1024)
                            previewSlots.add(new PreviewSlot(code,media,preview,hint));
                    } else if("square".equals(kind)){
                        FrameLayout tile=new FrameLayout(this);
                        tile.setBackground(round(PANEL,20));
                        ImageView poster=new ImageView(this);
                        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        tile.addView(poster,new FrameLayout.LayoutParams(-1,-1));
                        TextView play=text("▶",35,WHITE,true);
                        play.setGravity(Gravity.CENTER);
                        tile.addView(play,new FrameLayout.LayoutParams(-1,-1));
                        TextView length=text((media.optLong("duration")/1000)+" сек",12,WHITE,true);
                        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.RIGHT);
                        lp.setMargins(dp(8),dp(8),dp(10),dp(10));
                        tile.addView(length,lp);
                        LinearLayout.LayoutParams sqLp=new LinearLayout.LayoutParams(dp(195),dp(195));
                        sqLp.topMargin=dp(6);
                        bubble.addView(tile,sqLp);
                        tile.setContentDescription("Квадратное видеосообщение · воспроизвести");
                        tile.setOnClickListener(v->openMedia(media));
                    } else {
                        TextView open=labeledButton("voice".equals(kind)?"Воспроизвести":"Открыть файл",
                                "voice".equals(kind)?R.drawable.ic_ehtid_play:R.drawable.ic_ehtid_attach,ACCENT,12);
                        LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,dp(39));
                        mp.topMargin=dp(6);
                        bubble.addView(open,mp);
                        open.setOnClickListener(v->openMedia(media));
                    }
                    if("voice".equals(kind)) {
                        String cached=options().getString("transcript_"+RoomAccess.topic(code)+"_"+media.optString("id"),"");
                        TextView transcript=labeledButton(cached.isEmpty()?"Расшифровать":"Расшифровка готова",
                                R.drawable.ic_ehtid_transcript,PANEL,12);
                        LinearLayout.LayoutParams trp=new LinearLayout.LayoutParams(-1,dp(41));
                        trp.topMargin=dp(5);
                        bubble.addView(transcript,trp);
                        TextView output=text(cached.isEmpty()?"":"«"+cached+"»",13,WHITE,false);
                        output.setMaxWidth(dp(290));
                        output.setTextIsSelectable(true);
                        output.setPadding(dp(3),dp(6),dp(3),dp(3));
                        bubble.addView(output);
                        transcript.setOnClickListener(v->requestTranscription(media,output));
                    }
                }
                String clockText = new SimpleDateFormat("HH:mm",Locale.getDefault())
                        .format(new Date(m.optLong("at",System.currentTimeMillis())));
                String delivery = m.optString("delivery");
                if (own && "pending".equals(delivery)) clockText += " · В очереди";
                if (own && "relay".equals(delivery)) clockText += " · Принято реле";
                if (own && "p2p".equals(delivery)) clockText += " · Передано P2P";
                TextView time = text(clockText,10,
                        "pending".equals(delivery) ? Color.rgb(255,185,105) : MUTED,false);
                time.setGravity(Gravity.END);
                time.setMinWidth(dp(116));
                time.setMaxWidth(dp(290));
                time.setPadding(dp(4),dp(4),dp(2),0);
                // Keep metadata in its own measured row: short messages must not clip the clock.
                LinearLayout metadata=new LinearLayout(this);
                metadata.setGravity(Gravity.END);
                metadata.addView(time,new LinearLayout.LayoutParams(-2,-2));
                bubble.addView(metadata,new LinearLayout.LayoutParams(-1,-2));
            }
        }
        scroll.post(()->{
            if(keepOffset)scroll.scrollTo(0,Math.max(0,oldY+feed.getHeight()-oldFeedHeight));
            else if(page==0)scroll.fullScroll(View.FOCUS_DOWN);
            schedulePreviewChecks();
        });
    }

    private static final class PreviewSlot {
        final String room;
        final JSONObject media;
        final ImageView image;
        final TextView hint;
        boolean started;
        PreviewSlot(String room,JSONObject media,ImageView image,TextView hint) {
            this.room=room; this.media=media; this.image=image; this.hint=hint;
        }
    }
    private void schedulePreviewChecks() {
        if(previewRefreshQueued || scroll==null)return;
        previewRefreshQueued=true;
        scroll.postOnAnimation(()->{
            previewRefreshQueued=false;
            if(page!=0)return;
            Rect visible=new Rect();
            for(PreviewSlot slot:previewSlots) {
                if(slot.started || !slot.room.equals(code) ||
                        !slot.image.getGlobalVisibleRect(visible) ||
                        visible.width()<32 || visible.height()<32)continue;
                slot.started=true;
                imagePreviews.load(getApplicationContext(),slot.room,slot.media,(bitmap,error)->{
                    if(!slot.room.equals(code) || !slot.image.isAttachedToWindow())return;
                    if(bitmap!=null){
                        slot.image.setPadding(0,0,0,0);
                        slot.image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        slot.image.setImageBitmap(bitmap);
                        slot.hint.setText("Нажмите для просмотра");
                    }else{
                        slot.hint.setText("Не удалось загрузить миниатюру · нажмите для открытия");
                    }
                });
            }
        });
    }
    private int pingColor(InternetEngine.Member member) {
        if(member.pingMs<0)return MUTED;
        if(member.pingMs<120)return ONLINE;
        if(member.pingMs<400)return Color.rgb(246,199,112);
        return Color.rgb(244,137,140);
    }
    private String memberPingText(InternetEngine.Member member) {
        String route=member.isMe?"Cloudflare · HTTPS RTT":
                service!=null&&service.isPeerMesh()?"Участник · P2P RTT":"Участник · RTT через реле";
        if(member.pingMs<0||member.pingAt<=0)return route+": нет свежего замера";
        long seconds=Math.max(0,(System.currentTimeMillis()-member.pingAt)/1000);
        String age=seconds<60?seconds+" с назад":((seconds+30)/60)+" мин назад";
        return route+": "+member.pingMs+" мс · "+age;
    }
    private void refreshPingHeader() {
        if(pingHeader==null)return;
        for(InternetEngine.Member member:currentMembers) if(member.isMe) {
            pingHeader.setText(member.pingMs<0?
                    "Cloudflare · пинг —":
                    "Cloudflare · "+member.pingMs+" мс");
            pingHeader.setTextColor(pingColor(member));
            return;
        }
        pingHeader.setText("Cloudflare · пинг —");
        pingHeader.setTextColor(MUTED);
    }
    private void openMedia(JSONObject media) {
        String selectedRoom=code;
        status.setText("● Загружаем защищённое вложение…");
        new Thread(()->{
            try {
                File local=MediaTransfer.download(this,selectedRoom,media,
                        (done,total)->runOnUiThread(()->{
                            if(selectedRoom.equals(code))
                                status.setText("● Загрузка "+done+"/"+total);
                        }));
                final String kind=media.optString("kind");
                final Bitmap expanded="image".equals(kind)?
                        ImagePreviews.decode(local,1600,2000):null;
                runOnUiThread(()->{
                    if(!selectedRoom.equals(code))return;
                    status.setText("● Вложение готово");
                    if("voice".equals(kind))playVoice(local);
                    else if("image".equals(kind))showImage(expanded);
                    else if("square".equals(kind))playSquare(local);
                    else openOtherFile(local,media.optString("mime","application/octet-stream"));
                });
            } catch(Exception error){
                runOnUiThread(()->status.setText("● Не удалось открыть файл: "+error.getMessage()));
            }
        },"EHTID-Download").start();
    }

    private void playSquare(File local) {
        VideoView video=new VideoView(this);
        video.setVideoPath(local.getAbsolutePath());
        video.setOnPreparedListener(mp->{mp.setLooping(true);video.start();});
        FrameLayout frame=new FrameLayout(this);
        frame.setBackgroundColor(Color.BLACK);
        frame.addView(video,new FrameLayout.LayoutParams(-1,dp(320)));
        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle("Квадрат EHTID")
                .setView(frame)
                .setPositiveButton("Закрыть",null).create();
        dialog.setOnDismissListener(d->video.stopPlayback());
        dialog.show();
    }

    private void playVoice(File local) {
        if(voicePlayer!=null){
            voicePlayer.release();
            voicePlayer=null;
        }
        try {
            voicePlayer=new MediaPlayer();
            voicePlayer.setDataSource(local.getAbsolutePath());
            voicePlayer.setOnPreparedListener(mp->{
                mp.start();
                status.setText("● Воспроизводится голосовое");
            });
            voicePlayer.setOnCompletionListener(mp->{
                mp.release();
                if(voicePlayer==mp)voicePlayer=null;
                status.setText("● Голосовое прослушано");
            });
            voicePlayer.setOnErrorListener((mp,what,extra)->{
                mp.release();
                if(voicePlayer==mp)voicePlayer=null;
                status.setText("● Ошибка воспроизведения");
                return true;
            });
            voicePlayer.prepareAsync();
        } catch(Exception error) {
            status.setText("● Ошибка аудио: "+error.getMessage());
        }
    }

    private void showImage(Bitmap bitmap) {
        if(bitmap==null){status.setText("● Не удалось открыть изображение");return;}
        ImageView view=new ImageView(this);
        view.setImageBitmap(bitmap);
        view.setAdjustViewBounds(true);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        new AlertDialog.Builder(this)
                .setTitle("Изображение из чата")
                .setView(view)
                .setPositiveButton("Закрыть",null).show();
    }

    private void openOtherFile(File local,String mime) {
        try {
            Uri uri=FileProvider.getUriForFile(this,
                    "com.ehtid.p2pchat.files",local);
            Intent open=new Intent(Intent.ACTION_VIEW);
            open.setDataAndType(uri,mime==null?"application/octet-stream":mime);
            open.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(open,"Открыть вложение"));
        }catch(Exception error){
            status.setText("● Нет приложения для открытия этого файла");
        }
    }

    private void requestTranscription(JSONObject meta,TextView output) {
        final String selectedRoom=code;
        final String cacheKey="transcript_"+RoomAccess.topic(selectedRoom)+"_"+meta.optString("id");
        String cached=options().getString(cacheKey,"");
        if(!cached.isEmpty()){
            output.setText("«"+cached+"»");
            return;
        }
        if(pendingTranscriptions.contains(cacheKey)){
            output.setText("Распознавание уже выполняется…");
            return;
        }
        // Never transmit plaintext voice audio without the user's consent.
        final String[] languages={"Автоматически","Русский","English"};
        final String[] codes={"auto","ru","en"};
        final int[] selected={0};
        LinearLayout panel=new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(22),dp(8),dp(22),dp(7));
        TextView explanation=text("Голосовое будет отправлено в Cloudflare Workers AI "+
                "без сквозного шифрования. Текст останется только на вашем телефоне.",13,MUTED,false);
        panel.addView(explanation);
        RadioGroup choices=new RadioGroup(this);
        for(int i=0;i<languages.length;i++){
            RadioButton item=new RadioButton(this);
            item.setText(languages[i]);
            item.setTextColor(WHITE);
            item.setTextSize(14);
            item.setId(1000+i);
            item.setPadding(0,dp(5),0,dp(5));
            choices.addView(item);
        }
        choices.check(1000);
        choices.setOnCheckedChangeListener((group,checked)->selected[0]=Math.max(0,Math.min(2,checked-1000)));
        LinearLayout.LayoutParams choiceLp=new LinearLayout.LayoutParams(-1,-2);
        choiceLp.topMargin=dp(8);
        panel.addView(choices,choiceLp);
        new AlertDialog.Builder(this)
                .setTitle("Распознавание речи")
                .setView(panel)
                .setNegativeButton("Отмена",null)
                .setPositiveButton("Отправить и распознать",(dialog,which)->
                        transcribeMedia(meta,output,selectedRoom,codes[selected[0]]))
                .show();
    }

    private void transcribeMedia(JSONObject meta,TextView output,String selectedRoom,String language) {
        final String cacheKey="transcript_"+RoomAccess.topic(selectedRoom)+"_"+meta.optString("id");
        if(!pendingTranscriptions.add(cacheKey))return;
        output.setText("Распознаём голосовое…");
        status.setText("● Распознаём речь через Cloudflare AI…");
        new Thread(()->{
            try {
                File file=MediaTransfer.download(this,selectedRoom,meta,null);
                byte[] audio;
                try(InputStream in=new FileInputStream(file)){
                    audio=MediaTransfer.readLimited(in);
                }
                String transcript=MediaTransfer.transcribe(audio,selectedRoom,language);
                runOnUiThread(()->{
                    pendingTranscriptions.remove(cacheKey);
                    // Persist per-room transcript even when the user switched rooms.
                    options().edit().putString(cacheKey,transcript).apply();
                    if(!selectedRoom.equals(code))return;
                    output.setText("«"+transcript+"»");
                    status.setText("● Расшифровка готова");
                });
            }catch(Exception error){
                final String reason=error.getMessage()==null?"Ошибка сети":error.getMessage();
                runOnUiThread(()->{
                    pendingTranscriptions.remove(cacheKey);
                    if(!selectedRoom.equals(code))return;
                    output.setText("Не удалось распознать: "+reason);
                    status.setText("● Ошибка распознавания. Можно повторить.");
                });
            }
        },"EHTID-Whisper").start();
    }

    private void launchChat(String room, boolean joining) {
        if (profile().getString("nick","").trim().isEmpty()) {
            askName(true);
            return;
        }
        if(room==null||!room.matches("[A-Z2-9]{16,32}"))return;
        boolean changed=!room.equals(code);
        code=room;
        codeLabel.setText(code);
        codePeek.setText("Код "+code.substring(0,6)+"… · открыть доступ");
        savedRooms.remember(room);
        getPreferences(MODE_PRIVATE).edit().putString("room",room).apply();
        if(changed){
            historyViewLimit=300;
            visibleSnapshot=new ArrayList<>();
            input.setText("");
        }
        ChatHistory history=ChatHistory.get(this);
        history.migrateLegacy(profile(),room);
        draw(history.recent(room,historyViewLimit));
        currentMembers.clear();
        drawMembers();
        if(peopleContent!=null)refreshPeoplePage();
        if(profileContent!=null)refreshProfilePage();
        if(pages!=null)showPage(0);
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
                bindRequested = bindService(new Intent(this,ChatService.class),
                        conn,Context.BIND_AUTO_CREATE);
            } catch (RuntimeException e) {
                status.setText("● Ошибка привязки: " + e.getMessage());
            }
        } else if (service != null) {
            service.switchRoom(room,joining);
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

    private void requestCallPermission(InternetEngine.Member peer,boolean answer) {
        if(service==null){status.setText("● Нет подключения к чату");return;}
        if(Build.VERSION.SDK_INT>=23 &&
                checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            pendingCallMember=peer;
            pendingAnswerCall=answer;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},205);
            return;
        }
        if(answer)service.answerCall();
        else if(peer!=null)service.callMember(peer.id,peer.name);
    }

    private void showCallStatus(String phase,String nickname){
        if(!phase.equals(lastCallPhase)) {
            lastCallPhase=phase;
            if("incoming".equals(phase) && active && !isFinishing()) {
                if(incomingCallDialog!=null)incomingCallDialog.dismiss();
                incomingCallDialog=new AlertDialog.Builder(this)
                        .setTitle("Входящий звонок EHTID")
                        .setMessage("Звонит "+nickname)
                        .setNegativeButton("Отклонить",(d,w)->{
                            if(service!=null)service.declineCall();
                        })
                        .setPositiveButton("Ответить",(d,w)->{
                            requestCallPermission(null,true);
                        }).create();
                incomingCallDialog.show();
            }
            if("idle".equals(phase) && incomingCallDialog!=null){
                incomingCallDialog.dismiss();incomingCallDialog=null;
            }
        }
        if(!"idle".equals(phase) && status!=null)
            status.setText("● Звонок: "+nickname+" · "+phase);
    }
    private void openCallControls(){
        if(service==null){status.setText("● Сначала подключитесь к комнате");return;}
        String phase=service.callPhase();
        if("incoming".equals(phase)) {
            new AlertDialog.Builder(this).setTitle("Входящий звонок")
                    .setMessage(service.callPeerName())
                    .setPositiveButton("Ответить",(d,w)->requestCallPermission(null,true))
                    .setNegativeButton("Отклонить",(d,w)->service.declineCall()).show();
        }else if(!"idle".equals(phase)) {
            new AlertDialog.Builder(this).setTitle("Голосовой звонок")
                    .setMessage(service.callPeerName()+" · "+phase)
                    .setPositiveButton("Завершить",(d,w)->service.endCall())
                    .setNegativeButton("Закрыть",null).show();
        }else {
            showPage(1);
            Toast.makeText(this,"Выбери участника и нажми «Позвонить»",Toast.LENGTH_LONG).show();
        }
    }

    private void settingsDialog() {
        String[] labels={"Уведомления о сообщениях","Работа чата в фоне",
                "Показывать текст сообщений","Звук уведомлений"};
        String[] keys={"notifications","background","preview","sound",
                "calls_notifications","vibration"};
        boolean[] checked=new boolean[keys.length];
        for(int i=0;i<keys.length;i++)
            checked[i]=options().getBoolean(keys[i],true);
        new AlertDialog.Builder(this)
                .setTitle("Настройки")
                .setMessage("Cloudflare подключён автоматически. Для входа нужен только код комнаты.")
                .setMultiChoiceItems(labels,checked,(d,which,enabled)->{
                    options().edit().putBoolean(keys[which],enabled).apply();
                    if(which==1 && service!=null) service.refreshSettings();
                    if(which==0 && enabled) maybeAskNotification();
                })
                .setNegativeButton("Изменить имя",(d,w)->askName(false))
                .setPositiveButton("Готово",(d,w)->{})
                .show();
    }

    private TextView button(String label,int color,int sp) {
        TextView v = text(label,sp,WHITE,true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(12),dp(9),dp(12),dp(9));
        v.setMinHeight(dp(40));
        v.setBackground(ripple(color,12));
        return v;
    }
    private RippleDrawable ripple(int color,int radius) {
        return new RippleDrawable(ColorStateList.valueOf(Color.argb(55,255,255,255)),
                round(color,radius),round(Color.WHITE,radius));
    }
    private void setIcon(TextView view,int resource,int tint) {
        Drawable glyph=getDrawable(resource);
        if(glyph!=null){
            glyph=glyph.mutate();
            glyph.setTint(tint);
            glyph.setBounds(0,0,dp(23),dp(23));
            view.setCompoundDrawables(glyph,null,null,null);
        }
    }
    private TextView iconButton(int icon,int bg,String description) {
        TextView v=button("",bg,17);
        v.setPadding(dp(10),dp(10),dp(10),dp(10));
        setIcon(v,icon,WHITE);
        v.setContentDescription(description);
        return v;
    }
    private TextView labeledButton(String label,int icon,int bg,int size) {
        TextView v=button(label,bg,size);
        setIcon(v,icon,WHITE);
        v.setCompoundDrawablePadding(dp(7));
        v.setContentDescription(label);
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

    @Override protected void onDestroy() {
        imagePreviews.close();
        if(recorder!=null)stopRecording(false);
        if(voicePlayer!=null) {
            try {voicePlayer.release();} catch(Exception ignored){}
            voicePlayer=null;
        }
        super.onDestroy();
    }

    @Override protected void onStart() {
        super.onStart();
        active=true;
        if (profile().getString("nick","").trim().length()>0 && !bindRequested) launchChat(code,false);
        if(service!=null) service.setVisible(page==0);
        if(readyUpdate!=null&&readyUpdate.ready()&&
                !readyUpdate.version.equals(lastPromptedUpdate))
            showUpdatePrompt(readyUpdate);
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
