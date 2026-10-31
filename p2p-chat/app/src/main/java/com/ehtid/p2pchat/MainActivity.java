package com.ehtid.p2pchat;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONObject;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(12, 18, 30);
    private static final int SURFACE = Color.rgb(23, 32, 49);
    private static final int ACCENT = Color.rgb(65, 145, 255);
    private static final int WHITE = Color.rgb(238, 244, 255);
    private static final int MUTED = Color.rgb(153, 168, 193);
    private LinearLayout feed;
    private ScrollView scroll;
    private EditText input, joinInput;
    private TextView status, codeLabel;
    private InternetEngine engine;
    private String code;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        code = getPreferences(MODE_PRIVATE).getString("room", "");
        if (code.isEmpty()) code = newCode();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(15), dp(13), dp(15), dp(12));
        root.setBackgroundColor(BG);

        TextView title = text("EHTID  /  INTERNET P2P", 21, WHITE, true);
        title.setLetterSpacing(0.05f);
        root.addView(title);
        TextView subtitle = text("Любая сеть • WebRTC + резервное реле", 12, MUTED, false);
        subtitle.setPadding(0, dp(4), 0, dp(12));
        root.addView(subtitle);

        LinearLayout roomBox = new LinearLayout(this);
        roomBox.setOrientation(LinearLayout.VERTICAL);
        roomBox.setPadding(dp(12), dp(10), dp(12), dp(12));
        roomBox.setBackground(round(SURFACE, 14));
        root.addView(roomBox, new LinearLayout.LayoutParams(-1, -2));

        TextView label = text("КОД КОМНАТЫ • передай собеседнику", 11, MUTED, true);
        roomBox.addView(label);
        codeLabel = text(code, 19, WHITE, true);
        codeLabel.setLetterSpacing(0.07f);
        codeLabel.setTextIsSelectable(true);
        codeLabel.setPadding(0, dp(4), 0, dp(8));
        roomBox.addView(codeLabel);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        roomBox.addView(row);
        TextView copy = button("Копировать", ACCENT);
        row.addView(copy);
        copy.setOnClickListener(v -> {
            ((ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE))
                    .setPrimaryClip(ClipData.newPlainText("Код комнаты EHTID", code));
            status.setText("●  Код скопирован. Отправь его второму человеку.");
        });
        TextView newRoom = button("Новая комната", Color.rgb(63, 75, 98));
        LinearLayout.LayoutParams nr = new LinearLayout.LayoutParams(-2, -2);
        nr.leftMargin = dp(8);
        row.addView(newRoom, nr);
        newRoom.setOnClickListener(v -> openRoom(newCode(), false));

        LinearLayout join = new LinearLayout(this);
        join.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams jp = new LinearLayout.LayoutParams(-1, -2);
        jp.topMargin = dp(10);
        roomBox.addView(join, jp);
        joinInput = new EditText(this);
        joinInput.setTextSize(14);
        joinInput.setTextColor(WHITE);
        joinInput.setHintTextColor(MUTED);
        joinInput.setHint("Вставь код собеседника");
        joinInput.setSingleLine(true);
        joinInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        joinInput.setPadding(dp(9), dp(6), dp(9), dp(6));
        joinInput.setBackground(round(Color.rgb(35, 47, 67), 10));
        join.addView(joinInput, new LinearLayout.LayoutParams(0, dp(44), 1f));
        TextView joinButton = button("Войти", ACCENT);
        LinearLayout.LayoutParams jb = new LinearLayout.LayoutParams(-2, dp(44));
        jb.leftMargin = dp(6);
        join.addView(joinButton, jb);
        joinButton.setOnClickListener(v -> {
            String target = InternetEngine.normalize(joinInput.getText().toString());
            if (!target.matches("[A-Z2-9]{16,32}")) {
                status.setText("●  Код должен содержать 16–32 символа A–Z / 2–9.");
            } else if (target.equals(code)) {
                status.setText("●  Ты уже в этой комнате.");
            } else {
                openRoom(target, true);
                joinInput.setText("");
            }
        });

        status = text("●  Подключение к интернету…", 12, Color.rgb(130, 196, 255), false);
        status.setPadding(dp(10), dp(10), dp(10), dp(10));
        status.setBackground(round(SURFACE, 12));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = dp(10);
        root.addView(status, sp);

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        feed = new LinearLayout(this);
        feed.setOrientation(LinearLayout.VERTICAL);
        feed.setPadding(0, dp(12), 0, dp(12));
        scroll.addView(feed, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout composer = new LinearLayout(this);
        composer.setGravity(Gravity.CENTER_VERTICAL);
        composer.setPadding(dp(6), dp(5), dp(5), dp(5));
        composer.setBackground(round(SURFACE, 17));
        input = new EditText(this);
        input.setTextColor(WHITE);
        input.setHintTextColor(MUTED);
        input.setTextSize(15);
        input.setHint("Сообщение…");
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setPadding(dp(7), dp(4), dp(7), dp(4));
        input.setInputType(InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setMaxLines(4);
        composer.addView(input, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView send = button("➤", ACCENT);
        send.setTextSize(23);
        composer.addView(send, new LinearLayout.LayoutParams(dp(51), dp(49)));
        send.setOnClickListener(v -> {
            String value = input.getText().toString().trim();
            if (value.length() == 0) return;
            if (value.length() > 1000) {
                status.setText("●  Не более 1000 символов в сообщении");
                return;
            }
            engine.sendMessage(value);
            input.setText("");
        });
        root.addView(composer);
        setContentView(root);
        openRoom(code, false);
    }

    private void openRoom(String room, boolean joining) {
        if (engine != null) engine.stop();
        code = room;
        getPreferences(MODE_PRIVATE).edit().putString("room", room).apply();
        codeLabel.setText(room);
        status.setText("●  Подключаем интернет-канал…");
        engine = new InternetEngine(getApplicationContext(), room, joining,
                new InternetEngine.Listener() {
            @Override public void onStatus(String message) {
                status.setText("●  " + message);
            }
            @Override public void onMessages(List<JSONObject> messages) {
                draw(messages);
            }
        });
        draw(engine.snapshot());
        engine.start();
    }

    private void draw(List<JSONObject> messages) {
        feed.removeAllViews();
        if (messages.isEmpty()) {
            TextView welcome = text(
                "Первый отправивший сообщение становится хостом.\n\n" +
                "На втором телефоне вставь его код комнаты и нажми «Войти». " +
                "Приложение само найдёт собеседника через интернет.", 15, MUTED, false);
            welcome.setGravity(Gravity.CENTER);
            welcome.setPadding(dp(16), dp(50), dp(16), dp(20));
            feed.addView(welcome);
        } else {
            for (JSONObject m : messages) {
                boolean mine = engine != null && engine.myId().equals(m.optString("from"));
                LinearLayout row = new LinearLayout(this);
                row.setGravity(mine ? Gravity.RIGHT : Gravity.LEFT);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
                rp.bottomMargin = dp(9);
                feed.addView(row, rp);
                LinearLayout bubble = new LinearLayout(this);
                bubble.setOrientation(LinearLayout.VERTICAL);
                bubble.setPadding(dp(13), dp(9), dp(13), dp(8));
                bubble.setBackground(round(mine ? Color.rgb(34, 100, 190) : SURFACE, 15));
                row.addView(bubble);
                TextView msg = text(m.optString("text"), 15, WHITE, false);
                msg.setMaxWidth(dp(285));
                msg.setTextIsSelectable(true);
                bubble.addView(msg);
                String timestamp = new SimpleDateFormat("HH:mm", Locale.getDefault())
                        .format(new Date(m.optLong("at", System.currentTimeMillis())));
                TextView time = text(timestamp, 10, MUTED, false);
                time.setGravity(Gravity.RIGHT);
                bubble.addView(time);
            }
        }
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private TextView button(String label, int color) {
        TextView v = text(label, 13, WHITE, true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(12), dp(10), dp(12), dp(10));
        v.setBackground(round(color, 12));
        return v;
    }
    private TextView text(String s, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }
    private GradientDrawable round(int color, int d) {
        GradientDrawable b = new GradientDrawable();
        b.setColor(color);
        b.setCornerRadius(dp(d));
        return b;
    }
    private int dp(int n) {
        return (int)(getResources().getDisplayMetrics().density * n + 0.5f);
    }
    private static String newCode() {
        SecureRandom rng = new SecureRandom();
        final String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder s = new StringBuilder();
        for (int i=0; i<20; i++) s.append(alphabet.charAt(rng.nextInt(alphabet.length())));
        return s.toString();
    }
    @Override protected void onDestroy() {
        if (engine != null) engine.stop();
        super.onDestroy();
    }
}
