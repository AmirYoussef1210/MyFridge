package com.example.myfridge.fragments;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.myfridge.MainActivity;
import com.example.myfridge.R;
import com.example.myfridge.notifications.ExpiryNotificationHelper;
import com.example.myfridge.notifications.ExpiryWorkScheduler;
import com.example.myfridge.rtdb.RtdbRepository;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class SettingsFragment extends Fragment {
    private static final int REQ_POST_NOTIFICATIONS = 5401;
    private RadioGroup rgUnits;
    private RadioButton rbMetric;
    private RadioButton rbImperial;
    private EditText etDays;
    private SwitchMaterial swExpiryNotifications;
    private TextView tvError;
    private ProgressBar progressLoading;
    private RtdbRepository rtdb;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.activity_settings, container, false);
        rtdb = new RtdbRepository();
        rgUnits = root.findViewById(R.id.rg_settings_units);
        rbMetric = root.findViewById(R.id.rb_settings_metric);
        rbImperial = root.findViewById(R.id.rb_settings_imperial);
        etDays = root.findViewById(R.id.et_settings_days_before_expire);
        swExpiryNotifications = root.findViewById(R.id.sw_expiry_notifications);
        tvError = root.findViewById(R.id.tv_settings_error);
        progressLoading = root.findViewById(R.id.progress_loading);
        ExpiryNotificationHelper.createChannel(requireContext());
        swExpiryNotifications.setChecked(ExpiryWorkScheduler.areExpiryNotificationsEnabled(requireContext()));
        loadFromRtdb();
        root.findViewById(R.id.btn_save_settings).setOnClickListener(v -> saveSettings());
        root.findViewById(R.id.btn_about).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), com.example.myfridge.AboutActivity.class)));
        root.findViewById(R.id.btn_logout).setOnClickListener(v -> logout());
        return root;
    }

    private void loadFromRtdb() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        progressLoading.setVisibility(View.VISIBLE);
        rtdb.fetchUserPreferences(user, new RtdbRepository.UserPrefsCallback() {
            @Override public void onSuccess(@NonNull String units, int daysBeforeExpireChoice) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    progressLoading.setVisibility(View.GONE);
                    etDays.setText(String.valueOf(daysBeforeExpireChoice));
                    String u = units == null ? "" : units.trim().toLowerCase();
                    if ("imperial".equals(u)) rbImperial.setChecked(true); else rbMetric.setChecked(true);
                });
            }
            @Override public void onFailure(@NonNull com.google.firebase.database.DatabaseError error) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    progressLoading.setVisibility(View.GONE);
                    etDays.setText("2");
                });
            }
        });
    }

    private void saveSettings() {
        tvError.setText("");
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { tvError.setText("Not signed in."); return; }
        int selectedId = rgUnits.getCheckedRadioButtonId();
        String units = "";
        if (selectedId != -1) {
            RadioButton rb = rgUnits.findViewById(selectedId);
            if (rb != null && rb.getTag() != null) units = String.valueOf(rb.getTag());
        }
        if (units.isEmpty()) { tvError.setText("Please choose units."); return; }
        String daysRaw = etDays.getText() == null ? "" : etDays.getText().toString().trim();
        if (TextUtils.isEmpty(daysRaw)) { tvError.setText("Enter days before expiration (1–30)."); return; }
        int days;
        try { days = Integer.parseInt(daysRaw); } catch (NumberFormatException e) { tvError.setText("Invalid number."); return; }
        if (days < 1 || days > 30) { tvError.setText("Days must be between 1 and 30."); return; }

        Map<String, Object> updates = new HashMap<>();
        updates.put("units", units);
        updates.put("daysBeforeExpireChoice", days);
        FirebaseDatabase.getInstance().getReference().child("users").child(user.getUid()).updateChildren(updates).addOnCompleteListener(task -> {
            if (!task.isSuccessful()) { tvError.setText("Could not save. Check connection."); return; }
            boolean notifyOn = swExpiryNotifications.isChecked();
            ExpiryWorkScheduler.setExpiryNotificationsEnabled(requireContext(), notifyOn);
            if (notifyOn) {
                requestPostNotificationPermissionIfNeeded();
                ExpiryWorkScheduler.schedule(requireContext());
                ExpiryWorkScheduler.runOnceSoon(requireContext());
            }
            Toast.makeText(requireContext(), "Saved", Toast.LENGTH_SHORT).show();
        });
    }

    private void requestPostNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < 33) return;
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return;
        ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_POST_NOTIFICATIONS);
    }

    private void logout() {
        ExpiryWorkScheduler.cancel(requireContext());
        FirebaseAuth.getInstance().signOut();
        SharedPreferences sp = requireContext().getSharedPreferences("MyFridgePrefs", Context.MODE_PRIVATE);
        sp.edit().putBoolean("stayLoggedIn", false).apply();
        Intent i = new Intent(requireContext(), MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        requireActivity().finish();
    }
}

