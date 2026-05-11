package com.example.myfridge.notifications;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * {@link BroadcastReceiver} that drives the daily fridge-expiry check.
 *
 * <p>Handles two intent actions:</p>
 * <ul>
 *   <li>{@link Intent#ACTION_BOOT_COMPLETED} — AlarmManager alarms are cleared on
 *       device reboot, so this action reschedules the daily alarm via
 *       {@link ExpiryWorkScheduler#schedule(Context)} as soon as the device boots.</li>
 *   <li>{@link ExpiryWorkScheduler#ACTION_EXPIRY_CHECK} — fired once per day by
 *       AlarmManager. The actual check runs on a dedicated background thread
 *       (obtained via {@link #goAsync()}) so the main thread is never blocked.</li>
 * </ul>
 *
 * <p>Register this receiver in {@code AndroidManifest.xml} with both actions and
 * {@code android:exported="true"} so the system can deliver
 * {@code BOOT_COMPLETED} broadcasts.</p>
 *
 * @see ExpiryWorkScheduler
 * @see ExpiryCheckWorker
 */
public class ExpiryAlarmReceiver extends BroadcastReceiver {

    /**
     * Runnable that performs the expiry check on a background thread and signals
     * the system when complete via {@link PendingResult#finish()}.
     */
    private static class ExpiryCheckRunnable implements Runnable {

        private final Context app;
        private final PendingResult result;

        /**
         * Creates a runnable that will perform the expiry check and then signal
         * the system that the async broadcast is complete.
         *
         * @param app    the application context passed to {@link ExpiryCheckWorker#doCheck}
         * @param result the pending result token returned by {@link #goAsync()}
         */
        ExpiryCheckRunnable(Context app, PendingResult result) {
            this.app = app;
            this.result = result;
        }

        /**
         * Executes {@link ExpiryCheckWorker#doCheck} and calls
         * {@link PendingResult#finish()} in a {@code finally} block so the system
         * always knows when the async broadcast handler has completed.
         */
        @Override
        public void run() {
            try {
                ExpiryCheckWorker.doCheck(app);
            } finally {
                result.finish();
            }
        }
    }

    /**
     * Called by the system when a matching broadcast is received.
     *
     * <p>On {@link Intent#ACTION_BOOT_COMPLETED} the daily alarm is rescheduled and
     * the method returns immediately. On {@link ExpiryWorkScheduler#ACTION_EXPIRY_CHECK}
     * a background thread is started via {@link #goAsync()} to run the expiry check
     * without blocking the main thread; {@link PendingResult#finish()} is called in a
     * {@code finally} block to signal completion to the system.</p>
     *
     * @param context the context in which the receiver is running
     * @param intent  the intent that triggered this receiver
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            ExpiryWorkScheduler.schedule(context);
            return;
        }

        // Alarm fired — run check on a background thread so we don't block the main thread
        PendingResult result = goAsync();
        new Thread(new ExpiryCheckRunnable(context.getApplicationContext(), result)).start();
    }
}
