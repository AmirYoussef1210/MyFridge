package com.example.myfridge.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.myfridge.MainScreenActivity;
import com.example.myfridge.R;
import com.example.myfridge.rtdb.RtdbRepository;
import com.example.myfridge.storage.Product;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Calendar;
import java.util.List;

/**
 * Home / dashboard fragment displayed on the main screen.
 * <p>
 * Shows a personalised greeting, a summary of total inventory items, how many
 * are about to expire (within the user's configured window), and counts for the
 * dairy and produce categories. Tapping the "about to expire" row navigates to
 * {@link StorageFragment}; tapping the shopping call-to-action navigates to
 * {@link ShoppingFragment}.
 * </p>
 * <p>
 * Stats are refreshed every time the fragment returns to the foreground
 * (via {@link #onResume}) so they stay current after the user edits inventory.
 * </p>
 */
public class HomeFragment extends Fragment {

    private TextView txtHelloUser;
    private TextView txtTotalProducts;
    private TextView txtAboutToExpire;
    private TextView txtDairyItems;
    private TextView txtProduceItems;
    private ProgressBar progressLoading;
    private RtdbRepository rtdb;

    /**
     * Inflates {@code R.layout.fragment_home}, binds all stat views, and attaches
     * click listeners to the "about to expire" and shopping rows.
     *
     * @param inflater           the LayoutInflater to inflate the fragment view
     * @param container          the parent view group (may be {@code null})
     * @param savedInstanceState previously saved instance state (may be {@code null})
     * @return the root view of the inflated fragment layout
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_home, container, false);
        rtdb = new RtdbRepository();
        txtHelloUser = root.findViewById(R.id.txt_hello_user);
        txtTotalProducts = root.findViewById(R.id.txt_total_products);
        txtAboutToExpire = root.findViewById(R.id.txt_about_to_expire);
        txtDairyItems = root.findViewById(R.id.txt_dairy_items);
        txtProduceItems = root.findViewById(R.id.txt_produce_items);
        progressLoading = root.findViewById(R.id.progress_loading);
        root.findViewById(R.id.row_about_to_expire).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openInventory();
            }
        });
        root.findViewById(R.id.row_shopping_cta).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openShopping();
            }
        });
        return root;
    }

    /**
     * Refreshes the greeting label and all inventory stats whenever the fragment
     * returns to the foreground, ensuring the displayed data is always up-to-date.
     */
    @Override
    public void onResume() {
        super.onResume();
        updateGreeting();
        updateStats();
    }

    /**
     * Navigates to the {@link StorageFragment} inventory screen by delegating to
     * the host {@link com.example.myfridge.MainScreenActivity}.
     */
    private void openInventory() {
        if (getActivity() instanceof MainScreenActivity) {
            ((MainScreenActivity) getActivity()).switchFragment(new StorageFragment());
        }
    }

    /**
     * Navigates to the {@link ShoppingFragment} shopping list screen by delegating to
     * the host {@link com.example.myfridge.MainScreenActivity}.
     */
    private void openShopping() {
        if (getActivity() instanceof MainScreenActivity) {
            ((MainScreenActivity) getActivity()).switchFragment(new ShoppingFragment());
        }
    }

    /**
     * Reads the current user's display name from RTDB and updates the greeting
     * label. Falls back to the Firebase display name, then email, then "User"
     * when the name is not found.
     */
    private void updateGreeting() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            txtHelloUser.setText("Hello, User");
            return;
        }

        FirebaseDatabase.getInstance()
                .getReference()
                .child("users")
                .child(user.getUid())
                .child("name")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        String name = snapshot.getValue(String.class);
                        if (name == null || name.trim().isEmpty()) name = user.getDisplayName();
                        if (name == null || name.trim().isEmpty()) name = user.getEmail();
                        if (name == null || name.trim().isEmpty()) name = "User";
                        if (!isAdded()) return;
                        txtHelloUser.setText("Hello, " + name);
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        String name = user.getDisplayName();
                        if (name == null || name.trim().isEmpty()) name = user.getEmail();
                        if (name == null || name.trim().isEmpty()) name = "User";
                        if (!isAdded()) return;
                        txtHelloUser.setText("Hello, " + name);
                    }
                });
    }

    /**
     * Fetches the user's expiry-window preference and full inventory from RTDB,
     * then updates the total-items, about-to-expire, dairy, and produce counters.
     * Items are considered "about to expire" when their expiry calendar-day falls
     * within the configured {@code daysBeforeExpireChoice} window from today.
     * Resets all counts to zero on any fetch failure.
     */
    private void updateStats() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            txtTotalProducts.setText("0");
            txtAboutToExpire.setText("About to expire: 0");
            txtDairyItems.setText("0 Items");
            txtProduceItems.setText("0 Items");
            return;
        }

        progressLoading.setVisibility(View.VISIBLE);
        rtdb.fetchUserPreferences(user, new RtdbRepository.UserPrefsCallback() {
            @Override
            public void onSuccess(String units, int daysBeforeExpireChoice) {
                rtdb.fetchAllInventory(user, new RtdbRepository.ProductsCallback() {
                    @Override
                    public void onSuccess(List<Product> products) { // this is getting the info in the main screen from the inventory
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                progressLoading.setVisibility(View.GONE);
                                int totalAmount = 0;
                                int aboutToExpireCount = 0;
                                int dairyAmount = 0;
                                int produceAmount = 0;
                                // Compare calendar days so "tomorrow" is always 1 day away
                                // regardless of the exact clock time when the expiry was set.
                                Calendar todayCal = Calendar.getInstance();
                                todayCal.set(Calendar.HOUR_OF_DAY, 0);
                                todayCal.set(Calendar.MINUTE, 0);
                                todayCal.set(Calendar.SECOND, 0);
                                todayCal.set(Calendar.MILLISECOND, 0);
                                long todayMidnight = todayCal.getTimeInMillis();
                                for (Product p : products) {
                                    int amt = Math.max(0, p.amount);
                                    totalAmount += amt;
                                    if (p.expiresAtMs > 0L) {
                                        Calendar expCal = Calendar.getInstance();
                                        expCal.setTimeInMillis(p.expiresAtMs);
                                        expCal.set(Calendar.HOUR_OF_DAY, 0);
                                        expCal.set(Calendar.MINUTE, 0);
                                        expCal.set(Calendar.SECOND, 0);
                                        expCal.set(Calendar.MILLISECOND, 0);
                                        long daysUntil = (expCal.getTimeInMillis() - todayMidnight) / 86400000L;
                                        if (daysUntil >= 0 && daysUntil <= daysBeforeExpireChoice) aboutToExpireCount += amt;
                                    }
                                    String cat = p.category == null ? "" : p.category.trim().toLowerCase();
                                    if (cat.equals("dairy")) dairyAmount += amt;
                                    else if (cat.equals("produce") || cat.equals("vegetable") || cat.equals("vegetables")
                                            || cat.equals("fruit") || cat.equals("fruits")) produceAmount += amt;
                                }
                                txtTotalProducts.setText(String.valueOf(totalAmount));
                                txtAboutToExpire.setText("About to expire: " + aboutToExpireCount);
                                txtDairyItems.setText(dairyAmount + " Items");
                                txtProduceItems.setText(produceAmount + " Items");
                            }
                        });
                    }

                    @Override
                    public void onFailure(DatabaseError error) { //if fails put all defaults 0
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                progressLoading.setVisibility(View.GONE);
                                txtTotalProducts.setText("0");
                                txtAboutToExpire.setText("About to expire: 0");
                                txtDairyItems.setText("0 Items");
                                txtProduceItems.setText("0 Items");
                            }
                        });
                    }
                });
            }

            @Override
            public void onFailure(DatabaseError error) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressLoading.setVisibility(View.GONE);
                        txtTotalProducts.setText("0");
                        txtAboutToExpire.setText("About to expire: 0");
                        txtDairyItems.setText("0 Items");
                        txtProduceItems.setText("0 Items");
                    }
                });
            }
        });
    }
}
