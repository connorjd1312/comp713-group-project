package nz.ac.aut.comp713.repairdesk;

import android.app.job.JobParameters;
import android.app.job.JobService;

/** Periodic background check scheduled by RepairDeskApp; runs even if the app's process was stopped. */
public class UpdateCheckJobService extends JobService {

    @Override
    public boolean onStartJob(JobParameters params) {
        RepairDeskApp.io().execute(() -> {
            boolean ok = RepairDeskApp.checkNow(getApplicationContext());
            jobFinished(params, !ok);   // reschedule with back-off if the server was unreachable
        });
        return true;   // work continues on the background thread
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;   // retry later if Android stops the job early
    }
}
