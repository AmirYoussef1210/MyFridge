package com.example.myfridge.notifications;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public final class ExpiryWorkScheduler {

    private static final String PREFS = "MyFridgePrefs";
    public static final String KEY_EXPIRY_NOTIFICATIONS_ENABLED = "expiry_notifications_enabled";
    private static final String UNIQUE_WORK_NAME = "myfridge_expiry_check";

    private ExpiryWorkScheduler() {}

    public static boolean areExpiryNotificationsEnabled(@NonNull Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_EXPIRY_NOTIFICATIONS_ENABLED, true);
    }

    public static void setExpiryNotificationsEnabled(@NonNull Context context, boolean enabled) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_EXPIRY_NOTIFICATIONS_ENABLED, enabled)
                .apply();
        if (enabled) {
            schedule(context);
        } else {
            cancel(context);
        }
    }

    /**
     * Runs roughly once per day while enabled (minimum interval for periodic work).
     */
    public static void schedule(@NonNull Context context) {
        if (!areExpiryNotificationsEnabled(context)) return;

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                ExpiryCheckWorker.class,
                1,
                TimeUnit.DAYS
        )
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniquePeriodicWork(
                        UNIQUE_WORK_NAME,
                        ExistingPeriodicWorkPolicy.UPDATE,
                        request
                );
    }

    public static void cancel(@NonNull Context context) {
        WorkManager.getInstance(context.getApplicationContext())
                .cancelUniqueWork(UNIQUE_WORK_NAME);
    }

    /** One-off run after changing settings (soon). */
    public static void runOnceSoon(@NonNull Context context) {
        if (!areExpiryNotificationsEnabled(context)) return;
        androidx.work.OneTimeWorkRequest once = new androidx.work.OneTimeWorkRequest.Builder(ExpiryCheckWorker.class)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                .build();
        WorkManager.getInstance(context.getApplicationContext()).enqueue(once);
    }
}
