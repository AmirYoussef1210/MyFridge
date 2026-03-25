package com.example.myfridge;

import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.myfridge.fragments.HomeFragment;
import com.example.myfridge.fragments.SettingsFragment;
import com.example.myfridge.fragments.ShoppingFragment;
import com.example.myfridge.fragments.StorageFragment;
import com.example.myfridge.notifications.ExpiryNotificationHelper;
import com.example.myfridge.notifications.ExpiryWorkScheduler;
import com.example.myfridge.rtdb.RtdbRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class MainScreenActivity extends AppCompatActivity {
    private RtdbRepository rtdb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_screen);

        rtdb = new RtdbRepository();

        Button btnInventory = findViewById(R.id.btn_inventory);
        btnInventory.setOnClickListener(v -> switchFragment(new StorageFragment()));

        Button btnShopping = findViewById(R.id.btn_shopping);
        btnShopping.setOnClickListener(v -> switchFragment(new ShoppingFragment()));

        Button btnSettings = findViewById(R.id.btn_settings);
        btnSettings.setOnClickListener(v -> switchFragment(new SettingsFragment()));

        if (savedInstanceState == null) {
            switchFragment(new HomeFragment());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            rtdb.ensureUserProfile(user);
            ExpiryNotificationHelper.createChannel(this);
            ExpiryWorkScheduler.schedule(this);
        }
    }

    /** Used by bottom bar and {@link HomeFragment} shortcut rows. */
    public void switchFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.main_fragment_container, fragment)
                .commit();
    }

    @Override
    public void onBackPressed() {
        Fragment current = getSupportFragmentManager().findFragmentById(R.id.main_fragment_container);
        if (current != null && !(current instanceof HomeFragment)) {
            switchFragment(new HomeFragment());
            return;
        }
        moveTaskToBack(true);
    }
}
