package com.guidancetutors.priceswidget;

import android.app.job.JobParameters;
import android.app.job.JobService;

public class PriceJob extends JobService {
    @Override
    public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            Fetcher.run(this);
            PriceWidget.render(this);
            jobFinished(params, false);
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) { return true; }
}
