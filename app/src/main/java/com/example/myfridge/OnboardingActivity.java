package com.example.myfridge;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class OnboardingActivity extends AppCompatActivity {

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

        btnSave.setOnClickListener(v -> {
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

            FirebaseDatabase.getInstance()
                    .getReference()
                    .child("users")
                    .child(user.getUid())
                    .updateChildren(updates)
                    .addOnCompleteListener(t -> {
                        if (t.isSuccessful()) {
                            Intent i = new Intent(this, MainScreenActivity.class);
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(i);
                            finish();
                        } else {
                            tvError.setText("Failed saving settings. Try again.");
                        }
                    });
        });
    }
}

