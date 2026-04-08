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

/**
 * Primary activity that hosts the bottom-navigation bar and the fragment
 * container used by the rest of the application.
 * <p>
 * Extends {@link MasterActivity} which provides the top-right settings menu
 * button. Navigation between {@link com.example.myfridge.fragments.HomeFragment},
 * {@link com.example.myfridge.fragments.StorageFragment},
 * {@link com.example.myfridge.fragments.ShoppingFragment} and
 * {@link com.example.myfridge.fragments.SettingsFragment} is driven by the
 * bottom-bar buttons.
 * </p>
 * <p>
 * On resume it ensures the user profile node exists, creates the expiry
 * notification channel and schedules the daily expiry alarm via {@link com.example.myfridge.notifications.ExpiryWorkScheduler}.
 * </p>
 */
public class MainScreenActivity extends MasterActivity {

    private RtdbRepository rtdb;

    /**
     * Returns the layout resource that will be inflated into the master
     * content host by {@link MasterActivity}.
     *
     * @return {@code R.layout.activity_main_screen}
     */
    @Override
    protected int getMasterContentLayoutId() {
        return R.layout.activity_main_screen;
    }

    /**
     * Wires the bottom-navigation buttons and shows {@link com.example.myfridge.fragments.HomeFragment}
     * as the initial destination when the activity is first created.
     *
     * @param savedInstanceState previously saved instance state; fragment is not
     *                           replaced when restoring from saved state
     */
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

    /**
     * Called when the user taps "Settings" in the master options popup.
     * Navigates to {@link com.example.myfridge.fragments.SettingsFragment}.
     */
    @Override
    protected void onSettingsMenuSelected() {
        switchFragment(new SettingsFragment());
    }

    /**
     * Ensures user profile, notification channel and background expiry-check
     * work are set up each time the activity returns to the foreground.
     */
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

    /**
     * Replaces the fragment container with {@code fragment}, unless the same
     * fragment class is already displayed (no-op in that case).
     * Used by the bottom bar buttons and {@link com.example.myfridge.fragments.HomeFragment}
     * shortcut rows.
     *
     * @param fragment the fragment to display
     */
    public void switchFragment(Fragment fragment) {
        Fragment current = getSupportFragmentManager().findFragmentById(R.id.main_fragment_container);
        if (current != null && current.getClass().equals(fragment.getClass())) {
            return;
        }
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.main_fragment_container, fragment)
                .commit();
    }

    /**
     * Overrides back-press behaviour: navigates back to {@link com.example.myfridge.fragments.HomeFragment}
     * when a non-home fragment is displayed; moves the task to the background
     * (rather than finishing) when already on the home screen.
     */
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
