package nz.ac.aut.comp713.repairdesk;

import android.app.Application;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Creates the notification channel and runs the update check.
 *
 * While the app process is alive the check runs every 15 seconds, so a technician's change
 * produces a notification within seconds (also when the app is in the background).
 * When Android has stopped the process, a JobScheduler job checks every 15 minutes
 * (the shortest period Android allows for periodic jobs).
 */
public class RepairDeskApp extends Application {

    private static final String TAG = "RepairDesk";
    private static final long POLL_INTERVAL_MS = 15_000;
    private static final int JOB_ID = 1001;

    private static final ExecutorService IO = Executors.newFixedThreadPool(2);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable pollTask = new Runnable() {
        @Override
        public void run() {
            IO.execute(() -> checkNow(RepairDeskApp.this));
            handler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    /** Background thread pool for network calls from activities. */
    public static ExecutorService io() {
        return IO;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        TicketNotifier.createChannel(this);
        handler.postDelayed(pollTask, POLL_INTERVAL_MS);
        if (Prefs.isSignedIn(this)) {
            scheduleBackgroundJob(this);
        }
    }

    /** Fetch the customer's tickets and notify about changes. Blocking; call off the main thread. */
    static boolean checkNow(Context context) {
        String email = Prefs.email(context);
        if (email == null) return true;
        try {
            List<Ticket> tickets = Prefs.api(context).listTickets(email);
            TicketNotifier.checkForChanges(context, tickets);
            return true;
        } catch (ApiException e) {
            Log.w(TAG, "Update check failed: " + e.describe());
            return false;
        }
    }

    public static void scheduleBackgroundJob(Context context) {
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(context, UpdateCheckJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(TimeUnit.MINUTES.toMillis(15))
                .setPersisted(false)
                .build();
        context.getSystemService(JobScheduler.class).schedule(job);
    }

    public static void cancelBackgroundJob(Context context) {
        context.getSystemService(JobScheduler.class).cancel(JOB_ID);
    }
}
