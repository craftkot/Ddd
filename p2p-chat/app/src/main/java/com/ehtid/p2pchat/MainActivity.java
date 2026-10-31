package com.ehtid.p2pchat;

import android.app.Activity;
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
    private ScrollView scroller;
    private EditText input;
    private TextView status;
    private TextView empty;
    private PeerEngine engine;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(12));
        root.setBackgroundColor(BG);

        TextView title = text("EHTID  /  P2P CHAT", 24, WHITE, true);
        title.setLetterSpacing(0.05f);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(37)));

        TextView lead = text("Прямой чат без облачного сервера", 13, MUTED, false);
        root.addView(lead);

        status = text("●  Ищем чат в локальной сети…", 13, Color.rgb(120, 190, 255), false);
        status.setPadding(dp(12), dp(11), dp(12), dp(11));
        status.setBackground(round(SURFACE, 14));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.topMargin = dp(17);
        root.addView(status, statusParams);

        scroller = new ScrollView(this);
        scroller.setFillViewport(true);
        scroller.setVerticalScrollBarEnabled(false);
        feed = new LinearLayout(this);
        feed.setOrientation(LinearLayout.VERTICAL);
        feed.setPadding(0, dp(16), 0, dp(16));
        scroller.addView(feed, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams scrollerParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        scrollerParams.topMargin = dp(10);
        root.addView(scroller, scrollerParams);

        LinearLayout composer = new LinearLayout(this);
        composer.setGravity(Gravity.CENTER_VERTICAL);
        composer.setOrientation(LinearLayout.HORIZONTAL);
        composer.setPadding(dp(7), dp(6), dp(6), dp(6));
        composer.setBackground(round(SURFACE, 19));

        input = new EditText(this);
        input.setTextColor(WHITE);
        input.setHintTextColor(MUTED);
        input.setTextSize(15);
        input.setHint("Сообщение…");
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setPadding(dp(8), dp(5), dp(8), dp(5));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setMaxLines(4);
        composer.addView(input, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView send = text("➤", 23, Color.WHITE, true);
        send.setGravity(Gravity.CENTER);
        send.setBackground(round(ACCENT, 14));
        composer.addView(send, new LinearLayout.LayoutParams(dp(50), dp(49)));
        send.setOnClickListener(v -> {
            String message = input.getText().toString().trim();
            if (message.isEmpty()) return;
            if (message.length() > 1200) {
                status.setText("●  Максимальная длина сообщения — 1200 символов");
                return;
            }
            engine.send(message);
            input.setText("");
        });
        root.addView(composer);
        setContentView(root);

        engine = new PeerEngine(getApplicationContext(), new PeerEngine.Listener() {
            @Override public void onStatus(String message) {
                status.setText("●  " + message);
            }
            @Override public void onMessages(List<JSONObject> messages) {
                drawMessages(messages);
            }
        });
        drawMessages(engine.snapshot());
        engine.start();
    }

    private void drawMessages(List<JSONObject> messages) {
        feed.removeAllViews();
        if (messages.isEmpty()) {
            LinearLayout placeholder = new LinearLayout(this);
            placeholder.setGravity(Gravity.CENTER);
            placeholder.setOrientation(LinearLayout.VERTICAL);
            TextView icon = text("◉", 48, ACCENT, true);
            icon.setGravity(Gravity.CENTER);
            placeholder.addView(icon);
            TextView heading = text("Начните переписку", 20, WHITE, true);
            heading.setGravity(Gravity.CENTER);
            placeholder.addView(heading);
            TextView desc = text("Отправьте первое сообщение — этот телефон станет хостом. Второй телефон в той же Wi-Fi сети подключится автоматически.", 14, MUTED, false);
            desc.setGravity(Gravity.CENTER);
            desc.setPadding(dp(18), dp(10), dp(18), 0);
            placeholder.addView(desc);
            feed.addView(placeholder, new LinearLayout.LayoutParams(-1, dp(260)));
        } else {
            for (JSONObject msg : messages) {
                boolean own = engine != null && engine.myId().equals(msg.optString("from"));
                LinearLayout line = new LinearLayout(this);
                line.setOrientation(LinearLayout.HORIZONTAL);
                line.setGravity(own ? Gravity.RIGHT : Gravity.LEFT);
                LinearLayout.LayoutParams row = new LinearLayout.LayoutParams(-1, -2);
                row.bottomMargin = dp(10);
                feed.addView(line, row);

                LinearLayout bubble = new LinearLayout(this);
                bubble.setOrientation(LinearLayout.VERTICAL);
                bubble.setPadding(dp(13), dp(10), dp(13), dp(9));
                bubble.setBackground(round(own ? Color.rgb(31, 100, 194) : SURFACE, 17));
                line.addView(bubble, new LinearLayout.LayoutParams(-2, -2));

                TextView body = text(msg.optString("text"), 16, WHITE, false);
                body.setMaxWidth(dp(280));
                body.setTextIsSelectable(true);
                bubble.addView(body);

                String time = new SimpleDateFormat("HH:mm", Locale.getDefault())
                        .format(new Date(msg.optLong("at", System.currentTimeMillis())));
                TextView timestamp = text(time, 11, own ? Color.rgb(190, 215, 255) : MUTED, false);
                timestamp.setGravity(Gravity.RIGHT);
                LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
                tp.topMargin = dp(3);
                bubble.addView(timestamp, tp);
            }
        }
        scroller.post(() -> scroller.fullScroll(View.FOCUS_DOWN));
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }
    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(radiusDp));
        return background;
    }
    private int dp(float value) {
        return (int)(getResources().getDisplayMetrics().density * value + 0.5f);
    }
    @Override public void onDestroy() {
        if (engine != null) engine.stop();
        super.onDestroy();
    }
}
