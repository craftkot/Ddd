package com.ehtid.p2pchat;

import android.app.job.JobParameters;
import android.app.job.JobService;

/**
 * Opportunistic background download. Android can defer this job for battery,
 * network or Doze; it is not an exact 12-hour schedule.
 */
public final class ReleaseUpdateJob extends JobService {
    @Override public boolean onStartJob(JobParameters params){
        if(!ReleaseUpdate.automatic(this))return false;
        new Thread(()->{
            try{ReleaseUpdate.checkInJob(getApplicationContext());}
            finally{jobFinished(params,false);}
        },"EHTID-Auto-Update").start();
        return true;
    }
    @Override public boolean onStopJob(JobParameters params){
        // Permit Android to retry on the next scheduler window.
        return true;
    }
}
