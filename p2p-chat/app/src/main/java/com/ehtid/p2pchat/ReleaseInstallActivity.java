package com.ehtid.p2pchat;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.File;

/**
 * A single installation gateway for foreground and background update alerts.
 * Verifies SHA-256, APK version and permanent signing certificate OFF the UI
 * thread. Resumes installation after the user grants unknown-app permission.
 */
public final class ReleaseInstallActivity extends Activity {
    private boolean awaitingPermission;
    private boolean visitedSettings;
    private boolean checking;
    private boolean closing;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView progress=new TextView(this);
        progress.setText("Проверяем подпись обновления EHTID…");
        progress.setTextSize(16);
        progress.setPadding(32,48,32,32);
        setContentView(progress);
        verifyAndInstall();
    }

    @Override protected void onPause() {
        super.onPause();
        if(awaitingPermission)visitedSettings=true;
    }

    @Override protected void onResume() {
        super.onResume();
        if(awaitingPermission && visitedSettings && !checking && !closing) {
            awaitingPermission=false;
            visitedSettings=false;
            if(Build.VERSION.SDK_INT>=26 && !getPackageManager().canRequestPackageInstalls()){
                Toast.makeText(this,
                        "Разрешение не выдано. Установку можно повторить из профиля.",
                        Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            verifyAndInstall();
        }
    }

    private void verifyAndInstall() {
        if(checking || closing)return;
        checking=true;
        new Thread(()->{
            ReleaseUpdater.Result update=ReleaseUpdater.prepared(this);
            runOnUiThread(()->{
                checking=false;
                if(isFinishing() || isDestroyed() || closing)return;
                if(update==null || !update.ready() || update.file==null) {
                    Toast.makeText(this,
                            "Обновление не найдено или повреждено. Повтори проверку в профиле.",
                            Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                if(Build.VERSION.SDK_INT>=26 && !getPackageManager().canRequestPackageInstalls()){
                    awaitingPermission=true;
                    try {
                        Intent settings=new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:"+getPackageName()));
                        startActivity(settings);
                    } catch(Exception e){
                        awaitingPermission=false;
                        Toast.makeText(this,"Не удалось открыть разрешения: "+e.getMessage(),
                                Toast.LENGTH_LONG).show();
                        finish();
                    }
                    return;
                }
                openInstaller(update.file);
            });
        },"EHTID-Verify-Installer").start();
    }

    private void openInstaller(File file) {
        try {
            Uri uri=FileProvider.getUriForFile(this,getPackageName()+".files",file);
            Intent intent=new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri,"application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            closing=true;
            startActivity(intent);
            finish();
        }catch(Exception e){
            closing=false;
            Toast.makeText(this,"Не удалось запустить установщик: "+e.getMessage(),
                    Toast.LENGTH_LONG).show();
            finish();
        }
    }
}
