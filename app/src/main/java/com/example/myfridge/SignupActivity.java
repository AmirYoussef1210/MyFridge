package com.example.myfridge;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;

public class SignupActivity extends AppCompatActivity {

    private EditText etFullName, etEmail, etPassword;
    private Button btnSignup;
    private TextView tvMessage;
    private FirebaseAuth mAuth;

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

        btnSignup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
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

                ProgressDialog pd = new ProgressDialog(SignupActivity.this);
                pd.setTitle("Connecting");
                pd.setMessage("Creating user...");
                pd.show();

                mAuth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener(SignupActivity.this, new OnCompleteListener<AuthResult>() {
                            @Override
                            public void onComplete(@NonNull Task<AuthResult> task) {
                                pd.dismiss();
                                if (task.isSuccessful()) {
                                    FirebaseUser user = mAuth.getCurrentUser();
                                    tvMessage.setText("User created successfully\nUid: " + user.getUid());
                                } else {
                                    Exception exp = task.getException();
                                    String message;
                                    if (exp instanceof FirebaseAuthWeakPasswordException) {
                                        message = "Password too weak.";
                                    } else if (exp instanceof FirebaseAuthInvalidCredentialsException) {
                                        message = "Invalid email address.";
                                    } else if (exp instanceof FirebaseAuthUserCollisionException) {
                                        message = "User already exists.";
                                    } else if (exp instanceof FirebaseNetworkException) {
                                        message = "Network error. Please check your connection and try again.";
                                    } else {
                                        message = "An error occurred. Please try again later.";
                                    }
                                    tvMessage.setText(message);
                                }
                            }
                        });
            }
        });
    }
}
