package com.example.myfridge;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.myfridge.notifications.ExpiryNotificationHelper;
import com.example.myfridge.notifications.ExpiryWorkScheduler;
import com.example.myfridge.storage.Product;
import com.example.myfridge.rtdb.RtdbRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.List;

public class MainScreenActivity extends AppCompatActivity {

    private TextView txtHelloUser;
    private TextView txtTotalProducts;
    private TextView txtAboutToExpire;
    private TextView txtDairyItems;
    private TextView txtProduceItems;

    private RtdbRepository rtdb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_screen);

        rtdb = new RtdbRepository();

        Button btnInventory = findViewById(R.id.btn_inventory);
        btnInventory.setOnClickListener(v -> startActivity(new Intent(this, StorageActivity.class)));

        Button btnSettings = findViewById(R.id.btn_settings);
        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        txtHelloUser = findViewById(R.id.txt_hello_user);
        txtTotalProducts = findViewById(R.id.txt_total_products);
        txtAboutToExpire = findViewById(R.id.txt_about_to_expire);
        txtDairyItems = findViewById(R.id.txt_dairy_items);
        txtProduceItems = findViewById(R.id.txt_produce_items);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateGreeting();
        updateStats();

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            rtdb.ensureUserProfile(user);
            ExpiryNotificationHelper.createChannel(this);
            ExpiryWorkScheduler.schedule(this);
        }
    }

    private void updateGreeting() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            txtHelloUser.setText("Hello, User");
            return;
        }

        // Prefer RTDB profile name (works for email/password users).
        FirebaseDatabase.getInstance()
                .getReference()
                .child("users")
                .child(user.getUid())
                .child("name")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        String name = snapshot.getValue(String.class);
                        if (name == null || name.trim().isEmpty()) {
                            name = user.getDisplayName();
                        }
                        if (name == null || name.trim().isEmpty()) {
                            name = user.getEmail();
                        }
                        if (name == null || name.trim().isEmpty()) {
                            name = "User";
                        }
                        txtHelloUser.setText("Hello, " + name);
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        String name = user.getDisplayName();
                        if (name == null || name.trim().isEmpty()) {
                            name = user.getEmail();
                        }
                        if (name == null || name.trim().isEmpty()) {
                            name = "User";
                        }
                        txtHelloUser.setText("Hello, " + name);
                    }
                });
    }

    private void updateStats() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            txtTotalProducts.setText("0");
            txtAboutToExpire.setText("About to expire: 0");
            txtDairyItems.setText("0 Items");
            txtProduceItems.setText("0 Items");
            return;
        }

        rtdb.fetchUserPreferences(user, new RtdbRepository.UserPrefsCallback() {
            @Override
            public void onSuccess(String units, int daysBeforeExpireChoice) {
                long windowMs = (long) daysBeforeExpireChoice * 24L * 60L * 60L * 1000L;
                rtdb.fetchAllInventory(user, new RtdbRepository.ProductsCallback() {
                    @Override
                    public void onSuccess(List<Product> products) {
                        runOnUiThread(() -> {
                            int totalAmount = 0;
                            int aboutToExpireCount = 0;
                            int dairyAmount = 0;
                            int produceAmount = 0;

                            long now = System.currentTimeMillis();

                            for (Product p : products) {
                                int amt = Math.max(0, p.amount);
                                totalAmount += amt;

                                if (p.expiresAtMs > 0L) {
                                    long diff = p.expiresAtMs - now;
                                    if (diff >= 0L && diff <= windowMs) {
                                        aboutToExpireCount += amt;
                                    }
                                }

                                String cat = p.category == null ? "" : p.category.trim().toLowerCase();
                                if (cat.equals("dairy")) {
                                    dairyAmount += amt;
                                } else if (cat.equals("produce") || cat.equals("vegetable") || cat.equals("vegetables") ||
                                        cat.equals("fruit") || cat.equals("fruits")) {
                                    produceAmount += amt;
                                }
                            }

                            txtTotalProducts.setText(String.valueOf(totalAmount));
                            txtAboutToExpire.setText("About to expire: " + aboutToExpireCount);
                            txtDairyItems.setText(dairyAmount + " Items");
                            txtProduceItems.setText(produceAmount + " Items");
                        });
                    }

                    @Override
                    public void onFailure(DatabaseError error) {
                        runOnUiThread(() -> {
                            txtTotalProducts.setText("0");
                            txtAboutToExpire.setText("About to expire: 0");
                            txtDairyItems.setText("0 Items");
                            txtProduceItems.setText("0 Items");
                        });
                    }
                });
            }

            @Override
            public void onFailure(DatabaseError error) {
                runOnUiThread(() -> {
                    txtTotalProducts.setText("0");
                    txtAboutToExpire.setText("About to expire: 0");
                    txtDairyItems.setText("0 Items");
                    txtProduceItems.setText("0 Items");
                });
            }
        });
    }

    @Override
    public void onBackPressed() {
        // Prevent going back to login screen
        moveTaskToBack(true);
    }
}
