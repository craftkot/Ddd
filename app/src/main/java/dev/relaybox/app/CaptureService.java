package dev.relaybox.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CaptureService extends NotificationListenerService {
    static final String ACTION_NEW="dev.relaybox.app.NEW_MESSAGE";
    private final ExecutorService queue=Executors.newSingleThreadExecutor();

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        Notification n=sbn.getNotification();
        Platform p=Platform.recognize(this,sbn.getPackageName(),n);
        if(p==null || (n.flags & Notification.FLAG_GROUP_SUMMARY)!=0) return;
        Bundle e=n.extras;
        String sender=Platform.str(e,Notification.EXTRA_TITLE);
        String body=Platform.str(e,Notification.EXTRA_BIG_TEXT);
        if(body.isEmpty()) body=Platform.str(e,Notification.EXTRA_TEXT);
        if(body.isEmpty()) return;
        if(sender.isEmpty()) sender=p.label;
        final String finalSender=sender, finalBody=body;
        final long timestamp=sbn.getPostTime();
        final String fingerprint=sbn.getKey()+"|"+sender.hashCode()+"|"+body.hashCode();
        queue.execute(() -> {
            try(MessageStore store=new MessageStore(getApplicationContext())) {
                long id=store.add(fingerprint,p,finalSender,finalBody,timestamp);
                if(id<0) return;
                if(getSharedPreferences("relaybox",0).getBoolean("mirror",false)) {
                    showMirror(id,p,finalSender,finalBody);
                }
                Intent changed=new Intent(ACTION_NEW);
                changed.setPackage(getPackageName());
                sendBroadcast(changed);
            } catch(Exception ignored) {
                // Encryption errors fail closed; never persist unencrypted fallback.
            }
        });
    }
    private void showMirror(long id,Platform p,String sender,String body) {
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) return;
        NotificationManager manager=(NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        if(manager==null) return;
        String channel="relaybox_inbox";
        manager.createNotificationChannel(new NotificationChannel(channel,"RelayBox · новые сообщения",NotificationManager.IMPORTANCE_DEFAULT));
        Intent intent=new Intent(this,ChatActivity.class);
        intent.putExtra("platform",p.id);
        intent.putExtra("row",id);
        PendingIntent pending=PendingIntent.getActivity(this,(int)(id & 0x7fffffff),intent,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification publicPart=new Notification.Builder(this,channel)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle("RelayBox · новое сообщение")
                .setContentText("Откройте приложение").build();
        Notification alert=new Notification.Builder(this,channel)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle(p.label+" · "+sender)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPublicVersion(publicPart)
                .setContentIntent(pending)
                .setCategory(Notification.CATEGORY_MESSAGE).build();
        manager.notify((int)(id & 0x7fffffff),alert);
    }
    @Override public void onDestroy() {
        queue.shutdown();
        super.onDestroy();
    }
}
