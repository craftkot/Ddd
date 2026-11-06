package com.ehtid.p2pchat;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import androidx.core.content.FileProvider;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Checks public GitHub Releases, downloads a newer EHTID APK into private
 * app storage, checks GitHub SHA256 + the existing Android signing identity.
 *
 * Android does NOT allow ordinary apps to silently install APKs: an explicit
 * system installer approval is shown. In-place installation retains all
 * SharedPreferences histories and private media files.
 */
public final class AppUpdater {
    private static final String API="https://api.github.com/repos/craftkot/Ddd/releases?per_page=35";
    private static final String WORK_NAME="ehtid-signed-github-update";
    private static final String SETTINGS="ehtid_update_settings";
    private static final String CHANNEL="ehtid_app_updates";
    private static final int NOTIFICATION=7021;
    private static final int MAX_RELEASE_JSON=2*1024*1024;
    private static final long MAX_APK=150L*1024*1024;
    private static final long CHECK_INTERVAL=60L*60*1000;
    private static final Handler MAIN=new Handler(Looper.getMainLooper());

    private AppUpdater() {}

    public static final class Result {
        public final String message;
        public final boolean newerReady;
        Result(String message,boolean newerReady){
            this.message=message;this.newerReady=newerReady;
        }
    }

    private static final class Release {
        final String version, url, digest;
        final long size;
        Release(String version,String url,String digest,long size) {
            this.version=version;this.url=url;this.digest=digest;this.size=size;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(SETTINGS,Context.MODE_PRIVATE);
    }

    public static boolean enabled(Context context){
        return prefs(context).getBoolean("automatic",true);
    }

    public static boolean wifiOnly(Context context){
        return prefs(context).getBoolean("wifi_only",false);
    }

    public static void configure(Context context,boolean automatic,boolean wifiOnly){
        prefs(context).edit().putBoolean("automatic",automatic)
                .putBoolean("wifi_only",wifiOnly).apply();
        schedule(context);
    }

    public static void schedule(Context context) {
        WorkManager manager=WorkManager.getInstance(context.getApplicationContext());
        if(!enabled(context)){
            manager.cancelUniqueWork(WORK_NAME);
            return;
        }
        Constraints constraints=new Constraints.Builder().setRequiredNetworkType(
                wifiOnly(context)?NetworkType.UNMETERED:NetworkType.CONNECTED).build();
        PeriodicWorkRequest request=new PeriodicWorkRequest.Builder(
                UpdateWorker.class,6,TimeUnit.HOURS)
                .setConstraints(constraints)
                .build();
        manager.enqueueUniquePeriodicWork(WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,request);
    }

    private static String currentVersion(Context context) throws Exception {
        PackageInfo current=context.getPackageManager().getPackageInfo(context.getPackageName(),0);
        return current.versionName==null?"":current.versionName;
    }

    private static File updateDir(Context context) throws IOException {
        File directory=new File(context.getFilesDir(),"updates");
        if(!directory.exists()&&!directory.mkdirs())
            throw new IOException("Невозможно создать папку обновлений");
        return directory;
    }

    private static File expectedFile(Context context,String version) throws IOException {
        if(UpdateVersion.parse(version)==null)throw new IOException("Неправильная версия");
        return new File(updateDir(context),"ehtid-v"+version+".apk");
    }

    public static String readyVersion(Context context) {
        String version=prefs(context).getString("ready_version","");
        UpdateVersion available=UpdateVersion.parse(version);
        UpdateVersion installed;
        try {installed=UpdateVersion.parse(currentVersion(context));}
        catch(Exception ignored){return "";}
        if(available==null||installed==null||available.compareTo(installed)<=0)return "";
        try {
            File path=expectedFile(context,version);
            if(!path.isFile()||path.length()<100000)return "";
        }catch(Exception ignored){return "";}
        return version;
    }

    private static String sha256(File file) throws Exception {
        MessageDigest sha=MessageDigest.getInstance("SHA-256");
        try(InputStream input=new FileInputStream(file)) {
            byte[] bytes=new byte[32768];
            int count;
            while((count=input.read(bytes))!=-1)sha.update(bytes,0,count);
        }
        return hex(sha.digest());
    }

    private static String hex(byte[] digest) {
        StringBuilder sb=new StringBuilder(digest.length*2);
        for(byte b:digest)sb.append(String.format(Locale.ROOT,"%02x",b&255));
        return sb.toString();
    }

    private static boolean matchingSigner(Context context,File apk) {
        try {
            PackageManager pm=context.getPackageManager();
            int flag=Build.VERSION.SDK_INT>=28
                    ?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
            PackageInfo archived=pm.getPackageArchiveInfo(apk.getAbsolutePath(),flag);
            PackageInfo installed=pm.getPackageInfo(context.getPackageName(),flag);
            if(archived==null||installed==null||
                    !context.getPackageName().equals(archived.packageName))return false;
            if(Build.VERSION.SDK_INT>=28 &&
                    archived.getLongVersionCode()<=installed.getLongVersionCode())return false;
            if(Build.VERSION.SDK_INT<28 && archived.versionCode<=installed.versionCode)return false;
            Signature[] a=Build.VERSION.SDK_INT>=28
                    ?archived.signingInfo==null?null:archived.signingInfo.getApkContentsSigners()
                    :archived.signatures;
            Signature[] b=Build.VERSION.SDK_INT>=28
                    ?installed.signingInfo==null?null:installed.signingInfo.getApkContentsSigners()
                    :installed.signatures;
            if(a==null||b==null||a.length!=1||b.length!=1)return false;
            return MessageDigest.isEqual(a[0].toByteArray(),b[0].toByteArray());
        }catch(Exception ignored){return false;}
    }

    private static boolean trustedHost(URL url) {
        if(!"https".equalsIgnoreCase(url.getProtocol()))return false;
        String host=url.getHost().toLowerCase(Locale.ROOT);
        return "github.com".equals(host)||"api.github.com".equals(host)||
                host.endsWith(".githubusercontent.com");
    }

    private static HttpURLConnection connect(String input) throws Exception {
        URL url=new URL(input);
        for(int redirect=0;redirect<=6;redirect++) {
            if(!trustedHost(url))throw new IOException("Недоверенный адрес обновления");
            HttpURLConnection c=(HttpURLConnection)url.openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(15000);
            c.setReadTimeout(45000);
            c.setRequestProperty("User-Agent","EHTID-Chat-Android-Updater");
            c.setRequestProperty("Accept","application/vnd.github+json, application/octet-stream");
            int status=c.getResponseCode();
            if(status==301||status==302||status==303||status==307||status==308) {
                String location=c.getHeaderField("Location");
                c.disconnect();
                if(location==null||location.isEmpty())
                    throw new IOException("GitHub не указал адрес перенаправления");
                url=new URL(url,location);
                continue;
            }
            if(status!=200) {
                c.disconnect();
                throw new IOException("GitHub HTTP "+status);
            }
            return c;
        }
        throw new IOException("Слишком много перенаправлений GitHub");
    }

    private static String readText(String url,int limit) throws Exception {
        HttpURLConnection c=connect(url);
        try(InputStream stream=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[16384];
            int n,total=0;
            while((n=stream.read(buffer))!=-1){
                total+=n;
                if(total>limit)throw new IOException("Слишком большой ответ GitHub");
                out.write(buffer,0,n);
            }
            return out.toString("UTF-8");
        }finally{c.disconnect();}
    }

    private static Release latest(String installedVersion) throws Exception {
        JSONArray releases=new JSONArray(readText(API,MAX_RELEASE_JSON));
        UpdateVersion current=UpdateVersion.parse(installedVersion);
        if(current==null)throw new IOException("Некорректная версия приложения");
        UpdateVersion best=null;
        Release selected=null;
        for(int i=0;i<releases.length();i++) {
            JSONObject release=releases.optJSONObject(i);
            if(release==null||release.optBoolean("draft",true))continue;
            String tag=release.optString("tag_name","");
            UpdateVersion candidate=UpdateVersion.fromReleaseTag(tag);
            if(candidate==null||candidate.compareTo(current)<=0||
                    best!=null&&candidate.compareTo(best)<=0)continue;
            String expectedName=UpdateVersion.expectedApkName(tag);
            JSONArray assets=release.optJSONArray("assets");
            if(assets==null)continue;
            for(int a=0;a<assets.length();a++){
                JSONObject asset=assets.optJSONObject(a);
                if(asset==null||!expectedName.equals(asset.optString("name","")))continue;
                String digest=asset.optString("digest","");
                if(!digest.matches("sha256:[0-9a-fA-F]{64}"))continue;
                String url=asset.optString("browser_download_url","");
                String expectedPrefix="https://github.com/craftkot/Ddd/releases/download/"+tag+"/";
                if(!url.equals(expectedPrefix+expectedName))continue;
                long size=asset.optLong("size",0);
                if(size<100000||size>MAX_APK)continue;
                selected=new Release(candidate.toString(),url,digest.substring(7).toLowerCase(Locale.ROOT),size);
                best=candidate;
                break;
            }
        }
        return selected;
    }

    /**
     * Called from WorkManager or a background thread only. A release
     * without SHA-256 is never installed. Local cache isn't removed
     * during app updates and does not hold chat history.
     */
    public static synchronized Result checkAndDownload(Context context,boolean forced) throws Exception {
        Context app=context.getApplicationContext();
        if(!forced&&!enabled(app))return new Result("Автообновление отключено",false);
        String already=readyVersion(app);
        if(!already.isEmpty()&&!forced)
            return new Result("Обновление "+already+" уже скачано",true);
        SharedPreferences p=prefs(app);
        long now=System.currentTimeMillis();
        if(!forced&&now-p.getLong("last_checked",0L)<CHECK_INTERVAL)
            return new Result("Проверка уже выполнялась",!already.isEmpty());

        Release release=latest(currentVersion(app));
        p.edit().putLong("last_checked",now).apply();
        if(release==null)
            return new Result("Установлена актуальная версия",!already.isEmpty());
        File ready=expectedFile(app,release.version);
        if(ready.isFile()&&ready.length()==release.size &&
                sha256(ready).equalsIgnoreCase(release.digest) &&
                matchingSigner(app,ready)) {
            rememberReady(app,release);
            return new Result("Обновление "+release.version+" готово",true);
        }

        File temp=new File(ready.getParentFile(),"ehtid-download.part");
        if(temp.exists()&&!temp.delete())
            throw new IOException("Не удалось очистить старую загрузку");
        HttpURLConnection c=connect(release.url);
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            long total=0;
            try(InputStream input=c.getInputStream();FileOutputStream output=new FileOutputStream(temp)){
                byte[] bytes=new byte[65536];
                int count;
                while((count=input.read(bytes))!=-1) {
                    total+=count;
                    if(total>MAX_APK||total>release.size)
                        throw new IOException("Размер обновления больше ожидаемого");
                    digest.update(bytes,0,count);
                    output.write(bytes,0,count);
                }
                output.getFD().sync();
            }
            if(total!=release.size||!hex(digest.digest()).equalsIgnoreCase(release.digest))
                throw new IOException("Проверка SHA-256 не пройдена");
        }catch(Exception failure) {
            temp.delete();
            throw failure;
        }finally{c.disconnect();}
        // Android also enforces the same signature on installation. Checking
        // it here prevents offering a wrong package to the installer at all.
        if(!matchingSigner(app,temp)) {
            temp.delete();
            throw new IOException("APK подписан другим ключом или имеет неверный versionCode");
        }
        if(ready.exists()&&!ready.delete()) {
            temp.delete();
            throw new IOException("Невозможно заменить предыдущий APK");
        }
        if(!temp.renameTo(ready)) {
            temp.delete();
            throw new IOException("Не удалось завершить сохранение APK");
        }
        rememberReady(app,release);
        notifyReady(app,release.version);
        return new Result("Обновление "+release.version+" скачано и проверено",true);
    }

    private static void rememberReady(Context app,Release release) {
        prefs(app).edit().putString("ready_version",release.version)
                .putString("ready_sha256",release.digest)
                .putLong("ready_size",release.size).commit();
    }

    private static void notifyReady(Context app,String version) {
        if(Build.VERSION.SDK_INT>=33 &&
                app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        !=PackageManager.PERMISSION_GRANTED)return;
        NotificationManager nm=(NotificationManager)app.getSystemService(Context.NOTIFICATION_SERVICE);
        if(nm==null)return;
        if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(
                new NotificationChannel(CHANNEL,"Обновления EHTID",NotificationManager.IMPORTANCE_DEFAULT));
        Intent open=new Intent(app,MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|
                Intent.FLAG_ACTIVITY_SINGLE_TOP);
        open.putExtra("ehtid_open_update",true);
        int flags=PendingIntent.FLAG_UPDATE_CURRENT;
        if(Build.VERSION.SDK_INT>=23)flags|=PendingIntent.FLAG_IMMUTABLE;
        PendingIntent action=PendingIntent.getActivity(app,NOTIFICATION,open,flags);
        Notification.Builder builder=Build.VERSION.SDK_INT>=26
                ?new Notification.Builder(app,CHANNEL):new Notification.Builder(app);
        builder.setSmallIcon(R.drawable.ic_chat_notification)
                .setContentTitle("EHTID Chat "+version)
                .setContentText("Обновление проверено и скачано. Нажми, чтобы установить.")
                .setContentIntent(action)
                .setAutoCancel(true);
        nm.notify(NOTIFICATION,builder.build());
    }

    public static void checkAsync(Activity activity,boolean forced) {
        if(activity.isFinishing())return;
        new Thread(()->{
            try {
                Result result=checkAndDownload(activity.getApplicationContext(),forced);
                MAIN.post(()->{
                    if(activity.isFinishing()||activity.isDestroyed())return;
                    if(forced)android.widget.Toast.makeText(activity,result.message,
                            android.widget.Toast.LENGTH_LONG).show();
                    if(result.newerReady)promptInstall(activity,true);
                });
            }catch(Exception error){
                if(forced)MAIN.post(()->{
                    if(!activity.isFinishing()&&!activity.isDestroyed())
                        new AlertDialog.Builder(activity)
                                .setTitle("Не удалось обновить")
                                .setMessage(error.getMessage())
                                .setPositiveButton("Закрыть",null).show();
                });
            }
        },"EHTID-GitHub-Updater").start();
    }

    private static String lastPrompted="";
    public static void promptInstall(Activity activity,boolean force) {
        String version=readyVersion(activity);
        if(version.isEmpty())return;
        if(!force&&version.equals(lastPrompted))return;
        lastPrompted=version;
        new AlertDialog.Builder(activity)
                .setTitle("EHTID Chat "+version+" готов")
                .setMessage("APK скачан из GitHub Releases и проверен по SHA-256 " +
                        "и сертификату подписи. Android запросит подтверждение установки. " +
                        "Переписки, список комнат, профили и файлы сохранятся.")
                .setNegativeButton("Позже",null)
                .setPositiveButton("Установить",(dialog,which)->install(activity))
                .show();
    }

    public static void install(Activity activity) {
        String version=readyVersion(activity);
        if(version.isEmpty()) {
            android.widget.Toast.makeText(activity,"Нет скачанного обновления",
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        if(Build.VERSION.SDK_INT>=26 &&
                !activity.getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(activity)
                    .setTitle("Разрешить установку обновлений")
                    .setMessage("Android требует один раз разрешить EHTID Chat устанавливать APK. " +
                            "После разрешения вернись в приложение и нажми «Установить обновление».")
                    .setNegativeButton("Позже",null)
                    .setPositiveButton("Открыть настройки",(dialog,which)->{
                        Intent intent=new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:"+activity.getPackageName()));
                        activity.startActivity(intent);
                    }).show();
            return;
        }
        try {
            File file=expectedFile(activity,version);
            if(!file.isFile())throw new IOException("APK отсутствует");
            Uri uri=FileProvider.getUriForFile(activity,
                    activity.getPackageName()+".files",file);
            Intent installer=new Intent(Intent.ACTION_VIEW);
            installer.setDataAndType(uri,"application/vnd.android.package-archive");
            installer.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(installer);
        }catch(Exception error) {
            new AlertDialog.Builder(activity).setTitle("Установка не началась")
                    .setMessage(error.getMessage())
                    .setPositiveButton("Закрыть",null).show();
        }
    }
}
