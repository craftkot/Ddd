package com.ehtid.p2pchat;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * GitHub Releases updater, including public prereleases.
 *
 * Automatically CHECKS and DOWNLOADS. Package installation always requires
 * Android's own package installer and user approval (no silent installation).
 * No user data, room codes or message history is sent to GitHub.
 */
public final class ReleaseUpdate {
    public interface Callback { void done(String message); }

    private static final String RELEASES=
            "https://api.github.com/repos/craftkot/Ddd/releases?per_page=25";
    private static final String REPO="https://github.com/craftkot/Ddd/releases/download/";
    private static final String PREFS="ehtid_updater";
    private static final String CHANNEL="ehtid_apk_updates";
    private static final long CHECK_INTERVAL=12L*60*60*1000;
    private static final long MAX_APK_BYTES=130L*1024*1024;
    private static final int UPDATE_JOB=41260;
    private static final int NOTIFICATION_ID=41261;
    private static final AtomicBoolean RUNNING=new AtomicBoolean(false);
    // Exact permanent EHTID signing identity from the GitHub release workflow.
    private static final String CERT_SHA256=
            "efabef78a3c7dc1cf3be833d83e2e02d221685b90c120b1018c6b5e249ac7c77";

    private ReleaseUpdate(){}

    private static SharedPreferences preferences(Context ctx){
        return ctx.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
    public static boolean automatic(Context ctx){
        return preferences(ctx).getBoolean("automatic",true);
    }
    public static void setAutomatic(Context ctx,boolean enabled){
        preferences(ctx).edit().putBoolean("automatic",enabled).apply();
        schedule(ctx);
    }
    /** Native JobScheduler works without Google Play Services or a paid server. */
    public static void schedule(Context context){
        Context ctx=context.getApplicationContext();
        JobScheduler jobs=(JobScheduler)ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if(jobs==null)return;
        if(!automatic(ctx)){jobs.cancel(UPDATE_JOB);return;}
        JobInfo info=new JobInfo.Builder(UPDATE_JOB,
                new ComponentName(ctx,ReleaseUpdateJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setPeriodic(CHECK_INTERVAL)
                .build();
        jobs.schedule(info);
    }
    public static void checkOnLaunch(Context context){
        if(!automatic(context))return;
        Context ctx=context.getApplicationContext();
        new Thread(()->perform(ctx,false,null),"EHTID-Check-GitHub").start();
    }
    public static void manualCheck(Context context,Callback callback){
        Context ctx=context.getApplicationContext();
        new Thread(()->perform(ctx,true,callback),"EHTID-Manual-Update").start();
    }
    public static void checkInJob(Context context){
        perform(context.getApplicationContext(),false,null);
    }
    private static void notifyCallback(Callback callback,String message){
        if(callback!=null)new Handler(Looper.getMainLooper()).post(()->callback.done(message));
    }
    private static void perform(Context ctx,boolean force,Callback callback){
        if(!RUNNING.compareAndSet(false,true)){
            notifyCallback(callback,"Проверка обновлений уже выполняется");
            return;
        }
        try{
            SharedPreferences prefs=preferences(ctx);
            long last=prefs.getLong("checked_at",0L);
            if(!force&&last>0&&System.currentTimeMillis()-last<CHECK_INTERVAL){
                if(hasDownloadedUpdate(ctx))notifyUpdate(ctx);
                notifyCallback(callback,"Проверка выполнена ранее");
                return;
            }
            String installed=currentVersion(ctx);
            JSONArray releases=listReleases();
            JSONObject chosen=null,asset=null;
            String chosenVersion=null;
            for(int i=0;i<releases.length();i++){
                JSONObject release=releases.optJSONObject(i);
                if(release==null||release.optBoolean("draft",false))continue;
                String tag=release.optString("tag_name");
                if(!ReleasePolicy.newer(tag,installed))continue;
                String name=ReleasePolicy.assetName(tag);
                JSONArray assets=release.optJSONArray("assets");
                if(name==null||assets==null)continue;
                for(int k=0;k<assets.length();k++){
                    JSONObject entry=assets.optJSONObject(k);
                    if(entry==null||!name.equals(entry.optString("name"))||
                            !"uploaded".equals(entry.optString("state")) ||
                            !ReleasePolicy.safeDigest(entry.optString("digest")))continue;
                    long size=entry.optLong("size",0);
                    if(size<1024||size>MAX_APK_BYTES)continue;
                    String expected=REPO+tag+"/"+name;
                    if(!expected.equals(entry.optString("browser_download_url")))continue;
                    String version=ReleasePolicy.versionFromTag(tag);
                    if(chosen==null || ReleasePolicy.newer(tag,chosenVersion)){
                        chosen=release;
                        asset=entry;
                        chosenVersion=version;
                    }
                }
            }
            prefs.edit().putLong("checked_at",System.currentTimeMillis()).apply();
            if(chosen==null||asset==null){
                notifyCallback(callback,"Установлена актуальная версия: "+installed);
                return;
            }
            String tag=chosen.optString("tag_name");
            String digest=asset.optString("digest").substring(7).toLowerCase(Locale.ROOT);
            long size=asset.optLong("size");
            File output=targetFile(ctx,tag);
            if(!verifyArchive(ctx,output,digest,size,tag)){
                File pending=new File(output.getAbsolutePath()+".part");
                if(pending.exists())pending.delete();
                download(asset.optString("browser_download_url"),pending,size);
                if(!verifyArchive(ctx,pending,digest,size,tag)){
                    pending.delete();
                    throw new SecurityException("Проверка скачанного APK не прошла");
                }
                if(output.exists()&&!output.delete())throw new java.io.IOException("Нельзя заменить старый APK");
                if(!pending.renameTo(output)){
                    pending.delete();
                    throw new java.io.IOException("Нельзя сохранить APK");
                }
            }
            prefs.edit().putString("ready_tag",tag).putString("ready_sha",digest)
                    .putLong("ready_size",size).apply();
            notifyUpdate(ctx);
            notifyCallback(callback,"Скачано обновление "+chosenVersion+
                    ". Открой установку и подтверди её в Android.");
        }catch(Exception error){
            notifyCallback(callback,"Обновление: "+error.getMessage());
        }finally{RUNNING.set(false);}
    }

    private static String currentVersion(Context ctx) throws Exception{
        PackageInfo p=ctx.getPackageManager().getPackageInfo(ctx.getPackageName(),0);
        return p.versionName==null?"0.0.0":p.versionName;
    }
    private static JSONArray listReleases() throws Exception{
        HttpURLConnection c=open(new URL(RELEASES));
        try{
            if(c.getResponseCode()!=200)
                throw new java.io.IOException("GitHub HTTP "+c.getResponseCode());
            return new JSONArray(readLimited(c.getInputStream(),3*1024*1024));
        }finally{c.disconnect();}
    }
    private static HttpURLConnection open(URL url) throws Exception{
        if(!"https".equalsIgnoreCase(url.getProtocol()))
            throw new SecurityException("Только HTTPS");
        String host=url.getHost().toLowerCase(Locale.ROOT);
        if(!(host.equals("api.github.com")||host.equals("github.com")||
                host.equals("release-assets.githubusercontent.com")||
                host.equals("objects.githubusercontent.com")))
            throw new SecurityException("Неизвестный сервер обновлений");
        HttpURLConnection c=(HttpURLConnection)url.openConnection();
        c.setInstanceFollowRedirects(false);
        c.setConnectTimeout(16000);
        c.setReadTimeout(35000);
        c.setRequestProperty("User-Agent","EHTID-Chat-Android-Updater");
        c.setRequestProperty("Accept","application/vnd.github+json");
        return c;
    }
    private static String readLimited(InputStream source,int max) throws Exception{
        try(InputStream input=source;ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buf=new byte[8192];int n,total=0;
            while((n=input.read(buf))!=-1){
                total+=n;
                if(total>max)throw new java.io.IOException("Ответ GitHub слишком большой");
                out.write(buf,0,n);
            }
            return out.toString("UTF-8");
        }
    }
    private static void download(String origin,File temp,long expectedSize) throws Exception{
        URL url=new URL(origin);
        HttpURLConnection c=null;
        for(int redirect=0;redirect<6;redirect++){
            if(c!=null)c.disconnect();
            c=open(url);
            int status=c.getResponseCode();
            if(status==301||status==302||status==303||status==307||status==308){
                String location=c.getHeaderField("Location");
                if(location==null)throw new java.io.IOException("Нет ссылки на файл");
                url=new URL(url,location);
                continue;
            }
            if(status!=200)throw new java.io.IOException("Загрузка GitHub HTTP "+status);
            File parent=temp.getParentFile();
            if(!parent.exists()&&!parent.mkdirs())
                throw new java.io.IOException("Не удалось создать папку обновлений");
            long length=0;
            try(InputStream input=c.getInputStream();FileOutputStream out=new FileOutputStream(temp)){
                byte[] buf=new byte[65536];int n;
                while((n=input.read(buf))!=-1){
                    length+=n;
                    if(length>MAX_APK_BYTES||length>expectedSize){
                        throw new java.io.IOException("Размер обновления не совпадает");
                    }
                    out.write(buf,0,n);
                }
                out.flush();
                out.getFD().sync();
            }finally{c.disconnect();}
            if(length!=expectedSize)throw new java.io.IOException("APK скачан не полностью");
            return;
        }
        if(c!=null)c.disconnect();
        throw new java.io.IOException("Слишком много перенаправлений загрузки");
    }
    private static File targetFile(Context ctx,String tag){
        if(ReleasePolicy.versionFromTag(tag)==null)
            throw new IllegalArgumentException("Недопустимая версия");
        return new File(new File(ctx.getFilesDir(),"updates"),tag+".apk");
    }

    private static long code(PackageInfo p){
        return Build.VERSION.SDK_INT>=28?p.getLongVersionCode():p.versionCode;
    }
    private static Signature[] signers(PackageInfo p){
        if(Build.VERSION.SDK_INT>=28){
            if(p.signingInfo==null)return null;
            return p.signingInfo.getApkContentsSigners();
        }
        return p.signatures;
    }
    private static String hex(byte[] bytes){
        char[] chars="0123456789abcdef".toCharArray();
        char[] out=new char[bytes.length*2];
        for(int i=0;i<bytes.length;i++){
            int v=bytes[i]&255;
            out[i*2]=chars[v>>>4];out[i*2+1]=chars[v&15];
        }
        return new String(out);
    }
    private static String sha256(File file) throws Exception{
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream input=new FileInputStream(file)){
            byte[] block=new byte[65536];int n;
            while((n=input.read(block))!=-1)digest.update(block,0,n);
        }
        return hex(digest.digest());
    }
    private static boolean correctCertificate(PackageInfo p) throws Exception{
        Signature[] sigs=signers(p);
        if(sigs==null||sigs.length!=1)return false;
        String sha=hex(MessageDigest.getInstance("SHA-256").digest(sigs[0].toByteArray()));
        return CERT_SHA256.equals(sha);
    }
    private static boolean verifyArchive(Context ctx,File file,String digest,long size,String tag){
        try{
            if(!file.isFile()||file.length()!=size||!sha256(file).equals(digest))return false;
            int flags=Build.VERSION.SDK_INT>=28?
                    PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
            PackageManager pm=ctx.getPackageManager();
            PackageInfo installed=pm.getPackageInfo(ctx.getPackageName(),flags);
            PackageInfo archive=pm.getPackageArchiveInfo(file.getAbsolutePath(),flags);
            if(archive==null || !ctx.getPackageName().equals(archive.packageName) ||
                    !ReleasePolicy.newer(tag,installed.versionName) ||
                    !ReleasePolicy.versionFromTag(tag).equals(
                            ReleasePolicy.versionFromTag("ehtid-p2p-chat-v"+archive.versionName)) ||
                    code(archive)<=code(installed))return false;
            return correctCertificate(installed)&&correctCertificate(archive);
        }catch(Exception ignored){return false;}
    }
    /** Checks saved update integrity every time before passing to the installer. */
    public static File readyFile(Context ctx){
        try{
            SharedPreferences p=preferences(ctx);
            String tag=p.getString("ready_tag","");
            String sha=p.getString("ready_sha","");
            long size=p.getLong("ready_size",0);
            if(ReleasePolicy.versionFromTag(tag)==null||!sha.matches("[a-f0-9]{64}")||
                    size<=0||size>MAX_APK_BYTES)return null;
            File file=targetFile(ctx,tag);
            return verifyArchive(ctx,file,sha,size,tag)?file:null;
        }catch(Exception ignored){return null;}
    }
    public static boolean hasDownloadedUpdate(Context ctx){return readyFile(ctx)!=null;}
    /** Cheap UI hint; full SHA-256 and signing verification happens before install. */
    public static boolean markedReady(Context ctx){
        try{
            String tag=preferences(ctx).getString("ready_tag","");
            if(!ReleasePolicy.newer(tag,currentVersion(ctx)))return false;
            long length=preferences(ctx).getLong("ready_size",0);
            File file=targetFile(ctx,tag);
            return length>0&&length<=MAX_APK_BYTES&&file.isFile()&&file.length()==length;
        }catch(Exception error){return false;}
    }
    public static String readyVersion(Context ctx){
        String tag=preferences(ctx).getString("ready_tag","");
        return ReleasePolicy.versionFromTag(tag);
    }
    private static void notifyUpdate(Context ctx){
        if(!hasDownloadedUpdate(ctx))return;
        if(Build.VERSION.SDK_INT>=33 &&
                ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=
                        PackageManager.PERMISSION_GRANTED)return;
        NotificationManager nm=(NotificationManager)ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if(nm==null)return;
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel ch=new NotificationChannel(CHANNEL,
                    "Обновления EHTID",NotificationManager.IMPORTANCE_DEFAULT);
            nm.createNotificationChannel(ch);
        }
        Intent open=new Intent(ctx,ReleaseInstallActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags=PendingIntent.FLAG_UPDATE_CURRENT;
        if(Build.VERSION.SDK_INT>=23)flags|=PendingIntent.FLAG_IMMUTABLE;
        PendingIntent action=PendingIntent.getActivity(ctx,UPDATE_JOB,open,flags);
        Notification.Builder n=Build.VERSION.SDK_INT>=26?
                new Notification.Builder(ctx,CHANNEL):new Notification.Builder(ctx);
        n.setSmallIcon(R.drawable.ic_chat_notification)
                .setContentTitle("EHTID Chat "+readyVersion(ctx)+" загружен")
                .setContentText("Нажмите, чтобы подтвердить установку обновления Android")
                .setContentIntent(action).setAutoCancel(false).setOngoing(false);
        nm.notify(NOTIFICATION_ID,n.build());
    }
}
