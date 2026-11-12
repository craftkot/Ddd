package com.ehtid.p2pchat;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.util.concurrent.TimeUnit;

/**
 * Inexact periodic update discovery/download (Android Doze may defer it).
 * OS installation requires a user tap; notifications never silently install.
 */
public final class UpdateCheckWorker extends Worker {
    private static final String WORK_NAME="ehtid-github-releases-update";
    private static final String CHANNEL="ehtid_app_updates";
    public UpdateCheckWorker(Context context,WorkerParameters parameters) {
        super(context,parameters);
    }
    public static void schedule(Context ctx) {
        Context app=ctx.getApplicationContext();
        WorkManager manager=WorkManager.getInstance(app);
        // Previous releases used two different update schedulers. Migrate away
        // from them, otherwise a single phone can download the APK repeatedly.
        manager.cancelUniqueWork("ehtid-signed-github-update");
        JobScheduler jobs=(JobScheduler)app.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if(jobs!=null)jobs.cancel(41260);
        boolean automatic=app.getSharedPreferences("ehtid_updater",Context.MODE_PRIVATE)
                .getBoolean("automatic",true);
        if(!automatic){
            manager.cancelUniqueWork(WORK_NAME);
            return;
        }
        Constraints requirements=new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED).build();
        PeriodicWorkRequest request=new PeriodicWorkRequest.Builder(
                UpdateCheckWorker.class,12,TimeUnit.HOURS)
                .setConstraints(requirements).build();
        manager.enqueueUniquePeriodicWork(WORK_NAME,ExistingPeriodicWorkPolicy.KEEP,request);
    }
    @Override public Result doWork() {
        try {
            ReleaseUpdater.Result status=ReleaseUpdater.checkAndDownload(
                    getApplicationContext(),false);
            if(status.ready())notifyReady(status);
            return Result.success();
        }catch(Exception problem) {
            // A short outage shouldn't use up repeated background retries:
            // the next scheduled check will try again.
            return Result.success();
        }
    }
    private void notifyReady(ReleaseUpdater.Result status) {
        Context context=getApplicationContext();
        SharedPreferences prefs=context.getSharedPreferences("ehtid_updater",
                Context.MODE_PRIVATE);
        if(status.version.equals(prefs.getString("notified_version","")))return;
        if(Build.VERSION.SDK_INT>=33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=
                        PackageManager.PERMISSION_GRANTED)return;
        NotificationManager manager=(NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(manager==null)return;
        if(Build.VERSION.SDK_INT>=26)
            manager.createNotificationChannel(new NotificationChannel(CHANNEL,
                    "Обновления EHTID",NotificationManager.IMPORTANCE_DEFAULT));
        Intent intent=new Intent(context,ReleaseInstallActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags=PendingIntent.FLAG_UPDATE_CURRENT;
        if(Build.VERSION.SDK_INT>=23)flags|=PendingIntent.FLAG_IMMUTABLE;
        PendingIntent open=PendingIntent.getActivity(context,710,intent,flags);
        manager.notify(710,new NotificationCompat.Builder(context,CHANNEL)
                .setSmallIcon(R.drawable.ic_chat_notification)
                .setContentTitle("EHTID Chat "+status.version+" готов")
                .setContentText("Обновление загружено. Нажми, чтобы установить поверх текущей версии.")
                .setAutoCancel(true)
                .setContentIntent(open).build());
        prefs.edit().putString("notified_version",status.version).apply();
    }
}
