package dev.relaybox.app;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class AppSmokeTest {
    @Rule public ActivityScenarioRule<MainActivity> rule =
            new ActivityScenarioRule<>(MainActivity.class);

    private static View find(View root, String text) {
        if (root instanceof TextView) {
            CharSequence current=((TextView) root).getText();
            if(current!=null && current.toString().contains(text))return root;
        }
        if(root instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)root;
            for(int i=0;i<group.getChildCount();i++) {
                View result=find(group.getChildAt(i),text);
                if(result!=null)return result;
            }
        }
        return null;
    }
    private static void tap(Activity a,String caption) {
        View view=find(a.getWindow().getDecorView(),caption);
        assertNotNull("Missing clickable view: "+caption,view);
        assertTrue("Click failed: "+caption,view.performClick());
    }
    private static void contains(Activity a,String caption) {
        assertNotNull("Missing screen text: "+caption,
                find(a.getWindow().getDecorView(),caption));
    }
    @Test public void openEveryTabAndStoreTestMessages() {
        rule.getScenario().onActivity(a -> {
            contains(a,"RelayBox");
            contains(a,"Входящие");
            tap(a,"Сервисы");
            contains(a,"Авито");
            contains(a,"FunPay");
            contains(a,"Playerok");
            tap(a,"Настройки");
            contains(a,"Тестовые сообщения");
            tap(a,"Тестовые сообщения");
            contains(a,"Тестовый покупатель");
            tap(a,"Настройки");
            contains(a,"Удалить историю");
            tap(a,"Входящие");
            contains(a,"Тестовый покупатель");
        });
    }
}
