package com.example.myfridge.notifications;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;

import java.util.Calendar;

/**
 * Utility class that manages scheduling of the daily expiry check via
 * {@link AlarmManager}.
 *
 * <p>The alarm fires once a day at approximately 9:00 AM using
 * {@link AlarmManager#setInexactRepeating} with {@link AlarmManager#RTC_WAKEUP},
 * which wakes the device if it is asleep. The inexact repeat type lets Android
 * batch the alarm with others to save battery, while still guaranteeing daily
 * delivery.</p>
 *
 * <p>The enabled/disabled state is persisted in {@code SharedPreferences} under
 * the key {@link #KEY_EXPIRY_NOTIFICATIONS_ENABLED}. The alarm is automatically
 * rescheduled after device reboot by {@link ExpiryAlarmReceiver}.</p>
 *
 * <p>This class is not instantiable.</p>
 *
 * @see ExpiryAlarmReceiver
 * @see ExpiryCheckWorker
 */
public final class ExpiryWorkScheduler {

    private static final String PREFS = "MyFridgePrefs";

    /** SharedPreferences key that stores whether expiry notifications are enabled. */
    public static final String KEY_EXPIRY_NOTIFICATIONS_ENABLED = "expiry_notifications_enabled";

    /**
     * Broadcast action sent by AlarmManager to trigger the daily expiry check.
     * Received by {@link ExpiryAlarmReceiver}.
     */
    static final String ACTION_EXPIRY_CHECK = "com.example.myfridge.ACTION_EXPIRY_CHECK";

    /** Request code used for the AlarmManager {@link PendingIntent}. */
    private static final int ALARM_REQUEST_CODE = 7200;

    /** Prevents instantiation. */
    private ExpiryWorkScheduler() {}

    /**
     * Returns whether expiry notifications are currently enabled.
     * Defaults to {@code true} if the preference has never been set.
     *
     * @param context any context
     * @return {@code true} if expiry notifications are enabled
     */
    public static boolean areExpiryNotificationsEnabled(@NonNull Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_EXPIRY_NOTIFICATIONS_ENABLED, true);
    }

    /**
     * Persists the enabled state and either schedules or cancels the daily alarm
     * accordingly.
     *
     * @param context any context
     * @param enabled {@code true} to enable and schedule, {@code false} to disable and cancel
     */
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
     * Registers a daily inexact repeating alarm that fires at approximately 9:00 AM
     * each day. Uses {@link AlarmManager#RTC_WAKEUP} so the device wakes up to
     * deliver the alarm even when the screen is off.
     *
     * <p>If the 9:00 AM trigger time has already passed for today, the first firing
     * is moved to 9:00 AM tomorrow. Calling this method while an alarm is already
     * registered replaces the existing alarm because the {@link PendingIntent} is
     * created with {@link PendingIntent#FLAG_UPDATE_CURRENT}.</p>
     *
     * <p>No-op if expiry notifications are disabled.</p>
     *
     * @param context any context
     */
    public static void schedule(@NonNull Context context) {
        if (!areExpiryNotificationsEnabled(context)) return;

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 9);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }

        am.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                cal.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY,
                getAlarmPendingIntent(context)
        );
    }

    /**
     * Cancels the daily expiry-check alarm. Called on logout and when the user
     * disables notifications in settings.
     *
     * @param context any context
     */
    public static void cancel(@NonNull Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            am.cancel(getAlarmPendingIntent(context));
        }
    }

    /**
     * Runs the expiry check immediately on a new background thread. Intended to be
     * called right after the user saves settings so they see the effect of their
     * changes without waiting for the next daily alarm.
     *
     * <p>No-op if expiry notifications are disabled.</p>
     *
     * @param context any context
     */
    public static void runOnceSoon(@NonNull Context context) {
        if (!areExpiryNotificationsEnabled(context)) return;
        new Thread(new ExpiryCheckRunnable(context.getApplicationContext())).start();
    }

    /**
     * Runnable that performs the expiry check on a background thread.
     */
    private static class ExpiryCheckRunnable implements Runnable {

        private final Context app;

        /**
         * Creates a runnable that will perform the expiry check on a background thread.
         *
         * @param app the application context passed to {@link ExpiryCheckWorker#doCheck}
         */
        ExpiryCheckRunnable(Context app) {
            this.app = app;
        }

        /**
         * Delegates to {@link ExpiryCheckWorker#doCheck} to perform the full
         * expiry check synchronously on the calling background thread.
         */
        @Override
        public void run() {
            ExpiryCheckWorker.doCheck(app);
        }
    }

    /**
     * Builds the {@link PendingIntent} used to register and cancel the alarm.
     * The intent targets {@link ExpiryAlarmReceiver} with
     * {@link #ACTION_EXPIRY_CHECK} so the receiver can distinguish an alarm
     * trigger from a {@code BOOT_COMPLETED} broadcast.
     *
     * @param context any context
     * @return a {@link PendingIntent} for the expiry-check broadcast
     */
    private static PendingIntent getAlarmPendingIntent(@NonNull Context context) {
        Intent intent = new Intent(context.getApplicationContext(), ExpiryAlarmReceiver.class);
        intent.setAction(ACTION_EXPIRY_CHECK);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(
                context.getApplicationContext(),
                ALARM_REQUEST_CODE,
                intent,
                flags
        );
    }
}
