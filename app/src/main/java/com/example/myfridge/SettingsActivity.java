package com.example.myfridge;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.myfridge.notifications.ExpiryNotificationHelper;
import com.example.myfridge.notifications.ExpiryWorkScheduler;
import com.example.myfridge.rtdb.RtdbRepository;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

/**
 * Standalone settings screen (Activity variant — also available as
 * {@link com.example.myfridge.fragments.SettingsFragment}).
 * <p>
 * Loads the current user preferences (units, expiry-window days, notification
 * toggle) from RTDB and lets the user save updated values. Also exposes
 * navigation to {@link AboutActivity} and a logout action.
 * </p>
 * <p>
 * On Android 13+ (API 33), requests the {@code POST_NOTIFICATIONS} runtime
 * permission when the expiry-notification switch is turned on.
 * </p>
 */
public class SettingsActivity extends AppCompatActivity {

    private static final int REQ_POST_NOTIFICATIONS = 5401;

    private RadioGroup rgUnits;
    private RadioButton rbMetric;
    private RadioButton rbImperial;
    private EditText etDays;
    private SwitchMaterial swExpiryNotifications;
    private TextView tvError;
    private RtdbRepository rtdb;
    private NetworkChangeReceiver networkReceiver;

    @Override
    protected void onResume() {
        super.onResume();
        networkReceiver = new NetworkChangeReceiver(this);
        registerReceiver(networkReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (networkReceiver != null) {
            unregisterReceiver(networkReceiver);
            networkReceiver = null;
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        rtdb = new RtdbRepository();
        rgUnits = findViewById(R.id.rg_settings_units);
        rbMetric = findViewById(R.id.rb_settings_metric);
        rbImperial = findViewById(R.id.rb_settings_imperial);
        etDays = findViewById(R.id.et_settings_days_before_expire);
        swExpiryNotifications = findViewById(R.id.sw_expiry_notifications);
        tvError = findViewById(R.id.tv_settings_error);

        ExpiryNotificationHelper.createChannel(this);
        swExpiryNotifications.setChecked(ExpiryWorkScheduler.areExpiryNotificationsEnabled(this));

        loadFromRtdb();

        Button btnSave = findViewById(R.id.btn_save_settings);
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings();
            }
        });

        Button btnAbout = findViewById(R.id.btn_about);
        btnAbout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, AboutActivity.class));
            }
        });

        Button btnLogout = findViewById(R.id.btn_logout);
        btnLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                logout();
            }
        });
    }

    /**
     * Reads the authenticated user's preferences from RTDB and pre-populates
     * the unit radio buttons and expiry-days field. Defaults to 2 days if the
     * fetch fails.
     */
    private void loadFromRtdb() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        rtdb.fetchUserPreferences(user, new RtdbRepository.UserPrefsCallback() {
            @Override
            public void onSuccess(@NonNull String units, int daysBeforeExpireChoice) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        etDays.setText(String.valueOf(daysBeforeExpireChoice));
                        String u = units == null ? "" : units.trim().toLowerCase();
                        if ("imperial".equals(u)) {
                            rbImperial.setChecked(true);
                        } else {
                            rbMetric.setChecked(true);
                        }
                    }
                });
            }

            @Override
            public void onFailure(@NonNull com.google.firebase.database.DatabaseError error) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        etDays.setText("2");
                    }
                });
            }
        });
    }

    /**
     * Validates the unit selection and expiry-window value (1–30), then writes
     * both to RTDB. Also applies the notification toggle: schedules or cancels
     * the {@link com.example.myfridge.notifications.ExpiryWorkScheduler} and
     * requests the POST_NOTIFICATIONS permission if needed.
     */
    private void saveSettings() {
        tvError.setText("");
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            tvError.setText("Not signed in.");
            return;
        }

        int selectedId = rgUnits.getCheckedRadioButtonId();
        String units = "";
        if (selectedId != -1) {
            RadioButton rb = findViewById(selectedId);
            if (rb != null && rb.getTag() != null) {
                units = String.valueOf(rb.getTag());
            }
        }
        if (units.isEmpty()) {
            tvError.setText("Please choose units.");
            return;
        }

        String daysRaw = etDays.getText() == null ? "" : etDays.getText().toString().trim();
        if (TextUtils.isEmpty(daysRaw)) {
            tvError.setText("Enter days before expiration (1–30).");
            return;
        }
        int days;
        try {
            days = Integer.parseInt(daysRaw);
        } catch (NumberFormatException e) {
            tvError.setText("Invalid number.");
            return;
        }
        if (days < 1 || days > 30) {
            tvError.setText("Days must be between 1 and 30.");
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("units", units);
        updates.put("daysBeforeExpireChoice", days);

        FirebaseDatabase.getInstance()
                .getReference()
                .child("users")
                .child(user.getUid())
                .updateChildren(updates)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (!task.isSuccessful()) {
                            tvError.setText("Could not save. Check connection.");
                            return;
                        }

                        boolean notifyOn = swExpiryNotifications.isChecked();
                        ExpiryWorkScheduler.setExpiryNotificationsEnabled(SettingsActivity.this, notifyOn);
                        if (notifyOn) {
                            requestPostNotificationPermissionIfNeeded();
                            ExpiryWorkScheduler.schedule(SettingsActivity.this);
                            ExpiryWorkScheduler.runOnceSoon(SettingsActivity.this);
                        }

                        Toast.makeText(SettingsActivity.this, "Saved", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * On Android 13+ (API 33), requests the {@code POST_NOTIFICATIONS} runtime
     * permission if it has not already been granted. Shows a rationale dialog first
     * if the user has previously denied, or directs to Settings if permanently denied.
     * No-op on earlier API levels.
     */
    private void requestPostNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < 33) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS)) {
            // User denied once before — explain why, then re-request
            new AlertDialog.Builder(this)
                    .setTitle("Notifications needed")
                    .setMessage("Allow notifications so you get alerts before your food expires.")
                    .setPositiveButton("Allow", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            ActivityCompat.requestPermissions(
                                    SettingsActivity.this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_POST_NOTIFICATIONS);
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            // First-time request (system will show the dialog)
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQ_POST_NOTIFICATIONS
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_POST_NOTIFICATIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Granted — nothing extra to do, scheduler was already set up in saveSettings()
            } else if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS)) {
                // Denied but "Don't ask again" was NOT checked — single toast
                Toast.makeText(this, "Notifications permission denied. You won't receive expiry alerts.", Toast.LENGTH_SHORT).show();
            } else {
                // "Don't ask again" — direct to Settings
                new AlertDialog.Builder(this)
                        .setTitle("Notifications permission required")
                        .setMessage("Notification permission was permanently denied. Please enable it in Settings to receive expiry alerts.")
                        .setPositiveButton("Open Settings", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                intent.setData(Uri.fromParts("package", getPackageName(), null));
                                startActivity(intent);
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        }
    }

    /**
     * Cancels scheduled expiry work, signs the user out of Firebase, clears the
     * "stay logged in" shared-preference flag and navigates back to
     * {@link MainActivity} with a cleared back stack.
     */
    private void logout() {
        ExpiryWorkScheduler.cancel(this);

        FirebaseAuth.getInstance().signOut();

        SharedPreferences sp = getSharedPreferences("MyFridgePrefs", Context.MODE_PRIVATE);
        sp.edit().putBoolean("stayLoggedIn", false).apply();

        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}
