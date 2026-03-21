package com.example.myfridge.notifications;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.myfridge.MainScreenActivity;
import com.example.myfridge.R;

import java.util.List;

public final class ExpiryNotificationHelper {

    public static final String CHANNEL_ID = "expiry_alerts";

    private ExpiryNotificationHelper() {}

    public static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.expiry_notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription(context.getString(R.string.expiry_notification_channel_desc));
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.createNotificationChannel(channel);
        }
    }

    /**
     * Single summary notification listing products expiring within the user's window.
     */
    public static void showExpiringSoonNotification(Context context, List<String> productLines) {
        if (productLines == null || productLines.isEmpty()) return;

        createChannel(context);

        String title = context.getString(R.string.expiry_notification_title);
        StringBuilder body = new StringBuilder();
        int maxLines = Math.min(productLines.size(), 8);
        for (int i = 0; i < maxLines; i++) {
            if (i > 0) body.append('\n');
            body.append("• ").append(productLines.get(i));
        }
        if (productLines.size() > maxLines) {
            body.append('\n').append("…");
        }

        Intent open = new Intent(context, MainScreenActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= android.app.PendingIntent.FLAG_IMMUTABLE;
        }
        android.app.PendingIntent pi = android.app.PendingIntent.getActivity(
                context, 0, open, flags
        );

        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_fridge)
                .setContentTitle(title)
                .setContentText(body.length() > 120 ? body.substring(0, 117) + "…" : body.toString())
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body.toString()))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pi)
                .setAutoCancel(true);

        NotificationManagerCompat.from(context).notify(7101, b.build());
    }
}
