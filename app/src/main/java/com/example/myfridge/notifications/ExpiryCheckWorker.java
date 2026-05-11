package com.example.myfridge.notifications;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.content.ContextCompat;

import com.example.myfridge.rtdb.RtdbRepository;
import com.example.myfridge.storage.Product;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseError;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Package-private utility class that performs the fridge expiry check.
 *
 * <p>Called by {@link ExpiryAlarmReceiver} on a dedicated background thread and by
 * {@link ExpiryWorkScheduler#runOnceSoon} on a newly spawned thread. Must
 * <strong>never</strong> be called on the main thread because {@link #doCheck}
 * blocks for up to 45 seconds waiting for Firebase results.</p>
 *
 * <p>Execution flow of {@link #doCheck}:</p>
 * <ol>
 *   <li>Verifies that expiry notifications are enabled.</li>
 *   <li>On Android 13+ (API 33), confirms the {@code POST_NOTIFICATIONS} runtime
 *       permission has been granted.</li>
 *   <li>Confirms a Firebase user is signed in.</li>
 *   <li>Fetches the user's expiry-window preference and full inventory from the
 *       Realtime Database; blocks on a {@link CountDownLatch} for up to 45 seconds.</li>
 *   <li>Collects products whose expiry date falls within the configured window and,
 *       if any are found, fires a summary notification via
 *       {@link ExpiryNotificationHelper#showExpiringSoonNotification}.</li>
 * </ol>
 *
 * @see ExpiryAlarmReceiver
 * @see ExpiryWorkScheduler
 * @see ExpiryNotificationHelper
 */
class ExpiryCheckWorker {

    /** Prevents instantiation. */
    private ExpiryCheckWorker() {}

    /**
     * Performs the expiry check synchronously on the calling thread.
     *
     * <p>Returns immediately (without posting a notification) when:</p>
     * <ul>
     *   <li>expiry notifications are disabled,</li>
     *   <li>the {@code POST_NOTIFICATIONS} permission is missing (Android 13+),</li>
     *   <li>no Firebase user is currently signed in,</li>
     *   <li>the Firebase fetch times out or is interrupted, or</li>
     *   <li>the fetch returns an error.</li>
     * </ul>
     *
     * @param app the application context; must not be an Activity context
     */
    static void doCheck(Context app) {
        if (!ExpiryWorkScheduler.areExpiryNotificationsEnabled(app)) return;

        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        RtdbRepository repo = new RtdbRepository();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Integer> daysRef = new AtomicReference<>(2);
        AtomicReference<List<Product>> productsRef = new AtomicReference<>(new ArrayList<>());
        AtomicReference<DatabaseError> errRef = new AtomicReference<>();

        repo.fetchUserPreferences(user, new RtdbRepository.UserPrefsCallback() {
            @Override
            public void onSuccess(String units, int daysBeforeExpireChoice) {
                daysRef.set(daysBeforeExpireChoice);
                repo.fetchAllInventory(user, new RtdbRepository.ProductsCallback() {
                    @Override
                    public void onSuccess(List<Product> products) {
                        productsRef.set(products);
                        latch.countDown();
                    }

                    @Override
                    public void onFailure(DatabaseError error) {
                        errRef.set(error);
                        latch.countDown();
                    }
                });
            }

            @Override
            public void onFailure(DatabaseError error) {
                errRef.set(error);
                latch.countDown();
            }
        });

        try {
            latch.await(45, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        if (errRef.get() != null) return;

        int days = daysRef.get() == null ? 2 : daysRef.get();
        DateFormat df = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
        Calendar todayCal = Calendar.getInstance();
        todayCal.set(Calendar.HOUR_OF_DAY, 0);
        todayCal.set(Calendar.MINUTE, 0);
        todayCal.set(Calendar.SECOND, 0);
        todayCal.set(Calendar.MILLISECOND, 0);
        long todayMidnight = todayCal.getTimeInMillis();

        List<String> lines = new ArrayList<>();
        for (Product p : productsRef.get()) {
            if (p.expiresAtMs <= 0L) continue;
            Calendar expCal = Calendar.getInstance();
            expCal.setTimeInMillis(p.expiresAtMs);
            expCal.set(Calendar.HOUR_OF_DAY, 0);
            expCal.set(Calendar.MINUTE, 0);
            expCal.set(Calendar.SECOND, 0);
            expCal.set(Calendar.MILLISECOND, 0);
            long daysUntil = (expCal.getTimeInMillis() - todayMidnight) / 86400000L;
            if (daysUntil >= 0 && daysUntil <= days) {
                String exp = df.format(new Date(p.expiresAtMs));
                int amt = Math.max(0, p.amount);
                lines.add(p.name + (amt > 1 ? " (×" + amt + ")" : "") + " — expires " + exp);
            }
        }

        if (!lines.isEmpty()) {
            ExpiryNotificationHelper.showExpiringSoonNotification(app, lines);
        }
    }
}
