package com.ehtid.p2pchat;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** Background GitHub release checks run roughly every 6 hours when online. */
public final class UpdateWorker extends Worker {
    public UpdateWorker(@NonNull Context context,@NonNull WorkerParameters params) {
        super(context,params);
    }

    @NonNull @Override public Result doWork() {
        if(!AppUpdater.enabled(getApplicationContext()))return Result.success();
        try {
            AppUpdater.checkAndDownload(getApplicationContext(),false);
            return Result.success();
        }catch(Exception error){
            // Unavailable network, GitHub rate limiting or a signature mismatch
            // never causes an untrusted APK to be installed.
            return Result.retry();
        }
    }
}
