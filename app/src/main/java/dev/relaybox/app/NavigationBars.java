package dev.relaybox.app;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

/**
 * Hide only Android's OS navigation bar, never the app's bottom tabs or status bar.
 * A swipe from the bottom temporarily reveals system controls.
 */
final class NavigationBars {
    private NavigationBars() {}

    static boolean isEnabled(Activity activity) {
        return activity.getSharedPreferences("relaybox", 0)
                .getBoolean("hide_system_navigation", true);
    }

    static void apply(Activity activity) {
        boolean hide = isEnabled(activity);
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = activity.getWindow().getInsetsController();
            if (controller == null) return;
            int type = WindowInsets.Type.navigationBars();
            if (hide) {
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(type);
            } else {
                controller.show(type);
            }
        } else {
            View decor = activity.getWindow().getDecorView();
            int mask = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            int flags = decor.getSystemUiVisibility();
            decor.setSystemUiVisibility(hide ? (flags | mask) : (flags & ~mask));
        }
    }
}
