package com.javierrb35.flashlight;

import android.app.job.JobParameters;
import android.app.job.JobService;

/** Periodic background version check; no APK download or installation. */
public final class UpdateJobService extends JobService {
    private volatile Thread worker;

    @Override public boolean onStartJob(JobParameters params) {
        worker=new Thread(() -> {
            try {
                UpdateChecker.Result result=UpdateChecker.check(getApplicationContext(),false);
                if(!Thread.currentThread().isInterrupted())
                    UpdateChecker.maybeNotify(getApplicationContext(),result.update);
            } finally {
                jobFinished(params,false);
            }
        },"flashlight-update-check");
        worker.start();
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) {
        Thread active=worker;
        if(active!=null)active.interrupt();
        return true;
    }
}
