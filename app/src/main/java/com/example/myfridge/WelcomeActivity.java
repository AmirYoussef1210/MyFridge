package com.example.myfridge;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.ImageView;

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

        // Downsample the large background image to avoid canvas size crash.
        // inScaled=false prevents Android from density-scaling the bitmap up before we sample it.
        ImageView bg = findViewById(R.id.img_background);
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inScaled = false;
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(getResources(), R.drawable.welcome_screen, opts);
        opts.inSampleSize = calculateInSampleSize(opts, 1080, 1920);
        opts.inJustDecodeBounds = false;
        Bitmap bmp = BitmapFactory.decodeResource(getResources(), R.drawable.welcome_screen, opts);
        bg.setImageBitmap(bmp);

        findViewById(R.id.btn_sign_in).setOnClickListener(v ->
                startActivity(new Intent(this, MainActivity.class)));

        findViewById(R.id.btn_create_account).setOnClickListener(v ->
                startActivity(new Intent(this, SignupActivity.class)));
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
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
