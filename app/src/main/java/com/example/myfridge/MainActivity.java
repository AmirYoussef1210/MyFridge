package com.example.myfridge;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
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
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseUser;

public class MainActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private TextView tvMessage, txtSignup;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        tvMessage = findViewById(R.id.tvMessage);
        txtSignup = findViewById(R.id.txtSignup);

        txtSignup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SignupActivity.class));
            }
        });
    }

    public void login(View view) {
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
                            FirebaseUser user = mAuth.getCurrentUser();
                            tvMessage.setText("Login successful\nUid: " + user.getUid());
                        } else {
                            Exception exp = task.getException();
                            String message;
                            if (exp instanceof FirebaseAuthInvalidUserException) {
                                message = "Invalid email address.";
                            } else if (exp instanceof FirebaseAuthInvalidCredentialsException) {
                                message = "Invalid password.";
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
}
