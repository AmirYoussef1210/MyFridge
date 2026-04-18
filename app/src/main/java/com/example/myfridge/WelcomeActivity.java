package com.example.myfridge;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
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

/**
 * Splash / welcome screen shown on first launch or when the user is not
 * automatically logged in.
 * <p>
 * If the user previously enabled "stay logged in" and still has a valid
 * Firebase session, the screen is skipped and {@link #routeAfterLogin} is
 * called immediately. Otherwise the welcome layout is displayed with
 * "Sign in" and "Create account" buttons.
 * </p>
 * <p>
 * The background bitmap is downsampled to avoid an OOM / canvas-size crash
 * on devices with a large drawable source.
 * </p>
 */
public class WelcomeActivity extends AppCompatActivity {

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

    /**
     * Initialises the activity, checks the auto-login preference and either
     * routes the user straight to the app or shows the welcome screen.
     *
     * @param savedInstanceState previously saved instance state (may be {@code null})
     */
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

    /**
     * Calculates the largest power-of-two sample size that keeps the decoded
     * bitmap at least as large as the requested dimensions, to avoid loading an
     * unnecessarily large image into memory.
     *
     * @param options   {@link BitmapFactory.Options} populated after a
     *                  {@code inJustDecodeBounds = true} decode pass
     * @param reqWidth  minimum desired width in pixels
     * @param reqHeight minimum desired height in pixels
     * @return a power-of-two {@code inSampleSize} value (≥ 1)
     */
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

    /**
     * Reads the user's RTDB preferences and navigates to {@link OnboardingActivity}
     * if units or expiry-window are missing, otherwise to {@link MainScreenActivity}.
     * The back stack is cleared so the user cannot return to the welcome screen.
     *
     * @param user the currently authenticated Firebase user
     */
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
