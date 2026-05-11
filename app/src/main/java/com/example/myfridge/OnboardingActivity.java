package com.example.myfridge;

import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

/**
 * First-time setup screen shown after registration or when user preferences are
 * incomplete.
 * <p>
 * Asks the user to choose a measurement unit system (metric / imperial) and the
 * number of days before expiry at which a notification should be triggered
 * (1–30 days). Values are written to {@code /users/<uid>} in Firebase RTDB;
 * on success the user is sent to {@link MainScreenActivity}.
 * </p>
 * <p>
 * Redirects to {@link MainActivity} if no authenticated user is found, and
 * registers a {@link NetworkChangeReceiver} while in the foreground.
 * </p>
 */
public class OnboardingActivity extends AppCompatActivity {

    private NetworkChangeReceiver networkReceiver;

    /** Registers the network receiver while the activity is in the foreground. */
    @Override
    protected void onResume() {
        super.onResume();
        networkReceiver = new NetworkChangeReceiver(this);
        registerReceiver(networkReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    /**
     * Unregisters the {@link NetworkChangeReceiver} to prevent memory leaks
     * when the activity moves to the background.
     */
    @Override
    protected void onPause() {
        super.onPause();
        if (networkReceiver != null) {
            unregisterReceiver(networkReceiver);
            networkReceiver = null;
        }
    }

    /**
     * Inflates the onboarding layout, personalises the greeting with the user's
     * display name, and attaches a save listener that validates and persists the
     * chosen units and expiry-window to RTDB.
     *
     * @param savedInstanceState previously saved instance state (may be {@code null})
     */
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        TextView txtTitle = findViewById(R.id.txt_onboarding_title);
        String display = user.getDisplayName();
        if (display == null || display.trim().isEmpty()) display = "Welcome";
        txtTitle.setText("Hi " + display + ", let's set things up");

        RadioGroup rgUnits = findViewById(R.id.rg_units);
        EditText etDays = findViewById(R.id.et_days_before_expire);
        Button btnSave = findViewById(R.id.btn_save_onboarding);
        TextView tvError = findViewById(R.id.tv_onboarding_error);

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tvError.setText("");

                int selectedId = rgUnits.getCheckedRadioButtonId();
                String units = "";
                if (selectedId != -1) {
                    RadioButton rb = findViewById(selectedId);
                    units = rb.getTag() == null ? "" : String.valueOf(rb.getTag());
                }

                String daysRaw = etDays.getText() == null ? "" : etDays.getText().toString().trim();
                Integer days = null;
                if (!TextUtils.isEmpty(daysRaw)) {
                    try {
                        days = Integer.parseInt(daysRaw);
                    } catch (NumberFormatException ignored) {
                    }
                }

                if (units.isEmpty()) {
                    tvError.setText("Please choose units.");
                    return;
                }
                if (days == null || days < 1 || days > 30) {
                    tvError.setText("Days before expire must be between 1 and 30.");
                    return;
                }

                Map<String, Object> updates = new HashMap<>();
                updates.put("units", units);
                updates.put("daysBeforeExpireChoice", days);

                final Integer finalDays = days;
                final String finalUnits = units;
                FirebaseDatabase.getInstance()
                        .getReference()
                        .child("users")
                        .child(user.getUid())
                        .updateChildren(updates)
                        .addOnCompleteListener(new OnCompleteListener<Void>() {
                            @Override
                            public void onComplete(Task<Void> t) {
                                if (t.isSuccessful()) {
                                    Intent i = new Intent(OnboardingActivity.this, MainScreenActivity.class);
                                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    startActivity(i);
                                    finish();
                                } else {
                                    tvError.setText("Failed saving settings. Try again.");
                                }
                            }
                        });
            }
        });
    }
}

