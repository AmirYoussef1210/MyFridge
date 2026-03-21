package com.example.myfridge.notifications;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.myfridge.rtdb.RtdbRepository;
import com.example.myfridge.storage.Product;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseError;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class ExpiryCheckWorker extends Worker {

    public ExpiryCheckWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context app = getApplicationContext();
        if (!ExpiryWorkScheduler.areExpiryNotificationsEnabled(app)) {
            return Result.success();
        }

        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                return Result.success();
            }
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return Result.success();
        }

        RtdbRepository repo = new RtdbRepository();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Integer> daysRef = new AtomicReference<>(2);
        AtomicReference<List<Product>> productsRef = new AtomicReference<>(new ArrayList<>());
        AtomicReference<DatabaseError> errRef = new AtomicReference<>();

        repo.fetchUserPreferences(user, new RtdbRepository.UserPrefsCallback() {
            @Override
            public void onSuccess(@NonNull String units, int daysBeforeExpireChoice) {
                daysRef.set(daysBeforeExpireChoice);
                repo.fetchAllInventory(user, new RtdbRepository.ProductsCallback() {
                    @Override
                    public void onSuccess(@NonNull List<Product> products) {
                        productsRef.set(products);
                        latch.countDown();
                    }

                    @Override
                    public void onFailure(@NonNull DatabaseError error) {
                        errRef.set(error);
                        latch.countDown();
                    }
                });
            }

            @Override
            public void onFailure(@NonNull DatabaseError error) {
                errRef.set(error);
                latch.countDown();
            }
        });

        try {
            if (!latch.await(45, TimeUnit.SECONDS)) {
                return Result.retry();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.retry();
        }

        if (errRef.get() != null) {
            return Result.success();
        }

        int days = daysRef.get() == null ? 2 : daysRef.get();
        long windowMs = (long) days * 24L * 60L * 60L * 1000L;
        long now = System.currentTimeMillis();
        DateFormat df = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());

        List<String> lines = new ArrayList<>();
        for (Product p : productsRef.get()) {
            if (p.expiresAtMs <= 0L) continue;
            long diff = p.expiresAtMs - now;
            if (diff >= 0L && diff <= windowMs) {
                String exp = df.format(new Date(p.expiresAtMs));
                int amt = Math.max(0, p.amount);
                lines.add(p.name + (amt > 1 ? " (×" + amt + ")" : "") + " — expires " + exp);
            }
        }

        if (!lines.isEmpty()) {
            ExpiryNotificationHelper.showExpiringSoonNotification(app, lines);
        }

        return Result.success();
    }
}
