package com.example.myfridge.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

import java.util.List;

public class HomeFragment extends Fragment {

    private TextView txtHelloUser;
    private TextView txtTotalProducts;
    private TextView txtAboutToExpire;
    private TextView txtDairyItems;
    private TextView txtProduceItems;
    private RtdbRepository rtdb;

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
        root.findViewById(R.id.row_about_to_expire).setOnClickListener(v -> openInventory());
        root.findViewById(R.id.row_shopping_cta).setOnClickListener(v -> openShopping());
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateGreeting();
        updateStats();
    }

    private void openInventory() {
        if (getActivity() instanceof MainScreenActivity) {
            ((MainScreenActivity) getActivity()).switchFragment(new StorageFragment());
        }
    }

    private void openShopping() {
        if (getActivity() instanceof MainScreenActivity) {
            ((MainScreenActivity) getActivity()).switchFragment(new ShoppingFragment());
        }
    }

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
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
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
                                    if (diff >= 0L && diff <= windowMs) aboutToExpireCount += amt;
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
                        });
                    }

                    @Override
                    public void onFailure(DatabaseError error) {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
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
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    txtTotalProducts.setText("0");
                    txtAboutToExpire.setText("About to expire: 0");
                    txtDairyItems.setText("0 Items");
                    txtProduceItems.setText("0 Items");
                });
            }
        });
    }
}
