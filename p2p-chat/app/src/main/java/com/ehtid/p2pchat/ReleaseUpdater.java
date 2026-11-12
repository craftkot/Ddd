package com.ehtid.p2pchat;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import androidx.core.content.FileProvider;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Downloads updates only from the canonical, public EHTID GitHub Release.
 *
 * No application can bypass Android's installer confirmation using normal
 * unprivileged app permissions. The APK is authenticated by its SHA-256,
 * expected package name and the permanent EHTID signing certificate *before*
 * being offered to Android Package Installer.
 */
public final class ReleaseUpdater {
    private static final String API="https://api.github.com/repos/craftkot/Ddd/releases?per_page=30";
    private static final String REPOSITORY="https://github.com/craftkot/Ddd/releases/download/";
    private static final String CERT_SHA256="efabef78a3c7dc1cf3be833d83e2e02d221685b90c120b1018c6b5e249ac7c77";
    private static final String APP_ID="com.ehtid.p2pchat";
    private static final long MAX_APK=120L*1024*1024;
    private static final long CHECK_INTERVAL=4L*60*60*1000;
    private static final Object LOCK=new Object();
    private static final ExecutorService BACKGROUND=Executors.newSingleThreadExecutor();
    private static final String PREFS="ehtid_updater";

    public static final class Result {
        public final String status, version, detail;
        public final File file;
        private Result(String status,String version,File file,String detail){
            this.status=status;this.version=version;this.file=file;this.detail=detail;
        }
        public boolean ready(){return "ready".equals(status);}
    }
    public interface Callback {void finished(Result result);}
    public interface Progress {void changed(long received,long total);}
    private static Result result(String status,String version,File file,String detail){
        return new Result(status,version,file,detail);
    }
    private static final class Candidate {
        final String version,url,sha256;
        final long size;
        Candidate(String version,String url,String sha,long size){
            this.version=version;this.url=url;this.sha256=sha;this.size=size;
        }
    }
    private ReleaseUpdater(){}

    public static void checkAsync(Context context,boolean force,Callback callback) {
        checkAsync(context,force,callback,null);
    }
    public static void checkAsync(Context context,boolean force,Callback callback,Progress progress) {
        Context app=context.getApplicationContext();
        BACKGROUND.execute(()->{
            Result res;
            try { res=checkAndDownload(app,force,progress); }
            catch(Exception error){
                String reason=error.getMessage();
                res=result("error","",null,
                        reason==null || reason.isEmpty()?"Ошибка проверки GitHub":reason);
            }
            final Result finished=res;
            if(callback!=null)new android.os.Handler(android.os.Looper.getMainLooper())
                    .post(()->callback.finished(finished));
        });
    }

    public static Result checkAndDownload(Context context,boolean force) throws Exception {
        return checkAndDownload(context,force,null);
    }
    public static Result checkAndDownload(Context context,boolean force,Progress progress) throws Exception {
        synchronized(LOCK) {
            Context app=context.getApplicationContext();
            SharedPreferences prefs=app.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
            Result ready=prepared(app);
            if(ready!=null)return ready;
            if(!prefs.getBoolean("automatic",true)&&!force)
                return result("disabled","",null,"Автообновление отключено");
            long now=System.currentTimeMillis();
            if(!force && now-prefs.getLong("checked",0)<CHECK_INTERVAL)
                return result("skipped","",null,"Следующая проверка позднее");

            Candidate candidate=lookupNewest(app);
            // Mark the attempt only after a successful GitHub API response.
            prefs.edit().putLong("checked",now).apply();
            if(candidate==null)return result("current","",null,"Установлена актуальная версия");

            File dir=new File(app.getFilesDir(),"updates");
            if(!dir.exists()&&!dir.mkdirs())
                throw new java.io.IOException("Не удалось создать каталог обновлений");
            File target=new File(dir,"EHTID-P2P-Chat-v"+candidate.version+".apk");
            if(target.isFile()) {
                if(verifyArchive(app,target,candidate.sha256)){
                    saveReady(prefs,candidate.version,target,candidate.sha256);
                    return result("ready",candidate.version,target,"Обновление скачано и проверено");
                }
                if(!target.delete())throw new java.io.IOException("Повреждённый APK не удалён");
            }
            File temp=new File(dir,"ehtid-update.tmp.apk");
            if(temp.exists())temp.delete();
            try {
                download(candidate,temp,progress);
                if(!verifyArchive(app,temp,candidate.sha256))
                    throw new java.io.IOException("APK не прошёл проверку подписи, версии или SHA-256");
                if(!temp.renameTo(target))
                    throw new java.io.IOException("Не удалось подготовить обновление");
            } finally {if(temp.exists())temp.delete();}

            saveReady(prefs,candidate.version,target,candidate.sha256);
            return result("ready",candidate.version,target,
                    "Обновление готово. Для установки требуется подтверждение Android.");
        }
    }

    public static Result prepared(Context context) {
        Context app=context.getApplicationContext();
        SharedPreferences prefs=app.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        String version=prefs.getString("version","");
        String sha=prefs.getString("digest","");
        if(version.isEmpty()||!sha.matches("[a-f0-9]{64}"))return null;
        ReleaseVersion installed=installedVersion(app);
        ReleaseVersion staged=ReleaseVersion.fromApp(version);
        if(installed==null||staged==null||staged.compareTo(installed)<=0){
            clearPrepared(prefs);
            return null;
        }
        File stagedFile=new File(new File(app.getFilesDir(),"updates"),
                "EHTID-P2P-Chat-v"+version+".apk");
        if(!stagedFile.isFile()||!verifyArchive(app,stagedFile,sha)){
            clearPrepared(prefs);
            return null;
        }
        return result("ready",version,stagedFile,"Обновление готово");
    }

    private static void saveReady(SharedPreferences prefs,String version,File file,String digest){
        prefs.edit().putString("version",version)
                .putString("digest",digest).commit();
    }
    private static void clearPrepared(SharedPreferences prefs){
        prefs.edit().remove("version").remove("digest").apply();
    }

    private static ReleaseVersion installedVersion(Context app) {
        try {
            PackageInfo self=app.getPackageManager().getPackageInfo(APP_ID,0);
            return ReleaseVersion.fromApp(self.versionName);
        }catch(Exception error){return null;}
    }

    private static Candidate lookupNewest(Context app) throws Exception {
        ReleaseVersion current=installedVersion(app);
        if(current==null)throw new java.io.IOException("Не удалось прочитать текущую версию");
        HttpURLConnection c=open(API);
        try {
            int status=c.getResponseCode();
            if(status!=200)throw new java.io.IOException("GitHub API: HTTP "+status);
            String json=new String(readLimited(c.getInputStream(),2*1024*1024),
                    java.nio.charset.StandardCharsets.UTF_8);
            JSONArray releases=new JSONArray(json);
            Candidate best=null;
            ReleaseVersion latest=current;
            for(int i=0;i<releases.length();i++) {
                JSONObject release=releases.optJSONObject(i);
                if(release==null||release.optBoolean("draft",false))continue;
                String tag=release.optString("tag_name","");
                ReleaseVersion version=ReleaseVersion.fromTag(tag);
                if(version==null||version.compareTo(latest)<=0)continue;
                JSONArray assets=release.optJSONArray("assets");
                if(assets==null)continue;
                String fileName="EHTID-P2P-Chat-"+tag.substring("ehtid-p2p-chat-".length())+".apk";
                String expected=REPOSITORY+tag+"/"+fileName;
                for(int j=0;j<assets.length();j++) {
                    JSONObject asset=assets.optJSONObject(j);
                    if(asset==null||!"uploaded".equals(asset.optString("state")))continue;
                    if(!fileName.equals(asset.optString("name")))continue;
                    String link=asset.optString("browser_download_url","");
                    String digest=asset.optString("digest","");
                    long bytes=asset.optLong("size",-1);
                    if(!expected.equals(link)||!digest.matches("sha256:[a-fA-F0-9]{64}")||
                            bytes<1_000_000||bytes>MAX_APK)continue;
                    best=new Candidate(version.toString(),link,
                            digest.substring(7).toLowerCase(Locale.ROOT),bytes);
                    latest=version;
                    break;
                }
            }
            return best;
        }finally{c.disconnect();}
    }

    private static HttpURLConnection open(String address) throws Exception {
        URL url=new URL(address);
        if(!url.getProtocol().equals("https") ||
                !(url.getHost().equals("github.com")||url.getHost().equals("api.github.com")||
                url.getHost().equals("objects.githubusercontent.com")||
                url.getHost().equals("release-assets.githubusercontent.com")||
                url.getHost().equals("github-releases.githubusercontent.com")))
            throw new java.io.IOException("Недоверенный источник обновления");
        HttpURLConnection conn=(HttpURLConnection)url.openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setConnectTimeout(18000);
        conn.setReadTimeout(45000);
        conn.setRequestProperty("User-Agent","EHTID-Chat-Android-Updater");
        conn.setRequestProperty("Accept","application/vnd.github+json");
        return conn;
    }

    private static HttpURLConnection redirected(String start) throws Exception {
        String current=start;
        for(int i=0;i<7;i++){
            HttpURLConnection c=open(current);
            int code=c.getResponseCode();
            if(code>=300&&code<=308 && code!=304) {
                String redirect=c.getHeaderField("Location");
                c.disconnect();
                if(redirect==null)throw new java.io.IOException("Отсутствует адрес перенаправления");
                current=new URL(new URL(current),redirect).toString();
            }else return c;
        }
        throw new java.io.IOException("Слишком много перенаправлений GitHub");
    }

    private static void download(Candidate candidate,File temp,Progress progress) throws Exception {
        HttpURLConnection c=redirected(candidate.url);
        try {
            if(c.getResponseCode()!=200)
                throw new java.io.IOException("GitHub APK: HTTP "+c.getResponseCode());
            long total=0;
            byte[] chunk=new byte[32768];
            try(InputStream input=c.getInputStream();
                FileOutputStream output=new FileOutputStream(temp)) {
                int n;
                while((n=input.read(chunk))!=-1){
                    total+=n;
                    if(total>candidate.size||total>MAX_APK)
                        throw new java.io.IOException("Размер файла больше указанного GitHub");
                    output.write(chunk,0,n);
                    if(progress!=null && (total==candidate.size ||
                            total/262144!=(total-n)/262144))
                        progress.changed(total,candidate.size);
                }
                output.getFD().sync();
            }
            if(total!=candidate.size)
                throw new java.io.IOException("Недокачанный APK: получено "+total+" байт");
        }finally{c.disconnect();}
    }

    private static byte[] readLimited(InputStream input,int limit) throws Exception {
        try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buf=new byte[8192];
            int size=0,n;
            while((n=in.read(buf))!=-1) {
                size+=n;
                if(size>limit)throw new java.io.IOException("Слишком большой ответ API");
                out.write(buf,0,n);
            }
            return out.toByteArray();
        }
    }

    private static String hex(byte[] data) {
        StringBuilder output=new StringBuilder(data.length*2);
        for(byte b:data)output.append(String.format(Locale.ROOT,"%02x",b&0xff));
        return output.toString();
    }

    private static boolean verifyArchive(Context app,File file,String sha256) {
        if(file==null||!file.isFile()||file.length()<1_000_000||file.length()>MAX_APK||
                !sha256.matches("[a-f0-9]{64}"))return false;
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(InputStream input=new java.io.FileInputStream(file)){
                byte[] buf=new byte[32768];int n;
                while((n=input.read(buf))!=-1)digest.update(buf,0,n);
            }
            if(!sha256.equals(hex(digest.digest())))return false;
            PackageManager pm=app.getPackageManager();
            int flags=Build.VERSION.SDK_INT>=28?
                    PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
            PackageInfo apk=pm.getPackageArchiveInfo(file.getAbsolutePath(),flags);
            if(apk==null||!APP_ID.equals(apk.packageName))return false;
            PackageInfo installed=pm.getPackageInfo(APP_ID,0);
            long archiveCode=Build.VERSION.SDK_INT>=28?
                    apk.getLongVersionCode():apk.versionCode;
            long currentCode=Build.VERSION.SDK_INT>=28?
                    installed.getLongVersionCode():installed.versionCode;
            if(archiveCode<=currentCode)return false;
            ReleaseVersion next=ReleaseVersion.fromApp(apk.versionName);
            ReleaseVersion current=ReleaseVersion.fromApp(installed.versionName);
            if(next==null||current==null||next.compareTo(current)<=0)return false;
            Signature[] signatures;
            if(Build.VERSION.SDK_INT>=28 && apk.signingInfo!=null)
                signatures=apk.signingInfo.getApkContentsSigners();
            else signatures=apk.signatures;
            if(signatures==null||signatures.length!=1)return false;
            String certificate=hex(MessageDigest.getInstance("SHA-256").digest(
                    signatures[0].toByteArray()));
            return CERT_SHA256.equals(certificate);
        }catch(Exception ignored){return false;}
    }

    public static void requestInstallation(android.app.Activity activity,Result ready) {
        if(ready==null||!ready.ready())return;
        // The gateway rechecks the staged APK on a worker thread and continues
        // automatically after Android's unknown-app-source settings screen.
        activity.startActivity(new Intent(activity,ReleaseInstallActivity.class));
    }
}
