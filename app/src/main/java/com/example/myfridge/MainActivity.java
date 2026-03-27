package com.example.myfridge;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class MainActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private CheckBox cbStayLoggedIn;
    private TextView tvMessage, txtSignup;
    private Button btnLogin;
    private FirebaseAuth mAuth;
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
    protected void onCreate(Bundle savedInstanceState) {
        // Force light mode globally
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        super.onCreate(savedInstanceState);

        // 1. Check if user should stay logged in
        SharedPreferences sp = getSharedPreferences("MyFridgePrefs", Context.MODE_PRIVATE);
        boolean stayLoggedIn = sp.getBoolean("stayLoggedIn", false);
        mAuth = FirebaseAuth.getInstance();

        if (stayLoggedIn && mAuth.getCurrentUser() != null) {
            routeAfterLogin(mAuth.getCurrentUser());
            return;
        }

        setContentView(R.layout.activity_main);

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        cbStayLoggedIn = findViewById(R.id.cbStayLoggedIn);
        tvMessage = findViewById(R.id.tvMessage);
        txtSignup = findViewById(R.id.txtSignup);
        btnLogin = findViewById(R.id.btnLogin);

        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                login();
            }
        });

        txtSignup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SignupActivity.class));
            }
        });
    }

    private void login() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            tvMessage.setText("Please fill all fields");
            return;
        }

        ProgressDialog pd = new ProgressDialog(this);
        pd.setTitle("Connecting");
        pd.setMessage("Logging in...");
        pd.show();

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        pd.dismiss();
                        if (task.isSuccessful()) {
                            // 2. Save preference if checkbox is checked
                            if (cbStayLoggedIn.isChecked()) {
                                SharedPreferences sp = getSharedPreferences("MyFridgePrefs", Context.MODE_PRIVATE);
                                sp.edit().putBoolean("stayLoggedIn", true).apply();
                            }

                            routeAfterLogin(mAuth.getCurrentUser());
                        } else {
                            handleError(task.getException());
                        }
                    }
                });
    }

    private void routeAfterLogin(FirebaseUser user) {
        if (user == null) {
            setContentView(R.layout.activity_main);
            return;
        }

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
                        Intent i = new Intent(MainActivity.this, missing ? OnboardingActivity.class : MainScreenActivity.class);
                        startActivity(i);
                        finish();
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        startActivity(new Intent(MainActivity.this, MainScreenActivity.class));
                        finish();
                    }
                });
    }

    private void handleError(Exception exp) {
        String message;
        if (exp instanceof FirebaseAuthInvalidUserException) {
            message = "Invalid email address.";
        } else if (exp instanceof FirebaseAuthInvalidCredentialsException) {
            message = "Invalid password.";
        } else if (exp instanceof FirebaseNetworkException) {
            message = "Network error.";
        } else {
            message = "An error occurred.";
        }
        tvMessage.setText(message);
    }
}
