package com.example.myfridge;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class WelcomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);

        // If user chose "stay logged in" and is still authenticated, skip welcome
        SharedPreferences sp = getSharedPreferences("MyFridgePrefs", Context.MODE_PRIVATE);
        boolean stayLoggedIn = sp.getBoolean("stayLoggedIn", false);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (stayLoggedIn && user != null) {
            routeAfterLogin(user);
            return;
        }

        setContentView(R.layout.activity_welcome);

        findViewById(R.id.btn_sign_in).setOnClickListener(v ->
                startActivity(new Intent(this, MainActivity.class)));

        findViewById(R.id.btn_create_account).setOnClickListener(v ->
                startActivity(new Intent(this, SignupActivity.class)));
    }

    private void routeAfterLogin(FirebaseUser user) {
        FirebaseDatabase.getInstance()
                .getReference()
                .child("users")
                .child(user.getUid())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        String units = snapshot.child("units").getValue(String.class);
                        Long days = snapshot.child("daysBeforeExpireChoice").getValue(Long.class);
                        boolean missing = (units == null || units.trim().isEmpty()) || (days == null || days <= 0L);
                        Intent i = new Intent(WelcomeActivity.this, missing ? OnboardingActivity.class : MainScreenActivity.class);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(i);
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        Intent i = new Intent(WelcomeActivity.this, MainScreenActivity.class);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(i);
                    }
                });
    }
}
