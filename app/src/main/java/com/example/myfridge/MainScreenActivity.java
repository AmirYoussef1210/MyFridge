package com.example.myfridge;

import android.os.Bundle;
import android.widget.Button;

import androidx.annotation.Nullable;
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

public class MainScreenActivity extends MasterActivity {

    private RtdbRepository rtdb;

    @Override
    protected int getMasterContentLayoutId() {
        return R.layout.activity_main_screen;
    }

    @Override
    protected void onAfterMasterContentInflated(@Nullable Bundle savedInstanceState) {
        rtdb = new RtdbRepository();

        Button btnHome = findViewById(R.id.btn_home);
        btnHome.setOnClickListener(v -> switchFragment(new HomeFragment()));

        Button btnInventory = findViewById(R.id.btn_inventory);
        btnInventory.setOnClickListener(v -> switchFragment(new StorageFragment()));

        Button btnShopping = findViewById(R.id.btn_shopping);
        btnShopping.setOnClickListener(v -> switchFragment(new ShoppingFragment()));

        if (savedInstanceState == null) {
            switchFragment(new HomeFragment());
        }
    }

    @Override
    protected void onSettingsMenuSelected() {
        switchFragment(new SettingsFragment());
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
