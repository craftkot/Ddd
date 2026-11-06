package com.ehtid.p2pchat;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.File;

/**
 * User-facing Android package installer handoff.
 *
 * An ordinary app cannot perform silent APK installation: on Android 8+
 * users must grant "Install unknown apps" for EHTID and then approve the
 * system installer dialog. The signed APK is checked again before opening.
 */
public final class ReleaseInstallActivity extends Activity {
    private boolean requestingPermission=false;
    private boolean installerOpened=false;
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        new AlertDialog.Builder(this)
                .setTitle("Обновление EHTID Chat")
                .setMessage("APK уже скачан и проверен. Установка пройдёт через "+
                        "стандартный установщик Android. "+
                        "Переписки и комнаты сохраняются при обновлении без удаления приложения.")
                .setNegativeButton("Позже",(d,w)->finish())
                .setPositiveButton("Установить",(d,w)->openInstaller())
                .setOnCancelListener(d->finish())
                .show();
    }
    private void openInstaller(){
        if(Build.VERSION.SDK_INT>=26 && !getPackageManager().canRequestPackageInstalls()){
            requestingPermission=true;
            Toast.makeText(this,"Разреши установку обновлений для EHTID, затем вернись",Toast.LENGTH_LONG).show();
            Intent settings=new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:"+getPackageName()));
            startActivity(settings);
            return;
        }
        File apk=ReleaseUpdate.readyFile(this);
        if(apk==null){
            Toast.makeText(this,"Обновление устарело или проверка подписи не прошла",Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        try{
            Uri uri=FileProvider.getUriForFile(this,
                    getPackageName()+".files",apk);
            Intent install=new Intent(Intent.ACTION_VIEW);
            install.setDataAndType(uri,"application/vnd.android.package-archive");
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            installerOpened=true;
            startActivity(install);
        }catch(Exception error){
            installerOpened=false;
            Toast.makeText(this,"Не удалось открыть установщик: "+error.getMessage(),Toast.LENGTH_LONG).show();
        }
        finish();
    }
    @Override protected void onResume(){
        super.onResume();
        if(requestingPermission && !installerOpened &&
                (Build.VERSION.SDK_INT<26||getPackageManager().canRequestPackageInstalls())){
            requestingPermission=false;
            openInstaller();
        }
    }
}
