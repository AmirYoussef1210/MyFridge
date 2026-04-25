package com.example.myfridge;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.myfridge.rtdb.RtdbRepository;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.database.FirebaseDatabase;

/**
 * Account-creation screen.
 * <p>
 * Collects a full name, email address and password, creates a new Firebase
 * Authentication account, sets the display name on the Auth profile, and
 * writes the name to RTDB under {@code /users/<uid>/name}. After a successful
 * registration the user is forwarded to {@link OnboardingActivity}.
 * </p>
 * <p>
 * A {@link NetworkChangeReceiver} is registered while the activity is visible.
 * </p>
 */
public class SignupActivity extends AppCompatActivity {

    private EditText etFullName, etEmail, etPassword;
    private Button btnSignup;
    private TextView tvMessage, txtLogin;
    private FirebaseAuth mAuth;
    private NetworkChangeReceiver networkReceiver;

    /** Registers the network-change receiver while the activity is in the foreground. */
    @Override
    protected void onResume() {
        super.onResume();
        networkReceiver = new NetworkChangeReceiver(this);
        registerReceiver(networkReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    /** Unregisters the network-change receiver when the activity leaves the foreground. */
    @Override
    protected void onPause() {
        super.onPause();
        if (networkReceiver != null) {
            unregisterReceiver(networkReceiver);
            networkReceiver = null;
        }
    }

    /**
     * Inflates the signup layout and wires the sign-up button and the
     * "already have an account" link.
     *
     * @param savedInstanceState previously saved instance state (may be {@code null})
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        mAuth = FirebaseAuth.getInstance();

        etFullName = findViewById(R.id.etFullName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnSignup = findViewById(R.id.btnSignup);
        tvMessage = findViewById(R.id.tvMessage);
        txtLogin = findViewById(R.id.txtLogin);

        btnSignup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                signup();
            }
        });

        txtLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish(); // Go back to MainActivity (Login)
            }
        });
    }

    /**
     * Validates the input fields (name, email, password length ≥ 6) and calls
     * {@link FirebaseAuth#createUserWithEmailAndPassword}. On success, sets the
     * display name via {@link com.google.firebase.auth.UserProfileChangeRequest},
     * writes the name to RTDB, ensures a user profile node exists, and starts
     * {@link OnboardingActivity}.
     */
    private void signup() {
        String fullName = etFullName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(fullName) || TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            tvMessage.setText("Please fill all fields");
            return;
        }

        if (password.length() < 6) {
            tvMessage.setText("Password must be at least 6 characters.");
            return;
        }

        ProgressDialog pd = new ProgressDialog(this);
        pd.setTitle("Connecting");
        pd.setMessage("Creating user...");
        pd.show();

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        pd.dismiss();
                        if (task.isSuccessful()) {
                            FirebaseUser user = mAuth.getCurrentUser();
                            if (user != null) {
                                // Save name to Auth profile (optional) and RTDB (used by greeting/settings).
                                UserProfileChangeRequest req = new UserProfileChangeRequest.Builder()
                                        .setDisplayName(fullName)
                                        .build();

                                user.updateProfile(req).addOnCompleteListener(new OnCompleteListener<Void>() {
                                    @Override
                                    public void onComplete(@NonNull Task<Void> t) {
                                        new RtdbRepository().ensureUserProfile(user);
                                        FirebaseDatabase.getInstance()
                                                .getReference()
                                                .child("users")
                                                .child(user.getUid())
                                                .child("name")
                                                .setValue(fullName);
                                        Intent i = new Intent(SignupActivity.this, OnboardingActivity.class);
                                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                        startActivity(i);
                                        finish();
                                    }
                                });
                                return;
                            }
                            finish();
                        } else {
                            handleError(task.getException());
                        }
                    }
                });
    }

    /**
     * Maps Firebase registration exceptions to user-friendly messages displayed
     * in {@code tvMessage}.
     *
     * @param exp exception thrown by the failed registration task; may be {@code null}
     */
    private void handleError(Exception exp) {
        String message;
        if (exp instanceof FirebaseAuthWeakPasswordException) {
            message = "Password too weak.";
        } else if (exp instanceof FirebaseAuthInvalidCredentialsException) {
            message = "Invalid email address.";
        } else if (exp instanceof FirebaseAuthUserCollisionException) {
            message = "User already exists.";
        } else if (exp instanceof FirebaseNetworkException) {
            message = "Network error.";
        } else {
            message = "An error occurred.";
        }
        tvMessage.setText(message);
    }
}
